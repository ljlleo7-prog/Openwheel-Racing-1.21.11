package com.openwheelracing.content.race;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.openwheelracing.OpenwheelRacing;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class PitLanePenaltyData extends SavedData {
    private static final Codec<Incident> INCIDENT_CODEC = RecordCodecBuilder.create(instance -> instance.group(
        Codec.LONG.fieldOf("id").forGetter(Incident::id),
        Codec.STRING.xmap(UUID::fromString, UUID::toString).fieldOf("driver_id").forGetter(Incident::driverId),
        Codec.STRING.fieldOf("driver_name").forGetter(Incident::driverName),
        Codec.DOUBLE.fieldOf("instant_kmh").forGetter(Incident::instantKmh),
        Codec.DOUBLE.fieldOf("average_kmh").forGetter(Incident::averageKmh),
        Codec.LONG.fieldOf("game_time").forGetter(Incident::gameTime),
        Codec.STRING.fieldOf("reason").forGetter(Incident::reason)
    ).apply(instance, Incident::new));
    private static final Codec<PitLanePenaltyData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
        INCIDENT_CODEC.listOf().optionalFieldOf("pending", List.of()).forGetter(data -> data.pending),
        Codec.LONG.optionalFieldOf("next_id", 1L).forGetter(data -> data.nextId)
    ).apply(instance, PitLanePenaltyData::new));
    private static final SavedDataType<PitLanePenaltyData> TYPE = new SavedDataType<>(OpenwheelRacing.MODID + "_pit_lane_penalties",
        PitLanePenaltyData::new, CODEC, null);

    private final List<Incident> pending = new ArrayList<>();
    private long nextId = 1L;
    private int revision;

    public PitLanePenaltyData() {}
    private PitLanePenaltyData(List<Incident> pending, long nextId) { this.pending.addAll(pending); this.nextId = nextId; }
    public static PitLanePenaltyData get(ServerLevel level) { return level.getDataStorage().computeIfAbsent(TYPE); }
    public List<Incident> pending() { return List.copyOf(pending); }
    public int revision() { return revision; }

    public Incident report(UUID driverId, String driverName, double instantKmh, double averageKmh, long gameTime, String reason) {
        Incident incident = new Incident(nextId++, driverId, driverName, instantKmh, averageKmh, gameTime, reason);
        pending.add(incident);
        if (pending.size() > 100) pending.removeFirst();
        revision++;
        setDirty();
        return incident;
    }

    public record Incident(long id, UUID driverId, String driverName, double instantKmh, double averageKmh, long gameTime, String reason) {}
}
