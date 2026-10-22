package com.openwheelracing.content.recovery;

import com.openwheelracing.OpenwheelRacing;
import com.mojang.authlib.GameProfile;
import java.util.UUID;
import net.neoforged.neoforge.common.util.FakePlayer;
import com.openwheelracing.content.entity.OpenwheelCarEntity;
import com.openwheelracing.registry.OWREntities;
import com.openwheelracing.registry.OWRItems;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Exercises real interaction, server ticks and release without a controlling passenger. */
public final class CarRecoveryGameTests {
    private static final DeferredRegister<Consumer<GameTestHelper>> FUNCTIONS =
        DeferredRegister.create(Registries.TEST_FUNCTION, OpenwheelRacing.MODID);

    static {
        FUNCTIONS.register("stuck_car_recovery", () -> helper -> stuckCarRecovery(helper, false));
        FUNCTIONS.register("occupied_stuck_car_recovery", () -> helper -> stuckCarRecovery(helper, true));
        FUNCTIONS.register("empty_car_recovery", () -> helper -> emptyCarRecovery(helper, OWREntities.PROTOTYPE_CAR.get()));
        FUNCTIONS.register("empty_safety_car_recovery", () -> helper -> emptyCarRecovery(helper, OWREntities.SAFETY_CAR.get()));
    }

    private CarRecoveryGameTests() {
    }

    public static void register(IEventBus bus) {
        FUNCTIONS.register(bus);
    }

