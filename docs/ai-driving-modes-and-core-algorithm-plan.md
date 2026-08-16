# AI Driving Modes and Core Pace Algorithm

## Purpose

This document proposes the next AI driving architecture before implementation. It addresses two separate needs:

1. Preserve both independent racing and orderly traffic modes.
2. Replace the current formation-lap-like racing behavior with a stable, fast controller that reaches the available straight-line speed and carries substantially more speed through corners without zigzagging.

The proposal keeps server-authoritative `OpenwheelCarEntity` physics, automatic gearing, bounded local AI collision avoidance, and the existing survey route as the source of track position. It does not introduce fake players, packet spoofing, or a full classification/session rewrite.

## Current diagnosis

### Traffic behavior is currently hard-wired

`BasicAiCarController` previously coupled target speed to `nearestAheadGap`, which caused queueing. The current implementation removed that coupling and uses `BasicAiNearbyAvoidance` for local predicted AI collision threats. This is the correct basis for an independent mode, but the controller needs an explicit selectable pace policy so formation, VSC, SC, and racing behavior are not conflated.

`BasicAiFleetManager.prepareLevel` is already the once-per-server-tick command preparation boundary. It is the correct place to resolve the active mode and prepare traffic snapshots before each car moves.

### The current speed model is too conservative in corners

The current controller uses:

```text
cornerSpeed = sqrt(MAX_LATERAL_ACCELERATION / curvature)
```

with `MAX_LATERAL_ACCELERATION = 5.0 m/s²`, then applies a braking-distance projection. This produces approximately 30 km/h corner speeds on moderate survey curvature and is appropriate to a cautious formation lap, not racing.

The route speed model samples only a limited set of forward distances and uses a fixed curvature window. It has no explicit exit-speed model, no acceleration model, and no distinction between entry, apex, and exit. It can therefore brake too early and remain slow after the apex.

### The current steering model can zigzag

The current steering target is a single point at a speed-dependent lookahead. Lateral correction is:

```text
steer -= signedLateralDistance * 0.045
```

The controller then slews steering by `0.08` per tick. This combines:

- a moving target point;
- route projection noise;
- localizer candidate changes near curvature/seams;
- a direct lateral correction term;
- avoidance steering added on top of the already filtered steering.

The result can alternate left/right around the survey line instead of converging smoothly.

### Straight-line speed is not determined by the AI target alone

The current AI target is `MAX_TARGET_SPEED_MPS = 95.0`, or 342 km/h in Minecraft's 20-tick conversion, but actual speed is also bounded by:

- the selected gear's `gearTopSpeedKmh`;
- setup top-speed coefficient;
- engine RPM/redline and drivetrain force;
- aerodynamic drag and surface grip;
- `VehiclePhysics` movement integration;
- any DRS/ERS and damage effects.

Therefore the new controller must expose telemetry for target speed, gear, RPM, throttle, brake, longitudinal speed, and route curvature before changing physics constants. A target increase alone cannot guarantee speed if the drivetrain or gear profile is the real limit.

## Proposed selectable AI modes

Introduce a small AI traffic/pace mode owned by the fleet manager, not by individual route logic:

```text
RACE        independent fastest-forward driving; no route-gap queueing
FORMATION   fixed formation speed and controlled following gap
VSC         capped target speed and controlled gap; no overtaking behavior
SAFETY_CAR  follow a controlled safety-car pace/gap; no racing acceleration
```

### Mode semantics

#### `RACE`

- No route-distance spacing penalty.
- Target speed comes from the racing-line speed planner.
- Local AI collision avoidance remains active.
- A slower AI ahead does not reduce target speed unless local geometry predicts contact.
- Overtaking/path choice is a later extension; the first version only avoids imminent contact.

#### `FORMATION`

- Use formation speed cap and a stable gap controller.
- Follow the assigned grid/order, not arbitrary nearest AI geometry.
- Apply low-pass gap error and acceleration limits to prevent accordion oscillation.
- Keep lateral route tracking conservative.

