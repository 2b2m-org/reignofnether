package com.solegendary.reignofnether.bot;

import com.solegendary.reignofnether.resources.ResourceCost;
import com.solegendary.reignofnether.resources.Resources;
import com.solegendary.reignofnether.unit.UnitAction;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
    void personalityNamesParseSafelyForCommandsAndSaves() {
        assertAll(
                () -> assertEquals(BotPersonality.STEADY, BotPersonality.fromName("steady").orElseThrow()),
                () -> assertEquals(BotPersonality.RUSHER, BotPersonality.fromName("RuShEr").orElseThrow()),
                () -> assertTrue(BotPersonality.fromName("").isEmpty()),
                () -> assertTrue(BotPersonality.fromName("berserker").isEmpty())
        );
    }

    @Test
    void rebuildsAMissingCapitolAndWaitsForConstruction() {
        assertAll(
                () -> assertEquals(BotGoal.BUILD_CAPITOL,
                        BotDecisionMaker.chooseGoal(BotDifficulty.MEDIUM, BotPersonality.STEADY,
                                context(false, false))),
                () -> assertEquals(BotGoal.WAIT_FOR_CAPITOL,
                        BotDecisionMaker.chooseGoal(BotDifficulty.MEDIUM, BotPersonality.STEADY,
                                context(true, false)))
        );
    }

    @Test
    void forecastsPopulationUsingTheNextUnitsActualCost() {
        BotDecisionContext context = new BotDecisionContext(
                true, true,
                5,
                0,
                8, 10,
                false,
                1, 3,
                true,
                true,
                true
        );

        assertEquals(BotGoal.BUILD_SUPPLY,
                BotDecisionMaker.chooseGoal(BotDifficulty.MEDIUM, BotPersonality.STEADY, context));
    }

    @Test
    void growsEconomyBeforeMilitary() {
        assertAll(
                () -> assertEquals(BotGoal.TRAIN_WORKER,
                        BotDecisionMaker.chooseGoal(BotDifficulty.MEDIUM, BotPersonality.STEADY,
                                new BotDecisionContext(
                                true, true, 4, 0, 4, 20, false, 1, 3,
                                false, false, false))),
                () -> assertEquals(BotGoal.BUILD_FARM,
                        BotDecisionMaker.chooseGoal(BotDifficulty.MEDIUM, BotPersonality.STEADY,
                                new BotDecisionContext(
                                true, true, 5, 0, 5, 20, false, 1, 3,
                                false, false, false))),
                () -> assertEquals(BotGoal.BUILD_MILITARY,
                        BotDecisionMaker.chooseGoal(BotDifficulty.MEDIUM, BotPersonality.STEADY,
                                new BotDecisionContext(
                                true, true, 5, 0, 5, 20, false, 1, 3,
                                true, false, false)))
        );
    }

    @Test
    void hardFieldsAnOpeningArmyBeforeFinishingItsEconomy() {
        assertAll(
                () -> assertEquals(BotGoal.BUILD_FARM,
                        BotDecisionMaker.chooseGoal(BotDifficulty.HARD, BotPersonality.STEADY,
                                new BotDecisionContext(
                                        true, true, 5, 0, 5, 20, false, 1, 3,
                                        false, false, false))),
                () -> assertEquals(BotGoal.BUILD_MILITARY,
                        BotDecisionMaker.chooseGoal(BotDifficulty.HARD, BotPersonality.STEADY,
                                new BotDecisionContext(
                                        true, true, 5, 0, 5, 20, false, 1, 3,
                                        true, false, false))),
                () -> assertEquals(BotGoal.WAIT_FOR_MILITARY,
                        BotDecisionMaker.chooseGoal(BotDifficulty.HARD, BotPersonality.STEADY,
                                new BotDecisionContext(
                                        true, true, 5, 0, 5, 40, false, 1, 3,
                                        true, true, false))),
                () -> assertEquals(BotGoal.TRAIN_ARMY,
                        BotDecisionMaker.chooseGoal(BotDifficulty.HARD, BotPersonality.STEADY,
                                new BotDecisionContext(
                                        true, true, 5, 11, 16, 40, false, 1, 3,
                                        true, true, true))),
                () -> assertEquals(BotGoal.TRAIN_WORKER,
                        BotDecisionMaker.chooseGoal(BotDifficulty.HARD, BotPersonality.STEADY,
                                new BotDecisionContext(
                                        true, true, 5, 12, 17, 40, false, 1, 3,
                                        true, true, true))),
                () -> assertEquals(BotGoal.TRAIN_WORKER,
                        BotDecisionMaker.chooseGoal(BotDifficulty.HARD, BotPersonality.STEADY,
                                new BotDecisionContext(
                                        true, true, 6, 0, 6, 40, false, 1, 3,
                                        true, true, true))),
                () -> assertEquals(BotGoal.TRAIN_WORKER,
                        BotDecisionMaker.chooseGoal(BotDifficulty.MEDIUM, BotPersonality.TURTLE,
                                new BotDecisionContext(
                                        true, true, 5, 0, 5, 40, false, 1, 3,
                                        true, true, false))),
                () -> assertEquals(BotGoal.TRAIN_WORKER,
                        BotDecisionMaker.chooseGoal(BotDifficulty.MEDIUM, BotPersonality.TURTLE,
                                new BotDecisionContext(
                                        true, true, 5, 0, 5, 40, false, 1, 3,
                                        true, true, true))),
                () -> assertEquals(BotGoal.TRAIN_ARMY,
                        BotDecisionMaker.chooseGoal(BotDifficulty.HARD, BotPersonality.STEADY,
                                new BotDecisionContext(
                                        true, true, 9, 0, 9, 40, false, 1, 3,
                                        true, true, true)))
        );
    }

    @Test
    void hardOpeningArmySizeReflectsPersonality() {
        assertAll(
                () -> assertEquals(8, BotDecisionMaker.openingArmyPopulation(
                        BotDifficulty.HARD, BotPersonality.RUSHER)),
                () -> assertEquals(12, BotDecisionMaker.openingArmyPopulation(
                        BotDifficulty.HARD, BotPersonality.STEADY)),
                () -> assertEquals(16, BotDecisionMaker.openingArmyPopulation(
                        BotDifficulty.HARD, BotPersonality.TURTLE)),
                () -> assertEquals(0, BotDecisionMaker.openingArmyPopulation(
                        BotDifficulty.MEDIUM, BotPersonality.TURTLE))
        );
    }

    @Test
    void waitsForMilitaryBuildingBeforeTrainingArmy() {
        assertAll(
                () -> assertEquals(BotGoal.WAIT_FOR_MILITARY,
                        BotDecisionMaker.chooseGoal(BotDifficulty.MEDIUM, BotPersonality.STEADY,
                                new BotDecisionContext(
                                true, true, 5, 0, 5, 20, false, 1, 3,
                                true, true, false))),
                () -> assertEquals(BotGoal.TRAIN_ARMY,
                        BotDecisionMaker.chooseGoal(BotDifficulty.MEDIUM, BotPersonality.STEADY,
                                new BotDecisionContext(
                                true, true, 5, 0, 5, 20, false, 1, 3,
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
                () -> assertTrue(BotDifficulty.EASY.targetArmyPopulation()
                        < BotDifficulty.MEDIUM.targetArmyPopulation()),
                () -> assertTrue(BotDifficulty.MEDIUM.targetArmyPopulation()
                        < BotDifficulty.HARD.targetArmyPopulation()),
                () -> assertTrue(BotDifficulty.HARD.decisionIntervalTicks()
                        < BotDifficulty.MEDIUM.decisionIntervalTicks()),
                () -> assertTrue(BotDifficulty.MEDIUM.decisionIntervalTicks()
                        < BotDifficulty.EASY.decisionIntervalTicks()),
                () -> assertEquals(5, BotDifficulty.MEDIUM.targetWorkers()),
                () -> assertEquals(24, BotDifficulty.EASY.targetArmyPopulation()),
                () -> assertEquals(36, BotDifficulty.MEDIUM.targetArmyPopulation()),
                () -> assertEquals(48, BotDifficulty.HARD.targetArmyPopulation()),
                () -> assertEquals(12, BotDifficulty.EASY.attackPopulation()),
                () -> assertEquals(24, BotDifficulty.MEDIUM.attackPopulation()),
                () -> assertEquals(45, BotDifficulty.HARD.attackPopulation())
        );
    }

    @Test
    void personalitiesTradeEarlyPressureForEconomyAndArmyScale() {
        assertAll(
                () -> assertTrue(BotDecisionMaker.targetWorkers(BotDifficulty.MEDIUM, BotPersonality.RUSHER)
                        < BotDecisionMaker.targetWorkers(BotDifficulty.MEDIUM, BotPersonality.STEADY)),
                () -> assertTrue(BotDecisionMaker.targetWorkers(BotDifficulty.MEDIUM, BotPersonality.STEADY)
                        < BotDecisionMaker.targetWorkers(BotDifficulty.MEDIUM, BotPersonality.TURTLE)),
                () -> assertTrue(BotDecisionMaker.attackPopulation(BotDifficulty.MEDIUM, BotPersonality.RUSHER)
                        < BotDecisionMaker.attackPopulation(BotDifficulty.MEDIUM, BotPersonality.STEADY)),
                () -> assertTrue(BotDecisionMaker.attackPopulation(BotDifficulty.MEDIUM, BotPersonality.STEADY)
                        < BotDecisionMaker.attackPopulation(BotDifficulty.MEDIUM, BotPersonality.TURTLE)),
                () -> assertTrue(BotDecisionMaker.targetArmyPopulation(
                                BotDifficulty.MEDIUM, BotPersonality.RUSHER)
                        < BotDecisionMaker.targetArmyPopulation(BotDifficulty.MEDIUM, BotPersonality.STEADY)),
                () -> assertTrue(BotDecisionMaker.targetArmyPopulation(
                                BotDifficulty.MEDIUM, BotPersonality.STEADY)
                        < BotDecisionMaker.targetArmyPopulation(BotDifficulty.MEDIUM, BotPersonality.TURTLE))
        );
    }

    @Test
    void difficultyBandsRemainDistinctAcrossPersonalities() {
        assertAll(
                () -> assertTrue(BotDecisionMaker.attackPopulation(
                                BotDifficulty.MEDIUM, BotPersonality.RUSHER)
                        > BotDecisionMaker.attackPopulation(BotDifficulty.EASY, BotPersonality.TURTLE)),
                () -> assertTrue(BotDecisionMaker.attackPopulation(
                                BotDifficulty.HARD, BotPersonality.RUSHER)
                        > BotDecisionMaker.attackPopulation(BotDifficulty.MEDIUM, BotPersonality.TURTLE)),
                () -> assertTrue(BotDecisionMaker.targetArmyPopulation(
                                BotDifficulty.MEDIUM, BotPersonality.RUSHER)
                        > BotDecisionMaker.targetArmyPopulation(BotDifficulty.EASY, BotPersonality.TURTLE)),
                () -> assertTrue(BotDecisionMaker.targetArmyPopulation(
                                BotDifficulty.HARD, BotPersonality.RUSHER)
                        > BotDecisionMaker.targetArmyPopulation(BotDifficulty.MEDIUM, BotPersonality.TURTLE))
        );
    }

    @Test
    void attackGatesRemainReachableWithAFogScoutReserved() {
        for (BotDifficulty difficulty : BotDifficulty.values()) {
            for (BotPersonality personality : BotPersonality.values()) {
                int target = BotDecisionMaker.targetArmyPopulation(difficulty, personality);
                assertEquals(BotDecisionMaker.ArmyOrder.ATTACK_MOVE,
                        BotDecisionMaker.chooseArmyOrder(difficulty, personality, target - 3,
                                0, false, false),
                        () -> difficulty + "/" + personality + " gate must be reachable with a scout reserved");
            }
        }
    }

    @Test
    void attackThresholdsStayStrictlyAbovePositiveRetreatThresholds() {
        for (BotDifficulty difficulty : BotDifficulty.values()) {
            for (BotPersonality personality : BotPersonality.values()) {
                assertTrue(BotDecisionMaker.retreatPopulation(difficulty, personality) > 0,
                        () -> difficulty + "/" + personality + " must retain when outmatched");
                assertTrue(BotDecisionMaker.attackPopulation(difficulty, personality)
                                > BotDecisionMaker.retreatPopulation(difficulty, personality),
                        () -> difficulty + "/" + personality + " must not alternate attack and retreat orders");
            }
        }
    }

    @Test
    void retreatDisciplineScalesByDifficultyAndPersonality() {
        assertAll(
                () -> assertEquals(1, BotDecisionMaker.retreatPopulation(
                        BotDifficulty.EASY, BotPersonality.RUSHER)),
                () -> assertEquals(1, BotDecisionMaker.retreatPopulation(
                        BotDifficulty.EASY, BotPersonality.STEADY)),
                () -> assertEquals(5, BotDecisionMaker.retreatPopulation(
                        BotDifficulty.EASY, BotPersonality.TURTLE)),
                () -> assertEquals(8, BotDecisionMaker.retreatPopulation(
                        BotDifficulty.MEDIUM, BotPersonality.RUSHER)),
                () -> assertEquals(12, BotDecisionMaker.retreatPopulation(
                        BotDifficulty.MEDIUM, BotPersonality.STEADY)),
                () -> assertEquals(16, BotDecisionMaker.retreatPopulation(
                        BotDifficulty.MEDIUM, BotPersonality.TURTLE)),
                () -> assertEquals(20, BotDecisionMaker.retreatPopulation(
                        BotDifficulty.HARD, BotPersonality.RUSHER)),
                () -> assertEquals(24, BotDecisionMaker.retreatPopulation(
                        BotDifficulty.HARD, BotPersonality.STEADY)),
                () -> assertEquals(28, BotDecisionMaker.retreatPopulation(
                        BotDifficulty.HARD, BotPersonality.TURTLE))
        );
    }

    @Test
    void uncommittedArmiesRespectPersonalityAttackThresholds() {
        for (BotDifficulty difficulty : BotDifficulty.values())
            for (BotPersonality personality : BotPersonality.values()) {
                int threshold = BotDecisionMaker.attackPopulation(difficulty, personality);
                assertAll(
                        () -> assertTrue(threshold
                                        >= BotDecisionMaker.retreatPopulation(difficulty, personality),
                                () -> difficulty + "/" + personality
                                        + " must become attack-ready before retreat checks can apply"),
                        () -> assertEquals(BotDecisionMaker.ArmyOrder.HOLD,
                                BotDecisionMaker.chooseArmyOrder(
                                        difficulty, personality, threshold - 1,
                                        0, false, false)),
                        () -> assertEquals(BotDecisionMaker.ArmyOrder.ATTACK_MOVE,
                                BotDecisionMaker.chooseArmyOrder(
                                        difficulty, personality, threshold,
                                        0, false, false))
                );
            }
    }

    @Test
    void hardBotsPressOnlyClearObservedAdvantages() {
        assertAll(
                () -> assertTrue(BotDecisionMaker.shouldPressVisibleAdvantage(
                        BotDifficulty.HARD, BotPersonality.STEADY, 36, 24)),
                () -> assertFalse(BotDecisionMaker.shouldPressVisibleAdvantage(
                        BotDifficulty.HARD, BotPersonality.STEADY, 35, 24)),
                () -> assertTrue(BotDecisionMaker.shouldPressVisibleAdvantage(
                        BotDifficulty.HARD, BotPersonality.STEADY, 36, 28)),
                () -> assertFalse(BotDecisionMaker.shouldPressVisibleAdvantage(
                        BotDifficulty.HARD, BotPersonality.STEADY, 36, 29)),
                () -> assertTrue(BotDecisionMaker.shouldPressVisibleAdvantage(
                        BotDifficulty.HARD, BotPersonality.STEADY, 36, 12)),
                () -> assertFalse(BotDecisionMaker.shouldPressVisibleAdvantage(
                        BotDifficulty.HARD, BotPersonality.STEADY, 48, 11)),
                () -> assertTrue(BotDecisionMaker.shouldPressVisibleAdvantage(
                        BotDifficulty.HARD, BotPersonality.RUSHER, 32, 25)),
                () -> assertFalse(BotDecisionMaker.shouldPressVisibleAdvantage(
                        BotDifficulty.HARD, BotPersonality.TURTLE, 39, 24)),
                () -> assertTrue(BotDecisionMaker.shouldPressVisibleAdvantage(
                        BotDifficulty.HARD, BotPersonality.TURTLE, 40, 32)),
                () -> assertFalse(BotDecisionMaker.shouldPressVisibleAdvantage(
                        BotDifficulty.MEDIUM, BotPersonality.STEADY, 48, 24)),
                () -> assertFalse(BotDecisionMaker.shouldPressVisibleAdvantage(
                        BotDifficulty.HARD, BotPersonality.STEADY, 48, 0)),
                () -> assertEquals(BotDecisionMaker.ArmyOrder.DEFEND,
                        BotDecisionMaker.chooseArmyOrder(
                                BotDifficulty.HARD, BotPersonality.STEADY, 36, 24,
                                BotDecisionMaker.shouldPressVisibleAdvantage(
                                        BotDifficulty.HARD, BotPersonality.STEADY, 36, 24),
                                true))
        );
    }

    @Test
    void reservedScoutsAndBeaconGuardsCountTowardLaunchReadiness() {
        assertAll(
                () -> assertFalse(BotDecisionMaker.shouldLaunchWithReservedUnits(
                        BotDifficulty.HARD, BotPersonality.RUSHER, 34, 6)),
                () -> assertTrue(BotDecisionMaker.shouldLaunchWithReservedUnits(
                        BotDifficulty.HARD, BotPersonality.RUSHER, 35, 6)),
                () -> assertFalse(BotDecisionMaker.shouldLaunchWithReservedUnits(
                        BotDifficulty.HARD, BotPersonality.STEADY, 35, 9)),
                () -> assertTrue(BotDecisionMaker.shouldLaunchWithReservedUnits(
                        BotDifficulty.HARD, BotPersonality.STEADY, 36, 9)),
                () -> assertFalse(BotDecisionMaker.shouldLaunchWithReservedUnits(
                        BotDifficulty.HARD, BotPersonality.TURTLE, 36, 12)),
                () -> assertTrue(BotDecisionMaker.shouldLaunchWithReservedUnits(
                        BotDifficulty.HARD, BotPersonality.TURTLE, 37, 12))
        );
    }

    @Test
    void beaconOrdersCaptureOpenGroundAndWaitForACombatReadyContest() {
        for (BotDifficulty difficulty : BotDifficulty.values())
            for (BotPersonality personality : BotPersonality.values()) {
                int threshold = BotDecisionMaker.attackPopulation(difficulty, personality);
                assertAll(
                        () -> assertEquals(BotDecisionMaker.BeaconOrder.NONE,
                                BotDecisionMaker.chooseBeaconOrder(
                                        difficulty, personality, threshold, 0,
                                        BotDecisionMaker.BeaconControl.ABSENT)),
                        () -> assertEquals(BotDecisionMaker.BeaconOrder.CAPTURE,
                                BotDecisionMaker.chooseBeaconOrder(
                                        difficulty, personality, 1, 0,
                                        BotDecisionMaker.BeaconControl.NEUTRAL)),
                        () -> assertEquals(BotDecisionMaker.BeaconOrder.CAPTURE,
                                BotDecisionMaker.chooseBeaconOrder(
                                        difficulty, personality, threshold - 1, threshold - 2,
                                        BotDecisionMaker.BeaconControl.NEUTRAL)),
                        () -> assertEquals(BotDecisionMaker.BeaconOrder.NONE,
                                BotDecisionMaker.chooseBeaconOrder(
                                        difficulty, personality, threshold - 1, threshold - 1,
                                        BotDecisionMaker.BeaconControl.NEUTRAL)),
                        () -> assertEquals(BotDecisionMaker.BeaconOrder.CAPTURE,
                                BotDecisionMaker.chooseBeaconOrder(
                                        difficulty, personality, threshold - 1, 1,
                                        BotDecisionMaker.BeaconControl.HOSTILE)),
                        () -> assertEquals(BotDecisionMaker.BeaconOrder.NONE,
                                BotDecisionMaker.chooseBeaconOrder(
                                        difficulty, personality, threshold - 1, threshold - 1,
                                        BotDecisionMaker.BeaconControl.HOSTILE)),
                        () -> assertEquals(BotDecisionMaker.BeaconOrder.CONTEST,
                                BotDecisionMaker.chooseBeaconOrder(
                                        difficulty, personality, threshold, threshold,
                                        BotDecisionMaker.BeaconControl.HOSTILE)),
                        () -> assertEquals(BotDecisionMaker.BeaconOrder.CAPTURE,
                                BotDecisionMaker.chooseBeaconOrder(
                                        difficulty, personality, 1, 0,
                                        BotDecisionMaker.BeaconControl.HOSTILE)),
                        () -> assertEquals(BotDecisionMaker.BeaconOrder.GARRISON,
                                BotDecisionMaker.chooseBeaconOrder(
                                        difficulty, personality, 1, 0,
                                        BotDecisionMaker.BeaconControl.OWNED)),
                        () -> assertEquals(BotDecisionMaker.BeaconOrder.NONE,
                                BotDecisionMaker.chooseBeaconOrder(
                                        difficulty, personality, 1, 0,
                                        BotDecisionMaker.BeaconControl.ALLIED))
                );
            }
    }

    @Test
    void beaconOwnersReinforceOnlyWhenTheirGuardsNeedHelp() {
        assertAll(
                () -> assertFalse(BotDecisionMaker.shouldReinforceBeacon(
                        BotPersonality.RUSHER, 1, 1)),
                () -> assertTrue(BotDecisionMaker.shouldReinforceBeacon(
                        BotPersonality.RUSHER, 2, 1)),
                () -> assertFalse(BotDecisionMaker.shouldReinforceBeacon(
                        BotPersonality.STEADY, 1, 2)),
                () -> assertTrue(BotDecisionMaker.shouldReinforceBeacon(
                        BotPersonality.STEADY, 2, 2)),
                () -> assertFalse(BotDecisionMaker.shouldReinforceBeacon(
                        BotPersonality.TURTLE, 0, 3)),
                () -> assertTrue(BotDecisionMaker.shouldReinforceBeacon(
                        BotPersonality.TURTLE, 1, 3))
        );
    }

    @Test
    void beaconGarrisonsAndAurasExpressPersonalityWithoutGeneratingResources() {
        assertAll(
                () -> assertEquals(3,
                        BotDecisionMaker.beaconGuardPopulation(BotPersonality.RUSHER)),
                () -> assertEquals(6,
                        BotDecisionMaker.beaconGuardPopulation(BotPersonality.STEADY)),
                () -> assertEquals(9,
                        BotDecisionMaker.beaconGuardPopulation(BotPersonality.TURTLE)),
                () -> assertEquals(UnitAction.BEACON_STRENGTH,
                        BotDecisionMaker.beaconAuraAction(BotPersonality.RUSHER)),
                () -> assertEquals(UnitAction.BEACON_REGENERATION,
                        BotDecisionMaker.beaconAuraAction(BotPersonality.STEADY)),
                () -> assertEquals(UnitAction.BEACON_RESISTANCE,
                        BotDecisionMaker.beaconAuraAction(BotPersonality.TURTLE))
        );
    }

    @Test
    void hardPlansSupplyFurtherAhead() {
        BotDecisionContext context = new BotDecisionContext(
                true, true, 9, 0, 13, 20, false, 1, 3,
                true, true, true
        );

        assertAll(
                () -> assertEquals(BotGoal.TRAIN_ARMY,
                        BotDecisionMaker.chooseGoal(BotDifficulty.EASY, BotPersonality.STEADY, context)),
                () -> assertEquals(BotGoal.TRAIN_ARMY,
                        BotDecisionMaker.chooseGoal(BotDifficulty.MEDIUM, BotPersonality.STEADY, context)),
                () -> assertEquals(BotGoal.BUILD_SUPPLY,
                        BotDecisionMaker.chooseGoal(BotDifficulty.HARD, BotPersonality.STEADY, context))
        );
    }

    @Test
    void workerSplitsKeepWoodIncomeAndHardFrontLoadsConstruction() {
        assertAll(
                () -> assertEquals(3, BotDecisionMaker.foodWorkerCount(
                        BotDifficulty.EASY, BotPersonality.STEADY, 4, false)),
                () -> assertEquals(3, BotDecisionMaker.foodWorkerCount(
                        BotDifficulty.MEDIUM, BotPersonality.STEADY, 5, false)),
                () -> assertEquals(4, BotDecisionMaker.foodWorkerCount(
                        BotDifficulty.MEDIUM, BotPersonality.STEADY, 5, true)),
                () -> assertEquals(3, BotDecisionMaker.foodWorkerCount(
                        BotDifficulty.HARD, BotPersonality.STEADY, 5, false)),
                () -> assertEquals(3, BotDecisionMaker.foodWorkerCount(
                        BotDifficulty.HARD, BotPersonality.STEADY, 4, false)),
                () -> assertEquals(4, BotDecisionMaker.foodWorkerCount(
                        BotDifficulty.HARD, BotPersonality.STEADY, 5, true)),
                () -> assertEquals(4, BotDecisionMaker.foodWorkerCount(
                        BotDifficulty.HARD, BotPersonality.STEADY, 9, false)),
                () -> assertEquals(8, BotDecisionMaker.foodWorkerCount(
                        BotDifficulty.HARD, BotPersonality.STEADY, 9, true)),
                () -> assertEquals(8, BotDecisionMaker.foodWorkerCount(
                        BotDifficulty.HARD, BotPersonality.TURTLE, 10, true))
        );
    }

    @Test
    void monsterArmyShelterWindowSpansMidnightThroughDaylight() {
        assertAll(
                () -> assertFalse(BotDecisionMaker.shouldShelterMonsterArmy(21999, false)),
                () -> assertTrue(BotDecisionMaker.shouldShelterMonsterArmy(22000, false)),
                () -> assertTrue(BotDecisionMaker.shouldShelterMonsterArmy(0, false)),
                () -> assertTrue(BotDecisionMaker.shouldShelterMonsterArmy(500, false)),
                () -> assertTrue(BotDecisionMaker.shouldShelterMonsterArmy(501, false)),
                () -> assertTrue(BotDecisionMaker.shouldShelterMonsterArmy(12500, false)),
                () -> assertFalse(BotDecisionMaker.shouldShelterMonsterArmy(12501, false)),
                () -> assertFalse(BotDecisionMaker.shouldShelterMonsterArmy(6000, true))
        );
    }

    @Test
    void armyCompositionFallsBackToAnyAffordableUnit() {
        for (BotPersonality personality : BotPersonality.values()) {
            assertEquals(BotDecisionMaker.ArmyUnitChoice.MELEE,
                    BotDecisionMaker.chooseArmyUnit(personality, 18, 15, true, false));
            assertEquals(BotDecisionMaker.ArmyUnitChoice.RANGED,
                    BotDecisionMaker.chooseArmyUnit(personality, 0, 0, false, true));
            assertEquals(BotDecisionMaker.ArmyUnitChoice.RANGED,
                    BotDecisionMaker.chooseArmyUnit(personality, 12, 18, false, true));
            assertEquals(BotDecisionMaker.ArmyUnitChoice.NONE,
                    BotDecisionMaker.chooseArmyUnit(personality, 0, 0, false, false));
        }
    }

    @Test
    void personalitiesMaintainDistinctArmyCompositionsFromSurvivingUnits() {
        assertAll(
                () -> assertEquals(BotDecisionMaker.ArmyUnitChoice.MELEE,
                        BotDecisionMaker.chooseArmyUnit(BotPersonality.RUSHER, 9, 3, true, true)),
                () -> assertEquals(BotDecisionMaker.ArmyUnitChoice.RANGED,
                        BotDecisionMaker.chooseArmyUnit(BotPersonality.STEADY, 18, 6, true, true)),
                () -> assertEquals(BotDecisionMaker.ArmyUnitChoice.RANGED,
                        BotDecisionMaker.chooseArmyUnit(BotPersonality.TURTLE, 18, 6, true, true)),
                () -> assertEquals(BotDecisionMaker.ArmyUnitChoice.MELEE,
                        BotDecisionMaker.chooseArmyUnit(BotPersonality.TURTLE, 12, 18, true, true))
        );
    }

    @Test
    void productionNeverOvershootsItsPopulationBudget() {
        assertAll(
                () -> assertTrue(BotDecisionMaker.fitsArmyPopulation(33, 3, 36)),
                () -> assertFalse(BotDecisionMaker.fitsArmyPopulation(34, 3, 36))
        );
    }

    @Test
    void armySpendingPreservesOneExactWorkerReplacement() {
        ResourceCost army = ResourceCost.Unit(50, 20, 10, 1, 1);
        ResourceCost worker = ResourceCost.Unit(50, 10, 5, 1, 1);

        assertAll(
                () -> assertTrue(BotDecisionMaker.canSpendAndPreserveReserve(
                        new Resources("bot", 100, 30, 15), army, worker)),
                () -> assertFalse(BotDecisionMaker.canSpendAndPreserveReserve(
                        new Resources("bot", 99, 30, 15), army, worker)),
                () -> assertFalse(BotDecisionMaker.canSpendAndPreserveReserve(
                        new Resources("bot", 100, 29, 15), army, worker)),
                () -> assertFalse(BotDecisionMaker.canSpendAndPreserveReserve(
                        new Resources("bot", 100, 30, 14), army, worker)),
                () -> assertFalse(BotDecisionMaker.canSpendAndPreserveReserve(null, army, worker))
        );
    }

    @Test
    void workerFleeDistanceUsesHysteresisAtExactBoundaries() {
        assertAll(
                () -> assertTrue(BotDecisionMaker.shouldFleeWorker(false, 23 * 23)),
                () -> assertTrue(BotDecisionMaker.shouldFleeWorker(false, 24 * 24)),
                () -> assertFalse(BotDecisionMaker.shouldFleeWorker(false, 25 * 25)),
                () -> assertTrue(BotDecisionMaker.shouldFleeWorker(true, 26 * 26)),
                () -> assertTrue(BotDecisionMaker.shouldFleeWorker(true, 32 * 32)),
                () -> assertFalse(BotDecisionMaker.shouldFleeWorker(true, 33 * 33)),
                () -> assertFalse(BotDecisionMaker.shouldFleeWorker(true, Double.POSITIVE_INFINITY))
        );
    }

    @Test
    void personalitiesPrioritizeDifferentKnownTargets() {
        assertAll(
                () -> assertEquals(0, BotDecisionMaker.targetPriority(BotPersonality.RUSHER, false, false)),
                () -> assertEquals(0, BotDecisionMaker.targetPriority(BotPersonality.RUSHER, true, true)),
                () -> assertTrue(BotDecisionMaker.targetPriority(BotPersonality.STEADY, true, false)
                        < BotDecisionMaker.targetPriority(BotPersonality.STEADY, false, true)),
                () -> assertTrue(BotDecisionMaker.targetPriority(BotPersonality.TURTLE, false, true)
                        < BotDecisionMaker.targetPriority(BotPersonality.TURTLE, true, true))
        );
    }

    @Test
    void armiesEngageThreatsInsteadOfIgnoringThemDuringBaseRaces() {
        assertAll(
                () -> assertEquals(BotDecisionMaker.ArmyOrder.DEFEND,
                        BotDecisionMaker.chooseArmyOrder(BotDifficulty.EASY, BotPersonality.STEADY,
                                1, 1, false, true)),
                () -> assertEquals(BotDecisionMaker.ArmyOrder.ATTACK_MOVE,
                        BotDecisionMaker.chooseArmyOrder(BotDifficulty.MEDIUM, BotPersonality.STEADY,
                                24, 0, false, false)),
                () -> assertEquals(BotDecisionMaker.ArmyOrder.RETREAT,
                        BotDecisionMaker.chooseArmyOrder(BotDifficulty.HARD, BotPersonality.STEADY,
                                15, 15, true, false)),
                () -> assertEquals(BotDecisionMaker.ArmyOrder.ATTACK_MOVE,
                        BotDecisionMaker.chooseArmyOrder(BotDifficulty.HARD, BotPersonality.STEADY,
                                15, 3, true, false)),
                () -> assertEquals(BotDecisionMaker.ArmyOrder.HOLD,
                        BotDecisionMaker.chooseArmyOrder(BotDifficulty.HARD, BotPersonality.STEADY,
                                15, 0, false, false)),
                () -> assertEquals(BotDecisionMaker.ArmyOrder.HOLD,
                        BotDecisionMaker.chooseArmyOrder(BotDifficulty.HARD, BotPersonality.STEADY,
                                3, 6, false, false)),
                () -> assertTrue(BotDecisionMaker.shouldFocusEnemyArmy(
                        false, true, 36, 12)),
                () -> assertFalse(BotDecisionMaker.shouldFocusEnemyArmy(
                        false, true, 36, 9)),
                () -> assertTrue(BotDecisionMaker.shouldFocusEnemyArmy(
                        true, true, 36, 3)),
                () -> assertTrue(BotDecisionMaker.shouldDefend(
                        BotPersonality.TURTLE, true, false, false, false)),
                () -> assertTrue(BotDecisionMaker.shouldDefend(
                        BotPersonality.RUSHER, true, false, true, true))
        );
    }

    @Test
    void attackReadySurvivalBotsPrioritizeOwnBaseAndLeaveAlliedResponseToTurtles() {
        assertAll(
                () -> assertFalse(BotDecisionMaker.shouldDefend(
                        BotPersonality.STEADY, true, true, false, true)),
                () -> assertFalse(BotDecisionMaker.shouldDefend(
                        BotPersonality.RUSHER, true, true, false, false)),
                () -> assertTrue(BotDecisionMaker.shouldDefend(
                        BotPersonality.TURTLE, true, true, false, false)),
                () -> assertTrue(BotDecisionMaker.shouldDefend(
                        BotPersonality.STEADY, true, true, true, false)),
                () -> assertTrue(BotDecisionMaker.shouldDefend(
                        BotPersonality.STEADY, false, true, false, false)),
                () -> assertTrue(BotDecisionMaker.shouldDefend(
                        BotPersonality.STEADY, true, false, false, true)),
                () -> assertTrue(BotDecisionMaker.defensePriority(false, false, true)
                        < BotDecisionMaker.defensePriority(false, true, false)),
                () -> assertTrue(BotDecisionMaker.defensePriority(true, true, false)
                        < BotDecisionMaker.defensePriority(true, false, true))
        );
    }

    @Test
    void survivalTeamsRespondBeforeVisibleEnemiesDamageAnAlliedBase() {
        assertAll(
                () -> assertTrue(BotDecisionMaker.shouldRememberDefenseThreat(true, false, true)),
                () -> assertTrue(BotDecisionMaker.shouldRememberDefenseThreat(false, true, true)),
                () -> assertFalse(BotDecisionMaker.shouldRememberDefenseThreat(false, false, true)),
                () -> assertFalse(BotDecisionMaker.shouldRememberDefenseThreat(true, true, false))
        );
    }

    @Test
    void rangedTargetingPrioritizesFlyersWithoutOpeningTheStrategicGate() {
        assertAll(
                () -> assertFalse(BotDecisionMaker.shouldFocusEnemyArmy(
                        false, true, 36, 3)),
                () -> assertTrue(BotDecisionMaker.rangedTargetPriority(true, true)
                        < BotDecisionMaker.rangedTargetPriority(false, true)),
                () -> assertTrue(BotDecisionMaker.rangedTargetPriority(false, true)
                        < BotDecisionMaker.rangedTargetPriority(true, false)),
                () -> assertTrue(BotDecisionMaker.rangedTargetPriority(true, false)
                        < BotDecisionMaker.rangedTargetPriority(false, false))
        );
    }

    @Test
    void repairsUseOnlySurplusWoodAndPrioritizeCriticalBuildings() {
        assertAll(
                () -> assertFalse(BotDecisionMaker.shouldRepairBuilding(true, 500, 200)),
                () -> assertFalse(BotDecisionMaker.shouldRepairBuilding(false, 200, 200)),
                () -> assertTrue(BotDecisionMaker.shouldRepairBuilding(false, 201, 200)),
                () -> assertTrue(BotDecisionMaker.repairTargetPriority(true, false)
                        < BotDecisionMaker.repairTargetPriority(false, true)),
                () -> assertTrue(BotDecisionMaker.repairTargetPriority(false, true)
                        < BotDecisionMaker.repairTargetPriority(false, false)),
                () -> assertEquals(150, BotDecisionMaker.repairWoodReserve(100, 0, 150, 150, 0)),
                () -> assertEquals(200, BotDecisionMaker.repairWoodReserve(100, 0, 200, 150, 0)),
                () -> assertEquals(175, BotDecisionMaker.repairWoodReserve(75, 75, 150, 75, 100))
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
                true, true, 7, 0, 7, 20, false, 1, 3,
                true, true, true
        );
        for (BotDifficulty difficulty : BotDifficulty.values()) {
            for (BotPersonality personality : BotPersonality.values()) {
                BotGoal first = BotDecisionMaker.chooseGoal(difficulty, personality, context);
                assertEquals(first, BotDecisionMaker.chooseGoal(difficulty, personality, context));
            }
        }
    }

    private static BotDecisionContext context(boolean capitolPresent, boolean capitolBuilt) {
        return new BotDecisionContext(
                capitolPresent,
                capitolBuilt,
                3,
                0,
                3, 10,
                false,
                1, 3,
                false,
                false,
                false
        );
    }
}
