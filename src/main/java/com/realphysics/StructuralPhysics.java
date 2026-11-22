package com.realphysics;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Gravity + stress for blocks.
 *  1. Cluster check: a block group not connected to the world (size <= maxCluster) falls.
 *  2. Cantilever check: an overhang longer than the material allows collapses
 *     (allowed span grows with hardness: dirt 3, stone 4, wood 5, obsidian 12).
 */
public final class StructuralPhysics {

    private record Pending(ServerLevel level, BlockPos pos, boolean cantilever) {}

    private static final ArrayDeque<Pending> QUEUE = new ArrayDeque<>();
    private static final int MAX_QUEUE = 4096;
    private static final int PER_TICK = 48;
    private static final int MAX_FALL = 128;

    private static void enqueue(Pending p) {
        if (QUEUE.size() < MAX_QUEUE) QUEUE.add(p);
    }

    public static void onBreak(Level world, BlockPos pos) {
        if (!PhysicsConfig.structural || !(world instanceof ServerLevel sl)) return;
        enqueue(new Pending(sl, pos.immutable(), true));
    }

    /** TNT and creepers explode and then get removed: re-check blocks around them. */
    public static void onEntityGone(Entity e, ServerLevel world) {
        if (!PhysicsConfig.structural) return;
        if (!(e instanceof PrimedTnt || e instanceof Creeper)) return;
        Entity.RemovalReason r = e.getRemovalReason();
        if (r != Entity.RemovalReason.DISCARDED) return;
        BlockPos c = e.blockPosition();
        int R = 4;
        for (BlockPos q : BlockPos.betweenClosed(c.offset(-R, -R, -R), c.offset(R, R, R))) {
            if (q.distSqr(c) <= R * R) enqueue(new Pending(world, q.immutable(), false));
        }
    }

    public static void processQueue() {
        int budget = PER_TICK;
        Pending p;
        while (budget-- > 0 && (p = QUEUE.poll()) != null) {
            ServerLevel level = p.level();
            for (Direction d : Direction.values()) {
                List<BlockPos> fell = checkCluster(level, p.pos().relative(d));
                if (!fell.isEmpty()) {
                    int n = 0;
                    for (BlockPos f : fell) {
                        if (n++ >= 32) break;
                        enqueue(new Pending(level, f, false)); // progressive collapse
                    }
                }
            }
            if (p.cantilever() && PhysicsConfig.cantilever) cantilever(level, p.pos());
        }
    }

    // ---------------------------------------------------------------

    private static boolean isAnchor(ServerLevel level, BlockPos pos, BlockState s) {
        return !s.isAir() && s.getDestroySpeed(level, pos) < 0.0F; // bedrock, barrier...
    }

    private static boolean isStructural(ServerLevel level, BlockPos pos, BlockState s) {
        if (s.isAir() || s.is(BlockTags.LEAVES) || s.hasBlockEntity() || s.canBeReplaced()) return false;
        return !s.getCollisionShape(level, pos).isEmpty();
    }

    private static void drop(ServerLevel level, Collection<BlockPos> blocks) {
        List<BlockPos> sorted = new ArrayList<>(blocks);
        sorted.sort(Comparator.comparingInt(BlockPos::getY));
        int n = 0;
        for (BlockPos pos : sorted) {
            if (n++ >= MAX_FALL) break;
            BlockState s = level.getBlockState(pos);
            if (s.isAir()) continue;
            FallingBlockEntity fb = FallingBlockEntity.fall(level, pos, s);
            fb.setHurtsEntities(2.0F, 40);
        }
    }

    private static List<BlockPos> checkCluster(ServerLevel level, BlockPos start) {
        List<BlockPos> none = new ArrayList<>();
        if (level.isOutsideBuildHeight(start) || !level.hasChunkAt(start)) return none;
        if (!isStructural(level, start, level.getBlockState(start))) return none;

        int max = PhysicsConfig.maxCluster;
        Set<BlockPos> seen = new HashSet<>();
        ArrayDeque<BlockPos> todo = new ArrayDeque<>();
        seen.add(start);
        todo.add(start);
        while (!todo.isEmpty()) {
            BlockPos cur = todo.poll();
            for (Direction d : Direction.values()) {
                BlockPos n = cur.relative(d);
                if (seen.contains(n)) continue;
                if (level.isOutsideBuildHeight(n) || !level.hasChunkAt(n)) return none;
                BlockState s = level.getBlockState(n);
                if (isAnchor(level, n, s)) return none;
                if (!isStructural(level, n, s)) continue;
                seen.add(n);
                if (seen.size() > max) return none;
                todo.add(n);
            }
        }
        List<BlockPos> list = new ArrayList<>(seen);
        drop(level, list);
        return list;
    }

    private static int allowedSpan(ServerLevel level, BlockPos p, BlockState s) {
        float h = Math.max(0.1F, s.getDestroySpeed(level, p));
        int a = (int) Math.round(2.5 * Math.sqrt(h)) + 1;
        return Math.max(2, Math.min(12, a));
    }

    private static boolean cohesive(BlockState s) {
        return s.is(BlockTags.BASE_STONE_OVERWORLD) || s.is(BlockTags.BASE_STONE_NETHER) || s.is(BlockTags.DIRT);
    }

    private static void cantilever(ServerLevel level, BlockPos c) {
        final int R = 10;
        Set<BlockPos> region = new HashSet<>();
        for (BlockPos q : BlockPos.betweenClosed(c.offset(-R, -R, -R), c.offset(R, R, R))) {
            if (level.isOutsideBuildHeight(q) || !level.hasChunkAt(q)) continue;
            if (isStructural(level, q, level.getBlockState(q))) region.add(q.immutable());
        }
        if (region.isEmpty()) return;

        Map<BlockPos, Integer> dist = new HashMap<>();
        ArrayDeque<BlockPos> todo = new ArrayDeque<>();
        for (BlockPos b : region) {
            boolean edge = Math.abs(b.getX() - c.getX()) == R || Math.abs(b.getY() - c.getY()) == R
                    || Math.abs(b.getZ() - c.getZ()) == R;
            BlockPos below = b.below();
            BlockState sb = level.getBlockState(below);
            BlockPos above = b.above();
            boolean hangs = cohesive(level.getBlockState(b))
                    && isStructural(level, above, level.getBlockState(above));
            if (edge || hangs || isStructural(level, below, sb) || isAnchor(level, below, sb)) {
                dist.put(b, 0);
                todo.add(b);
            }
        }
        while (!todo.isEmpty()) {
            BlockPos cur = todo.poll();
            int d = dist.get(cur);
            for (Direction dir : Direction.values()) {
                BlockPos n = cur.relative(dir);
                if (!region.contains(n) || dist.containsKey(n)) continue;
                dist.put(n, d + 1);
                todo.add(n);
            }
        }

        List<BlockPos> falling = new ArrayList<>();
        for (BlockPos b : region) {
            Integer d = dist.get(b);
            if (d == null || d > allowedSpan(level, b, level.getBlockState(b))) falling.add(b);
        }
        if (!falling.isEmpty()) drop(level, falling);
    }

    private StructuralPhysics() {}
}
