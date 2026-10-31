# Lap Timing Mechanism Spec

## Purpose

- Should lap timing support casual solo play, competitive time trials, multiplayer racing, or all later?

  All, solo first

- What counts as a valid lap?

  From starting counter block to finish counter block, and the car must always at least have some part landing on track blocks, even a tiny bit. If the entire car is off track, that will count as four-wheel-off-track and an invalid lap. and the starting and ending block will both have direction guard, if reverse passing it will not count (to avoid spinning around the starting line to count as a finished lap)

## Track Definition

- How does the player define a track?

  Continuous-placed track blocks is OK, no additional track objects verification.

- Is one checkpoint enough, or should tracks support multiple ordered checkpoints?

  MVP need not checkpoints first, and well, we will spec this later

- Should checkpoint order be configured manually, auto-detected, or inferred from placement?

  Manually

- Can multiple tracks exist in one world?

  Of course

## Timing Rules

- When does a lap start?

  Driving movement starts a background timed attempt for Race Director records. The displayed live lap timer starts only on a forward start-line crossing; placing a car does not start it. Forward finish crossings complete the current attempt and start the next. Attempts that began away from the start line are recorded as invalid, with a 3m horizontal/vertical grace around the configured line and forward movement required for start eligibility.

- When does a lap finish?

  When the car passes the finishing counter in the right approx. direction

- What invalidates a lap?

  Leaving track entirely, or reverse-passing

- Should reversing through checkpoints count?

  No

- Should leaving the car, breaking blocks, teleporting, or dying cancel the lap?

  Yes

- Attempts shorter than 0.2 seconds or with less than 10m actual movement during the attempt are silently discarded, including invalid attempts. The distance threshold uses actual movement, independent of racing-line localization confidence. Telemetry and background timed attempts are independent of the displayed live lap timer, which starts only on a forward start-line crossing. Meaningful attempts beginning away from the line remain visible as invalid records when finished or interrupted.
- Race Director can discard a selected lap entirely; discarding removes it from rankings and removes its learned profile.

## Results

- Should best laps be stored per car, per player, per track, or globally?

  Stored per player, and record can be cleared

- Should lap times persist across world reloads?

  Yes

- Should the game show sector times, deltas, leaderboard, or only current/best lap?

  Only current/best for now because track logic is still very coarse now

## Feedback

- What should the HUD show while timing?

  A real-time current timing and a past laps record board, may involve delta later

- What messages/sounds should trigger on start, checkpoint, invalid lap, and finish?

  a "INVALID LAP" title for invalid laps with reason attached below; other will trigger message telling the user about current status, and by finish, the message will contain the previous lap time.

## Acceptance Criteria

- What is enough for the next timing prototype?

  Off-track penalties, successful lap timing

- What should wait for multiplayer or track-definition systems?

  Delta, fill leaderboard, checkpoint logic, multiplayer time trail, ...

## AI Lap Recording

- Autonomous AI cars use the same start/finish crossings, minimum lap time, checkpoint checks, off-track invalidation, telemetry capture, and session lap records as player cars.
- Records use the stable AI driver UUID and display name (for example, `AI-07`), so rankings and archives include AI times without requiring a player passenger.
- Driving movement starts a background timed attempt; a first forward start-line crossing starts the displayed live timer. Subsequent forward crossings finish the current attempt and start the next. Background attempts starting away from the line are retained as invalid records, subject to the discard thresholds.
- GP formation and preparation crossings remain excluded. Starting a new session clears in-progress intervals for both player and AI cars.
- A player taking control owns subsequent laps; timing intervals cannot be shared between drivers.
- For AI-only GP grids, enter the spawned AI display name in the GP registration field, or use `/owr gp register_ai "Event" AI-07 7`. AI registration requires exactly one matching AI car on the event track. Use `/owr gp unregister_ai "Event" AI-07` to remove that entry.

- Player racing-line learning now excludes newly recorded laps with block/entity impacts, while retaining those laps in timing/telemetry records. Existing historical profiles do not contain a collision flag; invalidate a known bad historical lap to remove its profile.

## Detailed Lap Records

- The primary board shows abbreviated creation time (HH:mm), with the lap duration first so it remains visible in a narrow row.
- All Lap Records opens a server-paginated browser spanning current and archived sessions in the dimension.
- Filters combine player name (case-insensitive substring) or exact driver UUID, exact numeric session ID (`#12`), inclusive lap duration bounds in seconds, and inclusive UTC creation dates (`YYYY-MM-DD`) or ISO timestamps.
- Sessions display their name and unique numeric ID to distinguish repeated names. Sorting supports lap duration, creation timestamp, player, or session ID in either direction, with record ID as a stable tie breaker.
- Normal mode has two columns. Expanded mode has one column with tyre type/compound, wear, BoP weight/power percentages, traveled distance, validity, and per-row invalidate/revalidate/discard controls. Management actions require Race Director control permission on the server.
- New records persist tyre, BoP, and distance metadata. Historical records with no metadata show unknown values. Revalidating a record restores ranking eligibility but does not regenerate its learned racing line.

## Continuous Driving Telemetry

- Driving capture starts whenever a living player or autonomous AI controls a car, independently of route detection, lap timing, GP phase, and lap validity.
- Raw position, speed, elapsed time, and traveled distance are sampled every five server ticks. Route fields are explicitly untracked when recording raw driving traces.
- Captures rotate every 200 ticks and flush on driver change/dismount, entity removal, and entity save. Lap/session resets do not clear this recorder.
- CSV segments are stored under the world's `openwheelracing/driving-telemetry/` directory with capture, car, driver, dimension, and UTC start metadata. These raw segments are independent of the lap-record list and its telemetry replay files.

## Precision Verification

- Ordinary finish crossings insert valid records synchronously in the crossing tick; background telemetry writes and driving-capture rotation do not gate the record insertion.
- Start and finish times use the line intersection fraction within a 50ms simulation tick. Their difference is rounded once to an integer millisecond, retained in records and serialization. This is 1ms resolution based on interpolated motion, rather than a guarantee of 1ms physical accuracy.
- `./gradlew lapTimingTest` runs the runtime-backed geometry, timestamp, record/ranking, serialization, and independent-capture checks. `./gradlew build` includes these tests. These component integration tests do not simulate a player driving a full circuit in a running server.

## Director Lap Count Corrections

- Race Director selects a car in the telemetry car list and uses `+1 Lap` / `-1 Lap` to correct that driver's live race count. Player occupants take precedence over the car's assigned AI driver.
- Each action is validated on the server against director permissions, the active session ID, and the currently classified driver/car. Counts cannot be reduced below zero.
- The live classification and rank update immediately. The correction is stored separately from timed lap records, survives normal record reconciliation and checkpoint recovery, and does not create a fabricated lap time or affect best-lap rankings.
- The director receives the resulting count, and adjustments are logged with director, session, car, delta, and total.

- An actual forward start-line trigger is authoritative for start validity, regardless of where or when the first telemetry sample is captured. The 3m grace applies only to attempts lacking an actual trigger. Line-triggered timing retains its interpolated crossing timestamp; later telemetry samples cannot shift or invalidate that origin. Other validity checks (off-track, checkpoints, driver changes, and minimum duration/distance) still apply.
