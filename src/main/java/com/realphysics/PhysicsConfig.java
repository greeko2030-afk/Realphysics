package com.realphysics;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

import net.fabricmc.loader.api.FabricLoader;

/** config/realphysics.properties - every module can be switched off. */
public final class PhysicsConfig {
    // Mechanics
    public static boolean gravity = true;        // altitude gravity (Newton's law of gravitation)
    public static boolean airDrag = true;        // quadratic air resistance -> terminal velocity
    public static boolean friction = true;       // surface friction (sand, gravel, snow)
    public static boolean mass = true;           // mass: armor + carried weight
    public static boolean combat = true;         // Newton 2nd/3rd law, momentum, impulse
    public static boolean oscillation = true;    // simple harmonic bobbing in water
    // Structures
    public static boolean structural = true;     // floating clusters fall
    public static boolean cantilever = true;     // stress: overhangs too long collapse
    // Fluids
    public static boolean fluids = true;         // buoyancy, drag, venturi flow
    public static boolean hydrostatic = true;    // deep water pressure
    // Thermodynamics
    public static boolean thermal = true;        // body temperature model
    public static boolean thermalDamage = true;  // heat stroke / hypothermia
    public static boolean changeOfState = true;  // melt / freeze / boil
    public static boolean evaporateSources = false;
    // Electromagnetism / waves / optics
    public static boolean electric = true;       // lightning current through metal + water
    public static boolean magnetism = true;      // lodestone pulls iron items
    public static boolean waves = true;          // sonic boom + glass resonance
    public static boolean optics = true;         // lens focusing sunlight

    public static double gravityRadius = 1500.0; // smaller = stronger altitude effect
    public static double dragK = 0.004;
    public static double sonicSpeed = 2.2;       // blocks per tick (elytra)
    public static int maxCluster = 96;
    public static int pressureDepth = 40;

    private static boolean b(Properties p, String k, boolean d) {
        String v = p.getProperty(k);
        if (v == null) { p.setProperty(k, Boolean.toString(d)); return d; }
        return Boolean.parseBoolean(v.trim());
    }

    private static double n(Properties p, String k, double d) {
        String v = p.getProperty(k);
        if (v == null) { p.setProperty(k, Double.toString(d)); return d; }
        try { return Double.parseDouble(v.trim()); } catch (NumberFormatException e) { return d; }
    }

    public static void load() {
        Path file = FabricLoader.getInstance().getConfigDir().resolve("realphysics.properties");
        Properties p = new Properties();
        try {
            if (Files.exists(file)) {
                try (InputStream in = Files.newInputStream(file)) { p.load(in); }
            }
        } catch (IOException ignored) { }

        gravity = b(p, "gravity", gravity);
        airDrag = b(p, "airDrag", airDrag);
        friction = b(p, "friction", friction);
        mass = b(p, "mass", mass);
        combat = b(p, "combat", combat);
        oscillation = b(p, "oscillation", oscillation);
        structural = b(p, "structural", structural);
        cantilever = b(p, "cantilever", cantilever);
        fluids = b(p, "fluids", fluids);
        hydrostatic = b(p, "hydrostatic", hydrostatic);
        thermal = b(p, "thermal", thermal);
        thermalDamage = b(p, "thermalDamage", thermalDamage);
        changeOfState = b(p, "changeOfState", changeOfState);
        evaporateSources = b(p, "evaporateSources", evaporateSources);
        electric = b(p, "electric", electric);
        magnetism = b(p, "magnetism", magnetism);
        waves = b(p, "waves", waves);
        optics = b(p, "optics", optics);
        gravityRadius = n(p, "gravityRadius", gravityRadius);
        dragK = n(p, "dragK", dragK);
        sonicSpeed = n(p, "sonicSpeed", sonicSpeed);
        maxCluster = (int) n(p, "maxCluster", maxCluster);
        pressureDepth = (int) n(p, "pressureDepth", pressureDepth);

        try (OutputStream out = Files.newOutputStream(file)) {
            p.store(out, "RealPhysics config");
        } catch (IOException ignored) { }
    }

    private PhysicsConfig() {}
}
