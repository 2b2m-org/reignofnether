package com.solegendary.reignofnether.unit.packets;

import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import com.solegendary.reignofnether.debug.RtsDebugPathPreview;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.pathfinder.Path;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;

// Sent server→client when a unit gets a fresh path. The client renders the path so the player
// can see the route their units will actually take. Gated by /rts-debug on the sender side.
public class UnitPathClientboundPacket implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<UnitPathClientboundPacket> TYPE =
        CustomPacketPayload.createType("reignofnether:unit_path_clientbound");
    public static final StreamCodec<FriendlyByteBuf, UnitPathClientboundPacket> STREAM_CODEC =
        StreamCodec.ofMember(UnitPathClientboundPacket::encode, UnitPathClientboundPacket::new);

    @Override
    public CustomPacketPayload.Type<UnitPathClientboundPacket> type() {
        return TYPE;
    }

    private final int entityId;
    private final byte pathType;
    private final List<BlockPos> nodes;

    public static void sendPath(LivingEntity entity, Path path, byte pathType) {
        if (path == null || path.getNodeCount() == 0)
            return;
        List<BlockPos> bps = new ArrayList<>(path.getNodeCount());
        for (int i = 0; i < path.getNodeCount(); i++)
            bps.add(path.getNode(i).asBlockPos());
        PacketDistributor.sendToAllPlayers(new UnitPathClientboundPacket(entity.getId(), pathType, bps));
    }

    public UnitPathClientboundPacket(int entityId, byte pathType, List<BlockPos> nodes) {
        this.entityId = entityId;
        this.pathType = pathType;
        this.nodes = nodes;
    }

    public UnitPathClientboundPacket(FriendlyByteBuf buffer) {
        this.entityId = buffer.readInt();
        this.pathType = buffer.readByte();
        int n = buffer.readVarInt();
        this.nodes = new ArrayList<>(n);
        for (int i = 0; i < n; i++)
            this.nodes.add(buffer.readBlockPos());
    }

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeInt(this.entityId);
        buffer.writeByte(this.pathType);
        buffer.writeVarInt(this.nodes.size());
        for (BlockPos bp : this.nodes)
            buffer.writeBlockPos(bp);
    }

    public void handle(IPayloadContext context) {
        context.enqueueWork(() -> {
            RtsDebugPathPreview.receiveUnitPath(this.entityId, this.pathType, this.nodes);
        });
    }
}
