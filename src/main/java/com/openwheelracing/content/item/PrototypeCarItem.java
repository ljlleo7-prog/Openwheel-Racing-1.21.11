package com.openwheelracing.content.item;

import com.openwheelracing.content.car.CarLivery;
import com.openwheelracing.content.car.CarLiveryColors;
import com.openwheelracing.content.car.CarLiveryTexture;
import com.openwheelracing.content.car.PrototypeCarSetup;
import com.openwheelracing.content.entity.OpenwheelCarEntity;
import com.openwheelracing.registry.OWRDataComponents;
import com.openwheelracing.registry.OWREntities;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomModelData;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

public class PrototypeCarItem extends Item {
    public PrototypeCarItem(Properties properties) {
        super(properties);
    }

    public static PrototypeCarSetup getSetup(ItemStack stack) {
        PrototypeCarSetup setup = stack.get(OWRDataComponents.CAR_SETUP.get());
        return setup == null ? PrototypeCarSetup.DEFAULT : setup;
    }

    public static ItemStack createWithDefaultSetup() {
        return create(PrototypeCarSetup.DEFAULT, 0.0f, 0.0f, 0, OpenwheelCarEntity.ERS_MODE_BALANCED, 100);
    }

    public static ItemStack create(PrototypeCarSetup setup, float damage, float tyreWear) {
        return create(setup, damage, tyreWear, 0);
    }

    public static ItemStack create(PrototypeCarSetup setup, float damage, float tyreWear, int livery) {
        return create(setup, damage, tyreWear, livery, OpenwheelCarEntity.ERS_MODE_BALANCED, 100);
    }

    public static ItemStack create(PrototypeCarSetup setup, float damage, float tyreWear, int livery, int ersMode, int ersEnergyPercent) {
        ItemStack stack = new ItemStack(com.openwheelracing.registry.OWRItems.PROTOTYPE_CAR_SPAWN.get());
        stack.set(OWRDataComponents.CAR_SETUP.get(), setup);
        stack.set(OWRDataComponents.CAR_DAMAGE.get(), Math.max(0, Math.min(100, Math.round(damage))));
        stack.set(OWRDataComponents.TYRE_WEAR.get(), Math.max(0, Math.min(100, Math.round(tyreWear))));
        stack.set(OWRDataComponents.ERS_MODE.get(), Math.max(OpenwheelCarEntity.ERS_MODE_HARVEST, Math.min(OpenwheelCarEntity.ERS_MODE_ATTACK, ersMode)));
        stack.set(OWRDataComponents.ERS_ENERGY_PERCENT.get(), Math.max(0, Math.min(100, ersEnergyPercent)));
        int clampedLivery = Math.max(0, Math.min(CarLivery.count() - 1, livery));
        CarLiveryColors colors = CarLiveryColors.fromPreset(CarLivery.fromIndex(clampedLivery));
        stack.set(OWRDataComponents.CAR_LIVERY.get(), clampedLivery);
        stack.set(OWRDataComponents.CAR_LIVERY_COLORS.get(), colors);
        stack.set(OWRDataComponents.CAR_LIVERY_TEXTURE.get(), CarLiveryTexture.NONE);
        applyLiveryItemDisplay(stack, colors);
        return stack;
    }

    public static int getDamage(ItemStack stack) {
        Integer damage = stack.get(OWRDataComponents.CAR_DAMAGE.get());
        return damage == null ? 0 : damage;
    }

    public static int getTyreWear(ItemStack stack) {
        Integer tyreWear = stack.get(OWRDataComponents.TYRE_WEAR.get());
        return tyreWear == null ? 0 : tyreWear;
    }

    public static int getErsMode(ItemStack stack) {
        Integer mode = stack.get(OWRDataComponents.ERS_MODE.get());
        return mode == null ? OpenwheelCarEntity.ERS_MODE_BALANCED : Math.max(OpenwheelCarEntity.ERS_MODE_HARVEST, Math.min(OpenwheelCarEntity.ERS_MODE_ATTACK, mode));
    }

    public static int getErsEnergyPercent(ItemStack stack) {
        Integer energy = stack.get(OWRDataComponents.ERS_ENERGY_PERCENT.get());
        return energy == null ? 100 : Math.max(0, Math.min(100, energy));
    }

    public static int getLivery(ItemStack stack) {
        Integer livery = stack.get(OWRDataComponents.CAR_LIVERY.get());
        return livery == null ? 0 : Math.max(0, Math.min(CarLivery.count() - 1, livery));
    }

