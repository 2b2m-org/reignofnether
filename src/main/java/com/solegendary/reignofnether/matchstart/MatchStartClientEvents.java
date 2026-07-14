package com.solegendary.reignofnether.matchstart;

import com.solegendary.reignofnether.orthoview.OrthoviewClientEvents;
import com.solegendary.reignofnether.player.PlayerClientEvents;
import com.solegendary.reignofnether.startpos.StartPosClientEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.client.event.ClientChatReceivedEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.bus.api.SubscribeEvent;

import java.util.ArrayDeque;
import java.util.Deque;

public class MatchStartClientEvents {

    private static int countdownTicks = -1;
    private static final int CHAT_BUFFER_MAX = 80;
    private static final Deque<Component> chatBuffer = new ArrayDeque<>();

    public static int getCountdownTicks() {
        return countdownTicks;
    }

    public static void syncCountdown(int remainingTicks) {
        countdownTicks = remainingTicks;
        StartPosClientEvents.isStarting = remainingTicks >= 0;
    }

    public static Deque<Component> getChatBuffer() {
        return chatBuffer;
    }

    @SubscribeEvent
    public static void onChatReceived(ClientChatReceivedEvent evt) {
        Component msg = evt.getMessage();
        if (msg == null) return;
        if (chatBuffer.size() >= CHAT_BUFFER_MAX) chatBuffer.pollFirst();
        chatBuffer.addLast(msg);
    }

    public static void dismiss() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof MatchStartScreen) {
            mc.setScreen(null);
        }
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post evt) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;

        tickCountdown();

        boolean orthoOn = OrthoviewClientEvents.isEnabled();

        // Close the screen if ortho is disabled or player is rts-locked
        if (mc.screen instanceof MatchStartScreen) {
            if (!orthoOn || PlayerClientEvents.rtsLocked) {
                mc.setScreen(null);
            }
        }
    }

    private static void tickCountdown() {
        if (!StartPosClientEvents.isStarting) {
            countdownTicks = -1;
        } else if (countdownTicks > 0) {
            countdownTicks--;
        }
    }

    @SubscribeEvent
    public static void onClientLogout(ClientPlayerNetworkEvent.LoggingOut evt) {
        countdownTicks = -1;
        chatBuffer.clear();
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof MatchStartScreen) {
            mc.setScreen(null);
        }
    }
}
