package com.solegendary.reignofnether.bot;

import com.solegendary.reignofnether.building.Building;
import com.solegendary.reignofnether.building.BuildingPlacement;
import com.solegendary.reignofnether.building.BuildingServerEvents;
import com.solegendary.reignofnether.building.buildings.placements.PortalPlacement;
import com.solegendary.reignofnether.building.buildings.placements.ProductionPlacement;
import com.solegendary.reignofnether.building.production.ActiveProduction;
import com.solegendary.reignofnether.building.production.ProductionItem;
import com.solegendary.reignofnether.player.PlayerServerEvents;
import com.solegendary.reignofnether.player.RTSPlayer;
import com.solegendary.reignofnether.resources.Resources;
import com.solegendary.reignofnether.resources.ResourcesServerEvents;
import com.solegendary.reignofnether.unit.UnitServerEvents;
import com.solegendary.reignofnether.unit.interfaces.AttackerUnit;
import com.solegendary.reignofnether.unit.interfaces.Unit;
import com.solegendary.reignofnether.unit.interfaces.WorkerUnit;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

final class BotSelf {
    private static final Comparator<PortalPlacement> PORTAL_ORDER = Comparator
            .comparingInt((PortalPlacement portal) -> portal.originPos.getX())
            .thenComparingInt(portal -> portal.originPos.getY())
            .thenComparingInt(portal -> portal.originPos.getZ());

    private final String ownerName;
    private BlockPos militaryPortalOrigin;
    private BlockPos supplyPortalOrigin;

    BotSelf(String ownerName) {
        this.ownerName = ownerName;
        RTSPlayer player = PlayerServerEvents.getRTSPlayer(ownerName);
        if (player != null) {
            militaryPortalOrigin = player.aiMilitaryPortalOrigin;
            supplyPortalOrigin = player.aiSupplyPortalOrigin;
        }
    }

    List<LivingEntity> workers() {
        List<LivingEntity> workers = new ArrayList<>();
        for (LivingEntity entity : UnitServerEvents.getAllUnits())
            if (entity.isAlive() && entity instanceof Unit unit && entity instanceof WorkerUnit
                    && unit.getOwnerName().equals(ownerName))
                workers.add(entity);
        workers.sort(Comparator.comparingInt(LivingEntity::getId));
        return workers;
    }

    List<LivingEntity> army() {
        List<LivingEntity> army = new ArrayList<>();
        for (LivingEntity entity : UnitServerEvents.getAllUnits())
            if (entity.isAlive() && entity instanceof Unit unit && entity instanceof AttackerUnit
                    && !(entity instanceof WorkerUnit) && unit.getOwnerName().equals(ownerName))
                army.add(entity);
        army.sort(Comparator.comparingInt(LivingEntity::getId));
        return army;
    }

    BuildingPlacement building(Building building) {
        for (BuildingPlacement placement : BuildingServerEvents.getBuildings())
            if (placement.ownerName.equals(ownerName) && placement.getBuilding() == building)
                return placement;
        return null;
    }

    ProductionPlacement militaryBuilding(BotStrategy strategy) {
        if (!strategy.usesTransformingPortals())
            return asProduction(building(strategy.military()));
        return portalAt(militaryPortalOrigin);
    }

    ProductionPlacement supplyBuilding(BotStrategy strategy) {
        if (!strategy.usesTransformingPortals())
            return asProduction(building(strategy.supply()));
        return portalAt(supplyPortalOrigin);
    }

    void recordPortal(PortalPlacement portal, boolean militaryRole) {
        if (militaryRole)
            militaryPortalOrigin = portal.originPos;
        else
            supplyPortalOrigin = portal.originPos;
        persistPortalRoles();
    }

    void reconcilePortalRoles(BotStrategy strategy) {
        if (!strategy.usesTransformingPortals())
            return;

        List<PortalPlacement> portals = ownedPortals();
        PortalPlacement military = validRecordedPortal(
                portals, militaryPortalOrigin, PortalPlacement.PortalType.MILITARY,
                strategy.militaryTransform(), strategy.supplyTransform());
        PortalPlacement supply = validRecordedPortal(
                portals, supplyPortalOrigin, PortalPlacement.PortalType.CIVILIAN,
                strategy.supplyTransform(), strategy.militaryTransform());

        if (military == null)
            military = exactRolePortal(portals, PortalPlacement.PortalType.MILITARY,
                    strategy.militaryTransform());
        if (supply == null)
            supply = exactRolePortal(portals, PortalPlacement.PortalType.CIVILIAN,
                    strategy.supplyTransform());
        if (military == supply)
            supply = null;

        if (military == null && supply != null)
            military = unclaimedBasicPortal(portals, supply, strategy.supplyTransform());
        if (supply == null && military != null)
            supply = unclaimedBasicPortal(portals, military, strategy.militaryTransform());
        if (military == null && supply == null) {
            List<PortalPlacement> basicPortals = unclaimedBasicPortals(
                    portals, strategy.militaryTransform(), strategy.supplyTransform());
            if (basicPortals.size() >= 2) {
                military = basicPortals.get(0);
                supply = basicPortals.get(1);
            }
        }

        militaryPortalOrigin = military == null ? null : military.originPos;
        supplyPortalOrigin = supply == null ? null : supply.originPos;
        persistPortalRoles();
    }

