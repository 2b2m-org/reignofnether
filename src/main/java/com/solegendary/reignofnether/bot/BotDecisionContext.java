package com.solegendary.reignofnether.bot;

public record BotDecisionContext(
        boolean capitolPresent,
        boolean capitolBuilt,
        int workersAndQueued,
        int population,
        int populationSupply,
        boolean supplyUnderConstruction,
        boolean farmPresent,
        boolean militaryPresent,
        boolean militaryReady
) {
}
