package com.openwheelracing.client.sound;

import com.openwheelracing.content.entity.OpenwheelCarEntity;
import com.openwheelracing.content.entity.VehiclePhysics;
import com.openwheelracing.content.entity.VehiclePhysicsPreset;
import com.openwheelracing.content.entity.VehiclePhysicsPresetState;
import com.openwheelracing.registry.OWRSoundEvents;

import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.registries.DeferredHolder;

final class CarEngineSoundInstance extends AbstractTickableSoundInstance {
    private static final float IDLE_RPM = 900.0f;
    private static final float REDLINE_RPM = 13_000.0f;
    private static final float RPM_SAMPLE_SECONDS = 1.0f / 20.0f;
    private static final float RPM_VELOCITY_BLEND = 0.45f;
    private static final float RPM_VELOCITY_DECAY = 0.82f;
    private static final float RPM_PREDICTION_SECONDS = 0.035f;
    private static final float RPM_TRACKING_BLEND = 0.65f;
    private static final float CLASSIC_NEUTRAL_RISE = 18_000.0f;
    private static final float CLASSIC_NEUTRAL_DECAY = 3_800.0f;
    private static final float CLASSIC_ENGINE_BRAKE_DECAY = 7_000.0f;
    private static final float CLASSIC_CLUTCH_RPM_DROP = 12_000.0f;
    private static final float CLASSIC_LAUNCH_RPM = 4_000.0f;
    private static final int CLASSIC_CLUTCH_RELEASE_TICKS = 12;
    private static final Sample[] SAMPLES = {
        new Sample(5_250f, OWRSoundEvents.CAR_ENGINE_RPM_5250),
        new Sample(7_425f, OWRSoundEvents.CAR_ENGINE_RPM_7425),
        new Sample(9_970f, OWRSoundEvents.CAR_ENGINE_RPM_9970),
        new Sample(11_360f, OWRSoundEvents.CAR_ENGINE_RPM_11360)
    };

    private static final float SAMPLE_KEEPALIVE_GAIN = 0.015f;

    private final int sampleIndex;
    private final float sampleRpm;
    private OpenwheelCarEntity car;
    private Vec3 listenerPosition;
    private Vec3 previousSourcePosition;
    private float engineRpm;
    private int lastSynchronizedRpm;
    private float rpmVelocity;
    private int ticksSinceRpmSample = 1;
    private int lastGear = Integer.MIN_VALUE;
    private int shiftCutTicks;
    private int classicClutchReleaseTicks;
    private VehiclePhysicsPreset lastPreset;

    private CarEngineSoundInstance(OpenwheelCarEntity car, Vec3 listenerPosition, int sampleIndex) {
        super(SAMPLES[sampleIndex].sound.get(), SoundSource.PLAYERS, RandomSource.create());
        this.car = car;
        this.listenerPosition = listenerPosition;
        this.sampleIndex = sampleIndex;
        this.sampleRpm = SAMPLES[sampleIndex].rpm;
        looping = true;
        delay = 0;
        attenuation = Attenuation.LINEAR;
        volume = 0.0f;
        pitch = 1.0f;
        x = car.getX();
        y = car.getY() + 0.35;
        z = car.getZ();
        previousSourcePosition = new Vec3(x, y, z);
        engineRpm = car.getRpm();
        lastSynchronizedRpm = car.getRpm();
        lastGear = car.getGear();
        lastPreset = VehiclePhysicsPresetState.clientPreset();
    }

    static CarEngineSoundInstance rpmSample(OpenwheelCarEntity car, Vec3 listenerPosition, int sampleIndex) {
        return new CarEngineSoundInstance(car, listenerPosition, sampleIndex);
    }

    static int sampleCount() {
        return SAMPLES.length;
    }

    void replaceCar(OpenwheelCarEntity car) {
        if (this.car != null && this.car.getId() == car.getId()) {
            this.car = car;
            return;
        }

        this.car = car;
        x = car.getX();
        y = car.getY() + 0.35;
        z = car.getZ();
        previousSourcePosition = new Vec3(x, y, z);
        engineRpm = car.getRpm();
        lastSynchronizedRpm = car.getRpm();
        rpmVelocity = 0.0f;
        ticksSinceRpmSample = 1;
        lastGear = car.getGear();
        shiftCutTicks = 0;
        classicClutchReleaseTicks = 0;
        lastPreset = VehiclePhysicsPresetState.clientPreset();
    }

