package com.solegendary.reignofnether.registrars;

import com.solegendary.reignofnether.ReignOfNether;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.enchantment.Enchantment;

public final class EnchantmentRegistrar {
    public static final ResourceKey<Enchantment> VIGOR = key("vigor");
    public static final ResourceKey<Enchantment> BREACHING = key("breaching");
    public static final ResourceKey<Enchantment> FORTIFYING = key("fortifying");
    public static final ResourceKey<Enchantment> MAIMING = key("maiming");
    public static final ResourceKey<Enchantment> ZEAL = key("zeal");
    public static final ResourceKey<Enchantment> GUST = key("gust");
    public static final ResourceKey<Enchantment> LONGSHOT = key("longshot");

    private EnchantmentRegistrar() {
    }

    private static ResourceKey<Enchantment> key(String path) {
        return ResourceKey.create(
            Registries.ENCHANTMENT,
            ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, path)
        );
    }
}
