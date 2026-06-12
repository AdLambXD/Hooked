package com.hooked.model;

import org.bukkit.Location;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.util.Vector;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

public final class Debris {

    private static final AtomicInteger ID_COUNTER = new AtomicInteger(0);

    private final UUID entityId;
    private final DebrisType type;
    private final Location spawnLocation;
    private Vector driftDirection;
    private volatile boolean hooked;
    private volatile UUID hookedBy;
    private long lastUpdateTime;
    private long hookedTimestamp;
    private final long createdAt;
    private final int debugId;
    private final Map<String, Object> customData = new HashMap<>();
    private BlockDisplay entity;

    public Debris(final UUID entityId, final DebrisType type, final Location spawnLocation,
                  final Vector driftDirection) {
        this.entityId = entityId;
        this.type = type;
        this.spawnLocation = spawnLocation.clone();
        this.driftDirection = driftDirection.clone();
        this.hooked = false;
        this.hookedBy = null;
        this.lastUpdateTime = System.currentTimeMillis();
        this.hookedTimestamp = 0L;
        this.createdAt = System.currentTimeMillis();
        this.debugId = ID_COUNTER.incrementAndGet();
    }

    public synchronized boolean tryHook(final UUID playerId) {
        if (hooked) {
            return false;
        }
        hooked = true;
        hookedBy = playerId;
        hookedTimestamp = System.currentTimeMillis();
        return true;
    }

    public synchronized void releaseHook() {
        hooked = false;
        hookedBy = null;
        hookedTimestamp = 0L;
    }

    public boolean isHookTimedOut() {
        return hooked && (System.currentTimeMillis() - hookedTimestamp > 5000L);
    }

    public UUID getEntityId() { return entityId; }
    public DebrisType getType() { return type; }
    public Location getSpawnLocation() { return spawnLocation.clone(); }
    public Vector getDriftDirection() { return driftDirection.clone(); }
    public void setDriftDirection(final Vector dir) { this.driftDirection = dir.clone(); }
    public boolean isHooked() { return hooked; }
    public UUID getHookedBy() { return hookedBy; }
    public long getLastUpdateTime() { return lastUpdateTime; }
    public void setLastUpdateTime(final long time) { this.lastUpdateTime = time; }
    public long getCreatedAt() { return createdAt; }
    public int getDebugId() { return debugId; }
    public Map<String, Object> getCustomData() { return customData; }
    public BlockDisplay getEntity() { return entity; }
    public void setEntity(final BlockDisplay entity) { this.entity = entity; }
}
