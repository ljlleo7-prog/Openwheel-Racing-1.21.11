package com.openwheelracing.content.track;

import com.openwheelracing.registry.OWRBlocks;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;

public enum TrackEditorMaterial {
    ASPHALT(false),
    PIT_LANE(false),
    WHITE_CONCRETE(false),
    LIGHT_GRAY_CONCRETE(false),
    GRAY_CONCRETE(false),
    BLACK_CONCRETE(false),
    RED_CONCRETE(false),
    CYAN_CONCRETE(false),
    BLUE_CONCRETE(false),
    SAND(false),
    GRASS(false),
    DIRT(false),
    GRAVEL(false),
    KERB(true),
    BARRIER(true),
    SANDSTONE(false);

    private final boolean edge;

    TrackEditorMaterial(boolean edge) {
        this.edge = edge;
    }

    public boolean isEdge() {
        return edge;
    }

    public BlockState state(Direction facing) {
        Block block = switch (this) {
            case ASPHALT -> OWRBlocks.ASPHALT_TRACK.get();
            case PIT_LANE -> OWRBlocks.PIT_LANE.get();
            case WHITE_CONCRETE -> Blocks.WHITE_CONCRETE;
            case LIGHT_GRAY_CONCRETE -> Blocks.LIGHT_GRAY_CONCRETE;
            case GRAY_CONCRETE -> Blocks.GRAY_CONCRETE;
            case BLACK_CONCRETE -> Blocks.BLACK_CONCRETE;
            case RED_CONCRETE -> Blocks.RED_CONCRETE;
            case CYAN_CONCRETE -> Blocks.CYAN_CONCRETE;
            case BLUE_CONCRETE -> Blocks.BLUE_CONCRETE;
            case SAND -> Blocks.SAND;
            case GRASS -> Blocks.GRASS_BLOCK;
            case DIRT -> Blocks.DIRT;
            case GRAVEL -> Blocks.GRAVEL;
            case KERB -> OWRBlocks.KERB.get();
            case BARRIER -> OWRBlocks.BARRIER.get();
            case SANDSTONE -> Blocks.SANDSTONE;
        };
        BlockState state = block.defaultBlockState();
        if (state.hasProperty(HorizontalDirectionalBlock.FACING)) {
            return state.setValue(HorizontalDirectionalBlock.FACING, facing);
        }
        return state;
    }
}
