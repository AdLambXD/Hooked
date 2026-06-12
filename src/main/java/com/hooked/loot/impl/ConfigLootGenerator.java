package com.hooked.loot.impl;

import com.hooked.config.ConfigManager;
import com.hooked.loot.ILootGenerator;
import com.hooked.model.DebrisType;
import com.hooked.model.LootEntry;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Logger;

public final class ConfigLootGenerator implements ILootGenerator {

    private final JavaPlugin plugin;
    private final Logger logger;
    private final ConfigManager configManager;
    private final ThreadLocalRandom random = ThreadLocalRandom.current();

    public ConfigLootGenerator(final JavaPlugin plugin, final ConfigManager configManager) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
        this.configManager = configManager;
    }

    @Override
    public void giveLoot(final Player player, final DebrisType type) {
        for (final LootEntry entry : type.getLootTable()) {
            if (random.nextDouble() * 100.0 >= entry.chance()) continue;

            final int amount = random.nextInt(entry.min(), entry.max() + 1);
            ItemStack item = null;

            if (entry.itemsAdderId() != null && configManager.isIntegrationItemsAdder()) {
                item = getItemsAdderItem(entry.itemsAdderId(), amount);
            }

            if (item == null && entry.itemMaterial() != null) {
                final Material mat = Material.getMaterial(entry.itemMaterial());
                if (mat != null) {
                    item = new ItemStack(mat, amount);
                }
            }

            if (item == null) {
                logger.warning("Could not create loot item for entry: " + entry);
                continue;
            }

            final Map<Integer, ItemStack> excess = player.getInventory().addItem(item);
            for (final ItemStack stack : excess.values()) {
                player.getWorld().dropItem(player.getLocation(), stack);
            }
        }

        if (configManager.isDebug()) {
            logger.fine("Loot given to " + player.getName() + " from type " + type.getId());
        }
    }

    private ItemStack getItemsAdderItem(final String iaId, final int amount) {
        try {
            final Object iaPlugin = Bukkit.getPluginManager().getPlugin("ItemsAdder");
            if (iaPlugin == null) return null;

            final Class<?> iaClass = iaPlugin.getClass();
            final Class<?> itemsAdderClass = Class.forName("dev.lone.itemsadder.api.CustomStack");
            final Object customStack = itemsAdderClass.getMethod("getInstance", String.class)
                .invoke(null, iaId);
            if (customStack == null) return null;

            final Object item = customStack.getClass().getMethod("getItemStack").invoke(customStack);
            final ItemStack stack = ((ItemStack) item).clone();
            stack.setAmount(Math.min(amount, stack.getMaxStackSize()));
            return stack;
        } catch (final Exception e) {
            if (configManager.isDebug()) {
                logger.fine("ItemsAdder item lookup failed for " + iaId + ": " + e.getMessage());
            }
            return null;
        }
    }
}
