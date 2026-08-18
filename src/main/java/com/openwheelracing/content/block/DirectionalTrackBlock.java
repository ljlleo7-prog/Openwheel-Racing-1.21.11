package com.openwheelracing.content.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import org.jspecify.annotations.Nullable;

public class DirectionalTrackBlock extends HorizontalDirectionalBlock implements WettableTrack {
    public static final MapCodec<DirectionalTrackBlock> CODEC = simpleCodec(DirectionalTrackBlock::new);

    public DirectionalTrackBlock(Properties properties) {
        super(properties.randomTicks());
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(MOISTURE, TrackMoisture.DRY));
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<net.minecraft.world.level.block.Block, BlockState> builder) {
        builder.add(FACING, MOISTURE);
    }

    @Override
    protected void randomTick(BlockState state, net.minecraft.server.level.ServerLevel level, net.minecraft.core.BlockPos pos,
                              net.minecraft.util.RandomSource random) {
        WettableTrackWeather.randomTick(state, level, pos, random);
    }
}
