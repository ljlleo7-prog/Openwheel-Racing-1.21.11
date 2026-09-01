# Rubber and Tyres Mechanism Spec

## Purpose

- Should rubber primarily support tyres, track parts, seals, belts, or other racing components?

  Tyres and barriers currently

- Should tyres be consumable, repairable, upgradeable, or permanent?

  Consumable, not repairable, not upgradeable, but can decompose into equivalent amount of rubber

## Rubber Source

- Does rubber come from crude oil refining, trees, both, or another process?

  Refinement, you can choose a specific type of tree to make it occasionally drop crude rubber

- Should natural rubber exist separately from synthetic rubber?

  Not for now

- How early should players access rubber?

  Early-mid I believe, early relative to all other components

## Tyre Crafting

- What ingredients make tyres?

  Iron and rubber, rubber more

- How many tyres should one car require?

  4

- Should different tyre compounds exist?

  Yes, but I will specify later, maybe we can add a tyre refinery station for specifying tyre compound

- Should wet/dry/off-road tyres exist later?

  Yes

## Tyre Wear

- How should tyre wear affect grip, braking, and steering?

  Lost grip and increase drag as it wears

- Can players repair worn tyres?

  Not 100% worn: it will just drop into equivalent rubber

- Does tyre wear persist when the car is converted back to an item?

  Yes

- Should kerbs, crashes, rough terrain, or high steering increase wear?

  Yes

## Feedback

- How should the player see tyre condition?

  HUD will display, and player might observe speed decline

- Should there be warning sounds/messages for low tyre condition?

  Yes under 30% health

## Pit-Lane Tyre Changes

- A crew player starts by right-clicking a stationary car on a pit-stop mark with a racing jack. The car rises visually and is immobilized until it is lowered; a driver may remain seated.
- Once raised, the technician right-clicks with an empty hand to unscrew and remove the old tyres, right-clicks with the new tyre set to install it, then right-clicks with the jack to lower the car.
- Each physical operation independently takes 6-10 ticks. The jack and tyre item cooldowns, technician action-bar percentage, and driver HUD show progress and the required next operation.
- Correct next inputs during the final two ticks are buffered. Starting earlier interrupts unfinished work and rolls the current operation back by two ticks once per phase; invalid-sequence inputs are rejected without a griefable penalty.
- The new tyre set is reserved when installation starts. Compound, type, wear, and temperatures apply only when installation completes. The removed tyre set retains its remaining life and returns to the technician after lowering, or drops beside the car if it cannot be returned.
- Service state persists across chunk/world reloads, and another technician can take over a waiting operation if the original technician disconnects.
- Any player can deliberately crouch-right-click with an empty hand to perform an emergency jack release. It refunds whichever tyre set is not fitted and lets an empty car be picked up immediately, guaranteeing that no technician UUID or missing tool can deadlock the car.

## Acceptance Criteria

- What is the minimum tyre/rubber loop for the next prototype?
