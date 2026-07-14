package com.solegendary.reignofnether.bot;

import com.solegendary.reignofnether.building.Building;
import com.solegendary.reignofnether.building.BuildingBlock;
import com.solegendary.reignofnether.building.BuildingPlacement;
import com.solegendary.reignofnether.building.BuildingServerEvents;
import com.solegendary.reignofnether.building.BuildingUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

final class BotBuildingPlanner {
    private static final int BUILDING_GAP = 3;
    private static final int MIN_RADIUS = 12;
    private static final int MAX_RADIUS = 64;
    // Flat roofs can pass footprint checks but strand builders above or below the starting terrace.
    private static final int MAX_BASE_TERRACE_DELTA = 3;
    private static final double EXIT_LANE_HALF_WIDTH = 4;
    private static final List<Offset> SEARCH_OFFSETS = createSearchOffsets();
    private static final List<Rotation> BUILDING_ROTATIONS = List.of(
            Rotation.NONE,
            Rotation.CLOCKWISE_90,
            Rotation.CLOCKWISE_180,
            Rotation.COUNTERCLOCKWISE_90
    );

    private BotBuildingPlanner() {
    }

    enum PlacementRole {
        CAPITOL(0, 0),
        SUPPLY(-16, 16),
        FARM(-8, 14),
        MILITARY(18, 16);

        private final double preferredForward;
        private final double preferredLateral;

        PlacementRole(double preferredForward, double preferredLateral) {
            this.preferredForward = preferredForward;
            this.preferredLateral = preferredLateral;
        }

        double score(double forward, double lateral) {
            double forwardDelta = forward - preferredForward;
            double lateralDelta = Math.abs(lateral) - preferredLateral;
            return forwardDelta * forwardDelta + lateralDelta * lateralDelta;
        }
    }

    private record Axis(double x, double z) {
    }

    private record Candidate(int xOffset, int zOffset, double forwardDistance,
                             double lateralDistance, double roleScore, int distanceSqr) {
    }

    record Offset(int x, int z) {
    }

    record Placement(BlockPos origin, Rotation rotation) {
    }

    private record FootprintSize(double halfWidth, double halfDepth) {
    }

    private record BuildingVariant(Rotation rotation, ArrayList<BuildingBlock> blocks,
                                   FootprintSize size) {
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
        ArrayList<BuildingBlock> relativeBlocks = building.getRelativeBlockData(level);
        return findPlacement(level, building, relativeBlocks, home, includeHome,
                null, PlacementRole.CAPITOL, List.of(Rotation.NONE))
                .map(Placement::origin);
    }

    static Optional<Placement> findPlacement(ServerLevel level, Building building, BlockPos home,
                                             boolean includeHome, BotWorldView worldView,
                                             PlacementRole role) {
        ArrayList<BuildingBlock> relativeBlocks = building.getRelativeBlockData(level);
        return findPlacement(level, building, relativeBlocks, home, includeHome,
                worldView, role, BUILDING_ROTATIONS);
    }

    private static Optional<Placement> findPlacement(
            ServerLevel level, Building building, ArrayList<BuildingBlock> relativeBlocks,
            BlockPos home, boolean includeHome, BotWorldView worldView,
            PlacementRole role, List<Rotation> rotations) {
        List<BuildingVariant> variants = rotations.stream()
                .map(rotation -> {
                    ArrayList<BuildingBlock> blocks = rotate(level, relativeBlocks, rotation);
                    return new BuildingVariant(rotation, blocks, footprintSize(blocks));
                })
                .toList();
        Axis front = frontAxis(level, home);
        List<Candidate> candidates = candidates(includeHome, role, front);
        Optional<Placement> placement = findPlacement(
                level, building, home, worldView, role, front, variants, candidates, false);
        if (placement.isPresent() || role == PlacementRole.CAPITOL)
            return placement;
        return findPlacement(level, building, home, worldView, role, front, variants, candidates, true);
    }

