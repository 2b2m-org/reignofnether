package com.solegendary.reignofnether.bot;

import com.solegendary.reignofnether.building.Building;
import com.solegendary.reignofnether.building.Buildings;
import com.solegendary.reignofnether.building.production.ProductionItem;
import com.solegendary.reignofnether.building.production.ProductionItems;
import com.solegendary.reignofnether.faction.Faction;

public record BotStrategy(
        Building capitol,
        Building supply,
        Building farm,
        Building military,
        ProductionItem worker,
        ProductionItem melee,
        ProductionItem ranged,
        ProductionItem supplyTransform,
        ProductionItem militaryTransform
) {
    public static BotStrategy forFaction(Faction faction) {
        return switch (faction) {
            case VILLAGERS -> new BotStrategy(
                    Buildings.TOWN_CENTRE,
                    Buildings.VILLAGER_HOUSE,
                    Buildings.WHEAT_FARM,
                    Buildings.BARRACKS,
                    ProductionItems.VILLAGER,
                    ProductionItems.VINDICATOR,
                    ProductionItems.PILLAGER,
                    null,
                    null
            );
            case MONSTERS -> new BotStrategy(
                    Buildings.MAUSOLEUM,
                    Buildings.HAUNTED_HOUSE,
                    Buildings.PUMPKIN_FARM,
                    Buildings.GRAVEYARD,
                    ProductionItems.ZOMBIE_VILLAGER,
                    ProductionItems.ZOMBIE,
                    ProductionItems.SKELETON,
                    null,
                    null
            );
            case PIGLINS -> new BotStrategy(
                    Buildings.CENTRAL_PORTAL,
                    Buildings.PORTAL_BASIC,
                    Buildings.NETHERWART_FARM,
                    Buildings.PORTAL_BASIC,
                    ProductionItems.GRUNT,
                    ProductionItems.BRUTE,
                    ProductionItems.HEADHUNTER,
                    ProductionItems.RESEARCH_PORTAL_FOR_CIVILIAN,
                    ProductionItems.RESEARCH_PORTAL_FOR_MILITARY
            );
            default -> throw new IllegalArgumentException("Unsupported bot faction: " + faction);
        };
    }

    public boolean usesTransformingPortals() {
        return supplyTransform != null;
    }
}
