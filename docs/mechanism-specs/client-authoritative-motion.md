# Client driving and accepted server motion — protocol 31

## Authority

A technically admitted human driver's client runs driving physics. The server
accepts an ordered stream of resulting poses, velocities and handling telemetry;
it does not run an additional human driving step or periodically replace that
client's velocity/yaw with its own prediction. AI, unoccupied cars and legacy
test fixtures retain their server solver.

Normal driving has no request/response dependency after login admission. Local
physics advances every client tick and immediately applies manual and automatic
shifts. The server receives the resulting frames and replicates accepted state
to observers. Vanilla observer velocity packets are ignored for the locally
driven framed car. Routine metadata echoes cannot replace its gear, RPM, speed,
tyre state, energy or locally selected ABS/TC/DRS/ERS controls. Damage, setup
limits, race data and the server's automatic-shifting permission still apply.
Service/recovery updates and explicit framed validation corrections remain able
to replace local state. Other cars continue receiving ordinary observer updates.

Vanilla vehicle movement packets do not independently move a framed car. Each
custom frame is bound to the controlling player, vehicle UUID, dimension and
stream epoch. Server recovery and rejected movement can still explicitly
resynchronize the car, with the existing HUD notification. The old disputed
server-physics crash replay is not the active human-driving path in this mode;
the auto-rewind preference does not disable validation or recovery.

## Admission and technical rules

Login, respawn, dimension changes and server physics-preset changes issue a fresh
challenge. Driving waits for acknowledgment of the matching build fingerprint
and deterministic physics probe. Old challenge answers cannot admit a new
session. An unanswered check times out after 30 real seconds. Timeout is not
reported as a technical-rules violation.

The build generates an index of all entity/car class files, including nested
classes, and hashes their names and bytes. Both ends must use the exact same
build. Mismatched definitions/probe and nonfinite or out-of-range performance
fields are rejected with a technical-rules violation message. Existing legal
steering/assist preferences remain permitted. This is a build compatibility and
ordinary tampering check, **not cryptographic proof of an honest remote client**:
a deliberately modified client can falsify the fingerprint and telemetry.
Movement bounds are also not a complete acceleration/grip/aero anti-cheat.

## Accepted path and timing

Frames must be contiguous; duplicates do nothing. Pending work is capped at 64
frames, and admission has a 12-frame burst budget refilled at 20 frames per real
second. Per-frame displacement/velocity bounds, loaded-chunk and world-border checks
run without moving the live entity as a probe. Solid-block and barrier geometry
are resolved by the driving client; the accepted server stream does not reject
wall overlap or retreat paths. This intentionally favors driver control when a
car is embedded or server/client geometry disagrees. The server no longer
independently prevents bounded client-reported movement through solid walls.
Invalid envelopes or unavailable areas still reset the stream to the last
accepted pose with stopped momentum.

Step/climb displacement does not become upward velocity on the next physics
frame. Genuine upward input momentum and gravity-driven free fall remain.

Two rejections within two seconds and four blocks trigger a stopped correction
with a “Movement paused” HUD prompt. Held pedals are inhibited locally until
both throttle and brake are released. Idle simulation frames continue during
that hold; ordinary driving never waits for a per-input server acknowledgment.
This is a recovery action, not a lap-time rollback or technical-rules penalty.
Old-epoch synchronization replies are limited to one per 500 ms, and repeated
idle resets cannot continuously advance the server epoch.

Every accepted segment runs the full server race movement pipeline: configured
finish, physical markers, steward checkpoints/sectors and pit lines, route
coverage, profiles and lap telemetry. Rejected/duplicate samples cannot record
crossings. Adjacent steward lines crossed in one segment retain movement order.

The first accepted frame anchors sequence time to the server clock; subsequent
frames retain individual 50 ms intervals even when delivered together. Server
monotonic time paces admission and advances the live elapsed-time display.
A sequence timeline cannot fall more than 12 ticks behind elapsed real time:
long client freezes count toward elapsed time instead of stopping the lap clock.
Short packet bursts retain their original crossing fractions. That grace is a
bounded timing uncertainty, not exact one-way latency compensation or a claim
of identical clocks across competitors. Low-TPS race control still uses world
ticks in other subsystems and needs live comparison with this real-time-paced
lap clock.

## Telemetry and state

