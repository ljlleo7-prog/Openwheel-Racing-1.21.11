# Race lap reconciliation: bounded fix and proposed event reconciliation

## Verified bounded correction

The current patch keeps the saved-data format unchanged. When a recorded lap raises the race count, the classification engine now anchors route progress at the finish instead of retaining the previous localized position. The next observed route wrap therefore cannot add that same passage again. A participant that has only produced untracked observations can receive recorded lap credit before its first valid localization. Reference-gap interpolation waits for fresh timestamped observations after this anchor.

The initial regression reproduced `recorded finish -> old pre-line position -> tracker wrap` as two laps for one passage. The bounded correction returns one. Additional tests cover tracker-first ordering, repeated record reconciliation, a full untracked interval, never-localized participants, multi-lap record floors, director adjustments, and saving/restoring the finish anchor through the existing checkpoint format.

## Remaining limitation: different missing passages

The two cumulative totals do not identify which laps each source saw:

| Real passage | Position total | Recorded total | Aggregate race count |
|---|---:|---:|---:|
| Lap 1: position only | 1 | 0 | 1 |
| Lap 2: both | 2 | 1 | 2 |
| Lap 3: recorder only | 2 | 2 | 2 |

Three real passages exist, but the aggregate maximum is two. Adding the two totals would instead double-count their shared Lap 2. The diagnostic `aggregateFloorsCannotIdentifyDifferentMissingPassages` demonstrates this limitation explicitly; it is not a claim of complete recovery.

## Broader persistent reconciliation design (not implemented)

Consume individual recorded finish identities and position-crossing evidence through one per-driver/session passage reconciliation layer. A timer notification should carry the record ID and crossing tick, not only the current record count. A position observation should acknowledge an already credited finish when appropriate, rather than independently adding it again.

Persist the following additional checkpoint metadata:

- A race-start record-ID baseline, excluding qualifying or pre-race records.
- Each participant's last consumed record ID, so repeated delivery, record deletion and checkpoint replay cannot fabricate a new passage.
- A bounded passage history identifying position evidence, record evidence, event time and whether each passage has already received race credit.

Keep manual director adjustments separate. Restore a checkpoint's existing totals and adjustment, then merge only demonstrably new evidence. For older checkpoints without identities, preserve their counts and initialize a conservative record baseline; do not replay all historical laps or silently reconstruct their past. Existing incorrect counts would still need director correction.

Matching needs both temporal/route-phase evidence and explicit handling of untracked reacquisition. Ambiguous historical gaps should be reported instead of inventing a lap. Validate interleaved missing passages, source ordering, stale/repeated events, long untracked periods, grid-start crossings, director corrections, and checkpoint/record save ordering before deployment.

Automatic approval review rejected executing this broader persistent-ledger rewrite because its classification, saved-state and migration effects were not specifically authorized by the duplicate-lap check. The draft engine rewrite was removed. The active patch contains only the bounded correction and regression coverage described above.

## Automatic provisional policy and behavior smoke tests

The requested default is automatic provisional classification with report-only uncertainty; steward confirmation is not a prerequisite. See `race-failure-behavior.md` for fault-injection results and the different race/qualifying tradeoffs. Persistent reconciliation remains a separate unimplemented design; no save-format changes are included.
