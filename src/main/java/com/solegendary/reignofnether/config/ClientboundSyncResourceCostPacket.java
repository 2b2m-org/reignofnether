package com.solegendary.reignofnether.config;

import static com.solegendary.reignofnether.ReignOfNether.payloadType;

import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import com.solegendary.reignofnether.resources.ResourceCost;
import net.minecraft.network.FriendlyByteBuf;


/*
    Clientbound packet to synchronize serverside config options with the client
    so that the GUI and other elements can properly reflect the values present on the server.
 */
public class ClientboundSyncResourceCostPacket implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<ClientboundSyncResourceCostPacket> TYPE =
        payloadType("clientbound_sync_resource_cost");
    public static final StreamCodec<FriendlyByteBuf, ClientboundSyncResourceCostPacket> STREAM_CODEC =
        StreamCodec.ofMember(ClientboundSyncResourceCostPacket::encode, ClientboundSyncResourceCostPacket::new);

    @Override
    public CustomPacketPayload.Type<ClientboundSyncResourceCostPacket> type() {
        return TYPE;
    }
    private final int food;
    private final int wood;
    private final int ore;
    private final int ticks;
    private final int population;
    private final String id;

    public ClientboundSyncResourceCostPacket(ResourceCost entry) {
        this.food = entry.food;
        this.wood = entry.wood;
        this.ore = entry.ore;
        this.ticks = entry.ticks;
        this.population = entry.population;
        this.id = entry.id;
    }
    public ClientboundSyncResourceCostPacket(FriendlyByteBuf buf) {
        this.food = buf.readInt();
        this.wood = buf.readInt();
        this.ore = buf.readInt();
        this.ticks = buf.readInt();
        this.population = buf.readInt();
        this.id = buf.readUtf();
    }
    public void encode(FriendlyByteBuf buf) {
        buf.writeInt(this.getFood());
        buf.writeInt(this.getWood());
        buf.writeInt(this.getOre());
        buf.writeInt(this.getTicks());
        buf.writeInt(this.getPopulation());
        buf.writeUtf(this.getId());
    }
    public static void handle(ClientboundSyncResourceCostPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> ConfigClientEvents.loadConfigData(packet));
    }

    public int getFood() {
        return food;
    }

    public int getWood() {
        return wood;
    }

    public int getOre() {
        return ore;
    }

    public int getTicks() {
        return ticks;
    }

    public int getPopulation() {
        return population;
    }

    public String getId() {
        return id;
    }
}
