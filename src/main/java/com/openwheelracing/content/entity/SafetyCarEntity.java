package com.openwheelracing.content.entity;

import com.openwheelracing.content.car.PrototypeCarSetup;
import com.openwheelracing.content.item.SafetyCarItem;
import com.openwheelracing.registry.OWRItems;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

public class SafetyCarEntity extends OpenwheelCarEntity {
    public static final PrototypeCarSetup SAFETY_CAR_SETUP = new PrototypeCarSetup(2, 4, 3, 1);
    private static final VehicleProfile SAFETY_CAR_PROFILE = new VehicleProfile(
        1_615.0, 2.63, 1.68, 0.47, 2_500.0, 0.72, 2.20, 0.47, 537_000.0, 0.0,
        32_000.0, 0.60, 1.85, 1.95, 205_000.0, 215_000.0, 230_000.0, 280_000.0,
        Math.toRadians(42.0), Math.toRadians(6.0), 1.0, 0.55, 850.0, 3200.0, 7200.0, 7800.0,
        360.0, new double[] {850.0, 1500.0, 2000.0, 3000.0, 4500.0, 6000.0, 6700.0, 7200.0},
        new double[] {0.07, 0.23, 0.42, 0.62, 0.82, 0.93, 1.00, 0.90},
        55.0, new double[] {0.0, 82.0, 120.0, 165.0, 215.0, 265.0, 305.0, 325.0},
        1.28, OWRItems.SAFETY_CAR_SPAWN
    );

    public SafetyCarEntity(EntityType<? extends SafetyCarEntity> entityType, Level level) {
        super(entityType, level);
        setSetup(SAFETY_CAR_SETUP);
        setDamagePercent(0.0f);
        setTyreWearPercentAndSync(0.0f);
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
    protected float normalizeDamagePercent(float damage) {
        return 0.0f;
    }

    @Override
    protected float normalizeTyreWearPercent(float tyreWear) {
        return 0.0f;
    }

    @Override
    protected boolean takesDamage() {
        return false;
    }

    @Override
    protected boolean usesTyreCondition() {
        return false;
    }

    @Override
    protected ItemStack createPickupItem() {
        return SafetyCarItem.create(getDamagePercent(), getTyreWearPercent());
    }
}
