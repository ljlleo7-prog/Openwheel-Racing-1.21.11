# Next Generation: Car Development and Racing Teams

Status: proposed design, ready for discussion before implementation.  
Date: 2026-10-09  
Target: Java 21 / NeoForge 1.21.11.

## Product direction

Players form a racing team, build a shared car design, manufacture parts, validate upgrades on track, and enter races together. Development should reward engineering choices and useful testing. Team membership should make cooperation practical for drivers, builders, mechanics, and engineers, while a solo player can perform every role.

“Next generation” means the next major gameplay generation of this mod. Teams are persistent organizations within a world/server, independent of a particular race weekend. All values and limits below are proposals; they do not change current balance or physics.

## Existing foundations

The repository already has car assembly and parts replacement stations, item-persistent setup and component damage, livery editing, telemetry, AI cars, BoP controls, and race timing/weekend services. `TeamCarRow` supplies a monitor view of cars but does not establish persistent membership or ownership. `VehicleProfile` contains physical parameters that can become the integration boundary for developed parts.

Use [car setup](mechanism-specs/car-setup-and-tuning.md), [crafting progression](mechanism-specs/progression-and-crafting.md), [damage and recovery](mechanism-specs/damage-and-recovery.md), and [race weekend requirements](race-weekend-system-prd.md) as constraints. The older roadmap and PRDs describe intended or historical behavior; inspect current implementations when starting each milestone.

## Core player loop

1. Create or join a team, or continue as an independent constructor.
2. Assemble the baseline car using the existing material chain.
3. Run a test and inspect speed, sector times, handling, temperatures, and wear.
4. Choose an engineering objective: lower drag, stronger downforce, lighter chassis, or a different power curve.
5. Commit materials to a design project and manufacture a prototype.
6. Install it at a workstation and run controlled validation laps.
7. Approve the revision, manufacture race parts and spares, and fit each team car.
8. Register legal configurations for a weekend, race, repair, and review results.

An upgrade is a physical part with a recorded design revision. Unlocking a design does not upgrade every car automatically. Setup remains an adjustment within the fitted hardware's permitted range; damage and wear remain the condition of that hardware.

## Racing teams

### Identity and membership

Each team has a stable UUID, unique normalized name, short tag, colors, optional livery template, owner, member list, design library, registered cars, and an audit history. Names and liveries are display properties; they never determine permissions.

Start with one active team membership per player per world. Invitations require explicit acceptance and expire. Any member may leave. The owner must transfer ownership before leaving; an operator can recover an abandoned team through an audited action. Team creation is free for the first release so cooperation is immediately available.

| Role | Default permissions |
| --- | --- |
| Owner | Transfer ownership, manage membership and roles, retire team, all team operations |
| Manager | Invite members, register race entries, assign cars, approve projects and spending |
| Engineer | Create designs, inspect team telemetry, operate development projects |
| Mechanic | Manufacture approved parts, install parts, repair authorized cars |
| Driver | Drive assigned cars, adjust permitted setup, contribute test runs |
| Observer | Read permitted team timing and telemetry |

Members may hold several roles. Race-director authority stays separate and follows event permissions. Being a manager never grants flags, penalties, or result editing. Solo owners receive all operational permissions.

### Cars, resources, and departure

A car has a stable car UUID plus an optional owning team UUID, independent of its temporary entity ID. Assignment to a driver is separate from ownership. Team cars may be driven, recovered, modified, or reassigned only through server-checked capabilities. Operator overrides are audited.

Use actual workstation inventories and ordinary containers for materials in the initial release. Joining a team does not grant access to every chest in its base; protected storage can follow later. Development inputs are reserved into a persistent project escrow when the project starts. UI actions cannot consume resources from arbitrary remote containers.

Leaving or being removed revokes future team operations and shared telemetry immediately. Team cars and designs remain with the team. A departing driver receives no automatic copy of team designs. Personal cars retain personal ownership. Disbanding first requires resolving registered cars and active projects; archive the team identity so historical results remain readable.

## Car development

### Initial hardware scope

Start with aero and chassis. Add powertrain development after these deliver a complete playable loop. Keep tyre compounds in the established tyre supply system.

| Family | Design choices | Physical effect | Required tradeoff |
| --- | --- | --- | --- |
| Front/rear aero package | Load, efficiency, balance | Downforce and drag curves; permitted wing range | More load costs drag, mass, or materials |
| Chassis | Lightweight, durable, balanced | Mass and component damage resistance | Lower mass costs durability or materials |
| Engine, later | Peak power or broader delivery | Bounded engine power curve | Power costs fuel use or wear once those exist |
| Gearbox, later | Ratio set and durability | Gear ratios and permitted final-drive range | Acceleration versus top speed; material cost |
| Cooling/ERS, later | Cooling or deployment capacity | Supported thermal/energy parameters | Mass and drag; requires real thermal/energy behavior |