    void updateListener(Vec3 listenerPosition) {
        this.listenerPosition = listenerPosition;
    }

    @Override
    public boolean canStartSilent() {
        return true;
    }

    @Override
    public boolean canPlaySound() {
        return car != null && car.isAlive() && !car.isRemoved();
    }

    @Override
    public void tick() {
        if (!canPlaySound()) {
            stop();
            return;
        }

        previousSourcePosition = new Vec3(x, y, z);
        x = car.getX();
        y = car.getY() + 0.35;
        z = car.getZ();

        int gear = car.getGear();
        float speedKmh = car.getSpeedKmh();
        int synchronizedRpm = car.getRpm();
        VehiclePhysicsPreset preset = VehiclePhysicsPresetState.clientPreset();
        if (preset != lastPreset) {
            resetPrediction(gear, synchronizedRpm, preset);
        }
        if (preset.isDynamic()) {
            updateDynamicRpm(gear, synchronizedRpm);
        } else {
            updateClassicRpm(gear, speedKmh);
        }

        float distance = (float) listenerPosition.distanceTo(new Vec3(x, y, z));
        float shiftGain = shiftCutTicks > 0 ? 0.28f : 1.0f;
        float baseVolume = baseVolume(speedKmh, engineRpm) * CarSoundPhysics.attenuation(distance) * shiftGain;
        volume = baseVolume * crossfadeGain(engineRpm, sampleIndex);
        pitch = CarSoundPhysics.doppler(samplePitch(engineRpm, sampleRpm), new Vec3(x, y, z), previousSourcePosition, listenerPosition);
        if (shiftCutTicks > 0) {
            shiftCutTicks--;
        }
    }

    private void resetPrediction(int gear, int synchronizedRpm, VehiclePhysicsPreset preset) {
        engineRpm = Mth.clamp(synchronizedRpm, IDLE_RPM, REDLINE_RPM);
        lastSynchronizedRpm = synchronizedRpm;
        rpmVelocity = 0.0f;
        ticksSinceRpmSample = 1;
        lastGear = gear;
        shiftCutTicks = 0;
        classicClutchReleaseTicks = 0;
        lastPreset = preset;
    }

    private void updateDynamicRpm(int gear, int synchronizedRpm) {
        if (gear != lastGear) {
            int previousGear = lastGear;
            shiftCutTicks = previousGear > 0 && gear > previousGear ? 1 : 0;
            lastGear = gear;
            engineRpm = Mth.clamp(synchronizedRpm, IDLE_RPM, REDLINE_RPM);
            lastSynchronizedRpm = synchronizedRpm;
            rpmVelocity = 0.0f;
            ticksSinceRpmSample = 1;
        } else {
            engineRpm = predictEngineRpm(synchronizedRpm);
        }
    }

    private void updateClassicRpm(int gear, float speedKmh) {
        if (gear != lastGear) {
            int previousGear = lastGear;
            if (previousGear == 0 && gear != 0 && engineRpm > CLASSIC_LAUNCH_RPM) {
                classicClutchReleaseTicks = CLASSIC_CLUTCH_RELEASE_TICKS;
                engineRpm = applyClassicClutchRelease(gear, speedKmh);
            } else {
                classicClutchReleaseTicks = 0;
                engineRpm = classicRpmFromSpeed(gear, speedKmh);
            }
            shiftCutTicks = previousGear > 0 && gear > previousGear ? 1 : 0;
            lastGear = gear;
        } else {
            engineRpm = predictClassicRpm(gear, speedKmh);
        }
    }

    private float predictClassicRpm(int gear, float speedKmh) {
        if (gear == 0) {
            int serverRpm = car.getRpm();
            engineRpm += serverRpm > engineRpm
                ? CLASSIC_NEUTRAL_RISE * RPM_SAMPLE_SECONDS
                : -CLASSIC_NEUTRAL_DECAY * RPM_SAMPLE_SECONDS;
            return Mth.clamp(engineRpm, IDLE_RPM, REDLINE_RPM);
        }
        if (classicClutchReleaseTicks > 0) {
            return applyClassicClutchRelease(gear, speedKmh);
        }
        float floorRpm = classicRpmFromSpeed(gear, speedKmh);
        engineRpm = engineRpm > floorRpm
            ? Math.max(floorRpm, engineRpm - CLASSIC_ENGINE_BRAKE_DECAY * RPM_SAMPLE_SECONDS)
            : floorRpm;
        return Mth.clamp(engineRpm, IDLE_RPM, REDLINE_RPM);
    }

