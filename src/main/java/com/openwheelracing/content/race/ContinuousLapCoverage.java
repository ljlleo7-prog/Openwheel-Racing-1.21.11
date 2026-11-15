package com.openwheelracing.content.race;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Union of continuous surveyed-distance intervals. Reverse travel never inflates coverage. */
public final class ContinuousLapCoverage {
    public static final double REQUIRED_COVERAGE = 0.90;
    private final double length;
    private final double finishDistance;
    private final List<Interval> intervals = new ArrayList<>();
    private Sample previous;
    private double covered;

    public ContinuousLapCoverage(double length, double finishDistance) {
        if (!Double.isFinite(length) || length <= 0 || !Double.isFinite(finishDistance)) throw new IllegalArgumentException("Invalid route");
        this.length = length;
        this.finishDistance = wrap(finishDistance);
    }

    public record Interval(double start, double end) {}
    public record Sample(double distance, double tick, double x, double y, double z, boolean reliable) {}
    public record Crossing(double tick, double fraction, double coverage) {}

    public double coverage() { return covered / length; }
    public List<Interval> intervals() { return List.copyOf(intervals); }
    public void reset() { intervals.clear(); covered = 0; previous = null; }

    /** The observed geometric crossing proves the short departure prefix of the new lap. */
    public void restartAtFinish(Sample sample) {
        reset();
        if (!finite(sample)) return;
        previous = sample;
        double departure = wrap(sample.distance() - finishDistance);
        if (sample.reliable() && departure <= Math.min(80, length * 0.1)) add(0, departure);
    }

    public Optional<Crossing> sample(Sample sample) {
        if (!finite(sample)) { previous = null; return Optional.empty(); }
        Sample before = previous;
        previous = sample;
        if (before == null) return Optional.empty();
        double ticks = sample.tick() - before.tick();
        double movement = Math.sqrt(square(sample.x() - before.x()) + square(sample.y() - before.y()) + square(sample.z() - before.z()));
        if (ticks < 0 || movement > Math.min(80, Math.max(12, ticks * 8))) {
            // A discontinuity cannot carry an earlier lap's coverage into a rescue/teleport.
            intervals.clear(); covered = 0;
            return Optional.empty();
        }
        if (ticks > 10 || !sample.reliable() || !before.reliable()) return Optional.empty();
        double a = wrap(before.distance() - finishDistance);
        double b = wrap(sample.distance() - finishDistance);
        double delta = b - a;
        if (delta > length / 2) delta -= length;
        if (delta < -length / 2) delta += length;
        if (Math.abs(delta) > Math.min(length / 4, Math.min(80, movement * 2 + 1))) return Optional.empty();
        if (delta > 0 && a + delta >= length) {
            add(a, length);
            double fraction = (length - a) / delta;
            double atFinish = coverage();
            if (a >= length - Math.min(80, length * 0.1) && b <= Math.min(80, length * 0.1)
                && atFinish + 1e-9 >= REQUIRED_COVERAGE) {
                intervals.clear(); covered = 0;
                add(0, b);
                return Optional.of(new Crossing(before.tick() + ticks * fraction, fraction, atFinish));
            }
            add(0, b);
        } else if (delta < 0 && a + delta < 0) {
            add(0, a);
            add(b, length);
        } else {
            add(Math.min(a, a + delta), Math.max(a, a + delta));
        }
        return Optional.empty();
    }

    private void add(double start, double end) {
        if (end - start <= 1e-9) return;
        int first = 0;
        while (first < intervals.size() && intervals.get(first).end() < start - 1e-9) first++;
        int last = first;
        while (last < intervals.size() && intervals.get(last).start() <= end + 1e-9) {
            Interval old = intervals.get(last++);
            start = Math.min(start, old.start()); end = Math.max(end, old.end());
            covered -= old.end() - old.start();
        }
        intervals.subList(first, last).clear();
        intervals.add(first, new Interval(start, end));
        covered += end - start;
    }

    private static boolean finite(Sample s) {
        return Double.isFinite(s.distance()) && Double.isFinite(s.tick()) && Double.isFinite(s.x()) && Double.isFinite(s.y()) && Double.isFinite(s.z());
    }
    private static double square(double x) { return x * x; }
    private double wrap(double distance) { return (distance % length + length) % length; }
}
