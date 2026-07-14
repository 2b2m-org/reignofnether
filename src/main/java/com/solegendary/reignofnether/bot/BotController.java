package com.solegendary.reignofnether.bot;

import com.solegendary.reignofnether.ReignOfNether;
import com.solegendary.reignofnether.building.Building;
import com.solegendary.reignofnether.building.BuildingClientboundPacket;
import com.solegendary.reignofnether.building.BuildingPlacement;
import com.solegendary.reignofnether.building.BuildingServerEvents;
import com.solegendary.reignofnether.building.buildings.placements.PortalPlacement;
import com.solegendary.reignofnether.building.buildings.placements.ProductionPlacement;
import com.solegendary.reignofnether.building.production.ProductionItem;
import com.solegendary.reignofnether.player.PlayerServerEvents;
import com.solegendary.reignofnether.player.RTSPlayer;
import com.solegendary.reignofnether.resources.ResourceCost;
import com.solegendary.reignofnether.resources.ResourceName;
import com.solegendary.reignofnether.resources.ResourceSource;
import com.solegendary.reignofnether.resources.ResourceSources;
import com.solegendary.reignofnether.resources.Resources;
import com.solegendary.reignofnether.unit.UnitServerEvents;
import com.solegendary.reignofnether.unit.goals.GatherResourcesGoal;
import com.solegendary.reignofnether.unit.interfaces.RangedAttackerUnit;
import com.solegendary.reignofnether.unit.interfaces.Unit;
import com.solegendary.reignofnether.unit.interfaces.WorkerUnit;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Rotation;

