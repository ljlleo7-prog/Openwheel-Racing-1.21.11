package com.openwheelracing.network;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.openwheelracing.content.entity.OpenwheelCarEntity;
import com.openwheelracing.content.entity.VehiclePhysics;
import com.openwheelracing.content.entity.VehiclePhysicsPreset;
import com.openwheelracing.content.entity.VehiclePhysicsPresetState;
import com.openwheelracing.OpenwheelRacing;
import com.openwheelracing.content.car.CarLiveryTexture;
import com.openwheelracing.content.car.PrototypeCarSetup;
import com.openwheelracing.content.car.ServerLiveryTextures;
import com.openwheelracing.content.menu.CarAssemblyMenu;
import com.openwheelracing.content.item.PrototypeCarItem;
import com.openwheelracing.content.menu.CarPartsReplacementMenu;
import com.openwheelracing.content.menu.RaceDirectorMenu;
import com.openwheelracing.content.race.LapProfileCollector;
import com.openwheelracing.content.race.LapTimingPreferences;
import com.openwheelracing.content.race.LapTimingScope;
import com.openwheelracing.content.race.OWRLapProfiles;
import com.openwheelracing.content.race.OWRLapRecords;
import com.openwheelracing.content.race.OWRRaceControlState;
import com.openwheelracing.content.race.RaceDirectorLapRow;
import com.openwheelracing.content.race.RaceDirectorSnapshot;
import com.openwheelracing.content.race.BoPDriverRow;
import com.openwheelracing.content.race.BoPProfileState;
import com.openwheelracing.content.race.RaceFlagMode;
import com.openwheelracing.content.race.TeamCarRow;
import com.openwheelracing.content.race.timing.LiveRaceTimingService;
import com.openwheelracing.content.race.timing.LiveRaceTimingSnapshot;
import com.openwheelracing.content.race.timing.RaceGap;
import com.openwheelracing.content.race.timing.RaceParticipantKey;
import com.openwheelracing.content.race.timing.RaceParticipantKind;
import com.openwheelracing.content.race.timing.RacePositionChange;
import com.openwheelracing.content.race.timing.RaceProgressConfidence;
import com.openwheelracing.content.race.timing.RaceTimingRow;
import com.openwheelracing.content.track.TrackEditorMaterial;
import com.openwheelracing.content.track.TrackEditorMode;
import com.openwheelracing.content.track.TrackEditorOperation;
import com.openwheelracing.content.track.TrackEditorPlacementService;
import com.openwheelracing.content.track.TrackEditorPreset;
import com.openwheelracing.content.track.TrackEditorUndoStore;
import com.openwheelracing.content.track.TrackMapSnapshot;
import com.openwheelracing.content.track.TrackMapAutoDetector;
import com.openwheelracing.content.track.TrackDefinition;
import com.openwheelracing.content.track.survey.SurveyRoute;
import com.openwheelracing.registry.OWRItems;
import java.util.List;
import java.lang.reflect.Method;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.LevelBasedPermissionSet;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.event.network.CustomPayloadEvent;
import net.minecraftforge.network.ChannelBuilder;
import net.minecraftforge.network.SimpleChannel;

public final class OWRNetwork {
    private static final int PROTOCOL = 18;
    public static final SimpleChannel CHANNEL = ChannelBuilder
        .named(Identifier.fromNamespaceAndPath(OpenwheelRacing.MODID, "main"))
        .networkProtocolVersion(PROTOCOL)
        .simpleChannel();

    public static final int TIMING_STATUS_UNREACHED = 0;
    public static final int TIMING_STATUS_SLOWER = 1;
    public static final int TIMING_STATUS_PERSONAL_BEST = 2;
    public static final int TIMING_STATUS_SESSION_BEST = 3;

    private OWRNetwork() {
    }

    public static void register() {
        CHANNEL.messageBuilder(TuneCarMessage.class)
            .encoder(TuneCarMessage::encode)
            .decoder(TuneCarMessage::decode)
            .consumerMainThread(TuneCarMessage::handle)
            .add();
        CHANNEL.messageBuilder(ApplyCarSetupMessage.class)
            .encoder(ApplyCarSetupMessage::encode)
            .decoder(ApplyCarSetupMessage::decode)
            .consumerMainThread(ApplyCarSetupMessage::handle)
            .add();
        CHANNEL.messageBuilder(RepairCarMessage.class)
            .encoder(RepairCarMessage::encode)
            .decoder(RepairCarMessage::decode)
            .consumerMainThread(RepairCarMessage::handle)
            .add();
        CHANNEL.messageBuilder(StartPartReplacementMessage.class)
            .encoder(StartPartReplacementMessage::encode)
            .decoder(StartPartReplacementMessage::decode)
            .consumerMainThread(StartPartReplacementMessage::handle)
            .add();
        CHANNEL.messageBuilder(CycleLiveryMessage.class)
            .encoder(CycleLiveryMessage::encode)
            .decoder(CycleLiveryMessage::decode)
            .consumerMainThread(CycleLiveryMessage::handle)
            .add();
        CHANNEL.messageBuilder(SetLiveryColorMessage.class)
            .encoder(SetLiveryColorMessage::encode)
            .decoder(SetLiveryColorMessage::decode)
            .consumerMainThread(SetLiveryColorMessage::handle)
            .add();
        CHANNEL.messageBuilder(UploadLiveryTextureMessage.class)
            .encoder(UploadLiveryTextureMessage::encode)
            .decoder(UploadLiveryTextureMessage::decode)
            .consumerMainThread(UploadLiveryTextureMessage::handle)
            .add();
        CHANNEL.messageBuilder(SetLiveryTextureMessage.class)
            .encoder(SetLiveryTextureMessage::encode)
            .decoder(SetLiveryTextureMessage::decode)
            .consumerMainThread(SetLiveryTextureMessage::handle)
            .add();
        CHANNEL.messageBuilder(ShiftMessage.class)
            .encoder(ShiftMessage::encode)
            .decoder(ShiftMessage::decode)
            .consumerMainThread(ShiftMessage::handle)
            .add();
        CHANNEL.messageBuilder(ExitCarMessage.class)
            .encoder(ExitCarMessage::encode)
            .decoder(ExitCarMessage::decode)
            .consumerMainThread(ExitCarMessage::handle)
            .add();
        CHANNEL.messageBuilder(DriveInputMessage.class)
            .encoder(DriveInputMessage::encode)
            .decoder(DriveInputMessage::decode)
            .consumerMainThread(DriveInputMessage::handle)
            .add();
        CHANNEL.messageBuilder(ToggleAbsMessage.class)
            .encoder(ToggleAbsMessage::encode)
            .decoder(ToggleAbsMessage::decode)
            .consumerMainThread(ToggleAbsMessage::handle)
            .add();
        CHANNEL.messageBuilder(ToggleTractionControlMessage.class)
            .encoder(ToggleTractionControlMessage::encode)
            .decoder(ToggleTractionControlMessage::decode)
            .consumerMainThread(ToggleTractionControlMessage::handle)
            .add();
        CHANNEL.messageBuilder(ToggleDrsMessage.class)
            .encoder(ToggleDrsMessage::encode)
            .decoder(ToggleDrsMessage::decode)
            .consumerMainThread(ToggleDrsMessage::handle)
            .add();
        CHANNEL.messageBuilder(CycleErsModeMessage.class)
            .encoder(CycleErsModeMessage::encode)
            .decoder(CycleErsModeMessage::decode)
            .consumerMainThread(CycleErsModeMessage::handle)
            .add();
        CHANNEL.messageBuilder(SetErsModeMessage.class)
            .encoder(SetErsModeMessage::encode)
            .decoder(SetErsModeMessage::decode)
            .consumerMainThread(SetErsModeMessage::handle)
            .add();
        CHANNEL.messageBuilder(SetErsThresholdsMessage.class)
            .encoder(SetErsThresholdsMessage::encode)
            .decoder(SetErsThresholdsMessage::decode)
            .consumerMainThread(SetErsThresholdsMessage::handle)
            .add();
        CHANNEL.messageBuilder(MountCarMessage.class)
            .encoder(MountCarMessage::encode)
            .decoder(MountCarMessage::decode)
            .consumerMainThread(MountCarMessage::handle)
            .add();
        CHANNEL.messageBuilder(TrackEditorPlaceMessage.class)
            .encoder(TrackEditorPlaceMessage::encode)
            .decoder(TrackEditorPlaceMessage::decode)
            .consumerMainThread(TrackEditorPlaceMessage::handle)
            .add();
        CHANNEL.messageBuilder(TrackEditorUndoMessage.class)
            .encoder(TrackEditorUndoMessage::encode)
            .decoder(TrackEditorUndoMessage::decode)
            .consumerMainThread(TrackEditorUndoMessage::handle)
            .add();
        CHANNEL.messageBuilder(RaceDirectorToggleRuleMessage.class)
            .encoder(RaceDirectorToggleRuleMessage::encode)
            .decoder(RaceDirectorToggleRuleMessage::decode)
            .consumerMainThread(RaceDirectorToggleRuleMessage::handle)
            .add();
        CHANNEL.messageBuilder(RaceDirectorSetMinLapTicksMessage.class)
            .encoder(RaceDirectorSetMinLapTicksMessage::encode)
            .decoder(RaceDirectorSetMinLapTicksMessage::decode)
            .consumerMainThread(RaceDirectorSetMinLapTicksMessage::handle)
            .add();
        CHANNEL.messageBuilder(RaceDirectorSetRaceLapLimitMessage.class)
            .encoder(RaceDirectorSetRaceLapLimitMessage::encode)
            .decoder(RaceDirectorSetRaceLapLimitMessage::decode)
            .consumerMainThread(RaceDirectorSetRaceLapLimitMessage::handle)
            .add();
        CHANNEL.messageBuilder(SetLapTimingScopeMessage.class)
            .encoder(SetLapTimingScopeMessage::encode)
            .decoder(SetLapTimingScopeMessage::decode)
            .consumerMainThread(SetLapTimingScopeMessage::handle)
            .add();
        CHANNEL.messageBuilder(RaceDirectorSetErsLimitMessage.class)
            .encoder(RaceDirectorSetErsLimitMessage::encode)
            .decoder(RaceDirectorSetErsLimitMessage::decode)
            .consumerMainThread(RaceDirectorSetErsLimitMessage::handle)
            .add();
        CHANNEL.messageBuilder(RaceDirectorSetGlobalFlagMessage.class)
            .encoder(RaceDirectorSetGlobalFlagMessage::encode)
            .decoder(RaceDirectorSetGlobalFlagMessage::decode)
            .consumerMainThread(RaceDirectorSetGlobalFlagMessage::handle)
            .add();
        CHANNEL.messageBuilder(RaceDirectorSignalControlMessage.class)
            .encoder(RaceDirectorSignalControlMessage::encode)
            .decoder(RaceDirectorSignalControlMessage::decode)
            .consumerMainThread(RaceDirectorSignalControlMessage::handle)
            .add();
        CHANNEL.messageBuilder(RaceDirectorCycleConditionModifierMessage.class)
            .encoder(RaceDirectorCycleConditionModifierMessage::encode)
            .decoder(RaceDirectorCycleConditionModifierMessage::decode)
            .consumerMainThread(RaceDirectorCycleConditionModifierMessage::handle)
            .add();
        CHANNEL.messageBuilder(RaceDirectorStartSessionMessage.class)
            .encoder(RaceDirectorStartSessionMessage::encode)
            .decoder(RaceDirectorStartSessionMessage::decode)
            .consumerMainThread(RaceDirectorStartSessionMessage::handle)
            .add();
        CHANNEL.messageBuilder(RaceDirectorRefreshSessionMessage.class)
            .encoder(RaceDirectorRefreshSessionMessage::encode)
            .decoder(RaceDirectorRefreshSessionMessage::decode)
            .consumerMainThread(RaceDirectorRefreshSessionMessage::handle)
            .add();
        CHANNEL.messageBuilder(RaceDirectorGrandPrixControlMessage.class)
            .encoder(RaceDirectorGrandPrixControlMessage::encode)
            .decoder(RaceDirectorGrandPrixControlMessage::decode)
            .consumerMainThread(RaceDirectorGrandPrixControlMessage::handle)
            .add();
        CHANNEL.messageBuilder(RaceDirectorGrandPrixSetupMessage.class)
            .encoder(RaceDirectorGrandPrixSetupMessage::encode)
            .decoder(RaceDirectorGrandPrixSetupMessage::decode)
            .consumerMainThread(RaceDirectorGrandPrixSetupMessage::handle)
            .add();
        CHANNEL.messageBuilder(RaceDirectorSetArchiveModeMessage.class)
            .encoder(RaceDirectorSetArchiveModeMessage::encode)
            .decoder(RaceDirectorSetArchiveModeMessage::decode)
            .consumerMainThread(RaceDirectorSetArchiveModeMessage::handle)
            .add();
        CHANNEL.messageBuilder(RaceDirectorSetPageMessage.class)
            .encoder(RaceDirectorSetPageMessage::encode)
            .decoder(RaceDirectorSetPageMessage::decode)
            .consumerMainThread(RaceDirectorSetPageMessage::handle)
            .add();
        CHANNEL.messageBuilder(TeamTerminalSenseCarsMessage.class)
            .encoder(TeamTerminalSenseCarsMessage::encode)
            .decoder(TeamTerminalSenseCarsMessage::decode)
            .consumerMainThread(TeamTerminalSenseCarsMessage::handle)
            .add();
        CHANNEL.messageBuilder(TeamTerminalBindCarMessage.class)
            .encoder(TeamTerminalBindCarMessage::encode)
            .decoder(TeamTerminalBindCarMessage::decode)
            .consumerMainThread(TeamTerminalBindCarMessage::handle)
            .add();
        CHANNEL.messageBuilder(TeamTerminalAiPushMessage.class)
            .encoder(TeamTerminalAiPushMessage::encode)
            .decoder(TeamTerminalAiPushMessage::decode)
            .consumerMainThread(TeamTerminalAiPushMessage::handle)
            .add();
        CHANNEL.messageBuilder(MonitorTelemetrySubscribeMessage.class)
            .encoder(MonitorTelemetrySubscribeMessage::encode)
            .decoder(MonitorTelemetrySubscribeMessage::decode)
            .consumerMainThread(MonitorTelemetrySubscribeMessage::handle)
            .add();
        CHANNEL.messageBuilder(RaceMonitorAutoDetectMapMessage.class)
            .encoder(RaceMonitorAutoDetectMapMessage::encode)
            .decoder(RaceMonitorAutoDetectMapMessage::decode)
            .consumerMainThread(RaceMonitorAutoDetectMapMessage::handle)
            .add();
        CHANNEL.messageBuilder(RaceDirectorInvalidateLapMessage.class)
            .encoder(RaceDirectorInvalidateLapMessage::encode)
            .decoder(RaceDirectorInvalidateLapMessage::decode)
            .consumerMainThread(RaceDirectorInvalidateLapMessage::handle)
            .add();
        CHANNEL.messageBuilder(RaceDirectorSetBoPMessage.class)
            .encoder(RaceDirectorSetBoPMessage::encode)
            .decoder(RaceDirectorSetBoPMessage::decode)
            .consumerMainThread(RaceDirectorSetBoPMessage::handle)
            .add();
        CHANNEL.messageBuilder(RaceDirectorSetBoPLapsMessage.class)
            .encoder(RaceDirectorSetBoPLapsMessage::encode)
            .decoder(RaceDirectorSetBoPLapsMessage::decode)
            .consumerMainThread(RaceDirectorSetBoPLapsMessage::handle)
            .add();
        CHANNEL.messageBuilder(RaceDirectorSnapshotMessage.class)
            .encoder(RaceDirectorSnapshotMessage::encode)
            .decoder(RaceDirectorSnapshotMessage::decode)
            .consumerMainThread(RaceDirectorSnapshotMessage::handle)
            .add();
        CHANNEL.messageBuilder(TrackMoistureSnapshotMessage.class)
            .encoder(TrackMoistureSnapshotMessage::encode)
            .decoder(TrackMoistureSnapshotMessage::decode)
            .consumerMainThread(TrackMoistureSnapshotMessage::handle)
            .add();
        CHANNEL.messageBuilder(LiveryTextureCacheMessage.class)
            .encoder(LiveryTextureCacheMessage::encode)
            .decoder(LiveryTextureCacheMessage::decode)
            .consumerMainThread(LiveryTextureCacheMessage::handle)
            .add();
        CHANNEL.messageBuilder(RaceFlagUpdateMessage.class)
            .encoder(RaceFlagUpdateMessage::encode)
            .decoder(RaceFlagUpdateMessage::decode)
            .consumerMainThread(RaceFlagUpdateMessage::handle)
            .add();
        CHANNEL.messageBuilder(DriveInputAckMessage.class)
            .encoder(DriveInputAckMessage::encode)
            .decoder(DriveInputAckMessage::decode)
            .consumerMainThread(DriveInputAckMessage::handle)
            .add();
        CHANNEL.messageBuilder(RankingBoardMessage.class)
            .encoder(RankingBoardMessage::encode)
            .decoder(RankingBoardMessage::decode)
            .consumerMainThread(RankingBoardMessage::handle)
            .add();
        CHANNEL.messageBuilder(CommandFeedbackMessage.class)
            .encoder(CommandFeedbackMessage::encode)
            .decoder(CommandFeedbackMessage::decode)
            .consumerMainThread(CommandFeedbackMessage::handle)
            .add();
        CHANNEL.messageBuilder(StewardLineOverlayMessage.class)
            .encoder(StewardLineOverlayMessage::encode)
            .decoder(StewardLineOverlayMessage::decode)
            .consumerMainThread(StewardLineOverlayMessage::handle)
            .add();
        CHANNEL.messageBuilder(SurveyRouteOverlayMessage.class)
            .encoder(SurveyRouteOverlayMessage::encode)
            .decoder(SurveyRouteOverlayMessage::decode)
            .consumerMainThread(SurveyRouteOverlayMessage::handle)
            .add();
        CHANNEL.messageBuilder(AiRacingLineOverlayMessage.class)
            .encoder(AiRacingLineOverlayMessage::encode)
            .decoder(AiRacingLineOverlayMessage::decode)
            .consumerMainThread(AiRacingLineOverlayMessage::handle)
            .add();
        CHANNEL.messageBuilder(TimingDeltaHudMessage.class)
            .encoder(TimingDeltaHudMessage::encode)
            .decoder(TimingDeltaHudMessage::decode)
            .consumerMainThread(TimingDeltaHudMessage::handle)
            .add();
        CHANNEL.messageBuilder(LiveLapDeltaHudMessage.class)
            .encoder(LiveLapDeltaHudMessage::encode)
            .decoder(LiveLapDeltaHudMessage::decode)
            .consumerMainThread(LiveLapDeltaHudMessage::handle)
            .add();
        CHANNEL.messageBuilder(MonitorTelemetryMessage.class)
            .encoder(MonitorTelemetryMessage::encode)
            .decoder(MonitorTelemetryMessage::decode)
            .consumerMainThread(MonitorTelemetryMessage::handle)
            .add();
        CHANNEL.messageBuilder(LiveRaceTimingSnapshotMessage.class)
            .encoder(LiveRaceTimingSnapshotMessage::encode)
            .decoder(LiveRaceTimingSnapshotMessage::decode)
            .consumerMainThread(LiveRaceTimingSnapshotMessage::handle)
            .add();
        CHANNEL.messageBuilder(VehiclePhysicsPresetMessage.class)
            .encoder(VehiclePhysicsPresetMessage::encode)
            .decoder(VehiclePhysicsPresetMessage::decode)
            .consumerMainThread(VehiclePhysicsPresetMessage::handle)
            .add();
    }

