package com.solegendary.reignofnether.fogofwar;

import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.neoforged.neoforge.network.PacketDistributor;


public class FrozenChunkClientboundPacket implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<FrozenChunkClientboundPacket> TYPE =
        CustomPacketPayload.createType("reignofnether:frozen_chunk_clientbound");
    public static final StreamCodec<FriendlyByteBuf, FrozenChunkClientboundPacket> STREAM_CODEC =
        StreamCodec.ofMember(FrozenChunkClientboundPacket::encode, FrozenChunkClientboundPacket::new);

    @Override
    public CustomPacketPayload.Type<FrozenChunkClientboundPacket> type() {
        return TYPE;
    }

    FrozenChunkAction action;
    BlockPos blockPos;

    public static void setBuildingDestroyedServerside(BlockPos buildingOrigin) {
        PacketDistributor.sendToAllPlayers(new FrozenChunkClientboundPacket(FrozenChunkAction.SET_BUILDING_DESTROYED, buildingOrigin));
    }

    public static void setBuildingBuiltServerside(BlockPos buildingOrigin) {
        PacketDistributor.sendToAllPlayers(new FrozenChunkClientboundPacket(FrozenChunkAction.SET_BUILDING_BUILT, buildingOrigin));
    }

    public static void unmuteChunks() {
        PacketDistributor.sendToAllPlayers(new FrozenChunkClientboundPacket(FrozenChunkAction.UNMUTE, new BlockPos(0,0,0)));
    }

    // packet-handler functions
    public FrozenChunkClientboundPacket(FrozenChunkAction action, BlockPos blockPos) {
        this.action = action;
        this.blockPos = blockPos;
    }

    public FrozenChunkClientboundPacket(FriendlyByteBuf buffer) {
        this.action = buffer.readEnum(FrozenChunkAction.class);
        this.blockPos = buffer.readBlockPos();
    }

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeEnum(this.action);
        buffer.writeBlockPos(this.blockPos);
    }

    // client-side packet-consuming functions
    public void handle(IPayloadContext context) {

        context.enqueueWork(() -> {
            {
                        switch (action) {
                            case SET_BUILDING_DESTROYED -> FogOfWarClientEvents.setBuildingDestroyedServerside(blockPos);
                            case SET_BUILDING_BUILT -> FogOfWarClientEvents.setBuildingBuiltServerside(blockPos);
                            case UNMUTE -> FogOfWarClientEvents.unmuteChunks();
                        }
                    }
        });
    }
}