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
import com.solegendary.reignofnether.research.ResearchServerEvents;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class BotController {
    private static final int TARGET_REVISIT_TICKS = 600;
    private static final double SCOUT_REACHED_DISTANCE_SQR = 144;
    private static final int[][] SCOUT_DIRECTIONS = {
            {1, 0}, {0, 1}, {-1, 0}, {0, -1},
            {1, 1}, {-1, 1}, {-1, -1}, {1, -1}
    };

    private final String ownerName;
    private final BotWorldView worldView = new BotWorldView();
    private final Map<BlockPos, Integer> targetCooldowns = new HashMap<>();
    private BotGoal currentGoal = BotGoal.WAIT_FOR_CAPITOL;
    private BlockPos militaryPortalOrigin;
    private BlockPos supplyPortalOrigin;
    private BuildingPlacement attackTarget;
    private BlockPos scoutTarget;
    private double scoutBestDistance;
    private int scoutLastProgressTick;
    private int scoutWaypointIndex;
    private boolean attackCommitted;
    private int nextDecisionTick;
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

        int tick = level.getServer().getTickCount();
        BotDifficulty difficulty = player.aiDifficulty;
        if (tick < nextDecisionTick)
            return;
        nextDecisionTick = tick + difficulty.decisionIntervalTicks();

        BotStrategy strategy = BotStrategy.forFaction(player.faction);
        if (strategy.usesTransformingPortals())
            maintainPortalTransforms(strategy, difficulty);

        BotDecisionContext context = createDecisionContext(strategy);
        if (tick >= nextWorkerReconcileTick) {
            assignWorkerJobs(strategy, difficulty, context.militaryReady());
            nextWorkerReconcileTick = tick + difficulty.workerReconcileTicks();
        }

        worldView.observe(player);
        BotGoal nextGoal = BotDecisionMaker.chooseGoal(difficulty, context);
        if (nextGoal != currentGoal) {
            currentGoal = nextGoal;
            ReignOfNether.LOGGER.info("[Bot] {} goal={}", ownerName, currentGoal);
        }
        executeGoal(level, player, strategy, difficulty, currentGoal);

        if (tick >= nextAttackTick)
            commandArmy(level, player, difficulty);
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
                + " difficulty=" + player.aiDifficulty.name().toLowerCase()
                + " goal=" + currentGoal.name().toLowerCase()
                + " workers=" + workers + " army=" + army
                + " population=" + population + "/" + supply + " " + resourceText
                + (hasTestSpeedCheats() ? " [TEST SPEED CHEAT]" : "");
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
                strategy.worker().getCost(false, ownerName).population,
                Math.max(strategy.melee().getCost(false, ownerName).population,
                        strategy.ranged().getCost(false, ownerName).population),
                ownedBuilding(strategy.farm()) != null,
                military != null,
                military != null && military.isBuilt && (!strategy.usesTransformingPortals()
                        || military instanceof PortalPlacement portal
                        && portal.getPortalType() == PortalPlacement.PortalType.MILITARY)
        );
    }

    private void executeGoal(ServerLevel level, RTSPlayer player, BotStrategy strategy, BotDifficulty difficulty,
                             BotGoal goal) {
        switch (goal) {
            case WAIT_FOR_CAPITOL, WAIT_FOR_MILITARY -> {
            }
            case BUILD_SUPPLY -> {
                if (!buildStructure(level, player, strategy.supply(), strategy.supplyTransform(), false, difficulty)) {
                    if (ownedWorkers().size() + countQueued(strategy.worker()) < difficulty.targetWorkers())
                        trainAt(ownedBuilding(strategy.capitol()), strategy.worker(), "worker", difficulty);
                    else
                        trainArmy(strategy, difficulty);
                }
            }
            case TRAIN_WORKER -> trainAt(ownedBuilding(strategy.capitol()), strategy.worker(), "worker", difficulty);
            case BUILD_FARM -> buildStructure(level, player, strategy.farm(), null, false, difficulty);
            case BUILD_MILITARY -> buildStructure(level, player, strategy.military(), strategy.militaryTransform(),
                    true, difficulty);
            case TRAIN_ARMY -> trainArmy(strategy, difficulty);
        }
    }

    private boolean buildStructure(ServerLevel level, RTSPlayer player, Building building, ProductionItem transform,
                                   boolean militaryRole, BotDifficulty difficulty) {
        if (!canAfford(building))
            return false;

        LivingEntity builder = ownedWorkers().stream()
                .filter(entity -> ((WorkerUnit) entity).getBuildRepairGoal().getBuildingTarget() == null)
                .findFirst()
                .orElse(null);
        if (builder == null)
            return false;

        var origin = BotBuildingPlanner.findPlacement(level, building, player.aiHomePos, ownerName, false);
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
            if (militaryRole)
                militaryPortalOrigin = portal.originPos;
            else
                supplyPortalOrigin = portal.originPos;
            startProduction(portal, transform, militaryRole ? "military portal" : "civilian portal", difficulty);
        }

        ReignOfNether.LOGGER.info("[Bot] {} placed {} at {}", ownerName, building.name, placement.originPos);
        return true;
    }

    private void trainArmy(BotStrategy strategy, BotDifficulty difficulty) {
        ProductionPlacement building = militaryBuilding(strategy);
        if (building == null || !building.isBuilt)
            return;

        int armyAndQueued = ownedArmy().size() + countQueued(strategy.melee()) + countQueued(strategy.ranged());
        if (armyAndQueued >= difficulty.targetArmySize()
                || building.productionQueue.size() >= difficulty.maxProductionQueue())
            return;

        BotDecisionMaker.ArmyUnitChoice choice = BotDecisionMaker.chooseArmyUnit(
                difficulty,
                armyAndQueued,
                strategy.melee().canAfford(building),
                strategy.ranged().canAfford(building)
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
        ProductionPlacement military = militaryBuilding(strategy);
        if (military instanceof PortalPlacement portal
                && portal.getPortalType() == PortalPlacement.PortalType.BASIC
                && portal.productionQueue.isEmpty())
            startProduction(portal, strategy.militaryTransform(), "military portal", difficulty);

        ProductionPlacement supply = supplyBuilding(strategy);
        if (supply instanceof PortalPlacement portal
                && portal.getPortalType() == PortalPlacement.PortalType.BASIC
                && portal.productionQueue.isEmpty())
            startProduction(portal, strategy.supplyTransform(), "civilian portal", difficulty);
    }

    private void assignWorkerJobs(BotStrategy strategy, BotDifficulty difficulty, boolean militaryReady) {
        BuildingPlacement farm = ownedBuilding(strategy.farm());
        if (farm != null && !farm.isBuilt)
            farm = null;

        List<LivingEntity> workers = ownedWorkers();
        workers.sort(Comparator.comparingInt(LivingEntity::getId));
        int foodWorkers = BotDecisionMaker.foodWorkerCount(difficulty, workers.size(), militaryReady);
        int workerIndex = 0;
        for (LivingEntity entity : workers) {
            WorkerUnit worker = (WorkerUnit) entity;
            ResourceName resource = workerIndex++ < foodWorkers ? ResourceName.FOOD : ResourceName.WOOD;
            if (worker.getBuildRepairGoal().getBuildingTarget() != null)
                continue;

            BuildingPlacement targetFarm = resource == ResourceName.FOOD ? farm : null;
            GatherResourcesGoal gather = worker.getGatherResourceGoal();
            if (gather.getTargetResourceName() != resource || gather.getTargetFarm() != targetFarm) {
                Unit unit = (Unit) entity;
                Unit.fullResetBehaviours(unit);
                gather.setTargetResourceName(resource);
                gather.setTargetFarm(targetFarm);
            }
        }
    }

    private void commandArmy(ServerLevel level, RTSPlayer player, BotDifficulty difficulty) {
        int tick = level.getServer().getTickCount();
        targetCooldowns.entrySet().removeIf(entry -> entry.getValue() <= tick);

        List<LivingEntity> army = ownedArmy();
        if (army.isEmpty()) {
            attackCommitted = false;
            attackTarget = null;
            clearScoutTarget();
            nextAttackTick = tick + difficulty.attackRefreshTicks();
            return;
        }

        if (attackCommitted && difficulty.retreatThreshold() > 0
                && army.size() < difficulty.retreatThreshold()) {
            moveArmy(army, player.aiHomePos.above());
            ReignOfNether.LOGGER.info("[Bot] {} retreating with {} units", ownerName, army.size());
            attackCommitted = false;
            attackTarget = null;
            clearScoutTarget();
            nextAttackTick = tick + difficulty.attackRefreshTicks();
            return;
        }

        if (!attackCommitted && army.size() < difficulty.attackThreshold()) {
            nextAttackTick = tick + difficulty.attackRefreshTicks();
            return;
        }

        if (attackTarget != null && !BuildingServerEvents.getBuildings().contains(attackTarget)) {
            targetCooldowns.put(attackTarget.originPos, tick + TARGET_REVISIT_TICKS);
            attackTarget = null;
        }
        if (attackTarget != null && !isActiveEnemy(player, attackTarget.ownerName))
            attackTarget = null;

        boolean selectedNewTarget = false;
        if (attackTarget == null) {
            attackTarget = selectEnemyBuilding(player, difficulty, army.get(0).blockPosition());
            selectedNewTarget = attackTarget != null;
        }
        if (attackTarget == null) {
            if (worldView.fogEnabled())
                scout(level, player, army, tick);
            nextAttackTick = tick + difficulty.attackRefreshTicks();
            return;
        }

        int[] ids = army.stream().mapToInt(LivingEntity::getId).toArray();
        UnitServerEvents.addActionItem(
                ownerName,
                UnitAction.ATTACK_BUILDING,
                -1,
                ids,
                attackTarget.originPos,
                BlockPos.ZERO
        );
        attackCommitted = true;
        clearScoutTarget();
        if (selectedNewTarget) {
            ReignOfNether.LOGGER.info("[Bot] {} attacking {} at {} with {} units",
                    ownerName, attackTarget.ownerName, attackTarget.originPos, ids.length);
        }
        nextAttackTick = tick + difficulty.attackRefreshTicks();
    }

    private BuildingPlacement selectEnemyBuilding(RTSPlayer player, BotDifficulty difficulty, BlockPos armyPos) {
        return worldView.knownEnemyBuildings(player).stream()
                .filter(building -> isActiveEnemy(player, building.ownerName))
                .filter(building -> !targetCooldowns.containsKey(building.originPos))
                .min(Comparator
                        .comparingInt((BuildingPlacement building) -> BotDecisionMaker.targetPriority(
                                difficulty, building.isCapitol, building instanceof ProductionPlacement))
                        .thenComparingDouble(building -> building.centrePos.distSqr(armyPos)))
                .orElse(null);
    }

    private void scout(ServerLevel level, RTSPlayer player, List<LivingEntity> army, int tick) {
        if (scoutTarget != null && worldView.isVisible(scoutTarget) && level.hasChunkAt(scoutTarget)) {
            BlockPos ground = BotBuildingPlanner.groundAt(level, scoutTarget.getX(), scoutTarget.getZ());
            if (Math.abs(ground.getY() - player.aiHomePos.getY()) > 8) {
                scoutTarget = null;
            } else {
                BlockPos adjustedTarget = ground.above();
                if (!adjustedTarget.equals(scoutTarget)) {
                    scoutTarget = adjustedTarget;
                    resetScoutProgress(army, tick);
                    moveArmy(army, scoutTarget);
                }
            }
        }

        if (scoutTarget != null) {
            double currentDistance = closestArmyDistance(army, scoutTarget);
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
        resetScoutProgress(army, tick);
        moveArmy(army, scoutTarget);
        ReignOfNether.LOGGER.info("[Bot] {} scouting at {} with {} units",
                ownerName, scoutTarget, army.size());
    }

    private void resetScoutProgress(List<LivingEntity> army, int tick) {
        scoutBestDistance = closestArmyDistance(army, scoutTarget);
        scoutLastProgressTick = tick;
    }

    private static double closestArmyDistance(List<LivingEntity> army, BlockPos target) {
        return army.stream()
                .mapToDouble(entity -> Math.sqrt(entity.blockPosition().distSqr(target)))
                .min()
                .orElse(Double.POSITIVE_INFINITY);
    }

    private void clearScoutTarget() {
        scoutTarget = null;
        scoutBestDistance = 0;
        scoutLastProgressTick = 0;
    }

    private BlockPos nextScoutTarget(ServerLevel level, BlockPos home) {
        for (int attempt = 0; attempt < SCOUT_DIRECTIONS.length * 4; attempt++) {
            int index = scoutWaypointIndex++ % (SCOUT_DIRECTIONS.length * 4);
            int[] direction = SCOUT_DIRECTIONS[index % SCOUT_DIRECTIONS.length];
            int radius = 128 * (index / SCOUT_DIRECTIONS.length + 1);
            BlockPos column = new BlockPos(
                    home.getX() + direction[0] * radius,
                    home.getY(),
                    home.getZ() + direction[1] * radius
            );
            if (!level.getWorldBorder().isWithinBounds(column))
                continue;
            if (!worldView.isVisible(column) || !level.hasChunkAt(column))
                return column.above();
            BlockPos ground = BotBuildingPlanner.groundAt(
                    level,
                    column.getX(),
                    column.getZ()
            );
            if (Math.abs(ground.getY() - home.getY()) <= 8)
                return ground.above();
        }
        return home.above();
    }

    private void moveArmy(List<LivingEntity> army, BlockPos target) {
        UnitServerEvents.addActionItem(
                ownerName,
                UnitAction.MOVE,
                -1,
                army.stream().mapToInt(LivingEntity::getId).toArray(),
                target,
                BlockPos.ZERO
        );
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
        army.sort(Comparator.comparingInt(LivingEntity::getId));
        return army;
    }

    private Resources getResources() {
        for (Resources resources : ResourcesServerEvents.resourcesList)
            if (resources.ownerName.equals(ownerName))
                return resources;
        return null;
    }

    private boolean hasTestSpeedCheats() {
        return ResearchServerEvents.playerHasCheat(ownerName, "warpten")
                || ResearchServerEvents.playerHasCheat(ownerName, "operationcwal");
    }

    private boolean canAfford(Building building) {
        Resources resources = getResources();
        return resources != null
                && resources.food >= building.cost.food
                && resources.wood >= building.cost.wood
                && resources.ore >= building.cost.ore;
    }
}
