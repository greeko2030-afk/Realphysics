package com.realphysics;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

public final class FluidPhysics {

    private static boolean isHot(BlockState s) {
        return s.getFluidState().is(FluidTags.LAVA) || s.is(Blocks.MAGMA_BLOCK) || s.is(Blocks.FIRE);
    }

    /** Boiling: water heated by lava / magma scalds whoever is in it. */
    public static void scald(ServerLevel level, LivingEntity le) {
        if (!PhysicsConfig.changeOfState || !le.isInWater()) return;
        BlockPos p = le.blockPosition();
        boolean hot = false;
        for (Direction d : Direction.values()) {
            if (isHot(level.getBlockState(p.relative(d)))) { hot = true; break; }
        }
        if (!hot && isHot(level.getBlockState(p.below(2)))) hot = true;
        if (!hot) return;
        le.hurt(level.damageSources().inFire(), 1.0F);
        level.sendParticles(ParticleTypes.CLOUD, le.getX(), le.getY() + 1.0, le.getZ(), 4, 0.3, 0.3, 0.3, 0.02);
        level.sendParticles(ParticleTypes.BUBBLE, le.getX(), le.getY() + 0.5, le.getZ(), 6, 0.3, 0.3, 0.3, 0.02);
    }

    /** Hydrostatic pressure P = rho * g * h : too deep without protection hurts. */
    public static void pressure(ServerLevel level, ServerPlayer p) {
        if (!PhysicsConfig.hydrostatic || p.isCreative() || p.isSpectator()) return;
        if (!p.isEyeInFluid(FluidTags.WATER)) return;
        if (p.hasEffect(MobEffects.WATER_BREATHING) || p.hasEffect(MobEffects.CONDUIT_POWER)) return;
        int depth = 0;
        BlockPos q = BlockPos.containing(p.getX(), p.getEyeY(), p.getZ()).above();
        while (depth < 200 && level.getFluidState(q).is(FluidTags.WATER)) {
            depth++;
            q = q.above();
        }
        if (depth > PhysicsConfig.pressureDepth) {
            float dmg = 0.5F + (depth - PhysicsConfig.pressureDepth) * 0.05F;
            p.hurt(level.damageSources().generic(), Math.min(4.0F, dmg));
        }
    }

    private FluidPhysics() {}
}
