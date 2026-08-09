package com.openwheelracing.content.entity;

import com.openwheelracing.content.car.PrototypeCarSetup;
import com.openwheelracing.content.item.SafetyCarItem;
import com.openwheelracing.registry.OWRItems;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

public class SafetyCarEntity extends OpenwheelCarEntity {
    public static final PrototypeCarSetup SAFETY_CAR_SETUP = new PrototypeCarSetup(1, 2, 0, 1);
    private static final VehicleProfile SAFETY_CAR_PROFILE = new VehicleProfile(
        1_635.0, 2.73, 1.88, 0.53, 2_850.0, 0.82, 0.08, 430_000.0,
        22_000.0, 1.18, 1.22, 95_000.0, 110_000.0, 140_000.0, 165_000.0,
        Math.toRadians(38.0), Math.toRadians(4.6), 0.64, 0.35, OWRItems.SAFETY_CAR_SPAWN
    );

    public SafetyCarEntity(EntityType<? extends SafetyCarEntity> entityType, Level level) {
        super(entityType, level);
        setSetup(SAFETY_CAR_SETUP);
        setAbsEnabled(true);
        setTractionControlEnabled(true);
    }

    @Override
    protected VehicleProfile vehicleProfile() {
        return SAFETY_CAR_PROFILE;
    }

    @Override
    public boolean participatesInRaceTiming() {
        return false;
    }

    @Override
    public boolean isSafetyCar() {
        return true;
    }

    @Override
    public void setSetup(PrototypeCarSetup setup) {
        super.setSetup(SAFETY_CAR_SETUP);
    }

    @Override
    public void setDrsActive(boolean active) {
        super.setDrsActive(false);
    }

    @Override
    public void toggleDrs() {
        setDrsActive(false);
    }

    @Override
    public void setErsMode(int mode) {
        super.setErsMode(ERS_MODE_HARVEST);
    }

    @Override
    public void cycleErsMode(int direction) {
        setErsMode(ERS_MODE_HARVEST);
    }

    @Override
    public void cycleErsModeLocal(int direction) {
        setErsMode(ERS_MODE_HARVEST);
    }

    @Override
    public void setErsEnergyJoules(double energyJoules) {
        super.setErsEnergyJoules(0.0);
    }

    @Override
    public void applyErsLimits(int maxCapacityMj, int maxBalancedDeployKw, int maxAttackDeployKw, int maxHarvestNegativeKw) {
        setErsMode(ERS_MODE_HARVEST);
        setErsEnergyJoules(0.0);
    }

    @Override
    protected ItemStack createPickupItem() {
        return SafetyCarItem.create(getDamagePercent(), getTyreWearPercent());
    }
}
