package com.openwheelracing.content.race;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.openwheelracing.OpenwheelRacing;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

/** Persistent, player-bound balance-of-performance assignments. */
public final class BoPProfileState extends SavedData {
    public static final int MIN_WEIGHT_PERCENT = -10;
    public static final int MAX_WEIGHT_PERCENT = 20;
    public static final int MIN_POWER_PERCENT = -30;
    public static final int MAX_POWER_PERCENT = 10;
    private record Profile(int weightPercent, int powerPercent) {
        private static final Codec<Profile> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.INT.optionalFieldOf("weight_percent", 0).forGetter(Profile::weightPercent),
            Codec.INT.optionalFieldOf("power_percent", 0).forGetter(Profile::powerPercent)
        ).apply(i, Profile::new));
    }
    private static final Codec<BoPProfileState> CODEC = Codec.unboundedMap(Codec.STRING, Profile.CODEC)
        .xmap(BoPProfileState::new, state -> state.profiles.entrySet().stream()
            .collect(java.util.stream.Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue)));
    private static final SavedDataType<BoPProfileState> TYPE = new SavedDataType<>(
        OpenwheelRacing.MODID + "_bop_profiles", BoPProfileState::new, CODEC, null);
    private final Map<String, Profile> profiles = new HashMap<>();

    public BoPProfileState() {}
    private BoPProfileState(Map<String, Profile> profiles) { this.profiles.putAll(profiles); }
    public static BoPProfileState get(ServerLevel level) { return level.getDataStorage().computeIfAbsent(TYPE); }

    public ProfileValues get(UUID driverId) {
        Profile p = profiles.get(driverId.toString());
        return p == null ? new ProfileValues(0, 0) : new ProfileValues(p.weightPercent(), p.powerPercent());
    }
    public void set(UUID driverId, int weightPercent, int powerPercent) {
        if (driverId == null) return;
        profiles.put(driverId.toString(), new Profile(
            clamp(weightPercent, MIN_WEIGHT_PERCENT, MAX_WEIGHT_PERCENT),
            clamp(powerPercent, MIN_POWER_PERCENT, MAX_POWER_PERCENT)));
        setDirty();
    }
    private static int clamp(int value, int min, int max) { return Math.max(min, Math.min(max, value)); }
    public record ProfileValues(int weightPercent, int powerPercent) {
        public double weightMultiplier() { return BoPMath.multiplierFromPercent(weightPercent); }
        public double powerMultiplier() { return BoPMath.multiplierFromPercent(powerPercent); }
    }
}
