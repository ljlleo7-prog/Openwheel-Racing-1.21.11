package com.openwheelracing.content.item;

import com.openwheelracing.content.entity.OpenwheelCarEntity;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

public final class CarDraggerItem extends Item {
    public CarDraggerItem(Properties properties) {
        super(properties);
    }

    public static OpenwheelCarEntity heldCar(Player player) {
        return player.level().getEntitiesOfClass(OpenwheelCarEntity.class,
            player.getBoundingBox().inflate(16), car -> car.isRecoveryOwner(player)).stream().findFirst().orElse(null);
    }

    public static void control(Player player, OpenwheelCarEntity target) {
        OpenwheelCarEntity held = heldCar(player);
        if (held != null) {
            held.controlRecovery(player);
        } else if (target != null) {
            target.beginRecovery(player);
        } else {
            player.displayClientMessage(Component.translatable("message.openwheelracing.car_dragger.aim"), true);
        }
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (!level.isClientSide()) control(player, null);
        return InteractionResult.SUCCESS;
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        if (!context.getLevel().isClientSide() && context.getPlayer() != null) control(context.getPlayer(), null);
        return InteractionResult.SUCCESS;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
                                Consumer<Component> tooltip, TooltipFlag flag) {
        tooltip.accept(Component.translatable("tooltip.openwheelracing.car_dragger.use").withStyle(ChatFormatting.AQUA));
        tooltip.accept(Component.translatable("tooltip.openwheelracing.car_dragger.controls").withStyle(ChatFormatting.GRAY));
        tooltip.accept(Component.translatable("tooltip.openwheelracing.car_dragger.safety").withStyle(ChatFormatting.GRAY));
    }
}
