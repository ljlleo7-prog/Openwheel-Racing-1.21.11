# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project overview

Openwheel Racing is a Minecraft NeoForge mod for Minecraft `1.21.11` using NeoForge `21.11.44`, official Mojang mappings, and Java 21. The Gradle project name is `openwheel-racing`; the mod id is `openwheelracing`; the current Gradle version is `1.11.0`.

The mod implements an open-wheel racing loop: assembling and tuning prototype cars, custom liveries, driving/race control, track authoring, lap timing, setup systems, crash damage/tyre wear, crude oil/refining resources, and crafting progression.

## Common commands

```bash
./gradlew build                 # Compile Java, run checks/tests, process resources, assemble jar
./gradlew test                  # Run the standard JUnit test source set
./gradlew liveryPrototypeTest   # Run isolated livery PNG prototype tests
./gradlew --tests 'com.example.ClassName' test                 # Run one standard test class/method pattern
./gradlew --tests 'com.openwheelracing.livery.*' liveryPrototypeTest # Run matching livery prototype tests
./gradlew runClient             # Launch the NeoForge client in run/
./gradlew runServer             # Launch a dedicated server with --nogui
./gradlew runGameTestServer     # Launch NeoForge game test server
./gradlew runData               # Generate data into src/generated/resources
./gradlew exportPrototypeCarLiveryMask # Export build/livery/prototype_car_livery_mask.png
```

There is no separate lint task currently configured. `./gradlew build` is the main validation command and depends on `check`, which includes the isolated livery prototype test task.

`runClient` requires a working GUI display. In headless/remote sessions it can fail before mod loading with a GLFW display error such as `glfwGetPrimaryMonitor failed`; treat that as an environment/display issue unless logs show a later mod crash.

## Build and source sets

`build.gradle` applies `net.neoforged.moddev`, configures Java 21 toolchains for compile/test/exec tasks, and includes `src/generated/resources` in main resources.

Besides the main mod source set, the build defines:

- `src/liveryPrototype/java`: standalone livery mask/template renderer/exporter code.
- `src/liveryPrototypeTest/java`: JUnit tests for the isolated livery prototype source set.

NeoForge run configs share `run/` as the game directory except data generation, which uses `run-data/` and writes generated resources into `src/generated/resources`.

## Architecture

### Registration entry point

`src/main/java/com/openwheelracing/OpenwheelRacing.java` is the NeoForge mod entry point. It registers DeferredRegister-backed systems on the mod bus, including data components, entities, items, blocks, fluids, block entities, menus, recipes, sounds, and creative tabs.

It also registers common setup/network payload handlers, fuel handling, commands, and login synchronization hooks. Forge/NeoForge event-bus strict runtime checks are enabled in `build.gradle`, so register listeners on the correct bus; some client events use their static `BUS` fields rather than annotation-based auto-subscription.

### Registries

The `registry/` package owns content registration and should be updated whenever adding gameplay content:

- `OWRBlocks`, `OWRItems`, `OWRFluids`, `OWREntities`, `OWRBlockEntities`, `OWRMenus`, `OWRRecipes`, and `OWRSoundEvents` register concrete content.
- `OWRDataComponents` stores persistent item-side car setup, damage, tyre wear, livery, and related car state.
- `OWRCreativeTabs` controls creative tab visibility/order.
- `OWRFuelHandler` centralizes refinery/fuel burn-time behavior.

### Vehicle and race systems

`content/entity/OpenwheelCarEntity.java` owns most vehicle simulation and race interaction: acceleration/gearing, steering, surface grip/drag, tyre wear, damage, barrier impact reduction, lap/checkpoint handling, off-track invalidation, setup persistence, and item return behavior.

`content/entity/VehiclePhysics.java` contains the lower-level vehicle dynamics model used by the car entity. `content/car/PrototypeCarSetup.java` defines persistent setup values and setup multipliers. Current setup ranges are:

- Power mode: `0..3`
- Tyre compound index: `0..4` displayed as C1-C5
- Aero preset: `0..4`
- Gearing preset: `0..2`

Race-control state is saved server-side: `content/race/OWRRaceControlState.java` stores rule toggles, input allowances, ERS limits, flags, and damage/wear modifiers; `content/race/OWRLapRecords.java` stores sessions, lap records, invalidations, and best laps.

### Blocks, workstations, and menus

- `CarAssemblyWorkstationBlock` + `CarAssemblyWorkstationBlockEntity` + `CarAssemblyMenu` implement car construction/setup/livery workstation behavior. Workstation variants are selected through `CarWorkstationType`.
- `RaceDirectorBlock` + `RaceDirectorBlockEntity` + `RaceDirectorMenu` back the race director, race board, and team terminal UI variants.
- `RefineryBlock` + `RefineryBlockEntity` + `RefineryMenu` implement furnace-like oil refining with crude input, fuel input, and multiple output slots.
- `DirectionalTrackBlock` provides horizontal facing for directional track blocks.
- `LapMarkerBlock` extends the directional track block and calls car lap/checkpoint handlers using marker facing direction.
- `CrudeOilBlock` handles crude oil deposit behavior.

