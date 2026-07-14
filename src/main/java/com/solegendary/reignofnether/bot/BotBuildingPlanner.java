package com.solegendary.reignofnether.bot;

import com.solegendary.reignofnether.building.Building;
import com.solegendary.reignofnether.building.BuildingBlock;
import com.solegendary.reignofnether.building.BuildingPlacement;
import com.solegendary.reignofnether.building.BuildingServerEvents;
import com.solegendary.reignofnether.building.BuildingUtils;
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

    record Footprint(BlockPos origin, BlockPos min, BlockPos max, BlockPos centre) {
        AABB bounds(int gap) {
            return new AABB(
                    min.getX() - gap,
                    min.getY(),
                    min.getZ() - gap,
                    max.getX() + gap + 1,
                    max.getY() + 1,
                    max.getZ() + gap + 1
            );
        }

        boolean overlaps(Footprint other, int gap) {
            return bounds(gap).intersects(other.bounds(0));
        }
    }

    static Optional<BlockPos> findPlacement(ServerLevel level, Building building, BlockPos home,
                                            boolean includeHome) {
        return findPlacement(level, building, home, includeHome, null);
    }

    static Optional<BlockPos> findPlacement(ServerLevel level, Building building, BlockPos home,
                                            boolean includeHome, BotWorldView worldView) {
        for (BlockPos centre : candidateCentres(level, home, includeHome)) {
            Footprint footprint = footprintAtCentre(level, building, centre);
            if (canPlace(level, footprint, worldView))
                return Optional.of(footprint.origin());
        }
        return Optional.empty();
    }

    static Footprint footprintAtCentre(ServerLevel level, Building building, BlockPos centre) {
        ArrayList<BuildingBlock> relativeBlocks = building.getRelativeBlockData(level);
        BlockPos origin = PlayerServerEvents.getBuildingOriginPos(centre, relativeBlocks);
        return footprintAtOrigin(level, building, origin);
    }

    static Footprint footprintAtOrigin(ServerLevel level, Building building, BlockPos origin) {
        ArrayList<BuildingBlock> blocks = BuildingUtils.getAbsoluteBlockData(
                building.getRelativeBlockData(level), level, origin, Rotation.NONE);
        return new Footprint(
                origin,
                BuildingUtils.getMinCorner(blocks),
                BuildingUtils.getMaxCorner(blocks),
                BuildingUtils.getCentrePos(blocks)
        );
    }

    static void loadChunks(ServerLevel level, Footprint footprint) {
        int minChunkX = SectionPos.blockToSectionCoord(footprint.min().getX());
        int maxChunkX = SectionPos.blockToSectionCoord(footprint.max().getX());
        int minChunkZ = SectionPos.blockToSectionCoord(footprint.min().getZ());
        int maxChunkZ = SectionPos.blockToSectionCoord(footprint.max().getZ());
        for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++)
            for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++)
                level.getChunk(chunkX, chunkZ);
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

    static boolean canPlace(ServerLevel level, Footprint footprint, BotWorldView worldView) {
        BlockPos min = footprint.min();
        BlockPos max = footprint.max();
        BlockPos origin = footprint.origin();

        for (int chunkX = SectionPos.blockToSectionCoord(min.getX());
             chunkX <= SectionPos.blockToSectionCoord(max.getX()); chunkX++)
            for (int chunkZ = SectionPos.blockToSectionCoord(min.getZ());
                 chunkZ <= SectionPos.blockToSectionCoord(max.getZ()); chunkZ++)
                if (!level.hasChunk(chunkX, chunkZ))
                    return false;
        if (!level.getWorldBorder().isWithinBounds(min) || !level.getWorldBorder().isWithinBounds(max))
            return false;
        BlockPos visibilityMin = min.offset(-BUILDING_GAP, 0, -BUILDING_GAP);
        BlockPos visibilityMax = max.offset(BUILDING_GAP, 0, BUILDING_GAP);
        if (worldView != null && !worldView.isFootprintVisible(visibilityMin, visibilityMax))
            return false;

        double requiredBorderDistance = Math.max(max.getX() - min.getX(), max.getZ() - min.getZ()) / 2.0 + BUILDING_GAP;
        if (level.getWorldBorder().getDistanceToBorder(footprint.centre().getX(), footprint.centre().getZ())
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

        AABB bounds = new AABB(
                min.getX() - BUILDING_GAP,
                level.getMinBuildHeight(),
                min.getZ() - BUILDING_GAP,
                max.getX() + BUILDING_GAP + 1,
                level.getMaxBuildHeight(),
                max.getZ() + BUILDING_GAP + 1
        );
        return !overlapsExistingBuilding(bounds);
    }

    static boolean overlapsExistingBuilding(Footprint footprint, int gap) {
        return overlapsExistingBuilding(footprint.bounds(gap));
    }

    private static boolean overlapsExistingBuilding(AABB bounds) {
        for (BuildingPlacement existing : BuildingServerEvents.getBuildings()) {
            if (bounds.intersects(AABB.encapsulatingFullBlocks(
                    existing.minCorner, existing.maxCorner)))
                return true;
        }
        return false;
    }
}
