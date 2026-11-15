# Lap capture reproduction campaign

Current implementation and accuracy results: [accepted motion and continuous coverage](lap-recording-reliability.md). The packet bypass below is historical and is now fixed; its strict regression passes. Physical-block height and multi-checkpoint defects remain separate diagnostic cases.

## Follow-up: virtual line authority, checkpoint checking disabled

A separate real-server packet-handler test reproduces a capture bypass with the affected configuration: configured virtual line, checkpoint checking disabled, off-track checking disabled, living seated server-player driver, and a valid armed lap.

```sh
./gradlew runLapNetworkTestServer --offline
# Diagnostic mode retains the evidence but permits the reproduced defect:
./gradlew runLapNetworkTestServer --offline -PlapCaptureStrict=false
```

Before the fix, the strict test failed: **all six vanilla vehicle-position crossing cases lost the completed lap**. The final accepted-motion hook now records all six. The physics-path-only control and all six delayed-control-input controls record it correctly. The report is `build/lap-network-gametest/lap-network-summary.csv`.

| Movement/input path | Scheduled arrival delays | Recorded laps |
|---|---|---:|
| Ordinary mod movement pipeline | 0 ticks | 1/1 |
| Delayed drive inputs, stale sequence rejection, and sequence-gap acceptance; then the same checked movement | 0, 1, 2, 4, 10, 20 ticks | 6/6 |
| Real `ServerGamePacketListenerImpl.handleMoveVehicle` crossing, followed by checked movement | 0, 1, 2, 4, 10, 20 ticks | 0/6 |

Minecraft's local player sends `ServerboundMoveVehiclePacket` for its locally authoritative vehicle. `OpenwheelCarEntity` inherits passenger-based client authority, while also running the mod's server physics. The vanilla server handler accepts vehicle position updates through `Entity.move` and `absSnapTo`. Those operations do **not** run `processRaceTimingMovement` or the configured finish detector.

In each packet case, the real vanilla handler accepts a move from 2 m before the line to 2 m after it. The next movement processed by the mod runs from 2 m after the line to 3 m after it. Neither endpoint of that checked segment is on the approach side, so the crossing is never recorded. This happens without checkpoint or height rejection and without a driver change. It also happens at zero scheduled delay: latency is not required for the authority integration defect, although client/server prediction divergence and packet timing can exercise the unchecked position-change path.

The tests use real `ServerPlayer` instances registered in the level's chunk tracker, complete the mount-teleport acknowledgement, and execute the unmodified inbound vanilla handler. Only outbound traffic is replaced by a sink. Listener vehicle-reference bookkeeping is initialized to the state normally established by its tick. The trajectory and packet arrival schedule are controlled; this is not a full TCP/Netty latency emulator, and physics does not continue advancing during the scheduled wait. Drive-input controls exercise the production `applyDriveInput` method directly, rather than a custom-payload channel handshake. These tests establish that a valid accepted position packet can bypass recording; they do not measure a natural race's missed-lap rate.

A repair needs to give movement a consistent authority: either prevent vanilla client vehicle-position packets from applying to these server-simulated cars, or capture every accepted authoritative movement path with correct timing and duplicate suppression. Changing only detector width/height would leave this bypass intact. The implemented repair observes final accepted motion without changing movement authority; see the current reliability document.

## Running the tests

```sh
./gradlew build --offline
./gradlew runLapCaptureTestServer --offline
```

The second command is a **strict regression test**. It currently fails because the campaign reproduces lost lap records and checkpoint rejection. This is separate from the normal `build` checks. To run the complete diagnostic campaign while allowing the documented defects:

```sh
./gradlew runLapCaptureTestServer --offline -PlapCaptureStrict=false
```

Diagnostic mode still checks the normal control, fixture integrity, and the native-physics baseline. Its successful exit is not a claim that all capture scenarios passed. Both modes write:

- `build/lap-capture-gametest/lap-capture-summary.csv`: expected crossings/laps, actual valid and total records, invalidation reasons, and scenario parameters.
- `build/lap-capture-gametest/lap-capture-events.csv`: server ticks and positions at independently expected crossings and actual record insertions.

The server world and runtime output stay under `build/lap-capture-gametest/`, separate from `run/` and production saves. Seed: `1369957744` (`0x51A7E570`). Minecraft's GameTest framework chooses the fixture's world coordinates independently of this seed, so the campaign also exercises different large positive/negative coordinates between runs.

## What is simulated

The campaign runs 7,680 server ticks, with up to 24 cars at once. Five stages replay deterministic 20 Hz rectangular-circuit trajectories on real registered car entities in a running NeoForge server. These replace the force solver with known positions, then invoke the same production movement-to-record pipeline used by ordinary driving: attempt start, distance accumulation, telemetry sampling, configured-line scanning, block discovery/merging, steward checkpoint processing, and synchronous SavedData record insertion. The independent expected-crossing oracle uses circuit distance, not the detector under test.

The final stage runs ordinary `OpenwheelCarEntity.tick()`, including vehicle forces, terrain/ground collision, lap timer checks, and detection. It repeatedly supplies an upstream coasting velocity and resets the car upstream between passes; it is a controlled straight-line physics baseline rather than an autonomous full-circuit race.

