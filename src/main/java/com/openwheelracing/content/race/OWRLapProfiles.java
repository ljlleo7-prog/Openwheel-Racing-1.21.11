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
    private static final Codec<List<Integer>> TIME_CODEC = Codec.INT.listOf(1, MAX_PROFILE_SAMPLES);
    private static final Codec<List<Integer>> SPEED_CODEC = Codec.INT.listOf(1, MAX_PROFILE_SAMPLES);
    private static final Codec<BestLapProfile> PROFILE_CODEC = RecordCodecBuilder.create(instance -> instance.group(
        Codec.STRING.fieldOf("dimension").forGetter(BestLapProfile::dimensionId),
        UUID_CODEC.fieldOf("track_id").forGetter(BestLapProfile::trackId),
        UUID_CODEC.fieldOf("route_id").forGetter(BestLapProfile::routeId),
        UUID_CODEC.fieldOf("driver_id").forGetter(BestLapProfile::driverId),
        Codec.STRING.fieldOf("driver_name").forGetter(BestLapProfile::driverName),
        Codec.LONG.fieldOf("lap_record_id").forGetter(BestLapProfile::lapRecordId),
        Codec.INT.fieldOf("lap_millis").forGetter(BestLapProfile::lapMillis),
        Codec.DOUBLE.fieldOf("route_length").forGetter(BestLapProfile::routeLength),
        Codec.DOUBLE.fieldOf("spacing").forGetter(BestLapProfile::spacing),
        TIME_CODEC.fieldOf("time_millis").forGetter(BestLapProfile::timeMillisList),
        SPEED_CODEC.fieldOf("speed_cmps").forGetter(BestLapProfile::speedCmpsList),
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

    public Optional<BestLapProfile> get(UUID trackId, UUID routeId, UUID driverId) {
        return profiles.stream().filter(profile -> profile.trackId().equals(trackId) && profile.routeId().equals(routeId) && profile.driverId().equals(driverId)).findFirst();
    }

    public boolean putIfFaster(BestLapProfile profile) {
        Optional<BestLapProfile> previous = get(profile.trackId(), profile.routeId(), profile.driverId());
        if (previous.isPresent() && previous.get().lapMillis() <= profile.lapMillis()) return false;
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

    public record BestLapProfile(String dimensionId, UUID trackId, UUID routeId, UUID driverId, String driverName, long lapRecordId,
                                 int lapMillis, double routeLength, double spacing, int[] timeMillis, int[] speedCmps, long createdGameTime) {
        public BestLapProfile {
            timeMillis = timeMillis.clone();
            speedCmps = speedCmps.clone();
            if (timeMillis.length == 0 || timeMillis.length > MAX_PROFILE_SAMPLES || speedCmps.length != timeMillis.length) throw new IllegalArgumentException("invalid profile samples");
        }

        private static BestLapProfile create(String dimensionId, UUID trackId, UUID routeId, UUID driverId, String driverName, long lapRecordId,
                                             int lapMillis, double routeLength, double spacing, List<Integer> times, List<Integer> speeds, long createdGameTime) {
            return new BestLapProfile(dimensionId, trackId, routeId, driverId, driverName, lapRecordId, lapMillis, routeLength, spacing,
                times.stream().mapToInt(Integer::intValue).toArray(), speeds.stream().mapToInt(Integer::intValue).toArray(), createdGameTime);
        }

        private List<Integer> timeMillisList() { return java.util.Arrays.stream(timeMillis).boxed().toList(); }
        private List<Integer> speedCmpsList() { return java.util.Arrays.stream(speedCmps).boxed().toList(); }

        public int referenceMillis(double routeDistance) {
            double normalized = normalize(routeDistance, routeLength);
            double index = normalized / spacing;
            int lower = Math.min((int) Math.floor(index), timeMillis.length - 1);
            int upper = (lower + 1) % timeMillis.length;
            double fraction = index - Math.floor(index);
            int upperTime = upper == 0 ? lapMillis : timeMillis[upper];
            return (int) Math.round(timeMillis[lower] + (upperTime - timeMillis[lower]) * fraction);
        }

        public double speedKmh(double routeDistance) {
            double index = normalize(routeDistance, routeLength) / spacing;
            int lower = Math.min((int) Math.floor(index), speedCmps.length - 1);
            int upper = (lower + 1) % speedCmps.length;
            double fraction = index - Math.floor(index);
            double cmps = speedCmps[lower] + (speedCmps[upper] - speedCmps[lower]) * fraction;
            return cmps * 0.036;
        }

        private static double normalize(double distance, double length) {
            if (length <= 0.0) return 0.0;
            double normalized = distance % length;
            return normalized < 0.0 ? normalized + length : normalized;
        }
    }
}
