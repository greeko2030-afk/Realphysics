package com.realphysics;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.Vec3;

/**
 * Per-tick velocity physics. Runs on the client for the local player
 * and on the server for every other entity.
 * Covers: gravitation (altitude), air resistance / terminal velocity, friction,
 * Archimedes (buoyancy), fluid drag, venturi flow, simple harmonic motion.
 */
public final class MotionPhysics {

    public static void apply(Entity e) {
        if (e.isPassenger() || e.isSpectator()) return;
        boolean living = e instanceof LivingEntity;
        boolean item = e instanceof ItemEntity;
        boolean boat = e instanceof Boat;
        if (!living && !item && !boat) return;
        if (e instanceof Player p && (p.getAbilities().flying || p.isFallFlying())) return;

        Level level = e.level();
        Vec3 v = e.getDeltaMovement();
        double x = v.x, y = v.y, z = v.z;
        boolean inWater = e.isInWater();
        boolean inLava = e.isInLava();
        boolean grounded = e.onGround();

        // 1) Law of universal gravitation: g falls with distance from the planet's centre
        if (PhysicsConfig.gravity && !grounded && !inWater && !inLava && !e.isNoGravity() && !boat) {
            double h = Math.max(0.0, e.getY() - 63.0);
            double r = PhysicsConfig.gravityRadius;
            double f = (r * r) / ((r + h) * (r + h));
            double g = living ? 0.08 : 0.04;
            y += g * (1.0 - f);
        }

        // 2) Air resistance: F ~ v^2  -> terminal velocity
        if (PhysicsConfig.airDrag && !grounded && !inWater && !inLava && !boat) {
            double sp = Math.sqrt(x * x + y * y + z * z);
            if (sp > 0.6) {
                double s = 1.0 - PhysicsConfig.dragK * sp;
                x *= s; y *= s; z *= s;
            }
        }

        // 3) Friction: loose surfaces have higher kinetic friction
        if (PhysicsConfig.friction && grounded && living) {
            BlockState below = level.getBlockState(BlockPos.containing(e.getX(), e.getY() - 0.2, e.getZ()));
            double f = surfaceFriction(below);
            x *= f; z *= f;
        }

        // 4) Fluids
        if (PhysicsConfig.fluids && inWater) {
            if (e instanceof ItemEntity ie) {
                if (sinks(ie.getItem())) {
                    y = Math.max(-0.25, y - 0.03);           // denser than water
                } else if (PhysicsConfig.oscillation) {
                    y += 0.0035 * Math.sin(e.tickCount * 0.2); // SHM about equilibrium
                }
            } else if (boat) {
                if (PhysicsConfig.oscillation) y += 0.004 * Math.sin(e.tickCount * 0.15);
            } else if (e instanceof LivingEntity le) {
                // heavy armor -> less net buoyancy
                y -= Math.min(0.04, le.getArmorValue() * 0.002);
                // quadratic drag in fluid
                double sp = Math.sqrt(x * x + y * y + z * z);
                if (sp > 0.15) {
                    double s = 1.0 - Math.min(0.25, 0.12 * sp);
                    x *= s; y *= s; z *= s;
                }
            }

            // Continuity / Venturi: narrow channel -> faster flow
            BlockPos bp = e.blockPosition();
            FluidState fs = level.getFluidState(bp);
            if (fs.is(FluidTags.WATER) && !fs.isSource()) {
                Vec3 flow = fs.getFlow(level, bp);
                if (flow.lengthSqr() > 1.0E-6) {
                    int narrow = 0;
                    for (Direction d : Direction.Plane.HORIZONTAL) {
                        if (level.getBlockState(bp.relative(d)).canOcclude()) narrow++;
                    }
                    double extra = 0.014 * 0.35 * narrow;
                    x += flow.x * extra;
                    z += flow.z * extra;
                }
            }
        }

        e.setDeltaMovement(x, y, z);
    }

    private static double surfaceFriction(BlockState s) {
        if (s.is(BlockTags.SAND)) return 0.95;
        if (s.is(BlockTags.SNOW)) return 0.96;
        String p = BuiltInRegistries.BLOCK.getKey(s.getBlock()).getPath();
        if (p.equals("gravel")) return 0.97;
        return 1.0;
    }

    /** Density > water? */
    static boolean sinks(ItemStack st) {
        if (st.is(ItemTags.PLANKS) || st.is(ItemTags.LOGS) || st.is(ItemTags.WOOL)
                || st.is(ItemTags.LEAVES) || st.is(ItemTags.SAPLINGS) || st.is(ItemTags.FLOWERS)
                || st.is(ItemTags.BOATS)) return false;
        String p = BuiltInRegistries.ITEM.getKey(st.getItem()).getPath();
        if (p.equals("stick") || p.equals("paper") || p.equals("feather") || p.equals("bamboo")) return false;
        if (st.getItem() instanceof BlockItem) return true;
        if (st.getMaxStackSize() == 1) return true;  // tools, armor
        return p.endsWith("_ingot") || p.endsWith("_nugget") || p.startsWith("raw_")
                || p.equals("diamond") || p.equals("emerald") || p.equals("coal") || p.equals("lapis_lazuli");
    }

    private MotionPhysics() {}
}
