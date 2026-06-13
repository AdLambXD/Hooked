package com.hooked.command;

import com.hooked.config.ConfigManager;
import com.hooked.manager.IDebrisManager;
import com.hooked.model.Debris;
import com.hooked.model.DebrisType;
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

    private void sendUsage(final CommandSender sender) {
        sender.sendMessage("§6=== Hooked Commands ===");
        sender.sendMessage("§e/hooked reload §7- Reload configuration");
        sender.sendMessage("§e/hooked debug §7- Toggle personal debug mode");
        sender.sendMessage("§e/hooked stats §7- Show debris statistics");
        sender.sendMessage("§e/hooked test hook §7- Spawn a test debris at crosshair");
    }

    @Override
    public List<String> onTabComplete(final CommandSender sender, final Command command,
                                       final String alias, final String[] args) {
        if (args.length == 1) {
            return List.of("reload", "debug", "stats", "test").stream()
                .filter(s -> s.startsWith(args[0].toLowerCase())).toList();
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("test")) {
            return Collections.singletonList("hook");
        }
        return Collections.emptyList();
    }
}
