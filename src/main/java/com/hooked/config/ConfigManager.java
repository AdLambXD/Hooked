package com.hooked.config;

import com.hooked.model.DebrisType;
import com.hooked.model.LootEntry;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.logging.Logger;

public final class ConfigManager {

    private final JavaPlugin plugin;
    private final Logger logger;
    private FileConfiguration config;
    private File typesFile;
    private FileConfiguration typesConfig;

    private boolean debug;
    private int maxPerPlayer;
    private int spawnIntervalSeconds;
    private int spawnDistanceMin;
    private int spawnDistanceMax;
    private int despawnDistance;
    private int spawnNorthMin;
    private int spawnNorthMax;
    private int spawnXRange;
    private double driftSpeed;
    private double yLevelOffset;
    private double cooldownSeconds;
    private double hitRadius;
    private double grabAnimationSpeed;
    private boolean attackEnabled;
    private double attackCooldownSeconds;
    private boolean integrationItemsAdder;
    private boolean integrationWorldGuard;
    private boolean integrationBentoBox;
    private String driftAiClass;
    private String showLootMessage;
    private boolean environmentEnabled;
    private Map<String, Double> weatherSpawnMultipliers;
    private Map<String, Double> weatherDriftMultipliers;
    private Map<String, Double> moonSpawnMultipliers;
    private Map<String, Double> moonDriftMultipliers;
    private final List<DebrisType> debrisTypes = new ArrayList<>();

