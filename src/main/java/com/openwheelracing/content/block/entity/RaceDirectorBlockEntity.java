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
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;

public class RaceDirectorBlockEntity extends BlockEntity implements MenuProvider {
    private final RaceMonitorType monitorType;
    private int leftTeamCarId = -1;
    private int rightTeamCarId = -1;

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

    public int getLeftTeamCarId() {
        return leftTeamCarId;
    }

    public int getRightTeamCarId() {
        return rightTeamCarId;
    }

    public void setTeamCarId(int side, int entityId) {
        if (side == 0) {
            leftTeamCarId = entityId;
        } else if (side == 1) {
            rightTeamCarId = entityId;
        }
        setChanged();
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putInt("LeftTeamCarId", leftTeamCarId);
        output.putInt("RightTeamCarId", rightTeamCarId);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        leftTeamCarId = input.getIntOr("LeftTeamCarId", -1);
        rightTeamCarId = input.getIntOr("RightTeamCarId", -1);
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
