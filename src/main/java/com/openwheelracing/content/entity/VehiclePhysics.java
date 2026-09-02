package com.openwheelracing.content.entity;

import com.openwheelracing.content.car.PrototypeCarSetup;

public final class VehiclePhysics {
    static final double BASE_GRIP_ENVELOPE = 1.06;
    static final double MIN_TRACTION_CONTROL_OUTPUT_FRACTION = 0.30;
    public static final double KINETIC_SCRUB_SPEED_THRESHOLD = 0.75;
    public static final double KMH_PER_BLOCK_PER_TICK = 72.0;
    public static final double SPEED_TO_BLOCKS_PER_TICK = 1.0 / KMH_PER_BLOCK_PER_TICK;
    public static final double PIT_SPEED_LIMIT_KMH = 79.0;
    public static final double PIT_SPEED_LIMIT_BLOCKS_PER_TICK = PIT_SPEED_LIMIT_KMH * SPEED_TO_BLOCKS_PER_TICK;
    public static final double ASPHALT_GRIP = 1.00;
    public static final double ASPHALT_DRAG = 0.997;
    public static final double PIT_LANE_GRIP = ASPHALT_GRIP;
    public static final double PIT_LANE_DRAG = ASPHALT_DRAG;
    public static final double NOMINAL_WHEEL_RADIUS_METERS = 0.33;
    public static final double PROTOTYPE_MECHANICAL_STEERING_LOCK_DEGREES = 34.0;

    public static final double TYRE_AMBIENT_TEMPERATURE_C = 33.0;
    public static final double TYRE_HEAT_CAPACITY_J_PER_C = 43_500.0;
    public static final double TYRE_SURFACE_HEAT_CAPACITY_J_PER_C = 20_000.0;
    public static final double TYRE_CARCASS_HEAT_CAPACITY_J_PER_C = TYRE_HEAT_CAPACITY_J_PER_C - TYRE_SURFACE_HEAT_CAPACITY_J_PER_C;
    public static final double TYRE_ROLLING_RESISTANCE_HEAT_FRACTION = 0.80;
    public static final double TYRE_SLIP_HEAT_FRACTION = 1.45;
    public static final double TYRE_STATIONARY_COOLING_PER_SECOND = 0.00240;
    public static final double TYRE_WIND_COOLING_PER_MPS_SECOND = 0.0000210;
    public static final double TYRE_HOT_COOLING_PER_SECOND = 0.000030;
    public static final double TYRE_HOT_COOLING_START_C = 110.0;
    public static final double TYRE_G_FORCE_HEAT_FACTOR = 0.61;
    public static final double TYRE_SURFACE_FRICTION_HEAT_FRACTION = 0.90;
    public static final double TYRE_CARCASS_FRICTION_HEAT_FRACTION = 1.0 - TYRE_SURFACE_FRICTION_HEAT_FRACTION;
    public static final double TYRE_BRAKE_TO_CARCASS_HEAT_FRACTION = 0.90;
    public static final double TYRE_CARCASS_TRANSFER_WATTS_PER_C = 600.0;
    public static final double TYRE_CARCASS_COOLING_FRACTION = 0.45;
    public static final double TYRE_EXPOSURE_ATTACK_PER_SECOND = 1.8;
    public static final double TYRE_EXPOSURE_RELEASE_PER_SECOND = 2.8;
    public static final double TYRE_EXPOSURE_TRANSFER_FLOOR = 0.05;

    private static final double REVERSE_TOP_SPEED_KMH = 60.0;
    private static final double[] GEAR_TOP_SPEEDS_KMH = {0.0, 100.0, 135.0, 170.0, 205.0, 245.0, 280.0, 320.0, 360.0};

    private static final double CAR_MASS_KG = 769.0;
    private static final double GRAVITY = 9.81;
    private static final double ASPHALT_MU_LONGITUDINAL = 2.475;
    private static final double MAX_BRAKE_FORCE = 40_000.0;

    private VehiclePhysics() {
    }

    public static boolean isNewerSequence(int candidate, int current) {
        return candidate != current && candidate - current > 0;
    }

    public static boolean exceedsSequenceGap(int candidate, int current, int allowedGap) {
        int gap = candidate - current;
        return gap > Math.max(0, allowedGap);
    }

    public static double speedKmhToBlocksPerTick(double speedKmh) {
        return speedKmh * SPEED_TO_BLOCKS_PER_TICK;
    }

    public static double gearTopSpeedKmh(int gear) {
        if (gear < 0) {
            return REVERSE_TOP_SPEED_KMH;
        }
        int clampedGear = Math.min(gear, GEAR_TOP_SPEEDS_KMH.length - 1);
        return GEAR_TOP_SPEEDS_KMH[clampedGear];
    }

    public static double gearTopSpeedKmh(int gear, PrototypeCarSetup setup) {
        return gearTopSpeedKmh(gear) * setup.topSpeedCoefficient();
    }

    public static double gearTopSpeedBlocksPerTick(int gear, PrototypeCarSetup setup) {
        return speedKmhToBlocksPerTick(gearTopSpeedKmh(gear, setup));
    }

    public static double brakingDistanceMeters(double initialSpeedMetersPerSecond, double surfaceGrip) {
        double normalLoad = CAR_MASS_KG * GRAVITY;
        double availableBrakeForce = ASPHALT_MU_LONGITUDINAL * surfaceGrip * normalLoad;
        double brakeAcceleration = Math.min(MAX_BRAKE_FORCE, availableBrakeForce) / CAR_MASS_KG;
        return initialSpeedMetersPerSecond * initialSpeedMetersPerSecond / (2.0 * brakeAcceleration);
    }

    static double combinedSlipScale(double normalizedDemand) {
        if (normalizedDemand <= 1.0) {
            return 1.0;
        }
        double excess = normalizedDemand - 1.0;
        double slidingRetention = 1.0 - 0.12 * (1.0 - Math.exp(-square(excess / 0.45)));
        return slidingRetention / normalizedDemand;
    }

    static PlanarForce opposingPlanarForce(double velocityLongitudinal, double velocityLateral, double magnitude) {
        double speed = Math.hypot(velocityLongitudinal, velocityLateral);
        if (speed <= 1.0E-9 || magnitude <= 0.0) {
            return new PlanarForce(0.0, 0.0);
        }
        double scale = -magnitude / speed;
        return new PlanarForce(velocityLongitudinal * scale, velocityLateral * scale);
    }

