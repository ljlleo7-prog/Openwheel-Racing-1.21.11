package com.openwheelracing.content.block;

import com.openwheelracing.OpenwheelRacing;
import com.openwheelracing.content.weather.RegionalWeatherData;
import com.openwheelracing.content.weather.WeatherRegion;
import com.openwheelracing.registry.OWRBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Consumer;

/** In-world regression coverage for the background weather sweep. */
public final class TrackWeatherGameTests {
    private static final DeferredRegister<Consumer<GameTestHelper>> FUNCTIONS =
        DeferredRegister.create(Registries.TEST_FUNCTION, OpenwheelRacing.MODID);

    static {
        FUNCTIONS.register("natural_drying", () -> TrackWeatherGameTests::naturalDrying);
    }

    private TrackWeatherGameTests() {
    }

    public static void register(IEventBus bus) {
        FUNCTIONS.register(bus);
    }

    private static void naturalDrying(GameTestHelper helper) {
        var level = helper.getLevel();
        BlockPos wet = helper.absolutePos(new BlockPos(1, 2, 1));
        BlockPos carDried = wet.east();
        String regionName = "drying_regression";
        RegionalWeatherData.get(level).put(new WeatherRegion(regionName, wet.getX() - 1, wet.getZ() - 1,
            wet.getX() + 2, wet.getZ() + 1, 0, 0, 0, 1200, 42));
        level.setBlock(wet, OWRBlocks.ASPHALT_TRACK.get().defaultBlockState().setValue(WettableTrack.MOISTURE, TrackMoisture.WET), 3);
        level.setBlock(carDried, OWRBlocks.ASPHALT_TRACK.get().defaultBlockState(), 3);
        helper.runAfterDelay(240, () -> {
            RegionalWeatherData.get(level).remove(regionName);
            helper.assertTrue(WettableTrack.moisture(level.getBlockState(wet)) == TrackMoisture.DRY,
                "Loaded wet track must dry through the natural sweep without tyre contact");
            helper.assertTrue(WettableTrack.moisture(level.getBlockState(carDried)) == TrackMoisture.DRY,
                "Natural drying must preserve the already dry line");
            helper.succeed();
        });
    }
}
