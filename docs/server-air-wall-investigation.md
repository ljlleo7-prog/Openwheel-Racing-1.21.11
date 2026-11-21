> Historical server-physics path: protocol 32 human driving now uses
> [accepted client motion](mechanism-specs/client-authoritative-motion.md). Legacy rollback tests remain regression coverage.

# Server-test candidate — client-prediction-first synchronization

## Artifact and installation

Use the single JAR in `build/server-test/`, on BOTH server and every participating
client. `SHA256SUMS` identifies the exact file; `BUILD-INFO.json` records the
source commit, dirty-worktree status and automated validation summary.

The internal mod version remains **1.20.3**, with **network protocol 24**.
An older 1.20.3 JAR is not necessarily this candidate. Do not mix builds.
Tested environment: Minecraft 1.21.11, NeoForge 21.11.44, Java 21.

This artifact contains the current workspace, including the existing lap-record,
UI, AI and migration work; it is not a physics-only cherry-pick or a clean
tagged release. No server has been deployed or production world changed here.

For the first trial, use a copy of the server world: the workspace also contains
lap-record SavedData/migration changes. Keep the previous matching client/server
JARs and world copy for reverting the whole trial. Install only one Openwheel
Racing JAR per mods folder.

The external threshold-suppression mixin shown in the screenshot is not included
in this candidate. Establish a baseline without that external patch; if it must
remain installed, record that and treat compatibility as unverified. Modern
framed cars bypass vanilla vehicle packet movement rather than raising its
movement-check thresholds. Ordinary player movement checks are not disabled by
this implementation.

## Current driving-feel policy

Routine velocity/yaw forcing is removed. Normal motion remains client-predicted.
Only confirmed positional drift above 2.5 blocks for ten consecutive ticks can
trigger an ordinary correction, at most once every twenty ticks. Its applied
correction has an amber notification. Explicit crash rewinds, adjudicated impact
resolution, recovery and broken-stream epoch resynchronization are separate
exceptions and remain enabled. There is no reintroduction of vanilla validation
movement side effects or blanket movement-threshold suppression.

The controlled normal/stalled steering fixtures both send zero ordinary motion
corrections. This preserves the local solver and reduces network interference,
but exact subjective equivalence to the old build remains unverified.

## Automated checks passed

The final validation command completed successfully:

```text
./gradlew build runBlockRollbackTestServer runLapNetworkTestServer runSurveyCompensationTestServer runLapCaptureTestServer
```

- Standard, livery prototype and isolated lap-timing tests passed or were up-to-date.
- The dedicated-server rollback suite covers clear-path recovery, first/last
  lookahead-tick true impacts, false client collision claims, timeout,
  cooldown/repeated walls, unrelated damage, changed geometry, marshal recovery,
  stale gear reports, payload round trips and tyre thermal state restoration.
- Identical rapid steering measured 0 m/s server/oracle velocity disagreement
  without stalls and approximately 4.95 m/s with scripted input stalls.
  This is a simulation comparison, not a capture of a real client's felt force.
- The legacy packet/timing suite reported zero missed packet crossings in its
  six accepted-crossing cases.
- Survey compensation tests passed.
- The strict seeded lap-capture campaign completed 7,680 ticks with no mismatches.
- Startup no longer reports obsolete `@OnlyIn` annotations on the three HUD classes.
- Locale key sets are aligned and whitespace checks pass.

## Readiness repairs completed

The broader strict campaign originally found two previously documented timing
defects. Both were fixed and the campaign rerun with strict assertions enabled:

1. Ground-marker discovery searched only 0.2 m below the swept box, even where
   crossing geometry accepted greater vertical support. Discovery now matches
   that existing support; it does not expand the geometry's height allowance.
   The 0.3 m lift pulse is recorded, the tested 0.5 m marker case is accepted,
   and the 0.8 m/higher marker cases remain rejected.
2. Steward timing dispatched only the earliest crossed line in a movement tick.
   It now dispatches every crossing in movement order, including the two adjacent
   checkpoints in a 5 m movement interval.

The client auto-rewind preference can now be changed on a remote server, enabling
per-driver A/B trials. Packet-stall simulation remains restricted to single-player.
The tests still enforce ordinary collision damage when assistance is disabled.

## Suggested trial and diagnostics

1. Run an ordinary clean lap, then repeat the previously troublesome location in
   the same direction, with similar speed and steering/tyre-heating inputs.
2. While seated, run `/owrhandling start`. After an incident,
   `/owrhandling mark`, then `/owrhandling stop`.
