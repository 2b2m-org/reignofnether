package com.solegendary.reignofnether.bot;

import com.solegendary.reignofnether.ReignOfNether;
import com.solegendary.reignofnether.building.BuildingPlacement;
import com.solegendary.reignofnether.building.BuildingServerEvents;
import com.solegendary.reignofnether.building.buildings.placements.ProductionPlacement;
import com.solegendary.reignofnether.player.RTSPlayer;
import com.solegendary.reignofnether.unit.UnitAction;
import com.solegendary.reignofnether.unit.UnitServerEvents;
import com.solegendary.reignofnether.unit.interfaces.AttackerUnit;
import com.solegendary.reignofnether.unit.interfaces.RangedAttackerUnit;
import com.solegendary.reignofnether.unit.interfaces.Unit;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;

import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

final class BotArmy {
    private static final int TARGET_REVISIT_TICKS = 600;
    private static final int OBJECTIVE_STALL_TICKS = 600;
    private static final int DEFENSE_MEMORY_TICKS = 200;
    private static final int REGROUP_TICKS = 200;
    private static final double HOME_INTERCEPTION_DISTANCE_SQR = 64 * 64;
    private static final double ARMY_INTERCEPTION_DISTANCE_SQR = 48 * 48;
    private static final double OBJECTIVE_PROGRESS_DISTANCE = 2;
    private static final double SCOUT_REACHED_DISTANCE_SQR = 144;
    private static final int[][] SCOUT_DIRECTIONS = {
            {1, 0}, {0, 1}, {-1, 0}, {0, -1},
            {1, 1}, {-1, 1}, {-1, -1}, {1, -1}
    };

    private final String ownerName;
    private final BotSelf self;
    private final BotWorldView worldView;
    private final Map<BlockPos, Integer> targetCooldowns = new HashMap<>();
    private final Map<BuildingPlacement, Integer> ownedBuildingBlockCounts = new HashMap<>();
    private final Map<BlockPos, Integer> homeThreats = new HashMap<>();
    private AttackObjective objective;
    private double objectiveBestDistance;
    private int objectiveFewestBlocks;
    private int objectiveLastProgressTick;
    private int regroupUntilTick;
    private int scoutUnitId = -1;
    private BlockPos scoutTarget;
    private double scoutBestDistance;
    private int scoutLastProgressTick;
    private int scoutWaypointIndex;
    private int nextCommandTick;

    BotArmy(String ownerName, BotSelf self, BotWorldView worldView) {
        this.ownerName = ownerName;
        this.self = self;
        this.worldView = worldView;
    }

    void tick(ServerLevel level, RTSPlayer player, BotStrategy strategy,
              BotDifficulty difficulty, BotPersonality personality) {
        int tick = level.getServer().getTickCount();
        List<LivingEntity> visibleEnemyCombatants = worldView.visibleEnemyCombatants(player);
        observeHomeDamage(visibleEnemyCombatants, tick);
        if (tick < nextCommandTick)
            return;
        command(level, player, strategy, difficulty, personality, visibleEnemyCombatants, tick);
    }

