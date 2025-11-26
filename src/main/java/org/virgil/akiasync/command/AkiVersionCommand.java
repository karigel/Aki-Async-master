package org.virgil.akiasync.command;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.command.defaults.BukkitCommand;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.virgil.akiasync.bootstrap.AkiAsyncInitializer;

public final class AkiVersionCommand extends BukkitCommand {
    public AkiVersionCommand() {
        super("aki-version");
        this.setDescription("Shows the AkiAsync version and optimization status");
        this.setPermission("akiasync.admin");
    }

    @Override
    public boolean execute(@NonNull CommandSender sender, @NonNull String label, @NonNull String[] args) {
        if (!testPermission(sender)) {
            return true;
        }
        
        final AkiAsyncInitializer init = AkiAsyncInitializer.getInstance();
        if (init == null || init.getConfigManager() == null) {
            sender.sendMessage("[AkiAsync] 初始化器未准备好");
            return true;
        }
        
        final String prefix = "[AkiAsync] ";
        sender.sendMessage(prefix + "========================================");
        sender.sendMessage(prefix + "Plugin: AkiAsync");
        sender.sendMessage(prefix + "Version: 3.2.7-SNAPSHOT");
        sender.sendMessage(prefix + "");
        sender.sendMessage(prefix + "Server: " + Bukkit.getName() + " " + Bukkit.getVersion());
        sender.sendMessage(prefix + "Minecraft: " + Bukkit.getMinecraftVersion());
        sender.sendMessage(prefix + "Java: " + System.getProperty("java.version"));
        sender.sendMessage(prefix + "");
        sender.sendMessage(prefix + "Active Optimizations:");
        sender.sendMessage(prefix + "  Entity Tracker: " + (init.getConfigManager().isEntityTrackerEnabled() ? "ON" : "OFF"));
        sender.sendMessage(prefix + "  Mob Spawning: " + (init.getConfigManager().isMobSpawningEnabled() ? "ON" : "OFF"));
        sender.sendMessage(prefix + "  Entity Tick Parallel: " + (init.getConfigManager().isEntityTickParallel() ? "ON" : "OFF"));
        sender.sendMessage(prefix + "  Block Entity Parallel: " + (init.getConfigManager().isBlockEntityParallelTickEnabled() ? "ON" : "OFF"));
        sender.sendMessage(prefix + "  Async Lighting: " + (init.getConfigManager().isAsyncLightingEnabled() ? "ON" : "OFF"));
        sender.sendMessage(prefix + "  TNT Optimization: " + (init.getConfigManager().isTNTOptimizationEnabled() ? "ON" : "OFF"));
        sender.sendMessage(prefix + "========================================");
        return true;
    }
}

