package com.realphysics;

import net.minecraft.world.entity.LivingEntity;

/** Heavier body -> more kinetic energy at impact -> more fall damage. */
public final class MassPhysics {
    public static void fall(LivingEntity le) {
        if (!PhysicsConfig.mass || le.onGround() || le.isInWater()) return;
        double drop = le.yo - le.getY();
        if (drop <= 0.1) return;
        double factor = Math.min(2.5, MassUtil.mass(le) / MassUtil.bodyMass(le));
        if (factor > 1.0) le.fallDistance += (float) (drop * (factor - 1.0));
    }

    private MassPhysics() {}
}
