package com.autofarm;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * All the tunables live here. Edit them in-game via AutoFarmConfigScreen
 * (default keybind: O) or by hand in config/autofarm.json.
 *
 * The slot indices below are 0-indexed positions inside the *container* part
 * of whatever screen is open (i.e. NOT the player's own inventory - those are
 * higher indices and we never hardcode those, we search for them by item id).
 *
 * From what you described, the spawner loot screen is 5 rows x 9 columns = 45
 * container slots (0-44), and the bottom row is slots 36-44. You said, counting
 * left-to-right in that bottom row starting at 1: slot 4 = back, slot 6 = next
 * page, slot 8 = drop dispenser. That maps to raw indices 39 / 41 / 43 below.
 * If dropping/paging doesn't hit the right button, adjust these three numbers
 * first - that's the most likely thing to be off.
 */
public class AutoFarmConfig {

    // ---- Master switch ----
    public boolean enabled = false;

    // ---- How much to collect per spawner visit ----
    /** Number of pages to drop before moving on to selling. */
    public int pagesPerCycle = 5;

    // ---- Spawner loot screen ----
    /** Substring (case-insensitive) that identifies the spawner loot screen title. */
    public String spawnerScreenTitleContains = "Spawners";
    /** Substring in the block id you're allowed to auto-interact with. */
    public String spawnerBlockIdContains = "spawner";
    /** Raw container slot index of the "back a page" button. */
    public int backSlotIndex = 39;
    /** Raw container slot index of the "next page" button. */
    public int nextSlotIndex = 41;
    /** Raw container slot index of the "drop this page" dispenser/dropper button. */
    public int dropSlotIndex = 43;

    // ---- Selling / orders ----
    /** Chat command run (without leading slash) to open the order/sell menu. */
    public String orderCommand = "order bone";
    /** Substring identifying the order browse screen title. */
    public String orderScreenTitleContains = "Orders";
    /** Substring identifying the confirm-delivery screen title. */
    public String confirmScreenTitleContains = "Confirm Delivery";
    /** Item id (or substring of it) that counts as "the thing we're selling". */
    public String sellItemId = "minecraft:bone";
    /** Item id substring used to find the green "confirm" pane on the confirm screen. */
    public String confirmButtonItemId = "lime_stained_glass_pane";
    /** Raw container slot index of the top ("best paying") order slot to shift-click into. */
    public int bestOrderSlotIndex = 0;

    // ---- Timing (in client ticks, 20 ticks = 1 second) ----
    public int actionDelayTicks = 4;
    public int postCloseWaitTicks = 40;
    public int doubleEscapeGapTicks = 40;

    // ---- Keybinds are stored by GLFW name in AutoFarmMod, not here ----

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public static Path configPath() {
        return FabricLoader.getInstance().getConfigDir().resolve("autofarm.json");
    }

    public static AutoFarmConfig loadOrCreate() {
        Path path = configPath();
        if (Files.exists(path)) {
            try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
                AutoFarmConfig loaded = GSON.fromJson(reader, AutoFarmConfig.class);
                if (loaded != null) {
                    return loaded;
                }
            } catch (IOException e) {
                AutoFarmMod.LOGGER.warn("Failed to read autofarm.json, using defaults", e);
            }
        }
        AutoFarmConfig fresh = new AutoFarmConfig();
        fresh.save();
        return fresh;
    }

    public void save() {
        Path path = configPath();
        try {
            Files.createDirectories(path.getParent());
            try (Writer writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
                GSON.toJson(this, writer);
            }
        } catch (IOException e) {
            AutoFarmMod.LOGGER.warn("Failed to save autofarm.json", e);
        }
    }
}
