package com.solegendary.reignofnether.fogofwar;

import net.neoforged.neoforge.network.PacketDistributor;

import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import com.solegendary.reignofnether.ReignOfNether;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;


public class FogOfWarServerboundPacket implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<FogOfWarServerboundPacket> TYPE =
        CustomPacketPayload.createType("reignofnether:fog_of_war_serverbound");
    public static final StreamCodec<FriendlyByteBuf, FogOfWarServerboundPacket> STREAM_CODEC =
        StreamCodec.ofMember(FogOfWarServerboundPacket::encode, FogOfWarServerboundPacket::new);

    @Override
    public CustomPacketPayload.Type<FogOfWarServerboundPacket> type() {
        return TYPE;
    }

    boolean enable;

    public static void setServerFog(boolean enable) {
        Minecraft MC = Minecraft.getInstance();
        if (MC.player != null)
            PacketDistributor.sendToServer(new FogOfWarServerboundPacket(enable));
    }

    // packet-handler functions
    public FogOfWarServerboundPacket(boolean enable) {
        this.enable = enable;
    }

    public FogOfWarServerboundPacket(FriendlyByteBuf buffer) {
        this.enable = buffer.readBoolean();
    }

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeBoolean(this.enable);
    }

    // server-side packet-consuming functions
    public void handle(IPayloadContext context) {
        context.enqueueWork(() -> {

            ServerPlayer player = (ServerPlayer) context.player();
            if (player == null) {
                ReignOfNether.LOGGER.warn("FogOfWarServerboundPacket: Sender was null");
                return;
            } else if (!player.hasPermissions(4)) {
                ReignOfNether.LOGGER.warn("FogOfWarServerboundPacket: Tried to process packet from " + player.getName() + " with insufficient permissions");
                return;
            }

            ReignOfNether.LOGGER.info("[FogOfWar] {} set fog of war to {}", player.getName(), enable);

            FogOfWarServerEvents.setEnabled(enable);
        });
    }
}