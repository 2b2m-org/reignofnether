package com.solegendary.reignofnether.unit.packets;

import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import com.solegendary.reignofnether.building.BuildingClientEvents;
import com.solegendary.reignofnether.unit.UnitAction;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.neoforged.neoforge.network.PacketDistributor;


// allow the server to force unit actions as though it was sent by the client so it is recorded on both sides
public class BeaconSyncClientboundPacket implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<BeaconSyncClientboundPacket> TYPE =
        CustomPacketPayload.createType("reignofnether:beacon_sync_clientbound");
    public static final StreamCodec<FriendlyByteBuf, BeaconSyncClientboundPacket> STREAM_CODEC =
        StreamCodec.ofMember(BeaconSyncClientboundPacket::encode, BeaconSyncClientboundPacket::new);

    @Override
    public CustomPacketPayload.Type<BeaconSyncClientboundPacket> type() {
        return TYPE;
    }

    private final UnitAction action;
    private final BlockPos beaconPos;
    private final boolean activate;

    public static void syncBeacon(UnitAction action, BlockPos beaconPos, boolean activate) {
        PacketDistributor.sendToAllPlayers(new BeaconSyncClientboundPacket(action, beaconPos, activate));
    }

    // packet-handler functions
    public BeaconSyncClientboundPacket(
        UnitAction action,
        BlockPos beaconPos,
        boolean activate
    ) {
        this.action = action;
        this.beaconPos = beaconPos;
        this.activate = activate;
    }

    public BeaconSyncClientboundPacket(FriendlyByteBuf buffer) {
        this.action = buffer.readEnum(UnitAction.class);
        this.beaconPos = buffer.readBlockPos();
        this.activate = buffer.readBoolean();
    }

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeEnum(this.action);
        buffer.writeBlockPos(this.beaconPos);
        buffer.writeBoolean(this.activate);
    }

    // server-side packet-consuming functions
    public void handle(IPayloadContext context) {
        context.enqueueWork(() -> {
            BuildingClientEvents.syncBeacon(
                this.action,
                this.beaconPos,
                this.activate
            );
        });
    }
}
