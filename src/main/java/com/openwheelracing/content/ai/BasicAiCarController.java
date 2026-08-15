package com.openwheelracing.content.ai;

import com.openwheelracing.content.track.TrackGeometry;
import com.openwheelracing.content.track.survey.SurveyRouteGeometry;
import com.openwheelracing.content.track.survey.SurveyRouteLocalizer;
import com.openwheelracing.content.track.survey.SurveyRouteModel;

public final class BasicAiCarController {
    public static final double MIN_TARGET_SPEED_MPS = 5.0;
    public static final double MAX_TARGET_SPEED_MPS = 55.0;
    public static final double MAX_LATERAL_ACCELERATION = 12.0;
    public static final double COMFORTABLE_DECELERATION = 7.0;
    public static final double MAX_SPACING_RANGE = 45.0;
    private static final double WHEELBASE = 3.60;
    private static final double[] CURVATURE_DISTANCES = {12.0, 24.0, 40.0, 65.0, 95.0, 135.0, 180.0, 240.0};
    private static final double CURVATURE_WINDOW = 8.0;
    private static final double STEERING_RATE_PER_TICK = 0.045;
    private static final double LATERAL_DEADBAND = 1.5;
    private static final double LATERAL_RECOVERY_GAIN = 0.018;
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
            double confidenceFactor = localization.status() == SurveyRouteLocalizer.Status.TRACKED ? 1.0 : 0.55;
            double lookahead = clamp(10.0 + input.speedMetersPerSecond() * 0.80, 10.0, 42.0);
            if (localization.status() != SurveyRouteLocalizer.Status.TRACKED) {
                lookahead = Math.max(14.0, lookahead);
            }
            previousSteering = steeringCommand(input.route(), routeDistance, input.position(), input.headingRadians(), candidate.signedLateralDistance(), lookahead, previousSteering);
            targetSpeed = targetSpeedMetersPerSecond(input.route(), routeDistance) * confidenceFactor;
            targetSpeed = applySpacing(targetSpeed, input.speedMetersPerSecond(), input.nearestAheadGap());
            BasicAiRouteSafety.Assessment safety = BasicAiRouteSafety.assess(candidate.signedLateralDistance(), 4.0, 4.0, candidate.signedLateralDistance(), 4.0, 4.0);
            if (input.obstacleAhead()) {
                targetSpeed = Math.min(targetSpeed, 5.0);
                previousSteering = approach(previousSteering, safety.recoveryDirection() * 0.35f, STEERING_RATE_PER_TICK);
            } else if (safety.state() == BasicAiRouteSafety.State.UNSAFE) {
                targetSpeed = Math.min(targetSpeed, 5.0);
                previousSteering = approach(previousSteering, safety.recoveryDirection() * 0.35f, STEERING_RATE_PER_TICK);
            } else if (safety.state() == BasicAiRouteSafety.State.RECOVERING) {
                targetSpeed = Math.min(targetSpeed, 12.0);
            }
            command = speedCommand(input.speedMetersPerSecond(), targetSpeed, previousSteering, input.nearestAheadGap());
        }

        BasicAiDriverIdentity identity = input.identity();
        status = new BasicAiStatus(identity.driverId(), identity.fleetId(), identity.trackId(), identity.gridIndex(), identity.displayName(), input.entityId(), true,
            localization.status(), localization.confidence(), routeDistance, routeLaps, runningTicks, input.speedMetersPerSecond() * 3.6,
            input.nearestAheadGap(), reason + (targetSpeed > 0.0 ? " target=" + Math.round(targetSpeed * 3.6) + "km/h" : ""));
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
        SurveyRouteSampler.Sample preview = SurveyRouteSampler.sample(route, routeDistance + lookahead);
        double toleratedOffset = clamp(signedLateralDistance, -LATERAL_DEADBAND, LATERAL_DEADBAND);
        double normalX = -Math.sin(preview.headingRadians());
        double normalZ = Math.cos(preview.headingRadians());
        SurveyRouteModel.Point target = new SurveyRouteModel.Point(
            preview.position().x() + normalX * toleratedOffset,
            preview.position().y(),
            preview.position().z() + normalZ * toleratedOffset
        );
        double targetBearing = Math.atan2(target.z() - position.z(), target.x() - position.x());
        double headingError = TrackGeometry.wrapRadians(targetBearing - headingRadians);
        double curvature = 2.0 * Math.sin(headingError) / Math.max(1.0, lookahead);
        double steer = Math.atan(WHEELBASE * curvature) / Math.toRadians(34.0);
        steer -= (signedLateralDistance - toleratedOffset) * LATERAL_RECOVERY_GAIN;
        float desired = (float) clamp(steer, -1.0, 1.0);
        return approach(previousSteering, desired, STEERING_RATE_PER_TICK);
    }

    public static double targetSpeedMetersPerSecond(SurveyRouteModel route, double routeDistance) {
        double allowedNow = MAX_TARGET_SPEED_MPS;
        for (double distance : CURVATURE_DISTANCES) {
            double curvature = SurveyRouteSampler.curvature(route, routeDistance + distance, CURVATURE_WINDOW);
            if (curvature <= 1.0E-5) {
                continue;
            }
            double cornerSpeed = clamp(Math.sqrt(MAX_LATERAL_ACCELERATION / curvature), MIN_TARGET_SPEED_MPS, MAX_TARGET_SPEED_MPS);
            double anticipated = Math.sqrt(cornerSpeed * cornerSpeed + 2.0 * COMFORTABLE_DECELERATION * distance);
            allowedNow = Math.min(allowedNow, anticipated);
        }
        return clamp(allowedNow, MIN_TARGET_SPEED_MPS, MAX_TARGET_SPEED_MPS);
    }

    public static double applySpacing(double targetSpeed, double currentSpeed, double nearestAheadGap) {
        if (!Double.isFinite(nearestAheadGap) || nearestAheadGap > MAX_SPACING_RANGE) {
            return targetSpeed;
        }
        double desiredGap = 8.0 + currentSpeed * 0.9;
        if (nearestAheadGap >= desiredGap) {
            return targetSpeed;
        }
        double factor = clamp((nearestAheadGap - 3.0) / Math.max(1.0, desiredGap - 3.0), 0.0, 1.0);
        return Math.min(targetSpeed, currentSpeed * factor);
    }

    public static BasicAiDriveCommand speedCommand(double currentSpeed, double targetSpeed, float steering, double nearestAheadGap) {
        if (Double.isFinite(nearestAheadGap) && nearestAheadGap < 5.0) {
            return new BasicAiDriveCommand(0.0f, (float) clamp((5.0 - nearestAheadGap) / 2.5 + 0.35, 0.35, 1.0), steering);
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
                        double headingRadians, double speedMetersPerSecond, double nearestAheadGap,
                        SurveyRouteLocalizer.Result localization, boolean obstacleAhead) {
    }

    private record UUIDPair(java.util.UUID routeId, java.util.UUID trackId) {
        boolean matches(java.util.UUID candidateRouteId, java.util.UUID candidateTrackId) {
            return routeId.equals(candidateRouteId) && trackId.equals(candidateTrackId);
        }
    }
}
