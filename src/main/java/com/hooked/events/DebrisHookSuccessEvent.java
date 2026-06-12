package com.hooked.events;

import com.hooked.model.Debris;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

public class DebrisHookSuccessEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();
    private final Player player;
    private final Debris debris;

    public DebrisHookSuccessEvent(final Player player, final Debris debris) {
        this.player = player;
        this.debris = debris;
    }

    public Player getPlayer() { return player; }
    public Debris getDebris() { return debris; }

    @Override
    public @NotNull HandlerList getHandlers() { return HANDLERS; }

    public static HandlerList getHandlerList() { return HANDLERS; }
}
