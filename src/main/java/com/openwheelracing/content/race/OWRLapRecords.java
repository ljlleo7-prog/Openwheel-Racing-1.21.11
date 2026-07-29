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
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

public class OWRLapRecords extends SavedData {
    public static final int DEFAULT_MIN_VALID_LAP_TICKS = 100;
    public static final long DEFAULT_SESSION_ID = 1L;
    public static final String DEFAULT_SESSION_NAME = "Session 1";

    private static final int MAX_SESSION_NAME_LENGTH = 40;

    private static final Codec<Map<UUID, Integer>> PLAYER_BEST_LAPS_CODEC = Codec.unboundedMap(Codec.STRING, Codec.INT)
        .xmap(OWRLapRecords::unpackBestLaps, OWRLapRecords::packBestLaps);

    private static final Codec<OWRLapRecords> CODEC = RecordCodecBuilder.create(instance -> instance.group(
        PLAYER_BEST_LAPS_CODEC.optionalFieldOf("player_best_laps", Map.of()).forGetter(OWRLapRecords::playerBestLaps),
        LapRecord.CODEC.listOf().optionalFieldOf("laps", List.of()).forGetter(OWRLapRecords::laps),
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
    private long nextLapId = 1L;
    private long activeSessionId = DEFAULT_SESSION_ID;
    private String activeSessionName = DEFAULT_SESSION_NAME;
    private long nextSessionId = DEFAULT_SESSION_ID + 1L;
    private int revision;

    public OWRLapRecords() {
    }

    private OWRLapRecords(Map<UUID, Integer> playerBestLaps, List<LapRecord> laps, long nextLapId, long activeSessionId, String activeSessionName, long nextSessionId) {
        this.playerBestLaps.putAll(playerBestLaps);
        this.laps.addAll(laps);
        this.nextLapId = Math.max(nextLapId, laps.stream().mapToLong(LapRecord::id).max().orElse(0L) + 1L);
        this.activeSessionId = activeSessionId > 0L ? activeSessionId : DEFAULT_SESSION_ID;
        this.activeSessionName = sanitizeSessionName(activeSessionName, DEFAULT_SESSION_NAME);
        long highestSessionId = this.laps.stream().mapToLong(LapRecord::sessionId).max().orElse(this.activeSessionId);
        this.nextSessionId = Math.max(Math.max(nextSessionId, this.activeSessionId + 1L), highestSessionId + 1L);
    }

    public static OWRLapRecords get(ServerLevel level) {
        return level.getServer().getLevel(Level.OVERWORLD).getDataStorage().computeIfAbsent(TYPE);
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

    public int getOverallBestLapTicks() {
        int best = Integer.MAX_VALUE;
        for (int ticks : playerBestLaps.values()) {
            if (ticks > 0 && ticks < best) best = ticks;
        }
        return best == Integer.MAX_VALUE ? 0 : best;
    }

    public record DriverBest(String name, int ticks) {}

    public List<DriverBest> getPlayerBestLapsSorted() {
        Map<UUID, String> names = new HashMap<>();
        for (LapRecord lap : laps) {
            names.putIfAbsent(lap.driverId(), lap.driverName());
        }
        return playerBestLaps.entrySet().stream()
            .filter(e -> e.getValue() > 0)
            .map(e -> new DriverBest(names.getOrDefault(e.getKey(), "?"), e.getValue()))
            .sorted(Comparator.comparingInt(DriverBest::ticks))
            .toList();
    }

    public List<DriverBest> getActiveSessionBestLapsSorted() {
        Map<UUID, DriverBest> bests = new HashMap<>();
        for (LapRecord lap : laps) {
            if (lap.sessionId() != activeSessionId || lap.invalidated() || lap.lapTicks() <= 0) {
                continue;
            }
            DriverBest previous = bests.get(lap.driverId());
            if (previous == null || lap.lapTicks() < previous.ticks()) {
                bests.put(lap.driverId(), new DriverBest(lap.driverName(), lap.lapTicks()));
            }
        }
        return bests.values().stream()
            .sorted(Comparator.comparingInt(DriverBest::ticks))
            .toList();
    }

    public LapRecord recordLap(UUID driverId, String driverName, int lapTicks, long completedGameTime, String dimensionId, long startFinishPos, int checkpointCount, CarSnapshot car) {
        LapRecord record = new LapRecord(nextLapId++, driverId, driverName, lapTicks, completedGameTime, dimensionId, startFinishPos, checkpointCount, car, false, "", "", activeSessionId, activeSessionName);
        laps.add(record);
        setBestLapIfBetter(driverId, lapTicks);
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

    public boolean setBestLapIfBetter(UUID playerId, int ticks) {
        if (ticks <= 0) {
            return false;
        }
        int previous = getBestLap(playerId);
        if (previous != 0 && ticks >= previous) {
            return false;
        }
        playerBestLaps.put(playerId, ticks);
        markChanged();
        return true;
    }

    private void recomputeBestLap(UUID playerId) {
        int best = laps.stream()
            .filter(record -> record.driverId().equals(playerId))
            .filter(record -> !record.invalidated())
            .mapToInt(LapRecord::lapTicks)
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

    private static Map<UUID, Integer> unpackBestLaps(Map<String, Integer> packed) {
        Map<UUID, Integer> unpacked = new HashMap<>();
        for (Map.Entry<String, Integer> entry : packed.entrySet()) {
            try {
                int ticks = entry.getValue();
                if (ticks > 0) {
                    unpacked.put(UUID.fromString(entry.getKey()), ticks);
                }
            } catch (IllegalArgumentException ignored) {
            }
        }
        return unpacked;
    }

    private static Map<String, Integer> packBestLaps(Map<UUID, Integer> unpacked) {
        Map<String, Integer> packed = new HashMap<>();
        unpacked.forEach((playerId, ticks) -> {
            if (ticks > 0) {
                packed.put(playerId.toString(), ticks);
            }
        });
        return packed;
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

    public record LapRecord(long id, UUID driverId, String driverName, int lapTicks, long completedGameTime, String dimensionId, long startFinishPos, int checkpointCount, CarSnapshot car, boolean invalidated, String invalidationReason, String invalidatedBy, long sessionId, String sessionName) {
        public static final Codec<LapRecord> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.LONG.fieldOf("id").forGetter(LapRecord::id),
            uuidCodec().fieldOf("driver_id").forGetter(LapRecord::driverId),
            Codec.STRING.fieldOf("driver_name").forGetter(LapRecord::driverName),
            Codec.INT.fieldOf("lap_ticks").forGetter(LapRecord::lapTicks),
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
        ).apply(instance, LapRecord::new));

        LapRecord invalidated(UUID invalidatedBy, String reason) {
            return new LapRecord(id, driverId, driverName, lapTicks, completedGameTime, dimensionId, startFinishPos, checkpointCount, car, true, reason, invalidatedBy.toString(), sessionId, sessionName);
        }
    }
}
