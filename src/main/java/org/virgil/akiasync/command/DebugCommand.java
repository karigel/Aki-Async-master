package org.virgil.akiasync.command;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.jspecify.annotations.NullMarked;
import org.virgil.akiasync.AkiAsyncPlugin;
import org.virgil.akiasync.event.ConfigReloadEvent;

@NullMarked
public class DebugCommand implements CommandExecutor {
    private final AkiAsyncPlugin plugin;
    public DebugCommand(AkiAsyncPlugin plugin) {
        this.plugin = plugin;
    }
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length != 1) {
            sender.sendMessage("[AkiAsync] Usage: /aki-debug <true|false>");
            return true;
        }
        String arg = args[0].toLowerCase();
        boolean enableDebug;
        if (arg.equals("true") || arg.equals("on") || arg.equals("enable")) {
            enableDebug = true;
        } else if (arg.equals("false") || arg.equals("off") || arg.equals("disable")) {
            enableDebug = false;
        } else {
            sender.sendMessage("[AkiAsync] Invalid argument. Use 'true' or 'false'");
            return true;
        }
        try {
            plugin.getConfigManager().setDebugLoggingEnabled(enableDebug);
            sender.sendMessage("[AkiAsync] Debug logging " + (enableDebug ? "enabled" : "disabled") + " successfully!");
            Bukkit.getPluginManager().callEvent(new ConfigReloadEvent());
            sender.sendMessage("[AkiAsync] Configuration reloaded to apply debug changes.");
        } catch (Exception e) {
            sender.sendMessage("[AkiAsync] Failed to toggle debug logging: " + e.getMessage());
            plugin.getLogger().severe("Error toggling debug logging: " + e.getMessage());
        }
        return true;
    }
}
