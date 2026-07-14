package com.solegendary.reignofnether.rtsmap;

import static com.solegendary.reignofnether.ReignOfNether.payloadType;

import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import com.solegendary.reignofnether.ReignOfNether;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

public class RTSMapInfoServerboundPacket implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<RTSMapInfoServerboundPacket> TYPE =
        payloadType("rts_map_info_serverbound");
    public static final StreamCodec<FriendlyByteBuf, RTSMapInfoServerboundPacket> STREAM_CODEC =
        StreamCodec.ofMember(RTSMapInfoServerboundPacket::encode, RTSMapInfoServerboundPacket::new);

    @Override
    public CustomPacketPayload.Type<RTSMapInfoServerboundPacket> type() {
        return TYPE;
    }
    private final String mode;

    public static void setStartingMode(String mode) {
        PacketDistributor.sendToServer(new RTSMapInfoServerboundPacket(mode));
    }

    public RTSMapInfoServerboundPacket(String mode) {
        this.mode = mode;
    }

    public RTSMapInfoServerboundPacket(FriendlyByteBuf buffer) {
        this.mode = buffer.readUtf();
    }

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeUtf(this.mode);
    }

    public void handle(IPayloadContext context) {
        context.enqueueWork(() -> {

            ServerPlayer player = (ServerPlayer) context.player();
            if (player == null) {
                ReignOfNether.LOGGER.warn("RTSMapInfoServerboundPacket: Sender was null");
                return;
            } else if (!player.hasPermissions(2)) {
                ReignOfNether.LOGGER.warn("RTSMapInfoServerboundPacket: Tried to process packet from " + player.getName() + " with insufficient permissions");
                return;
            }
            RTSMapInfoServerEvents.trySetStartingMode(mode).ifPresent(player::sendSystemMessage);
        });
    }
}
