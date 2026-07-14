package com.solegendary.reignofnether.bot;

import com.solegendary.reignofnether.resources.ResourceCost;
import com.solegendary.reignofnether.resources.Resources;
import com.solegendary.reignofnether.time.TimeUtils;
import com.solegendary.reignofnether.unit.UnitAction;

public final class BotDecisionMaker {
    private static final int BLOCKS_PER_CHUNK = 16;
    private static final double SCOUT_PROGRESS_BLOCKS = 4;
    private static final int SCOUT_STALL_TICKS = 600;
    private static final int WORKER_DANGER_DISTANCE_SQR = 24 * 24;
    private static final int WORKER_FLEE_DISTANCE_SQR = 32 * 32;
    private static final int MIN_VISIBLE_ADVANTAGE_POPULATION = 12;
    private static final long MONSTER_RECALL_TIME = 22000;

    public enum ArmyUnitChoice {
        NONE,
        MELEE,
        RANGED
    }

    public enum ScoutWaypointDecision {
        KEEP,
        PROGRESS,
        REPLACE
    }

    public enum ArmyOrder {
        HOLD,
        DEFEND,
        ATTACK_MOVE,
        RETREAT
    }

    public enum BeaconControl {
        ABSENT,
        NEUTRAL,
        OWNED,
        ALLIED,
        HOSTILE
    }

    public enum BeaconOrder {
        NONE,
        CAPTURE,
        CONTEST,
        GARRISON
    }

    private BotDecisionMaker() {
    }

    public static BotGoal chooseGoal(BotDifficulty difficulty, BotPersonality personality,
                                     BotDecisionContext context) {
        if (!context.capitolPresent())
            return BotGoal.BUILD_CAPITOL;
        if (!context.capitolBuilt())
            return BotGoal.WAIT_FOR_CAPITOL;

        int targetWorkers = targetWorkers(difficulty, personality);
        int initialWorkers = Math.min(targetWorkers, 5);
        int nextPopulationCost = context.militaryReady()
                ? context.armyPopulationCost()
                : context.workerPopulationCost();
        int requiredHeadroom = nextPopulationCost * difficulty.supplyLookaheadUnits();
        if (!context.supplyUnderConstruction()
                && context.populationSupply() - context.population() < requiredHeadroom)
            return BotGoal.BUILD_SUPPLY;
        if (context.workersAndQueued() < initialWorkers)
            return BotGoal.TRAIN_WORKER;
        if (!context.farmPresent())
            return BotGoal.BUILD_FARM;
        if (!context.militaryPresent())
            return BotGoal.BUILD_MILITARY;
        if (!context.militaryReady())
            return shouldTrainWorker(difficulty, personality,
                    context.workersAndQueued(), context.armyAndQueuedPopulation())
                    ? BotGoal.TRAIN_WORKER
                    : BotGoal.WAIT_FOR_MILITARY;
        if (shouldTrainWorker(difficulty, personality,
                context.workersAndQueued(), context.armyAndQueuedPopulation()))
            return BotGoal.TRAIN_WORKER;
        return BotGoal.TRAIN_ARMY;
    }

    static int openingArmyPopulation(BotDifficulty difficulty, BotPersonality personality) {
        if (difficulty != BotDifficulty.HARD)
            return 0;
        return switch (personality) {
            case RUSHER -> 8;
            case STEADY -> 12;
            case TURTLE -> 16;
        };
    }

    static boolean shouldTrainWorker(BotDifficulty difficulty, BotPersonality personality,
                                     int workersAndQueued, int armyAndQueuedPopulation) {
        int targetWorkers = targetWorkers(difficulty, personality);
        if (workersAndQueued >= targetWorkers)
            return false;
        int initialWorkers = Math.min(targetWorkers, 5);
        return workersAndQueued != initialWorkers
                || armyAndQueuedPopulation >= openingArmyPopulation(difficulty, personality);
    }

    public static int foodWorkerCount(BotDifficulty difficulty, BotPersonality personality,
                                      int totalWorkers, boolean productionPriority) {
        if (totalWorkers <= 1)
            return Math.max(totalWorkers, 0);
        return switch (difficulty) {
            case EASY -> totalWorkers - 1;
            case MEDIUM -> productionPriority
                    ? totalWorkers - 1
                    : Math.max(1, (totalWorkers * 3 + 4) / 5);
            case HARD -> productionPriority
                    ? Math.max(1, totalWorkers - (personality == BotPersonality.TURTLE ? 2 : 1))
                    : Math.max(Math.min(3, totalWorkers - 1), (totalWorkers * 4) / 9);
        };
    }

