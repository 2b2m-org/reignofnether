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
import com.solegendary.reignofnether.research.ResearchServerEvents;
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

import java.util.List;

public final class BotController {
    private final String ownerName;
    private final BotSelf self;
    private final BotWorldView worldView;
    private final BotArmy army;
    private BotGoal currentGoal = BotGoal.WAIT_FOR_CAPITOL;
    private int nextDecisionTick;
    private int nextWorkerReconcileTick;

    public BotController(String ownerName) {
        this.ownerName = ownerName;
        self = new BotSelf(ownerName);
        worldView = new BotWorldView();
        army = new BotArmy(ownerName, self, worldView);
    }

    public String getOwnerName() {
        return ownerName;
    }

    public BotGoal getCurrentGoal() {
        return currentGoal;
    }

    public void tick(ServerLevel level) {
        RTSPlayer player = PlayerServerEvents.getRTSPlayer(ownerName);
        if (player == null || !player.aiControlled || player.aiHomePos == null)
            return;

        int tick = level.getServer().getTickCount();
        BotDifficulty difficulty = player.aiDifficulty;
        BotPersonality personality = player.aiPersonality;
        if (tick < nextDecisionTick)
            return;
        nextDecisionTick = tick + difficulty.decisionIntervalTicks();
        worldView.observe(player);

        BotStrategy strategy = BotStrategy.forFaction(player.faction);
        self.reconcilePortalRoles(strategy);
        if (strategy.usesTransformingPortals())
            maintainPortalTransforms(strategy, difficulty);

        BotDecisionContext context = createDecisionContext(strategy);
        if (tick >= nextWorkerReconcileTick) {
            boolean economyComplete = context.workersAndQueued()
                    >= BotDecisionMaker.targetWorkers(difficulty, personality);
            assignWorkerJobs(strategy, difficulty, economyComplete);
            nextWorkerReconcileTick = tick + difficulty.workerReconcileTicks();
        }

        BotGoal nextGoal = BotDecisionMaker.chooseGoal(difficulty, personality, context);
        if (nextGoal != currentGoal) {
            currentGoal = nextGoal;
            ReignOfNether.LOGGER.info("[Bot] {} goal={}", ownerName, currentGoal);
        }
        executeGoal(level, player, strategy, difficulty, personality, currentGoal);
        army.tick(level, player, strategy, difficulty, personality);
    }

    public String describe() {
        RTSPlayer player = PlayerServerEvents.getRTSPlayer(ownerName);
        if (player == null)
            return ownerName + " (inactive)";

        int workers = self.workers().size();
        int army = self.army().size();
        int population = UnitServerEvents.getCurrentPopulation(ownerName);
        int supply = BuildingServerEvents.getTotalPopulationSupply(ownerName);
        Resources resources = self.resources();
        String resourceText = resources == null
                ? "resources unavailable"
                : "food=" + resources.food + " wood=" + resources.wood + " ore=" + resources.ore;
        return ownerName + " faction=" + player.faction.name().toLowerCase()
                + " difficulty=" + player.aiDifficulty.name().toLowerCase()
                + " personality=" + player.aiPersonality.name().toLowerCase()
                + " goal=" + currentGoal.name().toLowerCase()
                + " workers=" + workers + " army=" + army
                + " population=" + population + "/" + supply + " " + resourceText
                + (hasTestSpeedCheats() ? " [TEST SPEED CHEAT]" : "");
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
                .filter(entity -> {
                    WorkerUnit worker = (WorkerUnit) entity;
                    return worker.getBuildRepairGoal().getBuildingTarget() == null
                            && ((Unit) entity).getReturnResourcesGoal().getBuildingTarget() == null;
                })
                .findFirst()
                .orElse(null);
        if (builder == null)
            return false;

        var origin = BotBuildingPlanner.findPlacement(
                level, building, player.aiHomePos, ownerName, false, worldView);
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

        ReignOfNether.LOGGER.info("[Bot] {} placed {} at {}", ownerName, building.name, placement.originPos);
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
        ReignOfNether.LOGGER.info("[Bot] {} queued {} for {}", ownerName, item.getItemName(), purpose);
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

    private void assignWorkerJobs(BotStrategy strategy, BotDifficulty difficulty, boolean economyComplete) {
        BuildingPlacement farm = self.building(strategy.farm());
        if (farm != null && (!farm.isBuilt || !hasHarvestableFood(farm)))
            farm = null;

        List<LivingEntity> workers = self.workers();
        reassignOrphanedBuilders(workers);
        int foodWorkers = BotDecisionMaker.foodWorkerCount(difficulty, workers.size(), economyComplete);
        int workerIndex = 0;
        for (LivingEntity entity : workers) {
            WorkerUnit worker = (WorkerUnit) entity;
            Unit unit = (Unit) entity;
            ResourceName resource = workerIndex++ < foodWorkers ? ResourceName.FOOD : ResourceName.WOOD;
            if (worker.getBuildRepairGoal().getBuildingTarget() != null
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
                    .anyMatch(worker -> worker.getBuildRepairGoal().getBuildingTarget() == building);
            if (hasAssignedBuilder)
                continue;

            LivingEntity replacement = workers.stream()
                    .filter(entity -> {
                        WorkerUnit worker = (WorkerUnit) entity;
                        return worker.getBuildRepairGoal().getBuildingTarget() == null
                                && ((Unit) entity).getReturnResourcesGoal().getBuildingTarget() == null;
                    })
                    .findFirst()
                    .orElse(null);
            if (replacement == null)
                return;

            Unit.fullResetBehaviours((Unit) replacement);
            ((WorkerUnit) replacement).getBuildRepairGoal().setBuildingTarget(building);
            ReignOfNether.LOGGER.info("[Bot] {} reassigned builder to {} at {}",
                    ownerName, building.getBuilding().name, building.originPos);
        }
    }

    private boolean hasTestSpeedCheats() {
        return ResearchServerEvents.playerHasCheat(ownerName, "warpten")
                || ResearchServerEvents.playerHasCheat(ownerName, "operationcwal");
    }
}
