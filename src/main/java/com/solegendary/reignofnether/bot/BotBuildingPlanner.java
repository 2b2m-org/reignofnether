package com.solegendary.reignofnether.bot;

import com.solegendary.reignofnether.building.Building;
import com.solegendary.reignofnether.building.BuildingPlacement;
import com.solegendary.reignofnether.building.BuildingServerEvents;
import com.solegendary.reignofnether.player.PlayerServerEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

final class BotBuildingPlanner {
    private static final int BUILDING_GAP = 3;
    private static final int MAX_RADIUS = 48;

    private BotBuildingPlanner() {
    }

    static Optional<BlockPos> findPlacement(ServerLevel level, Building building, BlockPos home, String ownerName,
                                            boolean includeHome) {
        return findPlacement(level, building, home, ownerName, includeHome, null);
    }

    static Optional<BlockPos> findPlacement(ServerLevel level, Building building, BlockPos home, String ownerName,
                                            boolean includeHome, BotWorldView worldView) {
        for (BlockPos centre : candidateCentres(level, home, includeHome)) {
            var relativeBlocks = building.getRelativeBlockData(level);
            BlockPos origin = PlayerServerEvents.getBuildingOriginPos(centre, relativeBlocks);
            BuildingPlacement placement = building.createBuildingPlacement(level, origin, Rotation.NONE, ownerName);
            if (canPlace(level, placement, worldView))
                return Optional.of(origin);
        }
        return Optional.empty();
    }

    static BlockPos groundAt(ServerLevel level, int x, int z) {
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1;
        return new BlockPos(x, y, z);
    }

    static boolean isChunkLoaded(ServerLevel level, BlockPos pos) {
        return level.hasChunk(SectionPos.blockToSectionCoord(pos.getX()),
                SectionPos.blockToSectionCoord(pos.getZ()));
    }

    private static List<BlockPos> candidateCentres(ServerLevel level, BlockPos home, boolean includeHome) {
        List<BlockPos> centres = new ArrayList<>();
        if (includeHome)
            centres.add(groundAt(level, home.getX(), home.getZ()));

        int startRadius = includeHome ? 12 : 18;
        double outwardX = home.getX() - level.getWorldBorder().getCenterX();
        double outwardZ = home.getZ() - level.getWorldBorder().getCenterZ();
        for (int radius = startRadius; radius <= MAX_RADIUS; radius += 6) {
            int diagonal = Math.max(1, Math.round(radius * 0.7f));
            int[][] offsets = {
                    {radius, 0}, {0, radius}, {-radius, 0}, {0, -radius},
                    {diagonal, diagonal}, {-diagonal, diagonal}, {-diagonal, -diagonal}, {diagonal, -diagonal}
            };
            Arrays.sort(offsets, Comparator.<int[]>comparingDouble(
                    offset -> offset[0] * outwardX + offset[1] * outwardZ).reversed());
            for (int[] offset : offsets)
                centres.add(groundAt(level, home.getX() + offset[0], home.getZ() + offset[1]));
        }
        return centres;
    }

    private static boolean canPlace(ServerLevel level, BuildingPlacement placement, BotWorldView worldView) {
        BlockPos min = placement.minCorner;
        BlockPos max = placement.maxCorner;
        BlockPos origin = placement.originPos;

        if (!isChunkLoaded(level, min) || !isChunkLoaded(level, max))
            return false;
        BlockPos visibilityMin = min.offset(-BUILDING_GAP, 0, -BUILDING_GAP);
        BlockPos visibilityMax = max.offset(BUILDING_GAP, 0, BUILDING_GAP);
        if (worldView != null && !worldView.isFootprintVisible(visibilityMin, visibilityMax))
            return false;

        double requiredBorderDistance = Math.max(max.getX() - min.getX(), max.getZ() - min.getZ()) / 2.0 + BUILDING_GAP;
        if (level.getWorldBorder().getDistanceToBorder(placement.centrePos.getX(), placement.centrePos.getZ())
                < requiredBorderDistance)
            return false;

        for (int x = min.getX(); x <= max.getX(); x++) {
            for (int z = min.getZ(); z <= max.getZ(); z++) {
                if (groundAt(level, x, z).getY() != origin.getY())
                    return false;
                for (int y = origin.getY() + 1; y <= max.getY(); y++) {
                    var state = level.getBlockState(new BlockPos(x, y, z));
                    if (!state.isAir() && !state.canBeReplaced())
                        return false;
                }
            }
        }

        AABB candidateBounds = new AABB(
                min.getX() - BUILDING_GAP,
                level.getMinBuildHeight(),
                min.getZ() - BUILDING_GAP,
                max.getX() + BUILDING_GAP + 1,
                level.getMaxBuildHeight(),
                max.getZ() + BUILDING_GAP + 1
        );
        for (BuildingPlacement existing : BuildingServerEvents.getBuildings()) {
            AABB existingBounds = AABB.encapsulatingFullBlocks(existing.minCorner, existing.maxCorner);
            if (candidateBounds.intersects(existingBounds))
                return false;
        }
        return true;
    }
}