    private void command(ServerLevel level, RTSPlayer player, BotStrategy strategy,
                         BotDifficulty difficulty, BotPersonality personality,
                         List<LivingEntity> visibleEnemyCombatants, int tick) {
        targetCooldowns.entrySet().removeIf(entry -> entry.getValue() <= tick);
        homeThreats.entrySet().removeIf(entry -> entry.getValue() < tick);

        List<LivingEntity> fullArmy = self.army();
        if (fullArmy.isEmpty()) {
            clearObjective();
            clearScout();
            nextCommandTick = tick + difficulty.attackRefreshTicks();
            return;
        }

        LivingEntity scout = reconcileScout(fullArmy);
        if (scout != null)
            commandScout(level, player, scout, tick);
        List<LivingEntity> main = scout == null
                ? fullArmy
                : fullArmy.stream().filter(entity -> entity != scout).toList();
        if (main.isEmpty()) {
            nextCommandTick = tick + difficulty.attackRefreshTicks();
            return;
        }

        BlockPos armyPos = armyCentroidRepresentative(main);
        BuildingPlacement defenseTarget = selectDefenseTarget(strategy, player.aiHomePos, tick);
        boolean attackCommitted = objective != null;
        boolean homeThreat = defenseTarget != null && BotDecisionMaker.shouldDefend(
                personality,
                attackCommitted,
                defenseTarget.isCapitol || defenseTarget == self.militaryBuilding(strategy)
        );
        List<LivingEntity> tacticalEnemyArmy = tacticalEnemyArmy(
                visibleEnemyCombatants, armyPos, player.aiHomePos);
        int mainPopulation = BotSelf.population(main);
        int tacticalEnemyPopulation = BotSelf.population(tacticalEnemyArmy);
        boolean hasRangedResponder = main.stream().anyMatch(RangedAttackerUnit.class::isInstance);
        boolean rangedFlyingThreat = hasRangedResponder
                && tacticalEnemyArmy.stream().anyMatch(BotArmy::isFlying);

        if (tick < regroupUntilTick) {
            attackMoveArmy(main, player.aiHomePos.above());
            nextCommandTick = tick + difficulty.attackRefreshTicks();
            return;
        }

        BotDecisionMaker.ArmyOrder armyOrder = BotDecisionMaker.chooseArmyOrder(
                difficulty, personality, mainPopulation, tacticalEnemyPopulation,
                attackCommitted, homeThreat);
        if (armyOrder == BotDecisionMaker.ArmyOrder.DEFEND) {
            BlockPos target = defenseTarget.getClosestGroundPos(armyPos, 1);
            if (rangedFlyingThreat)
                engageEnemyArmy(main, target,
                        selectRangedTarget(tacticalEnemyArmy, armyPos, player.aiHomePos));
            else
                attackMoveArmy(main, target);
            nextCommandTick = tick + difficulty.attackRefreshTicks();
            return;
        }
        if (armyOrder == BotDecisionMaker.ArmyOrder.RETREAT) {
            abandonObjective(tick);
            regroupUntilTick = tick + REGROUP_TICKS;
            attackMoveArmy(main, player.aiHomePos.above());
            ReignOfNether.LOGGER.info("[Bot] {} regrouping with {} units", ownerName, main.size());
            nextCommandTick = tick + difficulty.attackRefreshTicks();
            return;
        }
        if (armyOrder == BotDecisionMaker.ArmyOrder.HOLD) {
            attackMoveArmy(main, player.aiHomePos.above());
            nextCommandTick = tick + difficulty.attackRefreshTicks();
            return;
        }

        if (shouldEngageEnemyArmy(tacticalEnemyArmy, mainPopulation, player.aiHomePos,
                attackCommitted)) {
            if (objective != null)
                objectiveLastProgressTick = tick;
            LivingEntity groundTarget = selectGroundTarget(
                    tacticalEnemyArmy, armyPos, player.aiHomePos);
            BlockPos attackMoveTarget = groundTarget != null
                    ? groundTarget.blockPosition()
                    : objective != null ? objective.anchor() : player.aiHomePos.above();
            LivingEntity rangedTarget = hasRangedResponder
                    ? selectRangedTarget(tacticalEnemyArmy, armyPos, player.aiHomePos)
                    : null;
            engageEnemyArmy(main, attackMoveTarget, rangedTarget);
            nextCommandTick = tick + difficulty.attackRefreshTicks();
            return;
        }

        BotWorldView.KnownEnemyBuilding target = resolveObjective(player);
        if (objective != null && target == null)
            clearObjective();
        if (objective == null) {
            target = selectEnemyBuilding(player, personality, armyPos);
            if (target != null)
                startObjective(target, armyPos, tick, main.size());
        }

        if (target == null) {
            attackMoveArmy(main, player.aiHomePos.above());
            nextCommandTick = tick + difficulty.attackRefreshTicks();
            return;
        }

        updateObjectiveProgress(target, main, tick);
        if (tick - objectiveLastProgressTick >= OBJECTIVE_STALL_TICKS) {
            ReignOfNether.LOGGER.info("[Bot] {} abandoning stalled target at {}", ownerName, objective.origin());
            abandonObjective(tick);
            attackMoveArmy(main, player.aiHomePos.above());
            nextCommandTick = tick + difficulty.attackRefreshTicks();
            return;
        }

        attackMoveArmy(main, objective.anchor());
        nextCommandTick = tick + difficulty.attackRefreshTicks();
    }

    private LivingEntity reconcileScout(List<LivingEntity> army) {
        if (!worldView.fogEnabled() || army.size() < 2) {
            clearScout();
            return null;
        }
        LivingEntity current = army.stream()
                .filter(entity -> entity.getId() == scoutUnitId)
                .findFirst()
                .orElse(null);
        if (current != null)
            return current;

        LivingEntity scout = army.stream()
                .min(Comparator
                        .comparingInt((LivingEntity entity) -> BotSelf.population(entity))
                        .thenComparingInt(LivingEntity::getId))
                .orElseThrow();
        scoutUnitId = scout.getId();
        scoutTarget = null;
        return scout;
    }

