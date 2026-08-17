package com.openwheelracing.content.ai;

import java.util.Arrays;

public final class BasicAiRacingLineProfile {
    public static final int MAX_POINTS = 4096;
    public static final double MAX_OFFSET_METERS = 2.5;

    private final double spacing;
    private final short[] lateralOffsetCm;
    private final short[] headingResidualMilliRad;
    private final int[] sampleCount;

    public BasicAiRacingLineProfile(double spacing, short[] lateralOffsetCm, short[] headingResidualMilliRad, int[] sampleCount) {
        if (!(spacing > 0.0) || lateralOffsetCm.length == 0 || lateralOffsetCm.length > MAX_POINTS
            || lateralOffsetCm.length != headingResidualMilliRad.length || lateralOffsetCm.length != sampleCount.length) {
            throw new IllegalArgumentException("invalid racing line profile");
        }
        this.spacing = spacing;
        this.lateralOffsetCm = lateralOffsetCm.clone();
        this.headingResidualMilliRad = headingResidualMilliRad.clone();
        this.sampleCount = sampleCount.clone();
    }

    public static BasicAiRacingLineProfile fromProfile(double routeLength, int pointCount,
                                                       com.openwheelracing.content.race.OWRLapProfiles.BestLapProfile profile) {
        BasicAiRacingLineProfile empty = empty(routeLength, pointCount);
        double[] offsets = new double[empty.pointCount()];
        double[] headings = new double[empty.pointCount()];
        for (int index = 0; index < offsets.length; index++) {
            double distance = index * empty.spacing();
            offsets[index] = profile.lateralOffsetMeters(distance);
            headings[index] = profile.headingResidualRadians(distance);
        }
        return fromSamples(routeLength, pointCount, offsets, headings);
    }

    static BasicAiRacingLineProfile fromPrefix(double routeLength, int pointCount, OWRAiTrainingData.Prefix prefix) {
        BasicAiRacingLineProfile empty = empty(routeLength, pointCount);
        short[] offsets = new short[empty.pointCount()];
        short[] headings = new short[empty.pointCount()];
        int[] counts = new int[empty.pointCount()];
        for (int index = 0; index < offsets.length; index++) {
            double distance = index * empty.spacing();
            double relative = SurveyRouteSampler.forwardDelta(prefix.startDistance(), distance, routeLength);
            if (relative > prefix.distance() || prefix.spacing() <= 0.0 || prefix.offsets().length == 0) continue;
            double samplePosition = relative / prefix.spacing();
            int lower = Math.min((int) Math.floor(samplePosition), prefix.offsets().length - 1);
            int upper = Math.min(lower + 1, prefix.offsets().length - 1);
            boolean lowerObserved = prefix.observed().length == prefix.offsets().length && prefix.observed()[lower] > 0;
            boolean upperObserved = prefix.observed().length == prefix.offsets().length && prefix.observed()[upper] > 0;
            if (!lowerObserved || !upperObserved) continue;
            double fraction = samplePosition - Math.floor(samplePosition);
            double offset = prefix.offsets()[lower] + (prefix.offsets()[upper] - prefix.offsets()[lower]) * fraction;
            double heading = prefix.headings()[lower] + (prefix.headings()[upper] - prefix.headings()[lower]) * fraction;
            offsets[index] = (short) Math.round(clamp(offset / 100.0, -MAX_OFFSET_METERS, MAX_OFFSET_METERS) * 100.0);
            headings[index] = (short) Math.round(clamp(heading / 1000.0, -0.7, 0.7) * 1000.0);
            counts[index] = 1;
        }
        return new BasicAiRacingLineProfile(empty.spacing(), offsets, headings, counts);
    }

    static BasicAiRacingLineProfile fromSamples(double routeLength, int pointCount, double[] offsetSamples, double[] headingSamples) {
        BasicAiRacingLineProfile empty = empty(routeLength, pointCount);
        if (offsetSamples.length != empty.pointCount() || headingSamples.length != empty.pointCount()) {
            throw new IllegalArgumentException("invalid racing line samples");
        }
        short[] offsets = new short[empty.pointCount()];
        short[] headings = new short[empty.pointCount()];
        int[] counts = new int[empty.pointCount()];
        for (int index = 0; index < offsets.length; index++) {
            offsets[index] = (short) Math.round(clamp(offsetSamples[index], -MAX_OFFSET_METERS, MAX_OFFSET_METERS) * 100.0);
            headings[index] = (short) Math.round(clamp(headingSamples[index], -0.7, 0.7) * 1000.0);
            counts[index] = 1;
        }
        return new BasicAiRacingLineProfile(empty.spacing(), offsets, headings, counts);
    }

