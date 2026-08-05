package com.openwheelracing.content.track;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

public record TrackDefinition(
    UUID trackId,
    String name,
    String dimensionId,
    List<CenterlineNode> centerline,
    Optional<StartFinishLine> startFinish,
    List<Checkpoint> checkpoints,
    List<Sector> sectors,
    List<BoundarySample> boundaries,
    List<GridSlot> gridSlots,
    List<AiWaypoint> aiLine,
    List<StewardLine> stewardLines,
    int schemaVersion
) {
    public static final int CURRENT_SCHEMA = 1;
    public static final Codec<UUID> UUID_CODEC = Codec.STRING.xmap(UUID::fromString, UUID::toString);

    public static final Codec<TrackDefinition> CODEC = RecordCodecBuilder.create(instance -> instance.group(
        UUID_CODEC.fieldOf("track_id").forGetter(TrackDefinition::trackId),
        Codec.STRING.fieldOf("name").forGetter(TrackDefinition::name),
        Codec.STRING.fieldOf("dimension").forGetter(TrackDefinition::dimensionId),
        CenterlineNode.CODEC.listOf().optionalFieldOf("centerline", List.of()).forGetter(TrackDefinition::centerline),
        StartFinishLine.CODEC.optionalFieldOf("start_finish").forGetter(TrackDefinition::startFinish),
        Checkpoint.CODEC.listOf().optionalFieldOf("checkpoints", List.of()).forGetter(TrackDefinition::checkpoints),
        Sector.CODEC.listOf().optionalFieldOf("sectors", List.of()).forGetter(TrackDefinition::sectors),
        BoundarySample.CODEC.listOf().optionalFieldOf("boundaries", List.of()).forGetter(TrackDefinition::boundaries),
        GridSlot.CODEC.listOf().optionalFieldOf("grid_slots", List.of()).forGetter(TrackDefinition::gridSlots),
        AiWaypoint.CODEC.listOf().optionalFieldOf("ai_line", List.of()).forGetter(TrackDefinition::aiLine),
        StewardLine.CODEC.listOf().optionalFieldOf("steward_lines", List.of()).forGetter(TrackDefinition::stewardLines),
        Codec.INT.optionalFieldOf("schema", CURRENT_SCHEMA).forGetter(TrackDefinition::schemaVersion)
    ).apply(instance, TrackDefinition::new));

    public TrackDefinition {
        name = name == null || name.isBlank() ? "Unnamed Track" : name;
        dimensionId = dimensionId == null || dimensionId.isBlank() ? "minecraft:overworld" : dimensionId;
        centerline = List.copyOf(centerline == null ? List.of() : centerline);
        startFinish = startFinish == null ? Optional.empty() : startFinish;
        checkpoints = List.copyOf(checkpoints == null ? List.of() : checkpoints);
        sectors = List.copyOf(sectors == null ? List.of() : sectors);
        boundaries = List.copyOf(boundaries == null ? List.of() : boundaries);
        gridSlots = List.copyOf(gridSlots == null ? List.of() : gridSlots);
        aiLine = List.copyOf(aiLine == null ? List.of() : aiLine);
        stewardLines = List.copyOf(stewardLines == null ? List.of() : stewardLines);
        schemaVersion = Math.max(1, schemaVersion);
    }

    public static TrackDefinition empty(UUID trackId, String name, String dimensionId) {
        return new TrackDefinition(trackId, name, dimensionId, List.of(), Optional.empty(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), CURRENT_SCHEMA);
    }

    public boolean hasRaceStewardingGeometry() {
        return centerline.size() >= 2 && startFinish.isPresent();
    }

    public double length() {
        if (centerline.isEmpty()) {
            return 0.0;
        }
        return Math.max(0.0, centerline.get(centerline.size() - 1).distanceAlongTrack());
    }

    public TrackDefinition withCenterline(List<CenterlineNode> centerline) {
        return new TrackDefinition(trackId, name, dimensionId, centerline, startFinish, checkpoints, sectors, boundaries, gridSlots, aiLine, stewardLines, schemaVersion);
    }

    public TrackDefinition withStartFinish(StartFinishLine startFinish) {
        return new TrackDefinition(trackId, name, dimensionId, centerline, Optional.of(startFinish), checkpoints, sectors, boundaries, gridSlots, aiLine, stewardLines, schemaVersion);
    }

    public TrackDefinition withCheckpoints(List<Checkpoint> checkpoints) {
        return new TrackDefinition(trackId, name, dimensionId, centerline, startFinish, checkpoints, sectors, boundaries, gridSlots, aiLine, stewardLines, schemaVersion);
    }

    public TrackDefinition withBoundaries(List<BoundarySample> boundaries) {
        return new TrackDefinition(trackId, name, dimensionId, centerline, startFinish, checkpoints, sectors, boundaries, gridSlots, aiLine, stewardLines, schemaVersion);
    }

    public TrackDefinition withGridSlots(List<GridSlot> gridSlots) {
        return new TrackDefinition(trackId, name, dimensionId, centerline, startFinish, checkpoints, sectors, boundaries, gridSlots, aiLine, stewardLines, schemaVersion);
    }

    public TrackDefinition withAiLine(List<AiWaypoint> aiLine) {
        return new TrackDefinition(trackId, name, dimensionId, centerline, startFinish, checkpoints, sectors, boundaries, gridSlots, aiLine, stewardLines, schemaVersion);
    }

    public TrackDefinition withStewardLines(List<StewardLine> stewardLines) {
        return new TrackDefinition(trackId, name, dimensionId, centerline, startFinish, checkpoints, sectors, boundaries, gridSlots, aiLine, stewardLines, schemaVersion);
    }

    public record Point3(double x, double y, double z) {
        public static final Codec<Point3> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.DOUBLE.fieldOf("x").forGetter(Point3::x),
            Codec.DOUBLE.fieldOf("y").forGetter(Point3::y),
            Codec.DOUBLE.fieldOf("z").forGetter(Point3::z)
        ).apply(instance, Point3::new));

        public static Point3 centerOf(BlockPos pos) {
            return new Point3(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
        }

        public Vec3 asVec3() {
            return new Vec3(x, y, z);
        }
    }

    public record CenterlineNode(Point3 position, double widthLeft, double widthRight, double headingRadians, double distanceAlongTrack) {
        public static final Codec<CenterlineNode> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Point3.CODEC.fieldOf("position").forGetter(CenterlineNode::position),
            Codec.DOUBLE.optionalFieldOf("width_left", 4.0).forGetter(CenterlineNode::widthLeft),
            Codec.DOUBLE.optionalFieldOf("width_right", 4.0).forGetter(CenterlineNode::widthRight),
            Codec.DOUBLE.optionalFieldOf("heading", 0.0).forGetter(CenterlineNode::headingRadians),
            Codec.DOUBLE.optionalFieldOf("distance", 0.0).forGetter(CenterlineNode::distanceAlongTrack)
        ).apply(instance, CenterlineNode::new));

        public CenterlineNode {
            widthLeft = Math.max(0.5, widthLeft);
            widthRight = Math.max(0.5, widthRight);
            distanceAlongTrack = Math.max(0.0, distanceAlongTrack);
        }
    }

    public record StartFinishLine(Point3 left, Point3 right, double headingRadians) {
        public static final Codec<StartFinishLine> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Point3.CODEC.fieldOf("left").forGetter(StartFinishLine::left),
            Point3.CODEC.fieldOf("right").forGetter(StartFinishLine::right),
            Codec.DOUBLE.optionalFieldOf("heading", 0.0).forGetter(StartFinishLine::headingRadians)
        ).apply(instance, StartFinishLine::new));
    }

    public record Checkpoint(int index, String name, Point3 left, Point3 right, boolean required) {
        public static final Codec<Checkpoint> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.INT.fieldOf("index").forGetter(Checkpoint::index),
            Codec.STRING.optionalFieldOf("name", "").forGetter(Checkpoint::name),
            Point3.CODEC.fieldOf("left").forGetter(Checkpoint::left),
            Point3.CODEC.fieldOf("right").forGetter(Checkpoint::right),
            Codec.BOOL.optionalFieldOf("required", true).forGetter(Checkpoint::required)
        ).apply(instance, Checkpoint::new));
    }

    public record Sector(int index, String name, int startCheckpointIndex, int endCheckpointIndex) {
        public static final Codec<Sector> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.INT.fieldOf("index").forGetter(Sector::index),
            Codec.STRING.optionalFieldOf("name", "").forGetter(Sector::name),
            Codec.INT.fieldOf("start_checkpoint").forGetter(Sector::startCheckpointIndex),
            Codec.INT.fieldOf("end_checkpoint").forGetter(Sector::endCheckpointIndex)
        ).apply(instance, Sector::new));
    }

    public enum StewardLineType {
        CHECKPOINT("Checkpoint"),
        SECTOR_SPLIT("Sector Split"),
        PIT_LIMIT_START("Pit Limit Start"),
        PIT_LIMIT_END("Pit Limit End"),
        SAFETY_CAR_LINE("Safety Car Line"),
        DRS_DETECTION("DRS Detection"),
        DRS_ACTIVATION("DRS Activation");

        public static final Codec<StewardLineType> CODEC = Codec.STRING.xmap(
            StewardLineType::fromSerializedName,
            StewardLineType::serializedName
        );

        private final String displayName;

        StewardLineType(String displayName) {
            this.displayName = displayName;
        }

        public String displayName() {
            return displayName;
        }

        public String serializedName() {
            return name().toLowerCase(Locale.ROOT);
        }

        public static StewardLineType fromSerializedName(String value) {
            String normalized = value == null ? "" : value.trim().replace('-', '_').replace(' ', '_').toUpperCase(Locale.ROOT);
            if (normalized.equals("MARSHALLING_POINT") || normalized.equals("MARSHALING_POINT") || normalized.equals("CP")) {
                return CHECKPOINT;
            }
            if (normalized.equals("SC_LINE")) {
                return SAFETY_CAR_LINE;
            }
            return StewardLineType.valueOf(normalized);
        }
    }

    public record StewardLine(StewardLineType type, int index, String name, Point3 left, Point3 right, double headingRadians, double distanceAlongTrack) {
        public static final Codec<StewardLine> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            StewardLineType.CODEC.fieldOf("type").forGetter(StewardLine::type),
            Codec.INT.optionalFieldOf("index", 1).forGetter(StewardLine::index),
            Codec.STRING.optionalFieldOf("name", "").forGetter(StewardLine::name),
            Point3.CODEC.fieldOf("left").forGetter(StewardLine::left),
            Point3.CODEC.fieldOf("right").forGetter(StewardLine::right),
            Codec.DOUBLE.optionalFieldOf("heading", 0.0).forGetter(StewardLine::headingRadians),
            Codec.DOUBLE.optionalFieldOf("distance", 0.0).forGetter(StewardLine::distanceAlongTrack)
        ).apply(instance, StewardLine::new));

        public StewardLine {
            if (type == null) {
                type = StewardLineType.CHECKPOINT;
            }
            index = Math.max(1, index);
            name = name == null || name.isBlank() ? type.displayName() + " " + index : name;
            distanceAlongTrack = Math.max(0.0, distanceAlongTrack);
        }
    }

    public enum BoundarySide {
        LEFT,
        RIGHT;

        public static final Codec<BoundarySide> CODEC = Codec.STRING.xmap(
            value -> BoundarySide.valueOf(value.toUpperCase(Locale.ROOT)),
            value -> value.name().toLowerCase(Locale.ROOT)
        );
    }

    public record BoundarySample(BoundarySide side, Point3 position, double distanceAlongTrack) {
        public static final Codec<BoundarySample> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            BoundarySide.CODEC.fieldOf("side").forGetter(BoundarySample::side),
            Point3.CODEC.fieldOf("position").forGetter(BoundarySample::position),
            Codec.DOUBLE.optionalFieldOf("distance", 0.0).forGetter(BoundarySample::distanceAlongTrack)
        ).apply(instance, BoundarySample::new));
    }

    public record GridSlot(int index, Point3 position, double headingRadians) {
        public static final Codec<GridSlot> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.INT.fieldOf("index").forGetter(GridSlot::index),
            Point3.CODEC.fieldOf("position").forGetter(GridSlot::position),
            Codec.DOUBLE.optionalFieldOf("heading", 0.0).forGetter(GridSlot::headingRadians)
        ).apply(instance, GridSlot::new));
    }

    public record AiWaypoint(int index, Point3 position, double targetSpeedKmh, double racingLineOffset) {
        public static final Codec<AiWaypoint> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.INT.fieldOf("index").forGetter(AiWaypoint::index),
            Point3.CODEC.fieldOf("position").forGetter(AiWaypoint::position),
            Codec.DOUBLE.optionalFieldOf("target_speed_kmh", 80.0).forGetter(AiWaypoint::targetSpeedKmh),
            Codec.DOUBLE.optionalFieldOf("racing_line_offset", 0.0).forGetter(AiWaypoint::racingLineOffset)
        ).apply(instance, AiWaypoint::new));
    }
}
