package com.solegendary.reignofnether.building;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;

class CaptureDecisionTest {

    @Test
    void strongestIndividualOwnerContestsCaptureBeforeAlliancePolicy() {
        assertAll(
                () -> assertEquals("AllyBot", CaptureDecision.highestPopulationOwner(
                        populations("AllyBot", 10, "Enemy", 1))),
                () -> assertEquals("Enemy", CaptureDecision.highestPopulationOwner(
                        populations("AllyBot", 1, "Enemy", 10))),
                () -> assertEquals("First", CaptureDecision.highestPopulationOwner(
                        populations("First", 5, "Second", 5)))
        );
    }

    private static Map<String, Integer> populations(
            String firstOwner, int firstPopulation,
            String secondOwner, int secondPopulation) {
        Map<String, Integer> populations = new LinkedHashMap<>();
        populations.put(firstOwner, firstPopulation);
        populations.put(secondOwner, secondPopulation);
        return populations;
    }
}
