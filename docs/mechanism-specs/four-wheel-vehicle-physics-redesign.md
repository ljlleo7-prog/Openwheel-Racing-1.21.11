# Four-Wheel Vehicle Physics Redesign Plan

## Purpose

The current car physics mostly treats the car body as one rigid body with aggregated front/rear tyre forces. That works for basic acceleration and turning, but it makes some failure modes hard to reason about: lingering yaw after steering release, stationary rotation, unexpected sideways sliding, and broad damping constants that are difficult to justify physically.

This redesign moves the model toward a four-contact-patch simulation: each tyre computes its own velocity, slip, load, grip limit, and force. The chassis remains a rigid body binding the four wheels together. Rotation and translation emerge from the sum of wheel forces and their moments around the center of gravity.

The goal is not hardcore simulation for its own sake. The goal is a physically legible model that remains controllable and fun.

## Design Principles

- Preserve real physical constants unless they are clearly wrong: mass, gravity, wheelbase, track width, CG height, friction coefficients, aero areas, and load-transfer relationships should keep their meaning.
- Prefer fixing model structure before tuning constants.
- No hidden yaw damping as a handling patch. If a car stops rotating, it should be because tyre forces oppose rotation, not because yaw rate is multiplied by a magic factor.
- No artificial steering after input release. If steering input is zero, front wheel angle may persist only while the steering rack is physically returning to center.
- Stationary or near-stationary cars should not spin from tyre memory. At very low speed, static tyre constraints should oppose lateral and rotational motion strongly.
- The server must stay cheap. Per-car tick work should be bounded and avoid expensive broad block scans or object-heavy streams.

## Current Model Summary

The current `OpenwheelCarEntity` already has several useful pieces:

- Rigid body state: longitudinal velocity, lateral velocity, yaw rate.
- Geometry: wheelbase, track width, front/rear axle distances.
- Load transfer: longitudinal and lateral load-transfer estimates.
- Per-wheel normal loads and grip limits.
- Per-wheel lateral slip angles and longitudinal slip ratios.
- Pacejka-like longitudinal/lateral force curves.
- Combined-slip limiting.
- Tyre relaxation memory per wheel.

The weakness is that some calculations still operate as aggregate front/rear behavior, and there is not a clear wheel-local state/force pipeline. Stationary constraints are also weak because slip-angle math becomes ill-defined near zero speed.

## Proposed Architecture

### 1. Explicit Wheel State

Introduce a small internal record or class for each wheel during a physics tick:

```java
private record WheelState(
    double localX,
    double localZ,
    boolean front,
    double steerAngle,
    double normalLoad,
    double surfaceGrip,
    double muLong,
    double muLat,
    double vx,
    double vz,
    double slipRatio,
    double slipAngle,
    double longitudinalForce,
    double lateralForce
) {}
```

Coordinates should be body-local:

- `localZ` forward/back from CG.
- `localX` left/right from CG.
- Front wheels have steering angle.
- Rear wheels have toe-in but no steering input.

Use the existing geometry:

- FL: `x=-HALF_TRACK_WIDTH`, `z=FRONT_AXLE_DISTANCE`
- FR: `x=+HALF_TRACK_WIDTH`, `z=FRONT_AXLE_DISTANCE`
- RL: `x=-HALF_TRACK_WIDTH`, `z=-REAR_AXLE_DISTANCE`
- RR: `x=+HALF_TRACK_WIDTH`, `z=-REAR_AXLE_DISTANCE`

### 2. Wheel Patch Velocity

For each wheel, compute patch velocity from rigid-body motion:

```text
v_wheel_x = velocityLat + yawRate * localZ
v_wheel_z = velocityLong - yawRate * localX
```

Where:

- `x` is lateral body axis.
- `z` is forward body axis.
- Positive yaw rate makes left/right wheels see different longitudinal velocity and front/rear wheels see different lateral velocity.

Then rotate into each wheel's rolling frame:

```text
v_roll =  v_z * cos(steerAngle) + v_x * sin(steerAngle)
v_side = -v_z * sin(steerAngle) + v_x * cos(steerAngle)
```

This removes the need for ad-hoc patch formulas and makes each wheel's behavior interpretable.

### 3. Slip Model

#### Lateral Slip

For moving tyres:

```text
slipAngle = atan2(v_side, max(abs(v_roll), slipSpeedFloor))
```

The speed floor prevents numerical explosion at low speed.

#### Longitudinal Slip

Keep the existing force-request-to-slip approach initially, because wheel angular velocity is not yet modeled. For each wheel:

```text
slipRatio = longitudinalSlipRatio(longitudinalForceRequest, stiffness, longitudinalLimit)
```

