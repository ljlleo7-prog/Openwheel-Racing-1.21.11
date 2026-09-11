package com.openwheelracing.content.race.weekend;

import com.openwheelracing.content.race.session.RaceSessionLifecycle;
import com.openwheelracing.content.race.session.RaceSessionState;
import com.openwheelracing.content.race.session.RaceSessionSuspensionReason;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Pure, server-authoritative aggregate for one configurable Grand Prix weekend.
 * Minecraft integration and persistence live in {@code OWRGrandPrixRegistry}; this
 * class deliberately contains no world or entity references so its rules can be
 * tested deterministically.
 */
public final class GrandPrixWeekend {
    public static final int MAX_SESSIONS = 32;
    public static final int MAX_ENTRIES = 24;
    public static final int MAX_AUDIT_ENTRIES = 512;
    public static final int MAX_SESSION_NAME_LENGTH = 40;

    private final UUID eventId;
    private final String name;
    private final UUID trackId;
    private final String dimensionId;
    private final List<Session> sessions = new ArrayList<>();
    private final Map<UUID, Entry> entries = new LinkedHashMap<>();
    private final List<AuditEntry> audit = new ArrayList<>();
    private EventState state;
    private int activeSessionIndex;
    private long lastKnownTick;

    public GrandPrixWeekend(UUID eventId, String name, UUID trackId, String dimensionId) {
        this(eventId, name, trackId, dimensionId, EventState.DRAFT, -1, List.of(), List.of(), List.of());
    }

    private GrandPrixWeekend(UUID eventId, String name, UUID trackId, String dimensionId, EventState state,
                             int activeSessionIndex, List<SessionSnapshot> sessions, List<Entry> entries,
                             List<AuditEntry> audit) {
        this.eventId = Objects.requireNonNull(eventId, "eventId");
        this.name = requireText(name, "name");
        this.trackId = Objects.requireNonNull(trackId, "trackId");
        this.dimensionId = requireText(dimensionId, "dimensionId");
        this.state = Objects.requireNonNull(state, "state");
        this.activeSessionIndex = activeSessionIndex;
        sessions.forEach(snapshot -> this.sessions.add(Session.restore(snapshot)));
        entries.forEach(entry -> this.entries.put(entry.driverId(), entry));
        this.audit.addAll(audit.stream().skip(Math.max(0, audit.size() - MAX_AUDIT_ENTRIES)).toList());
        this.lastKnownTick = this.audit.stream().mapToLong(AuditEntry::tick).max().orElse(0L);
        validateStoredState();
    }

    public static GrandPrixWeekend restore(Snapshot snapshot) {
        Objects.requireNonNull(snapshot, "snapshot");
        return new GrandPrixWeekend(snapshot.eventId(), snapshot.name(), snapshot.trackId(), snapshot.dimensionId(),
            snapshot.state(), snapshot.activeSessionIndex(), snapshot.sessions(), snapshot.entries(), snapshot.audit());
    }

    public UUID eventId() {
        return eventId;
    }

    public String name() {
        return name;
    }

    public UUID trackId() {
        return trackId;
    }

    public String dimensionId() {
        return dimensionId;
    }

    public EventState state() {
        return state;
    }

    public int activeSessionIndex() {
        return activeSessionIndex;
    }

    public List<SessionView> sessions(long serverTick) {
        List<SessionView> views = new ArrayList<>(sessions.size());
        for (Session session : sessions) {
            views.add(session.view(serverTick));
        }
        return List.copyOf(views);
    }

    public Optional<SessionView> activeSession(long serverTick) {
        return activeSession().map(session -> session.view(serverTick));
    }

    public List<Entry> entries() {
        return List.copyOf(entries.values());
    }

    public List<AuditEntry> audit() {
        return List.copyOf(audit);
    }

    public void recordCreation(long tick, UUID actor, String actorName) {
        if (!audit.isEmpty() || state != EventState.DRAFT) {
            throw new IllegalStateException("Weekend creation is already recorded");
        }
        audit(tick, actor, actorName, "CREATE_EVENT", name);
    }

