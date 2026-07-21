# Product Requirements Document: ERS-K Deployment System

**Version**: 1.1
**Date**: 2026-07-21
**Author**: Sarah (Product Owner)
**Quality Score**: 94/100

---

## Executive Summary

Openwheel Racing should add a single ERS-K system that turns braking energy into a strategic acceleration resource. The feature should mimic the spirit of the 2026 Formula 1 power-unit direction without recreating every regulation detail or forcing the player to watch a battery every second.

The system replaces part of the prototype car's existing peak output with electric deployment rather than simply stacking extra power on top. Drivers harvest energy under braking, spend it under throttle, and use quick right-hand mode keys to choose between clear ERS-K personalities: Harvest for recharge and high-speed lift/negative power, Balanced for everyday deploy/recovery, and Attack for sustained 350 kW push.

The target experience is strategic but forgiving: a good lap feels faster when the player manages braking zones and deployment modes well, while a depleted battery should reduce punch rather than make the car feel broken.

---

## Problem Statement

**Current Situation**: The car currently has engine power, setup modes, DRS, braking, traction, tyre wear, damage, and HUD feedback, but no hybrid energy layer. Power delivery is mostly immediate and setup-driven, so there is little lap-to-lap energy strategy beyond gearing, DRS use, and driving line.

**Proposed Solution**: Add an ERS-K-only energy system. Braking converts part of deceleration work into stored energy through a clear energy-transfer formula. Throttle converts stored energy into electric power in Balanced and Attack modes, while Harvest mode performs zero deploy and can apply negative power above 260 km/h to emphasize energy recovery. Player-selected modes control the harvest/deploy bias, with optional tuning for clip thresholds in a setup UI.

**Business Impact**: This feature deepens the driving loop, makes braking zones tactically meaningful, and gives the prototype car a more modern 2026-inspired identity while preserving the mod's controllable, fun handling direction.

---

## Success Metrics

**Primary KPIs:**
- **Driving feel**: ERS-K changes acceleration enough to feel meaningful in low/mid speed exits without making the car uncontrollable.
- **Cognitive load**: A player can complete multiple laps without feeling forced to micromanage charge every second.
- **Strategic value**: Attack mode gives full push for overtaking and lap push windows, Balanced mode is viable for normal laps with about 200 kW nominal deploy, and Harvest mode recovers energy predictably with no positive deploy.
- **Technical stability**: ERS-K state remains synchronized between server, client HUD, saved car entity, and persisted car item.

**Validation**: Validate through build success, local drive testing, HUD inspection, and comparison laps on a representative circuit with repeated braking zones.

---

## User Personas

### Primary: Keyboard Driver
- **Role**: Player driving the prototype open-wheel car with WASD and keyboard shifting.
- **Goals**: Drive fast laps, feel modern hybrid acceleration, and change modes without taking the left hand off driving.
- **Pain Points**: Too many controls can make the car feel like a cockpit simulator instead of a Minecraft racing mod.
- **Technical Level**: Intermediate.

### Secondary: Wheel/Gamepad Driver
- **Role**: Player using the existing wheel input setup.
- **Goals**: Bind ERS-K controls to wheel buttons and see the same HUD/state behavior as keyboard drivers.
- **Pain Points**: Keyboard-only controls reduce immersion for wheel users.
- **Technical Level**: Advanced.

---

## User Stories & Acceptance Criteria

### Story 1: Harvest Energy While Braking

**As a** driver,
**I want to** recover ERS-K energy during meaningful braking,
**So that** clean braking zones give me deployable power for the next acceleration phase.

**Acceptance Criteria:**
- [ ] ERS-K harvest occurs only when the car is moving forward, braking input is active, and the car is grounded on a valid drivable surface.
- [ ] Harvest is based on an energy-transfer formula using mass, speed reduction, braking input, mode harvest factor, and recovery efficiency.
- [ ] Harvest is capped per tick and by battery capacity so heavy braking cannot instantly fill the store.
- [ ] Harvest feedback is visible in the HUD without blocking primary speed/gear information.