Future upgrade: add wheel angular velocity and compute true driven/braked slip ratio.

### 4. Static / Near-Stationary Constraint

This is the key fix for stationary spin and unexplained sliding.

At low speed, slip-angle equations become poor because tiny sideways velocities can produce large angles. Instead, the tyres should act like static friction constraints resisting lateral motion and yaw.

Use a low-speed blend:

```text
staticBlend = smoothstep(0, staticSpeedThreshold, speed)
```

At very low speed, lateral tyre force target should oppose wheel side velocity directly:

```text
F_lat_static_target = -v_side * lateralConstraintStiffness
F_lat_static = clamp(F_lat_static_target, -muLat * normalLoad, +muLat * normalLoad)
```

At normal speed, use the Pacejka lateral force target.

Blend:

```text
F_lat_target = lerp(F_lat_static, F_lat_pacejka, staticBlend)
```

Physical interpretation: at low speed, tyres resist being scrubbed sideways because the contact patch is not freely sliding. This naturally prevents stationary rotation because each wheel generates lateral force opposing its local lateral velocity from yaw.

### 5. Tyre Relaxation

Keep tyre relaxation, but apply it uniformly and physically:

```text
relaxationGain = 1 - exp(-(abs(v_roll) * dt) / relaxationLength)
relaxedForce += (targetForce - relaxedForce) * relaxationGain
```

At near-zero speed, relaxation gain can become too small. For static constraint mode, use direct static force or a minimum relaxation gain because static friction response should not lag for seconds while parked.

Rule:

```text
if speed < staticSpeedThreshold:
    relaxedLateralForce = staticConstraintForce
else:
    relaxedLateralForce += (target - relaxed) * relaxationGain
```

This is physically more defensible than arbitrary yaw damping.

### 6. Force Transformation Back to Body

Each wheel produces force in wheel coordinates:

```text
F_roll = longitudinalForce
F_side = lateralForce
```

Transform to body coordinates:

```text
F_z = F_roll * cos(steerAngle) - F_side * sin(steerAngle)
F_x = F_roll * sin(steerAngle) + F_side * cos(steerAngle)
```

Then sum forces:

```text
F_long_total = sum(F_z)
F_lat_total  = sum(F_x)
```

Yaw moment from wheel forces:

```text
Mz = sum(localZ * F_x - localX * F_z)
```

This is the core rigid-body constraint: the wheels are separate, but the chassis binds them through summed forces and moments.

### 7. Rigid Body Integration

Use the existing body state and integrate:

```text
ax_body = F_long_total / mass + yawRate * velocityLat
ay_body = F_lat_total / mass - yawRate * velocityLong
yawAcceleration = Mz / yawInertia

velocityLong += ax_body * dt
velocityLat  += ay_body * dt
yawRate      += yawAcceleration * dt
```

Then integrate yaw and world movement as currently done.

### 8. Load Transfer

Keep the current front/rear and left/right load transfer as a first pass, but reorganize it per wheel:

- Static front/rear load from weight distribution.
- Aero load from aero balance.
- Longitudinal load transfer from acceleration.
- Lateral load transfer from lateral acceleration.

Use previous-substep acceleration estimates or the current force estimate. Later we can iterate once for better accuracy, but avoid expensive solver loops initially.

### 9. Combined Slip

Keep the existing friction ellipse / combined demand logic per wheel:

```text
demand = sqrt((Fx / FxLimit)^2 + (Fy / FyLimit)^2)
if demand > 1:
    scale forces down
```

This naturally models tyres having less lateral capacity while braking or driving.

### 10. Stationary Rotation Behavior

If the car is stationary and yawRate is non-zero, each wheel has lateral velocity from yaw:

```text
v_side ≈ yawRate * localZ
```

Front wheels and rear wheels see opposite lateral velocities, so they generate opposing lateral forces. Those forces create a yaw moment opposite the rotation.

Therefore, a stationary car stops rotating because tyre contact patches resist scrub. No yaw damping constant is needed.

## Implementation Plan

### Phase 1 — Refactor Without Behavior Target Change

Goal: move calculations into wheel-local functions while preserving current feel as much as possible.

Tasks:

1. Add `WheelCorner` enum or static wheel geometry descriptors.
2. Add a local `WheelForces` record for substep calculations.
3. Move current per-wheel normal load, mu, slip, Pacejka, relaxation, and combined-slip logic into a helper method.
4. Replace front/rear aggregate sums with a sum over four wheel outputs.
5. Preserve current constants and setup multipliers.
6. Verify `./gradlew build` and quick drive behavior.

Acceptance:

- Similar acceleration/top speed.
- Similar steering response at medium/high speed.
- No new server lag.
- No direct `setPos` terrain correction changes in this phase.

