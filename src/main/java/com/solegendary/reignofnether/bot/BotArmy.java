package com.solegendary.reignofnether.bot;

import com.solegendary.reignofnether.ReignOfNether;
import com.solegendary.reignofnether.alliance.AlliancesServerEvents;
import com.solegendary.reignofnether.building.BuildingPlacement;
import com.solegendary.reignofnether.building.BuildingServerEvents;
import com.solegendary.reignofnether.building.addon.NightSourceAddon;
import com.solegendary.reignofnether.building.buildings.placements.BeaconPlacement;
import com.solegendary.reignofnether.building.buildings.placements.ProductionPlacement;
import com.solegendary.reignofnether.faction.Faction;
import com.solegendary.reignofnether.player.PlayerServerEvents;
import com.solegendary.reignofnether.player.RTSPlayer;
import com.solegendary.reignofnether.survival.SurvivalServerEvents;
import com.solegendary.reignofnether.time.NightUtils;
import com.solegendary.reignofnether.time.TimeServerEvents;
import com.solegendary.reignofnether.unit.UnitAction;
import com.solegendary.reignofnether.unit.UnitServerEvents;
import com.solegendary.reignofnether.unit.interfaces.AttackerUnit;
import com.solegendary.reignofnether.unit.interfaces.RangedAttackerUnit;
import com.solegendary.reignofnether.unit.interfaces.Unit;
import com.solegendary.reignofnether.unit.interfaces.WorkerUnit;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class BotArmy {
    private static final int TARGET_REVISIT_TICKS = 600;
    private static final int OBJECTIVE_STALL_TICKS = 600;
    private static final int DEFENSE_MEMORY_TICKS = 200;
    private static final int ALLY_DEFENSE_MEMORY_TICKS = 1200;
    private static final int REGROUP_TICKS = 200;
    private static final double DEFENSE_DISTANCE_SQR = 64 * 64;
    private static final double SURVIVAL_DEFENSE_DISTANCE_SQR = 128 * 128;
    private static final double ARMY_INTERCEPTION_DISTANCE_SQR = 48 * 48;
    private static final double OBJECTIVE_PROGRESS_DISTANCE = 2;
    private static final double SCOUT_REACHED_DISTANCE_SQR = 144;
    private static final int BEACON_GARRISON_MARGIN = 6;
    private static final int BEACON_RETRY_TICKS = 600;
    private static final int BEACON_INTEL_MEMORY_TICKS = 1200;
    private static final int TEAM_ADVICE_TICKS = 600;
    private static final int TEAM_ADVICE_DISTANCE = 64;
    private static final int TEAM_INTENT_DEDUPE_DISTANCE = 16;
    private static final int TEAM_INTENT_DEDUPE_TICKS = 600;
    private static final int[][] SCOUT_DIRECTIONS = {
            {1, 0}, {0, 1}, {-1, 0}, {0, -1},
            {1, 1}, {-1, 1}, {-1, -1}, {1, -1}
    };

    private final String ownerName;
    private final String displayName;
    private final BotSelf self;
    private final BotWorldView worldView;
    private final Map<BlockPos, Integer> targetCooldowns = new HashMap<>();
    private final Map<BuildingPlacement, Integer> friendlyBuildingBlockCounts = new HashMap<>();
    private final Map<BlockPos, Integer> defenseThreats = new HashMap<>();
    private final Set<Integer> beaconGuardIds = new HashSet<>();
    private BeaconPlacement trackedBeacon;
    private String trackedBeaconOwner = "";
    private double beaconAssaultBestDistance;
    private int beaconAssaultLastProgressTick;
    private int beaconRetryAfterTick;
    private int lastSeenBeaconEnemyPopulation;
    private int lastSeenBeaconEnemyTick = -1;
    private AttackObjective objective;
    private double objectiveBestDistance;
    private int objectiveFewestBlocks;
    private int objectiveLastProgressTick;
    private int regroupUntilTick;
    private int scoutUnitId = -1;
    private BlockPos scoutTarget;
    private double scoutBestDistance;
    private int scoutLastProgressTick;
    private int scoutWaypointIndex;
    private int nextCommandTick;
    private final Map<BlockPos, Integer> recentTeamIntents = new HashMap<>();
    private BotTeamAdvice humanTeamAdvice;
    private BotTeamAdvice botTeamAdvice;
    private BlockPos scoutAdviceTarget;
    private BlockPos activeDefenseIntent;
    private boolean beaconIntentActive;
    private boolean shelteringFromSun;

    BotArmy(String ownerName, String displayName, BotSelf self, BotWorldView worldView) {
        this.ownerName = ownerName;
        this.displayName = displayName;
        this.self = self;
        this.worldView = worldView;
    }

    void acceptHumanTeamAdvice(String senderName, int x, int z, int tick) {
        humanTeamAdvice = new BotTeamAdvice(senderName, x, z, tick + TEAM_ADVICE_TICKS);
    }

    void acceptBotTeamAdvice(String senderName, int x, int z, int tick) {
        botTeamAdvice = new BotTeamAdvice(senderName, x, z, tick + TEAM_ADVICE_TICKS);
    }

    void tick(ServerLevel level, RTSPlayer player, BotStrategy strategy,
              BotDifficulty difficulty, BotPersonality personality) {
        int tick = level.getServer().getTickCount();
        boolean shouldShelter = player.faction == Faction.MONSTERS
                && BotDecisionMaker.shouldShelterMonsterArmy(
                level.getDayTime(), TimeServerEvents.isBloodMoonActive());
        if (shouldShelter != shelteringFromSun)
            nextCommandTick = 0;
        shelteringFromSun = shouldShelter;
        List<LivingEntity> visibleEnemyCombatants = worldView.visibleEnemyCombatants(player);
        observeFriendlyBuildingThreats(visibleEnemyCombatants, tick);
        if (shelteringFromSun) {
            shelterMonsterArmy(level, player, self.army());
            objectiveLastProgressTick = tick;
            beaconAssaultLastProgressTick = tick;
            return;
        }
        if (tick < nextCommandTick)
            return;
        command(level, player, strategy, difficulty, personality, visibleEnemyCombatants, tick);
    }

    private void command(ServerLevel level, RTSPlayer player, BotStrategy strategy,
                         BotDifficulty difficulty, BotPersonality personality,
                         List<LivingEntity> visibleEnemyCombatants, int tick) {
        targetCooldowns.entrySet().removeIf(entry -> entry.getValue() <= tick);
        defenseThreats.entrySet().removeIf(entry -> entry.getValue() < tick);
        BlockPos teamAdviceTarget = activeTeamAdviceTarget(player, tick);

        List<LivingEntity> fullArmy = self.army();
        if (fullArmy.isEmpty()) {
            clearObjective();
            clearScout();
            beaconGuardIds.clear();
            clearBeaconAssault();
            activeDefenseIntent = null;
            beaconIntentActive = false;
            nextCommandTick = tick + difficulty.attackRefreshTicks();
            return;
        }

        LivingEntity scout = reconcileScout(fullArmy);
        if (scout != null) {
            BlockPos scoutOrderTarget = isKnownTeamObjective(player, teamAdviceTarget, tick)
                    ? null
                    : teamAdviceTarget;
            commandScout(level, player, scout, scoutOrderTarget, tick);
        }
        List<LivingEntity> main = scout == null
                ? fullArmy
                : fullArmy.stream().filter(entity -> entity != scout).toList();
        if (main.isEmpty()) {
            activeDefenseIntent = null;
            beaconIntentActive = false;
            nextCommandTick = tick + difficulty.attackRefreshTicks();
            return;
        }

        boolean survivalEnabled = SurvivalServerEvents.isEnabled();
        BeaconPlacement beacon = survivalEnabled ? null : worldView.capturableBeacon();
        syncBeaconState(beacon);
        int visibleEnemyPopulationInBeaconRing = visibleEnemyPopulationInBeaconRing(
                visibleEnemyCombatants, beacon);
        int knownEnemyPopulationInBeaconRing = knownEnemyPopulationInBeaconRing(
                beacon, visibleEnemyPopulationInBeaconRing, tick);
        BotDecisionMaker.BeaconControl beaconControl = beaconControl(beacon);
        BotDecisionMaker.BeaconOrder beaconOrder = BotDecisionMaker.chooseBeaconOrder(
                difficulty,
                personality,
                BotSelf.population(main),
                knownEnemyPopulationInBeaconRing,
                beaconControl
        );
        if (teamAdviceTarget != null
                && beaconControl == BotDecisionMaker.BeaconControl.ALLIED
                && isWithinHorizontalDistance(
                        teamAdviceTarget,
                        beaconCaptureCentre(beacon),
                        beacon.getBuilding().captureRange))
            beaconOrder = BotDecisionMaker.BeaconOrder.CONTEST;
        if (beaconOrder == BotDecisionMaker.BeaconOrder.GARRISON) {
            activateBeaconAura(beacon, personality);
            List<LivingEntity> guards = reconcileBeaconGuards(
                    main, beacon, BotDecisionMaker.beaconGuardPopulation(personality));
            garrisonBeacon(guards, beacon);
            main = main.stream()
                    .filter(entity -> !beaconGuardIds.contains(entity.getId()))
                    .toList();
            beaconOrder = BotDecisionMaker.shouldReinforceBeacon(
                    personality,
                    knownEnemyPopulationInBeaconRing,
                    BotSelf.population(guards))
                    ? BotDecisionMaker.BeaconOrder.CONTEST
                    : BotDecisionMaker.BeaconOrder.NONE;
        } else {
            beaconGuardIds.clear();
        }
        if (main.isEmpty()) {
            clearObjective();
            activeDefenseIntent = null;
            if (beaconOrder != BotDecisionMaker.BeaconOrder.NONE)
                announceBeaconIntent(level, beacon, teamAdviceTarget, tick);
            else
                beaconIntentActive = false;
            nextCommandTick = tick + difficulty.attackRefreshTicks();
            return;
        }

        BlockPos armyPos = armyCentroidRepresentative(main);
        beaconOrder = applyBeaconBackoff(beaconOrder, beaconControl, beacon, main, tick);
        if (beaconOrder == BotDecisionMaker.BeaconOrder.NONE)
            beaconIntentActive = false;
        int mainPopulation = BotSelf.population(main);
        BotWorldView.KnownEnemyBuilding target = resolveObjective(player);
        if (objective != null && target == null) {
            clearObjective();
            target = selectEnemyBuilding(player, personality, armyPos, teamAdviceTarget);
        }
        if (survivalEnabled && objective == null && target == null
                && mainPopulation >= BotDecisionMaker.attackPopulation(difficulty, personality))
            target = selectEnemyBuilding(player, personality, armyPos, teamAdviceTarget);

        boolean attackReady = objective != null || target != null
                || beaconOrder != BotDecisionMaker.BeaconOrder.NONE;
        BuildingPlacement defenseTarget = selectDefenseTarget(
                strategy, personality, player.aiHomePos, teamAdviceTarget,
                attackReady, survivalEnabled, tick);
        boolean defenseThreat = defenseTarget != null;
        List<LivingEntity> tacticalEnemyArmy = tacticalEnemyArmy(
                visibleEnemyCombatants, armyPos, player.aiHomePos);
        int tacticalEnemyPopulation = BotSelf.population(tacticalEnemyArmy);
        boolean hasRangedResponder = main.stream().anyMatch(RangedAttackerUnit.class::isInstance);

        if (tick < regroupUntilTick) {
            attackMoveArmy(main, player.aiHomePos.above());
            nextCommandTick = tick + difficulty.attackRefreshTicks();
            return;
        }

        BotDecisionMaker.ArmyOrder armyOrder = BotDecisionMaker.chooseArmyOrder(
                difficulty, personality, mainPopulation, tacticalEnemyPopulation,
                attackReady, defenseThreat);
        if (armyOrder != BotDecisionMaker.ArmyOrder.DEFEND)
            activeDefenseIntent = null;
        if (armyOrder == BotDecisionMaker.ArmyOrder.DEFEND) {
            announceDefenseIntent(level, defenseTarget, teamAdviceTarget, tick);
            if (objective != null)
                objectiveLastProgressTick = tick;
            List<LivingEntity> defenseEnemies = defenseEnemies(
                    visibleEnemyCombatants, defenseTarget, survivalEnabled);
            LivingEntity groundEnemy = closestDefenseEnemy(defenseEnemies, defenseTarget, false);
            LivingEntity flyingEnemy = hasRangedResponder
                    ? closestDefenseEnemy(tacticalEnemyArmy, defenseTarget, true)
                    : null;
            BlockPos defensePos = groundEnemy != null
                    ? groundEnemy.blockPosition()
                    : defenseTarget.getClosestGroundPos(armyPos, 1);
            if (flyingEnemy != null)
                engageEnemyArmy(main, defensePos, flyingEnemy);
            else
                attackMoveArmy(main, defensePos);
            nextCommandTick = tick + difficulty.attackRefreshTicks();
            return;
        }
        if (armyOrder == BotDecisionMaker.ArmyOrder.RETREAT) {
            abandonObjective(tick);
            regroupUntilTick = tick + REGROUP_TICKS;
            attackMoveArmy(main, player.aiHomePos.above());
            ReignOfNether.LOGGER.info("[Bot] {} regrouping with {} units", displayName, main.size());
            nextCommandTick = tick + difficulty.attackRefreshTicks();
            return;
        }
        List<LivingEntity> enemyArmyToEngage = armyOrder == BotDecisionMaker.ArmyOrder.HOLD
                ? tacticalEnemyArmy.stream()
                .filter(enemy -> enemy.blockPosition().distSqr(player.aiHomePos)
                        <= DEFENSE_DISTANCE_SQR)
                .toList()
                : tacticalEnemyArmy;
        if (shouldEngageEnemyArmy(enemyArmyToEngage, mainPopulation, player.aiHomePos,
                attackReady)) {
            if (objective != null)
                objectiveLastProgressTick = tick;
            LivingEntity groundTarget = selectGroundTarget(
                    enemyArmyToEngage, armyPos, player.aiHomePos);
            BlockPos attackMoveTarget = groundTarget != null
                    ? groundTarget.blockPosition()
                    : objective != null ? objective.anchor() : player.aiHomePos.above();
            LivingEntity rangedTarget = hasRangedResponder
                    ? selectRangedTarget(enemyArmyToEngage, armyPos, player.aiHomePos)
                    : null;
            engageEnemyArmy(main, attackMoveTarget, rangedTarget);
            nextCommandTick = tick + difficulty.attackRefreshTicks();
            return;
        }

        if (armyOrder == BotDecisionMaker.ArmyOrder.HOLD) {
            if (beaconOrder == BotDecisionMaker.BeaconOrder.NONE)
                attackMoveArmy(main, player.aiHomePos.above());
            else {
                announceBeaconIntent(level, beacon, teamAdviceTarget, tick);
                captureBeacon(main, beacon, armyPos);
            }
            nextCommandTick = tick + difficulty.attackRefreshTicks();
            return;
        }

        if (beaconOrder != BotDecisionMaker.BeaconOrder.NONE) {
            announceBeaconIntent(level, beacon, teamAdviceTarget, tick);
            captureBeacon(main, beacon, armyPos);
            nextCommandTick = tick + difficulty.attackRefreshTicks();
            return;
        }

        if (objective == null) {
            if (target == null)
                target = selectEnemyBuilding(player, personality, armyPos, teamAdviceTarget);
            if (target != null) {
                startObjective(target, armyPos, tick, main.size());
                if (teamAdvicePriority(teamAdviceTarget, target.centre()) != 0)
                    shareTeamIntent(level, objective.anchor(), tick);
            }
        }

        if (target == null) {
            attackMoveArmy(main, player.aiHomePos.above());
            nextCommandTick = tick + difficulty.attackRefreshTicks();
            return;
        }

        updateObjectiveProgress(target, main, tick);
        if (tick - objectiveLastProgressTick >= OBJECTIVE_STALL_TICKS) {
            ReignOfNether.LOGGER.info(
                    "[Bot] {} abandoning stalled target at {}", displayName, objective.origin());
            abandonObjective(tick);
            attackMoveArmy(main, player.aiHomePos.above());
            nextCommandTick = tick + difficulty.attackRefreshTicks();
            return;
        }

        attackMoveArmy(main, objective.anchor());
        nextCommandTick = tick + difficulty.attackRefreshTicks();
    }

    private BotDecisionMaker.BeaconControl beaconControl(BeaconPlacement beacon) {
        if (beacon == null)
            return BotDecisionMaker.BeaconControl.ABSENT;
        if (beacon.ownerName.isBlank())
            return BotDecisionMaker.BeaconControl.NEUTRAL;
        if (beacon.ownerName.equals(ownerName))
            return BotDecisionMaker.BeaconControl.OWNED;
        if (AlliancesServerEvents.isAllied(ownerName, beacon.ownerName))
            return BotDecisionMaker.BeaconControl.ALLIED;
        return BotDecisionMaker.BeaconControl.HOSTILE;
    }

    private static int visibleEnemyPopulationInBeaconRing(List<LivingEntity> visibleEnemies,
                                                           BeaconPlacement beacon) {
        if (beacon == null)
            return 0;
        int range = beacon.getBuilding().captureRange;
        double rangeSqr = range * range;
        return BotSelf.population(visibleEnemies.stream()
                .filter(enemy -> !(enemy instanceof WorkerUnit))
                .filter(enemy -> enemy.position().distanceToSqr(
                        beacon.centrePos.getX(),
                        beacon.minCorner.getY(),
                        beacon.centrePos.getZ()) <= rangeSqr)
                .toList());
    }

    private BlockPos activeTeamAdviceTarget(RTSPlayer player, int tick) {
        humanTeamAdvice = validTeamAdvice(humanTeamAdvice, tick);
        botTeamAdvice = validTeamAdvice(botTeamAdvice, tick);
        BotTeamAdvice advice = humanTeamAdvice != null ? humanTeamAdvice : botTeamAdvice;
        if (advice == null)
            return null;
        return new BlockPos(advice.x(), player.aiHomePos.getY() + 1, advice.z());
    }

    private BotTeamAdvice validTeamAdvice(BotTeamAdvice advice, int tick) {
        if (advice == null)
            return null;
        RTSPlayer sender = PlayerServerEvents.getRTSPlayer(advice.senderName());
        return advice.isActive(tick) && sender != null
                && AlliancesServerEvents.isAllied(ownerName, sender.name)
                ? advice
                : null;
    }

    private boolean isKnownTeamObjective(RTSPlayer player, BlockPos target, int tick) {
        if (target == null)
            return true;
        if (worldView.knownEnemyBuildings(player).stream()
                .anyMatch(building -> teamAdvicePriority(target, building.centre()) == 0))
            return true;
        if (BuildingServerEvents.getBuildings().stream()
                .filter(building -> AlliancesServerEvents.isAlliedOrOwned(
                        ownerName, building.ownerName))
                .filter(building -> defenseThreats.getOrDefault(building.originPos, -1) >= tick)
                .anyMatch(building -> teamAdvicePriority(target, building.centrePos) == 0))
            return true;
        BeaconPlacement beacon = SurvivalServerEvents.isEnabled()
                ? null
                : worldView.capturableBeacon();
        return beacon != null
                && isWithinHorizontalDistance(
                        target,
                        beaconCaptureCentre(beacon),
                        beacon.getBuilding().captureRange);
    }

    private static int teamAdvicePriority(BlockPos adviceTarget, BlockPos candidate) {
        if (adviceTarget == null)
            return 1;
        return isWithinHorizontalDistance(adviceTarget, candidate, TEAM_ADVICE_DISTANCE) ? 0 : 1;
    }

    private static boolean isWithinHorizontalDistance(BlockPos first, BlockPos second, int range) {
        long x = (long) second.getX() - first.getX();
        long z = (long) second.getZ() - first.getZ();
        return x * x + z * z <= (long) range * range;
    }

    private void captureBeacon(List<LivingEntity> army, BeaconPlacement beacon, BlockPos armyPos) {
        clearObjective();
        attackMoveArmy(army, beacon.getClosestGroundPos(armyPos, 1));
    }

    private void announceDefenseIntent(ServerLevel level, BuildingPlacement target,
                                       BlockPos teamAdviceTarget, int tick) {
        if (target.originPos.equals(activeDefenseIntent))
            return;
        activeDefenseIntent = target.originPos;
        if (teamAdvicePriority(teamAdviceTarget, target.centrePos) != 0)
            shareTeamIntent(level, target.centrePos, tick);
    }

    private void announceBeaconIntent(ServerLevel level, BeaconPlacement beacon,
                                      BlockPos teamAdviceTarget, int tick) {
        if (beaconIntentActive)
            return;
        beaconIntentActive = true;
        BlockPos target = beaconCaptureCentre(beacon);
        if (teamAdvicePriority(teamAdviceTarget, target) != 0)
            shareTeamIntent(level, target, tick);
    }

    private void shareTeamIntent(ServerLevel level, BlockPos target, int tick) {
        recentTeamIntents.entrySet().removeIf(entry -> entry.getValue() <= tick);
        if (recentTeamIntents.keySet().stream().anyMatch(position ->
                isWithinHorizontalDistance(position, target, TEAM_INTENT_DEDUPE_DISTANCE)))
            return;
        recentTeamIntents.put(target, tick + TEAM_INTENT_DEDUPE_TICKS);
        BotServerEvents.shareTeamIntent(level, ownerName, target, tick);
    }

    private BotDecisionMaker.BeaconOrder applyBeaconBackoff(
            BotDecisionMaker.BeaconOrder desiredOrder,
            BotDecisionMaker.BeaconControl control,
            BeaconPlacement beacon,
            List<LivingEntity> army,
            int tick) {
        if (beacon == null) {
            return desiredOrder;
        }
        if (control == BotDecisionMaker.BeaconControl.OWNED
                || control == BotDecisionMaker.BeaconControl.ALLIED) {
            beaconRetryAfterTick = 0;
            clearBeaconAssault();
            return desiredOrder;
        }
        if (desiredOrder == BotDecisionMaker.BeaconOrder.NONE) {
            clearBeaconAssault();
            if (tick >= beaconRetryAfterTick) {
                beaconRetryAfterTick = tick + BEACON_RETRY_TICKS;
                ReignOfNether.LOGGER.info(
                        "[Bot] {} delaying a defended beacon push", displayName);
            }
            return desiredOrder;
        }
        if (tick < beaconRetryAfterTick)
            return BotDecisionMaker.BeaconOrder.NONE;

        BlockPos captureCentre = beaconCaptureCentre(beacon);
        double distance = closestArmyDistance(army, captureCentre);
        if (beaconAssaultLastProgressTick == 0) {
            beaconAssaultBestDistance = distance;
            beaconAssaultLastProgressTick = tick;
        } else if (distance + OBJECTIVE_PROGRESS_DISTANCE < beaconAssaultBestDistance) {
            beaconAssaultBestDistance = distance;
            beaconAssaultLastProgressTick = tick;
        } else if (tick - beaconAssaultLastProgressTick >= OBJECTIVE_STALL_TICKS) {
            beaconRetryAfterTick = tick + BEACON_RETRY_TICKS;
            clearBeaconAssault();
            ReignOfNether.LOGGER.info(
                    "[Bot] {} abandoning a stalled beacon push", displayName);
            return BotDecisionMaker.BeaconOrder.NONE;
        }
        return desiredOrder;
    }

    private void clearBeaconState() {
        trackedBeacon = null;
        trackedBeaconOwner = "";
        beaconRetryAfterTick = 0;
        beaconIntentActive = false;
        lastSeenBeaconEnemyPopulation = 0;
        lastSeenBeaconEnemyTick = -1;
        clearBeaconAssault();
    }

    private void syncBeaconState(BeaconPlacement beacon) {
        if (beacon == null) {
            clearBeaconState();
            return;
        }
        if (trackedBeacon == beacon && trackedBeaconOwner.equals(beacon.ownerName))
            return;
        boolean differentBeacon = trackedBeacon != beacon;
        trackedBeacon = beacon;
        trackedBeaconOwner = beacon.ownerName;
        beaconRetryAfterTick = 0;
        if (differentBeacon) {
            lastSeenBeaconEnemyPopulation = 0;
            lastSeenBeaconEnemyTick = -1;
        }
        clearBeaconAssault();
    }

    private int knownEnemyPopulationInBeaconRing(BeaconPlacement beacon,
                                                   int visibleEnemyPopulation,
                                                   int tick) {
        if (beacon == null)
            return 0;
        if (worldView.isVisible(beaconCaptureCentre(beacon))) {
            lastSeenBeaconEnemyPopulation = visibleEnemyPopulation;
            lastSeenBeaconEnemyTick = tick;
        }
        return lastSeenBeaconEnemyTick >= 0
                && tick - lastSeenBeaconEnemyTick < BEACON_INTEL_MEMORY_TICKS
                ? lastSeenBeaconEnemyPopulation
                : 0;
    }

    private void clearBeaconAssault() {
        beaconAssaultBestDistance = 0;
        beaconAssaultLastProgressTick = 0;
    }

    private static BlockPos beaconCaptureCentre(BeaconPlacement beacon) {
        return new BlockPos(
                beacon.centrePos.getX(), beacon.minCorner.getY(), beacon.centrePos.getZ());
    }

    private void activateBeaconAura(BeaconPlacement beacon, BotPersonality personality) {
        if (!beacon.ownerName.equals(ownerName))
            return;
        UnitAction action = BotDecisionMaker.beaconAuraAction(personality);
        if (beacon.getAuraEffect() == BeaconPlacement.getMobEffectForAction(action))
            return;
        boolean offCooldown = beacon.getAbilities().stream()
                .filter(ability -> ability.action == action)
                .anyMatch(ability -> ability.isOffCooldown(beacon));
        if (!offCooldown)
            return;
        UnitServerEvents.addActionItem(
                ownerName,
                action,
                -1,
                new int[0],
                BlockPos.ZERO,
                beacon.originPos
        );
    }

    private List<LivingEntity> reconcileBeaconGuards(List<LivingEntity> army, BeaconPlacement beacon,
                                                      int targetPopulation) {
        beaconGuardIds.removeIf(id -> army.stream().noneMatch(entity -> entity.getId() == id));
        List<LivingEntity> retained = army.stream()
                .filter(entity -> beaconGuardIds.contains(entity.getId()))
                .sorted(Comparator.comparingInt(LivingEntity::getId))
                .toList();
        int guardPopulation = BotSelf.population(retained);
        for (int index = retained.size() - 1; index >= 0 && guardPopulation > targetPopulation; index--) {
            LivingEntity entity = retained.get(index);
            beaconGuardIds.remove(entity.getId());
            guardPopulation -= BotSelf.population(entity);
        }

        List<LivingEntity> candidates = army.stream()
                .filter(entity -> !beaconGuardIds.contains(entity.getId()))
                .sorted(Comparator
                        .comparingInt((LivingEntity entity) -> isFlying(entity) ? 1 : 0)
                        .thenComparingDouble(entity -> entity.position().distanceToSqr(
                                beacon.centrePos.getX(),
                                beacon.minCorner.getY(),
                                beacon.centrePos.getZ()))
                        .thenComparingInt(BotSelf::population)
                        .thenComparingInt(LivingEntity::getId))
                .toList();
        for (LivingEntity entity : candidates) {
            int population = BotSelf.population(entity);
            if (guardPopulation + population <= targetPopulation) {
                beaconGuardIds.add(entity.getId());
                guardPopulation += population;
            }
        }
        return army.stream()
                .filter(entity -> beaconGuardIds.contains(entity.getId()))
                .toList();
    }

    private void garrisonBeacon(List<LivingEntity> army, BeaconPlacement beacon) {
        int innerRange = Math.max(1, beacon.getBuilding().captureRange - BEACON_GARRISON_MARGIN);
        double innerRangeSqr = innerRange * innerRange;
        List<LivingEntity> inside = new ArrayList<>();
        List<LivingEntity> outside = new ArrayList<>();
        for (LivingEntity entity : army) {
            if (entity.position().distanceToSqr(
                    beacon.centrePos.getX(),
                    beacon.minCorner.getY(),
                    beacon.centrePos.getZ()) <= innerRangeSqr)
                inside.add(entity);
            else
                outside.add(entity);
        }
        holdArmy(inside);
        attackMoveArmy(outside, beacon.getClosestGroundPos(beacon.centrePos, 1));
    }

    private LivingEntity reconcileScout(List<LivingEntity> army) {
        if (!worldView.fogEnabled() || army.size() < 2) {
            clearScout();
            return null;
        }
        LivingEntity current = army.stream()
                .filter(entity -> entity.getId() == scoutUnitId)
                .findFirst()
                .orElse(null);
        if (current != null)
            return current;

        LivingEntity scout = army.stream()
                .min(Comparator
                        .comparingInt((LivingEntity entity) -> BotSelf.population(entity))
                        .thenComparingInt(LivingEntity::getId))
                .orElseThrow();
        scoutUnitId = scout.getId();
        scoutTarget = null;
        scoutAdviceTarget = null;
        return scout;
    }

    private void commandScout(ServerLevel level, RTSPlayer player, LivingEntity scout,
                              BlockPos adviceTarget, int tick) {
        List<LivingEntity> scoutGroup = List.of(scout);
        if (adviceTarget != null && !adviceTarget.equals(scoutAdviceTarget)) {
            scoutAdviceTarget = adviceTarget;
            scoutTarget = adviceTarget;
            resetScoutProgress(scout, tick);
            attackMoveArmy(scoutGroup, scoutTarget);
            ReignOfNether.LOGGER.info("[Bot] {} scouting team marker at {}", displayName, scoutTarget);
        } else if (adviceTarget == null && scoutAdviceTarget != null) {
            scoutAdviceTarget = null;
            scoutTarget = null;
        }
        if (scoutTarget != null && worldView.isVisible(scoutTarget)
                && BotBuildingPlanner.isChunkLoaded(level, scoutTarget)) {
            BlockPos ground = BotBuildingPlanner.groundAt(level, scoutTarget.getX(), scoutTarget.getZ());
            if (Math.abs(ground.getY() - player.aiHomePos.getY()) > 8) {
                scoutTarget = null;
            } else {
                BlockPos adjustedTarget = ground.above();
                if (!adjustedTarget.equals(scoutTarget)) {
                    scoutTarget = adjustedTarget;
                    resetScoutProgress(scout, tick);
                    attackMoveArmy(scoutGroup, scoutTarget);
                }
            }
        }

        if (scoutTarget != null) {
            double currentDistance = Math.sqrt(scout.blockPosition().distSqr(scoutTarget));
            BotDecisionMaker.ScoutWaypointDecision decision = BotDecisionMaker.evaluateScoutWaypoint(
                    currentDistance * currentDistance <= SCOUT_REACHED_DISTANCE_SQR,
                    scoutBestDistance,
                    currentDistance,
                    tick - scoutLastProgressTick
            );
            if (decision == BotDecisionMaker.ScoutWaypointDecision.PROGRESS) {
                scoutBestDistance = currentDistance;
                scoutLastProgressTick = tick;
                return;
            }
            if (decision == BotDecisionMaker.ScoutWaypointDecision.KEEP)
                return;
        }

        scoutTarget = nextScoutTarget(level, player.aiHomePos);
        resetScoutProgress(scout, tick);
        attackMoveArmy(scoutGroup, scoutTarget);
        ReignOfNether.LOGGER.info("[Bot] {} scouting at {}", displayName, scoutTarget);
    }

    private void observeFriendlyBuildingThreats(List<LivingEntity> visibleEnemies, int tick) {
        List<BuildingPlacement> friendlyBuildings = BuildingServerEvents.getBuildings().stream()
                .filter(building -> AlliancesServerEvents.isAlliedOrOwned(ownerName, building.ownerName))
                .toList();
        friendlyBuildingBlockCounts.keySet().removeIf(building ->
                !friendlyBuildings.contains(building) || !worldView.isVisible(building.centrePos));
        defenseThreats.keySet().removeIf(origin -> friendlyBuildings.stream()
                .noneMatch(building -> building.originPos.equals(origin)));
        boolean survivalEnabled = SurvivalServerEvents.isEnabled();
        double defenseDistanceSqr = survivalEnabled
                ? SURVIVAL_DEFENSE_DISTANCE_SQR
                : DEFENSE_DISTANCE_SQR;
        Set<BuildingPlacement> proximityThreats = new HashSet<>();
        if (survivalEnabled) {
            for (LivingEntity enemy : visibleEnemies) {
                BuildingPlacement nearest = null;
                double nearestDistance = Double.MAX_VALUE;
                for (BuildingPlacement building : friendlyBuildings) {
                    if (!worldView.isVisible(building.centrePos)
                            || building.getBuilding().invulnerable)
                        continue;
                    double distance = distanceToDefenseBuildingSqr(enemy, building, true);
                    if (distance <= defenseDistanceSqr && distance < nearestDistance) {
                        nearest = building;
                        nearestDistance = distance;
                    }
                }
                if (nearest != null)
                    proximityThreats.add(nearest);
            }
        }

        for (BuildingPlacement building : friendlyBuildings) {
            if (!worldView.isVisible(building.centrePos))
                continue;
            int blocksPlaced = building.getBlocksPlaced();
            Integer previous = friendlyBuildingBlockCounts.put(building, blocksPlaced);
            boolean buildingDamaged = previous != null && blocksPlaced < previous;
            if (!survivalEnabled && !buildingDamaged)
                continue;
            boolean enemyNearby = proximityThreats.contains(building)
                    || buildingDamaged && visibleEnemies.stream().anyMatch(enemy ->
                            distanceToDefenseBuildingSqr(enemy, building, survivalEnabled)
                                    <= defenseDistanceSqr);
            if (!BotDecisionMaker.shouldRememberDefenseThreat(
                    survivalEnabled, buildingDamaged, enemyNearby))
                continue;

            int memoryTicks = building.ownerName.equals(ownerName)
                    ? DEFENSE_MEMORY_TICKS
                    : ALLY_DEFENSE_MEMORY_TICKS;
            Integer previousExpiry = defenseThreats.put(building.originPos, tick + memoryTicks);
            if (previousExpiry == null || previousExpiry < tick) {
                ReignOfNether.LOGGER.info("[Bot] {} defending {} {} at {}",
                        displayName, buildingDamaged ? "damaged" : "threatened",
                        building.getBuilding().name, building.originPos);
            }
        }
    }

    private static List<LivingEntity> defenseEnemies(List<LivingEntity> visibleEnemies,
                                                       BuildingPlacement building,
                                                       boolean survivalEnabled) {
        double distanceSqr = survivalEnabled
                ? SURVIVAL_DEFENSE_DISTANCE_SQR
                : DEFENSE_DISTANCE_SQR;
        return visibleEnemies.stream()
                .filter(enemy -> distanceToDefenseBuildingSqr(enemy, building, survivalEnabled)
                        <= distanceSqr)
                .toList();
    }

    private static double distanceToDefenseBuildingSqr(LivingEntity enemy,
                                                         BuildingPlacement building,
                                                         boolean survivalEnabled) {
        return survivalEnabled
                ? building.centrePos.distToCenterSqr(enemy.position())
                : enemy.blockPosition().distSqr(building.centrePos);
    }

    private static LivingEntity closestDefenseEnemy(List<LivingEntity> enemies,
                                                      BuildingPlacement building,
                                                      boolean flying) {
        return enemies.stream()
                .filter(enemy -> isFlying(enemy) == flying)
                .min(Comparator
                        .comparingDouble((LivingEntity enemy) ->
                                building.centrePos.distToCenterSqr(enemy.position()))
                        .thenComparingDouble(LivingEntity::getHealth)
                        .thenComparingInt(LivingEntity::getId))
                .orElse(null);
    }

    private BuildingPlacement selectDefenseTarget(BotStrategy strategy, BotPersonality personality,
                                                    BlockPos home, BlockPos teamAdviceTarget,
                                                    boolean attackReady,
                                                    boolean survivalEnabled, int tick) {
        ProductionPlacement military = self.militaryBuilding(strategy);
        return BuildingServerEvents.getBuildings().stream()
                .filter(building -> AlliancesServerEvents.isAlliedOrOwned(ownerName, building.ownerName))
                .filter(building -> defenseThreats.getOrDefault(building.originPos, -1) >= tick)
                .filter(building -> BotDecisionMaker.shouldDefend(
                        personality,
                        attackReady,
                        survivalEnabled,
                        building.ownerName.equals(ownerName),
                        building.isCapitol || building == military
                ))
                .min(Comparator
                        .comparingInt((BuildingPlacement building) ->
                                BotDecisionMaker.defensePriority(
                                        survivalEnabled,
                                        building.ownerName.equals(ownerName),
                                        building.isCapitol || building == military))
                        .thenComparingInt(building ->
                                teamAdvicePriority(teamAdviceTarget, building.centrePos))
                        .thenComparingDouble(building -> building.centrePos.distSqr(home))
                        .thenComparingInt(building -> building.originPos.getX())
                        .thenComparingInt(building -> building.originPos.getY())
                        .thenComparingInt(building -> building.originPos.getZ()))
                .orElse(null);
    }

    private BotWorldView.KnownEnemyBuilding selectEnemyBuilding(
            RTSPlayer player, BotPersonality personality, BlockPos armyPos,
            BlockPos teamAdviceTarget) {
        return worldView.knownEnemyBuildings(player).stream()
                .filter(building -> !building.invulnerable())
                .filter(building -> !targetCooldowns.containsKey(building.origin()))
                .min(Comparator
                        .comparingInt((BotWorldView.KnownEnemyBuilding building) ->
                                teamAdvicePriority(teamAdviceTarget, building.centre()))
                        .thenComparingInt(building ->
                                BotDecisionMaker.targetPriority(
                                        personality, building.capitol(), building.production()))
                        .thenComparingDouble(building -> building.centre().distSqr(armyPos))
                        .thenComparingInt(building -> building.origin().getX())
                        .thenComparingInt(building -> building.origin().getY())
                        .thenComparingInt(building -> building.origin().getZ()))
                .orElse(null);
    }

    private void startObjective(BotWorldView.KnownEnemyBuilding target, BlockPos armyPos, int tick, int armySize) {
        BlockPos anchor = target.closestGroundPos(armyPos, 1);
        objective = new AttackObjective(target.origin(), anchor, target.ownerName());
        objectiveBestDistance = Math.sqrt(armyPos.distSqr(anchor));
        objectiveFewestBlocks = target.blocksPlaced();
        objectiveLastProgressTick = tick;
        ReignOfNether.LOGGER.info("[Bot] {} attacking {} at {} with {} units",
                displayName, PlayerServerEvents.getPlayerDisplayName(target.ownerName()),
                target.origin(), armySize);
    }

    private BotWorldView.KnownEnemyBuilding resolveObjective(RTSPlayer player) {
        if (objective == null || !BotWorldView.isPotentialEnemy(player, objective.targetOwner()))
            return null;
        BotWorldView.KnownEnemyBuilding target = worldView.knownEnemyBuilding(
                player, objective.origin(), objective.targetOwner());
        return target == null || target.invulnerable() ? null : target;
    }

    private void updateObjectiveProgress(BotWorldView.KnownEnemyBuilding target, List<LivingEntity> army, int tick) {
        double distance = closestArmyDistance(army, objective.anchor());
        int blocks = target.blocksPlaced();
        if (distance + OBJECTIVE_PROGRESS_DISTANCE < objectiveBestDistance || blocks < objectiveFewestBlocks) {
            objectiveBestDistance = Math.min(objectiveBestDistance, distance);
            objectiveFewestBlocks = Math.min(objectiveFewestBlocks, blocks);
            objectiveLastProgressTick = tick;
        }
    }

    private void abandonObjective(int tick) {
        if (objective != null)
            targetCooldowns.put(objective.origin(), tick + TARGET_REVISIT_TICKS);
        clearObjective();
    }

    private void clearObjective() {
        objective = null;
        objectiveBestDistance = 0;
        objectiveFewestBlocks = 0;
        objectiveLastProgressTick = 0;
    }

    private boolean shouldEngageEnemyArmy(List<LivingEntity> enemies, int armyPopulation, BlockPos homePos,
                                          boolean attackReady) {
        List<LivingEntity> groundEnemies = enemies.stream().filter(enemy -> !isFlying(enemy)).toList();
        boolean homeThreat = groundEnemies.stream().anyMatch(enemy ->
                enemy.blockPosition().distSqr(homePos) <= DEFENSE_DISTANCE_SQR);
        int enemyPopulation = BotSelf.population(groundEnemies);
        return BotDecisionMaker.shouldFocusEnemyArmy(
                homeThreat, attackReady, armyPopulation, enemyPopulation);
    }

    private LivingEntity selectGroundTarget(List<LivingEntity> enemies, BlockPos armyPos, BlockPos homePos) {
        return enemies.stream()
                .filter(enemy -> !isFlying(enemy))
                .min(Comparator
                        .comparingInt((LivingEntity enemy) ->
                                enemy.blockPosition().distSqr(homePos) <= DEFENSE_DISTANCE_SQR ? 0 : 1)
                        .thenComparingDouble(enemy -> enemy.blockPosition().distSqr(armyPos))
                        .thenComparingDouble(LivingEntity::getHealth)
                        .thenComparingInt(LivingEntity::getId))
                .orElse(null);
    }

    private LivingEntity selectRangedTarget(List<LivingEntity> enemies, BlockPos armyPos, BlockPos homePos) {
        return enemies.stream()
                .min(Comparator
                        .comparingInt((LivingEntity enemy) -> BotDecisionMaker.rangedTargetPriority(
                                enemy.blockPosition().distSqr(homePos) <= DEFENSE_DISTANCE_SQR,
                                isFlying(enemy)))
                        .thenComparingDouble(enemy -> enemy.blockPosition().distSqr(armyPos))
                        .thenComparingDouble(LivingEntity::getHealth)
                        .thenComparingInt(LivingEntity::getId))
                .orElse(null);
    }

    private static boolean isFlying(LivingEntity entity) {
        return entity instanceof Unit unit && unit.isFlyingUnit();
    }

    private static List<LivingEntity> tacticalEnemyArmy(List<LivingEntity> enemies, BlockPos armyPos,
                                                         BlockPos homePos) {
        return enemies.stream()
                .filter(enemy -> enemy.blockPosition().distSqr(homePos) <= DEFENSE_DISTANCE_SQR
                        || enemy.blockPosition().distSqr(armyPos) <= ARMY_INTERCEPTION_DISTANCE_SQR)
                .toList();
    }

    private BlockPos nextScoutTarget(ServerLevel level, BlockPos home) {
        int[][] directions = SCOUT_DIRECTIONS.clone();
        double inwardX = level.getWorldBorder().getCenterX() - home.getX();
        double inwardZ = level.getWorldBorder().getCenterZ() - home.getZ();
        Arrays.sort(directions, Comparator.<int[]>comparingDouble(
                direction -> direction[0] * inwardX + direction[1] * inwardZ).reversed());
        for (int attempt = 0; attempt < SCOUT_DIRECTIONS.length * 4; attempt++) {
            int index = scoutWaypointIndex++ % (SCOUT_DIRECTIONS.length * 4);
            int[] direction = directions[index % directions.length];
            int radius = 128 * (index / SCOUT_DIRECTIONS.length + 1);
            BlockPos column = new BlockPos(
                    home.getX() + direction[0] * radius,
                    home.getY(),
                    home.getZ() + direction[1] * radius
            );
            if (!level.getWorldBorder().isWithinBounds(column))
                continue;
            if (!worldView.isVisible(column) || !BotBuildingPlanner.isChunkLoaded(level, column))
                return column.above();
            BlockPos ground = BotBuildingPlanner.groundAt(level, column.getX(), column.getZ());
            if (Math.abs(ground.getY() - home.getY()) <= 8)
                return ground.above();
        }
        return home.above();
    }

    private void resetScoutProgress(LivingEntity scout, int tick) {
        scoutBestDistance = Math.sqrt(scout.blockPosition().distSqr(scoutTarget));
        scoutLastProgressTick = tick;
    }

    private void clearScout() {
        scoutUnitId = -1;
        scoutTarget = null;
        scoutAdviceTarget = null;
        scoutBestDistance = 0;
        scoutLastProgressTick = 0;
    }

    private static double closestArmyDistance(List<LivingEntity> army, BlockPos target) {
        return army.stream()
                .mapToDouble(entity -> Math.sqrt(entity.blockPosition().distSqr(target)))
                .min()
                .orElse(Double.POSITIVE_INFINITY);
    }

    private static BlockPos armyCentroidRepresentative(List<LivingEntity> army) {
        long x = 0;
        long y = 0;
        long z = 0;
        for (LivingEntity entity : army) {
            BlockPos pos = entity.blockPosition();
            x += pos.getX();
            y += pos.getY();
            z += pos.getZ();
        }
        BlockPos mean = new BlockPos(
                (int) (x / army.size()),
                (int) (y / army.size()),
                (int) (z / army.size())
        );
        return army.stream()
                .min(Comparator.comparingDouble(entity -> entity.blockPosition().distSqr(mean)))
                .orElseThrow()
                .blockPosition();
    }

    private void holdArmy(List<LivingEntity> army) {
        int[] ids = army.stream()
                .filter(entity -> entity instanceof Unit unit && !unit.getHoldPosition())
                .mapToInt(LivingEntity::getId)
                .toArray();
        if (ids.length == 0)
            return;
        UnitServerEvents.addActionItem(
                ownerName,
                UnitAction.HOLD,
                -1,
                ids,
                BlockPos.ZERO,
                BlockPos.ZERO
        );
    }

    private void shelterMonsterArmy(ServerLevel level, RTSPlayer player, List<LivingEntity> army) {
        List<LivingEntity> sheltered = new ArrayList<>();
        for (LivingEntity entity : army) {
            if (isShelteredFromSun(level, entity)) {
                sheltered.add(entity);
                continue;
            }
            BuildingPlacement nightSource = closestFriendlyNightSource(entity);
            BlockPos target = nightSource == null
                    ? player.aiHomePos.above()
                    : nightSource.getClosestGroundPos(entity.blockPosition(), 3);
            moveUnit(entity, target);
        }
        holdArmy(sheltered);
    }

    private static boolean isShelteredFromSun(ServerLevel level, LivingEntity entity) {
        BlockPos eyePos = BlockPos.containing(entity.getX(), entity.getEyeY(), entity.getZ());
        return NightUtils.isInRangeOfNightSource(entity.getEyePosition(), false)
                || !level.canSeeSky(eyePos)
                || entity.isInWaterRainOrBubble()
                || entity.isInPowderSnow
                || entity.wasInPowderSnow;
    }

    private BuildingPlacement closestFriendlyNightSource(LivingEntity entity) {
        return BuildingServerEvents.getBuildings().stream()
                .filter(building -> AlliancesServerEvents.isAlliedOrOwned(ownerName, building.ownerName))
                .filter(building -> !building.isDestroyedServerside)
                .filter(building -> {
                    NightSourceAddon source = building.getBuilding().getActiveAddon(NightSourceAddon.class);
                    return source != null && source.getNightRange(building) > 0;
                })
                .min(Comparator
                        .comparingDouble((BuildingPlacement building) ->
                                building.centrePos.distToCenterSqr(entity.position()))
                        .thenComparingInt(building -> building.originPos.getX())
                        .thenComparingInt(building -> building.originPos.getY())
                        .thenComparingInt(building -> building.originPos.getZ()))
                .orElse(null);
    }

    private void moveUnit(LivingEntity entity, BlockPos target) {
        Unit unit = (Unit) entity;
        boolean hasAttackMove = entity instanceof AttackerUnit attacker
                && attacker.getAttackMoveTarget() != null;
        if (target.equals(unit.getMoveGoal().getMoveTarget()) && !hasAttackMove)
            return;
        UnitServerEvents.addActionItem(
                ownerName,
                UnitAction.MOVE,
                -1,
                new int[]{entity.getId()},
                target,
                BlockPos.ZERO
        );
    }

    private void attackMoveArmy(List<LivingEntity> army, BlockPos target) {
        int[] ids = army.stream()
                .filter(entity -> !(entity instanceof AttackerUnit attacker)
                        || !target.equals(attacker.getAttackMoveTarget()))
                .mapToInt(LivingEntity::getId)
                .toArray();
        if (ids.length == 0)
            return;
        UnitServerEvents.addActionItem(
                ownerName,
                UnitAction.ATTACK_MOVE,
                -1,
                ids,
                target,
                BlockPos.ZERO
        );
    }

    private void engageEnemyArmy(List<LivingEntity> army, BlockPos attackMoveTarget,
                                 LivingEntity rangedTarget) {
        List<LivingEntity> attackMoveUnits = army.stream()
                .filter(entity -> !(entity instanceof RangedAttackerUnit))
                .toList();
        attackMoveArmy(attackMoveUnits, attackMoveTarget);

        if (rangedTarget == null)
            return;

        int[] rangedIds = army.stream()
                .filter(RangedAttackerUnit.class::isInstance)
                .filter(entity -> !(entity instanceof Unit unit)
                        || !(entity instanceof Mob mob)
                        || mob.getTarget() != rangedTarget
                        || !unit.getTargetGoal().forced)
                .mapToInt(LivingEntity::getId)
                .toArray();
        if (rangedIds.length == 0)
            return;
        UnitServerEvents.addActionItem(
                ownerName,
                UnitAction.ATTACK,
                rangedTarget.getId(),
                rangedIds,
                rangedTarget.blockPosition(),
                BlockPos.ZERO
        );
    }

    private record AttackObjective(BlockPos origin, BlockPos anchor, String targetOwner) {
    }

    private record BotTeamAdvice(String senderName, int x, int z, int expiresAtTick) {
        boolean isActive(int tick) {
            return tick < expiresAtTick;
        }
    }
}
