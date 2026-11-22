package com.realphysics;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Electrical discharge: lightning current spreads through conductors
 * (water + metals). Ohm's law: I = V / R  - leather armor adds resistance.
 */
public final class ElectricPhysics {
    private static final Set<UUID> DONE = new HashSet<>();

    static boolean conductive(BlockState s) {
        if (s.getFluidState().is(FluidTags.WATER)) return true;
        String p = BuiltInRegistries.BLOCK.getKey(s.getBlock()).getPath();
        return p.contains("iron") || p.contains("copper") || p.contains("gold") || p.contains("netherite")
                || p.contains("lightning_rod") || p.contains("rail") || p.contains("redstone_wire")
                || p.equals("redstone_block") || p.contains("chain") || p.contains("anvil")
                || p.contains("bars") || p.contains("cauldron");
    }

    private static double resistance(LivingEntity le) {
        double r = 5.0;
        EquipmentSlot[] slots = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
        for (EquipmentSlot s : slots) {
            var st = le.getItemBySlot(s);
            if (!st.isEmpty() && BuiltInRegistries.ITEM.getKey(st.getItem()).getPath().startsWith("leather")) r += 8.0;
        }
        return r;
    }

    public static void discharge(ServerLevel level, LightningBolt bolt) {
        if (!PhysicsConfig.electric || bolt.tickCount > 1) return;
        if (!DONE.add(bolt.getUUID())) return;
        if (DONE.size() > 500) DONE.clear();

        BlockPos origin = bolt.blockPosition();
        BlockPos seed = null;
        for (int i = 0; i <= 4; i++) {
            BlockPos q = origin.below(i);
            if (conductive(level.getBlockState(q))) { seed = q; break; }
        }
        if (seed == null) return;

        Set<BlockPos> seen = new HashSet<>();
        ArrayDeque<BlockPos> todo = new ArrayDeque<>();
        seen.add(seed);
        todo.add(seed);
        Set<LivingEntity> victims = new HashSet<>();
        while (!todo.isEmpty() && seen.size() < 400) {
            BlockPos c = todo.poll();
            victims.addAll(level.getEntitiesOfClass(LivingEntity.class, new AABB(c).inflate(0.5)));
            for (Direction d : Direction.values()) {
                BlockPos n = c.relative(d);
                if (seen.contains(n) || !level.hasChunkAt(n)) continue;
                if (conductive(level.getBlockState(n))) { seen.add(n); todo.add(n); }
            }
        }

        Vec3 src = Vec3.atCenterOf(seed);
        for (LivingEntity le : victims) {
            double dist = le.position().distanceTo(src);
            double volts = 40.0 / (1.0 + 0.12 * dist);
            float dmg = (float) Math.min(14.0, volts / resistance(le) * 1.2);   // I = V / R
            le.hurt(level.damageSources().lightningBolt(), dmg);
        }
        int n = 0;
        for (BlockPos c : seen) {
            if (n++ > 80) break;
            level.sendParticles(ParticleTypes.ELECTRIC_SPARK, c.getX() + 0.5, c.getY() + 1.0, c.getZ() + 0.5, 2, 0.3, 0.2, 0.3, 0.1);
        }
    }

    private ElectricPhysics() {}
}
