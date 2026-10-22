package com.openwheelracing.mixin;

import com.openwheelracing.content.weather.RegionalWeather;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Level.class)
public abstract class LevelWeatherMixin {
    @Inject(method = "precipitationAt", at = @At("HEAD"), cancellable = true)
    private void openwheelracing$precipitation(BlockPos pos, CallbackInfoReturnable<Biome.Precipitation> cir) {
        Level level = (Level) (Object) this;
        if (RegionalWeather.enabled(level)) cir.setReturnValue(RegionalWeather.exposed(level, pos)
            ? RegionalWeather.precipitation(level, pos) : Biome.Precipitation.NONE);
    }

    @Inject(method = "getRainLevel", at = @At("HEAD"), cancellable = true)
    private void openwheelracing$rain(float partialTick, CallbackInfoReturnable<Float> cir) {
        Level level = (Level) (Object) this;
        if (RegionalWeather.enabled(level)) cir.setReturnValue(RegionalWeather.rainLevel(level, partialTick));
    }

    @Inject(method = "getThunderLevel", at = @At("HEAD"), cancellable = true)
    private void openwheelracing$thunder(float partialTick, CallbackInfoReturnable<Float> cir) {
        Level level = (Level) (Object) this;
        if (RegionalWeather.enabled(level)) cir.setReturnValue(RegionalWeather.thunderLevel(level, partialTick));
    }
}
