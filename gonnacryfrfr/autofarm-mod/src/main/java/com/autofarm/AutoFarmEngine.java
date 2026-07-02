package com.autofarm;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

/**
 * Runs entirely on the client tick. One state transition (at most) happens
 * per tick, most states just wait out a short delay so we don't hammer the
 * server with instant clicks. Nothing here touches the network directly
 * except through normal client interaction / slot-click packets, i.e.
 * everything a real player clicking through the GUI would also send.
 *
 * Written against Mojang's official mappings (Minecraft 26.1+). The class
 * names below (Minecraft, LocalPlayer, AbstractContainerMenu, ClickType,
 * InteractionHand...) are the real, official names now that the game ships
 * unobfuscated - not Yarn names.
 */
public class AutoFarmEngine {

    private final AutoFarmConfig config;

    private AutoFarmState state = AutoFarmState.IDLE;
    private int waitTicks = 0;
    private int timeoutTicks = 0;
    private int pagesDropped = 0;

    public AutoFarmEngine(AutoFarmConfig config) {
        this.config = config;
    }

    public AutoFarmState getState() {
        return state;
    }

    public void stop() {
        state = AutoFarmState.IDLE;
        waitTicks = 0;
        timeoutTicks = 0;
        pagesDropped = 0;
    }

    private void goTo(AutoFarmState next, int delay) {
        state = next;
        waitTicks = delay;
    }

    private void goToWithTimeout(AutoFarmState next, int delay, int timeout) {
        state = next;
        waitTicks = delay;
        timeoutTicks = timeout;
    }

    private void abortToIdle(Minecraft mc, String reason) {
        info(mc, "AutoFarm: stopping - " + reason);
        stop();
    }

    private void info(Minecraft mc, String msg) {
        if (mc.player != null) {
            mc.player.sendSystemMessage(Component.literal(msg));
        }
        AutoFarmMod.LOGGER.info(msg);
    }

    /** Call once per client tick. */
    public void tick(Minecraft mc) {
        if (!config.enabled) return;
        LocalPlayer player = mc.player;
        if (player == null) return;

        if (waitTicks > 0) {
            waitTicks--;
            return;
        }
        if (timeoutTicks > 0) {
            timeoutTicks--;
            if (timeoutTicks == 0 && !stateReachedExpectedScreen()) {
                abortToIdle(mc, "timed out waiting for a screen in state " + state
                        + " (check your slot indices / title substrings in the config)");
                return;
            }
        }

        switch (state) {
            case IDLE -> handleIdle(mc, player);
            case INTERACT_SPAWNER -> handleInteractSpawner(mc, player);
            case WAIT_LOOT_SCREEN -> handleWaitLootScreen(mc);
            case DROP_PAGE -> handleDropPage(mc, player);
            case WAIT_AFTER_DROP -> handleWaitAfterDrop();
            case NEXT_PAGE -> handleNextPage(mc, player);
            case WAIT_AFTER_NEXT -> goTo(AutoFarmState.DROP_PAGE, 0);
            case CLOSE_LOOT_SCREEN -> handleCloseLootScreen(mc, player);
            case WAIT_BEFORE_ORDER_COMMAND -> goTo(AutoFarmState.SEND_ORDER_COMMAND, 0);
            case SEND_ORDER_COMMAND -> handleSendOrderCommand(mc, player);
            case WAIT_ORDER_SCREEN -> handleWaitOrderScreen(mc);
            case SELL_ITEM -> handleSellItem(mc, player);
            case WAIT_AFTER_SELL_CLICK -> handleWaitAfterSellClick(mc);
            case OPEN_CONFIRM_SCREEN -> handleOpenConfirmScreen(mc, player);
            case WAIT_CONFIRM_SCREEN -> handleWaitConfirmScreen(mc);
            case CLICK_CONFIRM -> handleClickConfirm(mc, player);
            case WAIT_AFTER_CONFIRM -> handleWaitAfterConfirm(mc);
            case CLOSE_ORDERS_FIRST_ESCAPE -> handleCloseFirstEscape(mc, player);
            case WAIT_BETWEEN_ESCAPES -> goTo(AutoFarmState.CLOSE_ORDERS_SECOND_ESCAPE, 0);
            case CLOSE_ORDERS_SECOND_ESCAPE -> handleCloseSecondEscape(mc, player);
            case WAIT_BEFORE_RESTART -> goTo(AutoFarmState.IDLE, 0);
        }
    }

