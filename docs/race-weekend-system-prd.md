# Product Requirements Document: Configurable Race Weekend System

**Version**: 1.0  
**Date**: 2026-08-15  
**Author**: Sarah (Product Owner)  
**Quality Score**: 94/100

---

## Executive Summary

Openwheel Racing has strong foundations for solo lap timing, lap invalidation, track geometry, race-control settings, telemetry, and best-lap display, but it does not yet have an authoritative multi-car race system. Existing “live” rankings represent best laps rather than race order, current terminal row rank is display order, flags are global state without complete procedures, and there is no stable entry roster, grid/start workflow, race classification, session lifecycle, penalty ledger, or multi-car gap engine.

This feature will create a server-authoritative, director-operated race platform. A configurable event builder will compose zero or more free-practice sessions, one of three qualifying formats, an optional sprint, and a main race. Sessions will share one timing, classification, stewarding, and result architecture so timed races, lap-count races, qualifying, and practice do not develop conflicting rules.

The first playable milestone is the **race core**, not merely a schedule UI: registered entries, reliable order and gaps, grid staging and start, session state, qualifying formats, sprint/race formats, global flags, incident review, provisional results, and director officialization. Complete weekend orchestration follows once those foundations are proven.

---

## Product Goals

1. Run an organized multiplayer race whose live order, gaps, finish state, and official result remain trustworthy through overtakes, lapping, penalties, disconnects, an empty server, and process restart.
2. Let a race director build a weekend from a blank configuration rather than imposing an F1-specific schedule.
3. Support multiple racing formats through shared domain logic and explicit format policies.
4. Give drivers a compact, actionable timing tower while giving directors a detailed operational and stewarding view.
5. Preserve current time-trial and best-lap functionality without misrepresenting it as race classification.
6. Make sessions feel temporally distinct by locking each one to its configured Minecraft time of day.

### Success Metrics

- Deterministic classification: identical timing events produce identical classifications across restart/reload tests.
- Correct order: all tested normal, lapped, simultaneous-crossing, retirement, reconnect, and finish scenarios produce the expected order.
- Timing stability: no visible position oscillation on a valid surveyed route under normal multiplayer movement.
- Operational completeness: a director can configure, run, suspend, finish, review, officialize, and advance a complete event without commands or data-file edits.
- Result integrity: an officialized result is immutable unless deliberately reopened by an authorized director action, which is audited.
- Scale target: support at least 24 registered entries without per-view entity scans becoming the source of timing truth.

---

## Users and Authority

### Race Director

Configures the event, registers entries, controls session progression, assigns grids, operates global flags, reviews incidents, applies or removes penalties, corrects provisional classifications, and officializes results.

### Driver

Occupies a registered entry, participates in active sessions, receives session/flag/penalty information, and sees position, leader gap, interval, lap or remaining-time state, and qualifying information.

### Team/Observer

Uses the team terminal or race monitor to view timing and permitted telemetry without gaining race-control authority.

### Authority Model

- The dedicated/integrated server is authoritative for clocks, crossings, completed laps, order, penalties, flags, session state, and results.
- Clients render snapshots and may interpolate display values but never decide classification.
- Director actions require server-side permission checks and create audit-log entries.
- Automated systems detect objective events and candidates; the director adjudicates incidents and officializes results.

---

## Readiness Assessment

### Ready or Reusable

- Persistent lap archive, session best laps, splits, and invalidation in `OWRLapRecords`.
- Start/finish, checkpoint, wrong-way, off-track, and steward-line primitives in `OpenwheelCarEntity`.
- Persistent global race-control options in `OWRRaceControlState`.
- Track centerline, boundaries, sectors, grid slots, AI points, and steward-line definitions in `TrackDefinition` and `TrackGeometry`.
- Best-lap profile sampling and personal live delta.
- Race director/team terminal menus, car telemetry, map snapshots, and broad payload registration.
- Global green, yellow, red, VSC, and safety-car flag modes.
- A non-competing safety-car entity foundation.

### Partial Foundations Requiring Refactoring

