package com.openwheelracing.content.race.timing;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.openwheelracing.OpenwheelRacing;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import java.util.List;
import java.util.UUID;

public final class LiveRaceTimingData extends SavedData {
    private static final Codec<UUID> UUID_CODEC = Codec.STRING.xmap(UUID::fromString, UUID::toString);
    private static final Codec<SavedParticipant> PARTICIPANT_CODEC = RecordCodecBuilder.create(instance -> instance.group(
        UUID_CODEC.fieldOf("participant_id").forGetter(SavedParticipant::participantId),
        Codec.INT.fieldOf("participant_kind").forGetter(SavedParticipant::participantKind),
        Codec.STRING.optionalFieldOf("display_name", "").forGetter(SavedParticipant::displayName),
        Codec.INT.optionalFieldOf("completed_laps", 0).forGetter(SavedParticipant::completedLaps),
        Codec.DOUBLE.optionalFieldOf("route_distance", 0.0).forGetter(SavedParticipant::routeDistanceMeters),
        Codec.INT.optionalFieldOf("stable_position", 0).forGetter(SavedParticipant::stablePosition)
    ).apply(instance, SavedParticipant::new));
    private static final Codec<LiveRaceTimingData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
        Codec.BOOL.optionalFieldOf("configured", false).forGetter(data -> data.configured),
        Codec.BOOL.optionalFieldOf("active", false).forGetter(data -> data.active),
        Codec.STRING.optionalFieldOf("suspension_reason", "").forGetter(data -> data.suspensionReason),
        Codec.LONG.optionalFieldOf("session_id", 0L).forGetter(data -> data.sessionId),
        Codec.STRING.optionalFieldOf("session_name", "").forGetter(data -> data.sessionName),
        UUID_CODEC.optionalFieldOf("track_id", new UUID(0L, 0L)).forGetter(data -> data.trackId),
        UUID_CODEC.optionalFieldOf("route_id", new UUID(0L, 0L)).forGetter(data -> data.routeId),
        Codec.INT.optionalFieldOf("survey_revision", 0).forGetter(data -> data.surveyRevision),
        Codec.LONG.optionalFieldOf("snapshot_revision", 0L).forGetter(data -> data.snapshotRevision),
        PARTICIPANT_CODEC.listOf().optionalFieldOf("participants", List.of()).forGetter(data -> data.participants)
    ).apply(instance, LiveRaceTimingData::new));
    private static final SavedDataType<LiveRaceTimingData> TYPE = new SavedDataType<>(
        OpenwheelRacing.MODID + "_live_race_timing",
        LiveRaceTimingData::new,
        CODEC,
        null
    );

    private boolean configured;
    private boolean active;
    private String suspensionReason = "";
    private long sessionId;
    private String sessionName = "";
    private UUID trackId = new UUID(0L, 0L);
    private UUID routeId = new UUID(0L, 0L);
    private int surveyRevision;
    private long snapshotRevision;
    private List<SavedParticipant> participants = List.of();

    public LiveRaceTimingData() {
    }

    private LiveRaceTimingData(boolean configured, boolean active, String suspensionReason, long sessionId, String sessionName,
                               UUID trackId, UUID routeId, int surveyRevision, long snapshotRevision, List<SavedParticipant> participants) {
        this.configured = configured;
        this.active = false;
        this.suspensionReason = configured ? "SERVER_RECOVERY" : suspensionReason;
        this.sessionId = sessionId;
        this.sessionName = sessionName;
        this.trackId = trackId;
        this.routeId = routeId;
        this.surveyRevision = surveyRevision;
        this.snapshotRevision = snapshotRevision;
        this.participants = List.copyOf(participants);
    }

    public static LiveRaceTimingData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(TYPE);
    }

    public Checkpoint checkpoint() {
        return new Checkpoint(configured, active, suspensionReason, sessionId, sessionName, trackId, routeId, surveyRevision, snapshotRevision, participants);
    }

    public void update(Checkpoint checkpoint) {
        configured = checkpoint.configured();
        active = checkpoint.active();
        suspensionReason = checkpoint.suspensionReason();
        sessionId = checkpoint.sessionId();
        sessionName = checkpoint.sessionName();
        trackId = checkpoint.trackId();
        routeId = checkpoint.routeId();
        surveyRevision = checkpoint.surveyRevision();
        snapshotRevision = checkpoint.snapshotRevision();
        participants = List.copyOf(checkpoint.participants());
        setDirty();
    }

    public record Checkpoint(boolean configured, boolean active, String suspensionReason, long sessionId, String sessionName,
                             UUID trackId, UUID routeId, int surveyRevision, long snapshotRevision, List<SavedParticipant> participants) {
        public Checkpoint {
            suspensionReason = suspensionReason == null ? "" : suspensionReason;
            sessionName = sessionName == null ? "" : sessionName;
            participants = List.copyOf(participants);
        }
    }

    public record SavedParticipant(UUID participantId, int participantKind, String displayName, int completedLaps,
                                   double routeDistanceMeters, int stablePosition) {
    }
}
