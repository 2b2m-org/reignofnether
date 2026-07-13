package com.solegendary.reignofnether.util;

import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.enchantment.ItemEnchantments;

import java.util.Map;

public final class EnchantmentUtil {
    private static final Map<ResourceKey<Enchantment>, Item> LEVEL_TWO_ENCHANTS = Map.of(
        Enchantments.SHARPNESS, Items.IRON_AXE,
        Enchantments.QUICK_CHARGE, Items.CROSSBOW
    );

    private EnchantmentUtil() {
    }

    public static Holder.Reference<Enchantment> holder(
        RegistryAccess registryAccess,
        ResourceKey<Enchantment> enchantment
    ) {
        return registryAccess.registryOrThrow(Registries.ENCHANTMENT).getHolderOrThrow(enchantment);
    }

    public static int getLevel(ItemStack stack, ResourceKey<Enchantment> enchantment) {
        for (var entry : stack.getEnchantments().entrySet()) {
            if (entry.getKey().is(enchantment)) {
                return entry.getIntValue();
            }
        }
        return 0;
    }

    public static boolean has(ItemStack stack, ResourceKey<Enchantment> enchantment) {
        return getLevel(stack, enchantment) > 0;
    }

    public static boolean hasFrostWalker(LivingEntity entity) {
        return has(entity.getItemBySlot(EquipmentSlot.FEET), Enchantments.FROST_WALKER);
    }

    public static void enchant(
        ItemStack stack,
        RegistryAccess registryAccess,
        ResourceKey<Enchantment> enchantment,
        int level
    ) {
        stack.enchant(holder(registryAccess, enchantment), level);
    }

    public static void remove(ItemStack stack, ResourceKey<Enchantment> enchantment) {
        EnchantmentHelper.updateEnchantments(stack, mutable -> mutable.removeIf(holder -> holder.is(enchantment)));
    }

    public static void clear(ItemStack stack) {
        EnchantmentHelper.setEnchantments(stack, ItemEnchantments.EMPTY);
    }

    public static void updateEnchantLevels(LivingEntity entity, boolean regularLevels) {
        updateEnchantLevels(entity.getItemBySlot(EquipmentSlot.CHEST), regularLevels);
        updateEnchantLevels(entity.getItemBySlot(EquipmentSlot.MAINHAND), regularLevels);
    }

    private static void updateEnchantLevels(ItemStack stack, boolean regularLevels) {
        EnchantmentHelper.updateEnchantments(stack, mutable -> {
            for (Holder<Enchantment> enchantment : mutable.keySet().toArray(Holder[]::new)) {
                int baseLevel = LEVEL_TWO_ENCHANTS.entrySet().stream()
                    .anyMatch(entry -> enchantment.is(entry.getKey()) && entry.getValue() == stack.getItem()) ? 2 : 1;
                mutable.set(enchantment, baseLevel * (regularLevels ? 1 : 2));
            }
        });
    }
}
