package com.openwheelracing.content.ai;

import com.openwheelracing.content.track.TrackGeometry;
import com.openwheelracing.content.track.survey.SurveyRouteGeometry;
import com.openwheelracing.content.track.survey.SurveyRouteLocalizer;
import com.openwheelracing.content.track.survey.SurveyRouteModel;

public final class BasicAiCarController {
    public static final double MIN_TARGET_SPEED_MPS = 5.0;
    public static final double MAX_TARGET_SPEED_MPS = 95.0;
    public static final double MAX_SPACING_RANGE = 45.0;
    private static final double WHEELBASE = 3.60;
    private static final double STEERING_RATE_PER_TICK = 0.08;
    private static final int AMBIGUOUS_STOP_TICKS = 40;
    private static final int MIN_LAP_TICKS = 100;

    private final SurveyRouteLocalizer.State localizerState = new SurveyRouteLocalizer.State();
    private BasicAiStatus status;
    private UUIDPair routeIdentity;
    private double previousRouteDistance;
    private double accumulatedLapProgress;
    private int routeLaps;
    private long runningTicks;
    private long ticksSinceLap;
    private int ambiguousTicks;
    private float previousSteering;
    private double cachedTargetSpeed;
    private BasicAiTrafficMode cachedMode;

    public BasicAiStatus status() {
        return status;
    }

    public SurveyRouteLocalizer.Result localize(SurveyRouteModel route, SurveyRouteModel.Point position, double headingRadians) {
        if (routeIdentity == null || !routeIdentity.matches(route.routeId(), route.trackId())) {
            resetRoute(route);
        }
        return SurveyRouteLocalizer.locate(route, position, headingRadians, localizerState);
    }

    public BasicAiDriveCommand tick(Input input) {
        if (routeIdentity == null || !routeIdentity.matches(input.route().routeId(), input.route().trackId())) {
            resetRoute(input.route());
        }
        runningTicks++;
        ticksSinceLap++;
        SurveyRouteLocalizer.Result localization = input.localization();
        SurveyRouteGeometry.Candidate candidate = localization.best().orElse(null);
        double routeDistance = candidate == null ? previousRouteDistance : candidate.distanceAlongRoute();
        double targetSpeed = 0.0;
        String reason = localization.reason();
        BasicAiDriveCommand command;

        if (localization.status() == SurveyRouteLocalizer.Status.UNTRACKED || candidate == null) {
            ambiguousTicks = 0;
            if (input.speedMetersPerSecond() < 0.5) {
                localizerState.reset();
            }
            previousSteering = approach(previousSteering, 0.0f, STEERING_RATE_PER_TICK);
            command = BasicAiDriveCommand.stopped(previousSteering);
        } else if (localization.status() == SurveyRouteLocalizer.Status.AMBIGUOUS && ++ambiguousTicks > AMBIGUOUS_STOP_TICKS) {
            previousSteering = approach(previousSteering, 0.0f, STEERING_RATE_PER_TICK);
            command = BasicAiDriveCommand.stopped(previousSteering);
            if (input.speedMetersPerSecond() < 0.5) {
                localizerState.reset();
                ambiguousTicks = 0;
            }
            reason = "persistent ambiguity";
        } else {
            if (localization.status() != SurveyRouteLocalizer.Status.AMBIGUOUS) {
                ambiguousTicks = 0;
                updateLapProgress(routeDistance, input.route().length());
            }
            double lookahead = clamp(7.0 + input.speedMetersPerSecond() * 0.65, 7.0, 32.0);
            if (localization.status() != SurveyRouteLocalizer.Status.TRACKED) {
                lookahead = Math.max(14.0, lookahead);
            }
            float routeSteering = desiredSteering(input.route(), routeDistance, input.position(), input.headingRadians(),
                candidate.signedLateralDistance(), lookahead);
            float desiredSteering = input.avoidance().threat()
                ? (float) clamp(routeSteering + input.avoidance().steeringBias(), -1.0, 1.0)
                : routeSteering;
            previousSteering = approach(previousSteering, desiredSteering, STEERING_RATE_PER_TICK);
            if (cachedMode != input.mode() || (runningTicks + input.entityId()) % 3L == 0L || !(cachedTargetSpeed > 0.0)) {
                cachedTargetSpeed = BasicAiSpeedPlanner.targetSpeed(input.route(), routeDistance, input.grip(), input.mode());
                cachedMode = input.mode();
            }
            targetSpeed = cachedTargetSpeed;
            targetSpeed = applyQueueing(targetSpeed, input.speedMetersPerSecond(), input.queueGap(), input.mode());
            command = speedCommand(input.speedMetersPerSecond(), targetSpeed, previousSteering, input.avoidance());
        }

        BasicAiDriverIdentity identity = input.identity();
        status = new BasicAiStatus(identity.driverId(), identity.fleetId(), identity.trackId(), identity.gridIndex(), identity.displayName(), input.entityId(), true,
            localization.status(), localization.confidence(), routeDistance, routeLaps, runningTicks, input.speedMetersPerSecond() * 3.6,
            input.queueGap(), reason + " mode=" + input.mode().name().toLowerCase(java.util.Locale.ROOT)
                + (input.avoidance().threat() ? " avoidance" : "")
                + (targetSpeed > 0.0 ? " target=" + Math.round(targetSpeed * 3.6) + "km/h" : ""));
        previousRouteDistance = routeDistance;
        return command;
    }