    private static Optional<Placement> findPlacement(
            ServerLevel level, Building building, BlockPos home, BotWorldView worldView,
            PlacementRole role, Axis front, List<BuildingVariant> variants,
            List<Candidate> candidates,
            boolean blockingExitLane) {
        for (Candidate candidate : candidates) {
            BlockPos column = home.offset(candidate.xOffset(), 0, candidate.zOffset());
            if (!isChunkLoaded(level, column)) {
                if (worldView != null)
                    continue;
                level.getChunk(SectionPos.blockToSectionCoord(column.getX()),
                        SectionPos.blockToSectionCoord(column.getZ()));
            }
            if (worldView != null && !worldView.isVisible(column))
                continue;
            BlockPos centre = groundAt(level, column.getX(), column.getZ());
            if (role != PlacementRole.CAPITOL
                    && !isOnBaseTerrace(home.getY(), centre.getY()))
                continue;
            for (BuildingVariant variant : variants) {
                if (role != PlacementRole.CAPITOL
                        && blocksExitLane(candidate, variant.size(), front) != blockingExitLane)
                    continue;
                Footprint footprint = footprintAtCentre(level, variant.blocks(), centre);
                if (!canOccupy(level, building, variant.blocks(), footprint, worldView))
                    continue;
                if (!BuildingUtils.hasPlacementClipping(
                        level, variant.blocks(), footprint.origin()))
                    return Optional.of(new Placement(footprint.origin(), variant.rotation()));
            }
        }
        return Optional.empty();
    }

    static Footprint footprintAtCentre(ServerLevel level, Building building, BlockPos centre) {
        return footprintAtCentre(level, building.getRelativeBlockData(level), centre);
    }

    private static Footprint footprintAtCentre(ServerLevel level,
                                               ArrayList<BuildingBlock> relativeBlocks,
                                               BlockPos centre) {
        BlockPos min = BuildingUtils.getMinCorner(relativeBlocks);
        BlockPos max = BuildingUtils.getMaxCorner(relativeBlocks);
        BlockPos origin = centre.offset(
                -(max.getX() - min.getX()) / 2 - min.getX(),
                0,
                -(max.getZ() - min.getZ()) / 2 - min.getZ());
        return footprintAtOrigin(level, relativeBlocks, origin);
    }

    static Footprint footprintAtOrigin(ServerLevel level, Building building, BlockPos origin) {
        return footprintAtOrigin(level, building.getRelativeBlockData(level), origin);
    }