- Named lap-record sessions are untyped and have no lifecycle or format rules.
- The HUD “LIVE” board ranks session-best laps, not current race order.
- Team terminal `liveRank` is a row/display index, not classification.
- Route projection can estimate progress but is not connected to authoritative multi-car timing.
- Survey localization explicitly reports tracked, low-confidence, ambiguous, and untracked states; race order must preserve this uncertainty instead of silently reordering cars.
- Grid slots can be authored but are not assigned, staged, locked, started, or monitored for jump starts.
- Flags can be selected but lack complete enforceable procedures and incident integration.
- Pit service and limiter primitives exist, but sporting pit rules do not.

### Blocking Prerequisites

1. Stable event entry identities independent of currently loaded car entities or online players.
2. A dedicated, persistent race-event/weekend state separate from lap archives and generic race-control settings.
3. A central timing and classification service consuming line-crossing and progress events.
4. Explicit session state machines and finish semantics.
5. Grid assignment, staging, countdown/start lights, release, and jump-start detection.
6. Persistent session results, statuses, penalties, incidents, and officialization state.
7. New server snapshots and client views for race classification rather than repurposing best-lap payloads.
8. Automated tests for multi-car order, formats, finish rules, persistence, and recovery.

### Important Risks in Existing Foundations

- `RaceDirectorMenu` scans loaded entities and caps rows; view-layer scanning must not become the race timing engine.
- Route projection can be ambiguous at crossings, parallel sections, pit lanes, or incomplete survey geometry.
- Car-local lap completion currently writes directly to lap records; weekend logic needs one authoritative consumer without breaking time-trial recording.
- Best-lap ranking and live race classification must have distinct names, payloads, and UI modes.
- Adding weekend state to `OWRLapRecords` or `OWRRaceControlState` would over-couple archives, regulations, and active event state.

**Readiness conclusion:** the project is ready to begin the prerequisite race core, but it is not ready to implement the weekend scheduler directly. Doing so first would create configurable sessions whose positions, finish states, grids, and results are not authoritative.

---

## Scope and Configuration

### Blank Event Builder

A new event starts with no sessions. The director may add, remove, and reorder supported sessions while the event is in draft. Configuration becomes locked when the event opens; reopening requires an explicit audited action and must be prohibited once dependent official results exist unless those results are invalidated.

### Supported Session Composition

- Zero or more independently named free-practice sessions.
- Zero or one qualifying session using timed, one-shot, or two-shot format.
- Zero or one sprint.
- Zero or one main race.
- The event builder should validate dependencies, such as a grid source referencing an existing, officialized result.

### Time Representation and Session Conditions

- Store sporting duration in Minecraft ticks for deterministic server execution.
- Display and edit durations as minutes and seconds.
- Independently configure staging/countdown, active-session duration, and any result-review hold; session advancement remains manual.
- Each session configures a Minecraft world time and locks that time for the entire session, providing consistent lighting and distinct practice, qualifying, sprint, and race conditions.
- The sporting clock is independent of world time and daylight; changing or locking world time cannot advance, pause, or reset session timing.
- When the last player leaves, automatically transition an active session to `SUSPENDED_EMPTY_SERVER`, persist its exact state, and freeze its sporting clock.
- Rejoining does not automatically resume the session. An authorized director must explicitly resume it after entries and cars are rebound or restored.

### Free Practice

Each practice session has:

- Name.
- Configured duration.
- Optional entry eligibility subset.
- Track-limit/checkpoint enforcement settings inherited from event rules with optional permitted overrides.
- Best-lap classification.

Practice results do not determine a later grid unless explicitly selected as that session’s grid source.

### Qualifying

#### Timed Qualifying

- Open track for a configured duration.
- A lap started before time expires may be completed, subject to a configurable maximum grace period.
- Classification uses each entry’s best valid lap, then the defined tie-break order.

#### One-Shot Qualifying

- Each eligible entry may complete up to one valid timed lap during the open qualifying session.
- Invalid laps do not consume the valid-lap allowance.
- The session remains open until the director finishes it or all eligible entries have reached their valid-lap allowance.
- Traffic remains possible; it is not a sequential solo-run format.

#### Two-Shot Qualifying

