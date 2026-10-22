package com.openwheelracing.mixin;

import com.openwheelracing.content.weather.RegionalWeather;
import com.openwheelracing.content.weather.WeatherSchedule;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.levelgen.Heightmap;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerLevel.class)
public abstract class ServerWeatherMixin {
    @Inject(method = "advanceWeatherCycle", at = @At("HEAD"), cancellable = true)
    private void openwheelracing$scheduledWeather(CallbackInfo ci) {
        if (RegionalWeather.enabled((ServerLevel) (Object) this)) ci.cancel();
    }

    @Redirect(method = "tickThunder", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerLevel;isRainingAt(Lnet/minecraft/core/BlockPos;)Z"))
    private boolean openwheelracing$localLightning(ServerLevel level, BlockPos pos) {
        return RegionalWeather.enabled(level) ? RegionalWeather.sample(level, pos).thunder()
            && RegionalWeather.exposed(level, pos) : level.isRainingAt(pos);
    }

    @Inject(method = "tickPrecipitation", at = @At("HEAD"), cancellable = true)
    private void openwheelracing$regionalSnow(BlockPos column, CallbackInfo ci) {
        ServerLevel level = (ServerLevel) (Object) this;
        if (!RegionalWeather.enabled(level)) return;
        ci.cancel();
        BlockPos pos = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING, column);
        if (!level.isAreaLoaded(pos, 1)) return;
        var weather = RegionalWeather.sample(level, pos);
        if (!weather.wet()) return;
        var state = level.getBlockState(pos);
        int maxLayers = Math.min(8, level.getGameRules().get(GameRules.MAX_SNOW_ACCUMULATION_HEIGHT));
        if (weather.condition() == WeatherSchedule.Condition.SNOW && maxLayers > 0) {
            if (state.is(Blocks.SNOW)) {
                int layers = state.getValue(SnowLayerBlock.LAYERS);
                if (layers < maxLayers) {
                    var next = state.setValue(SnowLayerBlock.LAYERS, layers + 1);
                    Block.pushEntitiesUp(state, next, level, pos);
                    level.setBlockAndUpdate(pos, next);
                }
            } else if (state.isAir() && Blocks.SNOW.defaultBlockState().canSurvive(level, pos)) {
                level.setBlockAndUpdate(pos, Blocks.SNOW.defaultBlockState());
            }
        }
        BlockPos ground = pos.below();
        var groundState = level.getBlockState(ground);
        groundState.getBlock().handlePrecipitation(groundState, level, ground, RegionalWeather.precipitation(level, pos));
    }
}
