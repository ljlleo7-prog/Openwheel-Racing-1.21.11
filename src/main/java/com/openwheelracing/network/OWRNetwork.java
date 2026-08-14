package com.openwheelracing.network;

import com.openwheelracing.content.entity.OpenwheelCarEntity;
import com.openwheelracing.OpenwheelRacing;
import com.openwheelracing.content.car.CarLiveryTexture;
import com.openwheelracing.content.car.ServerLiveryTextures;
import com.openwheelracing.content.menu.CarAssemblyMenu;
import com.openwheelracing.content.menu.CarPartsReplacementMenu;
import com.openwheelracing.content.menu.RaceDirectorMenu;
import com.openwheelracing.content.race.LapProfileCollector;
import com.openwheelracing.content.race.OWRLapProfiles;
import com.openwheelracing.content.race.OWRLapRecords;
import com.openwheelracing.content.race.OWRRaceControlState;
import com.openwheelracing.content.race.RaceDirectorLapRow;
import com.openwheelracing.content.race.RaceDirectorSnapshot;
import com.openwheelracing.content.race.RaceFlagMode;
import com.openwheelracing.content.race.TeamCarRow;
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
import java.lang.reflect.Method;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public final class OWRNetwork {
    private static final String PROTOCOL = "13";

    public static final int TIMING_STATUS_UNREACHED = 0;
    public static final int TIMING_STATUS_SLOWER = 1;
    public static final int TIMING_STATUS_PERSONAL_BEST = 2;
    public static final int TIMING_STATUS_SESSION_BEST = 3;

    private OWRNetwork() {
    }

    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(PROTOCOL);
        registrar.playToServer(TuneCarMessage.TYPE, codec(TuneCarMessage::encode, TuneCarMessage::decode), TuneCarMessage::handle);
        registrar.playToServer(RepairCarMessage.TYPE, codec(RepairCarMessage::encode, RepairCarMessage::decode), RepairCarMessage::handle);
        registrar.playToServer(StartPartReplacementMessage.TYPE, codec(StartPartReplacementMessage::encode, StartPartReplacementMessage::decode), StartPartReplacementMessage::handle);
        registrar.playToServer(CycleLiveryMessage.TYPE, codec(CycleLiveryMessage::encode, CycleLiveryMessage::decode), CycleLiveryMessage::handle);
        registrar.playToServer(SetLiveryColorMessage.TYPE, codec(SetLiveryColorMessage::encode, SetLiveryColorMessage::decode), SetLiveryColorMessage::handle);
        registrar.playToServer(UploadLiveryTextureMessage.TYPE, codec(UploadLiveryTextureMessage::encode, UploadLiveryTextureMessage::decode), UploadLiveryTextureMessage::handle);
        registrar.playToServer(SetLiveryTextureMessage.TYPE, codec(SetLiveryTextureMessage::encode, SetLiveryTextureMessage::decode), SetLiveryTextureMessage::handle);
        registrar.playToServer(ShiftMessage.TYPE, codec(ShiftMessage::encode, ShiftMessage::decode), ShiftMessage::handle);
        registrar.playToServer(ExitCarMessage.TYPE, codec(ExitCarMessage::encode, ExitCarMessage::decode), ExitCarMessage::handle);
        registrar.playToServer(DriveInputMessage.TYPE, codec(DriveInputMessage::encode, DriveInputMessage::decode), DriveInputMessage::handle);
        registrar.playToServer(ToggleAbsMessage.TYPE, codec(ToggleAbsMessage::encode, ToggleAbsMessage::decode), ToggleAbsMessage::handle);
        registrar.playToServer(ToggleTractionControlMessage.TYPE, codec(ToggleTractionControlMessage::encode, ToggleTractionControlMessage::decode), ToggleTractionControlMessage::handle);
        registrar.playToServer(ToggleDrsMessage.TYPE, codec(ToggleDrsMessage::encode, ToggleDrsMessage::decode), ToggleDrsMessage::handle);
        registrar.playToServer(CycleErsModeMessage.TYPE, codec(CycleErsModeMessage::encode, CycleErsModeMessage::decode), CycleErsModeMessage::handle);
        registrar.playToServer(SetErsModeMessage.TYPE, codec(SetErsModeMessage::encode, SetErsModeMessage::decode), SetErsModeMessage::handle);
        registrar.playToServer(SetErsThresholdsMessage.TYPE, codec(SetErsThresholdsMessage::encode, SetErsThresholdsMessage::decode), SetErsThresholdsMessage::handle);
        registrar.playToServer(MountCarMessage.TYPE, codec(MountCarMessage::encode, MountCarMessage::decode), MountCarMessage::handle);
        registrar.playToServer(TrackEditorPlaceMessage.TYPE, codec(TrackEditorPlaceMessage::encode, TrackEditorPlaceMessage::decode), TrackEditorPlaceMessage::handle);
        registrar.playToServer(TrackEditorUndoMessage.TYPE, codec(TrackEditorUndoMessage::encode, TrackEditorUndoMessage::decode), TrackEditorUndoMessage::handle);
        registrar.playToServer(RaceDirectorToggleRuleMessage.TYPE, codec(RaceDirectorToggleRuleMessage::encode, RaceDirectorToggleRuleMessage::decode), RaceDirectorToggleRuleMessage::handle);
        registrar.playToServer(RaceDirectorSetMinLapTicksMessage.TYPE, codec(RaceDirectorSetMinLapTicksMessage::encode, RaceDirectorSetMinLapTicksMessage::decode), RaceDirectorSetMinLapTicksMessage::handle);
        registrar.playToServer(RaceDirectorSetErsLimitMessage.TYPE, codec(RaceDirectorSetErsLimitMessage::encode, RaceDirectorSetErsLimitMessage::decode), RaceDirectorSetErsLimitMessage::handle);
        registrar.playToServer(RaceDirectorSetGlobalFlagMessage.TYPE, codec(RaceDirectorSetGlobalFlagMessage::encode, RaceDirectorSetGlobalFlagMessage::decode), RaceDirectorSetGlobalFlagMessage::handle);
        registrar.playToServer(RaceDirectorCycleConditionModifierMessage.TYPE, codec(RaceDirectorCycleConditionModifierMessage::encode, RaceDirectorCycleConditionModifierMessage::decode), RaceDirectorCycleConditionModifierMessage::handle);
        registrar.playToServer(RaceDirectorStartSessionMessage.TYPE, codec(RaceDirectorStartSessionMessage::encode, RaceDirectorStartSessionMessage::decode), RaceDirectorStartSessionMessage::handle);
        registrar.playToServer(RaceDirectorRefreshSessionMessage.TYPE, codec(RaceDirectorRefreshSessionMessage::encode, RaceDirectorRefreshSessionMessage::decode), RaceDirectorRefreshSessionMessage::handle);
        registrar.playToServer(RaceDirectorSetArchiveModeMessage.TYPE, codec(RaceDirectorSetArchiveModeMessage::encode, RaceDirectorSetArchiveModeMessage::decode), RaceDirectorSetArchiveModeMessage::handle);
        registrar.playToServer(RaceDirectorSetPageMessage.TYPE, codec(RaceDirectorSetPageMessage::encode, RaceDirectorSetPageMessage::decode), RaceDirectorSetPageMessage::handle);
        registrar.playToServer(TeamTerminalSenseCarsMessage.TYPE, codec(TeamTerminalSenseCarsMessage::encode, TeamTerminalSenseCarsMessage::decode), TeamTerminalSenseCarsMessage::handle);
        registrar.playToServer(TeamTerminalBindCarMessage.TYPE, codec(TeamTerminalBindCarMessage::encode, TeamTerminalBindCarMessage::decode), TeamTerminalBindCarMessage::handle);
        registrar.playToServer(MonitorTelemetrySubscribeMessage.TYPE, codec(MonitorTelemetrySubscribeMessage::encode, MonitorTelemetrySubscribeMessage::decode), MonitorTelemetrySubscribeMessage::handle);
        registrar.playToServer(RaceMonitorAutoDetectMapMessage.TYPE, codec(RaceMonitorAutoDetectMapMessage::encode, RaceMonitorAutoDetectMapMessage::decode), RaceMonitorAutoDetectMapMessage::handle);
        registrar.playToServer(RaceDirectorInvalidateLapMessage.TYPE, codec(RaceDirectorInvalidateLapMessage::encode, RaceDirectorInvalidateLapMessage::decode), RaceDirectorInvalidateLapMessage::handle);
        registrar.playToClient(RaceDirectorSnapshotMessage.TYPE, codec(RaceDirectorSnapshotMessage::encode, RaceDirectorSnapshotMessage::decode), RaceDirectorSnapshotMessage::handle);
        registrar.playToClient(LiveryTextureCacheMessage.TYPE, codec(LiveryTextureCacheMessage::encode, LiveryTextureCacheMessage::decode), LiveryTextureCacheMessage::handle);
        registrar.playToClient(RaceFlagUpdateMessage.TYPE, codec(RaceFlagUpdateMessage::encode, RaceFlagUpdateMessage::decode), RaceFlagUpdateMessage::handle);
        registrar.playToClient(DriveInputAckMessage.TYPE, codec(DriveInputAckMessage::encode, DriveInputAckMessage::decode), DriveInputAckMessage::handle);
        registrar.playToClient(RankingBoardMessage.TYPE, codec(RankingBoardMessage::encode, RankingBoardMessage::decode), RankingBoardMessage::handle);
        registrar.playToClient(CommandFeedbackMessage.TYPE, codec(CommandFeedbackMessage::encode, CommandFeedbackMessage::decode), CommandFeedbackMessage::handle);
        registrar.playToClient(StewardLineOverlayMessage.TYPE, codec(StewardLineOverlayMessage::encode, StewardLineOverlayMessage::decode), StewardLineOverlayMessage::handle);
        registrar.playToClient(SurveyRouteOverlayMessage.TYPE, codec(SurveyRouteOverlayMessage::encode, SurveyRouteOverlayMessage::decode), SurveyRouteOverlayMessage::handle);
        registrar.playToClient(TimingDeltaHudMessage.TYPE, codec(TimingDeltaHudMessage::encode, TimingDeltaHudMessage::decode), TimingDeltaHudMessage::handle);
        registrar.playToClient(LiveLapDeltaHudMessage.TYPE, codec(LiveLapDeltaHudMessage::encode, LiveLapDeltaHudMessage::decode), LiveLapDeltaHudMessage::handle);
        registrar.playToClient(MonitorTelemetryMessage.TYPE, codec(MonitorTelemetryMessage::encode, MonitorTelemetryMessage::decode), MonitorTelemetryMessage::handle);
    }

    public static void sendDriveInputAck(ServerPlayer player, OpenwheelCarEntity car) {
        PacketDistributor.sendToPlayer(player, new DriveInputAckMessage(
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
            car.getRelaxedRrLateralForce()
        ));
    }

    public static void sendLiveryTexture(ServerPlayer player, String textureId, byte[] pngBytes) {
        PacketDistributor.sendToPlayer(player, new LiveryTextureCacheMessage(textureId, pngBytes));
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
                PacketDistributor.sendToPlayersTrackingEntity(car, new LiveryTextureCacheMessage(safe, pngBytes));
            }
        }
    }

    public static void sendToServer(CustomPacketPayload payload) {
        try {
            Class<?> distributor = Class.forName("net.neoforged.neoforge.client.network.ClientPacketDistributor");
            Method method = distributor.getMethod("sendToServer", CustomPacketPayload.class, CustomPacketPayload[].class);
            method.invoke(null, payload, new CustomPacketPayload[0]);
        } catch (ReflectiveOperationException ignored) {
        }
    }

    private static <T extends CustomPacketPayload> StreamCodec<RegistryFriendlyByteBuf, T> codec(Encoder<T> encoder, Decoder<T> decoder) {
        return StreamCodec.of((buffer, message) -> encoder.encode(message, buffer), decoder::decode);
    }

    private static <T extends CustomPacketPayload> CustomPacketPayload.Type<T> payloadType(String path) {
        return new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(OpenwheelRacing.MODID, path));
    }

    @FunctionalInterface
    private interface Encoder<T> {
        void encode(T message, FriendlyByteBuf buffer);
    }

    @FunctionalInterface
    private interface Decoder<T> {
        T decode(FriendlyByteBuf buffer);
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

    private static float sanitizeKeyboardPedal(float value) {
        return sanitizePedal(value) >= 0.5f ? 1.0f : 0.0f;
    }

    private static float sanitizeKeyboardSteering(float value) {
        float clamped = sanitizeSteering(value);
        if (clamped > 0.5f) {
            return 1.0f;
        }
        if (clamped < -0.5f) {
            return -1.0f;
        }
        return 0.0f;
    }

    public record TuneCarMessage(int slot, int delta) implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<TuneCarMessage> TYPE = payloadType("tune_car_message");

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }

        private static void encode(TuneCarMessage message, FriendlyByteBuf buffer) {
            buffer.writeInt(message.slot);
            buffer.writeInt(message.delta);
        }

        private static TuneCarMessage decode(FriendlyByteBuf buffer) {
            return new TuneCarMessage(buffer.readInt(), buffer.readInt());
        }

        private static void handle(TuneCarMessage message, IPayloadContext context) {
            context.enqueueWork(() -> {
                ServerPlayer player = context.player() instanceof ServerPlayer serverPlayer ? serverPlayer : null;
                if (player == null || !(player.containerMenu instanceof CarAssemblyMenu menu) || !menu.allowsSetup()) {
                    return;
                }
                if (menu.queueSetupTune(message.slot, message.delta)) {
                    menu.slotsChanged(menu.getContainer());
                }
            });
        }
    }

    public record RepairCarMessage() implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<RepairCarMessage> TYPE = payloadType("repair_car_message");

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }

        private static void encode(RepairCarMessage message, FriendlyByteBuf buffer) {
        }

        private static RepairCarMessage decode(FriendlyByteBuf buffer) {
            return new RepairCarMessage();
        }

        private static void handle(RepairCarMessage message, IPayloadContext context) {
            context.enqueueWork(() -> {
                ServerPlayer player = context.player() instanceof ServerPlayer serverPlayer ? serverPlayer : null;
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

    public record StartPartReplacementMessage() implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<StartPartReplacementMessage> TYPE = payloadType("start_part_replacement_message");

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }

        private static void encode(StartPartReplacementMessage message, FriendlyByteBuf buffer) {
        }

        private static StartPartReplacementMessage decode(FriendlyByteBuf buffer) {
            return new StartPartReplacementMessage();
        }

        private static void handle(StartPartReplacementMessage message, IPayloadContext context) {
            context.enqueueWork(() -> {
                ServerPlayer player = context.player() instanceof ServerPlayer serverPlayer ? serverPlayer : null;
                if (player == null || !(player.containerMenu instanceof CarPartsReplacementMenu menu)) {
                    return;
                }
                if (menu.queueReplacement()) {
                    menu.slotsChanged(menu.getContainer());
                }
            });
        }
    }

    public record CycleLiveryMessage(int delta) implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<CycleLiveryMessage> TYPE = payloadType("cycle_livery_message");

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }

        private static void encode(CycleLiveryMessage message, FriendlyByteBuf buffer) {
            buffer.writeInt(message.delta);
        }

        private static CycleLiveryMessage decode(FriendlyByteBuf buffer) {
            return new CycleLiveryMessage(buffer.readInt());
        }

        private static void handle(CycleLiveryMessage message, IPayloadContext context) {
            context.enqueueWork(() -> {
                ServerPlayer player = context.player() instanceof ServerPlayer serverPlayer ? serverPlayer : null;
                if (player == null || !(player.containerMenu instanceof CarAssemblyMenu menu) || !menu.allowsLivery()) {
                    return;
                }
                if (menu.queueLiveryPreset(message.delta)) {
                    menu.slotsChanged(menu.getContainer());
                }
            });
        }
    }

    public record SetLiveryColorMessage(int channel, int color) implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<SetLiveryColorMessage> TYPE = payloadType("set_livery_color_message");

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }

        private static void encode(SetLiveryColorMessage message, FriendlyByteBuf buffer) {
            buffer.writeInt(message.channel);
            buffer.writeInt(message.color);
        }

        private static SetLiveryColorMessage decode(FriendlyByteBuf buffer) {
            return new SetLiveryColorMessage(buffer.readInt(), buffer.readInt());
        }

        private static void handle(SetLiveryColorMessage message, IPayloadContext context) {
            context.enqueueWork(() -> {
                ServerPlayer player = context.player() instanceof ServerPlayer serverPlayer ? serverPlayer : null;
                if (player == null || !(player.containerMenu instanceof CarAssemblyMenu menu) || !menu.allowsLivery()) {
                    return;
                }
                if (menu.queueLiveryColor(message.channel, message.color)) {
                    menu.slotsChanged(menu.getContainer());
                }
            });
        }
    }

    public record UploadLiveryTextureMessage(String textureId, byte[] pngBytes) implements CustomPacketPayload {
        private static final int MAX_BYTES = 1_048_576;
        public static final CustomPacketPayload.Type<UploadLiveryTextureMessage> TYPE = payloadType("upload_livery_texture_message");

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }

        private static void encode(UploadLiveryTextureMessage message, FriendlyByteBuf buffer) {
            buffer.writeUtf(CarLiveryTexture.sanitize(message.textureId));
            buffer.writeByteArray(message.pngBytes);
        }

        private static UploadLiveryTextureMessage decode(FriendlyByteBuf buffer) {
            return new UploadLiveryTextureMessage(buffer.readUtf(80), buffer.readByteArray(MAX_BYTES));
        }

        private static void handle(UploadLiveryTextureMessage message, IPayloadContext context) {
            context.enqueueWork(() -> {
                ServerPlayer player = context.player() instanceof ServerPlayer serverPlayer ? serverPlayer : null;
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

    public record SetLiveryTextureMessage(String textureId) implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<SetLiveryTextureMessage> TYPE = payloadType("set_livery_texture_message");

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }

        private static void encode(SetLiveryTextureMessage message, FriendlyByteBuf buffer) {
            buffer.writeUtf(CarLiveryTexture.sanitize(message.textureId));
        }

        private static SetLiveryTextureMessage decode(FriendlyByteBuf buffer) {
            return new SetLiveryTextureMessage(buffer.readUtf(80));
        }

        private static void handle(SetLiveryTextureMessage message, IPayloadContext context) {
            context.enqueueWork(() -> {
                ServerPlayer player = context.player() instanceof ServerPlayer serverPlayer ? serverPlayer : null;
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

    public record ShiftMessage(int direction) implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<ShiftMessage> TYPE = payloadType("shift_message");

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }

        private static void encode(ShiftMessage message, FriendlyByteBuf buffer) {
            buffer.writeInt(message.direction);
        }

        private static ShiftMessage decode(FriendlyByteBuf buffer) {
            return new ShiftMessage(buffer.readInt());
        }

        private static void handle(ShiftMessage message, IPayloadContext context) {
            context.enqueueWork(() -> {
                ServerPlayer player = context.player() instanceof ServerPlayer serverPlayer ? serverPlayer : null;
                if (player == null || !(player.getVehicle() instanceof OpenwheelCarEntity car)) {
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

    public record ExitCarMessage() implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<ExitCarMessage> TYPE = payloadType("exit_car_message");

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }

        private static void encode(ExitCarMessage message, FriendlyByteBuf buffer) {
        }

        private static ExitCarMessage decode(FriendlyByteBuf buffer) {
            return new ExitCarMessage();
        }

        private static void handle(ExitCarMessage message, IPayloadContext context) {
            context.enqueueWork(() -> {
                ServerPlayer player = context.player() instanceof ServerPlayer serverPlayer ? serverPlayer : null;
                if (player != null) {
                    player.stopRiding();
                }
            });
        }
    }

    public record DriveInputMessage(int sequence, float keyboardThrottle, float keyboardBrake, float keyboardSteering, float wheelThrottle, float wheelBrake, float wheelSteering,
            float lowSpeedSteeringRate, float highSpeedSteeringRate, float lowSpeedCenteringRate, float highSpeedCenteringRate,
            float lowSpeedSteeringGain, float highSpeedSteeringGain, float speedResponseCurve, boolean keyboardSteeringSource) implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<DriveInputMessage> TYPE = payloadType("drive_input_message");

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }

        private static void encode(DriveInputMessage message, FriendlyByteBuf buffer) {
            buffer.writeVarInt(message.sequence);
            buffer.writeFloat(message.keyboardThrottle);
            buffer.writeFloat(message.keyboardBrake);
            buffer.writeFloat(message.keyboardSteering);
            buffer.writeFloat(message.wheelThrottle);
            buffer.writeFloat(message.wheelBrake);
            buffer.writeFloat(message.wheelSteering);
            buffer.writeFloat(message.lowSpeedSteeringRate);
            buffer.writeFloat(message.highSpeedSteeringRate);
            buffer.writeFloat(message.lowSpeedCenteringRate);
            buffer.writeFloat(message.highSpeedCenteringRate);
            buffer.writeFloat(message.lowSpeedSteeringGain);
            buffer.writeFloat(message.highSpeedSteeringGain);
            buffer.writeFloat(message.speedResponseCurve);
            buffer.writeBoolean(message.keyboardSteeringSource);
        }

        private static DriveInputMessage decode(FriendlyByteBuf buffer) {
            return new DriveInputMessage(buffer.readVarInt(), buffer.readFloat(), buffer.readFloat(), buffer.readFloat(), buffer.readFloat(), buffer.readFloat(), buffer.readFloat(),
                buffer.readFloat(), buffer.readFloat(), buffer.readFloat(), buffer.readFloat(), buffer.readFloat(), buffer.readFloat(), buffer.readFloat(), buffer.readBoolean());
        }

        private static void handle(DriveInputMessage message, IPayloadContext context) {
            context.enqueueWork(() -> {
                ServerPlayer player = context.player() instanceof ServerPlayer serverPlayer ? serverPlayer : null;
                if (player == null || !(player.getVehicle() instanceof OpenwheelCarEntity car)) {
                    return;
                }
                float keyboardThrottle = sanitizeKeyboardPedal(message.keyboardThrottle);
                float keyboardBrake = sanitizeKeyboardPedal(message.keyboardBrake);
                float keyboardSteering = sanitizeKeyboardSteering(message.keyboardSteering);
                float throttle = keyboardThrottle;
                float brake = keyboardBrake;
                float steering = keyboardSteering;
                boolean wheelAllowed = OWRRaceControlState.get(player.level()).isWheelInputAllowed();
                if (wheelAllowed) {
                    float wheelThrottle = sanitizePedal(message.wheelThrottle);
                    float wheelBrake = sanitizePedal(message.wheelBrake);
                    float wheelSteering = sanitizeSteering(message.wheelSteering);
                    throttle = Math.max(keyboardThrottle, wheelThrottle);
                    brake = Math.max(keyboardBrake, wheelBrake);
                    if (Math.abs(wheelSteering) > 0.0f) {
                        steering = wheelSteering;
                    }
                }
                car.applyDriveInput(message.sequence, throttle, brake, steering, wheelAllowed ? message.keyboardSteeringSource : true);
                car.setKeyboardSteeringTuning(message.lowSpeedSteeringRate, message.highSpeedSteeringRate, message.lowSpeedCenteringRate, message.highSpeedCenteringRate,
                    message.lowSpeedSteeringGain, message.highSpeedSteeringGain, message.speedResponseCurve);
            });
        }
    }

    public record ToggleAbsMessage() implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<ToggleAbsMessage> TYPE = payloadType("toggle_abs_message");

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }

        private static void encode(ToggleAbsMessage message, FriendlyByteBuf buffer) {
        }

        private static ToggleAbsMessage decode(FriendlyByteBuf buffer) {
            return new ToggleAbsMessage();
        }

        private static void handle(ToggleAbsMessage message, IPayloadContext context) {
            context.enqueueWork(() -> {
                ServerPlayer player = context.player() instanceof ServerPlayer serverPlayer ? serverPlayer : null;
                if (player == null || !(player.getVehicle() instanceof OpenwheelCarEntity car)) {
                    return;
                }
                car.toggleAbs();
            });
        }
    }

    public record ToggleTractionControlMessage() implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<ToggleTractionControlMessage> TYPE = payloadType("toggle_traction_control_message");

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }

        private static void encode(ToggleTractionControlMessage message, FriendlyByteBuf buffer) {
        }

        private static ToggleTractionControlMessage decode(FriendlyByteBuf buffer) {
            return new ToggleTractionControlMessage();
        }

        private static void handle(ToggleTractionControlMessage message, IPayloadContext context) {
            context.enqueueWork(() -> {
                ServerPlayer player = context.player() instanceof ServerPlayer serverPlayer ? serverPlayer : null;
                if (player == null || !(player.getVehicle() instanceof OpenwheelCarEntity car)) {
                    return;
                }
                car.toggleTractionControl();
            });
        }
    }

    public record ToggleDrsMessage() implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<ToggleDrsMessage> TYPE = payloadType("toggle_drs_message");

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }

        private static void encode(ToggleDrsMessage message, FriendlyByteBuf buffer) {
        }

        private static ToggleDrsMessage decode(FriendlyByteBuf buffer) {
            return new ToggleDrsMessage();
        }

        private static void handle(ToggleDrsMessage message, IPayloadContext context) {
            context.enqueueWork(() -> {
                ServerPlayer player = context.player() instanceof ServerPlayer serverPlayer ? serverPlayer : null;
                if (player == null || !(player.getVehicle() instanceof OpenwheelCarEntity car)) {
                    return;
                }
                car.toggleDrs();
            });
        }
    }

    public record CycleErsModeMessage(int direction) implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<CycleErsModeMessage> TYPE = payloadType("cycle_ers_mode_message");

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }

        private static void encode(CycleErsModeMessage message, FriendlyByteBuf buffer) {
            buffer.writeInt(message.direction);
        }

        private static CycleErsModeMessage decode(FriendlyByteBuf buffer) {
            return new CycleErsModeMessage(buffer.readInt());
        }

        private static void handle(CycleErsModeMessage message, IPayloadContext context) {
            context.enqueueWork(() -> {
                ServerPlayer player = context.player() instanceof ServerPlayer serverPlayer ? serverPlayer : null;
                if (player == null || !(player.getVehicle() instanceof OpenwheelCarEntity car)) {
                    return;
                }
                car.cycleErsMode(message.direction);
            });
        }
    }

    public record SetErsModeMessage(int mode) implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<SetErsModeMessage> TYPE = payloadType("set_ers_mode_message");

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }

        private static void encode(SetErsModeMessage message, FriendlyByteBuf buffer) {
            buffer.writeInt(message.mode);
        }

        private static SetErsModeMessage decode(FriendlyByteBuf buffer) {
            return new SetErsModeMessage(buffer.readInt());
        }

        private static void handle(SetErsModeMessage message, IPayloadContext context) {
            context.enqueueWork(() -> {
                ServerPlayer player = context.player() instanceof ServerPlayer serverPlayer ? serverPlayer : null;
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
            int licoSpeedThresholdKmh, double licoSteeringThresholdDegrees, double licoLateralGThreshold, int licoHarvestPowerKw, int licoBalancedPowerKw, int licoAttackPowerKw) implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<SetErsThresholdsMessage> TYPE = payloadType("set_ers_thresholds_message");

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }

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

        private static void handle(SetErsThresholdsMessage message, IPayloadContext context) {
            context.enqueueWork(() -> {
                ServerPlayer player = context.player() instanceof ServerPlayer serverPlayer ? serverPlayer : null;
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

    public record MountCarMessage() implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<MountCarMessage> TYPE = payloadType("mount_car_message");

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }

        private static void encode(MountCarMessage message, FriendlyByteBuf buffer) {
        }

        private static MountCarMessage decode(FriendlyByteBuf buffer) {
            return new MountCarMessage();
        }

        private static void handle(MountCarMessage message, IPayloadContext context) {
            context.enqueueWork(() -> {
                ServerPlayer player = context.player() instanceof ServerPlayer serverPlayer ? serverPlayer : null;
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

    public record TrackEditorPlaceMessage(TrackEditorOperation operation) implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<TrackEditorPlaceMessage> TYPE = payloadType("track_editor_place_message");

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }

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

        private static void handle(TrackEditorPlaceMessage message, IPayloadContext context) {
            context.enqueueWork(() -> {
                ServerPlayer player = context.player() instanceof ServerPlayer serverPlayer ? serverPlayer : null;
                if (player != null) {
                    TrackEditorPlacementService.PlacementResult result = TrackEditorPlacementService.place(player, message.operation());
                    if (result != TrackEditorPlacementService.PlacementResult.PLACED) {
                        player.displayClientMessage(Component.translatable("message.openwheelracing.track_editor.place_failed." + result.name().toLowerCase(java.util.Locale.ROOT)), true);
                    }
                }
            });
        }
    }

    public record TrackEditorUndoMessage() implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<TrackEditorUndoMessage> TYPE = payloadType("track_editor_undo_message");

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }

        private static void encode(TrackEditorUndoMessage message, FriendlyByteBuf buffer) {
        }

        private static TrackEditorUndoMessage decode(FriendlyByteBuf buffer) {
            return new TrackEditorUndoMessage();
        }

        private static void handle(TrackEditorUndoMessage message, IPayloadContext context) {
            context.enqueueWork(() -> {
                ServerPlayer player = context.player() instanceof ServerPlayer serverPlayer ? serverPlayer : null;
                if (player != null) {
                    TrackEditorUndoStore.undo(player);
                }
            });
        }
    }

    public static void sendRaceDirectorSnapshot(ServerPlayer player, RaceDirectorSnapshot snapshot) {
        PacketDistributor.sendToPlayer(player, new RaceDirectorSnapshotMessage(snapshot));
    }

    public static void sendCommandFeedback(ServerPlayer player, String message) {
        PacketDistributor.sendToPlayer(player, new CommandFeedbackMessage(message));
    }

    public static void sendStewardLineOverlay(ServerPlayer player, boolean visible, TrackDefinition track, int revision) {
        UUID trackId = track == null ? new UUID(0L, 0L) : track.trackId();
        String trackName = track == null ? "" : track.name();
        List<TrackDefinition.StewardLine> lines = track == null ? List.of() : track.stewardLines();
        PacketDistributor.sendToPlayer(player, new StewardLineOverlayMessage(visible, trackId, trackName, revision, lines));
    }

    public static void sendSurveyRouteOverlay(ServerPlayer player, boolean visible, String dimensionId, UUID trackId, String trackName, boolean recording, SurveyRoute route) {
        PacketDistributor.sendToPlayer(player, new SurveyRouteOverlayMessage(visible, dimensionId, trackId, trackName, recording,
            route == null ? List.of() : route.rawSamples(), route == null ? List.of() : route.nodes(), route == null ? 0.0 : route.length(), route == null ? 2.0 : route.spacing()));
    }

    public static void sendMonitorTelemetry(ServerPlayer viewer, int carEntityId, UUID driverId, LapProfileCollector.Latest latest, float carSpeedKmh, double routeLength, boolean profileUpdate, OWRLapProfiles.BestLapProfile best) {
        int[] bestSpeeds = best == null ? new int[0] : best.speedCmps();
        PacketDistributor.sendToPlayer(viewer, new MonitorTelemetryMessage(carEntityId, driverId, latest.active(), latest.status().ordinal(), latest.elapsedMillis(),
            (float) latest.routeDistance(), carSpeedKmh, (float) routeLength, best == null ? 0.0f : (float) best.spacing(), profileUpdate, bestSpeeds));
    }

    public static void sendLiveLapDelta(ServerPlayer player, int carEntityId, LapProfileCollector.Latest latest, OWRLapProfiles.BestLapProfile best,
            int referenceMillis, int deltaMillis, long serverGameTime) {
        PacketDistributor.sendToPlayer(player, new LiveLapDeltaHudMessage(carEntityId, latest.active(), best != null, latest.status().ordinal(), latest.elapsedMillis(),
            (float) latest.routeDistance(), best == null ? 0 : best.lapMillis(), referenceMillis, deltaMillis, serverGameTime));
    }

    public static void sendTimingDeltaReset(ServerPlayer player, int segmentCount) {
        PacketDistributor.sendToPlayer(player, new TimingDeltaHudMessage(true, segmentCount, List.of(), "", -1, 0, 0));
    }

    public static void sendTimingDeltaUpdate(ServerPlayer player, int segmentCount, List<Integer> statuses, String label, int segmentIndex, int cumulativeDeltaMillis, int miniDeltaMillis) {
        PacketDistributor.sendToPlayer(player, new TimingDeltaHudMessage(false, segmentCount, statuses, label, segmentIndex, cumulativeDeltaMillis, miniDeltaMillis));
    }

    public static void sendRaceFlag(ServerPlayer player, ServerLevel level, boolean announce) {
        PacketDistributor.sendToPlayer(player, new RaceFlagUpdateMessage(OWRRaceControlState.get(level).getGlobalFlag().ordinal(), announce));
    }

    public static void broadcastRaceFlag(ServerLevel level, RaceFlagMode flag, boolean announce) {
        RaceFlagUpdateMessage message = new RaceFlagUpdateMessage(flag.ordinal(), announce);
        for (ServerPlayer player : level.getServer().getPlayerList().getPlayers()) {
            if (player.level().dimension().equals(level.dimension())) {
                PacketDistributor.sendToPlayer(player, message);
            }
        }
    }

    public static void sendRankingBoard(ServerPlayer player, ServerLevel level) {
        OWRLapRecords records = OWRLapRecords.get(level);
        PacketDistributor.sendToPlayer(player, new RankingBoardMessage(records.getActiveSessionName(), records.getActiveSessionBestLapsSorted()));
    }

    public static void broadcastRankingBoard(net.minecraft.server.MinecraftServer server, net.minecraft.server.level.ServerLevel level) {
        OWRLapRecords records = OWRLapRecords.get(level);
        RankingBoardMessage msg = new RankingBoardMessage(records.getActiveSessionName(), records.getActiveSessionBestLapsSorted());
        for (net.minecraft.server.level.ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (p.level().dimension().equals(level.dimension())) {
                PacketDistributor.sendToPlayer(p, msg);
            }
        }
    }

    public record RaceDirectorSnapshotMessage(RaceDirectorSnapshot snapshot) implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<RaceDirectorSnapshotMessage> TYPE = payloadType("race_director_snapshot_message");

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }

        private static void encode(RaceDirectorSnapshotMessage message, FriendlyByteBuf buffer) {
            RaceDirectorSnapshot snapshot = message.snapshot;
            buffer.writeBoolean(snapshot.checkpointCheckEnabled());
            buffer.writeBoolean(snapshot.offTrackCheckEnabled());
            buffer.writeInt(snapshot.minimumValidLapTicks());
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
            buffer.writeVarInt(snapshot.laps().size());
            for (RaceDirectorLapRow row : snapshot.laps()) {
                RaceDirectorLapRow.encode(row, buffer);
            }
            buffer.writeVarInt(snapshot.teamCars().size());
            for (TeamCarRow row : snapshot.teamCars()) {
                TeamCarRow.encode(row, buffer);
            }
        }

        private static RaceDirectorSnapshotMessage decode(FriendlyByteBuf buffer) {
            boolean checkpointCheckEnabled = buffer.readBoolean();
            boolean offTrackCheckEnabled = buffer.readBoolean();
            int minimumValidLapTicks = buffer.readInt();
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
            return new RaceDirectorSnapshotMessage(new RaceDirectorSnapshot(checkpointCheckEnabled, offTrackCheckEnabled, minimumValidLapTicks, page, maxPage, raceControlRevision, lapRecordsRevision, maxErsCapacityMj, maxBalancedDeployKw, maxAttackDeployKw, maxHarvestNegativeKw, globalFlag, carDamageModifier, tyreWearModifier, activeSessionId, activeSessionName, archiveMode, leftTeamCarId, rightTeamCarId, trackMap, trackMapScanRunning, trackMapScanScannedChunks, trackMapScanTotalChunks, trackMapScanDetectedCells, laps, teamCars));
        }

        private static void handle(RaceDirectorSnapshotMessage message, IPayloadContext context) {
            context.enqueueWork(() -> applyRaceDirectorSnapshot(message.snapshot));
        }
    }

    public record LiveryTextureCacheMessage(String textureId, byte[] pngBytes) implements CustomPacketPayload {
        private static final int MAX_BYTES = 1_048_576;
        public static final CustomPacketPayload.Type<LiveryTextureCacheMessage> TYPE = payloadType("livery_texture_cache_message");

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }

        private static void encode(LiveryTextureCacheMessage message, FriendlyByteBuf buffer) {
            buffer.writeUtf(CarLiveryTexture.sanitize(message.textureId));
            buffer.writeByteArray(message.pngBytes);
        }

        private static LiveryTextureCacheMessage decode(FriendlyByteBuf buffer) {
            return new LiveryTextureCacheMessage(buffer.readUtf(80), buffer.readByteArray(MAX_BYTES));
        }

        private static void handle(LiveryTextureCacheMessage message, IPayloadContext context) {
            context.enqueueWork(() -> applyLiveryTextureCache(message));
        }
    }

    public record RaceFlagUpdateMessage(int flag, boolean announce) implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<RaceFlagUpdateMessage> TYPE = payloadType("race_flag_update_message");

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }

        private static void encode(RaceFlagUpdateMessage message, FriendlyByteBuf buffer) {
            buffer.writeInt(message.flag);
            buffer.writeBoolean(message.announce);
        }

        private static RaceFlagUpdateMessage decode(FriendlyByteBuf buffer) {
            return new RaceFlagUpdateMessage(buffer.readInt(), buffer.readBoolean());
        }

        private static void handle(RaceFlagUpdateMessage message, IPayloadContext context) {
            context.enqueueWork(() -> applyRaceFlagUpdate(message));
        }
    }

    public record DriveInputAckMessage(int entityId, int ackedInputSequence, double x, double y, double z, double deltaX, double deltaY, double deltaZ, float yaw, double yawRate, double steeringAngle, double relaxedFlLatForce, double relaxedFrLatForce, double relaxedRlLatForce, double relaxedRrLatForce) implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<DriveInputAckMessage> TYPE = payloadType("drive_input_ack_message");

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }

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
        }

        private static DriveInputAckMessage decode(FriendlyByteBuf buffer) {
            return new DriveInputAckMessage(buffer.readVarInt(), buffer.readVarInt(), buffer.readDouble(), buffer.readDouble(), buffer.readDouble(), buffer.readDouble(), buffer.readDouble(), buffer.readDouble(), buffer.readFloat(), buffer.readDouble(), buffer.readDouble(), buffer.readDouble(), buffer.readDouble(), buffer.readDouble(), buffer.readDouble());
        }

        private static void handle(DriveInputAckMessage message, IPayloadContext context) {
            context.enqueueWork(() -> applyDriveInputAck(message));
        }
    }

    public record RaceDirectorToggleRuleMessage(int rule) implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<RaceDirectorToggleRuleMessage> TYPE = payloadType("race_director_toggle_rule_message");

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }

        public static final int CHECKPOINTS = 0;
        public static final int OFF_TRACK = 1;

        private static void encode(RaceDirectorToggleRuleMessage message, FriendlyByteBuf buffer) {
            buffer.writeInt(message.rule);
        }

        private static RaceDirectorToggleRuleMessage decode(FriendlyByteBuf buffer) {
            return new RaceDirectorToggleRuleMessage(buffer.readInt());
        }

        private static void handle(RaceDirectorToggleRuleMessage message, IPayloadContext context) {
            context.enqueueWork(() -> {
                ServerPlayer player = context.player() instanceof ServerPlayer serverPlayer ? serverPlayer : null;
                if (player == null || !(player.containerMenu instanceof RaceDirectorMenu menu) || !menu.allowsRaceControl()) {
                    return;
                }
                OWRRaceControlState state = OWRRaceControlState.get(player.level());
                if (message.rule == CHECKPOINTS) {
                    state.toggleCheckpointCheck();
                } else if (message.rule == OFF_TRACK) {
                    state.toggleOffTrackCheck();
                }
            });
        }
    }

    public record RaceDirectorSetMinLapTicksMessage(int ticks) implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<RaceDirectorSetMinLapTicksMessage> TYPE = payloadType("race_director_set_min_lap_ticks_message");

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }

        private static void encode(RaceDirectorSetMinLapTicksMessage message, FriendlyByteBuf buffer) {
            buffer.writeInt(message.ticks);
        }

        private static RaceDirectorSetMinLapTicksMessage decode(FriendlyByteBuf buffer) {
            return new RaceDirectorSetMinLapTicksMessage(buffer.readInt());
        }

        private static void handle(RaceDirectorSetMinLapTicksMessage message, IPayloadContext context) {
            context.enqueueWork(() -> {
                ServerPlayer player = context.player() instanceof ServerPlayer serverPlayer ? serverPlayer : null;
                if (player == null || !(player.containerMenu instanceof RaceDirectorMenu menu) || !menu.allowsRaceControl()) {
                    return;
                }
                OWRRaceControlState.get(player.level()).setMinimumValidLapTicks(message.ticks);
            });
        }
    }

    public record RaceDirectorSetErsLimitMessage(int limit, int delta) implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<RaceDirectorSetErsLimitMessage> TYPE = payloadType("race_director_set_ers_limit_message");

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }

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

        private static void handle(RaceDirectorSetErsLimitMessage message, IPayloadContext context) {
            context.enqueueWork(() -> {
                ServerPlayer player = context.player() instanceof ServerPlayer serverPlayer ? serverPlayer : null;
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

    public record RaceDirectorSetGlobalFlagMessage(int flag) implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<RaceDirectorSetGlobalFlagMessage> TYPE = payloadType("race_director_set_global_flag_message");

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }

        private static void encode(RaceDirectorSetGlobalFlagMessage message, FriendlyByteBuf buffer) {
            buffer.writeInt(message.flag);
        }

        private static RaceDirectorSetGlobalFlagMessage decode(FriendlyByteBuf buffer) {
            return new RaceDirectorSetGlobalFlagMessage(buffer.readInt());
        }

        private static void handle(RaceDirectorSetGlobalFlagMessage message, IPayloadContext context) {
            context.enqueueWork(() -> {
                ServerPlayer player = context.player() instanceof ServerPlayer serverPlayer ? serverPlayer : null;
                if (player == null || !(player.containerMenu instanceof RaceDirectorMenu menu) || !menu.allowsRaceControl()) {
                    return;
                }
                OWRRaceControlState state = OWRRaceControlState.get(player.level());
                RaceFlagMode requested = RaceFlagMode.fromOrdinal(message.flag);
                RaceFlagMode next = state.getGlobalFlag() == requested ? RaceFlagMode.GREEN : requested;
                state.setGlobalFlag(next);
                if (player.level() instanceof ServerLevel serverLevel) {
                    broadcastRaceFlag(serverLevel, next, true);
                }
                sendRaceDirectorSnapshot(player, menu.createSnapshot(player.level()));
            });
        }
    }

    public record RaceDirectorCycleConditionModifierMessage(int modifier, int delta) implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<RaceDirectorCycleConditionModifierMessage> TYPE = payloadType("race_director_cycle_condition_modifier_message");

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }

        public static final int CAR_DAMAGE = 0;
        public static final int TYRE_WEAR = 1;

        private static void encode(RaceDirectorCycleConditionModifierMessage message, FriendlyByteBuf buffer) {
            buffer.writeInt(message.modifier);
            buffer.writeInt(message.delta);
        }

        private static RaceDirectorCycleConditionModifierMessage decode(FriendlyByteBuf buffer) {
            return new RaceDirectorCycleConditionModifierMessage(buffer.readInt(), buffer.readInt());
        }

        private static void handle(RaceDirectorCycleConditionModifierMessage message, IPayloadContext context) {
            context.enqueueWork(() -> {
                ServerPlayer player = context.player() instanceof ServerPlayer serverPlayer ? serverPlayer : null;
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

    public record RaceDirectorStartSessionMessage(String sessionName) implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<RaceDirectorStartSessionMessage> TYPE = payloadType("race_director_start_session_message");

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }

        private static void encode(RaceDirectorStartSessionMessage message, FriendlyByteBuf buffer) {
            buffer.writeUtf(message.sessionName);
        }

        private static RaceDirectorStartSessionMessage decode(FriendlyByteBuf buffer) {
            return new RaceDirectorStartSessionMessage(buffer.readUtf(80));
        }

        private static void handle(RaceDirectorStartSessionMessage message, IPayloadContext context) {
            context.enqueueWork(() -> {
                ServerPlayer player = context.player() instanceof ServerPlayer serverPlayer ? serverPlayer : null;
                if (player == null || !(player.containerMenu instanceof RaceDirectorMenu menu) || !menu.allowsRaceControl()) {
                    return;
                }
                menu.setArchiveMode(false);
                menu.setPage(0);
                OWRLapRecords.get(player.level()).startNewSession(message.sessionName);
                sendRaceDirectorSnapshot(player, menu.createSnapshot(player.level()));
                if (player.level() instanceof ServerLevel serverLevel) {
                    broadcastRankingBoard(serverLevel.getServer(), serverLevel);
                }
            });
        }
    }

    public record RaceDirectorRefreshSessionMessage() implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<RaceDirectorRefreshSessionMessage> TYPE = payloadType("race_director_refresh_session_message");

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }

        private static void encode(RaceDirectorRefreshSessionMessage message, FriendlyByteBuf buffer) {
        }

        private static RaceDirectorRefreshSessionMessage decode(FriendlyByteBuf buffer) {
            return new RaceDirectorRefreshSessionMessage();
        }

        private static void handle(RaceDirectorRefreshSessionMessage message, IPayloadContext context) {
            context.enqueueWork(() -> {
                ServerPlayer player = context.player() instanceof ServerPlayer serverPlayer ? serverPlayer : null;
                if (player == null || !(player.containerMenu instanceof RaceDirectorMenu menu) || !menu.allowsRaceControl()) {
                    return;
                }
                sendRaceDirectorSnapshot(player, menu.createSnapshot(player.level()));
            });
        }
    }

    public record RaceDirectorSetArchiveModeMessage(boolean archiveMode) implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<RaceDirectorSetArchiveModeMessage> TYPE = payloadType("race_director_set_archive_mode_message");

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }

        private static void encode(RaceDirectorSetArchiveModeMessage message, FriendlyByteBuf buffer) {
            buffer.writeBoolean(message.archiveMode);
        }

        private static RaceDirectorSetArchiveModeMessage decode(FriendlyByteBuf buffer) {
            return new RaceDirectorSetArchiveModeMessage(buffer.readBoolean());
        }

        private static void handle(RaceDirectorSetArchiveModeMessage message, IPayloadContext context) {
            context.enqueueWork(() -> {
                ServerPlayer player = context.player() instanceof ServerPlayer serverPlayer ? serverPlayer : null;
                if (player == null || !(player.containerMenu instanceof RaceDirectorMenu menu) || !menu.allowsRaceControl()) {
                    return;
                }
                menu.setArchiveMode(message.archiveMode);
                sendRaceDirectorSnapshot(player, menu.createSnapshot(player.level()));
            });
        }
    }

    public record RaceDirectorSetPageMessage(int page) implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<RaceDirectorSetPageMessage> TYPE = payloadType("race_director_set_page_message");

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }

        private static void encode(RaceDirectorSetPageMessage message, FriendlyByteBuf buffer) {
            buffer.writeInt(message.page);
        }

        private static RaceDirectorSetPageMessage decode(FriendlyByteBuf buffer) {
            return new RaceDirectorSetPageMessage(buffer.readInt());
        }

        private static void handle(RaceDirectorSetPageMessage message, IPayloadContext context) {
            context.enqueueWork(() -> {
                ServerPlayer player = context.player() instanceof ServerPlayer serverPlayer ? serverPlayer : null;
                if (player == null || !(player.containerMenu instanceof RaceDirectorMenu menu) || !menu.allowsRaceControl()) {
                    return;
                }
                menu.setPage(message.page);
                sendRaceDirectorSnapshot(player, menu.createSnapshot(player.level()));
            });
        }
    }

    public record TeamTerminalSenseCarsMessage() implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<TeamTerminalSenseCarsMessage> TYPE = payloadType("team_terminal_sense_cars_message");

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }

        private static void encode(TeamTerminalSenseCarsMessage message, FriendlyByteBuf buffer) {
        }

        private static TeamTerminalSenseCarsMessage decode(FriendlyByteBuf buffer) {
            return new TeamTerminalSenseCarsMessage();
        }

        private static void handle(TeamTerminalSenseCarsMessage message, IPayloadContext context) {
            context.enqueueWork(() -> {
                ServerPlayer player = context.player() instanceof ServerPlayer serverPlayer ? serverPlayer : null;
                if (player == null || !(player.containerMenu instanceof RaceDirectorMenu menu) || !menu.showsTeamTerminal()) {
                    return;
                }
                sendRaceDirectorSnapshot(player, menu.createSnapshot(player.level()));
            });
        }
    }

    public record TeamTerminalBindCarMessage(int side, int entityId) implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<TeamTerminalBindCarMessage> TYPE = payloadType("team_terminal_bind_car_message");

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }

        private static void encode(TeamTerminalBindCarMessage message, FriendlyByteBuf buffer) {
            buffer.writeInt(message.side);
            buffer.writeInt(message.entityId);
        }

        private static TeamTerminalBindCarMessage decode(FriendlyByteBuf buffer) {
            return new TeamTerminalBindCarMessage(buffer.readInt(), buffer.readInt());
        }

        private static void handle(TeamTerminalBindCarMessage message, IPayloadContext context) {
            context.enqueueWork(() -> {
                ServerPlayer player = context.player() instanceof ServerPlayer serverPlayer ? serverPlayer : null;
                if (player == null || !(player.containerMenu instanceof RaceDirectorMenu menu) || !menu.showsTeamTerminal()) {
                    return;
                }
                menu.bindTeamCar(message.side, message.entityId);
                sendRaceDirectorSnapshot(player, menu.createSnapshot(player.level()));
            });
        }
    }

    public record MonitorTelemetrySubscribeMessage(int containerId, int carEntityId) implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<MonitorTelemetrySubscribeMessage> TYPE = payloadType("monitor_telemetry_subscribe_message");
        @Override public CustomPacketPayload.Type<? extends CustomPacketPayload> type() { return TYPE; }
        private static void encode(MonitorTelemetrySubscribeMessage message, FriendlyByteBuf buffer) { buffer.writeVarInt(message.containerId); buffer.writeInt(message.carEntityId); }
        private static MonitorTelemetrySubscribeMessage decode(FriendlyByteBuf buffer) { return new MonitorTelemetrySubscribeMessage(buffer.readVarInt(), buffer.readInt()); }
        private static void handle(MonitorTelemetrySubscribeMessage message, IPayloadContext context) {
            context.enqueueWork(() -> {
                if (context.player() instanceof ServerPlayer player && player.containerMenu instanceof RaceDirectorMenu menu && menu.containerId == message.containerId
                        && menu.getMonitorType() != com.openwheelracing.content.block.entity.RaceMonitorType.BOARD) {
                    menu.setTelemetryCarId(message.carEntityId);
                }
            });
        }
    }

    public record RaceMonitorAutoDetectMapMessage(int radiusBlocks) implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<RaceMonitorAutoDetectMapMessage> TYPE = payloadType("race_monitor_auto_detect_map_message");

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }

        private static void encode(RaceMonitorAutoDetectMapMessage message, FriendlyByteBuf buffer) {
            buffer.writeVarInt(message.radiusBlocks);
        }

        private static RaceMonitorAutoDetectMapMessage decode(FriendlyByteBuf buffer) {
            return new RaceMonitorAutoDetectMapMessage(buffer.readVarInt());
        }

        private static void handle(RaceMonitorAutoDetectMapMessage message, IPayloadContext context) {
            context.enqueueWork(() -> {
                ServerPlayer player = context.player() instanceof ServerPlayer serverPlayer ? serverPlayer : null;
                if (player == null || !(player.containerMenu instanceof RaceDirectorMenu menu) || (!menu.showsTeamTerminal() && !menu.showsBoard())) {
                    return;
                }
                int radius = Math.max(TrackMapAutoDetector.MIN_RADIUS_BLOCKS, Math.min(TrackMapAutoDetector.MAX_RADIUS_BLOCKS, message.radiusBlocks));
                menu.autoDetectTrackMap(player.level(), radius);
                sendRaceDirectorSnapshot(player, menu.createSnapshot(player.level()));
            });
        }
    }

    public record RaceDirectorInvalidateLapMessage(long lapId) implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<RaceDirectorInvalidateLapMessage> TYPE = payloadType("race_director_invalidate_lap_message");

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }

        private static void encode(RaceDirectorInvalidateLapMessage message, FriendlyByteBuf buffer) {
            buffer.writeLong(message.lapId);
        }

        private static RaceDirectorInvalidateLapMessage decode(FriendlyByteBuf buffer) {
            return new RaceDirectorInvalidateLapMessage(buffer.readLong());
        }

        private static void handle(RaceDirectorInvalidateLapMessage message, IPayloadContext context) {
            context.enqueueWork(() -> {
                ServerPlayer player = context.player() instanceof ServerPlayer serverPlayer ? serverPlayer : null;
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

    public record RankingBoardMessage(String sessionName, List<OWRLapRecords.DriverBest> entries) implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<RankingBoardMessage> TYPE = payloadType("ranking_board_message");

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }

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

        private static void handle(RankingBoardMessage message, IPayloadContext context) {
            context.enqueueWork(() -> applyRankingBoard(message));
        }
    }

    public record TimingDeltaHudMessage(boolean reset, int segmentCount, List<Integer> statuses, String label, int segmentIndex, int cumulativeDeltaMillis, int miniDeltaMillis) implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<TimingDeltaHudMessage> TYPE = payloadType("timing_delta_hud_message");

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }

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

        private static void handle(TimingDeltaHudMessage message, IPayloadContext context) {
            context.enqueueWork(() -> applyTimingDeltaHud(message));
        }
    }

    public record LiveLapDeltaHudMessage(int carEntityId, boolean lapActive, boolean hasReference, int localizationStatus, int elapsedMillis,
            float routeDistance, int bestLapMillis, int referenceMillis, int deltaMillis, long serverGameTime) implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<LiveLapDeltaHudMessage> TYPE = payloadType("live_lap_delta_hud_message");

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type() { return TYPE; }

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

        private static void handle(LiveLapDeltaHudMessage message, IPayloadContext context) {
            context.enqueueWork(() -> applyLiveLapDeltaHud(message));
        }
    }

    public record MonitorTelemetryMessage(int carEntityId, UUID driverId, boolean lapActive, int localizationStatus, int elapsedMillis,
            float routeDistance, float speedKmh, float routeLength, float profileSpacing, boolean profileUpdate, int[] bestSpeedCmps) implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<MonitorTelemetryMessage> TYPE = payloadType("monitor_telemetry_message");
        @Override public CustomPacketPayload.Type<? extends CustomPacketPayload> type() { return TYPE; }
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
        private static void handle(MonitorTelemetryMessage message, IPayloadContext context) { context.enqueueWork(() -> applyMonitorTelemetry(message)); }
    }

    public record CommandFeedbackMessage(String message) implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<CommandFeedbackMessage> TYPE = payloadType("command_feedback_message");

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }

        private static void encode(CommandFeedbackMessage message, FriendlyByteBuf buffer) {
            buffer.writeUtf(message.message, 256);
        }

        private static CommandFeedbackMessage decode(FriendlyByteBuf buffer) {
            return new CommandFeedbackMessage(buffer.readUtf(256));
        }

        private static void handle(CommandFeedbackMessage message, IPayloadContext context) {
            context.enqueueWork(() -> applyCommandFeedback(message));
        }
    }

    public record StewardLineOverlayMessage(boolean visible, UUID trackId, String trackName, int revision, List<TrackDefinition.StewardLine> lines) implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<StewardLineOverlayMessage> TYPE = payloadType("steward_line_overlay_message");

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }

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

        private static void handle(StewardLineOverlayMessage message, IPayloadContext context) {
            context.enqueueWork(() -> applyStewardLineOverlay(message));
        }
    }

    public record SurveyRouteOverlayMessage(boolean visible, String dimensionId, UUID trackId, String trackName, boolean recording,
            List<SurveyRoute.Sample> rawSamples, List<SurveyRoute.Node> nodes, double length, double spacing) implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<SurveyRouteOverlayMessage> TYPE = payloadType("survey_route_overlay_message");
        private static final int MAX_POINTS = SurveyRoute.MAX_POINTS;

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type() { return TYPE; }

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

        private static void handle(SurveyRouteOverlayMessage message, IPayloadContext context) {
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
                    message.relaxedRrLatForce
                );
            }
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