import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class BotController {
    private static final int REPAIR_THREAT_DISTANCE_SQR = 64 * 64;
    private static final int WORKER_SAFETY_INTERVAL_TICKS = 10;
    private static final int WORKER_FLEE_MEMORY_TICKS = 100;
    private static final int WORKER_FLEE_TARGET_CHANGE_DISTANCE_SQR = 8 * 8;
    private static final int WORKER_FLEE_ARRIVAL_DISTANCE_SQR = 4 * 4;

    private final String ownerName;
    private final String displayName;
    private final BotSelf self;
    private final BotWorldView worldView;
    private final BotArmy army;
    private final Map<Integer, Integer> fleeingWorkerUntilTicks = new HashMap<>();
    private BotGoal currentGoal = BotGoal.WAIT_FOR_CAPITOL;
    private int nextDecisionTick;
    private int nextWorkerSafetyTick;
    private int nextWorkerReconcileTick;
    private int repairWoodReserve;
    private boolean repairAssigned;

    public BotController(String ownerName) {
        this.ownerName = ownerName;
        RTSPlayer player = PlayerServerEvents.getRTSPlayer(ownerName);
        displayName = player == null ? ownerName : player.displayName;
        self = new BotSelf(ownerName);
        worldView = new BotWorldView();
        army = new BotArmy(ownerName, displayName, self, worldView);
    }

    public String getDisplayName() {
        return displayName;
    }

    void acceptHumanTeamAdvice(String senderName, int x, int z, int tick) {
        army.acceptHumanTeamAdvice(senderName, x, z, tick);
    }

    void acceptBotTeamAdvice(String senderName, int x, int z, int tick) {
        army.acceptBotTeamAdvice(senderName, x, z, tick);
    }

    public void tick(ServerLevel level) {
        RTSPlayer player = PlayerServerEvents.getRTSPlayer(ownerName);
        if (player == null || !player.aiControlled || player.aiHomePos == null)
            return;

        int tick = level.getServer().getTickCount();
        BotDifficulty difficulty = player.aiDifficulty;
        BotPersonality personality = player.aiPersonality;
        BotStrategy strategy = BotStrategy.forFaction(player.faction);
        if (tick >= nextWorkerSafetyTick) {
            worldView.observe(player);
            maintainWorkerSafety(level, player, strategy, tick);
            nextWorkerSafetyTick = tick + WORKER_SAFETY_INTERVAL_TICKS;
        }
        if (repairAssigned && stopRepairsAtWoodReserve())
            nextWorkerReconcileTick = 0;
        if (tick < nextDecisionTick)
            return;
        nextDecisionTick = tick + difficulty.decisionIntervalTicks();

        self.reconcilePortalRoles(strategy);
        if (strategy.usesTransformingPortals())
            maintainPortalTransforms(strategy, difficulty);

        BotDecisionContext context = createDecisionContext(strategy);
        BotGoal nextGoal = BotDecisionMaker.chooseGoal(difficulty, personality, context);
        repairWoodReserve = repairWoodReserve(strategy);
        List<LivingEntity> workers = self.workers();
        List<LivingEntity> availableWorkers = workers.stream()
                .filter(worker -> !isFleeingWorker(worker))
                .toList();
        List<LivingEntity> visibleEnemies = worldView.visibleEnemyCombatants(player);
        List<LivingEntity> visibleMilitaryEnemies = visibleEnemies.stream()
                .filter(entity -> !(entity instanceof WorkerUnit))
                .toList();
        boolean orphanedConstruction = hasOrphanedConstruction(availableWorkers, visibleMilitaryEnemies);
        if (orphanedConstruction)
            nextWorkerReconcileTick = 0;
        if (maintainRepairs(availableWorkers, visibleEnemies,
                isConstructionGoal(nextGoal) || orphanedConstruction))
            nextWorkerReconcileTick = 0;
        if (tick >= nextWorkerReconcileTick) {
            boolean productionPriority = !isConstructionGoal(nextGoal)
                    && (nextGoal == BotGoal.WAIT_FOR_MILITARY || context.militaryReady()
                    || context.workersAndQueued()
                    >= BotDecisionMaker.targetWorkers(difficulty, personality));
            assignWorkerJobs(strategy, difficulty, personality, productionPriority,
                    availableWorkers, visibleMilitaryEnemies);
            nextWorkerReconcileTick = tick + difficulty.workerReconcileTicks();
        }

        if (nextGoal != currentGoal) {
            currentGoal = nextGoal;
            ReignOfNether.LOGGER.info("[Bot] {} goal={}", displayName, currentGoal);
        }
        executeGoal(level, player, strategy, difficulty, personality, currentGoal);
        army.tick(level, player, strategy, difficulty, personality);
    }

    public String describe() {
        RTSPlayer player = PlayerServerEvents.getRTSPlayer(ownerName);
        if (player == null)
            return displayName + " (inactive)";

        int workers = self.workers().size();
        int army = self.army().size();
        int population = UnitServerEvents.getCurrentPopulation(ownerName);
        int supply = BuildingServerEvents.getTotalPopulationSupply(ownerName);
        Resources resources = self.resources();
        String resourceText = resources == null
                ? "resources unavailable"
                : "food=" + resources.food + " wood=" + resources.wood + " ore=" + resources.ore;
        return displayName + " faction=" + player.faction.name().toLowerCase()
                + " difficulty=" + player.aiDifficulty.name().toLowerCase()
                + " personality=" + player.aiPersonality.name().toLowerCase()
                + " goal=" + currentGoal.name().toLowerCase()
                + " workers=" + workers + " army=" + army
                + " population=" + population + "/" + supply + " " + resourceText;
    }

    private BotDecisionContext createDecisionContext(BotStrategy strategy) {
        BuildingPlacement capitol = self.building(strategy.capitol());
        ProductionPlacement military = self.militaryBuilding(strategy);
        int queuedWorkers = self.countQueued(strategy.worker());

        return new BotDecisionContext(
                capitol != null,
                capitol != null && capitol.isBuilt,
                self.workers().size() + queuedWorkers,
                armyAndQueuedPopulation(strategy),
                UnitServerEvents.getCurrentPopulation(ownerName),
                BuildingServerEvents.getTotalPopulationSupply(ownerName),
                self.supplyUnderConstruction(strategy),
                strategy.worker().getCost(false, ownerName).population,
                Math.max(strategy.melee().getCost(false, ownerName).population,
                        strategy.ranged().getCost(false, ownerName).population),
                self.building(strategy.farm()) != null,
                military != null,
                military != null && military.isBuilt && (!strategy.usesTransformingPortals()
                        || military instanceof PortalPlacement portal
                        && portal.getPortalType() == PortalPlacement.PortalType.MILITARY)
        );
    }

    private void executeGoal(ServerLevel level, RTSPlayer player, BotStrategy strategy, BotDifficulty difficulty,
                             BotPersonality personality, BotGoal goal) {
        switch (goal) {
            case BUILD_CAPITOL -> buildStructure(
                    level, player, strategy.capitol(), null, false, difficulty);
            case WAIT_FOR_CAPITOL, WAIT_FOR_MILITARY -> {
            }
            case BUILD_SUPPLY -> {
                if (!buildStructure(level, player, strategy.supply(), strategy.supplyTransform(), false, difficulty))
                    continueProduction(strategy, difficulty, personality, true);
            }
            case TRAIN_WORKER -> {
                if (!trainAt(self.building(strategy.capitol()), strategy.worker(), "worker", difficulty))
                    trainArmy(strategy, difficulty, personality, false);
            }
            case BUILD_FARM -> {
                if (!buildStructure(level, player, strategy.farm(), null, false, difficulty))
                    continueProduction(strategy, difficulty, personality, true);
            }
            case BUILD_MILITARY -> {
                if (!buildStructure(level, player, strategy.military(), strategy.militaryTransform(), true,
                        difficulty))
                    continueProduction(strategy, difficulty, personality, true);
            }
            case TRAIN_ARMY -> trainArmy(strategy, difficulty, personality, false);
        }
    }

    private void continueProduction(BotStrategy strategy, BotDifficulty difficulty, BotPersonality personality,
                                    boolean meleeOnly) {
        int workersAndQueued = self.workers().size() + self.countQueued(strategy.worker());
        if (BotDecisionMaker.shouldTrainWorker(difficulty, personality,
                workersAndQueued, armyAndQueuedPopulation(strategy))
                && trainAt(self.building(strategy.capitol()), strategy.worker(), "worker", difficulty))
            return;
        trainArmy(strategy, difficulty, personality, meleeOnly);
    }

    private int armyAndQueuedPopulation(BotStrategy strategy) {
        return BotSelf.population(self.army())
                + self.queuedPopulation(strategy.melee())
                + self.queuedPopulation(strategy.ranged());
    }

    private boolean buildStructure(ServerLevel level, RTSPlayer player, Building building, ProductionItem transform,
                                   boolean militaryRole, BotDifficulty difficulty) {
        if (!self.canAfford(building))
            return false;

        LivingEntity builder = self.workers().stream()
                .filter(this::isAvailableBuilder)
                .findFirst()
                .orElse(null);
        if (builder == null)
            return false;

        var origin = BotBuildingPlanner.findPlacement(
                level, building, player.aiHomePos, false, worldView);
        if (origin.isEmpty())
            return false;

        BuildingPlacement placement = BuildingServerEvents.placeBuilding(
                building,
                origin.get(),
                Rotation.NONE,
                ownerName,
                new int[]{builder.getId()},
                false,
                false
        );
        if (placement == null || !BuildingServerEvents.getBuildings().contains(placement))
            return false;

        if (placement instanceof PortalPlacement portal && transform != null) {
            self.recordPortal(portal, militaryRole);
            startProduction(portal, transform, militaryRole ? "military portal" : "civilian portal", difficulty);
        }

        ReignOfNether.LOGGER.info(
                "[Bot] {} placed {} at {}", displayName, building.name, placement.originPos);
        return true;
    }

    private void trainArmy(BotStrategy strategy, BotDifficulty difficulty, BotPersonality personality,
                           boolean meleeOnly) {
        ProductionPlacement building = self.militaryBuilding(strategy);
        if (building == null || !building.isBuilt)
            return;

        List<LivingEntity> army = self.army();
        List<LivingEntity> ranged = army.stream()
                .filter(RangedAttackerUnit.class::isInstance)
                .toList();
        int currentRangedPopulation = BotSelf.population(ranged);
        int rangedPopulation = currentRangedPopulation + self.queuedPopulation(strategy.ranged());
        int meleePopulation = BotSelf.population(army) - currentRangedPopulation
                + self.queuedPopulation(strategy.melee());
        int armyAndQueuedPopulation = meleePopulation + rangedPopulation;
        int targetPopulation = BotDecisionMaker.targetArmyPopulation(difficulty, personality);
        if (armyAndQueuedPopulation >= targetPopulation
                || building.productionQueue.size() >= difficulty.maxProductionQueue())
            return;

        Resources resources = self.resources();
        ResourceCost workerCost = strategy.worker().getCost(false, ownerName);
        ResourceCost meleeCost = strategy.melee().getCost(false, ownerName);
        ResourceCost rangedCost = strategy.ranged().getCost(false, ownerName);
        boolean canAffordMelee = strategy.melee().canAfford(building)
                && BotDecisionMaker.canSpendAndPreserveReserve(resources, meleeCost, workerCost);
        boolean canAffordRanged = !meleeOnly && strategy.ranged().canAfford(building)
                && BotDecisionMaker.canSpendAndPreserveReserve(resources, rangedCost, workerCost);
        int meleePopulationCost = Math.max(1, meleeCost.population);
        int rangedPopulationCost = Math.max(1, rangedCost.population);
        BotDecisionMaker.ArmyUnitChoice choice = BotDecisionMaker.chooseArmyUnit(
                personality,
                meleePopulation,
                rangedPopulation,
                canAffordMelee
                        && BotDecisionMaker.fitsArmyPopulation(
                        armyAndQueuedPopulation, meleePopulationCost, targetPopulation),
                canAffordRanged
                        && BotDecisionMaker.fitsArmyPopulation(
                        armyAndQueuedPopulation, rangedPopulationCost, targetPopulation)
        );
        ProductionItem item = switch (choice) {
            case MELEE -> strategy.melee();
            case RANGED -> strategy.ranged();
            case NONE -> null;
        };
        if (item != null)
            startProduction(building, item, "army", difficulty);
    }

    private boolean trainAt(BuildingPlacement placement, ProductionItem item, String purpose,
                            BotDifficulty difficulty) {
        if (!(placement instanceof ProductionPlacement production) || !production.isBuilt)
            return false;
        return startProduction(production, item, purpose, difficulty);
    }

    private boolean startProduction(ProductionPlacement building, ProductionItem item, String purpose,
                                    BotDifficulty difficulty) {
        if (building.productionQueue.size() >= difficulty.maxProductionQueue() || !item.canAfford(building))
            return false;
        if (!building.startProductionItem(item))
            return false;

        BuildingClientboundPacket.startProduction(building.originPos, item);
        ReignOfNether.LOGGER.info("[Bot] {} queued {} for {}", displayName, item.getItemName(), purpose);
        return true;
    }

    private void maintainPortalTransforms(BotStrategy strategy, BotDifficulty difficulty) {
        ProductionPlacement military = self.militaryBuilding(strategy);
        if (military instanceof PortalPlacement portal
                && portal.getPortalType() == PortalPlacement.PortalType.BASIC
                && portal.productionQueue.isEmpty())
            startProduction(portal, strategy.militaryTransform(), "military portal", difficulty);

        ProductionPlacement supply = self.supplyBuilding(strategy);
        if (supply instanceof PortalPlacement portal
                && portal.getPortalType() == PortalPlacement.PortalType.BASIC
                && portal.productionQueue.isEmpty())
            startProduction(portal, strategy.supplyTransform(), "civilian portal", difficulty);
    }

    private void maintainWorkerSafety(ServerLevel level, RTSPlayer player, BotStrategy strategy, int tick) {
        List<LivingEntity> workers = self.workers();
        Set<Integer> livingWorkerIds = new HashSet<>();
        for (LivingEntity worker : workers)
            livingWorkerIds.add(worker.getId());
        fleeingWorkerUntilTicks.keySet().retainAll(livingWorkerIds);

        List<LivingEntity> combatants = visibleMilitaryEnemies(player);
        BuildingPlacement capitol = self.building(strategy.capitol());
        int newlyFleeing = 0;
        int resumedWorkers = 0;
        boolean stateChanged = false;

        for (LivingEntity worker : workers) {
            Integer fleeUntil = fleeingWorkerUntilTicks.get(worker.getId());
            boolean fleeing = fleeUntil != null && tick < fleeUntil;
            List<LivingEntity> nearbyThreats = combatants.stream()
                    .filter(enemy -> BotDecisionMaker.shouldFleeWorker(fleeing,
                            worker.blockPosition().distSqr(enemy.blockPosition())))
                    .toList();

            if (nearbyThreats.isEmpty()) {
                if (fleeUntil != null && tick >= fleeUntil) {
                    fleeingWorkerUntilTicks.remove(worker.getId());
                    Unit unit = (Unit) worker;
                    if (Resources.getTotalResourcesFromItems(unit.getItems()).getTotalValue() > 0) {
                        Unit.fullResetBehaviours(unit);
                        unit.getReturnResourcesGoal().returnToClosestBuilding();
                    }
                    resumedWorkers++;
                    stateChanged = true;
                }
                continue;
            }

            fleeingWorkerUntilTicks.put(worker.getId(), tick + WORKER_FLEE_MEMORY_TICKS);
            BlockPos retreatTarget = workerRetreatTarget(level, player, capitol, worker, nearbyThreats);
            Unit unit = (Unit) worker;
            BlockPos currentTarget = unit.getMoveGoal().getMoveTarget();
            boolean needsNewTarget = currentTarget == null
                    ? worker.blockPosition().distSqr(retreatTarget) > WORKER_FLEE_ARRIVAL_DISTANCE_SQR
                    : currentTarget.distSqr(retreatTarget) > WORKER_FLEE_TARGET_CHANGE_DISTANCE_SQR;
            if (!fleeing || needsNewTarget) {
                Unit.fullResetBehaviours(unit);
                unit.setMoveTarget(retreatTarget);
            }
            if (!fleeing) {
                newlyFleeing++;
                stateChanged = true;
            }
        }

        if (stateChanged)
            nextWorkerReconcileTick = 0;
        if (newlyFleeing > 0)
            ReignOfNether.LOGGER.info("[Bot] {} evacuating {} worker(s)", displayName, newlyFleeing);
        if (resumedWorkers > 0)
            ReignOfNether.LOGGER.info("[Bot] {} resuming {} worker(s)", displayName, resumedWorkers);
    }

    private List<LivingEntity> visibleMilitaryEnemies(RTSPlayer player) {
        return worldView.visibleEnemyCombatants(player).stream()
                .filter(entity -> !(entity instanceof WorkerUnit))
                .toList();
    }

    private static BlockPos workerRetreatTarget(ServerLevel level, RTSPlayer player, BuildingPlacement capitol,
                                                LivingEntity worker, List<LivingEntity> threats) {
        double threatX = 0;
        double threatZ = 0;
        for (LivingEntity threat : threats) {
            threatX += threat.getX();
            threatZ += threat.getZ();
        }
        threatX /= threats.size();
        threatZ /= threats.size();

        BlockPos anchor = capitol == null ? player.aiHomePos : capitol.centrePos;
        double awayX = anchor.getX() - threatX;
        double awayZ = anchor.getZ() - threatZ;
        double length = Math.hypot(awayX, awayZ);
        if (length < 0.001) {
            awayX = worker.getX() - threatX;
            awayZ = worker.getZ() - threatZ;
            length = Math.hypot(awayX, awayZ);
        }
        if (length < 0.001) {
            double angle = Math.floorMod(worker.getId(), 8) * Math.PI / 4;
            awayX = Math.cos(angle);
            awayZ = Math.sin(angle);
            length = 1;
        }

        BlockPos rear = BlockPos.containing(
                anchor.getX() + awayX / length * 32,
                anchor.getY(),
                anchor.getZ() + awayZ / length * 32
        );
        if (capitol != null) {
            BlockPos perimeter = capitol.getClosestGroundPos(rear, 3);
            double towardThreatX = threatX - worker.getX();
            double towardThreatZ = threatZ - worker.getZ();
            double towardPerimeterX = perimeter.getX() - worker.getX();
            double towardPerimeterZ = perimeter.getZ() - worker.getZ();
            if (towardThreatX * towardPerimeterX + towardThreatZ * towardPerimeterZ <= 0)
                return perimeter;
        }

        awayX = worker.getX() - threatX;
        awayZ = worker.getZ() - threatZ;
        length = Math.hypot(awayX, awayZ);
        if (length < 0.001) {
            double angle = Math.floorMod(worker.getId(), 8) * Math.PI / 4;
            awayX = Math.cos(angle);
            awayZ = Math.sin(angle);
            length = 1;
        }
        rear = BlockPos.containing(
                worker.getX() + awayX / length * 24,
                worker.getY(),
                worker.getZ() + awayZ / length * 24
        );
        return BotBuildingPlanner.groundAt(level, rear.getX(), rear.getZ()).above();
    }

    private boolean isFleeingWorker(LivingEntity worker) {
        return fleeingWorkerUntilTicks.containsKey(worker.getId());
    }

    private void assignWorkerJobs(BotStrategy strategy, BotDifficulty difficulty,
                                  BotPersonality personality, boolean productionPriority,
                                  List<LivingEntity> workers,
                                  List<LivingEntity> visibleEnemies) {
        BuildingPlacement farm = self.building(strategy.farm());
        if (farm != null && (!farm.isBuilt || !hasHarvestableFood(farm)))
            farm = null;

        reassignOrphanedBuilders(workers, visibleEnemies);
        int foodWorkers = BotDecisionMaker.foodWorkerCount(
                difficulty, personality, workers.size(), productionPriority);
        int workerIndex = 0;
        for (LivingEntity entity : workers) {
            WorkerUnit worker = (WorkerUnit) entity;
            Unit unit = (Unit) entity;
            ResourceName resource = workerIndex++ < foodWorkers ? ResourceName.FOOD : ResourceName.WOOD;
            if (worker.getBuildRepairGoal().getBuildingTarget() != null
                    || !worker.getBuildRepairGoal().queuedBuildings.isEmpty()
                    || unit.getReturnResourcesGoal().getBuildingTarget() != null)
                continue;

            BuildingPlacement targetFarm = resource == ResourceName.FOOD ? farm : null;
            GatherResourcesGoal gather = worker.getGatherResourceGoal();
            if (gather.getTargetResourceName() != resource || gather.getTargetFarm() != targetFarm) {
                Unit.fullResetBehaviours(unit);
                gather.setTargetResourceName(resource);
                gather.setTargetFarm(targetFarm);
            }
        }
    }

    private boolean maintainRepairs(List<LivingEntity> workers, List<LivingEntity> visibleEnemies,
                                    boolean constructionPending) {
        Resources resources = self.resources();
        if (resources == null)
            return false;

        boolean wasRepairAssigned = repairAssigned;
        boolean hasQueuedBuildingOrder = workers.stream()
                .map(entity -> (WorkerUnit) entity)
                .anyMatch(worker -> !worker.getBuildRepairGoal().queuedBuildings.isEmpty());
        if (hasQueuedBuildingOrder) {
            boolean stoppedRepair = wasRepairAssigned;
            for (LivingEntity entity : workers) {
                WorkerUnit worker = (WorkerUnit) entity;
                BuildingPlacement target = worker.getBuildRepairGoal().getBuildingTarget();
                if (target != null && target.isBuilt
                        && worker.getBuildRepairGoal().queuedBuildings.isEmpty()) {
                    worker.getBuildRepairGoal().stopBuilding();
                    stoppedRepair = true;
                }
            }
            repairAssigned = false;
            return stoppedRepair;
        }

        List<LivingEntity> repairers = workers.stream()
                .filter(entity -> {
                    BuildingPlacement target = ((WorkerUnit) entity).getBuildRepairGoal().getBuildingTarget();
                    return target != null && target.isBuilt;
                })
                .toList();
        boolean stoppedRepair = false;
        for (int i = 1; i < repairers.size(); i++) {
            ((WorkerUnit) repairers.get(i)).getBuildRepairGoal().stopBuilding();
            stoppedRepair = true;
        }
        stoppedRepair |= wasRepairAssigned && repairers.isEmpty();
        repairAssigned = !repairers.isEmpty();
        LivingEntity currentRepairer = repairers.isEmpty() ? null : repairers.get(0);
        boolean hasWoodSurplus = BotDecisionMaker.shouldRepairBuilding(
                false, resources.wood, repairWoodReserve);

        if (currentRepairer != null && (constructionPending || !hasWoodSurplus)) {
            ((WorkerUnit) currentRepairer).getBuildRepairGoal().stopBuilding();
            currentRepairer = null;
            repairAssigned = false;
            stoppedRepair = true;
        }
        if (constructionPending || !hasWoodSurplus)
            return stoppedRepair;

        List<BuildingPlacement> repairCandidates = BuildingServerEvents.getBuildings().stream()
                .filter(this::isDamagedRepairableBuilding)
                .sorted(Comparator
                        .comparingInt((BuildingPlacement building) -> BotDecisionMaker.repairTargetPriority(
                                building.isCapitol, building instanceof ProductionPlacement))
                        .thenComparingDouble(building -> (double) building.getBlocksPlaced()
                                / Math.max(1, building.getBlocksTotal()))
                        .thenComparingInt(building -> building.originPos.getX())
                        .thenComparingInt(building -> building.originPos.getY())
                        .thenComparingInt(building -> building.originPos.getZ()))
                .toList();
        if (currentRepairer == null && repairCandidates.isEmpty())
            return stoppedRepair;

        if (currentRepairer != null) {
            BuildingPlacement target = ((WorkerUnit) currentRepairer)
                    .getBuildRepairGoal().getBuildingTarget();
            if (repairCandidates.contains(target) && !hasVisibleThreatNear(target, visibleEnemies))
                return stoppedRepair;
            ((WorkerUnit) currentRepairer).getBuildRepairGoal().stopBuilding();
            repairAssigned = false;
            stoppedRepair = true;
        }

        BuildingPlacement target = repairCandidates.stream()
                .filter(building -> !hasVisibleThreatNear(building, visibleEnemies))
                .findFirst()
                .orElse(null);
        if (target == null)
            return stoppedRepair;

        LivingEntity repairer = workers.stream()
                .filter(this::isAvailableBuilder)
                .min(Comparator.comparingDouble(entity -> entity.blockPosition().distSqr(target.centrePos)))
                .orElse(null);
        if (repairer == null)
            return stoppedRepair;

        Unit.fullResetBehaviours((Unit) repairer);
        ((WorkerUnit) repairer).getBuildRepairGoal().setBuildingTarget(target);
        repairAssigned = true;
        ReignOfNether.LOGGER.info("[Bot] {} assigned one worker to repair {} at {}",
                displayName, target.getBuilding().name, target.originPos);
        return stoppedRepair;
    }

    private boolean isDamagedRepairableBuilding(BuildingPlacement building) {
        return building != null
                && building.ownerName.equals(ownerName)
                && building.isBuilt
                && building.getBuilding().repairable
                && !building.hasPendingBlockPlacements()
                && building.getBlocksPlaced() < building.getBlocksTotal();
    }

    private static boolean hasVisibleThreatNear(BuildingPlacement building, List<LivingEntity> visibleEnemies) {
        return visibleEnemies.stream().anyMatch(enemy ->
                enemy.blockPosition().distSqr(building.centrePos) <= REPAIR_THREAT_DISTANCE_SQR);
    }

    private boolean stopRepairsAtWoodReserve() {
        Resources resources = self.resources();
        if (resources != null && resources.wood > repairWoodReserve)
            return false;

        boolean stopped = false;
        for (LivingEntity entity : self.workers()) {
            WorkerUnit worker = (WorkerUnit) entity;
            BuildingPlacement target = worker.getBuildRepairGoal().getBuildingTarget();
            if (target != null && target.isBuilt) {
                worker.getBuildRepairGoal().stopBuilding();
                stopped = true;
            }
        }
        if (stopped)
            repairAssigned = false;
        return stopped;
    }

    private static boolean isConstructionGoal(BotGoal goal) {
        return goal == BotGoal.BUILD_CAPITOL
                || goal == BotGoal.BUILD_SUPPLY
                || goal == BotGoal.BUILD_FARM
                || goal == BotGoal.BUILD_MILITARY;
    }

    private boolean hasOrphanedConstruction(List<LivingEntity> workers, List<LivingEntity> visibleEnemies) {
        return BuildingServerEvents.getBuildings().stream()
                .filter(building -> building.ownerName.equals(ownerName) && !building.isBuilt)
                .filter(building -> !hasVisibleThreatNear(building, visibleEnemies))
                .anyMatch(building -> workers.stream()
                        .map(entity -> (WorkerUnit) entity)
                        .noneMatch(worker -> isAssignedTo(worker, building)));
    }

    private int repairWoodReserve(BotStrategy strategy) {
        int supplyTransformWood = strategy.supplyTransform() == null
                ? 0 : strategy.supplyTransform().getCost(false, ownerName).wood;
        int militaryTransformWood = strategy.militaryTransform() == null
                ? 0 : strategy.militaryTransform().getCost(false, ownerName).wood;
        return BotDecisionMaker.repairWoodReserve(
                strategy.supply().cost.wood,
                supplyTransformWood,
                strategy.farm().cost.wood,
                strategy.military().cost.wood,
                militaryTransformWood
        );
    }

    private boolean isAvailableBuilder(LivingEntity entity) {
        WorkerUnit worker = (WorkerUnit) entity;
        return !isFleeingWorker(entity)
                && worker.getBuildRepairGoal().getBuildingTarget() == null
                && worker.getBuildRepairGoal().queuedBuildings.isEmpty()
                && ((Unit) entity).getReturnResourcesGoal().getBuildingTarget() == null;
    }

    private static boolean isAssignedTo(WorkerUnit worker, BuildingPlacement building) {
        return worker.getBuildRepairGoal().getBuildingTarget() == building
                || worker.getBuildRepairGoal().queuedBuildings.contains(building);
    }

    private static boolean hasHarvestableFood(BuildingPlacement farm) {
        for (var block : farm.getBlocks()) {
            var state = farm.getLevel().getBlockState(block.getBlockPos());
            ResourceSource source = ResourceSources.getFromBlockPos(block.getBlockPos(), farm.getLevel());
            if (source != null && source.resourceName == ResourceName.FOOD
                    && source.resourceValue > 0 && source.blockStateTest.test(state))
                return true;
        }
        return false;
    }

    private void reassignOrphanedBuilders(List<LivingEntity> workers, List<LivingEntity> visibleEnemies) {
        for (BuildingPlacement building : BuildingServerEvents.getBuildings()) {
            if (!building.ownerName.equals(ownerName) || building.isBuilt
                    || hasVisibleThreatNear(building, visibleEnemies))
                continue;
            boolean hasAssignedBuilder = workers.stream()
                    .map(entity -> (WorkerUnit) entity)
                    .anyMatch(worker -> isAssignedTo(worker, building));
            if (hasAssignedBuilder)
                continue;

            LivingEntity replacement = workers.stream()
                    .filter(this::isAvailableBuilder)
                    .findFirst()
                    .orElse(null);
            if (replacement == null)
                return;

            Unit.fullResetBehaviours((Unit) replacement);
            ((WorkerUnit) replacement).getBuildRepairGoal().setBuildingTarget(building);
            ReignOfNether.LOGGER.info("[Bot] {} reassigned builder to {} at {}",
                    displayName, building.getBuilding().name, building.originPos);
        }
    }

}