    static double tyreRelaxationGainForPatch(double wheelLongitudinalSpeed, double wheelLateralSpeed,
                                              double relaxationLength, double dtSeconds) {
        double patchSpeed = Math.hypot(wheelLongitudinalSpeed, wheelLateralSpeed);
        double timeConstant = relaxationLength / Math.max(1.0, patchSpeed);
        return 1.0 - Math.exp(-dtSeconds / timeConstant);
    }

    static WheelPatchVelocity wheelPatchVelocity(double velocityLongitudinal, double velocityLateral,
                                                  double yawRate, double localX, double localZ,
                                                  double steeringAngle) {
        double patchLateral = velocityLateral - yawRate * localZ;
        double patchLongitudinal = velocityLongitudinal + yawRate * localX;
        double cos = Math.cos(steeringAngle);
        double sin = Math.sin(steeringAngle);
        double wheelLongitudinal = patchLongitudinal * cos - patchLateral * sin;
        double wheelLateral = patchLongitudinal * sin + patchLateral * cos;
        return new WheelPatchVelocity(wheelLongitudinal, wheelLateral);
    }

    static PlanarForce wheelForceToBody(double wheelLongitudinalForce, double wheelLateralForce,
                                         double steeringAngle) {
        double cos = Math.cos(steeringAngle);
        double sin = Math.sin(steeringAngle);
        return new PlanarForce(
            wheelLongitudinalForce * cos + wheelLateralForce * sin,
            -wheelLongitudinalForce * sin + wheelLateralForce * cos);
    }

    static double minecraftYawMoment(double localX, double localZ,
                                     double bodyLongitudinalForce, double bodyLateralForce) {
        return localX * bodyLongitudinalForce - localZ * bodyLateralForce;
    }

    static BodyAcceleration minecraftBodyAcceleration(double velocityLongitudinal, double velocityLateral,
                                                       double yawRate, double forceLongitudinal,
                                                       double forceLateral, double massKg) {
        return new BodyAcceleration(
            forceLongitudinal / massKg - yawRate * velocityLateral,
            forceLateral / massKg + yawRate * velocityLongitudinal);
    }

    static double kinematicLongitudinalSlip(double wheelAngularSpeed, double wheelRadius,
                                            double patchLongitudinalSpeed) {
        double wheelSurfaceSpeed = wheelAngularSpeed * wheelRadius;
        return clamp((wheelSurfaceSpeed - patchLongitudinalSpeed)
            / Math.max(3.0, Math.abs(patchLongitudinalSpeed)), -1.8, 1.8);
    }

    public static WheelSpeedSynchronization wheelSpeedSynchronization(double wheelAngularSpeed,
                                                                       double wheelRadius,
                                                                       double patchLongitudinalSpeed) {
        return wheelSpeedSynchronization(wheelAngularSpeed, wheelRadius, patchLongitudinalSpeed, false);
    }

    public static WheelSpeedSynchronization wheelSpeedSynchronization(double wheelAngularSpeed,
                                                                       double wheelRadius,
                                                                       double patchLongitudinalSpeed,
                                                                       boolean capAtClassicTelemetryLimit) {
        double surfaceSpeed = wheelAngularSpeed * Math.max(0.0, wheelRadius);
        double reference = Math.max(3.0, Math.abs(patchLongitudinalSpeed));
        double relativeDifference = (Math.abs(surfaceSpeed) - Math.abs(patchLongitudinalSpeed)) / reference;
        boolean directionMismatch = Math.abs(surfaceSpeed) > 0.5
            && Math.abs(patchLongitudinalSpeed) > 0.5
            && Math.signum(surfaceSpeed) != Math.signum(patchLongitudinalSpeed);
        double reportedDifference = capAtClassicTelemetryLimit
            ? clamp(relativeDifference, -2.0, 2.0)
            : relativeDifference;
        return new WheelSpeedSynchronization(surfaceSpeed, patchLongitudinalSpeed,
            reportedDifference, directionMismatch);
    }

    static double drivenAxleSpeedMetersPerSecond(double leftWheelAngularSpeed,
                                                  double rightWheelAngularSpeed,
                                                  double wheelRadius,
                                                  double fallbackChassisSpeed,
                                                  boolean wheelSpeedsInitialized) {
        if (!wheelSpeedsInitialized
                || !Double.isFinite(leftWheelAngularSpeed)
                || !Double.isFinite(rightWheelAngularSpeed)) {
            return Math.max(0.0, Math.abs(fallbackChassisSpeed));
        }
        double differentialCarrierAngularSpeed = (leftWheelAngularSpeed + rightWheelAngularSpeed) * 0.5;
        return Math.abs(differentialCarrierAngularSpeed) * Math.max(0.0, wheelRadius);
    }

    static double brakingWheelAngularTarget(double wheelLongitudinalSpeed, double wheelRadius,
                                             double slipReferenceSpeed, double brakingSlipMagnitude) {
        double rollingDirection = Math.signum(wheelLongitudinalSpeed);
        if (rollingDirection == 0.0 || wheelRadius <= 0.0) {
            return 0.0;
        }
        double targetSurfaceSpeed = Math.max(0.0, Math.abs(wheelLongitudinalSpeed)
            - Math.max(0.0, brakingSlipMagnitude) * Math.max(0.0, slipReferenceSpeed));
        return rollingDirection * targetSurfaceSpeed / wheelRadius;
    }

    static double drivenWheelAngularSpeed(double wheelAngularSpeed, double requestedForce,
                                          double tyreForce, double patchLongitudinalSpeed,
                                          double wheelRadius, double rotationalInertia, double dt) {
        if (rotationalInertia <= 0.0 || dt <= 0.0) return wheelAngularSpeed;
        double radius = Math.max(0.0, wheelRadius);
        double drivenSpeed = wheelAngularSpeed + requestedForce * radius / rotationalInertia * dt;
        double patchAngularSpeed = radius > 0.0 ? patchLongitudinalSpeed / radius : drivenSpeed;
        double relativeSpeed = drivenSpeed - patchAngularSpeed;
        double reactionDelta = tyreForce * radius / rotationalInertia * dt;
        if (relativeSpeed != 0.0 && Math.signum(reactionDelta) == Math.signum(relativeSpeed)
                && Math.abs(reactionDelta) > Math.abs(relativeSpeed)) {
            reactionDelta = relativeSpeed;
        }
        return drivenSpeed - reactionDelta;
    }

