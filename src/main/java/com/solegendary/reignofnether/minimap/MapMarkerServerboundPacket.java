package com.solegendary.reignofnether.minimap;

import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import com.solegendary.reignofnether.alliance.AlliancesServerEvents;
import com.solegendary.reignofnether.sounds.SoundAction;
import com.solegendary.reignofnether.sounds.SoundClientboundPacket;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.HashSet;
import java.util.Set;

public class MapMarkerServerboundPacket implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<MapMarkerServerboundPacket> TYPE =
        CustomPacketPayload.createType("reignofnether:map_marker_serverbound");
    public static final StreamCodec<FriendlyByteBuf, MapMarkerServerboundPacket> STREAM_CODEC =
        StreamCodec.ofMember(MapMarkerServerboundPacket::encode, MapMarkerServerboundPacket::new);

    @Override
    public CustomPacketPayload.Type<MapMarkerServerboundPacket> type() {
        return TYPE;
    }
    private final int x;
    private final int z;



    public MapMarkerServerboundPacket(int x, int z) {
        this.x = x;
        this.z = z;
    }

    public MapMarkerServerboundPacket(FriendlyByteBuf buffer) {
        this.x = buffer.readInt();
        this.z = buffer.readInt();
    }

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeInt(this.x);
        buffer.writeInt(this.z);
    }

    public void handle(IPayloadContext context) {
        context.enqueueWork(() -> {
            ServerPlayer player = (ServerPlayer) context.player();
            if (player == null) {
                return;
            }

            MinecraftServer server = player.getServer();
            if (server == null) {
                return;
            }

            PlayerList playerList = server.getPlayerList();
            Set<ServerPlayer> recipients = new HashSet<>();
            recipients.add(player);
            String playerName = player.getName().getString();
            for (String allyName : AlliancesServerEvents.getAllAllies(playerName)) {
                ServerPlayer allyPlayer = playerList.getPlayerByName(allyName);
                if (allyPlayer != null) {
                    recipients.add(allyPlayer);
                }
            }

            MapMarkerClientboundPacket markerPacket = new MapMarkerClientboundPacket(x, z, playerName);
            for (ServerPlayer target : recipients) {
                PacketDistributor.sendToPlayer(target, markerPacket);
                PacketDistributor.sendToPlayer(target,
                        new SoundClientboundPacket(SoundAction.ALLY, BlockPos.ZERO, "", 1.0f, -1));
            }
        });
    }
}
