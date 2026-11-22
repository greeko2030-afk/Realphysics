package com.realphysics;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Change of state with latent heat (harder ice needs more energy):
 *  melting  : ice -> water, packed ice -> ice, blue ice -> packed ice, snow -> water/air
 *  freezing : water next to ice freezes
 *  evaporation (optional): isolated water in hot, sunny biomes
 */
public final class ChangeOfStatePhysics {

    private static boolean iceFamily(BlockState s) {
        return s.is(Blocks.ICE) || s.is(Blocks.PACKED_ICE) || s.is(Blocks.BLUE_ICE);
    }

    private static double heat(ServerLevel level, BlockPos c) {
        double sum = 0;
        for (BlockPos q : BlockPos.betweenClosed(c.offset(-3, -3, -3), c.offset(3, 3, 3))) {
            double st = ThermalPhysics.sourceStrength(level.getBlockState(q));
            if (st > 0) sum += st / (1.0 + q.distSqr(c));
        }
        if (level.canSeeSky(c.above()) && level.isDay() && !level.isRaining()
                && level.getBiome(c).value().getBaseTemperature() >= 1.2F) sum += 3.0;
        return sum;
    }

    public static void sample(ServerLevel level, ServerPlayer p) {
        if (!PhysicsConfig.changeOfState) return;
        BlockPos c = p.blockPosition();
        RandomSource r = level.random;
        for (int i = 0; i < 24; i++) {
            BlockPos pos = c.offset(r.nextInt(33) - 16, r.nextInt(17) - 8, r.nextInt(33) - 16);
            if (!level.hasChunkAt(pos)) continue;
            BlockState s = level.getBlockState(pos);
            if (iceFamily(s) || s.is(Blocks.SNOW) || s.is(Blocks.SNOW_BLOCK) || s.is(Blocks.POWDER_SNOW)) {
                melt(level, pos, s, r);
            } else if (s.is(Blocks.WATER) && s.getFluidState().isSource()) {
                waterChange(level, pos, r);
            }
        }
    }

    private static void melt(ServerLevel level, BlockPos pos, BlockState s, RandomSource r) {
        if (heat(level, pos) < 2.0) return;
        float chance;
        BlockState result;
        if (s.is(Blocks.ICE)) { chance = 0.35F; result = Blocks.WATER.defaultBlockState(); }
        else if (s.is(Blocks.PACKED_ICE)) { chance = 0.15F; result = Blocks.ICE.defaultBlockState(); }
        else if (s.is(Blocks.BLUE_ICE)) { chance = 0.08F; result = Blocks.PACKED_ICE.defaultBlockState(); }
        else if (s.is(Blocks.SNOW_BLOCK)) {
            chance = 0.4F;
            result = Blocks.SNOW.defaultBlockState().setValue(SnowLayerBlock.LAYERS, 4);
        }
        else { chance = 0.5F; result = Blocks.AIR.defaultBlockState(); }
        if (r.nextFloat() > chance) return;
        level.setBlockAndUpdate(pos, result);
        level.sendParticles(ParticleTypes.CLOUD, pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5, 3, 0.2, 0.2, 0.2, 0.02);
    }

    private static void waterChange(ServerLevel level, BlockPos pos, RandomSource r) {
        int ice = 0;
        int water = 0;
        for (Direction d : Direction.values()) {
            BlockState n = level.getBlockState(pos.relative(d));
            if (iceFamily(n)) ice++;
            if (d.getAxis().isHorizontal() && n.is(Blocks.WATER)) water++;
        }
        // Freezing: heat flows into the ice, water loses latent heat
        if (ice >= 2 && heat(level, pos) < 1.0 && r.nextFloat() < 0.15F) {
            level.setBlockAndUpdate(pos, Blocks.ICE.defaultBlockState());
            return;
        }
        // Evaporation of an isolated source in hot sunshine
        if (PhysicsConfig.evaporateSources && water == 0
                && level.getBlockState(pos.above()).isAir() && level.canSeeSky(pos.above())
                && level.isDay() && !level.isRaining()
                && level.getBiome(pos).value().getBaseTemperature() >= 1.5F && r.nextFloat() < 0.02F) {
            level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
            level.sendParticles(ParticleTypes.CLOUD, pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5, 5, 0.2, 0.3, 0.2, 0.02);
        }
    }

    private ChangeOfStatePhysics() {}
}