    static double tractionControlledDriveRequest(double request, double limit, double strength) {
        return tractionControlledDriveRequest(request, limit, strength, true);
    }

    static double tractionControlledDriveRequest(double request, double limit, double strength,
                                                 boolean retainMinimumOutput) {
        if (request <= 0.0) return request;
        double minimumOutput = retainMinimumOutput
            ? request * MIN_TRACTION_CONTROL_OUTPUT_FRACTION
            : 0.0;
        double limited = Math.min(request, Math.max(minimumOutput, Math.max(0.0, limit)));
        return request + (limited - request) * clamp(strength, 0.0, 1.0);
    }

    static double offAxisDriveAuthority(double longitudinalSpeed, double totalSpeed) {
        double forwardFraction = Math.abs(longitudinalSpeed) / Math.max(1.0, Math.abs(totalSpeed));
        return 0.30 + 0.70 * clamp(forwardFraction / 0.45, 0.0, 1.0);
    }

    static double brakeTravelDirection(double longitudinalSpeed, double driveDirection) {
        if (Math.abs(driveDirection) > 0.5) {
            return Math.signum(driveDirection);
        }
        return Math.abs(longitudinalSpeed) > 0.1 ? Math.signum(longitudinalSpeed) : 0.0;
    }

    static double understeerFeedbackRelief(double frontSaturation, double rearSaturation,
                                           double frontSlipAngle, double rearSlipAngle,
                                           double steeringAngle, double longitudinalSpeed,
                                           double yawRate, double wheelbase) {
        if (Math.abs(steeringAngle) < Math.toRadians(0.25) || Math.abs(longitudinalSpeed) < 5.0) {
            return 0.0;
        }
        if (yawRate * steeringAngle < -0.01) {
            return 0.0;
        }
        double frontSlipExcess = Math.abs(frontSlipAngle) - Math.abs(rearSlipAngle) - Math.toRadians(0.5);
        if (frontSlipExcess <= 0.0) {
            return 0.0;
        }
        double frontDominance = Math.max(0.0, frontSaturation - rearSaturation * 0.85);
        double saturationEvidence = clamp((Math.max(frontSaturation, frontDominance) - 0.92) / 0.36, 0.0, 1.0);
        double slipEvidence = smoothstep(frontSlipExcess / Math.toRadians(4.0));
        double desiredYawRate = Math.abs(longitudinalSpeed * Math.tan(steeringAngle) / Math.max(0.5, wheelbase));
        double yawProgress = Math.abs(yawRate);
        double yawDeficit = clamp((desiredYawRate - yawProgress) / Math.max(0.05, desiredYawRate * 0.45), 0.0, 1.0);
        return saturationEvidence * slipEvidence * yawDeficit;
    }

    static double balancedHandlingYawMoment(double frontYawMoment, double rearYawMoment,
                                            double steeringAngle, double longitudinalSpeed,
                                            double yawRate, double wheelbase,
                                            double brakeInput, double throttleInput,
                                            double brakingAdjustment, double neutralAdjustment,
                                            double throttleAdjustment) {
        double steeringDirection = Math.signum(steeringAngle);
        if (steeringDirection == 0.0 || Math.abs(longitudinalSpeed) < 2.0) {
            return frontYawMoment + rearYawMoment;
        }

        double steeringUse = smoothstep(Math.abs(steeringAngle) / Math.toRadians(6.0));
        double brakeUse = smoothstep(clamp(brakeInput, 0.0, 1.0));
        double throttleUse = smoothstep(clamp(throttleInput, 0.0, 1.0));
        double brakingScale = clamp(brakingAdjustment, 0.0, 1.5);
        double neutralScale = clamp(neutralAdjustment, 0.0, 1.5);
        double throttleScale = clamp(throttleAdjustment, 0.0, 1.5);
        double nonBrakingYawAssist = 1.0 - smoothstep(clamp(brakeInput, 0.0, 1.0) / 0.05);

        // A locked/saturated front axle should wash wide rather than keep winding yaw into
        // the corner. Preserve the opposite-signed part because it is arresting a spin.
        double frontTurnIn = Math.max(0.0, frontYawMoment * steeringDirection);
        double adjustedFront = frontYawMoment
            - steeringDirection * frontTurnIn
                * Math.min(0.45, 0.20 * brakingScale) * brakeUse * steeringUse;

        // Power oversteer comes from easing the rear axle's counter-yaw only while the car
        // is below its kinematic yaw target. The relief vanishes at/above target yaw, so it
        // cannot continue feeding an established spin.
        double desiredYawRate = Math.abs(longitudinalSpeed * Math.tan(steeringAngle)
            / Math.max(0.5, wheelbase));
        double yawAlongSteering = yawRate * steeringDirection;
        double yawDeficit = clamp((desiredYawRate - yawAlongSteering)
            / Math.max(0.05, desiredYawRate), 0.0, 1.0);
        double rearCounterYaw = Math.max(0.0, -rearYawMoment * steeringDirection);
        double lowSpeedRotation = lowSpeedCornerRotationBlend(longitudinalSpeed);
        double coastRelief = 0.20 + 0.30 * lowSpeedRotation;
        double throttleRelief = 0.35 + 0.15 * lowSpeedRotation;
        double counterYawRelief = (coastRelief * neutralScale
            + throttleRelief * throttleScale * throttleUse) * nonBrakingYawAssist;
        double rawTurnInMoment = (frontYawMoment + rearYawMoment) * steeringDirection;
        double turnInGate = smoothstep(rawTurnInMoment / 2_000.0);
        double adjustedRear = rearYawMoment
            + steeringDirection * rearCounterYaw * counterYawRelief * steeringUse
                * smoothstep(yawDeficit) * turnInGate;
        double availableTyreMoment = Math.abs(frontYawMoment) + Math.abs(rearYawMoment);
        double adjustedMoment = adjustedFront + adjustedRear;

        // Braking softens turn-in but deliberately retains steering authority. Stability
        // comes from the separate signed yaw-error recovery below, not from locking the
        // chassis to zero turn-in moment.
        double remainingTurnIn = Math.max(0.0, adjustedMoment * steeringDirection);
        double turnInReduction = Math.min(0.80, 0.45 * brakingScale) * brakeUse;
        adjustedMoment -= steeringDirection * remainingTurnIn * turnInReduction;

        double signedBrakingYawTarget = steeringDirection * desiredYawRate * 0.85;
        double sameDirectionExcess = yawAlongSteering - Math.abs(signedBrakingYawTarget);
        double oppositeDirectionExcess = -yawAlongSteering - desiredYawRate * 0.15;
        double brakingYawError = sameDirectionExcess > 0.0
            ? steeringDirection * sameDirectionExcess
            : oppositeDirectionExcess > 0.0
                ? -steeringDirection * oppositeDirectionExcess
                : 0.0;
        double brakingRecovery = smoothstep(Math.abs(brakingYawError)
            / Math.max(0.08, desiredYawRate * 0.35));
        adjustedMoment -= Math.signum(brakingYawError) * availableTyreMoment
            * Math.min(1.25, brakingScale) * brakeUse * steeringUse * brakingRecovery;
        return adjustedMoment;
    }

