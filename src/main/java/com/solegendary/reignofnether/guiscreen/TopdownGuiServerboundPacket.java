package com.solegendary.reignofnether.guiscreen;

import net.neoforged.neoforge.network.PacketDistributor;

import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import com.solegendary.reignofnether.ReignOfNether;
import com.solegendary.reignofnether.player.PlayerServerEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;


public class TopdownGuiServerboundPacket implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<TopdownGuiServerboundPacket> TYPE =
        CustomPacketPayload.createType("reignofnether:topdown_gui_serverbound");
    public static final StreamCodec<FriendlyByteBuf, TopdownGuiServerboundPacket> STREAM_CODEC =
        StreamCodec.ofMember(TopdownGuiServerboundPacket::encode, TopdownGuiServerboundPacket::new);

    @Override
    public CustomPacketPayload.Type<TopdownGuiServerboundPacket> type() {
        return TYPE;
    }
    public boolean topdownGuiOpen = false;
    public int playerId = -1; // to track

    // client-side helper functions
    public static void openTopdownGui(int playerId) {
        PacketDistributor.sendToServer(new TopdownGuiServerboundPacket(true, playerId));
    }
    public static void closeTopdownGui(int playerId) {
        Minecraft.getInstance().popGuiLayer();
        PacketDistributor.sendToServer(new TopdownGuiServerboundPacket(false, playerId));
    }


    // packet-handler functions
    public TopdownGuiServerboundPacket(Boolean pos, int playerId) {
        this.topdownGuiOpen = pos;
        this.playerId = playerId;
    }

    public TopdownGuiServerboundPacket(FriendlyByteBuf buffer) {
        this.topdownGuiOpen = buffer.readBoolean();
        this.playerId = buffer.readInt();
    }

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeBoolean(this.topdownGuiOpen);
        buffer.writeInt(this.playerId);
    }


    // server-side packet-consuming functions
    public void handle(IPayloadContext context) {
        context.enqueueWork(() -> {

            ServerPlayer player = (ServerPlayer) context.player();
            if (player == null) {
                ReignOfNether.LOGGER.warn("TopdownGuiServerboundPacket: Sender was null");
                return;
            } else if (player.getId() != playerId) {
                ReignOfNether.LOGGER.warn("TopdownGuiServerboundPacket: Tried to process packet from " + player.getName() + " for id: " + this.playerId);
                return;
            }

            if (this.topdownGuiOpen)
                PlayerServerEvents.openTopdownGui(this.playerId);
            else
                PlayerServerEvents.closeTopdownGui(this.playerId);
        });
    }
}