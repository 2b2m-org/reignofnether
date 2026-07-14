package com.solegendary.reignofnether.bot;

import java.util.Locale;
import java.util.Optional;

public enum BotDifficulty {
    EASY(60, 4, 1, 24, 12, 300, 400, 1),
    MEDIUM(20, 5, 2, 36, 24, 100, 200, 2),
    HARD(10, 9, 3, 48, 36, 40, 100, 2);

    private final int decisionIntervalTicks;
    private final int targetWorkers;
    private final int supplyLookaheadUnits;
    private final int targetArmyPopulation;
    private final int attackPopulation;
    private final int workerReconcileTicks;
    private final int attackRefreshTicks;
    private final int maxProductionQueue;

    BotDifficulty(int decisionIntervalTicks, int targetWorkers, int supplyLookaheadUnits,
                  int targetArmyPopulation, int attackPopulation,
                  int workerReconcileTicks, int attackRefreshTicks, int maxProductionQueue) {
        this.decisionIntervalTicks = decisionIntervalTicks;
        this.targetWorkers = targetWorkers;
        this.supplyLookaheadUnits = supplyLookaheadUnits;
        this.targetArmyPopulation = targetArmyPopulation;
        this.attackPopulation = attackPopulation;
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

    public int targetArmyPopulation() {
        return targetArmyPopulation;
    }

    public int attackPopulation() {
        return attackPopulation;
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
