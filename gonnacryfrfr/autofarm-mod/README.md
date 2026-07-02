# AutoFarm (Fabric, Minecraft 26.1.2)

Client-side mod: right-click a spawner, drop N pages of loot, sell everything
to the highest-paying order, repeat — until you press the toggle key again.

## Important: why your first attempt failed

The "Invalid input: Unable to infer project type for input file" error you
saw in the Modrinth app happened because `autofarm-mod.zip` is **source
code**, not a mod. Modrinth (and Minecraft itself) can only load compiled
`.jar` files. The zip has to be *built* first - that's what the steps below
do. Don't drag this zip into your mods folder or the Modrinth app directly.

Also: your instance is running **Minecraft 26.1.2**, not "1.21.x". Mojang
switched to year-based version numbers in 2026, and 26.1 was a huge deal for
modding - it's the first release that ships unobfuscated, requires **Java
25**, and Fabric dropped Yarn mappings for Mojang's official ones. I rebuilt
this whole project against that (confirmed against Fabric's own
`fabric-example-mod` repo, `26.1.2` branch) - the version I gave you
initially was written for the old `1.21.x` toolchain and wouldn't have
compiled at all.

## Easiest way to build it (no Java install needed)

This project includes `.github/workflows/build.yml`, which builds the jar for
you automatically using GitHub Actions (free).

1. Make a free GitHub account if you don't have one: https://github.com/join
2. Create a new repository (any name, e.g. `autofarm-mod`).
3. On the repo's "Add file" → "Upload files" page, drag in **everything from
   this folder** (keep the folder structure).
4. Commit the upload - this triggers the build automatically.
5. Go to the **Actions** tab → open the run → wait for it to finish → under
   "Artifacts" download `autofarm-mod-jar`. Unzip that download to get
   `autofarm-*.jar`.
6. Put that jar in your instance's `mods/` folder, alongside Fabric API
   (make sure the Fabric API version matches what's in `gradle.properties`).

**One thing you need to fix before this will build**: open
`gradle.properties` and set `fabric_api_version` to the real current value.
Go to https://fabricmc.net/develop, pick Minecraft version `26.1.2`, and
copy the "Fabric API" version string shown there - I couldn't reach that
page from here to confirm the exact build number.

If the build still fails, click into the failed step's log in the Actions
tab and paste it back to me - between the version rename and Mojang mappings
switch, some Fabric API class name may have moved since I wrote this, and an
exact compiler error is far more reliable for me to fix than guessing again.

## Building locally instead (if you'd rather)

1. Install JDK 25.
2. Grab `gradlew` / `gradlew.bat` / the `gradle/` wrapper folder from
   https://github.com/FabricMC/fabric-example-mod (26.1.2 branch) and copy
   them into this project folder (don't overwrite `build.gradle`,
   `gradle.properties`, or `settings.gradle` - those are already set up).
3. `./gradlew build` (or `gradlew.bat build` on Windows).
4. Jar comes out at `build/libs/autofarm-1.0.0.jar`.

## Controls

- **P** — start/stop the whole loop
- **O** — open the settings screen

## How it works

`AutoFarmEngine` is a tick-based state machine (`AutoFarmState`). Every state
either waits out a short delay or performs exactly one simulated click/command
using the same client packets a real click sends - nothing here reads memory
or talks to the server outside normal gameplay packets.

Loop summary:
1. **Idle** — waits until you're looking at a block whose id contains
   `spawnerBlockIdContains` (default `"spawner"`).
2. Right-clicks it, waits for a screen titled like `spawnerScreenTitleContains`
   (default `"Spawners"`).
3. Clicks `dropSlotIndex` to drop a page, `nextSlotIndex` to advance, repeating
   until `pagesPerCycle` pages have dropped.
4. Closes the screen, waits `postCloseWaitTicks`, runs `/<orderCommand>`
   (default `order bone`).
5. On the resulting screen, shift-clicks every stack of `sellItemId` in your
   inventory into the top order slot (highest payer) until you're out.
6. Closes to the confirm screen (matched by `confirmScreenTitleContains`),
   clicks whatever slot contains `confirmButtonItemId` (default a lime glass
   pane).
7. If your inventory refilled with more of the sell item, loops back to step 5.
   Otherwise, closes out twice and goes back to step 1.

## Things most likely to need tweaking

Open the config screen (**O**) or edit `config/autofarm.json` directly:

| Field | What it is | Default |
|---|---|---|
| `pagesPerCycle` | how many pages to drop before going to sell | `5` |
| `backSlotIndex` / `nextSlotIndex` / `dropSlotIndex` | raw container slot numbers (0-indexed) in the loot screen | `39` / `41` / `43` |
| `orderCommand` | chat command run to open the sell menu, no leading `/` | `order bone` |
| `sellItemId` | item id (or substring) counted as sellable | `minecraft:bone` |
| `confirmButtonItemId` | item id substring used to find the green confirm pane | `lime_stained_glass_pane` |
| `spawnerScreenTitleContains` / `orderScreenTitleContains` / `confirmScreenTitleContains` | substrings used to detect which screen is open | `Spawners` / `Orders` / `Confirm Delivery` |

If clicks land on the wrong button, the slot indices are the first thing to
check. If a step seems to hang, check chat - the mod logs why it gave up
(e.g. "timed out waiting for a screen") rather than looping forever.

## A note on server rules

This automates clicking through GUIs with no player input once started. You
mentioned your server explicitly allows this - worth double-checking that
holds for however this "competition" is scored too, since a rule that's fine
for casual farming might read differently for a competitive leaderboard.