- Same as one-shot, with up to two valid timed laps per eligible entry.
- Classification uses the best valid lap.
- Invalid laps do not consume the allowance.

For both shot formats, the HUD and director monitor show valid laps completed and allowance remaining. Because invalid laps do not consume the allowance, the configured/director-controlled session end prevents an entry from circulating indefinitely.

### Sprint and Main Race

Sprint and main race use the same race engine and independently configure:

- Lap-count or timed format.
- Lap count or duration.
- Grid source and optional reversible director adjustments.
- Eligible entries.
- Enabled regulations and modifiers.
- Whether results may feed a later grid.

No championship points model is required for the first milestone.

---

## Core Domain Model

The names below are conceptual and may be adapted to repository conventions.

### `RaceEventConfig`

- Event id and display name.
- Track definition id/version reference.
- Ordered session configurations.
- Entry rules and sporting-rule configuration.
- Configuration revision and lifecycle state.

### `RaceSessionConfig`

- Stable session id, name, and type: practice, qualifying, sprint, race.
- Format policy: timed practice, timed qualifying, valid-lap-limited qualifying, timed race, or lap-count race.
- Duration/lap limit, countdown, grace period, eligibility, grid source, and regulation overrides.
- Locked Minecraft world time for the session.

### `RaceEntry`

- Stable entry id.
- Driver UUID and display snapshot.
- Optional team identity.
- Bound car identity/snapshot.
- Eligibility and lifecycle status.
- Reconnect/rebind state.

Safety cars, track vehicles, observers, and unregistered cars never become classified entries.

### `RaceSessionState`

Suggested lifecycle:

`CONFIGURED -> OPEN -> STAGING -> COUNTDOWN -> RUNNING -> SUSPENDED -> FINISHING -> PROVISIONAL -> OFFICIAL`

Suspension records a reason such as `DIRECTOR`, `RED_FLAG`, `EMPTY_SERVER`, or `SERVER_RECOVERY`; an empty server or process reload must never be represented as normal running time.

Additional terminal state: `ABANDONED`.

Not every session uses every state, but transitions must be explicit, validated, persisted, and audited. Moving to the next session is always a director action.

### `EntrySessionState`

- Grid position and start status.
- Completed laps, current lap, checkpoint/sector state, progress confidence.
- Best valid lap and qualifying allowance usage.
- Running, finished, DNF, DNS, DSQ, or not classified status.
- Finish timestamp/order.
- Applied and pending penalties.

### `SessionResult`

- Provisional/official revision.
- Ordered classified and unclassified entries.
- Session-specific timing fields.
- Fastest laps and sector data where applicable.
- Penalties and status reasons.
- Grid derived from the result, if any.
- Audit metadata for corrections and officialization.

### `IncidentRecord` and `PenaltyRecord`

Incident candidates preserve evidence and detector confidence. Penalties preserve type, value, reason, issuer, timestamp, serving state, and classification effect.

---

## Timing and Classification Requirements

### Authoritative Event Stream

The timing service consumes server-side events rather than scanning entities to reconstruct history:

- Entry/car bind and unbind.
- Start/finish crossing with direction and timestamp.
- Checkpoint and sector crossing.
- Steward-line crossing.
- Pit entry/exit and limiter state where available.
- Route-progress samples with localization confidence.
- Driver disconnect/reconnect.
- Flag and session transitions.
- Incident and penalty actions.

`OWRLapRecords` remains the lap archive and time-trial/best-lap store. The race service owns event classification and may publish completed valid laps to the archive through a defined integration boundary.

### Race Order

Running race order is determined by this priority:

1. Higher completed-lap count.
2. Greater valid route progress on the current lap.
3. Most recent authoritative timing-line order when progress is ambiguous or within a defined tolerance.
4. Stable previous order until stronger evidence arrives.

Finished cars are ordered by finish crossing, then penalties. DNS, DNF, DSQ, and not-classified rules are explicit and separate from running position.

### Gaps and Intervals

