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

- A crew player starts a tyre change by right-clicking a stationary car on a pit-stop mark while holding a tyre set. A driver may remain seated.
- The tyre set is reserved when service starts; the car cannot drive or begin another service during the five-second change.
- Service progresses through visible stages: jacking the car, removing old tyres, installing new tyres, and securing wheels.
- The driver sees the current stage and countdown on the HUD. The crew player receives the same stage messages.
- New compound, type, wear, and temperatures apply only when installation completes. The removed tyre set, including its remaining life, is returned to the crew or dropped beside the car if it cannot be returned.

## Acceptance Criteria

- What is the minimum tyre/rubber loop for the next prototype?
