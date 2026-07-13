package com.solegendary.reignofnether.registrars;

import com.solegendary.reignofnether.ReignOfNether;
import com.solegendary.reignofnether.items.HeroExperienceBottleItem;
import com.solegendary.reignofnether.items.ThrowableTnt;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.SpawnEggItem;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.minecraft.core.registries.Registries;
import net.neoforged.neoforge.registries.DeferredHolder;

public class ItemRegistrar {

    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(Registries.ITEM, ReignOfNether.MOD_ID);

    public static final DeferredHolder<Item, SpawnEggItem> ZOMBIE_UNIT_SPAWN_EGG =
            ITEMS.register("zombie_unit_spawn_egg", () -> new SpawnEggItem(EntityRegistrar.ZOMBIE_UNIT.get(),
                    0x009999, 0x577048, new Item.Properties()));

    public static final DeferredHolder<Item, SpawnEggItem> HUSK_UNIT_SPAWN_EGG =
            ITEMS.register("husk_unit_spawn_egg", () -> new SpawnEggItem(EntityRegistrar.HUSK_UNIT.get(),
                    0x71695B, 0xB7A276, new Item.Properties()));

    public static final DeferredHolder<Item, SpawnEggItem> DROWNED_UNIT_SPAWN_EGG =
            ITEMS.register("drowned_unit_spawn_egg", () -> new SpawnEggItem(EntityRegistrar.DROWNED_UNIT.get(),
                    9433559, 7969893, new Item.Properties()));

    public static final DeferredHolder<Item, SpawnEggItem> ZOMBIE_PIGLIN_UNIT_SPAWN_EGG =
            ITEMS.register("zombie_piglin_unit_spawn_egg", () -> new SpawnEggItem(EntityRegistrar.ZOMBIE_PIGLIN_UNIT.get(),
                    15373203, 5009705, new Item.Properties()));

    public static final DeferredHolder<Item, SpawnEggItem> ZOGLIN_UNIT =
            ITEMS.register("zoglin_unit_spawn_egg", () -> new SpawnEggItem(EntityRegistrar.ZOGLIN_UNIT.get(),
                    13004373, 15132390, new Item.Properties()));

    public static final DeferredHolder<Item, SpawnEggItem> SKELETON_UNIT_SPAWN_EGG =
            ITEMS.register("skeleton_unit_spawn_egg", () -> new SpawnEggItem(EntityRegistrar.SKELETON_UNIT.get(),
                    0xa7a7a7, 0x3a3a3a, new Item.Properties()));

    public static final DeferredHolder<Item, SpawnEggItem> STRAY_UNIT_SPAWN_EGG =
            ITEMS.register("stray_unit_spawn_egg", () -> new SpawnEggItem(EntityRegistrar.STRAY_UNIT.get(),
                    0x5B6F6F, 0xAEB8B8, new Item.Properties()));

    public static final DeferredHolder<Item, SpawnEggItem> BOGGED_UNIT_SPAWN_EGG =
            ITEMS.register("bogged_unit_spawn_egg", () -> new SpawnEggItem(EntityRegistrar.BOGGED_UNIT.get(),
                    0xa1a387, 0x3e4d12, new Item.Properties()));

    public static final DeferredHolder<Item, SpawnEggItem> CREEPER_UNIT_SPAWN_EGG =
            ITEMS.register("creeper_unit_spawn_egg", () -> new SpawnEggItem(EntityRegistrar.CREEPER_UNIT.get(),
                    0x0c990a, 0x000000, new Item.Properties()));

    public static final DeferredHolder<Item, SpawnEggItem> SPIDER_UNIT_SPAWN_EGG =
            ITEMS.register("spider_unit_spawn_egg", () -> new SpawnEggItem(EntityRegistrar.SPIDER_UNIT.get(),
                    0x322B26, 0x840B0B, new Item.Properties()));

