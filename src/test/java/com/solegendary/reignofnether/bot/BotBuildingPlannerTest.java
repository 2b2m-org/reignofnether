package com.solegendary.reignofnether.bot;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BotBuildingPlannerTest {
    @Test
    void rolesPreferTheirSemanticSideOfTheBase() {
        assertTrue(BotBuildingPlanner.PlacementRole.MILITARY.score(18, 16)
                < BotBuildingPlanner.PlacementRole.MILITARY.score(-18, 16));
        assertTrue(BotBuildingPlanner.PlacementRole.SUPPLY.score(-16, 16)
                < BotBuildingPlanner.PlacementRole.SUPPLY.score(16, 16));
        assertTrue(BotBuildingPlanner.PlacementRole.FARM.score(-8, 14)
                < BotBuildingPlanner.PlacementRole.FARM.score(-32, 14));
    }

    @Test
    void exitLaneAccountsForTheWholeBuildingFootprint() {
        assertTrue(BotBuildingPlanner.blocksExitLane(20, 5, 8, 5));
        assertFalse(BotBuildingPlanner.blocksExitLane(20, 5, 10, 5));
        assertFalse(BotBuildingPlanner.blocksExitLane(-20, 5, 0, 5));
    }
}
