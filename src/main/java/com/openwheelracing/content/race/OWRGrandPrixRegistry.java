package com.openwheelracing.content.race;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.openwheelracing.OpenwheelRacing;
import com.openwheelracing.content.race.session.RaceSessionLifecycle;
import com.openwheelracing.content.race.session.RaceSessionState;
import com.openwheelracing.content.race.session.RaceSessionSuspensionReason;
import com.openwheelracing.content.race.weekend.GrandPrixWeekend;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

/** Persistent server-wide driver entry registry for Grand Prix weekends. */
public final class OWRGrandPrixRegistry extends SavedData {
    public static final int MAX_GP_NAME_LENGTH = 80;
    public static final int MAX_DISPLAY_CODE_LENGTH = 16;
    private static final Codec<UUID> UUID_CODEC = Codec.STRING.xmap(UUID::fromString, UUID::toString);
    private static final Codec<Entry> ENTRY_CODEC = RecordCodecBuilder.create(instance -> instance.group(
        Codec.STRING.fieldOf("gp_key").forGetter(Entry::gpKey),
        Codec.STRING.fieldOf("gp_name").forGetter(Entry::gpName),
        UUID_CODEC.fieldOf("player_id").forGetter(Entry::playerId),
        Codec.STRING.fieldOf("player_name").forGetter(Entry::playerName),
        Codec.STRING.fieldOf("display_code").forGetter(Entry::displayCode),
        Codec.LONG.optionalFieldOf("registered_game_time", 0L).forGetter(Entry::registeredGameTime)
    ).apply(instance, Entry::new));
    private static final Codec<GrandPrixWeekend.EventState> EVENT_STATE_CODEC = enumCodec(GrandPrixWeekend.EventState.class);
    private static final Codec<GrandPrixWeekend.SessionType> SESSION_TYPE_CODEC = enumCodec(GrandPrixWeekend.SessionType.class);
    private static final Codec<GrandPrixWeekend.SessionFormat> SESSION_FORMAT_CODEC = enumCodec(GrandPrixWeekend.SessionFormat.class);
    private static final Codec<GrandPrixWeekend.GridSource> GRID_SOURCE_CODEC = enumCodec(GrandPrixWeekend.GridSource.class);
    private static final Codec<GrandPrixWeekend.EntryStatus> ENTRY_STATUS_CODEC = enumCodec(GrandPrixWeekend.EntryStatus.class);
    private static final Codec<GrandPrixWeekend.ResultStatus> RESULT_STATUS_CODEC = enumCodec(GrandPrixWeekend.ResultStatus.class);
    private static final Codec<RaceSessionState> SESSION_STATE_CODEC = enumCodec(RaceSessionState.class);
    private static final Codec<RaceSessionSuspensionReason> SUSPENSION_REASON_CODEC = enumCodec(RaceSessionSuspensionReason.class);
    private static final Codec<GrandPrixWeekend.SessionConfig> SESSION_CONFIG_CODEC = RecordCodecBuilder.create(instance -> instance.group(
        Codec.LONG.fieldOf("session_id").forGetter(GrandPrixWeekend.SessionConfig::sessionId),
        Codec.STRING.fieldOf("name").forGetter(GrandPrixWeekend.SessionConfig::name),
        SESSION_TYPE_CODEC.fieldOf("type").forGetter(GrandPrixWeekend.SessionConfig::type),
        SESSION_FORMAT_CODEC.fieldOf("format").forGetter(GrandPrixWeekend.SessionConfig::format),
        Codec.LONG.optionalFieldOf("duration_ticks", 0L).forGetter(GrandPrixWeekend.SessionConfig::durationTicks),
        Codec.INT.optionalFieldOf("lap_limit", 0).forGetter(GrandPrixWeekend.SessionConfig::lapLimit),
        Codec.LONG.optionalFieldOf("countdown_ticks", 100L).forGetter(GrandPrixWeekend.SessionConfig::countdownTicks),
        Codec.LONG.optionalFieldOf("grace_ticks", 0L).forGetter(GrandPrixWeekend.SessionConfig::graceTicks),
        Codec.LONG.optionalFieldOf("world_time", 6000L).forGetter(GrandPrixWeekend.SessionConfig::worldTime),
        GRID_SOURCE_CODEC.optionalFieldOf("grid_source", GrandPrixWeekend.GridSource.CONFIGURED_ENTRY_ORDER).forGetter(GrandPrixWeekend.SessionConfig::gridSource)
    ).apply(instance, GrandPrixWeekend.SessionConfig::new));
    private static final Codec<RaceSessionLifecycle.Checkpoint> LIFECYCLE_CODEC = RecordCodecBuilder.create(instance -> instance.group(
        Codec.LONG.optionalFieldOf("duration_ticks", 0L).forGetter(RaceSessionLifecycle.Checkpoint::durationTicks),
        SESSION_STATE_CODEC.fieldOf("state").forGetter(RaceSessionLifecycle.Checkpoint::state),
        SESSION_STATE_CODEC.optionalFieldOf("suspended_from", RaceSessionState.CONFIGURED).forGetter(RaceSessionLifecycle.Checkpoint::suspendedFrom),
        SUSPENSION_REASON_CODEC.optionalFieldOf("suspension_reason", RaceSessionSuspensionReason.NONE).forGetter(RaceSessionLifecycle.Checkpoint::suspensionReason),
        Codec.LONG.optionalFieldOf("elapsed_ticks", 0L).forGetter(RaceSessionLifecycle.Checkpoint::elapsedTicks)
    ).apply(instance, RaceSessionLifecycle.Checkpoint::new));
    private static final Codec<GrandPrixWeekend.Entry> WEEKEND_ENTRY_CODEC = RecordCodecBuilder.create(instance -> instance.group(
        UUID_CODEC.fieldOf("driver_id").forGetter(GrandPrixWeekend.Entry::driverId),
        Codec.STRING.fieldOf("driver_name").forGetter(GrandPrixWeekend.Entry::driverName),
        Codec.STRING.fieldOf("display_code").forGetter(GrandPrixWeekend.Entry::displayCode),
        ENTRY_STATUS_CODEC.optionalFieldOf("status", GrandPrixWeekend.EntryStatus.ENTERED).forGetter(GrandPrixWeekend.Entry::status),
        Codec.LONG.optionalFieldOf("registered_tick", 0L).forGetter(GrandPrixWeekend.Entry::registeredTick)
    ).apply(instance, GrandPrixWeekend.Entry::new));
    private static final Codec<GrandPrixWeekend.ResultRow> RESULT_ROW_CODEC = RecordCodecBuilder.create(instance -> instance.group(
        Codec.INT.fieldOf("position").forGetter(GrandPrixWeekend.ResultRow::position),
        UUID_CODEC.fieldOf("driver_id").forGetter(GrandPrixWeekend.ResultRow::driverId),
        Codec.STRING.fieldOf("driver_name").forGetter(GrandPrixWeekend.ResultRow::driverName),
        RESULT_STATUS_CODEC.fieldOf("status").forGetter(GrandPrixWeekend.ResultRow::status),
        Codec.INT.optionalFieldOf("completed_laps", 0).forGetter(GrandPrixWeekend.ResultRow::completedLaps),
        Codec.INT.optionalFieldOf("best_lap_millis", 0).forGetter(GrandPrixWeekend.ResultRow::bestLapMillis),
        Codec.LONG.optionalFieldOf("finish_tick", 0L).forGetter(GrandPrixWeekend.ResultRow::finishTick),
        Codec.INT.optionalFieldOf("time_penalty_millis", 0).forGetter(GrandPrixWeekend.ResultRow::timePenaltyMillis)
    ).apply(instance, GrandPrixWeekend.ResultRow::new));
    private static final Codec<GrandPrixWeekend.SessionResult> SESSION_RESULT_CODEC = RecordCodecBuilder.create(instance -> instance.group(
        Codec.INT.fieldOf("revision").forGetter(GrandPrixWeekend.SessionResult::revision),
        Codec.BOOL.optionalFieldOf("official", false).forGetter(GrandPrixWeekend.SessionResult::official),
        RESULT_ROW_CODEC.listOf().optionalFieldOf("rows", List.of()).forGetter(GrandPrixWeekend.SessionResult::rows),
        Codec.LONG.optionalFieldOf("published_tick", 0L).forGetter(GrandPrixWeekend.SessionResult::publishedTick),
        optionalUuidCodec("published_by").forGetter(result -> Optional.ofNullable(result.publishedBy())),
        Codec.STRING.optionalFieldOf("published_by_name", "").forGetter(GrandPrixWeekend.SessionResult::publishedByName)
    ).apply(instance, (revision, official, rows, tick, actor, actorName) ->
        new GrandPrixWeekend.SessionResult(revision, official, rows, tick, actor.orElse(null), actorName)));
    private static final Codec<GrandPrixWeekend.AuditEntry> AUDIT_CODEC = RecordCodecBuilder.create(instance -> instance.group(
        Codec.LONG.optionalFieldOf("tick", 0L).forGetter(GrandPrixWeekend.AuditEntry::tick),
        optionalUuidCodec("actor_id").forGetter(entry -> Optional.ofNullable(entry.actorId())),
        Codec.STRING.optionalFieldOf("actor_name", "").forGetter(GrandPrixWeekend.AuditEntry::actorName),
        Codec.STRING.fieldOf("action").forGetter(GrandPrixWeekend.AuditEntry::action),
        Codec.STRING.optionalFieldOf("detail", "").forGetter(GrandPrixWeekend.AuditEntry::detail)
    ).apply(instance, (tick, actor, actorName, action, detail) ->
        new GrandPrixWeekend.AuditEntry(tick, actor.orElse(null), actorName, action, detail)));
    private static final Codec<GrandPrixWeekend.SessionSnapshot> SESSION_SNAPSHOT_CODEC = RecordCodecBuilder.create(instance -> instance.group(
        SESSION_CONFIG_CODEC.fieldOf("config").forGetter(GrandPrixWeekend.SessionSnapshot::config),
        LIFECYCLE_CODEC.fieldOf("lifecycle").forGetter(GrandPrixWeekend.SessionSnapshot::lifecycle),
        SESSION_RESULT_CODEC.optionalFieldOf("result").forGetter(snapshot -> Optional.ofNullable(snapshot.result())),
        UUID_CODEC.listOf().optionalFieldOf("grid", List.of()).forGetter(GrandPrixWeekend.SessionSnapshot::grid),
        Codec.LONG.optionalFieldOf("countdown_elapsed_ticks", 0L).forGetter(GrandPrixWeekend.SessionSnapshot::countdownElapsedTicks)
    ).apply(instance, (config, lifecycle, result, grid, countdownElapsed) ->
        new GrandPrixWeekend.SessionSnapshot(config, lifecycle, result.orElse(null), grid, countdownElapsed)));
    private static final Codec<GrandPrixWeekend.Snapshot> WEEKEND_CODEC = RecordCodecBuilder.create(instance -> instance.group(
        UUID_CODEC.fieldOf("event_id").forGetter(GrandPrixWeekend.Snapshot::eventId),
        Codec.STRING.fieldOf("name").forGetter(GrandPrixWeekend.Snapshot::name),
        UUID_CODEC.fieldOf("track_id").forGetter(GrandPrixWeekend.Snapshot::trackId),
        Codec.STRING.fieldOf("dimension_id").forGetter(GrandPrixWeekend.Snapshot::dimensionId),
        EVENT_STATE_CODEC.fieldOf("state").forGetter(GrandPrixWeekend.Snapshot::state),
        Codec.INT.optionalFieldOf("active_session_index", -1).forGetter(GrandPrixWeekend.Snapshot::activeSessionIndex),
        SESSION_SNAPSHOT_CODEC.listOf().optionalFieldOf("sessions", List.of()).forGetter(GrandPrixWeekend.Snapshot::sessions),
        WEEKEND_ENTRY_CODEC.listOf().optionalFieldOf("entries", List.of()).forGetter(GrandPrixWeekend.Snapshot::entries),
        AUDIT_CODEC.listOf().optionalFieldOf("audit", List.of()).forGetter(GrandPrixWeekend.Snapshot::audit)
    ).apply(instance, GrandPrixWeekend.Snapshot::new));
    private static final Codec<OWRGrandPrixRegistry> CODEC = RecordCodecBuilder.create(instance -> instance.group(
        ENTRY_CODEC.listOf().optionalFieldOf("entries", List.of()).forGetter(OWRGrandPrixRegistry::entriesForCodec),
        WEEKEND_CODEC.listOf().optionalFieldOf("weekends", List.of()).forGetter(OWRGrandPrixRegistry::weekendsForCodec),
        Codec.LONG.optionalFieldOf("next_session_id", 1L).forGetter(registry -> registry.nextSessionId)
    ).apply(instance, OWRGrandPrixRegistry::new));
    private static final SavedDataType<OWRGrandPrixRegistry> TYPE = new SavedDataType<>(
        OpenwheelRacing.MODID + "_grand_prix_registry", OWRGrandPrixRegistry::new, CODEC, null);

