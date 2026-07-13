package com.solegendary.reignofnether;

import com.solegendary.reignofnether.config.ReignOfNetherClientConfigs;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;

public class ClientModConfigs {

    public static void registerClientConfigs(ModContainer modContainer) {
        modContainer.registerConfig(ModConfig.Type.CLIENT,
                ReignOfNetherClientConfigs.SPEC,
                "reignofnether-client-" + ReignOfNether.VERSION_STRING + ".toml");

        modContainer.registerExtensionPoint(
                IConfigScreenFactory.class,
                ReignOfNetherClientConfigs.createConfigScreen()
        );
    }
}