- **Gap**: time to the leader when a trustworthy historical crossing/progress comparison exists; otherwise display lap deficit or unavailable state.
- **Interval**: time to the classified car immediately ahead; otherwise lap deficit or unavailable state.
- Do not convert geometric distance directly to time using instantaneous speed.
- Prefer interpolated timestamps at shared route reference points.
- Display lapped states as `+N LAP(S)`.
- Low-confidence/ambiguous localization must retain stable order and mark timing data unavailable or estimated; it must not cause oscillating overtakes.
- Client interpolation smooths display only and cannot alter server order.

### Overtake Events

- Emit a likely overtake when two running entries exchange authoritative order and the new order persists beyond a debounce window or is confirmed at a timing reference.
- Record participants, old/new positions, location/progress reference, flag state, lap, timestamp, and confidence.
- Show position changes and event history.
- Do not automatically judge legality in the first milestone.

### Tie-Breaking

Qualifying equal best laps are resolved by:

1. Earlier completion of the equal lap.
2. Better second valid lap when both have one, then subsequent valid laps in order.
3. Director-assigned deterministic order if still tied.

Race ties use authoritative finish crossing order. If indistinguishable within timestamp precision, preserve pre-line order and flag the result for director review.

---

## Grid and Start Procedure

1. Select a grid source: official qualifying result, official sprint result, another permitted official session, manual order, or configured entry order.
2. Materialize a `GridAssignment` so later source edits cannot silently alter a staged grid.
3. Allow audited director adjustments before staging.
4. Validate sufficient authored grid slots and report missing/duplicate/unsafe assignments.
5. Stage or teleport bound cars to assigned slots, align them to slot facing, and temporarily constrain movement.
6. Display session identity, grid position, countdown/start lights, and current flags.
7. Detect movement beyond tolerance before release as a jump-start candidate; do not auto-penalize.
8. Release all eligible staged entries on one authoritative server tick and record the start timestamp.
9. Entries failing to start remain eligible for director handling and may become DNS according to session closure rules.

Formation laps are out of scope for the first milestone.

---

## Race Finish and Classification

### Lap-Count Race

- The leader begins the final lap after completing the configured penultimate lap.
- The leader receives the chequered flag after completing the configured race distance.
- Each other running car finishes on its next valid start/finish crossing after the leader is chequered, including lapped cars.

### Timed Race

- Expiry marks the leader’s final-lap condition.
- The race leader finishes on the next valid start/finish crossing after time expires.
- Each other running entry finishes on its next valid crossing after the leader has finished.
- The timer cannot freeze classification at zero.

### Session Closure

- The director may wait for running entries, mark remaining entries DNF, or close after a configured finish window.
- Provisional results apply completed race distance, finish state, and classification-affecting penalties.
- The director reviews incidents and corrections, then officializes.
- A later grid may consume only an officialized result.

### Disconnect, Empty Server, and Reconnect

- The stable entry and its classification state survive disconnect.
- While at least one player remains online, the sporting clock continues and no distance is invented for a disconnected entry.
- When the connected-player count reaches zero, the transition to suspended is idempotent: freeze the sporting clock once, persist an `EMPTY_SERVER` suspension reason, and preserve the last authoritative classification.
- The director/system may rebind a returning driver to the same authorized car/entry while the session is suspended or active.
- The first returning player receives the persisted event/session snapshot; neither player login nor entity/chunk loading resumes the sporting clock.
- An authorized director resumes the session explicitly after validating entry/car bindings. The clock continues from its persisted remainder or elapsed-tick anchor, never from wall-clock time spent offline.
- If an entry does not finish, it is classified DNF at session closure using the applicable completed-distance rules.
- A disconnected driver cannot claim another entry without director authorization.

---

## Flags and Stewarding

### Initial Global Flag Set

- **Green**: normal running.
- **Yellow**: global caution state and overtake-warning context.
- **Red**: suspend racing under an explicit persisted state; restart/resume is a director procedure.
- **VSC**: global controlled-speed state; first milestone may use a clearly defined speed/delta rule but must not claim enforcement until it is testable.
- **Safety Car**: global safety-car state using the existing safety-car entity foundation; full queue, wave-by, pit, and restart regulations may be phased.
- **Chequered**: entry/session finish signal generated by finish semantics.

