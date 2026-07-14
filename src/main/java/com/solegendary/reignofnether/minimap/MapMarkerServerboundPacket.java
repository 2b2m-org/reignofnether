package com.solegendary.reignofnether.minimap;

import static com.solegendary.reignofnether.ReignOfNether.payloadType;

import com.solegendary.reignofnether.bot.BotServerEvents;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public class MapMarkerServerboundPacket implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<MapMarkerServerboundPacket> TYPE =
            payloadType("map_marker_serverbound");
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

            String playerName = player.getName().getString();
            MapMarkerServerEvents.sendToPlayerAndAllies(server, x, z, playerName);
            BotServerEvents.acceptHumanMarker(player, x, z);
        });
    }
}
