package com.hooked;

import com.hooked.ai.IDriftAI;
import com.hooked.ai.impl.DefaultDriftAI;
import com.hooked.command.HookedCommand;
import com.hooked.config.ConfigManager;
import com.hooked.constants.Constants;
import com.hooked.hook.IHookHandler;
import com.hooked.hook.impl.HookHandlerImpl;
import com.hooked.loot.ILootGenerator;
import com.hooked.loot.impl.ConfigLootGenerator;
import com.hooked.manager.IDebrisManager;
import com.hooked.manager.impl.DebrisManagerImpl;
import com.hooked.spawn.ISpawnController;
import com.hooked.spawn.impl.SpawnControllerImpl;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Objects;
import java.util.logging.Logger;

public final class HookedPlugin extends JavaPlugin {

    private Logger logger;
    private ConfigManager configManager;
    private IDebrisManager debrisManager;
    private ISpawnController spawnController;
    private IDriftAI driftAI;
    private IHookHandler hookHandler;
    private ILootGenerator lootGenerator;
    private HookedCommand commandHandler;

    @Override
    public void onEnable() {
        this.logger = getLogger();
        logger.info("Hooked v" + getPluginMeta().getVersion() + " enabling...");

        try {
            configManager = new ConfigManager(this);
            configManager.load();

            lootGenerator = new ConfigLootGenerator(this, configManager);
            debrisManager = new DebrisManagerImpl(this, configManager);

            spawnController = new SpawnControllerImpl(this, configManager, debrisManager);
            driftAI = new DefaultDriftAI(this, configManager, debrisManager);
            hookHandler = new HookHandlerImpl(this, configManager, debrisManager, lootGenerator);

            commandHandler = new HookedCommand(this, configManager, debrisManager);
            final PluginCommand cmd = Objects.requireNonNull(getCommand("hooked"));
            cmd.setExecutor(commandHandler);
            cmd.setTabCompleter(commandHandler);

            spawnController.start();
            driftAI.start();
            hookHandler.start();

            logger.info("Hooked enabled successfully. " + Constants.PLUGIN_NAME + " is ready.");
        } catch (final Exception e) {
            logger.severe("Failed to enable Hooked: " + e.getMessage());
            getServer().getPluginManager().disablePlugin(this);
        }
    }

    @Override
    public void onDisable() {
        logger.info("Hooked disabling...");

        try {
            if (hookHandler != null) hookHandler.stop();
            if (driftAI != null) driftAI.stop();
            if (spawnController != null) spawnController.stop();
            if (debrisManager != null) debrisManager.clearAll();

            logger.info("Hooked disabled. All debris cleared.");
        } catch (final Exception e) {
            logger.warning("Error during disable: " + e.getMessage());
        }
    }

    public ConfigManager getConfigManager() { return configManager; }
    public IDebrisManager getDebrisManager() { return debrisManager; }
}
