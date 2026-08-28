package com.openwheelracing.content.race;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.openwheelracing.OpenwheelRacing;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

public class OWRRaceControlState extends SavedData {
    public static final int DEFAULT_RACE_LAP_LIMIT = 12;
    public static final int MAX_RACE_LAP_LIMIT = 999;
    private static final double[] CONDITION_MODIFIERS = {0.0, 0.5, 1.0, 2.0, 5.0, 10.0};

    private static final Codec<OWRRaceControlState> CODEC = RecordCodecBuilder.create(instance -> instance.group(
        Codec.BOOL.optionalFieldOf("checkpoint_check_enabled", false).forGetter(OWRRaceControlState::isCheckpointCheckEnabled),
        Codec.BOOL.optionalFieldOf("off_track_check_enabled", true).forGetter(OWRRaceControlState::isOffTrackCheckEnabled),
        Codec.INT.optionalFieldOf("minimum_valid_lap_ticks", OWRLapRecords.DEFAULT_MIN_VALID_LAP_TICKS).forGetter(OWRRaceControlState::getMinimumValidLapTicks),
        Codec.BOOL.optionalFieldOf("wheel_input_allowed", true).forGetter(OWRRaceControlState::isWheelInputAllowed),
        Codec.BOOL.optionalFieldOf("auto_shifting_allowed", true).forGetter(OWRRaceControlState::isAutoShiftingAllowed),
        Codec.INT.optionalFieldOf("max_ers_capacity_mj", 4).forGetter(OWRRaceControlState::getMaxErsCapacityMj),
        Codec.INT.optionalFieldOf("max_balanced_deploy_kw", 200).forGetter(OWRRaceControlState::getMaxBalancedDeployKw),
        Codec.INT.optionalFieldOf("max_attack_deploy_kw", 350).forGetter(OWRRaceControlState::getMaxAttackDeployKw),
        Codec.INT.optionalFieldOf("max_harvest_negative_kw", 350).forGetter(OWRRaceControlState::getMaxHarvestNegativeKw),
        Codec.INT.optionalFieldOf("global_flag", RaceFlagMode.DEFAULT.ordinal()).forGetter(state -> state.getGlobalFlag().ordinal()),
        Codec.DOUBLE.optionalFieldOf("car_damage_modifier", 1.0).forGetter(OWRRaceControlState::getCarDamageModifier),
        Codec.DOUBLE.optionalFieldOf("tyre_wear_modifier", 1.0).forGetter(OWRRaceControlState::getTyreWearModifier),
        Codec.INT.optionalFieldOf("race_lap_limit", DEFAULT_RACE_LAP_LIMIT).forGetter(OWRRaceControlState::getRaceLapLimit),
        SignalSettings.CODEC.optionalFieldOf("signals", SignalSettings.DEFAULT).forGetter(OWRRaceControlState::signalSettings)
    ).apply(instance, OWRRaceControlState::new));

    private static final SavedDataType<OWRRaceControlState> TYPE = new SavedDataType<>(
        OpenwheelRacing.MODID + "_race_control",
        OWRRaceControlState::new,
        CODEC,
        null
    );

    private boolean checkpointCheckEnabled;
    private boolean offTrackCheckEnabled;
    private int minimumValidLapTicks;
    private boolean wheelInputAllowed;
    private boolean autoShiftingAllowed;
    private int maxErsCapacityMj;
    private int maxBalancedDeployKw;
    private int maxAttackDeployKw;
    private int maxHarvestNegativeKw;
    private RaceFlagMode globalFlag;
    private double carDamageModifier;
    private double tyreWearModifier;
    private int raceLapLimit;
    private int revision;
    private final java.util.Map<String, Integer> sectorFlags;
    private final java.util.Map<String, Integer> driverFlags;
    private boolean autoFlagging;
    private int startPhase;
    private RaceSignal pitEntrySignal;
    private RaceSignal pitExitSignal;
    private RaceSignal pitWeatherSignal;

    public OWRRaceControlState() {
        this(false, true, OWRLapRecords.DEFAULT_MIN_VALID_LAP_TICKS, true, true, 4, 200, 350, 350,
            RaceFlagMode.DEFAULT.ordinal(), 1.0, 1.0, DEFAULT_RACE_LAP_LIMIT, SignalSettings.DEFAULT);
    }