### Story 2: Deploy Energy Under Throttle

**As a** driver,
**I want to** spend ERS-K energy under throttle,
**So that** low and medium-speed corner exits feel stronger when I have charge available.

**Acceptance Criteria:**
- [ ] ERS-K deployment occurs only when throttle is active, the car is in a forward gear, and stored energy is above zero.
- [ ] ERS-K replaces part of the current total output power budget rather than adding unlimited extra power.
- [ ] Deploy power follows mode-specific behavior: Harvest has 0 positive deploy, Balanced targets about 200 kW nominal deploy and clips after roughly 260 km/h, and Attack provides up to 350 kW without aggressive high-speed super-clipping.
- [ ] Deployment drains energy at a rate proportional to actual electric power delivered.

### Story 3: Change ERS-K Mode With Right-Hand Keys

**As a** keyboard driver,
**I want to** change ERS-K modes with right-hand keys,
**So that** my left hand can keep controlling throttle, brake, and steering.

**Acceptance Criteria:**
- [ ] The MVP includes three modes: Harvest, Balanced, and Attack.
- [ ] Default keyboard controls are `J` for previous mode and `L` for next mode.
- [ ] Mode changes are edge-triggered, synced to the server, and reflected immediately on the local HUD.
- [ ] Localization keys exist in both English and Chinese for controls and HUD labels.

### Story 4: Understand ERS-K State In The HUD

**As a** driver,
**I want to** see battery, mode, and deploy/harvest state clearly,
**So that** I can adjust mode without staring away from the road.

**Acceptance Criteria:**
- [ ] HUD shows current mode, approximate energy level, and whether the car is harvesting or deploying.
- [ ] HUD uses an XP-bar-inspired energy meter that is drawn by the mod itself, not the vanilla XP bar, so it remains visible in Creative mode and does not conflict with player XP.
- [ ] Low energy gives subtle warning feedback, not constant alarm spam.
- [ ] Existing HUD toggles should continue to control whether driving/setup/ranking/debug overlays are shown.

---

## Functional Requirements

### Core Features

**Feature 1: ERS-K Energy Store**
- Description: Each car has a normalized or joule-based ERS-K store, with capacity tuned for strategic use over several braking/acceleration events.
- User flow: Driver brakes into a corner, energy store rises, then throttle on exit spends energy according to mode.
- Edge cases: Energy cannot go below zero or above capacity; no harvest while airborne, reversing, stationary, or coasting without brake input.
- Error handling: Invalid/suspicious client mode input is clamped server-side.

**Feature 2: Braking Harvest Formula**
- Description: Harvest should be physically legible and use existing vehicle constants where possible.
- Formula target:

```text
speedBeforeMps = previousHorizontalSpeed * 20
speedAfterMps  = currentHorizontalSpeed * 20
kineticDeltaJ  = 0.5 * CAR_MASS_KG * max(0, speedBeforeMps^2 - speedAfterMps^2)
brakeShare     = clamp(brakeInput, 0, 1)
harvestJ       = kineticDeltaJ * brakeShare * modeHarvestFactor * recoveryEfficiency
storedEnergy   = min(capacityJ, storedEnergy + min(harvestJ, maxHarvestPerTickJ))
```

- Balance defaults for implementation pass:
  - Capacity target: enough for several corner exits, not a single-use boost.
  - Recovery efficiency: start around 35-45% and tune by feel.
  - Per-tick cap: prevents emergency stops from becoming instant full recharge.
- Edge cases: Avoid rewarding collision deceleration, wall impacts, off-ground motion, or passive drag slowdown as harvest.

**Feature 3: Deploy Formula, Speed Clipping, And Negative Power**
- Description: ERS-K power is requested under throttle, mode chooses requested power, available energy limits actual power, and mode-specific speed behavior controls clipping or negative power.
- Formula target:

