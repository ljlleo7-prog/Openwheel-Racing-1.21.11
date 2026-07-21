package com.openwheelracing.client.sound;

import com.openwheelracing.content.entity.OpenwheelCarEntity;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.world.phys.Vec3;

final class CarSoundSet {
    private final CarEngineSoundCluster engine;
    private final CarTyreSoundInstance frontLeft;
    private final CarTyreSoundInstance frontRight;
    private final CarTyreSoundInstance rearLeft;
    private final CarTyreSoundInstance rearRight;

    private OpenwheelCarEntity car;

    private CarSoundSet(SoundManager soundManager, OpenwheelCarEntity car, Vec3 listenerPosition) {
        this.car = car;
        engine = CarEngineSoundCluster.start(soundManager, car, listenerPosition);
        frontLeft = CarTyreSoundInstance.frontLeft(car, listenerPosition);
        frontRight = CarTyreSoundInstance.frontRight(car, listenerPosition);
        rearLeft = CarTyreSoundInstance.rearLeft(car, listenerPosition);
        rearRight = CarTyreSoundInstance.rearRight(car, listenerPosition);
    }

    static CarSoundSet start(SoundManager soundManager, OpenwheelCarEntity car, Vec3 listenerPosition) {
        CarSoundSet soundSet = new CarSoundSet(soundManager, car, listenerPosition);
        soundManager.play(soundSet.frontLeft);
        soundManager.play(soundSet.frontRight);
        soundManager.play(soundSet.rearLeft);
        soundManager.play(soundSet.rearRight);
        return soundSet;
    }

    void replaceCar(OpenwheelCarEntity car) {
        this.car = car;
        engine.replaceCar(car);
        frontLeft.replaceCar(car);
        frontRight.replaceCar(car);
        rearLeft.replaceCar(car);
        rearRight.replaceCar(car);
    }

    void updateListener(Vec3 listenerPosition) {
        engine.updateListener(listenerPosition);
        frontLeft.updateListener(listenerPosition);
        frontRight.updateListener(listenerPosition);
        rearLeft.updateListener(listenerPosition);
        rearRight.updateListener(listenerPosition);
    }

    void updateEngine(SoundManager soundManager) {
        engine.update(soundManager);
    }

    boolean isEntityGone() {
        return car == null || !car.isAlive() || car.isRemoved();
    }

    void stop(SoundManager soundManager) {
        engine.stop(soundManager);
        soundManager.stop(frontLeft);
        soundManager.stop(frontRight);
        soundManager.stop(rearLeft);
        soundManager.stop(rearRight);
    }
}
