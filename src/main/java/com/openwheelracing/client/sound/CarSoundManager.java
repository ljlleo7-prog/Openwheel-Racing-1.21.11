package com.openwheelracing.client.sound;

import com.openwheelracing.content.entity.OpenwheelCarEntity;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

public final class CarSoundManager {
    private static final int MAX_ACTIVE_CAR_SOUNDS = 3;
    private static final int SELECTION_HEARTBEAT_TICKS = 80;

    private static final Map<Integer, CarSoundSet> SOUNDS = new HashMap<>();
    private static Set<Integer> selectedCarIds = new HashSet<>();
    private static int heartbeatOffset;

    private CarSoundManager() {
    }

    public static void onClientTick() {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        LocalPlayer player = mc.player;
        if (level == null || player == null) {
            stopAll(mc.getSoundManager());
            selectedCarIds.clear();
            heartbeatOffset = 0;
            return;
        }

        SoundManager soundManager = mc.getSoundManager();
        Vec3 listenerPosition = player.position();
        if (shouldRefreshSelection()) {
            refreshSelection(level, player, soundManager);
        }

        Set<Integer> activeSelection = new HashSet<>(selectedCarIds);
        Set<Integer> stillActive = new HashSet<>();
        for (int carId : activeSelection) {
            OpenwheelCarEntity car = selectedCar(level, player, carId);
            if (car == null) {
                continue;
            }
            stillActive.add(carId);
            CarSoundSet soundSet = SOUNDS.get(carId);
            if (soundSet == null) {
                soundSet = CarSoundSet.start(soundManager, car, listenerPosition);
                SOUNDS.put(carId, soundSet);
            }
            soundSet.replaceCar(car);
        }

        SOUNDS.entrySet().removeIf(entry -> {
            CarSoundSet soundSet = entry.getValue();
            if (!stillActive.contains(entry.getKey()) || soundSet.isEntityGone()) {
                soundSet.stop(soundManager);
                return true;
            }
            soundSet.updateListener(listenerPosition);
            soundSet.updateEngine(soundManager);
            return false;
        });
        selectedCarIds = stillActive;
    }

    private static boolean shouldRefreshSelection() {
        heartbeatOffset = (heartbeatOffset + 1) % SELECTION_HEARTBEAT_TICKS;
        return heartbeatOffset == 0 || selectedCarIds.isEmpty();
    }

    private static void refreshSelection(ClientLevel level, LocalPlayer player, SoundManager soundManager) {
        Set<Integer> nextSelection = selectedCarIds(level, player);
        SOUNDS.entrySet().removeIf(entry -> {
            if (nextSelection.contains(entry.getKey())) {
                return false;
            }
            entry.getValue().stop(soundManager);
            return true;
        });
        selectedCarIds = nextSelection;
    }

    private static Set<Integer> selectedCarIds(ClientLevel level, LocalPlayer player) {
        List<OpenwheelCarEntity> candidates = new ArrayList<>();
        if (player.getVehicle() instanceof OpenwheelCarEntity car) {
            candidates.add(car);
        }

        for (Entity entity : level.entitiesForRendering()) {
            if (entity instanceof OpenwheelCarEntity car
                    && car.distanceToSqr(player) <= CarSoundPhysics.MAX_AUDIBLE_DISTANCE_SQR
                    && !containsCar(candidates, car)) {
                candidates.add(car);
            }
        }

        candidates.sort((a, b) -> Double.compare(selectionDistance(player, a), selectionDistance(player, b)));
        Set<Integer> ids = new HashSet<>();
        for (OpenwheelCarEntity car : candidates) {
            if (ids.size() >= MAX_ACTIVE_CAR_SOUNDS) {
                break;
            }
            ids.add(car.getId());
        }
        return ids;
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
        if (entity instanceof OpenwheelCarEntity car
                && car.isAlive()
                && !car.isRemoved()
                && car.distanceToSqr(player) <= CarSoundPhysics.MAX_AUDIBLE_DISTANCE_SQR) {
            return car;
        }
        return null;
    }

    private static void stopAll(SoundManager soundManager) {
        SOUNDS.values().forEach(soundSet -> soundSet.stop(soundManager));
        SOUNDS.clear();
    }
}