```text
speedKmh = speedBlocksPerTick * 72
speedClip = modeSpeedClip(speedKmh)
requestedDeployW = modeDeployPowerW * throttleInput * speedClip
availableDeployW = storedEnergyJ / PHYSICS_DT
actualDeployW = min(requestedDeployW, availableDeployW, modeMaxDeployPowerW)
storedEnergyJ -= max(0, actualDeployW) * PHYSICS_DT
```

- Mode-specific power behavior:
  - **Harvest**: `modeDeployPowerW = 0`; no positive deployment under throttle. Above 260 km/h, apply a rising negative power curve to simulate high-speed harvesting/drag assistance, reaching full negative power around 320 km/h while still storing energy only within capacity.
  - **Balanced**: `modeDeployPowerW = 200 kW` nominal. Deployment starts clipping down after roughly 260 km/h and reaches its minimum by roughly 315 km/h. Clip start/end should be tunable either through the car setup GUI or Openwheel setup if exposing it does not clutter the MVP.
  - **Attack**: maximum deployment target is 350 kW. Attack should provide full deploy whenever throttle and energy are available, without Harvest-style negative power or aggressive super-clipping.
- Power-budget rule: existing `PEAK_POWER_WATTS` should be split conceptually into combustion output plus ERS-K output; total peak should remain close to the intended car power envelope until balance testing says otherwise.
- Speed clip target: Balanced should taper at high speed to mimic 2026-style energy behavior; Attack should feel direct and powerful rather than fading out too early.

**Feature 4: Deploy Modes**
- Description: Three simple modes provide meaningful strategy without overwhelming the driver.

| Mode | Intent | Deploy | Harvest | Player Use |
| --- | --- | --- | --- | --- |
| Harvest | Recover energy and reduce high-speed energy loss | 0 positive deploy; above 260 km/h can curve into negative power, full by ~320 km/h | High | Out lap, recovery laps, recharge after battery depletion, high-speed lift/recovery sections |
| Balanced | Default race mode | About 200 kW nominal, clipping down after ~260 km/h, minimum by ~315 km/h | Medium | Normal laps without micromanagement |
| Attack | Spend charge aggressively | Full 350 kW whenever throttle and energy are available; no Harvest-style negative power | Braking only | Exits, overtakes, lap push windows |

- Mode behavior should be deterministic, not random or rubber-banded.
- Balanced mode should be strong enough that casual players can leave it selected for most driving.
- Harvest mode should never give forward electric boost; its identity is recovery and optional high-speed negative power above 260 km/h.
- Attack mode should feel like the clear push mode: 350 kW maximum deploy, energy-limited, and only normal braking harvest.

**Feature 5: Controls And Networking**
- Description: Add right-hand mode cycling controls beside the existing keyboard driving scheme.
- Default keys: `J` = previous ERS-K mode, `L` = next ERS-K mode.
- Implementation targets:
  - Add key mappings in `OWRKeyMappings`.
  - Handle edge-triggered clicks in `OWRClientInputHandler`.
  - Add network message for mode delta or explicit mode selection in `OWRNetwork`.
  - Add optional wheel button roles in `WheelInputSettings` after keyboard MVP is stable.

**Feature 6: HUD Feedback**
- Description: Add compact ERS-K readout to the driving HUD.
- Display targets:
  - Mode label: `ERS Harvest`, `ERS Balanced`, `ERS Attack`.
  - XP-bar-inspired battery meter drawn independently by the mod, not the actual vanilla XP bar.
  - Energy should remain visible in Creative mode, when the vanilla XP bar may be hidden or visually irrelevant.
  - State indicator: `CHG`, `DEP`, `NEG`, or neutral.
