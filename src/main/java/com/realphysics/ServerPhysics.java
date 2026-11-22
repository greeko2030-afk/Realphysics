package com.realphysics;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;

/** Server-side driver: runs every module for entities near players. */
public final class ServerPhysics {

    public static void tickLevel(ServerLevel level) {
        List<ServerPlayer> players = level.players();
        if (players.isEmpty()) return;

        long t = level.getGameTime();
        boolean second = t % 20L == 0L;

        if (second) MagnetPhysics.rescan(level, players);

        Set<Entity> seen = new HashSet<>();
        for (ServerPlayer p : players) {
            for (Entity e : level.getEntities((Entity) null, p.getBoundingBox().inflate(40.0))) {
                if (seen.add(e)) entity(level, e, second);
            }
        }

        for (ServerPlayer p : players) {
            WavePhysics.track(level, p, t);
            if (second) {
                MassUtil.updatePlayer(p);
                ThermalPhysics.tick(level, p);
                FluidPhysics.pressure(level, p);
                ChangeOfStatePhysics.sample(level, p);
            }
        }
    }

    private static void entity(ServerLevel level, Entity e, boolean second) {
        if (!(e instanceof Player)) MotionPhysics.apply(e);

        if (e instanceof LivingEntity le) {
            MassPhysics.fall(le);
            if (second) FluidPhysics.scald(level, le);
        }
        if (e instanceof ItemEntity ie) {
            MagnetPhysics.pull(level, ie);
            if (second) OpticsPhysics.lens(level, ie);
        }
        if (e instanceof LightningBolt bolt) {
            ElectricPhysics.discharge(level, bolt);
        }
    }

    private ServerPhysics() {}
}
