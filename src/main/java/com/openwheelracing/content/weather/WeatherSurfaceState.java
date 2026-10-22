package com.openwheelracing.content.weather;

/** Persistent moisture history begins when a climate becomes active, not at world tick zero. */
public record WeatherSurfaceState(WeatherRegion region, long updatedAt, double progress, long epoch,
                                  double progressAtEpoch, boolean raining, boolean thundering) {
    public static WeatherSurfaceState initial(WeatherRegion region, long tick) {
        var weather = WeatherSchedule.sample(region, tick);
        double progress = weather.wet() ? 0 : -0.5;
        return new WeatherSurfaceState(region, tick, progress, tick, progress, weather.wet(), weather.thunder());
    }

    public WeatherSurfaceState advance(long tick) {
        if (tick < updatedAt) return this;
        long cursor = updatedAt;
        WeatherSurfaceState state = this;
        while (cursor < tick) {
            var weather = WeatherSchedule.sample(region, cursor);
            state = state.align(weather, cursor);
            long end = Math.min(tick, weather.endsAt());
            state = new WeatherSurfaceState(region, end,
                WeatherSurfaceModel.advance(state.progress, weather, end - cursor),
                state.epoch, state.progressAtEpoch, weather.wet(), weather.thunder());
            cursor = end;
        }
        return state.align(WeatherSchedule.sample(region, tick), tick);
    }

    public WeatherSurfaceState reconfigure(WeatherRegion replacement, long tick) {
        var previous = advance(tick);
        var weather = WeatherSchedule.sample(replacement, tick);
        return new WeatherSurfaceState(replacement, tick, previous.progress, previous.epoch,
            previous.progressAtEpoch, previous.raining, previous.thundering).align(weather, tick);
    }

    private WeatherSurfaceState align(WeatherSchedule.Sample weather, long tick) {
        if (raining == weather.wet() && thundering == weather.thunder()) return this;
        double nextProgress = weather.wet() ? Math.max(0, progress) : progress;
        return new WeatherSurfaceState(region, tick, nextProgress, tick, nextProgress, weather.wet(), weather.thunder());
    }
}
