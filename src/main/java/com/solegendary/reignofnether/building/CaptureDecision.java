package com.solegendary.reignofnether.building;

import java.util.Map;

final class CaptureDecision {
    private CaptureDecision() {
    }

    static String highestPopulationOwner(Map<String, Integer> populationByOwner) {
        String highestOwner = null;
        int highestPopulation = 0;
        for (Map.Entry<String, Integer> entry : populationByOwner.entrySet()) {
            if (entry.getValue() > highestPopulation) {
                highestOwner = entry.getKey();
                highestPopulation = entry.getValue();
            }
        }
        return highestOwner;
    }
}
