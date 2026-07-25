package com.openwheelracing.content.block;

import com.openwheelracing.registry.OWRFluids;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.world.level.redstone.Orientation;
import net.neoforged.neoforge.fluids.FluidInteractionRegistry;

public class CrudeOilBlock extends LiquidBlock {
    public CrudeOilBlock(Properties properties) {
        super(OWRFluids.CRUDE_OIL.get(), properties);
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean isMoving) {
        if (!reactToNeighbors(level, pos)) {
            scheduleFluidTick(state, level, pos);
        }
    }

    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block block, @org.jspecify.annotations.Nullable Orientation orientation, boolean isMoving) {
        if (!reactToNeighbors(level, pos)) {
            scheduleFluidTick(state, level, pos);
        }
    }

    public FlowingFluid getFluid() {
        return OWRFluids.CRUDE_OIL.get();
    }

    private void scheduleFluidTick(BlockState state, Level level, BlockPos pos) {
        if (!FluidInteractionRegistry.canInteract(level, pos)) {
            level.scheduleTick(pos, state.getFluidState().getType(), getFluid().getTickDelay(level));
        }
    }

    private static boolean reactToNeighbors(LevelAccessor level, BlockPos pos) {
        if (level.isClientSide()) {
            return false;
        }

        for (Direction direction : Direction.values()) {
            BlockState neighbor = level.getBlockState(pos.relative(direction));
            if (neighbor.is(Blocks.WATER)) {
                level.setBlock(pos, Blocks.WATER.defaultBlockState(), Block.UPDATE_ALL);
                return true;
            }
            if (isIgnitedBy(neighbor) && level instanceof ServerLevel serverLevel) {
                serverLevel.explode(null, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 3.0f, Level.ExplosionInteraction.BLOCK);
                return true;
            }
        }
        return false;
    }

    private static boolean isIgnitedBy(BlockState state) {
        return state.is(Blocks.FIRE)
            || state.is(Blocks.SOUL_FIRE)
            || state.is(Blocks.LAVA)
            || state.is(Blocks.MAGMA_BLOCK);
    }
}