Local/sector yellow, blue, white, black, and mechanical flags are later enhancements.

### Detection and Review

The system should create incident candidates for objective or telemetry-supported conditions, including:

- Track limits and invalid laps.
- Wrong-way or missed-checkpoint events.
- Jump-start movement.
- Pit speed violation where reliable pit geometry exists.
- Contact events with involved entries and available severity data.
- Potential flag-state or passing violations.

Candidates do not automatically become penalties in the first milestone.

### Director Penalties

Initial penalty types:

- Warning.
- Fixed time penalty.
- Drive-through.
- Stop-go.
- Disqualification.
- No further action/dismissal.

Drive-through and stop-go require reliable pit entry/exit and serving detection before being exposed as enforceable options; otherwise they remain a later sub-phase and fixed time penalties are the operational fallback. Every decision is audited and visible to the affected driver.

---

## User Experience

### Driver Timing Tower

Context-sensitive compact display:

- Session name/type and state.
- Position and number of classified entries.
- Leader gap and interval ahead, including lap deficits and unavailable/estimated states.
- Current lap and race lap count, or remaining session/race time.
- Qualifying best lap, current-lap validity, and valid-lap allowance for shot formats.
- Global flag and final-lap/chequered state.
- Pending/applied penalty summary.
- Recent position changes/overtake indicator.

The current personal-best delta remains a separate display concept.

### Race Director Monitor

- Draft event builder and validation.
- Registered roster and car binding.
- Session controls and validated transitions.
- Grid source, assignments, adjustments, and staging status.
- Live classification with gaps, intervals, lap/sector state, confidence, pit status, and connection state.
- Flag controls.
- Incident queue, evidence summary, decisions, and penalties.
- Overtake/event feed.
- Provisional classification editor with audit trail.
- Officialize/reopen controls with confirmation.

### Team Terminal

- Live classification and permitted team-car telemetry.
- Session, flag, pit, penalty, and result state.
- No director controls unless the player also has race-control permission.

---

## Networking, Persistence, and Performance

### Networking

Use purpose-specific payloads/snapshots for:

- Event/session summary.
- Roster and binding updates.
- Grid and staging updates.
- Timing tower/classification snapshots.
- Flag/session transitions.
- Incidents, penalties, and overtake feed.
- Provisional and official results.

Send deltas or bounded snapshots at controlled cadence. Do not broadcast full historical event logs every tick. Version new payload groups consistently with `OWRNetwork` registration.

### Persistence

Create dedicated versioned saved data for active events and official results. Persist:

- Configuration and revision, including each session's locked world time.
- Roster and bindings.
- Active session/state, suspension reason, sporting-clock remainder/anchors, and last connected-player transition state.
- Entry timing/classification state sufficient for restart recovery.
- Grid assignments.
- Global flags, incidents, penalties, director actions, provisional results, and official results.

On server reload, recover into the same safe state. Any session saved in `RUNNING`, `COUNTDOWN`, or `STAGING` recovers suspended with reason `SERVER_RECOVERY`; it never consumes offline wall-clock time or silently releases cars. Reapply the configured locked world time when its dimension loads. Entity UUIDs may help restore bindings, but persisted race truth must not depend on those entities or chunks being loaded.

### Performance

- Central timing state is updated once server-side and shared with all views.
- Menus/HUDs never establish truth by independently scanning all loaded entities.
- Route localization and map containment should be cached/indexed where repeated.
- Snapshot cadence and payload size must remain bounded for at least 24 entries.

---

## Acceptance Criteria

### Event and Sessions

- [ ] A director can create a blank event and add/reorder supported sessions.
- [ ] Invalid configurations explain missing track, grid, duration, eligibility, or result dependencies.
- [ ] Configuration locks when the event opens.
- [ ] Every session can configure and lock a distinct Minecraft time of day.
- [ ] The sporting clock remains independent of world-time changes.
- [ ] Session transitions are server-validated, persisted, permission-checked, and audited.
- [ ] No session automatically advances to the next session.

### Entries and Recovery

