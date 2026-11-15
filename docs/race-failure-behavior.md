# Race and qualifying failure behavior

`RaceFailureBehaviorSmokeTest` drives the production classification engine with 12 cars, 80 m/s, a 1,000 m closed route, 10 m spacing, and 120 seconds at 20 Hz. Ground-truth order never changes. Faults affect the leading driver unless specified. It compares position-only classification, the automatic hybrid (position progress plus recorded-lap floor), and race continuity with that hybrid. These are deterministic injected faults, not measured real-world failure probabilities or a complete Minecraft/network simulation. Real packet-handler and survey GameTests are documented in `lap-recording-reliability.md`.

Reproduce with `./gradlew test --tests '*RaceFailureBehaviorSmokeTest'`. Outputs are `build/reports/race-failure-behavior.csv` and `race-failure-trace.csv`. The latter retains per-half-second leader evidence so recovery behavior can be inspected rather than reduced to a success percentage. Summary includes final count errors, cumulative under/overcredited car-ticks, wrong-leader duration, longest continuous wrong-leader interval, largest single-update rank jump, missing rows and uncertain rows.

## Observed behavior

| Injected fault | Position-only result | Automatic hybrid result |
|---|---|---|
| Control, distributed 10% timer misses, duplicate cumulative timer updates | Correct counts and order | Correct counts and order |
| Two-second untracked interval | Counts recover; false leader for 1.9 s; largest jump 11 places | Same |
| Fifteen-second untracked interval, including a complete lap | One missing lap; false leader for 112.45 s through end of trial | Final count recovers; false leader for 14.85 s total; largest jump 11 places |
| Fifteen-second missing entity or ambiguous localization | Same failure as full-lap untracked interval | Same recovery and order instability |
| Two-second implausible +450 m localization jump | Rejected progress; counts correct, but false leader for 1.9 s | Same |
| Timer notifications delayed one second | Correct counts and order | Correct counts and order |
| Timer misses lap 1; position misses lap 3 | One missing lap | Still one missing lap despite matching source totals |

Missing entities remain as stale classification rows in these short trials; they do not disappear. Existing engine behavior removes a continuously missing participant after 6,000 ticks (five minutes), covered by `permanentlyMissingParticipantRetiresAfterFiveMinutes`. That is a separate race continuity risk: a missing observation should not silently mean retirement. This test does not claim to fix that behavior.

The 11-place jump occurs on reacquisition. During the gap the frozen leading car is overtaken by successive moving rows, then returns to the front in one update. Four-tick sorting hysteresis does not solve this: a sustained sensor fault outlasts the hysteresis. Correct final totals therefore do not imply acceptable live classification.

The mixed-miss test is particularly important: equal cumulative counters do not prove agreement on individual passages. Neither taking their maximum nor adding their totals reliably repairs that case.

## Preferred behavior by session

For a race, retain automatic provisional lap credit from the hybrid rather than stalling on an unavailable source. Retain the participant and last credible order during an observation gap, clearly mark that order as stale/provisional, and permit stronger finish/lap evidence to update it. Do not extrapolate an unseen car across the finish simply because its previous speed predicts it. A held order can be temporarily wrong after a genuine overtake or crash; that visible uncertainty is preferable to silently presenting sensor loss as confirmed overtaking. Reacquisition can still require a large genuine correction, so continuity must never conceal disagreement or override a proven finish. The implemented bounded policy is tested with a genuinely stopped car being overtaken; broader multi-car battles remain outside this deterministic campaign.

For qualifying, the primary result is an independently timed attempt, not track order. A missed attempt is undesirable, but a false personal best or a wrong driver/time association is worse. Keep the existing tested precise geometric recovery eligible; route-only estimates remain visible race evidence and are excluded from qualifying bests. Do not fill a missing qualifying time by averaging adjacent laps or extrapolating route progress. Existing accuracy trials show route-only errors up to about 119 ms at 2 Hz, whereas geometric recovery matches the primary saved millisecond timestamp in 50,000 trials.

Reporting should be automatic and nonblocking: show provisional total, source disagreement, detection confidence, last reliable observation and gap window, with existing director +/- lap correction available. Preserve the fact that a gap occurred even if current source totals agree. A report cannot unambiguously label which historical lap is missing from aggregate counters alone; passage identities would be needed for that claim. The initial study recommended those reporting/continuity changes. The bounded implementation below provides confidence markers and detection-gap logs without changing saved formats; a full passage-identity audit remains unimplemented.

## Implemented race continuity and interval display

Race continuity is now enabled for standalone timing and race sessions, and disabled for practice/qualifying. Uncertain participants retain their previous slot within the same completed-lap group; reliable cars continue sorting in the other slots. Proven lap credit can move a car between groups immediately. When reliable observations resume, genuine overtakes take effect. Missing race participants remain stale rows rather than silently retiring after five minutes. Existing explicit participant eligibility/retirement filtering still controls which cars are observed.

The expanded smoke campaign compares all three policies. In the continuity policy, the short dropout, full-lap dropout, missing entity, ambiguity and implausible route jump now produce **zero wrong-leader ticks and zero rank jumps** in this constant-order fixture, with no final count error. Reconciliation updates ordering immediately, removing the one-tick lag after recorded credit. The mixed-source-miss case still undercounts one lap and remains explicitly documented; continuity is not passage reconciliation. Separate tests verify genuine overtakes after reacquisition, confirmed finish credit overriding a held order, and continuous 125 ms intervals across multiple finish crossings.

`/owrgap interval` (default) shows time to the car ahead; `/owrgap leader` shows time to the leader; `/owrgap` toggles. The choice is saved in the client wheel/HUD settings file. Both use timestamp differences at shared route references, including across the finish, rather than the difference between current displayed lap numbers. Time is preferred whenever shared references remain available, even for a lapped car; a lap gap is the fallback when reference history is unavailable. Uncertain or contradictory ordering shows an unavailable gap, never a fabricated zero. The tower labels its selected mode and retains the existing `~`, `?`, `!` confidence markers.

Detection gap start/recovery events are logged automatically with session, driver UUID, tick window and provisional count. No confirmation is required. The historical log entry remains after current confidence recovers; runtime gap state is not a new saved-data schema.

## Legacy world selection recovery

A codec round-trip regression reproduces an old global selection pointing at another dimension: the overworld track, finish line and survey identity can all remain saved, while `activeTrack("minecraft:overworld")` is empty. Startup now recovers a missing/foreign active selection when exactly one local track has a finish line. It preserves an explicit local selection and does not guess among multiple timing tracks. No line geometry, survey data or track identity is rewritten.

`/owr steward active` is now read-only and reports the finish endpoints, heading and height tolerance alongside survey status. It no longer silently creates/selects a track while diagnosing missing selection. Use `/owr steward list` and `/owr steward select <track UUID>` when multiple candidates require selection. This is a verified migration failure path, not a confirmed diagnosis of a particular server without examining its saved world. Old line orientation, height and missing data remain separate possible causes.
