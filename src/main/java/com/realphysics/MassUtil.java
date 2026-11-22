package com.realphysics;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;

/** Mass model: body volume * density + armor + carried items. */
public final class MassUtil {
    public static final double REF_MASS = 65.0;
    private static final ResourceLocation LOAD_ID =
            ResourceLocation.fromNamespaceAndPath(RealPhysics.MOD_ID, "carried_load");
    private static final Map<UUID, Double> CARRIED = new HashMap<>();

    public static double bodyMass(LivingEntity e) {
        return Math.max(2.0, e.getBbWidth() * e.getBbWidth() * e.getBbHeight() * 100.0);
    }

    public static double mass(LivingEntity e) {
        double m = bodyMass(e) + e.getArmorValue() * 1.5;
        if (e instanceof Player p) m += CARRIED.getOrDefault(p.getUUID(), 0.0);
        return m;
    }

    public static double stackMass(ItemStack s) {
        if (s.isEmpty()) return 0;
        double per;
        if (s.getItem() instanceof BlockItem) per = 0.1;
        else if (s.getMaxStackSize() == 1) per = 1.5;
        else per = 0.03;
        return per * s.getCount();
    }

    /** Once per second: weigh inventory, slow the player when overloaded (Newton 2nd law: a = F/m). */
    public static void updatePlayer(ServerPlayer p) {
        double carried = 0;
        for (ItemStack s : p.getInventory().items) carried += stackMass(s);
        CARRIED.put(p.getUUID(), carried);

        AttributeInstance speed = p.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed == null) return;
        double over = Math.max(0.0, carried - 60.0);
        double penalty = Math.min(0.35, over / 400.0);
        if (PhysicsConfig.mass && penalty > 0.01) {
            speed.addOrUpdateTransientModifier(new AttributeModifier(
                    LOAD_ID, -penalty, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        } else {
            speed.removeModifier(LOAD_ID);
        }
    }

    public static void clear(UUID id) { CARRIED.remove(id); }

    public static void clearAll() { CARRIED.clear(); }

    private MassUtil() {}
}
