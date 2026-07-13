package com.solegendary.reignofnether.worldborder;

import com.solegendary.reignofnether.fogofwar.FogOfWarClientEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.world.level.border.WorldBorder;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.bus.api.SubscribeEvent;

public class WorldBorderClientEvents {

    private static final Minecraft MC = Minecraft.getInstance();

    private static double lastCenterX = 0D;
    private static double lastCenterZ = 0D;
    private static double lastSize = 0D;

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post evt) {
if (MC.level == null)
            return;

        WorldBorder border = MC.level.getWorldBorder();
        double centerX = border.getCenterX();
        double centerZ = border.getCenterZ();
        double size = border.getSize();

        if (centerX != lastCenterX ||
            centerZ != lastCenterZ ||
            size != lastSize)
            FogOfWarClientEvents.resetFogChunks();

        lastCenterX = centerX;
        lastCenterZ = centerZ;
        lastSize = size;
    }
}
