package com.realphysics;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;

/** The local player moves client-side, so motion physics for the player runs here. */
public class RealPhysicsClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player == null || client.level == null || client.isPaused()) return;
            MotionPhysics.apply(client.player);
        });
    }
}
