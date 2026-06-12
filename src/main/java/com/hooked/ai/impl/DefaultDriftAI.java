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
    private long lastPerturbTime;

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
        lastPerturbTime = System.currentTimeMillis();
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
        final double deltaTime = (now - lastPerturbTime) / 1000.0;
        final double speed = configManager.getDriftSpeed();
        final double perturbDegrees = Constants.DIRECTION_PERTURB_DEGREES;

        for (final Debris debris : debrisManager.getAllDebris()) {
            if (debris.isHooked()) continue;

            final BlockDisplay entity = debris.getEntity();
            if (entity == null || !entity.isValid()) continue;

            final Location currentLoc = entity.getLocation();
            Vector direction = debris.getDriftDirection();

            if (now - debris.getLastUpdateTime() >= 5000L) {
                final double angle = random.nextDouble(-perturbDegrees, perturbDegrees) * Math.PI / 180.0;
                direction = rotateY(direction, angle);
                debris.setDriftDirection(direction);
                debris.setLastUpdateTime(now);
            }

            final double moveDist = speed * deltaTime;
            Location newLoc = currentLoc.clone().add(direction.clone().multiply(moveDist));

            if (canMoveTo(entity.getWorld(), newLoc)) {
                scheduleTeleport(entity, newLoc, debris, direction);
            } else {
                final double turnAngle = Math.toRadians(
                    random.nextDouble(Constants.OBSTACLE_TURN_MIN_DEGREES,
                                      Constants.OBSTACLE_TURN_MAX_DEGREES));
                final Vector newDir = rotateY(direction, turnAngle);
                final Location altLoc = currentLoc.clone().add(newDir.clone().multiply(moveDist));
                if (canMoveTo(entity.getWorld(), altLoc)) {
                    debris.setDriftDirection(newDir);
                    debris.setLastUpdateTime(now);
                    scheduleTeleport(entity, altLoc, debris, newDir);
                }
            }
        }
    }

    private void scheduleTeleport(final BlockDisplay entity, final Location newLoc,
                                   final Debris debris, final Vector newDir) {
        entity.getScheduler().run(plugin, task -> {
            if (entity.isValid()) {
                final float yaw = (float) Math.toDegrees(Math.atan2(-newDir.getX(), newDir.getZ()));
                newLoc.setYaw(yaw);
                entity.teleport(newLoc);
            }
        }, null);

        if (configManager.isDebug()) {
            logger.fine("Debris #" + debris.getDebugId() + " drift: -> ("
                + newLoc.getBlockX() + ", " + newLoc.getBlockY() + ", " + newLoc.getBlockZ()
                + ") dir: " + newDir);
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

        final int aheadX = (int) Math.floor(loc.getX() + loc.getDirection().getX());
        final int aheadZ = (int) Math.floor(loc.getZ() + loc.getDirection().getZ());
        final Material ahead = world.getBlockAt(aheadX, by, aheadZ).getType();
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