Directional blocks such as `kerb`, `checkpoint`, and `start_finish` have `facing=north/east/south/west` blockstate variants. Source textures should be drawn north-facing; blockstates rotate the model for other directions.

### Track editor and stewarding geometry

Track authoring code lives under `content/track/`. `TrackEditorPlacementService` handles guarded batch placement and undo limits for the in-game track editor. `TrackDefinitionsData`, `TrackDefinition`, `TrackGeometry`, and `TrackStewardingGeometryBuilder` persist and derive stewarding geometry.

`content/command/OWRCommands.java` registers `/owr` commands for wheel-input regulation and stewarding workflows such as track metadata, start/finish, checkpoints, grid slots, boundaries, and AI line points.

### Client systems

Client-only code is under `client/`:

- `OpenwheelRacingClient` registers screens, entity rendering, overlays, render layers, HUD hooks, client tick input hooks, and client-side payload handlers.
- `OpenwheelRacingClientEvents` handles key mapping registration.
- `client/input/` handles keyboard and wheel input settings/state.
- `client/hud/` renders car HUD, race flags, and lap/ranking feedback.
- `client/screen/` contains workstation, livery editor, race director, track editor, wheel setup, and setup screens.
- `client/render/` and `client/livery/` render the prototype car and dynamic livery textures/previews.
- `client/sound/` manages engine and tyre sound instances/physics.
- `mixin/client/` plus `src/main/resources/openwheelracing.mixins.json` customize camera/client behavior.

### Livery systems

Persistent livery data is item-component based. Server/common data structures live in `content/car/CarLivery.java`, `CarLiveryColors.java`, and `CarLiveryTexture.java`; component registration lives in `OWRDataComponents`.

Client-side livery editing and preview logic lives in `client/livery/` and `client/screen/LiveryEditorScreen.java`. Runtime custom livery PNGs are dynamic textures stored under the client game directory at `openwheelracing/liveries`.

The isolated livery prototype source set is used to generate and test livery masks/templates without loading Minecraft classes. Use `exportPrototypeCarLiveryMask` and `liveryPrototypeTest` when changing that renderer.

### Networking

`network/OWRNetwork.java` uses NeoForge `RegisterPayloadHandlersEvent` / `PayloadRegistrar` rather than the older SimpleChannel pattern. Keep the protocol version and payload registrations in sync when adding packets.

Existing payloads cover setup tuning/repair/livery changes, custom livery metadata and image chunks, drive input, ABS/TC/DRS/ERS controls, mounting, track editor placement/undo, race director actions, race snapshots, flag state, and ranking board updates.

### Resources and data

Resources live in `src/main/resources`:

- `assets/openwheelracing/blockstates/` maps block states to models.
- `assets/openwheelracing/models/block/` and `models/item/` define rendering models.
- `assets/openwheelracing/textures/` contains block/item/gui/entity textures.
- `assets/openwheelracing/lang/en_us.json` and `zh_cn.json` should stay key-aligned.
- `data/openwheelracing/recipe/` contains vanilla and custom recipes.
- `data/openwheelracing/loot_table/blocks/` contains block drops.
- `data/openwheelracing/worldgen/`, `data/forge/biome_modifier/`, and biome tags define crude oil generation.

`docs/mechanism-specs/` contains gameplay mechanism specs. Check these before changing mechanics, balance, or progression. `docs/asset-checklist.md` documents expected asset files, GUI coordinates, and model texture bindings. `docs/development-roadmap.md` captures roadmap/baseline context.

## Localization

When adding a translatable name, tooltip, container title, key binding, or UI label, update both:

```text
src/main/resources/assets/openwheelracing/lang/en_us.json
src/main/resources/assets/openwheelracing/lang/zh_cn.json
```

Keep the key sets identical. A quick parity check:

```bash
python3 - <<'PY'
import json
from pathlib import Path
base = Path('src/main/resources/assets/openwheelracing/lang')
en = json.loads((base / 'en_us.json').read_text())
zh = json.loads((base / 'zh_cn.json').read_text())
print('missing zh', sorted(set(en) - set(zh)))
print('extra zh', sorted(set(zh) - set(en)))
PY
```

## Asset/model notes

Block item models generally inherit their block models so block items render as 3D block views. If a block appears with the wrong texture in-game, first check its `models/block/*.json` texture bindings, then its blockstate variant rotations.

For directional top textures such as checkpoint/start-finish arrows or kerb stripe orientation, draw the texture in the north-facing default orientation and rely on blockstate `y` rotations.

## Generated and runtime files

Gradle run outputs and Minecraft runtime logs are under `run/`; data-generation runtime files are under `run-data/`. These are noisy and commonly modified by launching the client/server/data generator.

Generated data output goes to `src/generated/resources` when using `./gradlew runData`.
