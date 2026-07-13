package com.solegendary.reignofnether.fogofwar;

import static com.solegendary.reignofnether.ReignOfNether.payloadType;

import net.neoforged.neoforge.network.PacketDistributor;

import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import com.solegendary.reignofnether.sounds.SoundClientEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.state.BlockState;


public class FrozenChunkServerboundPacket implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<FrozenChunkServerboundPacket> TYPE =
        payloadType("frozen_chunk_serverbound");
    public static final StreamCodec<FriendlyByteBuf, FrozenChunkServerboundPacket> STREAM_CODEC =
        StreamCodec.ofMember(FrozenChunkServerboundPacket::encode, FrozenChunkServerboundPacket::new);

    @Override
    public CustomPacketPayload.Type<FrozenChunkServerboundPacket> type() {
        return TYPE;
    }

    BlockPos renderChunkOrigin;

    public static void syncServerBlocks(BlockPos renderChunkOrigin) {
        Minecraft MC = Minecraft.getInstance();
        if (MC.level != null) {
            for (int x = 0; x < 16; x++) {
                for (int y = 0; y < 16; y++) {
                    for (int z = 0; z < 16; z++) {
                        BlockPos bp = renderChunkOrigin.offset(x,y,z);
                        BlockState bs = MC.level.getBlockState(bp);

                        if (bs.is(BlockTags.PORTALS) ||
                                bs.is(BlockTags.REPLACEABLE_BY_TREES) || bs.is(BlockTags.REPLACEABLE)) {
                            SoundClientEvents.mutedBps.add(bp);
                        }
                    }
                }
            }
        }
        if (MC.player != null)
            PacketDistributor.sendToServer(new FrozenChunkServerboundPacket(renderChunkOrigin));
    }

    // packet-handler functions
    public FrozenChunkServerboundPacket(BlockPos renderChunkOrigin) {
        this.renderChunkOrigin = renderChunkOrigin;
    }

    public FrozenChunkServerboundPacket(FriendlyByteBuf buffer) {
        this.renderChunkOrigin = buffer.readBlockPos();
    }

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeBlockPos(this.renderChunkOrigin);
    }

    // server-side packet-consuming functions
    public void handle(IPayloadContext context) {
        context.enqueueWork(() -> {
            FogOfWarServerEvents.syncClientBlocks(this.renderChunkOrigin);
        });
    }
}
