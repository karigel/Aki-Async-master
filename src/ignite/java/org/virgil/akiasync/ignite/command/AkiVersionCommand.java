package org.virgil.akiasync.ignite.command;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.command.defaults.BukkitCommand;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.virgil.akiasync.ignite.AkiAsyncInitializer;
import org.virgil.akiasync.ignite.config.IgniteConfigManager;

public final class AkiVersionCommand extends BukkitCommand {
    private static final String VERSION = "3.2.16-SNAPSHOT";
    
    public AkiVersionCommand() {
        super("aki-version");
        this.setDescription("Shows the AkiAsync version and optimization status");
        this.setPermission("akiasync.version");
    }

    @Override
    public boolean execute(@NonNull CommandSender sender, @NonNull String label, @NonNull String[] args) {
        if (!testPermission(sender)) {
            return true;
        }
        
        final AkiAsyncInitializer init = AkiAsyncInitializer.getInstance();
        if (init == null || init.getConfigManager() == null) {
            sender.sendMessage("[AkiAsync] Initializer not ready");
            return true;
        }
        
        final IgniteConfigManager config = init.getConfigManager();
        final String p = "[AkiAsync] ";
        
        sender.sendMessage(p + "========================================");
        sender.sendMessage(p + "Plugin: AkiAsync");
        sender.sendMessage(p + "Version: " + VERSION + " (Ignite Fork)");
        sender.sendMessage(p + "Server: " + Bukkit.getName() + " " + Bukkit.getVersion());
        sender.sendMessage(p + "");
        sender.sendMessage(p + "Active Optimizations:");
        sender.sendMessage(p + "  Entity Tracker: " + (config.isEntityTrackerEnabled() ? "ON" : "OFF"));
        sender.sendMessage(p + "  Mob Spawning: " + (config.isMobSpawningEnabled() ? "ON" : "OFF"));
        sender.sendMessage(p + "  Entity Tick Parallel: " + (config.isEntityTickParallel() ? "ON" : "OFF"));
        sender.sendMessage(p + "  Block Entity Parallel: " + (config.isBlockEntityParallelTickEnabled() ? "ON" : "OFF"));
        sender.sendMessage(p + "  Async Lighting: " + (config.isAsyncLightingEnabled() ? "ON" : "OFF"));
        sender.sendMessage(p + "  Async Pathfinding: " + (config.isAsyncPathfindingEnabled() ? "ON" : "OFF"));
        sender.sendMessage(p + "  Chunk Tick Async: " + (config.isChunkTickAsyncEnabled() ? "ON" : "OFF"));
        sender.sendMessage(p + "  Brain Throttle: " + (config.isBrainThrottleEnabled() ? "ON" : "OFF"));
        sender.sendMessage(p + "  TNT Optimization: " + (config.isTNTOptimizationEnabled() ? "ON" : "OFF"));
        sender.sendMessage(p + "  Universal AI: " + (config.isUniversalAiOptimizationEnabled() ? "ON" : "OFF"));
        sender.sendMessage(p + "  Network Optimization: " + (config.isNetworkOptimizationEnabled() ? "ON" : "OFF"));
        sender.sendMessage(p + "  Fast Movement Chunk Load: " + (config.isFastMovementChunkLoadEnabled() ? "ON" : "OFF"));
        sender.sendMessage(p + "========================================");
        return true;
    }
}