#### `VSC`

- Use a configurable global speed cap.
- Preserve route order and gap control, but do not stop every car when one car is slow.
- Use the same local collision avoidance as `RACE` as a safety layer.

#### `SAFETY_CAR`

- Follow the safety-car reference or a virtual reference point.
- Use a lower speed cap and larger time gap.
- Disable racing-line attack behavior while retaining stable route tracking and collision avoidance.

### Source of mode selection

`OWRRaceControlState.getGlobalFlag()` already persists `RaceFlagMode` values including `GREEN`, `YELLOW`, `RED`, `SAFETY_CAR`, and `VIRTUAL_SAFETY_CAR`. The first integration should map:

```text
GREEN  -> RACE
YELLOW -> configurable cautious/formation policy
SAFETY_CAR -> SAFETY_CAR
VIRTUAL_SAFETY_CAR -> VSC
RED -> stopped/hold policy
```

A dedicated AI mode override should remain available for testing, because a global flag and an AI testing command are different concerns. Recommended command-level override:

```text
/owr ai fleet mode race
/owr ai fleet mode formation
/owr ai fleet mode vsc
/owr ai fleet mode safety_car
/owr ai fleet mode auto
```

`auto` follows `OWRRaceControlState`; explicit modes override it until reset. Persist only the fleet mode/override if persistence is needed. Do not create race classifications or fake participants for this feature.

## Proposed core racing algorithm

Replace the current single-point speed/steering coupling with three independent layers:

1. stable route localization and preview geometry;
2. a forward speed planner with entry/apex/exit reasoning;
3. a steering controller that damps lateral and heading error.

### 1. Stable route geometry sample

For every command-preparation tick, localize once and derive a `RoutePreview`:

```text
current: route distance, signed lateral error, heading error
near:    +8 m
mid:     +18 m
far:     +35 m or speed-scaled preview
```

Each sample should include:

- position;
- tangent heading;
- signed curvature;
- route width if available;
- route-distance delta.

Use modular route distance and the existing seam-safe `SurveyRouteSampler`. Do not repeatedly re-localize against different candidate points during one tick.

Apply a small temporal filter to preview curvature and signed lateral error. Reset the filter only when the route identity changes or localization becomes genuinely untracked. A one-tick candidate switch should not reverse steering direction.

### 2. Cached approximate grip-envelope speed planner

The racing target should remain a percentage of predicted grip, but the planner must be much cheaper than replaying the four-wheel physics model. AI planning remains server-only; clients receive only ordinary entity state and existing diagnostics.

Use one effective whole-car grip envelope derived from slowly changing vehicle condition, then add speed-dependent aero analytically.

#### Cached base capability

Build a compact `ApproximateGripState` per AI car and refresh it only when relevant state changes materially or on a slow cadence, for example every 10–20 server ticks:

```text
mass
base lateral mu
base longitudinal mu
average tyre temperature factor
worst-axle temperature factor
average tyre wear factor
worst-axle wear/damage factor
surface grip factor
setup grip/aero/power coefficients
front/rear aero damage factor
chassis/engine power factor
brake force per mass
rolling/drag coefficients
```

Use current values already calculated or stored by `OpenwheelCarEntity`; do not sample four projected wheel surfaces for every future route point. For the first implementation, use the current car surface as the forecast surface and invalidate immediately when it changes. Later, route nodes may cache a coarse surface category if mixed-surface forecasting proves necessary.

Aggregate asymmetric tyre state conservatively without simulating every wheel:

```text
condition factor =
    weighted average condition * axle balance
    limited by a floor derived from the weaker front/rear axle
```

This prevents one damaged or cold axle from disappearing inside a simple four-wheel average while remaining constant-time.

#### Speed-dependent effective grip

For a candidate speed `v`, calculate approximate downforce once:

```text
downforce(v) = 0.5 * airDensity * effectiveClA * v²
normalLoad(v) = mass * gravity + downforce(v)
```

Then approximate available acceleration:

