package com.openwheelracing.content.race.timing;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class LiveRaceClassificationEngine {
    public static final double DEFAULT_REFERENCE_SPACING_METERS = 4.0;
    public static final double DEFAULT_SWAP_MARGIN_METERS = 1.5;
    public static final int DEFAULT_SWAP_CONFIRMATION_TICKS = 4;
    public static final int MAX_REFERENCE_HISTORY = 2048;
    public static final int MAX_POSITION_CHANGES = 32;
    private static final double MAX_ACCEPTED_FORWARD_METERS = 80.0;
    private static final double SEAM_ZONE = 0.25;
    private static final long STALE_AFTER_MILLIS = 5_000L;
    private static final long MISSING_RETIREMENT_TICKS = 6_000L;

    private final double referenceSpacingMeters;
    private final double swapMarginMeters;
    private final int swapConfirmationTicks;
    private final Map<RaceParticipantKey, ParticipantState> participants = new HashMap<>();
    private final Deque<RacePositionChange> positionChanges = new ArrayDeque<>();
    private List<RaceParticipantKey> stableOrder = List.of();
    private List<RaceParticipantKey> pendingOrder = List.of();
    private int pendingOrderTicks;
    private long revision;
    private double routeLengthMeters;
    private java.util.UUID trackId = new java.util.UUID(0L, 0L);
    private java.util.UUID routeId = new java.util.UUID(0L, 0L);

    public LiveRaceClassificationEngine() {
        this(DEFAULT_REFERENCE_SPACING_METERS, DEFAULT_SWAP_MARGIN_METERS, DEFAULT_SWAP_CONFIRMATION_TICKS);
    }

    public LiveRaceClassificationEngine(double referenceSpacingMeters, double swapMarginMeters, int swapConfirmationTicks) {
        if (!(referenceSpacingMeters > 0.0) || !(swapMarginMeters >= 0.0) || swapConfirmationTicks < 1) {
            throw new IllegalArgumentException("Invalid race timing parameters");
        }
        this.referenceSpacingMeters = referenceSpacingMeters;
        this.swapMarginMeters = swapMarginMeters;
        this.swapConfirmationTicks = swapConfirmationTicks;
    }

    public LiveRaceTimingSnapshot advance(double routeLengthMeters, long serverTick, long preciseMillis, List<RaceTimingObservation> observations) {
        if (!(routeLengthMeters > 0.0) || !Double.isFinite(routeLengthMeters)) {
            throw new IllegalArgumentException("Route length must be positive and finite");
        }
        if (this.routeLengthMeters != routeLengthMeters) {
            reset(routeLengthMeters);
        }
        Map<RaceParticipantKey, RaceTimingObservation> current = new HashMap<>();
        for (RaceTimingObservation observation : observations) {
            if (observation.participant() == null) {
                continue;
            }
            current.put(observation.participant(), observation);
        }
        for (ParticipantState state : participants.values()) {
            if (!current.containsKey(state.key)) {
                state.markMissing(serverTick);
            }
        }
        participants.entrySet().removeIf(entry -> entry.getValue().missingSinceTick >= 0L
            && serverTick - entry.getValue().missingSinceTick > MISSING_RETIREMENT_TICKS);
        for (RaceTimingObservation observation : current.values()) {
            ParticipantState state = participants.computeIfAbsent(observation.participant(), key -> new ParticipantState(key));
            state.observe(observation, routeLengthMeters, preciseMillis, referenceSpacingMeters);
        }

        List<RaceParticipantKey> rawOrder = participants.values().stream()
            .sorted(rawComparator())
            .map(state -> state.key)
            .toList();
        if (!new HashSet<>(stableOrder).equals(new HashSet<>(rawOrder))) {
            stableOrder = rawOrder;
            pendingOrder = List.of();
            pendingOrderTicks = 0;
            for (int index = 0; index < stableOrder.size(); index++) {
                ParticipantState state = participants.get(stableOrder.get(index));
                if (state != null) {
                    state.lastPosition = index + 1;
                    state.lastPositionChange = 0;
                }
            }
        } else {
            updateStableOrder(rawOrder, current, serverTick);
        }
        revision++;
        return snapshot(serverTick, current);
    }

    public void reset(double routeLengthMeters) {
        this.routeLengthMeters = routeLengthMeters;
        participants.clear();
        stableOrder = List.of();
        pendingOrder = List.of();
        pendingOrderTicks = 0;
        positionChanges.clear();
        revision++;
    }

    public LiveRaceTimingSnapshot currentSnapshot(long serverTick) {
        return snapshot(serverTick, Map.of());
    }

    public void restore(double routeLengthMeters, List<RestoredProgress> restored) {
        reset(routeLengthMeters);
        List<RestoredProgress> ordered = restored.stream()
            .sorted(Comparator.comparingInt(RestoredProgress::stablePosition).thenComparing(progress -> progress.participant().id()))
            .toList();
        List<RaceParticipantKey> order = new ArrayList<>();
        for (RestoredProgress progress : ordered) {
            ParticipantState state = new ParticipantState(progress.participant());
            state.displayName = progress.displayName();
            state.completedLaps = Math.max(0, progress.completedLaps());
            state.routeDistanceMeters = ParticipantState.wrap(progress.routeDistanceMeters(), routeLengthMeters);
            state.absoluteProgressMeters = state.completedLaps * routeLengthMeters + state.routeDistanceMeters;
            state.previousAbsoluteProgressMeters = state.absoluteProgressMeters;
            state.latestReferenceIndex = (long) Math.floor(state.absoluteProgressMeters / referenceSpacingMeters);
            state.confidence = RaceProgressConfidence.STALE;
            state.initialized = true;
            state.lastPosition = Math.max(1, progress.stablePosition());
            participants.put(progress.participant(), state);
            order.add(progress.participant());
        }
        stableOrder = List.copyOf(order);
    }

    public record RestoredProgress(RaceParticipantKey participant, String displayName, int completedLaps,
                                   double routeDistanceMeters, int stablePosition) {
    }

    public void ensureRevisionAfter(long previousRevision) {
        revision = Math.max(revision, previousRevision + 1L);
    }

    public long revision() {
        return revision;
    }

    private void updateStableOrder(List<RaceParticipantKey> rawOrder, Map<RaceParticipantKey, RaceTimingObservation> observations, long serverTick) {
        if (stableOrder.isEmpty()) {
            stableOrder = rawOrder;
            pendingOrder = List.of();
            pendingOrderTicks = 0;
            return;
        }
        if (stableOrder.equals(rawOrder)) {
            pendingOrder = List.of();
            pendingOrderTicks = 0;
            return;
        }
        if (hasClearConfirmedChange(rawOrder, observations)) {
            applyOrder(rawOrder, serverTick);
            return;
        }
        if (!pendingOrder.equals(rawOrder)) {
            pendingOrder = List.copyOf(rawOrder);
            pendingOrderTicks = 1;
        } else {
            pendingOrderTicks++;
        }
        if (pendingOrderTicks >= swapConfirmationTicks) {
            applyOrder(rawOrder, serverTick);
        }
    }

    private boolean hasClearConfirmedChange(List<RaceParticipantKey> rawOrder, Map<RaceParticipantKey, RaceTimingObservation> observations) {
        for (int index = 0; index < rawOrder.size(); index++) {
            RaceParticipantKey key = rawOrder.get(index);
            int previousIndex = stableOrder.indexOf(key);
            if (previousIndex < 0 || previousIndex == index) {
                continue;
            }
            ParticipantState state = participants.get(key);
            RaceTimingObservation observation = observations.get(key);
            if (state != null && observation != null && observation.confidence().canInitiatePositionChange()) {
                RaceParticipantKey displaced = index < stableOrder.size() ? stableOrder.get(index) : null;
                ParticipantState other = displaced == null ? null : participants.get(displaced);
                if (other == null || state.absoluteProgressMeters - other.absoluteProgressMeters > swapMarginMeters) {
                    return true;
                }
            }
        }
        return false;
    }

    private void applyOrder(List<RaceParticipantKey> nextOrder, long serverTick) {
        Map<RaceParticipantKey, Integer> oldPositions = positions(stableOrder);
        stableOrder = List.copyOf(nextOrder);
        pendingOrder = List.of();
        pendingOrderTicks = 0;
        for (int index = 0; index < stableOrder.size(); index++) {
            RaceParticipantKey key = stableOrder.get(index);
            int oldPosition = oldPositions.getOrDefault(key, index + 1);
            int newPosition = index + 1;
            if (oldPosition != newPosition) {
                ParticipantState state = participants.get(key);
                if (state != null) {
                    state.lastPositionChange = oldPosition - newPosition;
                    state.lastPosition = newPosition;
                    positionChanges.addLast(new RacePositionChange(key, state.displayName, oldPosition, newPosition,
                        state.completedLaps, state.routeDistanceMeters, serverTick));
                }
            }
        }
        while (positionChanges.size() > MAX_POSITION_CHANGES) {
            positionChanges.removeFirst();
        }
    }

    private LiveRaceTimingSnapshot snapshot(long serverTick, Map<RaceParticipantKey, RaceTimingObservation> observations) {
        List<RaceTimingRow> rows = new ArrayList<>();
        Map<RaceParticipantKey, Integer> positions = positions(stableOrder);
        RaceParticipantKey leader = stableOrder.isEmpty() ? null : stableOrder.getFirst();
        ParticipantState leaderState = leader == null ? null : participants.get(leader);
        for (int index = 0; index < stableOrder.size(); index++) {
            RaceParticipantKey key = stableOrder.get(index);
            ParticipantState state = participants.get(key);
            if (state == null) {
                continue;
            }
            RaceParticipantKey ahead = index == 0 ? null : stableOrder.get(index - 1);
            ParticipantState aheadState = ahead == null ? null : participants.get(ahead);
            RaceGap gap = gapBetween(leaderState, state);
            RaceGap interval = gapBetween(aheadState, state);
            int change = state.lastPositionChange;
            rows.add(new RaceTimingRow(index + 1, key, state.displayName, state.entityId, state.completedLaps, state.routeDistanceMeters,
                state.absoluteProgressMeters, state.confidence, gap, interval, change));
            state.lastPosition = positions.getOrDefault(key, index + 1);
        }
        return new LiveRaceTimingSnapshot(true, "", 0L, "", stateTrackId(), stateRouteId(), revision, serverTick, routeLengthMeters,
            rows, List.copyOf(positionChanges));
    }

    private RaceGap gapBetween(ParticipantState ahead, ParticipantState behind) {
        if (ahead == null) {
            return RaceGap.leader();
        }
        double absoluteDifference = ahead.absoluteProgressMeters - behind.absoluteProgressMeters;
        int lapDifference = (int) Math.floor(Math.max(0.0, absoluteDifference) / routeLengthMeters);
        long reference = behind.latestReferenceIndex;
        if (lapDifference > 0) {
            return RaceGap.laps(lapDifference);
        }
        if (reference < 0) {
            return RaceGap.unavailable();
        }
        Long aheadMillis = ahead.referenceTimes.get(reference);
        Long behindMillis = behind.referenceTimes.get(reference);
        if (aheadMillis == null || behindMillis == null) {
            return RaceGap.unavailable();
        }
        return RaceGap.time(Math.max(0L, behindMillis - aheadMillis));
    }

    private UUID stateTrackId() {
        return participants.values().stream().findFirst().map(state -> state.trackId).orElse(new UUID(0L, 0L));
    }

    private UUID stateRouteId() {
        return participants.values().stream().findFirst().map(state -> state.routeId).orElse(new UUID(0L, 0L));
    }

    private Comparator<ParticipantState> rawComparator() {
        return Comparator.comparingDouble((ParticipantState state) -> state.absoluteProgressMeters).reversed()
            .thenComparingInt(state -> confidenceRank(state.confidence))
            .thenComparingInt(state -> state.initialOrderHint)
            .thenComparing(state -> state.key.id());
    }

    private static int confidenceRank(RaceProgressConfidence confidence) {
        return switch (confidence) {
            case CONFIRMED -> 0;
            case DEGRADED -> 1;
            case AMBIGUOUS -> 2;
            case UNTRACKED -> 3;
            case STALE -> 4;
        };
    }

    private static Map<RaceParticipantKey, Integer> positions(List<RaceParticipantKey> order) {
        Map<RaceParticipantKey, Integer> positions = new HashMap<>();
        for (int index = 0; index < order.size(); index++) {
            positions.put(order.get(index), index + 1);
        }
        return positions;
    }

    private static final class ParticipantState {
        private final RaceParticipantKey key;
        private final Map<Long, Long> referenceTimes = new HashMap<>();
        private String displayName = "";
        private int entityId;
        private int initialOrderHint = Integer.MAX_VALUE;
        private int completedLaps;
        private double routeDistanceMeters;
        private double absoluteProgressMeters;
        private double previousAbsoluteProgressMeters;
        private long previousMillis = Long.MIN_VALUE;
        private long lastAcceptedMillis;
        private long latestReferenceIndex = -1L;
        private RaceProgressConfidence confidence = RaceProgressConfidence.STALE;
        private int lastPosition;
        private int lastPositionChange;
        private boolean initialized;
        private long missingSinceTick = -1L;
        private UUID trackId = new UUID(0L, 0L);
        private UUID routeId = new UUID(0L, 0L);

        private ParticipantState(RaceParticipantKey key) {
            this.key = key;
        }

        private void observe(RaceTimingObservation observation, double routeLength, long fallbackMillis, double referenceSpacing) {
            missingSinceTick = -1L;
            displayName = observation.displayName();
            entityId = observation.entityId();
            initialOrderHint = observation.initialOrderHint();
            RaceProgressConfidence nextConfidence = observation.confidence();
            long now = observation.preciseMillis() == 0L ? fallbackMillis : observation.preciseMillis();
            double distance = wrap(observation.routeDistanceMeters(), routeLength);
            boolean plausible = true;
            if (initialized && nextConfidence.canInitiatePositionChange()) {
                double candidateAbsolute = completedLaps * routeLength + distance;
                double forward = candidateAbsolute - absoluteProgressMeters;
                if (forward < -routeLength * 0.5) {
                    candidateAbsolute += routeLength;
                    forward = candidateAbsolute - absoluteProgressMeters;
                }
                if (forward < -1.0 || forward > MAX_ACCEPTED_FORWARD_METERS) {
                    plausible = false;
                    nextConfidence = RaceProgressConfidence.DEGRADED;
                } else if (candidateAbsolute >= absoluteProgressMeters && routeDistanceMeters > routeLength * (1.0 - SEAM_ZONE)
                    && distance <= routeLength * SEAM_ZONE && forward > 0.0) {
                    completedLaps++;
                }
            }
            if (nextConfidence.canInitiatePositionChange() && plausible) {
                if (!initialized) {
                    initialized = true;
                    routeDistanceMeters = distance;
                    absoluteProgressMeters = distance;
                    previousAbsoluteProgressMeters = distance;
                    previousMillis = now;
                    lastAcceptedMillis = now;
                    latestReferenceIndex = (long) Math.floor(absoluteProgressMeters / referenceSpacing);
                } else {
                    double candidateAbsolute = completedLaps * routeLength + distance;
                    if (candidateAbsolute >= absoluteProgressMeters) {
                        previousAbsoluteProgressMeters = absoluteProgressMeters;
                        absoluteProgressMeters = candidateAbsolute;
                        routeDistanceMeters = distance;
                        latestReferenceIndex = (long) Math.floor(absoluteProgressMeters / referenceSpacing);
                        addReferenceCrossings(previousAbsoluteProgressMeters, absoluteProgressMeters, previousMillis, now, referenceSpacing);
                        previousMillis = now;
                        lastAcceptedMillis = now;
                    }
                }
            }
            confidence = nextConfidence == RaceProgressConfidence.AMBIGUOUS || nextConfidence == RaceProgressConfidence.UNTRACKED
                ? nextConfidence : (nextConfidence == RaceProgressConfidence.DEGRADED ? RaceProgressConfidence.DEGRADED : RaceProgressConfidence.CONFIRMED);
            if (lastAcceptedMillis > 0L && now - lastAcceptedMillis > STALE_AFTER_MILLIS) {
                confidence = RaceProgressConfidence.STALE;
            }
        }

        private void markMissing(long serverTick) {
            if (missingSinceTick < 0L) {
                missingSinceTick = serverTick;
            }
            confidence = RaceProgressConfidence.STALE;
        }

        private void addReferenceCrossings(double previous, double current, long previousMillis, long currentMillis, double spacing) {
            if (current <= previous || current - previous > MAX_ACCEPTED_FORWARD_METERS) {
                return;
            }
            long first = (long) Math.floor(previous / spacing) + 1L;
            long last = (long) Math.floor(current / spacing);
            for (long reference = first; reference <= last; reference++) {
                double fraction = (reference * spacing - previous) / (current - previous);
                long millis = previousMillis + Math.round((currentMillis - previousMillis) * fraction);
                referenceTimes.put(reference, millis);
            }
            long minimum = Math.max(0L, last - MAX_REFERENCE_HISTORY);
            referenceTimes.keySet().removeIf(reference -> reference < minimum);
        }

        private static double wrap(double distance, double length) {
            double wrapped = distance % length;
            return wrapped < 0.0 ? wrapped + length : wrapped;
        }
    }
}
