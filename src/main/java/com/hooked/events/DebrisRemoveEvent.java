package com.hooked.events;

import com.hooked.model.Debris;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

public class DebrisRemoveEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();
    private final Debris debris;
    private final Cause cause;

    public enum Cause { HOOKED, DESPAWN, PLUGIN, EXTERNAL }

    public DebrisRemoveEvent(final Debris debris, final Cause cause) {
        this.debris = debris;
        this.cause = cause;
    }

    public Debris getDebris() { return debris; }
    public Cause getCause() { return cause; }

    @Override
    public @NotNull HandlerList getHandlers() { return HANDLERS; }

    public static HandlerList getHandlerList() { return HANDLERS; }
}
