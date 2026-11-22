package com.realphysics;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/** Lens principle: a spyglass / glass bottle lying in strong sun focuses light and ignites flammable blocks. */
public final class OpticsPhysics {

    public static void lens(ServerLevel level, ItemEntity ie) {
        if (!PhysicsConfig.optics || !ie.onGround()) return;
        Item it = ie.getItem().getItem();
        if (it != Items.SPYGLASS && it != Items.GLASS_BOTTLE) return;

        BlockPos pos = ie.blockPosition();
        long dt = level.getDayTime() % 24000L;
        if (dt < 3000L || dt > 9000L || level.isRaining() || !level.canSeeSky(pos)) return;
        if (!level.getBlockState(pos).isAir()) return;

        BlockState below = level.getBlockState(pos.below());
        boolean flammable = below.is(BlockTags.WOOL) || below.is(BlockTags.PLANKS)
                || below.is(BlockTags.LEAVES) || below.is(Blocks.HAY_BLOCK);
        if (flammable && level.random.nextFloat() < 0.12F) {
            level.setBlockAndUpdate(pos, Blocks.FIRE.defaultBlockState());
        }
    }

    private OpticsPhysics() {}
}