    private OWRRaceControlState(boolean checkpointCheckEnabled, boolean offTrackCheckEnabled, int minimumValidLapTicks, boolean wheelInputAllowed, boolean autoShiftingAllowed,
            int maxErsCapacityMj, int maxBalancedDeployKw, int maxAttackDeployKw, int maxHarvestNegativeKw, int globalFlag,
            double carDamageModifier, double tyreWearModifier, int raceLapLimit, SignalSettings signals) {
        this.checkpointCheckEnabled = checkpointCheckEnabled;
        this.offTrackCheckEnabled = offTrackCheckEnabled;
        this.minimumValidLapTicks = Math.max(1, minimumValidLapTicks);
        this.wheelInputAllowed = wheelInputAllowed;
        this.autoShiftingAllowed = autoShiftingAllowed;
        this.maxErsCapacityMj = clamp(maxErsCapacityMj, 2, 12);
        this.maxBalancedDeployKw = clamp(maxBalancedDeployKw, 0, 350);
        this.maxAttackDeployKw = clamp(maxAttackDeployKw, 0, 350);
        this.maxHarvestNegativeKw = clamp(maxHarvestNegativeKw, 0, 350);
        this.globalFlag = RaceFlagMode.fromOrdinal(globalFlag);
        this.carDamageModifier = snapConditionModifier(carDamageModifier);
        this.tyreWearModifier = snapConditionModifier(tyreWearModifier);
        this.raceLapLimit = clamp(raceLapLimit, 0, MAX_RACE_LAP_LIMIT);
        this.sectorFlags = new java.util.HashMap<>(signals.sectorFlags());
        this.driverFlags = new java.util.HashMap<>(signals.driverFlags());
        this.autoFlagging = signals.autoFlagging();
        this.startPhase = clamp(signals.startPhase(), 0, 6);
        this.pitEntrySignal = RaceSignal.fromOrdinal(signals.pitEntrySignal());
        this.pitExitSignal = RaceSignal.fromOrdinal(signals.pitExitSignal());
        this.pitWeatherSignal = RaceSignal.fromOrdinal(signals.pitWeatherSignal());
    }

