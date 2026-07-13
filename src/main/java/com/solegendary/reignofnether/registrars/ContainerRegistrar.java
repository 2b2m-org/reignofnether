package com.solegendary.reignofnether.registrars;

import com.solegendary.reignofnether.ReignOfNether;
import com.solegendary.reignofnether.guiscreen.TopdownGuiContainer;
import net.minecraft.world.flag.FeatureFlagSet;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.ForgeRegistries;
import net.neoforged.neoforge.registries.RegistryObject;

public class ContainerRegistrar {

    public static final DeferredRegister<MenuType<?>> CONTAINERS = DeferredRegister.create(ForgeRegistries.MENU_TYPES,
            ReignOfNether.MOD_ID);

    public static final RegistryObject<MenuType<TopdownGuiContainer>> TOPDOWNGUI_CONTAINER = CONTAINERS
            .register("topdowngui_container", () -> new MenuType<>(TopdownGuiContainer::new, FeatureFlagSet.of()));

    public static void init(IEventBus modBus) {
        CONTAINERS.register(modBus);
    }
}
