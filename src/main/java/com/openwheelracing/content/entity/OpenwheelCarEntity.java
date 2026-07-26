package com.openwheelracing.content.entity;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

import com.mojang.logging.LogUtils;
import com.openwheelracing.content.car.CarLivery;
import com.openwheelracing.content.car.CarLiveryColors;
import com.openwheelracing.content.car.CarLiveryTexture;
import com.openwheelracing.content.car.PrototypeCarSetup;
import com.openwheelracing.content.item.PrototypeCarItem;
import com.openwheelracing.content.item.TyreItem;
import com.openwheelracing.content.race.OWRLapRecords;
import com.openwheelracing.content.race.OWRRaceControlState;
import com.openwheelracing.network.OWRNetwork;
import com.openwheelracing.registry.OWRBlocks;
import com.openwheelracing.registry.OWRItems;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.decoration.HangingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

public class OpenwheelCarEntity extends Entity {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final EntityDataAccessor<Integer> GEAR = SynchedEntityData.defineId(OpenwheelCarEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> RPM = SynchedEntityData.defineId(OpenwheelCarEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> SPEED = SynchedEntityData.defineId(OpenwheelCarEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DAMAGE = SynchedEntityData.defineId(OpenwheelCarEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> TYRE_WEAR = SynchedEntityData.defineId(OpenwheelCarEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> TYRE_SLIP = SynchedEntityData.defineId(OpenwheelCarEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Integer> CURRENT_LAP_TICKS = SynchedEntityData.defineId(OpenwheelCarEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> BEST_LAP_TICKS = SynchedEntityData.defineId(OpenwheelCarEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> COMPLETED_LAP_TICKS = SynchedEntityData.defineId(OpenwheelCarEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> COMPLETED_LAP_LINGER_TICKS = SynchedEntityData.defineId(OpenwheelCarEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> COMPLETED_LAP_RESULT = SynchedEntityData.defineId(OpenwheelCarEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> CHECKPOINT_ARMED = SynchedEntityData.defineId(OpenwheelCarEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Integer> PIT_STOP_TICKS = SynchedEntityData.defineId(OpenwheelCarEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> ABS_ENABLED = SynchedEntityData.defineId(OpenwheelCarEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> TRACTION_CONTROL_ENABLED = SynchedEntityData.defineId(OpenwheelCarEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Integer> LIVERY = SynchedEntityData.defineId(OpenwheelCarEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> LIVERY_BODY = SynchedEntityData.defineId(OpenwheelCarEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> LIVERY_ACCENT_1 = SynchedEntityData.defineId(OpenwheelCarEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> LIVERY_ACCENT_2 = SynchedEntityData.defineId(OpenwheelCarEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<String> LIVERY_TEXTURE = SynchedEntityData.defineId(OpenwheelCarEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<Integer> TYRE_COMPOUND = SynchedEntityData.defineId(OpenwheelCarEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> DRS_ACTIVE = SynchedEntityData.defineId(OpenwheelCarEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Integer> ERS_MODE = SynchedEntityData.defineId(OpenwheelCarEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> ERS_ENERGY = SynchedEntityData.defineId(OpenwheelCarEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Integer> ERS_ACTIVITY = SynchedEntityData.defineId(OpenwheelCarEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> ERS_POWER_KW = SynchedEntityData.defineId(OpenwheelCarEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Integer> ERS_BALANCED_CLIP_START = SynchedEntityData.defineId(OpenwheelCarEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> ERS_BALANCED_CLIP_END = SynchedEntityData.defineId(OpenwheelCarEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> ERS_HARVEST_NEGATIVE_START = SynchedEntityData.defineId(OpenwheelCarEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> ERS_HARVEST_NEGATIVE_FULL = SynchedEntityData.defineId(OpenwheelCarEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> ERS_BALANCED_START_POWER = SynchedEntityData.defineId(OpenwheelCarEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> ERS_BALANCED_END_POWER = SynchedEntityData.defineId(OpenwheelCarEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> ERS_HARVEST_START_POWER = SynchedEntityData.defineId(OpenwheelCarEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> ERS_HARVEST_END_POWER = SynchedEntityData.defineId(OpenwheelCarEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> ERS_CAPACITY = SynchedEntityData.defineId(OpenwheelCarEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Integer> ERS_ATTACK_POWER = SynchedEntityData.defineId(OpenwheelCarEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> ERS_LICO_SPEED_THRESHOLD = SynchedEntityData.defineId(OpenwheelCarEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> ERS_LICO_STEERING_THRESHOLD = SynchedEntityData.defineId(OpenwheelCarEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> ERS_LICO_LATERAL_G_THRESHOLD = SynchedEntityData.defineId(OpenwheelCarEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Integer> ERS_LICO_HARVEST_POWER = SynchedEntityData.defineId(OpenwheelCarEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> ERS_LICO_BALANCED_POWER = SynchedEntityData.defineId(OpenwheelCarEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> ERS_LICO_ATTACK_POWER = SynchedEntityData.defineId(OpenwheelCarEntity.class, EntityDataSerializers.INT);

    private static final int PIT_STOP_DURATION = 60; // 3 seconds
    private static final int PIT_RUBBER_COST = 2;    // rubber items consumed per stop
    private static final int COMPLETED_LAP_LINGER_DURATION = 100;
    public static final int LAP_RESULT_NONE = 0;
    public static final int LAP_RESULT_SLOWER = 1;
    public static final int LAP_RESULT_PERSONAL_BEST = 2;
    public static final int LAP_RESULT_OVERALL_BEST = 3;

    public static final int ERS_MODE_HARVEST = 0;
    public static final int ERS_MODE_BALANCED = 1;
    public static final int ERS_MODE_ATTACK = 2;
    public static final int ERS_ACTIVITY_NEUTRAL = 0;
    public static final int ERS_ACTIVITY_HARVESTING = 1;
    public static final int ERS_ACTIVITY_DEPLOYING = 2;
    public static final int ERS_ACTIVITY_NEGATIVE = 3;
    public static final int ERS_BALANCED_CLIP_START_DEFAULT_KMH = 260;
    public static final int ERS_BALANCED_CLIP_END_DEFAULT_KMH = 315;
    public static final int ERS_HARVEST_NEGATIVE_START_DEFAULT_KMH = 260;
    public static final int ERS_HARVEST_NEGATIVE_FULL_DEFAULT_KMH = 320;
    public static final int ERS_BALANCED_START_POWER_DEFAULT_KW = 200;
    public static final int ERS_BALANCED_END_POWER_DEFAULT_KW = 0;
    public static final int ERS_HARVEST_START_POWER_DEFAULT_KW = 0;
    public static final int ERS_HARVEST_END_POWER_DEFAULT_KW = -110;
    public static final int ERS_ATTACK_POWER_DEFAULT_KW = 350;
    public static final int ERS_LICO_SPEED_THRESHOLD_DEFAULT_KMH = 260;
    public static final double ERS_LICO_STEERING_THRESHOLD_DEFAULT_DEGREES = 1.8;
    public static final double ERS_LICO_LATERAL_G_THRESHOLD_DEFAULT = 0.28;
    public static final int ERS_LICO_HARVEST_POWER_DEFAULT_KW = -350;
    public static final int ERS_LICO_BALANCED_POWER_DEFAULT_KW = -180;
    public static final int ERS_LICO_ATTACK_POWER_DEFAULT_KW = 0;
    public static final double ERS_LICO_POWER_RAMP_KW_PER_SECOND = 400.0;
    public static final int ERS_LICO_LIFT_CONFIRM_TICKS = 4;
    public static final double ERS_LICO_THROTTLE_DEADZONE = 0.03;
    public static final double ERS_LICO_BRAKE_DEADZONE = 0.02;
    public static final double ERS_CAPACITY_DEFAULT_J = 4_000_000.0;

    // Seat offset: eye height = car Y + (-0.62) + player eye height (1.62) ≈ 1.0 above ground
    private static final Vec3 SEAT_OFFSET = new Vec3(0.0, -0.76, 0.05);
    private static final Vec3[] DISMOUNT_OFFSETS = {
        new Vec3(1.1, 0.0, 0.15),
        new Vec3(-1.1, 0.0, 0.15),
        new Vec3(0.0, 0.0, 1.25),
        new Vec3(0.0, 0.0, -1.65)
    };

    private static final int REVERSE_GEAR = -1;
    private static final int NEUTRAL_GEAR = 0;
    private static final int MAX_GEAR = 8;
    private static final double SPEED_TO_BLOCKS_PER_TICK = VehiclePhysics.SPEED_TO_BLOCKS_PER_TICK;
    private static final double MAX_REASONABLE_SPEED_BLOCKS_PER_TICK = 420.0 * SPEED_TO_BLOCKS_PER_TICK;
    private static final double MAX_REASONABLE_MOVEMENT_BLOCKS_PER_TICK = MAX_REASONABLE_SPEED_BLOCKS_PER_TICK + 0.35;
    private static final double PASSIVE_GROUND_DRAG = 0.92;
    private static final double PASSIVE_AIR_DRAG = 0.985;
    private static final double PASSIVE_YAW_DAMPING = 0.70;
    private static final double TERRAIN_CLIMB_CLEARANCE = 0.08;
    private static final double CAR_MASS_KG = 769.0;
    private static final double GRAVITY = 9.81;
    private static final double PHYSICS_DT = 1.0 / 20.0;
    private static final int PHYSICS_SUBSTEPS = 4;
    private static final double WHEELBASE = 3.60;
    private static final double TRACK_WIDTH = 1.63;
    private static final double HALF_TRACK_WIDTH = TRACK_WIDTH * 0.5;
    private static final double FRONT_AXLE_DISTANCE = WHEELBASE * (1.0 - 0.46);
    private static final double REAR_AXLE_DISTANCE = WHEELBASE * 0.46;
    private static final double YAW_INERTIA = 1100.0;
    private static final double FRONT_STATIC_WEIGHT = 0.46;
    private static final double CG_HEIGHT = 0.27;
    private static final double AIR_DENSITY = 1.225;
    private static final double DRAG_AREA = 1.05;
    private static final double DOWNFORCE_AREA = 7.0;
    private static final double FRONT_AERO_BALANCE = 0.43;
    private static final double ROLLING_RESISTANCE = 0.012;
    private static final double ASPHALT_MU_LATERAL = 2.15;
    private static final double ASPHALT_MU_LONGITUDINAL = 2.25;
    private static final double KINETIC_MU_RATIO = 0.94;
    private static final double LOAD_SENSITIVITY = 0.035;
    private static final double MIN_SURFACE_MU = 0.02;
    private static final double FRONT_CORNERING_STIFFNESS = 210_000.0;
    private static final double REAR_CORNERING_STIFFNESS = 285_000.0;
    private static final double FRONT_LONGITUDINAL_STIFFNESS = 245_000.0;
    private static final double REAR_LONGITUDINAL_STIFFNESS = 315_000.0;
    private static final double PACEJKA_LONGITUDINAL_SHAPE = 1.65;
    private static final double PACEJKA_LONGITUDINAL_CURVATURE = 0.97;
    private static final double PACEJKA_LATERAL_SHAPE = 1.30;
    private static final double PACEJKA_LATERAL_CURVATURE = 0.92;
    private static final double FRONT_ROLL_STIFFNESS_SHARE = 0.53;
    private static final double REAR_ROLL_STIFFNESS_SHARE = 0.47;
    private static final double FRONT_TOE_OUT = Math.toRadians(0.12);
    private static final double REAR_TOE_IN = Math.toRadians(0.08);
    private static final double MAX_BRAKE_FORCE = 40_000.0;
    private static final double BRAKE_FRONT_BIAS = 0.58;
    private static final double MIN_POWER_SPEED = 5.0;
    private static final double PEAK_POWER_WATTS = 780_000.0;
    private static final double ERS_POWER_SHARE_WATTS = 350_000.0;
    private static final double ERS_MAX_HARVEST_PER_TICK_J = 30_000.0;
    private static final double ERS_RECOVERY_EFFICIENCY = 0.40;
    private static final double IDLE_RPM = 900.0;
    private static final double LAUNCH_RPM = 4000.0;
    private static final double LAUNCH_CLUTCH_SPEED = 0.42;
    private static final double REDLINE_RPM = 13000.0;
    private static final int CLUTCH_RELEASE_TICKS = 12;
    private static final double NEUTRAL_RPM_RISE_PER_SECOND = 18_000.0;
    private static final double NEUTRAL_RPM_DECAY_PER_SECOND = 3_800.0;
    private static final double CLUTCH_RPM_DROP_PER_SECOND = 12_000.0;
    private static final double ENGINE_BRAKE_RPM_DROP_PER_SECOND = 7_000.0;
    private static final double CLUTCH_RELEASE_TRACTION_LIMIT = 0.95;
    private static final double STEERING_DEADZONE = 0.08;
    private static final double[] ENGINE_RPM_POINTS = {900.0, 2500.0, 4000.0, 4700.0, 6500.0, 8200.0, 10500.0, 11800.0, 12600.0, 13000.0};
    private static final double[] ENGINE_POWER_POINTS = {0.03, 0.10, 0.22, 0.34, 0.56, 0.75, 0.95, 1.00, 0.78, 0.42};
    private static final double LOW_SPEED_STEER_ANGLE = Math.toRadians(34.0);
    private static final double HIGH_SPEED_STEER_ANGLE = Math.toRadians(2.45);
    private static final double STEERING_HIGH_SPEED_CURVE_POWER = 0.72;
    private static final double STEERING_TRAIL_BRAKE_RELEASE = 0.35;
    private static final double TRAIL_BRAKE_REAR_PRESSURE_RELIEF = 0.42;
    private static final double TRAIL_BRAKE_REAR_RELIEF_MAX_STEER = Math.toRadians(6.0);
    private static final double FRONT_UNDERSTEER_WARNING_THRESHOLD = 0.94;
    private static final double FRONT_UNDERSTEER_WARNING_RECOVERY = 0.84;
    private static final long FRONT_UNDERSTEER_WARNING_COOLDOWN = 20L;
    private static final double ENTITY_IMPACT_MIN_SPEED = 0.16;
    private static final double ENTITY_IMPACT_SOFT_SPEED = 0.30;
    private static final double ENTITY_IMPACT_CAR_DAMAGE = 5.5;
    private static final double ENTITY_IMPACT_LIVING_DAMAGE = 40.0;
    private static final double ENTITY_IMPACT_OTHER_CAR_DAMAGE = 3.5;
    private static final long ENTITY_IMPACT_COOLDOWN_TICKS = 8L;
    private static final double DRS_DRAG_FACTOR = 0.78;
    private static final double DRS_DOWNFORCE_FACTOR = 0.72;
    private static final double STEERING_OFF_GRIP_RELIEF_START = 0.92;
    private static final double STEERING_OFF_GRIP_RELIEF_FULL = 1.28;
    private static final double STEERING_OFF_GRIP_LOCK_BONUS = 0.45;
    private static final double STEERING_OFF_GRIP_RATE_BONUS = 1.35;
    private static final double LOW_SPEED_STEERING_RACK_RATE = Math.toRadians(120.0);
    private static final double HIGH_SPEED_STEERING_RACK_RATE = Math.toRadians(4.0);
    private static final double LOW_SPEED_STEERING_CENTERING_RATE = Math.toRadians(90.0);
    private static final double HIGH_SPEED_STEERING_CENTERING_RATE = Math.toRadians(180.0);
    private static final double STEERING_SPEED_SCALE = 20.0;
    private static final double TRACTION_CONTROL_SLIP_TARGET = 0.92;
    private static final double SLIP_ANGLE_DEADBAND = Math.toRadians(0.15);
    private static final double FRONT_TYRE_RELAXATION_LENGTH = 0.42;
    private static final double REAR_TYRE_RELAXATION_LENGTH = 0.45;
    private static final double STATIC_TYRE_SPEED_THRESHOLD = 1.5;
    private static final double[] TRACK_WHEEL_SIDE_OFFSETS = {-1.34, 1.34};
    private static final double[] TRACK_WHEEL_LENGTH_OFFSETS = {-2.95, 1.55};
    private static final double[] TRACK_PATCH_SIDE_OFFSETS = {-0.18, 0.0, 0.18};
    private static final double[] TRACK_PATCH_LENGTH_OFFSETS = {-0.32, 0.0, 0.32};
    private PrototypeCarSetup setup = PrototypeCarSetup.DEFAULT;
    private double previousHorizontalSpeed;
    private double lastClimbDelta;
    private double lastGroundSnapDelta;
    private double lastTerrainPositionCorrectionY;
    private long lapStartedAt = -1L;
    private long lastStartFinishMarker;
    private long lastStartFinishTriggerAt = -20L;
    private long lastLowTyreWarningAt = -200L;
    private long lastDamageWarningAt = -200L;
    private long lastFrontUndersteerWarningAt = -200L;
    private boolean frontUndersteerWarningActive;
    private long lastOffTrackCheckAt = -4L;
    // Client-side: yaw the car was at when passenger was last synced; used to detect
    // server-authoritative yaw corrections and keep the driver view aligned.
    private float clientLastSyncedCarYaw = Float.NaN;
    // Checkpoint positions (packed BlockPos longs) visited in the current lap, in order
    private final java.util.LinkedList<Long> visitedCheckpoints = new java.util.LinkedList<>();
    private final java.util.HashSet<Long> visitedCheckpointSet = new java.util.HashSet<>();
    // Last driver input received from client; cleared each tick after use
    private float inputThrottle;
    private float inputBrake;
    private float inputSteering;
    private long lastMovementWarningAt = -200L;
    private final java.util.HashMap<Integer, Long> lastEntityImpactById = new java.util.HashMap<>();
    private boolean wasRiddenLastTick;
    private double steeringAngle;
    private double frontSteeringOffGripRelief;
    private double yawRate;
    private int clutchReleaseTicks;
    private int clutchReleaseRpm;
    private double relaxedFlLatForce;
    private double relaxedFrLatForce;
    private double relaxedRlLatForce;
    private double relaxedRrLatForce;
    private double debugVelocityLong;
    private double debugVelocityLat;
    private double debugDriveForce;
    private double debugDragForce;
    private double debugFlLatForce;
    private double debugFrLatForce;
    private double debugRlLatForce;
    private double debugRrLatForce;
    private double debugFlLongForce;
    private double debugFrLongForce;
    private double debugRlLongForce;
    private double debugRrLongForce;
    private double debugFlLoad;
    private double debugFrLoad;
    private double debugRlLoad;
    private double debugRrLoad;
    private double debugFlDemand;
    private double debugFrDemand;
    private double debugRlDemand;
    private double debugRrDemand;
    private double debugFlSlipAngle;
    private double debugFrSlipAngle;
    private double debugRlSlipAngle;
    private double debugRrSlipAngle;
    private double debugDownforce;
    private double ersLiftAndCoastPowerWatts;
    private int ersLiftConfirmTicks;
    private boolean ersLiftAndCoastArmed;

    public void applyDriveInput(float throttle, float brake, float steering) {
        this.inputThrottle = throttle;
        this.inputBrake = brake;
        this.inputSteering = steering;
    }

    public OpenwheelCarEntity(EntityType<? extends OpenwheelCarEntity> entityType, Level level) {
        super(entityType, level);
        blocksBuilding = false;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(GEAR, NEUTRAL_GEAR);
        builder.define(RPM, 900);
        builder.define(SPEED, 0.0f);
        builder.define(DAMAGE, 0.0f);
        builder.define(TYRE_WEAR, 0.0f);
        builder.define(TYRE_SLIP, 0.0f);
        builder.define(CURRENT_LAP_TICKS, 0);
        builder.define(BEST_LAP_TICKS, 0);
        builder.define(COMPLETED_LAP_TICKS, 0);
        builder.define(COMPLETED_LAP_LINGER_TICKS, 0);
        builder.define(COMPLETED_LAP_RESULT, LAP_RESULT_NONE);
        builder.define(CHECKPOINT_ARMED, false);
        builder.define(PIT_STOP_TICKS, 0);
        builder.define(ABS_ENABLED, false);
        builder.define(TRACTION_CONTROL_ENABLED, false);
        builder.define(LIVERY, 0);
        builder.define(LIVERY_BODY, CarLiveryColors.DEFAULT.body());
        builder.define(LIVERY_ACCENT_1, CarLiveryColors.DEFAULT.accent1());
        builder.define(LIVERY_ACCENT_2, CarLiveryColors.DEFAULT.accent2());
        builder.define(LIVERY_TEXTURE, "");
        builder.define(TYRE_COMPOUND, PrototypeCarSetup.DEFAULT.grip());
        builder.define(DRS_ACTIVE, false);
        builder.define(ERS_MODE, ERS_MODE_BALANCED);
        builder.define(ERS_ENERGY, (float) ERS_CAPACITY_DEFAULT_J);
        builder.define(ERS_ACTIVITY, ERS_ACTIVITY_NEUTRAL);
        builder.define(ERS_POWER_KW, 0.0f);
        builder.define(ERS_BALANCED_CLIP_START, ERS_BALANCED_CLIP_START_DEFAULT_KMH);
        builder.define(ERS_BALANCED_CLIP_END, ERS_BALANCED_CLIP_END_DEFAULT_KMH);
        builder.define(ERS_HARVEST_NEGATIVE_START, ERS_HARVEST_NEGATIVE_START_DEFAULT_KMH);
        builder.define(ERS_HARVEST_NEGATIVE_FULL, ERS_HARVEST_NEGATIVE_FULL_DEFAULT_KMH);
        builder.define(ERS_BALANCED_START_POWER, ERS_BALANCED_START_POWER_DEFAULT_KW);
        builder.define(ERS_BALANCED_END_POWER, ERS_BALANCED_END_POWER_DEFAULT_KW);
        builder.define(ERS_HARVEST_START_POWER, ERS_HARVEST_START_POWER_DEFAULT_KW);
        builder.define(ERS_HARVEST_END_POWER, ERS_HARVEST_END_POWER_DEFAULT_KW);
        builder.define(ERS_CAPACITY, (float) ERS_CAPACITY_DEFAULT_J);
        builder.define(ERS_ATTACK_POWER, ERS_ATTACK_POWER_DEFAULT_KW);
        builder.define(ERS_LICO_SPEED_THRESHOLD, ERS_LICO_SPEED_THRESHOLD_DEFAULT_KMH);
        builder.define(ERS_LICO_STEERING_THRESHOLD, (float) ERS_LICO_STEERING_THRESHOLD_DEFAULT_DEGREES);
        builder.define(ERS_LICO_LATERAL_G_THRESHOLD, (float) ERS_LICO_LATERAL_G_THRESHOLD_DEFAULT);
        builder.define(ERS_LICO_HARVEST_POWER, ERS_LICO_HARVEST_POWER_DEFAULT_KW);
        builder.define(ERS_LICO_BALANCED_POWER, ERS_LICO_BALANCED_POWER_DEFAULT_KW);
        builder.define(ERS_LICO_ATTACK_POWER, ERS_LICO_ATTACK_POWER_DEFAULT_KW);
    }

    @Override
    public float maxUpStep() {
        return 1.1f;
    }

    public void setSetup(PrototypeCarSetup setup) {
        this.setup = setup;
        entityData.set(TYRE_COMPOUND, setup.grip());
    }

    public void applyTyreCompound(int compound) {
        setSetup(new PrototypeCarSetup(setup.power(), compound, setup.aero(), setup.gearing()));
    }

    public int getTyreCompound() {
        return entityData.get(TYRE_COMPOUND);
    }

    public PrototypeCarSetup getSetup() {
        return setup;
    }

    public void setDamagePercent(float damage) {
        entityData.set(DAMAGE, Math.max(0.0f, Math.min(100.0f, damage)));
    }

    public void setTyreWearPercent(float tyreWear) {
        entityData.set(TYRE_WEAR, Math.max(0.0f, Math.min(100.0f, tyreWear)));
    }

    public void setLivery(int livery) {
        int clamped = Math.max(0, Math.min(CarLivery.count() - 1, livery));
        entityData.set(LIVERY, clamped);
        setLiveryColors(CarLiveryColors.fromPreset(CarLivery.fromIndex(clamped)));
    }

    public int getLivery() {
        return entityData.get(LIVERY);
    }

    public void setLiveryColors(CarLiveryColors colors) {
        entityData.set(LIVERY_BODY, colors.body());
        entityData.set(LIVERY_ACCENT_1, colors.accent1());
        entityData.set(LIVERY_ACCENT_2, colors.accent2());
    }

    public CarLiveryColors getLiveryColors() {
        return new CarLiveryColors(entityData.get(LIVERY_BODY), entityData.get(LIVERY_ACCENT_1), entityData.get(LIVERY_ACCENT_2));
    }

    public void setLiveryTexture(CarLiveryTexture texture) {
        entityData.set(LIVERY_TEXTURE, texture == null ? "" : texture.id());
    }

    public CarLiveryTexture getLiveryTexture() {
        return new CarLiveryTexture(entityData.get(LIVERY_TEXTURE));
    }

    public int getGear() {
        return entityData.get(GEAR);
    }

    public String getGearLabel() {
        return gearLabel(getGear());
    }

    private static int clampGear(int gear) {
        return Math.max(REVERSE_GEAR, Math.min(MAX_GEAR, gear));
    }

    private static String gearLabel(int gear) {
        if (gear == REVERSE_GEAR) {
            return "R";
        }
        if (gear == NEUTRAL_GEAR) {
            return "N";
        }
        return Integer.toString(gear);
    }

    private static double gearTopSpeed(int gear, PrototypeCarSetup setup) {
        if (gear == NEUTRAL_GEAR) {
            return 0.0;
        }
        return VehiclePhysics.gearTopSpeedBlocksPerTick(gear, setup);
    }

    public int getRpm() {
        return entityData.get(RPM);
    }

    public float getSpeedKmh() {
        return entityData.get(SPEED);
    }

    public float getDamagePercent() {
        return entityData.get(DAMAGE);
    }

    public float getTyreWearPercent() {
        return entityData.get(TYRE_WEAR);
    }

    public float getTyreSlipIntensity() {
        return entityData.get(TYRE_SLIP);
    }

    public boolean isAbsEnabled() {
        return entityData.get(ABS_ENABLED);
    }

    public void setAbsEnabled(boolean enabled) {
        entityData.set(ABS_ENABLED, enabled);
    }

    public void toggleAbs() {
        setAbsEnabled(!isAbsEnabled());
    }

    public boolean isTractionControlEnabled() {
        return entityData.get(TRACTION_CONTROL_ENABLED);
    }

    public void setTractionControlEnabled(boolean enabled) {
        entityData.set(TRACTION_CONTROL_ENABLED, enabled);
    }

    public void toggleTractionControl() {
        setTractionControlEnabled(!isTractionControlEnabled());
    }

    public boolean isDrsActive() {
        return entityData.get(DRS_ACTIVE);
    }

    public void setDrsActive(boolean active) {
        entityData.set(DRS_ACTIVE, active);
    }

    public void toggleDrs() {
        setDrsActive(!isDrsActive());
    }

    public int getErsMode() {
        return entityData.get(ERS_MODE);
    }

    public String getErsModeLabel() {
        return switch (getErsMode()) {
            case ERS_MODE_HARVEST -> "HARVEST";
            case ERS_MODE_ATTACK -> "ATTACK";
            default -> "BALANCED";
        };
    }

    public float getErsEnergyPercent() {
        return (float) (entityData.get(ERS_ENERGY) / getErsCapacityJoules() * 100.0);
    }

    public int getErsActivity() {
        return entityData.get(ERS_ACTIVITY);
    }

    public float getErsPowerKw() {
        return entityData.get(ERS_POWER_KW);
    }

    public double getErsCapacityJoules() {
        return entityData.get(ERS_CAPACITY);
    }

    public static double ersCapacityJoules() {
        return ERS_CAPACITY_DEFAULT_J;
    }

    public void setErsEnergyJoules(double energyJoules) {
        entityData.set(ERS_ENERGY, (float) clamp(energyJoules, 0.0, getErsCapacityJoules()));
    }

    public void setErsMode(int mode) {
        entityData.set(ERS_MODE, clampErsMode(mode));
    }

    public int getErsBalancedClipStartKmh() {
        return entityData.get(ERS_BALANCED_CLIP_START);
    }

    public int getErsBalancedClipEndKmh() {
        return entityData.get(ERS_BALANCED_CLIP_END);
    }

    public int getErsHarvestNegativeStartKmh() {
        return entityData.get(ERS_HARVEST_NEGATIVE_START);
    }

    public int getErsHarvestNegativeFullKmh() {
        return entityData.get(ERS_HARVEST_NEGATIVE_FULL);
    }

    public int getErsBalancedStartPowerKw() {
        return entityData.get(ERS_BALANCED_START_POWER);
    }

    public int getErsBalancedEndPowerKw() {
        return entityData.get(ERS_BALANCED_END_POWER);
    }

    public int getErsHarvestStartPowerKw() {
        return entityData.get(ERS_HARVEST_START_POWER);
    }

    public int getErsHarvestEndPowerKw() {
        return entityData.get(ERS_HARVEST_END_POWER);
    }

    public int getErsAttackPowerKw() {
        return entityData.get(ERS_ATTACK_POWER);
    }

    public int getErsLicoSpeedThresholdKmh() {
        return entityData.get(ERS_LICO_SPEED_THRESHOLD);
    }

    public double getErsLicoSteeringThresholdDegrees() {
        return entityData.get(ERS_LICO_STEERING_THRESHOLD);
    }

    public double getErsLicoLateralGThreshold() {
        return entityData.get(ERS_LICO_LATERAL_G_THRESHOLD);
    }

    public int getErsLicoHarvestPowerKw() {
        return entityData.get(ERS_LICO_HARVEST_POWER);
    }

    public int getErsLicoBalancedPowerKw() {
        return entityData.get(ERS_LICO_BALANCED_POWER);
    }

    public int getErsLicoAttackPowerKw() {
        return entityData.get(ERS_LICO_ATTACK_POWER);
    }

    public void setErsTuning(int balancedClipStartKmh, int balancedClipEndKmh, int harvestNegativeStartKmh, int harvestNegativeFullKmh,
            int balancedStartPowerKw, int balancedEndPowerKw, int harvestStartPowerKw, int harvestEndPowerKw, double capacityJoules) {
        setErsTuning(balancedClipStartKmh, balancedClipEndKmh, harvestNegativeStartKmh, harvestNegativeFullKmh, balancedStartPowerKw, balancedEndPowerKw, harvestStartPowerKw, harvestEndPowerKw, capacityJoules, getErsAttackPowerKw(),
            getErsLicoSpeedThresholdKmh(), getErsLicoSteeringThresholdDegrees(), getErsLicoLateralGThreshold(), getErsLicoHarvestPowerKw(), getErsLicoBalancedPowerKw(), getErsLicoAttackPowerKw());
    }

    public void setErsTuning(int balancedClipStartKmh, int balancedClipEndKmh, int harvestNegativeStartKmh, int harvestNegativeFullKmh,
            int balancedStartPowerKw, int balancedEndPowerKw, int harvestStartPowerKw, int harvestEndPowerKw, double capacityJoules, int attackPowerKw) {
        setErsTuning(balancedClipStartKmh, balancedClipEndKmh, harvestNegativeStartKmh, harvestNegativeFullKmh, balancedStartPowerKw, balancedEndPowerKw, harvestStartPowerKw, harvestEndPowerKw, capacityJoules, attackPowerKw,
            getErsLicoSpeedThresholdKmh(), getErsLicoSteeringThresholdDegrees(), getErsLicoLateralGThreshold(), getErsLicoHarvestPowerKw(), getErsLicoBalancedPowerKw(), getErsLicoAttackPowerKw());
    }

    public void setErsTuning(int balancedClipStartKmh, int balancedClipEndKmh, int harvestNegativeStartKmh, int harvestNegativeFullKmh,
            int balancedStartPowerKw, int balancedEndPowerKw, int harvestStartPowerKw, int harvestEndPowerKw, double capacityJoules, int attackPowerKw,
            int licoSpeedThresholdKmh, double licoSteeringThresholdDegrees, double licoLateralGThreshold, int licoHarvestPowerKw, int licoBalancedPowerKw, int licoAttackPowerKw) {
        int balancedStart = clampInt(balancedClipStartKmh, 220, 350);
        int balancedEnd = Math.max(balancedStart + 10, clampInt(balancedClipEndKmh, 230, 360));
        int harvestStart = clampInt(harvestNegativeStartKmh, 220, 360);
        int harvestFull = Math.max(harvestStart + 10, clampInt(harvestNegativeFullKmh, 230, 370));
        double currentCapacity = getErsCapacityJoules();
        double newCapacity = clamp(capacityJoules, 2_000_000.0, 12_000_000.0);
        entityData.set(ERS_BALANCED_CLIP_START, balancedStart);
        entityData.set(ERS_BALANCED_CLIP_END, balancedEnd);
        entityData.set(ERS_HARVEST_NEGATIVE_START, harvestStart);
        entityData.set(ERS_HARVEST_NEGATIVE_FULL, harvestFull);
        entityData.set(ERS_BALANCED_START_POWER, clampInt(balancedStartPowerKw, 0, 350));
        entityData.set(ERS_BALANCED_END_POWER, clampInt(balancedEndPowerKw, 0, 350));
        entityData.set(ERS_HARVEST_START_POWER, clampInt(harvestStartPowerKw, -350, 0));
        entityData.set(ERS_HARVEST_END_POWER, clampInt(harvestEndPowerKw, -350, 0));
        entityData.set(ERS_ATTACK_POWER, clampInt(attackPowerKw, 0, 350));
        entityData.set(ERS_LICO_SPEED_THRESHOLD, clampInt(licoSpeedThresholdKmh, 180, 360));
        entityData.set(ERS_LICO_STEERING_THRESHOLD, (float) clamp(licoSteeringThresholdDegrees, 0.2, 8.0));
        entityData.set(ERS_LICO_LATERAL_G_THRESHOLD, (float) clamp(licoLateralGThreshold, 0.05, 1.0));
        entityData.set(ERS_LICO_HARVEST_POWER, clampInt(licoHarvestPowerKw, -350, 0));
        entityData.set(ERS_LICO_BALANCED_POWER, clampInt(licoBalancedPowerKw, -350, 0));
        entityData.set(ERS_LICO_ATTACK_POWER, clampInt(licoAttackPowerKw, -350, 0));
        entityData.set(ERS_CAPACITY, (float) newCapacity);
        if (currentCapacity > 0.0 && newCapacity != currentCapacity) {
            setErsEnergyJoules(entityData.get(ERS_ENERGY) * newCapacity / currentCapacity);
        } else {
            setErsEnergyJoules(entityData.get(ERS_ENERGY));
        }
    }

    public void setErsThresholds(int balancedClipStartKmh, int balancedClipEndKmh, int harvestNegativeStartKmh, int harvestNegativeFullKmh) {
        setErsTuning(
            balancedClipStartKmh,
            balancedClipEndKmh,
            harvestNegativeStartKmh,
            harvestNegativeFullKmh,
            getErsBalancedStartPowerKw(),
            getErsBalancedEndPowerKw(),
            getErsHarvestStartPowerKw(),
            getErsHarvestEndPowerKw(),
            getErsCapacityJoules(),
            getErsAttackPowerKw(),
            getErsLicoSpeedThresholdKmh(),
            getErsLicoSteeringThresholdDegrees(),
            getErsLicoLateralGThreshold(),
            getErsLicoHarvestPowerKw(),
            getErsLicoBalancedPowerKw(),
            getErsLicoAttackPowerKw()
        );
    }

    public void applyErsLimits(int maxCapacityMj, int maxBalancedDeployKw, int maxAttackDeployKw, int maxHarvestNegativeKw) {
        setErsTuning(
            getErsBalancedClipStartKmh(),
            getErsBalancedClipEndKmh(),
            getErsHarvestNegativeStartKmh(),
            getErsHarvestNegativeFullKmh(),
            Math.min(getErsBalancedStartPowerKw(), maxBalancedDeployKw),
            Math.min(getErsBalancedEndPowerKw(), maxBalancedDeployKw),
            -Math.min(Math.abs(getErsHarvestStartPowerKw()), maxHarvestNegativeKw),
            -Math.min(Math.abs(getErsHarvestEndPowerKw()), maxHarvestNegativeKw),
            Math.min(getErsCapacityJoules(), maxCapacityMj * 1_000_000.0),
            Math.min(getErsAttackPowerKw(), maxAttackDeployKw),
            getErsLicoSpeedThresholdKmh(),
            getErsLicoSteeringThresholdDegrees(),
            getErsLicoLateralGThreshold(),
            -Math.min(Math.abs(getErsLicoHarvestPowerKw()), maxHarvestNegativeKw),
            -Math.min(Math.abs(getErsLicoBalancedPowerKw()), maxHarvestNegativeKw),
            -Math.min(Math.abs(getErsLicoAttackPowerKw()), maxHarvestNegativeKw)
        );
    }

    public void cycleErsMode(int direction) {
        if (direction == 0) {
            return;
        }
        int mode = Math.floorMod(getErsMode() + (direction > 0 ? 1 : -1), 3);
        setErsMode(mode);
        playShiftFeedback(mode == ERS_MODE_ATTACK ? 1.25f : mode == ERS_MODE_HARVEST ? 0.75f : 1.0f);
        messageDriver(Component.literal("ERS " + getErsModeLabel()));
    }

    public void cycleErsModeLocal(int direction) {
        if (direction != 0) {
            setErsMode(Math.floorMod(getErsMode() + (direction > 0 ? 1 : -1), 3));
        }
    }

    private static int clampErsMode(int mode) {
        return Math.max(ERS_MODE_HARVEST, Math.min(ERS_MODE_ATTACK, mode));
    }

    private static int clampInt(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    public float getFrontWheelSteerDegrees() {
        return (float) Math.toDegrees(steeringAngle);
    }

    public double getDebugVelocityLong() { return debugVelocityLong; }
    public double getDebugVelocityLat() { return debugVelocityLat; }
    public double getDebugYawRate() { return yawRate; }
    public double getDebugDriveForce() { return debugDriveForce; }
    public double getDebugDragForce() { return debugDragForce; }
    public double getDebugFlLatForce() { return debugFlLatForce; }
    public double getDebugFrLatForce() { return debugFrLatForce; }
    public double getDebugRlLatForce() { return debugRlLatForce; }
    public double getDebugRrLatForce() { return debugRrLatForce; }
    public double getDebugFlLongForce() { return debugFlLongForce; }
    public double getDebugFrLongForce() { return debugFrLongForce; }
    public double getDebugRlLongForce() { return debugRlLongForce; }
    public double getDebugRrLongForce() { return debugRrLongForce; }
    public double getDebugFlLoad() { return debugFlLoad; }
    public double getDebugFrLoad() { return debugFrLoad; }
    public double getDebugRlLoad() { return debugRlLoad; }
    public double getDebugRrLoad() { return debugRrLoad; }
    public double getDebugFlDemand() { return debugFlDemand; }
    public double getDebugFrDemand() { return debugFrDemand; }
    public double getDebugRlDemand() { return debugRlDemand; }
    public double getDebugRrDemand() { return debugRrDemand; }
    public double getDebugFlSlipAngleDegrees() { return Math.toDegrees(debugFlSlipAngle); }
    public double getDebugFrSlipAngleDegrees() { return Math.toDegrees(debugFrSlipAngle); }
    public double getDebugRlSlipAngleDegrees() { return Math.toDegrees(debugRlSlipAngle); }
    public double getDebugRrSlipAngleDegrees() { return Math.toDegrees(debugRrSlipAngle); }
    public double getDebugDownforce() { return debugDownforce; }

    public Vec3 getWheelSoundPosition(double sideOffset, double lengthOffset) {
        double yaw = Math.toRadians(getYRot());
        Vec3 forward = new Vec3(-Math.sin(yaw), 0.0, Math.cos(yaw));
        Vec3 right = new Vec3(forward.z, 0.0, -forward.x);
        return position().add(right.scale(sideOffset)).add(forward.scale(lengthOffset)).add(0.0, 0.25, 0.0);
    }

    public int getCurrentLapTicks() {
        return entityData.get(CURRENT_LAP_TICKS);
    }

    public int getBestLapTicks() {
        return entityData.get(BEST_LAP_TICKS);
    }

    public int getCompletedLapTicks() {
        return entityData.get(COMPLETED_LAP_TICKS);
    }

    public int getCompletedLapLingerTicks() {
        return entityData.get(COMPLETED_LAP_LINGER_TICKS);
    }

    public int getCompletedLapResult() {
        return entityData.get(COMPLETED_LAP_RESULT);
    }

    public boolean hasCheckpoint() {
        return entityData.get(CHECKPOINT_ARMED);
    }

    public boolean isInPitStop() {
        return entityData.get(PIT_STOP_TICKS) > 0;
    }

    public int getPitStopTicks() {
        return entityData.get(PIT_STOP_TICKS);
    }

    public boolean tryStartPitStop(Player player) {
        if (!isOnPitStopMark()) {
            messageDriver(Component.literal("Pit stop only available on the pit stop mark"));
            return false;
        }
        double speed = Math.sqrt(getDeltaMovement().x * getDeltaMovement().x + getDeltaMovement().z * getDeltaMovement().z);
        if (speed > 0.05) {
            messageDriver(Component.literal("Come to a stop before pit service"));
            return false;
        }
        if (isInPitStop()) {
            return false;
        }
        int rubberAvailable = player.getInventory().countItem(OWRItems.RUBBER.get());
        if (rubberAvailable < PIT_RUBBER_COST) {
            messageDriver(Component.literal("Need " + PIT_RUBBER_COST + " rubber for pit stop"));
            return false;
        }
        player.getInventory().clearOrCountMatchingItems(item -> item.is(OWRItems.RUBBER.get()), PIT_RUBBER_COST, player.inventoryMenu.getCraftSlots());
        entityData.set(PIT_STOP_TICKS, PIT_STOP_DURATION);
        messageDriver(Component.literal("Pit stop: servicing..."));
        return true;
    }

    private void trySwapTyres(Player player, InteractionHand hand, ItemStack heldStack) {
        if (!isOnPitStopMark()) {
            messageDriver(Component.literal("Tyre change only available on the pit stop mark"));
            return;
        }
        double speed = Math.sqrt(getDeltaMovement().x * getDeltaMovement().x + getDeltaMovement().z * getDeltaMovement().z);
        if (speed > 0.05) {
            messageDriver(Component.literal("Come to a stop before tyre change"));
            return;
        }
        if (isInPitStop() || player.getCooldowns().isOnCooldown(heldStack)) {
            return;
        }

        int newCompound = TyreItem.getCompound(heldStack);
        int newRemainingPercent = TyreItem.getRemainingPercent(heldStack);
        int oldCompound = getTyreCompound();
        int oldRemainingPercent = TyreItem.normalizeRemainingPercent(100.0 - getTyreWearPercent());

        applyTyreCompound(newCompound);
        setTyreWearPercent(100.0f - newRemainingPercent);
        player.getCooldowns().addCooldown(heldStack, 10);
        if (!player.getAbilities().instabuild) {
            heldStack.shrink(1);
        }
        ItemStack oldTyres = TyreItem.create(oldCompound, 1, oldRemainingPercent);
        if (!player.addItem(oldTyres)) {
            player.drop(oldTyres, false);
        }
        messageDriver(Component.literal("Tyres changed to C" + (newCompound + 1) + " (" + newRemainingPercent + "%)"));
    }

    public void crossStartFinishLine(BlockPos pos, Direction markerFacing) {
        long packed = pos.asLong();
        if (packed == lastStartFinishMarker && level().getGameTime() == lastStartFinishTriggerAt) {
            return;
        }
        lastStartFinishMarker = packed;
        lastStartFinishTriggerAt = level().getGameTime();
        if (!isForwardPass(markerFacing)) {
            invalidateLap("reverse pass");
            return;
        }

        long gameTime = level().getGameTime();
        if (lapStartedAt >= 0L) {
            if (isCheckpointCheckEnabled() && visitedCheckpoints.isEmpty()) {
                invalidateLap("no checkpoints crossed");
                startLap(gameTime, Component.literal("Lap started — cross all checkpoints"));
                return;
            }
            completeLap(pos, gameTime);
        } else {
            messageDriver(Component.literal("Lap started"));
        }

        startLap(gameTime, null);
    }

    public void crossCheckpoint(BlockPos pos, Direction markerFacing) {
        if (!isCheckpointCheckEnabled()) {
            return;
        }
        if (!isForwardPass(markerFacing)) {
            invalidateLap("reverse checkpoint pass");
            return;
        }
        if (lapStartedAt < 0L) {
            return;
        }
        long packed = pos.asLong();
        if (!visitedCheckpointSet.add(packed)) {
            return;
        }
        visitedCheckpoints.add(packed);
        entityData.set(CHECKPOINT_ARMED, true);
        messageDriver(Component.literal("CP " + visitedCheckpoints.size()));
    }

    private void startLap(long gameTime, @Nullable Component message) {
        lapStartedAt = gameTime;
        resetLapProgress();
        if (message != null) {
            messageDriver(message);
        }
    }

    private void completeLap(BlockPos startFinishPos, long gameTime) {
        int lapTicks = Math.max(1, (int)(gameTime - lapStartedAt));
        if (!(level() instanceof ServerLevel serverLevel) || !(getControllingPassenger() instanceof Player player)) {
            return;
        }
        int minimumLapTicks = OWRRaceControlState.get(serverLevel).getMinimumValidLapTicks();
        if (lapTicks <= minimumLapTicks) {
            messageDriver(Component.translatable("message.openwheelracing.race_director.lap_ignored", String.format("%.1f", minimumLapTicks / 20.0f)));
            return;
        }
        entityData.set(CURRENT_LAP_TICKS, lapTicks);
        OWRLapRecords records = OWRLapRecords.get(serverLevel);
        int previousBest = records.getBestLap(player.getUUID());
        int previousOverallBest = records.getOverallBestLapTicks();
        records.recordLap(
            player.getUUID(),
            player.getScoreboardName(),
            lapTicks,
            gameTime,
            serverLevel.dimension().identifier().toString(),
            startFinishPos.asLong(),
            visitedCheckpoints.size(),
            new OWRLapRecords.CarSnapshot(
                setup.power(),
                setup.grip(),
                setup.aero(),
                setup.gearing(),
                Math.round(getDamagePercent()),
                Math.round(getTyreWearPercent()),
                isAbsEnabled()
            )
        );
        int bestLap = records.getBestLap(player.getUUID());
        boolean personalBest = bestLap != 0 && bestLap != previousBest && bestLap == lapTicks;
        int lapResult = previousOverallBest == 0 || lapTicks < previousOverallBest
            ? LAP_RESULT_OVERALL_BEST
            : personalBest ? LAP_RESULT_PERSONAL_BEST : LAP_RESULT_SLOWER;
        entityData.set(BEST_LAP_TICKS, bestLap);
        entityData.set(COMPLETED_LAP_TICKS, lapTicks);
        entityData.set(COMPLETED_LAP_LINGER_TICKS, COMPLETED_LAP_LINGER_DURATION);
        entityData.set(COMPLETED_LAP_RESULT, lapResult);
        OWRNetwork.broadcastRankingBoard(serverLevel.getServer(), serverLevel);
        awardCompleteLapAdvancement(serverLevel, player);
        messageDriver(Component.literal("Lap: " + formatLapTime(lapTicks)
            + " | CPs: " + visitedCheckpoints.size()
            + (personalBest ? " | Personal best" : "")));
    }

    private void awardCompleteLapAdvancement(ServerLevel serverLevel, Player player) {
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return;
        }
        serverPlayer.getAdvancements().award(serverLevel.getServer().getAdvancements().get(Identifier.fromNamespaceAndPath("openwheelracing", "progression/complete_lap")), "complete_lap");
    }

    private void resetLapProgress() {
        visitedCheckpoints.clear();
        visitedCheckpointSet.clear();
        entityData.set(CHECKPOINT_ARMED, false);
        entityData.set(CURRENT_LAP_TICKS, 0);
    }

    public void shiftUp() {
        if (getGear() < MAX_GEAR) {
            setGear(clampGear(getGear() + 1));
            playShiftFeedback(1.1f);
            messageDriver(Component.literal("Gear " + getGearLabel()));
            logShift("up");
        }
    }

    public void shiftDown() {
        if (getGear() > REVERSE_GEAR) {
            setGear(clampGear(getGear() - 1));
            playShiftFeedback(0.8f);
            messageDriver(Component.literal("Gear " + getGearLabel()));
            logShift("down");
        }
    }

    public void shiftLocal(int direction) {
        if (direction > 0 && getGear() < MAX_GEAR) {
            setGear(clampGear(getGear() + 1));
        } else if (direction < 0 && getGear() > REVERSE_GEAR) {
            setGear(clampGear(getGear() - 1));
        }
    }

    private void setGear(int gear) {
        int previousGear = getGear();
        entityData.set(GEAR, gear);
        if (previousGear == NEUTRAL_GEAR && gear != NEUTRAL_GEAR && getRpm() > LAUNCH_RPM) {
            clutchReleaseTicks = CLUTCH_RELEASE_TICKS;
            clutchReleaseRpm = getRpm();
        }
        if (gear == NEUTRAL_GEAR) {
            clutchReleaseTicks = 0;
            clutchReleaseRpm = 0;
        }
    }

    @Override
    public void tick() {
        super.tick();

        if (!level().isClientSide()) {
            boolean ridden = getControllingPassenger() != null;
            if (wasRiddenLastTick && !ridden && getDeltaMovement().horizontalDistance() > MAX_REASONABLE_MOVEMENT_BLOCKS_PER_TICK) {
                logMovementWarning("dismount with excessive velocity", position(), getDeltaMovement(), getDeltaMovement(), 0.0, 0.0, 0.0, getCurrentSurface());
            }
            wasRiddenLastTick = ridden;
            tickLapTimer();
            tickCompletedLapLinger();
            tickPitStop();
            Vec3 preDelta = getDeltaMovement();
            if (preDelta.horizontalDistanceSqr() > 1.0E-4) {
                clearHollowCollisionBlocks(false);
            }
            tickMovement(true);
            clearHollowCollisionBlocks(true);
            tickImpactDamage();
            tickWarnings();
        }
    }

    @Override
    public boolean isPickable() {
        return true;
    }

    @Override
    public boolean isAttackable() {
        return true;
    }

    @Override
    public InteractionResult interact(Player player, InteractionHand hand) {
        if (level().isClientSide()) {
            return InteractionResult.PASS;
        }

        ItemStack heldStack = player.getItemInHand(hand);
        if (heldStack.is(OWRItems.TIRES.get())) {
            trySwapTyres(player, hand, heldStack);
            return InteractionResult.CONSUME;
        }

        // Sneak + empty hand on empty car → pick up as item
        if (getPassengers().isEmpty() && player.isShiftKeyDown() && heldStack.isEmpty()) {
            ItemStack item = PrototypeCarItem.create(setup, getDamagePercent(), getTyreWearPercent(), getLivery(), getErsMode(), Math.round(getErsEnergyPercent()));
            PrototypeCarItem.setLiveryColors(item, getLiveryColors());
            PrototypeCarItem.setLiveryTexture(item, getLiveryTexture());
            if (!player.addItem(item)) {
                player.drop(item, false);
            }
            discard();
            return InteractionResult.CONSUME;
        }

        // Seated driver sneak-right-clicks to request pit stop
        if (hasPassenger(player) && player.isShiftKeyDown()) {
            tryStartPitStop(player);
            return InteractionResult.CONSUME;
        }

        if (getPassengers().isEmpty() && player.startRiding(this)) {
            prepareForDriver(player);
            return InteractionResult.CONSUME;
        }

        return InteractionResult.PASS;
    }

    @Override
    protected boolean canAddPassenger(Entity passenger) {
        return getPassengers().isEmpty() && passenger instanceof Player;
    }

    @Override
    @Nullable
    public LivingEntity getControllingPassenger() {
        Entity passenger = getFirstPassenger();
        return passenger instanceof LivingEntity livingEntity ? livingEntity : null;
    }

    @Override
    public void positionRider(Entity passenger, MoveFunction callback) {
        if (hasPassenger(passenger)) {
            Vec3 seat = SEAT_OFFSET.yRot((float) -Math.toRadians(getYRot()));
            double riderX = getX() + seat.x;
            double riderY = getY() + seat.y;
            double riderZ = getZ() + seat.z;
            callback.accept(passenger, riderX, riderY, riderZ);
        }
    }

    @Override
    public Vec3 getDismountLocationForPassenger(LivingEntity passenger) {
        for (Vec3 offset : DISMOUNT_OFFSETS) {
            Vec3 location = position().add(offset.yRot((float) -Math.toRadians(getYRot())));
            if (level().noCollision(passenger, passenger.getBoundingBox().move(location.subtract(passenger.position())))) {
                return location;
            }
        }
        return super.getDismountLocationForPassenger(passenger);
    }

    @Override
    public boolean hurtServer(ServerLevel level, net.minecraft.world.damagesource.DamageSource damageSource, float amount) {
        if (isRemoved()) return false;
        addDamage(amount * 4.0f);
        if (getDamagePercent() >= 100.0f) {
            destroyIntoMaterials(level);
        }
        return true;
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        setSetup(new PrototypeCarSetup(
            input.getIntOr("Power", PrototypeCarSetup.DEFAULT.power()),
            input.getIntOr("Grip", PrototypeCarSetup.DEFAULT.grip()),
            input.getIntOr("Aero", PrototypeCarSetup.DEFAULT.aero()),
            input.getIntOr("Gearing", PrototypeCarSetup.DEFAULT.gearing())
        ));
        entityData.set(GEAR, clampGear(input.getIntOr("Gear", NEUTRAL_GEAR)));
        entityData.set(RPM, input.getIntOr("Rpm", 900));
        clutchReleaseTicks = input.getIntOr("ClutchReleaseTicks", 0);
        clutchReleaseRpm = input.getIntOr("ClutchReleaseRpm", 0);
        entityData.set(DAMAGE, (float) input.getDoubleOr("Damage", 0.0));
        entityData.set(TYRE_WEAR, (float) input.getDoubleOr("TyreWear", 0.0));
        setLivery(input.getIntOr("Livery", 0));
        setLiveryColors(new CarLiveryColors(
            input.getIntOr("LiveryBody", getLiveryColors().body()),
            input.getIntOr("LiveryAccent1", getLiveryColors().accent1()),
            input.getIntOr("LiveryAccent2", getLiveryColors().accent2())
        ));
        setLiveryTexture(new CarLiveryTexture(input.getStringOr("LiveryTexture", "")));
        entityData.set(CURRENT_LAP_TICKS, input.getIntOr("CurrentLapTicks", 0));
        entityData.set(BEST_LAP_TICKS, input.getIntOr("BestLapTicks", 0));
        entityData.set(CHECKPOINT_ARMED, input.getBooleanOr("CheckpointArmed", false));
        setAbsEnabled(input.getBooleanOr("AbsEnabled", false));
        setTractionControlEnabled(input.getBooleanOr("TractionControlEnabled", false));
        setErsMode(input.getIntOr("ErsMode", ERS_MODE_BALANCED));
        setErsTuning(
            input.getIntOr("ErsBalancedClipStart", ERS_BALANCED_CLIP_START_DEFAULT_KMH),
            input.getIntOr("ErsBalancedClipEnd", ERS_BALANCED_CLIP_END_DEFAULT_KMH),
            input.getIntOr("ErsHarvestNegativeStart", ERS_HARVEST_NEGATIVE_START_DEFAULT_KMH),
            input.getIntOr("ErsHarvestNegativeFull", ERS_HARVEST_NEGATIVE_FULL_DEFAULT_KMH),
            input.getIntOr("ErsBalancedStartPower", ERS_BALANCED_START_POWER_DEFAULT_KW),
            input.getIntOr("ErsBalancedEndPower", ERS_BALANCED_END_POWER_DEFAULT_KW),
            input.getIntOr("ErsHarvestStartPower", ERS_HARVEST_START_POWER_DEFAULT_KW),
            input.getIntOr("ErsHarvestEndPower", ERS_HARVEST_END_POWER_DEFAULT_KW),
            input.getDoubleOr("ErsCapacity", ERS_CAPACITY_DEFAULT_J),
            input.getIntOr("ErsAttackPower", ERS_ATTACK_POWER_DEFAULT_KW),
            input.getIntOr("ErsLicoSpeedThreshold", ERS_LICO_SPEED_THRESHOLD_DEFAULT_KMH),
            input.getDoubleOr("ErsLicoSteeringThreshold", ERS_LICO_STEERING_THRESHOLD_DEFAULT_DEGREES),
            input.getDoubleOr("ErsLicoLateralGThreshold", ERS_LICO_LATERAL_G_THRESHOLD_DEFAULT),
            input.getIntOr("ErsLicoHarvestPower", ERS_LICO_HARVEST_POWER_DEFAULT_KW),
            input.getIntOr("ErsLicoBalancedPower", ERS_LICO_BALANCED_POWER_DEFAULT_KW),
            input.getIntOr("ErsLicoAttackPower", ERS_LICO_ATTACK_POWER_DEFAULT_KW)
        );
        setErsEnergyJoules(input.getDoubleOr("ErsEnergy", getErsCapacityJoules()));
        steeringAngle = input.getDoubleOr("SteeringAngle", 0.0);
        yawRate = input.getDoubleOr("YawRate", 0.0);
        lapStartedAt = input.getLongOr("LapStartedAt", -1L);
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        output.putInt("Power", setup.power());
        output.putInt("Grip", setup.grip());
        output.putInt("Aero", setup.aero());
        output.putInt("Gearing", setup.gearing());
        output.putInt("Gear", getGear());
        output.putInt("Rpm", getRpm());
        output.putInt("ClutchReleaseTicks", clutchReleaseTicks);
        output.putInt("ClutchReleaseRpm", clutchReleaseRpm);
        output.putDouble("Damage", getDamagePercent());
        output.putDouble("TyreWear", getTyreWearPercent());
        output.putInt("Livery", getLivery());
        CarLiveryColors liveryColors = getLiveryColors();
        output.putInt("LiveryBody", liveryColors.body());
        output.putInt("LiveryAccent1", liveryColors.accent1());
        output.putInt("LiveryAccent2", liveryColors.accent2());
        output.putString("LiveryTexture", getLiveryTexture().id());
        output.putInt("CurrentLapTicks", getCurrentLapTicks());
        output.putInt("BestLapTicks", getBestLapTicks());
        output.putBoolean("CheckpointArmed", hasCheckpoint());
        output.putBoolean("AbsEnabled", isAbsEnabled());
        output.putBoolean("TractionControlEnabled", isTractionControlEnabled());
        output.putInt("ErsMode", getErsMode());
        output.putDouble("ErsEnergy", entityData.get(ERS_ENERGY));
        output.putInt("ErsBalancedClipStart", getErsBalancedClipStartKmh());
        output.putInt("ErsBalancedClipEnd", getErsBalancedClipEndKmh());
        output.putInt("ErsHarvestNegativeStart", getErsHarvestNegativeStartKmh());
        output.putInt("ErsHarvestNegativeFull", getErsHarvestNegativeFullKmh());
        output.putInt("ErsBalancedStartPower", getErsBalancedStartPowerKw());
        output.putInt("ErsBalancedEndPower", getErsBalancedEndPowerKw());
        output.putInt("ErsHarvestStartPower", getErsHarvestStartPowerKw());
        output.putInt("ErsHarvestEndPower", getErsHarvestEndPowerKw());
        output.putInt("ErsAttackPower", getErsAttackPowerKw());
        output.putInt("ErsLicoSpeedThreshold", getErsLicoSpeedThresholdKmh());
        output.putDouble("ErsLicoSteeringThreshold", getErsLicoSteeringThresholdDegrees());
        output.putDouble("ErsLicoLateralGThreshold", getErsLicoLateralGThreshold());
        output.putInt("ErsLicoHarvestPower", getErsLicoHarvestPowerKw());
        output.putInt("ErsLicoBalancedPower", getErsLicoBalancedPowerKw());
        output.putInt("ErsLicoAttackPower", getErsLicoAttackPowerKw());
        output.putDouble("ErsCapacity", getErsCapacityJoules());
        output.putDouble("SteeringAngle", steeringAngle);
        output.putDouble("YawRate", yawRate);
        output.putLong("LapStartedAt", lapStartedAt);
    }

    private boolean isOnPitStopMark() {
        BlockPos basePos = BlockPos.containing(getX(), getBoundingBox().minY - 0.05, getZ());
        return level().getBlockState(basePos).is(OWRBlocks.PIT_STOP_MARK.get());
    }

    private void tickCompletedLapLinger() {
        int ticks = entityData.get(COMPLETED_LAP_LINGER_TICKS);
        if (ticks <= 0) {
            if (entityData.get(COMPLETED_LAP_RESULT) != LAP_RESULT_NONE) {
                entityData.set(COMPLETED_LAP_RESULT, LAP_RESULT_NONE);
            }
            return;
        }
        entityData.set(COMPLETED_LAP_LINGER_TICKS, ticks - 1);
    }

    private void tickPitStop() {
        int ticks = entityData.get(PIT_STOP_TICKS);
        if (ticks <= 0) {
            return;
        }
        // Block inputs during service
        inputThrottle = 0;
        inputBrake = 0;
        inputSteering = 0;

        ticks--;
        entityData.set(PIT_STOP_TICKS, ticks);

        if (ticks == 0) {
            entityData.set(DAMAGE, 0.0f);
            entityData.set(TYRE_WEAR, 0.0f);
            messageDriver(Component.literal("Pit stop complete — car serviced"));
        }
    }

    private void tickLapTimer() {
        if (lapStartedAt >= 0L) {
            Entity passenger = getControllingPassenger();
            if (!(passenger instanceof Player player) || !player.isAlive()) {
                invalidateLap("driver left car");
                return;
            }
            if (isOffTrackCheckEnabled()) {
                long time = level().getGameTime();
                if (time - lastOffTrackCheckAt >= 4L) {
                    lastOffTrackCheckAt = time;
                    if (!isOnTrackSurface()) {
                        invalidateLap("four wheels off track");
                        return;
                    }
                }
            }
            entityData.set(CURRENT_LAP_TICKS, Math.max(0, (int) (level().getGameTime() - lapStartedAt)));
        }
    }

    // ── Surface profiles ──────────────────────────────────────────────────────
    private enum SurfaceProfile {
        //                          grip   drag   sinkDrag  wearMult  lapValid
        ASPHALT(                    VehiclePhysics.ASPHALT_GRIP,    VehiclePhysics.ASPHALT_DRAG,    0.00,     1.0,     true),
        CONCRETE(                   0.93,                          0.990,                          0.00,     1.1,     true),
        KERB(                       0.78,                          0.991,                          0.01,     1.8,     true),
        PIT_LANE(                   VehiclePhysics.PIT_LANE_GRIP,   VehiclePhysics.PIT_LANE_DRAG,   0.00,     0.6,     true),
        DIRT(                       0.58,  0.925,  0.10,     1.4,     false),
        GRASS(                      0.42,  0.900,  0.14,     1.6,     false),
        GRAVEL(                     0.45,  0.910,  0.24,     2.0,     false),
        SAND(                       0.28,  0.860,  0.40,     2.4,     false),
        WATER(                      0.02,  0.720,  0.35,     0.2,     false);

        final double grip;
        final double drag;
        final double sinkDrag;
        final double wearMult;
        final boolean countsAsTrack;

        SurfaceProfile(double grip, double drag, double sinkDrag, double wearMult, boolean countsAsTrack) {
            this.grip           = grip;
            this.drag           = drag;
            this.sinkDrag       = sinkDrag;
            this.wearMult       = wearMult;
            this.countsAsTrack  = countsAsTrack;
        }
    }

    private SurfaceProfile getSurfaceAt(Vec3 pos) {
        if (isWaterAt(pos)) {
            return SurfaceProfile.WATER;
        }
        BlockPos basePos = BlockPos.containing(pos.x, getBoundingBox().minY - 0.05, pos.z);
        Block block = level().getBlockState(basePos).getBlock();
        if (block == OWRBlocks.ASPHALT_TRACK.get()
                || block == OWRBlocks.ASPHALT_TRACK_SLAB.get()
                || block == OWRBlocks.START_FINISH.get()
                || block == OWRBlocks.CHECKPOINT.get()) return SurfaceProfile.ASPHALT;
        if (block == OWRBlocks.PIT_LANE.get()
                || block == OWRBlocks.PIT_LANE_SLAB.get()
                || block == OWRBlocks.PIT_STOP_MARK.get()) return SurfaceProfile.PIT_LANE;
        if (block == OWRBlocks.KERB.get()) return SurfaceProfile.KERB;
        if (isPavedBlock(block)) return SurfaceProfile.CONCRETE;
        if (block == Blocks.GRASS_BLOCK
                || block == Blocks.DIRT_PATH
                || block == Blocks.PODZOL
                || block == Blocks.MYCELIUM
                || block == Blocks.MOSS_BLOCK) return SurfaceProfile.GRASS;
        if (block == Blocks.GRAVEL) return SurfaceProfile.GRAVEL;
        if (block == Blocks.SAND
                || block == Blocks.RED_SAND
                || block == Blocks.SUSPICIOUS_SAND) return SurfaceProfile.SAND;
        return SurfaceProfile.DIRT;
    }

    private boolean isWaterAt(Vec3 pos) {
        BlockPos waterPos = BlockPos.containing(pos.x, getBoundingBox().minY + 0.15, pos.z);
        return level().getFluidState(waterPos).is(FluidTags.WATER)
            || level().getFluidState(waterPos.above()).is(FluidTags.WATER);
    }

    private static boolean isPavedBlock(Block block) {
        return block == Blocks.WHITE_CONCRETE
                || block == Blocks.ORANGE_CONCRETE
                || block == Blocks.MAGENTA_CONCRETE
                || block == Blocks.LIGHT_BLUE_CONCRETE
                || block == Blocks.YELLOW_CONCRETE
                || block == Blocks.LIME_CONCRETE
                || block == Blocks.PINK_CONCRETE
                || block == Blocks.GRAY_CONCRETE
                || block == Blocks.LIGHT_GRAY_CONCRETE
                || block == Blocks.CYAN_CONCRETE
                || block == Blocks.PURPLE_CONCRETE
                || block == Blocks.BLUE_CONCRETE
                || block == Blocks.BROWN_CONCRETE
                || block == Blocks.GREEN_CONCRETE
                || block == Blocks.RED_CONCRETE
                || block == Blocks.BLACK_CONCRETE
                || block == Blocks.STONE
                || block == Blocks.STONE_SLAB
                || block == Blocks.SMOOTH_STONE
                || block == Blocks.SMOOTH_STONE_SLAB
                || block == Blocks.STONE_BRICKS
                || block == Blocks.STONE_BRICK_SLAB
                || block == Blocks.CRACKED_STONE_BRICKS
                || block == Blocks.MOSSY_STONE_BRICKS
                || block == Blocks.MOSSY_STONE_BRICK_SLAB
                || block == Blocks.ANDESITE
                || block == Blocks.ANDESITE_SLAB
                || block == Blocks.POLISHED_ANDESITE
                || block == Blocks.POLISHED_ANDESITE_SLAB
                || block == Blocks.DIORITE
                || block == Blocks.DIORITE_SLAB
                || block == Blocks.POLISHED_DIORITE
                || block == Blocks.POLISHED_DIORITE_SLAB
                || block == Blocks.GRANITE
                || block == Blocks.GRANITE_SLAB
                || block == Blocks.POLISHED_GRANITE
                || block == Blocks.POLISHED_GRANITE_SLAB
                || block == Blocks.DEEPSLATE
                || block == Blocks.COBBLED_DEEPSLATE
                || block == Blocks.COBBLED_DEEPSLATE_SLAB
                || block == Blocks.POLISHED_DEEPSLATE
                || block == Blocks.POLISHED_DEEPSLATE_SLAB
                || block == Blocks.DEEPSLATE_BRICKS
                || block == Blocks.DEEPSLATE_BRICK_SLAB
                || block == Blocks.DEEPSLATE_TILES
                || block == Blocks.DEEPSLATE_TILE_SLAB;
    }

    private SurfaceProfile getCurrentSurface() {
        return getSurfaceAt(position());
    }

    private boolean isOnTrackSurface() {
        double yaw = Math.toRadians(getYRot());
        Vec3 forward = new Vec3(-Math.sin(yaw), 0.0, Math.cos(yaw));
        Vec3 right = new Vec3(forward.z, 0.0, -forward.x);
        double minSide = TRACK_WHEEL_SIDE_OFFSETS[0] + TRACK_PATCH_SIDE_OFFSETS[0];
        double maxSide = TRACK_WHEEL_SIDE_OFFSETS[TRACK_WHEEL_SIDE_OFFSETS.length - 1] + TRACK_PATCH_SIDE_OFFSETS[TRACK_PATCH_SIDE_OFFSETS.length - 1];
        double minLength = TRACK_WHEEL_LENGTH_OFFSETS[0] + TRACK_PATCH_LENGTH_OFFSETS[0];
        double maxLength = TRACK_WHEEL_LENGTH_OFFSETS[TRACK_WHEEL_LENGTH_OFFSETS.length - 1] + TRACK_PATCH_LENGTH_OFFSETS[TRACK_PATCH_LENGTH_OFFSETS.length - 1];
        for (double side = minSide; side <= maxSide + 1.0E-6; side += 0.45) {
            for (double length = minLength; length <= maxLength + 1.0E-6; length += 0.45) {
                Vec3 samplePos = position()
                    .add(right.scale(side))
                    .add(forward.scale(length));
                if (getSurfaceAt(samplePos).countsAsTrack) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean isCheckpointCheckEnabled() {
        return level() instanceof ServerLevel serverLevel && OWRRaceControlState.get(serverLevel).isCheckpointCheckEnabled();
    }

    private boolean isOffTrackCheckEnabled() {
        return !(level() instanceof ServerLevel serverLevel) || OWRRaceControlState.get(serverLevel).isOffTrackCheckEnabled();
    }

    private void scanLapMarkers(Vec3 beforeMove, Vec3 actualMovement) {
        double yaw = Math.toRadians(getYRot());
        Vec3 forward = new Vec3(-Math.sin(yaw), 0.0, Math.cos(yaw));
        Vec3 right = new Vec3(forward.z, 0.0, -forward.x);
        int steps = Math.max(1, (int)Math.ceil(actualMovement.horizontalDistance() / 0.25));
        Vec3 currentPosition = position();
        for (int step = 0; step <= steps; step++) {
            double t = (double)step / steps;
            Vec3 center = beforeMove.lerp(currentPosition, t);
            if (scanLapMarkerAt(center, forward, right)) {
                return;
            }
        }
    }

    private boolean scanLapMarkerAt(Vec3 center, Vec3 forward, Vec3 right) {
        for (double side : TRACK_WHEEL_SIDE_OFFSETS) {
            for (double length : TRACK_WHEEL_LENGTH_OFFSETS) {
                for (double sidePatch : TRACK_PATCH_SIDE_OFFSETS) {
                    for (double lengthPatch : TRACK_PATCH_LENGTH_OFFSETS) {
                        Vec3 samplePos = center
                            .add(right.scale(side + sidePatch))
                            .add(forward.scale(length + lengthPatch));
                        BlockPos pos = BlockPos.containing(samplePos.x, getBoundingBox().minY - 0.05, samplePos.z);
                        Block block = level().getBlockState(pos).getBlock();
                        if (block == OWRBlocks.START_FINISH.get()) {
                            crossStartFinishLine(pos, level().getBlockState(pos).getValue(net.minecraft.world.level.block.HorizontalDirectionalBlock.FACING));
                            return true;
                        }
                        if (block == OWRBlocks.CHECKPOINT.get()) {
                            crossCheckpoint(pos, level().getBlockState(pos).getValue(net.minecraft.world.level.block.HorizontalDirectionalBlock.FACING));
                            return true;
                        }
                    }
                }
            }
        }
        return false;
    }


    public void tickLocalClientMovement(float throttle, float brake, float steering) {
        Entity passenger = getControllingPassenger();
        if (level().isClientSide() && passenger != null) {
            float currentCarYaw = getYRot();

            // If the server corrected the car's yaw since last tick, apply the same delta
            // to the passenger immediately so the view stays aligned with the car's nose.
            if (!Float.isNaN(clientLastSyncedCarYaw)) {
                float serverCorrection = currentCarYaw - clientLastSyncedCarYaw;
                if (serverCorrection != 0.0f) {
                    passenger.setYRot(passenger.getYRot() + serverCorrection);
                    passenger.setYHeadRot(passenger.getYRot());
                    passenger.setYBodyRot(passenger.getYRot());
                }
            }

            float previousYaw = getYRot();
            tickMovement(throttle, brake, steering, false);
            float yawDelta = getYRot() - previousYaw;
            positionRider(passenger);
            passenger.setYRot(passenger.getYRot() + yawDelta);
            passenger.setYHeadRot(passenger.getYRot());
            passenger.setYBodyRot(passenger.getYRot());

            clientLastSyncedCarYaw = getYRot();
        }
    }

    private void tickMovement(boolean debugMovement) {
        double throttle = inputThrottle;
        double steering = inputSteering;
        double brake = inputBrake;
        inputThrottle = 0;
        inputBrake = 0;
        inputSteering = 0;
        if (getControllingPassenger() == null) {
            ersLiftConfirmTicks = 0;
            ersLiftAndCoastPowerWatts = 0.0;
            ersLiftAndCoastArmed = false;
            tickPassiveMovement(debugMovement);
            return;
        }
        tickMovement(throttle, brake, steering, debugMovement);
    }

    private void tickPassiveMovement(boolean debugMovement) {
        Vec3 requestedMovement = getDeltaMovement();
        SurfaceProfile surface = getCurrentSurface();
        double drag = onGround() ? Math.min(PASSIVE_GROUND_DRAG, surface.drag) : PASSIVE_AIR_DRAG;
        Vec3 delta = new Vec3(requestedMovement.x * drag, onGround() ? 0.0 : requestedMovement.y - 0.04, requestedMovement.z * drag);
        if (delta.horizontalDistanceSqr() < 1.0E-5) {
            delta = new Vec3(0.0, delta.y, 0.0);
            resetTyreRelaxation();
        }
        yawRate *= PASSIVE_YAW_DAMPING;
        steeringAngle *= 0.65;
        if (Math.abs(yawRate) < 0.01) {
            yawRate = 0.0;
        }
        setYRot(getYRot() + (float) Math.toDegrees(yawRate * PHYSICS_DT));
        delta = clampHorizontalMovement(delta, MAX_REASONABLE_MOVEMENT_BLOCKS_PER_TICK);

        setDeltaMovement(delta);
        Vec3 beforeMove = position();
        lastTerrainPositionCorrectionY = 0.0;
        Vec3 actualMovement = moveWithPreemptiveClimb(delta);
        double groundSnapDelta = snapToNearbyGround(delta, actualMovement);
        if (groundSnapDelta < 0.0) {
            actualMovement = actualMovement.add(0.0, groundSnapDelta, 0.0);
        }
        lastClimbDelta = actualMovement.y;
        lastGroundSnapDelta = groundSnapDelta;
        double carriedVerticalMovement = actualMovement.y;
        if (onGround() && Math.abs(actualMovement.y) <= maxUpStep() + 0.15) {
            carriedVerticalMovement = 0.0;
        }
        actualMovement = clampHorizontalMovement(actualMovement, MAX_REASONABLE_MOVEMENT_BLOCKS_PER_TICK);
        setDeltaMovement(new Vec3(actualMovement.x, carriedVerticalMovement, actualMovement.z));
        handleEntityImpacts(beforeMove, actualMovement);

        entityData.set(SPEED, (float)(actualMovement.horizontalDistance() * 72.0));
        entityData.set(RPM, updateEngineRpm(actualMovement.horizontalDistance(), getGear(), gearTopSpeed(getGear(), setup), 0.0, false, false));
        entityData.set(TYRE_SLIP, 0.0f);
        previousHorizontalSpeed = requestedMovement.horizontalDistance();
    }

    private void tickMovement(double throttle, double brake, double steering, boolean debugMovement) {

        Vec3 delta = getDeltaMovement();
        double horizontalSpeed = Math.sqrt(delta.x * delta.x + delta.z * delta.z);

        if (brake > 0.0 && isDrsActive()) {
            setDrsActive(false);
        }

        double damageFactor    = 1.0 - getDamagePercent()   / 140.0;
        double tyreFactor      = 1.0 - getTyreWearPercent() / 180.0;
        int gear = clampGear(getGear());
        if (gear != getGear()) {
            entityData.set(GEAR, gear);
        }
        double gearTopSpeed = gearTopSpeed(gear, setup);
        SurfaceProfile surface = getCurrentSurface();
        double pitSpeedLimit = surface == SurfaceProfile.PIT_LANE ? VehiclePhysics.PIT_SPEED_LIMIT_BLOCKS_PER_TICK : Double.MAX_VALUE;

        Vec3 forward = Vec3.directionFromRotation(0.0f, getYRot());
        Vec3 right = new Vec3(forward.z, 0.0, -forward.x);
        double velocityLong = (delta.x * forward.x + delta.z * forward.z) * 20.0;
        double velocityLat = (delta.x * right.x + delta.z * right.z) * 20.0;
        double speedMetersPerSecond = Math.sqrt(velocityLong * velocityLong + velocityLat * velocityLat);
        boolean canApplyDrive = gear != NEUTRAL_GEAR && throttle > 0.0;
        if (speedMetersPerSecond < 0.35 && !canApplyDrive && brake == 0.0) {
            velocityLong = 0.0;
            velocityLat = 0.0;
            yawRate = 0.0;
            steeringAngle = 0.0;
            resetTyreRelaxation();
        }
        boolean launchClutch = throttle > 0.0 && (gear == 1 || gear == REVERSE_GEAR) && horizontalSpeed < LAUNCH_CLUTCH_SPEED;
        boolean clutchReleasing = clutchReleaseTicks > 0 && gear != NEUTRAL_GEAR;
        int engineRpm = updateEngineRpm(horizontalSpeed, gear, gearTopSpeed, throttle, launchClutch, clutchReleasing);
        double power = combustionPowerWatts(engineRpm) * setup.powerMultiplier() * setup.accelerationMultiplier() * damageFactor;
        double requestedIcePowerWatts = throttle > 0.0 && gear > NEUTRAL_GEAR ? power * throttle : 0.0;
        double tyreSlip = 0.0;

        double steerInput = Math.abs(steering) > STEERING_DEADZONE ? steering : 0.0;
        double speedRatio = speedMetersPerSecond / STEERING_SPEED_SCALE;
        double speedSteerT = square(speedRatio) / (1.0 + square(speedRatio));
        double steeringLockT = Math.pow(speedSteerT, STEERING_HIGH_SPEED_CURVE_POWER);
        double offGripRelief = frontSteeringOffGripRelief;
        double steeringLock = (LOW_SPEED_STEER_ANGLE + (HIGH_SPEED_STEER_ANGLE - LOW_SPEED_STEER_ANGLE) * steeringLockT)
            * (1.0 + offGripRelief * STEERING_OFF_GRIP_LOCK_BONUS);
        double rackRate = (LOW_SPEED_STEERING_RACK_RATE + (HIGH_SPEED_STEERING_RACK_RATE - LOW_SPEED_STEERING_RACK_RATE) * speedSteerT)
            * (1.0 + offGripRelief * STEERING_OFF_GRIP_RATE_BONUS);
        double centeringRate = LOW_SPEED_STEERING_CENTERING_RATE + (HIGH_SPEED_STEERING_CENTERING_RATE - LOW_SPEED_STEERING_CENTERING_RATE) * speedSteerT;
        double targetSteeringAngle = steerInput * steeringLock;
        double steeringError = targetSteeringAngle - steeringAngle;
        boolean centering = Math.abs(targetSteeringAngle) < Math.abs(steeringAngle) && Math.signum(targetSteeringAngle) != Math.signum(steeringError);
        double steeringRate = centering ? centeringRate : rackRate;
        double steeringGain = 1.0 - Math.exp(-steeringRate * PHYSICS_DT / Math.max(Math.toRadians(0.25), steeringLock));
        steeringAngle += steeringError * steeringGain;
        if (brake > 0.0 && Math.abs(steeringAngle) > SLIP_ANGLE_DEADBAND && speedMetersPerSecond > 8.0) {
            double release = Math.min(0.18, brake * STEERING_TRAIL_BRAKE_RELEASE * Math.min(1.0, speedMetersPerSecond / 35.0));
            steeringAngle *= 1.0 - release;
        }
        boolean steeringReleased = steerInput == 0.0 && Math.abs(steeringAngle) < SLIP_ANGLE_DEADBAND;

        boolean liftInputConfirmedThisTick = updateLiftAndCoastConfirmation(throttle, brake);
        double previousKineticEnergy = 0.5 * CAR_MASS_KG * (velocityLong * velocityLong + velocityLat * velocityLat) + 0.5 * YAW_INERTIA * yawRate * yawRate;
        double previousVelocityLong = velocityLong;
        double yawDelta = 0.0;
        double driveWorkJoules = 0.0;
        double requestedIceDriveEnergyJoules = 0.0;
        double actualPositiveDriveEnergyJoules = 0.0;
        double requestedPositiveErsEnergyJoules = 0.0;
        double requestedNegativeErsEnergyJoules = 0.0;
        double subDt = PHYSICS_DT / PHYSICS_SUBSTEPS;
        double substepLateralAccelerationEstimate = 0.0;
        double finalFlLatForce = 0.0;
        double finalFrLatForce = 0.0;
        double finalRlLatForce = 0.0;
        double finalRrLatForce = 0.0;
        double finalFlLongForce = 0.0;
        double finalFrLongForce = 0.0;
        double finalRlLongForce = 0.0;
        double finalRrLongForce = 0.0;
        double finalFlLoad = 0.0;
        double finalFrLoad = 0.0;
        double finalRlLoad = 0.0;
        double finalRrLoad = 0.0;
        double finalFlDemand = 0.0;
        double finalFrDemand = 0.0;
        double finalRlDemand = 0.0;
        double finalRrDemand = 0.0;
        double finalFlSlipAngle = 0.0;
        double finalFrSlipAngle = 0.0;
        double finalRlSlipAngle = 0.0;
        double finalRrSlipAngle = 0.0;
        double finalDownforce = 0.0;
        double finalDragForce = 0.0;
        double finalFrontSaturation = 0.0;
        double finalRearSaturation = 0.0;
        double cdACoefficient = setup.cdACoefficient();
        double clACoefficient = setup.clACoefficient();
        double tyreMuCoefficient = setup.tyreMuCoefficient();

        // Wheel surface grip is position/heading-dependent only — identical every substep.
        // Query once here rather than 4x inside the loop.
        double flSurfaceGrip = getSurfaceAt(position().add(right.scale(-HALF_TRACK_WIDTH)).add(forward.scale(FRONT_AXLE_DISTANCE))).grip;
        double frSurfaceGrip = getSurfaceAt(position().add(right.scale(HALF_TRACK_WIDTH)).add(forward.scale(FRONT_AXLE_DISTANCE))).grip;
        double rlSurfaceGrip = getSurfaceAt(position().add(right.scale(-HALF_TRACK_WIDTH)).add(forward.scale(-REAR_AXLE_DISTANCE))).grip;
        double rrSurfaceGrip = getSurfaceAt(position().add(right.scale(HALF_TRACK_WIDTH)).add(forward.scale(-REAR_AXLE_DISTANCE))).grip;

        for (int substep = 0; substep < PHYSICS_SUBSTEPS; substep++) {
            double subSpeedSquared = velocityLong * velocityLong + velocityLat * velocityLat;
            double subSpeed = Math.sqrt(subSpeedSquared);
            double subDownforce = 0.5 * AIR_DENSITY * DOWNFORCE_AREA * clACoefficient * subSpeedSquared * (isDrsActive() ? DRS_DOWNFORCE_FACTOR : 1.0);
            double subAeroDrag = 0.5 * AIR_DENSITY * DRAG_AREA * cdACoefficient * subSpeedSquared * (isDrsActive() ? DRS_DRAG_FACTOR : 1.0);
            double subStaticFrontLoad = CAR_MASS_KG * GRAVITY * FRONT_STATIC_WEIGHT;
            double subStaticRearLoad = CAR_MASS_KG * GRAVITY * (1.0 - FRONT_STATIC_WEIGHT);
            double subAeroFrontLoad = subDownforce * FRONT_AERO_BALANCE;
            double subAeroRearLoad = subDownforce * (1.0 - FRONT_AERO_BALANCE);

            double subSpeedBlocksPerTick = subSpeed / 20.0;
            double driveDirection = gear == REVERSE_GEAR ? -1.0 : gear > NEUTRAL_GEAR ? 1.0 : 0.0;
            double subDriveForceRequest = driveDirection != 0.0 && throttle > 0.0
                    && Math.abs(velocityLong) / 20.0 < gearTopSpeed
                    && subSpeedBlocksPerTick < pitSpeedLimit
                ? driveDirection * power * throttle / Math.max(MIN_POWER_SPEED, Math.abs(velocityLong))
                : 0.0;
            if (clutchReleasing && driveDirection != 0.0
                    && Math.abs(velocityLong) / 20.0 < gearTopSpeed
                    && subSpeedBlocksPerTick < pitSpeedLimit) {
                double releaseT = clutchReleaseTicks / (double) CLUTCH_RELEASE_TICKS;
                double storedPower = combustionPowerWatts(Math.max(engineRpm, clutchReleaseRpm)) * setup.powerMultiplier() * setup.accelerationMultiplier() * damageFactor;
                double clutchForce = driveDirection * storedPower * releaseT / MIN_POWER_SPEED;
                subDriveForceRequest = driveDirection > 0.0
                    ? Math.max(subDriveForceRequest, clutchForce)
                    : Math.min(subDriveForceRequest, clutchForce);
            }
            ErsPowerResult ersPower = calculateErsPower(throttle, brake, liftInputConfirmedThisTick, gear, subSpeedBlocksPerTick, velocityLong, substepLateralAccelerationEstimate / GRAVITY, subDt, surface);
            double subRequestedPositiveErsPower = Math.max(0.0, ersPower.powerWatts());
            requestedPositiveErsEnergyJoules += subRequestedPositiveErsPower * subDt;
            requestedNegativeErsEnergyJoules += Math.max(0.0, -ersPower.powerWatts()) * subDt;
            requestedIceDriveEnergyJoules += Math.max(0.0, requestedIcePowerWatts) * subDt;
            double pendingErsDriveForce = 0.0;
            double subForwardRollingFraction = Math.abs(velocityLong) / Math.max(1.0, subSpeed);
            if (subSpeed > 3.0 && subForwardRollingFraction < 0.45 && !clutchReleasing) {
                subDriveForceRequest *= subForwardRollingFraction / 0.45;
            }
            if ((launchClutch || clutchReleasing) && isTractionControlEnabled()) {
                double subStaticRearTraction = ASPHALT_MU_LONGITUDINAL * surface.grip * subStaticRearLoad;
                double tractionLimit = subStaticRearTraction * (clutchReleasing ? CLUTCH_RELEASE_TRACTION_LIMIT : 0.86);
                subDriveForceRequest = driveDirection >= 0.0
                    ? Math.min(subDriveForceRequest, tractionLimit)
                    : Math.max(subDriveForceRequest, -tractionLimit);
            }
            if (driveDirection > 0.0 && ersPower.powerWatts() != 0.0
                    && Math.abs(velocityLong) / 20.0 < gearTopSpeed
                    && subSpeedBlocksPerTick < pitSpeedLimit) {
                pendingErsDriveForce = ersPower.powerWatts() / Math.max(MIN_POWER_SPEED, Math.abs(velocityLong));
            }

            double subBrakeForceRequest = brake * MAX_BRAKE_FORCE;
            if (surface == SurfaceProfile.PIT_LANE && subSpeedBlocksPerTick >= pitSpeedLimit) {
                subDriveForceRequest = 0.0;
                pendingErsDriveForce = Math.min(0.0, pendingErsDriveForce);
                subBrakeForceRequest = Math.max(subBrakeForceRequest, 6_000.0);
            }
            subDriveForceRequest += pendingErsDriveForce;
            double subBrakeForceEstimate = brake * Math.min(MAX_BRAKE_FORCE, ASPHALT_MU_LONGITUDINAL * surface.grip * (CAR_MASS_KG * GRAVITY + subDownforce));
            double subBrakeDirection = Math.abs(velocityLong) > 0.1 ? Math.signum(velocityLong) : 0.0;
            double tyreWearDragFactor = 1.0 + getTyreWearPercent() * 0.0022;
            double subRollingForce = ROLLING_RESISTANCE * tyreWearDragFactor * (CAR_MASS_KG * GRAVITY + subDownforce);
            double subSinkDragForce = surface.sinkDrag * (CAR_MASS_KG * GRAVITY + subDownforce);
            double subPreliminaryAx = (subDriveForceRequest - subBrakeDirection * subBrakeForceEstimate - Math.signum(velocityLong) * (subAeroDrag + subRollingForce + subSinkDragForce)) / CAR_MASS_KG;
            double subLateralAccelerationEstimate = substepLateralAccelerationEstimate;
            double subLongitudinalLoadTransfer = CAR_MASS_KG * subPreliminaryAx * CG_HEIGHT / WHEELBASE;
            double subLateralLoadTransfer = CAR_MASS_KG * subLateralAccelerationEstimate * CG_HEIGHT / TRACK_WIDTH;
            double subNormalFront = Math.max(300.0, subStaticFrontLoad + subAeroFrontLoad - subLongitudinalLoadTransfer);
            double subNormalRear = Math.max(300.0, subStaticRearLoad + subAeroRearLoad + subLongitudinalLoadTransfer);
            double frontLateralTransfer = subLateralLoadTransfer * FRONT_ROLL_STIFFNESS_SHARE;
            double rearLateralTransfer = subLateralLoadTransfer * REAR_ROLL_STIFFNESS_SHARE;
            double flNormal = Math.max(75.0, subNormalFront * 0.5 - frontLateralTransfer * 0.5);
            double frNormal = Math.max(75.0, subNormalFront * 0.5 + frontLateralTransfer * 0.5);
            double rlNormal = Math.max(75.0, subNormalRear * 0.5 - rearLateralTransfer * 0.5);
            double rrNormal = Math.max(75.0, subNormalRear * 0.5 + rearLateralTransfer * 0.5);
            double subReferenceFrontWheelLoad = CAR_MASS_KG * GRAVITY * FRONT_STATIC_WEIGHT * 0.5;
            double subReferenceRearWheelLoad = CAR_MASS_KG * GRAVITY * (1.0 - FRONT_STATIC_WEIGHT) * 0.5;
            double subTyreWearGrip = Math.max(0.45, tyreFactor);
            double flSurfaceMuLat = ASPHALT_MU_LATERAL * flSurfaceGrip * tyreMuCoefficient;
            double frSurfaceMuLat = ASPHALT_MU_LATERAL * frSurfaceGrip * tyreMuCoefficient;
            double rlSurfaceMuLat = ASPHALT_MU_LATERAL * rlSurfaceGrip * tyreMuCoefficient;
            double rrSurfaceMuLat = ASPHALT_MU_LATERAL * rrSurfaceGrip * tyreMuCoefficient;
            double flSurfaceMuLong = ASPHALT_MU_LONGITUDINAL * flSurfaceGrip * tyreMuCoefficient;
            double frSurfaceMuLong = ASPHALT_MU_LONGITUDINAL * frSurfaceGrip * tyreMuCoefficient;
            double rlSurfaceMuLong = ASPHALT_MU_LONGITUDINAL * rlSurfaceGrip * tyreMuCoefficient;
            double rrSurfaceMuLong = ASPHALT_MU_LONGITUDINAL * rrSurfaceGrip * tyreMuCoefficient;
            double flMuLat = loadSensitiveMu(flSurfaceMuLat * subTyreWearGrip, flNormal, subReferenceFrontWheelLoad);
            double frMuLat = loadSensitiveMu(frSurfaceMuLat * subTyreWearGrip, frNormal, subReferenceFrontWheelLoad);
            double rlMuLat = loadSensitiveMu(rlSurfaceMuLat * subTyreWearGrip, rlNormal, subReferenceRearWheelLoad);
            double rrMuLat = loadSensitiveMu(rrSurfaceMuLat * subTyreWearGrip, rrNormal, subReferenceRearWheelLoad);
            double flMuLong = loadSensitiveMu(flSurfaceMuLong * subTyreWearGrip, flNormal, subReferenceFrontWheelLoad);
            double frMuLong = loadSensitiveMu(frSurfaceMuLong * subTyreWearGrip, frNormal, subReferenceFrontWheelLoad);
            double rlMuLong = loadSensitiveMu(rlSurfaceMuLong * subTyreWearGrip, rlNormal, subReferenceRearWheelLoad);
            double rrMuLong = loadSensitiveMu(rrSurfaceMuLong * subTyreWearGrip, rrNormal, subReferenceRearWheelLoad);

            double brakeFront = subBrakeForceRequest * BRAKE_FRONT_BIAS * 0.5;
            double brakeRear = subBrakeForceRequest * (1.0 - BRAKE_FRONT_BIAS) * 0.5;
            double trailBrakeSteerUse = Math.min(1.0, Math.abs(steeringAngle) / TRAIL_BRAKE_REAR_RELIEF_MAX_STEER);
            double trailBrakeRelease = brake * trailBrakeSteerUse * TRAIL_BRAKE_REAR_PRESSURE_RELIEF * 0.35;
            brakeRear *= 1.0 - trailBrakeRelease;
            double driveRear = subDriveForceRequest * 0.5;
            double brakeSign = subBrakeDirection;
            double flLongRequest = -brakeSign * brakeFront;
            double frLongRequest = -brakeSign * brakeFront;
            double rlLongRequest = driveRear - brakeSign * brakeRear;
            double rrLongRequest = driveRear - brakeSign * brakeRear;
            double rollingForceRamp = Math.max(0.0, Math.min(1.0, (subSpeed - 1.5) / 8.5));
            double rollingForceScale = rollingForceRamp * rollingForceRamp * (3.0 - 2.0 * rollingForceRamp);
            double compoundStiffness = 0.90 + (tyreMuCoefficient - 0.86) * 0.55;
            double flLongLimit = flMuLong * flNormal;
            double frLongLimit = frMuLong * frNormal;
            double rlLongLimit = rlMuLong * rlNormal;
            double rrLongLimit = rrMuLong * rrNormal;
            double flLatLimit = flMuLat * flNormal;
            double frLatLimit = frMuLat * frNormal;
            double rlLatLimit = rlMuLat * rlNormal;
            double rrLatLimit = rrMuLat * rrNormal;
            if (isAbsEnabled() && brake > 0.0) {
                flLongRequest = absLimitedBrakeForce(flLongRequest, relaxedFlLatForce, flLongLimit, flLatLimit);
                frLongRequest = absLimitedBrakeForce(frLongRequest, relaxedFrLatForce, frLongLimit, frLatLimit);
                double rlBrakeLimited = absLimitedBrakeForce(rlLongRequest - driveRear, relaxedRlLatForce, rlLongLimit, rlLatLimit);
                double rrBrakeLimited = absLimitedBrakeForce(rrLongRequest - driveRear, relaxedRrLatForce, rrLongLimit, rrLatLimit);
                rlLongRequest = driveRear + rlBrakeLimited;
                rrLongRequest = driveRear + rrBrakeLimited;
            }
            if (brake > 0.0 && Math.abs(steeringAngle) > SLIP_ANGLE_DEADBAND && velocityLong > 0.0) {
                double brakingRequest = Math.max(0.0, -(flLongRequest + frLongRequest + rlLongRequest + rrLongRequest));
                double maxBrakeImpulseForce = CAR_MASS_KG * velocityLong / subDt;
                if (brakingRequest > maxBrakeImpulseForce) {
                    double brakeScale = maxBrakeImpulseForce / brakingRequest;
                    flLongRequest *= flLongRequest < 0.0 ? brakeScale : 1.0;
                    frLongRequest *= frLongRequest < 0.0 ? brakeScale : 1.0;
                    rlLongRequest = driveRear + (rlLongRequest - driveRear) * brakeScale;
                    rrLongRequest = driveRear + (rrLongRequest - driveRear) * brakeScale;
                }
            }
            if (isTractionControlEnabled()) {
                double rearLatUse = Math.max(Math.abs(relaxedRlLatForce) / Math.max(1.0, rlLatLimit), Math.abs(relaxedRrLatForce) / Math.max(1.0, rrLatLimit));
                double rearTractionControlTarget = rearLatUse < 0.08 ? TRACTION_CONTROL_SLIP_TARGET : Math.sqrt(Math.max(0.0, square(TRACTION_CONTROL_SLIP_TARGET) - square(rearLatUse)));
                rlLongRequest = clamp(rlLongRequest, -rlLongLimit * rearTractionControlTarget, rlLongLimit * rearTractionControlTarget);
                rrLongRequest = clamp(rrLongRequest, -rrLongLimit * rearTractionControlTarget, rrLongLimit * rearTractionControlTarget);
            }
            WheelForces flForces = calculateWheelForces(
                -HALF_TRACK_WIDTH, FRONT_AXLE_DISTANCE, steeringAngle - FRONT_TOE_OUT,
                velocityLong, velocityLat, yawRate,
                flLongRequest, flMuLong, flMuLat, flNormal,
                FRONT_LONGITUDINAL_STIFFNESS * 0.5 * compoundStiffness * subTyreWearGrip * Math.sqrt(flSurfaceGrip),
                FRONT_CORNERING_STIFFNESS * 0.5 * rollingForceScale * compoundStiffness * subTyreWearGrip * Math.sqrt(flSurfaceGrip),
                flLongLimit, flLatLimit, FRONT_TYRE_RELAXATION_LENGTH, subDt, subSpeed, steeringReleased, relaxedFlLatForce);
            WheelForces frForces = calculateWheelForces(
                HALF_TRACK_WIDTH, FRONT_AXLE_DISTANCE, steeringAngle + FRONT_TOE_OUT,
                velocityLong, velocityLat, yawRate,
                frLongRequest, frMuLong, frMuLat, frNormal,
                FRONT_LONGITUDINAL_STIFFNESS * 0.5 * compoundStiffness * subTyreWearGrip * Math.sqrt(frSurfaceGrip),
                FRONT_CORNERING_STIFFNESS * 0.5 * rollingForceScale * compoundStiffness * subTyreWearGrip * Math.sqrt(frSurfaceGrip),
                frLongLimit, frLatLimit, FRONT_TYRE_RELAXATION_LENGTH, subDt, subSpeed, steeringReleased, relaxedFrLatForce);
            WheelForces rlForces = calculateWheelForces(
                -HALF_TRACK_WIDTH, -REAR_AXLE_DISTANCE, REAR_TOE_IN,
                velocityLong, velocityLat, yawRate,
                rlLongRequest, rlMuLong, rlMuLat, rlNormal,
                REAR_LONGITUDINAL_STIFFNESS * 0.5 * compoundStiffness * subTyreWearGrip * Math.sqrt(rlSurfaceGrip),
                REAR_CORNERING_STIFFNESS * 0.5 * rollingForceScale * compoundStiffness * subTyreWearGrip * Math.sqrt(rlSurfaceGrip),
                rlLongLimit, rlLatLimit, REAR_TYRE_RELAXATION_LENGTH, subDt, subSpeed, steeringReleased, relaxedRlLatForce);
            WheelForces rrForces = calculateWheelForces(
                HALF_TRACK_WIDTH, -REAR_AXLE_DISTANCE, -REAR_TOE_IN,
                velocityLong, velocityLat, yawRate,
                rrLongRequest, rrMuLong, rrMuLat, rrNormal,
                REAR_LONGITUDINAL_STIFFNESS * 0.5 * compoundStiffness * subTyreWearGrip * Math.sqrt(rrSurfaceGrip),
                REAR_CORNERING_STIFFNESS * 0.5 * rollingForceScale * compoundStiffness * subTyreWearGrip * Math.sqrt(rrSurfaceGrip),
                rrLongLimit, rrLatLimit, REAR_TYRE_RELAXATION_LENGTH, subDt, subSpeed, steeringReleased, relaxedRrLatForce);
            relaxedFlLatForce = flForces.relaxedLateralForce();
            relaxedFrLatForce = frForces.relaxedLateralForce();
            relaxedRlLatForce = rlForces.relaxedLateralForce();
            relaxedRrLatForce = rrForces.relaxedLateralForce();
            double flLongForce = flForces.bodyLongitudinalForce();
            double frLongForce = frForces.bodyLongitudinalForce();
            double rlLongForce = rlForces.bodyLongitudinalForce();
            double rrLongForce = rrForces.bodyLongitudinalForce();
            double flLatForce = flForces.bodyLateralForce();
            double frLatForce = frForces.bodyLateralForce();
            double rlLatForce = rlForces.bodyLateralForce();
            double rrLatForce = rrForces.bodyLateralForce();
            double flDemand = flForces.demand();
            double frDemand = frForces.demand();
            double rlDemand = rlForces.demand();
            double rrDemand = rrForces.demand();

            double frontLongForce = flLongForce + frLongForce;
            double rearLongForce = rlLongForce + rrLongForce;
            double frontLatForce = flLatForce + frLatForce;
            double rearLatForce = rlLatForce + rrLatForce;
            double dragForce = -Math.signum(velocityLong) * (subAeroDrag + subRollingForce + subSinkDragForce);
            double longitudinalForce = rearLongForce + frontLongForce + dragForce;
            double lateralForce = frontLatForce + rearLatForce;
            double yawMoment = flForces.yawMoment() + frForces.yawMoment() + rlForces.yawMoment() + rrForces.yawMoment();
            double yawAcceleration = yawMoment / YAW_INERTIA;
            double subVelocityLongBefore = velocityLong;
            double forceAccelerationLong = longitudinalForce / CAR_MASS_KG;
            double couplingAccelerationLong = yawRate * velocityLat;
            double subAccelerationLat = lateralForce / CAR_MASS_KG - yawRate * velocityLong;
            substepLateralAccelerationEstimate = subAccelerationLat;

            velocityLong += forceAccelerationLong * subDt;
            if (subVelocityLongBefore >= 0.0 && velocityLong < 0.0 && driveDirection >= 0.0 && subDriveForceRequest <= subBrakeForceRequest) {
                velocityLong = 0.0;
            }
            velocityLong += couplingAccelerationLong * subDt;
            if (subVelocityLongBefore >= -0.05 && velocityLong < 0.0 && !(gear == REVERSE_GEAR && throttle > 0.0) && Math.abs(yawDelta) < Math.PI * 0.5) {
                velocityLong = 0.0;
            } else if (subVelocityLongBefore < 0.0 && velocityLong > 0.0 && throttle == 0.0) {
                velocityLong = 0.0;
            }
            velocityLat += subAccelerationLat * subDt;
            yawRate += yawAcceleration * subDt;
            if (Math.abs(steeringAngle) > SLIP_ANGLE_DEADBAND && velocityLong > 1.0) {
                double targetYawRate = velocityLong / WHEELBASE * Math.tan(steeringAngle);
                double targetSign = Math.signum(targetYawRate);
                double yawSign = Math.signum(yawRate);
                if (targetSign != 0.0 && yawSign != 0.0 && targetSign != yawSign) {
                    yawRate += (targetYawRate - yawRate) * 0.35;
                } else {
                    double allowedYawRate = targetYawRate * (throttle > 0.0 ? 1.35 : 1.05);
                    if (Math.abs(yawRate) > Math.abs(allowedYawRate)) {
                        double recovery = brake > 0.0 ? 0.28 : throttle > 0.0 ? 0.10 : 0.18;
                        yawRate += (allowedYawRate - yawRate) * recovery;
                    }
                }
            }
            if (brake > 0.0) {
                yawRate *= 1.0 - Math.min(0.18, brake * 0.10);
            }
            yawDelta += yawRate * subDt;
            double positiveDriveWorkJoules = Math.max(0.0, (rearLongForce + frontLongForce) * velocityLong) * subDt;
            driveWorkJoules += positiveDriveWorkJoules;
            actualPositiveDriveEnergyJoules += positiveDriveWorkJoules;

            finalFlLatForce = flLatForce;
            finalFrLatForce = frLatForce;
            finalRlLatForce = rlLatForce;
            finalRrLatForce = rrLatForce;
            finalFlLongForce = flLongForce;
            finalFrLongForce = frLongForce;
            finalRlLongForce = rlLongForce;
            finalRrLongForce = rrLongForce;
            finalFlLoad = flNormal;
            finalFrLoad = frNormal;
            finalRlLoad = rlNormal;
            finalRrLoad = rrNormal;
            finalFlDemand = flDemand;
            finalFrDemand = frDemand;
            finalRlDemand = rlDemand;
            finalRrDemand = rrDemand;
            finalFlSlipAngle = flForces.slipAngle();
            finalFrSlipAngle = frForces.slipAngle();
            finalRlSlipAngle = rlForces.slipAngle();
            finalRrSlipAngle = rrForces.slipAngle();
            finalDownforce = subDownforce;
            finalDragForce = dragForce;
            finalFrontSaturation = Math.max(flDemand, frDemand);
            finalRearSaturation = Math.max(rlDemand, rrDemand);
        }

        reconcilePowerLimitedErsEnergy(requestedIceDriveEnergyJoules, requestedPositiveErsEnergyJoules, requestedNegativeErsEnergyJoules, actualPositiveDriveEnergyJoules, PHYSICS_DT);

        double newKineticEnergy = 0.5 * CAR_MASS_KG * (velocityLong * velocityLong + velocityLat * velocityLat) + 0.5 * YAW_INERTIA * yawRate * yawRate;
        double allowedEnergy = previousKineticEnergy + driveWorkJoules;
        if (newKineticEnergy > allowedEnergy && newKineticEnergy > 0.0) {
            double energyScale = Math.sqrt(allowedEnergy / newKineticEnergy);
            velocityLong *= energyScale;
            velocityLat *= energyScale;
            yawRate *= energyScale;
            yawDelta *= energyScale;
        }
        if (gear == REVERSE_GEAR && throttle > 0.0) {
            double reverseTopMetersPerSecond = gearTopSpeed * 20.0;
            double reverseGrip = Math.max(MIN_SURFACE_MU, surface.grip * tyreMuCoefficient * Math.max(0.45, tyreFactor));
            double reverseAcceleration = GRAVITY * ASPHALT_MU_LONGITUDINAL * reverseGrip * 0.28 * throttle * PHYSICS_DT;
            double reverseVelocityFloor = previousVelocityLong - reverseAcceleration;
            velocityLong = Math.max(-reverseTopMetersPerSecond, Math.min(velocityLong, reverseVelocityFloor));
            if (brake == 0.0 && velocityLong > -reverseTopMetersPerSecond) {
                velocityLong = Math.min(velocityLong, previousVelocityLong - reverseAcceleration * 0.45);
            }
        }
        if (steerInput == 0.0 && Math.abs(velocityLat) < 0.08 && Math.abs(yawRate) < 0.025) {
            velocityLat = 0.0;
            yawRate = 0.0;
            resetTyreRelaxation();
        }
        debugVelocityLong = velocityLong;
        debugVelocityLat = velocityLat;
        debugDriveForce = driveWorkJoules > 0.0 ? driveWorkJoules / PHYSICS_DT : 0.0;
        debugDragForce = finalDragForce;
        debugFlLatForce = finalFlLatForce;
        debugFrLatForce = finalFrLatForce;
        debugRlLatForce = finalRlLatForce;
        debugRrLatForce = finalRrLatForce;
        debugFlLongForce = finalFlLongForce;
        debugFrLongForce = finalFrLongForce;
        debugRlLongForce = finalRlLongForce;
        debugRrLongForce = finalRrLongForce;
        debugFlLoad = finalFlLoad;
        debugFrLoad = finalFrLoad;
        debugRlLoad = finalRlLoad;
        debugRrLoad = finalRrLoad;
        debugFlDemand = finalFlDemand;
        debugFrDemand = finalFrDemand;
        debugRlDemand = finalRlDemand;
        debugRrDemand = finalRrDemand;
        debugFlSlipAngle = finalFlSlipAngle;
        debugFrSlipAngle = finalFrSlipAngle;
        debugRlSlipAngle = finalRlSlipAngle;
        debugRrSlipAngle = finalRrSlipAngle;
        debugDownforce = finalDownforce;
        setYRot(getYRot() + (float) Math.toDegrees(yawDelta));
        updateFrontSteeringOffGripRelief(finalFrontSaturation, finalRearSaturation);
        tickFrontUndersteerWarning(finalFrontSaturation, finalRearSaturation, speedMetersPerSecond);

        double frontSlipAngle = Math.abs(finalFlSlipAngle) >= Math.abs(finalFrSlipAngle) ? finalFlSlipAngle : finalFrSlipAngle;
        double rearSlipAngle = Math.abs(finalRlSlipAngle) >= Math.abs(finalRrSlipAngle) ? finalRlSlipAngle : finalRrSlipAngle;
        double frontExcess = Math.max(0.0, finalFrontSaturation - 1.0);
        double rearExcess = Math.max(0.0, finalRearSaturation - 1.0);
        double slipMetric = Math.abs(frontSlipAngle) * 0.7 + Math.abs(rearSlipAngle) * 0.9 + frontExcess + rearExcess;
        double steeringWear = Math.abs(steeringAngle) / Math.max(Math.toRadians(1.0), LOW_SPEED_STEER_ANGLE);
        tyreSlip = Math.max(tyreSlip, Math.min(1.0, slipMetric * Math.min(1.0, speedMetersPerSecond / 18.0)));
        if (speedMetersPerSecond > 1.0) {
            addTyreWear((float) ((slipMetric + steeringWear * 0.06) * speedMetersPerSecond * 0.000175 * setup.tyreWearMultiplier() * surface.wearMult));
        }

        forward = Vec3.directionFromRotation(0.0f, getYRot());
        right = new Vec3(forward.z, 0.0, -forward.x);
        double newY = onGround() ? 0.0 : delta.y - 0.04;
        delta = new Vec3(
            (forward.x * velocityLong + right.x * velocityLat) / 20.0,
            newY,
            (forward.z * velocityLong + right.z * velocityLat) / 20.0
        );
        Vec3 unclampedDelta = delta;
        delta = clampHorizontalMovement(delta, MAX_REASONABLE_MOVEMENT_BLOCKS_PER_TICK);
        if (delta != unclampedDelta) {
            logMovementWarning("physics output clamped", position(), unclampedDelta, delta, throttle, brake, steering, surface);
        }

        setDeltaMovement(delta);
        Vec3 beforeMove = position();
        lastTerrainPositionCorrectionY = 0.0;
        Vec3 actualMovement = moveWithPreemptiveClimb(delta);
        double groundSnapDelta = snapToNearbyGround(delta, actualMovement);
        if (groundSnapDelta < 0.0) {
            actualMovement = actualMovement.add(0.0, groundSnapDelta, 0.0);
        }
        double elevationDelta = actualMovement.y - delta.y - lastTerrainPositionCorrectionY;
        lastClimbDelta = actualMovement.y;
        lastGroundSnapDelta = groundSnapDelta;
        double actualHorizontalSpeed = actualMovement.horizontalDistance() * 20.0;
        if (Math.abs(elevationDelta) > 1.0E-4 && actualHorizontalSpeed > 1.0E-4) {
            double horizontalKineticEnergy = 0.5 * CAR_MASS_KG * actualHorizontalSpeed * actualHorizontalSpeed;
            double adjustedHorizontalKineticEnergy = Math.max(0.0, horizontalKineticEnergy - CAR_MASS_KG * GRAVITY * elevationDelta);
            double adjustedHorizontalSpeed = Math.sqrt(2.0 * adjustedHorizontalKineticEnergy / CAR_MASS_KG);
            double speedScale = adjustedHorizontalSpeed / actualHorizontalSpeed;
            actualMovement = new Vec3(actualMovement.x * speedScale, actualMovement.y, actualMovement.z * speedScale);
        }
        double carriedVerticalMovement = actualMovement.y;
        if (onGround() && Math.abs(actualMovement.y) <= maxUpStep() + 0.15) {
            carriedVerticalMovement = 0.0;
        }
        Vec3 unclampedActualMovement = actualMovement;
        actualMovement = clampHorizontalMovement(actualMovement, MAX_REASONABLE_MOVEMENT_BLOCKS_PER_TICK);
        if (actualMovement != unclampedActualMovement) {
            logMovementWarning("actual movement clamped", beforeMove, unclampedActualMovement, actualMovement, throttle, brake, steering, surface);
        }
        double requestedActualDelta = Math.abs(unclampedActualMovement.horizontalDistance() - delta.horizontalDistance());
        if (requestedActualDelta > 1.0 && unclampedActualMovement.horizontalDistance() > MAX_REASONABLE_MOVEMENT_BLOCKS_PER_TICK) {
            logMovementWarning("movement/collision discrepancy", beforeMove, delta, unclampedActualMovement, throttle, brake, steering, surface);
        }
        setDeltaMovement(new Vec3(actualMovement.x, carriedVerticalMovement, actualMovement.z));
        handleEntityImpacts(beforeMove, actualMovement);
        if (!level().isClientSide()) {
            scanLapMarkers(beforeMove, actualMovement);
        }


        double newSpeed = Math.sqrt(actualMovement.x * actualMovement.x + actualMovement.z * actualMovement.z);
        int rpm = engineRpm;
        if (clutchReleaseTicks > 0) {
            clutchReleaseTicks--;
            if (clutchReleaseTicks == 0) {
                clutchReleaseRpm = 0;
            }
        }
        entityData.set(SPEED, (float)(newSpeed * 72.0));
        entityData.set(RPM, rpm);
        harvestErsFromBraking(horizontalSpeed, newSpeed, brake, surface);
        entityData.set(TYRE_SLIP, (float) Math.max(0.0, Math.min(1.0, tyreSlip)));
        previousHorizontalSpeed = horizontalSpeed;
    }

    private void logMovementWarning(String reason, Vec3 before, Vec3 requested, Vec3 actual, double throttle, double brake, double steering, SurfaceProfile surface) {
        if (level().isClientSide()) {
            return;
        }
        long time = level().getGameTime();
        if (time - lastMovementWarningAt < 20L) {
            return;
        }
        lastMovementWarningAt = time;
        LOGGER.warn("OWR car movement warning reason={} id={} passenger={} pos={} before={} requested={} actual={} input=({}, {}, {}) gear={} surface={} collision=({}, {}) onGround={} speedKmh={} delta={}",
            reason,
            getId(),
            getControllingPassenger() == null ? "none" : getControllingPassenger().getScoreboardName(),
            blockPosition(),
            before,
            requested,
            actual,
            throttle,
            brake,
            steering,
            getGear(),
            surface,
            horizontalCollision,
            verticalCollision,
            onGround(),
            getSpeedKmh(),
            getDeltaMovement());
    }

    private Vec3 clampHorizontalMovement(Vec3 movement, double maxHorizontalDistance) {
        double horizontalDistance = movement.horizontalDistance();
        if (Double.isFinite(horizontalDistance) && horizontalDistance <= maxHorizontalDistance) {
            return movement;
        }
        if (!Double.isFinite(horizontalDistance) || horizontalDistance <= 1.0E-6) {
            return new Vec3(0.0, movement.y, 0.0);
        }
        double scale = maxHorizontalDistance / horizontalDistance;
        return new Vec3(movement.x * scale, movement.y, movement.z * scale);
    }

    private Vec3 moveWithPreemptiveClimb(Vec3 requestedMovement) {
        Vec3 beforeMove = position();
        Vec3 terrainMovement = terrainFollowingMovement(beforeMove, requestedMovement);
        if (terrainMovement != null) {
            if (emptyShapeBlockIntersectsMovement(beforeMove, terrainMovement)) {
                return stopHorizontalAtEmptyShapeBlock(beforeMove, requestedMovement);
            }
            setPos(beforeMove.x + terrainMovement.x, beforeMove.y + terrainMovement.y, beforeMove.z + terrainMovement.z);
            horizontalCollision = false;
            verticalCollision = false;
            setOnGround(true);
            return terrainMovement;
        }
        if (emptyShapeBlockIntersectsMovement(beforeMove, requestedMovement)) {
            return stopHorizontalAtEmptyShapeBlock(beforeMove, requestedMovement);
        }
        move(MoverType.SELF, requestedMovement);
        return position().subtract(beforeMove);
    }

    private Vec3 stopHorizontalAtEmptyShapeBlock(Vec3 beforeMove, Vec3 requestedMovement) {
        horizontalCollision = true;
        if (Math.abs(requestedMovement.y) <= 1.0E-6) {
            return Vec3.ZERO;
        }
        move(MoverType.SELF, new Vec3(0.0, requestedMovement.y, 0.0));
        return position().subtract(beforeMove);
    }

    private boolean emptyShapeBlockIntersectsMovement(Vec3 beforeMove, Vec3 movement) {
        double horizontalDistance = movement.horizontalDistance();
        if (horizontalDistance < 1.0E-6) {
            return false;
        }
        int samples = Math.max(1, (int) Math.ceil(horizontalDistance / 0.20));
        for (int sample = 1; sample <= samples; sample++) {
            double t = sample / (double) samples;
            AABB footprint = getBoundingBox()
                .move(beforeMove.subtract(position()))
                .move(movement.x * t, 0.0, movement.z * t)
                .inflate(0.02, 0.0, 0.02);
            if (emptyShapeBlockIntersects(footprint)) {
                return true;
            }
        }
        return false;
    }

    private boolean emptyShapeBlockIntersects(AABB box) {
        int x0 = (int) Math.floor(box.minX);
        int x1 = (int) Math.floor(box.maxX - 1.0E-6);
        int z0 = (int) Math.floor(box.minZ);
        int z1 = (int) Math.floor(box.maxZ - 1.0E-6);
        int y0 = (int) Math.floor(box.minY);
        int y1 = (int) Math.floor(box.maxY - 1.0E-6);
        for (int y = y0; y <= y1; y++) {
            for (int x = x0; x <= x1; x++) {
                for (int z = z0; z <= z1; z++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    BlockState state = level().getBlockState(pos);
                    VoxelShape shape = state.getCollisionShape(level(), pos, CollisionContext.of(this));
                    if (shape.isEmpty() && !state.isAir() && !state.canBeReplaced() && !isSoftCollisionBlock(state)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private Vec3 terrainFollowingMovement(Vec3 beforeMove, Vec3 requestedMovement) {
        if (!onGround() || requestedMovement.horizontalDistanceSqr() < 1.0E-6) {
            return null;
        }
        double step = maxUpStep();
        AABB currentFootprint = getBoundingBox().move(beforeMove.subtract(position()));
        double currentFloor = footprintFloorHeight(currentFootprint, currentFootprint.minY - step - 0.15, currentFootprint.minY + 0.15);
        if (Double.isNaN(currentFloor)) {
            return null;
        }
        double snapCorrection = currentFootprint.minY - currentFloor;
        if (snapCorrection < 0.0 || snapCorrection > step + 0.15) {
            return null;
        }
        AABB targetFootprint = currentFootprint.move(requestedMovement.x, 0.0, requestedMovement.z);
        double targetFloor = footprintFloorHeight(targetFootprint, currentFloor - step - 0.15, currentFloor + step + 0.15);
        if (Double.isNaN(targetFloor)) {
            return null;
        }
        double floorDelta = targetFloor - currentFloor;
        if (floorDelta > step + 1.0E-4 || floorDelta < -step - 0.15) {
            return null;
        }
        if (floorDelta > TERRAIN_CLIMB_CLEARANCE && hasBlockingShapeAbove(currentFootprint, requestedMovement, targetFloor)) {
            return null;
        }
        double dyTotal = -snapCorrection + floorDelta;
        AABB targetBox = currentFootprint.move(requestedMovement.x, requestedMovement.y + dyTotal, requestedMovement.z);
        if (!level().noCollision(this, targetBox)) {
            for (VoxelShape shape : level().getBlockCollisions(this, targetBox)) {
                if (shape.max(Direction.Axis.Y) > targetFloor + step + 0.01) {
                    return null;
                }
            }
        }
        lastTerrainPositionCorrectionY = -snapCorrection;
        return new Vec3(requestedMovement.x, requestedMovement.y + dyTotal, requestedMovement.z);
    }

    private boolean hasBlockingShapeAbove(AABB currentFootprint, Vec3 requestedMovement, double targetFloor) {
        AABB sweptFootprint = new AABB(
            Math.min(currentFootprint.minX, currentFootprint.minX + requestedMovement.x),
            targetFloor + TERRAIN_CLIMB_CLEARANCE,
            Math.min(currentFootprint.minZ, currentFootprint.minZ + requestedMovement.z),
            Math.max(currentFootprint.maxX, currentFootprint.maxX + requestedMovement.x),
            targetFloor + maxUpStep() + TERRAIN_CLIMB_CLEARANCE,
            Math.max(currentFootprint.maxZ, currentFootprint.maxZ + requestedMovement.z)
        ).inflate(0.02, 0.0, 0.02);

        int x0 = (int) Math.floor(sweptFootprint.minX);
        int x1 = (int) Math.floor(sweptFootprint.maxX - 1.0E-6);
        int z0 = (int) Math.floor(sweptFootprint.minZ);
        int z1 = (int) Math.floor(sweptFootprint.maxZ - 1.0E-6);
        int y0 = (int) Math.floor(sweptFootprint.minY);
        int y1 = (int) Math.floor(sweptFootprint.maxY);
        for (int y = y0; y <= y1; y++) {
            for (int x = x0; x <= x1; x++) {
                for (int z = z0; z <= z1; z++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    VoxelShape shape = level().getBlockState(pos).getCollisionShape(level(), pos, CollisionContext.of(this));
                    if (!shape.isEmpty() && shape.bounds().move(pos).intersects(sweptFootprint)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private double footprintFloorHeight(AABB box, double minY, double maxY) {
        double highest = Double.NaN;
        int x0 = (int) Math.floor(box.minX);
        int x1 = (int) Math.floor(box.maxX - 1.0E-6);
        int z0 = (int) Math.floor(box.minZ);
        int z1 = (int) Math.floor(box.maxZ - 1.0E-6);
        int y0 = (int) Math.floor(minY);
        int y1 = (int) Math.floor(maxY);
        for (int y = y0; y <= y1; y++) {
            for (int x = x0; x <= x1; x++) {
                for (int z = z0; z <= z1; z++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    VoxelShape shape = level().getBlockState(pos).getCollisionShape(level(), pos, CollisionContext.of(this));
                    if (shape.isEmpty()) continue;
                    double h = pos.getY() + shape.max(Direction.Axis.Y);
                    if (h >= minY - 1.0E-4 && h <= maxY + 1.0E-4) {
                        highest = Double.isNaN(highest) ? h : Math.max(highest, h);
                    }
                }
            }
        }
        return highest;
    }

    private void clearHollowCollisionBlocks(boolean onlyAfterCollision) {
        if (!(level() instanceof ServerLevel serverLevel) || (onlyAfterCollision && !horizontalCollision && !verticalCollision)) {
            return;
        }
        for (BlockPos pos : BlockPos.betweenClosed(
            (int) Math.floor(getBoundingBox().minX) - 1,
            (int) Math.floor(getBoundingBox().minY),
            (int) Math.floor(getBoundingBox().minZ) - 1,
            (int) Math.floor(getBoundingBox().maxX) + 1,
            (int) Math.floor(getBoundingBox().maxY) + 1,
            (int) Math.floor(getBoundingBox().maxZ) + 1
        )) {
            if (isSoftCollisionBlock(serverLevel.getBlockState(pos))) {
                serverLevel.destroyBlock(pos, false, this);
            }
        }
    }

    private boolean isSoftCollisionBlock(BlockState state) {
        Block block = state.getBlock();
        return state.canBeReplaced()
            || state.is(BlockTags.FLOWERS)
            || state.is(BlockTags.ALL_SIGNS)
            || block instanceof LeavesBlock
            || block == Blocks.VINE
            || block == Blocks.SNOW;
    }

    private double snapToNearbyGround(Vec3 requestedMovement, Vec3 actualMovement) {
        if (onGround() || verticalCollision || requestedMovement.y >= -0.02 || actualMovement.y < -maxUpStep()) {
            return 0.0;
        }
        double snapDistance = maxUpStep() + 0.05;
        Vec3 beforeSnap = position();
        move(MoverType.SELF, new Vec3(0.0, -snapDistance, 0.0));
        double snappedDelta = getY() - beforeSnap.y;
        if (onGround() && snappedDelta < -0.02 && snappedDelta >= -snapDistance) {
            return snappedDelta;
        }
        setPos(beforeSnap.x, beforeSnap.y, beforeSnap.z);
        return 0.0;
    }

    private boolean isClimbLikeCollision() {
        return lastClimbDelta > 0.05 && lastClimbDelta <= maxUpStep() + 0.05;
    }

    private void tickImpactDamage() {
        if (horizontalCollision && previousHorizontalSpeed > 0.08) {
            Vec3 barrierNormal = nearbyBarrierNormal();
            boolean barrierImpact = barrierNormal.lengthSqr() > 0.0;
            if (!barrierImpact && isClimbLikeCollision()) {
                return;
            }
            double approachFactor = barrierImpact ? barrierApproachFactor(barrierNormal) : 1.0;
            float soundSeverity = (float) Math.max(0.6, previousHorizontalSpeed * (barrierImpact ? 9.0 * approachFactor : 14.0));
            if (previousHorizontalSpeed <= 0.28) {
                playCollisionSound(soundSeverity, true);
                return;
            }

            float severity = (float) ((previousHorizontalSpeed - 0.28) * (barrierImpact ? 14.0 * approachFactor : 40.0));
            addDamage(severity);
            playImpactFeedback(Math.max(severity, soundSeverity));
            if (barrierImpact) {
                setDeltaMovement(bounceFromBarrier(getDeltaMovement(), barrierNormal, approachFactor));
            } else {
                setDeltaMovement(getDeltaMovement().scale(0.15));
            }

            Entity passenger = getControllingPassenger();
            if (passenger instanceof Player player) {
                player.hurt(damageSources().flyIntoWall(), Math.max(1.0f, severity * 0.35f));
            }
            if (getDamagePercent() >= 100.0f && level() instanceof ServerLevel serverLevel) {
                destroyIntoMaterials(serverLevel);
            }
        }
    }

    private void handleEntityImpacts(Vec3 beforeMove, Vec3 actualMovement) {
        if (!(level() instanceof ServerLevel serverLevel)) {
            return;
        }
        double impactSpeed = actualMovement.horizontalDistance();
        if (impactSpeed < ENTITY_IMPACT_MIN_SPEED) {
            return;
        }

        AABB sweptBox = sweptBoundingBox(beforeMove).inflate(0.18, 0.08, 0.18);
        Vec3 horizontalMovement = new Vec3(actualMovement.x, 0.0, actualMovement.z);
        long time = level().getGameTime();
        if (lastEntityImpactById.size() > 48) {
            lastEntityImpactById.entrySet().removeIf(entry -> time - entry.getValue() > 200L);
        }

        for (Entity target : level().getEntities(this, sweptBox, this::canImpactEntity)) {
            Long lastImpactAt = lastEntityImpactById.get(target.getId());
            if (lastImpactAt != null && time - lastImpactAt < ENTITY_IMPACT_COOLDOWN_TICKS) {
                continue;
            }
            if (!target.getBoundingBox().inflate(0.08).intersects(sweptBox)) {
                continue;
            }

            Vec3 normal = entityImpactNormal(target, horizontalMovement);
            double approachSpeed = horizontalMovement.dot(normal);
            double resolvedSpeed = Math.max(impactSpeed * 0.45, approachSpeed);
            if (resolvedSpeed < ENTITY_IMPACT_MIN_SPEED) {
                continue;
            }

            lastEntityImpactById.put(target.getId(), time);
            boolean carTarget = target instanceof OpenwheelCarEntity;
            playCollisionSound((float) Math.max(0.6, resolvedSpeed * (carTarget ? 18.0 : 12.0)), carTarget);
            float carSeverity = (float) Math.max(0.0, (resolvedSpeed - ENTITY_IMPACT_SOFT_SPEED) * (carTarget ? ENTITY_IMPACT_OTHER_CAR_DAMAGE : ENTITY_IMPACT_CAR_DAMAGE));
            if (carSeverity > 0.0f) {
                addDamage(carSeverity);
                playImpactFeedback(carSeverity);
            }

            if (target instanceof OpenwheelCarEntity otherCar) {
                float otherSeverity = (float) Math.max(0.0, (resolvedSpeed - ENTITY_IMPACT_SOFT_SPEED) * ENTITY_IMPACT_OTHER_CAR_DAMAGE);
                if (otherSeverity > 0.0f) {
                    otherCar.addDamage(otherSeverity);
                    otherCar.playImpactFeedback(otherSeverity);
                }
            } else if (target instanceof LivingEntity livingEntity) {
                float targetDamage = (float) Math.max(1.0, (resolvedSpeed - ENTITY_IMPACT_MIN_SPEED) * ENTITY_IMPACT_LIVING_DAMAGE);
                livingEntity.hurtServer(serverLevel, damageSources().flyIntoWall(), targetDamage);
            }

            applyEntityImpactResponse(target, normal, resolvedSpeed, carTarget);
            if (getDamagePercent() >= 100.0f) {
                destroyIntoMaterials(serverLevel);
                return;
            }
        }
    }

    private boolean canImpactEntity(Entity entity) {
        return entity != this
            && entity.isAlive()
            && !hasPassenger(entity)
            && !(entity.getVehicle() instanceof OpenwheelCarEntity)
            && !(entity instanceof HangingEntity)
            && (entity instanceof LivingEntity || entity instanceof OpenwheelCarEntity);
    }

    private AABB sweptBoundingBox(Vec3 beforeMove) {
        AABB currentBox = getBoundingBox();
        Vec3 offset = beforeMove.subtract(position());
        AABB previousBox = new AABB(
            currentBox.minX + offset.x,
            currentBox.minY + offset.y,
            currentBox.minZ + offset.z,
            currentBox.maxX + offset.x,
            currentBox.maxY + offset.y,
            currentBox.maxZ + offset.z
        );
        return new AABB(
            Math.min(previousBox.minX, currentBox.minX),
            Math.min(previousBox.minY, currentBox.minY),
            Math.min(previousBox.minZ, currentBox.minZ),
            Math.max(previousBox.maxX, currentBox.maxX),
            Math.max(previousBox.maxY, currentBox.maxY),
            Math.max(previousBox.maxZ, currentBox.maxZ)
        );
    }

    private Vec3 entityImpactNormal(Entity target, Vec3 horizontalMovement) {
        Vec3 normal = target.position().subtract(position());
        normal = new Vec3(normal.x, 0.0, normal.z);
        if (normal.lengthSqr() < 1.0E-4) {
            normal = horizontalMovement.lengthSqr() > 1.0E-4
                ? horizontalMovement
                : Vec3.directionFromRotation(0.0f, getYRot());
        }
        return normal.normalize();
    }

    private void applyEntityImpactResponse(Entity target, Vec3 normal, double impactSpeed, boolean carTarget) {
        Vec3 carVelocity = getDeltaMovement();
        Vec3 carHorizontalVelocity = new Vec3(carVelocity.x, 0.0, carVelocity.z);
        double intoTarget = Math.max(0.0, carHorizontalVelocity.dot(normal));
        Vec3 redirectedCarVelocity = carHorizontalVelocity
            .subtract(normal.scale(intoTarget * (carTarget ? 1.35 : 0.85)))
            .scale(carTarget ? 0.70 : 0.82);
        setDeltaMovement(new Vec3(redirectedCarVelocity.x, carVelocity.y, redirectedCarVelocity.z));

        double targetPush = Math.min(carTarget ? 0.45 : 0.80, impactSpeed * (carTarget ? 0.65 : 1.15));
        Vec3 targetVelocity = target.getDeltaMovement();
        target.setDeltaMovement(targetVelocity.add(normal.x * targetPush, carTarget ? 0.0 : 0.08, normal.z * targetPush));
    }

    private void tickWarnings() {
        long time = level().getGameTime();
        if (getTyreWearPercent() >= 70.0f && time - lastLowTyreWarningAt > 100L) {
            lastLowTyreWarningAt = time;
            messageDriver(Component.literal("Tyre condition low"));
        }
        if (getDamagePercent() >= 70.0f && time - lastDamageWarningAt > 100L) {
            lastDamageWarningAt = time;
            messageDriver(Component.literal("Car damage critical"));
        }
    }

    private void invalidateLap(String reason) {
        if (lapStartedAt >= 0L) {
            lapStartedAt = -1L;
            visitedCheckpoints.clear();
            visitedCheckpointSet.clear();
            entityData.set(CURRENT_LAP_TICKS, 0);
            entityData.set(CHECKPOINT_ARMED, false);
            showInvalidLap(reason);
        }
    }

    public void syncPlayerBestLap(Player player) {
        if (level() instanceof ServerLevel serverLevel) {
            entityData.set(BEST_LAP_TICKS, OWRLapRecords.get(serverLevel).getBestLap(player.getUUID()));
        }
    }

    public void prepareForDriver(Player player) {
        syncPlayerBestLap(player);
        double speed = Math.sqrt(getDeltaMovement().x * getDeltaMovement().x + getDeltaMovement().z * getDeltaMovement().z);
        if (!level().isClientSide()) {
            LOGGER.info("OWR car mounted id={} player={} pos={} delta={} gear={} speed={} bbox={}",
                getId(),
                player.getScoreboardName(),
                blockPosition(),
                getDeltaMovement(),
                getGear(),
                speed,
                getBoundingBox());
        }
    }

    private void logShift(String direction) {
        if (!level().isClientSide()) {
            LOGGER.info("OWR car shift id={} direction={} gear={} speedKmh={} rpm={} passenger={}",
                getId(),
                direction,
                getGear(),
                getSpeedKmh(),
                getRpm(),
                getControllingPassenger() == null ? "none" : getControllingPassenger().getScoreboardName());
        }
    }

    private boolean isForwardPass(Direction markerFacing) {
        Vec3 carForward = Vec3.directionFromRotation(0.0f, getYRot());
        Vec3 markerForward = new Vec3(markerFacing.getStepX(), 0.0, markerFacing.getStepZ());
        return carForward.dot(markerForward) > 0.35;
    }

    private Vec3 nearbyBarrierNormal() {
        Vec3 normal = Vec3.ZERO;
        Vec3 carCenter = position();
        for (BlockPos pos : BlockPos.betweenClosed(blockPosition().offset(-1, 0, -1), blockPosition().offset(1, 2, 1))) {
            if (level().getBlockState(pos).is(OWRBlocks.BARRIER.get())) {
                Vec3 away = carCenter.subtract(Vec3.atCenterOf(pos));
                away = new Vec3(away.x, 0.0, away.z);
                if (away.lengthSqr() > 1.0E-4) {
                    normal = normal.add(away.normalize());
                }
            }
        }
        return normal.lengthSqr() > 1.0E-4 ? normal.normalize() : Vec3.ZERO;
    }

    private double barrierApproachFactor(Vec3 barrierNormal) {
        Vec3 velocity = getDeltaMovement();
        Vec3 horizontalVelocity = new Vec3(velocity.x, 0.0, velocity.z);
        if (horizontalVelocity.lengthSqr() < 1.0E-6) {
            return 0.25;
        }
        double headOn = Math.max(0.0, -horizontalVelocity.normalize().dot(barrierNormal));
        return 0.18 + headOn * headOn * 0.82;
    }

    private Vec3 bounceFromBarrier(Vec3 velocity, Vec3 barrierNormal, double approachFactor) {
        Vec3 horizontalVelocity = new Vec3(velocity.x, 0.0, velocity.z);
        double intoBarrier = horizontalVelocity.dot(barrierNormal);
        Vec3 reflected = horizontalVelocity;
        if (intoBarrier < 0.0) {
            reflected = horizontalVelocity.subtract(barrierNormal.scale(1.55 * intoBarrier));
        }
        double retainedSpeed = 0.35 + (1.0 - approachFactor) * 0.45;
        reflected = reflected.scale(retainedSpeed);
        return new Vec3(reflected.x, velocity.y, reflected.z);
    }

    private void destroyIntoMaterials(ServerLevel serverLevel) {
        invalidateLap("car destroyed");
        spawnAtLocation(serverLevel, new ItemStack(OWRItems.CHASSIS.get()));
        spawnAtLocation(serverLevel, new ItemStack(OWRItems.ENGINE.get()));
        spawnAtLocation(serverLevel, new ItemStack(OWRItems.GEARBOX.get()));
        spawnAtLocation(serverLevel, new ItemStack(OWRItems.RUBBER.get(), Math.max(1, 4 - Math.round(getTyreWearPercent() / 25.0f))));
        discard();
        serverLevel.explode(null, getX(), getY(), getZ(), 1.8f, Level.ExplosionInteraction.NONE);
    }

    private void updateFrontSteeringOffGripRelief(double frontSaturation, double rearSaturation) {
        double frontDominance = Math.max(0.0, frontSaturation - rearSaturation * 0.85);
        double saturationRelief = (Math.max(frontSaturation, frontDominance) - STEERING_OFF_GRIP_RELIEF_START) / (STEERING_OFF_GRIP_RELIEF_FULL - STEERING_OFF_GRIP_RELIEF_START);
        frontSteeringOffGripRelief = clamp(saturationRelief, 0.0, 1.0);
    }

    private void tickFrontUndersteerWarning(double frontSaturation, double rearSaturation, double speedMetersPerSecond) {
        boolean frontLimited = frontSaturation >= FRONT_UNDERSTEER_WARNING_THRESHOLD
            && frontSaturation > rearSaturation + 0.08
            && Math.abs(steeringAngle) > Math.toRadians(0.8)
            && speedMetersPerSecond > 10.0;
        if (!frontLimited) {
            if (frontSaturation < FRONT_UNDERSTEER_WARNING_RECOVERY) {
                frontUndersteerWarningActive = false;
            }
            return;
        }

        long time = level().getGameTime();
        if (!frontUndersteerWarningActive || time - lastFrontUndersteerWarningAt >= FRONT_UNDERSTEER_WARNING_COOLDOWN) {
            frontUndersteerWarningActive = true;
            lastFrontUndersteerWarningAt = time;
            level().playSound(null, getX(), getY(), getZ(), SoundEvents.ARMADILLO_SCUTE_DROP, SoundSource.PLAYERS, 0.45f, 1.65f);
            messageDriver(Component.literal("Front tyres washing wide"));
        }
    }

    private void playShiftFeedback(float pitch) {
        level().playSound(null, getX(), getY(), getZ(), SoundEvents.METAL_PRESSURE_PLATE_CLICK_ON, SoundSource.PLAYERS, 0.35f, pitch);
    }

    private void messageDriver(Component message) {
        Entity passenger = getControllingPassenger();
        if (passenger instanceof Player player) {
            player.displayClientMessage(message, true);
        }
    }

    private void showInvalidLap(String reason) {
        Component reasonMessage = Component.literal(reason);
        Entity passenger = getControllingPassenger();
        if (passenger instanceof ServerPlayer serverPlayer) {
            serverPlayer.connection.send(new ClientboundSetTitlesAnimationPacket(5, 35, 10));
            serverPlayer.connection.send(new ClientboundSetTitleTextPacket(Component.literal("INVALID LAP")));
            serverPlayer.connection.send(new ClientboundSetSubtitleTextPacket(reasonMessage));
        }
        messageDriver(Component.literal("INVALID LAP: " + reason));
    }

    private int updateEngineRpm(double speed, int gear, double gearTopSpeed, double throttle, boolean launchClutch, boolean clutchReleasing) {
        int currentRpm = getRpm();
        if (gear == NEUTRAL_GEAR) {
            double rpm = currentRpm;
            if (throttle > 0.0) {
                rpm += NEUTRAL_RPM_RISE_PER_SECOND * throttle * PHYSICS_DT;
            } else {
                rpm -= NEUTRAL_RPM_DECAY_PER_SECOND * PHYSICS_DT;
            }
            return clampRpm(rpm);
        }

        double wheelRpm = wheelRpm(speed, gearTopSpeed);
        double rpm = Math.max(IDLE_RPM, wheelRpm);
        if (launchClutch || clutchReleasing) {
            int storedRpm = Math.max(currentRpm, clutchReleaseRpm);
            double releasedRpm = storedRpm - CLUTCH_RPM_DROP_PER_SECOND * PHYSICS_DT;
            clutchReleaseRpm = clampRpm(releasedRpm);
            rpm = Math.max(rpm, releasedRpm);
            if (launchClutch) {
                rpm = Math.max(rpm, LAUNCH_RPM);
            }
        } else if (throttle == 0.0 && currentRpm > rpm) {
            rpm = Math.max(rpm, currentRpm - ENGINE_BRAKE_RPM_DROP_PER_SECOND * PHYSICS_DT);
        }
        return clampRpm(rpm);
    }

    private static double wheelRpm(double speed, double gearTopSpeed) {
        return gearTopSpeed <= 0.0 ? IDLE_RPM : speed / gearTopSpeed * REDLINE_RPM;
    }

    private static int clampRpm(double rpm) {
        return (int) Math.max(IDLE_RPM, Math.min(REDLINE_RPM, rpm));
    }

    private static double powerAcceleration(double powerWatts, double speedBlocksPerTick) {
        double speedMetersPerSecond = Math.max(MIN_POWER_SPEED, speedBlocksPerTick * 20.0);
        double accelerationMetersPerSecondSquared = powerWatts / (CAR_MASS_KG * speedMetersPerSecond);
        return accelerationMetersPerSecondSquared / 400.0;
    }

    private static double enginePowerWatts(int rpm) {
        if (rpm <= ENGINE_RPM_POINTS[0]) {
            return PEAK_POWER_WATTS * ENGINE_POWER_POINTS[0];
        }
        for (int i = 1; i < ENGINE_RPM_POINTS.length; i++) {
            if (rpm <= ENGINE_RPM_POINTS[i]) {
                double t = (rpm - ENGINE_RPM_POINTS[i - 1]) / (ENGINE_RPM_POINTS[i] - ENGINE_RPM_POINTS[i - 1]);
                double power = ENGINE_POWER_POINTS[i - 1] + (ENGINE_POWER_POINTS[i] - ENGINE_POWER_POINTS[i - 1]) * t;
                return PEAK_POWER_WATTS * power;
            }
        }
        return PEAK_POWER_WATTS * ENGINE_POWER_POINTS[ENGINE_POWER_POINTS.length - 1];
    }

    private static double combustionPowerWatts(int rpm) {
        return enginePowerWatts(rpm) * (PEAK_POWER_WATTS - ERS_POWER_SHARE_WATTS) / PEAK_POWER_WATTS;
    }

    private ErsPowerResult calculateErsPower(double throttle, double brake, boolean liftInputConfirmed, int gear, double speedBlocksPerTick, double velocityLong, double lateralAccelerationG, double dt, SurfaceProfile surface) {
        if (level().isClientSide()) {
            return new ErsPowerResult(0.0);
        }

        int mode = getErsMode();
        double speedKmh = speedBlocksPerTick * VehiclePhysics.KMH_PER_BLOCK_PER_TICK;
        double capacityJoules = getErsCapacityJoules();
        double storedEnergy = entityData.get(ERS_ENERGY);
        double powerWatts = 0.0;
        int activity = ERS_ACTIVITY_NEUTRAL;

        int harvestNegativeStartKmh = getErsHarvestNegativeStartKmh();
        int harvestNegativeFullKmh = getErsHarvestNegativeFullKmh();
        int balancedClipStartKmh = getErsBalancedClipStartKmh();
        int balancedClipEndKmh = getErsBalancedClipEndKmh();

        if (canLiftAndCoastHarvest(mode, throttle, brake, liftInputConfirmed, gear, speedKmh, velocityLong, lateralAccelerationG, surface)) {
            double targetPowerWatts = licoTargetPowerWatts(mode);
            double rampWatts = ERS_LICO_POWER_RAMP_KW_PER_SECOND * 1000.0 * dt;
            double currentNegativePowerWatts = Math.min(0.0, entityData.get(ERS_POWER_KW) * 1000.0);
            ersLiftAndCoastPowerWatts = Math.min(ersLiftAndCoastPowerWatts, currentNegativePowerWatts);
            ersLiftAndCoastPowerWatts = Math.max(targetPowerWatts, ersLiftAndCoastPowerWatts - rampWatts);
            powerWatts = ersLiftAndCoastPowerWatts;
            activity = ersActivityForPower(powerWatts);
            storedEnergy = applyErsPowerToEnergy(storedEnergy, capacityJoules, powerWatts, dt);
        } else if (mode == ERS_MODE_HARVEST && gear > NEUTRAL_GEAR && velocityLong > 0.0 && speedKmh > harvestNegativeStartKmh && surface.countsAsTrack) {
            powerWatts = interpolatePowerWatts(speedKmh, harvestNegativeStartKmh, harvestNegativeFullKmh, getErsHarvestStartPowerKw(), getErsHarvestEndPowerKw());
            ersLiftAndCoastPowerWatts = Math.min(0.0, powerWatts);
            activity = ersActivityForPower(powerWatts);
            storedEnergy = applyErsPowerToEnergy(storedEnergy, capacityJoules, powerWatts, dt);
        } else if (throttle > 0.0 && gear > NEUTRAL_GEAR && storedEnergy > 1.0 && surface.countsAsTrack) {
            double requestedPower = switch (mode) {
                case ERS_MODE_ATTACK -> getErsAttackPowerKw() * 1000.0;
                case ERS_MODE_BALANCED -> interpolatePowerWatts(speedKmh, balancedClipStartKmh, balancedClipEndKmh, getErsBalancedStartPowerKw(), getErsBalancedEndPowerKw());
                default -> 0.0;
            };
            requestedPower *= throttle;
            powerWatts = Math.min(requestedPower, storedEnergy / Math.max(1.0E-6, dt));
            if (powerWatts > 0.0) {
                storedEnergy -= powerWatts * dt;
                activity = ERS_ACTIVITY_DEPLOYING;
            }
        } else {
            ersLiftAndCoastPowerWatts = 0.0;
        }

        entityData.set(ERS_ENERGY, (float) clamp(storedEnergy, 0.0, capacityJoules));
        entityData.set(ERS_ACTIVITY, activity);
        entityData.set(ERS_POWER_KW, (float) (powerWatts / 1000.0));
        return new ErsPowerResult(powerWatts);
    }

    private static double interpolatePowerWatts(double speedKmh, int startKmh, int endKmh, int startPowerKw, int endPowerKw) {
        double t = smoothstep((speedKmh - startKmh) / Math.max(1.0, endKmh - startKmh));
        return (startPowerKw + (endPowerKw - startPowerKw) * t) * 1000.0;
    }

    private static int ersActivityForPower(double powerWatts) {
        return powerWatts < 0.0 ? ERS_ACTIVITY_NEGATIVE : ERS_ACTIVITY_NEUTRAL;
    }

    private static double applyErsPowerToEnergy(double storedEnergy, double capacityJoules, double powerWatts, double dt) {
        return Math.min(capacityJoules, storedEnergy + Math.max(0.0, -powerWatts) * dt * ERS_RECOVERY_EFFICIENCY);
    }

    private void reconcilePowerLimitedErsEnergy(double requestedIceEnergyJoules, double requestedPositiveErsEnergyJoules, double requestedNegativeErsEnergyJoules, double actualPositiveDriveEnergyJoules, double dt) {
        if (level().isClientSide()) {
            return;
        }
        double usefulPositiveErsEnergy = Math.min(requestedPositiveErsEnergyJoules, Math.max(0.0, actualPositiveDriveEnergyJoules - requestedIceEnergyJoules));
        double wastedPositiveErsEnergy = Math.max(0.0, requestedPositiveErsEnergyJoules - usefulPositiveErsEnergy);
        double unusedIceEnergy = Math.max(0.0, requestedIceEnergyJoules - actualPositiveDriveEnergyJoules);
        double negativeLimitJoules = maxCurrentErsNegativePowerWatts() * dt;
        double negativeRequestEnergy = Math.min(negativeLimitJoules, unusedIceEnergy + requestedNegativeErsEnergyJoules);
        double recoveryEnergy = negativeRequestEnergy * ERS_RECOVERY_EFFICIENCY;
        double storedEnergy = entityData.get(ERS_ENERGY);
        double capacityJoules = getErsCapacityJoules();
        storedEnergy = clamp(storedEnergy + wastedPositiveErsEnergy + recoveryEnergy, 0.0, capacityJoules);
        entityData.set(ERS_ENERGY, (float) storedEnergy);
        if (recoveryEnergy > 1.0 && usefulPositiveErsEnergy <= 1.0) {
            entityData.set(ERS_ACTIVITY, ERS_ACTIVITY_NEGATIVE);
            entityData.set(ERS_POWER_KW, (float) (-negativeRequestEnergy / Math.max(1.0E-6, dt) / 1000.0));
        } else if (usefulPositiveErsEnergy > 1.0) {
            entityData.set(ERS_ACTIVITY, ERS_ACTIVITY_DEPLOYING);
            entityData.set(ERS_POWER_KW, (float) (usefulPositiveErsEnergy / Math.max(1.0E-6, dt) / 1000.0));
        }
    }

    private double maxCurrentErsNegativePowerWatts() {
        return Math.max(0.0, Math.max(Math.max(Math.abs(getErsHarvestStartPowerKw()), Math.abs(getErsHarvestEndPowerKw())), Math.max(Math.abs(getErsLicoHarvestPowerKw()), Math.max(Math.abs(getErsLicoBalancedPowerKw()), Math.abs(getErsLicoAttackPowerKw()))))) * 1000.0;
    }

    private boolean updateLiftAndCoastConfirmation(double throttle, double brake) {
        if (throttle <= ERS_LICO_THROTTLE_DEADZONE && brake <= ERS_LICO_BRAKE_DEADZONE) {
            ersLiftConfirmTicks = Math.min(ERS_LICO_LIFT_CONFIRM_TICKS, ersLiftConfirmTicks + 1);
        } else {
            ersLiftConfirmTicks = 0;
            ersLiftAndCoastPowerWatts = 0.0;
            ersLiftAndCoastArmed = false;
        }
        return ersLiftConfirmTicks >= ERS_LICO_LIFT_CONFIRM_TICKS;
    }

    private boolean canLiftAndCoastHarvest(int mode, double throttle, double brake, boolean liftInputConfirmed, int gear, double speedKmh, double velocityLong, double lateralAccelerationG, SurfaceProfile surface) {
        if (!liftInputConfirmed
                || licoTargetPowerWatts(mode) >= 0.0
                || throttle > ERS_LICO_THROTTLE_DEADZONE
                || brake > ERS_LICO_BRAKE_DEADZONE
                || gear <= NEUTRAL_GEAR
                || velocityLong <= 0.0
                || Math.abs(steeringAngle) > Math.toRadians(getErsLicoSteeringThresholdDegrees())
                || Math.abs(lateralAccelerationG) > getErsLicoLateralGThreshold()
                || !surface.countsAsTrack) {
            ersLiftAndCoastArmed = false;
            return false;
        }
        if (!ersLiftAndCoastArmed && speedKmh < getErsLicoSpeedThresholdKmh()) {
            return false;
        }
        ersLiftAndCoastArmed = true;
        return true;
    }

    private double licoTargetPowerWatts(int mode) {
        int powerKw = switch (mode) {
            case ERS_MODE_HARVEST -> getErsLicoHarvestPowerKw();
            case ERS_MODE_BALANCED -> getErsLicoBalancedPowerKw();
            case ERS_MODE_ATTACK -> getErsLicoAttackPowerKw();
            default -> 0;
        };
        return powerKw * 1000.0;
    }

    private void harvestErsFromBraking(double previousSpeedBlocksPerTick, double currentSpeedBlocksPerTick, double brake, SurfaceProfile surface) {
        if (level().isClientSide() || brake <= 0.0 || !onGround() || !surface.countsAsTrack || horizontalCollision) {
            return;
        }
        if (getGear() <= NEUTRAL_GEAR || previousSpeedBlocksPerTick <= currentSpeedBlocksPerTick || previousSpeedBlocksPerTick < 0.04) {
            return;
        }
        double beforeMps = previousSpeedBlocksPerTick * 20.0;
        double afterMps = currentSpeedBlocksPerTick * 20.0;
        double kineticDeltaJ = 0.5 * CAR_MASS_KG * Math.max(0.0, beforeMps * beforeMps - afterMps * afterMps);
        double modeFactor = switch (getErsMode()) {
            case ERS_MODE_HARVEST -> 1.35;
            case ERS_MODE_ATTACK -> 0.65;
            default -> 1.0;
        };
        double harvested = Math.min(ERS_MAX_HARVEST_PER_TICK_J, kineticDeltaJ * clamp(brake, 0.0, 1.0) * modeFactor * ERS_RECOVERY_EFFICIENCY);
        if (harvested <= 1.0) {
            return;
        }
        setErsEnergyJoules(entityData.get(ERS_ENERGY) + harvested);
        if (entityData.get(ERS_ACTIVITY) == ERS_ACTIVITY_NEUTRAL) {
            entityData.set(ERS_ACTIVITY, ERS_ACTIVITY_HARVESTING);
            entityData.set(ERS_POWER_KW, (float) (-harvested / PHYSICS_DT / 1000.0));
        }
    }

    private record ErsPowerResult(double powerWatts) {}

    private void resetTyreRelaxation() {
        relaxedFlLatForce = 0.0;
        relaxedFrLatForce = 0.0;
        relaxedRlLatForce = 0.0;
        relaxedRrLatForce = 0.0;
    }

    private static double loadSensitiveMu(double baseMu, double normalLoad, double referenceLoad) {
        return Math.max(MIN_SURFACE_MU, baseMu * (1.0 - LOAD_SENSITIVITY * (normalLoad / referenceLoad - 1.0)));
    }

    private static double pacejkaLongitudinalForce(double slipRatio, double stiffness, double peakForce) {
        return pacejkaForce(slipRatio, stiffness, peakForce, PACEJKA_LONGITUDINAL_SHAPE, PACEJKA_LONGITUDINAL_CURVATURE);
    }

    private static double pacejkaLateralForce(double slipAngle, double stiffness, double peakForce) {
        if (Math.abs(slipAngle) < SLIP_ANGLE_DEADBAND) {
            return 0.0;
        }
        return -pacejkaForce(slipAngle, stiffness, peakForce, PACEJKA_LATERAL_SHAPE, PACEJKA_LATERAL_CURVATURE);
    }

    private static double pacejkaForce(double slip, double stiffness, double peakForce, double shape, double curvature) {
        if (peakForce <= 1.0 || stiffness <= 1.0) {
            return 0.0;
        }
        double stiffnessFactor = stiffness / Math.max(1.0, shape * peakForce);
        double term = stiffnessFactor * slip;
        return peakForce * Math.sin(shape * Math.atan(term - curvature * (term - Math.atan(term))));
    }

    private static double longitudinalSlipRatio(double requestedForce, double stiffness, double peakForce) {
        if (peakForce <= 1.0 || stiffness <= 1.0) {
            return 0.0;
        }
        return clamp(requestedForce / stiffness, -1.8, 1.8);
    }

    private static WheelForces calculateWheelForces(
            double localX, double localZ, double steerAngle,
            double velocityLong, double velocityLat, double yawRate,
            double longitudinalRequest, double muLong, double muLat, double normalLoad,
            double longitudinalStiffness, double lateralStiffness,
            double longitudinalLimit, double lateralLimit,
            double relaxationLength, double dt, double carSpeed,
            boolean steeringReleased, double previousRelaxedLateralForce) {
        double patchLatVelocity = velocityLat + yawRate * localZ;
        double patchLongVelocity = velocityLong - yawRate * localX;
        double cos = Math.cos(steerAngle);
        double sin = Math.sin(steerAngle);
        double wheelLongVelocity = patchLongVelocity * cos + patchLatVelocity * sin;
        double wheelLatVelocity = -patchLongVelocity * sin + patchLatVelocity * cos;
        double slipAngle = Math.abs(wheelLatVelocity) < 0.04 && Math.abs(yawRate) < 0.01 && Math.abs(steerAngle) < SLIP_ANGLE_DEADBAND
            ? 0.0
            : Math.atan2(wheelLatVelocity, Math.max(6.0, Math.abs(wheelLongVelocity)));
        double longitudinalSlip = longitudinalSlipRatio(longitudinalRequest, longitudinalStiffness, longitudinalLimit);
        double longitudinalForce = pacejkaLongitudinalForce(longitudinalSlip, longitudinalStiffness, longitudinalLimit);
        double lateralTarget = lateralTyreForceTarget(slipAngle, wheelLatVelocity, lateralStiffness, lateralLimit, muLat * normalLoad, carSpeed);
        double relaxationGain = carSpeed < STATIC_TYRE_SPEED_THRESHOLD ? 1.0 : tyreRelaxationGain(Math.abs(wheelLongVelocity), relaxationLength, dt);
        double relaxedLateralForce = previousRelaxedLateralForce + (lateralTarget - previousRelaxedLateralForce) * relaxationGain;
        if (steeringReleased && carSpeed >= STATIC_TYRE_SPEED_THRESHOLD) {
            relaxedLateralForce += (lateralTarget - relaxedLateralForce) * relaxationGain;
        }
        if (Math.abs(lateralTarget) < 1.0) {
            relaxedLateralForce = 0.0;
        }
        TyreForces combined = applyCombinedSlip(longitudinalForce, relaxedLateralForce, longitudinalLimit, lateralLimit);
        double bodyLongitudinalForce = combined.longitudinal() * cos - combined.lateral() * sin;
        double bodyLateralForce = combined.longitudinal() * sin + combined.lateral() * cos;
        double yawMoment = localZ * bodyLateralForce - localX * bodyLongitudinalForce;
        return new WheelForces(bodyLongitudinalForce, bodyLateralForce, combined.demand(), slipAngle, relaxedLateralForce, yawMoment);
    }

    private static double lateralTyreForceTarget(double slipAngle, double wheelLatVelocity, double stiffness, double lateralLimit, double staticLimit, double carSpeed) {
        double dynamicForce = pacejkaLateralForce(slipAngle, stiffness, lateralLimit);
        if (carSpeed >= STATIC_TYRE_SPEED_THRESHOLD) {
            return dynamicForce;
        }
        double staticForce = clamp(-wheelLatVelocity * stiffness, -staticLimit, staticLimit);
        double blend = smoothstep(carSpeed / STATIC_TYRE_SPEED_THRESHOLD);
        return staticForce + (dynamicForce - staticForce) * blend;
    }

    private static TyreForces applyCombinedSlip(double longitudinalForce, double lateralForce, double longitudinalLimit, double lateralLimit) {
        double demand = combinedSlipDemand(longitudinalForce, lateralForce, longitudinalLimit, lateralLimit);
        if (demand <= 1.0) {
            return new TyreForces(longitudinalForce, lateralForce, demand);
        }
        double scale = Math.min(1.0 / demand, KINETIC_MU_RATIO);
        return new TyreForces(longitudinalForce * scale, lateralForce * scale, demand);
    }

    private record TyreForces(double longitudinal, double lateral, double demand) {}
    private record WheelForces(double bodyLongitudinalForce, double bodyLateralForce, double demand, double slipAngle, double relaxedLateralForce, double yawMoment) {}

    private static double tyreRelaxationGain(double speedMetersPerSecond, double relaxationLength, double dt) {
        double timeConstant = relaxationLength / Math.max(1.0, speedMetersPerSecond);
        return 1.0 - Math.exp(-dt / timeConstant);
    }

    private static double absLimitedBrakeForce(double brakeForce, double lateralForce, double longitudinalLimit, double lateralLimit) {
        double lateralUse = Math.abs(lateralForce) / Math.max(1.0, lateralLimit);
        double longitudinalAvailable = longitudinalLimit * Math.sqrt(Math.max(0.0, 0.96 * 0.96 - lateralUse * lateralUse));
        return clamp(brakeForce, -longitudinalAvailable, longitudinalAvailable);
    }

    private static double combinedSlipDemand(double longitudinalForce, double lateralForce, double longitudinalLimit, double lateralLimit) {
        double x = longitudinalForce / Math.max(1.0, longitudinalLimit);
        double y = lateralForce / Math.max(1.0, lateralLimit);
        return Math.sqrt(x * x + y * y);
    }

    private static double square(double value) {
        return value * value;
    }

    private static double smoothstep(double value) {
        double t = clamp(value, 0.0, 1.0);
        return t * t * (3.0 - 2.0 * t);
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private static String formatLapTime(int ticks) {
        int totalCentiseconds = ticks * 5;
        int minutes = totalCentiseconds / 6000;
        int seconds = totalCentiseconds / 100 % 60;
        int centiseconds = totalCentiseconds % 100;
        return String.format("%d:%02d.%02d", minutes, seconds, centiseconds);
    }

    private static String formatVec(Vec3 vec) {
        return String.format("(%.4f, %.4f, %.4f)", vec.x, vec.y, vec.z);
    }

    private void playImpactFeedback(float severity) {
        if (level() instanceof ServerLevel serverLevel) {
            playCollisionSound(serverLevel, severity, true);
            serverLevel.sendParticles(ParticleTypes.SMOKE, getX(), getY() + 0.35, getZ(), Math.min(18, 4 + (int) severity), 0.35, 0.18, 0.35, 0.03);
            serverLevel.sendParticles(ParticleTypes.CRIT, getX(), getY() + 0.35, getZ(), Math.min(12, 2 + (int) (severity * 0.5f)), 0.25, 0.12, 0.25, 0.15);
        }
    }

    private void playCollisionSound(float severity, boolean metallic) {
        if (level() instanceof ServerLevel serverLevel) {
            playCollisionSound(serverLevel, severity, metallic);
        }
    }

    private void playCollisionSound(ServerLevel serverLevel, float severity, boolean metallic) {
        float volume = Math.min(2.0f, 0.35f + severity * 0.09f);
        float pitchBase = metallic ? 1.05f : 1.35f;
        float pitch = Math.max(0.55f, pitchBase - severity * 0.035f);
        serverLevel.playSound(null, getX(), getY(), getZ(), SoundEvents.METAL_HIT, SoundSource.BLOCKS, volume, pitch);
    }

    private void addDamage(float amount) {
        entityData.set(DAMAGE, Math.min(100.0f, getDamagePercent() + amount * raceControlDamageModifier()));
    }

    private void addTyreWear(float amount) {
        entityData.set(TYRE_WEAR, Math.min(100.0f, getTyreWearPercent() + amount * raceControlTyreWearModifier()));
    }

    private float raceControlDamageModifier() {
        return level() instanceof ServerLevel serverLevel ? (float) OWRRaceControlState.get(serverLevel).getCarDamageModifier() : 1.0f;
    }

    private float raceControlTyreWearModifier() {
        return level() instanceof ServerLevel serverLevel ? (float) OWRRaceControlState.get(serverLevel).getTyreWearModifier() : 1.0f;
    }
}