    public static void sendDriveInputAck(ServerPlayer player, OpenwheelCarEntity car) {
        CHANNEL.send(new DriveInputAckMessage(
            car.getId(),
            car.getLastAcceptedInputSequence(),
            car.getX(),
            car.getY(),
            car.getZ(),
            car.getDeltaMovement().x,
            car.getDeltaMovement().y,
            car.getDeltaMovement().z,
            car.getYRot(),
            car.getYawRateRadiansPerSecond(),
            car.getSteeringAngleRadians(),
            car.getRelaxedFlLateralForce(),
            car.getRelaxedFrLateralForce(),
            car.getRelaxedRlLateralForce(),
            car.getRelaxedRrLateralForce(),
            car.getWheelAngularSpeedFl(),
            car.getWheelAngularSpeedFr(),
            car.getWheelAngularSpeedRl(),
            car.getWheelAngularSpeedRr()
        ), PacketDistributor.PLAYER.with(player));
    }

    public static void sendLiveryTexture(ServerPlayer player, String textureId, byte[] pngBytes) {
        CHANNEL.send(new LiveryTextureCacheMessage(textureId, pngBytes), PacketDistributor.PLAYER.with(player));
    }

    public static void syncVisibleLiveries(ServerPlayer player) {
        if (!(player.level() instanceof ServerLevel level)) {
            return;
        }
        for (Entity entity : level.getAllEntities()) {
            if (entity instanceof OpenwheelCarEntity car) {
                ServerLiveryTextures.syncToPlayer(car, player);
            }
        }
    }

    private static void syncLiveryToTrackingCars(ServerLevel level, String textureId, byte[] pngBytes) {
        String safe = CarLiveryTexture.sanitize(textureId);
        for (Entity entity : level.getAllEntities()) {
            if (entity instanceof OpenwheelCarEntity car && car.getLiveryTexture().id().equals(safe)) {
                CHANNEL.send(new LiveryTextureCacheMessage(safe, pngBytes), PacketDistributor.TRACKING_ENTITY.with(car));
            }
        }
    }

    public static void sendToServer(Object payload) {
        CHANNEL.send(payload, PacketDistributor.SERVER.noArg());
    }

        private static float sanitizePedal(float value) {
        if (!Float.isFinite(value)) {
            return 0.0f;
        }
        return Math.max(0.0f, Math.min(1.0f, value));
    }

    private static float sanitizeSteering(float value) {
        if (!Float.isFinite(value)) {
            return 0.0f;
        }
        return Math.max(-1.0f, Math.min(1.0f, value));
    }

    private static float sanitizeSteeringAngle(float value) {
        if (!Float.isFinite(value)) {
            return 0.0f;
        }
        float mechanicalLock = (float) Math.toRadians(VehiclePhysics.PROTOTYPE_MECHANICAL_STEERING_LOCK_DEGREES);
        return Math.max(-mechanicalLock, Math.min(mechanicalLock, value));
    }

    public record TuneCarMessage(int slot, int delta) {


        private static void encode(TuneCarMessage message, FriendlyByteBuf buffer) {
            buffer.writeInt(message.slot);
            buffer.writeInt(message.delta);
        }

        private static TuneCarMessage decode(FriendlyByteBuf buffer) {
            return new TuneCarMessage(buffer.readInt(), buffer.readInt());
        }

        private static void handle(TuneCarMessage message, CustomPayloadEvent.Context context) {
            context.enqueueWork(() -> {
                ServerPlayer player = context.getSender();
                if (player == null || !(player.containerMenu instanceof CarAssemblyMenu menu) || !menu.allowsSetup()) {
                    return;
                }
                if (menu.queueSetupTune(message.slot, message.delta)) {
                    menu.slotsChanged(menu.getContainer());
                }
            });
        }
    }

    public record ApplyCarSetupMessage(int power, int gearing, int frontWing, int rearWing, int antiRoll, int brakeBias) {


        private static void encode(ApplyCarSetupMessage message, FriendlyByteBuf buffer) {
            buffer.writeInt(message.power); buffer.writeInt(message.gearing);
            buffer.writeInt(message.frontWing); buffer.writeInt(message.rearWing);
            buffer.writeInt(message.antiRoll); buffer.writeInt(message.brakeBias);
        }

        private static ApplyCarSetupMessage decode(FriendlyByteBuf buffer) {
            return new ApplyCarSetupMessage(buffer.readInt(), buffer.readInt(), buffer.readInt(),
                buffer.readInt(), buffer.readInt(), buffer.readInt());
        }

        private static void handle(ApplyCarSetupMessage message, CustomPayloadEvent.Context context) {
            context.enqueueWork(() -> {
                ServerPlayer player = context.getSender();
                if (player == null || !(player.containerMenu instanceof CarAssemblyMenu menu) || !menu.allowsSetup()) return;
                PrototypeCarSetup current = PrototypeCarItem.getSetup(menu.getOutputStack());
                PrototypeCarSetup requested = new PrototypeCarSetup(1, current.grip(), current.aero(), message.gearing,
                    message.frontWing, message.rearWing, message.antiRoll, message.brakeBias);
                if (menu.queueSetup(requested)) menu.slotsChanged(menu.getContainer());
            });
        }
    }

    public record RepairCarMessage() {


        private static void encode(RepairCarMessage message, FriendlyByteBuf buffer) {
        }

        private static RepairCarMessage decode(FriendlyByteBuf buffer) {
            return new RepairCarMessage();
        }

        private static void handle(RepairCarMessage message, CustomPayloadEvent.Context context) {
            context.enqueueWork(() -> {
                ServerPlayer player = context.getSender();
                if (player == null || !(player.containerMenu instanceof CarAssemblyMenu menu) || !menu.allowsSetup()) {
                    return;
                }
                if (!player.getInventory().contains(new ItemStack(OWRItems.RUBBER.get()))) {
                    return;
                }
                if (menu.queueRepair()) {
                    player.getInventory().clearOrCountMatchingItems(item -> item.is(OWRItems.RUBBER.get()), 1, player.inventoryMenu.getCraftSlots());
                    menu.slotsChanged(menu.getContainer());
                }
            });
        }
    }

    public record StartPartReplacementMessage() {


        private static void encode(StartPartReplacementMessage message, FriendlyByteBuf buffer) {
        }

        private static StartPartReplacementMessage decode(FriendlyByteBuf buffer) {
            return new StartPartReplacementMessage();
        }

        private static void handle(StartPartReplacementMessage message, CustomPayloadEvent.Context context) {
            context.enqueueWork(() -> {
                ServerPlayer player = context.getSender();
                if (player == null || !(player.containerMenu instanceof CarPartsReplacementMenu menu)) {
                    return;
                }
                if (menu.queueReplacement()) {
                    menu.slotsChanged(menu.getContainer());
                }
            });
        }
    }

    public record CycleLiveryMessage(int delta) {


        private static void encode(CycleLiveryMessage message, FriendlyByteBuf buffer) {
            buffer.writeInt(message.delta);
        }

        private static CycleLiveryMessage decode(FriendlyByteBuf buffer) {
            return new CycleLiveryMessage(buffer.readInt());
        }

        private static void handle(CycleLiveryMessage message, CustomPayloadEvent.Context context) {
            context.enqueueWork(() -> {
                ServerPlayer player = context.getSender();
                if (player == null || !(player.containerMenu instanceof CarAssemblyMenu menu) || !menu.allowsLivery()) {
                    return;
                }
                if (menu.queueLiveryPreset(message.delta)) {
                    menu.slotsChanged(menu.getContainer());
                }
            });
        }
    }

    public record SetLiveryColorMessage(int channel, int color) {


        private static void encode(SetLiveryColorMessage message, FriendlyByteBuf buffer) {
            buffer.writeInt(message.channel);
            buffer.writeInt(message.color);
        }

        private static SetLiveryColorMessage decode(FriendlyByteBuf buffer) {
            return new SetLiveryColorMessage(buffer.readInt(), buffer.readInt());
        }

        private static void handle(SetLiveryColorMessage message, CustomPayloadEvent.Context context) {
            context.enqueueWork(() -> {
                ServerPlayer player = context.getSender();
                if (player == null || !(player.containerMenu instanceof CarAssemblyMenu menu) || !menu.allowsLivery()) {
                    return;
                }
                if (menu.queueLiveryColor(message.channel, message.color)) {
                    menu.slotsChanged(menu.getContainer());
                }
            });
        }
    }

    public record UploadLiveryTextureMessage(String textureId, byte[] pngBytes) {
        private static final int MAX_BYTES = 1_048_576;


        private static void encode(UploadLiveryTextureMessage message, FriendlyByteBuf buffer) {
            buffer.writeUtf(CarLiveryTexture.sanitize(message.textureId));
            buffer.writeByteArray(message.pngBytes);
        }

        private static UploadLiveryTextureMessage decode(FriendlyByteBuf buffer) {
            return new UploadLiveryTextureMessage(buffer.readUtf(80), buffer.readByteArray(MAX_BYTES));
        }

        private static void handle(UploadLiveryTextureMessage message, CustomPayloadEvent.Context context) {
            context.enqueueWork(() -> {
                ServerPlayer player = context.getSender();
                if (player == null || !(player.containerMenu instanceof CarAssemblyMenu menu) || !menu.allowsLivery()) {
                    return;
                }
                try {
                    ServerLiveryTextures.save(player.level().getServer(), message.textureId, message.pngBytes);
                } catch (java.io.IOException ignored) {
                }
            });
        }
    }

    public record SetLiveryTextureMessage(String textureId) {


        private static void encode(SetLiveryTextureMessage message, FriendlyByteBuf buffer) {
            buffer.writeUtf(CarLiveryTexture.sanitize(message.textureId));
        }

        private static SetLiveryTextureMessage decode(FriendlyByteBuf buffer) {
            return new SetLiveryTextureMessage(buffer.readUtf(80));
        }

        private static void handle(SetLiveryTextureMessage message, CustomPayloadEvent.Context context) {
            context.enqueueWork(() -> {
                ServerPlayer player = context.getSender();
                if (player == null || !(player.containerMenu instanceof CarAssemblyMenu menu) || !menu.allowsLivery()) {
                    return;
                }
                if (menu.queueLiveryTexture(message.textureId)) {
                    if (player.level() instanceof ServerLevel serverLevel) {
                        byte[] pngBytes = ServerLiveryTextures.read(serverLevel.getServer(), message.textureId);
                        if (pngBytes.length > 0) {
                            syncLiveryToTrackingCars(serverLevel, message.textureId, pngBytes);
                        }
                    }
                    menu.slotsChanged(menu.getContainer());
                }
            });
        }
    }

    public record ShiftMessage(int direction, boolean automatic) {


        private static void encode(ShiftMessage message, FriendlyByteBuf buffer) {
            buffer.writeInt(message.direction);
            buffer.writeBoolean(message.automatic);
        }

        private static ShiftMessage decode(FriendlyByteBuf buffer) {
            return new ShiftMessage(buffer.readInt(), buffer.readBoolean());
        }

        private static void handle(ShiftMessage message, CustomPayloadEvent.Context context) {
            context.enqueueWork(() -> {
                ServerPlayer player = context.getSender();
                if (player == null || !(player.getVehicle() instanceof OpenwheelCarEntity car)) {
                    return;
                }
                if (message.automatic && !OWRRaceControlState.get(player.level()).isAutoShiftingAllowed()) {
                    return;
                }
                if (message.direction > 0) {
                    car.shiftUp();
                } else {
                    car.shiftDown();
                }
            });
        }
    }

    public record ExitCarMessage() {


        private static void encode(ExitCarMessage message, FriendlyByteBuf buffer) {
        }

        private static ExitCarMessage decode(FriendlyByteBuf buffer) {
            return new ExitCarMessage();
        }

        private static void handle(ExitCarMessage message, CustomPayloadEvent.Context context) {
            context.enqueueWork(() -> {
                ServerPlayer player = context.getSender();
                if (player != null) {
                    player.stopRiding();
                }
            });
        }
    }

