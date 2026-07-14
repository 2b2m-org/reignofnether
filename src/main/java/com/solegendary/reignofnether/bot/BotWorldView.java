package com.solegendary.reignofnether.bot;

import com.solegendary.reignofnether.alliance.AlliancesServerEvents;
import com.solegendary.reignofnether.building.BuildingPlacement;
import com.solegendary.reignofnether.building.BuildingServerEvents;
import com.solegendary.reignofnether.building.addon.GarrisonableBuildingAddon;
import com.solegendary.reignofnether.building.buildings.neutral.CapturableBeacon;
import com.solegendary.reignofnether.building.buildings.placements.BeaconPlacement;
import com.solegendary.reignofnether.building.buildings.placements.ProductionPlacement;
import com.solegendary.reignofnether.fogofwar.FogOfWarServerEvents;
import com.solegendary.reignofnether.player.PlayerServerEvents;
import com.solegendary.reignofnether.player.RTSPlayer;
import com.solegendary.reignofnether.registrars.GameRuleRegistrar;
import com.solegendary.reignofnether.survival.SurvivalServerEvents;
import com.solegendary.reignofnether.unit.UnitServerEvents;
import com.solegendary.reignofnether.unit.interfaces.AttackerUnit;
import com.solegendary.reignofnether.unit.interfaces.Unit;
import com.solegendary.reignofnether.unit.interfaces.WorkerUnit;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ChunkPos;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class BotWorldView {
    private static final int UNIT_VIEW_CHUNKS = 1;
    private static final int BUILDING_VIEW_CHUNKS = 1;
    private static final int CAPITOL_VIEW_CHUNKS = 2;

    private final Map<BlockPos, KnownEnemyBuilding> knownEnemyBuildings = new HashMap<>();
    private final Set<ChunkPos> visibleChunks = new HashSet<>();
    private boolean fogWasEnabled;

    void observe(RTSPlayer viewer) {
        List<BuildingPlacement> buildings = BuildingServerEvents.getBuildings();
        if (!FogOfWarServerEvents.isEnabled()) {
            knownEnemyBuildings.clear();
            visibleChunks.clear();
            fogWasEnabled = false;
            return;
        }
        if (!fogWasEnabled)
            knownEnemyBuildings.clear();
        fogWasEnabled = true;

        updateVisibleChunks(viewer.name, buildings);
        for (BuildingPlacement building : buildings) {
            if (!isPotentialEnemy(viewer, building.ownerName))
                continue;
            if (isRevealed(building.ownerName) || occupiesVisibleChunk(building.minCorner, building.maxCorner))
                knownEnemyBuildings.put(building.originPos, KnownEnemyBuilding.observe(building));
        }

        knownEnemyBuildings.entrySet().removeIf(entry -> {
            KnownEnemyBuilding known = entry.getValue();
            if (!isPotentialEnemy(viewer, known.ownerName()))
                return true;
            if (!isRevealed(known.ownerName())
                    && !occupiesVisibleChunk(known.minCorner(), known.maxCorner()))
                return false;
            return buildings.stream().noneMatch(building -> building.originPos.equals(known.origin())
                    && building.ownerName.equals(known.ownerName())
                    && isPotentialEnemy(viewer, building.ownerName));
        });
    }

    List<KnownEnemyBuilding> knownEnemyBuildings(RTSPlayer viewer) {
        List<KnownEnemyBuilding> result = new ArrayList<>();
        if (!FogOfWarServerEvents.isEnabled()) {
            for (BuildingPlacement building : BuildingServerEvents.getBuildings())
                if (isPotentialEnemy(viewer, building.ownerName))
                    result.add(KnownEnemyBuilding.observe(building));
            return result;
        }
        for (KnownEnemyBuilding building : knownEnemyBuildings.values())
            if (isPotentialEnemy(viewer, building.ownerName()))
                result.add(building);
        return result;
    }

    KnownEnemyBuilding knownEnemyBuilding(RTSPlayer viewer, BlockPos origin, String ownerName) {
        return knownEnemyBuildings(viewer).stream()
                .filter(building -> building.origin().equals(origin) && building.ownerName().equals(ownerName))
                .findFirst()
                .orElse(null);
    }

    boolean fogEnabled() {
        return FogOfWarServerEvents.isEnabled();
    }

    boolean isVisible(BlockPos pos) {
        return !FogOfWarServerEvents.isEnabled() || visibleChunks.contains(new ChunkPos(pos));
    }

    boolean isFootprintVisible(BlockPos min, BlockPos max) {
        if (!FogOfWarServerEvents.isEnabled())
            return true;
        ChunkPos minChunk = new ChunkPos(min);
        ChunkPos maxChunk = new ChunkPos(max);
        for (int x = minChunk.x; x <= maxChunk.x; x++)
            for (int z = minChunk.z; z <= maxChunk.z; z++)
                if (!visibleChunks.contains(new ChunkPos(x, z)))
                    return false;
        return true;
    }

    List<LivingEntity> visibleEnemyCombatants(RTSPlayer viewer) {
        List<LivingEntity> result = new ArrayList<>();
        for (LivingEntity entity : UnitServerEvents.getAllUnits()) {
            if (!entity.isAlive() || !(entity instanceof Unit unit) || !(entity instanceof AttackerUnit)
                    || GarrisonableBuildingAddon.getGarrison(unit) != null)
                continue;
            if (isPotentialEnemy(viewer, unit.getOwnerName()) && isVisible(entity.blockPosition()))
                result.add(entity);
        }
        result.sort(java.util.Comparator.comparingInt(LivingEntity::getId));
        return result;
    }

    List<LivingEntity> visibleWorkerThreats(ServerLevel level, RTSPlayer viewer) {
        boolean neutralAggro = level.getGameRules().getRule(GameRuleRegistrar.NEUTRAL_AGGRO).get();
        List<LivingEntity> result = new ArrayList<>();
        for (LivingEntity entity : UnitServerEvents.getAllUnits()) {
            if (!entity.isAlive() || entity.level() != level || !(entity instanceof Unit unit)
                    || !(entity instanceof AttackerUnit attacker)
                    || GarrisonableBuildingAddon.getGarrison(unit) != null)
                continue;
            boolean playerThreat = !(entity instanceof WorkerUnit)
                    && isPotentialEnemy(viewer, unit.getOwnerName());
            boolean neutralThreat = isAggressiveNeutralThreat(
                    neutralAggro, unit.getOwnerName(), attacker.getAggressiveWhenIdle());
            if ((playerThreat || neutralThreat) && isVisible(entity.blockPosition()))
                result.add(entity);
        }
        result.sort(java.util.Comparator.comparingInt(LivingEntity::getId));
        return result;
    }

    static boolean isAggressiveNeutralThreat(boolean neutralAggro, String ownerName,
                                               boolean aggressiveWhenIdle) {
        return neutralAggro && ownerName.isBlank() && aggressiveWhenIdle;
    }

    BeaconPlacement capturableBeacon() {
        // Its location, owner, and timer are broadcast to every player even under fog of war.
        return BuildingServerEvents.getBuildings().stream()
                .filter(BeaconPlacement.class::isInstance)
                .map(BeaconPlacement.class::cast)
                .filter(beacon -> beacon.isBuilt
                        && beacon.getBuilding() instanceof CapturableBeacon)
                .findFirst()
                .orElse(null);
    }

    private void updateVisibleChunks(String ownerName, List<BuildingPlacement> buildings) {
        visibleChunks.clear();
        Set<String> alliedOwners = new HashSet<>(AlliancesServerEvents.getAllAllies(ownerName));
        alliedOwners.add(ownerName);
        for (LivingEntity entity : UnitServerEvents.getAllUnits())
            if (entity.isAlive() && entity instanceof Unit unit && alliedOwners.contains(unit.getOwnerName()))
                addVisibleSquare(new ChunkPos(entity.blockPosition()), UNIT_VIEW_CHUNKS);

        for (BuildingPlacement building : buildings) {
            if (!building.ownerName.equals(ownerName))
                continue;
            addVisibleSquare(new ChunkPos(building.centrePos),
                    building.isCapitol ? CAPITOL_VIEW_CHUNKS : BUILDING_VIEW_CHUNKS);
        }
    }

    private boolean occupiesVisibleChunk(BlockPos min, BlockPos max) {
        ChunkPos minChunk = new ChunkPos(min);
        ChunkPos maxChunk = new ChunkPos(max);
        for (int x = minChunk.x; x <= maxChunk.x; x++)
            for (int z = minChunk.z; z <= maxChunk.z; z++)
                if (visibleChunks.contains(new ChunkPos(x, z)))
                    return true;
        return false;
    }

    private void addVisibleSquare(ChunkPos centre, int distance) {
        for (int x = -distance; x <= distance; x++)
            for (int z = -distance; z <= distance; z++)
                visibleChunks.add(new ChunkPos(centre.x + x, centre.z + z));
    }

    private static boolean isRevealed(String ownerName) {
        RTSPlayer player = PlayerServerEvents.getRTSPlayer(ownerName);
        return player != null && player.ticksWithoutCapitol >= PlayerServerEvents.TICKS_TO_REVEAL;
    }

    static boolean isPotentialEnemy(RTSPlayer viewer, String ownerName) {
        if (ownerName.isBlank() || ownerName.equals(viewer.name)
                || AlliancesServerEvents.isAllied(viewer.name, ownerName))
            return false;
        return PlayerServerEvents.getRTSPlayer(ownerName) != null
                || SurvivalServerEvents.isEnabled()
                && SurvivalServerEvents.ENEMY_OWNER_NAME.equals(ownerName);
    }

    record KnownEnemyBuilding(BlockPos origin, BlockPos centre, BlockPos minCorner, BlockPos maxCorner,
                              String ownerName, boolean capitol, boolean production, boolean invulnerable,
                              int blocksPlaced) {
        static KnownEnemyBuilding observe(BuildingPlacement building) {
            return new KnownEnemyBuilding(
                    building.originPos,
                    building.centrePos,
                    building.minCorner,
                    building.maxCorner,
                    building.ownerName,
                    building.isCapitol,
                    building instanceof ProductionPlacement,
                    building.getBuilding().invulnerable,
                    building.getBlocksPlaced()
            );
        }

        BlockPos closestGroundPos(BlockPos target, int radiusOffset) {
            BlockPos closest = minCorner.offset(-radiusOffset, 0, -radiusOffset);
            double closestDistance = Double.POSITIVE_INFINITY;
            for (int x = minCorner.getX() - radiusOffset;
                 x <= maxCorner.getX() + radiusOffset; x++) {
                for (int z = minCorner.getZ() - radiusOffset;
                     z <= maxCorner.getZ() + radiusOffset; z++) {
                    if (x >= minCorner.getX() && x <= maxCorner.getX()
                            && z >= minCorner.getZ() && z <= maxCorner.getZ())
                        continue;
                    double distance = target.distToCenterSqr(x, minCorner.getY(), z);
                    if (distance < closestDistance) {
                        closestDistance = distance;
                        closest = new BlockPos(x, minCorner.getY(), z);
                    }
                }
            }
            return closest;
        }
    }
}