    private static void stuckCarRecovery(GameTestHelper helper, boolean occupied) {
        for (int x = 0; x < 12; x++) {
            for (int z = 0; z < 12; z++) helper.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
        }
        for (int y = 1; y <= 5; y++) {
            for (int z = 2; z <= 10; z++) helper.setBlock(new BlockPos(7, y, z), Blocks.STONE);
        }
        ServerPlayer marshal = new FakePlayer(helper.getLevel(), new GameProfile(UUID.randomUUID(), "wall-marshal"));
        Vec3 start = helper.absoluteVec(new Vec3(2, 1, 6.5));
        marshal.setPos(start.x, start.y, start.z);
        helper.getLevel().addNewPlayer(marshal);
        marshal.setYRot(-90);
        marshal.setXRot(0);
        marshal.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(OWRItems.CAR_DRAGGER.get()));
        OpenwheelCarEntity car = helper.spawn(OWREntities.PROTOTYPE_CAR.get(), new Vec3(6.5, 1, 6.5));
        double initialX = car.getX();
        double groundY = car.getY();
        var driver = helper.makeMockPlayer(GameType.SURVIVAL);
        if (occupied) {
            driver.setPos(car.getX() - 2, car.getY(), car.getZ());
            driver.interactOn(car, InteractionHand.MAIN_HAND);
            helper.assertTrue(car.hasPassenger(driver), "Test driver must be seated before recovery");
        }
        marshal.interactOn(car, InteractionHand.MAIN_HAND);
        helper.assertTrue(car.isRecoveryOwner(marshal), "Partially embedded car must attach to the dragger");
        marshal.setPos(start.x - 2, start.y, start.z);
        helper.runAfterDelay(35, () -> {
            helper.assertTrue(car.getX() < initialX - 1.5, "Dragger must pull an already overlapping car out of the wall");
            helper.assertTrue(car.getY() > groundY + 1, "Extracted car must lift after clearing the wall");
            helper.assertTrue(!occupied || car.hasPassenger(driver), "Recovery must retain the seated driver");
            // A separate wall must still block movement; the escape rule must not enable noclip.
            for (int y = 1; y <= 5; y++) {
                for (int z = 2; z <= 10; z++) helper.setBlock(new BlockPos(2, y, z), Blocks.STONE);
            }
            marshal.setYRot(90);
        });
        helper.runAfterDelay(65, () -> {
            double wallEdge = helper.absoluteVec(new Vec3(3, 1, 6.5)).x;
            helper.assertTrue(car.getX() >= wallEdge + 0.94, "Recovered car must stop before entering a fresh wall");
            helper.assertTrue(car.getX() < initialX - 2, "Car must move toward the fresh wall before stopping");
            OWRItems.CAR_DRAGGER.get().use(helper.getLevel(), marshal, InteractionHand.MAIN_HAND);
        });
        helper.runAfterDelay(105, () -> {
            helper.assertTrue(!car.isRecoveryOwner(marshal), "Extracted car must release normally");
            helper.assertTrue(Math.abs(car.getY() - groundY) < 0.1, "Extracted car must lower onto the floor");
            helper.assertTrue(car.isAlive() && (!occupied || car.hasPassenger(driver)), "Extraction must preserve car and driver");
            driver.stopRiding();
            helper.getLevel().removePlayerImmediately(marshal, net.minecraft.world.entity.Entity.RemovalReason.DISCARDED);
            car.discard();
            helper.succeed();
        });
    }

    private static void emptyCarRecovery(GameTestHelper helper, EntityType<? extends OpenwheelCarEntity> type) {
        for (int x = 0; x < 12; x++) {
            for (int z = 0; z < 12; z++) helper.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
        }
        ServerPlayer marshal = new FakePlayer(helper.getLevel(), new GameProfile(UUID.randomUUID(), "recovery-marshal"));
        Vec3 start = helper.absoluteVec(new Vec3(6, 1, 2));
        marshal.setPos(start.x, start.y, start.z);
        helper.getLevel().addNewPlayer(marshal);
        marshal.setYRot(0);
        marshal.setXRot(0);
        marshal.getAbilities().flying = true;
        marshal.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(OWRItems.CAR_DRAGGER.get()));
        OpenwheelCarEntity car = helper.spawn(type, new Vec3(6, 1, 6));
        double groundY = car.getY();
        var carId = car.getUUID();

        marshal.interactOn(car, InteractionHand.MAIN_HAND);
        helper.assertTrue(car.isRecoveryOwner(marshal), "Right-click must attach the empty car");
        helper.assertTrue(car.getPassengers().isEmpty() && !marshal.isPassenger(),
            "Using the dragger must not board an empty car");

        helper.runAfterDelay(20, () -> {
            helper.assertTrue(car.getY() > groundY + 1, "Empty car must lift without a driver");
            marshal.setPos(start.x + 2, start.y, start.z);
        });
        helper.runAfterDelay(40, () -> {
            helper.assertTrue(car.getX() > start.x + 1.5, "Empty car must follow the walking marshal");
            helper.assertTrue(car.getPassengers().isEmpty(), "Transport must keep the car unoccupied");
            OWRItems.CAR_DRAGGER.get().use(helper.getLevel(), marshal, InteractionHand.MAIN_HAND);
        });
        helper.runAfterDelay(80, () -> {
            helper.assertTrue(!car.isRecoveryOwner(marshal), "Release must finish lowering the empty car");
            helper.assertTrue(Math.abs(car.getY() - groundY) < 0.1, "Released car must rest on the ground");
            helper.assertTrue(car.isAlive() && car.getUUID().equals(carId), "Recovery must preserve the original entity");
            helper.assertTrue(car.getDeltaMovement().lengthSqr() < 0.001, "Release must not throw the car");
            // NeoForge FakePlayer deliberately refuses all mounts; use a regular mock for boarding.
            var driver = helper.makeMockPlayer(GameType.SURVIVAL);
            driver.setPos(car.getX() + 2, car.getY(), car.getZ());
            driver.interactOn(car, InteractionHand.MAIN_HAND);
            helper.assertTrue(car.hasPassenger(driver), "Recovered empty car must remain driveable");
            driver.stopRiding();
            helper.getLevel().removePlayerImmediately(marshal, net.minecraft.world.entity.Entity.RemovalReason.DISCARDED);
            car.discard();
            helper.succeed();
        });
    }
}
