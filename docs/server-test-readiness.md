# Server-test candidate — accepted barrier scraping and physical contacts

## Install and compatibility

Use `build/server-test/openwheel-racing-1.21.2-server-test.jar` on BOTH server
and all clients. Internal version is 1.21.2; network protocol is now **32**.
Check SHA256SUMS and BUILD-INFO.json. This artifact includes the current dirty
workspace, including lap/UI/AI work, and has not been deployed here. Trial on a
world copy because lap SavedData/migration changes are also present.

The external movement-threshold suppression patch is not included. Framed cars
bypass vanilla vehicle movement execution; ordinary player checks remain intact.
Compatibility with that external patch is unverified.

## Collision torque disabled

Barrier and car-to-car impacts now apply linear response only. They do not add
spin or turn angular speed into extra collision impulse. Off-center car and
barrier tests verify unchanged yaw rates. Steering and tyre dynamics remain.
Rebound, tangential scraping, damage reductions, accepted motion and stale-frame
protection remain enabled. The previous protocol-31 build is superseded.

## Barrier scrape repair

Legal barrier scrapes now use their actual contact-then-slide path and collision
heading for acceptance. They do not trigger a resync or pedal-release stop. Shared
faces between barrier blocks no longer catch tangent movement. The regression
covers low/high speed, both directions, kerbs, and steering away without changing
the motion epoch; forged through-wall movement is still rejected.

Rebound runs locally, now points outward, and uses 0.18 restitution with a 2.4 m/s
cap. Grazing damage depends on inward normal speed; the base hard-impact coefficient
is reduced from 14 to 10. See [barrier contact](mechanism-specs/barrier-contact-acceptance.md).
The previous protocol-29 candidate is superseded.

## Car-to-car collision repair

Cars now sweep their component shapes before movement, predict the local
response immediately, and clip contact rather than crossing through. The server
confirms contacts and sends the resulting motion to both drivers in fresh
epochs, so old packets cannot erase either impulse. Impact torque is disabled; tangential velocity is preserved by the normal response. Equal-speed
convoys and vertically separated cars do not receive contact impulses.

Only contact causes this state update; clear driving remains client-owned.
See [car-to-car contact](mechanism-specs/car-to-car-contact.md) for tests and
latency/pileup limitations. The older protocol-28 candidate is superseded.

## Shared-car and physical pit changes

The jack now raises/lowers independently of tyre replacement. Empty-hand use on
an idle raised car removes and returns its fitted set; tyre use fits a supplied
set only when raised and tyreless. Lowering without tyres is permitted, but
those cars cannot drive. Pickup/save/replacement preserves tyreless state.

Technicians share idle raised cars while each in-flight action has one operator.
Server control suspends driver prediction during service/recovery. Fresh epochs
and snapshots prevent late driver packets from undoing tyre changes or carried
poses. Boarding during service is rejected; replacement drivers receive fresh
state. Interrupted installations refund once. The old procedural sequence is gone.

See [physical pit service](mechanism-specs/physical-pit-service.md).

## Terrain, ERS and resync repairs in this candidate

- A collision step's height is no longer retained as upward launch velocity.
- Terrain-following validation accepts the same floor contour as the local
  solver; the fast-descent case rejected by the old straight sweep is tested.
- ERS now runs on the client, including deployment, harvesting and energy refunds.
  Accepted server telemetry includes ERS power/activity and speed.
- A rejected stream restarts with stopped momentum. Two rejections at the same
  spot within two seconds show **Movement paused**; release both pedals to resume.
  This prevents held input from endlessly replaying the rejected trajectory.
- Old-epoch synchronization replies are throttled; idle resets are idempotent.

The earlier protocol-27 build is superseded. Install this matching build on both
ends. The affected real-world uphill launch still needs a live retest.

## Current behavior

Admitted clients own human driving physics. The server accepts validated motion
and records crossings, lap time and telemetry from those same ordered segments.
There is no ordinary server physics correction loop. This revision also blocks
vanilla velocity and driving-metadata echoes from overwriting the local solver;
automatic shifts now apply locally without waiting for a server response. An
80-frame delayed-metadata regression compares acceleration and steering against
an uninterrupted simulation. Explicit recovery and
rejection of blocked, unloaded, broken-stream or impossible motion can still
resynchronize the car and display a notification.

Login performs a fresh build/probe check. Modified definitions and prohibited
runtime parameters are rejected as technical-rules violations. Legal assist and
steering preferences are preserved. This self-reported check cannot defeat a
purpose-built dishonest client; it must not be described as cheat-proof.

See [the active mechanism specification](mechanism-specs/client-authoritative-motion.md)
for pacing, timing, telemetry and known validation limits. The older block-impact
rollback specification documents the previous server-physics mechanism and its
legacy regression suite, rather than the active human motion authority.

## Validation

```text
./gradlew build runBarrierScrapeTestServer runCarContactTestServer runSharedCarTestServer runRecoveryTestServer runClientAuthorityTestServer runBlockRollbackTestServer runLapNetworkTestServer runSurveyCompensationTestServer runLapCaptureTestServer
```

The dedicated client-authority fixture covers technical admission, stale proof,
modified builds/runtime grip, telemetry, no additional server solver step,
finish/checkpoint bursts, exact lap times, duplicates and rejected teleports.
The other suites cover legacy rollback, packet timing, survey compensation and
the strict 7,680-tick capture campaign. The build includes unit, livery and lap
timing tests. Results and final command details are in BUILD-INFO.json.

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

Those repairs remain active in the accepted-motion timing pipeline. The older
rollback suite separately preserves its per-driver preference and true-impact
regressions; it does not establish that the new motion path performs rewinds.

## Suggested trial

1. Check that login reports a passed technical check, then drive a normal lap.
2. Repeat the previously troublesome Baku location at similar speed and inputs.
3. Use `/owrhandling start`, then `mark` after an incident and `stop` to save
   the client CSV. Keep `/owrnetstatus` output, location, speed, damage/notification
   details and the matching build hash.
4. In single-player, `/owrnet lag` enables occasional packet stalls; `lag off`
   disables them. It does not emulate inbound loss or low TPS.

`/owrnet auto-rewind` controls the older disputed-impact mechanism; it does not
turn off accepted-motion validation in this version. `/owrnet rewind-preview`
still previews the notification.

## Unresolved or unverified

- The original repeated-location Baku air wall has not been reproduced here.
- No full interactive remote client/server play test has been completed.
- Steps, slopes, edge scrubbing and unusual shapes may cause conservative path
  rejection. An explicit resynchronization can therefore still occur.
- The login fingerprint is self-reported. Complete independent verification of
  acceleration, grip, collision claims, ERS use and tyre evolution is unfinished.
- Timing permits bounded burst grace; it is not exact latency compensation.
  Low-TPS world-tick race controls versus real-time-paced lap time need live tests.
- Scripted tyre-service/driver/marshal handoffs pass. Real simultaneous clients,
  disconnect timing, jack animations and safety-car wheel culling still need
  visual/network testing. No claim that every handling disturbance is eliminated.
- Scripted car-contact cases pass. Real multiplayer latency, sustained rubbing,
  car/wall combinations and crowded-server performance still need live/load tests.

This is a controlled trial candidate, not a production-proven release.