    static boolean fitsArmyPopulation(int armyPopulation, int unitPopulation, int targetPopulation) {
        return armyPopulation + unitPopulation <= targetPopulation;
    }

    static boolean canSpendAndPreserveReserve(Resources resources, ResourceCost purchase, ResourceCost reserve) {
        return resources != null
                && resources.food >= purchase.food + reserve.food
                && resources.wood >= purchase.wood + reserve.wood
                && resources.ore >= purchase.ore + reserve.ore;
    }

    static boolean shouldFleeWorker(boolean alreadyFleeing, double threatDistanceSqr) {
        int threshold = alreadyFleeing ? WORKER_FLEE_DISTANCE_SQR : WORKER_DANGER_DISTANCE_SQR;
        return threatDistanceSqr <= threshold;
    }

    static boolean shouldShelterMonsterArmy(long dayTime, boolean bloodMoonActive) {
        long time = TimeUtils.normaliseTime(dayTime);
        return !bloodMoonActive && (time >= MONSTER_RECALL_TIME || time <= TimeUtils.DUSK);
    }

    public static ArmyUnitChoice chooseArmyUnit(BotPersonality personality, int meleePopulation,
                                                 int rangedPopulation,
                                                 boolean canAffordMelee, boolean canAffordRanged) {
        int armyPopulation = meleePopulation + rangedPopulation;
        boolean preferRanged = armyPopulation > 0
                && rangedPopulation * personality.compositionDenominator()
                < armyPopulation * personality.rangedNumerator();

        if (preferRanged && canAffordRanged)
            return ArmyUnitChoice.RANGED;
        if (!preferRanged && canAffordMelee)
            return ArmyUnitChoice.MELEE;
        if (canAffordMelee)
            return ArmyUnitChoice.MELEE;
        if (canAffordRanged)
            return ArmyUnitChoice.RANGED;
        return ArmyUnitChoice.NONE;
    }

    public static int targetPriority(BotPersonality personality, boolean capitol, boolean productionBuilding) {
        return switch (personality) {
            case RUSHER -> 0;
            case STEADY -> capitol ? 0 : productionBuilding ? 1 : 2;
            case TURTLE -> capitol ? 1 : productionBuilding ? 0 : 2;
        };
    }

    public static ArmyOrder chooseArmyOrder(BotDifficulty difficulty, BotPersonality personality,
                                             int armyPopulation, int visibleEnemyPopulation,
                                             boolean attackReady,
                                             boolean homeThreat) {
        if (homeThreat)
            return ArmyOrder.DEFEND;
        int retreatPopulation = retreatPopulation(difficulty, personality);
        if (attackReady && armyPopulation < retreatPopulation
                && visibleEnemyPopulation >= armyPopulation)
            return ArmyOrder.RETREAT;
        if (attackReady || armyPopulation >= attackPopulation(difficulty, personality))
            return ArmyOrder.ATTACK_MOVE;
        return ArmyOrder.HOLD;
    }

    static boolean shouldPressVisibleAdvantage(BotDifficulty difficulty, BotPersonality personality,
                                                int armyPopulation, int visibleEnemyPopulation) {
        return difficulty == BotDifficulty.HARD
                && visibleEnemyPopulation >= MIN_VISIBLE_ADVANTAGE_POPULATION
                && armyPopulation >= targetArmyPopulation(difficulty, personality) - 12
                && armyPopulation * 4 >= visibleEnemyPopulation * 5;
    }

    static boolean shouldLaunchWithReservedUnits(BotDifficulty difficulty, BotPersonality personality,
                                                  int activePopulation, int reservedPopulation) {
        return activePopulation + reservedPopulation >= attackPopulation(difficulty, personality);
    }

    public static BeaconOrder chooseBeaconOrder(BotDifficulty difficulty, BotPersonality personality,
                                                 int armyPopulation, int knownEnemyPopulationInRing,
                                                 BeaconControl control) {
        if (armyPopulation <= 0)
            return BeaconOrder.NONE;
        return switch (control) {
            case ABSENT -> BeaconOrder.NONE;
            case OWNED -> BeaconOrder.GARRISON;
            case ALLIED -> BeaconOrder.NONE;
            case NEUTRAL, HOSTILE -> {
                if (armyPopulation > knownEnemyPopulationInRing)
                    yield BeaconOrder.CAPTURE;
                yield armyPopulation >= attackPopulation(difficulty, personality)
                        ? BeaconOrder.CONTEST
                        : BeaconOrder.NONE;
            }
        };
    }

    public static int beaconGuardPopulation(BotPersonality personality) {
        return switch (personality) {
            case RUSHER -> 3;
            case STEADY -> 6;
            case TURTLE -> 9;
        };
    }

