package com.solegendary.reignofnether.resources;

import net.neoforged.neoforge.network.PacketDistributor;

import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import com.solegendary.reignofnether.ReignOfNether;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;


public class ResourcesServerboundPacket implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<ResourcesServerboundPacket> TYPE =
        CustomPacketPayload.createType("reignofnether:resources_serverbound");
    public static final StreamCodec<FriendlyByteBuf, ResourcesServerboundPacket> STREAM_CODEC =
        StreamCodec.ofMember(ResourcesServerboundPacket::encode, ResourcesServerboundPacket::new);

    @Override
    public CustomPacketPayload.Type<ResourcesServerboundPacket> type() {
        return TYPE;
    }

    ResourcesAction action;
    public String senderName;
    public String receiverName;
    public int food;
    public int wood;
    public int ore;

    public static void sendResources(Resources resources, String senderName) {
        PacketDistributor.sendToServer(new ResourcesServerboundPacket(
                ResourcesAction.SEND_RESOURCES,
                senderName,
                resources.ownerName,
                resources.food,
                resources.wood,
                resources.ore
        ));
    }

    public ResourcesServerboundPacket(ResourcesAction action, String senderName, String receiverName, int food, int wood, int ore) {
        this.action = action;
        this.senderName = senderName;
        this.receiverName = receiverName;
        this.food = food;
        this.wood = wood;
        this.ore = ore;
    }

    public ResourcesServerboundPacket(FriendlyByteBuf buffer) {
        this.action = buffer.readEnum(ResourcesAction.class);
        this.senderName = buffer.readUtf();
        this.receiverName = buffer.readUtf();
        this.food = buffer.readInt();
        this.wood = buffer.readInt();
        this.ore = buffer.readInt();
    }

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeEnum(this.action);
        buffer.writeUtf(this.senderName);
        buffer.writeUtf(this.receiverName);
        buffer.writeInt(this.food);
        buffer.writeInt(this.wood);
        buffer.writeInt(this.ore);
    }

    // server-side packet-consuming functions
    public void handle(IPayloadContext context) {
        context.enqueueWork(() -> {

            ServerPlayer player = (ServerPlayer) context.player();
            if (player == null) {
                ReignOfNether.LOGGER.warn("ResourcesServerboundPacket: Sender was null");
                return;
            }
            if (!player.getName().getString().equals(senderName)) {
                ReignOfNether.LOGGER.warn("ResourcesServerboundPacket: Tried to process packet from " + player.getName() + " for: " + senderName);
                return;
            }
            if (action == ResourcesAction.SEND_RESOURCES) {
                ReignOfNether.LOGGER.info("[Resources] {} sent resources to {} (food: {}, wood: {}, ore: {})", senderName, this.receiverName, this.food, this.wood, this.ore);
                ResourcesServerEvents.trySendingAnyResources(this.receiverName, new Resources(this.senderName, this.food, this.wood, this.ore));
            }
        });
    }
}
