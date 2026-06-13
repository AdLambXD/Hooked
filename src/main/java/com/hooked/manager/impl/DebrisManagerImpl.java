package com.hooked.manager.impl;

import com.hooked.config.ConfigManager;
import com.hooked.constants.Constants;
import com.hooked.manager.IDebrisManager;
import com.hooked.model.ChunkKey;
import com.hooked.model.Debris;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityRemoveEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Logger;

public final class DebrisManagerImpl implements IDebrisManager, Listener {

    private final JavaPlugin plugin;
    private final Logger logger;
    private final ConfigManager configManager;
    private final Map<ChunkKey, List<Debris>> chunkMap = new ConcurrentHashMap<>();
    private final Map<UUID, Debris> entityIdMap = new ConcurrentHashMap<>();

    public DebrisManagerImpl(final JavaPlugin plugin, final ConfigManager configManager) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
        this.configManager = configManager;
        Bukkit.getPluginManager().registerEvents(this, plugin);
        startCleanupTask();
    }

    private void startCleanupTask() {
        Bukkit.getGlobalRegionScheduler().runAtFixedRate(plugin, task -> {
            try {
                cleanupTimedOutHooks();
                removeDistantDebris();
            } catch (final Exception e) {
                logger.warning("Cleanup task error: " + e.getMessage());
            }
        }, Constants.CLEANUP_TICK_INTERVAL, Constants.CLEANUP_TICK_INTERVAL);
    }

    private void cleanupTimedOutHooks() {
        final long now = System.currentTimeMillis();
        for (final Debris debris : getAllDebris()) {
            if (debris.isHookTimedOut()) {
                debris.releaseHook();
                if (configManager.isDebug()) {
                    logger.fine("Hook timeout released debris #" + debris.getDebugId());
                }
            }
        }
    }

    private void removeDistantDebris() {
        final int maxDist = configManager.getDespawnDistance();
        final List<UUID> toRemove = new ArrayList<>();
        for (final Debris debris : getAllDebris()) {
            boolean anyPlayerNearby = false;
            for (final org.bukkit.entity.Player player : Bukkit.getOnlinePlayers()) {
                if (player.getWorld().equals(debris.getSpawnLocation().getWorld())
                    && player.getLocation().distanceSquared(debris.getSpawnLocation()) < maxDist * maxDist) {
                    anyPlayerNearby = true;
                    break;
                }
            }
            if (!anyPlayerNearby) {
                toRemove.add(debris.getEntityId());
            }
        }
        for (final UUID id : toRemove) {
            removeDebris(id);
            if (configManager.isDebug()) {
                logger.fine("Removed distant debris " + id);
            }
        }
    }

    @Override
    public void addDebris(final Debris debris) {
        entityIdMap.put(debris.getEntityId(), debris);
        final BlockDisplay entity = debris.getEntity();
        if (entity != null) {
            final ChunkKey key = ChunkKey.fromLocation(entity.getLocation());
            chunkMap.computeIfAbsent(key, k -> new CopyOnWriteArrayList<>()).add(debris);
        }
    }

    @Override
    public void removeDebris(final UUID entityId) {
        final Debris debris = entityIdMap.remove(entityId);
        if (debris == null) return;

        final BlockDisplay entity = debris.getEntity();
        if (entity != null && entity.isValid()) {
            try {
                entity.getScheduler().run(plugin, task -> entity.remove(), null);
            } catch (final Exception e) {
                if (entity.isValid()) entity.remove();
            }
        }

        final org.bukkit.entity.Interaction interaction = debris.getInteractionEntity();
        if (interaction != null && interaction.isValid()) {
            try {
                interaction.getScheduler().run(plugin, task -> interaction.remove(), null);
            } catch (final Exception e) {
                if (interaction.isValid()) interaction.remove();
            }
        }

        for (final Map.Entry<ChunkKey, List<Debris>> entry : chunkMap.entrySet()) {
            final List<Debris> list = entry.getValue();
            list.remove(debris);
            if (list.isEmpty()) {
                chunkMap.remove(entry.getKey());
            }
        }

        if (configManager.isDebug()) {
            logger.fine("Removed debris #" + debris.getDebugId() + " entityId: " + entityId);
        }
    }

    @Override
    public Debris getDebris(final UUID entityId) {
        return entityIdMap.get(entityId);
    }

    @Override
    public Debris findNearestHookable(final Location hookLocation, final double radius) {
        Debris nearest = null;
        double nearestDistSq = radius * radius;

        for (final Debris debris : getAllDebris()) {
            if (debris.isHooked()) continue;

            final BlockDisplay entity = debris.getEntity();
            if (entity == null || !entity.isValid()) continue;

            final double distSq = entity.getLocation().distanceSquared(hookLocation);
            if (distSq < nearestDistSq) {
                nearestDistSq = distSq;
                nearest = debris;
            }
        }
        return nearest;
    }

    @Override
    public Collection<Debris> getAllDebris() {
        return Collections.unmodifiableCollection(entityIdMap.values());
    }

    @Override
    public int countNearby(final Location playerLocation, final int radius) {
        int count = 0;
        final double radiusSq = radius * radius;
        for (final Debris debris : getAllDebris()) {
            final BlockDisplay entity = debris.getEntity();
            if (entity == null || !entity.isValid()) continue;
            if (!entity.getWorld().equals(playerLocation.getWorld())) continue;
            if (entity.getLocation().distanceSquared(playerLocation) < radiusSq) {
                count++;
            }
        }
        return count;
    }

    @Override
    public void clearAll() {
        for (final UUID id : new ArrayList<>(entityIdMap.keySet())) {
            removeDebris(id);
        }
    }

    @EventHandler
    public void onEntityRemove(final EntityRemoveEvent event) {
        if (!(event.getEntity() instanceof final BlockDisplay entity)) return;
        if (!entity.getScoreboardTags().contains(Constants.ENTITY_TAG)) return;

        final UUID entityId = entity.getUniqueId();
        final Debris debris = entityIdMap.get(entityId);
        if (debris != null) {
            entityIdMap.remove(entityId);
            final ChunkKey key = ChunkKey.fromLocation(entity.getLocation());
            final List<Debris> list = chunkMap.get(key);
            if (list != null) {
                list.remove(debris);
                if (list.isEmpty()) {
                    chunkMap.remove(key);
                }
            }
            if (configManager.isDebug()) {
                logger.fine("Debris entity removed externally: #" + debris.getDebugId());
            }
        }
    }
}
