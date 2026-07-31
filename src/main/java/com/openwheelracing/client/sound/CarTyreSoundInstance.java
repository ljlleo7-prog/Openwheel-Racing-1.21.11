package com.openwheelracing.client.sound;

import com.openwheelracing.content.entity.OpenwheelCarEntity;
import com.openwheelracing.registry.OWRSoundEvents;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

final class CarTyreSoundInstance extends AbstractTickableSoundInstance {
    private final Wheel wheel;
    private OpenwheelCarEntity car;
    private Vec3 listenerPosition;
    private Vec3 previousSourcePosition;

    private CarTyreSoundInstance(OpenwheelCarEntity car, Vec3 listenerPosition, Wheel wheel) {
        super(OWRSoundEvents.CAR_TYRE_SQUEAL.get(), SoundSource.PLAYERS, RandomSource.create());
        this.car = car;
        this.listenerPosition = listenerPosition;
        this.wheel = wheel;
        looping = true;
        delay = 0;
        attenuation = Attenuation.LINEAR;
        volume = 0.0f;
        pitch = 1.4f;
        Vec3 position = car.getWheelSoundPosition(wheel.sideOffset, wheel.lengthOffset);
        x = position.x;
        y = position.y;
        z = position.z;
        previousSourcePosition = new Vec3(x, y, z);
    }

    static CarTyreSoundInstance frontLeft(OpenwheelCarEntity car, Vec3 listenerPosition) {
        return new CarTyreSoundInstance(car, listenerPosition, Wheel.FRONT_LEFT);
    }

    static CarTyreSoundInstance frontRight(OpenwheelCarEntity car, Vec3 listenerPosition) {
        return new CarTyreSoundInstance(car, listenerPosition, Wheel.FRONT_RIGHT);
    }

    static CarTyreSoundInstance rearLeft(OpenwheelCarEntity car, Vec3 listenerPosition) {
        return new CarTyreSoundInstance(car, listenerPosition, Wheel.REAR_LEFT);
    }

    static CarTyreSoundInstance rearRight(OpenwheelCarEntity car, Vec3 listenerPosition) {
        return new CarTyreSoundInstance(car, listenerPosition, Wheel.REAR_RIGHT);
    }

    void replaceCar(OpenwheelCarEntity car) {
        if (this.car != null && this.car.getId() == car.getId()) {
            this.car = car;
            return;
        }

        this.car = car;
        Vec3 position = car.getWheelSoundPosition(wheel.sideOffset, wheel.lengthOffset);
        x = position.x;
        y = position.y;
        z = position.z;
        previousSourcePosition = position;
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
        Vec3 position = car.getWheelSoundPosition(wheel.sideOffset, wheel.lengthOffset);
        x = position.x;
        y = position.y;
        z = position.z;

        float slip = car.getTyreSlipIntensity();
        float speed = Mth.clamp(car.getSpeedKmh() / 45.0f, 0.0f, 1.0f);
        float distance = (float) listenerPosition.distanceTo(position);
        volume = slip <= 0.03f ? 0.0f : wheel.volumeMultiplier * Mth.clamp((slip - 0.03f) / 0.97f, 0.0f, 1.0f) * (0.25f + speed * 0.75f) * CarSoundPhysics.attenuation(distance);
        pitch = CarSoundPhysics.doppler(Mth.clamp(1.55f + slip * 0.65f + wheel.pitchOffset, 1.3f, 2.0f), position, previousSourcePosition, listenerPosition);
    }

    private enum Wheel {
        FRONT_LEFT(-0.85, 1.05, 1.0f, 0.04f),
        FRONT_RIGHT(0.85, 1.05, 1.0f, 0.02f),
        REAR_LEFT(-0.85, -1.05, 0.75f, -0.02f),
        REAR_RIGHT(0.85, -1.05, 0.75f, -0.04f);

        private final double sideOffset;
        private final double lengthOffset;
        private final float volumeMultiplier;
        private final float pitchOffset;

        Wheel(double sideOffset, double lengthOffset, float volumeMultiplier, float pitchOffset) {
            this.sideOffset = sideOffset;
            this.lengthOffset = lengthOffset;
            this.volumeMultiplier = volumeMultiplier;
            this.pitchOffset = pitchOffset;
        }
    }
}
