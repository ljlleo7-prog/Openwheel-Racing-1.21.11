package com.openwheelracing.content.race;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.openwheelracing.OpenwheelRacing;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

public class OWRLapRecords extends SavedData {
    public static final int DEFAULT_MIN_VALID_LAP_TICKS = 100;
    public static final long DEFAULT_SESSION_ID = 1L;
    public static final String DEFAULT_SESSION_NAME = "Session 1";

    private static final int MAX_SESSION_NAME_LENGTH = 40;

    private static final Codec<Map<UUID, Integer>> TIMING_MAP_CODEC = Codec.unboundedMap(Codec.STRING, Codec.INT)
        .xmap(OWRLapRecords::unpackTimingMap, OWRLapRecords::packTimingMap);
    private static final Codec<Map<UUID, Integer>> LEGACY_TICK_TIMING_MAP_CODEC = Codec.unboundedMap(Codec.STRING, Codec.INT)
        .xmap(OWRLapRecords::unpackLegacyTickTimingMap, OWRLapRecords::packTimingMap);

    private static final Codec<OWRLapRecords> CODEC = RecordCodecBuilder.create(instance -> instance.group(
        TIMING_MAP_CODEC.optionalFieldOf("player_best_lap_millis", Map.of()).forGetter(OWRLapRecords::playerBestLaps),
        LEGACY_TICK_TIMING_MAP_CODEC.optionalFieldOf("player_best_laps", Map.of()).forGetter(records -> Map.of()),
        LapRecord.CODEC.listOf().optionalFieldOf("laps", List.of()).forGetter(OWRLapRecords::laps),
        SplitBestRecord.CODEC.listOf().optionalFieldOf("split_bests", List.of()).forGetter(OWRLapRecords::splitBests),
        Codec.LONG.optionalFieldOf("next_lap_id", 1L).forGetter(OWRLapRecords::nextLapId),
        Codec.LONG.optionalFieldOf("active_session_id", DEFAULT_SESSION_ID).forGetter(OWRLapRecords::activeSessionId),
        Codec.STRING.optionalFieldOf("active_session_name", DEFAULT_SESSION_NAME).forGetter(OWRLapRecords::activeSessionName),
        Codec.LONG.optionalFieldOf("next_session_id", DEFAULT_SESSION_ID + 1L).forGetter(OWRLapRecords::nextSessionId)
    ).apply(instance, OWRLapRecords::new));

    private static final SavedDataType<OWRLapRecords> TYPE = new SavedDataType<>(
        OpenwheelRacing.MODID + "_lap_records",
        OWRLapRecords::new,
        CODEC,
        null
    );

    private final Map<UUID, Integer> playerBestLaps = new HashMap<>();
    private final List<LapRecord> laps = new ArrayList<>();
    private final List<SplitBestRecord> splitBests = new ArrayList<>();
    private long nextLapId = 1L;
    private long activeSessionId = DEFAULT_SESSION_ID;
    private String activeSessionName = DEFAULT_SESSION_NAME;
    private long nextSessionId = DEFAULT_SESSION_ID + 1L;
    private int revision;
    private boolean migratedLegacyTiming;

    public OWRLapRecords() {
    }

    private OWRLapRecords(Map<UUID, Integer> playerBestLapMillis, Map<UUID, Integer> legacyPlayerBestLapMillis, List<LapRecord> laps, List<SplitBestRecord> splitBests, long nextLapId, long activeSessionId, String activeSessionName, long nextSessionId) {
        this.playerBestLaps.putAll(legacyPlayerBestLapMillis);
        this.playerBestLaps.putAll(playerBestLapMillis);
        this.laps.addAll(laps);
        this.splitBests.addAll(splitBests);
        this.nextLapId = Math.max(nextLapId, laps.stream().mapToLong(LapRecord::id).max().orElse(0L) + 1L);
        this.activeSessionId = activeSessionId > 0L ? activeSessionId : DEFAULT_SESSION_ID;
        this.activeSessionName = sanitizeSessionName(activeSessionName, DEFAULT_SESSION_NAME);
        long highestSessionId = this.laps.stream().mapToLong(LapRecord::sessionId).max().orElse(this.activeSessionId);
        long highestSplitSessionId = this.splitBests.stream().mapToLong(SplitBestRecord::sessionId).max().orElse(this.activeSessionId);
        this.nextSessionId = Math.max(Math.max(Math.max(nextSessionId, this.activeSessionId + 1L), highestSessionId + 1L), highestSplitSessionId + 1L);
        migratedLegacyTiming = !legacyPlayerBestLapMillis.isEmpty()
            || this.laps.stream().anyMatch(LapRecord::migratedFromLegacyTicks)
            || this.splitBests.stream().anyMatch(SplitBestRecord::migratedFromLegacyTicks);
        if (migratedLegacyTiming) {
            markChanged();
        }
    }

