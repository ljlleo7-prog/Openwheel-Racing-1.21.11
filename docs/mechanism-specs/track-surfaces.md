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
