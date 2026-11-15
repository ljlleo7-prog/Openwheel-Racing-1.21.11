> Historical server-physics path: protocol 27 human driving now uses
> [accepted client motion](client-authoritative-motion.md). Legacy rollback tests remain regression coverage.

# Disputed block-impact rollback

## Scope

Live server/client rollback is enabled for player-driven wall and solid-block
impacts. Entity impacts, falls, fire, low-speed obstruction and climbing contacts
are excluded. Unrelated damage is preserved. Both client and server must use
network protocol 24.

## Frame protocol and authority

The client sends a simulation frame every driving tick, including idle ticks.
It contains vehicle UUID, dimension, motion epoch, frame number, legacy input
sequence, raw driving input, gear, predicted pose, collision observation,
correction generation and automatic-rewind preference. Chat/inventory uses
neutral input while simulation is running, rather than stopping the frame clock.
A paused integrated game does not generate simulation frames.

The server retains 64 frames/checkpoints and simulates one authoritative movement
timeline. Once framed traffic is established, vanilla vehicle-position packets
are ignored for that car. They cannot move it again after its physics tick or
bypass rollback epochs. Cars without framed traffic retain the previous path.

Legacy input acknowledgements carry epochs and are bypassed for framed driving
(recovery still uses them). Normal driving keeps the original local prediction
and force solver; it no longer receives frequent forced motion snapshots.
Ordinary correction requires more than 1.5 blocks of confirmed position error
for six consecutive server ticks, and is limited to once per twelve ticks.
Velocity or yaw differences alone cannot trigger it. The client applies the same
position-only guard, then replays up to 32 local inputs after the snapshot.
An applied ordinary correction shows the amber network-resynchronization notice.
Its physical state is restored immediately and later local inputs are replayed.
The car, seated rider position and camera use a render-only translation/yaw
residual that eases to zero over 300 ms. Interpolation endpoints are translated
consistently to avoid a one-frame double jump. New corrections compose with the
remaining residual, with yaw using the shortest equivalent rotation. Collision
geometry, input handling and lap timing never use the rendered residual.
Explicit crash rewinds, epoch resets and vehicle/world changes clear the blend;
they are not silently disguised as ordinary smoothing. A component-envelope
block sweep rejects visual blends through nearby walls or unavailable chunks;
current collisions and yaw jumps over 30 degrees also use explicit correction
instead of portraying a misleading collision-free path.
Missing-frame predictions do not drive ordinary client corrections. Snapshots
up to four frames ahead can wait for their matching local frame. Large timeline
skews beyond the 64-frame history trigger an epoch resynchronization.

When missing inputs arrive, up to ten frames are replayed on an isolated server
vehicle before further normal movement. Collision-free repairs update the server
motion and affected checkpoints, preserving live damage, wear and authorized
gear commands. Repairs are refused near timing lines, across timing changes or
through collisions; they do not undo completed records. If the input history
cannot be repaired within ten frames, an explicit epoch resynchronization replaces
a repeated silent-correction loop. Its amber notification says “Network state
resynchronized”; this is distinct from a crash rewind.

## Disputed impact

A potentially disputed block impact becomes provisional if a clean checkpoint
exists three simulation ticks before impact and client evidence is absent or
disagrees meaningfully. An observed client collision at the matching frame
retains the ordinary impact immediately.

During adjudication the authoritative car holds its impact pose; the local
driver can continue producing evidence. Impact damage/feedback and ordinary
corrections are deferred. Other sources of damage remain active. Forty server
ticks is the maximum evidence wait; timeout commits the impact once.

Eligibility requires a complete, contiguous history through ten frames after
impact: 0.5 seconds of simulation at 20 TPS. Any observed client collision in
that interval rejects rewind. A changed correction generation, missing state,
changed wall geometry, unavailable chunks, an unsafe destination, or a crossed
finish/checkpoint/pit boundary also denies relief.

The server creates an unspawned vehicle of the same type, restores checkpoint
physics/configuration, and replays the recorded inputs through that interval.
Replay movement reads block shapes without fall effects, sounds or entity
pushes. It does not mutate track moisture, damage live entities, consume live
energy, emit impact feedback, run race timing or write telemetry. A collision
even at the final lookahead tick rejects rewind, including when the client
falsely reports its path as clear. Excessive divergence between replay and the
reported trajectory also denies relief. A separate three-tick check from the
restore point under the driver's latest control must also stay clear, allowing
room to react after the rewind rather than immediately repeating the impact.

