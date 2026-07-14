package com.solegendary.reignofnether.bot;

import com.solegendary.reignofnether.resources.ResourceCost;
import com.solegendary.reignofnether.resources.Resources;
import com.solegendary.reignofnether.unit.UnitAction;

public final class BotDecisionMaker {
    private static final double SCOUT_PROGRESS_BLOCKS = 4;
    private static final int SCOUT_STALL_TICKS = 600;
    private static final int WORKER_DANGER_DISTANCE_SQR = 24 * 24;
    private static final int WORKER_FLEE_DISTANCE_SQR = 32 * 32;

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
        if (!context.capitolPresent() || !context.capitolBuilt())
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
            return context.workersAndQueued() < targetWorkers
                    ? BotGoal.TRAIN_WORKER
                    : BotGoal.WAIT_FOR_MILITARY;
        if (context.workersAndQueued() < targetWorkers)
            return BotGoal.TRAIN_WORKER;
        return BotGoal.TRAIN_ARMY;
    }

    public static int foodWorkerCount(BotDifficulty difficulty, int totalWorkers, boolean economyComplete) {
        if (totalWorkers <= 1)
            return Math.max(totalWorkers, 0);
        return switch (difficulty) {
            case EASY -> totalWorkers - 1;
            case MEDIUM -> Math.max(1, (totalWorkers * 3 + 4) / 5);
            case HARD -> economyComplete
                    ? Math.max(1, (totalWorkers * 2 + 2) / 3)
                    : Math.max(1, (totalWorkers * 4) / 9);
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
        if (armyPopulation == 0 && canAffordRanged)
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
        if (attackReady && retreatPopulation > 0 && armyPopulation < retreatPopulation
                && visibleEnemyPopulation >= armyPopulation)
            return ArmyOrder.RETREAT;
        if (attackReady || armyPopulation >= attackPopulation(difficulty, personality))
            return ArmyOrder.ATTACK_MOVE;
        return ArmyOrder.HOLD;
    }

    public static BeaconOrder chooseBeaconOrder(BotDifficulty difficulty, BotPersonality personality,
                                                 int armyPopulation, int visibleEnemyPopulationInRing,
                                                 BeaconControl control) {
        if (armyPopulation <= 0)
            return BeaconOrder.NONE;
        return switch (control) {
            case ABSENT -> BeaconOrder.NONE;
            case OWNED -> BeaconOrder.GARRISON;
            case ALLIED -> BeaconOrder.NONE;
            case NEUTRAL, HOSTILE -> {
                if (armyPopulation > visibleEnemyPopulationInRing)
                    yield BeaconOrder.CAPTURE;
                yield armyPopulation >= attackPopulation(difficulty, personality)
                        ? BeaconOrder.CONTEST
                        : BeaconOrder.NONE;
            }
        };
    }

    public static int beaconGuardCount(BotPersonality personality) {
        return switch (personality) {
            case RUSHER -> 1;
            case STEADY -> 2;
            case TURTLE -> 3;
        };
    }

    static boolean shouldReinforceBeacon(BotPersonality personality,
                                         int visibleEnemyPopulationInRing,
                                         int guardPopulation) {
        if (visibleEnemyPopulationInRing <= 0)
            return false;
        return switch (personality) {
            case RUSHER -> visibleEnemyPopulationInRing > guardPopulation;
            case STEADY -> visibleEnemyPopulationInRing >= guardPopulation;
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
        return Math.max(0, difficulty.retreatPopulation() + personality.retreatPopulationOffset());
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
}