    private static Footprint footprintAtOrigin(ServerLevel level,
                                               ArrayList<BuildingBlock> relativeBlocks,
                                               BlockPos origin) {
        ArrayList<BuildingBlock> blocks = BuildingUtils.getAbsoluteBlockData(
                relativeBlocks, level, origin, Rotation.NONE);
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

    private static List<Candidate> candidates(boolean includeHome, PlacementRole role, Axis front) {
        List<Candidate> candidates = new ArrayList<>();
        if (includeHome)
            candidates.add(candidate(role, front, 0, 0));
        for (Offset offset : SEARCH_OFFSETS)
            candidates.add(candidate(role, front, offset.x(), offset.z()));
        candidates.sort(Comparator
                .comparingDouble(Candidate::roleScore)
                .thenComparingInt(Candidate::distanceSqr)
                .thenComparingInt(Candidate::xOffset)
                .thenComparingInt(Candidate::zOffset));
        return candidates;
    }

    static List<Offset> searchOffsets() {
        return SEARCH_OFFSETS;
    }

    private static List<Offset> createSearchOffsets() {
        int minDistanceSqr = MIN_RADIUS * MIN_RADIUS;
        int maxDistanceSqr = MAX_RADIUS * MAX_RADIUS;
        List<Offset> offsets = new ArrayList<>();
        for (int x = -MAX_RADIUS; x <= MAX_RADIUS; x++) {
            for (int z = -MAX_RADIUS; z <= MAX_RADIUS; z++) {
                int distanceSqr = x * x + z * z;
                if (distanceSqr >= minDistanceSqr && distanceSqr <= maxDistanceSqr)
                    offsets.add(new Offset(x, z));
            }
        }
        return List.copyOf(offsets);
    }

    static boolean isOnBaseTerrace(int homeY, int candidateY) {
        return Math.abs(candidateY - homeY) <= MAX_BASE_TERRACE_DELTA;
    }

    private static Candidate candidate(PlacementRole role, Axis front, int x, int z) {
        int distanceSqr = x * x + z * z;
        double forwardDistance = x * front.x() + z * front.z();
        double lateralDistance = -x * front.z() + z * front.x();
        return new Candidate(
                x,
                z,
                forwardDistance,
                lateralDistance,
                role.score(forwardDistance, lateralDistance),
                distanceSqr
        );
    }

    private static ArrayList<BuildingBlock> rotate(ServerLevel level,
                                                   ArrayList<BuildingBlock> blocks,
                                                   Rotation rotation) {
        ArrayList<BuildingBlock> rotated = new ArrayList<>(blocks.size());
        for (BuildingBlock block : blocks)
            rotated.add(block.rotate(level, rotation));
        return rotated;
    }

    private static Axis frontAxis(ServerLevel level, BlockPos home) {
        double x = level.getWorldBorder().getCenterX() - home.getX();
        double z = level.getWorldBorder().getCenterZ() - home.getZ();
        double length = Math.hypot(x, z);
        if (length < 0.001)
            return new Axis(1, 0);
        return new Axis(x / length, z / length);
    }

    private static FootprintSize footprintSize(ArrayList<BuildingBlock> relativeBlocks) {
        BlockPos min = BuildingUtils.getMinCorner(relativeBlocks);
        BlockPos max = BuildingUtils.getMaxCorner(relativeBlocks);
        return new FootprintSize(
                (max.getX() - min.getX() + 1) / 2.0,
                (max.getZ() - min.getZ() + 1) / 2.0
        );
    }

    private static boolean blocksExitLane(Candidate candidate, FootprintSize size, Axis front) {
        double forwardExtent = Math.abs(front.x()) * size.halfWidth()
                + Math.abs(front.z()) * size.halfDepth() + BUILDING_GAP;
        double lateralExtent = Math.abs(front.z()) * size.halfWidth()
                + Math.abs(front.x()) * size.halfDepth() + BUILDING_GAP;
        return blocksExitLane(
                candidate.forwardDistance(), forwardExtent,
                candidate.lateralDistance(), lateralExtent);
    }

    static boolean blocksExitLane(double forwardDistance, double forwardExtent,
                                  double lateralDistance, double lateralExtent) {
        return forwardDistance + forwardExtent > 0
                && Math.abs(lateralDistance) - lateralExtent < EXIT_LANE_HALF_WIDTH;
    }

    static boolean canPlace(ServerLevel level, Building building, Footprint footprint,
                            BotWorldView worldView) {
        ArrayList<BuildingBlock> blocks = building.getRelativeBlockData(level);
        return canOccupy(level, building, blocks, footprint, worldView)
                && !BuildingUtils.hasPlacementClipping(level, blocks, footprint.origin());
    }

    private static boolean canOccupy(ServerLevel level, Building building,
                                     ArrayList<BuildingBlock> relativeBlocks,
                                     Footprint footprint, BotWorldView worldView) {
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

        AABB bounds = new AABB(
                min.getX() - BUILDING_GAP,
                level.getMinBuildHeight(),
                min.getZ() - BUILDING_GAP,
                max.getX() + BUILDING_GAP + 1,
                level.getMaxBuildHeight(),
                max.getZ() + BUILDING_GAP + 1
        );
        if (overlapsExistingBuilding(bounds))
            return false;
        if (!BuildingUtils.hasValidGroundSupport(level, relativeBlocks, origin))
            return false;

        if (BuildingUtils.requiresNetherTerrain(building)) {
            ArrayList<BuildingBlock> blocks = BuildingUtils.getAbsoluteBlockData(
                    relativeBlocks, level, origin, Rotation.NONE);
            if (!BuildingServerEvents.isOnNetherBlocks(blocks, origin, level))
                return false;
        }
        return true;
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