    static double keyboardGripSteeringLimit(double speedMetersPerSecond, double wheelbase,
                                            double massKg, double gravity, double downforce,
                                            double baseLateralMu, double gripCoefficient) {
        double speedSquared = speedMetersPerSecond * speedMetersPerSecond;
        if (speedSquared < 4.0) {
            return Math.PI * 0.5;
        }
        double normalAcceleration = Math.max(0.0, gravity)
            + Math.max(0.0, downforce) / Math.max(1.0, massKg);
        double availableLateralAcceleration = Math.max(0.0, baseLateralMu)
            * Math.max(0.0, gripCoefficient) * normalAcceleration * 1.08;
        return Math.atan(Math.max(0.0, wheelbase) * availableLateralAcceleration / speedSquared);
    }

    static double keyboardSpeedSteeringLock(double speedMetersPerSecond,
                                            double lowSpeedLock, double highSpeedLock,
                                            double lowSpeedGain, double highSpeedGain,
                                            double responseCurve, double speedScale,
                                            double curvePower) {
        double speedRatio = Math.max(0.0, speedMetersPerSecond) / Math.max(0.1, speedScale);
        double speedBlend = square(speedRatio) / (1.0 + square(speedRatio));
        double lockBlend = Math.pow(speedBlend, Math.max(0.01, curvePower * responseCurve));
        double lowLock = Math.max(0.0, lowSpeedLock) * Math.max(0.0, lowSpeedGain);
        double highLock = Math.max(0.0, highSpeedLock) * Math.max(0.0, highSpeedGain);
        return lowLock + (highLock - lowLock) * lockBlend;
    }

    static double kinematicCornerRadius(double wheelbase, double steeringAngle) {
        double tangent = Math.tan(Math.abs(steeringAngle));
        return tangent <= 1.0E-9 ? Double.POSITIVE_INFINITY : Math.max(0.0, wheelbase) / tangent;
    }

    static double softCombinedLongitudinalLimit(double request, double lateralForce,
                                                 double longitudinalLimit, double lateralLimit,
                                                 double strength, double envelope) {
        double lateralUse = Math.abs(lateralForce) / Math.max(1.0, lateralLimit);
        double permitted = Math.max(0.0, envelope);
        double available = Math.max(0.0, longitudinalLimit)
            * Math.sqrt(Math.max(0.0, permitted * permitted - lateralUse * lateralUse));
        double bounded = clamp(request, -available, available);
        return request + (bounded - request) * clamp(strength, 0.0, 1.0);
    }

    static double activeGripEnvelope(boolean assistEnabled, double configuredEnvelope) {
        return assistEnabled ? clamp(configuredEnvelope, 0.90, 1.10) : BASE_GRIP_ENVELOPE;
    }

    static double tractionControlForceEnvelope(boolean assistEnabled, double configuredEnvelope,
                                                double strength) {
        if (!assistEnabled) {
            return BASE_GRIP_ENVELOPE;
        }
        double blend = clamp(strength, 0.0, 1.0);
        double target = clamp(configuredEnvelope, 0.90, 1.18);
        return BASE_GRIP_ENVELOPE + (target - BASE_GRIP_ENVELOPE) * blend;
    }

    static double lowSpeedTractionEnvelope(double speedMetersPerSecond, double configuredEnvelope) {
        double speedBlend = smoothstep((Math.max(0.0, speedMetersPerSecond) - 8.0) / 17.0);
        double lowSpeedEnvelope = Math.max(clamp(configuredEnvelope, 0.90, 1.10), 1.30);
        return lowSpeedEnvelope + (clamp(configuredEnvelope, 0.90, 1.10) - lowSpeedEnvelope) * speedBlend;
    }

    static double lowSpeedTractionControlStrength(double speedMetersPerSecond, double configuredStrength) {
        double speedBlend = smoothstep((Math.max(0.0, speedMetersPerSecond) - 8.0) / 17.0);
        double strength = clamp(configuredStrength, 0.0, 1.0);
        return strength * (0.40 + 0.60 * speedBlend);
    }

    static double lowSpeedCornerRotationBlend(double speedMetersPerSecond) {
        return 1.0 - smoothstep((Math.abs(speedMetersPerSecond) - 30.0) / 25.0);
    }

    static double lowSpeedRearCorneringStiffnessScale(double speedMetersPerSecond,
                                                       double neutralAdjustment) {
        return 1.0 - 0.30 * clamp(neutralAdjustment, 0.0, 1.5)
            * lowSpeedCornerRotationBlend(speedMetersPerSecond);
    }

    static double gripEnvelopeForInputSource(boolean keyboardInput, double assistedEnvelope) {
        return keyboardInput ? assistedEnvelope : Double.POSITIVE_INFINITY;
    }

