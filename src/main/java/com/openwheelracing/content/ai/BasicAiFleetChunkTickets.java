package com.openwheelracing.content.ai;

import com.openwheelracing.OpenwheelRacing;
import com.openwheelracing.content.entity.OpenwheelCarEntity;
import com.openwheelracing.content.track.survey.SurveyRouteModel;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.neoforged.neoforge.common.world.chunk.LoadingValidationCallback;
import net.neoforged.neoforge.common.world.chunk.RegisterTicketControllersEvent;
import net.neoforged.neoforge.common.world.chunk.TicketController;
import net.neoforged.neoforge.common.world.chunk.TicketHelper;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class BasicAiFleetChunkTickets {
    public static final int MAX_TOTAL_CHUNKS = 128;
    private static final Identifier ID = Identifier.fromNamespaceAndPath(OpenwheelRacing.MODID, "basic_ai_fleet");
    private static final Map<UUID, OwnedTickets> OWNED = new HashMap<>();
    private static final TicketController CONTROLLER = new TicketController(ID, BasicAiFleetChunkTickets::validate);
    private static int deniedAcquisitions;

    private BasicAiFleetChunkTickets() {
    }

    public static void register(RegisterTicketControllersEvent event) {
        event.register(CONTROLLER);
    }

    public static boolean acquire(ServerLevel level, OpenwheelCarEntity car, SurveyRouteModel route, double distance) {
        Set<AiRouteChunkWindow.ChunkCoordinate> desired = AiRouteChunkWindow.around(route, distance);
        OwnedTickets existing = OWNED.get(car.getUUID());
        Set<AiRouteChunkWindow.ChunkCoordinate> currentUnique = uniqueChunks();
        Set<AiRouteChunkWindow.ChunkCoordinate> additionalChunks = new HashSet<>(desired);
        additionalChunks.removeAll(currentUnique);
        if (currentUnique.size() + additionalChunks.size() > MAX_TOTAL_CHUNKS) {
            deniedAcquisitions++;
            return false;
        }
        if (existing == null) {
            existing = new OwnedTickets(level, new HashSet<>());
            OWNED.put(car.getUUID(), existing);
        }
        for (AiRouteChunkWindow.ChunkCoordinate chunk : desired) {
            if (existing.chunks().add(chunk)) {
                CONTROLLER.forceChunk(level, car.getUUID(), chunk.x(), chunk.z(), true, true);
            }
        }
        Set<AiRouteChunkWindow.ChunkCoordinate> stale = new HashSet<>(existing.chunks());
        stale.removeAll(desired);
        for (AiRouteChunkWindow.ChunkCoordinate chunk : stale) {
            CONTROLLER.forceChunk(level, car.getUUID(), chunk.x(), chunk.z(), false, true);
            existing.chunks().remove(chunk);
        }
        return true;
    }

    public static boolean replaceOwner(ServerLevel level, OpenwheelCarEntity oldCar, OpenwheelCarEntity replacement,
                                       SurveyRouteModel route, double distance) {
        OwnedTickets previous = OWNED.remove(oldCar.getUUID());
        if (previous != null) {
            for (AiRouteChunkWindow.ChunkCoordinate chunk : previous.chunks()) {
                CONTROLLER.forceChunk(previous.level(), oldCar.getUUID(), chunk.x(), chunk.z(), false, true);
            }
        }
        if (acquire(level, replacement, route, distance)) return true;
        if (previous != null) {
            OWNED.put(oldCar.getUUID(), previous);
            for (AiRouteChunkWindow.ChunkCoordinate chunk : previous.chunks()) {
                CONTROLLER.forceChunk(previous.level(), oldCar.getUUID(), chunk.x(), chunk.z(), true, true);
            }
        }
        return false;
    }

    public static void release(OpenwheelCarEntity car) {
        OwnedTickets owned = OWNED.remove(car.getUUID());
        if (owned == null) return;
        for (AiRouteChunkWindow.ChunkCoordinate chunk : owned.chunks()) {
            CONTROLLER.forceChunk(owned.level(), car.getUUID(), chunk.x(), chunk.z(), false, true);
        }
    }

    public static void releaseAll() {
        for (Map.Entry<UUID, OwnedTickets> entry : OWNED.entrySet()) {
            for (AiRouteChunkWindow.ChunkCoordinate chunk : entry.getValue().chunks()) {
                CONTROLLER.forceChunk(entry.getValue().level(), entry.getKey(), chunk.x(), chunk.z(), false, true);
            }
        }
        OWNED.clear();
        deniedAcquisitions = 0;
    }

    public static int totalTicketCount() {
        return uniqueChunks().size();
    }

    private static Set<AiRouteChunkWindow.ChunkCoordinate> uniqueChunks() {
        Set<AiRouteChunkWindow.ChunkCoordinate> chunks = new HashSet<>();
        OWNED.values().forEach(owned -> chunks.addAll(owned.chunks()));
        return chunks;
    }

    public static int deniedAcquisitions() {
        return deniedAcquisitions;
    }

    public static boolean hasTickets(UUID carId) {
        return OWNED.containsKey(carId);
    }

    public static int ticketCount(UUID carId) {
        OwnedTickets owned = OWNED.get(carId);
        return owned == null ? 0 : owned.chunks().size();
    }

    private static void validate(ServerLevel level, TicketHelper helper) {
    }

    private record OwnedTickets(ServerLevel level, Set<AiRouteChunkWindow.ChunkCoordinate> chunks) {
    }
}
