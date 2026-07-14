package com.solegendary.reignofnether.player;

import static com.solegendary.reignofnether.ReignOfNether.payloadType;

import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import com.solegendary.reignofnether.ability.TradeAction;
import com.solegendary.reignofnether.bot.BotDifficulty;
import com.solegendary.reignofnether.bot.BotPersonality;
import com.solegendary.reignofnether.orthoview.OrthoviewClientEvents;
import com.solegendary.reignofnether.faction.Faction;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.neoforged.neoforge.network.PacketDistributor;


public class PlayerClientboundPacket implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<PlayerClientboundPacket> TYPE =
        payloadType("player_clientbound");
    public static final StreamCodec<FriendlyByteBuf, PlayerClientboundPacket> STREAM_CODEC =
        StreamCodec.ofMember(PlayerClientboundPacket::encode, PlayerClientboundPacket::new);

    @Override
    public CustomPacketPayload.Type<PlayerClientboundPacket> type() {
        return TYPE;
    }

    PlayerAction playerAction;
    String playerName;
    String displayName;
    boolean aiControlled;
    BotDifficulty aiDifficulty;
    BotPersonality aiPersonality;
    Long value1;
    int value2;
    Faction faction;
    TradeAction tradeAction; // for updating market rates
    BlockPos pos;

    public static void addRTSPlayer(RTSPlayer player) {
        PacketDistributor.sendToAllPlayers(new PlayerClientboundPacket(
                PlayerAction.ADD_RTS_PLAYER, player.name, player.displayName, player.aiControlled,
                player.aiDifficulty, player.aiPersonality,
                (long) player.id, player.startPosColorId, player.faction));
    }

    public static void addScenarioNPCRTSPlayer(RTSPlayer player) {
        PacketDistributor.sendToAllPlayers(new PlayerClientboundPacket(
                PlayerAction.ADD_SCENARIO_NPC_RTS_PLAYER, player.name, player.displayName, player.aiControlled,
                (long) player.id, player.scenarioRoleIndex, player.faction));
    }

    public static void removeRTSPlayer(String playerName) {
        PacketDistributor.sendToAllPlayers(new PlayerClientboundPacket(PlayerAction.REMOVE_RTS_PLAYER, playerName, 0L, 0, Faction.NONE));
    }

    public static void defeat(String playerName) {
        PacketDistributor.sendToAllPlayers(new PlayerClientboundPacket(PlayerAction.DEFEAT, playerName, 0L, 0, Faction.NONE));
    }

    public static void victory(String playerName) {
        PacketDistributor.sendToAllPlayers(new PlayerClientboundPacket(PlayerAction.VICTORY, playerName, 0L, 0, Faction.NONE));
    }

    public static void resetRTS(boolean hard) {
        if (hard) {
            PacketDistributor.sendToAllPlayers(new PlayerClientboundPacket(PlayerAction.RESET_RTS_HARD, "", 0L, 0, Faction.NONE));
        } else {
            PacketDistributor.sendToAllPlayers(new PlayerClientboundPacket(PlayerAction.RESET_RTS, "", 0L, 0, Faction.NONE));
        }
    }

    public static void publishScenarioMap() {
        PacketDistributor.sendToAllPlayers(new PlayerClientboundPacket(PlayerAction.PUBLISH_SCENARIO_MAP, "", 0L, 0, Faction.NONE));
    }

    public static void syncRtsGameTime(Long rtsGameTicks) {
        PacketDistributor.sendToAllPlayers(new PlayerClientboundPacket(PlayerAction.SYNC_RTS_GAME_TIME, "", rtsGameTicks, 0, Faction.NONE));
    }

    public static void lockRTS(String playerName) {
        PacketDistributor.sendToAllPlayers(new PlayerClientboundPacket(PlayerAction.LOCK_RTS, playerName, 0L, 0, Faction.NONE));
    }

    public static void unlockRTS(String playerName) {
        PacketDistributor.sendToAllPlayers(new PlayerClientboundPacket(PlayerAction.UNLOCK_RTS, playerName, 0L, 0, Faction.NONE));
    }

    // prevent one particular player from joining the match
    public static void disableStartRTS(String playerName) {
        PacketDistributor.sendToAllPlayers(new PlayerClientboundPacket(PlayerAction.DISABLE_START_RTS, playerName, 0L, 0, Faction.NONE));
    }
    public static void enableStartRTS(String playerName) {
        PacketDistributor.sendToAllPlayers(new PlayerClientboundPacket(PlayerAction.ENABLE_START_RTS, playerName, 0L, 0, Faction.NONE));
    }

    public static void syncBeaconOwnerTicks(String playerName, long ticks) {
        PacketDistributor.sendToAllPlayers(new PlayerClientboundPacket(PlayerAction.SYNC_BEACON_OWNER_TICKS, playerName, ticks, 0, Faction.NONE));
    }

    public static void setRTSCamera(String playerName, boolean value) {
        PacketDistributor.sendToAllPlayers(new PlayerClientboundPacket(PlayerAction.SET_RTS_CAMERA, playerName, (long) (value ? 1 : 0), 0, Faction.NONE));
    }

    public static void setMarketRate(TradeAction tradeAction, String playerName, int value) {
        PacketDistributor.sendToAllPlayers(new PlayerClientboundPacket(tradeAction, playerName, (long) value));
    }

    public static void teleport(String playerName, BlockPos pos) {
        PacketDistributor.sendToAllPlayers(new PlayerClientboundPacket(PlayerAction.TELEPORT, playerName, pos));
    }

    public PlayerClientboundPacket(PlayerAction playerAction, String playerName, BlockPos pos) {
        this.playerAction = playerAction;
        this.playerName = playerName;
        this.displayName = playerName;
        this.aiControlled = false;
        this.aiDifficulty = BotDifficulty.MEDIUM;
        this.aiPersonality = BotPersonality.STEADY;
        this.value1 = 0L;
        this.value2 = 0;
        this.faction = Faction.NONE;
        this.tradeAction = TradeAction.FOOD_FOR_WOOD; // dummy value
        this.pos = pos;
    }

    public PlayerClientboundPacket(PlayerAction playerAction, String playerName, Long value1, int value2, Faction faction) {
        this(playerAction, playerName, playerName, false, value1, value2, faction);
    }

    private PlayerClientboundPacket(PlayerAction playerAction, String playerName, String displayName,
                                    boolean aiControlled, Long value1, int value2, Faction faction) {
        this(playerAction, playerName, displayName, aiControlled,
                BotDifficulty.MEDIUM, BotPersonality.STEADY, value1, value2, faction);
    }

    private PlayerClientboundPacket(PlayerAction playerAction, String playerName, String displayName,
                                    boolean aiControlled, BotDifficulty aiDifficulty,
                                    BotPersonality aiPersonality,
                                    Long value1, int value2, Faction faction) {
        this.playerAction = playerAction;
        this.playerName = playerName;
        this.displayName = displayName;
        this.aiControlled = aiControlled;
        this.aiDifficulty = aiDifficulty;
        this.aiPersonality = aiPersonality;
        this.value1 = value1;
        this.value2 = value2;
        this.faction = faction;
        this.tradeAction = TradeAction.FOOD_FOR_WOOD; // dummy value
        this.pos = new BlockPos(0,0,0);
    }

    public PlayerClientboundPacket(TradeAction tradeAction, String playerName, Long value1) {
        this.playerAction = PlayerAction.SET_MARKET_RATE;
        this.playerName = playerName;
        this.displayName = playerName;
        this.aiControlled = false;
        this.aiDifficulty = BotDifficulty.MEDIUM;
        this.aiPersonality = BotPersonality.STEADY;
        this.value1 = value1;
        this.value2 = 0;
        this.faction = Faction.NONE;
        this.tradeAction = tradeAction;
        this.pos = new BlockPos(0,0,0);
    }

    public PlayerClientboundPacket(FriendlyByteBuf buffer) {
        this.playerAction = buffer.readEnum(PlayerAction.class);
        this.playerName = buffer.readUtf();
        this.displayName = buffer.readUtf();
        this.aiControlled = buffer.readBoolean();
        this.aiDifficulty = buffer.readEnum(BotDifficulty.class);
        this.aiPersonality = buffer.readEnum(BotPersonality.class);
        this.value1 = buffer.readLong();
        this.value2 = buffer.readInt();
        this.faction = buffer.readEnum(Faction.class);
        this.tradeAction = buffer.readEnum(TradeAction.class);
        this.pos = buffer.readBlockPos();
    }

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeEnum(this.playerAction);
        buffer.writeUtf(this.playerName);
        buffer.writeUtf(this.displayName);
        buffer.writeBoolean(this.aiControlled);
        buffer.writeEnum(this.aiDifficulty);
        buffer.writeEnum(this.aiPersonality);
        buffer.writeLong(this.value1);
        buffer.writeInt(this.value2);
        buffer.writeEnum(this.faction);
        buffer.writeEnum(this.tradeAction);
        buffer.writeBlockPos(this.pos);
    }

    // server-side packet-consuming functions
    public void handle(IPayloadContext context) {

        context.enqueueWork(() -> {
            {
                        switch (playerAction) {
                            case TELEPORT -> OrthoviewClientEvents.centreCameraOnPosForPlayer(playerName, pos);
                            case DEFEAT -> PlayerClientEvents.defeat(playerName);
                            case VICTORY -> PlayerClientEvents.victory(playerName);
                            case ADD_RTS_PLAYER -> PlayerClientEvents.addRTSPlayer(
                                    playerName, displayName, aiControlled, aiDifficulty, aiPersonality,
                                    faction, value1, value2);
                            case ADD_SCENARIO_NPC_RTS_PLAYER -> PlayerClientEvents.addScenarioNPCRTSPlayer(
                                    playerName, displayName, faction, value1, value2);
                            case REMOVE_RTS_PLAYER -> PlayerClientEvents.removeRTSPlayer(playerName);
                            case RESET_RTS -> PlayerClientEvents.resetRTS(false);
                            case RESET_RTS_HARD -> PlayerClientEvents.resetRTS(true);
                            case PUBLISH_SCENARIO_MAP -> PlayerClientEvents.publishScenarioMap();
                            case SYNC_RTS_GAME_TIME -> PlayerClientEvents.syncRtsGameTime(value1);
                            case LOCK_RTS -> PlayerClientEvents.setRTSLock(true);
                            case UNLOCK_RTS -> PlayerClientEvents.setRTSLock(false);
                            case ENABLE_START_RTS -> PlayerClientEvents.setCanStartRTS(true);
                            case DISABLE_START_RTS -> PlayerClientEvents.setCanStartRTS(false);
                            case SYNC_BEACON_OWNER_TICKS -> PlayerClientEvents.syncBeaconOwnerTicks(playerName, value1);
                            case SET_RTS_CAMERA -> OrthoviewClientEvents.tryToSetCamera(playerName, value1 == 1L);
                            case SET_MARKET_RATE -> PlayerClientEvents.setMarketRate(tradeAction, playerName, Math.toIntExact(value1));
                        }
                    }
        });
    }
}
