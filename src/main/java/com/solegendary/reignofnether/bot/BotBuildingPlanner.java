package com.solegendary.reignofnether.bot;

import com.solegendary.reignofnether.building.Building;
import com.solegendary.reignofnether.building.BuildingPlacement;
import com.solegendary.reignofnether.building.BuildingServerEvents;
import com.solegendary.reignofnether.player.PlayerServerEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

final class BotBuildingPlanner {
    private static final int BUILDING_GAP = 3;
    private static final int MAX_RADIUS = 48;

    private BotBuildingPlanner() {
    }

    static Optional<BlockPos> findPlacement(ServerLevel level, Building building, BlockPos home, String ownerName,
                                            boolean includeHome) {
        for (BlockPos centre : candidateCentres(level, home, includeHome)) {
            var relativeBlocks = building.getRelativeBlockData(level);
            BlockPos origin = PlayerServerEvents.getBuildingOriginPos(centre, relativeBlocks);
            BuildingPlacement placement = building.createBuildingPlacement(level, origin, Rotation.NONE, ownerName);
            if (canPlace(level, placement))
                return Optional.of(origin);
        }
        return Optional.empty();
    }

    static BlockPos groundAt(ServerLevel level, int x, int z) {
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1;
        return new BlockPos(x, y, z);
    }

    private static List<BlockPos> candidateCentres(ServerLevel level, BlockPos home, boolean includeHome) {
        List<BlockPos> centres = new ArrayList<>();
        if (includeHome)
            centres.add(groundAt(level, home.getX(), home.getZ()));

        int startRadius = includeHome ? 12 : 18;
        for (int radius = startRadius; radius <= MAX_RADIUS; radius += 6) {
            int diagonal = Math.max(1, Math.round(radius * 0.7f));
            int[][] offsets = {
                    {radius, 0}, {0, radius}, {-radius, 0}, {0, -radius},
                    {diagonal, diagonal}, {-diagonal, diagonal}, {-diagonal, -diagonal}, {diagonal, -diagonal}
            };
            for (int[] offset : offsets)
                centres.add(groundAt(level, home.getX() + offset[0], home.getZ() + offset[1]));
        }
        return centres;
    }

    private static boolean canPlace(ServerLevel level, BuildingPlacement placement) {
        BlockPos min = placement.minCorner;
        BlockPos max = placement.maxCorner;
        BlockPos origin = placement.originPos;

        if (!level.hasChunkAt(min) || !level.hasChunkAt(max))
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