    private void commandScout(ServerLevel level, RTSPlayer player, LivingEntity scout, int tick) {
        List<LivingEntity> scoutGroup = List.of(scout);
        if (scoutTarget != null && worldView.isVisible(scoutTarget)
                && BotBuildingPlanner.isChunkLoaded(level, scoutTarget)) {
            BlockPos ground = BotBuildingPlanner.groundAt(level, scoutTarget.getX(), scoutTarget.getZ());
            if (Math.abs(ground.getY() - player.aiHomePos.getY()) > 8) {
                scoutTarget = null;
            } else {
                BlockPos adjustedTarget = ground.above();
                if (!adjustedTarget.equals(scoutTarget)) {
                    scoutTarget = adjustedTarget;
                    resetScoutProgress(scout, tick);
                    attackMoveArmy(scoutGroup, scoutTarget);
                }
            }
        }

        if (scoutTarget != null) {
            double currentDistance = Math.sqrt(scout.blockPosition().distSqr(scoutTarget));
            BotDecisionMaker.ScoutWaypointDecision decision = BotDecisionMaker.evaluateScoutWaypoint(
                    currentDistance * currentDistance <= SCOUT_REACHED_DISTANCE_SQR,
                    scoutBestDistance,
                    currentDistance,
                    tick - scoutLastProgressTick
            );
            if (decision == BotDecisionMaker.ScoutWaypointDecision.PROGRESS) {
                scoutBestDistance = currentDistance;
                scoutLastProgressTick = tick;
                return;
            }
            if (decision == BotDecisionMaker.ScoutWaypointDecision.KEEP)
                return;
        }

        scoutTarget = nextScoutTarget(level, player.aiHomePos);
        resetScoutProgress(scout, tick);
        attackMoveArmy(scoutGroup, scoutTarget);
        ReignOfNether.LOGGER.info("[Bot] {} scouting at {}", ownerName, scoutTarget);
    }

    private void observeHomeDamage(List<LivingEntity> visibleEnemies, int tick) {
        List<BuildingPlacement> ownedBuildings = BuildingServerEvents.getBuildings().stream()
                .filter(building -> building.ownerName.equals(ownerName))
                .toList();
        ownedBuildingBlockCounts.keySet().removeIf(building -> !ownedBuildings.contains(building));
        homeThreats.keySet().removeIf(origin -> ownedBuildings.stream()
                .noneMatch(building -> building.originPos.equals(origin)));

        for (BuildingPlacement building : ownedBuildings) {
            int blocksPlaced = building.getBlocksPlaced();
            Integer previous = ownedBuildingBlockCounts.put(building, blocksPlaced);
            if (previous == null || blocksPlaced >= previous)
                continue;
            boolean enemyNearby = visibleEnemies.stream().anyMatch(enemy ->
                    enemy.blockPosition().distSqr(building.centrePos) <= HOME_INTERCEPTION_DISTANCE_SQR);
            if (!enemyNearby)
                continue;

            Integer previousExpiry = homeThreats.put(building.originPos, tick + DEFENSE_MEMORY_TICKS);
            if (previousExpiry == null || previousExpiry < tick) {
                ReignOfNether.LOGGER.info("[Bot] {} defending damaged {} at {}",
                        ownerName, building.getBuilding().name, building.originPos);
            }
        }
    }

    private BuildingPlacement selectDefenseTarget(BotStrategy strategy, BlockPos home, int tick) {
        ProductionPlacement military = self.militaryBuilding(strategy);
        return BuildingServerEvents.getBuildings().stream()
                .filter(building -> building.ownerName.equals(ownerName))
                .filter(building -> homeThreats.getOrDefault(building.originPos, -1) >= tick)
                .min(Comparator
                        .comparingInt((BuildingPlacement building) ->
                                building.isCapitol || building == military ? 0 : 1)
                        .thenComparingDouble(building -> building.centrePos.distSqr(home))
                        .thenComparingInt(building -> building.originPos.getX())
                        .thenComparingInt(building -> building.originPos.getY())
                        .thenComparingInt(building -> building.originPos.getZ()))
                .orElse(null);
    }

    private BotWorldView.KnownEnemyBuilding selectEnemyBuilding(RTSPlayer player, BotPersonality personality,
                                                                 BlockPos armyPos) {
        return worldView.knownEnemyBuildings(player).stream()
                .filter(building -> !building.invulnerable())
                .filter(building -> !targetCooldowns.containsKey(building.origin()))
                .min(Comparator
                        .comparingInt((BotWorldView.KnownEnemyBuilding building) ->
                                BotDecisionMaker.targetPriority(
                                        personality, building.capitol(), building.production()))
                        .thenComparingDouble(building -> building.centre().distSqr(armyPos))
                        .thenComparingInt(building -> building.origin().getX())
                        .thenComparingInt(building -> building.origin().getY())
                        .thenComparingInt(building -> building.origin().getZ()))
                .orElse(null);
    }