    public static OWRRaceControlState get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(TYPE);
    }

    public static OWRRaceControlState getIfPresent(ServerLevel level) {
        return level.getDataStorage().get(TYPE);
    }

    public static void importLegacy(ServerLevel level, OWRRaceControlState legacy) {
        if (legacy == null || getIfPresent(level) != null) {
            return;
        }
        OWRRaceControlState copy = new OWRRaceControlState(
            legacy.checkpointCheckEnabled,
            legacy.offTrackCheckEnabled,
            legacy.minimumValidLapTicks,
            legacy.wheelInputAllowed,
            legacy.autoShiftingAllowed,
            legacy.maxErsCapacityMj,
            legacy.maxBalancedDeployKw,
            legacy.maxAttackDeployKw,
            legacy.maxHarvestNegativeKw,
            legacy.globalFlag.ordinal(),
            legacy.carDamageModifier,
            legacy.tyreWearModifier,
            legacy.raceLapLimit, legacy.signalSettings()
        );
        copy.markChanged();
        level.getDataStorage().set(TYPE, copy);
    }

    public int getRevision() {
        return revision;
    }

    public boolean isCheckpointCheckEnabled() {
        return checkpointCheckEnabled;
    }

    public boolean isOffTrackCheckEnabled() {
        return offTrackCheckEnabled;
    }

    public int getMinimumValidLapTicks() {
        return minimumValidLapTicks;
    }

    public boolean isWheelInputAllowed() {
        return wheelInputAllowed;
    }

    public boolean isAutoShiftingAllowed() {
        return autoShiftingAllowed;
    }

    public int getMaxErsCapacityMj() {
        return maxErsCapacityMj;
    }

    public int getMaxBalancedDeployKw() {
        return maxBalancedDeployKw;
    }

    public int getMaxAttackDeployKw() {
        return maxAttackDeployKw;
    }

    public int getMaxHarvestNegativeKw() {
        return maxHarvestNegativeKw;
    }

    public RaceFlagMode getGlobalFlag() {
        return globalFlag;
    }

    public double getCarDamageModifier() {
        return carDamageModifier;
    }

    public double getTyreWearModifier() {
        return tyreWearModifier;
    }

    public int getRaceLapLimit() {
        return raceLapLimit;
    }

    public boolean isAutoFlagging() { return autoFlagging; }
    public int getStartPhase() { return startPhase; }
    public RaceSignal getSectorSignal(int sector, int minisector) {
        Integer exact = sectorFlags.get(sector + ":" + minisector);
        if (exact == null) exact = sectorFlags.get(sector + ":-1");
        return exact == null ? signalForGlobalFlag() : RaceSignal.fromOrdinal(exact);
    }
    public RaceSignal getDriverSignal(java.util.UUID driverId) {
        Integer signal = driverId == null ? null : driverFlags.get(driverId.toString());
        return signal == null ? RaceSignal.OFF : RaceSignal.fromOrdinal(signal);
    }
    public RaceSignal getPitSignal(PitLightMode mode) {
        return switch (mode) { case ENTRY -> pitEntrySignal; case EXIT -> pitExitSignal; case WEATHER -> pitWeatherSignal; };
    }
    public RaceSignal signalForGlobalFlag() {
        return switch (globalFlag) { case GREEN -> RaceSignal.GREEN; case YELLOW, SAFETY_CAR, VIRTUAL_SAFETY_CAR -> RaceSignal.YELLOW; case RED -> RaceSignal.RED; };
    }
    private SignalSettings signalSettings() {
        return new SignalSettings(sectorFlags, driverFlags, autoFlagging, startPhase, pitEntrySignal.ordinal(), pitExitSignal.ordinal(), pitWeatherSignal.ordinal());
    }

    private record SignalSettings(java.util.Map<String, Integer> sectorFlags, java.util.Map<String, Integer> driverFlags,
            boolean autoFlagging, int startPhase, int pitEntrySignal, int pitExitSignal, int pitWeatherSignal) {
        private static final SignalSettings DEFAULT = new SignalSettings(java.util.Map.of(), java.util.Map.of(), false, 0,
            RaceSignal.GREEN.ordinal(), RaceSignal.GREEN.ordinal(), RaceSignal.OFF.ordinal());
        private static final Codec<SignalSettings> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.unboundedMap(Codec.STRING, Codec.INT).optionalFieldOf("sector_flags", java.util.Map.of()).forGetter(SignalSettings::sectorFlags),
            Codec.unboundedMap(Codec.STRING, Codec.INT).optionalFieldOf("driver_flags", java.util.Map.of()).forGetter(SignalSettings::driverFlags),
            Codec.BOOL.optionalFieldOf("auto_flagging", false).forGetter(SignalSettings::autoFlagging),
            Codec.INT.optionalFieldOf("start_phase", 0).forGetter(SignalSettings::startPhase),
            Codec.INT.optionalFieldOf("pit_entry", RaceSignal.GREEN.ordinal()).forGetter(SignalSettings::pitEntrySignal),
            Codec.INT.optionalFieldOf("pit_exit", RaceSignal.GREEN.ordinal()).forGetter(SignalSettings::pitExitSignal),
            Codec.INT.optionalFieldOf("pit_weather", RaceSignal.OFF.ordinal()).forGetter(SignalSettings::pitWeatherSignal)
        ).apply(instance, SignalSettings::new));
    }
    public void setSectorSignal(int sector, int minisector, RaceSignal signal) {
        String key = Math.max(0, sector) + ":" + Math.max(-1, minisector);
        if (signal == null || signal == RaceSignal.OFF) sectorFlags.remove(key); else sectorFlags.put(key, signal.ordinal());
        markChanged();
    }
    public void clearSectorSignals() {
        if (sectorFlags.isEmpty()) return;
        sectorFlags.clear();
        markChanged();
    }
    public void setDriverSignal(java.util.UUID driverId, RaceSignal signal) {
        if (driverId == null) return;
        if (signal == null || signal == RaceSignal.OFF) driverFlags.remove(driverId.toString()); else driverFlags.put(driverId.toString(), signal.ordinal());
        markChanged();
    }
    public void setAutoFlagging(boolean enabled) { if (autoFlagging != enabled) { autoFlagging = enabled; markChanged(); } }
    public void setStartPhase(int phase) { int next = clamp(phase, 0, 6); if (startPhase != next) { startPhase = next; markChanged(); } }
    public void setPitSignal(PitLightMode mode, RaceSignal signal) {
        RaceSignal next = signal == null ? RaceSignal.OFF : signal;
        switch (mode) { case ENTRY -> pitEntrySignal = next; case EXIT -> pitExitSignal = next; case WEATHER -> pitWeatherSignal = next; }
        markChanged();
    }

    public boolean toggleCheckpointCheck() {
        checkpointCheckEnabled = !checkpointCheckEnabled;
        markChanged();
        return checkpointCheckEnabled;
    }

    public boolean toggleOffTrackCheck() {
        offTrackCheckEnabled = !offTrackCheckEnabled;
        markChanged();
        return offTrackCheckEnabled;
    }

    public void setMinimumValidLapTicks(int ticks) {
        int clamped = Math.max(1, ticks);
        if (minimumValidLapTicks == clamped) {
            return;
        }
        minimumValidLapTicks = clamped;
        markChanged();
    }

    public void setRaceLapLimit(int laps) {
        int clamped = clamp(laps, 0, MAX_RACE_LAP_LIMIT);
        if (raceLapLimit == clamped) {
            return;
        }
        raceLapLimit = clamped;
        markChanged();
    }

    public void setWheelInputAllowed(boolean allowed) {
        if (wheelInputAllowed == allowed) {
            return;
        }
        wheelInputAllowed = allowed;
        markChanged();
    }

    public boolean toggleAutoShiftingAllowed() {
        autoShiftingAllowed = !autoShiftingAllowed;
        markChanged();
        return autoShiftingAllowed;
    }

    public void setMaxErsCapacityMj(int value) {
        int clamped = clamp(value, 2, 12);
        if (maxErsCapacityMj == clamped) {
            return;
        }
        maxErsCapacityMj = clamped;
        markChanged();
    }

    public void setMaxBalancedDeployKw(int value) {
        int clamped = clamp(value, 0, 350);
        if (maxBalancedDeployKw == clamped) {
            return;
        }
        maxBalancedDeployKw = clamped;
        markChanged();
    }

    public void setMaxAttackDeployKw(int value) {
        int clamped = clamp(value, 0, 350);
        if (maxAttackDeployKw == clamped) {
            return;
        }
        maxAttackDeployKw = clamped;
        markChanged();
    }

    public void setMaxHarvestNegativeKw(int value) {
        int clamped = clamp(value, 0, 350);
        if (maxHarvestNegativeKw == clamped) {
            return;
        }
        maxHarvestNegativeKw = clamped;
        markChanged();
    }

    public void setGlobalFlag(RaceFlagMode flag) {
        RaceFlagMode next = flag == null ? RaceFlagMode.DEFAULT : flag;
        if (globalFlag == next) {
            return;
        }
        globalFlag = next;
        markChanged();
    }

    public void cycleCarDamageModifier(int delta) {
        double next = cycleConditionModifier(carDamageModifier, delta);
        if (Double.compare(carDamageModifier, next) == 0) {
            return;
        }
        carDamageModifier = next;
        markChanged();
    }

    public void cycleTyreWearModifier(int delta) {
        double next = cycleConditionModifier(tyreWearModifier, delta);
        if (Double.compare(tyreWearModifier, next) == 0) {
            return;
        }
        tyreWearModifier = next;
        markChanged();
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static double cycleConditionModifier(double current, int delta) {
        int index = conditionModifierIndex(current);
        int next = Math.max(0, Math.min(CONDITION_MODIFIERS.length - 1, index + Integer.signum(delta)));
        return CONDITION_MODIFIERS[next];
    }

    private static double snapConditionModifier(double value) {
        return CONDITION_MODIFIERS[conditionModifierIndex(value)];
    }

    private static int conditionModifierIndex(double value) {
        if (!Double.isFinite(value)) {
            return 2;
        }
        int bestIndex = 0;
        double bestDistance = Double.MAX_VALUE;
        for (int index = 0; index < CONDITION_MODIFIERS.length; index++) {
            double distance = Math.abs(CONDITION_MODIFIERS[index] - value);
            if (distance < bestDistance) {
                bestIndex = index;
                bestDistance = distance;
            }
        }
        return bestIndex;
    }

    private void markChanged() {
        revision++;
        setDirty();
    }
}
