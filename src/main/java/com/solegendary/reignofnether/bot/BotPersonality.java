package com.solegendary.reignofnether.bot;

import java.util.Locale;
import java.util.Optional;

public enum BotPersonality {
    STEADY(0, 0, 0, 0, 2, 5),
    RUSHER(-1, -6, -6, -6, 1, 4),
    TURTLE(1, 6, 6, 6, 3, 5);

    private final int workerOffset;
    private final int armyPopulationOffset;
    private final int attackPopulationOffset;
    private final int retreatPopulationOffset;
    private final int rangedNumerator;
    private final int compositionDenominator;

    BotPersonality(int workerOffset, int armyPopulationOffset, int attackPopulationOffset,
                   int retreatPopulationOffset,
                   int rangedNumerator, int compositionDenominator) {
        this.workerOffset = workerOffset;
        this.armyPopulationOffset = armyPopulationOffset;
        this.attackPopulationOffset = attackPopulationOffset;
        this.retreatPopulationOffset = retreatPopulationOffset;
        this.rangedNumerator = rangedNumerator;
        this.compositionDenominator = compositionDenominator;
    }

    int workerOffset() {
        return workerOffset;
    }

    int armyPopulationOffset() {
        return armyPopulationOffset;
    }

    int attackPopulationOffset() {
        return attackPopulationOffset;
    }

    int retreatPopulationOffset() {
        return retreatPopulationOffset;
    }

    int rangedNumerator() {
        return rangedNumerator;
    }

    int compositionDenominator() {
        return compositionDenominator;
    }

    public static Optional<BotPersonality> fromName(String value) {
        if (value == null || value.isBlank())
            return Optional.empty();
        try {
            return Optional.of(valueOf(value.toUpperCase(Locale.ROOT)));
        } catch (IllegalArgumentException ignored) {
            return Optional.empty();
        }
    }
}