    private float applyClassicClutchRelease(int gear, float speedKmh) {
        float wheelRpm = classicRpmFromSpeed(gear, speedKmh);
        engineRpm = Math.max(wheelRpm,
            engineRpm - CLASSIC_CLUTCH_RPM_DROP * RPM_SAMPLE_SECONDS);
        classicClutchReleaseTicks--;
        return Mth.clamp(engineRpm, IDLE_RPM, REDLINE_RPM);
    }

    private float classicRpmFromSpeed(int gear, float speedKmh) {
        if (gear == 0) return IDLE_RPM;
        float topKmh = (float) VehiclePhysics.gearTopSpeedKmh(gear, car.getSetup());
        return Mth.clamp(Math.max(IDLE_RPM, speedKmh / topKmh * REDLINE_RPM), IDLE_RPM, REDLINE_RPM);
    }

    private float predictEngineRpm(int synchronizedRpm) {
        int rpmDelta = synchronizedRpm - lastSynchronizedRpm;
        if (rpmDelta != 0) {
            float elapsedSeconds = ticksSinceRpmSample * RPM_SAMPLE_SECONDS;
            float measuredVelocity = rpmDelta / elapsedSeconds;
            rpmVelocity += (measuredVelocity - rpmVelocity) * RPM_VELOCITY_BLEND;
            lastSynchronizedRpm = synchronizedRpm;
            ticksSinceRpmSample = 1;
        } else {
            rpmVelocity *= RPM_VELOCITY_DECAY;
            ticksSinceRpmSample = Math.min(20, ticksSinceRpmSample + 1);
        }

        float predictedTarget = Mth.clamp(
            synchronizedRpm + rpmVelocity * RPM_PREDICTION_SECONDS, IDLE_RPM, REDLINE_RPM);
        engineRpm += (predictedTarget - engineRpm) * RPM_TRACKING_BLEND;
        return Mth.clamp(engineRpm, IDLE_RPM, REDLINE_RPM);
    }

    private static float baseVolume(float speedKmh, float rpm) {
        float rpmNorm = Mth.clamp((rpm - IDLE_RPM) / (REDLINE_RPM - IDLE_RPM), 0.0f, 1.0f);
        float speedBoost = Mth.clamp(speedKmh / 100.0f, 0.0f, 1.0f) * 0.02f;
        float volumeMultiplier = 1.0f;
        return (1.25f + rpmNorm * 0.04f + speedBoost) * volumeMultiplier;
    }

    private static float crossfadeGain(float rpm, int index) {
        float gain = SAMPLE_KEEPALIVE_GAIN;
        if (index == 0 && rpm <= SAMPLES[0].rpm) {
            gain = 1.0f;
        } else if (index == SAMPLES.length - 1 && rpm >= SAMPLES[SAMPLES.length - 1].rpm) {
            gain = 1.0f;
        } else if (index > 0 && rpm >= SAMPLES[index - 1].rpm && rpm <= SAMPLES[index].rpm) {
            float t = smoothstep(Mth.clamp((rpm - SAMPLES[index - 1].rpm) / (SAMPLES[index].rpm - SAMPLES[index - 1].rpm), 0.0f, 1.0f));
            gain = t;
        } else if (index < SAMPLES.length - 1 && rpm >= SAMPLES[index].rpm && rpm <= SAMPLES[index + 1].rpm) {
            float t = smoothstep(Mth.clamp((rpm - SAMPLES[index].rpm) / (SAMPLES[index + 1].rpm - SAMPLES[index].rpm), 0.0f, 1.0f));
            gain = 1.0f - t;
        }
        return Math.max(SAMPLE_KEEPALIVE_GAIN, gain);
    }

    private static float samplePitch(float rpm, float sampleRpm) {
        return Mth.clamp(rpm / sampleRpm, 0.72f, 1.28f);
    }

    private static float smoothstep(float t) {
        return t * t * (3.0f - 2.0f * t);
    }

    private record Sample(float rpm, DeferredHolder<SoundEvent, SoundEvent> sound) {
    }
}
