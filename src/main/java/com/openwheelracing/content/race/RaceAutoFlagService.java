package com.openwheelracing.content.race;

import com.openwheelracing.content.entity.OpenwheelCarEntity;
import com.openwheelracing.content.track.TrackDefinition;
import com.openwheelracing.content.track.TrackDefinitionsData;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

public final class RaceAutoFlagService {
    private static final int STOPPED_TICKS = 200;
    private static final Map<ServerLevel, Map<UUID, Integer>> STOPPED = new IdentityHashMap<>();
    private static final Map<ServerLevel, Set<Integer>> OWNED_SECTORS = new IdentityHashMap<>();
    private static final Map<ServerLevel, Long> START_DUE = new IdentityHashMap<>();
    private RaceAutoFlagService() { }

    public static void onServerTick(ServerTickEvent.Post event) {
        boolean autoFlagTick = event.getServer().getTickCount() % 20 == 0;
        for (ServerLevel level : event.getServer().getAllLevels()) {
            OWRRaceControlState state = OWRRaceControlState.get(level);
            tickStartSequence(level, state);
            if (autoFlagTick) tickAutoFlags(level, state);
        }
    }

    private static void tickAutoFlags(ServerLevel level, OWRRaceControlState state) {
        if (!state.isAutoFlagging()) { clear(level, state); return; }
        TrackDefinition track = TrackDefinitionsData.get(level).activeTrack(level.dimension().identifier().toString()).orElse(null);
        if (track == null || track.checkpoints().isEmpty()) return;
        Map<UUID, Integer> timers = STOPPED.computeIfAbsent(level, ignored -> new HashMap<>());
        Set<UUID> seen = new HashSet<>();
        Set<Integer> hazardous = new HashSet<>();
        for (Entity entity : level.getAllEntities()) {
            if (!(entity instanceof OpenwheelCarEntity car) || !car.isVehicle()) continue;
            seen.add(car.getUUID());
            int ticks = car.getSpeedKmh() < 3.0 ? timers.getOrDefault(car.getUUID(), 0) + 20 : 0;
            timers.put(car.getUUID(), ticks);
            if (ticks >= STOPPED_TICKS) hazardous.add(nearestSector(track, car.getX(), car.getZ()));
        }
        timers.keySet().retainAll(seen);
        Set<Integer> owned = OWNED_SECTORS.computeIfAbsent(level, ignored -> new HashSet<>());
        for (int sector : new HashSet<>(owned)) if (!hazardous.contains(sector)) { state.setSectorSignal(sector, -1, RaceSignal.OFF); owned.remove(sector); }
        for (int sector : hazardous) if (owned.add(sector)) state.setSectorSignal(sector, -1, RaceSignal.YELLOW);
    }

    private static void tickStartSequence(ServerLevel level, OWRRaceControlState state) {
        int phase = state.getStartPhase();
        if (phase < 1 || phase > 5) { START_DUE.remove(level); return; }
        long now = level.getGameTime();
        long due = START_DUE.computeIfAbsent(level, ignored -> now + (phase < 5 ? 20L : 20L + level.random.nextInt(41)));
        if (now < due) return;
        state.setStartPhase(phase < 5 ? phase + 1 : 6);
        START_DUE.remove(level);
    }

    public static void requestStart(ServerLevel level) {
        START_DUE.put(level, level.getGameTime() + 20L);
    }

    private static int nearestSector(TrackDefinition track, double x, double z) {
        TrackDefinition.Checkpoint closest = track.checkpoints().getFirst(); double best = Double.MAX_VALUE;
        for (TrackDefinition.Checkpoint checkpoint : track.checkpoints()) {
            double cx = (checkpoint.left().x() + checkpoint.right().x()) * 0.5, cz = (checkpoint.left().z() + checkpoint.right().z()) * 0.5;
            double d = (cx - x) * (cx - x) + (cz - z) * (cz - z); if (d < best) { best = d; closest = checkpoint; }
        }
        int index = closest.index();
        return track.sectors().stream().filter(s -> index >= s.startCheckpointIndex() && index <= s.endCheckpointIndex()).map(TrackDefinition.Sector::index).findFirst().orElse(0);
    }

    private static void clear(ServerLevel level, OWRRaceControlState state) {
        Set<Integer> owned = OWNED_SECTORS.remove(level); if (owned != null) for (int sector : owned) state.setSectorSignal(sector, -1, RaceSignal.OFF);
        STOPPED.remove(level);
    }
    public static void clearAll() { STOPPED.clear(); OWNED_SECTORS.clear(); START_DUE.clear(); }
}
