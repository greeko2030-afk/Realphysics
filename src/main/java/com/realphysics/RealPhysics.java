package com.realphysics;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RealPhysics implements ModInitializer {
    public static final String MOD_ID = "realphysics";
    public static final Logger LOG = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        PhysicsConfig.load();

        ServerTickEvents.END_WORLD_TICK.register(ServerPhysics::tickLevel);
        ServerTickEvents.END_SERVER_TICK.register(server -> StructuralPhysics.processQueue());

        PlayerBlockBreakEvents.AFTER.register(
                (world, player, pos, state, be) -> StructuralPhysics.onBreak(world, pos));
        ServerEntityEvents.ENTITY_UNLOAD.register(StructuralPhysics::onEntityGone);

        CombatPhysics.register();
        LOG.info("RealPhysics loaded");
    }
}
