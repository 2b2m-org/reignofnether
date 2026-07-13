package com.solegendary.reignofnether.startpos;

import static com.solegendary.reignofnether.ReignOfNether.payloadType;

import net.neoforged.neoforge.network.PacketDistributor;

import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import com.solegendary.reignofnether.ReignOfNether;
import com.solegendary.reignofnether.faction.Faction;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;


public class StartPosServerboundPacket implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<StartPosServerboundPacket> TYPE =
        payloadType("start_pos_serverbound");
    public static final StreamCodec<FriendlyByteBuf, StartPosServerboundPacket> STREAM_CODEC =
        StreamCodec.ofMember(StartPosServerboundPacket::encode, StartPosServerboundPacket::new);

    @Override
    public CustomPacketPayload.Type<StartPosServerboundPacket> type() {
        return TYPE;
    }

    StartPosAction action;
    BlockPos blockPos;
    Faction faction;

    public static void reservePos(BlockPos pos, Faction faction) {
        PacketDistributor.sendToServer(new StartPosServerboundPacket(StartPosAction.RESERVE, pos, faction));
    }

    public static void unreservePos(BlockPos pos) {
        PacketDistributor.sendToServer(new StartPosServerboundPacket(StartPosAction.UNRESERVE, pos, Faction.NONE));
    }

    public static void readyPlayer() {
        PacketDistributor.sendToServer(new StartPosServerboundPacket(StartPosAction.PLAYER_READY, BlockPos.ZERO, Faction.NONE));
    }

    public static void unreadyPlayer() {
        PacketDistributor.sendToServer(new StartPosServerboundPacket(StartPosAction.PLAYER_UNREADY, BlockPos.ZERO, Faction.NONE));
    }

    public static void enablePos(BlockPos pos) {
        PacketDistributor.sendToServer(new StartPosServerboundPacket(StartPosAction.ENABLE, pos, Faction.NONE));
    }

    public static void disablePos(BlockPos pos) {
        PacketDistributor.sendToServer(new StartPosServerboundPacket(StartPosAction.DISABLE, pos, Faction.NONE));
    }

    public StartPosServerboundPacket(StartPosAction action, BlockPos pos, Faction faction) {
        this.action = action;
        this.blockPos = pos;
        this.faction = faction;
    }

    public StartPosServerboundPacket(FriendlyByteBuf buffer) {
        this.action = buffer.readEnum(StartPosAction.class);
        this.blockPos = buffer.readBlockPos();
        this.faction = buffer.readEnum(Faction.class);
    }

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeEnum(this.action);
        buffer.writeBlockPos(this.blockPos);
        buffer.writeEnum(this.faction);
    }

    // server-side packet-consuming functions
    public void handle(IPayloadContext context) {
        context.enqueueWork(() -> {

            ServerPlayer player = (ServerPlayer) context.player();
            if (player == null) {
                ReignOfNether.LOGGER.warn("StartPosServerboundPacket: Sender was null");
                return;
            }
            else if ((action == StartPosAction.ENABLE || action == StartPosAction.DISABLE) &&
                    !player.hasPermissions(4)) {
                ReignOfNether.LOGGER.warn("StartPosServerboundPacket: Tried to process packet from " + player.getName() + " with insufficient permissions");
                return;
            }

            String playerName = player.getGameProfile().getName();
            switch (action) {
                case RESERVE -> reserve(playerName);
                case UNRESERVE -> unreserve(playerName);
                case PLAYER_READY -> StartPosServerEvents.setPlayerReady(playerName, true);
                case PLAYER_UNREADY -> StartPosServerEvents.setPlayerReady(playerName, false);
                case ENABLE -> StartPosServerEvents.setPosEnabled(blockPos, true);
                case DISABLE -> StartPosServerEvents.setPosEnabled(blockPos, false);
            }
        });
    }

    private void reserve(String playerName) {
        if (StartPosServerEvents.isStartingGame() || !StartPosServerEvents.isReservableFaction(faction))
            return;

        StartPos requestedPos = null;
        for (StartPos startPos : StartPosServerEvents.startPoses) {
            if (startPos.pos.equals(blockPos)) {
                requestedPos = startPos;
                break;
            }
        }
        if (requestedPos == null || !requestedPos.enabled ||
                (!requestedPos.playerName.isBlank() && !requestedPos.playerName.equals(playerName)))
            return;

        for (StartPos startPos : StartPosServerEvents.startPoses) {
            if (startPos != requestedPos && startPos.playerName.equals(playerName))
                startPos.reset();
        }
        requestedPos.reset();
        requestedPos.faction = faction;
        requestedPos.playerName = playerName;
        StartPosClientboundPacket.reservePos(blockPos, faction, playerName);
    }

    private void unreserve(String playerName) {
        if (StartPosServerEvents.isStartingGame())
            return;

        for (StartPos startPos : StartPosServerEvents.startPoses) {
            if (startPos.pos.equals(blockPos) && startPos.playerName.equals(playerName)) {
                startPos.reset();
                StartPosClientboundPacket.unreservePos(blockPos);
                return;
            }
        }
    }
}