    private void startObjective(BotWorldView.KnownEnemyBuilding target, BlockPos armyPos, int tick, int armySize) {
        BlockPos anchor = target.closestGroundPos(armyPos, 1);
        objective = new AttackObjective(target.origin(), anchor, target.ownerName());
        objectiveBestDistance = Math.sqrt(armyPos.distSqr(anchor));
        objectiveFewestBlocks = target.blocksPlaced();
        objectiveLastProgressTick = tick;
        ReignOfNether.LOGGER.info("[Bot] {} attacking {} at {} with {} units",
                ownerName, target.ownerName(), target.origin(), armySize);
    }

    private BotWorldView.KnownEnemyBuilding resolveObjective(RTSPlayer player) {
        if (objective == null || !BotWorldView.isPotentialEnemy(player, objective.targetOwner()))
            return null;
        BotWorldView.KnownEnemyBuilding target = worldView.knownEnemyBuilding(
                player, objective.origin(), objective.targetOwner());
        return target == null || target.invulnerable() ? null : target;
    }

    private void updateObjectiveProgress(BotWorldView.KnownEnemyBuilding target, List<LivingEntity> army, int tick) {
        double distance = closestArmyDistance(army, objective.anchor());
        int blocks = target.blocksPlaced();
        if (distance + OBJECTIVE_PROGRESS_DISTANCE < objectiveBestDistance || blocks < objectiveFewestBlocks) {
            objectiveBestDistance = Math.min(objectiveBestDistance, distance);
            objectiveFewestBlocks = Math.min(objectiveFewestBlocks, blocks);
            objectiveLastProgressTick = tick;
        }
    }

    private void abandonObjective(int tick) {
        if (objective != null)
            targetCooldowns.put(objective.origin(), tick + TARGET_REVISIT_TICKS);
        clearObjective();
    }

    private void clearObjective() {
        objective = null;
        objectiveBestDistance = 0;
        objectiveFewestBlocks = 0;
        objectiveLastProgressTick = 0;
    }

    private boolean shouldEngageEnemyArmy(List<LivingEntity> enemies, int armyPopulation, BlockPos homePos,
                                          boolean attackCommitted) {
        List<LivingEntity> groundEnemies = enemies.stream().filter(enemy -> !isFlying(enemy)).toList();
        boolean homeThreat = groundEnemies.stream().anyMatch(enemy ->
                enemy.blockPosition().distSqr(homePos) <= HOME_INTERCEPTION_DISTANCE_SQR);
        int enemyPopulation = BotSelf.population(groundEnemies);
        return BotDecisionMaker.shouldFocusEnemyArmy(
                homeThreat, attackCommitted, armyPopulation, enemyPopulation);
    }

    private LivingEntity selectGroundTarget(List<LivingEntity> enemies, BlockPos armyPos, BlockPos homePos) {
        return enemies.stream()
                .filter(enemy -> !isFlying(enemy))
                .min(Comparator
                        .comparingInt((LivingEntity enemy) ->
                                enemy.blockPosition().distSqr(homePos) <= HOME_INTERCEPTION_DISTANCE_SQR ? 0 : 1)
                        .thenComparingDouble(enemy -> enemy.blockPosition().distSqr(armyPos))
                        .thenComparingDouble(LivingEntity::getHealth)
                        .thenComparingInt(LivingEntity::getId))
                .orElse(null);
    }

    private LivingEntity selectRangedTarget(List<LivingEntity> enemies, BlockPos armyPos, BlockPos homePos) {
        return enemies.stream()
                .min(Comparator
                        .comparingInt((LivingEntity enemy) -> BotDecisionMaker.rangedTargetPriority(
                                enemy.blockPosition().distSqr(homePos) <= HOME_INTERCEPTION_DISTANCE_SQR,
                                isFlying(enemy)))
                        .thenComparingDouble(enemy -> enemy.blockPosition().distSqr(armyPos))
                        .thenComparingDouble(LivingEntity::getHealth)
                        .thenComparingInt(LivingEntity::getId))
                .orElse(null);
    }

    private static boolean isFlying(LivingEntity entity) {
        return entity instanceof Unit unit && unit.isFlyingUnit();
    }

