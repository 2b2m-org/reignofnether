package com.solegendary.reignofnether.startpos;

import com.solegendary.reignofnether.ReignOfNether;
import com.solegendary.reignofnether.alliance.AlliancesServerEvents;
import com.solegendary.reignofnether.blocks.RTSStartBlock;
import com.solegendary.reignofnether.player.PlayerColors;
import com.solegendary.reignofnether.player.PlayerServerEvents;
import com.solegendary.reignofnether.rtsmap.RTSMapInfo;
import com.solegendary.reignofnether.rtsmap.RTSMapInfoServerEvents;
import com.solegendary.reignofnether.sounds.SoundAction;
import com.solegendary.reignofnether.sounds.SoundClientboundPacket;
import com.solegendary.reignofnether.faction.Faction;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.bus.api.SubscribeEvent;

import java.util.ArrayList;
import java.util.List;

// manages start block and readied start (startRTSEveryone) actions

public class StartPosServerEvents {

    public static final int MAX_START_POSES = 16;

    public static ArrayList<StartPos> startPoses = new ArrayList<>();

    private static int TICKS_TO_START_MAX = 100;
    private static int ticksToStart = TICKS_TO_START_MAX;
    private static boolean startingGame = false;

    private static int cullTicksMax = 100;
    private static int cullTicks = 0;

    public static boolean isStartingGame() {
        return startingGame;
    }

    static int getCountdownTicks() {
        return startingGame ? ticksToStart : -1;
    }

    public static void reset(ServerLevel serverLevel) {
        for (StartPos startPos : startPoses) {
            startPos.reset();
        }
        savePositions(serverLevel);
    }

    public static void setPlayerReady(String playerName, boolean ready) {
        StartPos playerPos = null;
        for (StartPos startPos : startPoses) {
            if (startPos.enabled && startPos.playerName.equals(playerName)) {
                playerPos = startPos;
                break;
            }
        }
        if (playerPos == null || (ready && !isPlayableFaction(playerPos.faction)))
            return;

        playerPos.ready = ready;
        if (canStartGame()) {
            startGameCountdown();
        } else if (startingGame) {
            cancelStartGameCountdown(false);
        } else {
            StartPosClientboundPacket.syncAll(true);
        }
    }

    static boolean isReservableFaction(Faction faction) {
        return faction == Faction.NONE || isPlayableFaction(faction);
    }

    private static boolean isPlayableFaction(Faction faction) {
        return faction == Faction.VILLAGERS || faction == Faction.MONSTERS ||
                faction == Faction.PIGLINS || faction == Faction.RANDOM;
    }

    private static boolean canStartGame() {
        boolean hasEnabledPos = false;
        for (StartPos startPos : startPoses) {
            if (!startPos.enabled)
                continue;
            hasEnabledPos = true;
            if (startPos.playerName.isBlank() || !startPos.ready || !isPlayableFaction(startPos.faction))
                return false;
        }
        return hasEnabledPos;
    }

    public static void setPosEnabled(BlockPos pos, boolean enable) {
        if (startingGame)
            return;
        for (StartPos startPos : startPoses) {
            if (startPos.pos.equals(pos)) {
                startPos.enabled = enable;
                if (!startPos.enabled)
                    startPos.reset();
                StartPosClientboundPacket.syncAll();
                return;
            }
        }
    }

    @SubscribeEvent
    public static void onBlockPlaced(BlockEvent.EntityPlaceEvent evt) {
        if (evt.getPlacedBlock().getBlock() instanceof RTSStartBlock rtsStartBlock) {
            if (RTSMapInfoServerEvents.usingMapInfoStartPositions() && evt.getLevel().getServer() != null) {
                evt.getLevel().getServer().sendSystemMessage(Component.translatable("startpos.reignofnether.max_positions"));
            }
            if (startPoses.size() < MAX_START_POSES) {
                StartPos newStartPos = new StartPos(evt.getPos(),
                        rtsStartBlock.getMapColor(rtsStartBlock.defaultBlockState(),
                            evt.getLevel(), evt.getPos(),
                            rtsStartBlock.defaultMapColor()).id
                );
                startPoses.add(newStartPos);
                if (startingGame)
                    cancelStartGameCountdown(false);
                else
                    StartPosClientboundPacket.syncAll();
            } else {
                evt.setCanceled(true);
                for (Player player : PlayerServerEvents.players)
                    if (player.distanceToSqr(Vec3.atCenterOf(evt.getPos())) < 100)
                        player.sendSystemMessage(Component.translatable("startpos.reignofnether.max_positions"));
            }
            if (evt.getLevel() instanceof ServerLevel serverLevel)
                savePositions(serverLevel);
        }
    }

    @SubscribeEvent
    public static void onBlockBreak(BlockEvent.BreakEvent evt) {
        if (startPoses.removeIf(sp -> sp.pos.equals(evt.getPos()))
                && (evt.getLevel() instanceof ServerLevel serverLevel)) {
            if (startingGame)
                cancelStartGameCountdown(false);
            else
                StartPosClientboundPacket.syncAll();
            savePositions(serverLevel);
        }
    }

    @SubscribeEvent
    public static void onPlayerJoin(PlayerEvent.PlayerLoggedInEvent evt) {
        if (evt.getEntity() instanceof ServerPlayer player)
            StartPosClientboundPacket.syncToPlayer(player);
    }