    static double dynamicFrontBrakeShare(double frontNormalLoad, double rearNormalLoad,
                                         double steeringUse, double baseFrontBias) {
        double loadShare = Math.max(0.0, frontNormalLoad)
            / Math.max(1.0, Math.max(0.0, frontNormalLoad) + Math.max(0.0, rearNormalLoad));
        return clamp(Math.max(baseFrontBias, loadShare + 0.04 * clamp(steeringUse, 0.0, 1.0)),
            baseFrontBias, 0.70);
    }

    static BrakeAxleRequests balanceBrakeRequests(double serviceBrakeDemand, double rearPowertrainBrake,
                                                   double targetFrontShare) {
        double requested = Math.max(0.0, serviceBrakeDemand);
        double powertrain = Math.max(0.0, rearPowertrainBrake);
        if (requested <= 1.0E-9) {
            return new BrakeAxleRequests(0.0, powertrain, powertrain);
        }
        double front = requested * clamp(targetFrontShare, 0.0, 1.0);
        double rear = requested - front;
        double blendedPowertrain = Math.min(powertrain, rear);
        return new BrakeAxleRequests(front, rear, blendedPowertrain);
    }

    static AxleWheelLoads lateralAxleLoads(double axleNormalLoad, double lateralLoadTransfer) {
        double halfLoad = axleNormalLoad * 0.5;
        double halfTransfer = lateralLoadTransfer * 0.5;
        return new AxleWheelLoads(
            Math.max(75.0, halfLoad + halfTransfer),
            Math.max(75.0, halfLoad - halfTransfer));
    }

    static double absLimitedBrakeForce(double brakeForce, double lateralForce,
                                       double longitudinalLimit, double lateralLimit, double envelope) {
        return softCombinedLongitudinalLimit(
            brakeForce, lateralForce, longitudinalLimit, lateralLimit, 1.0, envelope);
    }

    static double absLimitedRearRequest(double totalRequest, double driveRequest,
                                        double lateralForce, double longitudinalLimit,
                                        double lateralLimit, double envelope) {
        double positiveDrive = Math.max(0.0, driveRequest);
        double totalDecelerationRequest = Math.min(0.0, totalRequest - positiveDrive);
        return positiveDrive + absLimitedBrakeForce(
            totalDecelerationRequest, lateralForce, longitudinalLimit, lateralLimit, envelope);
    }

    static double tractionLimitedDriveForceForLoadTransfer(double requestedDriveForce,
                                                           double availableDrivenAxleForce) {
        double limit = Math.max(0.0, availableDrivenAxleForce);
        return clamp(requestedDriveForce, -limit, limit);
    }

    static double drivenAxleForceWithLongitudinalLoadTransfer(double unloadedAxleForceLimit,
                                                               double longitudinalMu,
                                                               double cgHeight,
                                                               double wheelbase) {
        double feedback = Math.max(0.0, longitudinalMu) * Math.max(0.0, cgHeight)
            / Math.max(0.5, wheelbase);
        return Math.max(0.0, unloadedAxleForceLimit) / Math.max(0.50, 1.0 - feedback);
    }

    static ErsWheelPower ersWheelPower(double icePowerWatts, double ersPowerWatts) {
        double netPower = Math.max(0.0, icePowerWatts) + ersPowerWatts;
        return new ErsWheelPower(Math.max(0.0, netPower), Math.max(0.0, -netPower));
    }

    static ErsEnergyFlow reconcileErsEnergy(double requestedIceEnergyJoules,
                                            double requestedPositiveErsEnergyJoules,
                                            double requestedNegativeErsEnergyJoules,
                                            double actualPositiveWheelEnergyJoules,
                                            double additionalHarvestLimitJoules) {
        double ice = Math.max(0.0, requestedIceEnergyJoules);
        double positiveErs = Math.max(0.0, requestedPositiveErsEnergyJoules);
        double scheduledRegen = Math.max(0.0, requestedNegativeErsEnergyJoules);
        double wheelEnergy = Math.max(0.0, actualPositiveWheelEnergyJoules);
        double iceAvailableToWheels = Math.max(0.0, ice - scheduledRegen);
        double iceUsedAtWheels = Math.min(iceAvailableToWheels, wheelEnergy);
        double positiveErsUsed = Math.min(positiveErs, Math.max(0.0, wheelEnergy - iceUsedAtWheels));
        double positiveErsRefund = positiveErs - positiveErsUsed;
        double unusedIce = Math.max(0.0, iceAvailableToWheels - iceUsedAtWheels);
        double additionalHarvest = Math.min(Math.max(0.0, additionalHarvestLimitJoules), unusedIce);
        return new ErsEnergyFlow(positiveErsUsed, positiveErsRefund, additionalHarvest);
    }

    static boolean isSurfaceWithinReferenceDistance(double tyreBottomY, double surfaceTopY,
                                                     double maximumGap, double tolerance) {
        double gap = tyreBottomY - surfaceTopY;
        double allowedTolerance = Math.max(0.0, tolerance);
        return gap >= -allowedTolerance - 1.0E-9
            && gap <= Math.max(0.0, maximumGap) + allowedTolerance + 1.0E-9;
    }

    static double steeringLockForInputSource(boolean keyboardInput, double mechanicalLock,
                                             double keyboardSpeedLock) {
        return keyboardInput ? Math.max(0.0, keyboardSpeedLock) : Math.max(0.0, mechanicalLock);
    }

    static double counterSteerRecoveryBlend(double steeringInput, double yawRate) {
        double opposingYaw = -clamp(steeringInput, -1.0, 1.0) * yawRate;
        return smoothstep((opposingYaw - 0.02) / 0.35);
    }

    public static double steeringCommandDegrees(double normalizedInput, double steeringLockRadians) {
        return Math.toDegrees(clamp(normalizedInput, -1.0, 1.0) * Math.max(0.0, steeringLockRadians));
    }

    static double clampSteeringAngleCommand(double steeringAngleRadians, double mechanicalLockRadians) {
        double finiteAngle = Double.isFinite(steeringAngleRadians) ? steeringAngleRadians : 0.0;
        double lock = Math.max(0.0, mechanicalLockRadians);
        return clamp(finiteAngle, -lock, lock);
    }

    record ErsWheelPower(double propulsionWatts, double regenerativeBrakingWatts) {}

    record ErsEnergyFlow(double positiveErsUsedJoules, double positiveErsRefundJoules,
                         double additionalIceHarvestJoules) {}