    public record DriveInputMessage(int sequence, float keyboardThrottle, float keyboardBrake, float wheelThrottle, float wheelBrake, float steeringAngleRadians,
            float lowSpeedSteeringRate, float highSpeedSteeringRate, float lowSpeedCenteringRate, float highSpeedCenteringRate,
            float lowSpeedSteeringGain, float highSpeedSteeringGain, float speedResponseCurve,
            float tractionControlStrength, float tractionControlEnvelope, float absEnvelope,
            float brakingYawAdjustment, float neutralYawAdjustment, float throttleYawAdjustment,
            float stabilityAssistStrength, boolean keyboardSteeringSource) {


        private static void encode(DriveInputMessage message, FriendlyByteBuf buffer) {
            buffer.writeVarInt(message.sequence);
            buffer.writeFloat(message.keyboardThrottle);
            buffer.writeFloat(message.keyboardBrake);
            buffer.writeFloat(message.wheelThrottle);
            buffer.writeFloat(message.wheelBrake);
            buffer.writeFloat(message.steeringAngleRadians);
            buffer.writeFloat(message.lowSpeedSteeringRate);
            buffer.writeFloat(message.highSpeedSteeringRate);
            buffer.writeFloat(message.lowSpeedCenteringRate);
            buffer.writeFloat(message.highSpeedCenteringRate);
            buffer.writeFloat(message.lowSpeedSteeringGain);
            buffer.writeFloat(message.highSpeedSteeringGain);
            buffer.writeFloat(message.speedResponseCurve);
            buffer.writeFloat(message.tractionControlStrength);
            buffer.writeFloat(message.tractionControlEnvelope);
            buffer.writeFloat(message.absEnvelope);
            buffer.writeFloat(message.brakingYawAdjustment);
            buffer.writeFloat(message.neutralYawAdjustment);
            buffer.writeFloat(message.throttleYawAdjustment);
            buffer.writeFloat(message.stabilityAssistStrength);
            buffer.writeBoolean(message.keyboardSteeringSource);
        }

        private static DriveInputMessage decode(FriendlyByteBuf buffer) {
            return new DriveInputMessage(
                buffer.readVarInt(),
                buffer.readFloat(), buffer.readFloat(),
                buffer.readFloat(), buffer.readFloat(),
                buffer.readFloat(),
                buffer.readFloat(), buffer.readFloat(), buffer.readFloat(), buffer.readFloat(),
                buffer.readFloat(), buffer.readFloat(), buffer.readFloat(),
                buffer.readFloat(), buffer.readFloat(), buffer.readFloat(),
                buffer.readFloat(), buffer.readFloat(), buffer.readFloat(), buffer.readFloat(),
                buffer.readBoolean());
        }

        private static void handle(DriveInputMessage message, CustomPayloadEvent.Context context) {
            context.enqueueWork(() -> {
                ServerPlayer player = context.getSender();
                if (player == null || !(player.getVehicle() instanceof OpenwheelCarEntity car)) {
                    return;
                }
                float keyboardThrottle = sanitizePedal(message.keyboardThrottle);
                float keyboardBrake = sanitizePedal(message.keyboardBrake);
                float throttle = keyboardThrottle;
                float brake = keyboardBrake;
                boolean wheelAllowed = OWRRaceControlState.get(player.level()).isWheelInputAllowed();
                if (wheelAllowed) {
                    float wheelThrottle = sanitizePedal(message.wheelThrottle);
                    float wheelBrake = sanitizePedal(message.wheelBrake);
                    throttle = Math.max(keyboardThrottle, wheelThrottle);
                    brake = Math.max(keyboardBrake, wheelBrake);
                }
                boolean keyboardSource = wheelAllowed ? message.keyboardSteeringSource : true;
                float steeringAngle = !wheelAllowed && !message.keyboardSteeringSource
                    ? 0.0f
                    : sanitizeSteeringAngle(message.steeringAngleRadians);
                car.applyDriveInput(message.sequence, throttle, brake, steeringAngle, keyboardSource);
                car.setKeyboardSteeringTuning(message.lowSpeedSteeringRate, message.highSpeedSteeringRate, message.lowSpeedCenteringRate, message.highSpeedCenteringRate,
                    message.lowSpeedSteeringGain, message.highSpeedSteeringGain, message.speedResponseCurve);
                car.setTractionControlStrength(message.tractionControlStrength);
                car.setAssistGripEnvelopes(message.tractionControlEnvelope, message.absEnvelope);
                car.setYawAdjustments(message.brakingYawAdjustment, message.neutralYawAdjustment, message.throttleYawAdjustment);
                car.setKeyboardStabilityAssistStrength(message.stabilityAssistStrength);
            });
        }
    }

    public record ToggleAbsMessage() {


        private static void encode(ToggleAbsMessage message, FriendlyByteBuf buffer) {
        }

        private static ToggleAbsMessage decode(FriendlyByteBuf buffer) {
            return new ToggleAbsMessage();
        }

        private static void handle(ToggleAbsMessage message, CustomPayloadEvent.Context context) {
            context.enqueueWork(() -> {
                ServerPlayer player = context.getSender();
                if (player == null || !(player.getVehicle() instanceof OpenwheelCarEntity car)) {
                    return;
                }
                car.toggleAbs();
            });
        }
    }

    public record ToggleTractionControlMessage() {


        private static void encode(ToggleTractionControlMessage message, FriendlyByteBuf buffer) {
        }

        private static ToggleTractionControlMessage decode(FriendlyByteBuf buffer) {
            return new ToggleTractionControlMessage();
        }

        private static void handle(ToggleTractionControlMessage message, CustomPayloadEvent.Context context) {
            context.enqueueWork(() -> {
                ServerPlayer player = context.getSender();
                if (player == null || !(player.getVehicle() instanceof OpenwheelCarEntity car)) {
                    return;
                }
                car.toggleTractionControl();
            });
        }
    }

    public record ToggleDrsMessage() {


        private static void encode(ToggleDrsMessage message, FriendlyByteBuf buffer) {
        }

        private static ToggleDrsMessage decode(FriendlyByteBuf buffer) {
            return new ToggleDrsMessage();
        }

        private static void handle(ToggleDrsMessage message, CustomPayloadEvent.Context context) {
            context.enqueueWork(() -> {
                ServerPlayer player = context.getSender();
                if (player == null || !(player.getVehicle() instanceof OpenwheelCarEntity car)) {
                    return;
                }
                car.toggleDrs();
            });
        }
    }

    public record CycleErsModeMessage(int direction) {


        private static void encode(CycleErsModeMessage message, FriendlyByteBuf buffer) {
            buffer.writeInt(message.direction);
        }

        private static CycleErsModeMessage decode(FriendlyByteBuf buffer) {
            return new CycleErsModeMessage(buffer.readInt());
        }

        private static void handle(CycleErsModeMessage message, CustomPayloadEvent.Context context) {
            context.enqueueWork(() -> {
                ServerPlayer player = context.getSender();
                if (player == null || !(player.getVehicle() instanceof OpenwheelCarEntity car)) {
                    return;
                }
                car.cycleErsMode(message.direction);
            });
        }
    }

    public record SetErsModeMessage(int mode) {


        private static void encode(SetErsModeMessage message, FriendlyByteBuf buffer) {
            buffer.writeInt(message.mode);
        }

        private static SetErsModeMessage decode(FriendlyByteBuf buffer) {
            return new SetErsModeMessage(buffer.readInt());
        }

        private static void handle(SetErsModeMessage message, CustomPayloadEvent.Context context) {
            context.enqueueWork(() -> {
                ServerPlayer player = context.getSender();
                if (player == null || !(player.getVehicle() instanceof OpenwheelCarEntity car)) {
                    return;
                }
                if (message.mode < OpenwheelCarEntity.ERS_MODE_HARVEST || message.mode > OpenwheelCarEntity.ERS_MODE_ATTACK) {
                    return;
                }
                car.setErsMode(message.mode);
            });
        }
    }

    public record SetErsThresholdsMessage(int balancedClipStartKmh, int balancedClipEndKmh, int harvestNegativeStartKmh, int harvestNegativeFullKmh,
            int balancedStartPowerKw, int balancedEndPowerKw, int harvestStartPowerKw, int harvestEndPowerKw, double capacityMj,
            int licoSpeedThresholdKmh, double licoSteeringThresholdDegrees, double licoLateralGThreshold, int licoHarvestPowerKw, int licoBalancedPowerKw, int licoAttackPowerKw) {


        private static void encode(SetErsThresholdsMessage message, FriendlyByteBuf buffer) {
            buffer.writeInt(message.balancedClipStartKmh);
            buffer.writeInt(message.balancedClipEndKmh);
            buffer.writeInt(message.harvestNegativeStartKmh);
            buffer.writeInt(message.harvestNegativeFullKmh);
            buffer.writeInt(message.balancedStartPowerKw);
            buffer.writeInt(message.balancedEndPowerKw);
            buffer.writeInt(message.harvestStartPowerKw);
            buffer.writeInt(message.harvestEndPowerKw);
            buffer.writeDouble(message.capacityMj);
            buffer.writeInt(message.licoSpeedThresholdKmh);
            buffer.writeDouble(message.licoSteeringThresholdDegrees);
            buffer.writeDouble(message.licoLateralGThreshold);
            buffer.writeInt(message.licoHarvestPowerKw);
            buffer.writeInt(message.licoBalancedPowerKw);
            buffer.writeInt(message.licoAttackPowerKw);
        }

        private static SetErsThresholdsMessage decode(FriendlyByteBuf buffer) {
            return new SetErsThresholdsMessage(
                buffer.readInt(),
                buffer.readInt(),
                buffer.readInt(),
                buffer.readInt(),
                buffer.readInt(),
                buffer.readInt(),
                buffer.readInt(),
                buffer.readInt(),
                buffer.readDouble(),
                buffer.readInt(),
                buffer.readDouble(),
                buffer.readDouble(),
                buffer.readInt(),
                buffer.readInt(),
                buffer.readInt()
            );
        }

        private static void handle(SetErsThresholdsMessage message, CustomPayloadEvent.Context context) {
            context.enqueueWork(() -> {
                ServerPlayer player = context.getSender();
                if (player == null || !(player.getVehicle() instanceof OpenwheelCarEntity car)) {
                    return;
                }
                OWRRaceControlState raceControl = OWRRaceControlState.get(player.level());
                car.setErsTuning(
                    message.balancedClipStartKmh,
                    message.balancedClipEndKmh,
                    message.harvestNegativeStartKmh,
                    message.harvestNegativeFullKmh,
                    Math.min(message.balancedStartPowerKw, raceControl.getMaxBalancedDeployKw()),
                    Math.min(message.balancedEndPowerKw, raceControl.getMaxBalancedDeployKw()),
                    -Math.min(Math.abs(message.harvestStartPowerKw), raceControl.getMaxHarvestNegativeKw()),
                    -Math.min(Math.abs(message.harvestEndPowerKw), raceControl.getMaxHarvestNegativeKw()),
                    Math.min(message.capacityMj, raceControl.getMaxErsCapacityMj()) * 1_000_000.0,
                    raceControl.getMaxAttackDeployKw(),
                    message.licoSpeedThresholdKmh,
                    message.licoSteeringThresholdDegrees,
                    message.licoLateralGThreshold,
                    -Math.min(Math.abs(message.licoHarvestPowerKw), raceControl.getMaxHarvestNegativeKw()),
                    -Math.min(Math.abs(message.licoBalancedPowerKw), raceControl.getMaxHarvestNegativeKw()),
                    -Math.min(Math.abs(message.licoAttackPowerKw), raceControl.getMaxHarvestNegativeKw())
                );
            });
        }
    }

    public record MountCarMessage() {


        private static void encode(MountCarMessage message, FriendlyByteBuf buffer) {
        }

        private static MountCarMessage decode(FriendlyByteBuf buffer) {
            return new MountCarMessage();
        }

        private static void handle(MountCarMessage message, CustomPayloadEvent.Context context) {
            context.enqueueWork(() -> {
                ServerPlayer player = context.getSender();
                if (player == null || player.getVehicle() != null) {
                    return;
                }

                Vec3 eye = player.getEyePosition();
                Vec3 look = player.getLookAngle();
                Vec3 reach = eye.add(look.scale(5.0));
                AABB search = player.getBoundingBox().inflate(5.0);

                OpenwheelCarEntity best = null;
                double bestDistance = Double.MAX_VALUE;
                for (Entity entity : player.level().getEntities(player, search, e -> e instanceof OpenwheelCarEntity && e.getPassengers().isEmpty())) {
                    AABB box = entity.getBoundingBox().inflate(0.35);
                    if (box.clip(eye, reach).isPresent()) {
                        double distance = entity.distanceToSqr(player);
                        if (distance < bestDistance) {
                            bestDistance = distance;
                            best = (OpenwheelCarEntity) entity;
                        }
                    }
                }

                if (best != null) {
                    player.startRiding(best);
                    best.prepareForDriver(player);
                }
            });
        }
    }

    public record TrackEditorPlaceMessage(TrackEditorOperation operation) {


        private static void encode(TrackEditorPlaceMessage message, FriendlyByteBuf buffer) {
            buffer.writeEnum(message.operation.mode());
            buffer.writeEnum(message.operation.material());
            buffer.writeVarInt(message.operation.width());
            buffer.writeEnum(message.operation.facing());
            buffer.writeEnum(message.operation.preset());
            buffer.writeEnum(message.operation.runoffMaterial());
            buffer.writeBoolean(message.operation.fullSurface());
            buffer.writeVarInt(message.operation.clearHeight());
            buffer.writeVarInt(message.operation.points().size());
            for (BlockPos point : message.operation.points()) {
                buffer.writeBlockPos(point);
            }
        }

        private static TrackEditorPlaceMessage decode(FriendlyByteBuf buffer) {
            TrackEditorMode mode = buffer.readEnum(TrackEditorMode.class);
            TrackEditorMaterial material = buffer.readEnum(TrackEditorMaterial.class);
            int width = buffer.readVarInt();
            Direction facing = buffer.readEnum(Direction.class);
            TrackEditorPreset preset = buffer.readEnum(TrackEditorPreset.class);
            TrackEditorMaterial runoffMaterial = buffer.readEnum(TrackEditorMaterial.class);
            boolean fullSurface = buffer.readBoolean();
            int clearHeight = buffer.readVarInt();
            int declaredSize = buffer.readVarInt();
            int size = Math.min(declaredSize, TrackEditorOperation.MAX_POINTS);
            java.util.List<BlockPos> points = new java.util.ArrayList<>(size);
            for (int i = 0; i < declaredSize; i++) {
                BlockPos point = buffer.readBlockPos();
                if (i < size) {
                    points.add(point);
                }
            }
            return new TrackEditorPlaceMessage(new TrackEditorOperation(mode, material, width, points, facing, preset, runoffMaterial, fullSurface, clearHeight));
        }

        private static void handle(TrackEditorPlaceMessage message, CustomPayloadEvent.Context context) {
            context.enqueueWork(() -> {
                ServerPlayer player = context.getSender();
                if (player != null) {
                    TrackEditorPlacementService.PlacementResult result = TrackEditorPlacementService.place(player, message.operation());
                    if (result != TrackEditorPlacementService.PlacementResult.PLACED) {
                        player.displayClientMessage(Component.translatable("message.openwheelracing.track_editor.place_failed." + result.name().toLowerCase(java.util.Locale.ROOT)), true);
                    }
                }
            });
        }
    }

    public record TrackEditorUndoMessage() {


        private static void encode(TrackEditorUndoMessage message, FriendlyByteBuf buffer) {
        }

        private static TrackEditorUndoMessage decode(FriendlyByteBuf buffer) {
            return new TrackEditorUndoMessage();
        }

        private static void handle(TrackEditorUndoMessage message, CustomPayloadEvent.Context context) {
            context.enqueueWork(() -> {
                ServerPlayer player = context.getSender();
                if (player != null) {
                    TrackEditorUndoStore.undo(player);
                }
            });
        }
    }

    public static void sendLiveRaceTiming(ServerPlayer player, LiveRaceTimingSnapshot snapshot) {
        CHANNEL.send(new LiveRaceTimingSnapshotMessage(snapshot), PacketDistributor.PLAYER.with(player));
    }