    static boolean shouldReinforceBeacon(BotPersonality personality,
                                         int knownEnemyPopulationInRing,
                                         int guardPopulation) {
        if (knownEnemyPopulationInRing <= 0)
            return false;
        return switch (personality) {
            case RUSHER -> knownEnemyPopulationInRing > guardPopulation;
            case STEADY -> knownEnemyPopulationInRing >= guardPopulation;
            case TURTLE -> true;
        };
    }

    public static UnitAction beaconAuraAction(BotPersonality personality) {
        return switch (personality) {
            case RUSHER -> UnitAction.BEACON_STRENGTH;
            case STEADY -> UnitAction.BEACON_REGENERATION;
            case TURTLE -> UnitAction.BEACON_RESISTANCE;
        };
    }

    public static int targetWorkers(BotDifficulty difficulty, BotPersonality personality) {
        return Math.max(3, difficulty.targetWorkers() + personality.workerOffset());
    }

    public static int targetArmyPopulation(BotDifficulty difficulty, BotPersonality personality) {
        return difficulty.targetArmyPopulation() + personality.armyPopulationOffset();
    }

    public static int attackPopulation(BotDifficulty difficulty, BotPersonality personality) {
        return difficulty.attackPopulation() + personality.attackPopulationOffset();
    }

    public static int retreatPopulation(BotDifficulty difficulty, BotPersonality personality) {
        return Math.max(1, difficulty.retreatPopulation() + personality.retreatPopulationOffset());
    }

    public static boolean shouldDefend(BotPersonality personality, boolean attackReady,
                                       boolean survivalEnabled, boolean ownBuildingThreatened,
                                       boolean criticalBuildingThreatened) {
        if (!attackReady || personality == BotPersonality.TURTLE)
            return true;
        return survivalEnabled ? ownBuildingThreatened : criticalBuildingThreatened;
    }

    static boolean shouldRememberDefenseThreat(boolean survivalEnabled, boolean buildingDamaged,
                                               boolean enemyNearby) {
        return enemyNearby && (survivalEnabled || buildingDamaged);
    }

    static int defensePriority(boolean survivalEnabled, boolean ownBuilding, boolean criticalBuilding) {
        if (survivalEnabled)
            return (ownBuilding ? 0 : 2) + (criticalBuilding ? 0 : 1);
        return (criticalBuilding ? 0 : 2) + (ownBuilding ? 0 : 1);
    }

    public static boolean shouldFocusEnemyArmy(boolean enemyThreatensHome, boolean attackReady,
                                                int armyPopulation, int nearbyEnemyPopulation) {
        return nearbyEnemyPopulation > 0 && (enemyThreatensHome || !attackReady
                || nearbyEnemyPopulation * 3 >= armyPopulation);
    }

    static int rangedTargetPriority(boolean threatensHome, boolean flying) {
        return (flying ? 0 : 2) + (threatensHome ? 0 : 1);
    }

    static boolean shouldRepairBuilding(boolean underThreat, int currentWood, int reservedWood) {
        return !underThreat && currentWood > reservedWood;
    }

    static int repairTargetPriority(boolean capitol, boolean productionBuilding) {
        return capitol ? 0 : productionBuilding ? 1 : 2;
    }

    static int repairWoodReserve(int supplyBuildingWood, int supplyTransformWood, int farmWood,
                                 int militaryBuildingWood, int militaryTransformWood) {
        int supplyPackage = supplyBuildingWood + supplyTransformWood;
        int militaryPackage = militaryBuildingWood + militaryTransformWood;
        return Math.max(farmWood, Math.max(supplyPackage, militaryPackage));
    }

    public static ScoutWaypointDecision evaluateScoutWaypoint(boolean reached, double bestDistance,
                                                               double currentDistance, int ticksSinceProgress) {
        if (reached)
            return ScoutWaypointDecision.REPLACE;
        if (currentDistance <= bestDistance - SCOUT_PROGRESS_BLOCKS)
            return ScoutWaypointDecision.PROGRESS;
        if (ticksSinceProgress >= SCOUT_STALL_TICKS)
            return ScoutWaypointDecision.REPLACE;
        return ScoutWaypointDecision.KEEP;
    }

    static boolean reachedScoutWaypoint(int scoutX, int scoutZ, int waypointX, int waypointZ) {
        return Math.floorDiv(scoutX, BLOCKS_PER_CHUNK)
                == Math.floorDiv(waypointX, BLOCKS_PER_CHUNK)
                && Math.floorDiv(scoutZ, BLOCKS_PER_CHUNK)
                == Math.floorDiv(waypointZ, BLOCKS_PER_CHUNK);
    }
}