    private boolean stateReachedExpectedScreen() {
        return switch (state) {
            case WAIT_LOOT_SCREEN -> GuiUtils.currentScreenTitleContains(config.spawnerScreenTitleContains);
            case WAIT_ORDER_SCREEN -> GuiUtils.currentScreenTitleContains(config.orderScreenTitleContains);
            case WAIT_CONFIRM_SCREEN -> GuiUtils.currentScreenTitleContains(config.confirmScreenTitleContains);
            default -> true;
        };
    }

    private void handleIdle(Minecraft mc, LocalPlayer player) {
        if (GuiUtils.hasScreenOpen()) return;

        HitResult target = mc.hitResult;
        if (!(target instanceof BlockHitResult blockHit)) {
            return; // not looking at a block - aim at the spawner
        }
        if (mc.level == null) return;
        String registryId = BuiltInRegistries.BLOCK
                .getKey(mc.level.getBlockState(blockHit.getBlockPos()).getBlock()).toString();
        if (!registryId.toLowerCase().contains(config.spawnerBlockIdContains.toLowerCase())) {
            return; // not looking at a spawner
        }
        pagesDropped = 0;
        goTo(AutoFarmState.INTERACT_SPAWNER, 0);
    }

    private void handleInteractSpawner(Minecraft mc, LocalPlayer player) {
        HitResult target = mc.hitResult;
        if (!(target instanceof BlockHitResult blockHit)) {
            abortToIdle(mc, "lost sight of the spawner");
            return;
        }
        if (mc.gameMode != null) {
            mc.gameMode.useItemOn(player, InteractionHand.MAIN_HAND, blockHit);
            player.swing(InteractionHand.MAIN_HAND);
        }
        goToWithTimeout(AutoFarmState.WAIT_LOOT_SCREEN, config.actionDelayTicks, 30);
    }

    private void handleWaitLootScreen(Minecraft mc) {
        if (GuiUtils.currentScreenTitleContains(config.spawnerScreenTitleContains)) {
            goTo(AutoFarmState.DROP_PAGE, config.actionDelayTicks);
        }
    }

