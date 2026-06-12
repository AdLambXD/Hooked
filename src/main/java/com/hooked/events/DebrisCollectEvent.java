package com.hooked.events;

import com.hooked.model.Debris;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

public class DebrisCollectEvent extends Event implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();
    private boolean cancelled;
    private final Player player;
    private final Debris debris;

    public DebrisCollectEvent(final Player player, final Debris debris) {
        this.player = player;
        this.debris = debris;
    }

    public Player getPlayer() { return player; }
    public Debris getDebris() { return debris; }

    @Override
    public boolean isCancelled() { return cancelled; }

    @Override
    public void setCancelled(final boolean cancel) { this.cancelled = cancel; }

    @Override
    public @NotNull HandlerList getHandlers() { return HANDLERS; }

    public static HandlerList getHandlerList() { return HANDLERS; }
}
