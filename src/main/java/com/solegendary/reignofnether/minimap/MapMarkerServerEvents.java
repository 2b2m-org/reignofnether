package com.solegendary.reignofnether.minimap;

import com.solegendary.reignofnether.alliance.AlliancesServerEvents;
import com.solegendary.reignofnether.sounds.SoundAction;
import com.solegendary.reignofnether.sounds.SoundClientboundPacket;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.HashSet;
import java.util.Set;

public final class MapMarkerServerEvents {
    private MapMarkerServerEvents() {
    }

    public static void sendToPlayerAndAllies(
            MinecraftServer server, int x, int z, String playerName) {
        Set<ServerPlayer> recipients = new HashSet<>();
        ServerPlayer player = server.getPlayerList().getPlayerByName(playerName);
        if (player != null)
            recipients.add(player);
        for (String allyName : AlliancesServerEvents.getAllAllies(playerName)) {
            ServerPlayer ally = server.getPlayerList().getPlayerByName(allyName);
            if (ally != null)
                recipients.add(ally);
        }

        MapMarkerClientboundPacket packet = new MapMarkerClientboundPacket(x, z, playerName);
        for (ServerPlayer recipient : recipients) {
            PacketDistributor.sendToPlayer(recipient, packet);
            PacketDistributor.sendToPlayer(recipient,
                    new SoundClientboundPacket(SoundAction.ALLY, BlockPos.ZERO, "", 1.0f, -1));
        }
    }
}