- [ ] Only registered eligible entries affect classification.
- [ ] Safety cars and unregistered cars cannot appear in results.
- [ ] Disconnect preserves entry state; authorized reconnect rebinds it.
- [ ] The last player leaving atomically suspends an active session and freezes its clock.
- [ ] Login, chunk loading, and entity loading cannot automatically resume a suspended session.
- [ ] An authorized director can resume from the exact persisted sporting-clock remainder and classification.
- [ ] Restart/reload recovers staging, countdown, or running sessions as `SERVER_RECOVERY` suspension and preserves event, roster, session, grid, flags, incidents, penalties, and provisional/official results.
- [ ] Offline wall-clock time never consumes session duration or changes classification.

### Timing and Classification

- [ ] Running order correctly handles completed laps, route progress, lapping, simultaneous crossings, pits, and ambiguous localization.
- [ ] Gap and interval displays distinguish seconds, lap deficits, estimated values, and unavailable values.
- [ ] Position does not oscillate solely because route localization becomes ambiguous.
- [ ] Overtake events are debounced and include context without declaring legality.
- [ ] Best-lap boards and race classification remain distinct.

### Qualifying

- [ ] Timed qualifying accepts laps started before expiry within its grace rule.
- [ ] One-shot permits up to one valid lap per entry; two-shot permits up to two.
- [ ] Invalid laps do not consume shot-format allowance.
- [ ] Equal best laps use deterministic tie-break rules.
- [ ] A provisional result must be officialized before it can source a grid.

### Race

- [ ] Lap-count and timed races use the defined leader/chequered finish semantics.
- [ ] Lapped cars finish on their next crossing after the leader’s finish.
- [ ] DNS, DNF, DSQ, finished, and not-classified states are distinguishable.
- [ ] Classification-affecting time penalties are reflected in provisional results.

### Operations and UX

- [ ] Grid assignment validates authored slots and stages entries deterministically.
- [ ] Start release occurs on one authoritative server tick.
- [ ] Premature movement creates a jump-start incident candidate.
- [ ] Drivers receive session, timing, flag, and penalty information.
- [ ] Directors can review incidents, change provisional results, and officialize with an audit trail.

---

## Delivery Plan

### Phase 0 — Specification and Test Harness

- Freeze domain terminology, state transitions, ordering rules, finish semantics, and persistence boundaries.
- Build pure fixtures for multi-entry timing events and expected classifications.
- Add test scenarios for lapping, ambiguous progress, simultaneous crossings, disconnect/reconnect, last-player departure, repeated empty-server signals, process restart recovery, world-time restoration, and penalties.

**Exit criterion:** race order and session rules can be tested without launching a Minecraft client.

### Phase 1 — Authoritative Entries, Timing, and Classification

- Implement stable entries and car/driver binding.
- Implement event timing stream, progress confidence, race order, gaps, intervals, and overtake candidates.
- Integrate line/checkpoint/progress events from cars.
- Preserve `OWRLapRecords` as the lap archive through an explicit boundary.
- Add dedicated timing snapshots and replace misleading “live rank” usage where race classification is intended.

**Exit criterion:** a manually started multi-car test session produces stable live order and gaps for 24 entries.

### Phase 2 — Grid, Start, Finish, and Results

- Implement grid sources/materialization, staging, countdown/lights, release, and jump-start candidate detection.
- Implement lap-count and timed race finish semantics.
- Implement statuses, provisional classification, fixed time penalties, corrections, officialization, and persistence/recovery.
- Add driver timing tower and core director classification controls.

**Exit criterion:** a director can run and officialize a standalone lap-count or timed race.

### Phase 3 — Practice and All Qualifying Formats

- Implement flexible practice sessions.
- Implement timed, one-valid-lap, and two-valid-lap qualifying policies.
- Implement allowance/grace/tie-break UI and tests.
- Allow official qualifying results to source race/sprint grids.

**Exit criterion:** each qualifying format deterministically produces an official grid.

### Phase 4 — Flags and Stewarding Core

- Integrate global green/yellow/red/VSC/safety-car/chequered session states.
- Implement incident queue and detectors for track limits, wrong way, missed checkpoint, jump starts, contact candidates, and supported pit/flag violations.
- Implement warnings, fixed time penalties, DSQ, dismissal, audit history, and affected-driver notifications.
- Expose drive-through/stop-go only when serving detection is complete.