    public void addSession(SessionConfig config, long tick, UUID actor, String actorName) {
        requireDraft();
        Objects.requireNonNull(config, "config");
        if (sessions.size() >= MAX_SESSIONS) {
            throw new IllegalStateException("A weekend supports at most " + MAX_SESSIONS + " sessions");
        }
        if (sessions.stream().anyMatch(session -> session.config.sessionId() == config.sessionId())) {
            throw new IllegalArgumentException("Duplicate session id " + config.sessionId());
        }
        long sameType = sessions.stream().filter(session -> session.config.type() == config.type()).count();
        if (config.type() != SessionType.PRACTICE && sameType > 0) {
            throw new IllegalStateException("A weekend may contain at most one " + config.type().displayName());
        }
        sessions.add(new Session(config));
        audit(tick, actor, actorName, "ADD_SESSION", config.name());
    }

    public void removeSession(int index, long tick, UUID actor, String actorName) {
        requireDraft();
        Session removed = sessions.remove(requireSessionIndex(index));
        audit(tick, actor, actorName, "REMOVE_SESSION", removed.config.name());
    }

    public void moveSession(int from, int to, long tick, UUID actor, String actorName) {
        requireDraft();
        int source = requireSessionIndex(from);
        if (to < 0 || to >= sessions.size()) {
            throw new IndexOutOfBoundsException("Target session index is outside the schedule");
        }
        Session session = sessions.remove(source);
        sessions.add(to, session);
        audit(tick, actor, actorName, "MOVE_SESSION", session.config.name() + " " + from + "->" + to);
    }

    public RegistrationResult register(UUID driverId, String driverName, String displayCode, long tick,
                                       UUID actor, String actorName) {
        requireRosterMutable();
        Objects.requireNonNull(driverId, "driverId");
        String cleanName = requireText(driverName, "driverName");
        String cleanCode = requireText(displayCode, "displayCode");
        Optional<Entry> collision = entries.values().stream()
            .filter(entry -> entry.displayCode().equalsIgnoreCase(cleanCode) && !entry.driverId().equals(driverId))
            .findFirst();
        if (collision.isPresent()) {
            return new RegistrationResult(false, false, collision.get());
        }
        Entry previous = entries.get(driverId);
        if (previous == null && entries.size() >= MAX_ENTRIES) {
            throw new IllegalStateException("A weekend supports at most " + MAX_ENTRIES + " entries");
        }
        Entry updated = new Entry(driverId, cleanName, cleanCode, EntryStatus.ENTERED, tick);
        entries.put(driverId, updated);
        audit(tick, actor, actorName, previous == null ? "REGISTER_ENTRY" : "UPDATE_ENTRY", cleanCode + " " + cleanName);
        return new RegistrationResult(true, previous != null, updated);
    }

    public boolean unregister(UUID driverId, long tick, UUID actor, String actorName) {
        requireRosterMutable();
        Entry removed = entries.remove(driverId);
        if (removed != null) {
            audit(tick, actor, actorName, "UNREGISTER_ENTRY", removed.displayCode() + " " + removed.driverName());
            return true;
        }
        return false;
    }

    public void open(long tick, UUID actor, String actorName) {
        requireDraft();
        List<String> errors = validationErrors();
        if (!errors.isEmpty()) {
            throw new IllegalStateException(String.join("; ", errors));
        }
        state = EventState.OPEN;
        audit(tick, actor, actorName, "OPEN_EVENT", "configuration locked");
    }

    public List<String> validationErrors() {
        List<String> errors = new ArrayList<>();
        if (sessions.isEmpty()) {
            errors.add("the schedule has no sessions");
        }
        if (entries.isEmpty()) {
            errors.add("the roster has no entries");
        }
        if (sessions.stream().filter(session -> session.config.type() == SessionType.QUALIFYING).count() > 1) {
            errors.add("the schedule has more than one qualifying session");
        }
        if (sessions.stream().filter(session -> session.config.type() == SessionType.SPRINT).count() > 1) {
            errors.add("the schedule has more than one sprint");
        }
        if (sessions.stream().filter(session -> session.config.type() == SessionType.RACE).count() > 1) {
            errors.add("the schedule has more than one race");
        }
        for (int index = 0; index < sessions.size(); index++) {
            SessionConfig config = sessions.get(index).config;
            if (config.gridSource() == GridSource.PREVIOUS_OFFICIAL && index == 0) {
                errors.add(config.name() + " has no previous session to source its grid");
            }
        }
        return List.copyOf(errors);
    }

