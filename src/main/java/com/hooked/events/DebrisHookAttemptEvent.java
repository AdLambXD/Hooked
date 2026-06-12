package com.hooked.events;

import com.hooked.model.Debris;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class DebrisHookAttemptEvent extends Event implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();
    private boolean cancelled;
    private final Player player;
    private final Debris debris;
    private final Location hookLocation;

    public DebrisHookAttemptEvent(final Player player, @Nullable final Debris debris,
                                   final Location hookLocation) {
        this.player = player;
        this.debris = debris;
        this.hookLocation = hookLocation;
    }

    public Player getPlayer() { return player; }

    @Nullable
    public Debris getDebris() { return debris; }

    public Location getHookLocation() { return hookLocation; }

    public boolean isSuccessful() { return debris != null; }

    @Override
    public boolean isCancelled() { return cancelled; }

    @Override
    public void setCancelled(final boolean cancel) { this.cancelled = cancel; }

    @Override
    public @NotNull HandlerList getHandlers() { return HANDLERS; }

    public static HandlerList getHandlerList() { return HANDLERS; }
}
