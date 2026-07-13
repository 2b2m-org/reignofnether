package com.solegendary.reignofnether.bot;

public final class BotDecisionMaker {
    private static final double SCOUT_PROGRESS_BLOCKS = 4;
    private static final int SCOUT_STALL_TICKS = 600;

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
                                             boolean attackCommitted,
                                             boolean homeThreat) {
        if (homeThreat)
            return ArmyOrder.DEFEND;
        int retreatPopulation = retreatPopulation(difficulty, personality);
        if (attackCommitted && retreatPopulation > 0 && armyPopulation < retreatPopulation
                && visibleEnemyPopulation >= armyPopulation)
            return ArmyOrder.RETREAT;
        if (attackCommitted || armyPopulation >= attackPopulation(difficulty, personality))
            return ArmyOrder.ATTACK_MOVE;
        return ArmyOrder.HOLD;
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

    public static boolean shouldDefend(BotPersonality personality, boolean attackCommitted,
                                       boolean criticalBuildingThreatened) {
        return !attackCommitted || criticalBuildingThreatened || personality == BotPersonality.TURTLE;
    }

    public static boolean shouldFocusEnemyArmy(boolean enemyThreatensHome, boolean attackCommitted,
                                                int armyPopulation, int nearbyEnemyPopulation) {
        return nearbyEnemyPopulation > 0 && (enemyThreatensHome || !attackCommitted
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
