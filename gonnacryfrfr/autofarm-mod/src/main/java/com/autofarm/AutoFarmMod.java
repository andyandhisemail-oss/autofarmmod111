package com.autofarm;

import com.autofarm.gui.AutoFarmConfigScreen;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Written against Mojang's official mappings (Minecraft 26.1+).
 *
 * NOTE ON UNCERTAINTY: the vanilla class names here (Minecraft, KeyMapping,
 * Component, InputConstants) are long-standing official Mojang names I'm
 * confident in. The two Fabric-API-specific names - the ClientTickEvents
 * package path and KeyMappingHelper's new package
 * (net.fabricmc.fabric.api.client.keymapping.v1) - are confirmed against
 * Fabric's own docs as of when this was written (docs.fabricmc.net). If
 * either import fails to resolve, that's the most likely reason: Fabric API
 * moved something again since. Check
 * https://docs.fabricmc.net/develop/porting/fabric-api for the current name.
 */
public class AutoFarmMod implements ClientModInitializer {

    public static final String MOD_ID = "autofarm";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    private static AutoFarmConfig config;
    private static AutoFarmEngine engine;

    private KeyMapping toggleKey;
    private KeyMapping configKey;

    @Override
    public void onInitializeClient() {
        config = AutoFarmConfig.loadOrCreate();
        engine = new AutoFarmEngine(config);

        toggleKey = KeyMappingHelper.registerKeyBinding(new KeyMapping(
                "key.autofarm.toggle",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_P,
                "category.autofarm.general"
        ));

        configKey = KeyMappingHelper.registerKeyBinding(new KeyMapping(
                "key.autofarm.config",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_O,
                "category.autofarm.general"
        ));

        ClientTickEvents.END_CLIENT_TICK.register(this::onClientTick);

        LOGGER.info("AutoFarm loaded. Default keys: P = toggle run/stop, O = open settings.");
    }

    private void onClientTick(Minecraft client) {
        while (configKey.consumeClick()) {
            if (client.screen == null) {
                client.setScreen(new AutoFarmConfigScreen(null, config));
            }
        }

        while (toggleKey.consumeClick()) {
            config.enabled = !config.enabled;
            config.save();
            if (!config.enabled) {
                engine.stop();
            }
            if (client.player != null) {
                client.player.sendSystemMessage(
                        Component.literal("AutoFarm " + (config.enabled ? "started. Look at a spawner." : "stopped.")));
            }
        }

        if (client.player != null && client.level != null) {
            engine.tick(client);
        } else if (config.enabled) {
            engine.stop();
        }
    }

    public static AutoFarmConfig getConfig() {
        return config;
    }

    public static AutoFarmEngine getEngine() {
        return engine;
    }
}