```text
lateralCapacity(v) =
    baseLateralMu
    * conditionFactor
    * surfaceFactor
    * loadSensitivityApproximation(normalLoad)
    * normalLoad / mass

longitudinalCapacity(v) =
    baseLongitudinalMu
    * conditionFactor
    * surfaceFactor
    * loadSensitivityApproximation(normalLoad)
    * normalLoad / mass
```

This retains the essential behavior: high-speed corners gain capability from aero, while low-speed corners rely mostly on tyre/surface mechanical grip. It intentionally omits per-wheel load transfer, Pacejka transients, tyre relaxation, and repeated future contact-patch sampling.

Use a small speed-bin lookup table in each cached grip state, for example 0–100 m/s in 5 m/s bins. Rebuild the table only when setup, tyre condition, damage, DRS/aero mode, or surface category crosses a threshold. Planner samples then become interpolation and a few scalar operations rather than repeated physics evaluation.

#### Grip utilization policy

Modes define utilization percentages of the approximate envelope:

```text
RACE       high utilization
FORMATION  low utilization
VSC        moderate-low utilization plus pace cap
SAFETY_CAR low utilization plus safety-car pace/gap cap
```

For predicted speed `v` and curvature `k`:

```text
requiredLateral = v² * abs(k)
lateralUse = requiredLateral / lateralCapacity(v)
feasible when lateralUse <= modeUtilization
```

Solve corner speed with a small fixed number of iterations or scan the cached speed bins; no per-sample binary search through the full vehicle model is required.

#### Approximate combined longitudinal/lateral demand

Use one whole-car friction circle:

```text
remainingFraction = sqrt(max(0, modeUtilization² - lateralUse²))
availableBrake = longitudinalCapacity(v) * remainingFraction
availableDriveGrip = longitudinalCapacity(v) * remainingFraction
```

Cap drive acceleration separately by a simple power/drag estimate:

```text
availableDrive = min(
    availableDriveGrip,
    effectivePower / max(v, minimumPowerSpeed) / mass - drag(v) / mass
)
```

Cap braking by existing maximum brake force per mass plus aerodynamic/rolling resistance. This is an approximation for planning only; actual ABS, TC, brake bias, rear-wheel drive, wheel load transfer, and combined slip remain enforced by authoritative live physics.

Run short preview passes:

1. **Backward braking pass** using approximate remaining brake capacity.
2. **Forward acceleration pass** using approximate power/grip/drag capacity.

Use a small fixed route horizon and sample count, such as 8–12 route samples. Cache route curvature by survey revision so every car does not recalculate identical geometry. The final target is the minimum of lateral, braking, acceleration, gear/top-speed, flag/mode, and collision-avoidance constraints.

#### Load controls

- All planning runs server-side only.
- Cache route curvature once per route revision and share it across AI cars.
- Cache each car's speed-bin grip table for 10–20 ticks and invalidate only on material tyre, damage, setup, surface, or DRS changes.
- Stagger cache refreshes across cars by stable identity so a 24-car fleet does not refresh in one tick.
- Recompute the full speed horizon every 2–4 ticks; reuse/interpolate the previous target between planning ticks.
- Keep nearby collision avoidance on its bounded local snapshot, independent from route planning.
- Do not simulate future tyre temperature/wear or mutate car state.
- Expose counters/timing for planner refreshes so server cost can be measured.

Formation/VSC/SC apply their mode pace cap and lower utilization reserve after the physical capability approximation is calculated.

### 3. Steering controller

Use a preview tangent plus a bounded cross-track correction rather than steering directly at a single point.

Recommended desired curvature:

```text
heading_term = k_heading * sin(preview_heading - vehicle_heading)
position_term = k_lateral * atan2(lateral_error, max(preview_distance, 1))
desired_curvature = heading_term / preview_distance + position_term
steer = atan(wheelbase * desired_curvature) / max_steer_angle
```

Important stability rules:

