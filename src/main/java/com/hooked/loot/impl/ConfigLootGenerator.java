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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Logger;

public final class ConfigLootGenerator implements ILootGenerator {

    private final JavaPlugin plugin;
    private final Logger logger;
    private final ConfigManager configManager;
    private final ThreadLocalRandom random = ThreadLocalRandom.current();

    private static final Map<String, String> MATERIAL_NAMES = Map.ofEntries(
        Map.entry("STICK", "木棍"),
        Map.entry("STRING", "线"),
        Map.entry("IRON_INGOT", "铁锭"),
        Map.entry("IRON_NUGGET", "铁粒"),
        Map.entry("GOLD_INGOT", "金锭"),
        Map.entry("GOLD_NUGGET", "金粒"),
        Map.entry("OAK_PLANKS", "橡木木板"),
        Map.entry("BIRCH_PLANKS", "白桦木板"),
        Map.entry("SPRUCE_PLANKS", "云杉木板"),
        Map.entry("JUNGLE_PLANKS", "丛林木板"),
        Map.entry("ACACIA_PLANKS", "金合欢木板"),
        Map.entry("DARK_OAK_PLANKS", "深色橡木木板"),
        Map.entry("CHERRY_PLANKS", "樱花木板"),
        Map.entry("BAMBOO_PLANKS", "竹板"),
        Map.entry("APPLE", "苹果"),
        Map.entry("CHICKEN", "生鸡肉"),
        Map.entry("COOKED_CHICKEN", "熟鸡肉"),
        Map.entry("PORKCHOP", "生猪排"),
        Map.entry("COOKED_PORKCHOP", "熟猪排"),
        Map.entry("BEEF", "生牛肉"),
        Map.entry("COOKED_BEEF", "牛排"),
        Map.entry("MUTTON", "生羊肉"),
        Map.entry("COOKED_MUTTON", "熟羊肉"),
        Map.entry("RABBIT", "生兔肉"),
        Map.entry("COOKED_RABBIT", "熟兔肉"),
        Map.entry("COD", "生鳕鱼"),
        Map.entry("COOKED_COD", "熟鳕鱼"),
        Map.entry("SALMON", "生鲑鱼"),
        Map.entry("COOKED_SALMON", "熟鲑鱼"),
        Map.entry("BREAD", "面包"),
        Map.entry("CARROT", "胡萝卜"),
        Map.entry("POTATO", "土豆"),
        Map.entry("WHEAT", "小麦"),
        Map.entry("WHEAT_SEEDS", "小麦种子"),
        Map.entry("BEETROOT", "甜菜根"),
        Map.entry("BEETROOT_SEEDS", "甜菜种子"),
        Map.entry("MELON_SLICE", "西瓜片"),
        Map.entry("PUMPKIN", "南瓜"),
        Map.entry("LEATHER", "皮革"),
        Map.entry("FEATHER", "羽毛"),
        Map.entry("EGG", "鸡蛋"),
        Map.entry("BONE", "骨头"),
        Map.entry("ROTTEN_FLESH", "腐肉"),
        Map.entry("GUNPOWDER", "火药"),
        Map.entry("SLIME_BALL", "粘液球"),
        Map.entry("INK_SAC", "墨囊"),
        Map.entry("GLOW_INK_SAC", "荧光墨囊"),
        Map.entry("COAL", "煤炭"),
        Map.entry("CHARCOAL", "木炭"),
        Map.entry("FLINT", "燧石"),
        Map.entry("CLAY_BALL", "粘土球"),
        Map.entry("SUGAR_CANE", "甘蔗"),
        Map.entry("KELP", "海带"),
        Map.entry("BAMBOO", "竹子"),
        Map.entry("TORCH", "火把"),
        Map.entry("BOWL", "碗"),
        Map.entry("PAPER", "纸"),
        Map.entry("BOOK", "书"),
        Map.entry("EMERALD", "绿宝石"),
        Map.entry("DIAMOND", "钻石"),
        Map.entry("NETHERITE_SCRAP", "下界合金碎片"),
        Map.entry("COPPER_INGOT", "铜锭"),
        Map.entry("RAW_IRON", "粗铁"),
        Map.entry("RAW_COPPER", "粗铜"),
        Map.entry("RAW_GOLD", "粗金")
    );