    public static void broadcastLiveRaceTiming(ServerLevel level, LiveRaceTimingSnapshot snapshot) {
        LiveRaceTimingSnapshotMessage message = new LiveRaceTimingSnapshotMessage(snapshot);
        for (ServerPlayer player : level.getServer().getPlayerList().getPlayers()) {
            if (player.level().dimension().equals(level.dimension())) {
                CHANNEL.send(message, PacketDistributor.PLAYER.with(player));
            }
        }
    }

    public static void sendRaceDirectorSnapshot(ServerPlayer player, RaceDirectorSnapshot snapshot) {
        CHANNEL.send(new RaceDirectorSnapshotMessage(snapshot), PacketDistributor.PLAYER.with(player));
    }

    public static void sendTrackMoistureSnapshot(ServerPlayer player, com.openwheelracing.content.race.TrackMoistureSnapshot snapshot) {
        CHANNEL.send(new TrackMoistureSnapshotMessage(snapshot), PacketDistributor.PLAYER.with(player));
    }

    public static void sendCommandFeedback(ServerPlayer player, String message) {
        CHANNEL.send(new CommandFeedbackMessage(message), PacketDistributor.PLAYER.with(player));
    }

    public static void sendVehiclePhysicsPreset(ServerPlayer player) {
        VehiclePhysicsPreset preset = VehiclePhysicsPresetState.get(
            ((ServerLevel) player.level()).getServer()).preset();
        CHANNEL.send(new VehiclePhysicsPresetMessage(preset.ordinal()), PacketDistributor.PLAYER.with(player));
    }

