# Accepted-motion detection and continuous lap coverage

## Movement and finish detection

The client continues to predict and drive as before. Vanilla vehicle-position packets keep their existing validation and movement behavior. A server mixin observes the car before `handleMoveVehicle` and checks its final position on return. Rejected packets are restored by vanilla and therefore contribute no movement; temporary collision-test positions are never counted. Handler entry on the network thread does not touch the timing state.

Accepted packet movement and ordinary physics both feed the movement-to-record pipeline. Exact detection uses the committed before/after positions, including when a physics velocity adjustment leaves a zero reported residual velocity. Purely vertical accepted updates update continuity without inventing a line crossing. Unobserved position changes and large discontinuities clear the attempt/coverage instead of treating a teleport as a finish.

A conservative swept X/Z bound, expanded for the car body, rejects distant movements before exact intersection work. It considers both endpoints, so a fast movement that crosses the whole finish region is retained. With a configured line and checkpoint checking disabled, physical marker-block enumeration is skipped entirely. Survey coverage still samples movements around the circuit because that evidence is necessary for compensation.

A shared finish-region latch prevents same-tick or next-tick packet corrections, line oscillation and the physics path from crediting one passage twice. It rearms after the car has left the finish region; rearming uses the movement's previous position so a single correction across the region cannot simultaneously rearm and recross.

## Continuous interval coverage

`ContinuousLapCoverage` stores a sorted union of continuous distance intervals on the surveyed route, with the configured finish location as zero. There is no section grid, bitmap, or discretized coverage count.

- Forward and reverse movement add the interval actually traversed. Repeated intervals merge and contribute no extra length.
- Wrapped intervals split at zero/route length.
- Recovery requires at least 90% distinct interval length and a forward near-end to near-start seam transition.
- Ambiguous/untracked mappings and long sample gaps leave holes. They do not connect distant route positions or erase otherwise valid intervals.
- World-space movement, elapsed ticks, and route displacement must be mutually plausible. Discontinuities clear accumulated evidence.
- Coverage is reset for each credited passage and for route/line, driver, session, recovery or teleport discontinuities.

The localizer and interval union are independent of the racing-line learner and its ordered profile coverage. Coverage tolerates a wall return, reversal or spin. Compensation is enabled only with a saved survey and configured finish line, while checkpoint checking is disabled.

The geometric detector gets first refusal. A route candidate can wait four ticks for a nearby geometric crossing; a candidate still on the approach side is not credited. A later exact crossing and a pending recovery merge through the shared passage state rather than adding two laps. Route-only estimates are committed after the short wait when the car is on the departure side.

## Timing and qualifying eligibility

Recovered records retain two persisted flags in their car snapshot:

- `recovered_timing`: this interval was recovered.
- `estimated_timing`: at least one boundary lacks precise geometric timing.

A recovered lap **can establish a qualifying best** when both its start and finish have actual geometric crossing times. Recovery of a missed height/heading detection can use the committed movement segment's intersection with the configured line and the same `LapTimingClock` used by normal recording. Continuous coverage supplies independent evidence that a circuit was completed; it does not replace that precise timestamp with survey interpolation.

A route-only timestamp, sampled grace start, or interval beginning at an earlier estimated boundary stays race credit only. These records do not enter best-lap/profile rankings. The first precise boundary after an estimate restores a precise start for the following lap, without retroactively claiming accuracy for the preceding interval. Manual revalidation cannot silently convert an estimated timestamp into an accurate qualifying result.

Estimated records use the existing invalidated state for qualifying exclusion, but `getRaceSessionLaps` includes their explicit estimated timing metadata. Live count reconciliation and checkpoint recovery use that race-credit collection, preserving director lap adjustments. Race Director and All Lap Records distinguish precise recoveries from estimated ones; estimates are amber and cannot use the revalidate action. The protocol version is 23, so update server and clients together.

## Measured accuracy

`LapRecoveryAccuracyTest` benchmarks survey-only seam interpolation against the existing **20 Hz geometric crossing clock**, rather than claiming physical one-millisecond truth. The experiment includes straight routes, 30 m/100 m radius curves with approximately 4 m survey spacing, 50/100/200/360 km/h speeds, +/-3 m lateral offsets, 0/15 degree finish skew, 0/5 m/s² acceleration and 64 sample phases.

| Sample rate | Trials | Accepted seam estimates | Worst crossing discrepancy | Worst centered/perpendicular discrepancy |
|---|---:|---:|---:|---:|
| 20 Hz | 9,216 | 9,216 | 71.429 ms | 0.026 ms |
| 10 Hz | 9,216 | 9,216 | 71.547 ms | 0.443 ms |
| 4 Hz | 9,216 | 8,840 | 71.386 ms | 2.721 ms |
| 2 Hz | 9,216 | 7,950 | 118.796 ms | 69.010 ms |

The straight/perpendicular 20 Hz control agrees with the primary interpolation. Lateral position plus finish skew creates systematic error that a higher sample rate does not remove. Oversized/unreliable projected seam steps are excluded from accepted estimates. These results are why route-only estimates cannot universally establish qualifying best times.

A separate 50,000-trial geometric-backed test varies headings, body-expanded versus original line endpoints, movement fractions, speed and world coordinates up to +/-29 million blocks. Saved lap times differed by **0 ms** from the primary geometric clock; the largest numerical boundary difference was about `0.0000011 ms`. This establishes consistency with the existing clock, not an absolute physical precision guarantee.

End-to-end server trials compare actual stored records with known circuit times. Precise movement-backed recoveries agree with their control times, while route-only recovery and estimated-start intervals remain excluded from qualifying. Tests also cover insufficient coverage, short localization dropouts, wall returns/reversals, spins, teleports, packet jitter, and recovery followed by normal detection.

## Validation and artifacts

```sh
./gradlew build --offline
./gradlew runLapNetworkTestServer --offline
./gradlew runSurveyCompensationTestServer --offline
./gradlew runLapCaptureTestServer --offline -PlapCaptureStrict=false
```

- `build/lap-network-gametest/lap-network-summary.csv`: accepted packet crossings, input delays, rejected collisions/excessive movement, backward/duplicate/ignored packets, vertical updates, distant motion and fast swept crossings.
- `build/survey-compensation-gametest/survey-compensation-summary.csv`: expected race laps, qualifying eligibility, recovery source, best times and saved timing differences.
- `build/reports/lap-recovery-accuracy.csv`: 36,864 survey-only timing trials grouped by sampling and geometry.
- `build/reports/lap-geometric-recovery-accuracy.csv`: geometric-backed clock comparison.

These tests do not replace an interactive multiplayer driving-feel test or emulate a complete TCP session under latency. Driving authority and collision resolution are not changed by this timing fix. The older diagnostic campaign also contains physical-block height and multi-steward-checkpoint defects outside the configured-line/no-checkpoint configuration; its strict mode remains red on those cases.

## Cross-source count reconciliation follow-up

A recorded lap that raises the race count now anchors position progress at the finish, preventing a later tracker wrap from crediting that passage again. The existing checkpoint format is unchanged. Aggregate totals still cannot combine disjoint missing passages perfectly; see [verified correction and proposed event reconciliation](race-lap-reconciliation-proposal.md).