Accepted snapshots carry per-wheel temperatures, carcass temperatures, forces,
slip values, tyre wear, engine RPM and aero/drive/drag force telemetry. Client
driving advances tyre heating, wear and the full ERS calculation: deployment,
negative-power harvest, power-limited ICE recovery/refunds and brake recovery.
The former client-side ERS early returns are removed. Accepted snapshots carry
ERS activity and power as well as energy; accepted speed is restored from the
reported velocity. Server accepted wear cannot decrease;
server tyre service remains responsible for replacement. Gear commands remain server-checked; their acknowledgment is not needed for the
local shift. ABS/TC/DRS/ERS selections accompany accepted snapshots so observers
see the driver state, including automatic brake-triggered DRS closure.
Race setup/caps remain server-owned. Energy increases are capped by the server
harvest-power allowance plus the existing per-tick brake-harvest allowance.
Power-limited ICE recovery is permitted with throttle applied. This is a
per-frame maximum, not a full independent energy audit. Independent tyre/force
verification is also unfinished. Collision damage uses accepted client collision reports; a
malicious client suppressing those reports is not fully detected.

Profiles and lap telemetry use accepted frame time, rather than packet-arrival
ticks, so bursts do not merge otherwise distinct samples. Driving traces retain
arrival wall-clock metadata for diagnostics. Server telemetry is a record of
accepted client motion, not an independent physics oracle.

## Verification and limits

`runClientAuthorityTestServer` tests fresh/stale/mismatched challenges, mounting
admission, accepted poses without an extra solver step, force telemetry,
finish/checkpoint packet bursts, three exact 2,000 ms laps, duplicate rejection,
impossible teleports and runtime grip-parameter rejection. An 80-frame fixture
feeds eight-frame-old metadata into accelerating, shifting and steering cars:
the protected solver must match an uninterrupted oracle, while unprotected
metadata must measurably disturb the control fixture. Server damage and explicit
service state must still apply.

The terrain/energy fixture runs normal Entity.move with local-authority behavior
and exercises the client-side branch by temporarily switching the isolated test
world's side flag for synchronous physics calls, restored in finally. It checks
climbing and descending a raised road without retained upward velocity, a
specific fast descent rejected by the former straight sweep, real-wall rejection,
and client ERS harvest/deployment. This is not a real socket/ClientLevel play test.
The accepted-stream fixture also checks stopped rejection snapshots, stale epoch
traffic throttling and the repeated-rejection pause status. Unit tests cover
step momentum versus real jumps/falls and held-pedal release recovery.

Clock unit tests
cover pacing, burst timing, duplicate frames and long client freezes. The older
rollback/timing/survey/capture suites remain regression checks of their own paths.

Still requiring live testing: Baku's repeated-location incident, slopes/steps
and unusual collision shapes, real socket stalls and sustained low TPS,
remote visual smoothness, tyre service while old frames are in flight, delayed
thermal echoes, mixed entity/car impacts and production-sized fields. Read-only
path validation can conservatively reject a locally legal step/edge contact.

## Shared-car operations

[Physical pit service](physical-pit-service.md) defines independent jack/tyre
states, technician action leases, tyreless persistence, and explicit server/driver
control handoffs. Driver changes invalidate the epoch synchronously. Framed
recovery sends current and final carried poses. Server-owned tyre configuration
is restored atomically before driving resumes, and known service time stays on
the lap clock. These are one-time authority boundaries, not ordinary driving
request/response dependencies.

## Car contacts

[Car-to-car contact](car-to-car-contact.md) adds immediate local sweep/impulse
prediction and server confirmation affecting both owners. Pre-contact queues
are invalidated once at that boundary. Normal driving continues without an
ordinary correction loop. Car-contact evidence is distinct from block contact.

## Barrier contacts

[Barrier contact acceptance](barrier-contact-acceptance.md) validates resolved
contact/slide paths and their collision headings. These contacts continue without
normal resync or pedal-release interruption. Rebound is predicted locally and
server feedback cannot apply its torque twice.

## Effective control telemetry

Motion state indices 78 and 79 carry tick-averaged post-assist throttle and braking fractions (0–1), validated as finite unit values. They are observed in the solver after stability/launch/traction and ABS force-request limits without modifying the physics. Engine braking alone is not a brake pedal input. They are captured/restored with the existing rollback motion so accepted client-driven cars and server-driven AI expose the same monitor readings. This adds 16 bytes to an existing frame; protocol 31 requires matching server/client builds.
