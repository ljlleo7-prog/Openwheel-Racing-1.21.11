package com.openwheelracing.content.ai;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.openwheelracing.OpenwheelRacing;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class OWRAiTrainingData extends SavedData {
    private static final Codec<UUID> UUID_CODEC = Codec.STRING.xmap(UUID::fromString, UUID::toString);
    private static final Codec<Incident> INCIDENT_CODEC = RecordCodecBuilder.create(instance -> instance.group(
        Codec.DOUBLE.fieldOf("route_distance").forGetter(Incident::routeDistance),
        Codec.INT.optionalFieldOf("count", 1).forGetter(Incident::count),
        Codec.INT.optionalFieldOf("trial", 0).forGetter(Incident::trial),
        Codec.DOUBLE.optionalFieldOf("exploration_offset", 0.0).forGetter(Incident::explorationOffset)
    ).apply(instance, (distance, count, trial, offset) -> new Incident(distance, count, trial, offset)));
    private static final Codec<Prefix> PREFIX_CODEC = RecordCodecBuilder.create(instance -> instance.group(
        Codec.DOUBLE.optionalFieldOf("start_distance", 0.0).forGetter(Prefix::startDistance),
        Codec.DOUBLE.optionalFieldOf("distance", 0.0).forGetter(Prefix::distance),
        Codec.DOUBLE.optionalFieldOf("spacing", 0.0).forGetter(Prefix::spacing),
        Codec.INT.listOf().optionalFieldOf("offsets", List.of()).forGetter(prefix -> java.util.Arrays.stream(prefix.offsets()).boxed().toList()),
        Codec.INT.listOf().optionalFieldOf("headings", List.of()).forGetter(prefix -> java.util.Arrays.stream(prefix.headings()).boxed().toList()),
        Codec.INT.listOf().optionalFieldOf("observed", List.of()).forGetter(prefix -> java.util.Arrays.stream(prefix.observed()).boxed().toList()),
        Codec.DOUBLE.optionalFieldOf("average_speed_kmh", 0.0).forGetter(Prefix::averageSpeedKmh)
    ).apply(instance, (start, distance, spacing, offsets, headings, observed, speed) -> new Prefix(start, distance, spacing,
        offsets.stream().mapToInt(Integer::intValue).toArray(), headings.stream().mapToInt(Integer::intValue).toArray(),
        observed.stream().mapToInt(Integer::intValue).toArray(), speed)));
    private static final Codec<Record> RECORD_CODEC = RecordCodecBuilder.create(instance -> instance.group(
        UUID_CODEC.fieldOf("track_id").forGetter(Record::trackId), UUID_CODEC.fieldOf("route_id").forGetter(Record::routeId),
        Codec.STRING.fieldOf("archetype").forGetter(Record::archetype), Codec.BOOL.optionalFieldOf("enabled", true).forGetter(Record::enabled),
        Codec.INT.optionalFieldOf("valid_laps", 0).forGetter(Record::validLaps), Codec.INT.optionalFieldOf("rejected_laps", 0).forGetter(Record::rejectedLaps),
        Codec.INT.optionalFieldOf("recoveries", 0).forGetter(Record::recoveries), Codec.INT.optionalFieldOf("best_lap_millis", 0).forGetter(Record::bestLapMillis),
        Codec.DOUBLE.optionalFieldOf("ema_lap_millis", 0.0).forGetter(Record::emaLapMillis), Codec.DOUBLE.optionalFieldOf("target_scale", 1.0).forGetter(Record::targetScale),
        Codec.DOUBLE.optionalFieldOf("braking_scale", 1.0).forGetter(Record::brakingScale), Codec.DOUBLE.optionalFieldOf("steering_scale", 1.0).forGetter(Record::steeringScale),
        Codec.LONG.optionalFieldOf("last_update", 0L).forGetter(Record::lastUpdate),
        INCIDENT_CODEC.listOf().optionalFieldOf("incidents", List.of()).forGetter(Record::incidents),
        PREFIX_CODEC.optionalFieldOf("prefix", new Prefix(0.0, 0.0, 0.0, new int[0], new int[0], new int[0], 0.0)).forGetter(Record::prefix)
    ).apply(instance, Record::new));
    private static final Codec<OWRAiTrainingData> CODEC = RecordCodecBuilder.create(instance -> instance.group(RECORD_CODEC.listOf().optionalFieldOf("records", List.of()).forGetter(data -> data.records)).apply(instance, OWRAiTrainingData::new));
    private static final SavedDataType<OWRAiTrainingData> TYPE = new SavedDataType<>(OpenwheelRacing.MODID + "_ai_training", OWRAiTrainingData::new, CODEC, null);
    private final List<Record> records = new ArrayList<>();

    public OWRAiTrainingData() {
    }

    private OWRAiTrainingData(List<Record> records) {
        this.records.addAll(records);
    }

    public static OWRAiTrainingData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(TYPE);
    }

    public Record getOrCreate(UUID trackId, UUID routeId, String archetype) {
        return records.stream().filter(record -> record.trackId().equals(trackId) && record.routeId().equals(routeId) && record.archetype().equals(archetype)).findFirst()
            .orElseGet(() -> {
                Record created = Record.defaults(trackId, routeId, archetype);
                records.add(created);
                return created;
            });
    }

    public void save(Record record) {
        records.removeIf(existing -> existing.trackId().equals(record.trackId()) && existing.routeId().equals(record.routeId()) && existing.archetype().equals(record.archetype()));
        records.add(record);
        setDirty();
    }

    public List<Record> matching(UUID trackId, UUID routeId) {
        return records.stream().filter(record -> record.trackId().equals(trackId) && record.routeId().equals(routeId)).toList();
    }

    public int clear(UUID trackId, UUID routeId) {
        int before = records.size();
        records.removeIf(record -> record.trackId().equals(trackId) && record.routeId().equals(routeId));
        if (before != records.size()) setDirty();
        return before - records.size();
    }

    public record Record(UUID trackId, UUID routeId, String archetype, boolean enabled, int validLaps, int rejectedLaps,
                         int recoveries, int bestLapMillis, double emaLapMillis, double targetScale, double brakingScale,
                         double steeringScale, long lastUpdate, List<Incident> incidents, Prefix prefix) {
        public Record {
            incidents = List.copyOf(incidents);
            prefix = prefix == null ? new Prefix(0.0, 0.0, 0.0, new int[0], new int[0], new int[0], 0.0) : prefix;
        }

        public static Record defaults(UUID trackId, UUID routeId, String archetype) {
            return new Record(trackId, routeId, archetype, true, 0, 0, 0, 0, 0.0, 1.0, 1.0, 1.0, 0L, List.of(), new Prefix(0.0, 0.0, 0.0, new int[0], new int[0], new int[0], 0.0));
        }
        public Record withLap(int lapMillis, BasicAiTrainingMath.Update update, long gameTime) {
            int best = bestLapMillis <= 0 ? lapMillis : Math.min(bestLapMillis, lapMillis);
            double ema = emaLapMillis <= 0.0 ? lapMillis : emaLapMillis * 0.8 + lapMillis * 0.2;
            return new Record(trackId, routeId, archetype, enabled, update.validLaps(), update.rejectedLaps(), recoveries,
                best, ema, update.targetScale(), update.brakingScale(), update.steeringScale(), gameTime, incidents, prefix);
        }

        public Record withRecovery(double routeDistance, double routeLength, long gameTime) {
            double normalized = normalize(routeDistance, routeLength);
            List<Incident> updated = new ArrayList<>(incidents);
            int nearest = -1;
            for (int index = 0; index < updated.size(); index++) {
                if (circularDistance(updated.get(index).routeDistance(), normalized, routeLength) <= 12.0) {
                    nearest = index;
                    break;
                }
            }
            if (nearest >= 0) {
                Incident previous = updated.get(nearest);
                updated.set(nearest, new Incident(previous.routeDistance(), Math.min(8, previous.count() + 1), previous.trial() + 1, explorationOffset(previous.trial() + 1)));
            } else {
                updated.add(new Incident(normalized, 1, 1, explorationOffset(1)));
            }
            return new Record(trackId, routeId, archetype, enabled, validLaps, rejectedLaps + 1, recoveries + 1, bestLapMillis,
                emaLapMillis, targetScale, brakingScale, steeringScale, gameTime, updated, prefix);
        }

        public Record withPrefix(com.openwheelracing.content.race.LapProfileCollector.PrefixSnapshot candidate, long gameTime) {
            Prefix next = Prefix.from(candidate);
            if (!next.betterThan(prefix)) return this;
            return new Record(trackId, routeId, archetype, enabled, validLaps, rejectedLaps, recoveries, bestLapMillis, emaLapMillis,
                targetScale, brakingScale, steeringScale, gameTime, incidents, next);
        }

        public Record withEnabled(boolean value) { return new Record(trackId, routeId, archetype, value, validLaps, rejectedLaps, recoveries, bestLapMillis, emaLapMillis, targetScale, brakingScale, steeringScale, lastUpdate, incidents, prefix); }

        private static double explorationOffset(int trial) {
            int step = Math.min(4, Math.max(1, trial));
            double magnitude = Math.min(2.0, 0.5 * step);
            return (trial % 2 == 0 ? -1.0 : 1.0) * magnitude;
        }

        private static double normalize(double distance, double length) {
            if (!(length > 0.0)) return 0.0;
            double normalized = distance % length;
            return normalized < 0.0 ? normalized + length : normalized;
        }

        private static double circularDistance(double first, double second, double length) {
            double direct = Math.abs(first - second);
            return length > 0.0 ? Math.min(direct, length - direct) : direct;
        }
    }

    public record Prefix(double startDistance, double distance, double spacing, int[] offsets, int[] headings, int[] observed, double averageSpeedKmh) {
        public Prefix {
            offsets = offsets.clone();
            headings = headings.clone();
            observed = observed.length == offsets.length ? observed.clone() : java.util.stream.IntStream.range(0, offsets.length).map(index -> 1).toArray();
            if (offsets.length != headings.length) throw new IllegalArgumentException("invalid AI prefix");
        }

        public Prefix(double startDistance, double distance, double spacing, int[] offsets, int[] headings, double averageSpeedKmh) {
            this(startDistance, distance, spacing, offsets, headings,
                java.util.stream.IntStream.range(0, offsets.length).map(index -> 1).toArray(), averageSpeedKmh);
        }

        static Prefix from(com.openwheelracing.content.race.LapProfileCollector.PrefixSnapshot snapshot) {
            return new Prefix(snapshot.startDistance(), snapshot.distance(), snapshot.spacing(), snapshot.lateralOffsetCm(), snapshot.headingResidualMilliRad(), snapshot.observed(), snapshot.averageSpeedKmh());
        }

        boolean betterThan(Prefix previous) {
            if (distance > previous.distance + 0.01) return true;
            if (distance + 0.01 < previous.distance) return false;
            if (offsets.length != previous.offsets.length) return offsets.length > previous.offsets.length;
            return averageSpeedKmh > previous.averageSpeedKmh;
        }
    }

    public record Incident(double routeDistance, int count, int trial, double explorationOffset) {
    }
}
