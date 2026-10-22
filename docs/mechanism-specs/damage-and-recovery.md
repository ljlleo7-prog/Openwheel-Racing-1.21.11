# Damage and Recovery Mechanism Spec

## Purpose

- What should crash damage add to gameplay?

  Avoid players aggressively driving and mimic risk tradeoff

- Should damage be a light penalty, a serious repair loop, or a race-ending threat?

  A percentage harm on the car and potential race-ending threat, also harms driver

## Damage Sources

- What should damage the car?

  High-speed collision with walls, high distance drops, and high-relative-speed collision with other entities. Fire, explosion and mobs also do damage.

## Effects

- How should damage affect speed, acceleration, steering, braking, and tyre wear?

  Impact car and driver HP, speed damage linearly (just for now)

- Should damage have thresholds with warnings or visible effects?

  Yes, a visible car damage percentage sign and hurting effect

- Should the car be destroyed at 100% damage?

  Yes, dropping into the raw materials they are composed of

## Repair and Recovery

- How can the player recover a damaged car?

  Workstation repair and pit stop repair, require some raw materials

## Feedback

- What sounds/particles/messages should crashes produce?

  explosion effects basically

- Should damaged cars render differently?

  Show it cracked effects

- Should the HUD warn at specific thresholds?

  Yes

## Acceptance Criteria

- What is the minimum satisfying damage/recovery loop?

  Different collision observe different harms, and also car repairing and full damage observed

## Marshal Car Dragger

The reusable `openwheelracing:car_dragger` moves the existing car entity and its seated driver
as one assembly. It preserves damage, setup, livery, inventory and passenger identity; it does
not package an occupied vehicle into an item. Safety cars inherit the same recovery behavior.

- Right-click a nearby, nearly stationary car on foot to attach. Active AI and tyre service must
  be stopped first. Each marshal holds one car; each car has one owner.
- Walk and look to position the car at a point 4.5 blocks ahead. The target height is limited
  to 0.5–3 blocks above the marshal's feet. Motion converges with a maximum speed of 0.25
  blocks per tick and four collision checks per movement.
- Sneak + use rotates the target heading by 45 degrees. The car turns at most 5 degrees per
  tick, checking its body footprint and passenger clearance before rotation or translation.
  Existing wall overlap may shrink or slide, but each collision box's overlap volume must
  not increase. New obstacles still block movement. Blocked diagonal movement falls back
  to horizontal sliding followed by lifting so a crashed car can be extracted. The seated
  driver's feet below the chassis are excluded from road-clearance checks.
- Use again to lower vertically at 0.08 blocks per tick until obstructed by the ground.
  Normal driving resumes with zero recovery velocity. There is no throwing impulse.
- Losing the tool, death, disconnecting, dimension changes or separation beyond 12 blocks
  automatically starts lowering. Unloaded chunks and the world border block movement.
  A suspended car saved and reloaded resumes lowering without retaining player ownership.
- Recovery bypasses drivetrain, impact processing and race marker detection, invalidates the
  current lap, and suspends client driving prediction. Authoritative position snapshots keep
  the seated driver aligned with the carried car. Other damage sources still apply normally.
- A particle tether shows the attachment. Tooltips and status messages are localized in English
  and Chinese. The creative tab includes the tool; crafting uses two iron ingots, racing
  electronics and a jack, arranged `I I /  E  /  J `.

The collision envelope is conservative: it uses the car's rotated footprint bounds plus rider
bounds, so tight diagonal gaps can block movement even if the visible model appears to fit.
In-game acceptance checks: occupied recovery with two clients, blocked walls and low ceilings,
rotation near a barrier, release on slabs, switching tools, disconnect/rejoin while suspended,
and crossing timing lines while carried without recording a valid lap.

Automated empty-car coverage runs through `./gradlew runGameTestServer`: both
`openwheelracing:empty_car_recovery` and `openwheelracing:empty_safety_car_recovery`
exercise the real right-click interaction, verify the marshal does not board during attachment,
advance server ticks to verify lift and transport without passengers, release through the item,
and check ground contact, preserved entity identity, zero release impulse and subsequent boarding.

Wall-extraction tests `openwheelracing:stuck_car_recovery` and
`openwheelracing:occupied_stuck_car_recovery` start with the car overlapping a tall wall,
pull it free, verify lifting and retention of the seated driver, then check that a fresh wall
still stops the car and that lowering completes. These run with `./gradlew runGameTestServer`.
