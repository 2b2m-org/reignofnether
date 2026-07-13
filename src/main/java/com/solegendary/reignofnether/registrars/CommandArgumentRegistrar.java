package com.solegendary.reignofnether.registrars;

import com.solegendary.reignofnether.ReignOfNether;
import com.solegendary.reignofnether.commands.argument.BuildingArgument;

import net.minecraft.commands.synchronization.ArgumentTypeInfo;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.ForgeRegistries;
import net.neoforged.neoforge.registries.RegistryObject;

public class CommandArgumentRegistrar {
	
	public static final DeferredRegister<ArgumentTypeInfo<?, ?>> COMMAND_ARGUMENT_TYPES =
		DeferredRegister.create(ForgeRegistries.COMMAND_ARGUMENT_TYPES, ReignOfNether.MOD_ID);
	
	public static final RegistryObject<ArgumentTypeInfo<BuildingArgument, ?>> BUILDING_ARG =
		COMMAND_ARGUMENT_TYPES.register(
			"building",
			BuildingArgument.Info::new
		);
	
	public static void init(IEventBus modBus) {
		COMMAND_ARGUMENT_TYPES.register(modBus);
	}
}
