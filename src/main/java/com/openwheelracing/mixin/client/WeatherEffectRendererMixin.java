package com.openwheelracing.mixin.client;

import com.openwheelracing.content.weather.RegionalWeather;
import net.minecraft.client.renderer.WeatherEffectRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(WeatherEffectRenderer.class)
public abstract class WeatherEffectRendererMixin {
    @Inject(method = "getPrecipitationAt", at = @At("HEAD"), cancellable = true)
    private void openwheelracing$regionalPrecipitation(Level level, BlockPos pos, CallbackInfoReturnable<Biome.Precipitation> cir) {
        if (RegionalWeather.enabled(level)) cir.setReturnValue(RegionalWeather.visualPrecipitation());
    }
}
