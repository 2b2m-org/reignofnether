
package com.solegendary.reignofnether.attackwarnings;

import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.neoforged.neoforge.network.PacketDistributor;


public class AttackWarningClientboundPacket implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<AttackWarningClientboundPacket> TYPE =
        CustomPacketPayload.createType("reignofnether:attack_warning_clientbound");
    public static final StreamCodec<FriendlyByteBuf, AttackWarningClientboundPacket> STREAM_CODEC =
        StreamCodec.ofMember(AttackWarningClientboundPacket::encode, AttackWarningClientboundPacket::new);

    @Override
    public CustomPacketPayload.Type<AttackWarningClientboundPacket> type() {
        return TYPE;
    }

    private final String attackedPlayerName;
    private final BlockPos attackPos;

    public static void sendWarning(String attackedPlayerName, BlockPos attackPos) {
        PacketDistributor.sendToAllPlayers(new AttackWarningClientboundPacket(
                attackedPlayerName,
                attackPos
            ));
    }

    // packet-handler functions
    public AttackWarningClientboundPacket(
        String attackedPlayerName,
        BlockPos attackPos
    ) {
        this.attackedPlayerName = attackedPlayerName;
        this.attackPos = attackPos;
    }

    public AttackWarningClientboundPacket(FriendlyByteBuf buffer) {
        this.attackedPlayerName = buffer.readUtf();
        this.attackPos = buffer.readBlockPos();
    }

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeUtf(this.attackedPlayerName);
        buffer.writeBlockPos(this.attackPos);
    }

    // server-side packet-consuming functions
    public void handle(IPayloadContext context) {
        context.enqueueWork(() -> {
            AttackWarningClientEvents.checkAndTriggerAttackWarning(attackedPlayerName, attackPos);
        });
    }
}