Do not offer development choices whose downside has no implemented simulation. Do not introduce random failures or hidden grip bonuses. Geometry changes, suspension development, and visual body variants need separate physics/art scope before inclusion.

### Designs and revisions

Each family has a small set of readable engineering axes with a bounded allocation budget. Players can build different competitive packages within the same class. Progression expands the available allocation budget modestly and unlocks alternatives; it must have a finite class ceiling.

A design is an immutable revision with its authoring constructor, compatible car platform, rules version, engineering allocations, derived physical values, material recipe, and validation state. Editing creates a new revision. Manufacturing produces parts referencing that revision and carrying their own condition. A complete car records exactly which revisions are fitted.

The first release uses deterministic outcomes: the same inputs produce the same design. Material cost and active project time constrain iteration. Track validation confirms completion and provides evidence about performance; it does not award unlimited power for repeating laps.

### Project lifecycle

`DRAFT -> FUNDED -> PROTOTYPE_READY -> VALIDATING -> APPROVED -> ARCHIVED`

- Draft: compare predicted values with the current approved design; no cost.
- Funded: server validates permissions, compatibility, budget, and materials, then reserves inputs atomically.
- Prototype ready: fabrication finishes after a configurable number of loaded workstation ticks. Unloaded stations pause; offline wall time gives no progress.
- Validating: manufacture/install a marked test part and record a defined test programme.
- Approved: objective validation requirements are satisfied and an authorized member approves production. Approval does not claim the revision is faster.
- Archived: stop offering new production while preserving existing parts and historical references.

Cancellation returns only unconsumed escrow inputs, with consumption checkpoints recorded. Destroying the workstation pauses the project in persistent storage and requires an authorized rebind; it never duplicates or silently deletes escrow. Limit initial teams to one active development project, configurable by the server.

### Track testing

The initial programme requires a small configurable number of valid laps and coverage of useful operating conditions. Bind each run to car identity, complete hardware/setup snapshot, track ID/revision, environment, tyre condition, damage, and server-recorded telemetry.

Reject development credit for skipped checkpoints, teleport/reset, illegal car changes, driver exit, or unsuitable test conditions. Repeated laps can complete a finite programme only once. Race laps may contribute if they meet the same checks; drivers should not need a separate grind after every race.

Show lap and sector differences, straight-line speed, braking behavior, balance, and wear. Warn when weather, tyres, damage, or setup differ enough to weaken a comparison. Faster lap time is evidence, not an automatic success gate: a slow driver must still be able to develop a legal car. AI testing can follow once the same eligibility rules work for stable AI identities.

### Balance proposal

For the first playable slice, offer three aero directions and three chassis directions, one baseline budget and one advanced budget. Start tuning around a maximum 3–5% change to any affected physical parameter from the class baseline, with explicit tradeoffs. These are experiment bounds, not promised lap-time gains.

One prototype should be affordable from the existing carbon fiber/electronics/metal chain. A project should fit within an ordinary play session. Choose exact recipes, fabrication ticks, and lap requirements during playtesting; store them as versioned data rather than scattering constants through menus.

Avoid automatic research rewards proportional to team size or race wins. More members can divide work, but every constructor faces the same project cap and legal performance ceiling. Season catch-up rules may grant baseline access, without modifying individual race results.

## Race integration

Events explicitly select a technical mode:

| Mode | Behavior |
| --- | --- |
| Spec | All entries use the event's common performance profile; stored developed hardware survives for later use |
| Development | Fitted developed parts determine performance within class limits |
| BoP | Developed hardware applies, followed by director-authorized, bounded BoP adjustments |

Persist the selected mode, regulation revision, and adjustments. Existing BoP behavior must be audited before integration so development and BoP are each applied exactly once. Never silently rebalance a development event using lap time.

Registration records entry UUID, driver/AI identity, optional team UUID, car UUID, hardware revisions, and compliance result. Freeze these references at the configured registration/parc-ferme boundary. Repairs with equivalent revisions may be allowed; performance revisions require the configured exception workflow. Team or livery changes never rewrite past results.

Team entry limits are event settings; two cars is a suggested preset, not a universal rule. Constructor scoring uses official results and an explicit event scoring policy, including the number of scoring entries. Reopened results create a new standings revision rather than double-counting points.

Team terminals expose only authorized team-car telemetry plus public classification. Driver assignment, invitations, project approvals, and part fitting remain possible when other members are offline. Mid-session membership changes do not bypass frozen event eligibility.