    public static void broadcastVehiclePhysicsPreset(net.minecraft.server.MinecraftServer server) {
        VehiclePhysicsPresetMessage message = new VehiclePhysicsPresetMessage(
            VehiclePhysicsPresetState.get(server).preset().ordinal());
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            CHANNEL.send(message, PacketDistributor.PLAYER.with(player));
        }
    }

    public static void sendStewardLineOverlay(ServerPlayer player, boolean visible, TrackDefinition track, int revision) {
        UUID trackId = track == null ? new UUID(0L, 0L) : track.trackId();
        String trackName = track == null ? "" : track.name();
        List<TrackDefinition.StewardLine> lines = track == null ? List.of() : track.stewardLines();
        CHANNEL.send(new StewardLineOverlayMessage(visible, trackId, trackName, revision, lines), PacketDistributor.PLAYER.with(player));
    }

    public static void sendAiRacingLineOverlay(ServerPlayer player, boolean visible, String dimensionId, UUID trackId, String source,
                                                List<AiRacingLineStrip> strips) {
        CHANNEL.send(new AiRacingLineOverlayMessage(visible, dimensionId, trackId, source, strips), PacketDistributor.PLAYER.with(player));
    }

    public static void sendSurveyRouteOverlay(ServerPlayer player, boolean visible, String dimensionId, UUID trackId, String trackName, boolean recording, SurveyRoute route) {
        CHANNEL.send(new SurveyRouteOverlayMessage(visible, dimensionId, trackId, trackName, recording,
            route == null ? List.of() : route.rawSamples(), route == null ? List.of() : route.nodes(), route == null ? 0.0 : route.length(), route == null ? 2.0 : route.spacing()), PacketDistributor.PLAYER.with(player));
    }

    public static void sendMonitorTelemetry(ServerPlayer viewer, int carEntityId, UUID driverId, LapProfileCollector.Latest latest, float carSpeedKmh, double routeLength, boolean profileUpdate, OWRLapProfiles.BestLapProfile best) {
        int[] bestSpeeds = best == null ? new int[0] : best.speedCmps();
        CHANNEL.send(new MonitorTelemetryMessage(carEntityId, driverId, latest.active(), latest.status().ordinal(), latest.elapsedMillis(),
            (float) latest.routeDistance(), carSpeedKmh, (float) routeLength, best == null ? 0.0f : (float) best.spacing(), profileUpdate, bestSpeeds), PacketDistributor.PLAYER.with(viewer));
    }

    public static void sendLiveLapDelta(ServerPlayer player, int carEntityId, LapProfileCollector.Latest latest, OWRLapProfiles.BestLapProfile best,
            int referenceMillis, int deltaMillis, long serverGameTime) {
        CHANNEL.send(new LiveLapDeltaHudMessage(carEntityId, latest.active(), best != null, latest.status().ordinal(), latest.elapsedMillis(),
            (float) latest.routeDistance(), best == null ? 0 : best.lapMillis(), referenceMillis, deltaMillis, serverGameTime), PacketDistributor.PLAYER.with(player));
    }

    public static void sendTimingDeltaReset(ServerPlayer player, int segmentCount) {
        CHANNEL.send(new TimingDeltaHudMessage(true, segmentCount, List.of(), "", -1, 0, 0), PacketDistributor.PLAYER.with(player));
    }

    public static void sendTimingDeltaUpdate(ServerPlayer player, int segmentCount, List<Integer> statuses, String label, int segmentIndex, int cumulativeDeltaMillis, int miniDeltaMillis) {
        CHANNEL.send(new TimingDeltaHudMessage(false, segmentCount, statuses, label, segmentIndex, cumulativeDeltaMillis, miniDeltaMillis), PacketDistributor.PLAYER.with(player));
    }

    public static void sendRaceFlag(ServerPlayer player, ServerLevel level, boolean announce) {
        CHANNEL.send(new RaceFlagUpdateMessage(OWRRaceControlState.get(level).getGlobalFlag().ordinal(), announce), PacketDistributor.PLAYER.with(player));
    }

    public static void broadcastRaceFlag(ServerLevel level, RaceFlagMode flag, boolean announce) {
        RaceFlagUpdateMessage message = new RaceFlagUpdateMessage(flag.ordinal(), announce);
        for (ServerPlayer player : level.getServer().getPlayerList().getPlayers()) {
            if (player.level().dimension().equals(level.dimension())) {
                CHANNEL.send(message, PacketDistributor.PLAYER.with(player));
            }
        }
    }

    public static void sendRankingBoard(ServerPlayer player, ServerLevel level) {
        OWRLapRecords records = OWRLapRecords.get(level);
        LapTimingScope scope = LapTimingPreferences.get(player.getUUID());
        CHANNEL.send(rankingBoard(records, scope, usesGpLapTimeLeaderboard(level)), PacketDistributor.PLAYER.with(player));
    }

    public static void broadcastRankingBoard(net.minecraft.server.MinecraftServer server, net.minecraft.server.level.ServerLevel level) {
        OWRLapRecords records = OWRLapRecords.get(level);
        for (net.minecraft.server.level.ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (p.level().dimension().equals(level.dimension())) {
                CHANNEL.send(rankingBoard(records, LapTimingPreferences.get(p.getUUID()), usesGpLapTimeLeaderboard(level)), PacketDistributor.PLAYER.with(p));
            }
        }
    }

    private static RankingBoardMessage rankingBoard(OWRLapRecords records, LapTimingScope scope, boolean forceSession) {
        return !forceSession && scope == LapTimingScope.ALL_TIME
            ? new RankingBoardMessage("ALL TIME", records.getPlayerBestLapsSorted())
            : new RankingBoardMessage("(" + records.getActiveSessionId() + ")", records.getActiveSessionBestLapsSorted());
    }

    private static boolean usesGpLapTimeLeaderboard(ServerLevel level) {
        return LiveRaceTimingService.latestSnapshot(level)
            .filter(snapshot -> !snapshot.weekendName().isBlank())
            .map(snapshot -> snapshot.sessionType().equals("PRACTICE") || snapshot.sessionType().equals("QUALIFYING"))
            .orElse(false);
    }

    public record LiveRaceTimingSnapshotMessage(LiveRaceTimingSnapshot snapshot) {
        private static final int MAX_ROWS = 32;
        private static final int MAX_CHANGES = 32;


        private static void encode(LiveRaceTimingSnapshotMessage message, FriendlyByteBuf buffer) {
            LiveRaceTimingSnapshot snapshot = message.snapshot();
            buffer.writeBoolean(snapshot.active());
            buffer.writeUtf(snapshot.suspensionReason(), 80);
            buffer.writeLong(snapshot.sessionId());
            buffer.writeUtf(snapshot.sessionName(), 80);
            buffer.writeUtf(snapshot.weekendName(), 80);
            buffer.writeUtf(snapshot.sessionType(), 24);
            buffer.writeUUID(snapshot.trackId());
            buffer.writeUUID(snapshot.routeId());
            buffer.writeLong(snapshot.revision());
            buffer.writeLong(snapshot.serverTick());
            buffer.writeDouble(snapshot.routeLengthMeters());
            buffer.writeVarInt(snapshot.lapLimit());
            buffer.writeLong(snapshot.remainingRaceTicks());
            List<RaceTimingRow> rows = snapshot.rows().stream().limit(MAX_ROWS).toList();
            buffer.writeVarInt(rows.size());
            for (RaceTimingRow row : rows) {
                encodeTimingRow(buffer, row);
            }
            List<RacePositionChange> changes = snapshot.recentPositionChanges().stream().skip(Math.max(0, snapshot.recentPositionChanges().size() - MAX_CHANGES)).toList();
            buffer.writeVarInt(changes.size());
            for (RacePositionChange change : changes) {
                encodePositionChange(buffer, change);
            }
        }

        private static LiveRaceTimingSnapshotMessage decode(FriendlyByteBuf buffer) {
            boolean active = buffer.readBoolean();
            String reason = buffer.readUtf(80);
            long sessionId = buffer.readLong();
            String sessionName = buffer.readUtf(80);
            String weekendName = buffer.readUtf(80);
            String sessionType = buffer.readUtf(24);
            UUID trackId = buffer.readUUID();
            UUID routeId = buffer.readUUID();
            long revision = buffer.readLong();
            long serverTick = buffer.readLong();
            double routeLength = buffer.readDouble();
            int lapLimit = buffer.readVarInt();
            long remainingRaceTicks = buffer.readLong();
            int rowCount = boundedCount(buffer.readVarInt(), MAX_ROWS, "live timing rows");
            List<RaceTimingRow> rows = new java.util.ArrayList<>(rowCount);
            for (int index = 0; index < rowCount; index++) {
                rows.add(decodeTimingRow(buffer));
            }
            int changeCount = boundedCount(buffer.readVarInt(), MAX_CHANGES, "live timing position changes");
            List<RacePositionChange> changes = new java.util.ArrayList<>(changeCount);
            for (int index = 0; index < changeCount; index++) {
                changes.add(decodePositionChange(buffer));
            }
            return new LiveRaceTimingSnapshotMessage(new LiveRaceTimingSnapshot(active, reason, sessionId, sessionName,
                weekendName, sessionType, trackId, routeId,
                revision, serverTick, routeLength, rows, changes, lapLimit, remainingRaceTicks));
        }

        private static void handle(LiveRaceTimingSnapshotMessage message, CustomPayloadEvent.Context context) {
            context.enqueueWork(() -> applyLiveRaceTimingSnapshot(message.snapshot()));
        }
    }

    private static int boundedCount(int count, int maximum, String label) {
        if (count < 0 || count > maximum) {
            throw new IllegalArgumentException(label + " count " + count + " exceeds " + maximum);
        }
        return count;
    }

    private static void encodeTimingRow(FriendlyByteBuf buffer, RaceTimingRow row) {
        buffer.writeVarInt(row.position());
        encodeParticipantKey(buffer, row.participant());
        buffer.writeUtf(row.displayName(), 40);
        buffer.writeVarInt(row.entityId());
        buffer.writeVarInt(row.completedLaps());
        buffer.writeDouble(row.routeDistanceMeters());
        buffer.writeDouble(row.absoluteProgressMeters());
        buffer.writeByte(row.confidence().ordinal());
        encodeGap(buffer, row.gapToLeader());
        encodeGap(buffer, row.intervalAhead());
        buffer.writeInt(row.positionChange());
    }

    private static RaceTimingRow decodeTimingRow(FriendlyByteBuf buffer) {
        int position = buffer.readVarInt();
        RaceParticipantKey participant = decodeParticipantKey(buffer);
        String name = buffer.readUtf(40);
        int entityId = buffer.readVarInt();
        int laps = buffer.readVarInt();
        double routeDistance = buffer.readDouble();
        double absoluteProgress = buffer.readDouble();
        RaceProgressConfidence confidence = enumValue(RaceProgressConfidence.values(), buffer.readUnsignedByte(), RaceProgressConfidence.STALE);
        RaceGap gap = decodeGap(buffer);
        RaceGap interval = decodeGap(buffer);
        return new RaceTimingRow(position, participant, name, entityId, laps, routeDistance, absoluteProgress, confidence, gap, interval, buffer.readInt());
    }

    private static void encodePositionChange(FriendlyByteBuf buffer, RacePositionChange change) {
        encodeParticipantKey(buffer, change.participant());
        buffer.writeUtf(change.displayName(), 40);
        buffer.writeVarInt(change.oldPosition());
        buffer.writeVarInt(change.newPosition());
        buffer.writeVarInt(change.completedLaps());
        buffer.writeDouble(change.routeDistanceMeters());
        buffer.writeLong(change.serverTick());
    }

    private static RacePositionChange decodePositionChange(FriendlyByteBuf buffer) {
        return new RacePositionChange(decodeParticipantKey(buffer), buffer.readUtf(40), buffer.readVarInt(), buffer.readVarInt(),
            buffer.readVarInt(), buffer.readDouble(), buffer.readLong());
    }

    private static void encodeParticipantKey(FriendlyByteBuf buffer, RaceParticipantKey key) {
        buffer.writeUUID(key.id());
        buffer.writeByte(key.kind().ordinal());
    }

    private static RaceParticipantKey decodeParticipantKey(FriendlyByteBuf buffer) {
        UUID id = buffer.readUUID();
        RaceParticipantKind kind = enumValue(RaceParticipantKind.values(), buffer.readUnsignedByte(), RaceParticipantKind.PLAYER);
        return new RaceParticipantKey(id, kind);
    }

    private static void encodeGap(FriendlyByteBuf buffer, RaceGap gap) {
        buffer.writeByte(gap.type().ordinal());
        buffer.writeLong(gap.millis());
        buffer.writeVarInt(gap.laps());
    }

    private static RaceGap decodeGap(FriendlyByteBuf buffer) {
        RaceGap.Type type = enumValue(RaceGap.Type.values(), buffer.readUnsignedByte(), RaceGap.Type.UNAVAILABLE);
        return new RaceGap(type, buffer.readLong(), buffer.readVarInt());
    }

    private static <T> T enumValue(T[] values, int ordinal, T fallback) {
        return ordinal >= 0 && ordinal < values.length ? values[ordinal] : fallback;
    }

    public record RaceDirectorSnapshotMessage(RaceDirectorSnapshot snapshot) {


        private static void encode(RaceDirectorSnapshotMessage message, FriendlyByteBuf buffer) {
            RaceDirectorSnapshot snapshot = message.snapshot;
            buffer.writeBoolean(snapshot.checkpointCheckEnabled());
            buffer.writeBoolean(snapshot.offTrackCheckEnabled());
            buffer.writeBoolean(snapshot.autoShiftingAllowed());
            buffer.writeInt(snapshot.minimumValidLapTicks());
            buffer.writeVarInt(snapshot.raceLapLimit());
            buffer.writeInt(snapshot.page());
            buffer.writeInt(snapshot.maxPage());
            buffer.writeInt(snapshot.raceControlRevision());
            buffer.writeInt(snapshot.lapRecordsRevision());
            buffer.writeInt(snapshot.maxErsCapacityMj());
            buffer.writeInt(snapshot.maxBalancedDeployKw());
            buffer.writeInt(snapshot.maxAttackDeployKw());
            buffer.writeInt(snapshot.maxHarvestNegativeKw());
            buffer.writeInt(snapshot.globalFlag().ordinal());
            buffer.writeDouble(snapshot.carDamageModifier());
            buffer.writeDouble(snapshot.tyreWearModifier());
            buffer.writeLong(snapshot.activeSessionId());
            buffer.writeUtf(snapshot.activeSessionName());
            buffer.writeBoolean(snapshot.archiveMode());
            buffer.writeInt(snapshot.leftTeamCarId());
            buffer.writeInt(snapshot.rightTeamCarId());
            TrackMapSnapshot.encode(snapshot.trackMap(), buffer);
            buffer.writeBoolean(snapshot.trackMapScanRunning());
            buffer.writeVarInt(snapshot.trackMapScanScannedChunks());
            buffer.writeVarInt(snapshot.trackMapScanTotalChunks());
            buffer.writeVarInt(snapshot.trackMapScanDetectedCells());
            com.openwheelracing.content.race.TrackMoistureSnapshot.encode(snapshot.trackMoisture(), buffer);
            buffer.writeVarInt(snapshot.laps().size());
            for (RaceDirectorLapRow row : snapshot.laps()) {
                RaceDirectorLapRow.encode(row, buffer);
            }
            buffer.writeVarInt(snapshot.teamCars().size());
            for (TeamCarRow row : snapshot.teamCars()) {
                TeamCarRow.encode(row, buffer);
            }
            buffer.writeVarInt(snapshot.pendingPitPenalties().size());
            for (com.openwheelracing.content.race.PitLanePenaltyRow row : snapshot.pendingPitPenalties()) {
                com.openwheelracing.content.race.PitLanePenaltyRow.encode(row, buffer);
            }
            snapshot.grandPrixWeekend().encode(buffer);
            buffer.writeVarInt(snapshot.bopSelectedLaps());
            buffer.writeVarInt(snapshot.bopDrivers().size());
            for (BoPDriverRow row : snapshot.bopDrivers()) {
                buffer.writeUUID(row.driverId());
                buffer.writeUtf(row.driverName(), 40);
                buffer.writeInt(row.averageLapMillis());
                buffer.writeVarInt(row.sampleLaps());
                buffer.writeInt(row.bestLapMillis());
                buffer.writeUtf(row.bestLapSessionName(), 40);
                buffer.writeByte(row.weightPercent());
                buffer.writeByte(row.powerPercent());
                buffer.writeInt(row.estimatedLapMillis());
            }
        }

        private static RaceDirectorSnapshotMessage decode(FriendlyByteBuf buffer) {
            boolean checkpointCheckEnabled = buffer.readBoolean();
            boolean offTrackCheckEnabled = buffer.readBoolean();
            boolean autoShiftingAllowed = buffer.readBoolean();
            int minimumValidLapTicks = buffer.readInt();
            int raceLapLimit = buffer.readVarInt();
            int page = buffer.readInt();
            int maxPage = buffer.readInt();
            int raceControlRevision = buffer.readInt();
            int lapRecordsRevision = buffer.readInt();
            int maxErsCapacityMj = buffer.readInt();
            int maxBalancedDeployKw = buffer.readInt();
            int maxAttackDeployKw = buffer.readInt();
            int maxHarvestNegativeKw = buffer.readInt();
            RaceFlagMode globalFlag = RaceFlagMode.fromOrdinal(buffer.readInt());
            double carDamageModifier = buffer.readDouble();
            double tyreWearModifier = buffer.readDouble();
            long activeSessionId = buffer.readLong();
            String activeSessionName = buffer.readUtf();
            boolean archiveMode = buffer.readBoolean();
            int leftTeamCarId = buffer.readInt();
            int rightTeamCarId = buffer.readInt();
            TrackMapSnapshot trackMap = TrackMapSnapshot.decode(buffer);
            boolean trackMapScanRunning = buffer.readBoolean();
            int trackMapScanScannedChunks = buffer.readVarInt();
            int trackMapScanTotalChunks = buffer.readVarInt();
            int trackMapScanDetectedCells = buffer.readVarInt();
            com.openwheelracing.content.race.TrackMoistureSnapshot trackMoisture = com.openwheelracing.content.race.TrackMoistureSnapshot.decode(buffer);
            int lapCount = buffer.readVarInt();
            java.util.ArrayList<RaceDirectorLapRow> laps = new java.util.ArrayList<>(lapCount);
            for (int index = 0; index < lapCount; index++) {
                laps.add(RaceDirectorLapRow.decode(buffer));
            }
            int carCount = buffer.readVarInt();
            java.util.ArrayList<TeamCarRow> teamCars = new java.util.ArrayList<>(carCount);
            for (int index = 0; index < carCount; index++) {
                teamCars.add(TeamCarRow.decode(buffer));
            }
            int penaltyCount = buffer.readVarInt();
            java.util.ArrayList<com.openwheelracing.content.race.PitLanePenaltyRow> penalties = new java.util.ArrayList<>(penaltyCount);
            for (int index = 0; index < penaltyCount; index++) {
                penalties.add(com.openwheelracing.content.race.PitLanePenaltyRow.decode(buffer));
            }
            com.openwheelracing.content.race.weekend.GrandPrixWeekendInfo grandPrixWeekend =
                com.openwheelracing.content.race.weekend.GrandPrixWeekendInfo.decode(buffer);
            int bopSelectedLaps = buffer.readVarInt();
            int bopCount = buffer.readVarInt();
            java.util.ArrayList<BoPDriverRow> bopDrivers = new java.util.ArrayList<>(bopCount);
            for (int index = 0; index < bopCount; index++) {
                bopDrivers.add(new BoPDriverRow(buffer.readUUID(), buffer.readUtf(40), buffer.readInt(), buffer.readVarInt(),
                    buffer.readInt(), buffer.readUtf(40), buffer.readByte(), buffer.readByte(), buffer.readInt()));
            }
            return new RaceDirectorSnapshotMessage(new RaceDirectorSnapshot(checkpointCheckEnabled, offTrackCheckEnabled, autoShiftingAllowed,
                minimumValidLapTicks, raceLapLimit, page, maxPage, raceControlRevision, lapRecordsRevision, maxErsCapacityMj,
                maxBalancedDeployKw, maxAttackDeployKw, maxHarvestNegativeKw, globalFlag, carDamageModifier, tyreWearModifier,
                activeSessionId, activeSessionName, archiveMode, leftTeamCarId, rightTeamCarId, trackMap, trackMapScanRunning,
                trackMapScanScannedChunks, trackMapScanTotalChunks, trackMapScanDetectedCells, trackMoisture, laps, teamCars,
                penalties, grandPrixWeekend, bopSelectedLaps, bopDrivers));
        }

        private static void handle(RaceDirectorSnapshotMessage message, CustomPayloadEvent.Context context) {
            context.enqueueWork(() -> applyRaceDirectorSnapshot(message.snapshot));
        }
    }

    public record TrackMoistureSnapshotMessage(com.openwheelracing.content.race.TrackMoistureSnapshot snapshot) {


        private static void encode(TrackMoistureSnapshotMessage message, FriendlyByteBuf buffer) {
            com.openwheelracing.content.race.TrackMoistureSnapshot.encode(message.snapshot, buffer);
        }

        private static TrackMoistureSnapshotMessage decode(FriendlyByteBuf buffer) {
            return new TrackMoistureSnapshotMessage(com.openwheelracing.content.race.TrackMoistureSnapshot.decode(buffer));
        }

        private static void handle(TrackMoistureSnapshotMessage message, CustomPayloadEvent.Context context) {
            context.enqueueWork(() -> applyTrackMoistureSnapshot(message.snapshot));
        }
    }

    public record LiveryTextureCacheMessage(String textureId, byte[] pngBytes) {
        private static final int MAX_BYTES = 1_048_576;


        private static void encode(LiveryTextureCacheMessage message, FriendlyByteBuf buffer) {
            buffer.writeUtf(CarLiveryTexture.sanitize(message.textureId));
            buffer.writeByteArray(message.pngBytes);
        }

        private static LiveryTextureCacheMessage decode(FriendlyByteBuf buffer) {
            return new LiveryTextureCacheMessage(buffer.readUtf(80), buffer.readByteArray(MAX_BYTES));
        }

        private static void handle(LiveryTextureCacheMessage message, CustomPayloadEvent.Context context) {
            context.enqueueWork(() -> applyLiveryTextureCache(message));
        }
    }

    public record RaceFlagUpdateMessage(int flag, boolean announce) {


        private static void encode(RaceFlagUpdateMessage message, FriendlyByteBuf buffer) {
            buffer.writeInt(message.flag);
            buffer.writeBoolean(message.announce);
        }

        private static RaceFlagUpdateMessage decode(FriendlyByteBuf buffer) {
            return new RaceFlagUpdateMessage(buffer.readInt(), buffer.readBoolean());
        }

        private static void handle(RaceFlagUpdateMessage message, CustomPayloadEvent.Context context) {
            context.enqueueWork(() -> applyRaceFlagUpdate(message));
        }
    }

    public record DriveInputAckMessage(int entityId, int ackedInputSequence, double x, double y, double z,
            double deltaX, double deltaY, double deltaZ, float yaw, double yawRate, double steeringAngle,
            double relaxedFlLatForce, double relaxedFrLatForce, double relaxedRlLatForce, double relaxedRrLatForce,
            double wheelAngularSpeedFl, double wheelAngularSpeedFr, double wheelAngularSpeedRl, double wheelAngularSpeedRr) {


        private static void encode(DriveInputAckMessage message, FriendlyByteBuf buffer) {
            buffer.writeVarInt(message.entityId);
            buffer.writeVarInt(message.ackedInputSequence);
            buffer.writeDouble(message.x);
            buffer.writeDouble(message.y);
            buffer.writeDouble(message.z);
            buffer.writeDouble(message.deltaX);
            buffer.writeDouble(message.deltaY);
            buffer.writeDouble(message.deltaZ);
            buffer.writeFloat(message.yaw);
            buffer.writeDouble(message.yawRate);
            buffer.writeDouble(message.steeringAngle);
            buffer.writeDouble(message.relaxedFlLatForce);
            buffer.writeDouble(message.relaxedFrLatForce);
            buffer.writeDouble(message.relaxedRlLatForce);
            buffer.writeDouble(message.relaxedRrLatForce);
            buffer.writeDouble(message.wheelAngularSpeedFl);
            buffer.writeDouble(message.wheelAngularSpeedFr);
            buffer.writeDouble(message.wheelAngularSpeedRl);
            buffer.writeDouble(message.wheelAngularSpeedRr);
        }

        private static DriveInputAckMessage decode(FriendlyByteBuf buffer) {
            return new DriveInputAckMessage(buffer.readVarInt(), buffer.readVarInt(), buffer.readDouble(), buffer.readDouble(), buffer.readDouble(), buffer.readDouble(), buffer.readDouble(), buffer.readDouble(), buffer.readFloat(), buffer.readDouble(), buffer.readDouble(), buffer.readDouble(), buffer.readDouble(), buffer.readDouble(), buffer.readDouble(), buffer.readDouble(), buffer.readDouble(), buffer.readDouble(), buffer.readDouble());
        }

        private static void handle(DriveInputAckMessage message, CustomPayloadEvent.Context context) {
            context.enqueueWork(() -> applyDriveInputAck(message));
        }
    }

    public record RaceDirectorToggleRuleMessage(int rule) {


        public static final int CHECKPOINTS = 0;
        public static final int OFF_TRACK = 1;
        public static final int AUTO_SHIFTING = 2;

        private static void encode(RaceDirectorToggleRuleMessage message, FriendlyByteBuf buffer) {
            buffer.writeInt(message.rule);
        }

        private static RaceDirectorToggleRuleMessage decode(FriendlyByteBuf buffer) {
            return new RaceDirectorToggleRuleMessage(buffer.readInt());
        }

        private static void handle(RaceDirectorToggleRuleMessage message, CustomPayloadEvent.Context context) {
            context.enqueueWork(() -> {
                ServerPlayer player = context.getSender();
                if (player == null || !(player.containerMenu instanceof RaceDirectorMenu menu) || !menu.allowsRaceControl()) {
                    return;
                }
                OWRRaceControlState state = OWRRaceControlState.get(player.level());
                if (message.rule == CHECKPOINTS) {
                    state.toggleCheckpointCheck();
                } else if (message.rule == OFF_TRACK) {
                    state.toggleOffTrackCheck();
                } else if (message.rule == AUTO_SHIFTING) {
                    state.toggleAutoShiftingAllowed();
                }
            });
        }
    }

    public record RaceDirectorSetMinLapTicksMessage(int ticks) {


        private static void encode(RaceDirectorSetMinLapTicksMessage message, FriendlyByteBuf buffer) {
            buffer.writeInt(message.ticks);
        }

        private static RaceDirectorSetMinLapTicksMessage decode(FriendlyByteBuf buffer) {
            return new RaceDirectorSetMinLapTicksMessage(buffer.readInt());
        }

        private static void handle(RaceDirectorSetMinLapTicksMessage message, CustomPayloadEvent.Context context) {
            context.enqueueWork(() -> {
                ServerPlayer player = context.getSender();
                if (player == null || !(player.containerMenu instanceof RaceDirectorMenu menu) || !menu.allowsRaceControl()) {
                    return;
                }
                OWRRaceControlState.get(player.level()).setMinimumValidLapTicks(message.ticks);
            });
        }
    }

    public record SetLapTimingScopeMessage(int scope) {


        private static void encode(SetLapTimingScopeMessage message, FriendlyByteBuf buffer) {
            buffer.writeByte(message.scope);
        }

        private static SetLapTimingScopeMessage decode(FriendlyByteBuf buffer) {
            return new SetLapTimingScopeMessage(buffer.readUnsignedByte());
        }

        private static void handle(SetLapTimingScopeMessage message, CustomPayloadEvent.Context context) {
            context.enqueueWork(() -> {
                if (!(context.getSender() instanceof ServerPlayer player) || !(player.level() instanceof ServerLevel level)) {
                    return;
                }
                LapTimingPreferences.set(player.getUUID(), LapTimingScope.fromOrdinal(message.scope));
                sendRankingBoard(player, level);
                if (player.getVehicle() instanceof OpenwheelCarEntity car) {
                    car.syncPlayerBestLap(player);
                }
            });
        }
    }

    public record RaceDirectorSetRaceLapLimitMessage(int laps) {


        private static void encode(RaceDirectorSetRaceLapLimitMessage message, FriendlyByteBuf buffer) {
            buffer.writeVarInt(message.laps);
        }

        private static RaceDirectorSetRaceLapLimitMessage decode(FriendlyByteBuf buffer) {
            return new RaceDirectorSetRaceLapLimitMessage(buffer.readVarInt());
        }

        private static void handle(RaceDirectorSetRaceLapLimitMessage message, CustomPayloadEvent.Context context) {
            context.enqueueWork(() -> {
                ServerPlayer player = context.getSender();
                if (player == null || !(player.containerMenu instanceof RaceDirectorMenu menu) || !menu.allowsRaceControl()) {
                    return;
                }
                OWRRaceControlState.get(player.level()).setRaceLapLimit(message.laps);
                sendRaceDirectorSnapshot(player, menu.createSnapshot(player.level()));
            });
        }
    }

    public record RaceDirectorSetErsLimitMessage(int limit, int delta) {


        public static final int CAPACITY = 0;
        public static final int BALANCED_DEPLOY = 1;
        public static final int ATTACK_DEPLOY = 2;
        public static final int HARVEST_NEGATIVE = 3;

        private static void encode(RaceDirectorSetErsLimitMessage message, FriendlyByteBuf buffer) {
            buffer.writeInt(message.limit);
            buffer.writeInt(message.delta);
        }

        private static RaceDirectorSetErsLimitMessage decode(FriendlyByteBuf buffer) {
            return new RaceDirectorSetErsLimitMessage(buffer.readInt(), buffer.readInt());
        }

        private static void handle(RaceDirectorSetErsLimitMessage message, CustomPayloadEvent.Context context) {
            context.enqueueWork(() -> {
                ServerPlayer player = context.getSender();
                if (player == null || !(player.containerMenu instanceof RaceDirectorMenu menu) || !menu.allowsRaceControl()) {
                    return;
                }
                OWRRaceControlState state = OWRRaceControlState.get(player.level());
                switch (message.limit) {
                    case CAPACITY -> state.setMaxErsCapacityMj(state.getMaxErsCapacityMj() + message.delta);
                    case BALANCED_DEPLOY -> state.setMaxBalancedDeployKw(state.getMaxBalancedDeployKw() + message.delta);
                    case ATTACK_DEPLOY -> state.setMaxAttackDeployKw(state.getMaxAttackDeployKw() + message.delta);
                    case HARVEST_NEGATIVE -> state.setMaxHarvestNegativeKw(state.getMaxHarvestNegativeKw() + message.delta);
                    default -> {
                    }
                }
                if (player.getVehicle() instanceof OpenwheelCarEntity car) {
                    car.applyErsLimits(state.getMaxErsCapacityMj(), state.getMaxBalancedDeployKw(), state.getMaxAttackDeployKw(), state.getMaxHarvestNegativeKw());
                }
                sendRaceDirectorSnapshot(player, menu.createSnapshot(player.level()));
            });
        }
    }

    public record RaceDirectorSetGlobalFlagMessage(int flag) {


        private static void encode(RaceDirectorSetGlobalFlagMessage message, FriendlyByteBuf buffer) {
            buffer.writeInt(message.flag);
        }

        private static RaceDirectorSetGlobalFlagMessage decode(FriendlyByteBuf buffer) {
            return new RaceDirectorSetGlobalFlagMessage(buffer.readInt());
        }

        private static void handle(RaceDirectorSetGlobalFlagMessage message, CustomPayloadEvent.Context context) {
            context.enqueueWork(() -> {
                ServerPlayer player = context.getSender();
                if (player == null || !(player.containerMenu instanceof RaceDirectorMenu menu) || !menu.allowsRaceControl()) {
                    return;
                }
                OWRRaceControlState state = OWRRaceControlState.get(player.level());
                RaceFlagMode requested = RaceFlagMode.fromOrdinal(message.flag);
                RaceFlagMode next = state.getGlobalFlag() == requested ? RaceFlagMode.GREEN : requested;
                state.clearSectorSignals();
                state.setGlobalFlag(next);
                if (player.level() instanceof ServerLevel serverLevel) {
                    broadcastRaceFlag(serverLevel, next, true);
                }
                sendRaceDirectorSnapshot(player, menu.createSnapshot(player.level()));
            });
        }
    }

    public record RaceDirectorSignalControlMessage(int action, int first, int second) {
        public static final int START_PHASE = 0, AUTO_FLAGGING = 1, SECTOR_FLAG = 2, PIT_SIGNAL = 3, DRIVER_FLAG = 4;
        private static void encode(RaceDirectorSignalControlMessage message, FriendlyByteBuf buffer) { buffer.writeVarInt(message.action); buffer.writeVarInt(message.first); buffer.writeVarInt(message.second); }
        private static RaceDirectorSignalControlMessage decode(FriendlyByteBuf buffer) { return new RaceDirectorSignalControlMessage(buffer.readVarInt(), buffer.readVarInt(), buffer.readVarInt()); }
        private static void handle(RaceDirectorSignalControlMessage message, CustomPayloadEvent.Context context) {
            context.enqueueWork(() -> {
                if (!(context.getSender() instanceof ServerPlayer player) || !(player.containerMenu instanceof RaceDirectorMenu menu) || !menu.allowsRaceControl()) return;
                OWRRaceControlState state = OWRRaceControlState.get(player.level());
                switch (message.action) {
                    case START_PHASE -> {
                        state.setStartPhase(message.first);
                        if (message.first == 1 && player.level() instanceof ServerLevel level) {
                            com.openwheelracing.content.race.RaceAutoFlagService.requestStart(level);
                        }
                    }
                    case AUTO_FLAGGING -> state.setAutoFlagging(message.first < 0 ? !state.isAutoFlagging() : message.first != 0);
                    case SECTOR_FLAG -> state.setSectorSignal(message.first, -1, com.openwheelracing.content.race.RaceSignal.fromOrdinal(message.second));
                    case PIT_SIGNAL -> state.setPitSignal(com.openwheelracing.content.race.PitLightMode.fromOrdinal(message.first), com.openwheelracing.content.race.RaceSignal.fromOrdinal(message.second));
                    case DRIVER_FLAG -> {
                        net.minecraft.world.entity.Entity entity = player.level().getEntity(message.first);
                        if (entity instanceof OpenwheelCarEntity car) {
                            java.util.UUID driver = car.getBasicAiIdentity().map(com.openwheelracing.content.ai.BasicAiDriverIdentity::driverId)
                                .orElseGet(() -> car.getFirstPassenger() == null ? null : car.getFirstPassenger().getUUID());
                            state.setDriverSignal(driver, com.openwheelracing.content.race.RaceSignal.fromOrdinal(message.second));
                        }
                    }
                    default -> { return; }
                }
                sendRaceDirectorSnapshot(player, menu.createSnapshot(player.level()));
            });
        }
    }

    public record RaceDirectorCycleConditionModifierMessage(int modifier, int delta) {


        public static final int CAR_DAMAGE = 0;
        public static final int TYRE_WEAR = 1;

        private static void encode(RaceDirectorCycleConditionModifierMessage message, FriendlyByteBuf buffer) {
            buffer.writeInt(message.modifier);
            buffer.writeInt(message.delta);
        }

        private static RaceDirectorCycleConditionModifierMessage decode(FriendlyByteBuf buffer) {
            return new RaceDirectorCycleConditionModifierMessage(buffer.readInt(), buffer.readInt());
        }

        private static void handle(RaceDirectorCycleConditionModifierMessage message, CustomPayloadEvent.Context context) {
            context.enqueueWork(() -> {
                ServerPlayer player = context.getSender();
                if (player == null || !(player.containerMenu instanceof RaceDirectorMenu menu) || !menu.allowsRaceControl()) {
                    return;
                }
                OWRRaceControlState state = OWRRaceControlState.get(player.level());
                if (message.modifier == CAR_DAMAGE) {
                    state.cycleCarDamageModifier(message.delta);
                } else if (message.modifier == TYRE_WEAR) {
                    state.cycleTyreWearModifier(message.delta);
                }
                sendRaceDirectorSnapshot(player, menu.createSnapshot(player.level()));
            });
        }
    }

    public record RaceDirectorStartSessionMessage(String sessionName) {


        private static void encode(RaceDirectorStartSessionMessage message, FriendlyByteBuf buffer) {
            buffer.writeUtf(message.sessionName);
        }

        private static RaceDirectorStartSessionMessage decode(FriendlyByteBuf buffer) {
            return new RaceDirectorStartSessionMessage(buffer.readUtf(80));
        }

        private static void handle(RaceDirectorStartSessionMessage message, CustomPayloadEvent.Context context) {
            context.enqueueWork(() -> {
                ServerPlayer player = context.getSender();
                if (player == null || !(player.containerMenu instanceof RaceDirectorMenu menu) || !menu.allowsRaceControl()) {
                    return;
                }
                menu.setArchiveMode(false);
                menu.setPage(0);
                OWRLapRecords records = OWRLapRecords.get(player.level());
                records.startNewSession(message.sessionName);
                if (player.level() instanceof ServerLevel serverLevel) {
                    int lapLimit = OWRRaceControlState.get(serverLevel).getRaceLapLimit();
                    LiveRaceTimingService.start(serverLevel, records.getActiveSessionId(), records.getActiveSessionName(), lapLimit);
                }
                sendRaceDirectorSnapshot(player, menu.createSnapshot(player.level()));
                if (player.level() instanceof ServerLevel serverLevel) {
                    broadcastRankingBoard(serverLevel.getServer(), serverLevel);
                }
            });
        }
    }

    public record RaceDirectorGrandPrixControlMessage(String eventName, String action) {


        private static void encode(RaceDirectorGrandPrixControlMessage message, FriendlyByteBuf buffer) {
            buffer.writeUtf(message.eventName, 80);
            buffer.writeUtf(message.action, 24);
        }

        private static RaceDirectorGrandPrixControlMessage decode(FriendlyByteBuf buffer) {
            return new RaceDirectorGrandPrixControlMessage(buffer.readUtf(80), buffer.readUtf(24));
        }

        private static void handle(RaceDirectorGrandPrixControlMessage message, CustomPayloadEvent.Context context) {
            context.enqueueWork(() -> {
                ServerPlayer player = context.getSender();
                if (player == null || !(player.containerMenu instanceof RaceDirectorMenu menu) || !menu.allowsRaceControl()) {
                    return;
                }
                String action = message.action.toLowerCase(java.util.Locale.ROOT);
                if (!java.util.Set.of("advance", "stage", "countdown", "start", "suspend", "resume", "finish",
                    "provisional", "official", "complete").contains(action)) {
                    return;
                }
                String command = "owr gp control " + StringArgumentType.escapeIfRequired(message.eventName) + " " + action;
                player.level().getServer().getCommands().performPrefixedCommand(
                    player.createCommandSourceStack().withPermission(LevelBasedPermissionSet.MODERATOR), command);
                sendRaceDirectorSnapshot(player, menu.createSnapshot(player.level()));
            });
        }
    }

    /** Bounded setup request; the existing Brigadier commands remain the single validation and persistence path. */
    public record RaceDirectorGrandPrixSetupMessage(String operation, List<String> arguments) {
        private static final int MAX_ARGUMENTS = 10;

        public RaceDirectorGrandPrixSetupMessage {
            operation = operation == null ? "" : operation;
            arguments = List.copyOf(arguments == null ? List.of() : arguments);
        }


        private static void encode(RaceDirectorGrandPrixSetupMessage message, FriendlyByteBuf buffer) {
            buffer.writeUtf(message.operation, 24);
            buffer.writeVarInt(Math.min(message.arguments.size(), MAX_ARGUMENTS));
            message.arguments.stream().limit(MAX_ARGUMENTS).forEach(argument -> buffer.writeUtf(argument, 80));
        }

        private static RaceDirectorGrandPrixSetupMessage decode(FriendlyByteBuf buffer) {
            String operation = buffer.readUtf(24);
            int count = buffer.readVarInt();
            if (count < 0 || count > MAX_ARGUMENTS) {
                throw new IllegalArgumentException("GP setup argument count exceeds " + MAX_ARGUMENTS);
            }
            java.util.ArrayList<String> arguments = new java.util.ArrayList<>(count);
            for (int index = 0; index < count; index++) {
                arguments.add(buffer.readUtf(80));
            }
            return new RaceDirectorGrandPrixSetupMessage(operation, arguments);
        }

        private static void handle(RaceDirectorGrandPrixSetupMessage message, CustomPayloadEvent.Context context) {
            context.enqueueWork(() -> {
                ServerPlayer player = context.getSender();
                if (player == null || !(player.containerMenu instanceof RaceDirectorMenu menu) || !menu.allowsRaceControl()) {
                    return;
                }
                String command = setupCommand(message.operation.toLowerCase(java.util.Locale.ROOT), message.arguments);
                if (command == null) {
                    return;
                }
                player.level().getServer().getCommands().performPrefixedCommand(
                    player.createCommandSourceStack().withPermission(LevelBasedPermissionSet.MODERATOR), command);
                sendRaceDirectorSnapshot(player, menu.createSnapshot(player.level()));
            });
        }

        private static String setupCommand(String operation, List<String> args) {
            try {
                int expectedArguments = switch (operation) {
                    case "create", "delete", "open" -> 1;
                    case "remove", "unregister" -> 2;
                    case "move", "register" -> 3;
                    case "add" -> 10;
                    default -> -1;
                };
                if (args.size() != expectedArguments) return null;
                return switch (operation) {
                    case "create" -> "owr gp create " + quote(args.get(0));
                    case "delete" -> "owr gp delete " + quote(args.get(0));
                    case "open" -> "owr gp open " + quote(args.get(0));
                    case "remove" -> "owr gp remove " + quote(args.get(0)) + " " + integer(args.get(1));
                    case "move" -> "owr gp move " + quote(args.get(0)) + " "
                        + integer(args.get(1)) + " " + integer(args.get(2));
                    case "register" -> "owr gp register " + quote(args.get(0)) + " "
                        + quote(args.get(1)) + " " + quote(args.get(2));
                    case "unregister" -> "owr gp unregister " + quote(args.get(0)) + " " + quote(args.get(1));
                    case "add" -> "owr gp add " + quote(args.get(0)) + " " + word(args.get(1))
                        + " " + word(args.get(2)) + " " + quote(args.get(3)) + " " + integer(args.get(4))
                        + " " + integer(args.get(5)) + " " + integer(args.get(6)) + " " + integer(args.get(7))
                        + " " + integer(args.get(8)) + " " + word(args.get(9));
                    default -> null;
                };
            } catch (IllegalArgumentException exception) {
                return null;
            }
        }

        private static String quote(String value) {
            return StringArgumentType.escapeIfRequired(value);
        }

        private static String integer(String value) {
            return Integer.toString(Integer.parseInt(value));
        }

        private static String word(String value) {
            if (!value.matches("[a-zA-Z_]+")) throw new IllegalArgumentException("Not a command word");
            return value;
        }
    }

    public record RaceDirectorRefreshSessionMessage() {


        private static void encode(RaceDirectorRefreshSessionMessage message, FriendlyByteBuf buffer) {
        }

        private static RaceDirectorRefreshSessionMessage decode(FriendlyByteBuf buffer) {
            return new RaceDirectorRefreshSessionMessage();
        }

        private static void handle(RaceDirectorRefreshSessionMessage message, CustomPayloadEvent.Context context) {
            context.enqueueWork(() -> {
                ServerPlayer player = context.getSender();
                if (player == null || !(player.containerMenu instanceof RaceDirectorMenu menu) || !menu.allowsRaceControl()) {
                    return;
                }
                sendRaceDirectorSnapshot(player, menu.createSnapshot(player.level()));
            });
        }
    }

    public record RaceDirectorSetArchiveModeMessage(boolean archiveMode) {


        private static void encode(RaceDirectorSetArchiveModeMessage message, FriendlyByteBuf buffer) {
            buffer.writeBoolean(message.archiveMode);
        }

        private static RaceDirectorSetArchiveModeMessage decode(FriendlyByteBuf buffer) {
            return new RaceDirectorSetArchiveModeMessage(buffer.readBoolean());
        }

        private static void handle(RaceDirectorSetArchiveModeMessage message, CustomPayloadEvent.Context context) {
            context.enqueueWork(() -> {
                ServerPlayer player = context.getSender();
                if (player == null || !(player.containerMenu instanceof RaceDirectorMenu menu) || !menu.allowsRaceControl()) {
                    return;
                }
                menu.setArchiveMode(message.archiveMode);
                sendRaceDirectorSnapshot(player, menu.createSnapshot(player.level()));
            });
        }
    }

    public record RaceDirectorSetPageMessage(int page) {


        private static void encode(RaceDirectorSetPageMessage message, FriendlyByteBuf buffer) {
            buffer.writeInt(message.page);
        }

        private static RaceDirectorSetPageMessage decode(FriendlyByteBuf buffer) {
            return new RaceDirectorSetPageMessage(buffer.readInt());
        }

        private static void handle(RaceDirectorSetPageMessage message, CustomPayloadEvent.Context context) {
            context.enqueueWork(() -> {
                ServerPlayer player = context.getSender();
                if (player == null || !(player.containerMenu instanceof RaceDirectorMenu menu) || !menu.allowsRaceControl()) {
                    return;
                }
                menu.setPage(message.page);
                sendRaceDirectorSnapshot(player, menu.createSnapshot(player.level()));
            });
        }
    }

    public record TeamTerminalSenseCarsMessage() {


        private static void encode(TeamTerminalSenseCarsMessage message, FriendlyByteBuf buffer) {
        }

        private static TeamTerminalSenseCarsMessage decode(FriendlyByteBuf buffer) {
            return new TeamTerminalSenseCarsMessage();
        }

        private static void handle(TeamTerminalSenseCarsMessage message, CustomPayloadEvent.Context context) {
            context.enqueueWork(() -> {
                ServerPlayer player = context.getSender();
                if (player == null || !(player.containerMenu instanceof RaceDirectorMenu menu) || !menu.showsTeamTerminal()) {
                    return;
                }
                sendRaceDirectorSnapshot(player, menu.createSnapshot(player.level()));
            });
        }
    }

    public record TeamTerminalBindCarMessage(int side, int entityId) {


        private static void encode(TeamTerminalBindCarMessage message, FriendlyByteBuf buffer) {
            buffer.writeInt(message.side);
            buffer.writeInt(message.entityId);
        }

        private static TeamTerminalBindCarMessage decode(FriendlyByteBuf buffer) {
            return new TeamTerminalBindCarMessage(buffer.readInt(), buffer.readInt());
        }

        private static void handle(TeamTerminalBindCarMessage message, CustomPayloadEvent.Context context) {
            context.enqueueWork(() -> {
                ServerPlayer player = context.getSender();
                if (player == null || !(player.containerMenu instanceof RaceDirectorMenu menu) || !menu.showsTeamTerminal()) {
                    return;
                }
                menu.bindTeamCar(message.side, message.entityId);
                sendRaceDirectorSnapshot(player, menu.createSnapshot(player.level()));
            });
        }
    }

    public record TeamTerminalAiPushMessage(int entityId) {
        private static void encode(TeamTerminalAiPushMessage message, FriendlyByteBuf buffer) { buffer.writeInt(message.entityId); }
        private static TeamTerminalAiPushMessage decode(FriendlyByteBuf buffer) { return new TeamTerminalAiPushMessage(buffer.readInt()); }
        private static void handle(TeamTerminalAiPushMessage message, CustomPayloadEvent.Context context) {
            context.enqueueWork(() -> {
                ServerPlayer player = context.getSender();
                if (player == null || !(player.containerMenu instanceof RaceDirectorMenu menu) || !menu.showsTeamTerminal()) return;
                if (player.level().getEntity(message.entityId) instanceof OpenwheelCarEntity car && car.isBasicAiOwned()) {
                    boolean changed = com.openwheelracing.content.ai.BasicAiFleetManager.requestPush(car);
                    sendCommandFeedback(player, changed
                        ? "AI push accepted: " + com.openwheelracing.content.ai.BasicAiFleetManager.calibrationStatus(car)
                        : "AI push refused: car is already at the maximum trial envelope.");
                    sendRaceDirectorSnapshot(player, menu.createSnapshot(player.level()));
                }
            });
        }
    }

    public record MonitorTelemetrySubscribeMessage(int containerId, int carEntityId) {
        private static void encode(MonitorTelemetrySubscribeMessage message, FriendlyByteBuf buffer) { buffer.writeVarInt(message.containerId); buffer.writeInt(message.carEntityId); }
        private static MonitorTelemetrySubscribeMessage decode(FriendlyByteBuf buffer) { return new MonitorTelemetrySubscribeMessage(buffer.readVarInt(), buffer.readInt()); }
        private static void handle(MonitorTelemetrySubscribeMessage message, CustomPayloadEvent.Context context) {
            context.enqueueWork(() -> {
                if (context.getSender() instanceof ServerPlayer player && player.containerMenu instanceof RaceDirectorMenu menu && menu.containerId == message.containerId
                        && menu.getMonitorType() != com.openwheelracing.content.block.entity.RaceMonitorType.BOARD) {
                    menu.setTelemetryCarId(message.carEntityId);
                }
            });
        }
    }

    public record RaceMonitorAutoDetectMapMessage(int radiusBlocks) {


        private static void encode(RaceMonitorAutoDetectMapMessage message, FriendlyByteBuf buffer) {
            buffer.writeVarInt(message.radiusBlocks);
        }

        private static RaceMonitorAutoDetectMapMessage decode(FriendlyByteBuf buffer) {
            return new RaceMonitorAutoDetectMapMessage(buffer.readVarInt());
        }

        private static void handle(RaceMonitorAutoDetectMapMessage message, CustomPayloadEvent.Context context) {
            context.enqueueWork(() -> {
                ServerPlayer player = context.getSender();
                if (player == null || !(player.containerMenu instanceof RaceDirectorMenu menu) || (!menu.showsTeamTerminal() && !menu.showsBoard())) {
                    return;
                }
                int radius = Math.max(TrackMapAutoDetector.MIN_RADIUS_BLOCKS, Math.min(TrackMapAutoDetector.MAX_RADIUS_BLOCKS, message.radiusBlocks));
                menu.autoDetectTrackMap(player.level(), radius);
                sendRaceDirectorSnapshot(player, menu.createSnapshot(player.level()));
            });
        }
    }

    public record RaceDirectorInvalidateLapMessage(long lapId) {


        private static void encode(RaceDirectorInvalidateLapMessage message, FriendlyByteBuf buffer) {
            buffer.writeLong(message.lapId);
        }

        private static RaceDirectorInvalidateLapMessage decode(FriendlyByteBuf buffer) {
            return new RaceDirectorInvalidateLapMessage(buffer.readLong());
        }

        private static void handle(RaceDirectorInvalidateLapMessage message, CustomPayloadEvent.Context context) {
            context.enqueueWork(() -> {
                ServerPlayer player = context.getSender();
                if (player == null || !(player.containerMenu instanceof RaceDirectorMenu menu) || !menu.allowsRaceControl()) {
                    return;
                }
                OWRLapRecords records = OWRLapRecords.get(player.level());
                records.getLap(message.lapId).ifPresent(record -> {
                    if (records.invalidateLap(message.lapId, player.getUUID(), "race director")) {
                        Component announcement = Component.translatable("message.openwheelracing.race_director.lap_invalidated", record.driverName(), formatLapTime(record.lapMillis()), player.getGameProfile().name());
                        if (player.level() instanceof ServerLevel serverLevel) {
                            for (ServerPlayer recipient : serverLevel.getServer().getPlayerList().getPlayers()) {
                                if (recipient.level().dimension().equals(serverLevel.dimension())) {
                                    recipient.sendSystemMessage(announcement);
                                }
                            }
                            OWRLapProfiles.get(serverLevel).removeByLapRecord(message.lapId);
                            broadcastRankingBoard(serverLevel.getServer(), serverLevel);
                        }
                        sendRaceDirectorSnapshot(player, menu.createSnapshot(player.level()));
                    }
                });
            });
        }
    }

    public record RaceDirectorSetBoPMessage(UUID driverId, int weightPercent, int powerPercent) {
        private static void encode(RaceDirectorSetBoPMessage message, FriendlyByteBuf buffer) {
            buffer.writeUUID(message.driverId()); buffer.writeByte(message.weightPercent()); buffer.writeByte(message.powerPercent());
        }
        private static RaceDirectorSetBoPMessage decode(FriendlyByteBuf buffer) {
            return new RaceDirectorSetBoPMessage(buffer.readUUID(), buffer.readByte(), buffer.readByte());
        }
        private static void handle(RaceDirectorSetBoPMessage message, CustomPayloadEvent.Context context) {
            context.enqueueWork(() -> {
                if (!(context.getSender() instanceof ServerPlayer player) || !(player.containerMenu instanceof RaceDirectorMenu menu) || !menu.allowsRaceControl()) return;
                int weight = Math.max(BoPProfileState.MIN_WEIGHT_PERCENT, Math.min(BoPProfileState.MAX_WEIGHT_PERCENT, message.weightPercent()));
                int power = Math.max(BoPProfileState.MIN_POWER_PERCENT, Math.min(BoPProfileState.MAX_POWER_PERCENT, message.powerPercent()));
                ServerLevel level = (ServerLevel) player.level();
                BoPProfileState.get(level).set(message.driverId(), weight, power);
                level.getEntitiesOfClass(OpenwheelCarEntity.class, player.getBoundingBox().inflate(128.0))
                    .stream().filter(car -> car.getControllingPassenger() instanceof ServerPlayer driver && driver.getUUID().equals(message.driverId()))
                    .forEach(car -> car.applyBoP(weight, power));
                sendRaceDirectorSnapshot(player, menu.createSnapshot(level));
            });
        }
    }

    public record RaceDirectorSetBoPLapsMessage(int laps) {
        private static void encode(RaceDirectorSetBoPLapsMessage message, FriendlyByteBuf buffer) { buffer.writeVarInt(message.laps()); }
        private static RaceDirectorSetBoPLapsMessage decode(FriendlyByteBuf buffer) { return new RaceDirectorSetBoPLapsMessage(buffer.readVarInt()); }
        private static void handle(RaceDirectorSetBoPLapsMessage message, CustomPayloadEvent.Context context) {
            context.enqueueWork(() -> {
                if (!(context.getSender() instanceof ServerPlayer player) || !(player.containerMenu instanceof RaceDirectorMenu menu) || !menu.allowsRaceControl()) return;
                menu.setBoPSelectedLaps(message.laps());
                sendRaceDirectorSnapshot(player, menu.createSnapshot((ServerLevel) player.level()));
            });
        }
    }

    public record RankingBoardMessage(String sessionName, List<OWRLapRecords.DriverBest> entries) {


        private static void encode(RankingBoardMessage message, FriendlyByteBuf buffer) {
            buffer.writeUtf(message.sessionName);
            buffer.writeVarInt(message.entries.size());
            for (OWRLapRecords.DriverBest entry : message.entries) {
                buffer.writeUtf(entry.name());
                buffer.writeInt(entry.millis());
            }
        }

        private static RankingBoardMessage decode(FriendlyByteBuf buffer) {
            String sessionName = buffer.readUtf();
            int size = buffer.readVarInt();
            java.util.ArrayList<OWRLapRecords.DriverBest> entries = new java.util.ArrayList<>(size);
            for (int i = 0; i < size; i++) {
                entries.add(new OWRLapRecords.DriverBest(buffer.readUtf(), buffer.readInt()));
            }
            return new RankingBoardMessage(sessionName, entries);
        }

        private static void handle(RankingBoardMessage message, CustomPayloadEvent.Context context) {
            context.enqueueWork(() -> applyRankingBoard(message));
        }
    }

    public record TimingDeltaHudMessage(boolean reset, int segmentCount, List<Integer> statuses, String label, int segmentIndex, int cumulativeDeltaMillis, int miniDeltaMillis) {


        private static void encode(TimingDeltaHudMessage message, FriendlyByteBuf buffer) {
            buffer.writeBoolean(message.reset);
            buffer.writeVarInt(message.segmentCount);
            buffer.writeVarInt(message.statuses.size());
            for (int status : message.statuses) {
                buffer.writeVarInt(status);
            }
            buffer.writeUtf(message.label);
            buffer.writeVarInt(message.segmentIndex);
            buffer.writeInt(message.cumulativeDeltaMillis);
            buffer.writeInt(message.miniDeltaMillis);
        }

        private static TimingDeltaHudMessage decode(FriendlyByteBuf buffer) {
            boolean reset = buffer.readBoolean();
            int segmentCount = buffer.readVarInt();
            int statusCount = buffer.readVarInt();
            java.util.ArrayList<Integer> statuses = new java.util.ArrayList<>(statusCount);
            for (int index = 0; index < statusCount; index++) {
                statuses.add(buffer.readVarInt());
            }
            return new TimingDeltaHudMessage(reset, segmentCount, statuses, buffer.readUtf(), buffer.readVarInt(), buffer.readInt(), buffer.readInt());
        }

        private static void handle(TimingDeltaHudMessage message, CustomPayloadEvent.Context context) {
            context.enqueueWork(() -> applyTimingDeltaHud(message));
        }
    }

    public record LiveLapDeltaHudMessage(int carEntityId, boolean lapActive, boolean hasReference, int localizationStatus, int elapsedMillis,
            float routeDistance, int bestLapMillis, int referenceMillis, int deltaMillis, long serverGameTime) {


        private static void encode(LiveLapDeltaHudMessage message, FriendlyByteBuf buffer) {
            buffer.writeVarInt(message.carEntityId);
            buffer.writeBoolean(message.lapActive);
            buffer.writeBoolean(message.hasReference);
            buffer.writeByte(message.localizationStatus);
            buffer.writeVarInt(message.elapsedMillis);
            buffer.writeFloat(message.routeDistance);
            buffer.writeVarInt(message.bestLapMillis);
            buffer.writeVarInt(message.referenceMillis);
            buffer.writeInt(message.deltaMillis);
            buffer.writeLong(message.serverGameTime);
        }

        private static LiveLapDeltaHudMessage decode(FriendlyByteBuf buffer) {
            return new LiveLapDeltaHudMessage(buffer.readVarInt(), buffer.readBoolean(), buffer.readBoolean(), buffer.readUnsignedByte(), buffer.readVarInt(),
                buffer.readFloat(), buffer.readVarInt(), buffer.readVarInt(), buffer.readInt(), buffer.readLong());
        }

        private static void handle(LiveLapDeltaHudMessage message, CustomPayloadEvent.Context context) {
            context.enqueueWork(() -> applyLiveLapDeltaHud(message));
        }
    }

    public record MonitorTelemetryMessage(int carEntityId, UUID driverId, boolean lapActive, int localizationStatus, int elapsedMillis,
            float routeDistance, float speedKmh, float routeLength, float profileSpacing, boolean profileUpdate, int[] bestSpeedCmps) {
        private static void encode(MonitorTelemetryMessage message, FriendlyByteBuf buffer) {
            buffer.writeInt(message.carEntityId); buffer.writeUUID(message.driverId); buffer.writeBoolean(message.lapActive); buffer.writeByte(message.localizationStatus);
            buffer.writeVarInt(message.elapsedMillis); buffer.writeFloat(message.routeDistance); buffer.writeFloat(message.speedKmh); buffer.writeFloat(message.routeLength); buffer.writeFloat(message.profileSpacing); buffer.writeBoolean(message.profileUpdate);
            buffer.writeVarInt(message.bestSpeedCmps.length); for (int speed : message.bestSpeedCmps) buffer.writeVarInt(speed);
        }
        private static MonitorTelemetryMessage decode(FriendlyByteBuf buffer) {
            int carId = buffer.readInt(); UUID driver = buffer.readUUID(); boolean active = buffer.readBoolean(); int status = buffer.readUnsignedByte(); int elapsed = buffer.readVarInt();
            float distance = buffer.readFloat(); float speed = buffer.readFloat(); float length = buffer.readFloat(); float spacing = buffer.readFloat(); boolean profileUpdate = buffer.readBoolean(); int count = buffer.readVarInt();
            if (count < 0 || count > OWRLapProfiles.MAX_PROFILE_SAMPLES) throw new IllegalArgumentException("monitor profile too large");
            int[] speeds = new int[count]; for (int i = 0; i < count; i++) speeds[i] = buffer.readVarInt();
            return new MonitorTelemetryMessage(carId, driver, active, status, elapsed, distance, speed, length, spacing, profileUpdate, speeds);
        }
        private static void handle(MonitorTelemetryMessage message, CustomPayloadEvent.Context context) { context.enqueueWork(() -> applyMonitorTelemetry(message)); }
    }

    public record CommandFeedbackMessage(String message) {


        private static void encode(CommandFeedbackMessage message, FriendlyByteBuf buffer) {
            buffer.writeUtf(message.message, 256);
        }

        private static CommandFeedbackMessage decode(FriendlyByteBuf buffer) {
            return new CommandFeedbackMessage(buffer.readUtf(256));
        }

        private static void handle(CommandFeedbackMessage message, CustomPayloadEvent.Context context) {
            context.enqueueWork(() -> applyCommandFeedback(message));
        }
    }

    public record VehiclePhysicsPresetMessage(int preset) {


        private static void encode(VehiclePhysicsPresetMessage message, FriendlyByteBuf buffer) {
            buffer.writeVarInt(message.preset);
        }

        private static VehiclePhysicsPresetMessage decode(FriendlyByteBuf buffer) {
            return new VehiclePhysicsPresetMessage(buffer.readVarInt());
        }

        private static void handle(VehiclePhysicsPresetMessage message, CustomPayloadEvent.Context context) {
            context.enqueueWork(() -> {
                VehiclePhysicsPreset[] values = VehiclePhysicsPreset.values();
                VehiclePhysicsPreset preset = message.preset >= 0 && message.preset < values.length
                    ? values[message.preset]
                    : VehiclePhysicsPreset.DYNAMIC;
                VehiclePhysicsPresetState.setClientPreset(preset);
            });
        }
    }

    public record StewardLineOverlayMessage(boolean visible, UUID trackId, String trackName, int revision, List<TrackDefinition.StewardLine> lines) {


        private static void encode(StewardLineOverlayMessage message, FriendlyByteBuf buffer) {
            buffer.writeBoolean(message.visible);
            buffer.writeUUID(message.trackId);
            buffer.writeUtf(message.trackName);
            buffer.writeVarInt(message.revision);
            buffer.writeVarInt(message.lines.size());
            for (TrackDefinition.StewardLine line : message.lines) {
                buffer.writeUtf(line.type().serializedName());
                buffer.writeVarInt(line.index());
                buffer.writeUtf(line.name());
                writePoint(buffer, line.left());
                writePoint(buffer, line.right());
                buffer.writeDouble(line.headingRadians());
                buffer.writeDouble(line.distanceAlongTrack());
            }
        }

        private static StewardLineOverlayMessage decode(FriendlyByteBuf buffer) {
            boolean visible = buffer.readBoolean();
            UUID trackId = buffer.readUUID();
            String trackName = buffer.readUtf();
            int revision = buffer.readVarInt();
            int count = buffer.readVarInt();
            java.util.ArrayList<TrackDefinition.StewardLine> lines = new java.util.ArrayList<>(count);
            for (int index = 0; index < count; index++) {
                TrackDefinition.StewardLineType type = TrackDefinition.StewardLineType.fromSerializedName(buffer.readUtf());
                int lineIndex = buffer.readVarInt();
                String name = buffer.readUtf();
                TrackDefinition.Point3 left = readPoint(buffer);
                TrackDefinition.Point3 right = readPoint(buffer);
                double heading = buffer.readDouble();
                double distance = buffer.readDouble();
                lines.add(new TrackDefinition.StewardLine(type, lineIndex, name, left, right, heading, distance));
            }
            return new StewardLineOverlayMessage(visible, trackId, trackName, revision, lines);
        }

        private static void handle(StewardLineOverlayMessage message, CustomPayloadEvent.Context context) {
            context.enqueueWork(() -> applyStewardLineOverlay(message));
        }
    }

    public record AiRacingLineStrip(UUID id, String label, boolean closed, int color, double[] x, double[] y, double[] z) {
        public AiRacingLineStrip {
            label = label == null ? "" : label;
            x = x == null ? new double[0] : x.clone();
            y = y == null ? new double[0] : y.clone();
            z = z == null ? new double[0] : z.clone();
            if (x.length != y.length || x.length != z.length || x.length > 4096) throw new IllegalArgumentException("invalid AI line strip");
        }
    }

    public record AiRacingLineOverlayMessage(boolean visible, String dimensionId, UUID trackId, String source,
                                              List<AiRacingLineStrip> strips) {
        public AiRacingLineOverlayMessage {
            strips = strips == null ? List.of() : List.copyOf(strips);
            if (strips.size() > 24) throw new IllegalArgumentException("too many AI line strips");
        }
        private static void encode(AiRacingLineOverlayMessage message, FriendlyByteBuf buffer) {
            buffer.writeBoolean(message.visible); buffer.writeUtf(message.dimensionId, 128); buffer.writeUUID(message.trackId); buffer.writeUtf(message.source, 32);
            buffer.writeVarInt(message.strips.size());
            for (AiRacingLineStrip strip : message.strips) {
                buffer.writeUUID(strip.id()); buffer.writeUtf(strip.label(), 64); buffer.writeBoolean(strip.closed()); buffer.writeInt(strip.color());
                buffer.writeVarInt(strip.x().length);
                for (int i = 0; i < strip.x().length; i++) { buffer.writeDouble(strip.x()[i]); buffer.writeDouble(strip.y()[i]); buffer.writeDouble(strip.z()[i]); }
            }
        }
        private static AiRacingLineOverlayMessage decode(FriendlyByteBuf buffer) {
            boolean visible = buffer.readBoolean(); String dimension = buffer.readUtf(128); UUID track = buffer.readUUID(); String source = buffer.readUtf(32);
            int stripCount = Math.min(buffer.readVarInt(), 24); List<AiRacingLineStrip> strips = new java.util.ArrayList<>(stripCount);
            for (int stripIndex = 0; stripIndex < stripCount; stripIndex++) {
                UUID id = buffer.readUUID(); String label = buffer.readUtf(64); boolean closed = buffer.readBoolean(); int color = buffer.readInt();
                int count = Math.min(buffer.readVarInt(), 4096); double[] x = new double[count], y = new double[count], z = new double[count];
                for (int i = 0; i < count; i++) { x[i] = buffer.readDouble(); y[i] = buffer.readDouble(); z[i] = buffer.readDouble(); }
                strips.add(new AiRacingLineStrip(id, label, closed, color, x, y, z));
            }
            return new AiRacingLineOverlayMessage(visible, dimension, track, source, strips);
        }
        private static void handle(AiRacingLineOverlayMessage message, CustomPayloadEvent.Context context) {
            context.enqueueWork(() -> com.openwheelracing.client.render.AiRacingLineOverlay.apply(message));
        }
    }

    public record SurveyRouteOverlayMessage(boolean visible, String dimensionId, UUID trackId, String trackName, boolean recording,
            List<SurveyRoute.Sample> rawSamples, List<SurveyRoute.Node> nodes, double length, double spacing) {
        private static final int MAX_POINTS = SurveyRoute.MAX_POINTS;


        private static void encode(SurveyRouteOverlayMessage message, FriendlyByteBuf buffer) {
            buffer.writeBoolean(message.visible);
            buffer.writeUtf(message.dimensionId, 128);
            buffer.writeUUID(message.trackId);
            buffer.writeUtf(message.trackName, 128);
            buffer.writeBoolean(message.recording);
            buffer.writeVarInt(message.rawSamples.size());
            for (SurveyRoute.Sample sample : message.rawSamples) {
                writeSurveyPoint(buffer, sample.position());
                buffer.writeDouble(sample.headingRadians());
            }
            buffer.writeVarInt(message.nodes.size());
            for (SurveyRoute.Node node : message.nodes) {
                buffer.writeVarInt(node.index());
                writeSurveyPoint(buffer, node.position());
                buffer.writeDouble(node.headingRadians());
                buffer.writeDouble(node.distanceAlongRoute());
            }
            buffer.writeDouble(message.length);
            buffer.writeDouble(message.spacing);
        }

        private static SurveyRouteOverlayMessage decode(FriendlyByteBuf buffer) {
            boolean visible = buffer.readBoolean();
            String dimensionId = buffer.readUtf(128);
            UUID trackId = buffer.readUUID();
            String trackName = buffer.readUtf(128);
            boolean recording = buffer.readBoolean();
            int rawCount = Math.min(buffer.readVarInt(), MAX_POINTS);
            java.util.ArrayList<SurveyRoute.Sample> raw = new java.util.ArrayList<>(rawCount);
            for (int i = 0; i < rawCount; i++) raw.add(new SurveyRoute.Sample(readSurveyPoint(buffer), buffer.readDouble()));
            int nodeCount = Math.min(buffer.readVarInt(), MAX_POINTS);
            java.util.ArrayList<SurveyRoute.Node> nodes = new java.util.ArrayList<>(nodeCount);
            for (int i = 0; i < nodeCount; i++) nodes.add(new SurveyRoute.Node(buffer.readVarInt(), readSurveyPoint(buffer), buffer.readDouble(), buffer.readDouble()));
            return new SurveyRouteOverlayMessage(visible, dimensionId, trackId, trackName, recording, raw, nodes, buffer.readDouble(), buffer.readDouble());
        }

        private static void handle(SurveyRouteOverlayMessage message, CustomPayloadEvent.Context context) {
            context.enqueueWork(() -> applySurveyRouteOverlay(message));
        }
    }

    private static void writeSurveyPoint(FriendlyByteBuf buffer, SurveyRoute.Point point) {
        buffer.writeDouble(point.x());
        buffer.writeDouble(point.y());
        buffer.writeDouble(point.z());
    }

    private static SurveyRoute.Point readSurveyPoint(FriendlyByteBuf buffer) {
        return new SurveyRoute.Point(buffer.readDouble(), buffer.readDouble(), buffer.readDouble());
    }

    private static void writePoint(FriendlyByteBuf buffer, TrackDefinition.Point3 point) {
        buffer.writeDouble(point.x());
        buffer.writeDouble(point.y());
        buffer.writeDouble(point.z());
    }

    private static TrackDefinition.Point3 readPoint(FriendlyByteBuf buffer) {
        return new TrackDefinition.Point3(buffer.readDouble(), buffer.readDouble(), buffer.readDouble());
    }

    private static void applyLiveryTextureCache(LiveryTextureCacheMessage message) {
        try {
            Class<?> minecraftClass = Class.forName("net.minecraft.client.Minecraft");
            Object minecraft = minecraftClass.getMethod("getInstance").invoke(null);
            Class<?> textures = Class.forName("com.openwheelracing.client.livery.ClientLiveryTextures");
            Method save = textures.getMethod("saveSynced", minecraftClass, String.class, byte[].class);
            save.invoke(null, minecraft, message.textureId, message.pngBytes);
            Class<?> renderer = Class.forName("com.openwheelracing.client.render.OpenwheelCarRenderer");
            Method invalidate = renderer.getMethod("invalidateLiveryCache", String.class);
            invalidate.invoke(null, message.textureId);
        } catch (ReflectiveOperationException ignored) {
        }
    }

    private static void applyRaceFlagUpdate(RaceFlagUpdateMessage message) {
        try {
            Class<?> client = Class.forName("com.openwheelracing.client.hud.RaceFlagClient");
            Method method = client.getMethod("setGlobalFlag", RaceFlagMode.class, boolean.class);
            method.invoke(null, RaceFlagMode.fromOrdinal(message.flag), message.announce);
        } catch (ReflectiveOperationException ignored) {
        }
    }

    private static void applyDriveInputAck(DriveInputAckMessage message) {
        try {
            Class<?> minecraftClass = Class.forName("net.minecraft.client.Minecraft");
            Object minecraft = minecraftClass.getMethod("getInstance").invoke(null);
            Object level = minecraftClass.getField("level").get(minecraft);
            if (level == null) {
                return;
            }
            Object player = minecraftClass.getField("player").get(minecraft);
            Entity entity = (Entity) level.getClass().getMethod("getEntity", int.class).invoke(level, message.entityId);
            if (entity instanceof OpenwheelCarEntity car && player instanceof Entity playerEntity && playerEntity.getVehicle() == car) {
                car.applyClientAuthoritativeSnapshot(
                    message.ackedInputSequence,
                    new Vec3(message.x, message.y, message.z),
                    new Vec3(message.deltaX, message.deltaY, message.deltaZ),
                    message.yaw,
                    message.yawRate,
                    message.steeringAngle,
                    message.relaxedFlLatForce,
                    message.relaxedFrLatForce,
                    message.relaxedRlLatForce,
                    message.relaxedRrLatForce,
                    message.wheelAngularSpeedFl,
                    message.wheelAngularSpeedFr,
                    message.wheelAngularSpeedRl,
                    message.wheelAngularSpeedRr
                );
            }
        } catch (ReflectiveOperationException ignored) {
        }
    }

    private static void applyLiveRaceTimingSnapshot(LiveRaceTimingSnapshot snapshot) {
        try {
            Class<?> client = Class.forName("com.openwheelracing.client.hud.LiveRaceTimingClient");
            Method method = client.getMethod("apply", LiveRaceTimingSnapshot.class);
            method.invoke(null, snapshot);
        } catch (ReflectiveOperationException ignored) {
        }
    }

    private static void applyRankingBoard(RankingBoardMessage message) {
        try {
            Class<?> client = Class.forName("com.openwheelracing.client.hud.LapRankingClient");
            Method method = client.getMethod("setRanking", String.class, List.class);
            method.invoke(null, message.sessionName, message.entries);
        } catch (ReflectiveOperationException ignored) {
        }
    }

    private static void applyMonitorTelemetry(MonitorTelemetryMessage message) {
        try {
            Class<?> cache = Class.forName("com.openwheelracing.client.telemetry.MonitorTelemetryClient");
            Method method = cache.getMethod("apply", MonitorTelemetryMessage.class);
            method.invoke(null, message);
        } catch (ReflectiveOperationException ignored) {
        }
    }

    private static void applyLiveLapDeltaHud(LiveLapDeltaHudMessage message) {
        try {
            Class<?> client = Class.forName("com.openwheelracing.client.hud.LiveLapDeltaClient");
            Method method = client.getMethod("apply", LiveLapDeltaHudMessage.class);
            method.invoke(null, message);
        } catch (ReflectiveOperationException ignored) {
        }
    }

    private static void applyTimingDeltaHud(TimingDeltaHudMessage message) {
        try {
            Class<?> client = Class.forName("com.openwheelracing.client.hud.LapDeltaClient");
            if (message.reset) {
                Method reset = client.getMethod("reset", int.class);
                reset.invoke(null, message.segmentCount);
            } else {
                Method update = client.getMethod("update", int.class, List.class, String.class, int.class, int.class, int.class);
                update.invoke(null, message.segmentCount, message.statuses, message.label, message.segmentIndex, message.cumulativeDeltaMillis, message.miniDeltaMillis);
            }
        } catch (ReflectiveOperationException ignored) {
        }
    }

    private static void applyRaceDirectorSnapshot(RaceDirectorSnapshot snapshot) {
        try {
            Class<?> cache = Class.forName("com.openwheelracing.client.map.ClientTrackMapCache");
            Method set = cache.getMethod("set", TrackMapSnapshot.class);
            set.invoke(null, snapshot.trackMap());
            Class<?> receiver = Class.forName("com.openwheelracing.client.screen.RaceDirectorScreen");
            Method method = receiver.getMethod("applySnapshot", RaceDirectorSnapshot.class);
            method.invoke(null, snapshot);
        } catch (ReflectiveOperationException ignored) {
        }
    }

    private static void applyTrackMoistureSnapshot(com.openwheelracing.content.race.TrackMoistureSnapshot snapshot) {
        try {
            Class<?> receiver = Class.forName("com.openwheelracing.client.screen.RaceDirectorScreen");
            Method method = receiver.getMethod("applyMoistureSnapshot", com.openwheelracing.content.race.TrackMoistureSnapshot.class);
            method.invoke(null, snapshot);
        } catch (ReflectiveOperationException ignored) {
        }
    }

    private static void applyCommandFeedback(CommandFeedbackMessage message) {
        try {
            Class<?> minecraftClass = Class.forName("net.minecraft.client.Minecraft");
            Object minecraft = minecraftClass.getMethod("getInstance").invoke(null);
            Object gui = minecraftClass.getField("gui").get(minecraft);
            Object chat = gui.getClass().getMethod("getChat").invoke(gui);
            Method addMessage = chat.getClass().getMethod("addMessage", Component.class);
            addMessage.invoke(chat, Component.literal("[OWR] " + message.message));
        } catch (ReflectiveOperationException ignored) {
        }
    }

    private static void applySurveyRouteOverlay(SurveyRouteOverlayMessage message) {
        try {
            Class<?> overlay = Class.forName("com.openwheelracing.client.render.SurveyRouteOverlay");
            Method method = overlay.getMethod("apply", SurveyRouteOverlayMessage.class);
            method.invoke(null, message);
        } catch (ReflectiveOperationException ignored) {
        }
    }

    private static void applyStewardLineOverlay(StewardLineOverlayMessage message) {
        try {
            Class<?> overlay = Class.forName("com.openwheelracing.client.render.StewardLineOverlay");
            Method method = overlay.getMethod("apply", StewardLineOverlayMessage.class);
            method.invoke(null, message);
        } catch (ReflectiveOperationException ignored) {
        }
    }

    private static String formatLapTime(int millis) {
        int minutes = millis / 60000;
        int seconds = millis / 1000 % 60;
        int milliseconds = millis % 1000;
        return String.format("%d:%02d.%03d", minutes, seconds, milliseconds);
    }
}