    public ConfigManager(final JavaPlugin plugin) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
    }

    public void load() {
        plugin.saveDefaultConfig();
        plugin.reloadConfig();
        this.config = plugin.getConfig();

        debug = getBoolean("debug", false);
        maxPerPlayer = getInt("debris.max_per_player", 25);
        spawnIntervalSeconds = getInt("debris.spawn_interval_seconds", 5);
        spawnDistanceMin = getInt("debris.spawn_distance_min", 16);
        spawnDistanceMax = getInt("debris.spawn_distance_max", 48);
        despawnDistance = getInt("debris.despawn_distance", 128);
        spawnNorthMin = getInt("debris.spawn_north_min", 48);
        spawnNorthMax = getInt("debris.spawn_north_max", 96);
        spawnXRange = getInt("debris.spawn_x_range", 24);
        driftSpeed = getDouble("debris.drift_speed", 1.25);
        yLevelOffset = getDouble("debris.y_level_offset", 0.2);
        cooldownSeconds = getDouble("hook.cooldown_seconds", 1.5);
        hitRadius = getDouble("hook.hit_radius", 2.0);
        grabAnimationSpeed = getDouble("hook.grab_animation_speed", 0.5);
        attackEnabled = getBoolean("attack.enabled", true);
        attackCooldownSeconds = getDouble("attack.cooldown_seconds", 0.5);
        integrationItemsAdder = getBoolean("integrations.itemsadder", true);
        integrationWorldGuard = getBoolean("integrations.worldguard", true);
        integrationBentoBox = getBoolean("integrations.bentobox", true);
        driftAiClass = getString("drift_ai_class", "");
        showLootMessage = getString("show_loot_message", "action_bar");

        loadEnvironmentConfig();
        loadTypesFile();
        loadDebrisTypes();

        if (debug) {
            logger.info("Configuration loaded: " + debrisTypes.size() + " debris types, "
                + "max_per_player=" + maxPerPlayer + ", drift_speed=" + driftSpeed);
        }
    }

    private void loadEnvironmentConfig() {
        environmentEnabled = getBoolean("environment.enabled", true);

        weatherSpawnMultipliers = new HashMap<>();
        weatherDriftMultipliers = new HashMap<>();
        for (final String weatherKey : List.of("clear", "rain", "thunder")) {
            weatherSpawnMultipliers.put(weatherKey,
                getDouble("environment.weather." + weatherKey + ".spawn_multiplier", 1.0));
            weatherDriftMultipliers.put(weatherKey,
                getDouble("environment.weather." + weatherKey + ".drift_multiplier", 1.0));
        }

        moonSpawnMultipliers = new HashMap<>();
        moonDriftMultipliers = new HashMap<>();
        for (final String moonKey : List.of("full_moon", "waning_gibbous", "last_quarter",
            "waning_crescent", "new_moon", "waxing_crescent",
            "first_quarter", "waxing_gibbous")) {
            moonSpawnMultipliers.put(moonKey,
                getDouble("environment.moon_phase." + moonKey + ".spawn_multiplier", 1.0));
            moonDriftMultipliers.put(moonKey,
                getDouble("environment.moon_phase." + moonKey + ".drift_multiplier", 1.0));
        }
    }

    private void loadTypesFile() {
        typesFile = new File(plugin.getDataFolder(), "types.yml");
        if (!typesFile.exists()) {
            plugin.saveResource("types.yml", false);
        }
        typesConfig = YamlConfiguration.loadConfiguration(typesFile);
    }

    private void loadDebrisTypes() {
        debrisTypes.clear();
        ConfigurationSection typesSection = typesConfig.getConfigurationSection("types");
        if (typesSection == null) {
            logger.warning("No 'types' section found in types.yml, using defaults.");
            addDefaultTypes();
            return;
        }

        for (final String key : typesSection.getKeys(false)) {
            try {
                final ConfigurationSection sec = typesSection.getConfigurationSection(key);
                if (sec == null) continue;

                final String blockName = sec.getString("block", "");
                final String iaId = sec.getString("itemsadder_id", "");
                final Material material = blockName.isEmpty() ? null : Material.getMaterial(blockName);
                if (material == null && iaId.isEmpty()) {
                    logger.warning("Debris type '" + key + "' has no block or itemsadder_id, skipping.");
                    continue;
                }

                final int weight = sec.getInt("weight", 10);
                final List<LootEntry> loot = loadLootEntries(sec, key);
                final Map<String, Double> envWeightMods = loadEnvWeightMods(sec);
                debrisTypes.add(new DebrisType(key, material, iaId.isEmpty() ? null : iaId, weight, loot, envWeightMods));
            } catch (final Exception e) {
                logger.warning("Failed to load debris type '" + key + "': " + e.getMessage());
            }
        }

        if (debrisTypes.isEmpty()) {
            logger.warning("No valid debris types loaded, using defaults.");
            addDefaultTypes();
        }
    }

    private List<LootEntry> loadLootEntries(final ConfigurationSection sec, final String typeKey) {
        final List<LootEntry> entries = new ArrayList<>();
        final List<Map<?, ?>> lootList = sec.getMapList("loot");
        for (final Map<?, ?> map : lootList) {
            final Object itemObj = map.get("item");
            final Object iaObj = map.get("itemsadder_id");
            final String itemName = itemObj != null ? String.valueOf(itemObj) : "";
            final String iaId = iaObj != null ? String.valueOf(iaObj) : "";
            if (itemName.isEmpty() && iaId.isEmpty()) continue;

            final Object minObj = map.get("min");
            final Object maxObj = map.get("max");
            final Object chanceObj = map.get("chance");
            final int min = minObj instanceof Number n ? n.intValue() : 1;
            final int max = maxObj instanceof Number n ? n.intValue() : 1;
            final double chance = chanceObj instanceof Number n ? n.doubleValue() : 100.0;
            entries.add(new LootEntry(itemName.isEmpty() ? null : itemName,
                                      iaId.isEmpty() ? null : iaId, min, max, chance));
        }
        return entries;
    }

    private Map<String, Double> loadEnvWeightMods(final ConfigurationSection sec) {
        final ConfigurationSection modSection = sec.getConfigurationSection("weight_mod");
        if (modSection == null) return Map.of();

        final Map<String, Double> mods = new HashMap<>();
        for (final String key : modSection.getKeys(false)) {
            mods.put(key, modSection.getDouble(key, 1.0));
        }
        return mods.isEmpty() ? Map.of() : Map.copyOf(mods);
    }

    private void addDefaultTypes() {
        debrisTypes.add(new DebrisType("plank", Material.OAK_PLANKS, null, 50, List.of(
            new LootEntry("OAK_PLANKS", null, 1, 3, 100.0))));
        debrisTypes.add(new DebrisType("barrel", Material.BARREL, null, 20, List.of(
            new LootEntry("STICK", null, 1, 4, 80.0),
            new LootEntry("STRING", null, 1, 1, 30.0))));
        debrisTypes.add(new DebrisType("leaves", Material.OAK_LEAVES, null, 30, List.of(
            new LootEntry("STICK", null, 1, 2, 70.0),
            new LootEntry("APPLE", null, 1, 1, 15.0))));
    }

    public void saveTypes() {
        typesConfig = new YamlConfiguration();
        for (final DebrisType type : debrisTypes) {
            final String path = "types." + type.getId();
            typesConfig.set(path + ".block", type.getBlockMaterial() != null ? type.getBlockMaterial().name() : "AIR");
            typesConfig.set(path + ".itemsadder_id", type.getItemsAdderId() != null ? type.getItemsAdderId() : "");
            typesConfig.set(path + ".weight", type.getWeight());

            final List<Map<String, Object>> lootList = new ArrayList<>();
            for (final LootEntry loot : type.getLootTable()) {
                final Map<String, Object> lootMap = new LinkedHashMap<>();
                lootMap.put("item", loot.itemMaterial() != null ? loot.itemMaterial() : "");
                lootMap.put("itemsadder_id", loot.itemsAdderId() != null ? loot.itemsAdderId() : "");
                lootMap.put("min", loot.min());
                lootMap.put("max", loot.max());
                lootMap.put("chance", loot.chance());
                lootList.add(lootMap);
            }
            typesConfig.set(path + ".loot", lootList);
            if (!type.getEnvWeightMods().isEmpty()) {
                for (final Map.Entry<String, Double> entry : type.getEnvWeightMods().entrySet()) {
                    typesConfig.set(path + ".weight_mod." + entry.getKey(), entry.getValue());
                }
            }
        }
        try {
            typesConfig.save(typesFile);
            if (debug) {
                logger.info("types.yml saved with " + debrisTypes.size() + " types.");
            }
        } catch (final IOException e) {
            logger.severe("Failed to save types.yml: " + e.getMessage());
        }
    }

    public DebrisType getDebrisType(final String id) {
        for (final DebrisType type : debrisTypes) {
            if (type.getId().equalsIgnoreCase(id)) return type;
        }
        return null;
    }

    public boolean addDebrisType(final DebrisType type) {
        if (getDebrisType(type.getId()) != null) return false;
        debrisTypes.add(type);
        saveTypes();
        return true;
    }

    public boolean removeDebrisType(final String id) {
        final DebrisType type = getDebrisType(id);
        if (type == null) return false;
        debrisTypes.remove(type);
        saveTypes();
        return true;
    }

    public boolean setDebrisTypeProperty(final String id, final String key, final String value) {
        final DebrisType current = getDebrisType(id);
        if (current == null) return false;

        final int idx = debrisTypes.indexOf(current);
        final Material blockMaterial;
        final String iaId;
        final int weight;
        final List<LootEntry> loot;
        final Map<String, Double> envWeightMods;

        switch (key.toLowerCase()) {
            case "block" -> {
                final Material mat = Material.getMaterial(value.toUpperCase());
                if (mat == null && (current.getItemsAdderId() == null || current.getItemsAdderId().isEmpty())) {
                    return false;
                }
                blockMaterial = mat;
                iaId = current.getItemsAdderId();
                weight = current.getWeight();
                loot = new ArrayList<>(current.getLootTable());
                envWeightMods = current.getEnvWeightMods();
            }
            case "weight" -> {
                try {
                    final int w = Integer.parseInt(value);
                    if (w <= 0) return false;
                    blockMaterial = current.getBlockMaterial();
                    iaId = current.getItemsAdderId();
                    weight = w;
                    loot = new ArrayList<>(current.getLootTable());
                    envWeightMods = current.getEnvWeightMods();
                } catch (final NumberFormatException e) {
                    return false;
                }
            }
            case "itemsadder_id" -> {
                final String newIaId = value.isEmpty() || value.equalsIgnoreCase("null") ? null : value;
                if (newIaId == null && current.getBlockMaterial() == null) return false;
                blockMaterial = current.getBlockMaterial();
                iaId = newIaId;
                weight = current.getWeight();
                loot = new ArrayList<>(current.getLootTable());
                envWeightMods = current.getEnvWeightMods();
            }
            default -> { return false; }
        }

        debrisTypes.set(idx, new DebrisType(id, blockMaterial, iaId, weight, loot, envWeightMods));
        saveTypes();
        return true;
    }

    public boolean addLootEntry(final String typeId, final LootEntry entry) {
        final DebrisType type = getDebrisType(typeId);
        if (type == null) return false;

        final int idx = debrisTypes.indexOf(type);
        final List<LootEntry> newLoot = new ArrayList<>(type.getLootTable());
        newLoot.add(entry);

        debrisTypes.set(idx, new DebrisType(type.getId(), type.getBlockMaterial(),
            type.getItemsAdderId(), type.getWeight(), newLoot, type.getEnvWeightMods()));
        saveTypes();
        return true;
    }

    public boolean removeLootEntry(final String typeId, final int lootIndex) {
        final DebrisType type = getDebrisType(typeId);
        if (type == null) return false;
        if (lootIndex < 0 || lootIndex >= type.getLootTable().size()) return false;

        final int idx = debrisTypes.indexOf(type);
        final List<LootEntry> newLoot = new ArrayList<>(type.getLootTable());
        newLoot.remove(lootIndex);

        debrisTypes.set(idx, new DebrisType(type.getId(), type.getBlockMaterial(),
            type.getItemsAdderId(), type.getWeight(), newLoot, type.getEnvWeightMods()));
        saveTypes();
        return true;
    }

    public boolean clearLoot(final String typeId) {
        final DebrisType type = getDebrisType(typeId);
        if (type == null) return false;

        final int idx = debrisTypes.indexOf(type);
        debrisTypes.set(idx, new DebrisType(type.getId(), type.getBlockMaterial(),
            type.getItemsAdderId(), type.getWeight(), List.of(), type.getEnvWeightMods()));
        saveTypes();
        return true;
    }

    private boolean getBoolean(final String path, final boolean def) {
        final Object val = config.get(path);
        if (val == null) {
            logger.warning("Config key '" + path + "' missing, using default: " + def);
            return def;
        }
        return config.getBoolean(path, def);
    }

    private int getInt(final String path, final int def) {
        final Object val = config.get(path);
        if (val == null) {
            logger.warning("Config key '" + path + "' missing, using default: " + def);
            return def;
        }
        return config.getInt(path, def);
    }

    private double getDouble(final String path, final double def) {
        final Object val = config.get(path);
        if (val == null) {
            logger.warning("Config key '" + path + "' missing, using default: " + def);
            return def;
        }
        return config.getDouble(path, def);
    }

    private String getString(final String path, final String def) {
        final Object val = config.get(path);
        if (val == null) {
            logger.warning("Config key '" + path + "' missing, using default: " + def);
            return def;
        }
        return config.getString(path, def);
    }

    public boolean isDebug() { return debug; }
    public int getMaxPerPlayer() { return maxPerPlayer; }
    public int getSpawnIntervalSeconds() { return spawnIntervalSeconds; }
    public int getSpawnDistanceMin() { return spawnDistanceMin; }
    public int getSpawnDistanceMax() { return spawnDistanceMax; }
    public int getDespawnDistance() { return despawnDistance; }
    public int getSpawnNorthMin() { return spawnNorthMin; }
    public int getSpawnNorthMax() { return spawnNorthMax; }
    public int getSpawnXRange() { return spawnXRange; }
    public double getDriftSpeed() { return driftSpeed; }
    public double getYLevelOffset() { return yLevelOffset; }
    public double getCooldownSeconds() { return cooldownSeconds; }
    public double getHitRadius() { return hitRadius; }
    public double getGrabAnimationSpeed() { return grabAnimationSpeed; }
    public boolean isAttackEnabled() { return attackEnabled; }
    public double getAttackCooldownSeconds() { return attackCooldownSeconds; }
    public boolean isIntegrationItemsAdder() { return integrationItemsAdder; }
    public boolean isIntegrationWorldGuard() { return integrationWorldGuard; }
    public boolean isIntegrationBentoBox() { return integrationBentoBox; }
    public String getDriftAiClass() { return driftAiClass; }
    public String getShowLootMessage() { return showLootMessage; }
    public List<DebrisType> getDebrisTypes() { return Collections.unmodifiableList(debrisTypes); }

    public boolean isEnvironmentEnabled() { return environmentEnabled; }

    public double getWeatherSpawnMultiplier(final String weather) {
        return weatherSpawnMultipliers.getOrDefault(weather, 1.0);
    }

    public double getWeatherDriftMultiplier(final String weather) {
        return weatherDriftMultipliers.getOrDefault(weather, 1.0);
    }

    public double getMoonSpawnMultiplier(final String moonPhase) {
        return moonSpawnMultipliers.getOrDefault(moonPhase, 1.0);
    }

    public double getMoonDriftMultiplier(final String moonPhase) {
        return moonDriftMultipliers.getOrDefault(moonPhase, 1.0);
    }
}