    /** Opens the next session. Advancement is always an explicit director action. */
    public SessionView advance(long tick, UUID actor, String actorName) {
        if (state != EventState.OPEN) {
            throw new IllegalStateException("The weekend is not open");
        }
        if (activeSessionIndex >= 0 && !sessions.get(activeSessionIndex).lifecycle.state().isTerminal()) {
            throw new IllegalStateException("The current session must be official or abandoned before advancing");
        }
        int next = activeSessionIndex + 1;
        if (next >= sessions.size()) {
            throw new IllegalStateException("There is no next session; complete the weekend instead");
        }
        activeSessionIndex = next;
        Session session = sessions.get(next);
        session.lifecycle.transitionTo(RaceSessionState.OPEN, tick);
        audit(tick, actor, actorName, "OPEN_SESSION", session.config.name());
        return session.view(tick);
    }

    public void stage(long tick, UUID actor, String actorName) {
        Session session = requireActiveSession();
        if (!session.config.type().usesGrid()) {
            throw new IllegalStateException("Practice and qualifying sessions do not use grid staging");
        }
        if (session.grid.size() != enteredDriverIds().size()) {
            throw new IllegalStateException("Materialize a complete grid before staging");
        }
        session.lifecycle.transitionTo(RaceSessionState.STAGING, tick);
        audit(tick, actor, actorName, "STAGE_SESSION", session.config.name());
    }

    public void materializeGrid(List<UUID> order, long tick, UUID actor, String actorName) {
        Session session = requireActiveSession();
        if (session.lifecycle.state() != RaceSessionState.OPEN || !session.config.type().usesGrid()) {
            throw new IllegalStateException("A grid may only be materialized for an open sprint or race");
        }
        List<UUID> expected = enteredDriverIds();
        List<UUID> supplied = List.copyOf(order);
        if (supplied.size() != expected.size() || supplied.stream().distinct().count() != supplied.size()
            || !new java.util.HashSet<>(supplied).equals(new java.util.HashSet<>(expected))) {
            throw new IllegalArgumentException("The grid must contain every entered driver exactly once");
        }
        session.grid = supplied;
        audit(tick, actor, actorName, "MATERIALIZE_GRID", session.config.name() + " entries=" + supplied.size());
    }

    public List<UUID> suggestedGridOrder() {
        Session session = requireActiveSession();
        return switch (session.config.gridSource()) {
            case CONFIGURED_ENTRY_ORDER -> enteredDriverIds();
            case MANUAL -> List.of();
            case PREVIOUS_OFFICIAL -> {
                for (int index = activeSessionIndex - 1; index >= 0; index--) {
                    Session previous = sessions.get(index);
                    if (previous.result != null && previous.result.official()) {
                        List<UUID> resultOrder = previous.result.rows().stream().map(ResultRow::driverId)
                            .filter(entries::containsKey).filter(id -> entries.get(id).status() == EntryStatus.ENTERED).toList();
                        if (resultOrder.size() == enteredDriverIds().size()) {
                            yield resultOrder;
                        }
                    }
                }
                throw new IllegalStateException("No previous official result can source this grid");
            }
        };
    }

    public void countdown(long tick, UUID actor, String actorName) {
        Session session = requireActiveSession();
        session.lifecycle.transitionTo(RaceSessionState.COUNTDOWN, tick);
        session.countdownSinceTick = tick;
        audit(tick, actor, actorName, "COUNTDOWN_SESSION", session.config.name());
    }

    public void start(long tick, UUID actor, String actorName) {
        Session session = requireActiveSession();
        RaceSessionState required = session.config.type().usesGrid() ? RaceSessionState.COUNTDOWN : RaceSessionState.OPEN;
        if (session.lifecycle.state() != required) {
            throw new IllegalStateException("The session must be " + required.name().toLowerCase(java.util.Locale.ROOT) + " before start");
        }
        if (required == RaceSessionState.COUNTDOWN && session.countdownRemainingTicks(tick) > 0L) {
            throw new IllegalStateException("The configured countdown has not expired");
        }
        session.lifecycle.transitionTo(RaceSessionState.RUNNING, tick);
        session.freezeCountdown(tick);
        audit(tick, actor, actorName, "START_SESSION", session.config.name());
    }