    public static CarLiveryColors getLiveryColors(ItemStack stack) {
        CarLiveryColors colors = stack.get(OWRDataComponents.CAR_LIVERY_COLORS.get());
        return colors == null ? CarLiveryColors.fromPreset(CarLivery.fromIndex(getLivery(stack))) : colors;
    }

    public static CarLiveryTexture getLiveryTexture(ItemStack stack) {
        CarLiveryTexture texture = stack.get(OWRDataComponents.CAR_LIVERY_TEXTURE.get());
        return texture == null ? CarLiveryTexture.NONE : texture;
    }

    public static void setLiveryTexture(ItemStack stack, CarLiveryTexture texture) {
        stack.set(OWRDataComponents.CAR_LIVERY_TEXTURE.get(), texture == null ? CarLiveryTexture.NONE : texture);
    }

    public static void setLiveryColors(ItemStack stack, CarLiveryColors colors) {
        stack.set(OWRDataComponents.CAR_LIVERY_COLORS.get(), colors);
        applyLiveryItemDisplay(stack, colors);
    }

    public static void applyLiveryItemDisplay(ItemStack stack, CarLiveryColors colors) {
        stack.set(DataComponents.CUSTOM_MODEL_DATA, new CustomModelData(List.of(), List.of(), List.of(), List.of(colors.bodySide())));
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        Player player = context.getPlayer();
        ItemStack stack = context.getItemInHand();

        if (!level.isClientSide() && player != null) {
            // Spawn on top of the clicked face, entity origin at foot level
            Vec3 spawnPos = Vec3.atBottomCenterOf(context.getClickedPos().relative(context.getClickedFace()));
            // Raise by 0.02 so the bounding box clears the surface it was placed on
            spawnPos = spawnPos.add(0, 0.02, 0);
            OpenwheelCarEntity car = new OpenwheelCarEntity(OWREntities.PROTOTYPE_CAR.get(), level);
            car.setPos(spawnPos.x, spawnPos.y, spawnPos.z);
            car.setYRot(player.getYRot());
            car.setSetup(getSetup(stack));
            car.setDamagePercent(getDamage(stack));
            car.setTyreWearPercent(getTyreWear(stack));
            car.setLivery(getLivery(stack));
            car.setLiveryColors(getLiveryColors(stack));
            car.setLiveryTexture(getLiveryTexture(stack));
            car.setErsMode(getErsMode(stack));
            car.setErsEnergyJoules(OpenwheelCarEntity.ersCapacityJoules() * getErsEnergyPercent(stack) / 100.0);
            level.addFreshEntity(car);

            if (!player.getAbilities().instabuild) {
                stack.shrink(1);
            }
        }

        return InteractionResult.SUCCESS;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> tooltip, TooltipFlag flag) {
        PrototypeCarSetup setup = getSetup(stack);
        tooltip.accept(Component.translatable("tooltip.openwheelracing.prototype_car.single_seat").withStyle(ChatFormatting.GRAY));
        tooltip.accept(Component.translatable("tooltip.openwheelracing.prototype_car.power", setup.power()).withStyle(ChatFormatting.RED));
        tooltip.accept(Component.translatable("tooltip.openwheelracing.prototype_car.grip", setup.grip() + 1).withStyle(ChatFormatting.GREEN));
        tooltip.accept(Component.translatable("tooltip.openwheelracing.prototype_car.aero", setup.aero()).withStyle(ChatFormatting.AQUA));
        tooltip.accept(Component.translatable("tooltip.openwheelracing.prototype_car.gearing", setup.gearing()).withStyle(ChatFormatting.GOLD));
        tooltip.accept(Component.translatable("tooltip.openwheelracing.prototype_car.livery", CarLivery.fromIndex(getLivery(stack)).displayName()).withStyle(ChatFormatting.BLUE));
        CarLiveryColors colors = getLiveryColors(stack);
        tooltip.accept(Component.translatable("tooltip.openwheelracing.prototype_car.livery_colors", CarLiveryColors.colorName(colors.body()), CarLiveryColors.colorName(colors.accent1()), CarLiveryColors.colorName(colors.accent2())).withStyle(ChatFormatting.BLUE));
        CarLiveryTexture texture = getLiveryTexture(stack);
        if (texture.isPresent()) {
            tooltip.accept(Component.translatable("tooltip.openwheelracing.prototype_car.livery_texture", texture.id()).withStyle(ChatFormatting.BLUE));
        }
        tooltip.accept(Component.translatable("tooltip.openwheelracing.prototype_car.damage", getDamage(stack)).withStyle(ChatFormatting.DARK_RED));
        tooltip.accept(Component.translatable("tooltip.openwheelracing.prototype_car.tyres", Math.max(0, 100 - getTyreWear(stack))).withStyle(ChatFormatting.YELLOW));
    }
}
