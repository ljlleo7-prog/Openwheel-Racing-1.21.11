package com.openwheelracing.content.block.entity;

import com.openwheelracing.content.menu.RaceDirectorMenu;
import com.openwheelracing.registry.OWRBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

public class RaceDirectorBlockEntity extends BlockEntity implements MenuProvider {
    private final RaceMonitorType monitorType;

    public RaceDirectorBlockEntity(BlockPos pos, BlockState state) {
        this(pos, state, RaceMonitorType.DIRECTOR);
    }

    public RaceDirectorBlockEntity(BlockPos pos, BlockState state, RaceMonitorType monitorType) {
        super(OWRBlockEntities.typeFor(monitorType).get(), pos, state);
        this.monitorType = monitorType;
    }

    public RaceMonitorType getMonitorType() {
        return monitorType;
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable(monitorType.containerKey());
    }

    @Override
    public @Nullable AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
        return new RaceDirectorMenu(containerId, playerInventory, this);
    }
}