    public boolean suspend(RaceSessionSuspensionReason reason, long tick, UUID actor, String actorName) {
        Session session = requireActiveSession();
        if (session.lifecycle.state() == RaceSessionState.COUNTDOWN) {
            session.freezeCountdown(tick);
        }
        boolean changed = session.lifecycle.suspend(reason, tick);
        if (changed) {
            audit(tick, actor, actorName, "SUSPEND_SESSION", reason.name());
        }
        return changed;
    }

    public void resume(long tick, UUID actor, String actorName) {
        Session session = requireActiveSession();
        boolean resumesCountdown = session.lifecycle.state() == RaceSessionState.SUSPENDED
            && session.lifecycle.suspendedFrom() == RaceSessionState.COUNTDOWN;
        session.lifecycle.resume(tick);
        if (resumesCountdown) {
            session.countdownSinceTick = tick;
        }
        audit(tick, actor, actorName, "RESUME_SESSION", session.config.name());
    }

    public void finish(long tick, UUID actor, String actorName) {
        Session session = requireActiveSession();
        session.lifecycle.transitionTo(RaceSessionState.FINISHING, tick);
        audit(tick, actor, actorName, "FINISH_SESSION", session.config.name());
    }

    public void publishProvisional(List<ResultRow> rows, long tick, UUID actor, String actorName) {
        Session session = requireActiveSession();
        if (session.lifecycle.state() != RaceSessionState.FINISHING) {
            throw new IllegalStateException("The session must be finishing before publishing a result");
        }
        session.result = new SessionResult(1, false, normalizeResult(rows), tick, actor, actorName);
        session.lifecycle.transitionTo(RaceSessionState.PROVISIONAL, tick);
        audit(tick, actor, actorName, "PUBLISH_PROVISIONAL", session.config.name());
    }

    public void reviseProvisional(List<ResultRow> rows, long tick, UUID actor, String actorName) {
        Session session = requireActiveSession();
        if (session.lifecycle.state() != RaceSessionState.PROVISIONAL || session.result == null || session.result.official()) {
            throw new IllegalStateException("Only a provisional result may be revised");
        }
        session.result = new SessionResult(session.result.revision() + 1, false, normalizeResult(rows), tick, actor, actorName);
        audit(tick, actor, actorName, "REVISE_PROVISIONAL", session.config.name());
    }

    public void officialize(long tick, UUID actor, String actorName) {
        Session session = requireActiveSession();
        if (session.result == null) {
            throw new IllegalStateException("The session has no provisional result");
        }
        session.lifecycle.transitionTo(RaceSessionState.OFFICIAL, tick);
        session.result = new SessionResult(session.result.revision() + 1, true, session.result.rows(), tick, actor, actorName);
        audit(tick, actor, actorName, "OFFICIALIZE_SESSION", session.config.name());
    }

    public void complete(long tick, UUID actor, String actorName) {
        if (state != EventState.OPEN || sessions.isEmpty() || activeSessionIndex != sessions.size() - 1
            || sessions.get(activeSessionIndex).lifecycle.state() != RaceSessionState.OFFICIAL) {
            throw new IllegalStateException("The final session must be official before completing the weekend");
        }
        state = EventState.COMPLETE;
        audit(tick, actor, actorName, "COMPLETE_EVENT", "all sessions complete");
    }

    public void abandon(long tick, UUID actor, String actorName) {
        if (state == EventState.DRAFT) {
            state = EventState.ABANDONED;
            audit(tick, actor, actorName, "ABANDON_EVENT", name);
            return;
        }
        Session session = requireActiveSession();
        session.lifecycle.transitionTo(RaceSessionState.ABANDONED, tick);
        audit(tick, actor, actorName, "ABANDON_SESSION", session.config.name());
    }

