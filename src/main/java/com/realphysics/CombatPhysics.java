package com.realphysics;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * Newton's laws in melee:
 *  - impulse J = damage + attacker momentum
 *  - 2nd law: dv = J / m  (light entities fly further)
 *  - 3rd law: the attacker receives an equal and opposite impulse (recoil)
 */
public final class CombatPhysics {

    public static void register() {
        ServerLivingEntityEvents.AFTER_DAMAGE.register((victim, source, baseDamage, damage, blocked) -> {
            if (!PhysicsConfig.combat || blocked || damage <= 0.0F) return;
            Entity direct = source.getDirectEntity();
            Entity atkEntity = source.getEntity();
            if (!(atkEntity instanceof LivingEntity atk) || direct != atkEntity || atk == victim) return;

            Vec3 dir = new Vec3(victim.getX() - atk.getX(), 0.0, victim.getZ() - atk.getZ());
            if (dir.lengthSqr() < 1.0E-4) return;
            dir = dir.normalize();

            double speed = Math.hypot(atk.getX() - atk.xo, atk.getZ() - atk.zo); // momentum per tick
            double impulse = 0.05 * damage + 1.0 * speed;

            double mv = MassUtil.mass(victim);
            double ma = MassUtil.mass(atk);
            double dvVictim = Math.min(0.8, impulse * (MassUtil.REF_MASS / mv) * 0.4);
            double dvAttacker = Math.min(0.5, impulse * (MassUtil.REF_MASS / ma) * 0.25);

            victim.push(dir.x * dvVictim, 0.04, dir.z * dvVictim);
            atk.push(-dir.x * dvAttacker, 0.0, -dir.z * dvAttacker);
            if (victim instanceof ServerPlayer) victim.hurtMarked = true;
            if (atk instanceof ServerPlayer) atk.hurtMarked = true;
        });
    }

    private CombatPhysics() {}
}
