package com.openwheelracing.client.sound;

import com.openwheelracing.content.entity.OpenwheelCarEntity;
import com.openwheelracing.content.entity.VehiclePhysics;
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
    private static final float NEUTRAL_RISE = 18_000f;
    private static final float NEUTRAL_DECAY = 3_800f;
    private static final float ENGINE_BRAKE_DECAY = 7_000f;
    private static final float CLUTCH_RPM_DROP = 12_000f;
    private static final float LAUNCH_RPM = 4_000f;
    private static final int CLUTCH_RELEASE_TICKS = 12;
    private static final float DT = 1.0f / 20.0f;
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
    private float predictedRpm;
    private int lastGear = Integer.MIN_VALUE;
    private int shiftCutTicks;
    private int clutchReleaseTicks;

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
        predictedRpm = car.getRpm();
        lastGear = car.getGear();
    }

    static CarEngineSoundInstance rpmSample(OpenwheelCarEntity car, Vec3 listenerPosition, int sampleIndex) {
        return new CarEngineSoundInstance(car, listenerPosition, sampleIndex);
    }

    static int sampleCount() {
        return SAMPLES.length;
    }

    void replaceCar(OpenwheelCarEntity car) {
        this.car = car;
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

        if (gear != lastGear) {
            int previousGear = lastGear;
            if (previousGear == 0 && gear != 0 && predictedRpm > LAUNCH_RPM) {
                clutchReleaseTicks = CLUTCH_RELEASE_TICKS;
                predictedRpm = applyClutchRelease(gear, speedKmh);
            } else {
                clutchReleaseTicks = 0;
                predictedRpm = rpmFromSpeed(gear, speedKmh);
            }
            shiftCutTicks = previousGear > 0 && gear > previousGear ? 1 : 0;
            lastGear = gear;
        } else {
            predictedRpm = predictRpm(gear, speedKmh);
        }

        float distance = (float) listenerPosition.distanceTo(new Vec3(x, y, z));
        float shiftGain = shiftCutTicks > 0 ? 0.28f : 1.0f;
        float baseVolume = baseVolume(speedKmh, predictedRpm) * CarSoundPhysics.attenuation(distance) * shiftGain;
        volume = baseVolume * crossfadeGain(predictedRpm, sampleIndex);
        pitch = CarSoundPhysics.doppler(samplePitch(predictedRpm, sampleRpm), new Vec3(x, y, z), previousSourcePosition, listenerPosition);
        if (shiftCutTicks > 0) {
            shiftCutTicks--;
        }
    }

    private float predictRpm(int gear, float speedKmh) {
        if (gear == 0) {
            int serverRpm = car.getRpm();
            if (serverRpm > predictedRpm) {
                predictedRpm += NEUTRAL_RISE * DT;
            } else {
                predictedRpm -= NEUTRAL_DECAY * DT;
            }
            return Mth.clamp(predictedRpm, IDLE_RPM, REDLINE_RPM);
        }

        if (clutchReleaseTicks > 0) {
            return applyClutchRelease(gear, speedKmh);
        }

        float floorRpm = rpmFromSpeed(gear, speedKmh);
        if (predictedRpm > floorRpm) {
            predictedRpm = Math.max(floorRpm, predictedRpm - ENGINE_BRAKE_DECAY * DT);
        } else {
            predictedRpm = floorRpm;
        }
        return Mth.clamp(predictedRpm, IDLE_RPM, REDLINE_RPM);
    }

    private float applyClutchRelease(int gear, float speedKmh) {
        float wheelRpm = rpmFromSpeed(gear, speedKmh);
        predictedRpm = Math.max(wheelRpm, predictedRpm - CLUTCH_RPM_DROP * DT);
        clutchReleaseTicks--;
        return Mth.clamp(predictedRpm, IDLE_RPM, REDLINE_RPM);
    }

    private float rpmFromSpeed(int gear, float speedKmh) {
        if (gear == 0) {
            return IDLE_RPM;
        }
        float topKmh = (float) VehiclePhysics.gearTopSpeedKmh(gear, car.getSetup());
        return Mth.clamp(Math.max(IDLE_RPM, speedKmh / topKmh * REDLINE_RPM), IDLE_RPM, REDLINE_RPM);
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
