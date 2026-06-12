package com.hooked.events;

import com.hooked.model.DebrisType;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

public class DebrisSpawnEvent extends Event implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();
    private boolean cancelled;
    private final DebrisType type;
    private Location location;
    private final Player player;

    public DebrisSpawnEvent(final DebrisType type, final Location location, final Player player) {
        this.type = type;
        this.location = location;
        this.player = player;
    }

    public DebrisType getType() { return type; }
    public Location getLocation() { return location; }
    public void setLocation(final Location location) { this.location = location; }
    public Player getPlayer() { return player; }

    @Override
    public boolean isCancelled() { return cancelled; }

    @Override
    public void setCancelled(final boolean cancel) { this.cancelled = cancel; }

    @Override
    public @NotNull HandlerList getHandlers() { return HANDLERS; }

    public static HandlerList getHandlerList() { return HANDLERS; }
}
