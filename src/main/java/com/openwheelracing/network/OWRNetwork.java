package com.openwheelracing.network;

import com.openwheelracing.content.entity.OpenwheelCarEntity;
import com.openwheelracing.OpenwheelRacing;
import com.openwheelracing.content.car.CarLivery;
import com.openwheelracing.content.car.CarLiveryColors;
import com.openwheelracing.content.car.CarLiveryTexture;
import com.openwheelracing.content.car.PrototypeCarSetup;
import com.openwheelracing.content.item.PrototypeCarItem;
import com.openwheelracing.content.menu.CarAssemblyMenu;
import com.openwheelracing.content.menu.RaceDirectorMenu;
import com.openwheelracing.content.race.OWRLapRecords;
import com.openwheelracing.content.race.OWRRaceControlState;
import com.openwheelracing.content.race.RaceDirectorLapRow;
import com.openwheelracing.content.race.RaceDirectorSnapshot;
import com.openwheelracing.client.hud.LapRankingClient;
import com.openwheelracing.content.track.TrackEditorMaterial;
import com.openwheelracing.content.track.TrackEditorMode;
import com.openwheelracing.content.track.TrackEditorOperation;
import com.openwheelracing.content.track.TrackEditorPlacementService;
import com.openwheelracing.content.track.TrackEditorPreset;
import com.openwheelracing.content.track.TrackEditorUndoStore;
import com.openwheelracing.registry.OWRDataComponents;
import com.openwheelracing.registry.OWRItems;
import java.lang.reflect.Method;
import java.util.List;
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
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public final class OWRNetwork {
    private static final String PROTOCOL = "1";

    private OWRNetwork() {
    }

    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(PROTOCOL);
        registrar.playToServer(TuneCarMessage.TYPE, codec(TuneCarMessage::encode, TuneCarMessage::decode), TuneCarMessage::handle);
        registrar.playToServer(RepairCarMessage.TYPE, codec(RepairCarMessage::encode, RepairCarMessage::decode), RepairCarMessage::handle);
        registrar.playToServer(CycleLiveryMessage.TYPE, codec(CycleLiveryMessage::encode, CycleLiveryMessage::decode), CycleLiveryMessage::handle);
        registrar.playToServer(SetLiveryColorMessage.TYPE, codec(SetLiveryColorMessage::encode, SetLiveryColorMessage::decode), SetLiveryColorMessage::handle);
        registrar.playToServer(SetLiveryTextureMessage.TYPE, codec(SetLiveryTextureMessage::encode, SetLiveryTextureMessage::decode), SetLiveryTextureMessage::handle);
        registrar.playToServer(ShiftMessage.TYPE, codec(ShiftMessage::encode, ShiftMessage::decode), ShiftMessage::handle);
        registrar.playToServer(ExitCarMessage.TYPE, codec(ExitCarMessage::encode, ExitCarMessage::decode), ExitCarMessage::handle);
        registrar.playToServer(DriveInputMessage.TYPE, codec(DriveInputMessage::encode, DriveInputMessage::decode), DriveInputMessage::handle);
        registrar.playToServer(ToggleAbsMessage.TYPE, codec(ToggleAbsMessage::encode, ToggleAbsMessage::decode), ToggleAbsMessage::handle);
        registrar.playToServer(ToggleTractionControlMessage.TYPE, codec(ToggleTractionControlMessage::encode, ToggleTractionControlMessage::decode), ToggleTractionControlMessage::handle);
        registrar.playToServer(ToggleDrsMessage.TYPE, codec(ToggleDrsMessage::encode, ToggleDrsMessage::decode), ToggleDrsMessage::handle);
        registrar.playToServer(CycleErsModeMessage.TYPE, codec(CycleErsModeMessage::encode, CycleErsModeMessage::decode), CycleErsModeMessage::handle);
        registrar.playToServer(SetErsThresholdsMessage.TYPE, codec(SetErsThresholdsMessage::encode, SetErsThresholdsMessage::decode), SetErsThresholdsMessage::handle);
        registrar.playToServer(MountCarMessage.TYPE, codec(MountCarMessage::encode, MountCarMessage::decode), MountCarMessage::handle);
        registrar.playToServer(TrackEditorPlaceMessage.TYPE, codec(TrackEditorPlaceMessage::encode, TrackEditorPlaceMessage::decode), TrackEditorPlaceMessage::handle);
        registrar.playToServer(TrackEditorUndoMessage.TYPE, codec(TrackEditorUndoMessage::encode, TrackEditorUndoMessage::decode), TrackEditorUndoMessage::handle);
        registrar.playToServer(RaceDirectorToggleRuleMessage.TYPE, codec(RaceDirectorToggleRuleMessage::encode, RaceDirectorToggleRuleMessage::decode), RaceDirectorToggleRuleMessage::handle);
        registrar.playToServer(RaceDirectorSetMinLapTicksMessage.TYPE, codec(RaceDirectorSetMinLapTicksMessage::encode, RaceDirectorSetMinLapTicksMessage::decode), RaceDirectorSetMinLapTicksMessage::handle);
        registrar.playToServer(RaceDirectorSetErsLimitMessage.TYPE, codec(RaceDirectorSetErsLimitMessage::encode, RaceDirectorSetErsLimitMessage::decode), RaceDirectorSetErsLimitMessage::handle);
        registrar.playToServer(RaceDirectorSetPageMessage.TYPE, codec(RaceDirectorSetPageMessage::encode, RaceDirectorSetPageMessage::decode), RaceDirectorSetPageMessage::handle);
        registrar.playToServer(RaceDirectorInvalidateLapMessage.TYPE, codec(RaceDirectorInvalidateLapMessage::encode, RaceDirectorInvalidateLapMessage::decode), RaceDirectorInvalidateLapMessage::handle);
        registrar.playToClient(RaceDirectorSnapshotMessage.TYPE, codec(RaceDirectorSnapshotMessage::encode, RaceDirectorSnapshotMessage::decode), RaceDirectorSnapshotMessage::handle);
        registrar.playToClient(RankingBoardMessage.TYPE, codec(RankingBoardMessage::encode, RankingBoardMessage::decode), RankingBoardMessage::handle);
    }

    public static void sendToServer(CustomPacketPayload payload) {
        ClientPacketDistributor.sendToServer(payload);
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
                if (player == null || !(player.containerMenu instanceof CarAssemblyMenu menu)) {
                    return;
                }
                ItemStack stack = menu.getOutputStack();
                if (!stack.is(OWRItems.PROTOTYPE_CAR_SPAWN.get())) {
                    return;
                }
                PrototypeCarSetup setup = PrototypeCarItem.getSetup(stack);
                PrototypeCarSetup updated = switch (message.slot) {
                    case 0 -> new PrototypeCarSetup(setup.power() + message.delta, setup.grip(), setup.aero(), setup.gearing());
                    case 1 -> new PrototypeCarSetup(setup.power(), setup.grip() + message.delta, setup.aero(), setup.gearing());
                    case 2 -> new PrototypeCarSetup(setup.power(), setup.grip(), setup.aero() + message.delta, setup.gearing());
                    case 3 -> new PrototypeCarSetup(setup.power(), setup.grip(), setup.aero(), setup.gearing() + message.delta);
                    default -> setup;
                };
                stack.set(OWRDataComponents.CAR_SETUP.get(), updated);
                menu.slotsChanged(menu.getContainer());
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
                if (player == null || !(player.containerMenu instanceof CarAssemblyMenu menu)) {
                    return;
                }
                ItemStack stack = menu.getOutputStack();
                if (!stack.is(OWRItems.PROTOTYPE_CAR_SPAWN.get())) {
                    return;
                }
                int damage = PrototypeCarItem.getCarDamage(stack);
                if (damage <= 0 || !player.getInventory().contains(new ItemStack(OWRItems.RUBBER.get()))) {
                    return;
                }
                player.getInventory().clearOrCountMatchingItems(item -> item.is(OWRItems.RUBBER.get()), 1, player.inventoryMenu.getCraftSlots());
                stack.set(OWRDataComponents.CAR_DAMAGE.get(), Math.max(0, damage - 25));
                menu.slotsChanged(menu.getContainer());
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
                if (player == null || !(player.containerMenu instanceof CarAssemblyMenu menu)) {
                    return;
                }
                ItemStack stack = menu.getOutputStack();
                if (!stack.is(OWRItems.PROTOTYPE_CAR_SPAWN.get())) {
                    return;
                }
                int current = PrototypeCarItem.getLivery(stack);
                int livery = CarLivery.wrapIndex(current + message.delta);
                CarLiveryColors colors = CarLiveryColors.fromPreset(CarLivery.fromIndex(livery));
                stack.set(OWRDataComponents.CAR_LIVERY.get(), livery);
                PrototypeCarItem.setLiveryColors(stack, colors);
                menu.slotsChanged(menu.getContainer());
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
                if (player == null || !(player.containerMenu instanceof CarAssemblyMenu menu)) {
                    return;
                }
                ItemStack stack = menu.getOutputStack();
                if (!stack.is(OWRItems.PROTOTYPE_CAR_SPAWN.get())) {
                    return;
                }
                PrototypeCarItem.setLiveryColors(stack, PrototypeCarItem.getLiveryColors(stack).withChannel(message.channel, message.color));
                menu.slotsChanged(menu.getContainer());
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
                if (player == null || !(player.containerMenu instanceof CarAssemblyMenu menu)) {
                    return;
                }
                ItemStack stack = menu.getOutputStack();
                if (!stack.is(OWRItems.PROTOTYPE_CAR_SPAWN.get())) {
                    return;
                }
                PrototypeCarItem.setLiveryTexture(stack, new CarLiveryTexture(message.textureId));
                menu.slotsChanged(menu.getContainer());
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

    public record DriveInputMessage(float keyboardThrottle, float keyboardBrake, float keyboardSteering, float wheelThrottle, float wheelBrake, float wheelSteering) implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<DriveInputMessage> TYPE = payloadType("drive_input_message");

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }

        private static void encode(DriveInputMessage message, FriendlyByteBuf buffer) {
            buffer.writeFloat(message.keyboardThrottle);
            buffer.writeFloat(message.keyboardBrake);
            buffer.writeFloat(message.keyboardSteering);
            buffer.writeFloat(message.wheelThrottle);
            buffer.writeFloat(message.wheelBrake);
            buffer.writeFloat(message.wheelSteering);
        }

        private static DriveInputMessage decode(FriendlyByteBuf buffer) {
            return new DriveInputMessage(buffer.readFloat(), buffer.readFloat(), buffer.readFloat(), buffer.readFloat(), buffer.readFloat(), buffer.readFloat());
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
                if (OWRRaceControlState.get(player.level()).isWheelInputAllowed()) {
                    float wheelThrottle = sanitizePedal(message.wheelThrottle);
                    float wheelBrake = sanitizePedal(message.wheelBrake);
                    float wheelSteering = sanitizeSteering(message.wheelSteering);
                    throttle = Math.max(keyboardThrottle, wheelThrottle);
                    brake = Math.max(keyboardBrake, wheelBrake);
                    if (Math.abs(wheelSteering) > 0.0f) {
                        steering = wheelSteering;
                    }
                }
                car.applyDriveInput(throttle, brake, steering);
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

    public static void broadcastRankingBoard(net.minecraft.server.MinecraftServer server, net.minecraft.server.level.ServerLevel level) {
        List<OWRLapRecords.DriverBest> sorted = OWRLapRecords.get(level).getPlayerBestLapsSorted();
        RankingBoardMessage msg = new RankingBoardMessage(sorted);
        for (net.minecraft.server.level.ServerPlayer p : server.getPlayerList().getPlayers()) {
            PacketDistributor.sendToPlayer(p, msg);
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
            buffer.writeVarInt(snapshot.laps().size());
            for (RaceDirectorLapRow row : snapshot.laps()) {
                RaceDirectorLapRow.encode(row, buffer);
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
            int lapCount = buffer.readVarInt();
            java.util.ArrayList<RaceDirectorLapRow> laps = new java.util.ArrayList<>(lapCount);
            for (int index = 0; index < lapCount; index++) {
                laps.add(RaceDirectorLapRow.decode(buffer));
            }
            return new RaceDirectorSnapshotMessage(new RaceDirectorSnapshot(checkpointCheckEnabled, offTrackCheckEnabled, minimumValidLapTicks, page, maxPage, raceControlRevision, lapRecordsRevision, maxErsCapacityMj, maxBalancedDeployKw, maxAttackDeployKw, maxHarvestNegativeKw, laps));
        }

        private static void handle(RaceDirectorSnapshotMessage message, IPayloadContext context) {
            context.enqueueWork(() -> applyRaceDirectorSnapshot(message.snapshot));
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
                if (player == null || !(player.containerMenu instanceof RaceDirectorMenu)) {
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
                if (player == null || !(player.containerMenu instanceof RaceDirectorMenu)) {
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
                if (player == null || !(player.containerMenu instanceof RaceDirectorMenu menu)) {
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
                if (player == null || !(player.containerMenu instanceof RaceDirectorMenu menu)) {
                    return;
                }
                menu.setPage(message.page);
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
                if (player == null || !(player.containerMenu instanceof RaceDirectorMenu menu)) {
                    return;
                }
                OWRLapRecords records = OWRLapRecords.get(player.level());
                records.getLap(message.lapId).ifPresent(record -> {
                    if (records.invalidateLap(message.lapId, player.getUUID(), "race director")) {
                        player.level().getServer().getPlayerList().broadcastSystemMessage(Component.translatable("message.openwheelracing.race_director.lap_invalidated", record.driverName(), formatLapTime(record.lapTicks()), player.getGameProfile().name()), false);
                        sendRaceDirectorSnapshot(player, menu.createSnapshot(player.level()));
                        if (player.level() instanceof ServerLevel serverLevel) {
                            broadcastRankingBoard(serverLevel.getServer(), serverLevel);
                        }
                    }
                });
            });
        }
    }

    public record RankingBoardMessage(List<OWRLapRecords.DriverBest> entries) implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<RankingBoardMessage> TYPE = payloadType("ranking_board_message");

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }

        private static void encode(RankingBoardMessage message, FriendlyByteBuf buffer) {
            buffer.writeVarInt(message.entries.size());
            for (OWRLapRecords.DriverBest entry : message.entries) {
                buffer.writeUtf(entry.name());
                buffer.writeInt(entry.ticks());
            }
        }

        private static RankingBoardMessage decode(FriendlyByteBuf buffer) {
            int size = buffer.readVarInt();
            java.util.ArrayList<OWRLapRecords.DriverBest> entries = new java.util.ArrayList<>(size);
            for (int i = 0; i < size; i++) {
                entries.add(new OWRLapRecords.DriverBest(buffer.readUtf(), buffer.readInt()));
            }
            return new RankingBoardMessage(entries);
        }

        private static void handle(RankingBoardMessage message, IPayloadContext context) {
            context.enqueueWork(() -> LapRankingClient.setRanking(message.entries));
        }
    }

    private static void applyRaceDirectorSnapshot(RaceDirectorSnapshot snapshot) {
        try {
            Class<?> receiver = Class.forName("com.openwheelracing.client.screen.RaceDirectorScreen");
            Method method = receiver.getMethod("applySnapshot", RaceDirectorSnapshot.class);
            method.invoke(null, snapshot);
        } catch (ReflectiveOperationException ignored) {
        }
    }

    private static String formatLapTime(int ticks) {
        int totalCentiseconds = ticks * 5;
        int minutes = totalCentiseconds / 6000;
        int seconds = totalCentiseconds / 100 % 60;
        int centiseconds = totalCentiseconds % 100;
        return String.format("%d:%02d.%02d", minutes, seconds, centiseconds);
    }
}