    public static final DeferredHolder<Item, SpawnEggItem> POISON_SPIDER_UNIT_SPAWN_EGG =
            ITEMS.register("poison_spider_unit_spawn_egg", () -> new SpawnEggItem(EntityRegistrar.POISON_SPIDER_UNIT.get(),
                    0x0B3F4A, 0x840B0B, new Item.Properties()));

    public static final DeferredHolder<Item, SpawnEggItem> WRAITH_UNIT_SPAWN_EGG =
            ITEMS.register("wraith_unit_spawn_egg", () -> new SpawnEggItem(EntityRegistrar.WRAITH_UNIT.get(),
                    0xc3cdc9, 0x1a1862, new Item.Properties()));

    public static final DeferredHolder<Item, SpawnEggItem> VILLAGER_UNIT_SPAWN_EGG =
            ITEMS.register("villager_unit_spawn_egg", () -> new SpawnEggItem(EntityRegistrar.VILLAGER_UNIT.get(),
                    0x523632, 0x946F66, new Item.Properties()));

    public static final DeferredHolder<Item, SpawnEggItem> MILITIA_UNIT_SPAWN_EGG =
            ITEMS.register("militia_unit_spawn_egg", () -> new SpawnEggItem(EntityRegistrar.MILITIA_UNIT.get(),
                    0x523632, 0x946F66, new Item.Properties()));

    public static final DeferredHolder<Item, SpawnEggItem> ZOMBIE_VILLAGER_UNIT_SPAWN_EGG =
            ITEMS.register("zombie_villager_unit_spawn_egg", () -> new SpawnEggItem(EntityRegistrar.ZOMBIE_VILLAGER_UNIT.get(),
                    0x523632, 0x647E51, new Item.Properties()));

    public static final DeferredHolder<Item, SpawnEggItem> VINDICATOR_UNIT_SPAWN_EGG =
            ITEMS.register("vindicator_unit_spawn_egg", () -> new SpawnEggItem(EntityRegistrar.VINDICATOR_UNIT.get(),
                    0x8B8F90, 0x1F4952, new Item.Properties()));

    public static final DeferredHolder<Item, SpawnEggItem> PILLAGER_UNIT_SPAWN_EGG =
            ITEMS.register("pillager_unit_spawn_egg", () -> new SpawnEggItem(EntityRegistrar.PILLAGER_UNIT.get(),
                    0x502C34, 0x757D78, new Item.Properties()));

    public static final DeferredHolder<Item, SpawnEggItem> WINDCALLER_UNIT_SPAWN_EGG =
            ITEMS.register("windcaller_unit_spawn_egg", () -> new SpawnEggItem(EntityRegistrar.WINDCALLER_UNIT.get(),
                    0x502C34, 0x757D78, new Item.Properties()));

    public static final DeferredHolder<Item, SpawnEggItem> IRON_GOLEM_UNIT_SPAWN_EGG =
            ITEMS.register("iron_golem_unit_spawn_egg", () -> new SpawnEggItem(EntityRegistrar.IRON_GOLEM_UNIT.get(),
                    0x101010, 0x757D78, new Item.Properties()));

    public static final DeferredHolder<Item, SpawnEggItem> WITCH_UNIT_SPAWN_EGG =
            ITEMS.register("witch_unit_spawn_egg", () -> new SpawnEggItem(EntityRegistrar.WITCH_UNIT.get(),
                    0x330000, 0x3A732D, new Item.Properties()));

    public static final DeferredHolder<Item, SpawnEggItem> EVOKER_UNIT_SPAWN_EGG =
            ITEMS.register("evoker_unit_spawn_egg", () -> new SpawnEggItem(EntityRegistrar.EVOKER_UNIT.get(),
                    0x8D9393, 0x141414, new Item.Properties()));

    public static final DeferredHolder<Item, SpawnEggItem> ENDERMAN_UNIT_SPAWN_EGG =
            ITEMS.register("enderman_unit_spawn_egg", () -> new SpawnEggItem(EntityRegistrar.ENDERMAN_UNIT.get(),
                    0x1E1E1E, 0x000000, new Item.Properties()));