    public boolean onConnectedPlayerCountChanged(int connectedPlayers, long tick) {
        Optional<Session> active = activeSession();
        if (active.isEmpty()) {
            return false;
        }
        if (connectedPlayers == 0 && active.get().lifecycle.state() == RaceSessionState.COUNTDOWN) {
            active.get().freezeCountdown(tick);
        }
        boolean changed = active.get().lifecycle.onConnectedPlayerCountChanged(connectedPlayers, tick);
        if (changed) {
            audit(tick, null, "SERVER", "SUSPEND_SESSION", RaceSessionSuspensionReason.EMPTY_SERVER.name());
        }
        return changed;
    }

    public Snapshot snapshot(long serverTick) {
        return new Snapshot(eventId, name, trackId, dimensionId, state, activeSessionIndex,
            sessions.stream().map(session -> session.snapshot(serverTick)).toList(), entries(), audit());
    }

    public Snapshot snapshot() {
        return snapshot(lastKnownTick);
    }

    public void observeServerTick(long serverTick) {
        if (serverTick < 0L) {
            throw new IllegalArgumentException("serverTick must not be negative");
        }
        lastKnownTick = Math.max(lastKnownTick, serverTick);
    }

    private List<ResultRow> normalizeResult(List<ResultRow> rows) {
        Objects.requireNonNull(rows, "rows");
        Map<UUID, ResultRow> unique = new LinkedHashMap<>();
        for (ResultRow row : rows) {
            if (!entries.containsKey(row.driverId())) {
                throw new IllegalArgumentException("Result contains an unregistered driver: " + row.driverId());
            }
            if (unique.put(row.driverId(), row) != null) {
                throw new IllegalArgumentException("Result contains a driver more than once: " + row.driverId());
            }
        }
        List<ResultRow> ordered = new ArrayList<>(unique.values());
        ordered.sort(Comparator.comparingInt(ResultRow::position));
        List<ResultRow> normalized = new ArrayList<>(ordered.size());
        for (int index = 0; index < ordered.size(); index++) {
            normalized.add(ordered.get(index).withPosition(index + 1));
        }
        return List.copyOf(normalized);
    }

    private List<UUID> enteredDriverIds() {
        return entries.values().stream().filter(entry -> entry.status() == EntryStatus.ENTERED)
            .map(Entry::driverId).toList();
    }

    private Optional<Session> activeSession() {
        return activeSessionIndex >= 0 && activeSessionIndex < sessions.size()
            ? Optional.of(sessions.get(activeSessionIndex)) : Optional.empty();
    }

    private Session requireActiveSession() {
        return activeSession().orElseThrow(() -> new IllegalStateException("The weekend has no active session"));
    }

    private int requireSessionIndex(int index) {
        if (index < 0 || index >= sessions.size()) {
            throw new IndexOutOfBoundsException("Session index is outside the schedule");
        }
        return index;
    }

    private void requireDraft() {
        if (state != EventState.DRAFT) {
            throw new IllegalStateException("The weekend configuration is locked");
        }
    }

    private void requireRosterMutable() {
        if (state == EventState.COMPLETE || state == EventState.ABANDONED) {
            throw new IllegalStateException("The weekend roster is closed");
        }
        if (activeSession().map(session -> session.lifecycle.state().isActive()).orElse(false)) {
            throw new IllegalStateException("The roster cannot change during an active session");
        }
        if (activeSession().map(session -> !session.grid.isEmpty()).orElse(false)) {
            throw new IllegalStateException("The roster cannot change after a grid is materialized");
        }
    }

    private void audit(long tick, UUID actor, String actorName, String action, String detail) {
        lastKnownTick = Math.max(lastKnownTick, tick);
        if (audit.size() == MAX_AUDIT_ENTRIES) {
            audit.removeFirst();
        }
        audit.add(new AuditEntry(Math.max(0L, tick), actor, actorName == null ? "" : actorName, action, detail));
    }

    private void validateStoredState() {
        if (activeSessionIndex < -1 || activeSessionIndex >= sessions.size()) {
            throw new IllegalArgumentException("Invalid active session index");
        }
        if (entries.size() > MAX_ENTRIES || sessions.size() > MAX_SESSIONS) {
            throw new IllegalArgumentException("Stored weekend exceeds supported limits");
        }
    }

