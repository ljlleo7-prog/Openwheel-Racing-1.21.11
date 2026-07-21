package com.openwheelracing.client.sound;

import com.openwheelracing.content.entity.OpenwheelCarEntity;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.world.phys.Vec3;

final class CarEngineSoundCluster {
    private final CarEngineSoundInstance[] samples = new CarEngineSoundInstance[CarEngineSoundInstance.sampleCount()];
    private OpenwheelCarEntity car;
    private Vec3 listenerPosition;

    private CarEngineSoundCluster(OpenwheelCarEntity car, Vec3 listenerPosition) {
        this.car = car;
        this.listenerPosition = listenerPosition;
        for (int i = 0; i < samples.length; i++) {
            samples[i] = CarEngineSoundInstance.rpmSample(car, listenerPosition, i);
        }
    }

    static CarEngineSoundCluster start(SoundManager soundManager, OpenwheelCarEntity car, Vec3 listenerPosition) {
        CarEngineSoundCluster cluster = new CarEngineSoundCluster(car, listenerPosition);
        for (CarEngineSoundInstance sample : cluster.samples) {
            soundManager.play(sample);
        }
        return cluster;
    }

    void replaceCar(OpenwheelCarEntity car) {
        this.car = car;
        for (CarEngineSoundInstance sample : samples) {
            sample.replaceCar(car);
        }
    }

    void updateListener(Vec3 listenerPosition) {
        this.listenerPosition = listenerPosition;
        for (CarEngineSoundInstance sample : samples) {
            sample.updateListener(listenerPosition);
        }
    }

    void update(SoundManager soundManager) {
        for (int i = 0; i < samples.length; i++) {
            if (samples[i].isStopped()) {
                samples[i] = CarEngineSoundInstance.rpmSample(car, listenerPosition, i);
                soundManager.play(samples[i]);
            }
        }
    }

    void stop(SoundManager soundManager) {
        for (CarEngineSoundInstance sample : samples) {
            soundManager.stop(sample);
        }
    }
}
