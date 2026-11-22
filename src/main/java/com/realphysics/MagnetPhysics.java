package com.realphysics;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/** Magnetism: lodestone attracts ferrous items, force ~ 1 / r^2. */
public final class MagnetPhysics {
    private static final Map<ResourceKey<Level>, List<BlockPos>> LODE = new HashMap<>();

    public static void rescan(ServerLevel level, List<ServerPlayer> players) {
        if (!PhysicsConfig.magnetism) return;
        List<BlockPos> list = new ArrayList<>();
        for (ServerPlayer p : players) {
            BlockPos c = p.blockPosition();
            for (BlockPos q : BlockPos.betweenClosed(c.offset(-16, -8, -16), c.offset(16, 8, 16))) {
                if (!level.hasChunkAt(q)) continue;
                if (level.getBlockState(q).is(Blocks.LODESTONE)) list.add(q.immutable());
            }
        }
        LODE.put(level.dimension(), list);
    }

    static boolean ferrous(ItemStack st) {
        String p = BuiltInRegistries.ITEM.getKey(st.getItem()).getPath();
        return p.contains("iron") || p.startsWith("chainmail") || p.equals("bucket") || p.equals("shears")
                || p.equals("compass") || p.equals("minecart") || p.equals("anvil") || p.equals("hopper")
                || p.equals("flint_and_steel") || p.equals("crossbow");
    }

    public static void pull(ServerLevel level, ItemEntity ie) {
        if (!PhysicsConfig.magnetism) return;
        List<BlockPos> list = LODE.get(level.dimension());
        if (list == null || list.isEmpty() || !ferrous(ie.getItem())) return;

        Vec3 pos = ie.position();
        Vec3 best = null;
        double bd = 1.0E9;
        for (BlockPos b : list) {
            Vec3 c = Vec3.atCenterOf(b);
            double d2 = c.distanceToSqr(pos);
            if (d2 < bd) { bd = d2; best = c; }
        }
        if (best == null || bd > 100.0) return;

        double d = Math.sqrt(bd);
        Vec3 dir = best.subtract(pos).scale(1.0 / Math.max(d, 1.0E-3));
        double a = Math.min(0.06, 0.4 / Math.max(1.0, bd));
        Vec3 v = ie.getDeltaMovement().add(dir.scale(a));
        if (d < 0.9) v = v.scale(0.2);
        ie.setDeltaMovement(v);
        ie.hasImpulse = true;
    }

    private MagnetPhysics() {}
}
