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
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

final class BotBuildingPlanner {
    private static final int BUILDING_GAP = 3;
    private static final int CANDIDATE_DIRECTIONS = 32;
    private static final int MIN_RADIUS = 12;
    private static final int MAX_RADIUS = 48;
    private static final int RADIUS_STEP = 2;
    private static final double EXIT_LANE_HALF_WIDTH = 4;

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

    private record Candidate(int xOffset, int zOffset, boolean blocksExitLane,
                             double roleScore, int distanceSqr) {
    }

    private record FootprintSize(double halfWidth, double halfDepth) {
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
        return findPlacement(level, building, home, includeHome, null, PlacementRole.CAPITOL);
    }

    static Optional<BlockPos> findPlacement(ServerLevel level, Building building, BlockPos home,
                                            boolean includeHome, BotWorldView worldView,
                                            PlacementRole role) {
        ArrayList<BuildingBlock> relativeBlocks = building.getRelativeBlockData(level);
        FootprintSize size = footprintSize(relativeBlocks);
        for (Candidate candidate : candidates(level, home, includeHome, role, size)) {
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
            Footprint footprint = footprintAtCentre(level, relativeBlocks, centre);
            if (canPlace(level, building, relativeBlocks, footprint, worldView))
                return Optional.of(footprint.origin());
        }
        return Optional.empty();
    }

    static Footprint footprintAtCentre(ServerLevel level, Building building, BlockPos centre) {
        return footprintAtCentre(level, building.getRelativeBlockData(level), centre);
    }

    private static Footprint footprintAtCentre(ServerLevel level,
                                               ArrayList<BuildingBlock> relativeBlocks,
                                               BlockPos centre) {
        BlockPos origin = PlayerServerEvents.getBuildingOriginPos(centre, relativeBlocks);
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

    private static List<Candidate> candidates(ServerLevel level, BlockPos home, boolean includeHome,
                                              PlacementRole role, FootprintSize size) {
        Axis front = frontAxis(level, home);
        List<Candidate> candidates = new ArrayList<>();
        double forwardExtent = Math.abs(front.x()) * size.halfWidth()
                + Math.abs(front.z()) * size.halfDepth() + BUILDING_GAP;
        double lateralExtent = Math.abs(front.z()) * size.halfWidth()
                + Math.abs(front.x()) * size.halfDepth() + BUILDING_GAP;
        Set<Long> seenOffsets = new HashSet<>();
        if (includeHome)
            addCandidate(candidates, seenOffsets, role, front, forwardExtent, lateralExtent, 0, 0);
        for (int radius = MIN_RADIUS; radius <= MAX_RADIUS; radius += RADIUS_STEP) {
            for (int direction = 0; direction < CANDIDATE_DIRECTIONS; direction++) {
                double angle = direction * Math.PI * 2 / CANDIDATE_DIRECTIONS;
                int x = (int) Math.round(Math.cos(angle) * radius);
                int z = (int) Math.round(Math.sin(angle) * radius);
                addCandidate(candidates, seenOffsets, role, front,
                        forwardExtent, lateralExtent, x, z);
            }
        }
        candidates.sort(Comparator
                .comparing(Candidate::blocksExitLane)
                .thenComparingDouble(Candidate::roleScore)
                .thenComparingInt(Candidate::distanceSqr)
                .thenComparingInt(Candidate::xOffset)
                .thenComparingInt(Candidate::zOffset));
        return candidates;
    }

    private static void addCandidate(List<Candidate> candidates, Set<Long> seenOffsets,
                                     PlacementRole role, Axis front,
                                     double forwardExtent, double lateralExtent,
                                     int x, int z) {
        long key = ((long) x << 32) ^ (z & 0xffffffffL);
        if (seenOffsets.add(key)) {
            int distanceSqr = x * x + z * z;
            double forwardDistance = x * front.x() + z * front.z();
            double lateralDistance = -x * front.z() + z * front.x();
            candidates.add(new Candidate(
                    x,
                    z,
                    role != PlacementRole.CAPITOL
                            && blocksExitLane(forwardDistance, forwardExtent,
                            lateralDistance, lateralExtent),
                    role.score(forwardDistance, lateralDistance),
                    distanceSqr
            ));
        }
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

    static boolean blocksExitLane(double forwardDistance, double forwardExtent,
                                  double lateralDistance, double lateralExtent) {
        return forwardDistance + forwardExtent > 0
                && Math.abs(lateralDistance) - lateralExtent < EXIT_LANE_HALF_WIDTH;
    }

    static boolean canPlace(ServerLevel level, Building building, Footprint footprint,
                            BotWorldView worldView) {
        return canPlace(level, building, building.getRelativeBlockData(level), footprint, worldView);
    }

    private static boolean canPlace(ServerLevel level, Building building,
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

        if (BuildingUtils.requiresNetherTerrain(building)) {
            ArrayList<BuildingBlock> blocks = BuildingUtils.getAbsoluteBlockData(
                    relativeBlocks, level, origin, Rotation.NONE);
            if (!BuildingServerEvents.isOnNetherBlocks(blocks, origin, level))
                return false;
        }

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