The replay currently reads present world geometry/weather. Changed geometry
near the original impact is explicitly detected and rejected. It is not a
historical world-state simulator. Its fallback block resolver is conservative
about steps; uncertain cases retain the impact rather than granting immunity.

## Restore and repetition guards

A successful decision restores only the affected car to the checkpoint three
ticks before impact. It restores motion, yaw, steering, wheel speeds,
relaxation, thermal state, gearbox/clutch and assists; it does not restore old
damage or tyre wear, and cannot increase live stored ERS energy. Race clocks
continue running, traveled distance returns to the checkpoint value, and
trajectory/profile sampling is reset. Crossed timing boundaries prevent rewind.

The correction contains incident ID, a new epoch, baseline frame and physics
state, bound to the vehicle UUID and dimension. The client drops old prediction
history and queued local test packets. The server rejects old-epoch inputs and
frames, while epoch-less vanilla movement cannot override framed authority.
Duplicate restores and sync replies cannot reset the car repeatedly.

After a resolution, another incident requires at least 100 server ticks, twenty
consecutive clean synchronized observations, and eight blocks of separation.
Dismount/remount does not bypass cooldown. The restored checkpoint must be clear
of blocks and other entities. Nearby entity involvement excludes adjudication.
These bounds can deny help during sustained lag; they do not guarantee that
every synchronization-related crash will be forgiven.

## Driver feedback and local testing

Feedback is private to the driver and independent of the optional driving HUD.
It respects hide-GUI and clears on vehicle, level or connection changes.

- A small amber “Checking wall contact…” label identifies provisional contact.
- Success shows a large red NETWORK REWIND banner, one short cyan fade and a
  directional displacement indicator. The displayed duration includes local
  progress made while evidence was collected, not just the three pre-impact ticks.
- A true impact shows “Wall contact confirmed”. Uncertain evidence instead says
  “Wall contact could not be verified”. Feedback never asserts verified fault
  when evidence is unavailable.

Client commands (packet-stall simulation is single-player-only):

- `/owrnet lag` or `/owrnet lag 6`: occasionally stall outbound frame reports,
  driving inputs and vanilla vehicle-position packets for 3–6 client ticks
  (up to nominally 300 ms), separated by random 60–140-tick intervals of normal
  delivery. The number is the maximum burst length, not continuous latency.
  Range 0–40. Gear commands and keepalives bypass simulated stalls.
- `/owrnet lag off` or `/owrnet lag 0`: disable stalls and flush queued traffic in order.
- `/owrnet auto-rewind on` / `off`: enable/disable actual automatic block rewind.
  Default on. Works on remote servers too, for the requesting driver only.
  This changes relief, not collision authority or normal reconciliation.
  Switching it off during adjudication commits that pending impact.
- `/owrnet rewind-preview`: preview the banner without moving or damaging the car.
- `/owrnet`: show client settings.
- `/owrnetstatus`: ask the server for the seated car's actual framed state,
  input age/frame counters, unresolved input history, repair count and last impact
  decision. This is read-only and requires no operator permission.

The test queue is capped at 512 packets, never sleeps a thread, never delays
keepalives, and cannot affect remote-server connections. Session changes discard
old traffic. It simulates occasional outbound retransmission-like stalls rather than
actually discarding reliable packets. It does not simulate inbound loss or low TPS. Pure-client lag controls do not stall the integrated server.

## Validation

`./gradlew runBlockRollbackTestServer` exercises real server ticks, force/collision
physics, block shapes, payload codec round trips and vanilla packet rejection:

- Clean counterfactual path: one rewind and no impact damage.
- Later genuine impacts, including impact+1 and impact+10: one committed impact.
- A client falsely reporting a later collision as clear: server replay rejects it.
- Missing evidence: bounded timeout and one damage commitment.
- Disabled assistance: normal damage, no provisional incident.
- Stale frame gear reports cannot overwrite an accepted live shift command.
- Normal steering and repaired packet stalls send zero routine motion corrections.
- Repeated wall contact: no rewind loop; the second impact damages normally.
- Unrelated damage survives an accepted rewind.
- Changed wall geometry denies rewind.
- Marshal recovery cancels a provisional impact and communicates its new epoch.
- Replay preserves safety-car profile and assist defaults without aliasing live state.

Unit suites cover sequence wraparound, old epochs, duplicate updates, packet
bursts, cooldown/context changes and thousands of same-wall observations.
`runLapNetworkTestServer` checks the legacy packet/timing path remains intact.
The visual effect and real-socket latency still need in-client play testing;
these automated scenarios do not reproduce the affected Baku deployment.
