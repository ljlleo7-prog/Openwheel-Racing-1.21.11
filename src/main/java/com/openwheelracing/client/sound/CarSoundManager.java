package com.openwheelracing.client.sound;

import com.openwheelracing.content.entity.OpenwheelCarEntity;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

public final class CarSoundManager {
    private static final int MAX_TRACKED_CARS = 2;
    private static final int SELECTION_HEARTBEAT_TICKS = 10;
    private static final CarSoundSlot[] SLOTS = new CarSoundSlot[MAX_TRACKED_CARS];

    private static int heartbeatOffset;

    static {
        for (int i = 0; i < SLOTS.length; i++) {
            SLOTS[i] = new CarSoundSlot();
        }
    }

    private CarSoundManager() {
    }

    public static void onClientTick() {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        LocalPlayer player = mc.player;
        if (level == null || player == null) {
            stopAll(mc.getSoundManager());
            heartbeatOffset = 0;
            return;
        }

        SoundManager soundManager = mc.getSoundManager();
        Vec3 listenerPosition = player.position();
        if (shouldRefreshSelection()) {
            refreshSelection(level, player, soundManager, listenerPosition);
        }

        for (CarSoundSlot slot : SLOTS) {
            slot.update(level, player, soundManager, listenerPosition);
        }
    }

    private static boolean shouldRefreshSelection() {
        if (!hasActiveSlots()) {
            heartbeatOffset = 0;
            return true;
        }

        heartbeatOffset++;
        if (heartbeatOffset >= SELECTION_HEARTBEAT_TICKS) {
            heartbeatOffset = 0;
            return true;
        }
        return false;
    }

    private static boolean hasActiveSlots() {
        for (CarSoundSlot slot : SLOTS) {
            if (slot.isActive()) {
                return true;
            }
        }
        return false;
    }

    private static void refreshSelection(ClientLevel level, LocalPlayer player, SoundManager soundManager, Vec3 listenerPosition) {
        List<OpenwheelCarEntity> selectedCars = selectedCars(level, player);
        for (int i = 0; i < SLOTS.length; i++) {
            if (i < selectedCars.size()) {
                SLOTS[i].assign(soundManager, selectedCars.get(i), listenerPosition);
            } else {
                SLOTS[i].stop(soundManager);
            }
        }
    }

    private static List<OpenwheelCarEntity> selectedCars(ClientLevel level, LocalPlayer player) {
        List<OpenwheelCarEntity> candidates = new ArrayList<>();
        if (player.getVehicle() instanceof OpenwheelCarEntity car && isSelectable(player, car)) {
            candidates.add(car);
        }

        for (Entity entity : level.entitiesForRendering()) {
            if (entity instanceof OpenwheelCarEntity car
                    && isSelectable(player, car)
                    && !containsCar(candidates, car)) {
                candidates.add(car);
            }
        }

        candidates.sort((a, b) -> Double.compare(selectionDistance(player, a), selectionDistance(player, b)));
        List<OpenwheelCarEntity> selected = new ArrayList<>();
        for (OpenwheelCarEntity car : candidates) {
            if (selected.size() >= MAX_TRACKED_CARS) {
                break;
            }
            selected.add(car);
        }
        return selected;
    }

    private static boolean isSelectable(LocalPlayer player, OpenwheelCarEntity car) {
        return car.isAlive()
                && !car.isRemoved()
                && car.distanceToSqr(player) <= CarSoundPhysics.MAX_AUDIBLE_DISTANCE_SQR;
    }

    private static double selectionDistance(LocalPlayer player, OpenwheelCarEntity car) {
        return player.getVehicle() == car ? -1.0 : car.distanceToSqr(player);
    }

    private static boolean containsCar(List<OpenwheelCarEntity> cars, OpenwheelCarEntity target) {
        for (OpenwheelCarEntity car : cars) {
            if (car.getId() == target.getId()) {
                return true;
            }
        }
        return false;
    }

    private static OpenwheelCarEntity selectedCar(ClientLevel level, LocalPlayer player, int carId) {
        Entity entity = level.getEntity(carId);
        if (entity instanceof OpenwheelCarEntity car && isSelectable(player, car)) {
            return car;
        }
        return null;
    }

    private static void stopAll(SoundManager soundManager) {
        for (CarSoundSlot slot : SLOTS) {
            slot.stop(soundManager);
        }
    }

    private static final class CarSoundSlot {
        private static final int NO_CAR = Integer.MIN_VALUE;

        private CarSoundSet soundSet;
        private int carId = NO_CAR;

        boolean isActive() {
            return soundSet != null && carId != NO_CAR;
        }

        void assign(SoundManager soundManager, OpenwheelCarEntity car, Vec3 listenerPosition) {
            if (soundSet == null) {
                soundSet = CarSoundSet.start(soundManager, car, listenerPosition);
            } else {
                soundSet.replaceCar(car);
                soundSet.updateListener(listenerPosition);
            }
            carId = car.getId();
        }

        void update(ClientLevel level, LocalPlayer player, SoundManager soundManager, Vec3 listenerPosition) {
            if (!isActive()) {
                return;
            }

            OpenwheelCarEntity car = selectedCar(level, player, carId);
            if (car == null) {
                stop(soundManager);
                return;
            }

            soundSet.replaceCar(car);
            soundSet.updateListener(listenerPosition);
            soundSet.updateEngine(soundManager);
        }

        void stop(SoundManager soundManager) {
            if (soundSet != null) {
                soundSet.stop(soundManager);
                soundSet = null;
            }
            carId = NO_CAR;
        }
    }
}
