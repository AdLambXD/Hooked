package com.hooked.config;

import com.hooked.model.DebrisType;
import com.hooked.model.LootEntry;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;
import java.util.logging.Logger;

public final class ConfigManager {

    private final JavaPlugin plugin;
    private final Logger logger;
    private FileConfiguration config;

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

        loadDebrisTypes();

        if (debug) {
            logger.info("Configuration loaded: " + debrisTypes.size() + " debris types, "
                + "max_per_player=" + maxPerPlayer + ", drift_speed=" + driftSpeed);
        }
    }

    private void loadDebrisTypes() {
        debrisTypes.clear();
        ConfigurationSection typesSection = config.getConfigurationSection("types");
        if (typesSection == null) {
            logger.warning("No 'types' section found in config, using defaults.");
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
                debrisTypes.add(new DebrisType(key, material, iaId.isEmpty() ? null : iaId, weight, loot));
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
    public List<DebrisType> getDebrisTypes() { return Collections.unmodifiableList(debrisTypes); }
}