    public static final DeferredHolder<Item, SpawnEggItem> WARDEN_UNIT_SPAWN_EGG =
            ITEMS.register("warden_unit_spawn_egg", () -> new SpawnEggItem(EntityRegistrar.WARDEN_UNIT.get(),
                    0x0e4145, 0x2da7b0, new Item.Properties()));

    public static final DeferredHolder<Item, SpawnEggItem> RAVAGER_UNIT_SPAWN_EGG =
            ITEMS.register("ravager_unit_spawn_egg", () -> new SpawnEggItem(EntityRegistrar.RAVAGER_UNIT.get(),
                    0x6e6d69, 0x413934, new Item.Properties()));

    public static final DeferredHolder<Item, SpawnEggItem> SILVERFISH_UNIT_SPAWN_EGG =
            ITEMS.register("silverfish_unit_spawn_egg", () -> new SpawnEggItem(EntityRegistrar.SILVERFISH_UNIT.get(),
                    0x666666, 0x222222, new Item.Properties()));

    public static final DeferredHolder<Item, SpawnEggItem> GRUNT_UNIT_SPAWN_EGG =
            ITEMS.register("grunt_unit_spawn_egg", () -> new SpawnEggItem(EntityRegistrar.GRUNT_UNIT.get(),
                    0x925A3D, 0xC9C685, new Item.Properties()));

    public static final DeferredHolder<Item, SpawnEggItem> BRUTE_UNIT_SPAWN_EGG =
            ITEMS.register("brute_unit_spawn_egg", () -> new SpawnEggItem(EntityRegistrar.BRUTE_UNIT.get(),
                    0x57290f, 0xC9C685, new Item.Properties()));

    public static final DeferredHolder<Item, SpawnEggItem> HEADHUNTER_UNIT_SPAWN_EGG =
            ITEMS.register("headhunter_unit_spawn_egg", () -> new SpawnEggItem(EntityRegistrar.HEADHUNTER_UNIT.get(),
                    0x57290f, 0xC9C685, new Item.Properties()));

    public static final DeferredHolder<Item, SpawnEggItem> MARAUDER_UNIT_SPAWN_EGG =
            ITEMS.register("marauder_unit_spawn_egg", () -> new SpawnEggItem(EntityRegistrar.MARAUDER_UNIT.get(),
                    0x57290f, 0xC9C685, new Item.Properties()));

    public static final DeferredHolder<Item, SpawnEggItem> HOGLIN_UNIT_SPAWN_EGG =
            ITEMS.register("hoglin_unit_spawn_egg", () -> new SpawnEggItem(EntityRegistrar.HOGLIN_UNIT.get(),
                    13004373, 6251620, new Item.Properties()));

    public static final DeferredHolder<Item, SpawnEggItem> BLAZE_UNIT_SPAWN_EGG =
            ITEMS.register("blaze_unit_spawn_egg", () -> new SpawnEggItem(EntityRegistrar.BLAZE_UNIT.get(),
                    16167425, 16775294, new Item.Properties()));

    public static final DeferredHolder<Item, SpawnEggItem> WITHER_SKELETON_UNIT_SPAWN_EGG =
            ITEMS.register("wither_skeleton_unit_spawn_egg", () -> new SpawnEggItem(EntityRegistrar.WITHER_SKELETON_UNIT.get(),
                    1315860, 4672845, new Item.Properties()));

    public static final DeferredHolder<Item, SpawnEggItem> GHAST_UNIT_SPAWN_EGG =
            ITEMS.register("ghast_unit_spawn_egg", () -> new SpawnEggItem(EntityRegistrar.GHAST_UNIT.get(),
                    16382457, 12369084, new Item.Properties()));

