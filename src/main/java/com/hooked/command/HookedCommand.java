package com.hooked.command;

import com.hooked.config.ConfigManager;
import com.hooked.manager.IDebrisManager;
import com.hooked.model.Debris;
import com.hooked.model.DebrisType;
import com.hooked.model.LootEntry;
import com.hooked.constants.Constants;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Logger;

public final class HookedCommand implements CommandExecutor, TabCompleter {

    private final JavaPlugin plugin;
    private final Logger logger;
    private final ConfigManager configManager;
    private final IDebrisManager debrisManager;
    private final Set<UUID> debugPlayers = new HashSet<>();

    private static final List<String> TYPE_SUBCOMMANDS = List.of("list", "info", "add", "remove", "set", "loot");
    private static final List<String> LOOT_SUBCOMMANDS = List.of("add", "remove", "clear");
    private static final List<String> TYPE_KEYS = List.of("block", "weight", "itemsadder_id");

    public HookedCommand(final JavaPlugin plugin, final ConfigManager configManager,
                         final IDebrisManager debrisManager) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
        this.configManager = configManager;
        this.debrisManager = debrisManager;
    }

    public boolean isDebugEnabled(final UUID playerId) {
        return configManager.isDebug() && debugPlayers.contains(playerId);
    }

    @Override
    public boolean onCommand(final CommandSender sender, final Command command,
                             final String label, final String[] args) {
        if (args.length == 0) {
            sendUsage(sender);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "reload" -> handleReload(sender);
            case "debug" -> handleDebug(sender);
            case "stats" -> handleStats(sender);
            case "test" -> handleTest(sender, args);
            case "type" -> handleType(sender, args);
            default -> sendUsage(sender);
        }
        return true;
    }

    private void handleReload(final CommandSender sender) {
        if (!sender.hasPermission("hooked.admin")) {
            sender.sendMessage("§cYou do not have permission.");
            return;
        }
        configManager.load();
        sender.sendMessage("§aHooked configuration reloaded.");
        logger.info("Configuration reloaded by " + sender.getName());
    }

    private void handleDebug(final CommandSender sender) {
        if (!(sender instanceof final Player player)) {
            sender.sendMessage("§cThis command can only be used by players.");
            return;
        }
        if (!player.hasPermission("hooked.debug")) {
            player.sendMessage("§cYou do not have permission.");
            return;
        }
        if (!configManager.isDebug()) {
            player.sendMessage("§cServer debug mode is not enabled in config.yml.");
            return;
        }

        final UUID id = player.getUniqueId();
        if (debugPlayers.remove(id)) {
            player.sendMessage("§eDebug mode disabled.");
        } else {
            debugPlayers.add(id);
            player.sendMessage("§aDebug mode enabled. Check console for debug output.");
        }
    }

    private void handleStats(final CommandSender sender) {
        if (!sender.hasPermission("hooked.admin")) {
            sender.sendMessage("§cYou do not have permission.");
            return;
        }

        final int total = debrisManager.getAllDebris().size();
        long hooked = debrisManager.getAllDebris().stream().filter(Debris::isHooked).count();

        sender.sendMessage("§6=== Hooked Stats ===");
        sender.sendMessage("§eTotal debris: §f" + total);
        sender.sendMessage("§eHooked: §f" + hooked);
        sender.sendMessage("§eFree: §f" + (total - hooked));
    }

    private void handleTest(final CommandSender sender, final String[] args) {
        if (!(sender instanceof final Player player)) {
            sender.sendMessage("§cThis command can only be used by players.");
            return;
        }
        if (!player.hasPermission("hooked.admin")) {
            player.sendMessage("§cYou do not have permission.");
            return;
        }
        if (args.length < 2 || !args[1].equalsIgnoreCase("hook")) {
            player.sendMessage("§eUsage: /hooked test hook");
            return;
        }

        final Location targetLoc = player.getTargetBlockExact(50) != null
            ? player.getTargetBlockExact(50).getLocation().add(0.5, 1, 0.5)
            : player.getEyeLocation().add(player.getLocation().getDirection().multiply(5));

        Bukkit.getRegionScheduler().run(plugin, targetLoc, task -> {
            final BlockDisplay entity = (BlockDisplay) targetLoc.getWorld()
                .spawnEntity(targetLoc, EntityType.BLOCK_DISPLAY);
            entity.setBlock(Material.OAK_PLANKS.createBlockData());
            entity.addScoreboardTag(Constants.ENTITY_TAG);
            entity.setPersistent(false);

            final Interaction interaction = (Interaction) targetLoc.getWorld()
                .spawnEntity(targetLoc, EntityType.INTERACTION);
            interaction.setInteractionWidth(1.0f);
            interaction.setInteractionHeight(1.0f);
            interaction.addScoreboardTag(Constants.ENTITY_TAG);
            interaction.setPersistent(false);

            final DebrisType type = configManager.getDebrisTypes().get(0);
            final Debris debris = new Debris(entity.getUniqueId(), type, entity.getLocation(),
                new Vector(0, 0, 0));
            debris.setEntity(entity);
            debris.setInteractionEntity(interaction);
            debrisManager.addDebris(debris);

            player.sendMessage("§aTest debris spawned at your crosshair. Entity ID: " + entity.getUniqueId());
            logger.info("Test debris spawned by " + player.getName()
                + " at " + targetLoc.getBlockX() + "," + targetLoc.getBlockY() + "," + targetLoc.getBlockZ());
        });
    }

    private void handleType(final CommandSender sender, final String[] args) {
        if (!sender.hasPermission("hooked.admin")) {
            sender.sendMessage("§cYou do not have permission.");
            return;
        }
        if (args.length < 2) {
            sender.sendMessage("§eUsage: /hooked type <list|info|add|remove|set|loot>");
            return;
        }

        switch (args[1].toLowerCase()) {
            case "list" -> handleTypeList(sender);
            case "info" -> handleTypeInfo(sender, args);
            case "add" -> handleTypeAdd(sender, args);
            case "remove" -> handleTypeRemove(sender, args);
            case "set" -> handleTypeSet(sender, args);
            case "loot" -> handleTypeLoot(sender, args);
            default -> sender.sendMessage("§eUnknown subcommand. Use: list, info, add, remove, set, loot");
        }
    }

    private void handleTypeList(final CommandSender sender) {
        final List<DebrisType> types = configManager.getDebrisTypes();
        sender.sendMessage("§6=== Debris Types (" + types.size() + ") ===");
        for (final DebrisType type : types) {
            final String matName = type.getBlockMaterial() != null ? type.getBlockMaterial().name() : "N/A";
            final String iaInfo = type.getItemsAdderId() != null ? " IA:" + type.getItemsAdderId() : "";
            sender.sendMessage(" §e" + type.getId() + " §7- Block: §f" + matName
                + " §7- Weight: §f" + type.getWeight()
                + " §7- Loot entries: §f" + type.getLootTable().size() + iaInfo);
        }
    }

    private void handleTypeInfo(final CommandSender sender, final String[] args) {
        if (args.length < 3) {
            sender.sendMessage("§eUsage: /hooked type info <typeId>");
            return;
        }
        final DebrisType type = configManager.getDebrisType(args[2]);
        if (type == null) {
            sender.sendMessage("§cType '" + args[2] + "' not found.");
            return;
        }

        sender.sendMessage("§6=== Type: §e" + type.getId() + " §6===");
        sender.sendMessage("§7Block: §f" + (type.getBlockMaterial() != null ? type.getBlockMaterial().name() : "N/A"));
        sender.sendMessage("§7ItemsAdder ID: §f" + (type.getItemsAdderId() != null ? type.getItemsAdderId() : "none"));
        sender.sendMessage("§7Weight: §f" + type.getWeight());
        sender.sendMessage("§7Loot table (" + type.getLootTable().size() + " entries):");
        final List<LootEntry> loot = type.getLootTable();
        for (int i = 0; i < loot.size(); i++) {
            final LootEntry entry = loot.get(i);
            final String itemName = entry.itemMaterial() != null ? entry.itemMaterial() : entry.itemsAdderId();
            sender.sendMessage(" §e[" + i + "] §f" + itemName
                + " §7x" + entry.min() + "-" + entry.max()
                + " §7chance: §f" + String.format("%.1f", entry.chance()) + "%");
        }
    }

    private void handleTypeAdd(final CommandSender sender, final String[] args) {
        if (args.length < 4) {
            sender.sendMessage("§eUsage: /hooked type add <id> <material> <weight> [itemsadder_id]");
            return;
        }

        final String id = args[2].toLowerCase();
        if (configManager.getDebrisType(id) != null) {
            sender.sendMessage("§cType '" + id + "' already exists.");
            return;
        }

        final String blockName = args[3];
        Material material = null;
        if (!blockName.equalsIgnoreCase("none")) {
            material = Material.getMaterial(blockName.toUpperCase());
            if (material == null) {
                sender.sendMessage("§cInvalid material: " + blockName);
                return;
            }
        }

        int weight;
        try {
            weight = Integer.parseInt(args[4]);
            if (weight <= 0) {
                sender.sendMessage("§cWeight must be positive.");
                return;
            }
        } catch (final NumberFormatException | ArrayIndexOutOfBoundsException e) {
            sender.sendMessage("§cWeight must be a positive integer.");
            return;
        }

        final String iaId = args.length >= 6 && !args[5].equalsIgnoreCase("none") ? args[5] : null;
        if (material == null && iaId == null) {
            sender.sendMessage("§cMust specify at least a material or an ItemsAdder ID.");
            return;
        }

        final DebrisType type = new DebrisType(id, material, iaId, weight, List.of());
        if (!configManager.addDebrisType(type)) {
            sender.sendMessage("§cFailed to add type (duplicate ID?).");
            return;
        }

        sender.sendMessage("§aType '" + id + "' added and saved to types.yml.");
        logger.info("Debris type '" + id + "' added by " + sender.getName());
    }

    private void handleTypeRemove(final CommandSender sender, final String[] args) {
        if (args.length < 3) {
            sender.sendMessage("§eUsage: /hooked type remove <typeId>");
            return;
        }

        final String id = args[2];
        if (!configManager.removeDebrisType(id)) {
            sender.sendMessage("§cType '" + id + "' not found.");
            return;
        }

        sender.sendMessage("§aType '" + id + "' removed and types.yml updated.");
        logger.info("Debris type '" + id + "' removed by " + sender.getName());
    }

    private void handleTypeSet(final CommandSender sender, final String[] args) {
        if (args.length < 5) {
            sender.sendMessage("§eUsage: /hooked type set <typeId> <block|weight|itemsadder_id> <value>");
            return;
        }

        final String id = args[2];
        final String key = args[3];
        final String value = args[4];

        if (configManager.setDebrisTypeProperty(id, key, value)) {
            sender.sendMessage("§aType '" + id + "' updated: " + key + " = " + value);
            logger.info("Debris type '" + id + "' property '" + key + "' set to '" + value + "' by " + sender.getName());
        } else {
            sender.sendMessage("§cFailed to update. Check that the type exists and the value is valid.");
        }
    }

    private void handleTypeLoot(final CommandSender sender, final String[] args) {
        if (args.length < 3) {
            sender.sendMessage("§eUsage: /hooked type loot <add|remove|clear> ...");
            return;
        }

        switch (args[2].toLowerCase()) {
            case "add" -> handleLootAdd(sender, args);
            case "remove" -> handleLootRemove(sender, args);
            case "clear" -> handleLootClear(sender, args);
            default -> sender.sendMessage("§eUnknown loot subcommand. Use: add, remove, clear");
        }
    }

    private void handleLootAdd(final CommandSender sender, final String[] args) {
        if (args.length < 7) {
            sender.sendMessage("§eUsage: /hooked type loot add <typeId> <item> <min> <max> <chance> [itemsadder_id]");
            return;
        }

        final String typeId = args[3];
        if (configManager.getDebrisType(typeId) == null) {
            sender.sendMessage("§cType '" + typeId + "' not found.");
            return;
        }

        final String itemName = args[4];
        final int min;
        final int max;
        final double chance;

        try {
            min = Integer.parseInt(args[5]);
            max = Integer.parseInt(args[6]);
            chance = Double.parseDouble(args[7]);
            if (min <= 0 || max < min) {
                sender.sendMessage("§cInvalid min/max values (min > 0, max >= min).");
                return;
            }
            if (chance < 0 || chance > 100) {
                sender.sendMessage("§cChance must be between 0 and 100.");
                return;
            }
        } catch (final NumberFormatException | ArrayIndexOutOfBoundsException e) {
            sender.sendMessage("§cInvalid number format for min/max/chance.");
            return;
        }

        final String iaId = args.length >= 9 && !args[8].equalsIgnoreCase("none") ? args[8] : null;
        final String lootItemMaterial = iaId != null ? null
            : (itemName.equalsIgnoreCase("none") ? null : itemName);
        if (lootItemMaterial == null && iaId == null) {
            sender.sendMessage("§cMust specify an item or ItemsAdder ID.");
            return;
        }

        final LootEntry entry = new LootEntry(lootItemMaterial, iaId, min, max, chance);
        if (configManager.addLootEntry(typeId, entry)) {
            sender.sendMessage("§aLoot entry added to type '" + typeId + "' and saved to types.yml.");
            logger.info("Loot entry added to '" + typeId + "' by " + sender.getName()
                + ": " + (lootItemMaterial != null ? lootItemMaterial : iaId));
        } else {
            sender.sendMessage("§cFailed to add loot entry.");
        }
    }

    private void handleLootRemove(final CommandSender sender, final String[] args) {
        if (args.length < 5) {
            sender.sendMessage("§eUsage: /hooked type loot remove <typeId> <index>");
            return;
        }

        final String typeId = args[3];
        int index;
        try {
            index = Integer.parseInt(args[4]);
        } catch (final NumberFormatException e) {
            sender.sendMessage("§cIndex must be a number.");
            return;
        }

        if (configManager.removeLootEntry(typeId, index)) {
            sender.sendMessage("§aLoot entry [" + index + "] removed from type '" + typeId + "' and types.yml updated.");
            logger.info("Loot entry [" + index + "] removed from '" + typeId + "' by " + sender.getName());
        } else {
            sender.sendMessage("§cFailed to remove. Check type exists and index is valid.");
        }
    }

    private void handleLootClear(final CommandSender sender, final String[] args) {
        if (args.length < 4) {
            sender.sendMessage("§eUsage: /hooked type loot clear <typeId>");
            return;
        }

        final String typeId = args[3];
        if (configManager.clearLoot(typeId)) {
            sender.sendMessage("§aAll loot cleared from type '" + typeId + "' and types.yml updated.");
            logger.info("Loot cleared from '" + typeId + "' by " + sender.getName());
        } else {
            sender.sendMessage("§cType '" + typeId + "' not found.");
        }
    }

    private void sendUsage(final CommandSender sender) {
        sender.sendMessage("§6=== Hooked Commands ===");
        sender.sendMessage("§e/hooked reload §7- Reload configuration");
        sender.sendMessage("§e/hooked debug §7- Toggle personal debug mode");
        sender.sendMessage("§e/hooked stats §7- Show debris statistics");
        sender.sendMessage("§e/hooked test hook §7- Spawn a test debris at crosshair");
        sender.sendMessage("§e/hooked type list §7- List all debris types");
        sender.sendMessage("§e/hooked type info <id> §7- Show type details and loot");
        sender.sendMessage("§e/hooked type add <id> <block> <weight> [iaId] §7- Add a debris type");
        sender.sendMessage("§e/hooked type remove <id> §7- Remove a debris type");
        sender.sendMessage("§e/hooked type set <id> <key> <val> §7- Modify type properties");
        sender.sendMessage("§e/hooked type loot add <id> <item> <min> <max> <chance> [iaId] §7- Add loot entry");
        sender.sendMessage("§e/hooked type loot remove <id> <idx> §7- Remove loot entry");
        sender.sendMessage("§e/hooked type loot clear <id> §7- Clear all loot");
    }

    @Override
    public List<String> onTabComplete(final CommandSender sender, final Command command,
                                       final String alias, final String[] args) {
        if (args.length == 1) {
            return filterStartsWith(List.of("reload", "debug", "stats", "test", "type"), args[0]);
        }
        if (args.length >= 2 && args[0].equalsIgnoreCase("test")) {
            if (args.length == 2) return filterStartsWith(List.of("hook"), args[1]);
            return Collections.emptyList();
        }
        if (args.length >= 2 && args[0].equalsIgnoreCase("type")) {
            return tabCompleteType(args);
        }
        return Collections.emptyList();
    }

    private List<String> tabCompleteType(final String[] args) {
        if (args.length == 2) {
            return filterStartsWith(TYPE_SUBCOMMANDS, args[1]);
        }

        final String sub = args[1].toLowerCase();
        if (sub.equals("loot")) {
            return tabCompleteLoot(args);
        }

        return switch (sub) {
            case "info", "remove" -> args.length == 3 ? filterStartsWith(getTypeIds(), args[2])
                : Collections.emptyList();
            case "set" -> tabCompleteSet(args);
            case "add" -> tabCompleteAdd(args);
            default -> Collections.emptyList();
        };
    }

    private List<String> tabCompleteLoot(final String[] args) {
        if (args.length == 3) {
            return filterStartsWith(LOOT_SUBCOMMANDS, args[2]);
        }
        if (args.length == 4) {
            return filterStartsWith(getTypeIds(), args[3]);
        }
        final String lootSub = args[2].toLowerCase();
        if (lootSub.equals("remove") || lootSub.equals("clear")) {
            return Collections.emptyList();
        }
        if (lootSub.equals("add")) {
            if (args.length == 5) {
                return filterStartsWith(getMaterialNames(), args[4]);
            }
            if (args.length == 6) return Collections.singletonList("1");
            if (args.length == 7) return Collections.singletonList("1");
            if (args.length == 8) return Collections.singletonList("100.0");
        }
        return Collections.emptyList();
    }

    private List<String> tabCompleteSet(final String[] args) {
        if (args.length == 3) {
            return filterStartsWith(getTypeIds(), args[2]);
        }
        if (args.length == 4) {
            return filterStartsWith(TYPE_KEYS, args[3]);
        }
        if (args.length == 5) {
            final String key = args[3].toLowerCase();
            if (key.equals("block")) {
                return filterStartsWith(getMaterialNames(), args[4]);
            }
        }
        return Collections.emptyList();
    }

    private List<String> tabCompleteAdd(final String[] args) {
        if (args.length == 4) {
            return filterStartsWith(getMaterialNames(), args[3]);
        }
        if (args.length == 5) {
            return Collections.singletonList("10");
        }
        return Collections.emptyList();
    }

    private List<String> getTypeIds() {
        final List<String> ids = new ArrayList<>();
        for (final DebrisType type : configManager.getDebrisTypes()) {
            ids.add(type.getId());
        }
        return ids;
    }

    private List<String> getMaterialNames() {
        final List<String> names = new ArrayList<>();
        for (final Material mat : Material.values()) {
            if (mat.isBlock() || mat.isItem()) {
                names.add(mat.name());
            }
        }
        return names;
    }

    private List<String> filterStartsWith(final List<String> options, final String prefix) {
        final String lower = prefix.toLowerCase();
        final List<String> result = new ArrayList<>();
        for (final String opt : options) {
            if (opt.toLowerCase().startsWith(lower)) {
                result.add(opt);
            }
        }
        return result.isEmpty() ? options : result;
    }
}
