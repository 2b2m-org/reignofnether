package com.solegendary.reignofnether.bot;

public final class BotDecisionMaker {
    public enum ArmyUnitChoice {
        NONE,
        MELEE,
        RANGED
    }

    private BotDecisionMaker() {
    }

    public static BotGoal chooseGoal(BotDifficulty difficulty, BotDecisionContext context) {
        if (!context.capitolPresent() || !context.capitolBuilt())
            return BotGoal.WAIT_FOR_CAPITOL;

        int nextPopulationCost = context.workersAndQueued() < difficulty.targetWorkers()
                ? context.workerPopulationCost()
                : context.armyPopulationCost();
        int requiredHeadroom = nextPopulationCost * difficulty.supplyLookaheadUnits();
        if (!context.supplyUnderConstruction()
                && context.populationSupply() - context.population() < requiredHeadroom)
            return BotGoal.BUILD_SUPPLY;
        if (context.workersAndQueued() < difficulty.targetWorkers())
            return BotGoal.TRAIN_WORKER;
        if (!context.farmPresent())
            return BotGoal.BUILD_FARM;
        if (!context.militaryPresent())
            return BotGoal.BUILD_MILITARY;
        if (!context.militaryReady())
            return BotGoal.WAIT_FOR_MILITARY;
        return BotGoal.TRAIN_ARMY;
    }

    public static int foodWorkerCount(BotDifficulty difficulty, int totalWorkers, boolean militaryReady) {
        if (totalWorkers <= 1)
            return Math.max(totalWorkers, 0);
        return switch (difficulty) {
            case EASY -> totalWorkers - 1;
            case MEDIUM -> Math.max(1, (totalWorkers * 3 + 4) / 5);
            case HARD -> militaryReady
                    ? Math.max(1, (totalWorkers * 2 + 2) / 3)
                    : Math.max(1, (totalWorkers * 4) / 9);
        };
    }

    public static ArmyUnitChoice chooseArmyUnit(BotDifficulty difficulty, int armyAndQueued,
                                                 boolean canAffordMelee, boolean canAffordRanged) {
        boolean preferRanged = switch (difficulty) {
            case EASY -> armyAndQueued % 4 == 3;
            case MEDIUM -> armyAndQueued % 3 == 2;
            case HARD -> armyAndQueued % 2 == 1;
        };

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

    public static int targetPriority(BotDifficulty difficulty, boolean capitol, boolean productionBuilding) {
        if (difficulty == BotDifficulty.EASY)
            return 0;
        if (capitol)
            return 0;
        if (difficulty == BotDifficulty.HARD && productionBuilding)
            return 1;
        return difficulty == BotDifficulty.HARD ? 2 : 1;
    }
}
