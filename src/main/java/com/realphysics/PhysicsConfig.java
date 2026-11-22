package com.realphysics;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

import net.fabricmc.loader.api.FabricLoader;

/** config/realphysics.properties - every module can be switched off. Values are validated on load. */
public final class PhysicsConfig {
    // Mechanics
    public static boolean gravity = true;        // altitude gravity (Newton's law of gravitation)
    public static boolean airDrag = true;        // quadratic air resistance -> terminal velocity
    public static boolean friction = true;       // surface friction (sand, gravel, snow)
    public static boolean mass = true;           // mass: armor + carried weight
    public static boolean combat = true;         // Newton 2nd/3rd law, momentum, impulse
    public static boolean oscillation = true;    // reserved for oscillating objects
    // Structures
    public static boolean structural = true;     // floating clusters fall
    public static boolean cantilever = true;     // stress: overhangs too long collapse
    // Fluids
    public static boolean fluids = true;         // master switch for water physics on entities
    public static boolean waterWaves = true;     // travelling wave field moves floating objects
    public static boolean waterPressure = true;  // Torricelli: deep water pushes currents harder
    public static boolean waterTurbulence = true;// eddies in fast / narrow flow
    public static boolean waterSplash = true;    // momentum transfer when something hits water
    public static boolean waterTemperature = true; // water temperature -> viscosity, body cooling, ice
    public static boolean hydrostatic = true;    // deep water pressure damage
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
    public static int maxEntitiesPerTick = 400;  // performance cap per level per tick

    private static boolean b(Properties p, String k, boolean d) {
        String v = p.getProperty(k);
        if (v == null) return d;
        String t = v.trim();
        if (t.equalsIgnoreCase("true")) return true;
        if (t.equalsIgnoreCase("false")) return false;
        return d; // invalid value -> default
    }

    private static double n(Properties p, String k, double d, double lo, double hi) {
        String v = p.getProperty(k);
        double r = d;
        if (v != null) {
            try { r = Double.parseDouble(v.trim()); } catch (NumberFormatException e) { r = d; }
        }
        if (Double.isNaN(r) || Double.isInfinite(r)) r = d;
        return Math.max(lo, Math.min(hi, r));
    }

    public static void load() {
        Path file = FabricLoader.getInstance().getConfigDir().resolve("realphysics.properties");
        Properties in = new Properties();
        try {
            if (Files.exists(file)) {
                try (InputStream is = Files.newInputStream(file)) { in.load(is); }
            }
        } catch (IOException ignored) { }

        gravity = b(in, "gravity", gravity);
        airDrag = b(in, "airDrag", airDrag);
        friction = b(in, "friction", friction);
        mass = b(in, "mass", mass);
        combat = b(in, "combat", combat);
        oscillation = b(in, "oscillation", oscillation);
        structural = b(in, "structural", structural);
        cantilever = b(in, "cantilever", cantilever);
        fluids = b(in, "fluids", fluids);
        waterWaves = b(in, "waterWaves", waterWaves);
        waterPressure = b(in, "waterPressure", waterPressure);
        waterTurbulence = b(in, "waterTurbulence", waterTurbulence);
        waterSplash = b(in, "waterSplash", waterSplash);
        waterTemperature = b(in, "waterTemperature", waterTemperature);
        hydrostatic = b(in, "hydrostatic", hydrostatic);
        thermal = b(in, "thermal", thermal);
        thermalDamage = b(in, "thermalDamage", thermalDamage);
        changeOfState = b(in, "changeOfState", changeOfState);
        evaporateSources = b(in, "evaporateSources", evaporateSources);
        electric = b(in, "electric", electric);
        magnetism = b(in, "magnetism", magnetism);
        waves = b(in, "waves", waves);
        optics = b(in, "optics", optics);

        gravityRadius = n(in, "gravityRadius", gravityRadius, 100.0, 100000.0);
        dragK = n(in, "dragK", dragK, 0.0, 0.05);
        sonicSpeed = n(in, "sonicSpeed", sonicSpeed, 0.5, 10.0);
        maxCluster = (int) n(in, "maxCluster", maxCluster, 8, 512);
        pressureDepth = (int) n(in, "pressureDepth", pressureDepth, 5, 300);
        maxEntitiesPerTick = (int) n(in, "maxEntitiesPerTick", maxEntitiesPerTick, 32, 4096);

        // write back sanitized values so the file always documents every option
        Properties out = new Properties();
        out.setProperty("gravity", Boolean.toString(gravity));
        out.setProperty("airDrag", Boolean.toString(airDrag));
        out.setProperty("friction", Boolean.toString(friction));
        out.setProperty("mass", Boolean.toString(mass));
        out.setProperty("combat", Boolean.toString(combat));
        out.setProperty("oscillation", Boolean.toString(oscillation));
        out.setProperty("structural", Boolean.toString(structural));
        out.setProperty("cantilever", Boolean.toString(cantilever));
        out.setProperty("fluids", Boolean.toString(fluids));
        out.setProperty("waterWaves", Boolean.toString(waterWaves));
        out.setProperty("waterPressure", Boolean.toString(waterPressure));
        out.setProperty("waterTurbulence", Boolean.toString(waterTurbulence));
        out.setProperty("waterSplash", Boolean.toString(waterSplash));
        out.setProperty("waterTemperature", Boolean.toString(waterTemperature));
        out.setProperty("hydrostatic", Boolean.toString(hydrostatic));
        out.setProperty("thermal", Boolean.toString(thermal));
        out.setProperty("thermalDamage", Boolean.toString(thermalDamage));
        out.setProperty("changeOfState", Boolean.toString(changeOfState));
        out.setProperty("evaporateSources", Boolean.toString(evaporateSources));
        out.setProperty("electric", Boolean.toString(electric));
        out.setProperty("magnetism", Boolean.toString(magnetism));
        out.setProperty("waves", Boolean.toString(waves));
        out.setProperty("optics", Boolean.toString(optics));
        out.setProperty("gravityRadius", Double.toString(gravityRadius));
        out.setProperty("dragK", Double.toString(dragK));
        out.setProperty("sonicSpeed", Double.toString(sonicSpeed));
        out.setProperty("maxCluster", Integer.toString(maxCluster));
        out.setProperty("pressureDepth", Integer.toString(pressureDepth));
        out.setProperty("maxEntitiesPerTick", Integer.toString(maxEntitiesPerTick));
        try (OutputStream os = Files.newOutputStream(file)) {
            out.store(os, "RealPhysics config (values are clamped to safe ranges on load)");
        } catch (IOException ignored) { }
    }

    private PhysicsConfig() {}
}
