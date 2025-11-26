package org.virgil.akiasync.command;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.command.defaults.BukkitCommand;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.virgil.akiasync.bootstrap.AkiAsyncInitializer;
import org.virgil.akiasync.event.ConfigReloadEvent;

public final class AkiDebugCommand extends BukkitCommand {
    public AkiDebugCommand() {
        super("aki-debug");
        this.setDescription("Toggle debug logging for AkiAsync");
        this.setPermission("akiasync.admin");
    }

    @Override
    public boolean execute(@NonNull CommandSender sender, @NonNull String label, @NonNull String[] args) {
        if (!testPermission(sender)) {
            return true;
        }
        
        if (args.length != 1) {
            sender.sendMessage("[AkiAsync] Usage: /aki-debug <true|false>");
            return true;
        }
        
        final AkiAsyncInitializer init = AkiAsyncInitializer.getInstance();
        if (init == null || init.getConfigManager() == null) {
            sender.sendMessage("[AkiAsync] 初始化器未准备好");
            return true;
        }
        
        final String arg = args[0].toLowerCase();
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
            init.getConfigManager().setDebugLoggingEnabled(enableDebug);
            sender.sendMessage("[AkiAsync] Debug logging " + (enableDebug ? "enabled" : "disabled") + " successfully!");
            Bukkit.getPluginManager().callEvent(new ConfigReloadEvent());
        } catch (Exception e) {
            sender.sendMessage("[AkiAsync] Failed to toggle debug logging: " + e.getMessage());
            init.getLogger().severe("Error toggling debug logging: " + e.getMessage());
        }
        return true;
    }
}

