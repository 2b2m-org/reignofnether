package com.solegendary.reignofnether.bot;

import com.solegendary.reignofnether.ReignOfNether;
import com.solegendary.reignofnether.alliance.AlliancesServerEvents;
import com.solegendary.reignofnether.building.Building;
import com.solegendary.reignofnether.building.BuildingClientboundPacket;
import com.solegendary.reignofnether.building.BuildingPlacement;
import com.solegendary.reignofnether.building.BuildingServerEvents;
import com.solegendary.reignofnether.building.buildings.placements.PortalPlacement;
import com.solegendary.reignofnether.building.buildings.placements.ProductionPlacement;
import com.solegendary.reignofnether.building.production.ActiveProduction;
import com.solegendary.reignofnether.building.production.ProductionItem;
import com.solegendary.reignofnether.faction.Faction;
import com.solegendary.reignofnether.player.PlayerServerEvents;
import com.solegendary.reignofnether.player.RTSPlayer;
import com.solegendary.reignofnether.resources.ResourceName;
import com.solegendary.reignofnether.resources.Resources;
import com.solegendary.reignofnether.resources.ResourcesServerEvents;
import com.solegendary.reignofnether.unit.UnitAction;
import com.solegendary.reignofnether.unit.UnitServerEvents;
import com.solegendary.reignofnether.unit.goals.GatherResourcesGoal;
import com.solegendary.reignofnether.unit.interfaces.AttackerUnit;
import com.solegendary.reignofnether.unit.interfaces.Unit;
import com.solegendary.reignofnether.unit.interfaces.WorkerUnit;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Rotation;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class BotController {
    public static final int ATTACK_THRESHOLD = 5;
    public static final int TARGET_ARMY_SIZE = 12;

    private static final int WORKER_RECONCILE_TICKS = 100;
    private static final int ATTACK_REFRESH_TICKS = 200;
    private static final int MAX_PRODUCTION_QUEUE = 2;

    private final String ownerName;
    private BotGoal currentGoal = BotGoal.WAIT_FOR_CAPITOL;
    private BlockPos militaryPortalOrigin;
    private BlockPos supplyPortalOrigin;
    private BlockPos lastAttackTarget;
    private int nextWorkerReconcileTick;
    private int nextAttackTick;

    public BotController(String ownerName) {
        this.ownerName = ownerName;
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

        BotStrategy strategy = BotStrategy.forFaction(player.faction);
        if (strategy.usesTransformingPortals())
            maintainPortalTransforms(strategy);

        int tick = level.getServer().getTickCount();
        if (tick >= nextWorkerReconcileTick) {
            assignWorkerJobs(strategy);
            nextWorkerReconcileTick = tick + WORKER_RECONCILE_TICKS;
        }

        BotDecisionContext context = createDecisionContext(strategy);
        BotGoal nextGoal = BotDecisionMaker.chooseGoal(context);
        if (nextGoal != currentGoal) {
            currentGoal = nextGoal;
            ReignOfNether.LOGGER.info("[Bot] {} goal={}", ownerName, currentGoal);
        }
        executeGoal(level, player, strategy, currentGoal);

        if (tick >= nextAttackTick)
            commandArmy(level, player);
    }

    public String describe() {
        RTSPlayer player = PlayerServerEvents.getRTSPlayer(ownerName);
        if (player == null)
            return ownerName + " (inactive)";

        int workers = ownedWorkers().size();
        int army = ownedArmy().size();
        int population = UnitServerEvents.getCurrentPopulation(ownerName);
        int supply = BuildingServerEvents.getTotalPopulationSupply(ownerName);
        Resources resources = getResources();
        String resourceText = resources == null
                ? "resources unavailable"
                : "food=" + resources.food + " wood=" + resources.wood + " ore=" + resources.ore;
        return ownerName + " faction=" + player.faction.name().toLowerCase()
                + " goal=" + currentGoal.name().toLowerCase()
                + " workers=" + workers + " army=" + army
                + " population=" + population + "/" + supply + " " + resourceText;
    }

    private BotDecisionContext createDecisionContext(BotStrategy strategy) {
        BuildingPlacement capitol = ownedBuilding(strategy.capitol());
        ProductionPlacement military = militaryBuilding(strategy);
        int queuedWorkers = countQueued(strategy.worker());

        return new BotDecisionContext(
                capitol != null,
                capitol != null && capitol.isBuilt,
                ownedWorkers().size() + queuedWorkers,
                UnitServerEvents.getCurrentPopulation(ownerName),
                BuildingServerEvents.getTotalPopulationSupply(ownerName),
                supplyUnderConstruction(strategy),
                ownedBuilding(strategy.farm()) != null,
                military != null,
                military != null && military.isBuilt && (!strategy.usesTransformingPortals()
                        || military instanceof PortalPlacement portal
                        && portal.getPortalType() == PortalPlacement.PortalType.MILITARY)
        );
    }

    private void executeGoal(ServerLevel level, RTSPlayer player, BotStrategy strategy, BotGoal goal) {
        switch (goal) {
            case WAIT_FOR_CAPITOL, WAIT_FOR_MILITARY -> {
            }
            case BUILD_SUPPLY -> buildStructure(level, player, strategy.supply(), strategy.supplyTransform(), false);
            case TRAIN_WORKER -> trainAt(ownedBuilding(strategy.capitol()), strategy.worker(), "worker");
            case BUILD_FARM -> buildStructure(level, player, strategy.farm(), null, false);
            case BUILD_MILITARY -> buildStructure(level, player, strategy.military(), strategy.militaryTransform(), true);
            case TRAIN_ARMY -> trainArmy(strategy);
        }
    }

    private void buildStructure(ServerLevel level, RTSPlayer player, Building building, ProductionItem transform,
                                boolean militaryRole) {
        if (!canAfford(building))
            return;

        LivingEntity builder = ownedWorkers().stream()
                .filter(entity -> ((WorkerUnit) entity).getBuildRepairGoal().getBuildingTarget() == null)
                .findFirst()
                .orElse(null);
        if (builder == null)
            return;

        var origin = BotBuildingPlanner.findPlacement(level, building, player.aiHomePos, ownerName, false);
        if (origin.isEmpty())
            return;

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
            return;

        if (placement instanceof PortalPlacement portal && transform != null) {
            if (militaryRole)
                militaryPortalOrigin = portal.originPos;
            else
                supplyPortalOrigin = portal.originPos;
            startProduction(portal, transform, militaryRole ? "military portal" : "civilian portal");
        }

        ReignOfNether.LOGGER.info("[Bot] {} placed {} at {}", ownerName, building.name, placement.originPos);
    }

    private void trainArmy(BotStrategy strategy) {
        ProductionPlacement building = militaryBuilding(strategy);
        if (building == null || !building.isBuilt)
            return;

        int armyAndQueued = ownedArmy().size() + countQueued(strategy.melee()) + countQueued(strategy.ranged());
        if (armyAndQueued >= TARGET_ARMY_SIZE)
            return;

        ProductionItem item = armyAndQueued % 3 == 2 ? strategy.ranged() : strategy.melee();
        trainAt(building, item, "army");
    }

    private boolean trainAt(BuildingPlacement placement, ProductionItem item, String purpose) {
        if (!(placement instanceof ProductionPlacement production) || !production.isBuilt)
            return false;
        return startProduction(production, item, purpose);
    }

    private boolean startProduction(ProductionPlacement building, ProductionItem item, String purpose) {
        if (building.productionQueue.size() >= MAX_PRODUCTION_QUEUE || !item.canAfford(building))
            return false;
        if (!building.startProductionItem(item))
            return false;

        BuildingClientboundPacket.startProduction(building.originPos, item);
        ReignOfNether.LOGGER.info("[Bot] {} queued {} for {}", ownerName, item.getItemName(), purpose);
        return true;
    }

    private void maintainPortalTransforms(BotStrategy strategy) {
        ProductionPlacement military = militaryBuilding(strategy);
        if (military instanceof PortalPlacement portal
                && portal.getPortalType() == PortalPlacement.PortalType.BASIC
                && portal.productionQueue.isEmpty())
            startProduction(portal, strategy.militaryTransform(), "military portal");

        ProductionPlacement supply = supplyBuilding(strategy);
        if (supply instanceof PortalPlacement portal
                && portal.getPortalType() == PortalPlacement.PortalType.BASIC
                && portal.productionQueue.isEmpty())
            startProduction(portal, strategy.supplyTransform(), "civilian portal");
    }

    private void assignWorkerJobs(BotStrategy strategy) {
        BuildingPlacement farm = ownedBuilding(strategy.farm());
        if (farm != null && !farm.isBuilt)
            farm = null;

        List<LivingEntity> workers = ownedWorkers();
        workers.sort(Comparator.comparingInt(LivingEntity::getId));
        int availableIndex = 0;
        for (LivingEntity entity : workers) {
            WorkerUnit worker = (WorkerUnit) entity;
            if (worker.getBuildRepairGoal().getBuildingTarget() != null)
                continue;

            ResourceName resource = availableIndex % 5 == 1 || availableIndex % 5 == 3
                    ? ResourceName.WOOD
                    : ResourceName.FOOD;
            BuildingPlacement targetFarm = resource == ResourceName.FOOD ? farm : null;
            GatherResourcesGoal gather = worker.getGatherResourceGoal();
            if (gather.getTargetResourceName() != resource || gather.getTargetFarm() != targetFarm) {
                Unit unit = (Unit) entity;
                Unit.fullResetBehaviours(unit);
                gather.setTargetResourceName(resource);
                gather.setTargetFarm(targetFarm);
            }
            availableIndex++;
        }
    }

    private void commandArmy(ServerLevel level, RTSPlayer player) {
        List<LivingEntity> army = ownedArmy();
        if (army.size() < ATTACK_THRESHOLD) {
            nextAttackTick = level.getServer().getTickCount() + ATTACK_REFRESH_TICKS;
            return;
        }

        BuildingPlacement target = nearestEnemyBuilding(player);
        if (target == null) {
            nextAttackTick = level.getServer().getTickCount() + ATTACK_REFRESH_TICKS;
            return;
        }

        int[] ids = army.stream().mapToInt(LivingEntity::getId).toArray();
        UnitServerEvents.addActionItem(
                ownerName,
                UnitAction.ATTACK_BUILDING,
                -1,
                ids,
                target.originPos,
                BlockPos.ZERO
        );
        if (!target.originPos.equals(lastAttackTarget)) {
            ReignOfNether.LOGGER.info("[Bot] {} attacking {} at {} with {} units",
                    ownerName, target.ownerName, target.originPos, ids.length);
            lastAttackTarget = target.originPos;
        }
        nextAttackTick = level.getServer().getTickCount() + ATTACK_REFRESH_TICKS;
    }

    private BuildingPlacement nearestEnemyBuilding(RTSPlayer player) {
        return BuildingServerEvents.getBuildings().stream()
                .filter(building -> isActiveEnemy(player, building.ownerName))
                .min(Comparator.comparingDouble(building -> building.centrePos.distSqr(player.aiHomePos)))
                .orElse(null);
    }

    private boolean isActiveEnemy(RTSPlayer player, String otherName) {
        if (otherName.isBlank() || otherName.equals(ownerName) || AlliancesServerEvents.isAllied(ownerName, otherName))
            return false;
        RTSPlayer other = PlayerServerEvents.getRTSPlayer(otherName);
        return other != null && other != player;
    }

    private BuildingPlacement ownedBuilding(Building building) {
        for (BuildingPlacement placement : BuildingServerEvents.getBuildings())
            if (placement.ownerName.equals(ownerName) && placement.getBuilding() == building)
                return placement;
        return null;
    }

    private ProductionPlacement militaryBuilding(BotStrategy strategy) {
        if (!strategy.usesTransformingPortals())
            return asProduction(ownedBuilding(strategy.military()));

        PortalPlacement exact = findPortal(PortalPlacement.PortalType.MILITARY, strategy.militaryTransform());
        if (exact != null) {
            militaryPortalOrigin = exact.originPos;
            return exact;
        }
        PortalPlacement saved = portalAt(militaryPortalOrigin);
        if (saved != null)
            return saved;

        PortalPlacement firstUnclaimed = firstUnclaimedBasicPortal(supplyPortalOrigin);
        if (firstUnclaimed != null)
            militaryPortalOrigin = firstUnclaimed.originPos;
        return firstUnclaimed;
    }

    private ProductionPlacement supplyBuilding(BotStrategy strategy) {
        if (!strategy.usesTransformingPortals())
            return asProduction(ownedBuilding(strategy.supply()));

        PortalPlacement saved = portalAt(supplyPortalOrigin);
        if (saved != null)
            return saved;

        militaryBuilding(strategy);
        PortalPlacement firstUnclaimed = firstUnclaimedBasicPortal(militaryPortalOrigin);
        if (firstUnclaimed != null) {
            supplyPortalOrigin = firstUnclaimed.originPos;
            return firstUnclaimed;
        }

        PortalPlacement exact = findPortal(PortalPlacement.PortalType.CIVILIAN, strategy.supplyTransform());
        if (exact != null)
            supplyPortalOrigin = exact.originPos;
        return exact;
    }

    private PortalPlacement findPortal(PortalPlacement.PortalType type, ProductionItem queuedTransform) {
        for (BuildingPlacement placement : BuildingServerEvents.getBuildings()) {
            if (placement instanceof PortalPlacement portal
                    && placement.ownerName.equals(ownerName)
                    && queueContains(portal, queuedTransform))
                return portal;
        }
        for (BuildingPlacement placement : BuildingServerEvents.getBuildings()) {
            if (!(placement instanceof PortalPlacement portal) || !placement.ownerName.equals(ownerName))
                continue;
            if (portal.getPortalType() == type)
                return portal;
        }
        return null;
    }

    private boolean supplyUnderConstruction(BotStrategy strategy) {
        for (BuildingPlacement placement : BuildingServerEvents.getBuildings()) {
            if (!placement.ownerName.equals(ownerName))
                continue;
            if (!strategy.usesTransformingPortals() && placement.getBuilding() == strategy.supply()
                    && !placement.isBuilt)
                return true;
            if (strategy.usesTransformingPortals() && placement instanceof PortalPlacement portal
                    && (queueContains(portal, strategy.supplyTransform())
                    || placement.originPos.equals(supplyPortalOrigin))
                    && (!placement.isBuilt || portal.getPortalType() == PortalPlacement.PortalType.BASIC))
                return true;
        }
        return false;
    }

    private PortalPlacement firstUnclaimedBasicPortal(BlockPos claimedOrigin) {
        for (BuildingPlacement placement : BuildingServerEvents.getBuildings()) {
            if (placement instanceof PortalPlacement portal
                    && placement.ownerName.equals(ownerName)
                    && portal.getPortalType() == PortalPlacement.PortalType.BASIC
                    && (claimedOrigin == null || !placement.originPos.equals(claimedOrigin)))
                return portal;
        }
        return null;
    }

    private PortalPlacement portalAt(BlockPos origin) {
        if (origin == null)
            return null;
        for (BuildingPlacement placement : BuildingServerEvents.getBuildings())
            if (placement instanceof PortalPlacement portal
                    && placement.ownerName.equals(ownerName)
                    && placement.originPos.equals(origin))
                return portal;
        return null;
    }

    private static boolean queueContains(ProductionPlacement placement, ProductionItem item) {
        if (item == null)
            return false;
        for (ActiveProduction active : placement.productionQueue)
            if (active.item == item)
                return true;
        return false;
    }

    private static ProductionPlacement asProduction(BuildingPlacement placement) {
        return placement instanceof ProductionPlacement production ? production : null;
    }

    private int countQueued(ProductionItem item) {
        int count = 0;
        for (BuildingPlacement placement : BuildingServerEvents.getBuildings())
            if (placement.ownerName.equals(ownerName) && placement instanceof ProductionPlacement production)
                for (ActiveProduction active : production.productionQueue)
                    if (active.item == item)
                        count++;
        return count;
    }

    private List<LivingEntity> ownedWorkers() {
        List<LivingEntity> workers = new ArrayList<>();
        for (LivingEntity entity : UnitServerEvents.getAllUnits())
            if (entity.isAlive() && entity instanceof Unit unit && entity instanceof WorkerUnit
                    && unit.getOwnerName().equals(ownerName))
                workers.add(entity);
        return workers;
    }

    private List<LivingEntity> ownedArmy() {
        List<LivingEntity> army = new ArrayList<>();
        for (LivingEntity entity : UnitServerEvents.getAllUnits())
            if (entity.isAlive() && entity instanceof Unit unit && entity instanceof AttackerUnit
                    && !(entity instanceof WorkerUnit) && unit.getOwnerName().equals(ownerName))
                army.add(entity);
        return army;
    }

    private Resources getResources() {
        for (Resources resources : ResourcesServerEvents.resourcesList)
            if (resources.ownerName.equals(ownerName))
                return resources;
        return null;
    }

    private boolean canAfford(Building building) {
        Resources resources = getResources();
        return resources != null
                && resources.food >= building.cost.food
                && resources.wood >= building.cost.wood
                && resources.ore >= building.cost.ore;
    }
}
