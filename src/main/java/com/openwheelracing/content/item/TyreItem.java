package com.openwheelracing.content.item;

import com.openwheelracing.content.car.PrototypeCarSetup;
import com.openwheelracing.content.car.TyreType;
import com.openwheelracing.registry.OWRDataComponents;
import com.openwheelracing.registry.OWRItems;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;

public class TyreItem extends Item {
    public TyreItem(Properties properties) {
        super(properties);
    }

    public static ItemStack create(int compound) {
        return create(compound, TyreType.SLICK, 1, 100);
    }

    public static ItemStack create(int compound, int count) {
        return create(compound, TyreType.SLICK, count, 100);
    }

    public static ItemStack create(int compound, int count, double remainingPercent) {
        return create(compound, TyreType.SLICK, count, remainingPercent);
    }

    public static ItemStack create(int compound, TyreType type, int count, double remainingPercent) {
        ItemStack stack = new ItemStack(OWRItems.TIRES.get(), count);
        TyreType normalizedType = type == null ? TyreType.SLICK : type;
        stack.set(OWRDataComponents.TYRE_COMPOUND.get(), normalizedType == TyreType.SLICK ? clampCompound(compound) : PrototypeCarSetup.DEFAULT.grip());
        if (normalizedType != TyreType.SLICK) stack.set(OWRDataComponents.TYRE_TYPE.get(), normalizedType.id());
        setRemainingPercent(stack, remainingPercent);
        return stack;
    }

    public static TyreType getType(ItemStack stack) {
        Integer type = stack.get(OWRDataComponents.TYRE_TYPE.get());
        return type == null ? TyreType.SLICK : TyreType.fromId(type);
    }

    public static int getCompound(ItemStack stack) {
        Integer compound = stack.get(OWRDataComponents.TYRE_COMPOUND.get());
        return compound == null ? PrototypeCarSetup.DEFAULT.grip() : clampCompound(compound);
    }

    public static int getRemainingPercent(ItemStack stack) {
        Integer remainingPercent = stack.get(OWRDataComponents.TYRE_REMAINING_PERCENT.get());
        return remainingPercent == null ? 100 : normalizeRemainingPercent(remainingPercent);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> tooltip, TooltipFlag flag) {
        TyreType type = getType(stack);
        if (type == TyreType.SLICK) tooltip.accept(Component.translatable("tooltip.openwheelracing.tires.compound", getCompound(stack) + 1).withStyle(ChatFormatting.GREEN));
        else tooltip.accept(Component.translatable("tooltip.openwheelracing.tires.type", type.displayName()).withStyle(ChatFormatting.AQUA));
        tooltip.accept(Component.translatable("tooltip.openwheelracing.tires.remaining", getRemainingPercent(stack)).withStyle(ChatFormatting.YELLOW));
    }

    public static int normalizeRemainingPercent(double remainingPercent) {
        return (int) Math.floor(Math.max(0.0, Math.min(100.0, remainingPercent)));
    }

    private static void setRemainingPercent(ItemStack stack, double remainingPercent) {
        int normalizedRemainingPercent = normalizeRemainingPercent(remainingPercent);
        if (normalizedRemainingPercent >= 100) {
            stack.remove(OWRDataComponents.TYRE_REMAINING_PERCENT.get());
        } else {
            stack.set(OWRDataComponents.TYRE_REMAINING_PERCENT.get(), normalizedRemainingPercent);
        }
    }

    private static int clampCompound(int compound) {
        return Math.max(0, Math.min(4, compound));
    }
}