    static KeyboardStabilityInputs keyboardStabilityInputs(double throttle, double brake,
                                                            double steeringInput, double speedMetersPerSecond,
                                                            double frontSlipAngle, double rearSlipAngle,
                                                            double baseFrontBrakeBias, double strength) {
        double assist = clamp(strength, 0.0, 1.0);
        double steerUse = smoothstep((Math.abs(steeringInput) - 0.08) / 0.92);
        double speedUse = smoothstep((speedMetersPerSecond - 5.0) / 20.0);
        double frontExcess = Math.max(0.0, Math.abs(frontSlipAngle) - Math.abs(rearSlipAngle) - Math.toRadians(1.0));
        double rearExcess = Math.max(0.0, Math.abs(rearSlipAngle) - Math.abs(frontSlipAngle) - Math.toRadians(0.5));
        double understeer = clamp(frontExcess / Math.toRadians(7.0), 0.0, 1.0);
        double oversteer = clamp(rearExcess / Math.toRadians(5.0), 0.0, 1.0);

        double throttleIntervention = Math.max(understeer, steerUse * speedUse * 0.30);
        double deliveredThrottle = clamp(throttle, 0.0, 1.0)
            * (1.0 - assist * throttleIntervention * 0.50);
        double deliveredBrake = clamp(brake, 0.0, 1.0)
            * (1.0 - assist * oversteer * 0.25);
        double brakeStabilityDemand = Math.max(oversteer, steerUse * speedUse * 0.35);
        double frontBrakeBias = clamp(baseFrontBrakeBias + assist * brakeStabilityDemand * 0.12,
            baseFrontBrakeBias, 0.72);
        return new KeyboardStabilityInputs(deliveredThrottle, deliveredBrake, frontBrakeBias);
    }

    public static double tyreBrakeHeatPowerPerTyre(double brakeInput, double totalBrakeHeatPower, double axleBias) {
        return Math.max(0.0, brakeInput) * Math.max(0.0, totalBrakeHeatPower) * clamp(axleBias, 0.0, 1.0) * 0.5;
    }

    public static double rearFrictionBrakeFraction(double brakeInput, double maxBrakeForce, double speedMetersPerSecond,
                                                   double frontBrakeBias, double mguKRegenPowerWatts) {
        double rearRequestedPower = Math.max(0.0, brakeInput)
            * Math.max(0.0, maxBrakeForce)
            * Math.max(0.0, speedMetersPerSecond)
            * (1.0 - clamp(frontBrakeBias, 0.0, 1.0));
        if (rearRequestedPower <= 1.0E-9) return 1.0;
        return clamp((rearRequestedPower - Math.max(0.0, mguKRegenPowerWatts)) / rearRequestedPower, 0.0, 1.0);
    }

    public static double tyreRollingHeatPowerWatts(double normalLoad, double speedMetersPerSecond, double rollingResistance) {
        return Math.max(0.0, rollingResistance * normalLoad * Math.max(0.0, speedMetersPerSecond) * TYRE_ROLLING_RESISTANCE_HEAT_FRACTION);
    }

    public static double tyreLongitudinalScrubHeatPowerWatts(double longitudinalReactionForce,
                                                             double longitudinalScrubSpeed) {
        double kineticScrubSpeed = Math.max(0.0,
            Math.abs(longitudinalScrubSpeed) - KINETIC_SCRUB_SPEED_THRESHOLD);
        return Math.abs(longitudinalReactionForce) * kineticScrubSpeed;
    }

    public static double tyreLateralNearSaturation(double lateralForce, double normalLoad) {
        double lateralUtilization = Math.abs(lateralForce) / Math.max(1.0, normalLoad * ASPHALT_MU_LONGITUDINAL);
        return Math.max(0.0, lateralUtilization - 0.88);
    }

    public static double tyreSlipHeatPowerWatts(double longitudinalForce, double lateralForce, double normalLoad, double speedMetersPerSecond, double demand, double slipAngleRadians) {
        double safeNormalLoad = Math.max(1.0, normalLoad);
        double speed = Math.max(0.0, speedMetersPerSecond);
        double longitudinalUtilization = Math.min(1.0, Math.abs(longitudinalForce) / (safeNormalLoad * ASPHALT_MU_LONGITUDINAL));
        double lateralUtilization = Math.min(1.0, Math.abs(lateralForce) / (safeNormalLoad * ASPHALT_MU_LONGITUDINAL));
        double slipAngle = Math.abs(slipAngleRadians);
        double longitudinalWorkSpeed = longitudinalUtilization * speed * 0.055;
        double lateralScrubSpeed = Math.sin(slipAngle) * speed * 0.210;
        double lateralGripWorkSpeed = lateralUtilization * speed * 0.0060;
        double slipFrictionPower = safeNormalLoad * speed * slipAngle * slipAngle * (0.72 + lateralUtilization * 0.48);
        double saturation = Math.max(0.0, demand - 1.0);
        double limitForce = safeNormalLoad * ASPHALT_MU_LONGITUDINAL;
        double kineticSlipVelocity = saturation * saturation * speed * 2.40;
        double kineticSlipPower = limitForce * kineticSlipVelocity;
        return (Math.abs(longitudinalForce) * longitudinalWorkSpeed + Math.abs(lateralForce) * (lateralScrubSpeed + lateralGripWorkSpeed) + slipFrictionPower + kineticSlipPower) * TYRE_SLIP_HEAT_FRACTION;
    }

    public static double tyreLoadHeatMultiplier(double longitudinalForce, double lateralForce, double normalLoad) {
        double safeNormalLoad = Math.max(1.0, normalLoad);
        double longitudinalG = Math.abs(longitudinalForce) / safeNormalLoad;
        double lateralG = Math.abs(lateralForce) / safeNormalLoad;
        double combinedG = Math.sqrt(longitudinalG * longitudinalG * 0.8 + lateralG * lateralG);
        return 1.0 + Math.max(0.0, combinedG - 0.08) * TYRE_G_FORCE_HEAT_FACTOR;
    }

    public static double tyreGForceHeatMultiplier(double longitudinalG, double lateralG) {
        double combinedG = Math.sqrt(longitudinalG * longitudinalG + lateralG * lateralG);
        return 1.0 + Math.max(0.0, combinedG - 0.08) * TYRE_G_FORCE_HEAT_FACTOR;
    }

