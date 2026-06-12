package com.hooked.api;

import com.hooked.HookedPlugin;
import com.hooked.manager.IDebrisManager;
import com.hooked.model.LootEntry;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

public final class HookedAPI {

    private static final Map<Player, Function<LootEntry, LootEntry>> lootModifiers = new ConcurrentHashMap<>();

    private HookedAPI() {}

    public static IDebrisManager getDebrisManager() {
        final HookedPlugin plugin = (HookedPlugin) Bukkit.getPluginManager().getPlugin("Hooked");
        if (plugin == null) return null;
        return plugin.getDebrisManager();
    }

    public static void setLootModifier(final Player player,
                                        final Function<LootEntry, LootEntry> modifier) {
        if (modifier == null) {
            lootModifiers.remove(player);
        } else {
            lootModifiers.put(player, modifier);
        }
    }

    public static Function<LootEntry, LootEntry> getLootModifier(final Player player) {
        return lootModifiers.get(player);
    }
}
