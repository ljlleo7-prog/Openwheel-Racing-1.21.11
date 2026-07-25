# Terrain-Coupled Physics Stability Plan

## Purpose

High-speed elevation changes such as Eau Rouge and Raidillon can expose discontinuities between tyre dynamics, yaw integration, and block-based terrain following. The goal of this plan is to fix sudden terrain-induced sideslip or path/yaw mismatch without disrupting the handling balance that already feels semi-stable on flat tracks.

This is a staged surgery plan, not a broad rewrite. Each phase should be validated independently before moving to the next.

## Guardrails

- Preserve current steering balance, aero balance, base grip, and power delivery unless a phase explicitly targets them.
- Do not use per-tick broad telemetry/logging in normal gameplay. A previous runtime logging attempt caused severe tick-rate loss.
- Prefer cheap, local state fixes over block scans or object-heavy diagnostics.
- Keep server tick cost bounded and predictable.
- Validate flat high-speed cornering before validating complex terrain.

## Known Risk Area

The likely failure is not one single terrain bug. It is coupling between:

- tyre lateral force buildup and relaxation,
- lateral load transfer,
- yaw-rate integration,
- terrain-following height correction,
- post-move velocity state.

A small terrain correction at high speed can become a large body-frame lateral or yaw impulse if these systems disagree.

## Approach A — Minimal Stability Patch

### Scope

Fix obvious discontinuities while preserving current handling.

### Changes

- Keep lateral tyre behavior in the existing Pacejka/relaxation model.
- Avoid raw lateral-speed triggers for kinetic slip at high speed.
- Compute lateral load transfer from physics-loop state, not previous-tick debug values.

### Current Conservative Fix

The first conservative fix replaces previous-tick `debugVelocityLat` as the lateral load-transfer input with a substep-local estimate. This avoids load-transfer spikes caused by terrain or tick-to-tick debug-state discontinuities.

### Pros

- Low risk.
- No extra runtime logging.
- Does not alter steering lock, base grip, aero, or terrain following.
- Targets a clearly suspicious source of false left/right load transfer.

### Risks

- Does not solve terrain height discontinuities.
- One-substep lag is a stabilizing approximation, not a full tyre/chassis model.
- If instability remains, it likely lives in terrain movement reconciliation or contact height logic.

### Validation

- Flat high-speed cornering should feel unchanged.
- Eau Rouge/Raidillon should not produce sudden unexplained lateral load spikes.
- No tick-rate regression.

## Approach B — Contact-State Reconciliation

### Scope

After terrain movement, reconcile actual movement with the physics state so terrain correction cannot silently inject impossible lateral velocity.

### Candidate Changes

- Recompute body-frame actual longitudinal/lateral speed from `actualMovement` and current yaw after terrain following.
- Detect large mismatch between requested physics movement and actual terrain movement.
- Damp only terrain-injected lateral mismatch, not driver-induced slide.
- Reset tyre relaxation only when terrain correction creates a discontinuity.

### Pros

- Directly targets path/yaw mismatch.
- Does not require rewriting terrain contact.
- Can address circular paths with little bearing change.

### Risks

- Too much correction can feel like hidden stability control.
- Thresholds must avoid suppressing legitimate oversteer or kerb behavior.
- Requires careful in-game validation.

### Suggested Trigger Conditions

- High speed.
- Terrain correction or elevation delta occurred this tick.
- Actual lateral movement differs meaningfully from simulated lateral movement.
- Yaw change does not explain the path curvature.

## Approach C — Smooth Terrain Height / Contact Sampling

### Scope

Replace the current highest-footprint terrain snap with a smoother contact-height estimate.

### Options

#### C1: Smoothed Floor Delta

Limit per-tick floor height changes from terrain following.

- Lowest implementation risk.
- May reduce violent height jumps.
- Still uses block-footprint simplification.

#### C2: Four-Wheel Contact Height

Sample ground under wheel/contact points and derive chassis height from those points.

- More physically legible.
- Better for crests and compressions.
- More invasive than C1.

#### C3: Plane-Fit Road Normal

Fit a contact plane from wheel samples and use it to model pitch/roll and road-normal velocity.

- Most realistic.
- Highest risk and complexity.
- Should not be first implementation.

### Risks

- Existing climb/snap behavior may change.
- Collision, lap marker sampling, and visual ride height could be affected.
- Needs extensive track testing.

## Approach D — Crest Unloading / Normal Load Modulation

### Scope

Reduce tyre normal load when vertical terrain acceleration implies partial unloading.

### Pros

- More physically correct over crests.
- Can explain real Eau Rouge/Raidillon behavior.

### Risks

- High gameplay risk.
- Could make the crash problem worse if applied before terrain contact is stable.
- Requires reliable terrain normal / vertical acceleration first.

### Recommendation

Do not implement until contact-state reconciliation and terrain smoothing are stable.

## Approach E — Stability Guard

### Scope

A hidden safety guard that detects terrain-induced lateral spikes and damps only those spikes.

### Pros

- Fast gameplay protection.
- Can be targeted to pathological terrain events.

### Risks

- Can feel artificial.
- Can mask real bugs.
- May flatten valid high-speed oversteer if thresholds are wrong.

### Recommendation

Use only as a last-resort safety net after structural fixes.

## Preferred Sequence

1. Apply conservative load-transfer fix.
2. Test flat high-speed cornering and Eau Rouge/Raidillon.
3. If instability remains, implement contact-state reconciliation.
4. If terrain still causes discontinuities, prototype smoothed floor delta.
5. Only then consider four-wheel terrain contact or crest unloading.

## Diagnostics Policy

Avoid runtime logging inside the per-car physics loop unless it is manually gated and extremely narrow. Prefer:

- one-shot debug commands,
- temporary local counters exposed only when explicitly enabled,
- client-side HUD debug fields already computed for rendering,
- short reproduction videos with speed/yaw/slip HUD visible.

Never add broad block scans, string-heavy logs, or per-tick structured telemetry to normal server gameplay.
