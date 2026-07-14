package com.solegendary.reignofnether.startpos;

import static com.solegendary.reignofnether.ReignOfNether.payloadType;

import com.solegendary.reignofnether.faction.Faction;
import io.netty.handler.codec.DecoderException;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;
import java.util.List;

public class StartPosClientboundPacket implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<StartPosClientboundPacket> TYPE =
            payloadType("start_pos_clientbound");
    public static final StreamCodec<FriendlyByteBuf, StartPosClientboundPacket> STREAM_CODEC =
            StreamCodec.ofMember(StartPosClientboundPacket::encode, StartPosClientboundPacket::new);

    private final List<PositionState> positions;
    private final int countdownTicks;
    private final boolean announceReadyChanges;

    public static void syncAll() {
        syncAll(false);
    }

    public static void syncAll(boolean announceReadyChanges) {
        PacketDistributor.sendToAllPlayers(snapshot(announceReadyChanges));
    }

    public static void syncToPlayer(ServerPlayer player) {
        PacketDistributor.sendToPlayer(player, snapshot(false));
    }

    private static StartPosClientboundPacket snapshot(boolean announceReadyChanges) {
        List<PositionState> positions = StartPosServerEvents.startPoses.stream()
                .map(PositionState::new)
                .toList();
        return new StartPosClientboundPacket(
                positions,
                StartPosServerEvents.getCountdownTicks(),
                announceReadyChanges
        );
    }

    private StartPosClientboundPacket(List<PositionState> positions, int countdownTicks,
                                      boolean announceReadyChanges) {
        this.positions = List.copyOf(positions);
        this.countdownTicks = countdownTicks;
        this.announceReadyChanges = announceReadyChanges;
    }

    private StartPosClientboundPacket(FriendlyByteBuf buffer) {
        int size = buffer.readVarInt();
        if (size < 0)
            throw new DecoderException("Negative start position count: " + size);
        List<PositionState> decoded = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            decoded.add(new PositionState(
                    buffer.readBlockPos(),
                    buffer.readEnum(Faction.class),
                    buffer.readUtf(32),
                    buffer.readInt(),
                    buffer.readBoolean(),
                    buffer.readBoolean()
            ));
        }
        positions = List.copyOf(decoded);
        countdownTicks = buffer.readInt();
        announceReadyChanges = buffer.readBoolean();
    }

    private void encode(FriendlyByteBuf buffer) {
        buffer.writeVarInt(positions.size());
        for (PositionState position : positions) {
            buffer.writeBlockPos(position.pos());
            buffer.writeEnum(position.faction());
            buffer.writeUtf(position.playerName(), 32);
            buffer.writeInt(position.colorId());
            buffer.writeBoolean(position.enabled());
            buffer.writeBoolean(position.ready());
        }
        buffer.writeInt(countdownTicks);
        buffer.writeBoolean(announceReadyChanges);
    }

    @Override
    public CustomPacketPayload.Type<StartPosClientboundPacket> type() {
        return TYPE;
    }

    public void handle(IPayloadContext context) {
        context.enqueueWork(() -> StartPosClientEvents.applySnapshot(
                positions,
                countdownTicks,
                announceReadyChanges
        ));
    }

    record PositionState(BlockPos pos, Faction faction, String playerName, int colorId,
                         boolean enabled, boolean ready) {
        private PositionState(StartPos startPos) {
            this(
                    startPos.pos,
                    startPos.faction,
                    startPos.playerName,
                    startPos.colorId,
                    startPos.enabled,
                    startPos.ready
            );
        }
    }
}
