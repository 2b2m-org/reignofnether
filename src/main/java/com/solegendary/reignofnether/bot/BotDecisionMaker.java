package com.solegendary.reignofnether.bot;

public final class BotDecisionMaker {
    public static final int TARGET_WORKERS = 5;
    public static final int SUPPLY_HEADROOM = 2;

    private BotDecisionMaker() {
    }

    public static BotGoal chooseGoal(BotDecisionContext context) {
        if (!context.capitolPresent() || !context.capitolBuilt())
            return BotGoal.WAIT_FOR_CAPITOL;
        if (!context.supplyUnderConstruction()
                && context.populationSupply() - context.population() <= SUPPLY_HEADROOM)
            return BotGoal.BUILD_SUPPLY;
        if (context.workersAndQueued() < TARGET_WORKERS)
            return BotGoal.TRAIN_WORKER;
        if (!context.farmPresent())
            return BotGoal.BUILD_FARM;
        if (!context.militaryPresent())
            return BotGoal.BUILD_MILITARY;
        if (!context.militaryReady())
            return BotGoal.WAIT_FOR_MILITARY;
        return BotGoal.TRAIN_ARMY;
    }
}
