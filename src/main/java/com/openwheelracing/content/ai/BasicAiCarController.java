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
    private static final int MIN_LAP_TICKS = 100;

    private final SurveyRouteLocalizer.State localizerState = new SurveyRouteLocalizer.State();
    private final AiProgressEstimator progressEstimator = new AiProgressEstimator();
    private BasicAiStatus status;
    private UUIDPair routeIdentity;
    private double previousRouteDistance;
    private double accumulatedLapProgress;
    private int routeLaps;
    private long runningTicks;
    private long ticksSinceLap;
    private int lastCompletedLapTicks;
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
        boolean unambiguous = localization.status() == SurveyRouteLocalizer.Status.TRACKED && candidate != null;
        AiProgressEstimator.Estimate progress = progressEstimator.update(candidate == null ? Double.NaN : candidate.distanceAlongRoute(),
            unambiguous, input.route().length(), Math.max(0.0, input.longitudinalSpeedMetersPerSecond()), 0.05);
        double routeDistance = progress.routeDistance();
        double targetSpeed = 0.0;
        String reason = localization.reason();
        BasicAiDriveCommand command;

        if (progress.state() == AiProgressEstimator.State.LOST) {
            if (input.speedMetersPerSecond() < 0.5) {
                localizerState.reset();
                progressEstimator.reset();
            }
            previousSteering = approach(previousSteering, 0.0f, STEERING_RATE_PER_TICK);
            command = BasicAiDriveCommand.stopped(previousSteering);
            reason = "localization lost; controlled stop";
        } else {
            updateLapProgress(routeDistance, input.route().length());
            if (routeLaps > lastCompletedLapTicks) {
                lastCompletedLapTicks = routeLaps;
            }
            AiTrackSample planned = input.plan().sample(routeDistance);
            double lateralError = candidate == null ? 0.0 : candidate.signedLateralDistance() - planned.lateralOffset();
            double headingError = TrackGeometry.wrapRadians(planned.tangentRadians() - input.headingRadians());
            float routeSteering = deterministicSteering(planned.curvature(), lateralError, headingError,
                input.yawRateRadiansPerSecond(), input.speedMetersPerSecond());
            float desiredSteering = input.avoidance().threat()
                ? (float) clamp(routeSteering + input.avoidance().steeringBias(), -1.0, 1.0)
                : routeSteering;
            previousSteering = approach(previousSteering, desiredSteering, STEERING_RATE_PER_TICK);
            targetSpeed = planned.targetSpeedMetersPerSecond();
            double envelope = clamp(1.0 - Math.abs(lateralError) * 0.10 - Math.abs(headingError) * 0.45, 0.35, 1.0);
            targetSpeed = Math.min(targetSpeed * envelope, input.mode().speedCapMetersPerSecond());
            double supervisedSpeed = supervisedTargetSpeed(input.plan(), routeDistance, input.speedMetersPerSecond(), input.grip());
            targetSpeed = Math.min(targetSpeed, supervisedSpeed);
            targetSpeed = applyQueueing(targetSpeed, input.speedMetersPerSecond(), input.queueGap(), input.mode());
            double nextSpeed = supervisedTargetSpeed(input.plan(), routeDistance + input.plan().spacing(),
                input.speedMetersPerSecond(), input.grip());
            double accelerationFeedForward = (nextSpeed * nextSpeed - supervisedSpeed * supervisedSpeed) / (2.0 * input.plan().spacing());
            command = speedCommand(input.speedMetersPerSecond(), targetSpeed, accelerationFeedForward, previousSteering,
                input.avoidance(), input.grip());
        }

        BasicAiDriverIdentity identity = input.identity();
        status = new BasicAiStatus(identity.driverId(), identity.fleetId(), identity.trackId(), identity.gridIndex(), identity.displayName(), input.entityId(), true,
            localization.status(), localization.confidence(), routeDistance, routeLaps, runningTicks, input.speedMetersPerSecond() * 3.6,
            input.queueGap(), reason + " progress=" + progress.state().name().toLowerCase(java.util.Locale.ROOT)
                + " source=" + input.plan().referenceSource().name().toLowerCase(java.util.Locale.ROOT)
                + " mode=" + input.mode().name().toLowerCase(java.util.Locale.ROOT)
                + (input.avoidance().threat() ? " avoidance" : "")
                + (targetSpeed > 0.0 ? " target=" + Math.round(targetSpeed * 3.6) + "km/h" : ""));
        previousRouteDistance = routeDistance;
        return command;
    }

    public int completedLapTicks() {
        return lastCompletedLapTicks;
    }

    public void stop(BasicAiDriverIdentity identity, int entityId, double speedKmh, String reason) {
        status = BasicAiStatus.stopped(identity, entityId, speedKmh, reason);
        previousSteering = 0.0f;
    }

    public void resetLocalization() {
        localizerState.reset();
        progressEstimator.reset();
    }

    public static float steeringCommand(SurveyRouteModel route, double routeDistance, SurveyRouteModel.Point position, double headingRadians,
                                        double signedLateralDistance, double lookahead, float previousSteering) {
        return approach(previousSteering, desiredSteering(route, routeDistance, position, headingRadians, signedLateralDistance, lookahead, 0.0, 0.0),
            STEERING_RATE_PER_TICK);
    }

    static float desiredSteering(SurveyRouteModel route, double routeDistance, SurveyRouteModel.Point position, double headingRadians,
                                 double signedLateralDistance, double lookahead, double lineOffsetDelta, double lineHeadingResidual) {
        SurveyRouteSampler.Sample preview = SurveyRouteSampler.sample(route, routeDistance + lookahead);
        double offsetGradientHeading = Math.atan2(lineOffsetDelta, Math.max(1.0, lookahead));
        double targetHeading = preview.headingRadians() + lineHeadingResidual + offsetGradientHeading;
        double headingError = TrackGeometry.wrapRadians(targetHeading - headingRadians);
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
        BasicAiGripModel.State nominal = BasicAiGripModel.build(new BasicAiGripModel.Input(95, 88, 104, 0, 0, 1, 1, 1, 1, 1, 1, 1, 1, 1));
        return speedCommand(currentSpeed, targetSpeed, 0.0, steering, avoidance, nominal);
    }

    static BasicAiDriveCommand speedCommand(double currentSpeed, double targetSpeed, double accelerationFeedForward, float steering,
                                             BasicAiNearbyAvoidance.Decision avoidance, BasicAiGripModel.State capability) {
        if (avoidance.threat() && avoidance.brake() > 0.0) {
            return new BasicAiDriveCommand(0.0f, (float) avoidance.brake(), steering);
        }
        double error = targetSpeed - currentSpeed;
        double requestedAcceleration = accelerationFeedForward + error * 0.75;
        if (requestedAcceleration >= 0.05) {
            float throttle = (float) clamp(requestedAcceleration / Math.max(0.25, capability.driveAcceleration(currentSpeed)), 0.0, 1.0);
            return new BasicAiDriveCommand(throttle, 0.0f, steering);
        }
        if (requestedAcceleration <= -0.05) {
            float brake = (float) clamp(-requestedAcceleration / Math.max(0.25, capability.brakeAcceleration(currentSpeed)), 0.0, 1.0);
            return new BasicAiDriveCommand(0.0f, brake, steering);
        }
        return new BasicAiDriveCommand(0.0f, 0.0f, steering);
    }

    static float deterministicSteering(double curvature, double lateralError, double headingError, double yawRate, double speed) {
        double feedForward = Math.atan(WHEELBASE * curvature);
        double stanley = Math.atan2(-1.25 * lateralError, Math.max(3.0, speed));
        double desired = (feedForward + 1.15 * headingError + stanley - 0.08 * yawRate) / Math.toRadians(34.0);
        return (float) clamp(desired, -1.0, 1.0);
    }

    static double supervisedTargetSpeed(AiTrackPlan plan, double routeDistance, double currentSpeed,
                                        BasicAiGripModel.State capability) {
        double target = plan.sample(routeDistance).targetSpeedMetersPerSecond();
        double previewDistance = Math.min(320.0, plan.routeLength() * 0.5);
        double reactionDistance = Math.max(4.0, currentSpeed * 0.55);
        double conservativeBrake = Math.max(1.0, capability.brakeAcceleration(currentSpeed) * 0.45);
        for (double ahead = plan.spacing(); ahead <= previewDistance; ahead += plan.spacing()) {
            double futureSpeed = plan.sample(routeDistance + ahead).targetSpeedMetersPerSecond();
            double usableDistance = Math.max(0.0, ahead - reactionDistance);
            double stoppingLimit = Math.sqrt(futureSpeed * futureSpeed + 2.0 * conservativeBrake * usableDistance);
            target = Math.min(target, stoppingLimit);
        }
        return target;
    }

    private void resetRoute(SurveyRouteModel route) {
        routeIdentity = new UUIDPair(route.routeId(), route.trackId());
        localizerState.reset();
        previousRouteDistance = 0.0;
        accumulatedLapProgress = 0.0;
        routeLaps = 0;
        runningTicks = 0L;
        ticksSinceLap = 0L;
        previousSteering = 0.0f;
        progressEstimator.reset();
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
                        double headingRadians, double speedMetersPerSecond, double longitudinalSpeedMetersPerSecond,
                        double yawRateRadiansPerSecond, SurveyRouteLocalizer.Result localization,
                        BasicAiNearbyAvoidance.Decision avoidance, BasicAiTrafficMode mode, BasicAiGripModel.State grip,
                        AiTrackPlan plan, double queueGap) {
    }

    private record UUIDPair(java.util.UUID routeId, java.util.UUID trackId) {
        boolean matches(java.util.UUID candidateRouteId, java.util.UUID candidateTrackId) {
            return routeId.equals(candidateRouteId) && trackId.equals(candidateTrackId);
        }
    }
}
