package com.openwheelracing.content.block;

import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.EnumProperty;

public interface WettableTrack {
    EnumProperty<TrackMoisture> MOISTURE = EnumProperty.create("moisture", TrackMoisture.class);

    static TrackMoisture moisture(BlockState state) {
        return state.hasProperty(MOISTURE) ? state.getValue(MOISTURE) : TrackMoisture.DRY;
    }
}