    public static double tyreHeatDeltaC(double heatPowerWatts, double compoundHeatGain, double longitudinalG, double lateralG, double dtSeconds) {
        double heatEnergy = Math.max(0.0, heatPowerWatts) * tyreGForceHeatMultiplier(longitudinalG, lateralG) * Math.max(0.0, compoundHeatGain) * dtSeconds;
        return heatEnergy / TYRE_HEAT_CAPACITY_J_PER_C;
    }

    public static double tyreHeatDeltaC(double heatPowerWatts, double compoundHeatGain, double longitudinalForce, double lateralForce, double normalLoad, double dtSeconds) {
        double heatEnergy = Math.max(0.0, heatPowerWatts) * tyreLoadHeatMultiplier(longitudinalForce, lateralForce, normalLoad) * Math.max(0.0, compoundHeatGain) * dtSeconds;
        return heatEnergy / TYRE_HEAT_CAPACITY_J_PER_C;
    }

    public static double tyreCoolingDeltaC(double temperatureC, double speedMetersPerSecond, double dtSeconds) {
        return tyreCoolingDeltaC(temperatureC, speedMetersPerSecond, dtSeconds, TYRE_AMBIENT_TEMPERATURE_C);
    }

    public static double tyreCoolingDeltaC(double temperatureC, double speedMetersPerSecond, double dtSeconds, double ambientTemperatureC) {
        double ambient = clamp(ambientTemperatureC, 0.0, 60.0);
        if (temperatureC <= ambient) {
            return 0.0;
        }
        double coolingRate = TYRE_STATIONARY_COOLING_PER_SECOND + TYRE_WIND_COOLING_PER_MPS_SECOND * Math.max(0.0, speedMetersPerSecond) + tyreHotCoolingRate(temperatureC);
        return (temperatureC - ambient) * (1.0 - Math.exp(-coolingRate * dtSeconds));
    }

    public static double tyreHotCoolingRate(double temperatureC) {
        double excessHeat = Math.max(0.0, temperatureC - TYRE_HOT_COOLING_START_C);
        return TYRE_HOT_COOLING_PER_SECOND * excessHeat * excessHeat;
    }

    public static double nextTyreTemperatureC(double temperatureC, double heatPowerWatts, double compoundHeatGain, double longitudinalG, double lateralG, double speedMetersPerSecond, double dtSeconds) {
        double next = temperatureC + tyreHeatDeltaC(heatPowerWatts, compoundHeatGain, longitudinalG, lateralG, dtSeconds) - tyreCoolingDeltaC(temperatureC, speedMetersPerSecond, dtSeconds);
        return Math.max(TYRE_AMBIENT_TEMPERATURE_C, next);
    }

    public static double tyreOptimalTemperatureC(int compound) {
        return switch (compound) {
            case 0 -> 115.0;
            case 1 -> 112.5;
            case 2 -> 110.0;
            case 3 -> 107.5;
            default -> 105.0;
        };
    }

    public static double tyreTemperatureGripMultiplier(int compound, double temperatureC) {
        double offset = temperatureC - tyreOptimalTemperatureC(compound);
        if (offset <= -55.0) return interpolate(offset, -65.0, -55.0, 0.52, 0.55);
        if (offset <= -45.0) return interpolate(offset, -55.0, -45.0, 0.55, 0.62);
        if (offset <= -35.0) return interpolate(offset, -45.0, -35.0, 0.62, 0.72);
        if (offset <= -25.0) return interpolate(offset, -35.0, -25.0, 0.72, 0.84);
        if (offset <= -15.0) return interpolate(offset, -25.0, -15.0, 0.84, 0.94);
        if (offset <= -10.0) return interpolate(offset, -15.0, -10.0, 0.94, 0.98);
        if (offset <= -5.0) return interpolate(offset, -10.0, -5.0, 0.98, 1.00);
        if (offset <= 5.0) return 1.00;
        if (offset <= 10.0) return interpolate(offset, 5.0, 10.0, 1.00, 0.985);
        if (offset <= 15.0) return interpolate(offset, 10.0, 15.0, 0.985, 0.965);
        if (offset <= 20.0) return interpolate(offset, 15.0, 20.0, 0.965, 0.94);
        if (offset <= 25.0) return interpolate(offset, 20.0, 25.0, 0.94, 0.86);
        if (offset <= 30.0) return interpolate(offset, 25.0, 30.0, 0.86, 0.80);
        if (offset <= 35.0) return interpolate(offset, 30.0, 35.0, 0.80, 0.70);
        if (offset <= 40.0) return interpolate(offset, 35.0, 40.0, 0.70, 0.65);
        return interpolate(offset, 40.0, 45.0, 0.65, 0.60);
    }

    public static double tyreDirectionChangeSeverity(double previousLongitudinal, double previousLateral,
            double currentLongitudinal, double currentLateral) {
        double cross = previousLongitudinal * currentLateral - previousLateral * currentLongitudinal;
        double dot = previousLongitudinal * currentLongitudinal + previousLateral * currentLateral;
        double rotation = cross * cross * 4.0;
        double loadedReversal = Math.max(0.0, -dot) * 2.0;
        return clamp(rotation + loadedReversal, 0.0, 1.0);
    }

    public static TyreThermalState nextTyreThermalState(double surfaceTemperatureC, double carcassTemperatureC, double slipExposure,
            double frictionHeatPowerWatts, double brakeHeatPowerWatts, double compoundHeatGain, double speedMetersPerSecond,
            double surfaceCoolingMultiplier, double stationaryCoolingMultiplier, double windCoolingMultiplier,
            double exposureDemand, double slipAngleRadians, double dtSeconds, boolean groundContact) {
        return nextTyreThermalState(surfaceTemperatureC, carcassTemperatureC, slipExposure, frictionHeatPowerWatts,
            brakeHeatPowerWatts, compoundHeatGain, speedMetersPerSecond, surfaceCoolingMultiplier, 1.0,
            stationaryCoolingMultiplier, windCoolingMultiplier, exposureDemand, slipAngleRadians, dtSeconds, groundContact);
    }