    boolean supplyUnderConstruction(BotStrategy strategy) {
        if (!strategy.usesTransformingPortals()) {
            for (BuildingPlacement placement : BuildingServerEvents.getBuildings())
                if (placement.ownerName.equals(ownerName)
                        && placement.getBuilding() == strategy.supply()
                        && !placement.isBuilt)
                    return true;
            return false;
        }

        PortalPlacement portal = portalAt(supplyPortalOrigin);
        return portal != null && (!portal.isBuilt
                || portal.getPortalType() == PortalPlacement.PortalType.BASIC);
    }

    int countQueued(ProductionItem item) {
        int count = 0;
        for (BuildingPlacement placement : BuildingServerEvents.getBuildings())
            if (placement.ownerName.equals(ownerName) && placement instanceof ProductionPlacement production)
                for (ActiveProduction active : production.productionQueue)
                    if (active.item == item)
                        count++;
        return count;
    }

    int queuedPopulation(ProductionItem item) {
        return countQueued(item) * Math.max(1, item.getCost(false, ownerName).population);
    }

    static int population(List<LivingEntity> units) {
        return units.stream().mapToInt(BotSelf::population).sum();
    }

    static int population(LivingEntity unit) {
        return Math.max(1, ((Unit) unit).getCost().population);
    }

    Resources resources() {
        for (Resources resources : ResourcesServerEvents.resourcesList)
            if (resources.ownerName.equals(ownerName))
                return resources;
        return null;
    }

    boolean canAfford(Building building) {
        Resources resources = resources();
        return resources != null
                && resources.food >= building.cost.food
                && resources.wood >= building.cost.wood
                && resources.ore >= building.cost.ore;
    }

    private List<PortalPlacement> ownedPortals() {
        List<PortalPlacement> portals = new ArrayList<>();
        for (BuildingPlacement placement : BuildingServerEvents.getBuildings())
            if (placement.ownerName.equals(ownerName) && placement instanceof PortalPlacement portal)
                portals.add(portal);
        portals.sort(PORTAL_ORDER);
        return portals;
    }

    private static PortalPlacement validRecordedPortal(List<PortalPlacement> portals, BlockPos origin,
                                                        PortalPlacement.PortalType role,
                                                        ProductionItem transform,
                                                        ProductionItem otherTransform) {
        if (origin == null)
            return null;
        for (PortalPlacement portal : portals)
            if (portal.originPos.equals(origin)
                    && (portal.getPortalType() == role || queueContains(portal, transform)
                    || portal.getPortalType() == PortalPlacement.PortalType.BASIC
                    && !queueContains(portal, otherTransform)))
                return portal;
        return null;
    }

    private static PortalPlacement exactRolePortal(List<PortalPlacement> portals,
                                                    PortalPlacement.PortalType role,
                                                    ProductionItem transform) {
        for (PortalPlacement portal : portals)
            if (queueContains(portal, transform))
                return portal;
        for (PortalPlacement portal : portals)
            if (portal.getPortalType() == role)
                return portal;
        return null;
    }

    private static PortalPlacement unclaimedBasicPortal(List<PortalPlacement> portals,
                                                         PortalPlacement claimed,
                                                         ProductionItem claimedTransform) {
        for (PortalPlacement portal : portals)
            if (portal != claimed
                    && portal.getPortalType() == PortalPlacement.PortalType.BASIC
                    && !queueContains(portal, claimedTransform))
                return portal;
        return null;
    }

    private static List<PortalPlacement> unclaimedBasicPortals(List<PortalPlacement> portals,
                                                                ProductionItem militaryTransform,
                                                                ProductionItem supplyTransform) {
        return portals.stream()
                .filter(portal -> portal.getPortalType() == PortalPlacement.PortalType.BASIC)
                .filter(portal -> !queueContains(portal, militaryTransform)
                        && !queueContains(portal, supplyTransform))
                .toList();
    }

    private void persistPortalRoles() {
        RTSPlayer player = PlayerServerEvents.getRTSPlayer(ownerName);
        if (player == null || Objects.equals(player.aiMilitaryPortalOrigin, militaryPortalOrigin)
                && Objects.equals(player.aiSupplyPortalOrigin, supplyPortalOrigin))
            return;
        player.aiMilitaryPortalOrigin = militaryPortalOrigin;
        player.aiSupplyPortalOrigin = supplyPortalOrigin;
        PlayerServerEvents.saveRTSPlayers();
    }

    private PortalPlacement portalAt(BlockPos origin) {
        if (origin == null)
            return null;
        for (BuildingPlacement placement : BuildingServerEvents.getBuildings())
            if (placement.ownerName.equals(ownerName)
                    && placement.originPos.equals(origin)
                    && placement instanceof PortalPlacement portal)
                return portal;
        return null;
    }

    private static boolean queueContains(ProductionPlacement placement, ProductionItem item) {
        for (ActiveProduction active : placement.productionQueue)
            if (active.item == item)
                return true;
        return false;
    }

    private static ProductionPlacement asProduction(BuildingPlacement placement) {
        return placement instanceof ProductionPlacement production ? production : null;
    }
}
