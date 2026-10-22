package com.openwheelracing.content.weather;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.permissions.Permissions;
import com.openwheelracing.network.RegionalWeatherPayload;

public final class WeatherCommands {
    private WeatherCommands() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("owrweather")
            .then(Commands.literal("forecast").executes(context -> {
                var source = context.getSource();
                if (RegionalWeather.enabled(source.getLevel())) {
                    for (var line : RegionalWeather.forecastLines(source.getLevel(), BlockPos.containing(source.getPosition()))) {
                        source.sendSuccess(() -> line, false);
                    }
                } else {
                    source.sendFailure(Component.translatable("weather.openwheelracing.unavailable"));
                }
                return 1;
            }))
            .then(Commands.literal("list").executes(context -> {
                for (var region : RegionalWeather.regions(context.getSource().getLevel())) {
                    context.getSource().sendSuccess(() -> Component.literal(region.toString()), false);
                }
                return 1;
            }))
            .then(Commands.literal("remove").requires(source -> source.permissions().hasPermission(Permissions.COMMANDS_MODERATOR))
                .then(Commands.argument("name", StringArgumentType.word()).executes(context -> {
                    boolean removed = RegionalWeatherData.get(context.getSource().getLevel()).remove(StringArgumentType.getString(context, "name"));
                    sync(context);
                    context.getSource().sendSuccess(() -> Component.literal(removed ? "Weather region removed." : "Weather region not found."), true);
                    return removed ? 1 : 0;
                })))
            .then(Commands.literal("set").requires(source -> source.permissions().hasPermission(Permissions.COMMANDS_MODERATOR))
                .then(Commands.argument("name", StringArgumentType.word())
                    .then(Commands.argument("minX", IntegerArgumentType.integer(-30000000, 30000000))
                        .then(Commands.argument("minZ", IntegerArgumentType.integer(-30000000, 30000000))
                            .then(Commands.argument("maxX", IntegerArgumentType.integer(-30000000, 30000000))
                                .then(Commands.argument("maxZ", IntegerArgumentType.integer(-30000000, 30000000))
                                    .then(Commands.argument("wetChance", DoubleArgumentType.doubleArg(0, 1))
                                        .then(Commands.argument("stormChance", DoubleArgumentType.doubleArg(0, 1))
                                            .then(Commands.argument("snowChance", DoubleArgumentType.doubleArg(0, 1))
                                                .then(Commands.argument("periodSeconds", IntegerArgumentType.integer(60, 3600))
                                                    .executes(WeatherCommands::set))))))))))));
    }

    private static int set(CommandContext<CommandSourceStack> context) {
        var source = context.getSource();
        String name = StringArgumentType.getString(context, "name");
        try {
            if (name.equals("default")) throw new IllegalArgumentException("The name default is reserved.");
            var region = new WeatherRegion(name, integer(context, "minX"), integer(context, "minZ"),
                integer(context, "maxX"), integer(context, "maxZ"), DoubleArgumentType.getDouble(context, "wetChance"),
                DoubleArgumentType.getDouble(context, "stormChance"), DoubleArgumentType.getDouble(context, "snowChance"),
                integer(context, "periodSeconds") * 20, source.getLevel().getSeed() ^ name.hashCode());
            RegionalWeatherData.get(source.getLevel()).put(region);
            sync(context);
            source.sendSuccess(() -> Component.literal("Weather region saved: " + name), true);
            return 1;
        } catch (IllegalArgumentException exception) {
            source.sendFailure(Component.literal(exception.getMessage()));
            return 0;
        }
    }

    private static int integer(CommandContext<CommandSourceStack> context, String name) {
        return IntegerArgumentType.getInteger(context, name);
    }

    private static void sync(CommandContext<CommandSourceStack> context) {
        for (var player : context.getSource().getLevel().players()) RegionalWeatherPayload.send(player);
    }
}
