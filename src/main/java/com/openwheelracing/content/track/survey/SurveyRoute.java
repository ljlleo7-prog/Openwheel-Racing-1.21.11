package com.openwheelracing.content.track.survey;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.List;
import java.util.UUID;

public record SurveyRoute(UUID routeId, UUID trackId, List<Sample> rawSamples, List<Node> nodes, double length, double spacing, int schemaVersion) {
    public static final int CURRENT_SCHEMA = 1;
    public static final int MAX_POINTS = 8192;
    private static final Codec<UUID> UUID_CODEC = Codec.STRING.xmap(UUID::fromString, UUID::toString);

    public static final Codec<SurveyRoute> CODEC = RecordCodecBuilder.create(instance -> instance.group(
        UUID_CODEC.fieldOf("route_id").forGetter(SurveyRoute::routeId), UUID_CODEC.fieldOf("track_id").forGetter(SurveyRoute::trackId),
        Sample.CODEC.listOf(0, MAX_POINTS).fieldOf("raw_samples").forGetter(SurveyRoute::rawSamples),
        Node.CODEC.listOf(0, MAX_POINTS).fieldOf("nodes").forGetter(SurveyRoute::nodes),
        Codec.DOUBLE.fieldOf("length").forGetter(SurveyRoute::length), Codec.DOUBLE.fieldOf("spacing").forGetter(SurveyRoute::spacing),
        Codec.INT.optionalFieldOf("schema", CURRENT_SCHEMA).forGetter(SurveyRoute::schemaVersion)
    ).apply(instance, SurveyRoute::new));

    public SurveyRoute {
        rawSamples = List.copyOf(rawSamples);
        nodes = List.copyOf(nodes);
        length = Math.max(0.0, length);
        spacing = Math.max(0.25, spacing);
        schemaVersion = Math.max(1, schemaVersion);
    }

    public static SurveyRoute fromModel(SurveyRouteModel model) {
        return new SurveyRoute(model.routeId(), model.trackId(),
            model.rawSamples().stream().map(sample -> new Sample(Point.fromModel(sample.position()), sample.headingRadians())).toList(),
            model.nodes().stream().map(node -> new Node(node.index(), Point.fromModel(node.position()), node.headingRadians(), node.distanceAlongRoute())).toList(),
            model.length(), model.spacing(), CURRENT_SCHEMA);
    }

    public SurveyRouteModel toModel() {
        return new SurveyRouteModel(routeId, trackId,
            rawSamples.stream().map(sample -> new SurveyRouteModel.Sample(sample.position().toModel(), sample.headingRadians())).toList(),
            nodes.stream().map(node -> new SurveyRouteModel.Node(node.index(), node.position().toModel(), node.headingRadians(), node.distanceAlongRoute())).toList(),
            length, spacing);
    }

    public record Point(double x, double y, double z) {
        public static final Codec<Point> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.DOUBLE.fieldOf("x").forGetter(Point::x), Codec.DOUBLE.fieldOf("y").forGetter(Point::y), Codec.DOUBLE.fieldOf("z").forGetter(Point::z)
        ).apply(instance, Point::new));
        static Point fromModel(SurveyRouteModel.Point point) { return new Point(point.x(), point.y(), point.z()); }
        SurveyRouteModel.Point toModel() { return new SurveyRouteModel.Point(x, y, z); }
    }

    public record Sample(Point position, double headingRadians) {
        public static final Codec<Sample> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Point.CODEC.fieldOf("position").forGetter(Sample::position), Codec.DOUBLE.optionalFieldOf("heading", 0.0).forGetter(Sample::headingRadians)
        ).apply(instance, Sample::new));
    }

    public record Node(int index, Point position, double headingRadians, double distanceAlongRoute) {
        public static final Codec<Node> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.INT.fieldOf("index").forGetter(Node::index), Point.CODEC.fieldOf("position").forGetter(Node::position),
            Codec.DOUBLE.optionalFieldOf("heading", 0.0).forGetter(Node::headingRadians), Codec.DOUBLE.fieldOf("distance").forGetter(Node::distanceAlongRoute)
        ).apply(instance, Node::new));
    }
}