- Visual guidance:
  - Place the ERS-K bar near the existing driving HUD without replacing speed, gear, lap, damage, tyre, or warning information.
  - Use the familiar horizontal-fill readability of the XP bar, but use distinct color/labeling so players do not confuse ERS-K energy with player XP.
  - Prefer broad readability over exact precision; percent/debug numbers can stay in debug HUD if needed.
- Keep the display secondary to speed, gear, lap, damage, tyre, and warning information.

---

## Technical Constraints

### Performance
- ERS-K update must run inside the existing per-car tick/physics path without block scans or allocations in hot loops.
- Per-tick calculations should be simple scalar math using already-known speed, input, grounded/surface state, and physics constants.

### Security / Authority
- Server remains authoritative for stored energy, mode, deploy amount, and harvest amount.
- Client may request mode changes but must not send energy or power values.
- Network handlers must clamp mode inputs and ignore requests from players not riding an `OpenwheelCarEntity`.

### Integration
- **Vehicle physics**: Integrate deploy as part of the existing drive-force/power calculation in `OpenwheelCarEntity`, respecting traction limits and combined-slip behavior.
- **Persistence**: Save ERS-K energy and selected mode on the car entity; persist to the car item if car state is already written back when dismounted/picked up.
- **HUD**: Extend existing car HUD with ERS-K state using a custom XP-style bar; do not rely on or modify the vanilla XP bar.
- **Setup UI**: If implemented in the same pass, expose Balanced clip start/end as an advanced setup value in the car setup GUI or Openwheel setup, defaulting to ~260 km/h clip start.
- **Localization**: Update `en_us.json` and `zh_cn.json` with keybind and HUD strings.
- **Controls**: Add keyboard controls first; wheel/gamepad binding can follow the existing `WheelInputSettings.ButtonRole` pattern.

### Technology Stack
- Java 21, Minecraft Forge 61.1.0, official mappings, existing Forge network channel.
- No new dependency required for MVP.

---

## MVP Scope & Phasing

### Phase 1: MVP Requirements And Simulation Core
- Add ERS-K entity state: mode, stored energy, current deploy/harvest status.
- Add braking harvest formula with caps and grounded/forward/brake gating.
- Add throttle deployment formula with Balanced speed clipping, Attack full-power deploy, and Harvest high-speed negative power behavior.
- Integrate deploy power into existing engine power budget.

**MVP Definition**: A driver can harvest during braking, deploy during throttle in Balanced/Attack, recover only in Harvest including high-speed negative power behavior, switch between Harvest/Balanced/Attack with J/L, and see ERS-K state on a custom XP-style HUD bar.

### Phase 2: Controls, HUD Polish, And Persistence
- Add localized keybinds and HUD labels.
- Persist ERS-K state through entity save/load and car item transfer.
- Add subtle sound or visual feedback for low energy, full battery, and mode changes if existing sound assets support it.
- Add wheel/gamepad button roles for ERS-K mode previous/next.

### Phase 3: Balance And Race Rules
- Tune capacity, deploy power, harvest efficiency, and speed clipping from track testing.
- Consider race director rules or server settings for enabling/disabling ERS-K in specific events.
- Consider per-setup interactions with power mode after the baseline feels good.

### Out of Scope For MVP
- ERS-H, turbo energy recovery, detailed MGU-H behavior, or full 2026 regulation replication.
- Manual per-corner deployment maps.
- Battery degradation, thermal limits, reliability penalties, or complex electronics crafting.
- Separate UI screen for ERS-K setup.
- Multiplayer balance restrictions beyond server-authoritative state and sane clamps.

---

## Risk Assessment

| Risk | Probability | Impact | Mitigation Strategy |
|------|------------|--------|---------------------|
| ERS-K makes acceleration too strong | Medium | High | Treat ERS-K as replacement within total power budget first, then tune upward only if needed. |
| Battery management becomes annoying | Medium | High | Keep Balanced mode viable, use broad HUD bands, and tune capacity for several braking/deploy events. |
| Harvest rewards crashes or wall hits | Medium | Medium | Gate harvest by brake input, grounded state, forward motion, and non-collision deceleration where detectable. |
| Network desync between HUD and server | Low | Medium | Store authoritative state in synced entity data and update local HUD immediately only for mode selection. |
| Speed clipping feels invisible | Medium | Medium | Show deploy state/power band in debug or HUD and tune clip thresholds through drive testing. |

