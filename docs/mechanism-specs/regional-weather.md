# Regional weather and forecasts

## Behavior

Weather-capable dimensions use a deterministic schedule instead of Minecraft's random weather timers. The schedule uses saved game time, dimension, world seed, and region identity. Restarting, sleeping, and `/time set` do not reroll it. Time advances only while the server ticks; seconds below mean 20 game ticks. Vanilla `/weather` and `advanceWeather` no longer control these dimensions.

Outside configured regions, the default climate has ten-minute periods, 30% long-run precipitation frequency, 20% conditional thunderstorm probability, and no snow. Weather has temporal persistence: at each period boundary there is an 80% chance to retain the previous condition and a 20% chance to redraw from the regional climate. The redraw can select the same condition too. With the default climate, precipitation continues with 86% probability after a wet period, and clear weather continues with 94% probability after a clear period. Rain, snow, and storms retain their exact condition when the persistence branch is selected. Regional probabilities describe long-run frequencies, so persistence creates longer spells without making the climate wetter overall. Changes happen at period boundaries. Forecasts report the actual schedule, without uncertainty or random errors.

Regions are inclusive X/Z rectangles, independent of height and biome. Most recently configured regions win overlaps. Up to 128 regions are saved per dimension. A region's name supplies its independent deterministic seed; editing probabilities or duration changes its forecast immediately. Removing a region restores the next overlapping region or the default climate.

Rain and snow wet exposed track surfaces using the existing multi-minute moisture progression. Normal precipitation targets a damp/wet mixture; storms can reach soaking. Moisture history is saved separately from the forecast and starts dry when a region is first observed; creating or editing a region does not apply fictitious historical rain. Continuous rain keeps the same spatial thresholds across forecast periods. Natural wetting and drying use Gaussian transition times. Wetting keeps its existing mean at 50% of each stage’s duration, with a 15% standard deviation. Natural drying is centered on the nominal stage time instead of halfway through it: a fully damp surface dries with a mean of seven minutes and a standard deviation of about 63 seconds. Roughly half the blocks remain damp at seven minutes, with a late tail beyond the target; the target is a population mean, not a deadline. Independent, rotated Gaussian fields at 96-, 40-, and 12-block scales combine broad moisture patches with weaker medium and fine variation, avoiding a shared square grid while preserving Gaussian statistics; height does not add random variation. A bounded lazy sweep of loaded track chunks applies at most one stage per block update, revisiting chunks every second in interleaved 16-column batches when the work budget permits (2,048 columns and 256 block changes per tick). Larger circuits can take longer to sweep. Weather queues are reset before server chunks load, so startup-loaded chunks remain scheduled. Placement hooks register newly placed track chunks; regional random ticks neither change moisture nor decide transition times. Chunk-load catch-up and telemetry use the same time-based field. Normal rain takes four minutes to reach damp and twelve minutes to reach its 1.5-stage damp/wet target; storms take twenty-seven minutes to reach soaking. Sheltered blocks do not wet, and vehicle drying can still leave local differences, so precipitation forecasts do not promise exact grip. Regional ambient temperature targets are 36°C by day, 25°C at night, 20°C during rain, and -2°C during snow, retaining gradual cooling/warming. Vehicle tyre cooling samples the car's region.

Car-based drying remains immediate at tyre-contact positions, using tyre temperature, speed, slip/scrub, and the existing per-tick update budget. It does not use Gaussian deadlines, neighboring-block smoothing, or the natural-weather sweep. Natural drying preserves a car-dried line that is already below the weather target; subsequent rain can wet it again.

Snow renders and accumulates without a snowy biome. Accumulation respects valid supporting blocks, loaded chunks, and `maxSnowAccumulationHeight`; normal block-light melting still applies. Biome-driven water freezing is replaced along with the precipitation tick; the custom schedule does not create ice. Lightning is restricted to exposed positions in storm regions. Nether and End weather behavior is unchanged.

## Configuration and access

All players can run `/owrweather forecast` for their position and `/owrweather list` for climate definitions. Race-control and team monitor maps show the forecast at the circuit map's center, falling back to the viewer's position when no circuit is mapped. The display shows current weather, time to the next period, and the next three periods. Circuits spanning multiple regions can have different weather along their route.

Moderators configure the current dimension:

```
/owrweather set <name> <minX> <minZ> <maxX> <maxZ> <wetChance> <stormChance> <snowChance> <periodSeconds>
/owrweather remove <name>
```

Probabilities range from 0 to 1. `wetChance` controls the long-run precipitation share; persistent spells can last several periods. Given precipitation, `snowChance` is evaluated first, then `stormChance` for periods that are not snow. Periods range from 60 to 3600 seconds. `default` is a reserved name. Reusing a region name replaces it and moves it to highest overlap priority.

Examples:

```
/owrweather set desert -2000 -2000 2000 2000 0.05 0.1 0 600
/owrweather set alpine 3000 3000 6000 6000 0.65 0.15 0.7 300
/owrweather set snow_test 0 0 100 100 1 0 1 60
```

Settings persist in dimension SavedData and synchronize on login, respawn, dimension changes, and configuration edits. Clients evaluate the same schedule locally; they do not need the world seed separately. Client sky darkening, precipitation intensity, and thunder intensity fade over ten seconds when the camera enters a different region or the schedule changes. The renderer retains precipitation through fade-out; rain/snow type changes fade fully out, switch while invisible, then fade in. The nearby precipitation effect follows the camera’s blended presentation. These visual fades do not shift forecast boundaries, actual precipitation, or track-moisture timing. Visual state resets on disconnect or dimension changes.

## Validation

Unit tests cover forecast/live equality, climate extremes, independent regional variance, input validation, boundary times, moisture progression across weather periods, Gaussian transition-time statistics, adjacent-block correlation, and continuity across positive and negative chunk boundaries. The `natural_drying` in-world game test checks that loaded wet asphalt dries through the lazy sweep without tyre contact and that an already dry line stays dry. Run `./gradlew build` and `./gradlew runGameTestServer`. Visually verify rain/snow, boundary crossings, monitor forecast text, and snow accumulation in a warm biome with a client.