3. After an unexplained incident, run /owrnetstatus while seated and keep its
   server-side reason/counters alongside the capture.
   Keep the resulting CSV from the CLIENT's `logs/openwheelracing-handling/`.
   Removal of the car or leaving the world also saves automatically.
   Record location, heading, speed, approximate clock time, driver and whether
   impact damage/sound or a rewind banner occurred. This is especially important
   for comparing the same location across drivers.
4. Compare `/owrnet auto-rewind on` and `off` in separate passes.
   This affects only that driver's disputed-impact relief, not the framed
   movement authority or ordinary reconciliation. Turning it off while checking
   an impact commits that pending impact.
5. For local practice only, `/owrnet lag` enables occasional 3–6-tick stalls with
   3–7 seconds of normal delivery between bursts; `/owrnet lag off` disables them.
   `/owrnet rewind-preview` previews the HUD without moving the car.

Assistance defaults on and resets on connection/dimension changes. Diagnostic
capture is off by default, bounded to 20,000 recent rows, and writes only on stop
or teardown. Mark/stop work after dismount. The controls are client commands, not
dedicated-console commands. There is no global server-side rollback kill switch;
the per-driver toggle does not disable normal networking.

## Unresolved or unverified behavior

- **The actual repeated-location air wall is not reproduced on the affected map.**
  The vanilla validation-movement side-effect path is established from source,
  but the old mod binary, suppression patch and exact Baku geometry were not
  executed together. Coordinate-specific block/shape, elevation and chunk
  boundary causes remain possible.
- **The first cause of the felt lateral force is not established.** Delayed-input
  divergence is reproduced, and a real client tyre-temperature synchronization
  defect is fixed. An affected-client trace is still needed to distinguish
  reconciliation impulses, synced damage/handling changes and local tyre forces.
- **No full interactive client/server play test was performed.** The suites run
  real server physics and codecs with scripted clients/oracles and fake outbound
  connections. Camera feel, banner placement, real socket timing, third-party
  plugins and the remote command UX still need live testing.
- **Ordinary reconciliation can still change motion.** Server physics still
  predicts while an exact frame is missing, but normal corrections are withheld
  until confirmed and up to ten late-input frames can be repaired on the server.
  Unrepairable gaps cause an explicit resynchronization. Protected timing regions,
  collisions and long outages can still prevent repair; elimination of every
  lag-related handling disturbance is not guaranteed.
- **Relief is intentionally conservative.** Missing evidence, timeout, changed
  geometry, unavailable chunks, correction-generation changes, unsafe current
  controls, nearby entities and timing boundaries can deny a rewind to a clean
  driver. The 0.5-second horizon is simulation time at 20 TPS, not an unlimited
  guarantee about future collisions; prolonged low TPS stretches wall-clock waits.
- **Replay is not a historical world simulation.** It reads present geometry and
  weather, with a local geometry-change guard. Complex steps, unusual collision
  shapes and rapidly changing environmental state are not exhaustively verified.
- **Only block impacts receive this relief.** Car-to-car and other entity impacts,
  fire, falls and climbing contacts retain their normal behavior.
- **A rewind resets current trajectory/profile sampling.** Race time continues;
  completed records/timing crossings are protected by rejecting incompatible
  rewinds. Full profile/telemetry continuity through a rewind remains unverified.
- **Crowded-server performance is not load-tested.** Histories and replay work
  are bounded, but simultaneous incidents under sustained lag have not been
  measured with a production-sized field.
- **The local simulator is limited.** It approximates outbound retransmission
  stalls; it does not emulate inbound packet loss, low TPS or an entire transport.

This is ready for a controlled server trial, not a claim that the original
incident is fully diagnosed or that the candidate is production-proven.

## Smooth, tighter ordinary correction

The next candidate tightens the ordinary position condition to 1.5 blocks for
six consecutive ticks, with twelve ticks between corrections. Position/yaw
presentation uses a 300 ms smooth residual on the local car, rider and camera;
logical physics and timing positions are not blended. Repeated corrections
compose with the current residual instead of jumping again. A block-envelope
sweep, unavailable chunks, current collision or yaw changes above 30 degrees
disable blending and retain an explicit correction notification.

The development client loaded the render hooks without injection errors. Unit
tests cover continuity, fade completion, overlapping corrections and yaw wrapping;
the server fixture verifies that a visual path through a barrier is rejected.
Interactive driving feel, camera clipping and third-party camera compatibility
remain unverified.
