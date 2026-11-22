package com.realphysics;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/**
 * Thermodynamics for the player:
 *  - ambient temperature from biome, altitude (lapse rate), day/night, sun, rain
 *  - thermal radiation from fire/lava (inverse-square), cold from ice/snow
 *  - greenhouse effect under glass
 *  - heat conduction through armor (leather insulates, metal conducts)
 *  - Newton's law of cooling: body temperature relaxes toward ambient
 *  - water has high heat capacity -> exchange 4x faster
 */
public final class ThermalPhysics {
    private static final Map<UUID, Double> BODY = new HashMap<>();

    private static boolean lit(BlockState s) {
        return s.hasProperty(BlockStateProperties.LIT) && s.getValue(BlockStateProperties.LIT);
    }

    /** Positive = heat source, negative = cold source (degrees at distance 1 block). */
    public static double sourceStrength(BlockState s) {
        if (s.isAir()) return 0;
        if (s.getFluidState().is(FluidTags.LAVA)) return 30;
        if (s.is(Blocks.FIRE)) return 14;
        if (s.is(Blocks.SOUL_FIRE)) return 8;
        if (s.is(Blocks.MAGMA_BLOCK)) return 10;
        if ((s.is(Blocks.CAMPFIRE) || s.is(Blocks.SOUL_CAMPFIRE)) && lit(s)) return 12;
        if ((s.is(Blocks.FURNACE) || s.is(Blocks.BLAST_FURNACE) || s.is(Blocks.SMOKER)) && lit(s)) return 9;
        if (s.is(Blocks.TORCH) || s.is(Blocks.WALL_TORCH) || s.is(Blocks.LANTERN)) return 1.2;
        if (s.is(Blocks.ICE)) return -4;
        if (s.is(Blocks.PACKED_ICE)) return -5;
        if (s.is(Blocks.BLUE_ICE)) return -7;
        if (s.is(Blocks.SNOW_BLOCK)) return -2.5;
        if (s.is(Blocks.POWDER_SNOW)) return -3;
        return 0;
    }

    private static boolean glass(BlockState s) {
        return BuiltInRegistries.BLOCK.getKey(s.getBlock()).getPath().contains("glass");
    }

    private static boolean greenhouse(ServerLevel level, BlockPos pos) {
        for (int i = 2; i <= 12; i++) {
            BlockState s = level.getBlockState(pos.above(i));
            if (s.isAir()) continue;
            return glass(s) && level.canSeeSky(pos.above(i + 1));
        }
        return false;
    }

    private static double radiant(ServerLevel level, ServerPlayer p) {
        BlockPos c = p.blockPosition();
        double sum = 0;
        for (BlockPos q : BlockPos.betweenClosed(c.offset(-5, -3, -5), c.offset(5, 3, 5))) {
            double st = sourceStrength(level.getBlockState(q));
            if (st == 0) continue;
            sum += st / (1.0 + q.distToCenterSqr(p.getX(), p.getY() + 1.0, p.getZ()));
        }
        return Math.max(-25.0, Math.min(60.0, sum));
    }

    private static double ambient(ServerLevel level, ServerPlayer p) {
        BlockPos pos = p.blockPosition();
        float bt = level.getBiome(pos).value().getBaseTemperature();
        double t = 15.0 + 20.0 * (bt - 0.8);
        t -= Math.max(0, pos.getY() - 63) * 0.065;          // lapse rate

        if (level.dimension() == Level.OVERWORLD) {
            boolean sky = level.canSeeSky(pos.above());
            double wave = Math.cos((level.getDayTime() % 24000L - 6000L) / 24000.0 * 2.0 * Math.PI); // noon = 1
            if (!sky && pos.getY() < 56) {                   // underground: stable + geothermal gradient
                double g = Math.min(1.0, (56 - pos.getY()) / 20.0);
                double cave = 13.0 + (56 - pos.getY()) * 0.08;
                t = t * (1.0 - g) + cave * g;
            } else {
                t += wave * 5.0;                             // day / night swing
                if (sky && !level.isRaining()) t += 4.0 * Math.max(0.0, wave);   // solar radiation
                if (!sky && greenhouse(level, pos)) t += 8.0 * Math.max(0.0, wave); // greenhouse effect
                if (sky && level.isRaining()) t -= 3.0;
            }
        }
        return t + radiant(level, p);
    }

    private static double conduction(ServerPlayer p) {
        EquipmentSlot[] slots = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
        double sum = 0;
        for (EquipmentSlot s : slots) {
            var st = p.getItemBySlot(s);
            if (st.isEmpty()) { sum += 1.0; continue; }
            String path = BuiltInRegistries.ITEM.getKey(st.getItem()).getPath();
            if (path.startsWith("leather")) sum += 0.5;
            else if (path.startsWith("iron") || path.startsWith("chainmail") || path.startsWith("golden")
                    || path.startsWith("copper")) sum += 1.4;
            else sum += 1.0;
        }
        return sum / 4.0;
    }

    /** Once per second. */
    public static void tick(ServerLevel level, ServerPlayer p) {
        if (!PhysicsConfig.thermal || p.isCreative() || p.isSpectator()) return;
        UUID id = p.getUUID();
        if (!p.isAlive()) { BODY.remove(id); return; }

        double amb = ambient(level, p);
        double rate = 0.02 * conduction(p);
        if (p.isInWater()) { amb = Math.min(amb, 14.0); rate *= 4.0; }

        double body = BODY.getOrDefault(id, 18.0);
        body += (amb - body) * Math.min(1.0, rate);          // Newton's law of cooling
        body = Math.max(-20.0, Math.min(70.0, body));
        if (p.hasEffect(MobEffects.FIRE_RESISTANCE)) body = Math.min(body, 36.0);
        BODY.put(id, body);

        if (PhysicsConfig.thermalDamage) {
            if (body > 38.0) {
                p.causeFoodExhaustion((float) Math.min(0.6, 0.05 * (body - 38.0)));  // sweating
                if (body > 46.0) p.hurt(level.damageSources().generic(), 1.0F);       // heat stroke
            }
            if (body < 5.0) {                                                         // hypothermia
                p.setTicksFrozen(Math.min(p.getTicksRequiredToFreeze() + 20, p.getTicksFrozen() + 80));
            }
        }
        if (body > 34.0) {
            p.displayClientMessage(Component.literal(String.format("Body temp: %.0f C (overheating)", body)), true);
        } else if (body < 8.0) {
            p.displayClientMessage(Component.literal(String.format("Body temp: %.0f C (freezing)", body)), true);
        }
    }

    private ThermalPhysics() {}
}
