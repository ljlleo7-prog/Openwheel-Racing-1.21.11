package com.openwheelracing.content.race;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.openwheelracing.OpenwheelRacing;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public final class OWRLapProfiles extends SavedData {
    public static final int MAX_PROFILE_SAMPLES = 4096;
    public static final int MAX_PROFILES_PER_ROUTE = 256;
    private static final Codec<UUID> UUID_CODEC = Codec.STRING.xmap(UUID::fromString, UUID::toString);
    private static final Codec<Origin> ORIGIN_CODEC = Codec.STRING.xmap(value -> Origin.valueOf(value.toUpperCase(java.util.Locale.ROOT)), origin -> origin.name().toLowerCase(java.util.Locale.ROOT));
    private static final Codec<List<Integer>> TIME_CODEC = Codec.INT.listOf(1, MAX_PROFILE_SAMPLES);
    private static final Codec<List<Integer>> SPEED_CODEC = Codec.INT.listOf(1, MAX_PROFILE_SAMPLES);
    private static final Codec<List<Integer>> LINE_CODEC = Codec.INT.listOf(1, MAX_PROFILE_SAMPLES);
    private static final Codec<BestLapProfile> PROFILE_CODEC = RecordCodecBuilder.create(instance -> instance.group(
        Codec.STRING.fieldOf("dimension").forGetter(BestLapProfile::dimensionId),
        UUID_CODEC.fieldOf("track_id").forGetter(BestLapProfile::trackId),
        UUID_CODEC.fieldOf("route_id").forGetter(BestLapProfile::routeId),
        UUID_CODEC.fieldOf("driver_id").forGetter(BestLapProfile::driverId),
        Codec.STRING.fieldOf("driver_name").forGetter(BestLapProfile::driverName),
        ORIGIN_CODEC.optionalFieldOf("origin", Origin.PLAYER).forGetter(BestLapProfile::origin),
        Codec.LONG.fieldOf("lap_record_id").forGetter(BestLapProfile::lapRecordId),
        Codec.INT.fieldOf("lap_millis").forGetter(BestLapProfile::lapMillis),
        Codec.DOUBLE.fieldOf("route_length").forGetter(BestLapProfile::routeLength),
        Codec.DOUBLE.fieldOf("spacing").forGetter(BestLapProfile::spacing),
        TIME_CODEC.fieldOf("time_millis").forGetter(BestLapProfile::timeMillisList),
        SPEED_CODEC.fieldOf("speed_cmps").forGetter(BestLapProfile::speedCmpsList),
        LINE_CODEC.optionalFieldOf("lateral_offset_cm", List.of()).forGetter(BestLapProfile::lateralOffsetList),
        LINE_CODEC.optionalFieldOf("heading_residual_millirad", List.of()).forGetter(BestLapProfile::headingResidualList),
        Codec.LONG.fieldOf("created_game_time").forGetter(BestLapProfile::createdGameTime)
    ).apply(instance, BestLapProfile::create));
    private static final Codec<OWRLapProfiles> CODEC = RecordCodecBuilder.create(instance -> instance.group(
        PROFILE_CODEC.listOf().optionalFieldOf("profiles", List.of()).forGetter(OWRLapProfiles::profiles)
    ).apply(instance, OWRLapProfiles::new));
    private static final SavedDataType<OWRLapProfiles> TYPE = new SavedDataType<>(OpenwheelRacing.MODID + "_lap_profiles", OWRLapProfiles::new, CODEC, null);

    private final List<BestLapProfile> profiles = new ArrayList<>();
    private int revision;

    public OWRLapProfiles() {}

    private OWRLapProfiles(List<BestLapProfile> profiles) {
        this.profiles.addAll(profiles);
    }

    public static OWRLapProfiles get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(TYPE);
    }

    public List<BestLapProfile> matching(UUID trackId, UUID routeId) {
        return profiles.stream().filter(profile -> profile.trackId().equals(trackId) && profile.routeId().equals(routeId)).toList();
    }

    public List<BestLapProfile> matching(UUID trackId, UUID routeId, Origin origin) {
        return profiles.stream().filter(profile -> profile.trackId().equals(trackId) && profile.routeId().equals(routeId) && profile.origin() == origin).toList();
    }

    public Optional<BestLapProfile> fastestValidPlayer(UUID trackId, UUID routeId, double routeLength) {
        return profiles.stream()
            .filter(profile -> profile.origin() == Origin.PLAYER && profile.trackId().equals(trackId) && profile.routeId().equals(routeId))
            .filter(BestLapProfile::hasRecordedLine)
            .filter(profile -> profile.lapMillis() > 0 && Math.abs(profile.routeLength() - routeLength) <= Math.max(2.0, routeLength * 0.01))
            .min(Comparator.comparingInt(BestLapProfile::lapMillis));
    }

    public Optional<BestLapProfile> get(UUID trackId, UUID routeId, UUID driverId) {
        return profiles.stream().filter(profile -> profile.trackId().equals(trackId) && profile.routeId().equals(routeId) && profile.driverId().equals(driverId)).findFirst();
    }

    public boolean putIfFaster(BestLapProfile profile) {
        Optional<BestLapProfile> previous = get(profile.trackId(), profile.routeId(), profile.driverId());
        if (previous.isPresent() && previous.get().lapMillis() <= profile.lapMillis()
            && (previous.get().hasRecordedLine() || !profile.hasRecordedLine())) return false;
        profiles.removeIf(existing -> existing.trackId().equals(profile.trackId()) && existing.routeId().equals(profile.routeId()) && existing.driverId().equals(profile.driverId()));
        profiles.add(profile);
        trim(profile.trackId(), profile.routeId());
        revision++;
        setDirty();
        return true;
    }

    public boolean removeByLapRecord(long lapRecordId) {
        boolean removed = profiles.removeIf(profile -> profile.lapRecordId() == lapRecordId);
        if (removed) {
            revision++;
            setDirty();
        }
        return removed;
    }

    public int revision() { return revision; }

    private void trim(UUID trackId, UUID routeId) {
        List<BestLapProfile> matching = profiles.stream().filter(profile -> profile.trackId().equals(trackId) && profile.routeId().equals(routeId))
            .sorted(Comparator.comparingLong(BestLapProfile::createdGameTime).reversed()).toList();
        if (matching.size() <= MAX_PROFILES_PER_ROUTE) return;
        matching.subList(MAX_PROFILES_PER_ROUTE, matching.size()).forEach(profiles::remove);
    }

    private List<BestLapProfile> profiles() { return profiles; }

    public enum Origin {
        PLAYER,
        AI
    }

    public record BestLapProfile(String dimensionId, UUID trackId, UUID routeId, UUID driverId, String driverName, Origin origin, long lapRecordId,
                                 int lapMillis, double routeLength, double spacing, int[] timeMillis, int[] speedCmps,
                                 int[] lateralOffsetCm, int[] headingResidualMilliRad, long createdGameTime) {
        public BestLapProfile {
            timeMillis = timeMillis.clone();
            speedCmps = speedCmps.clone();
            lateralOffsetCm = lateralOffsetCm == null || lateralOffsetCm.length != timeMillis.length ? new int[0] : lateralOffsetCm.clone();
            headingResidualMilliRad = headingResidualMilliRad == null || headingResidualMilliRad.length != timeMillis.length ? new int[0] : headingResidualMilliRad.clone();
            if (timeMillis.length == 0 || timeMillis.length > MAX_PROFILE_SAMPLES || speedCmps.length != timeMillis.length) throw new IllegalArgumentException("invalid profile samples");
        }

        public BestLapProfile(String dimensionId, UUID trackId, UUID routeId, UUID driverId, String driverName, long lapRecordId,
                              int lapMillis, double routeLength, double spacing, int[] timeMillis, int[] speedCmps, long createdGameTime) {
            this(dimensionId, trackId, routeId, driverId, driverName, Origin.PLAYER, lapRecordId, lapMillis, routeLength, spacing, timeMillis, speedCmps,
                new int[0], new int[0], createdGameTime);
        }

        public BestLapProfile(String dimensionId, UUID trackId, UUID routeId, UUID driverId, String driverName, long lapRecordId,
                              int lapMillis, double routeLength, double spacing, int[] timeMillis, int[] speedCmps,
                              int[] lateralOffsetCm, int[] headingResidualMilliRad, long createdGameTime) {
            this(dimensionId, trackId, routeId, driverId, driverName, Origin.PLAYER, lapRecordId, lapMillis, routeLength, spacing,
                timeMillis, speedCmps, lateralOffsetCm, headingResidualMilliRad, createdGameTime);
        }

        private static BestLapProfile create(String dimensionId, UUID trackId, UUID routeId, UUID driverId, String driverName, Origin origin, long lapRecordId,
                                             int lapMillis, double routeLength, double spacing, List<Integer> times, List<Integer> speeds,
                                             List<Integer> lateralOffsets, List<Integer> headingResiduals, long createdGameTime) {
            return new BestLapProfile(dimensionId, trackId, routeId, driverId, driverName, origin, lapRecordId, lapMillis, routeLength, spacing,
                times.stream().mapToInt(Integer::intValue).toArray(), speeds.stream().mapToInt(Integer::intValue).toArray(),
                lateralOffsets.stream().mapToInt(Integer::intValue).toArray(), headingResiduals.stream().mapToInt(Integer::intValue).toArray(), createdGameTime);
        }

        private List<Integer> timeMillisList() { return java.util.Arrays.stream(timeMillis).boxed().toList(); }
        private List<Integer> speedCmpsList() { return java.util.Arrays.stream(speedCmps).boxed().toList(); }
        private List<Integer> lateralOffsetList() { return java.util.Arrays.stream(lateralOffsetCm).boxed().toList(); }
        private List<Integer> headingResidualList() { return java.util.Arrays.stream(headingResidualMilliRad).boxed().toList(); }

        public boolean hasRecordedLine() {
            return lateralOffsetCm.length == timeMillis.length && headingResidualMilliRad.length == timeMillis.length;
        }
        public double lateralOffsetMeters(double routeDistance) { return lateralOffsetCm.length == 0 ? 0.0 : interpolate(lateralOffsetCm, routeDistance) * 0.01; }
        public double headingResidualRadians(double routeDistance) { return headingResidualMilliRad.length == 0 ? 0.0 : interpolate(headingResidualMilliRad, routeDistance) * 0.001; }
        private double interpolate(int[] values, double routeDistance) {
            double index = normalize(routeDistance, routeLength) / spacing;
            int lower = Math.min((int) Math.floor(index), values.length - 1);
            int upper = (lower + 1) % values.length;
            double fraction = index - Math.floor(index);
            return values[lower] + (values[upper] - values[lower]) * fraction;
        }

        public int referenceMillis(double routeDistance) {
            double index = normalize(routeDistance, routeLength) / spacing;
            int lower = Math.min((int) Math.floor(index), timeMillis.length - 1);
            int upper = (lower + 1) % timeMillis.length;
            double fraction = index - Math.floor(index);
            int upperTime = upper == 0 ? lapMillis : timeMillis[upper];
            return (int) Math.round(timeMillis[lower] + (upperTime - timeMillis[lower]) * fraction);
        }

        public double speedKmh(double routeDistance) {
            return interpolate(speedCmps, routeDistance) * 0.036;
        }

        private static double normalize(double distance, double length) {
            if (length <= 0.0) return 0.0;
            double normalized = distance % length;
            return normalized < 0.0 ? normalized + length : normalized;
        }
    }
}
