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
import org.bukkit.entity.Interaction;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Vector;

import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Logger;

public final class DefaultDriftAI implements IDriftAI {

    private static final long TICK_INTERVAL = 2L;
    private static final double TICK_INTERVAL_SECONDS = TICK_INTERVAL / 20.0;
    private static final int TELEPORT_DURATION = 3;

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
        }, TICK_INTERVAL, TICK_INTERVAL);
    }

    @Override
    public void stop() {
        running = false;
    }

    private void tickDrift() {
        final long now = System.currentTimeMillis();

        for (final Debris debris : debrisManager.getAllDebris()) {
            if (debris.isHooked()) continue;

            final BlockDisplay entity = debris.getEntity();
            if (entity == null || !entity.isValid()) continue;

            scheduleDriftMove(entity, debris, now);
        }
    }

    private void scheduleDriftMove(final BlockDisplay entity, final Debris debris,
                                    final long now) {
        entity.getScheduler().run(plugin, task -> {
            try {
                if (!entity.isValid()) return;

                ensureTeleportDuration(entity);

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
                final double speed = configManager.getDriftSpeed() * TICK_INTERVAL_SECONDS;
                final Location candidateLoc = currentLoc.clone().add(newDir.clone().multiply(speed));

                final double bobOffset = 0.05 * Math.sin((currentLoc.getX() + currentLoc.getZ()) * 3.0
                    + now * 0.0008 + debris.getDebugId());
                candidateLoc.setY(candidateLoc.getY() + bobOffset);

                final float yaw = (float) Math.toDegrees(Math.atan2(-newDir.getX(), newDir.getZ()));
                candidateLoc.setYaw(yaw);

                if (canMoveTo(entity.getWorld(), candidateLoc)) {
                    teleportEntity(entity, candidateLoc, debris);
                } else {
                    final double turnAngle = Math.toRadians(
                        random.nextDouble(Constants.OBSTACLE_TURN_MIN_DEGREES,
                                          Constants.OBSTACLE_TURN_MAX_DEGREES));
                    final Vector altDir = rotateY(newDir, turnAngle);
                    final Location altLoc = currentLoc.clone().add(altDir.clone().multiply(speed));
                    altLoc.setYaw((float) Math.toDegrees(Math.atan2(-altDir.getX(), altDir.getZ())));

                    if (canMoveTo(entity.getWorld(), altLoc)) {
                        debris.setDriftDirection(altDir);
                        debris.setLastUpdateTime(now);
                        teleportEntity(entity, altLoc, debris);
                    }
                }
            } catch (final Exception e) {
                if (e.getMessage() != null) {
                    logger.warning("Drift move error: " + e.getMessage());
                }
            }
        }, null);
    }

    private void ensureTeleportDuration(final BlockDisplay entity) {
        if (entity.getTeleportDuration() != TELEPORT_DURATION) {
            entity.setTeleportDuration(TELEPORT_DURATION);
        }
    }

    private void teleportEntity(final BlockDisplay entity, final Location loc,
                                 final Debris debris) {
        entity.teleportAsync(loc);

        final Interaction interaction = debris.getInteractionEntity();
        if (interaction != null && interaction.isValid()) {
            interaction.teleportAsync(loc);
        }

        if (configManager.isDebug()) {
            logger.fine("Debris #" + debris.getDebugId() + " drift: -> ("
                + loc.getBlockX() + ", " + loc.getBlockY() + ", " + loc.getBlockZ()
                + ") dir: " + debris.getDriftDirection());
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
