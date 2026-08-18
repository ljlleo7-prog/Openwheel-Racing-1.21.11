package com.openwheelracing.content.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;

public class WettableTrackSlabBlock extends SlabBlock implements WettableTrack {
    public static final MapCodec<WettableTrackSlabBlock> CODEC = simpleCodec(WettableTrackSlabBlock::new);

    public WettableTrackSlabBlock(Properties properties) {
        super(properties.randomTicks());
        registerDefaultState(defaultBlockState().setValue(MOISTURE, TrackMoisture.DRY));
    }

    @Override
    public MapCodec<? extends SlabBlock> codec() {
        return CODEC;
    }

    @Override
    protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        WettableTrackWeather.randomTick(state, level, pos, random);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(MOISTURE);
    }
}