    private static void cullInvalidPoses(ServerLevel serverLevel) {
        if (startPoses.removeIf(sp -> sp.isFromStartBlock
                && !(serverLevel.getBlockState(sp.pos).getBlock() instanceof RTSStartBlock))) {
            StartPosClientboundPacket.syncAll();
            savePositions(serverLevel);
        }
    }

    public static void startGameCountdown() {
        if (!startingGame) {
            ticksToStart = TICKS_TO_START_MAX;
            startingGame = true;
            StartPosClientboundPacket.syncAll(true);
        }
    }

    public static void cancelStartGameCountdown(boolean noMsg) {
        if (startingGame) {
            ticksToStart = TICKS_TO_START_MAX;
            startingGame = false;
            StartPosClientboundPacket.syncAll(true);
            if (!noMsg)
                PlayerServerEvents.sendMessageToAllPlayers("startpos.reignofnether.cancelled_start_game", true);
        }
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post evt) {
        if (startingGame) {
            if (ticksToStart % 20 == 0) {
                int secondsLeft = ticksToStart / 20;
                if (secondsLeft > 0) {
                    PlayerServerEvents.sendMessageToAllPlayersNoNewlines("startpos.reignofnether.starting_game", false, secondsLeft);
                    SoundClientboundPacket.playSoundForAllPlayers(SoundAction.CHAT);
                } else {
                    PlayerServerEvents.sendMessageToAllPlayers("startpos.reignofnether.started_game", true);
                    SoundClientboundPacket.playSoundForAllPlayers(SoundAction.ALLY);
                    for (ServerPlayer serverPlayer : PlayerServerEvents.players) {
                        for (StartPos startPos : startPoses) {
                            if (startPos.playerName.equals(serverPlayer.getName().getString()) && startPos.faction != Faction.NONE) {
                                PlayerServerEvents.startRTS(
                                        serverPlayer.getId(),
                                        new Vec3(startPos.pos.getX(), startPos.pos.getY(), startPos.pos.getZ()),
                                        startPos.faction,
                                        startPos.colorId
                                );
                                break;
                            }
                        }
                    }
                    AlliancesServerEvents.applyConfiguredAlliances(evt.getServer());
                    PlayerServerEvents.setRTSLock(true, true);
                    ticksToStart = TICKS_TO_START_MAX;
                    startingGame = false;
                    StartPosServerEvents.reset(evt.getServer().getLevel(Level.OVERWORLD));
                    StartPosClientboundPacket.syncAll();
                }
            }
            if (startingGame && ticksToStart >= 0)
                ticksToStart -= 1;
        } else if (!(PlayerServerEvents.isGameActive())) {
            cullTicks += 1;
            if (cullTicks >= cullTicksMax) {
                cullTicks = 0;
                cullInvalidPoses(evt.getServer().getLevel(Level.OVERWORLD));
            }
        }
    }

    public static void savePositions(ServerLevel serverLevel) {
        if (!RTSMapInfoServerEvents.usingMapInfoStartPositions()) {
            StartPosSaveData startPosData = StartPosSaveData.getInstance(serverLevel);
            startPosData.startPoses.clear();
            startPosData.startPoses.addAll(startPoses);
            startPosData.save();
            serverLevel.getDataStorage().save();
            //ReignOfNether.LOGGER.info("saved " + startPoses.size() + " start positions in serverevents");
        }
    }

    @SubscribeEvent
    public static void loadPositions(ServerStartedEvent evt) {
        ServerLevel level = evt.getServer().getLevel(Level.OVERWORLD);

        // rtsMapInfo is read in RTSMapInfoServerEvents.loadInfo
        if (level != null) {
            if (RTSMapInfoServerEvents.rtsMapInfo == null) {
                StartPosSaveData startPosData = StartPosSaveData.getInstance(level);
                startPoses.clear();
                startPoses.addAll(startPosData.startPoses);
                ReignOfNether.LOGGER.info("loaded " + startPoses.size() + " start positions in serverevents from save");
            } else {
                loadPositionsFromMapInfo();
                ReignOfNether.LOGGER.info("loaded " + startPoses.size() + " start positions from json file");
            }
        }
    }

    public static void loadPositionsFromMapInfo() {
        if (!RTSMapInfoServerEvents.usingMapInfoStartPositions())
            return;
        int playerColorIndex = 0;
        startPoses.clear();
        for (List<BlockPos> teamStartPoses : RTSMapInfoServerEvents.rtsMapInfo.getTeams()) {
            for (BlockPos startBlockPos : teamStartPoses) {
                StartPos startPos = new StartPos(startBlockPos, PlayerColors.colors[playerColorIndex].hexCode);
                startPos.isFromStartBlock = false;
                startPoses.add(startPos);
            }
            playerColorIndex += 1;
            if (playerColorIndex >= PlayerColors.colors.length)
                playerColorIndex = 0;
        }
        StartPosClientboundPacket.syncAll();
    }

    @SubscribeEvent
    public static void onPlayerLeave(PlayerEvent.PlayerLoggedOutEvent evt) {
        boolean removedReservation = false;
        for (StartPos startPos : startPoses) {
            if (evt.getEntity() instanceof ServerPlayer player &&
                    startPos.playerName.equals(player.getName().getString())) {
                startPos.reset();
                removedReservation = true;
            }
        }
        if (!removedReservation)
            return;
        if (startingGame)
            cancelStartGameCountdown(false);
        else
            StartPosClientboundPacket.syncAll();
    }
}
