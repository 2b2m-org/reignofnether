package com.solegendary.reignofnether.fogofwar;

import static com.solegendary.reignofnether.ReignOfNether.payloadType;

import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import net.minecraft.network.FriendlyByteBuf;
import net.neoforged.neoforge.network.PacketDistributor;


public class FogOfWarClientboundPacket implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<FogOfWarClientboundPacket> TYPE =
        payloadType("fog_of_war_clientbound");
    public static final StreamCodec<FriendlyByteBuf, FogOfWarClientboundPacket> STREAM_CODEC =
        StreamCodec.ofMember(FogOfWarClientboundPacket::encode, FogOfWarClientboundPacket::new);

    @Override
    public CustomPacketPayload.Type<FogOfWarClientboundPacket> type() {
        return TYPE;
    }

    public boolean enable;
    public String playerName;
    public int unitId;

    public static void setEnabled(boolean enable) {
        PacketDistributor.sendToAllPlayers(new FogOfWarClientboundPacket(enable, "", 0));
    }

    public static void revealOrHidePlayer(boolean reveal, String playerName) {
        PacketDistributor.sendToAllPlayers(new FogOfWarClientboundPacket(reveal, playerName, 0));
    }

    // when a ranged unit attacks from within fog, reveal it to the player who is being attacked
    public static void revealRangedUnit(String playerBeingAttacked, int unitId) {
        PacketDistributor.sendToAllPlayers(new FogOfWarClientboundPacket(true, playerBeingAttacked, unitId));
    }

    public FogOfWarClientboundPacket(boolean enable, String playerName, int unitId) {
        this.enable = enable;
        this.playerName = playerName;
        this.unitId = unitId;
    }

    public FogOfWarClientboundPacket(FriendlyByteBuf buffer) {
        this.enable = buffer.readBoolean();
        this.playerName = buffer.readUtf();
        this.unitId = buffer.readInt();
    }

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeBoolean(this.enable);
        buffer.writeUtf(this.playerName);
        buffer.writeInt(this.unitId);
    }

    // server-side packet-consuming functions
    public void handle(IPayloadContext context) {

        context.enqueueWork(() -> {
            {
                    if (unitId > 0)
                        FogOfWarClientEvents.revealRangedUnit(playerName, unitId);
                    else if (playerName.isEmpty())
                        FogOfWarClientEvents.setEnabled(enable);
                    else
                        FogOfWarClientEvents.revealOrHidePlayer(enable, playerName);
                }
        });
    }
}
