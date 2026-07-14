package com.solegendary.reignofnether.bot;

public record BotDecisionContext(
        boolean capitolPresent,
        boolean capitolBuilt,
        int workersAndQueued,
        int armyAndQueuedPopulation,
        int population,
        int populationSupply,
        boolean supplyUnderConstruction,
        int workerPopulationCost,
        int armyPopulationCost,
        boolean farmPresent,
        boolean militaryPresent,
        boolean militaryReady
) {
}
