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
import com.solegendary.reignofnether.resources.ResourceName;
import com.solegendary.reignofnether.resources.ResourceSource;
import com.solegendary.reignofnether.resources.ResourceSources;
import com.solegendary.reignofnether.resources.Resources;
import com.solegendary.reignofnether.unit.UnitServerEvents;
import com.solegendary.reignofnether.unit.goals.GatherResourcesGoal;
import com.solegendary.reignofnether.unit.interfaces.RangedAttackerUnit;
import com.solegendary.reignofnether.unit.interfaces.Unit;
import com.solegendary.reignofnether.unit.interfaces.WorkerUnit;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Rotation;

import java.util.Comparator;
import java.util.List;

public final class BotController {
    private static final int REPAIR_THREAT_DISTANCE_SQR = 64 * 64;

    private final String ownerName;
    private final String displayName;
    private final BotSelf self;
    private final BotWorldView worldView;
    private final BotArmy army;
    private BotGoal currentGoal = BotGoal.WAIT_FOR_CAPITOL;
    private int nextDecisionTick;
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

    public void tick(ServerLevel level) {
        RTSPlayer player = PlayerServerEvents.getRTSPlayer(ownerName);
        if (player == null || !player.aiControlled || player.aiHomePos == null)
            return;

        int tick = level.getServer().getTickCount();
        BotDifficulty difficulty = player.aiDifficulty;
        BotPersonality personality = player.aiPersonality;
        if (repairAssigned && stopRepairsAtWoodReserve())
            nextWorkerReconcileTick = 0;
        if (tick < nextDecisionTick)
            return;
        nextDecisionTick = tick + difficulty.decisionIntervalTicks();
        worldView.observe(player);

        BotStrategy strategy = BotStrategy.forFaction(player.faction);
        self.reconcilePortalRoles(strategy);
        if (strategy.usesTransformingPortals())
            maintainPortalTransforms(strategy, difficulty);

        BotDecisionContext context = createDecisionContext(strategy);
        BotGoal nextGoal = BotDecisionMaker.chooseGoal(difficulty, personality, context);
        repairWoodReserve = repairWoodReserve(strategy);
        List<LivingEntity> workers = self.workers();
        boolean orphanedConstruction = hasOrphanedConstruction(workers);
        if (orphanedConstruction)
            nextWorkerReconcileTick = 0;
        if (maintainRepairs(player, workers,
                isConstructionGoal(nextGoal) || orphanedConstruction))
            nextWorkerReconcileTick = 0;
        if (tick >= nextWorkerReconcileTick) {
            boolean economyComplete = context.workersAndQueued()
                    >= BotDecisionMaker.targetWorkers(difficulty, personality);
            assignWorkerJobs(strategy, difficulty, economyComplete, workers);
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
        if (workersAndQueued < BotDecisionMaker.targetWorkers(difficulty, personality)
                && trainAt(self.building(strategy.capitol()), strategy.worker(), "worker", difficulty))
            return;
        trainArmy(strategy, difficulty, personality, meleeOnly);
    }

    private boolean buildStructure(ServerLevel level, RTSPlayer player, Building building, ProductionItem transform,
                                   boolean militaryRole, BotDifficulty difficulty) {
        if (!self.canAfford(building))
            return false;

        LivingEntity builder = self.workers().stream()
                .filter(BotController::isAvailableBuilder)
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

        int meleeCost = Math.max(1, strategy.melee().getCost(false, ownerName).population);
        int rangedCost = Math.max(1, strategy.ranged().getCost(false, ownerName).population);
        BotDecisionMaker.ArmyUnitChoice choice = BotDecisionMaker.chooseArmyUnit(
                personality,
                meleePopulation,
                rangedPopulation,
                strategy.melee().canAfford(building)
                        && BotDecisionMaker.fitsArmyPopulation(
                        armyAndQueuedPopulation, meleeCost, targetPopulation),
                !meleeOnly && strategy.ranged().canAfford(building)
                        && BotDecisionMaker.fitsArmyPopulation(
                        armyAndQueuedPopulation, rangedCost, targetPopulation)
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

    private void assignWorkerJobs(BotStrategy strategy, BotDifficulty difficulty,
                                  boolean economyComplete, List<LivingEntity> workers) {
        BuildingPlacement farm = self.building(strategy.farm());
        if (farm != null && (!farm.isBuilt || !hasHarvestableFood(farm)))
            farm = null;

        reassignOrphanedBuilders(workers);
        int foodWorkers = BotDecisionMaker.foodWorkerCount(difficulty, workers.size(), economyComplete);
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

    private boolean maintainRepairs(RTSPlayer player, List<LivingEntity> workers,
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

        List<LivingEntity> visibleEnemies = worldView.visibleEnemyCombatants(player);
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
                .filter(BotController::isAvailableBuilder)
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
        return goal == BotGoal.BUILD_SUPPLY
                || goal == BotGoal.BUILD_FARM
                || goal == BotGoal.BUILD_MILITARY;
    }

    private boolean hasOrphanedConstruction(List<LivingEntity> workers) {
        return BuildingServerEvents.getBuildings().stream()
                .filter(building -> building.ownerName.equals(ownerName) && !building.isBuilt)
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

    private static boolean isAvailableBuilder(LivingEntity entity) {
        WorkerUnit worker = (WorkerUnit) entity;
        return worker.getBuildRepairGoal().getBuildingTarget() == null
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

    private void reassignOrphanedBuilders(List<LivingEntity> workers) {
        for (BuildingPlacement building : BuildingServerEvents.getBuildings()) {
            if (!building.ownerName.equals(ownerName) || building.isBuilt)
                continue;
            boolean hasAssignedBuilder = workers.stream()
                    .map(entity -> (WorkerUnit) entity)
                    .anyMatch(worker -> isAssignedTo(worker, building));
            if (hasAssignedBuilder)
                continue;

            LivingEntity replacement = workers.stream()
                    .filter(BotController::isAvailableBuilder)
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
