package com.hooked.attack.impl;

import com.hooked.attack.IAttackHandler;
import com.hooked.config.ConfigManager;
import com.hooked.constants.Constants;
import com.hooked.events.DebrisCollectEvent;
import com.hooked.events.DebrisHookAttemptEvent;
import com.hooked.events.DebrisHookSuccessEvent;
import com.hooked.loot.ILootGenerator;
import com.hooked.manager.IDebrisManager;
import com.hooked.model.Debris;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

public final class AttackHandlerImpl implements IAttackHandler, Listener {

    private final JavaPlugin plugin;
    private final Logger logger;
    private final ConfigManager configManager;
    private final IDebrisManager debrisManager;
    private final ILootGenerator lootGenerator;
    private final Map<UUID, Long> cooldownMap = new ConcurrentHashMap<>();

    public AttackHandlerImpl(final JavaPlugin plugin, final ConfigManager configManager,
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
        EntityDamageByEntityEvent.getHandlerList().unregister(this);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityDamage(final EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof final Player player)) return;
        if (!(event.getEntity() instanceof final Interaction interaction)) return;
        if (!interaction.getScoreboardTags().contains(Constants.ENTITY_TAG)) return;

        final UUID playerId = player.getUniqueId();

        event.setCancelled(true);
        event.setDamage(0);

        if (isOnCooldown(playerId)) {
            if (configManager.isDebug()) {
                logger.fine("Player " + player.getName() + " attack on cooldown");
            }
            return;
        }

        final Debris debris = debrisManager.findDebrisByInteractionUUID(interaction.getUniqueId());
        if (debris == null) return;

        final DebrisHookAttemptEvent attemptEvent = new DebrisHookAttemptEvent(
            player, debris, interaction.getLocation());
        Bukkit.getPluginManager().callEvent(attemptEvent);
        if (attemptEvent.isCancelled()) return;

        if (!debris.tryHook(playerId)) {
            playMissSound(player);
            if (configManager.isDebug()) {
                logger.fine("Attack conflict: debris #" + debris.getDebugId()
                    + " already hooked by " + debris.getHookedBy());
            }
            return;
        }

        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1.0f, 1.2f);
        setCooldown(playerId, configManager.getAttackCooldownSeconds());

        final DebrisHookSuccessEvent successEvent = new DebrisHookSuccessEvent(player, debris);
        Bukkit.getPluginManager().callEvent(successEvent);

        if (configManager.isDebug()) {
            logger.fine("Attack hit debris #" + debris.getDebugId() + " type=" + debris.getType().getId()
                + " player=" + player.getName());
        }

        player.getScheduler().run(plugin, task -> {
            final DebrisCollectEvent collectEvent = new DebrisCollectEvent(player, debris);
            Bukkit.getPluginManager().callEvent(collectEvent);
            if (collectEvent.isCancelled()) {
                debris.releaseHook();
                return;
            }

            lootGenerator.giveLoot(player, debris.getType());

            if (configManager.isDebug()) {
                logger.fine("Debris #" + debris.getDebugId() + " collected (attack) by " + player.getName());
            }
        }, null);

        debrisManager.removeDebris(debris.getEntityId());
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
        cooldownMap.put(playerId, System.currentTimeMillis() + (long) (seconds * 1000));
    }

    private void playMissSound(final Player player) {
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_ATTACK_KNOCKBACK, 0.5f, 1.5f);
    }
}
