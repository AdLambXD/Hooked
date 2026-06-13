package com.hooked.spawn.impl;

import com.hooked.config.ConfigManager;
import com.hooked.constants.Constants;
import com.hooked.events.DebrisSpawnEvent;
import com.hooked.manager.IDebrisManager;
import com.hooked.model.Debris;
import com.hooked.model.DebrisType;
import com.hooked.spawn.ISpawnController;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Vector;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Logger;

public final class SpawnControllerImpl implements ISpawnController {

    private final JavaPlugin plugin;
    private final Logger logger;
    private final ConfigManager configManager;
    private final IDebrisManager debrisManager;
    private final Random random = new Random();
    private boolean running;

    public SpawnControllerImpl(final JavaPlugin plugin, final ConfigManager configManager,
                               final IDebrisManager debrisManager) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
        this.configManager = configManager;
        this.debrisManager = debrisManager;
    }

    @Override
    public void start() {
        running = true;
        final long intervalTicks = configManager.getSpawnIntervalSeconds() * 20L;
        Bukkit.getGlobalRegionScheduler().runAtFixedRate(plugin, task -> {
            try {
                if (!running) {
                    task.cancel();
                    return;
                }
                tickSpawn();
            } catch (final Exception e) {
                logger.warning("Spawn tick error: " + e.getMessage());
            }
        }, intervalTicks, intervalTicks);
    }

    @Override
    public void stop() {
        running = false;
    }

    private void tickSpawn() {
        final int maxPerPlayer = configManager.getMaxPerPlayer();
        final int radius = configManager.getSpawnDistanceMax();
        final List<DebrisType> types = configManager.getDebrisTypes();
        if (types.isEmpty()) return;

        final int totalWeight = types.stream().mapToInt(DebrisType::getWeight).sum();

        for (final Player player : Bukkit.getOnlinePlayers()) {
            final World world = player.getWorld();
            if (world.getEnvironment() != World.Environment.NORMAL) continue;

            final Location northLoc = player.getLocation().clone().add(0, 0, -96);
            final int current = debrisManager.countNearby(northLoc, radius);
            final int deficit = maxPerPlayer - current;
            if (deficit <= 0) continue;

            for (int i = 0; i < Math.min(deficit, 3); i++) {
                final DebrisType type = selectType(types, totalWeight);
                if (type == null) continue;
                scheduleSpawnForPlayer(player, type);
            }
        }
    }

    private DebrisType selectType(final List<DebrisType> types, final int totalWeight) {
        int roll = random.nextInt(totalWeight);
        for (final DebrisType type : types) {
            roll -= type.getWeight();
            if (roll < 0) return type;
        }
        return types.get(0);
    }

    private void scheduleSpawnForPlayer(final Player player, final DebrisType type) {
        player.getScheduler().run(plugin, task -> {
            try {
                trySpawnOnRegion(player, type);
            } catch (final Exception e) {
                logger.warning("Failed to spawn debris: " + e.getMessage());
            }
        }, null);
    }

    private void trySpawnOnRegion(final Player player, final DebrisType type) {
        final Location playerLoc = player.getLocation();
        final World world = playerLoc.getWorld();

        for (int attempt = 0; attempt < Constants.MAX_SPAWN_ATTEMPTS; attempt++) {
            final Location spawnLoc = findSpawnLocation(playerLoc, world);
            if (spawnLoc == null) continue;

            if (!isLocationValid(spawnLoc, player)) continue;

            spawnDebris(spawnLoc, type, player);
            return;
        }
    }

    private Location findSpawnLocation(final Location playerLoc, final World world) {
        final double yOffset = configManager.getYLevelOffset();

        final double x = playerLoc.getX() + random.nextDouble(-16, 16);
        final double z = playerLoc.getZ() - 96;

        final int seaLevel = world.getSeaLevel();
        final int blockX = (int) Math.floor(x);
        final int blockZ = (int) Math.floor(z);

        for (int y = seaLevel; y >= seaLevel - 10; y--) {
            final Material mat = world.getBlockAt(blockX, y, blockZ).getType();
            if (mat == Material.WATER) {
                final Material above = world.getBlockAt(blockX, y + 1, blockZ).getType();
                if (above == Material.AIR || above == Material.CAVE_AIR || above == Material.VOID_AIR) {
                    return new Location(world, x, y + yOffset, z);
                }
            }
        }
        return null;
    }

    private boolean isLocationValid(final Location loc, final Player player) {
        final World world = loc.getWorld();
        final int bx = loc.getBlockX();
        final int by = loc.getBlockY();
        final int bz = loc.getBlockZ();

        final Material below = world.getBlockAt(bx, (int) Math.floor(loc.getY()) - 1, bz).getType();
        if (below != Material.WATER) return false;

        for (int dx = -Constants.OBSTACLE_CHECK_RADIUS; dx <= Constants.OBSTACLE_CHECK_RADIUS; dx++) {
            for (int dy = 0; dy <= 2; dy++) {
                for (int dz = -Constants.OBSTACLE_CHECK_RADIUS; dz <= Constants.OBSTACLE_CHECK_RADIUS; dz++) {
                    final Material mat = world.getBlockAt(bx + dx, by + dy, bz + dz).getType();
                    if (mat != Material.WATER && mat != Material.AIR
                        && mat != Material.CAVE_AIR && mat != Material.VOID_AIR) {
                        return false;
                    }
                }
            }
        }

        if (configManager.isIntegrationWorldGuard() && !canBuildWG(player, loc)) return false;
        if (configManager.isIntegrationBentoBox() && !canBuildBSkyBlock(player, loc)) return false;

        return true;
    }

    private boolean canBuildWG(final Player player, final Location loc) {
        final Plugin wg = Bukkit.getPluginManager().getPlugin("WorldGuard");
        if (wg == null || !wg.isEnabled()) return true;

        try {
            final Class<?> wgClass = Class.forName("com.sk89q.worldguard.WorldGuard");
            final Object wgInstance = wgClass.getMethod("getInstance").invoke(null);
            final Object platform = wgInstance.getClass().getMethod("getPlatform").invoke(wgInstance);
            final Object container = platform.getClass().getMethod("getRegionContainer").invoke(platform);
            final Object regionManager = container.getClass()
                .getMethod("get", org.bukkit.World.class).invoke(container, loc.getWorld());
            if (regionManager == null) return true;

            final Class<?> bv3 = Class.forName("com.sk89q.worldedit.util.BlockVector3");
            final Object vec = bv3.getMethod("at", double.class, double.class, double.class)
                .invoke(null, (double) loc.getBlockX(), (double) loc.getBlockY(), (double) loc.getBlockZ());
            final Object regions = regionManager.getClass()
                .getMethod("getApplicableRegions", bv3).invoke(regionManager, vec);

            final Object buildFlag = Class.forName("com.sk89q.worldguard.protection.flags.Flags")
                .getField("BUILD").get(null);

            return (boolean) regions.getClass()
                .getMethod("testState", Object.class, buildFlag.getClass())
                .invoke(regions, null, buildFlag);
        } catch (final ClassNotFoundException e) {
            return true;
        } catch (final Exception e) {
            if (configManager.isDebug()) {
                logger.fine("WorldGuard check error: " + e.getMessage());
            }
            return true;
        }
    }

    private boolean canBuildBSkyBlock(final Player player, final Location loc) {
        final Plugin bs = Bukkit.getPluginManager().getPlugin("BentoBox");
        if (bs == null || !bs.isEnabled()) return true;

        try {
            final Class<?> bentoClass = Class.forName("world.bentobox.bentobox.BentoBox");
            final Object instance = bentoClass.getMethod("getInstance").invoke(null);
            final Object islands = instance.getClass().getMethod("getIslands").invoke(instance);
            final Object island = islands.getClass().getMethod("getIslandAt", Location.class)
                .invoke(islands, loc);
            if (island == null) return true;

            final Object members = island.getClass().getMethod("getMemberSet").invoke(island);
            return (boolean) members.getClass().getMethod("contains", Object.class)
                .invoke(members, player.getUniqueId());
        } catch (final ClassNotFoundException e) {
            return true;
        } catch (final Exception e) {
            if (configManager.isDebug()) {
                logger.fine("BentoBox check error: " + e.getMessage());
            }
            return true;
        }
    }

    private void spawnDebris(final Location loc, final DebrisType type, final Player player) {
        final DebrisSpawnEvent event = new DebrisSpawnEvent(type, loc, player);
        Bukkit.getPluginManager().callEvent(event);
        if (event.isCancelled()) return;

        final BlockDisplay entity = (BlockDisplay) loc.getWorld().spawnEntity(
            event.getLocation(), EntityType.BLOCK_DISPLAY);
        entity.setBlock(type.getBlockMaterial().createBlockData());
        entity.addScoreboardTag(Constants.ENTITY_TAG);
        entity.setPersistent(false);

        final Interaction interaction = (Interaction) loc.getWorld().spawnEntity(
            event.getLocation(), EntityType.INTERACTION);
        interaction.setInteractionWidth(1.0f);
        interaction.setInteractionHeight(1.0f);
        interaction.addScoreboardTag(Constants.ENTITY_TAG);
        interaction.setPersistent(false);

        final double angleRad = Math.toRadians(random.nextDouble(-5.0, 5.0));
        final Vector driftDir = new Vector(-Math.sin(angleRad), 0, Math.cos(angleRad)).normalize();

        final Debris debris = new Debris(entity.getUniqueId(), type, entity.getLocation(), driftDir);
        debris.setEntity(entity);
        debris.setInteractionEntity(interaction);
        debrisManager.addDebris(debris);

        if (configManager.isDebug()) {
            logger.fine("Spawned debris [" + type.getId() + "] #" + debris.getDebugId()
                + " at (" + loc.getBlockX() + ", " + loc.getBlockY() + ", " + loc.getBlockZ() + ")"
                + " entityUUID: " + entity.getUniqueId());
        }
    }
}
