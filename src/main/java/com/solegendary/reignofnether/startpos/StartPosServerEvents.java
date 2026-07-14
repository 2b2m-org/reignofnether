package com.solegendary.reignofnether.startpos;

import com.solegendary.reignofnether.ReignOfNether;
import com.solegendary.reignofnether.alliance.AlliancesServerEvents;
import com.solegendary.reignofnether.blocks.RTSStartBlock;
import com.solegendary.reignofnether.bot.BotLobby;
import com.solegendary.reignofnether.building.Building;
import com.solegendary.reignofnether.building.BuildingBlock;
import com.solegendary.reignofnether.building.BuildingServerEvents;
import com.solegendary.reignofnether.building.BuildingUtils;
import com.solegendary.reignofnether.player.PlayerColors;
import com.solegendary.reignofnether.player.PlayerServerEvents;
import com.solegendary.reignofnether.resources.ResourcesServerEvents;
import com.solegendary.reignofnether.rtsmap.RTSMapInfoServerEvents;
import com.solegendary.reignofnether.sounds.SoundAction;
import com.solegendary.reignofnether.sounds.SoundClientboundPacket;
import com.solegendary.reignofnether.faction.Faction;
import com.solegendary.reignofnether.tutorial.TutorialServerEvents;
import com.solegendary.reignofnether.util.MiscUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.phys.AABB;
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

    private record PlannedStart(StartPos startPos, Faction faction, ServerPlayer serverPlayer) {
    }

    private record MatchStartPlan(List<PlannedStart> starts, String failedDisplayName) {
    }

    public static boolean isStartingGame() {
        return startingGame;
    }

    static int getCountdownTicks() {
        return startingGame ? ticksToStart : -1;
    }

    public static StartPos getStartPos(BlockPos pos) {
        for (StartPos startPos : startPoses) {
            if (startPos.pos.equals(pos))
                return startPos;
        }
        return null;
    }

    public static boolean hasReservations() {
        return startPoses.stream().anyMatch(StartPos::isOccupied);
    }

    public static boolean hasAiReservations() {
        return startPoses.stream().anyMatch(startPos -> startPos.aiControlled);
    }

    public static void reset(ServerLevel serverLevel) {
        for (StartPos startPos : startPoses) {
            startPos.reset();
        }
        savePositions(serverLevel);
    }

    public static void setPlayerReady(String ownerName, boolean ready) {
        StartPos playerPos = null;
        for (StartPos startPos : startPoses) {
            if (startPos.enabled && startPos.isOwnedBy(ownerName)) {
                playerPos = startPos;
                break;
            }
        }
        if (playerPos == null || (ready && !isPlayableFaction(playerPos.faction)))
            return;

        playerPos.ready = ready;
        updateCountdown(true);
    }

    public static void updateCountdown(boolean announceReadyChanges) {
        if (canStartGame()) {
            startGameCountdown();
        } else if (startingGame) {
            cancelStartGameCountdown(false);
        } else {
            StartPosClientboundPacket.syncAll(announceReadyChanges);
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
            if (!startPos.isOccupied() || !startPos.ready || !isPlayableFaction(startPos.faction))
                return false;
        }
        return hasEnabledPos;
    }

    private static boolean canAffordStartingBuilding(Building building) {
        boolean tutorial = TutorialServerEvents.isEnabled();
        int food = tutorial
                ? ResourcesServerEvents.STARTING_FOOD_TUTORIAL
                : ResourcesServerEvents.STARTING_FOOD;
        int wood = tutorial
                ? ResourcesServerEvents.STARTING_WOOD_TUTORIAL
                : ResourcesServerEvents.STARTING_WOOD;
        int ore = tutorial
                ? ResourcesServerEvents.STARTING_ORE_TUTORIAL
                : ResourcesServerEvents.STARTING_ORE;
        return food >= building.cost.food
                && wood >= building.cost.wood
                && ore >= building.cost.ore;
    }

    private static MatchStartPlan planMatchStart(ServerLevel level) {
        List<PlannedStart> starts = new ArrayList<>();
        List<AABB> plannedFootprints = new ArrayList<>();

        for (StartPos startPos : startPoses) {
            if (!startPos.enabled)
                continue;

            Faction faction = startPos.faction == Faction.RANDOM
                    ? MiscUtil.getRandomItem(List.of(
                            Faction.VILLAGERS, Faction.MONSTERS, Faction.PIGLINS))
                    : startPos.faction;
            Building building = PlayerServerEvents.getStartingBuilding(faction);
            if (building == null || PlayerServerEvents.rtsLocked
                    || PlayerServerEvents.hasRTSPlayerName(startPos.ownerName)
                    || !canAffordStartingBuilding(building))
                return new MatchStartPlan(List.of(), startPos.displayName);

            ServerPlayer serverPlayer = null;
            if (!startPos.aiControlled) {
                for (ServerPlayer player : PlayerServerEvents.players) {
                    if (startPos.isOwnedBy(player.getName().getString())) {
                        serverPlayer = player;
                        break;
                    }
                }
                if (serverPlayer == null || PlayerServerEvents.isRTSPlayer(serverPlayer.getId()))
                    return new MatchStartPlan(List.of(), startPos.displayName);
            }
            if (level.getWorldBorder().getDistanceToBorder(
                    startPos.pos.getX(), startPos.pos.getZ()) < 1)
                return new MatchStartPlan(List.of(), startPos.displayName);

            ArrayList<BuildingBlock> relativeBlocks = building.getRelativeBlockData(level);
            BlockPos origin = PlayerServerEvents.getBuildingOriginPos(startPos.pos, relativeBlocks);
            ArrayList<BuildingBlock> absoluteBlocks = BuildingUtils.getAbsoluteBlockData(
                    relativeBlocks, level, origin, Rotation.NONE);
            BlockPos min = BuildingUtils.getMinCorner(absoluteBlocks);
            BlockPos max = BuildingUtils.getMaxCorner(absoluteBlocks);
            for (int chunkX = SectionPos.blockToSectionCoord(min.getX());
                 chunkX <= SectionPos.blockToSectionCoord(max.getX()); chunkX++)
                for (int chunkZ = SectionPos.blockToSectionCoord(min.getZ());
                     chunkZ <= SectionPos.blockToSectionCoord(max.getZ()); chunkZ++)
                    level.getChunk(chunkX, chunkZ);

            AABB footprint = AABB.encapsulatingFullBlocks(min, max);
            boolean overlapsExisting = BuildingServerEvents.getBuildings().stream()
                    .map(buildingPlacement -> AABB.encapsulatingFullBlocks(
                            buildingPlacement.minCorner, buildingPlacement.maxCorner))
                    .anyMatch(footprint::intersects);
            if (overlapsExisting || plannedFootprints.stream().anyMatch(footprint::intersects))
                return new MatchStartPlan(List.of(), startPos.displayName);

            plannedFootprints.add(footprint);
            starts.add(new PlannedStart(startPos, faction, serverPlayer));
        }
        return new MatchStartPlan(List.copyOf(starts), null);
    }

    public static void setPosEnabled(BlockPos pos, boolean enable) {
        if (startingGame)
            return;
        for (StartPos startPos : startPoses) {
            if (startPos.pos.equals(pos)) {
                if (!enable && startPos.isOccupied())
                    return;
                startPos.enabled = enable;
                if (!startPos.enabled)
                    startPos.reset();
                updateCountdown(false);
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
                    ServerLevel level = evt.getServer().getLevel(Level.OVERWORLD);
                    if (level == null) {
                        cancelStartGameCountdown(true);
                        return;
                    }
                    MatchStartPlan startPlan = planMatchStart(level);
                    if (startPlan.failedDisplayName() != null) {
                        PlayerServerEvents.sendMessageToAllPlayers(
                                "server.reignofnether.match_start_failed", true,
                                startPlan.failedDisplayName());
                        cancelStartGameCountdown(true);
                        return;
                    }
                    var failedBot = BotLobby.startBots(level, startPoses);
                    if (failedBot.isPresent()) {
                        PlayerServerEvents.sendMessageToAllPlayers(
                                "server.reignofnether.match_start_failed", true, failedBot.get());
                        cancelStartGameCountdown(true);
                        return;
                    }
                    PlayerServerEvents.initializeMatchTime(level);
                    PlayerServerEvents.sendMessageToAllPlayers("startpos.reignofnether.started_game", true);
                    SoundClientboundPacket.playSoundForAllPlayers(SoundAction.ALLY);
                    for (PlannedStart plannedStart : startPlan.starts()) {
                        if (plannedStart.startPos().aiControlled)
                            continue;
                        StartPos startPos = plannedStart.startPos();
                        PlayerServerEvents.startRTS(
                                plannedStart.serverPlayer().getId(),
                                new Vec3(startPos.pos.getX(), startPos.pos.getY(), startPos.pos.getZ()),
                                plannedStart.faction(),
                                startPos.colorId
                        );
                    }
                    AlliancesServerEvents.applyConfiguredAlliances(evt.getServer());
                    PlayerServerEvents.setRTSLock(true, true);
                    ticksToStart = TICKS_TO_START_MAX;
                    startingGame = false;
                    StartPosServerEvents.reset(level);
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
        ticksToStart = TICKS_TO_START_MAX;
        startingGame = false;
        cullTicks = 0;
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
                    !startPos.aiControlled && startPos.isOwnedBy(player.getName().getString())) {
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
