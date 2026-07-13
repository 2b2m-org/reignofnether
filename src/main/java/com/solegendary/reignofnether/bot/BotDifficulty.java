package com.solegendary.reignofnether.bot;

import java.util.Locale;
import java.util.Optional;

public enum BotDifficulty {
    EASY(60, 4, 1, 8, 4, 0, 300, 400, 1),
    MEDIUM(20, 5, 2, 12, 8, 4, 100, 200, 2),
    HARD(20, 9, 3, 16, 16, 8, 40, 100, 2);

    private final int decisionIntervalTicks;
    private final int targetWorkers;
    private final int supplyLookaheadUnits;
    private final int targetArmySize;
    private final int attackThreshold;
    private final int retreatThreshold;
    private final int workerReconcileTicks;
    private final int attackRefreshTicks;
    private final int maxProductionQueue;

    BotDifficulty(int decisionIntervalTicks, int targetWorkers, int supplyLookaheadUnits,
                  int targetArmySize, int attackThreshold, int retreatThreshold,
                  int workerReconcileTicks, int attackRefreshTicks, int maxProductionQueue) {
        this.decisionIntervalTicks = decisionIntervalTicks;
        this.targetWorkers = targetWorkers;
        this.supplyLookaheadUnits = supplyLookaheadUnits;
        this.targetArmySize = targetArmySize;
        this.attackThreshold = attackThreshold;
        this.retreatThreshold = retreatThreshold;
        this.workerReconcileTicks = workerReconcileTicks;
        this.attackRefreshTicks = attackRefreshTicks;
        this.maxProductionQueue = maxProductionQueue;
    }

    public int decisionIntervalTicks() {
        return decisionIntervalTicks;
    }

    public int targetWorkers() {
        return targetWorkers;
    }

    public int supplyLookaheadUnits() {
        return supplyLookaheadUnits;
    }

    public int targetArmySize() {
        return targetArmySize;
    }

    public int attackThreshold() {
        return attackThreshold;
    }

    public int retreatThreshold() {
        return retreatThreshold;
    }

    public int workerReconcileTicks() {
        return workerReconcileTicks;
    }

    public int attackRefreshTicks() {
        return attackRefreshTicks;
    }

    public int maxProductionQueue() {
        return maxProductionQueue;
    }

    public static Optional<BotDifficulty> fromName(String value) {
        if (value == null || value.isBlank())
            return Optional.empty();
        try {
            return Optional.of(valueOf(value.toUpperCase(Locale.ROOT)));
        } catch (IllegalArgumentException ignored) {
            return Optional.empty();
        }
    }
}
