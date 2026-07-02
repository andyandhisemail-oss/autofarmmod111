package com.autofarm.gui;

import com.autofarm.AutoFarmConfig;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Simple, dependency-free config screen (no ModMenu needed). Open with the
 * configured keybind (default: O). Press "Save & Close" to persist changes
 * to config/autofarm.json.
 *
 * Written against Mojang's official mappings (Minecraft 26.1+): Screen,
 * EditBox (was TextFieldWidget), Button (was ButtonWidget), GuiGraphics
 * (was DrawContext) are all real official names.
 */
public class AutoFarmConfigScreen extends Screen {

    private final Screen parent;
    private final AutoFarmConfig config;

    private EditBox pagesPerCycleField;
    private EditBox orderCommandField;
    private EditBox sellItemIdField;
    private EditBox backSlotField;
    private EditBox nextSlotField;
    private EditBox dropSlotField;

    public AutoFarmConfigScreen(Screen parent, AutoFarmConfig config) {
        super(Component.literal("AutoFarm Settings"));
        this.parent = parent;
        this.config = config;
    }

    @Override
    protected void init() {
        int cx = this.width / 2;
        int y = this.height / 2 - 90;
        int fieldWidth = 160;

        pagesPerCycleField = addField(cx, y, fieldWidth, "Pages per cycle", String.valueOf(config.pagesPerCycle));
        y += 24;
        orderCommandField = addField(cx, y, fieldWidth, "Order command (no /)", config.orderCommand);
        y += 24;
        sellItemIdField = addField(cx, y, fieldWidth, "Sell item id", config.sellItemId);
        y += 24;
        backSlotField = addField(cx, y, fieldWidth, "Back page slot #", String.valueOf(config.backSlotIndex));
        y += 24;
        nextSlotField = addField(cx, y, fieldWidth, "Next page slot #", String.valueOf(config.nextSlotIndex));
        y += 24;
        dropSlotField = addField(cx, y, fieldWidth, "Drop button slot #", String.valueOf(config.dropSlotIndex));
        y += 32;

        this.addRenderableWidget(Button.builder(Component.literal("Save & Close"), b -> saveAndClose())
                .bounds(cx - fieldWidth / 2, y, fieldWidth, 20)
                .build());
        y += 24;
        this.addRenderableWidget(Button.builder(Component.literal("Cancel"), b -> onClose())
                .bounds(cx - fieldWidth / 2, y, fieldWidth, 20)
                .build());
    }

    private EditBox addField(int cx, int y, int width, String label, String initial) {
        EditBox field = new EditBox(this.font, cx - width / 2, y, width, 18, Component.literal(label));
        field.setMaxLength(64);
        field.setValue(initial);
        this.addRenderableWidget(field);
        return field;
    }

    private void saveAndClose() {
        try {
            config.pagesPerCycle = Integer.parseInt(pagesPerCycleField.getValue().trim());
        } catch (NumberFormatException ignored) {}
        config.orderCommand = orderCommandField.getValue().trim();
        config.sellItemId = sellItemIdField.getValue().trim();
        try {
            config.backSlotIndex = Integer.parseInt(backSlotField.getValue().trim());
        } catch (NumberFormatException ignored) {}
        try {
            config.nextSlotIndex = Integer.parseInt(nextSlotField.getValue().trim());
        } catch (NumberFormatException ignored) {}
        try {
            config.dropSlotIndex = Integer.parseInt(dropSlotField.getValue().trim());
        } catch (NumberFormatException ignored) {}
        config.save();
        onClose();
    }

    @Override
    public void onClose() {
        if (this.minecraft != null) {
            this.minecraft.setScreen(parent);
        }
    }

    @Override
    public void render(GuiGraphics context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
        context.drawCenteredString(this.font, this.title, this.width / 2, this.height / 2 - 110, 0xFFFFFF);
        String label = config.enabled
                ? "Currently: RUNNING (toggle key to stop)"
                : "Currently: STOPPED (toggle key to start)";
        context.drawCenteredString(this.font, Component.literal(label), this.width / 2, this.height / 2 + 96,
                config.enabled ? 0x55FF55 : 0xFF5555);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