    public void stop(BasicAiDriverIdentity identity, int entityId, double speedKmh, String reason) {
        status = BasicAiStatus.stopped(identity, entityId, speedKmh, reason);
        previousSteering = 0.0f;
    }

    public void resetLocalization() {
        localizerState.reset();
        ambiguousTicks = 0;
    }

    public static float steeringCommand(SurveyRouteModel route, double routeDistance, SurveyRouteModel.Point position, double headingRadians,
                                        double signedLateralDistance, double lookahead, float previousSteering) {
        return approach(previousSteering, desiredSteering(route, routeDistance, position, headingRadians, signedLateralDistance, lookahead),
            STEERING_RATE_PER_TICK);
    }

    static float desiredSteering(SurveyRouteModel route, double routeDistance, SurveyRouteModel.Point position, double headingRadians,
                                 double signedLateralDistance, double lookahead) {
        SurveyRouteSampler.Sample preview = SurveyRouteSampler.sample(route, routeDistance + lookahead);
        double headingError = TrackGeometry.wrapRadians(preview.headingRadians() - headingRadians);
        double headingCurvature = 2.0 * Math.sin(headingError) / Math.max(1.0, lookahead);
        double lateralCurvature = -Math.atan2(signedLateralDistance, Math.max(8.0, lookahead)) / Math.max(1.0, lookahead);
        double steer = Math.atan(WHEELBASE * (headingCurvature + lateralCurvature)) / Math.toRadians(34.0);
        return (float) clamp(steer, -1.0, 1.0);
    }

    public static double applyQueueing(double targetSpeed, double currentSpeed, double gap, BasicAiTrafficMode mode) {
        if (!mode.queueing() || !Double.isFinite(gap)) {
            return targetSpeed;
        }
        double desiredGap = 8.0 + currentSpeed * 0.8;
        if (gap >= desiredGap) {
            return targetSpeed;
        }
        double factor = clamp((gap - 3.0) / Math.max(1.0, desiredGap - 3.0), 0.0, 1.0);
        return Math.min(targetSpeed, currentSpeed * factor);
    }

    public static BasicAiDriveCommand speedCommand(double currentSpeed, double targetSpeed, float steering,
                                                    BasicAiNearbyAvoidance.Decision avoidance) {
        if (avoidance.threat() && avoidance.brake() > 0.0) {
            return new BasicAiDriveCommand(0.0f, (float) avoidance.brake(), steering);
        }
        double error = targetSpeed - currentSpeed;
        if (error > 0.8) {
            return new BasicAiDriveCommand(1.0f, 0.0f, steering);
        }
        if (error < -0.8) {
            return new BasicAiDriveCommand(0.0f, (float) clamp(-error / 8.0, 0.12, 1.0), steering);
        }
        return new BasicAiDriveCommand(0.0f, 0.0f, steering);
    }

    private void resetRoute(SurveyRouteModel route) {
        routeIdentity = new UUIDPair(route.routeId(), route.trackId());
        localizerState.reset();
        previousRouteDistance = 0.0;
        accumulatedLapProgress = 0.0;
        routeLaps = 0;
        runningTicks = 0L;
        ticksSinceLap = 0L;
        ambiguousTicks = 0;
        previousSteering = 0.0f;
        cachedTargetSpeed = 0.0;
        cachedMode = null;
    }

    private void updateLapProgress(double routeDistance, double routeLength) {
        if (!(routeLength > 0.0)) {
            return;
        }
        double forward = SurveyRouteSampler.forwardDelta(previousRouteDistance, routeDistance, routeLength);
        if (forward <= 0.0 || forward > Math.min(80.0, routeLength * 0.25)) {
            return;
        }
        accumulatedLapProgress += forward;
        if (accumulatedLapProgress >= routeLength && ticksSinceLap >= MIN_LAP_TICKS) {
            routeLaps++;
            accumulatedLapProgress -= routeLength;
            ticksSinceLap = 0L;
        }
    }

    private static float approach(float value, float target, double maximumDelta) {
        double delta = clamp(target - value, -maximumDelta, maximumDelta);
        return (float) (value + delta);
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    public record Input(BasicAiDriverIdentity identity, int entityId, SurveyRouteModel route, SurveyRouteModel.Point position,
                        double headingRadians, double speedMetersPerSecond, SurveyRouteLocalizer.Result localization,
                        BasicAiNearbyAvoidance.Decision avoidance, BasicAiTrafficMode mode, BasicAiGripModel.State grip,
                        double queueGap) {
    }

    private record UUIDPair(java.util.UUID routeId, java.util.UUID trackId) {
        boolean matches(java.util.UUID candidateRouteId, java.util.UUID candidateTrackId) {
            return routeId.equals(candidateRouteId) && trackId.equals(candidateTrackId);
        }
    }
}
