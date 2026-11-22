package com.realphysics;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.Vec3;

/**
 * Water physics layered on top of vanilla water (vanilla block flow is NOT replaced,
 * so redstone / farms / other mods keep working). It changes how water acts on objects:
 *
 *  - Archimedes: net buoyancy from density = mass / volume (salt water is denser)
 *  - Fluid resistance: F = 1/2 rho Cd A v^2 divided by mass, viscosity depends on temperature
 *  - Dynamic flow: Venturi (narrow channel = faster), waterfall momentum
 *  - Pressure: Torricelli, outflow speed ~ sqrt(2 g h) with h = water column above
 *  - Turbulence: pseudo-random eddies, strength grows with speed / confinement
 *  - Waves: travelling wave field (deep-water dispersion w^2 = g k) driven by wind, fetch, weather
 *  - Splash: momentum conservation when a body hits water, pushes nearby bodies
 *  - Temperature: biome + depth based, used for viscosity, body cooling, freezing, evaporation
 *
 * Everything is a pure function of world state + game time, so client and server agree
 * (the client runs it for the local player, the server for everything else).
 */
public final class WaterPhysics {
    private static final Map<ResourceKey<Level>, Set<Integer>> IN_WATER = new HashMap<>();

    private static double clamp(double v, double lo, double hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    // ------------------------------------------------------------------
    // Temperature
    // ------------------------------------------------------------------

    private static String biomeName(Level level, BlockPos pos) {
        return level.getBiome(pos).unwrapKey().map(k -> k.location().getPath()).orElse("");
    }

    public static double temperature(Level level, BlockPos pos) {
        return temperature(level, pos, biomeName(level, pos));
    }

    private static double temperature(Level level, BlockPos pos, String biome) {
        double t;
        if (biome.contains("frozen") || biome.contains("ice") || biome.contains("snowy")) t = 1.0;
        else if (biome.contains("cold")) t = 6.0;
        else if (biome.contains("lukewarm")) t = 22.0;
        else if (biome.contains("warm")) t = 27.0;
        else if (biome.contains("swamp") || biome.contains("mangrove")) t = 20.0;
        else if (biome.contains("river")) t = 12.0;
        else if (biome.contains("ocean")) t = 14.0;
        else {
            float bt = level.getBiome(pos).value().getBaseTemperature();
            t = 8.0 + 14.0 * bt;
        }
        if (pos.getY() < 63) t -= Math.min(8.0, (63 - pos.getY()) * 0.12); // thermocline: deeper = colder
        return clamp(t, 0.5, 40.0);
    }

    /** Dynamic viscosity relative to 20 C water: cold water is thicker. */
    private static double viscosity(double tempC) {
        return clamp(1.0 + (20.0 - tempC) * 0.04, 0.6, 1.8);
    }

    // ------------------------------------------------------------------
    // Waves
    // ------------------------------------------------------------------

    private static double windAngle(Level level) {
        return (level.getDayTime() / 12000.0) * 1.3 + 0.7; // slowly turning wind
    }

    /** Fraction of upwind sample points that are water (wave growth needs open water = fetch). */
    private static double fetch(Level level, double x, double y, double z, double angle) {
        int wet = 0;
        for (int i = 1; i <= 4; i++) {
            BlockPos q = BlockPos.containing(x - Math.cos(angle) * 4.0 * i, y, z - Math.sin(angle) * 4.0 * i);
            if (!level.hasChunkAt(q)) continue;
            if (level.getFluidState(q).is(FluidTags.WATER) || level.getFluidState(q.below()).is(FluidTags.WATER)) wet++;
        }
        return wet / 4.0;
    }

    /** Surface velocity of the wave field at (x,z): d(height)/dt plus a small Stokes drift. */
    public static Vec3 waveVelocity(Level level, double x, double y, double z, String biome) {
        double angle = windAngle(level);
        double f = fetch(level, x, y, z, angle);
        if (f <= 0.0) return Vec3.ZERO;

        double amp = biome.contains("ocean") ? 0.30 : 0.12;
        if (level.isThundering()) amp *= 2.0;
        else if (level.isRaining()) amp *= 1.5;
        amp *= f;

        double k = 2.0 * Math.PI / 12.0;                 // 12 block wavelength
        double w = Math.sqrt(9.8 * k) / 20.0;            // rad per tick (deep-water dispersion)
        double t = level.getGameTime();
        double c1 = Math.cos(angle), s1 = Math.sin(angle);
        double a2 = angle + 0.6;
        double c2 = Math.cos(a2), s2 = Math.sin(a2);
        double ph1 = k * (x * c1 + z * s1) - w * t;
        double ph2 = 1.7 * k * (x * c2 + z * s2) - Math.sqrt(1.7) * w * t;

        double vy = -amp * w * Math.cos(ph1) - 0.5 * amp * Math.sqrt(1.7) * w * Math.cos(ph2);
        double drift = amp * amp * k * w * 0.5;
        return new Vec3(c1 * drift, vy, s1 * drift);
    }

    // ------------------------------------------------------------------
    // Mass helper
    // ------------------------------------------------------------------

    private static double massOf(Entity e) {
        if (e instanceof LivingEntity le) return MassUtil.mass(le);
        if (e instanceof ItemEntity ie) return Math.max(0.05, MassUtil.stackMass(ie.getItem()));
        if (e instanceof Boat) return 80.0;
        if (e instanceof FallingBlockEntity) return 100.0;
        return 10.0;
    }

    private static double noise(long seed, int salt) {
        long h = seed * 0x9E3779B97F4A7C15L + salt * 0x632BE59BD9B4E019L;
        h ^= h >>> 32;
        h *= 0x9E3779B97F4A7C15L;
        h ^= h >>> 29;
        return ((h & 0xFFFFL) / 32767.5) - 1.0;
    }

    // ------------------------------------------------------------------
    // Main per-tick effect on a body that is in water
    // ------------------------------------------------------------------

    public static Vec3 apply(Entity e, Level level, Vec3 v) {
        double x = v.x, y = v.y, z = v.z;
        BlockPos bp = e.blockPosition();
        FluidState fs = level.getFluidState(bp);
        String biome = biomeName(level, bp);
        double temp = PhysicsConfig.waterTemperature ? temperature(level, bp, biome) : 15.0;
        double mass = massOf(e);
        double area = Math.max(0.02, e.getBbWidth() * e.getBbHeight());
        double volume = Math.max(0.001, e.getBbWidth() * e.getBbWidth() * e.getBbHeight());
        boolean heavy = false;

        // 1) Archimedes: net force ~ (density_body / density_water - 1)
        if (e instanceof LivingEntity) {
            double rel = mass / (volume * 100.0);        // 1.0 = neutrally buoyant
            if (biome.contains("ocean")) rel *= 0.975;    // salt water is denser -> more lift
            y -= clamp(0.06 * (rel - 1.0), -0.02, 0.06);
        } else if (e instanceof ItemEntity ie) {
            if (MotionPhysics.sinks(ie.getItem())) {
                heavy = true;
                y = Math.max(-0.25, y - 0.03);
            }
        } else if (e instanceof FallingBlockEntity) {
            heavy = true;
        }

        // 2) Fluid resistance: a = 1/2 rho Cd A v^2 / m (scaled), viscosity from temperature
        double speed = Math.sqrt(x * x + y * y + z * z);
        if (speed > 1.0E-4) {
            double frac = Math.min(0.5, 6.0 * 1.0 * area * speed * viscosity(temp) / mass);
            x *= 1.0 - frac;
            y *= 1.0 - frac;
            z *= 1.0 - frac;
        }

        // 3) Waves move floating bodies that sit at the surface
        if (PhysicsConfig.waterWaves && !heavy && fs.is(FluidTags.WATER)
                && level.getFluidState(bp.above()).isEmpty()) {
            Vec3 w = waveVelocity(level, e.getX(), e.getY(), e.getZ(), biome);
            double c = e instanceof LivingEntity ? 0.25 : (e instanceof Boat ? 0.1 : 0.03);
            y += w.y * c;
            x += w.x;
            z += w.z;
        }

        // 4) Currents: Venturi + Torricelli pressure + turbulence + waterfall momentum
        if (fs.is(FluidTags.WATER) && !fs.isSource()) {
            Vec3 flow = fs.getFlow(level, bp);
            double fl = flow.length();
            int narrow = 0;
            for (Direction d : Direction.Plane.HORIZONTAL) {
                if (level.getBlockState(bp.relative(d)).canOcclude()) narrow++;
            }
            if (fl > 1.0E-3) {
                double extra = 0.014 * 0.35 * narrow;     // continuity: narrow channel flows faster
                x += flow.x * extra;
                z += flow.z * extra;

                if (PhysicsConfig.waterPressure) {
                    int head = 0;
                    BlockPos q = bp.above();
                    while (head < 8 && level.getFluidState(q).is(FluidTags.WATER)) { head++; q = q.above(); }
                    if (head > 0) {
                        double j = 0.006 * Math.sqrt(head);   // v ~ sqrt(2 g h)
                        x += flow.x / fl * j;
                        z += flow.z / fl * j;
                    }
                }

                if (PhysicsConfig.waterTurbulence) {
                    double intensity = Math.min(1.0, speed * 2.0 + 0.3 * narrow + fl * 0.2);
                    if (intensity > 0.25) {
                        long seed = bp.asLong() ^ ((level.getGameTime() / 6L) * 0x2545F4914F6CDD1DL);
                        x += noise(seed, 1) * 0.006 * intensity;
                        y += noise(seed, 2) * 0.003 * intensity;
                        z += noise(seed, 3) * 0.006 * intensity;
                    }
                }
            }
            if (fs.hasProperty(FlowingFluid.FALLING) && fs.getValue(FlowingFluid.FALLING)) {
                y -= 0.015;                                  // falling water carries momentum downward
            }
        }

        return new Vec3(x, y, z);
    }

    // ------------------------------------------------------------------
    // Splash (server only): conservation of momentum
    // ------------------------------------------------------------------

    public static void splash(ServerLevel level, Entity e) {
        if (!PhysicsConfig.waterSplash) return;
        if (!(e instanceof LivingEntity || e instanceof ItemEntity || e instanceof FallingBlockEntity || e instanceof Boat)) return;

        Set<Integer> in = IN_WATER.computeIfAbsent(level.dimension(), k -> new HashSet<>());
        boolean now = e.isInWater();
        boolean was = in.contains(e.getId());
        if (now) in.add(e.getId()); else in.remove(e.getId());
        if (!now || was) return;

        double vIn = e.yo - e.getY();                         // downward speed in blocks per tick
        if (vIn < 0.45) return;
        double m = massOf(e);

        if (!(e instanceof ServerPlayer)) {
            e.setDeltaMovement(e.getDeltaMovement().multiply(0.5, 0.35, 0.5)); // impact decelerates the body
        }
        level.sendParticles(ParticleTypes.SPLASH, e.getX(), e.getY(), e.getZ(),
                (int) Math.min(60.0, 10.0 + vIn * 40.0), 0.3, 0.1, 0.3, 0.2);

        for (Entity o : level.getEntities(e, e.getBoundingBox().inflate(3.0))) {
            if (!(o instanceof LivingEntity || o instanceof ItemEntity)) continue;
            if (!o.isInWater()) continue;
            Vec3 d = o.position().subtract(e.position());
            double dist = Math.max(0.5, d.length());
            double j = Math.min(0.3, (m * vIn / (m + massOf(o))) * 0.12 / (dist * dist));
            Vec3 dir = new Vec3(d.x, 0.0, d.z);
            if (dir.lengthSqr() < 1.0E-6) continue;
            dir = dir.normalize();
            o.push(dir.x * j, 0.05, dir.z * j);
            if (o instanceof ServerPlayer) o.hurtMarked = true;
        }
    }

    /** Called once per second: forget entities that are no longer being processed. */
    public static void cleanup(ServerLevel level, Set<Entity> seen) {
        Set<Integer> in = IN_WATER.get(level.dimension());
        if (in == null) return;
        Set<Integer> ids = new HashSet<>();
        for (Entity e : seen) ids.add(e.getId());
        in.retainAll(ids);
    }

    public static void clearAll() {
        IN_WATER.clear();
    }

    private WaterPhysics() {}
}