    private static List<LivingEntity> tacticalEnemyArmy(List<LivingEntity> enemies, BlockPos armyPos,
                                                         BlockPos homePos) {
        return enemies.stream()
                .filter(enemy -> enemy.blockPosition().distSqr(homePos) <= HOME_INTERCEPTION_DISTANCE_SQR
                        || enemy.blockPosition().distSqr(armyPos) <= ARMY_INTERCEPTION_DISTANCE_SQR)
                .toList();
    }

    private BlockPos nextScoutTarget(ServerLevel level, BlockPos home) {
        int[][] directions = SCOUT_DIRECTIONS.clone();
        double inwardX = level.getWorldBorder().getCenterX() - home.getX();
        double inwardZ = level.getWorldBorder().getCenterZ() - home.getZ();
        Arrays.sort(directions, Comparator.<int[]>comparingDouble(
                direction -> direction[0] * inwardX + direction[1] * inwardZ).reversed());
        for (int attempt = 0; attempt < SCOUT_DIRECTIONS.length * 4; attempt++) {
            int index = scoutWaypointIndex++ % (SCOUT_DIRECTIONS.length * 4);
            int[] direction = directions[index % directions.length];
            int radius = 128 * (index / SCOUT_DIRECTIONS.length + 1);
            BlockPos column = new BlockPos(
                    home.getX() + direction[0] * radius,
                    home.getY(),
                    home.getZ() + direction[1] * radius
            );
            if (!level.getWorldBorder().isWithinBounds(column))
                continue;
            if (!worldView.isVisible(column) || !BotBuildingPlanner.isChunkLoaded(level, column))
                return column.above();
            BlockPos ground = BotBuildingPlanner.groundAt(level, column.getX(), column.getZ());
            if (Math.abs(ground.getY() - home.getY()) <= 8)
                return ground.above();
        }
        return home.above();
    }

    private void resetScoutProgress(LivingEntity scout, int tick) {
        scoutBestDistance = Math.sqrt(scout.blockPosition().distSqr(scoutTarget));
        scoutLastProgressTick = tick;
    }

    private void clearScout() {
        scoutUnitId = -1;
        scoutTarget = null;
        scoutBestDistance = 0;
        scoutLastProgressTick = 0;
    }

    private static double closestArmyDistance(List<LivingEntity> army, BlockPos target) {
        return army.stream()
                .mapToDouble(entity -> Math.sqrt(entity.blockPosition().distSqr(target)))
                .min()
                .orElse(Double.POSITIVE_INFINITY);
    }

    private static BlockPos armyCentroidRepresentative(List<LivingEntity> army) {
        long x = 0;
        long y = 0;
        long z = 0;
        for (LivingEntity entity : army) {
            BlockPos pos = entity.blockPosition();
            x += pos.getX();
            y += pos.getY();
            z += pos.getZ();
        }
        BlockPos mean = new BlockPos(
                (int) (x / army.size()),
                (int) (y / army.size()),
                (int) (z / army.size())
        );
        return army.stream()
                .min(Comparator.comparingDouble(entity -> entity.blockPosition().distSqr(mean)))
                .orElseThrow()
                .blockPosition();
    }

    private void attackMoveArmy(List<LivingEntity> army, BlockPos target) {
        int[] ids = army.stream()
                .filter(entity -> !(entity instanceof AttackerUnit attacker)
                        || !target.equals(attacker.getAttackMoveTarget()))
                .mapToInt(LivingEntity::getId)
                .toArray();
        if (ids.length == 0)
            return;
        UnitServerEvents.addActionItem(
                ownerName,
                UnitAction.ATTACK_MOVE,
                -1,
                ids,
                target,
                BlockPos.ZERO
        );
    }

    private void engageEnemyArmy(List<LivingEntity> army, BlockPos attackMoveTarget,
                                 LivingEntity rangedTarget) {
        List<LivingEntity> attackMoveUnits = army.stream()
                .filter(entity -> !(entity instanceof RangedAttackerUnit))
                .toList();
        attackMoveArmy(attackMoveUnits, attackMoveTarget);

        if (rangedTarget == null)
            return;

        int[] rangedIds = army.stream()
                .filter(RangedAttackerUnit.class::isInstance)
                .filter(entity -> !(entity instanceof Unit unit)
                        || !(entity instanceof Mob mob)
                        || mob.getTarget() != rangedTarget
                        || !unit.getTargetGoal().forced)
                .mapToInt(LivingEntity::getId)
                .toArray();
        if (rangedIds.length == 0)
            return;
        UnitServerEvents.addActionItem(
                ownerName,
                UnitAction.ATTACK,
                rangedTarget.getId(),
                rangedIds,
                rangedTarget.blockPosition(),
                BlockPos.ZERO
        );
    }

    private record AttackObjective(BlockPos origin, BlockPos anchor, String targetOwner) {
    }
}
