package com.autofarm;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * Small helpers so the engine doesn't have to hardcode raw slot math everywhere
 * and can fall back to "find the slot that looks like X" when exact indices
 * drift (plugin updates, different GUI size on a different menu, etc).
 *
 * Written against Mojang's official mappings (Minecraft 26.1+ ships
 * unobfuscated, so these are the real class/method names - no Yarn needed).
 */
public final class GuiUtils {

    private GuiUtils() {}

    public static Minecraft client() {
        return Minecraft.getInstance();
    }

    /** True if a screen is open and its title contains the given text (case-insensitive). */
    public static boolean currentScreenTitleContains(String needle) {
        Minecraft mc = client();
        if (mc.screen == null) return false;
        Component title = mc.screen.getTitle();
        if (title == null) return false;
        return title.getString().toLowerCase().contains(needle.toLowerCase());
    }

    public static boolean hasScreenOpen() {
        return client().screen != null;
    }

    /** Current player container menu, or null if none. */
    public static AbstractContainerMenu currentHandler() {
        var player = client().player;
        return player == null ? null : player.containerMenu;
    }

    public static int findContainerSlotByItemId(AbstractContainerMenu handler, String itemIdSubstring, int containerSlotCount) {
        if (handler == null) return -1;
        int limit = Math.min(containerSlotCount, handler.slots.size());
        for (int i = 0; i < limit; i++) {
            Slot slot = handler.getSlot(i);
            ItemStack stack = slot.getItem();
            if (stack.isEmpty()) continue;
            String id = ItemIds.of(stack);
            if (id.toLowerCase().contains(itemIdSubstring.toLowerCase())) {
                return i;
            }
        }
        return -1;
    }

    public static int findPlayerInvSlotByItemId(AbstractContainerMenu handler, String itemIdSubstring, int containerSlotCount) {
        if (handler == null) return -1;
        for (int i = containerSlotCount; i < handler.slots.size(); i++) {
            Slot slot = handler.getSlot(i);
            ItemStack stack = slot.getItem();
            if (stack.isEmpty()) continue;
            String id = ItemIds.of(stack);
            if (id.toLowerCase().contains(itemIdSubstring.toLowerCase())) {
                return i;
            }
        }
        return -1;
    }

    public static boolean playerInvHasNoneOf(AbstractContainerMenu handler, String itemIdSubstring, int containerSlotCount) {
        return findPlayerInvSlotByItemId(handler, itemIdSubstring, containerSlotCount) == -1;
    }
}