    private final List<Entry> entries = new ArrayList<>();
    private final List<GrandPrixWeekend> weekends = new ArrayList<>();
    private long nextSessionId = 1L;

    public OWRGrandPrixRegistry() {
    }

    private OWRGrandPrixRegistry(List<Entry> entries, List<GrandPrixWeekend.Snapshot> weekends, long nextSessionId) {
        for (Entry entry : entries) {
            if (valid(entry)) {
                register(entry.gpName(), entry.playerId(), entry.playerName(), entry.displayCode(), entry.registeredGameTime());
            }
        }
        for (GrandPrixWeekend.Snapshot snapshot : weekends) {
            try {
                this.weekends.add(GrandPrixWeekend.restore(snapshot));
            } catch (IllegalArgumentException | IllegalStateException ignored) {
                // One corrupt event must not make the server's SavedData unreadable.
            }
        }
        long highestSessionId = this.weekends.stream()
            .flatMap(weekend -> weekend.sessions(0L).stream())
            .mapToLong(view -> view.config().sessionId()).max().orElse(0L);
        this.nextSessionId = Math.max(Math.max(1L, nextSessionId), highestSessionId + 1L);
    }

    public static OWRGrandPrixRegistry get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(TYPE);
    }

    public RegistrationResult register(String gpName, UUID playerId, String playerName, String displayCode, long gameTime) {
        String cleanGpName = sanitizeGpName(gpName);
        Optional<GrandPrixWeekend> configuredWeekend = weekend(cleanGpName);
        if (configuredWeekend.isPresent()) {
            GrandPrixWeekend.RegistrationResult result = configuredWeekend.get().register(playerId, playerName,
                sanitizeDisplayCode(displayCode), gameTime, playerId, playerName);
            if (result.registered()) {
                setDirty();
            }
            GrandPrixWeekend.Entry entry = result.entry();
            return new RegistrationResult(result.registered(), result.updated(),
                new Entry(key(cleanGpName), configuredWeekend.get().name(), entry.driverId(), entry.driverName(),
                    entry.displayCode(), entry.registeredTick()));
        }
        String gpKey = key(cleanGpName);
        cleanGpName = entries.stream().filter(entry -> entry.gpKey().equals(gpKey)).map(Entry::gpName).findFirst().orElse(cleanGpName);
        String cleanCode = sanitizeDisplayCode(displayCode);
        String cleanPlayerName = playerName == null || playerName.isBlank() ? playerId.toString() : playerName.trim();
        Optional<Entry> collision = entries.stream()
            .filter(entry -> entry.gpKey().equals(gpKey) && entry.displayCode().equalsIgnoreCase(cleanCode))
            .filter(entry -> !entry.playerId().equals(playerId))
            .findFirst();
        if (collision.isPresent()) {
            return new RegistrationResult(false, false, collision.get());
        }

        Optional<Entry> previous = entry(cleanGpName, playerId);
        Entry updated = new Entry(gpKey, cleanGpName, playerId, cleanPlayerName, cleanCode, Math.max(0L, gameTime));
        entries.removeIf(entry -> entry.gpKey().equals(gpKey) && entry.playerId().equals(playerId));
        entries.add(updated);
        setDirty();
        return new RegistrationResult(true, previous.isPresent(), updated);
    }

    public boolean unregister(String gpName, UUID playerId) {
        Optional<GrandPrixWeekend> configuredWeekend = weekend(gpName);
        if (configuredWeekend.isPresent()) {
            boolean removed = configuredWeekend.get().unregister(playerId, 0L, playerId, "");
            if (removed) {
                setDirty();
            }
            return removed;
        }
        String gpKey = key(sanitizeGpName(gpName));
        boolean removed = entries.removeIf(entry -> entry.gpKey().equals(gpKey) && entry.playerId().equals(playerId));
        if (removed) {
            setDirty();
        }
        return removed;
    }

    public Optional<Entry> entry(String gpName, UUID playerId) {
        Optional<GrandPrixWeekend> configuredWeekend = weekend(gpName);
        if (configuredWeekend.isPresent()) {
            String gpKey = key(configuredWeekend.get().name());
            return configuredWeekend.get().entries().stream().filter(entry -> entry.driverId().equals(playerId)).findFirst()
                .map(entry -> new Entry(gpKey, configuredWeekend.get().name(), entry.driverId(), entry.driverName(),
                    entry.displayCode(), entry.registeredTick()));
        }
        String gpKey = key(sanitizeGpName(gpName));
        return entries.stream().filter(entry -> entry.gpKey().equals(gpKey) && entry.playerId().equals(playerId)).findFirst();
    }

    public List<Entry> entries(String gpName) {
        Optional<GrandPrixWeekend> configuredWeekend = weekend(gpName);
        if (configuredWeekend.isPresent()) {
            String gpKey = key(configuredWeekend.get().name());
            return configuredWeekend.get().entries().stream()
                .map(entry -> new Entry(gpKey, configuredWeekend.get().name(), entry.driverId(), entry.driverName(),
                    entry.displayCode(), entry.registeredTick()))
                .sorted(Comparator.comparing(Entry::displayCode, String.CASE_INSENSITIVE_ORDER).thenComparing(Entry::playerName))
                .toList();
        }
        String gpKey = key(sanitizeGpName(gpName));
        return entries.stream().filter(entry -> entry.gpKey().equals(gpKey))
            .sorted(Comparator.comparing(Entry::displayCode, String.CASE_INSENSITIVE_ORDER).thenComparing(Entry::playerName))
            .toList();
    }

    public List<String> grandPrixNames() {
        return java.util.stream.Stream.concat(entries.stream().map(Entry::gpName), weekends.stream().map(GrandPrixWeekend::name))
            .distinct().sorted(String.CASE_INSENSITIVE_ORDER).toList();
    }

    public GrandPrixWeekend createWeekend(String gpName, UUID trackId, String dimensionId, long tick,
                                           UUID actor, String actorName) {
        String cleanName = sanitizeGpName(gpName);
        if (weekend(cleanName).isPresent()) {
            throw new IllegalStateException("A GP weekend with that name already exists");
        }
        GrandPrixWeekend weekend = new GrandPrixWeekend(UUID.randomUUID(), cleanName, trackId, dimensionId);
        weekend.recordCreation(tick, actor, actorName);
        // Migrate entries created by the original registration-only implementation.
        for (Entry entry : entries(cleanName)) {
            weekend.register(entry.playerId(), entry.playerName(), entry.displayCode(), entry.registeredGameTime(), actor, actorName);
        }
        entries.removeIf(entry -> entry.gpKey().equals(key(cleanName)));
        weekends.add(weekend);
        setDirty();
        return weekend;
    }

    public Optional<GrandPrixWeekend> weekend(String gpName) {
        String gpKey = key(sanitizeGpName(gpName));
        return weekends.stream().filter(weekend -> key(weekend.name()).equals(gpKey)).findFirst();
    }

    public List<GrandPrixWeekend> weekends() {
        return weekends.stream().sorted(Comparator.comparing(GrandPrixWeekend::name, String.CASE_INSENSITIVE_ORDER)).toList();
    }

    public long allocateSessionId() {
        long allocated = nextSessionId++;
        setDirty();
        return allocated;
    }

    public void updateWeekend(String gpName, Consumer<GrandPrixWeekend> operation) {
        GrandPrixWeekend weekend = weekend(gpName).orElseThrow(() -> new IllegalArgumentException("Unknown GP weekend"));
        operation.accept(weekend);
        setDirty();
    }

    public void markDirty() {
        setDirty();
    }

    public boolean deleteWeekend(String gpName) {
        String gpKey = key(sanitizeGpName(gpName));
        Optional<GrandPrixWeekend> found = weekends.stream().filter(value -> key(value.name()).equals(gpKey)).findFirst();
        if (found.isEmpty()) {
            return false;
        }
        if (found.get().state() != GrandPrixWeekend.EventState.DRAFT
            && found.get().state() != GrandPrixWeekend.EventState.ABANDONED) {
            throw new IllegalStateException("Only draft or abandoned weekends may be deleted");
        }
        weekends.remove(found.get());
        setDirty();
        return true;
    }

    public static String sanitizeGpName(String value) {
        String clean = value == null ? "" : value.trim().replaceAll("\\s+", " ");
        if (clean.isEmpty() || clean.length() > MAX_GP_NAME_LENGTH) {
            throw new IllegalArgumentException("GP ID/name must be 1-" + MAX_GP_NAME_LENGTH + " characters");
        }
        return clean;
    }

    public static String sanitizeDisplayCode(String value) {
        String clean = value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
        if (clean.isEmpty() || clean.length() > MAX_DISPLAY_CODE_LENGTH || clean.chars().anyMatch(Character::isWhitespace)) {
            throw new IllegalArgumentException("Display code must be 1-" + MAX_DISPLAY_CODE_LENGTH + " characters without spaces");
        }
        return clean;
    }

    private static String key(String gpName) {
        return gpName.toLowerCase(Locale.ROOT);
    }

    private static boolean valid(Entry entry) {
        try {
            return entry != null && key(sanitizeGpName(entry.gpName())).equals(entry.gpKey())
                && sanitizeDisplayCode(entry.displayCode()).equals(entry.displayCode());
        } catch (IllegalArgumentException ignored) {
            return false;
        }
    }

    private List<Entry> entriesForCodec() {
        return List.copyOf(entries);
    }

    private List<GrandPrixWeekend.Snapshot> weekendsForCodec() {
        return weekends.stream().map(GrandPrixWeekend::snapshot).toList();
    }

    private static <E extends Enum<E>> Codec<E> enumCodec(Class<E> type) {
        return Codec.STRING.xmap(value -> Enum.valueOf(type, value.toUpperCase(Locale.ROOT)), value -> value.name().toLowerCase(Locale.ROOT));
    }

    private static com.mojang.serialization.MapCodec<Optional<UUID>> optionalUuidCodec(String field) {
        return UUID_CODEC.optionalFieldOf(field);
    }

    public record Entry(String gpKey, String gpName, UUID playerId, String playerName, String displayCode,
                        long registeredGameTime) {
    }

    public record RegistrationResult(boolean registered, boolean updated, Entry entry) {
    }
}
