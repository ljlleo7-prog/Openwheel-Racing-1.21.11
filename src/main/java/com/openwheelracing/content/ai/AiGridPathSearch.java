package com.openwheelracing.content.ai;

import java.util.*;

/** Incremental forward-only hybrid A*: samples swept car footprints, not just survey nodes. */
public final class AiGridPathSearch {
    public record Pose(double x, double z, double heading) { }
    public interface Road { boolean clear(double x, double z, double heading); }
    private record Key(int x, int z, int heading) { }
    private record Node(Pose pose, double cost, double score, Node parent) { }
    private static final double STEP = 4.0;
    private static final double TURN = Math.PI / 8.0;
    private static final int MAX_NODES = 12_000;
    private final PriorityQueue<Node> open = new PriorityQueue<>(Comparator.comparingDouble(Node::score));
    private final Map<Key, Double> costs = new HashMap<>();
    private final Pose start;
    private final AiGridApproach.Target target;
    private Road road;

    public void updateRoad(Road road) { this.road = road; }
    private final Pose approach;
    private List<Pose> path = List.of();
    private int expanded;
    private boolean done;

    public AiGridPathSearch(Pose start, AiGridApproach.Target target, Road road) {
        this.start = start;
        this.target = target;
        this.road = road;
        approach = new Pose(target.x() - 8.0 * Math.cos(target.heading()), target.z() - 8.0 * Math.sin(target.heading()), target.heading());
        double direction = Math.atan2(approach.z() - start.z(), approach.x() - start.x());
        if (distance(start, approach) > 4.0 && Math.abs(wrap(direction - start.heading())) < Math.toRadians(15)
            && Math.abs(wrap(direction - target.heading())) < Math.toRadians(15)
            && segmentClear(start, approach) && segmentClear(approach, new Pose(target.x(), target.z(), target.heading()))) {
            ArrayList<Pose> direct = new ArrayList<>();
            direct.add(start);
            int steps = Math.max(1, (int) Math.ceil(distance(start, approach) / STEP));
            for (int step = 1; step <= steps; step++) {
                double t = step / (double) steps;
                direct.add(new Pose(start.x() + (approach.x() - start.x()) * t, start.z() + (approach.z() - start.z()) * t,
                    start.heading() + wrap(target.heading() - start.heading()) * t));
            }
            direct.add(new Pose(target.x(), target.z(), target.heading()));
            path = List.copyOf(direct);
            done = true;
        }
        open.add(new Node(start, 0, distance(start, approach), null));
        costs.put(key(start), 0.0);
    }

    /** Returns consumed expansions so the fleet can enforce a shared per-tick budget. */
    public int advance(int budget) {
        int used = 0;
        while (!done && used < budget && !open.isEmpty()) {
            Node current = open.remove();
            if (current.cost() > costs.getOrDefault(key(current.pose()), Double.POSITIVE_INFINITY) + 0.001) continue;
            used++;
            expanded++;
            if (distance(current.pose(), approach) < 3.0 && Math.abs(wrap(current.pose().heading() - target.heading())) < Math.toRadians(24)
                && segmentClear(current.pose(), approach) && segmentClear(approach, new Pose(target.x(), target.z(), target.heading()))) {
                ArrayList<Pose> result = new ArrayList<>();
                for (Node n = current; n != null; n = n.parent()) result.add(n.pose());
                Collections.reverse(result);
                result.add(approach);
                result.add(new Pose(target.x(), target.z(), target.heading()));
                path = List.copyOf(result);
                done = true;
                break;
            }
            if (expanded >= MAX_NODES) { done = true; break; }
            for (int turn = -1; turn <= 1; turn++) {
                double heading = current.pose().heading() + turn * TURN;
                double mid = current.pose().heading() + turn * TURN * 0.5;
                Pose next = new Pose(current.pose().x() + STEP * Math.cos(mid), current.pose().z() + STEP * Math.sin(mid), heading);
                if (distance(next, start) > 220 || !arcClear(current.pose(), turn)) continue;
                double cost = current.cost() + STEP + Math.abs(turn) * 0.6;
                Key key = key(next);
                if (cost + 0.001 >= costs.getOrDefault(key, Double.POSITIVE_INFINITY)) continue;
                costs.put(key, cost);
                open.add(new Node(next, cost, cost + distance(next, approach), current));
            }
        }
        if (open.isEmpty()) done = true;
        return used;
    }

    private boolean arcClear(Pose from, int turn) {
        for (int part = 1; part <= 4; part++) {
            double fraction = part / 4.0;
            double mid = from.heading() + turn * TURN * fraction * 0.5;
            if (!road.clear(from.x() + STEP * fraction * Math.cos(mid), from.z() + STEP * fraction * Math.sin(mid),
                from.heading() + turn * TURN * fraction)) return false;
        }
        return true;
    }

    private boolean segmentClear(Pose from, Pose to) {
        int count = Math.max(1, (int) Math.ceil(distance(from, to)));
        for (int i = 1; i <= count; i++) {
            double t = i / (double) count;
            if (!road.clear(from.x() + (to.x() - from.x()) * t, from.z() + (to.z() - from.z()) * t,
                from.heading() + wrap(to.heading() - from.heading()) * t)) return false;
        }
        return true;
    }

    public boolean done() { return done; }
    public List<Pose> path() { return path; }
    private Key key(Pose p) {
        return new Key((int) Math.round((p.x() - start.x()) / 2.0), (int) Math.round((p.z() - start.z()) / 2.0),
            Math.floorMod((int) Math.round(p.heading() / TURN), 16));
    }
    private static double distance(Pose a, Pose b) { return Math.hypot(a.x() - b.x(), a.z() - b.z()); }
    private static double wrap(double angle) { return Math.atan2(Math.sin(angle), Math.cos(angle)); }
}
