package org.virgil.akiasync.command;

import org.bukkit.command.CommandSender;
import org.bukkit.command.defaults.BukkitCommand;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.virgil.akiasync.bootstrap.AkiAsyncInitializer;
import org.virgil.akiasync.network.PlayerTeleportTracker;

/**
 * Teleport Statistics Command (Ignite Version)
 * Output format matches upstream project
 */
public final class AkiTeleportStatsCommand extends BukkitCommand {
    
    public AkiTeleportStatsCommand() {
        super("aki-teleport-stats");
        this.setDescription("Shows teleport optimization statistics");
        this.setUsage("/aki-teleport-stats [reset]");
        this.setPermission("akiasync.teleportstats");
    }

    @Override
    public boolean execute(@NonNull CommandSender sender, @NonNull String label, @NonNull String[] args) {
        if (!testPermission(sender)) {
            return true;
        }
        
        final AkiAsyncInitializer init = AkiAsyncInitializer.getInstance();
        if (init == null) {
            sender.sendMessage("[AkiAsync] Initializer not ready");
            return true;
        }
        
        // Check if network optimization is enabled
        if (init.getConfigManager() == null || !init.getConfigManager().isNetworkOptimizationEnabled()) {
            sender.sendMessage("[AkiAsync] Network optimization is disabled");
            return true;
        }
        
        // Handle reset command
        if (args.length > 0 && args[0].equalsIgnoreCase("reset")) {
            PlayerTeleportTracker.resetGlobalStatistics();
            sender.sendMessage("[AkiAsync] Teleport statistics reset successfully");
            return true;
        }
        
        // Display statistics (upstream format)
        PlayerTeleportTracker tracker = PlayerTeleportTracker.getGlobalInstance();
        if (tracker != null) {
            String stats = tracker.getDetailedStatistics();
            for (String line : stats.split("\n")) {
                sender.sendMessage(line);
            }
        } else {
            // Fallback to static statistics
            sender.sendMessage("========== Teleport Tracker Statistics ==========");
            sender.sendMessage("Active teleports: " + PlayerTeleportTracker.getPendingTeleportCount());
            sender.sendMessage("Total teleports: " + PlayerTeleportTracker.getTotalTeleports());
            sender.sendMessage("Successful: " + PlayerTeleportTracker.getOptimizedTeleports());
            sender.sendMessage("Average delay: " + String.format("%.2f", PlayerTeleportTracker.getAverageProcessTime()) + "ms");
            sender.sendMessage("Packets sent: " + PlayerTeleportTracker.getPacketsSent());
            sender.sendMessage("Packets optimized: " + PlayerTeleportTracker.getPacketsOptimized());
            sender.sendMessage("================================================");
        }
        return true;
    }
}
