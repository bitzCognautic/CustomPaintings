package dev.custompaintings;

import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

public final class CustomPaintingsPlugin extends JavaPlugin {
    private PaintingManager paintingManager;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        this.paintingManager = new PaintingManager(this);
        this.paintingManager.loadIndex();

        PluginCommand paintingCommand = getCommand("painting");
        if (paintingCommand == null) {
            getLogger().severe("Command 'painting' is missing from plugin.yml");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        PaintingCommand executor = new PaintingCommand(paintingManager);
        paintingCommand.setExecutor(executor);
        paintingCommand.setTabCompleter(executor);

        getLogger().info("CustomPaintings enabled. Upload PNG files to: " + paintingManager.getUploadsDir().getAbsolutePath());
    }

    @Override
    public void onDisable() {
        if (paintingManager != null) {
            paintingManager.saveIndex();
        }
    }
}
