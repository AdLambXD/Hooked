package com.hooked.environment;

import com.hooked.config.ConfigManager;
import com.hooked.model.DebrisType;
import org.bukkit.World;

import java.util.Map;

public final class EnvironmentUtil {

    private EnvironmentUtil() {}

    public enum Weather {
        CLEAR, RAIN, THUNDER;

        public static Weather fromWorld(final World world) {
            if (world.isThundering()) return THUNDER;
            if (world.hasStorm()) return RAIN;
            return CLEAR;
        }

        public String getConfigKey() {
            return name().toLowerCase();
        }
    }

    public enum MoonPhase {
        FULL_MOON, WANING_GIBBOUS, LAST_QUARTER, WANING_CRESCENT,
        NEW_MOON, WAXING_CRESCENT, FIRST_QUARTER, WAXING_GIBBOUS;

        public static MoonPhase fromWorld(final World world) {
            final long days = world.getFullTime() / 24000L;
            final int phase = (int) (days % 8);
            return values()[phase];
        }

        public String getConfigKey() {
            return name().toLowerCase();
        }
    }

    public static double getWeatherSpawnMultiplier(final World world, final ConfigManager config) {
        final Weather weather = Weather.fromWorld(world);
        return config.getWeatherSpawnMultiplier(weather.getConfigKey());
    }

    public static double getWeatherDriftMultiplier(final World world, final ConfigManager config) {
        final Weather weather = Weather.fromWorld(world);
        return config.getWeatherDriftMultiplier(weather.getConfigKey());
    }

    public static double getMoonSpawnMultiplier(final World world, final ConfigManager config) {
        final MoonPhase moon = MoonPhase.fromWorld(world);
        return config.getMoonSpawnMultiplier(moon.getConfigKey());
    }

    public static double getMoonDriftMultiplier(final World world, final ConfigManager config) {
        final MoonPhase moon = MoonPhase.fromWorld(world);
        return config.getMoonDriftMultiplier(moon.getConfigKey());
    }

    public static int getEffectiveMaxPerPlayer(final World world, final ConfigManager config) {
        final double multiplier = getWeatherSpawnMultiplier(world, config)
            * getMoonSpawnMultiplier(world, config);
        return (int) Math.round(config.getMaxPerPlayer() * multiplier);
    }

    public static double getEffectiveDriftSpeed(final World world, final ConfigManager config) {
        final double multiplier = getWeatherDriftMultiplier(world, config)
            * getMoonDriftMultiplier(world, config);
        return config.getDriftSpeed() * multiplier;
    }

    public static int getEffectiveTypeWeight(final DebrisType type, final World world,
                                              final ConfigManager config) {
        if (!config.isEnvironmentEnabled()) return type.getWeight();

        double weatherMod = 1.0;
        double moonMod = 1.0;

        final Map<String, Double> envMods = type.getEnvWeightMods();
        if (!envMods.isEmpty()) {
            final Weather weather = Weather.fromWorld(world);
            final MoonPhase moon = MoonPhase.fromWorld(world);

            final Double weatherModVal = envMods.get(weather.getConfigKey());
            if (weatherModVal != null) weatherMod = weatherModVal;

            final Double moonModVal = envMods.get(moon.getConfigKey());
            if (moonModVal != null) moonMod = moonModVal;
        }

        return (int) Math.round(type.getWeight() * weatherMod * moonMod);
    }
}
