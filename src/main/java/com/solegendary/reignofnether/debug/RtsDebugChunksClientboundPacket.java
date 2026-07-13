package com.solegendary.reignofnether.debug;

import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import net.minecraft.network.FriendlyByteBuf;
import net.neoforged.neoforge.network.PacketDistributor;


// Server -> client: the set of currently-built walkability (navmesh) chunk keys, so the debug overlay can show
// which chunks have a built mesh. Sent once per second alongside the perf stats.
public class RtsDebugChunksClientboundPacket implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<RtsDebugChunksClientboundPacket> TYPE =
        CustomPacketPayload.createType("reignofnether:rts_debug_chunks_clientbound");
    public static final StreamCodec<FriendlyByteBuf, RtsDebugChunksClientboundPacket> STREAM_CODEC =
        StreamCodec.ofMember(RtsDebugChunksClientboundPacket::encode, RtsDebugChunksClientboundPacket::new);

    @Override
    public CustomPacketPayload.Type<RtsDebugChunksClientboundPacket> type() {
        return TYPE;
    }

    private final long[] keys;

    public static void broadcast(long[] keys) {
        PacketDistributor.sendToAllPlayers(new RtsDebugChunksClientboundPacket(keys));
    }

    public RtsDebugChunksClientboundPacket(long[] keys) {
        this.keys = keys;
    }

    public RtsDebugChunksClientboundPacket(FriendlyByteBuf buffer) {
        int n = buffer.readVarInt();
        this.keys = new long[n];
        for (int i = 0; i < n; i++) this.keys[i] = buffer.readLong();
    }

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeVarInt(this.keys.length);
        for (long k : this.keys) buffer.writeLong(k);
    }

    public void handle(IPayloadContext context) {
        context.enqueueWork(() ->
            RtsDebugNavmesh.setBuiltChunks(this.keys));
    }
}
