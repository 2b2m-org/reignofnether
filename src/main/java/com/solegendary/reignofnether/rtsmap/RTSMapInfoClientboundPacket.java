package com.solegendary.reignofnether.rtsmap;

import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import net.minecraft.network.FriendlyByteBuf;
import net.neoforged.neoforge.network.PacketDistributor;


public class RTSMapInfoClientboundPacket implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<RTSMapInfoClientboundPacket> TYPE =
        CustomPacketPayload.createType("reignofnether:rts_map_info_clientbound");
    public static final StreamCodec<FriendlyByteBuf, RTSMapInfoClientboundPacket> STREAM_CODEC =
        StreamCodec.ofMember(RTSMapInfoClientboundPacket::encode, RTSMapInfoClientboundPacket::new);

    @Override
    public CustomPacketPayload.Type<RTSMapInfoClientboundPacket> type() {
        return TYPE;
    }

    private final RTSMapInfoAction action;
    private final String value;

    public static void sendValue(RTSMapInfoAction action, String value) {
        PacketDistributor.sendToAllPlayers(new RTSMapInfoClientboundPacket(action, value));
    }

    public RTSMapInfoClientboundPacket(RTSMapInfoAction action, String value) {
        this.action = action;
        this.value = value;
    }

    public RTSMapInfoClientboundPacket(FriendlyByteBuf buffer) {
        this.action = buffer.readEnum(RTSMapInfoAction.class);
        this.value = buffer.readUtf();
    }

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeEnum(this.action);
        buffer.writeUtf(this.value);
    }

    public void handle(IPayloadContext context) {
        context.enqueueWork(() -> {
            {
                switch (action) {
                    case SET_MODE -> RTSMapInfoClientEvents.selectedMode = value;
                    case ADD_MODE -> {
                        if (!RTSMapInfoClientEvents.modeNames.contains(value))
                            RTSMapInfoClientEvents.modeNames.add(value);
                    }
                    case SET_MAP_NAME -> RTSMapInfoClientEvents.mapName = value;
                    case SET_DESCRIPTION -> RTSMapInfoClientEvents.description = value;
                    case ADD_AUTHOR -> RTSMapInfoClientEvents.authors.add(value);
                    case SET_VERSION -> RTSMapInfoClientEvents.version = value;
                }
            }
        });
    }
}
