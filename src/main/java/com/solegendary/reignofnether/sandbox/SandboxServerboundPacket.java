package com.solegendary.reignofnether.sandbox;

import static com.solegendary.reignofnether.ReignOfNether.payloadType;

import net.neoforged.neoforge.network.PacketDistributor;

import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import com.solegendary.reignofnether.ReignOfNether;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;


public class SandboxServerboundPacket implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<SandboxServerboundPacket> TYPE =
        payloadType("sandbox_serverbound");
    public static final StreamCodec<FriendlyByteBuf, SandboxServerboundPacket> STREAM_CODEC =
        StreamCodec.ofMember(SandboxServerboundPacket::encode, SandboxServerboundPacket::new);

    @Override
    public CustomPacketPayload.Type<SandboxServerboundPacket> type() {
        return TYPE;
    }

    public SandboxAction sandboxAction;
    public String playerName;
    public String unitName;
    public BlockPos blockPos;
    public int[] entityIds;

    public static void spawnUnit(SandboxAction sandboxAction, String playerName, String unitName, BlockPos blockPos) {
        if (!unitName.isBlank())
            PacketDistributor.sendToServer(new SandboxServerboundPacket(sandboxAction, playerName, unitName, blockPos,  new int[]{}));
    }
    public static void setAnchor(BlockPos blockPos, int[] entityIds) {
        PacketDistributor.sendToServer(new SandboxServerboundPacket(SandboxAction.SET_ANCHOR, "", "", blockPos, entityIds));
    }
    public static void resetToAnchor(int[] entityIds) {
        PacketDistributor.sendToServer(new SandboxServerboundPacket(SandboxAction.RESET_TO_ANCHOR, "", "", new BlockPos(0,0,0), entityIds));
    }
    public static void removeAnchor(int[] entityIds) {
        PacketDistributor.sendToServer(new SandboxServerboundPacket(SandboxAction.REMOVE_ANCHOR, "", "", new BlockPos(0,0,0), entityIds));
    }
    public static void setUnitOwner(int[] entityIds, String ownerName) {
        PacketDistributor.sendToServer(new SandboxServerboundPacket(SandboxAction.SET_UNIT_OWNER, ownerName, "", new BlockPos(0,0,0), entityIds));
    }
    public static void setBuildingOwner(BlockPos pos, String ownerName) {
        PacketDistributor.sendToServer(new SandboxServerboundPacket(SandboxAction.SET_BUILDING_OWNER, ownerName, "", pos,  new int[]{}));
    }
    public static void removeBuilding(BlockPos pos) {
        PacketDistributor.sendToServer(new SandboxServerboundPacket(SandboxAction.REMOVE_BUILDING, "", "", pos, new int[]{}));
    }

    public SandboxServerboundPacket(SandboxAction sandboxAction, String playerName, String unitName, BlockPos blockPos, int[] entityIds) {
        this.sandboxAction = sandboxAction;
        this.playerName = playerName;
        this.unitName = unitName;
        this.blockPos = blockPos;
        this.entityIds = entityIds;
    }

    public SandboxServerboundPacket(FriendlyByteBuf buffer) {
        this.sandboxAction = buffer.readEnum(SandboxAction.class);
        this.playerName = buffer.readUtf();
        this.unitName = buffer.readUtf();
        this.blockPos = buffer.readBlockPos();
        this.entityIds = buffer.readVarIntArray();
    }

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeEnum(this.sandboxAction);
        buffer.writeUtf(this.playerName);
        buffer.writeUtf(this.unitName);
        buffer.writeBlockPos(this.blockPos);
        buffer.writeVarIntArray(this.entityIds);
    }

    // server-side packet-consuming functions
    public void handle(IPayloadContext context) {
        context.enqueueWork(() -> {

            ServerPlayer player = (ServerPlayer) context.player();
            if (player == null) {
                ReignOfNether.LOGGER.warn("SandboxServerboundPacket: Sender was null");
                return;
            }
            else if (!SandboxServer.isAnyoneASandboxPlayer()) {
                ReignOfNether.LOGGER.warn("SandboxServerboundPacket: Tried to process packet from " + player.getName() + " while sandbox is disabled");
                return;
            }

            switch (sandboxAction) {
                case SPAWN_UNIT -> SandboxServer.spawnUnit(this.playerName, this.unitName, this.blockPos);
                case SET_ANCHOR -> SandboxServer.setAnchor(this.entityIds, this.blockPos);
                case RESET_TO_ANCHOR -> SandboxServer.resetToAnchor(this.entityIds);
                case REMOVE_ANCHOR -> SandboxServer.removeAnchor(this.entityIds);
                case SET_UNIT_OWNER -> SandboxServer.setUnitOwner(this.entityIds, this.playerName);
                case SET_BUILDING_OWNER -> SandboxServer.setBuildingOwner(this.blockPos, this.playerName);
                case REMOVE_BUILDING -> SandboxServer.removeBuilding(this.blockPos);
            }
        });
    }
}
