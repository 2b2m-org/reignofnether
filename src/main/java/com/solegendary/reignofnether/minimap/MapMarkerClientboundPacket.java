package com.solegendary.reignofnether.minimap;

import static com.solegendary.reignofnether.ReignOfNether.payloadType;

import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import net.minecraft.network.FriendlyByteBuf;


public class MapMarkerClientboundPacket implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<MapMarkerClientboundPacket> TYPE =
        payloadType("map_marker_clientbound");
    public static final StreamCodec<FriendlyByteBuf, MapMarkerClientboundPacket> STREAM_CODEC =
        StreamCodec.ofMember(MapMarkerClientboundPacket::encode, MapMarkerClientboundPacket::new);

    @Override
    public CustomPacketPayload.Type<MapMarkerClientboundPacket> type() {
        return TYPE;
    }
    private final int x;
    private final int z;
    private final String playerName;

    public MapMarkerClientboundPacket(int x, int z, String playerName) {
        this.x = x;
        this.z = z;
        this.playerName = playerName;
    }

    public MapMarkerClientboundPacket(FriendlyByteBuf buffer) {
        this.x = buffer.readInt();
        this.z = buffer.readInt();
        this.playerName = buffer.readUtf();
    }

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeInt(this.x);
        buffer.writeInt(this.z);
        buffer.writeUtf(this.playerName);
    }

    public void handle(IPayloadContext context) {
        context.enqueueWork(() -> {
            {
                MinimapClientEvents.addMapMarker(x, z, playerName);
            }
        });
    }
}

