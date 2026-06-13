package com.hooked.ai.impl;

import com.hooked.ai.IDriftAI;
import com.hooked.config.ConfigManager;
import com.hooked.constants.Constants;
import com.hooked.manager.IDebrisManager;
import com.hooked.model.Debris;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Vector;

import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Logger;

public final class DefaultDriftAI implements IDriftAI {

    private final JavaPlugin plugin;
    private final Logger logger;
    private final ConfigManager configManager;
    private final IDebrisManager debrisManager;
    private final ThreadLocalRandom random = ThreadLocalRandom.current();
    private boolean running;

    public DefaultDriftAI(final JavaPlugin plugin, final ConfigManager configManager,
                          final IDebrisManager debrisManager) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
        this.configManager = configManager;
        this.debrisManager = debrisManager;
    }

    @Override
    public void start() {
        running = true;
        Bukkit.getGlobalRegionScheduler().runAtFixedRate(plugin, task -> {
            try {
                if (!running) {
                    task.cancel();
                    return;
                }
                tickDrift();
            } catch (final Exception e) {
                logger.warning("Drift tick error: " + e.getMessage());
            }
        }, Constants.DRIFT_TICK_INTERVAL, Constants.DRIFT_TICK_INTERVAL);
    }

    @Override
    public void stop() {
        running = false;
    }

    private void tickDrift() {
        final long now = System.currentTimeMillis();
        final double speed = configManager.getDriftSpeed();

        for (final Debris debris : debrisManager.getAllDebris()) {
            if (debris.isHooked()) continue;

            final BlockDisplay entity = debris.getEntity();
            if (entity == null || !entity.isValid()) continue;

            final Vector currentDir = debris.getDriftDirection();
            final Vector newDir;

            if (now - debris.getLastUpdateTime() >= 5000L) {
                final double angle = random.nextDouble(-Constants.DIRECTION_PERTURB_DEGREES,
                    Constants.DIRECTION_PERTURB_DEGREES) * Math.PI / 180.0;
                newDir = rotateY(currentDir, angle);
                debris.setDriftDirection(newDir);
                debris.setLastUpdateTime(now);
            } else {
                newDir = currentDir;
            }

            final Location currentLoc = entity.getLocation();
            final Location candidateLoc = currentLoc.clone().add(newDir.clone().multiply(speed));

            scheduleDriftMove(entity, candidateLoc, newDir, debris, currentLoc);
        }
    }

    private void scheduleDriftMove(final BlockDisplay entity, final Location candidateLoc,
                                    final Vector newDir, final Debris debris,
                                    final Location originalLoc) {
        entity.getScheduler().run(plugin, task -> {
            try {
                if (!entity.isValid()) return;

                if (canMoveTo(entity.getWorld(), candidateLoc)) {
                    teleportEntity(entity, candidateLoc, debris, newDir);
                } else {
                    final double turnAngle = Math.toRadians(
                        random.nextDouble(Constants.OBSTACLE_TURN_MIN_DEGREES,
                                          Constants.OBSTACLE_TURN_MAX_DEGREES));
                    final Vector altDir = rotateY(newDir, turnAngle);
                    final double speed = configManager.getDriftSpeed();
                    final Location altLoc = originalLoc.clone().add(altDir.clone().multiply(speed));

                    if (canMoveTo(entity.getWorld(), altLoc)) {
                        debris.setDriftDirection(altDir);
                        debris.setLastUpdateTime(System.currentTimeMillis());
                        teleportEntity(entity, altLoc, debris, altDir);
                    }
                }
            } catch (final Exception e) {
                if (e.getMessage() != null) {
                    logger.warning("Drift move error: " + e.getMessage());
                }
            }
        }, null);
    }

    private void teleportEntity(final BlockDisplay entity, final Location loc,
                                 final Debris debris, final Vector dir) {
        final float yaw = (float) Math.toDegrees(Math.atan2(-dir.getX(), dir.getZ()));
        loc.setYaw(yaw);
        entity.teleportAsync(loc);

        final org.bukkit.entity.Interaction interaction = debris.getInteractionEntity();
        if (interaction != null && interaction.isValid()) {
            interaction.teleportAsync(loc);
        }

        if (configManager.isDebug()) {
            logger.fine("Debris #" + debris.getDebugId() + " drift: -> ("
                + loc.getBlockX() + ", " + loc.getBlockY() + ", " + loc.getBlockZ()
                + ") dir: " + dir);
        }
    }

    private boolean canMoveTo(final World world, final Location loc) {
        final int bx = loc.getBlockX();
        final int by = (int) Math.floor(loc.getY());
        final int bz = loc.getBlockZ();

        final Material below = world.getBlockAt(bx, by - 1, bz).getType();
        if (below != Material.WATER) return false;

        final Material at = world.getBlockAt(bx, by, bz).getType();
        if (at != Material.AIR && at != Material.CAVE_AIR && at != Material.VOID_AIR
            && at != Material.WATER) return false;

        final double aheadX = loc.getX() + loc.getDirection().getX();
        final double aheadZ = loc.getZ() + loc.getDirection().getZ();
        final int ax = (int) Math.floor(aheadX);
        final int az = (int) Math.floor(aheadZ);
        final Material ahead = world.getBlockAt(ax, by, az).getType();
        return ahead == Material.WATER || ahead == Material.AIR
            || ahead == Material.CAVE_AIR || ahead == Material.VOID_AIR;
    }

    private Vector rotateY(final Vector vec, final double angleRad) {
        final double cos = Math.cos(angleRad);
        final double sin = Math.sin(angleRad);
        return new Vector(
            vec.getX() * cos - vec.getZ() * sin,
            0,
            vec.getX() * sin + vec.getZ() * cos
        ).normalize();
    }
}