## User interface

The team terminal has Overview, Members, Garage, Development, Testing, and Race Entries views. Overview shows assignments and pending work. Development shows cost, time, physical effects, tradeoffs, and current versus proposed revision. Testing displays comparable runs and explains invalid evidence. Garage lists fitted parts, condition, spare availability, and compliance.

Reuse assembly and replacement station interactions for physical manufacture and installation. A terminal selects and explains work, while the workstation holds materials and performs it. Every destructive action identifies the exact car, part, or project affected. Permission failures explain the required role.

Follow the repository's one-blur-pass screen rule when moving between terminal and station screens. Add English and Chinese localization together.

## Technical architecture

Proposed boundaries, to refine against the implementation:

- `content/team/`: persistent team registry, invitations, capabilities, car ownership, audit events.
- `content/car/development/`: design definitions, immutable revisions, project state, escrow, validation programmes, performance resolver.
- `content/race/`: entry/team snapshots, technical compliance, result-derived constructor standings.
- `network/`: bounded requests and permission-filtered snapshots.
- `client/screen/`: terminal and workstation views; clients submit intent rather than trusted derived stats.
- `registry/`: versioned item components for car identity, ownership, and fitted hardware.

Store world-wide team/design/project records in server SavedData, shared across dimensions. Items and entities carry stable references plus sufficient fitted-value snapshots to preserve a car if a design is archived. Unknown or incompatible data must produce a visible migration/compliance state rather than silently resetting performance.

Resolve performance in one place: platform baseline, fitted hardware, legal setup, event technical policy/BoP, then runtime condition effects. Cache immutable resolved profiles when hardware, setup, or event policy changes; avoid project/registry lookups in every physics tick. Use the same resolver for prediction, server physics, client prediction, and AI behavior.

Mutations validate sender capabilities, record revision, car/station access, event locks, inputs, and compatibility. Use idempotent request IDs and revisions to reject replayed or stale requests. Inventory changes and project transitions commit together on the server thread. Recovery checks reconcile IDs after restart and reject duplicate active entities/items representing the same team car.

Legacy cars migrate to the baseline platform with their existing setup, condition, livery, and ERS state preserved. Keep independent ownership and ordinary racing usable without creating a team. Creative/admin part generation is explicit and must still pass event legality checks.

## Delivery sequence

| Milestone | Playable result | Completion gate |
| --- | --- | --- |
| 1. Team identity | Create/join team, assign roles and cars, view permitted telemetry | Membership, access revocation, ownership and restart persistence work |
| 2. Hardware identity | Baseline revisions survive manufacture, fitting, spawn and recovery | Legacy cars preserve behavior; item/entity transitions lose no state |
| 3. Aero development | Fund, fabricate, test, approve and reproduce one aero revision | Tradeoffs affect actual physics; cancellation and restart preserve resources |
| 4. Chassis and comparison | Choose mass/durability direction and compare test runs | Combined packages remain within class bounds; damage tradeoff works |
| 5. Team racing | Register team cars, enforce event locks and compute constructor results | Spec/development/BoP modes and official result revisions are consistent |
| 6. Expansion | Add supported powertrain axes, optional AI tests and season policies | Each added axis has a simulated downside and readable feedback |

Build milestone 3 before adding a broad research tree, sponsor economy, trading market, staff simulation, or dedicated wind-tunnel block. The first release should prove that one upgrade is worth making, testing, and sharing.

## Validation plan for implementation

Add focused tests for capabilities and revocation; project transitions and escrow; serialization/migration; deterministic stat resolution and bounds; illegal test-run rejection; item/entity identity; event locks; and standings recomputation. Include restart, cross-dimension, disconnected owner, destroyed workstation, stale request, and duplicated car scenarios.

Run the repository build when implementation begins. In-game acceptance: two players create a team, divide engineering and driving duties, validate a prototype, fit two cars, race under development rules, inspect constructor results, restart the server, and continue with identities and resources intact. Repeat solo and in spec mode.

This document changes design only; no gameplay implementation or build execution is required for the planning artifact.

## Decisions to settle before coding

Recommended defaults are provided so planning can proceed:

1. Survival focus with optional spec events; development is never mandatory for casual racing.
2. Deterministic designs and finite validation programmes; no random upgrade failures.
3. Aero/chassis first; engine/cooling only when their resource and wear tradeoffs exist.
4. World-persistent teams and designs; season resets are explicit policies introduced later.
5. Existing stations first; add specialist facilities only when the loop needs them.
6. Personal constructors and team constructors share the same limits and physics.

Exact material costs, project durations, performance bounds, trading rules, and season progression remain balance decisions to resolve through the first playable slice.
