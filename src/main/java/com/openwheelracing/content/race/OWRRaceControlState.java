package com.openwheelracing.content.race;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.openwheelracing.OpenwheelRacing;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

public class OWRRaceControlState extends SavedData {
    private static final double[] CONDITION_MODIFIERS = {0.0, 0.5, 1.0, 2.0, 5.0, 10.0};

    private static final Codec<OWRRaceControlState> CODEC = RecordCodecBuilder.create(instance -> instance.group(
        Codec.BOOL.optionalFieldOf("checkpoint_check_enabled", false).forGetter(OWRRaceControlState::isCheckpointCheckEnabled),
        Codec.BOOL.optionalFieldOf("off_track_check_enabled", true).forGetter(OWRRaceControlState::isOffTrackCheckEnabled),
        Codec.INT.optionalFieldOf("minimum_valid_lap_ticks", OWRLapRecords.DEFAULT_MIN_VALID_LAP_TICKS).forGetter(OWRRaceControlState::getMinimumValidLapTicks),
        Codec.BOOL.optionalFieldOf("wheel_input_allowed", true).forGetter(OWRRaceControlState::isWheelInputAllowed),
        Codec.INT.optionalFieldOf("max_ers_capacity_mj", 4).forGetter(OWRRaceControlState::getMaxErsCapacityMj),
        Codec.INT.optionalFieldOf("max_balanced_deploy_kw", 200).forGetter(OWRRaceControlState::getMaxBalancedDeployKw),
        Codec.INT.optionalFieldOf("max_attack_deploy_kw", 350).forGetter(OWRRaceControlState::getMaxAttackDeployKw),
        Codec.INT.optionalFieldOf("max_harvest_negative_kw", 350).forGetter(OWRRaceControlState::getMaxHarvestNegativeKw),
        Codec.INT.optionalFieldOf("global_flag", RaceFlagMode.DEFAULT.ordinal()).forGetter(state -> state.getGlobalFlag().ordinal()),
        Codec.DOUBLE.optionalFieldOf("car_damage_modifier", 1.0).forGetter(OWRRaceControlState::getCarDamageModifier),
        Codec.DOUBLE.optionalFieldOf("tyre_wear_modifier", 1.0).forGetter(OWRRaceControlState::getTyreWearModifier)
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
    private int maxErsCapacityMj;
    private int maxBalancedDeployKw;
    private int maxAttackDeployKw;
    private int maxHarvestNegativeKw;
    private RaceFlagMode globalFlag;
    private double carDamageModifier;
    private double tyreWearModifier;
    private int revision;

    public OWRRaceControlState() {
        this(false, true, OWRLapRecords.DEFAULT_MIN_VALID_LAP_TICKS, true, 4, 200, 350, 350, RaceFlagMode.DEFAULT.ordinal(), 1.0, 1.0);
    }

    private OWRRaceControlState(boolean checkpointCheckEnabled, boolean offTrackCheckEnabled, int minimumValidLapTicks, boolean wheelInputAllowed,
            int maxErsCapacityMj, int maxBalancedDeployKw, int maxAttackDeployKw, int maxHarvestNegativeKw, int globalFlag, double carDamageModifier, double tyreWearModifier) {
        this.checkpointCheckEnabled = checkpointCheckEnabled;
        this.offTrackCheckEnabled = offTrackCheckEnabled;
        this.minimumValidLapTicks = Math.max(1, minimumValidLapTicks);
        this.wheelInputAllowed = wheelInputAllowed;
        this.maxErsCapacityMj = clamp(maxErsCapacityMj, 2, 12);
        this.maxBalancedDeployKw = clamp(maxBalancedDeployKw, 0, 350);
        this.maxAttackDeployKw = clamp(maxAttackDeployKw, 0, 350);
        this.maxHarvestNegativeKw = clamp(maxHarvestNegativeKw, 0, 350);
        this.globalFlag = RaceFlagMode.fromOrdinal(globalFlag);
        this.carDamageModifier = snapConditionModifier(carDamageModifier);
        this.tyreWearModifier = snapConditionModifier(tyreWearModifier);
    }

    public static OWRRaceControlState get(ServerLevel level) {
        return level.getServer().getLevel(Level.OVERWORLD).getDataStorage().computeIfAbsent(TYPE);
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

    public void setWheelInputAllowed(boolean allowed) {
        if (wheelInputAllowed == allowed) {
            return;
        }
        wheelInputAllowed = allowed;
        markChanged();
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
