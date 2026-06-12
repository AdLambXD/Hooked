package com.hooked.hook.impl;

import com.hooked.config.ConfigManager;
import com.hooked.constants.Constants;
import com.hooked.events.DebrisCollectEvent;
import com.hooked.events.DebrisHookAttemptEvent;
import com.hooked.events.DebrisHookSuccessEvent;
import com.hooked.hook.IHookHandler;
import com.hooked.loot.ILootGenerator;
import com.hooked.manager.IDebrisManager;
import com.hooked.model.Debris;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Vector;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

public final class HookHandlerImpl implements IHookHandler, Listener {

    private final JavaPlugin plugin;
    private final Logger logger;
    private final ConfigManager configManager;
    private final IDebrisManager debrisManager;
    private final ILootGenerator lootGenerator;
    private final Map<UUID, Location> hookLocationMap = new ConcurrentHashMap<>();
    private final Map<UUID, Long> cooldownMap = new ConcurrentHashMap<>();

    public HookHandlerImpl(final JavaPlugin plugin, final ConfigManager configManager,
                           final IDebrisManager debrisManager, final ILootGenerator lootGenerator) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
        this.configManager = configManager;
        this.debrisManager = debrisManager;
        this.lootGenerator = lootGenerator;
    }

    @Override
    public void start() {
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    @Override
    public void stop() {
        PlayerFishEvent.getHandlerList().unregister(this);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerFish(final PlayerFishEvent event) {
        final Player player = event.getPlayer();
        final UUID playerId = player.getUniqueId();

        switch (event.getState()) {
            case FISHING -> {
                final Location hookLoc = event.getHook().getLocation().clone();
                hookLocationMap.put(playerId, hookLoc);
            }
            case REEL_IN, CAUGHT_FISH -> {
                event.setCancelled(true);
                event.getHook().remove();

                if (isOnCooldown(playerId)) {
                    if (configManager.isDebug()) {
                        logger.fine("Player " + player.getName() + " hook on cooldown");
                    }
                    return;
                }

                final Location hookLoc = hookLocationMap.remove(playerId);
                if (hookLoc == null) return;

                final double radius = configManager.getHitRadius();
                final Debris debris = debrisManager.findNearestHookable(hookLoc, radius);

                final DebrisHookAttemptEvent attemptEvent = new DebrisHookAttemptEvent(player, debris, hookLoc);
                Bukkit.getPluginManager().callEvent(attemptEvent);
                if (attemptEvent.isCancelled()) return;

                if (debris == null) {
                    handleMiss(player);
                } else {
                    if (!debris.tryHook(playerId)) {
                        handleMiss(player);
                        if (configManager.isDebug()) {
                            logger.fine("Hook conflict: debris #" + debris.getDebugId()
                                + " already hooked by " + debris.getHookedBy());
                        }
                    } else {
                        handleHookSuccess(player, debris);
                    }
                }
            }
            default -> {}
        }
    }

    private boolean isOnCooldown(final UUID playerId) {
        final Long cooldownEnd = cooldownMap.get(playerId);
        if (cooldownEnd == null) return false;
        if (System.currentTimeMillis() > cooldownEnd) {
            cooldownMap.remove(playerId);
            return false;
        }
        return true;
    }

    private void setCooldown(final UUID playerId, final double seconds) {
        cooldownMap.put(playerId, System.currentTimeMillis() + (long)(seconds * 1000));
    }

    private void handleMiss(final Player player) {
        player.playSound(player.getLocation(), Sound.ENTITY_FISHING_BOBBER_SPLASH, 0.5f, 1.5f);
        setCooldown(player.getUniqueId(), 1.0);
        if (configManager.isDebug()) {
            logger.fine("Hook miss for player " + player.getName());
        }
    }

    private void handleHookSuccess(final Player player, final Debris debris) {
        final BlockDisplay entity = debris.getEntity();
        if (entity == null || !entity.isValid()) {
            debris.releaseHook();
            return;
        }

        player.playSound(player.getLocation(), Sound.ENTITY_FISHING_BOBBER_RETRIEVE, 1.0f, 1.0f);
        setCooldown(player.getUniqueId(), configManager.getCooldownSeconds());

        final DebrisHookSuccessEvent successEvent = new DebrisHookSuccessEvent(player, debris);
        Bukkit.getPluginManager().callEvent(successEvent);

        if (configManager.isDebug()) {
            logger.fine("Hook hit debris #" + debris.getDebugId() + " type=" + debris.getType().getId()
                + " player=" + player.getName());
        }

        startGrabAnimation(player, debris, entity);
    }

    private void startGrabAnimation(final Player player, final Debris debris, final BlockDisplay entity) {
        final double speed = configManager.getGrabAnimationSpeed();

        entity.getScheduler().runAtFixedRate(plugin, task -> {
            try {
                if (!entity.isValid() || !player.isOnline()) {
                    task.cancel();
                    debris.releaseHook();
                    return;
                }

                final Location debrisLoc = entity.getLocation();
                final Location targetLoc = player.getEyeLocation().add(
                    player.getLocation().getDirection().multiply(1.5));
                final Vector toTarget = targetLoc.toVector().subtract(debrisLoc.toVector());
                final double distance = toTarget.length();

                if (distance < Constants.GRAB_FINISH_DISTANCE) {
                    task.cancel();
                    finishCollection(player, debris, entity);
                    return;
                }

                final Vector moveVec = toTarget.normalize().multiply(speed);
                final Location newLoc = debrisLoc.clone().add(moveVec);
                newLoc.setDirection(toTarget.normalize());
                entity.teleport(newLoc);

                player.getWorld().spawnParticle(Particle.BUBBLE, debrisLoc, 3, 0.2, 0.2, 0.2, 0.02);

                if (configManager.isDebug()) {
                    logger.fine("Grab anim debris #" + debris.getDebugId()
                        + " dist=" + String.format("%.2f", distance)
                        + " pos=" + newLoc.getBlockX() + "," + newLoc.getBlockY() + "," + newLoc.getBlockZ());
                }
            } catch (final Exception e) {
                task.cancel();
                debris.releaseHook();
                logger.warning("Grab animation error: " + e.getMessage());
            }
        }, null, 1L, 1L);
    }

    private void finishCollection(final Player player, final Debris debris, final BlockDisplay entity) {
        player.getScheduler().run(plugin, task -> {
            final DebrisCollectEvent collectEvent = new DebrisCollectEvent(player, debris);
            Bukkit.getPluginManager().callEvent(collectEvent);
            if (collectEvent.isCancelled()) {
                debris.releaseHook();
                return;
            }

            lootGenerator.giveLoot(player, debris.getType());

            if (configManager.isDebug()) {
                logger.fine("Debris #" + debris.getDebugId() + " collected by " + player.getName());
            }
        }, null);

        debrisManager.removeDebris(debris.getEntityId());
    }
}
