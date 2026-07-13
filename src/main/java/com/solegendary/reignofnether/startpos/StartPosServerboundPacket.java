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
    String playerName;

    public static void reservePos(BlockPos pos, Faction faction, String playerName) {
        PacketDistributor.sendToServer(new StartPosServerboundPacket(StartPosAction.RESERVE, pos, faction, playerName));
    }

    public static void unreservePos(BlockPos pos) {
        PacketDistributor.sendToServer(new StartPosServerboundPacket(StartPosAction.UNRESERVE, pos, Faction.NONE, ""));
    }

    public static void readyPlayer(String playerName) {
        PacketDistributor.sendToServer(new StartPosServerboundPacket(StartPosAction.PLAYER_READY, new BlockPos(0,0,0), Faction.NONE, playerName));
    }

    public static void unreadyPlayer(String playerName) {
        PacketDistributor.sendToServer(new StartPosServerboundPacket(StartPosAction.PLAYER_UNREADY, new BlockPos(0,0,0), Faction.NONE, playerName));
    }

    public static void enablePos(BlockPos pos) {
        PacketDistributor.sendToServer(new StartPosServerboundPacket(StartPosAction.ENABLE, pos, Faction.NONE, ""));
    }

    public static void disablePos(BlockPos pos) {
        PacketDistributor.sendToServer(new StartPosServerboundPacket(StartPosAction.DISABLE, pos, Faction.NONE, ""));
    }

    public StartPosServerboundPacket(StartPosAction action, BlockPos pos, Faction faction, String playerName) {
        this.action = action;
        this.blockPos = pos;
        this.faction = faction;
        this.playerName = playerName;
    }

    public StartPosServerboundPacket(FriendlyByteBuf buffer) {
        this.action = buffer.readEnum(StartPosAction.class);
        this.blockPos = buffer.readBlockPos();
        this.faction = buffer.readEnum(Faction.class);
        this.playerName = buffer.readUtf();
    }

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeEnum(this.action);
        buffer.writeBlockPos(this.blockPos);
        buffer.writeEnum(this.faction);
        buffer.writeUtf(this.playerName);
    }

    // server-side packet-consuming functions
    public void handle(IPayloadContext context) {
        context.enqueueWork(() -> {

            ServerPlayer player = (ServerPlayer) context.player();
            if (player == null) {
                ReignOfNether.LOGGER.warn("GameruleServerboundPacket: Sender was null");
                return;
            }
            else if ((action == StartPosAction.ENABLE || action == StartPosAction.DISABLE) &&
                    !player.hasPermissions(4)) {
                ReignOfNether.LOGGER.warn("GameruleServerboundPacket: Tried to process packet from " + player.getName() + " with insufficient permissions");
                return;
            }

            switch (action) {
                case RESERVE -> {
                    if (StartPosServerEvents.isStartingGame())
                        return;
                    for (StartPos startPos : StartPosServerEvents.startPoses) {
                        if (startPos.pos.equals(blockPos) && startPos.enabled) {
                            startPos.reset();
                            startPos.faction = faction;
                            startPos.playerName = playerName;
                            StartPosClientboundPacket.reservePos(blockPos, faction, playerName);
                        } else if (startPos.playerName.equals(playerName)) {
                            startPos.reset();
                        }
                    }
                    StartPosServerEvents.setPlayerReady(playerName, false);
                }
                case UNRESERVE -> {
                    if (StartPosServerEvents.isStartingGame())
                        return;
                    for (StartPos startPos : StartPosServerEvents.startPoses) {
                        if (startPos.pos.equals(blockPos)) {
                            startPos.reset();
                            StartPosClientboundPacket.unreservePos(blockPos);
                            break;
                        }
                    }
                    StartPosServerEvents.setPlayerReady(playerName, false);
                }
                case PLAYER_READY -> StartPosServerEvents.setPlayerReady(playerName, true);
                case PLAYER_UNREADY -> StartPosServerEvents.setPlayerReady(playerName, false);
                case ENABLE -> StartPosServerEvents.setPosEnabled(blockPos, true);
                case DISABLE -> StartPosServerEvents.setPosEnabled(blockPos, false);
            }
        });
    }
}