---

## Dependencies & Blockers

**Dependencies:**
- Existing `OpenwheelCarEntity` physics and input handling.
- Existing `OWRNetwork` client-to-server message pattern.
- Existing `OWRKeyMappings` keyboard registration.
- Existing `CarHudOverlay` driving HUD rendering.
- Existing localization files for English and Chinese parity.

**Known Blockers:**
- Exact balance values require in-game drive testing; the PRD defines initial formula shape and tuning targets, not final constants.
- Wheel/gamepad ERS button bindings depend on extending the current wheel setup UI cleanly.

---

## Implementation Notes

### Suggested Data Model

```text
ERS mode: int enum-like value, 0 Harvest / 1 Balanced / 2 Attack
ERS energy: float or double stored joules, clamped 0..capacity
ERS activity: int state, 0 neutral / 1 harvesting / 2 deploying
ERS deploy watts: optional debug/synced value for HUD/debug display
```

### Suggested Initial Constants

These are starting points for testing, not final balance commitments:

```text
ERS_CAPACITY_J = 3_000_000 to 5_000_000
ERS_BALANCED_DEPLOY_W = 200_000
ERS_ATTACK_DEPLOY_W = 350_000
ERS_HARVEST_DEPLOY_W = 0
ERS_HARVEST_NEGATIVE_POWER_MAX_W = 80_000 to 140_000
ERS_MAX_HARVEST_PER_TICK_J = 18_000 to 35_000
ERS_RECOVERY_EFFICIENCY = 0.35 to 0.45
BALANCED_DEPLOY_CLIP_START_KMH = 260
BALANCED_DEPLOY_CLIP_END_KMH = 315
HARVEST_NEGATIVE_POWER_START_KMH = 260
HARVEST_NEGATIVE_POWER_FULL_KMH = 320
```

### Suggested Mode Targets

```text
Harvest:  deploy 0 kW, high harvest, negative power ramps in above 260 km/h
Balanced: deploy ~200 kW nominal, medium harvest, deploy clips after ~260 km/h
Attack:   deploy 350 kW max, braking harvest only, no negative power behavior
```

### Definition Of Done

- `./gradlew build` succeeds.
- ERS-K state is visible on a custom XP-style HUD bar and changes with J/L.
- Braking increases energy on track; throttle decreases energy and changes acceleration feel.
- Energy does not harvest from crashes, passive drag, reverse driving, or airborne movement.
- Car save/load and item persistence do not lose ERS-K state unexpectedly.
- English and Chinese localization key sets remain aligned.

---

## Appendix

### Glossary
- **ERS-K**: Kinetic energy recovery/deployment system that harvests braking energy and redeploys it as electric drive power.
- **Harvest**: Capturing kinetic energy during braking and storing it in the ERS-K battery.
- **Deploy**: Spending stored ERS-K energy to provide electric power under throttle.
- **Speed clipping**: Reducing available ERS-K deployment as car speed rises so electric boost matters most at low/mid speed.
- **Selection hand**: Right-hand keyboard control area used for tactical mode changes, separate from left-hand WASD driving.

### References
- Existing power/brake constants: `OpenwheelCarEntity` and `VehiclePhysics`.
- Existing control pattern: `OWRKeyMappings`, `OWRClientInputHandler`, and `OWRNetwork`.
- Existing mechanism spec checklist: `docs/mechanism-specs/README.md`.

---

*This PRD was created through interactive requirements gathering with quality scoring to ensure comprehensive coverage of business, functional, UX, and technical dimensions.*