### Phase 2 — Add Static Constraint Mode

Goal: solve stationary spin and zero-input sideways creep physically.

Tasks:

1. Define `STATIC_TYRE_SPEED_THRESHOLD`, likely around `1.0-2.0 m/s`.
2. Add low-speed lateral constraint force per wheel.
3. Blend to Pacejka lateral force as speed rises.
4. Bypass long relaxation lag in static mode.
5. Keep only tiny numerical deadbands for residual floating-point noise.

Acceptance:

- Stationary car cannot rotate meaningfully without drive/brake/contact reason.
- Releasing steering at speed does not create hidden continued steering.
- Legitimate oversteer still exists when rear tyres are saturated.

### Phase 3 — Validate Energy and Wear

Goal: ensure forces, tyre wear, and damage remain explainable.

Tasks:

1. Confirm tyre slip metric uses actual per-wheel slip/demand.
2. Compute tyre wear from per-wheel lateral/longitudinal demand instead of aggregate front/rear only.
3. Verify energy clamp still prevents force integration from creating energy.
4. Preserve collision damage logic separately from tyre force logic.

Acceptance:

- Tyre wear increases under sustained slip, braking lock, wheelspin, and kerb/off-track usage.
- Zero-input straight driving does not accumulate high tyre slip.
- Energy clamp rarely activates during normal grip-limited driving.

### Phase 4 — Optional True Wheel Angular Speed

Goal: support more realistic throttle/brake slip later.

Tasks:

1. Add wheel angular velocity state for each wheel.
2. Apply engine torque through drivetrain to rear wheels.
3. Apply brake torque to all wheels by bias.
4. Compute true slip ratio from wheel circumferential speed vs ground speed.

This should be deferred unless current force-request slip becomes limiting.

## Performance Plan

- Keep exactly four wheel computations per physics substep.
- Avoid block scans inside force calculation except existing surface lookup samples.
- Avoid allocations where practical: use records only locally if JVM allocation is acceptable; otherwise use primitive variables or mutable scratch structs.
- Do not add solver loops until the basic four-wheel model is stable.
- Keep `PHYSICS_SUBSTEPS = 4` initially.

## Debugging / Telemetry

Add debug outputs only if needed:

- Per-wheel slip angle.
- Per-wheel demand.
- Per-wheel normal load.
- Per-wheel surface grip.
- Static constraint blend.

For HUD, show only aggregate values unless debugging is enabled.

## Risks

- Four-wheel force summing may initially feel more reactive or twitchy because yaw moment is more exact.
- Static constraint mode can feel sticky at very low speed if threshold is too high.
- Relaxation behavior may need careful tuning so the car remains smooth without preserving hidden steering.
- Over-cleaning low-speed slip could remove controllable low-speed rotation; this should be tuned through static blend, not arbitrary yaw damping.

## Recommended First Implementation Slice

Start with Phase 1 and Phase 2 together in one controlled patch:

1. Introduce wheel-local force helper.
2. Keep existing Pacejka/combined-slip constants.
3. Add static lateral constraint only below a low speed threshold.
4. Remove zero-steer arbitrary damping.
5. Keep current terrain/movement code unchanged except for already removing server-heavy terrain bypasses.
6. Build and test: stationary steering release, low-speed donut attempt, medium-speed corner exit, high-speed straight after steering release.

This directly addresses the current issue while keeping the model physically inspectable.

## Low-Speed Contact Regression (2026-10-22)

Static lateral stiffness is capped by a conservative supported-mass impulse
budget per substep. This prevents the static branch from reversing contact slip
through numerical overshoot. The existing blend to dynamic tyre forces remains;
at or above 1.5 m/s the dynamic force calculation is unchanged.
Slow blocked movement also removes the blocked fraction of the yaw already
applied that tick. This correction fades out from 1.5 to 2.5 m/s of actual
movement, preserving fast collision response and all unobstructed rotation.

Before/after unit comparison (`LowSpeedContactStabilityTest`):

| Scenario | Before | After |
| --- | --- | --- |
| 0.01 m/s patch slip, 200 kg supported mass, 100000 stiffness, 0.0125 s step | -0.0525 m/s (reversed) | 0.0075 m/s (settles) |
| Fully blocked 0.02 rad yaw step | 0.02 rad retained | 0 rad retained |
| Unblocked positive/negative yaw | Preserved | Preserved |
| Fast wall contact at 10 m/s | Preserved | Preserved |

The initial regression run failed the slip and blocked-yaw tests and passed the
preservation test. Additional coverage checks four-contact spin settling and
steering direction in forward and reverse. These are numerical unit checks;
in-game launch and wall-contact driving still requires manual validation.
