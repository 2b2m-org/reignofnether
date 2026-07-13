package com.solegendary.reignofnether.bot;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BotDecisionMakerTest {
    @Test
    void difficultyNamesParseSafelyForCommandsAndSaves() {
        assertAll(
                () -> assertEquals(BotDifficulty.HARD, BotDifficulty.fromName("hard").orElseThrow()),
                () -> assertEquals(BotDifficulty.MEDIUM, BotDifficulty.fromName("MeDiUm").orElseThrow()),
                () -> assertTrue(BotDifficulty.fromName("").isEmpty()),
                () -> assertTrue(BotDifficulty.fromName("impossible").isEmpty())
        );
    }

    @Test
    void waitsUntilCapitolExistsAndIsBuilt() {
        assertAll(
                () -> assertEquals(BotGoal.WAIT_FOR_CAPITOL,
                        BotDecisionMaker.chooseGoal(BotDifficulty.MEDIUM, context(false, false))),
                () -> assertEquals(BotGoal.WAIT_FOR_CAPITOL,
                        BotDecisionMaker.chooseGoal(BotDifficulty.MEDIUM, context(true, false)))
        );
    }

    @Test
    void forecastsPopulationUsingTheNextUnitsActualCost() {
        BotDecisionContext context = new BotDecisionContext(
                true, true,
                5,
                8, 10,
                false,
                1, 3,
                true,
                true,
                true
        );

        assertEquals(BotGoal.BUILD_SUPPLY,
                BotDecisionMaker.chooseGoal(BotDifficulty.MEDIUM, context));
    }

    @Test
    void growsEconomyBeforeMilitary() {
        assertAll(
                () -> assertEquals(BotGoal.TRAIN_WORKER,
                        BotDecisionMaker.chooseGoal(BotDifficulty.MEDIUM, new BotDecisionContext(
                                true, true, 4, 4, 20, false, 1, 3,
                                false, false, false))),
                () -> assertEquals(BotGoal.BUILD_FARM,
                        BotDecisionMaker.chooseGoal(BotDifficulty.MEDIUM, new BotDecisionContext(
                                true, true, 5, 5, 20, false, 1, 3,
                                false, false, false))),
                () -> assertEquals(BotGoal.BUILD_MILITARY,
                        BotDecisionMaker.chooseGoal(BotDifficulty.MEDIUM, new BotDecisionContext(
                                true, true, 5, 5, 20, false, 1, 3,
                                true, false, false)))
        );
    }

    @Test
    void waitsForMilitaryBuildingBeforeTrainingArmy() {
        assertAll(
                () -> assertEquals(BotGoal.WAIT_FOR_MILITARY,
                        BotDecisionMaker.chooseGoal(BotDifficulty.MEDIUM, new BotDecisionContext(
                                true, true, 5, 5, 20, false, 1, 3,
                                true, true, false))),
                () -> assertEquals(BotGoal.TRAIN_ARMY,
                        BotDecisionMaker.chooseGoal(BotDifficulty.MEDIUM, new BotDecisionContext(
                                true, true, 5, 5, 20, false, 1, 3,
                                true, true, true)))
        );
    }

    @Test
    void difficultyProfilesScaleWithoutChangingGameRules() {
        assertAll(
                () -> assertTrue(BotDifficulty.EASY.targetWorkers() < BotDifficulty.MEDIUM.targetWorkers()),
                () -> assertTrue(BotDifficulty.MEDIUM.targetWorkers() < BotDifficulty.HARD.targetWorkers()),
                () -> assertTrue(BotDifficulty.EASY.supplyLookaheadUnits()
                        < BotDifficulty.MEDIUM.supplyLookaheadUnits()),
                () -> assertTrue(BotDifficulty.MEDIUM.supplyLookaheadUnits()
                        < BotDifficulty.HARD.supplyLookaheadUnits()),
                () -> assertTrue(BotDifficulty.EASY.targetArmySize() < BotDifficulty.MEDIUM.targetArmySize()),
                () -> assertTrue(BotDifficulty.MEDIUM.targetArmySize() < BotDifficulty.HARD.targetArmySize()),
                () -> assertTrue(BotDifficulty.EASY.retreatThreshold()
                        < BotDifficulty.MEDIUM.retreatThreshold()),
                () -> assertTrue(BotDifficulty.MEDIUM.retreatThreshold()
                        < BotDifficulty.HARD.retreatThreshold()),
                () -> assertEquals(5, BotDifficulty.MEDIUM.targetWorkers()),
                () -> assertEquals(12, BotDifficulty.MEDIUM.targetArmySize())
        );
    }

    @Test
    void hardPlansSupplyFurtherAhead() {
        BotDecisionContext context = new BotDecisionContext(
                true, true, 9, 13, 20, false, 1, 3,
                true, true, true
        );

        assertAll(
                () -> assertEquals(BotGoal.TRAIN_ARMY,
                        BotDecisionMaker.chooseGoal(BotDifficulty.EASY, context)),
                () -> assertEquals(BotGoal.TRAIN_ARMY,
                        BotDecisionMaker.chooseGoal(BotDifficulty.MEDIUM, context)),
                () -> assertEquals(BotGoal.BUILD_SUPPLY,
                        BotDecisionMaker.chooseGoal(BotDifficulty.HARD, context))
        );
    }

    @Test
    void workerSplitsKeepWoodIncomeAndHardFrontLoadsConstruction() {
        assertAll(
                () -> assertEquals(3, BotDecisionMaker.foodWorkerCount(BotDifficulty.EASY, 4, false)),
                () -> assertEquals(3, BotDecisionMaker.foodWorkerCount(BotDifficulty.MEDIUM, 5, false)),
                () -> assertEquals(4, BotDecisionMaker.foodWorkerCount(BotDifficulty.HARD, 9, false)),
                () -> assertEquals(6, BotDecisionMaker.foodWorkerCount(BotDifficulty.HARD, 9, true))
        );
    }

    @Test
    void armyCompositionFallsBackInsteadOfStalling() {
        for (BotDifficulty difficulty : BotDifficulty.values()) {
            assertEquals(BotDecisionMaker.ArmyUnitChoice.MELEE,
                    BotDecisionMaker.chooseArmyUnit(difficulty, 11, true, false));
            assertEquals(BotDecisionMaker.ArmyUnitChoice.RANGED,
                    BotDecisionMaker.chooseArmyUnit(difficulty, 0, false, true));
            assertEquals(BotDecisionMaker.ArmyUnitChoice.NONE,
                    BotDecisionMaker.chooseArmyUnit(difficulty, 0, false, false));
        }
    }

    @Test
    void harderBotsPrioritizeKnownStrategicTargets() {
        assertAll(
                () -> assertEquals(0, BotDecisionMaker.targetPriority(BotDifficulty.EASY, false, false)),
                () -> assertEquals(0, BotDecisionMaker.targetPriority(BotDifficulty.EASY, true, true)),
                () -> assertTrue(BotDecisionMaker.targetPriority(BotDifficulty.MEDIUM, true, false)
                        < BotDecisionMaker.targetPriority(BotDifficulty.MEDIUM, false, true)),
                () -> assertTrue(BotDecisionMaker.targetPriority(BotDifficulty.HARD, true, false)
                        < BotDecisionMaker.targetPriority(BotDifficulty.HARD, false, true)),
                () -> assertTrue(BotDecisionMaker.targetPriority(BotDifficulty.HARD, false, true)
                        < BotDecisionMaker.targetPriority(BotDifficulty.HARD, false, false))
        );
    }

    @Test
    void progressingScoutDoesNotRotateAtTheOldAbsoluteTimeout() {
        assertAll(
                () -> assertEquals(BotDecisionMaker.ScoutWaypointDecision.PROGRESS,
                        BotDecisionMaker.evaluateScoutWaypoint(false, 150, 140, 600)),
                () -> assertEquals(BotDecisionMaker.ScoutWaypointDecision.KEEP,
                        BotDecisionMaker.evaluateScoutWaypoint(false, 140, 138, 100)),
                () -> assertEquals(BotDecisionMaker.ScoutWaypointDecision.REPLACE,
                        BotDecisionMaker.evaluateScoutWaypoint(false, 140, 140, 600)),
                () -> assertEquals(BotDecisionMaker.ScoutWaypointDecision.REPLACE,
                        BotDecisionMaker.evaluateScoutWaypoint(true, 12, 12, 0))
        );
    }

    @Test
    void decisionsAreDeterministicAtEveryDifficulty() {
        BotDecisionContext context = new BotDecisionContext(
                true, true, 7, 7, 20, false, 1, 3,
                true, true, true
        );
        for (BotDifficulty difficulty : BotDifficulty.values()) {
            BotGoal first = BotDecisionMaker.chooseGoal(difficulty, context);
            assertEquals(first, BotDecisionMaker.chooseGoal(difficulty, context));
        }
    }

    private static BotDecisionContext context(boolean capitolPresent, boolean capitolBuilt) {
        return new BotDecisionContext(
                capitolPresent,
                capitolBuilt,
                3,
                3, 10,
                false,
                1, 3,
                false,
                false,
                false
        );
    }
}
