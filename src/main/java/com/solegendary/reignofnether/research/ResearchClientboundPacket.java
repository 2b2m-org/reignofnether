package com.solegendary.reignofnether.research;

import static com.solegendary.reignofnether.ReignOfNether.payloadType;

import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.PacketDistributor;


public class ResearchClientboundPacket implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<ResearchClientboundPacket> TYPE =
        payloadType("research_clientbound");
    public static final StreamCodec<FriendlyByteBuf, ResearchClientboundPacket> STREAM_CODEC =
        StreamCodec.ofMember(ResearchClientboundPacket::encode, ResearchClientboundPacket::new);

    @Override
    public CustomPacketPayload.Type<ResearchClientboundPacket> type() {
        return TYPE;
    }

    public String playerName;
    public String itemName;
    public boolean add; // false for remove
    public boolean isCheat;
    public int value;

    public static void addCheat(String playerName, String itemName) {
        PacketDistributor.sendToAllPlayers(new ResearchClientboundPacket(playerName, itemName, true, true, 0));
    }
    public static void addCheatWithValue(String playerName, String itemName, int value) {
        PacketDistributor.sendToAllPlayers(new ResearchClientboundPacket(playerName, itemName, true, true, value));
    }
    public static void removeCheat(String playerName, String itemName) {
        PacketDistributor.sendToAllPlayers(new ResearchClientboundPacket(playerName, itemName, false, true, 0));
    }
    public static void addResearch(String playerName, String itemName) {
        PacketDistributor.sendToAllPlayers(new ResearchClientboundPacket(playerName, itemName, true, false, 0));
    }

    public ResearchClientboundPacket(String playerName, String itemName, boolean add, boolean isCheat, int value) {
        this.playerName = playerName;
        this.itemName = itemName;
        this.add = add;
        this.isCheat = isCheat;
        this.value = value;
    }

    public ResearchClientboundPacket(FriendlyByteBuf buffer) {
        this.playerName = buffer.readUtf();
        this.itemName = buffer.readUtf();
        this.add = buffer.readBoolean();
        this.isCheat = buffer.readBoolean();
        this.value = buffer.readInt();
    }

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeUtf(this.playerName);
        buffer.writeUtf(this.itemName);
        buffer.writeBoolean(this.add);
        buffer.writeBoolean(this.isCheat);
        buffer.writeInt(this.value);
    }

    // server-side packet-consuming functions
    public void handle(IPayloadContext context) {

        context.enqueueWork(() -> {
            {
                        if (Minecraft.getInstance().player.getName().getString().equals(this.playerName)) {
                            if (isCheat) {
                                if (value > 0)
                                    ResearchClient.addCheatWithValue(this.itemName, this.value);
                                else if (add)
                                    ResearchClient.addCheat(this.itemName);
                                else
                                    ResearchClient.removeCheat(this.itemName);
                            } else {
                                if (add)
                                    ResearchClient.addResearch(this.playerName, ResourceLocation.tryParse(this.itemName));
                            }
                        }
                    }
        });
    }
}
