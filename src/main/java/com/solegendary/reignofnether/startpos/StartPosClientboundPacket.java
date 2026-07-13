package com.solegendary.reignofnether.startpos;

import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import com.solegendary.reignofnether.faction.Faction;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.neoforged.neoforge.network.PacketDistributor;


public class StartPosClientboundPacket implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<StartPosClientboundPacket> TYPE =
        CustomPacketPayload.createType("reignofnether:start_pos_clientbound");
    public static final StreamCodec<FriendlyByteBuf, StartPosClientboundPacket> STREAM_CODEC =
        StreamCodec.ofMember(StartPosClientboundPacket::encode, StartPosClientboundPacket::new);

    @Override
    public CustomPacketPayload.Type<StartPosClientboundPacket> type() {
        return TYPE;
    }

    StartPosAction action;
    Faction faction;
    BlockPos blockPos;
    String playerName;
    int colorId;

    public static void addPos(StartPos startPos) {
        PacketDistributor.sendToAllPlayers(new StartPosClientboundPacket(StartPosAction.ADD, startPos.pos, startPos.faction, startPos.playerName, startPos.colorId));
    }

    public static void addDisabledPos(StartPos startPos) {
        PacketDistributor.sendToAllPlayers(new StartPosClientboundPacket(StartPosAction.ADD_DISABLED, startPos.pos, startPos.faction, startPos.playerName, startPos.colorId));
    }

    public static void removePos(BlockPos pos) {
        PacketDistributor.sendToAllPlayers(new StartPosClientboundPacket(StartPosAction.REMOVE, pos, Faction.NONE, "", 0));
    }

    public static void reservePos(BlockPos pos, Faction faction, String playerName) {
        PacketDistributor.sendToAllPlayers(new StartPosClientboundPacket(StartPosAction.RESERVE, pos, faction, playerName, 0));
    }

    public static void unreservePos(BlockPos pos) {
        PacketDistributor.sendToAllPlayers(new StartPosClientboundPacket(StartPosAction.UNRESERVE, pos, Faction.NONE, "", 0));
    }

    public static void reset() {
        PacketDistributor.sendToAllPlayers(new StartPosClientboundPacket(StartPosAction.RESET, new BlockPos(0,0,0), Faction.NONE, "", 0));
    }

    public static void startGameCountdown() {
        PacketDistributor.sendToAllPlayers(new StartPosClientboundPacket(StartPosAction.SET_GAME_STARTING, new BlockPos(0,0,0), Faction.NONE, "", 0));
    }

    public static void cancelStartGameCountdown() {
        PacketDistributor.sendToAllPlayers(new StartPosClientboundPacket(StartPosAction.UNSET_GAME_STARTING, new BlockPos(0,0,0), Faction.NONE, "", 0));
    }

    public static void readyPlayer(String playerName) {
        PacketDistributor.sendToAllPlayers(new StartPosClientboundPacket(StartPosAction.PLAYER_READY, new BlockPos(0,0,0), Faction.NONE, playerName, 0));
    }

    public static void unreadyPlayer(String playerName) {
        PacketDistributor.sendToAllPlayers(new StartPosClientboundPacket(StartPosAction.PLAYER_UNREADY, new BlockPos(0,0,0), Faction.NONE, playerName, 0));
    }

    public static void enablePos(BlockPos pos) {
        PacketDistributor.sendToAllPlayers(new StartPosClientboundPacket(StartPosAction.ENABLE, pos, Faction.NONE, "", 0));
    }

    public static void disablePos(BlockPos pos) {
        PacketDistributor.sendToAllPlayers(new StartPosClientboundPacket(StartPosAction.DISABLE, pos, Faction.NONE, "", 0));
    }

    public StartPosClientboundPacket(StartPosAction action, BlockPos blockPos, Faction faction, String playerName, int colorId) {
        this.action = action;
        this.blockPos = blockPos;
        this.faction = faction;
        this.playerName = playerName;
        this.colorId = colorId;
    }

    public StartPosClientboundPacket(FriendlyByteBuf buffer) {
        this.action = buffer.readEnum(StartPosAction.class);
        this.blockPos = buffer.readBlockPos();
        this.faction = buffer.readEnum(Faction.class);
        this.playerName = buffer.readUtf();
        this.colorId = buffer.readInt();
    }

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeEnum(this.action);
        buffer.writeBlockPos(this.blockPos);
        buffer.writeEnum(this.faction);
        buffer.writeUtf(this.playerName);
        buffer.writeInt(this.colorId);
    }

    // server-side packet-consuming functions
    public void handle(IPayloadContext context) {

        context.enqueueWork(() -> {
            {
                        switch (action) {
                            case ADD -> {
                                StartPosClientEvents.startPoses.removeIf(sp -> sp.pos.equals(blockPos));
                                StartPosClientEvents.startPoses.add(new StartPos(blockPos, faction, playerName, colorId));
                            }
                            case ADD_DISABLED -> {
                                StartPosClientEvents.startPoses.removeIf(sp -> sp.pos.equals(blockPos));
                                StartPos pos = new StartPos(blockPos, faction, playerName, colorId);
                                pos.enabled = false;
                                StartPosClientEvents.startPoses.add(pos);
                            }
                            case REMOVE -> {
                                StartPosClientEvents.startPoses.removeIf(sp -> sp.pos.equals(blockPos));
                            }
                            case RESERVE -> {
                                for (StartPos startPos : StartPosClientEvents.startPoses) {
                                    if (startPos.pos.equals(blockPos)) {
                                        startPos.reset();
                                        startPos.faction = faction;
                                        startPos.playerName = playerName;
                                    } else if (startPos.playerName.equals(playerName)) {
                                        startPos.reset();
                                    }
                                }
                            }
                            case UNRESERVE -> {
                                for (StartPos startPos : StartPosClientEvents.startPoses) {
                                    if (startPos.pos.equals(blockPos)) {
                                        startPos.reset();
                                        break;
                                    }
                                }
                            }
                            case RESET -> StartPosClientEvents.resetAll();
                            case SET_GAME_STARTING -> StartPosClientEvents.isStarting = true;
                            case UNSET_GAME_STARTING -> StartPosClientEvents.isStarting = false;
                            case PLAYER_READY -> StartPosClientEvents.setPlayerReady(playerName, true);
                            case PLAYER_UNREADY -> StartPosClientEvents.setPlayerReady(playerName, false);
                            case ENABLE -> StartPosClientEvents.setPosEnabled(blockPos, true);
                            case DISABLE -> StartPosClientEvents.setPosEnabled(blockPos, false);
                        }
                    }
        });
    }
}
