package com.solegendary.reignofnether;

import com.solegendary.reignofnether.building.Buildings;
import com.solegendary.reignofnether.building.production.ProductionItems;
import com.solegendary.reignofnether.commands.argument.BuildingArgument;
import com.solegendary.reignofnether.commands.argument.options.BuildingSelectorOptions;
import com.solegendary.reignofnether.config.ReignOfNetherCommonConfigs;
import com.solegendary.reignofnether.faction.FactionRegistries;
import com.solegendary.reignofnether.registrars.AttributeRegistrar;
import com.solegendary.reignofnether.registrars.BlockEntityRegistrar;
import com.solegendary.reignofnether.registrars.BlockRegistrar;
import com.solegendary.reignofnether.registrars.ClientEventRegistrar;
import com.solegendary.reignofnether.registrars.CommandArgumentRegistrar;
import com.solegendary.reignofnether.registrars.ContainerRegistrar;
import com.solegendary.reignofnether.registrars.EnchantmentRegistrar;
import com.solegendary.reignofnether.registrars.EntityRegistrar;
import com.solegendary.reignofnether.registrars.GameRuleRegistrar;
import com.solegendary.reignofnether.registrars.ItemRegistrar;
import com.solegendary.reignofnether.registrars.MobEffectRegistrar;
import com.solegendary.reignofnether.registrars.ParticleRegistrar;
import com.solegendary.reignofnether.registrars.ServerEventRegistrar;
import com.solegendary.reignofnether.registrars.SoundRegistrar;
import com.solegendary.reignofnether.resources.ResourceCosts;
import net.minecraft.commands.synchronization.ArgumentTypeInfos;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.fml.loading.FMLEnvironment;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Mod(ReignOfNether.MOD_ID)
public final class ReignOfNether {
    public static final Logger LOGGER = LogManager.getLogger();
    public static final String MOD_ID = "reignofnether";
    public static final String VERSION_STRING = "1.3.8a";

    public ReignOfNether(IEventBus modBus, ModContainer modContainer) {
        EnchantmentRegistrar.init(modBus);
        AttributeRegistrar.init(modBus);
        ItemRegistrar.init(modBus);
        EntityRegistrar.init(modBus);
        ContainerRegistrar.init(modBus);
        SoundRegistrar.init(modBus);
        BlockRegistrar.init(modBus);
        BlockEntityRegistrar.init(modBus);
        GameRuleRegistrar.init();
        Buildings.init();
        FactionRegistries.register();
        ProductionItems.init();
        MobEffectRegistrar.init(modBus);
        ParticleRegistrar.init(modBus);
        CommandArgumentRegistrar.init(modBus);
        BuildingSelectorOptions.bootStrap();

        modBus.register(CommonModEvents.class);
        modBus.addListener(ReignOfNether::commonSetup);
        modContainer.registerConfig(
            ModConfig.Type.COMMON,
            ReignOfNetherCommonConfigs.SPEC,
            "reignofnether-common-" + VERSION_STRING + ".toml"
        );

        if (FMLEnvironment.dist == Dist.CLIENT) {
            modBus.register(ClientModEvents.class);
            new ClientEventRegistrar().registerClientEvents();
            ClientModConfigs.registerClientConfigs(modContainer);
        } else {
            new ServerEventRegistrar().registerServerEvents();
        }
    }

    private static void commonSetup(FMLCommonSetupEvent event) {
        ResourceCosts.deferredLoadResourceCosts();
        event.enqueueWork(() -> ArgumentTypeInfos.registerByClass(
            BuildingArgument.class,
            CommandArgumentRegistrar.BUILDING_ARG.get()
        ));
    }
}