    private static final Map<String, String> TYPE_NAMES = Map.of(
        "plank", "木板",
        "barrel", "木桶",
        "leaves", "树叶"
    );

    public ConfigLootGenerator(final JavaPlugin plugin, final ConfigManager configManager) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
        this.configManager = configManager;
    }

    @Override
    public void giveLoot(final Player player, final DebrisType type) {
        final List<DropResult> drops = new ArrayList<>();

        for (final LootEntry entry : type.getLootTable()) {
            if (random.nextDouble() * 100.0 >= entry.chance()) continue;

            final int amount = random.nextInt(entry.min(), entry.max() + 1);
            ItemStack item = null;
            String displayName = null;

            if (entry.itemsAdderId() != null && configManager.isIntegrationItemsAdder()) {
                item = getItemsAdderItem(entry.itemsAdderId(), amount);
                if (item != null) displayName = getItemDisplayName(item);
            }

            if (item == null && entry.itemMaterial() != null) {
                final Material mat = Material.getMaterial(entry.itemMaterial());
                if (mat != null) {
                    item = new ItemStack(mat, amount);
                    displayName = getItemDisplayName(item);
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

            drops.add(new DropResult(displayName, amount, entry.chance()));
        }

        sendLootMessage(player, type, drops);

        if (configManager.isDebug()) {
            logger.fine("Loot given to " + player.getName() + " from type " + type.getId());
        }
    }

    private void sendLootMessage(final Player player, final DebrisType type, final List<DropResult> drops) {
        final String showMode = configManager.getShowLootMessage();
        if ("none".equalsIgnoreCase(showMode) && !hasEpicDrop(drops)) return;

        final String typeName = getTypeDisplayName(type.getId());

        if (drops.isEmpty()) {
            final String msg = "§7" + typeName + "没有掉落任何物品...";
            if (!"none".equalsIgnoreCase(showMode)) {
                sendByMode(player, msg, showMode);
            }
            return;
        }

        final StringBuilder actionBar = new StringBuilder("§e").append(typeName).append(" §7→ ");
        final StringBuilder epicBroadcast = new StringBuilder();
        boolean hasEpic = false;

        for (final DropResult drop : drops) {
            final String color = chanceColor(drop.chance);
            actionBar.append(color).append("+").append(drop.name).append(" x").append(drop.amount).append("  ");

            if (drop.chance < 10.0) {
                hasEpic = true;
                epicBroadcast.append("§6✨ §b").append(player.getName())
                    .append(" §6从§e").append(typeName)
                    .append("§6中获得了").append(color)
                    .append(drop.name).append(" x").append(drop.amount)
                    .append("§6！");
            }
        }

        if (!"none".equalsIgnoreCase(showMode)) {
            sendByMode(player, actionBar.toString().trim(), showMode);
        }

        if (hasEpic) {
            Bukkit.broadcastMessage(epicBroadcast.toString());
        }
    }

    private void sendByMode(final Player player, final String msg, final String mode) {
        switch (mode.toLowerCase()) {
            case "action_bar" -> player.sendActionBar(msg);
            case "chat" -> player.sendMessage(msg);
            case "both" -> {
                player.sendActionBar(msg);
                player.sendMessage(msg);
            }
        }
    }

    private boolean hasEpicDrop(final List<DropResult> drops) {
        for (final DropResult drop : drops) {
            if (drop.chance < 10.0) return true;
        }
        return false;
    }

    private static String chanceColor(final double chance) {
        if (chance >= 90) return "§f";
        if (chance >= 50) return "§a";
        if (chance >= 10) return "§e";
        return "§6";
    }

    private static String getTypeDisplayName(final String typeId) {
        return TYPE_NAMES.getOrDefault(typeId.toLowerCase(), typeId);
    }

    private static String getItemDisplayName(final ItemStack item) {
        final Material mat = item.getType();
        if (mat == Material.AIR) return "空气";
        if (item.hasItemMeta() && item.getItemMeta().hasDisplayName()) {
            return item.getItemMeta().getDisplayName();
        }
        return MATERIAL_NAMES.getOrDefault(mat.name(), mat.name());
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

    private record DropResult(String name, int amount, double chance) {}
}
