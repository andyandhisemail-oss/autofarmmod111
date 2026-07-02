package com.autofarm;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;

public final class ItemIds {
    private ItemIds() {}

    public static String of(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return "";
        return BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
    }
}