    private void clickSlot(LocalPlayer player, AbstractContainerMenu handler, int slot, ClickType type, int button) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.gameMode == null || handler == null) return;
        mc.gameMode.handleInventoryMouseClick(handler.containerId, slot, button, type, player);
    }

    private void handleDropPage(Minecraft mc, LocalPlayer player) {
        AbstractContainerMenu handler = GuiUtils.currentHandler();
        if (handler == null || !GuiUtils.currentScreenTitleContains(config.spawnerScreenTitleContains)) {
            abortToIdle(mc, "loot screen closed unexpectedly");
            return;
        }
        clickSlot(player, handler, config.dropSlotIndex, ClickType.PICKUP, 0);
        pagesDropped++;
        goTo(AutoFarmState.WAIT_AFTER_DROP, config.actionDelayTicks);
    }

    private void handleWaitAfterDrop() {
        if (pagesDropped >= config.pagesPerCycle) {
            goTo(AutoFarmState.CLOSE_LOOT_SCREEN, 0);
        } else {
            goTo(AutoFarmState.NEXT_PAGE, 0);
        }
    }

    private void handleNextPage(Minecraft mc, LocalPlayer player) {
        AbstractContainerMenu handler = GuiUtils.currentHandler();
        if (handler == null || !GuiUtils.currentScreenTitleContains(config.spawnerScreenTitleContains)) {
            abortToIdle(mc, "loot screen closed unexpectedly");
            return;
        }
        clickSlot(player, handler, config.nextSlotIndex, ClickType.PICKUP, 0);
        goTo(AutoFarmState.WAIT_AFTER_NEXT, config.actionDelayTicks);
    }

    private void handleCloseLootScreen(Minecraft mc, LocalPlayer player) {
        if (GuiUtils.hasScreenOpen()) {
            player.closeContainer();
        }
        goTo(AutoFarmState.WAIT_BEFORE_ORDER_COMMAND, config.postCloseWaitTicks);
    }

    private void handleSendOrderCommand(Minecraft mc, LocalPlayer player) {
        if (player.connection != null) {
            player.connection.sendCommand(config.orderCommand);
        }
        goToWithTimeout(AutoFarmState.WAIT_ORDER_SCREEN, config.actionDelayTicks * 2, 40);
    }

    private void handleWaitOrderScreen(Minecraft mc) {
        if (GuiUtils.currentScreenTitleContains(config.orderScreenTitleContains)) {
            goTo(AutoFarmState.SELL_ITEM, config.actionDelayTicks);
        }
    }

    private void handleSellItem(Minecraft mc, LocalPlayer player) {
        AbstractContainerMenu handler = GuiUtils.currentHandler();
        if (handler == null || !GuiUtils.currentScreenTitleContains(config.orderScreenTitleContains)) {
            goTo(AutoFarmState.OPEN_CONFIRM_SCREEN, 0);
            return;
        }
        int containerSize = handler.slots.size() - 36;
        int bonesSlot = GuiUtils.findPlayerInvSlotByItemId(handler, config.sellItemId, containerSize);
        if (bonesSlot == -1) {
            goTo(AutoFarmState.OPEN_CONFIRM_SCREEN, config.actionDelayTicks);
            return;
        }
        clickSlot(player, handler, bonesSlot, ClickType.QUICK_MOVE, 0);
        goTo(AutoFarmState.WAIT_AFTER_SELL_CLICK, config.actionDelayTicks);
    }

    private void handleWaitAfterSellClick(Minecraft mc) {
        goTo(AutoFarmState.SELL_ITEM, 0);
    }

    private void handleOpenConfirmScreen(Minecraft mc, LocalPlayer player) {
        if (GuiUtils.hasScreenOpen()) {
            player.closeContainer();
        }
        goToWithTimeout(AutoFarmState.WAIT_CONFIRM_SCREEN, config.postCloseWaitTicks, 40);
    }

    private void handleWaitConfirmScreen(Minecraft mc) {
        if (GuiUtils.currentScreenTitleContains(config.confirmScreenTitleContains)) {
            goTo(AutoFarmState.CLICK_CONFIRM, config.actionDelayTicks);
        }
    }

    private void handleClickConfirm(Minecraft mc, LocalPlayer player) {
        AbstractContainerMenu handler = GuiUtils.currentHandler();
        if (handler == null || !GuiUtils.currentScreenTitleContains(config.confirmScreenTitleContains)) {
            abortToIdle(mc, "confirm screen closed unexpectedly");
            return;
        }
        int containerSize = handler.slots.size() - 36;
        int confirmSlot = GuiUtils.findContainerSlotByItemId(handler, config.confirmButtonItemId, containerSize);
        if (confirmSlot == -1) {
            abortToIdle(mc, "couldn't find the confirm button (check confirmButtonItemId in config)");
            return;
        }
        clickSlot(player, handler, confirmSlot, ClickType.PICKUP, 0);
        goTo(AutoFarmState.WAIT_AFTER_CONFIRM, config.actionDelayTicks * 2);
    }

    private void handleWaitAfterConfirm(Minecraft mc) {
        AbstractContainerMenu handler = GuiUtils.currentHandler();
        boolean stillHaveBones = handler != null
                && !GuiUtils.playerInvHasNoneOf(handler, config.sellItemId, Math.max(0, handler.slots.size() - 36));
        if (stillHaveBones && GuiUtils.currentScreenTitleContains(config.orderScreenTitleContains)) {
            goTo(AutoFarmState.SELL_ITEM, config.actionDelayTicks);
        } else if (stillHaveBones) {
            goToWithTimeout(AutoFarmState.WAIT_ORDER_SCREEN, config.actionDelayTicks, 40);
        } else {
            goTo(AutoFarmState.CLOSE_ORDERS_FIRST_ESCAPE, 0);
        }
    }

    private void handleCloseFirstEscape(Minecraft mc, LocalPlayer player) {
        if (GuiUtils.hasScreenOpen()) {
            player.closeContainer();
        }
        goTo(AutoFarmState.WAIT_BETWEEN_ESCAPES, config.doubleEscapeGapTicks);
    }

    private void handleCloseSecondEscape(Minecraft mc, LocalPlayer player) {
        if (GuiUtils.hasScreenOpen()) {
            player.closeContainer();
        }
        goTo(AutoFarmState.WAIT_BEFORE_RESTART, config.actionDelayTicks);
    }
}