    public static BasicAiRacingLineProfile average(double routeLength, int pointCount, java.util.List<com.openwheelracing.content.race.OWRLapProfiles.BestLapProfile> profiles) {
        BasicAiRacingLineProfile result = empty(routeLength, pointCount);
        if (profiles.isEmpty()) return result;
        for (int index = 0; index < result.pointCount(); index++) {
            double distance = index * result.spacing();
            double offset = profiles.stream().mapToDouble(profile -> profile.lateralOffsetMeters(distance)).average().orElse(0.0);
            double heading = profiles.stream().mapToDouble(profile -> profile.headingResidualRadians(distance)).average().orElse(0.0);
            result = result.update(distance, offset, heading);
        }
        return result;
    }
    public static BasicAiRacingLineProfile empty(double routeLength, int pointCount) {
        int count = Math.max(1, Math.min(MAX_POINTS, pointCount));
        return new BasicAiRacingLineProfile(Math.max(0.25, routeLength / count), new short[count], new short[count], new int[count]);
    }

    public BasicAiRacingLineProfile update(double routeDistance, double offsetMeters, double headingResidualRadians) {
        int index = Math.floorMod((int) Math.floor(routeDistance / spacing), lateralOffsetCm.length);
        int samples = Math.min(32767, sampleCount[index] + 1);
        double oldOffset = lateralOffsetCm[index] / 100.0;
        double nextOffset = oldOffset + (clamp(offsetMeters, -MAX_OFFSET_METERS, MAX_OFFSET_METERS) - oldOffset) / samples;
        double oldHeading = headingResidualMilliRad[index] / 1000.0;
        double nextHeading = oldHeading + (clamp(headingResidualRadians, -0.7, 0.7) - oldHeading) / samples;
        short offset = (short) Math.round(clamp(nextOffset, -MAX_OFFSET_METERS, MAX_OFFSET_METERS) * 100.0);
        short heading = (short) Math.round(clamp(nextHeading, -0.7, 0.7) * 1000.0);
        short[] offsets = lateralOffsetCm.clone();
        short[] headings = headingResidualMilliRad.clone();
        int[] counts = sampleCount.clone();
        offsets[index] = offset;
        headings[index] = heading;
        counts[index] = samples;
        return new BasicAiRacingLineProfile(spacing, offsets, headings, counts).smooth();
    }

    public BasicAiRacingLineProfile smooth() {
        short[] offsets = lateralOffsetCm.clone();
        short[] headings = headingResidualMilliRad.clone();
        for (int index = 0; index < offsets.length; index++) {
            int previous = Math.floorMod(index - 1, offsets.length);
            int next = (index + 1) % offsets.length;
            if (sampleCount[index] == 0) {
                offsets[index] = (short) ((offsets[previous] + offsets[next]) / 2);
                headings[index] = (short) ((headings[previous] + headings[next]) / 2);
            } else {
                offsets[index] = (short) ((offsets[previous] + offsets[index] * 2 + offsets[next]) / 4);
                headings[index] = (short) ((headings[previous] + headings[index] * 2 + headings[next]) / 4);
            }
        }
        return new BasicAiRacingLineProfile(spacing, offsets, headings, sampleCount);
    }

    BasicAiRacingLineProfile withExploration(java.util.List<OWRAiTrainingData.Incident> incidents, double routeLength) {
        short[] offsets = lateralOffsetCm.clone();
        short[] headings = headingResidualMilliRad.clone();
        for (OWRAiTrainingData.Incident incident : incidents) {
            double center = incident.routeDistance();
            double start = center - 45.0;
            double end = center + 25.0;
            for (int index = 0; index < offsets.length; index++) {
                double distance = index * spacing;
                double ahead = SurveyRouteSampler.forwardDelta(start, distance, routeLength);
                if (ahead > 70.0) continue;
                double weight = ahead < 45.0 ? (ahead / 45.0) : (70.0 - ahead) / 25.0;
                weight = Math.max(0.0, Math.min(1.0, weight));
                double offset = offsets[index] / 100.0 + incident.explorationOffset() * weight;
                offsets[index] = (short) Math.round(clamp(offset, -MAX_OFFSET_METERS, MAX_OFFSET_METERS) * 100.0);
            }
        }
        return new BasicAiRacingLineProfile(spacing, offsets, headings, sampleCount);
    }

    public double offset(double routeDistance) {
        return interpolate(lateralOffsetCm, routeDistance) / 100.0;
    }

    public double headingResidual(double routeDistance) {
        return interpolate(headingResidualMilliRad, routeDistance) / 1000.0;
    }

    public int populatedPoints() {
        return (int) Arrays.stream(sampleCount).filter(value -> value > 0).count();
    }

    public int pointCount() { return lateralOffsetCm.length; }
    public double spacing() { return spacing; }
    public short[] lateralOffsetCm() { return lateralOffsetCm.clone(); }
    public short[] headingResidualMilliRad() { return headingResidualMilliRad.clone(); }
    public int[] sampleCount() { return sampleCount.clone(); }

    private double interpolate(short[] values, double distance) {
        double position = floorMod(distance / spacing, values.length);
        int lower = (int) Math.floor(position);
        int upper = (lower + 1) % values.length;
        double fraction = position - lower;
        return values[lower] + (values[upper] - values[lower]) * fraction;
    }

    private static double floorMod(double value, int modulus) {
        double result = value % modulus;
        return result < 0.0 ? result + modulus : result;
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
