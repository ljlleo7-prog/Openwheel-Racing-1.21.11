# Openwheel Racing

Openwheel Racing is a Minecraft NeoForge mod for Minecraft 1.21.11 that adds an open-wheel racing progression loop: craft and assemble a prototype race car, tune its setup, build circuits, manage race markers, refine crude oil resources, and race laps with timing, tyre wear, crash damage, and surface-dependent grip.

## Requirements

- Java 21
- Minecraft 1.21.11
- NeoForge 21.11.44 or later

## Playing the mod

### Quick start

1. Craft a car and its components with the car workstations, or take a **Prototype Car Spawn** item from the creative inventory while testing.
2. Use the car item on the ground to place it. The car faces the same direction as the player.
3. Right-click the empty car, or stand near it and press `G`, to take the driver's seat.
4. Select first gear with `I`, then use `W`, `A`, `S`, and `D` to drive. The gearbox is manual: press `I` to shift up and `K` to shift down.
5. Press `R` to leave the car. To recover a parked car as an item, leave it empty and sneak-right-click it with an empty hand; its setup, condition, tyres, livery, and ERS state are preserved.

All bindings can be changed under **Options > Controls > Openwheel Racing**. Steering-wheel input is also supported through the mod's wheel settings.

### Default driving controls

| Control | Key | Description |
| --- | --- | --- |
| Throttle | `W` | Apply power |
| Brake | `S` | Brake the car |
| Steer left/right | `A` / `D` | Steer; response changes with speed |
| Shift up/down | `I` / `K` | Select the next or previous gear |
| Mount car | `G` | Enter a nearby empty car |
| Exit car | `R` | Leave the driver's seat |
| Toggle ABS | `V` | Enable or disable anti-lock braking |
| Toggle traction control | `C` | Enable or disable traction control |
| Toggle DRS | `0` | Open or close DRS when permitted |
| Previous/next ERS mode | `J` / `L` | Cycle through Harvest, Balanced, and Attack |
| Track editor | `B` | Open the in-game circuit editing tools |

The in-car HUD shows speed, gear, RPM, lap and split information, tyre condition, damage, assists, DRS, and ERS status. ERS harvests energy under braking and deploys it under throttle according to the selected mode. DRS is subject to the current race and track conditions.

### Building and driving a timed circuit

Build the racing surface from asphalt, kerbs, pit-lane blocks, and barriers. Add one start/finish marker and place checkpoint markers around the lap in driving order. Cross the start/finish line to begin a lap, pass every checkpoint in order, and cross the line again to record the time. The HUD and action-bar messages report lap starts, checkpoints, splits, invalid laps, and completed times.

A lap can be rejected for skipping checkpoints, going the wrong way, leaving the valid track surface when off-track enforcement is enabled, or falling below the Race Director's minimum lap time. The `B` track editor provides the fuller circuit-definition workflow, including the centerline, start/finish, checkpoints, grid positions, boundaries, racing line, and route survey.

### Pit stops, setup, and repairs

Place pit-lane blocks and a **Pit Stop Mark** in the pit box. Stop the car on the mark, then sneak-right-click while seated to request service. Pit service requires rubber in the driver's inventory and completes after the service timer. A player outside the car can also use a tyre item on a stopped car at the pit mark to change its tyres.

Use the setup, livery, and parts-replacement workstations to tune or maintain a recovered car item. Driving wears the tyres, impacts damage individual components, and both affect performance, so recover the car and service it between runs when necessary.

### Monitoring and controlling a race

Place a monitor block and right-click it with an empty hand to open its screen:

- **Race Director** is the race-control view. It manages sessions, lap limits, checkpoint and off-track rules, the minimum valid lap time, global flags, condition modifiers, ERS limits, lap invalidation, track-map detection, live classification, and car telemetry.
- **Race Board Terminal** is the timing-board view for live classification, recent and archived laps, lap details, and the circuit map.
- **Team Terminal** monitors assigned cars. Bind cars to its left and right slots, select a car, and inspect its live telemetry and condition.

For a typical event, place a Race Director near the circuit, use **Auto-Detect Map** after the track markers are ready, create a named session, configure the rules and lap limit, then switch to the live view to watch positions and timing. Select a listed car to subscribe to its telemetry. Place Race Board Terminals where other players need a read-only timing view, and Team Terminals in garages or pit walls.

Operators can inspect or recover the live timing service with:

```text
/owr race timing status
/owr race timing stop
/owr race timing resume
```

The Gradle wrapper is included, so a separate Gradle installation is not required.

## Getting started

Build the mod jar:

```bash
./gradlew build
```

Run a local Minecraft client:

```bash
./gradlew runClient
```

Run a dedicated server:

```bash
./gradlew runServer
```

Run the Forge game test server:

```bash
./gradlew runGameTestServer
```

Run data generation:

```bash
./gradlew runData
```

The built jar is generated under `build/libs/`.

## Features

- Prototype open-wheel car entity with acceleration, gearing, steering, tyre wear, damage, barrier impacts, and persistence back to the car item.
- Car assembly workstation for custom car assembly, setup tuning, and repairs.
- Race director block plus racing surfaces including asphalt, pit lane, pit stop marks, kerbs, checkpoints, barriers, and start/finish markers.
- Lap timing, checkpoint validation, off-track invalidation, pit lane support, and in-car HUD feedback.
- Crude oil deposits, refinery processing, fuel items, and crafting progression resources.
- Custom renderer, workstation screens, recipes, loot tables, world generation, sounds, and English/Chinese localization.

## Project layout

```text
src/main/java/com/openwheelracing/   Java source code
src/main/resources/                  Mod assets, recipes, loot tables, worldgen, and metadata
src/generated/resources/             Generated data output
docs/mechanism-specs/                Gameplay mechanism design notes
docs/asset-checklist.md              Asset and model checklist
run/                                 Local Minecraft runtime directory
```

## Development notes

- `./gradlew build` is the main validation command.
- `runClient` requires a working GUI display. In headless environments it may fail before mod loading with a GLFW display error.
- Forge event-bus strict runtime checks are enabled, so register listeners on the correct bus.
- When adding localized names, tooltips, containers, or key bindings, update both `en_us.json` and `zh_cn.json`.
- Directional block textures should be authored in their north-facing orientation; blockstates rotate other directions.
- Check `docs/mechanism-specs/` before changing gameplay mechanics, balance, or progression.

## Version

Current project version: `1.16.2`.

## License

See the license files in this repository for details.

The car model was derived from *NEW F1 CAR 2026* (2026) by Abu Saif, licensed under [Creative Commons Attribution 4.0](http://creativecommons.org/licenses/by/4.0/). This mod rotates the model and remeshes it to 10% of its original density.
