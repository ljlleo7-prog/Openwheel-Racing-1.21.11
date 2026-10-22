# Track Surfaces Mechanism Spec

## Purpose

- What should make a purpose-built racing circuit better than vanilla terrain?

  Its pavement will reduce drag and increase grip, as grass has very bad drag and others have very bad grip

- Should track blocks be mostly visual, mostly mechanical, or both?

  Both

## Surface Types

For each surface, define visual role, driving effect, crafting cost, and future behavior.

| Surface | Visual Role | Driving Effect | Crafting Cost | Notes |
| --- | --- | --- | --- | --- |
| Asphalt track | Dark-color block feels like track | Low drag high grip | Mentioned a hundred times before |  |
| Kerb | red/white block | low drag low grip | Mentioned before |  |
| Barrier | Dark block with layer-like texture | Reduce damage of collision than other "rigid" blocks | mentioned before |  |
| Pit lane | subtly-Gridded block similar to track | Speed limit, high grip low drag though | Asphalt track + white coloring |  |
| Start/finish | Black-white grid with direction arrow | Timing | Asphalt track + quartz |  |
| Checkpoint | White line with some text-like texture on it with arrow | Check passing | Asphalt track + redstone |  |
| Sandstone | Sand-colored firm runoff/paved scenery | Concrete-like grip and drag; no loose-sand sinking | Vanilla sandstone | Useful for desert circuits such as Bahrain and Abu Dhabi |

## Driving Effects

- Should asphalt increase grip, reduce tyre wear, increase top speed, or all three?

  All 3

- Should kerbs help cornering, punish overuse, or only mark corners?

  Lower grip to punish overuse

- Should barriers damage cars, bounce cars, stop cars, or behave like normal blocks?

  Stop cars and prevent excessive damage (absorb energy)

- Should pit lane affect speed, repair, tyre changes, or refueling later?

  Limit speed just for now

## Building Experience

- How many blocks should a player need for a small circuit?

  at least 10*64 I believe

- Should track blocks be cheap enough for creative-like building in survival?

  Not really, sadly, but concrete is also acceptable as track blocks (we do that later)

- Should there be slabs, stairs, painted lines, or decorative variants?

  Not now

## Acceptance Criteria

- What must be true for the first track-building loop to feel complete?

## Loose Runoff Behavior

Sand and gravel retain low grip, but allow engine drive at all speeds. Sinking
resistance is small (sand 0.035, gravel 0.025, grass 0.02). Additional rolling
resistance and speed-dependent grain collision losses slow cars progressively,
with grain losses capped at 30 m/s. Grass also has reduced resistance so drivers
can recover from runoff. These losses oppose travel and cannot reverse the car.
Sand and gravel produce grounded hops below 0.1 blocks, spaced 9–15 ticks apart
and disabled below 2 m/s. Pitch rocks by up to 2.5 degrees with varied pulse
duration and strength. Per-car seeded variation keeps hops and pitch coordinated
without frame-to-frame randomness. Sand, grass, and gravel play vanilla footstep samples
at the left and right contacts while moving, including mixed-surface runoff.
