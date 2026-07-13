package com.solegendary.reignofnether.bot;

import com.solegendary.reignofnether.building.BuildingPlacement;
import com.solegendary.reignofnether.building.BuildingServerEvents;
import com.solegendary.reignofnether.fogofwar.FogOfWarServerEvents;
import com.solegendary.reignofnether.player.PlayerServerEvents;
import com.solegendary.reignofnether.player.RTSPlayer;
import com.solegendary.reignofnether.unit.UnitServerEvents;
import com.solegendary.reignofnether.unit.interfaces.Unit;
import net.minecraft.core.BlockPos;
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

    private final Map<BlockPos, BlockPos> knownEnemyCentres = new HashMap<>();
    private final Set<ChunkPos> visibleChunks = new HashSet<>();
    private boolean fogWasEnabled;

    void observe(RTSPlayer viewer) {
        List<BuildingPlacement> buildings = BuildingServerEvents.getBuildings();
        if (!FogOfWarServerEvents.isEnabled()) {
            knownEnemyCentres.clear();
            visibleChunks.clear();
            fogWasEnabled = false;
            return;
        }
        if (!fogWasEnabled)
            knownEnemyCentres.clear();
        fogWasEnabled = true;

        updateVisibleChunks(viewer.name, buildings);
        for (BuildingPlacement building : buildings) {
            if (!isPotentialEnemy(viewer, building.ownerName))
                continue;
            RTSPlayer enemy = PlayerServerEvents.getRTSPlayer(building.ownerName);
            boolean revealed = enemy != null && enemy.ticksWithoutCapitol >= PlayerServerEvents.TICKS_TO_REVEAL;
            if (revealed || visibleChunks.contains(new ChunkPos(building.centrePos)))
                knownEnemyCentres.put(building.originPos, building.centrePos);
        }

        knownEnemyCentres.entrySet().removeIf(entry -> visibleChunks.contains(new ChunkPos(entry.getValue()))
                && buildings.stream().noneMatch(building -> building.originPos.equals(entry.getKey())
                        && isPotentialEnemy(viewer, building.ownerName)));
    }

    List<BuildingPlacement> knownEnemyBuildings(RTSPlayer viewer) {
        List<BuildingPlacement> result = new ArrayList<>();
        boolean fogEnabled = FogOfWarServerEvents.isEnabled();
        for (BuildingPlacement building : BuildingServerEvents.getBuildings())
            if ((!fogEnabled || knownEnemyCentres.containsKey(building.originPos))
                    && isPotentialEnemy(viewer, building.ownerName))
                result.add(building);
        return result;
    }

    boolean fogEnabled() {
        return FogOfWarServerEvents.isEnabled();
    }

    boolean isVisible(BlockPos pos) {
        return !FogOfWarServerEvents.isEnabled() || visibleChunks.contains(new ChunkPos(pos));
    }

    private void updateVisibleChunks(String ownerName, List<BuildingPlacement> buildings) {
        visibleChunks.clear();
        for (LivingEntity entity : UnitServerEvents.getAllUnits())
            if (entity.isAlive() && entity instanceof Unit unit && unit.getOwnerName().equals(ownerName))
                addVisibleSquare(new ChunkPos(entity.blockPosition()), UNIT_VIEW_CHUNKS);

        for (BuildingPlacement building : buildings) {
            if (!building.ownerName.equals(ownerName))
                continue;
            addVisibleSquare(new ChunkPos(building.centrePos),
                    building.isCapitol ? CAPITOL_VIEW_CHUNKS : BUILDING_VIEW_CHUNKS);
        }
    }

    private void addVisibleSquare(ChunkPos centre, int distance) {
        for (int x = -distance; x <= distance; x++)
            for (int z = -distance; z <= distance; z++)
                visibleChunks.add(new ChunkPos(centre.x + x, centre.z + z));
    }

    private static boolean isPotentialEnemy(RTSPlayer viewer, String ownerName) {
        return !ownerName.isBlank() && !ownerName.equals(viewer.name)
                && PlayerServerEvents.getRTSPlayer(ownerName) != null;
    }
}
