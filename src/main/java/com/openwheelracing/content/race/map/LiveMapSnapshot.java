package com.openwheelracing.content.race.map;

import com.openwheelracing.content.track.TrackMapSnapshot;
import java.util.List;
import java.util.UUID;

/** Positions are collected by the server, independently of the viewer's entity tracking range. */
public record LiveMapSnapshot(int generation, String dimension, long tick, Geometry geometry, List<Car> cars) {
    public LiveMapSnapshot {
        cars = List.copyOf(cars);
    }

    public record Car(UUID carId, UUID driverId, String label, double x, double z, float heading) {}
    public record Point(double x, double z) {}
    public record Finish(Point left, Point right, double heading) {}
    public record Geometry(TrackMapSnapshot surface, List<Point> route, Finish finish) {
        public Geometry {
            route = List.copyOf(route);
        }
    }
}