    public static OWRLapRecords get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(TYPE);
    }

    public static OWRLapRecords getIfPresent(ServerLevel level) {
        return level.getDataStorage().get(TYPE);
    }

    public static void importLegacy(ServerLevel level, OWRLapRecords legacy, String dimensionId) {
        if (legacy == null || getIfPresent(level) != null) {
            return;
        }
        List<LapRecord> dimensionLaps = legacy.laps.stream()
            .filter(record -> record.dimensionId().equals(dimensionId))
            .toList();
        if (dimensionLaps.isEmpty()) {
            return;
        }
        OWRLapRecords copy = new OWRLapRecords(Map.of(), Map.of(), dimensionLaps, List.of(), 1L, legacy.activeSessionId, legacy.activeSessionName, legacy.nextSessionId);
        for (LapRecord lap : dimensionLaps) {
            if (!lap.invalidated() && lap.lapMillis() > 0) {
                copy.setBestLapIfBetter(lap.driverId(), lap.lapMillis());
            }
        }
        copy.markChanged();
        level.getDataStorage().set(TYPE, copy);
    }

    public boolean migratedLegacyTiming() {
        return migratedLegacyTiming;
    }

    public int getRevision() {
        return revision;
    }

    public long getActiveSessionId() {
        return activeSessionId;
    }

    public String getActiveSessionName() {
        return activeSessionName;
    }

    public void startNewSession(String name) {
        long sessionId = Math.max(nextSessionId, activeSessionId + 1L);
        activeSessionId = sessionId;
        activeSessionName = sanitizeSessionName(name, "Session " + sessionId);
        nextSessionId = sessionId + 1L;
        markChanged();
    }

    public int getLapCount() {
        return laps.size();
    }

    public int getVisibleLapCount(boolean archiveMode) {
        return (int) laps.stream().filter(record -> visibleInMode(record, archiveMode)).count();
    }

    public List<LapRecord> getRecentLaps(int page, int pageSize) {
        int start = Math.max(0, page) * pageSize;
        return laps.stream()
            .sorted(Comparator.comparingLong(LapRecord::id).reversed())
            .skip(start)
            .limit(pageSize)
            .toList();
    }

    public List<LapRecord> getVisibleLaps(boolean archiveMode, int page, int pageSize) {
        int start = Math.max(0, page) * pageSize;
        return laps.stream()
            .filter(record -> visibleInMode(record, archiveMode))
            .sorted(lapComparator(archiveMode))
            .skip(start)
            .limit(pageSize)
            .toList();
    }

    public int getBestLap(UUID playerId) {
        return playerBestLaps.getOrDefault(playerId, 0);
    }

    public int getBestLap(UUID playerId, LapTimingScope scope) {
        if (scope == LapTimingScope.ALL_TIME) {
            return getBestLap(playerId);
        }
        return laps.stream()
            .filter(lap -> lap.sessionId() == activeSessionId && lap.driverId().equals(playerId))
            .filter(lap -> !lap.invalidated() && lap.lapMillis() > 0)
            .mapToInt(LapRecord::lapMillis)
            .min()
            .orElse(0);
    }

    public int getOverallBestLapMillis() {
        int best = Integer.MAX_VALUE;
        for (int millis : playerBestLaps.values()) {
            if (millis > 0 && millis < best) best = millis;
        }
        return best == Integer.MAX_VALUE ? 0 : best;
    }

    public int getOverallBestLapMillis(LapTimingScope scope) {
        return scope == LapTimingScope.ALL_TIME ? getOverallBestLapMillis() : laps.stream()
            .filter(lap -> lap.sessionId() == activeSessionId && !lap.invalidated() && lap.lapMillis() > 0)
            .mapToInt(LapRecord::lapMillis)
            .min()
            .orElse(0);
    }

    public record DriverBest(String name, int millis) {}

    public List<DriverBest> getPlayerBestLapsSorted() {
        Map<UUID, String> names = new HashMap<>();
        for (LapRecord lap : laps) {
            names.putIfAbsent(lap.driverId(), lap.driverName());
        }
        return playerBestLaps.entrySet().stream()
            .filter(e -> e.getValue() > 0)
            .map(e -> new DriverBest(names.getOrDefault(e.getKey(), "?"), e.getValue()))
            .sorted(Comparator.comparingInt(DriverBest::millis))
            .toList();
    }

    public List<DriverBest> getActiveSessionBestLapsSorted() {
        Map<UUID, DriverBest> bests = new HashMap<>();
        for (LapRecord lap : laps) {
            if (lap.sessionId() != activeSessionId || lap.invalidated() || lap.lapMillis() <= 0) {
                continue;
            }
            DriverBest previous = bests.get(lap.driverId());
            if (previous == null || lap.lapMillis() < previous.millis()) {
                bests.put(lap.driverId(), new DriverBest(lap.driverName(), lap.lapMillis()));
            }
        }
        return bests.values().stream()
            .sorted(Comparator.comparingInt(DriverBest::millis))
            .toList();
    }

    public Optional<SplitBestRecord> getSplitBest(UUID driverId, String segmentKey) {
        return getSplitBest(LapTimingScope.SESSION, driverId, null, segmentKey);
    }

    public Optional<SplitBestRecord> getSplitBest(LapTimingScope scope, UUID driverId, UUID trackId, String segmentKey) {
        return splitBests.stream()
            .filter(record -> scope == LapTimingScope.ALL_TIME || record.sessionId() == activeSessionId)
            .filter(record -> trackId == null || record.trackId().equals(trackId))
            .filter(record -> record.driverId().equals(driverId))
            .filter(record -> record.segmentKey().equals(segmentKey))
            .min(Comparator.comparingInt(SplitBestRecord::bestMiniMillis));
    }

    public int getSessionBestMiniMillis(String segmentKey) {
        return getBestMiniMillis(LapTimingScope.SESSION, null, segmentKey);
    }

    public int getBestMiniMillis(LapTimingScope scope, UUID trackId, String segmentKey) {
        return splitBests.stream()
            .filter(record -> scope == LapTimingScope.ALL_TIME || record.sessionId() == activeSessionId)
            .filter(record -> trackId == null || record.trackId().equals(trackId))
            .filter(record -> record.segmentKey().equals(segmentKey))
            .mapToInt(SplitBestRecord::bestMiniMillis)
            .filter(millis -> millis > 0)
            .min()
            .orElse(0);
    }

    public SplitComparison compareSplit(UUID driverId, String segmentKey, int cumulativeMillis, int miniMillis) {
        return compareSplit(LapTimingScope.SESSION, driverId, null, segmentKey, cumulativeMillis, miniMillis);
    }

    public SplitComparison compareSplit(LapTimingScope scope, UUID driverId, UUID trackId, String segmentKey,
                                        int cumulativeMillis, int miniMillis) {
        Optional<SplitBestRecord> previous = getSplitBest(scope, driverId, trackId, segmentKey);
        int previousCumulative = previous.map(SplitBestRecord::bestCumulativeMillis).orElse(0);
        int previousMini = previous.map(SplitBestRecord::bestMiniMillis).orElse(0);
        int previousSessionMini = getBestMiniMillis(scope, trackId, segmentKey);
        boolean personalBestMini = previousMini == 0 || miniMillis < previousMini;
        boolean sessionBestMini = previousSessionMini == 0 || miniMillis < previousSessionMini;
        int cumulativeDelta = previousCumulative == 0 ? 0 : cumulativeMillis - previousCumulative;
        int miniDelta = previousMini == 0 ? 0 : miniMillis - previousMini;
        return new SplitComparison(previousCumulative, previousMini, previousSessionMini, cumulativeDelta, miniDelta, personalBestMini, sessionBestMini);
    }

    public void commitValidSplit(UUID driverId, String dimensionId, UUID trackId, String segmentKey, int segmentOrder, int cumulativeMillis, int miniMillis) {
        Optional<SplitBestRecord> previous = getSplitBest(LapTimingScope.SESSION, driverId, trackId, segmentKey);
        int previousCumulative = previous.map(SplitBestRecord::bestCumulativeMillis).orElse(0);
        int previousMini = previous.map(SplitBestRecord::bestMiniMillis).orElse(0);
        if (previous.isEmpty() || (cumulativeMillis > 0 && cumulativeMillis < previousCumulative) || miniMillis < previousMini) {
            SplitBestRecord updated = new SplitBestRecord(activeSessionId, driverId, dimensionId, trackId, segmentKey, segmentOrder,
                previousCumulative == 0 ? cumulativeMillis : Math.min(previousCumulative, cumulativeMillis),
                previousMini == 0 ? miniMillis : Math.min(previousMini, miniMillis),
                false);
            splitBests.removeIf(record -> record.sessionId() == activeSessionId && record.driverId().equals(driverId)
                && record.trackId().equals(trackId) && record.segmentKey().equals(segmentKey));
            splitBests.add(updated);
            markChanged();
        }
    }

    public LapRecord recordLap(UUID driverId, String driverName, int lapMillis, long completedGameTime, String dimensionId, long startFinishPos, int checkpointCount, CarSnapshot car) {
        LapRecord record = new LapRecord(nextLapId++, driverId, driverName, lapMillis, completedGameTime, dimensionId, startFinishPos, checkpointCount, car, false, "", "", activeSessionId, activeSessionName, false);
        laps.add(record);
        setBestLapIfBetter(driverId, lapMillis);
        markChanged();
        return record;
    }

    public boolean invalidateLap(long lapId, UUID invalidatedBy, String reason) {
        for (int index = 0; index < laps.size(); index++) {
            LapRecord record = laps.get(index);
            if (record.id() == lapId) {
                if (record.invalidated()) {
                    return false;
                }
                laps.set(index, record.invalidated(invalidatedBy, reason));
                recomputeBestLap(record.driverId());
                markChanged();
                return true;
            }
        }
        return false;
    }

    public Optional<LapRecord> getLap(long lapId) {
        return laps.stream().filter(record -> record.id() == lapId).findFirst();
    }

    public boolean setBestLapIfBetter(UUID playerId, int millis) {
        if (millis <= 0) {
            return false;
        }
        int previous = getBestLap(playerId);
        if (previous != 0 && millis >= previous) {
            return false;
        }
        playerBestLaps.put(playerId, millis);
        markChanged();
        return true;
    }

    private void recomputeBestLap(UUID playerId) {
        int best = laps.stream()
            .filter(record -> record.driverId().equals(playerId))
            .filter(record -> !record.invalidated())
            .mapToInt(LapRecord::lapMillis)
            .min()
            .orElse(0);
        if (best == 0) {
            playerBestLaps.remove(playerId);
        } else {
            playerBestLaps.put(playerId, best);
        }
    }

    private boolean visibleInMode(LapRecord record, boolean archiveMode) {
        return archiveMode ? record.sessionId() != activeSessionId : record.sessionId() == activeSessionId;
    }

    private Comparator<LapRecord> lapComparator(boolean archiveMode) {
        if (archiveMode) {
            return Comparator.<LapRecord>comparingLong(LapRecord::sessionId).reversed()
                .thenComparing(Comparator.comparingLong(LapRecord::id).reversed());
        }
        return Comparator.comparingLong(LapRecord::id).reversed();
    }

    private void markChanged() {
        revision++;
        setDirty();
    }

    private Map<UUID, Integer> playerBestLaps() {
        return playerBestLaps;
    }

    private List<LapRecord> laps() {
        return laps;
    }

    private List<SplitBestRecord> splitBests() {
        return splitBests;
    }

    private long nextLapId() {
        return nextLapId;
    }

    private long activeSessionId() {
        return activeSessionId;
    }

    private String activeSessionName() {
        return activeSessionName;
    }

    private long nextSessionId() {
        return nextSessionId;
    }

    private static Map<UUID, Integer> unpackTimingMap(Map<String, Integer> packed) {
        Map<UUID, Integer> unpacked = new HashMap<>();
        for (Map.Entry<String, Integer> entry : packed.entrySet()) {
            try {
                int millis = entry.getValue();
                if (millis > 0) {
                    unpacked.put(UUID.fromString(entry.getKey()), millis);
                }
            } catch (IllegalArgumentException ignored) {
            }
        }
        return unpacked;
    }

    private static Map<UUID, Integer> unpackLegacyTickTimingMap(Map<String, Integer> packed) {
        Map<UUID, Integer> unpacked = new HashMap<>();
        for (Map.Entry<String, Integer> entry : packed.entrySet()) {
            try {
                int millis = legacyTicksToMillis(entry.getValue());
                if (millis > 0) {
                    unpacked.put(UUID.fromString(entry.getKey()), millis);
                }
            } catch (IllegalArgumentException ignored) {
            }
        }
        return unpacked;
    }

    private static Map<String, Integer> packTimingMap(Map<UUID, Integer> unpacked) {
        Map<String, Integer> packed = new HashMap<>();
        unpacked.forEach((playerId, millis) -> {
            if (millis > 0) {
                packed.put(playerId.toString(), millis);
            }
        });
        return packed;
    }

    private static int legacyTicksToMillis(int ticks) {
        return ticks <= 0 ? 0 : ticks * 50;
    }

    private static String sanitizeSessionName(String name, String fallback) {
        String trimmed = name == null ? "" : name.trim();
        if (trimmed.isBlank()) {
            trimmed = fallback;
        }
        if (trimmed.length() > MAX_SESSION_NAME_LENGTH) {
            trimmed = trimmed.substring(0, MAX_SESSION_NAME_LENGTH).trim();
        }
        return trimmed.isBlank() ? fallback : trimmed;
    }

    private static Codec<UUID> uuidCodec() {
        return Codec.STRING.xmap(UUID::fromString, UUID::toString);
    }

    public record CarSnapshot(int power, int grip, int aero, int gearing, int damagePercent, int tyreWearPercent, boolean absEnabled) {
        public static final Codec<CarSnapshot> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.INT.fieldOf("power").forGetter(CarSnapshot::power),
            Codec.INT.fieldOf("grip").forGetter(CarSnapshot::grip),
            Codec.INT.fieldOf("aero").forGetter(CarSnapshot::aero),
            Codec.INT.fieldOf("gearing").forGetter(CarSnapshot::gearing),
            Codec.INT.fieldOf("damage_percent").forGetter(CarSnapshot::damagePercent),
            Codec.INT.fieldOf("tyre_wear_percent").forGetter(CarSnapshot::tyreWearPercent),
            Codec.BOOL.fieldOf("abs_enabled").forGetter(CarSnapshot::absEnabled)
        ).apply(instance, CarSnapshot::new));
    }

    public record SplitComparison(int previousCumulativeMillis, int previousMiniMillis, int previousSessionMiniMillis, int cumulativeDeltaMillis, int miniDeltaMillis, boolean personalBestMini, boolean sessionBestMini) {
    }

    public record SplitBestRecord(long sessionId, UUID driverId, String dimensionId, UUID trackId, String segmentKey, int segmentOrder, int bestCumulativeMillis, int bestMiniMillis, boolean migratedFromLegacyTicks) {
        public static final Codec<SplitBestRecord> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.LONG.fieldOf("session_id").forGetter(SplitBestRecord::sessionId),
            uuidCodec().fieldOf("driver_id").forGetter(SplitBestRecord::driverId),
            Codec.STRING.fieldOf("dimension_id").forGetter(SplitBestRecord::dimensionId),
            uuidCodec().fieldOf("track_id").forGetter(SplitBestRecord::trackId),
            Codec.STRING.fieldOf("segment_key").forGetter(SplitBestRecord::segmentKey),
            Codec.INT.fieldOf("segment_order").forGetter(SplitBestRecord::segmentOrder),
            Codec.INT.optionalFieldOf("best_cumulative_millis", 0).forGetter(SplitBestRecord::bestCumulativeMillis),
            Codec.INT.optionalFieldOf("best_cumulative_ticks", 0).forGetter(record -> 0),
            Codec.INT.optionalFieldOf("best_mini_millis", 0).forGetter(SplitBestRecord::bestMiniMillis),
            Codec.INT.optionalFieldOf("best_mini_ticks", 0).forGetter(record -> 0)
        ).apply(instance, SplitBestRecord::create));

        private static SplitBestRecord create(long sessionId, UUID driverId, String dimensionId, UUID trackId, String segmentKey, int segmentOrder, int bestCumulativeMillis, int legacyBestCumulativeTicks, int bestMiniMillis, int legacyBestMiniTicks) {
            int cumulativeMillis = bestCumulativeMillis > 0 ? bestCumulativeMillis : legacyTicksToMillis(legacyBestCumulativeTicks);
            int miniMillis = bestMiniMillis > 0 ? bestMiniMillis : legacyTicksToMillis(legacyBestMiniTicks);
            return new SplitBestRecord(sessionId, driverId, dimensionId, trackId, segmentKey, segmentOrder, cumulativeMillis, miniMillis, bestCumulativeMillis <= 0 && legacyBestCumulativeTicks > 0 || bestMiniMillis <= 0 && legacyBestMiniTicks > 0);
        }

        public SplitBestRecord {
            dimensionId = dimensionId == null || dimensionId.isBlank() ? "minecraft:overworld" : dimensionId;
            segmentKey = segmentKey == null || segmentKey.isBlank() ? "segment_" + Math.max(0, segmentOrder) : segmentKey;
            segmentOrder = Math.max(0, segmentOrder);
            bestCumulativeMillis = Math.max(1, bestCumulativeMillis);
            bestMiniMillis = Math.max(1, bestMiniMillis);
        }
    }

    public record LapRecord(long id, UUID driverId, String driverName, int lapMillis, long completedGameTime, String dimensionId, long startFinishPos, int checkpointCount, CarSnapshot car, boolean invalidated, String invalidationReason, String invalidatedBy, long sessionId, String sessionName, boolean migratedFromLegacyTicks) {
        public static final Codec<LapRecord> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.LONG.fieldOf("id").forGetter(LapRecord::id),
            uuidCodec().fieldOf("driver_id").forGetter(LapRecord::driverId),
            Codec.STRING.fieldOf("driver_name").forGetter(LapRecord::driverName),
            Codec.INT.optionalFieldOf("lap_millis", 0).forGetter(LapRecord::lapMillis),
            Codec.INT.optionalFieldOf("lap_ticks", 0).forGetter(record -> 0),
            Codec.LONG.fieldOf("completed_game_time").forGetter(LapRecord::completedGameTime),
            Codec.STRING.fieldOf("dimension_id").forGetter(LapRecord::dimensionId),
            Codec.LONG.fieldOf("start_finish_pos").forGetter(LapRecord::startFinishPos),
            Codec.INT.fieldOf("checkpoint_count").forGetter(LapRecord::checkpointCount),
            CarSnapshot.CODEC.fieldOf("car").forGetter(LapRecord::car),
            Codec.BOOL.optionalFieldOf("invalidated", false).forGetter(LapRecord::invalidated),
            Codec.STRING.optionalFieldOf("invalidation_reason", "").forGetter(LapRecord::invalidationReason),
            Codec.STRING.optionalFieldOf("invalidated_by", "").forGetter(LapRecord::invalidatedBy),
            Codec.LONG.optionalFieldOf("session_id", DEFAULT_SESSION_ID).forGetter(LapRecord::sessionId),
            Codec.STRING.optionalFieldOf("session_name", DEFAULT_SESSION_NAME).forGetter(LapRecord::sessionName)
        ).apply(instance, LapRecord::create));

        private static LapRecord create(long id, UUID driverId, String driverName, int lapMillis, int legacyLapTicks, long completedGameTime, String dimensionId, long startFinishPos, int checkpointCount, CarSnapshot car, boolean invalidated, String invalidationReason, String invalidatedBy, long sessionId, String sessionName) {
            int normalizedLapMillis = lapMillis > 0 ? lapMillis : legacyTicksToMillis(legacyLapTicks);
            return new LapRecord(id, driverId, driverName, normalizedLapMillis, completedGameTime, dimensionId, startFinishPos, checkpointCount, car, invalidated, invalidationReason, invalidatedBy, sessionId, sessionName, lapMillis <= 0 && legacyLapTicks > 0);
        }

        LapRecord invalidated(UUID invalidatedBy, String reason) {
            return new LapRecord(id, driverId, driverName, lapMillis, completedGameTime, dimensionId, startFinishPos, checkpointCount, car, true, reason, invalidatedBy.toString(), sessionId, sessionName, migratedFromLegacyTicks);
        }
    }
}