**Exit criterion:** flags change race behavior predictably and the director can resolve a full incident-to-result workflow.

### Phase 5 — Weekend Orchestration and Sprint

- Implement blank event builder and ordered session sequence.
- Add optional sprint using the shared race engine.
- Carry only officialized results into configured later grids.
- Add event-level monitor, session history, and final weekend summary.

**Exit criterion:** a director can run FP sessions, any qualifying mode, optional sprint, and main race from one persisted event.

### Phase 6 — Advanced Motorsport Procedures

- Local/sector flags and richer flag set.
- Complete VSC delta, safety-car queue/restart, illegal-pass adjudication aids, and pit procedures.
- Formation lap, mandatory compounds, pit windows, tyre allocations, and parc fermé.
- Championship points/standings and reusable weekend templates.
- AI race participants when runtime race-driving AI is ready.

---

## Explicitly Out of Scope for the Race-Core Milestone

- Automatic incident guilt or automatic illegal-overtake penalties.
- Formation laps and full safety-car queue/wave-by/restart regulations.
- Local/sector yellow and full blue/black/mechanical flag behavior.
- Mandatory tyre compounds, allocations, pit windows, and parc fermé.
- Championship points and season standings.
- Runtime AI competitors.
- Fixed real-world/F1 weekend presets; the initial builder is blank.
- Automatic progression from one weekend session to the next.

---

## Risks and Mitigations

| Risk | Probability | Impact | Mitigation |
|---|---:|---:|---|
| Route projection misorders cars at crossings, parallel sections, or pits | High | High | Treat localization confidence as first-class; use timing references and stable prior order; require track validation |
| Session variants create duplicated rules | Medium | High | Policy-based formats over one shared lifecycle, timing, entry, and result model |
| Car-local lap code conflicts with event authority | High | High | Define one event boundary and preserve lap archive publication separately |
| Server restart or an empty server corrupts active session meaning | Medium | High | Persist clock remainder, suspension reason, classification, and transition state; recover suspended and require explicit director resume |
| World-time control changes or advances the sporting clock | Medium | High | Store clocks independently and treat locked world time as presentation/environment state only |
| “LIVE” best-lap UI is confused with race position | High | Medium | Separate payloads, labels, and views; never reuse best-lap rank as race rank |
| Stewarding scope delays a playable race | High | High | Detect-then-review; begin with fixed time penalties and global flags; gate procedural penalties on reliable serving logic |
| Large telemetry payloads degrade multiplayer | Medium | Medium | Central snapshots, bounded cadence, deltas, and 24-entry performance tests |
| Valid-laps-only shot qualifying can run indefinitely | Medium | Medium | Always pair allowance with director finish and optionally a configured session clock |

---

## Key Technical Integration Areas

- `src/main/java/com/openwheelracing/content/race/OWRLapRecords.java`
- `src/main/java/com/openwheelracing/content/race/OWRRaceControlState.java`
- `src/main/java/com/openwheelracing/content/entity/OpenwheelCarEntity.java`
- `src/main/java/com/openwheelracing/content/track/TrackDefinition.java`
- `src/main/java/com/openwheelracing/content/track/TrackGeometry.java`
- `src/main/java/com/openwheelracing/content/track/survey/SurveyRouteLocalizer.java`
- `src/main/java/com/openwheelracing/content/menu/RaceDirectorMenu.java`
- `src/main/java/com/openwheelracing/client/screen/RaceDirectorScreen.java`
- `src/main/java/com/openwheelracing/client/hud/CarHudOverlay.java`
- `src/main/java/com/openwheelracing/network/OWRNetwork.java`

---

## Final Recommendation

Do not begin with the visual weekend configurator. Begin with Phase 0 and Phase 1: codify timing events and classification as pure, testable server logic, then prove live order and gaps under multiplayer edge cases. Once a standalone race can be staged, finished, reviewed, recovered, and officialized reliably, practice, qualifying, sprint, and the weekend builder become controlled compositions instead of separate fragile systems.
