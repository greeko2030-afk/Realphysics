package com.realphysics;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/** Sonic boom: gliding faster than "sound" -> shock wave that shatters glass (resonance) and pushes entities. */
public final class WavePhysics {
    private static final Map<UUID, Vec3> LAST = new HashMap<>();
    private static final Map<UUID, Long> BOOM = new HashMap<>();

    public static void track(ServerLevel level, ServerPlayer p, long t) {
        Vec3 now = p.position();
        Vec3 last = LAST.put(p.getUUID(), now);
        if (!PhysicsConfig.waves || last == null || !p.isFallFlying()) return;
        if (now.distanceTo(last) < PhysicsConfig.sonicSpeed) return;
        long lastBoom = BOOM.getOrDefault(p.getUUID(), -1000L);
        if (t - lastBoom < 120L) return;
        BOOM.put(p.getUUID(), t);
        boom(level, p);
    }

    private static boolean glass(BlockState s) {
        return BuiltInRegistries.BLOCK.getKey(s.getBlock()).getPath().contains("glass");
    }

    private static void boom(ServerLevel level, ServerPlayer p) {
        level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.WARDEN_SONIC_BOOM, SoundSource.PLAYERS, 4.0F, 1.0F);
        level.sendParticles(ParticleTypes.SONIC_BOOM, p.getX(), p.getY(), p.getZ(), 1, 0.0, 0.0, 0.0, 0.0);

        final int r = 6;
        BlockPos c = p.blockPosition();
        for (BlockPos q : BlockPos.betweenClosed(c.offset(-r, -r, -r), c.offset(r, r, r))) {
            if (q.distSqr(c) > r * r) continue;
            if (glass(level.getBlockState(q))) level.destroyBlock(q.immutable(), false);
        }
        for (LivingEntity le : level.getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(8.0))) {
            if (le == p) continue;
            Vec3 d = le.position().subtract(p.position());
            double len = Math.max(1.0, d.length());
            Vec3 push = d.scale(0.6 / (len * len));
            le.push(push.x, 0.15, push.z);
            if (le instanceof ServerPlayer) le.hurtMarked = true;
        }
    }

    private WavePhysics() {}
}