    private static String requireText(String value, String field) {
        String clean = value == null ? "" : value.trim();
        if (clean.isEmpty()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return clean;
    }

    private static final class Session {
        private final SessionConfig config;
        private final RaceSessionLifecycle lifecycle;
        private SessionResult result;
        private List<UUID> grid;
        private long countdownElapsedTicks;
        private long countdownSinceTick = -1L;

        private Session(SessionConfig config) {
            this(config, new RaceSessionLifecycle(config.durationTicks()), null, List.of(), 0L);
        }

        private Session(SessionConfig config, RaceSessionLifecycle lifecycle, SessionResult result, List<UUID> grid,
                        long countdownElapsedTicks) {
            this.config = config;
            this.lifecycle = lifecycle;
            this.result = result;
            this.grid = List.copyOf(grid);
            this.countdownElapsedTicks = Math.max(0L, countdownElapsedTicks);
        }

        private static Session restore(SessionSnapshot snapshot) {
            return new Session(snapshot.config(), RaceSessionLifecycle.recover(snapshot.lifecycle()), snapshot.result(), snapshot.grid(),
                snapshot.countdownElapsedTicks());
        }

        private SessionView view(long tick) {
            return new SessionView(config, lifecycle.state(), lifecycle.suspendedFrom(), lifecycle.suspensionReason(),
                lifecycle.elapsedTicks(tick), lifecycle.remainingTicks(tick), countdownRemainingTicks(tick), result, grid);
        }

        private SessionSnapshot snapshot(long tick) {
            return new SessionSnapshot(config, lifecycle.checkpoint(tick), result, grid, countdownElapsedAt(tick));
        }

        private long countdownElapsedAt(long tick) {
            if (lifecycle.state() == RaceSessionState.COUNTDOWN && countdownSinceTick >= 0L) {
                if (tick < countdownSinceTick) {
                    throw new IllegalArgumentException("serverTick must not move backwards during countdown");
                }
                return countdownElapsedTicks + tick - countdownSinceTick;
            }
            return countdownElapsedTicks;
        }

        private long countdownRemainingTicks(long tick) {
            return Math.max(0L, config.countdownTicks() - countdownElapsedAt(tick));
        }

        private void freezeCountdown(long tick) {
            countdownElapsedTicks = countdownElapsedAt(tick);
            countdownSinceTick = -1L;
        }
    }

    public enum EventState { DRAFT, OPEN, COMPLETE, ABANDONED }

    public enum SessionType {
        PRACTICE, QUALIFYING, SPRINT, RACE;

        public boolean usesGrid() {
            return this == SPRINT || this == RACE;
        }

        public String displayName() {
            return name().toLowerCase(java.util.Locale.ROOT);
        }
    }

    public enum SessionFormat {
        TIMED_PRACTICE,
        TIMED_QUALIFYING,
        ONE_SHOT_QUALIFYING,
        TWO_SHOT_QUALIFYING,
        TIMED_RACE,
        LAP_COUNT_RACE
    }

    public enum GridSource { CONFIGURED_ENTRY_ORDER, PREVIOUS_OFFICIAL, MANUAL }

    public enum EntryStatus { ENTERED, WITHDRAWN, DISQUALIFIED }

    public enum ResultStatus { FINISHED, DNF, DNS, DSQ, NOT_CLASSIFIED }

    public record SessionConfig(long sessionId, String name, SessionType type, SessionFormat format,
                                long durationTicks, int lapLimit, long countdownTicks, long graceTicks,
                                long worldTime, GridSource gridSource) {
        public SessionConfig {
            name = requireText(name, "session name");
            if (name.length() > MAX_SESSION_NAME_LENGTH) {
                throw new IllegalArgumentException("session name must be at most " + MAX_SESSION_NAME_LENGTH + " characters");
            }
            Objects.requireNonNull(type, "type");
            Objects.requireNonNull(format, "format");
            Objects.requireNonNull(gridSource, "gridSource");
            if (sessionId <= 0L || durationTicks < 0L || lapLimit < 0 || countdownTicks < 0L || graceTicks < 0L) {
                throw new IllegalArgumentException("Session identifiers, clocks, and limits are invalid");
            }
            if (worldTime < 0L || worldTime >= 24_000L) {
                throw new IllegalArgumentException("worldTime must be between 0 and 23999");
            }
            boolean compatible = switch (type) {
                case PRACTICE -> format == SessionFormat.TIMED_PRACTICE && durationTicks > 0L;
                case QUALIFYING -> (format == SessionFormat.TIMED_QUALIFYING
                    || format == SessionFormat.ONE_SHOT_QUALIFYING || format == SessionFormat.TWO_SHOT_QUALIFYING)
                    && (durationTicks > 0L || format != SessionFormat.TIMED_QUALIFYING);
                case SPRINT, RACE -> (format == SessionFormat.TIMED_RACE && durationTicks > 0L)
                    || (format == SessionFormat.LAP_COUNT_RACE && lapLimit > 0);
            };
            if (!compatible) {
                throw new IllegalArgumentException("Session type and format are incompatible or missing their required limit");
            }
            if (!type.usesGrid() && gridSource != GridSource.CONFIGURED_ENTRY_ORDER) {
                throw new IllegalArgumentException("Only sprint and race sessions may configure a grid source");
            }
        }

        public int qualifyingAllowance() {
            return format == SessionFormat.ONE_SHOT_QUALIFYING ? 1
                : format == SessionFormat.TWO_SHOT_QUALIFYING ? 2 : 0;
        }
    }

    public record Entry(UUID driverId, String driverName, String displayCode, EntryStatus status, long registeredTick) {
        public Entry {
            Objects.requireNonNull(driverId, "driverId");
            driverName = requireText(driverName, "driverName");
            displayCode = requireText(displayCode, "displayCode");
            Objects.requireNonNull(status, "status");
            registeredTick = Math.max(0L, registeredTick);
        }
    }

    public record ResultRow(int position, UUID driverId, String driverName, ResultStatus status, int completedLaps,
                            int bestLapMillis, long finishTick, int timePenaltyMillis) {
        public ResultRow {
            position = Math.max(1, position);
            Objects.requireNonNull(driverId, "driverId");
            driverName = requireText(driverName, "driverName");
            Objects.requireNonNull(status, "status");
            completedLaps = Math.max(0, completedLaps);
            bestLapMillis = Math.max(0, bestLapMillis);
            finishTick = Math.max(0L, finishTick);
            timePenaltyMillis = Math.max(0, timePenaltyMillis);
        }

        private ResultRow withPosition(int value) {
            return new ResultRow(value, driverId, driverName, status, completedLaps, bestLapMillis, finishTick, timePenaltyMillis);
        }
    }

    public record SessionResult(int revision, boolean official, List<ResultRow> rows, long publishedTick,
                                UUID publishedBy, String publishedByName) {
        public SessionResult {
            if (revision <= 0) {
                throw new IllegalArgumentException("Result revision must be positive");
            }
            rows = List.copyOf(rows);
            publishedTick = Math.max(0L, publishedTick);
            publishedByName = publishedByName == null ? "" : publishedByName;
        }
    }

    public record AuditEntry(long tick, UUID actorId, String actorName, String action, String detail) {
        public AuditEntry {
            tick = Math.max(0L, tick);
            actorName = actorName == null ? "" : actorName;
            action = requireText(action, "action");
            detail = detail == null ? "" : detail;
        }
    }

    public record SessionView(SessionConfig config, RaceSessionState state, RaceSessionState suspendedFrom,
                              RaceSessionSuspensionReason suspensionReason, long elapsedTicks, long remainingTicks,
                              long countdownRemainingTicks, SessionResult result, List<UUID> grid) {
        public SessionView {
            grid = List.copyOf(grid);
        }
    }

    public record SessionSnapshot(SessionConfig config, RaceSessionLifecycle.Checkpoint lifecycle, SessionResult result,
                                  List<UUID> grid, long countdownElapsedTicks) {
        public SessionSnapshot {
            grid = List.copyOf(grid);
            countdownElapsedTicks = Math.max(0L, countdownElapsedTicks);
        }
    }

    public record Snapshot(UUID eventId, String name, UUID trackId, String dimensionId, EventState state,
                           int activeSessionIndex, List<SessionSnapshot> sessions, List<Entry> entries,
                           List<AuditEntry> audit) {
        public Snapshot {
            sessions = List.copyOf(sessions);
            entries = List.copyOf(entries);
            audit = List.copyOf(audit);
        }
    }

    public record RegistrationResult(boolean registered, boolean updated, Entry entry) {
    }
}