    public static final DeferredHolder<Item, SpawnEggItem> MAGMA_CUBE_UNIT_SPAWN_EGG =
            ITEMS.register("magma_cube_unit_spawn_egg", () -> new SpawnEggItem(EntityRegistrar.MAGMA_CUBE_UNIT.get(),
                    3080192, 11776768, new Item.Properties()));

    public static final DeferredHolder<Item, SpawnEggItem> SLIME_UNIT_SPAWN_EGG =
            ITEMS.register("slime_unit_spawn_egg", () -> new SpawnEggItem(EntityRegistrar.SLIME_UNIT.get(),
                    5405768, 5018938, new Item.Properties()));

    public static final DeferredHolder<Item, SpawnEggItem> ROYAL_GUARD_UNIT_SPAWN_EGG =
            ITEMS.register("royal_guard_unit_spawn_egg", () -> new SpawnEggItem(EntityRegistrar.ROYAL_GUARD_UNIT.get(),
                    0x959b9b, 0x014675, new Item.Properties()));

    public static final DeferredHolder<Item, SpawnEggItem> NECROMANCER_UNIT_SPAWN_EGG =
            ITEMS.register("necromancer_unit_spawn_egg", () -> new SpawnEggItem(EntityRegistrar.NECROMANCER_UNIT.get(),
                    0x3f243d, 0x0b9cbb, new Item.Properties()));

    public static final DeferredHolder<Item, SpawnEggItem> PIGLIN_MERCHANT_UNIT_SPAWN_EGG =
            ITEMS.register("piglin_merchant_unit_spawn_egg", () -> new SpawnEggItem(EntityRegistrar.PIGLIN_MERCHANT_UNIT.get(),
                    0x3d1f12, 0x91da2a, new Item.Properties()));

    public static final DeferredHolder<Item, SpawnEggItem> POLAR_BEAR_UNIT_SPAWN_EGG =
            ITEMS.register("polar_bear_unit_spawn_egg", () -> new SpawnEggItem(EntityRegistrar.POLAR_BEAR_UNIT.get(),
                    0xe3e3e3, 0x6f6f6f, new Item.Properties()));

    public static final DeferredHolder<Item, SpawnEggItem> GRIZZLY_BEAR_UNIT_SPAWN_EGG =
            ITEMS.register("grizzly_bear_unit_spawn_egg", () -> new SpawnEggItem(EntityRegistrar.GRIZZLY_BEAR_UNIT.get(),
                    0x665442, 0x543423, new Item.Properties()));

    public static final DeferredHolder<Item, SpawnEggItem> PANDA_UNIT_SPAWN_EGG =
            ITEMS.register("panda_unit_spawn_egg", () -> new SpawnEggItem(EntityRegistrar.PANDA_UNIT.get(),
                    0xd9d9d9, 0x121218, new Item.Properties()));

    public static final DeferredHolder<Item, SpawnEggItem> WOLF_UNIT_SPAWN_EGG =
            ITEMS.register("wolf_unit_spawn_egg", () -> new SpawnEggItem(EntityRegistrar.WOLF_UNIT.get(),
                    0xc3bfbf, 0x947e6c, new Item.Properties()));

    public static final DeferredHolder<Item, SpawnEggItem> LLAMA_UNIT_SPAWN_EGG =
            ITEMS.register("llama_unit_spawn_egg", () -> new SpawnEggItem(EntityRegistrar.LLAMA_UNIT.get(),
                    0xa6896c, 0x6e442e, new Item.Properties()));

    public static final DeferredHolder<Item, Item> THROWABLE_TNT =
            ITEMS.register("throwable_tnt", () -> new ThrowableTnt(new Item.Properties()));

    public static final DeferredHolder<Item, Item> THROWN_HERO_EXPERIENCE_BOTTLE =
            ITEMS.register("thrown_hero_experience_bottle", () -> new HeroExperienceBottleItem(new Item.Properties()));

    public static void init(IEventBus modBus) {
        ITEMS.register(modBus);
    }
}