    public static TyreThermalState nextTyreThermalState(double surfaceTemperatureC, double carcassTemperatureC, double slipExposure,
            double frictionHeatPowerWatts, double brakeHeatPowerWatts, double compoundHeatGain, double speedMetersPerSecond,
            double surfaceCoolingMultiplier, double carcassCoolingMultiplier, double stationaryCoolingMultiplier, double windCoolingMultiplier,
            double exposureDemand, double slipAngleRadians, double dtSeconds, boolean groundContact) {
        return nextTyreThermalState(surfaceTemperatureC, carcassTemperatureC, slipExposure, frictionHeatPowerWatts,
            brakeHeatPowerWatts, compoundHeatGain, speedMetersPerSecond, surfaceCoolingMultiplier, carcassCoolingMultiplier,
            stationaryCoolingMultiplier, windCoolingMultiplier, exposureDemand, slipAngleRadians, dtSeconds, groundContact,
            TYRE_AMBIENT_TEMPERATURE_C);
    }

    public static TyreThermalState nextTyreThermalState(double surfaceTemperatureC, double carcassTemperatureC, double slipExposure,
            double frictionHeatPowerWatts, double brakeHeatPowerWatts, double compoundHeatGain, double speedMetersPerSecond,
            double surfaceCoolingMultiplier, double carcassCoolingMultiplier, double stationaryCoolingMultiplier, double windCoolingMultiplier,
            double exposureDemand, double slipAngleRadians, double dtSeconds, boolean groundContact, double ambientTemperatureC) {
        double dt = Math.max(0.0, dtSeconds);
        double ambient = clamp(ambientTemperatureC, 0.0, 60.0);
        double abuseTarget = clamp(Math.max(0.0, exposureDemand - 0.82) * 3.0 + Math.abs(slipAngleRadians) / 0.16, 0.0, 1.0);
        double exposureRate = abuseTarget > slipExposure ? TYRE_EXPOSURE_ATTACK_PER_SECOND : TYRE_EXPOSURE_RELEASE_PER_SECOND;
        double nextExposure = moveToward(slipExposure, abuseTarget, exposureRate * dt);
        if (!groundContact) {
            nextExposure = moveToward(nextExposure, 0.0, TYRE_EXPOSURE_RELEASE_PER_SECOND * dt);
        }

        double frictionPower = groundContact ? Math.max(0.0, frictionHeatPowerWatts) * Math.max(0.0, compoundHeatGain) : 0.0;
        double brakePower = groundContact ? Math.max(0.0, brakeHeatPowerWatts) : 0.0;
        double surfacePower = frictionPower * TYRE_SURFACE_FRICTION_HEAT_FRACTION + brakePower * (1.0 - TYRE_BRAKE_TO_CARCASS_HEAT_FRACTION);
        double carcassPower = frictionPower * TYRE_CARCASS_FRICTION_HEAT_FRACTION + brakePower * TYRE_BRAKE_TO_CARCASS_HEAT_FRACTION;
        double transientTransfer = TYRE_EXPOSURE_TRANSFER_FLOOR + (1.0 - TYRE_EXPOSURE_TRANSFER_FLOOR) * nextExposure;
        double conductionPower = (surfaceTemperatureC - carcassTemperatureC) * TYRE_CARCASS_TRANSFER_WATTS_PER_C * transientTransfer;
        surfacePower -= conductionPower;
        carcassPower += conductionPower;

        double surfaceCooling = tyreCoolingDeltaC(surfaceTemperatureC, speedMetersPerSecond, dt, ambient)
            * Math.max(0.0, surfaceCoolingMultiplier)
            * Math.max(0.0, stationaryCoolingMultiplier)
            * Math.max(0.0, windCoolingMultiplier);
        double carcassCooling = tyreCoolingDeltaC(carcassTemperatureC, speedMetersPerSecond, dt, ambient)
            * TYRE_CARCASS_COOLING_FRACTION * Math.max(0.0, carcassCoolingMultiplier);
        double surface = clamp(surfaceTemperatureC + surfacePower * dt / TYRE_SURFACE_HEAT_CAPACITY_J_PER_C - surfaceCooling, ambient, 145.0);
        double carcass = clamp(carcassTemperatureC + carcassPower * dt / TYRE_CARCASS_HEAT_CAPACITY_J_PER_C - carcassCooling, ambient, 145.0);
        return new TyreThermalState(surface, carcass, nextExposure);
    }

    private static double moveToward(double value, double target, double amount) {
        if (value < target) return Math.min(target, value + amount);
        return Math.max(target, value - amount);
    }

    private static double smoothstep(double value) {
        double t = clamp(value, 0.0, 1.0);
        return t * t * (3.0 - 2.0 * t);
    }

    private static double square(double value) {
        return value * value;
    }

    private static double interpolate(double value, double from, double to, double fromValue, double toValue) {
        double t = clamp((value - from) / Math.max(1.0E-9, to - from), 0.0, 1.0);
        return fromValue + (toValue - fromValue) * t;
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    public record TyreThermalState(double surfaceTemperatureC, double carcassTemperatureC, double slipExposure) {
    }

    public record WheelSpeedSynchronization(double surfaceSpeed, double patchSpeed,
                                             double relativeDifference, boolean directionMismatch) {
    }

    record PlanarForce(double longitudinal, double lateral) {
    }

    record WheelPatchVelocity(double longitudinal, double lateral) {
    }

    record BodyAcceleration(double longitudinal, double lateral) {
    }

    record AxleWheelLoads(double negativeLocalX, double positiveLocalX) {
    }

    record KeyboardStabilityInputs(double throttle, double brake, double frontBrakeBias) {
    }

    record BrakeAxleRequests(double front, double rear, double rearPowertrain) {
    }

    public static double simulateTyreEquilibriumC(double initialTemperatureC, double heatPowerWatts, double compoundHeatGain, double longitudinalG, double lateralG, double speedMetersPerSecond, int ticks) {
        double temperature = initialTemperatureC;
        for (int tick = 0; tick < ticks; tick++) {
            temperature = nextTyreTemperatureC(temperature, heatPowerWatts, compoundHeatGain, longitudinalG, lateralG, speedMetersPerSecond, 0.05);
        }
        return temperature;
    }
}
