package com.openwheelracing.content.item;

import com.openwheelracing.content.entity.OpenwheelCarEntity;
import com.openwheelracing.content.entity.SafetyCarEntity;
import com.openwheelracing.registry.OWRDataComponents;
import com.openwheelracing.registry.OWREntities;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

public class SafetyCarItem extends Item {
    public SafetyCarItem(Properties properties) {
        super(properties);
    }

    public static ItemStack createDefault() {
        return create(0.0f, 0.0f);
    }

    public static ItemStack create(float damage, float tyreWear) {
        ItemStack stack = new ItemStack(com.openwheelracing.registry.OWRItems.SAFETY_CAR_SPAWN.get());
        stack.set(OWRDataComponents.CAR_DAMAGE.get(), Math.max(0, Math.min(100, Math.round(damage))));
        stack.set(OWRDataComponents.TYRE_WEAR.get(), Math.max(0, Math.min(100, Math.round(tyreWear))));
        return stack;
    }

    public static int getCarDamage(ItemStack stack) {
        Integer damage = stack.get(OWRDataComponents.CAR_DAMAGE.get());
        return damage == null ? 0 : damage;
    }

    public static int getTyreWear(ItemStack stack) {
        Integer tyreWear = stack.get(OWRDataComponents.TYRE_WEAR.get());
        return tyreWear == null ? 0 : tyreWear;
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        Player player = context.getPlayer();
        ItemStack stack = context.getItemInHand();

        if (!level.isClientSide() && player != null) {
            Vec3 spawnPos = Vec3.atBottomCenterOf(context.getClickedPos().relative(context.getClickedFace())).add(0, 0.02, 0);
            SafetyCarEntity car = new SafetyCarEntity(OWREntities.SAFETY_CAR.get(), level);
            car.setPos(spawnPos.x, spawnPos.y, spawnPos.z);
            car.setYRot(player.getYRot());
            car.setDamagePercent(getCarDamage(stack));
            car.setTyreWearPercent(getTyreWear(stack));
            car.setErsMode(OpenwheelCarEntity.ERS_MODE_HARVEST);
            car.setErsEnergyJoules(0.0);
            level.addFreshEntity(car);

            if (!player.getAbilities().instabuild) {
                stack.shrink(1);
            }
        }

        return InteractionResult.SUCCESS;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> tooltip, TooltipFlag flag) {
        tooltip.accept(Component.translatable("tooltip.openwheelracing.safety_car.official").withStyle(ChatFormatting.GOLD));
        tooltip.accept(Component.translatable("tooltip.openwheelracing.safety_car.rules").withStyle(ChatFormatting.GRAY));
        tooltip.accept(Component.translatable("tooltip.openwheelracing.safety_car.performance").withStyle(ChatFormatting.AQUA));
        tooltip.accept(Component.translatable("tooltip.openwheelracing.prototype_car.damage", getCarDamage(stack)).withStyle(ChatFormatting.DARK_RED));
        tooltip.accept(Component.translatable("tooltip.openwheelracing.prototype_car.tyres", Math.max(0, 100 - getTyreWear(stack))).withStyle(ChatFormatting.YELLOW));
    }
}