The whole fixture is held loaded because mock riders do not carry real-player chunk tickets. Dummy riders have explicit invulnerability: GameTest's creative mock overrides the mode but does not initialize creative abilities. Every replay asserts that its rider remains seated, advances, and completes multiple circuits. This prevents chunk unloading, dummy-player deaths, or non-ticking entities from being counted as detector failures. Timed-record comparisons exclude the initial start-line crossing and retain invalid attempts separately.

An additional `LapCaptureGeometryStressTest` runs 50,000 seeded production `TrackGeometry.crossing` cases spanning world coordinates up to +/-29 million blocks, arbitrary headings, sloped line endpoints, lateral positions, 3.6–432 km/h movement, and fractional tick phases. This checks intersection math; it does not substitute for the live-server pipeline tests.

## Observed results on 2026-11-02

| Scenario | Expected valid laps | Recorded valid laps | Interpretation |
|---|---:|---:|---|
| 24 cars, configured virtual line, 112.5–360 km/h, 0–0.8 m lift and +/-25 degree heading error | 444 | 444 | No loss in this control |
| Virtual line, a 0.3 m lift pulse on circuit 13 | 15 | 15 | This mild lift is accepted |
| Ground marker blocks, the same occasional 0.3 m pulse | 15 | 14 | 6.7% timed-record loss reproduced |
| Two steward checkpoints 1 m apart, crossed within a 5 m movement tick | 186 | 0 | All corresponding attempts recorded as invalid: `missed checkpoints` |
| Same checkpoint arrangement, 0.5 m per tick control | 18 | 18 | No loss |
| Actual coasting physics on a flat asphalt floor | 90 | 90 | No loss in this physics baseline |

The imposed lift frequency deliberately creates an occasional failure. The resulting 6.7% is a synthetic reproduction, not an estimate of the production server's failure probability.

### Ground marker discovery loses slight airborne crossings

`scanVirtualMarkerLines` searches blocks inside the car's swept bounding box, inflated downward by only 0.2 m. With marker blocks in the road, both endpoints of a movement interval more than 0.2 m above the road can exclude the marker's block layer from the search. The block is never passed to the crossing geometry, even where that geometry's vertical overlap test would accept it.

The campaign captures all laps at 0.19 m lift and none at 0.21 m for these ground marker blocks. The occasional smooth 0.3 m pulse yields one missing timed record out of fifteen completed laps. The following recorded interval spans the missed crossing. No saved survey route is supplied in this fixture, so telemetry-based lap splitting cannot hide the initial loss.

### Configured virtual lines have a different height rule

`scanTrackStartFinishLine` uses swept X/Z intersection and compares the interpolated car origin with the line's interpolated height, accepting an absolute difference up to 2.5 m. It does not require physical contact with the line. With the line authored at the road block's base and the resting car one block above it, the remaining upward margin is about 1.5 m. The height sweep captures 1.49 m added lift and rejects 1.51 m. These thresholds depend on the line's actual authored height.

A configured line also suppresses start/finish marker blocks. Therefore, the 0.2 m block-discovery defect cannot explain a missed finish when that configured line is the detector in use.

### Multiple steward checkpoints in one tick

`scanStewardTimingLines` dispatches only the earliest crossed steward line in each movement tick. With required checkpoints at 18 m and 19 m along the outgoing straight, a 5 m tick crosses both, but only one is marked visited. At the next finish, checkpoint validation rejects the lap. The same geometry succeeds at 0.5 m per tick.

This is a valid-lap loss, not a missing raw record: the rejected attempts exist with reason `missed checkpoints`. It matters only when checkpoint checking is enabled. Configured finish scanning also precedes checkpoint dispatch, so a final required checkpoint very close to the finish needs a separate travel-order regression before that path is considered robust.

### Car heading is an independent rejection gate

The configured finish requires both forward movement and a car-forward dot product above `0.35`, approximately a 69.5 degree heading limit. The yaw sweep records +/-69 degree crossings and rejects +/-70 degrees as `reverse pass`, even while the trajectory moves forward. This can affect cars sliding or spinning across the line; it is not evidence that ordinary aligned passes are lost.

## Limits and next diagnosis

The current tests reproduce two concrete mechanisms and distinguish absent records from invalid records. They do not identify the cause of a particular race without its detector configuration, checkpoint settings, and crossing traces. They do not simulate client networking/lag, a populated real circuit's terrain over a full physics lap, marshal teleports, driver changes, or server restarts during a crossing. Off-track validation is disabled in the controlled campaign to keep it separate from capture reliability.

The original investigation extracted the movement-to-record pipeline without changing detection. The subsequent fix now observes accepted vehicle packets and adds continuous coverage recovery. The older campaign remains red in strict mode on the separate physical-block and checkpoint defects.

## Follow-up fixes for server trial (2026-11-14)

The historical marker-discovery and multi-checkpoint failures described above
were repaired: marker search height follows the existing crossing support, and
all crossed steward lines are dispatched in movement order. The marker height
sweep now expects the pre-existing geometry to accept up to the tested 0.5 m
lift, rejecting the 0.8 m and higher cases. This adjusts the oracle to the
geometry contract rather than the old discovery truncation. Final strict
campaign results are recorded in the server-test readiness notes.
