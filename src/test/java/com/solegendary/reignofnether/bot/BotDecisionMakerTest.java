package com.solegendary.reignofnether.bot;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;

class BotDecisionMakerTest {
    @Test
    void waitsUntilCapitolExistsAndIsBuilt() {
        assertAll(
                () -> assertEquals(BotGoal.WAIT_FOR_CAPITOL, BotDecisionMaker.chooseGoal(context(false, false))),
                () -> assertEquals(BotGoal.WAIT_FOR_CAPITOL, BotDecisionMaker.chooseGoal(context(true, false)))
        );
    }

    @Test
    void preventsPopulationBlockBeforeOtherGrowth() {
        BotDecisionContext context = new BotDecisionContext(
                true, true,
                3,
                8, 10,
                false,
                false,
                false,
                false
        );

        assertEquals(BotGoal.BUILD_SUPPLY, BotDecisionMaker.chooseGoal(context));
    }

    @Test
    void growsEconomyBeforeMilitary() {
        assertAll(
                () -> assertEquals(BotGoal.TRAIN_WORKER, BotDecisionMaker.chooseGoal(new BotDecisionContext(
                        true, true, 4, 4, 10, false, false, false, false))),
                () -> assertEquals(BotGoal.BUILD_FARM, BotDecisionMaker.chooseGoal(new BotDecisionContext(
                        true, true, 5, 5, 10, false, false, false, false))),
                () -> assertEquals(BotGoal.BUILD_MILITARY, BotDecisionMaker.chooseGoal(new BotDecisionContext(
                        true, true, 5, 5, 10, false, true, false, false)))
        );
    }

    @Test
    void waitsForMilitaryBuildingBeforeTrainingArmy() {
        assertAll(
                () -> assertEquals(BotGoal.WAIT_FOR_MILITARY, BotDecisionMaker.chooseGoal(new BotDecisionContext(
                        true, true, 5, 5, 10, false, true, true, false))),
                () -> assertEquals(BotGoal.TRAIN_ARMY, BotDecisionMaker.chooseGoal(new BotDecisionContext(
                        true, true, 5, 5, 10, false, true, true, true)))
        );
    }

    private static BotDecisionContext context(boolean capitolPresent, boolean capitolBuilt) {
        return new BotDecisionContext(
                capitolPresent,
                capitolBuilt,
                3,
                3, 10,
                false,
                false,
                false,
                false
        );
    }
}