- Choose one route candidate per tick and keep it for a short hysteresis interval.
- Use a lookahead that grows with speed, but clamp it to a reasonable racing range.
- Reduce lateral gain as speed rises rather than increasing it.
- Apply steering-rate limiting after combining route steering and collision avoidance.
- Add a small heading-rate damping term from current yaw rate if available.
- Do not add a full-strength avoidance bias every tick on top of an already biased steering value. Blend toward an avoidance target and slew once.
- Suppress full throttle when heading error or lateral error is large, but do not suppress throttle merely because the car is offset by a small amount.

This specifically addresses the previous failure where a car near the centerline but pointed across the track received full throttle and made a large intercept.

### 4. Collision avoidance layer

Keep `BasicAiNearbyAvoidance` independent from the route planner:

- evaluate local relative position and velocity;
- detect current/swept collision risk over a short horizon;
- choose a deterministic side;
- produce a steering target and brake floor;
- combine it with route steering before the single steering slew limiter.

For `RACE`, avoidance should only react to imminent contact. It must not create a queue behind a car that is merely slower but not on a collision trajectory.

For `FORMATION`, `VSC`, and `SAFETY_CAR`, spacing is a separate longitudinal planner and should not be implemented by reusing the race-mode avoidance threat flag.

## Diagnostic requirements before tuning

Add a compact AI debug/status payload or server log sampling for:

- mode;
- target speed and actual speed;
- route curvature at near/mid/far previews;
- lateral error and heading error;
- requested steering and applied steering;
- throttle/brake;
- gear and RPM;
- avoidance threat, TTC/severity, and chosen side.

Use this to distinguish:

- route planner too conservative;
- drivetrain/gear limit;
- steering oscillation;
- collision avoidance over-triggering;
- damage/tyre or surface grip limitation.

## Implementation sequence after approval

1. Add pure `BasicAiTrafficMode` and mode-resolution tests.
2. Add a side-effect-free approximate grip-state builder using existing car condition factors, plus a cached speed-bin capability table.
3. Add shared route-curvature caching and a short-horizon speed planner using the approximate whole-car friction circle.
4. Test the approximation against monotonic expectations and representative live-physics samples: warmer/in-range tyres outperform cold tyres, wear/damage reduce capacity, aero raises high-speed capacity, poor surfaces reduce both axes, and combined demand reduces remaining longitudinal grip.
5. Replace the current target speed calculation while keeping `OpenwheelCarEntity` integration authoritative.
6. Measure planner refresh counts and server tick cost with 1, 12, and 24 AI cars; adjust refresh cadence before increasing model detail.
7. Add mode-specific utilization reserves and longitudinal policies: race-independent, formation gap, VSC cap, SC reference.
8. Integrate `OWRRaceControlState` global flag resolution and `/owr ai fleet mode` override.
9. Keep `BasicAiNearbyAvoidance` as the final collision safety layer.
10. Run focused tests, full test suite, build, and then in-world test each mode on straight, corner, chicane, tyre-condition, surface-change, and restart/seam cases.

## Acceptance criteria

- `RACE` AI does not form a queue behind a slower or stopped AI unless local collision prediction requires temporary braking.
- `FORMATION`, `VSC`, and `SAFETY_CAR` preserve orderly following behavior with no accordion oscillation.
- Race AI reaches the available straight-line speed allowed by gear/setup/physics rather than an artificial controller ceiling.
- Race AI uses a configured percentage of a cached whole-car grip approximation, recomputed only when tyre condition, surface, setup, aero state, or damage changes materially.
- High-speed aero-supported corners are not constrained by a low fixed acceleration constant, while low-speed corners cannot demand more mechanical grip than the tyres predictably provide.
- Braking and acceleration envelopes consume the longitudinal capacity remaining after lateral demand; they are not fixed deceleration/acceleration constants.
- Extracted capability results remain consistent with representative outputs from the authoritative live wheel-force model.
- Steering converges smoothly without repeated left/right zigzagging on straights or corner exits.
- AI-to-AI collision avoidance remains active in every mode.
- Player behavior, world collision, authoritative vehicle physics, and existing race timing remain unchanged.
