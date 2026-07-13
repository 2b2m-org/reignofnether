package com.solegendary.reignofnether.registrars;

import com.solegendary.reignofnether.ReignOfNether;
import com.solegendary.reignofnether.commands.argument.BuildingArgument;

import net.minecraft.commands.synchronization.ArgumentTypeInfo;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.minecraft.core.registries.Registries;
import net.neoforged.neoforge.registries.DeferredHolder;

public class CommandArgumentRegistrar {
	
	public static final DeferredRegister<ArgumentTypeInfo<?, ?>> COMMAND_ARGUMENT_TYPES =
		DeferredRegister.create(Registries.COMMAND_ARGUMENT_TYPE, ReignOfNether.MOD_ID);
	
	public static final DeferredHolder<ArgumentTypeInfo<?, ?>, ArgumentTypeInfo<BuildingArgument, ?>> BUILDING_ARG =
		COMMAND_ARGUMENT_TYPES.register(
			"building",
			BuildingArgument.Info::new
		);
	
	public static void init(IEventBus modBus) {
		COMMAND_ARGUMENT_TYPES.register(modBus);
	}
}
