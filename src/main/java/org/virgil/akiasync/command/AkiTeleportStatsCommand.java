package org.virgil.akiasync.command;

import org.bukkit.command.CommandSender;
import org.bukkit.command.defaults.BukkitCommand;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.virgil.akiasync.bootstrap.AkiAsyncInitializer;
import org.virgil.akiasync.network.PlayerTeleportTracker;

/**
 * 传送统计命令 (Ignite 版本) / Teleport Statistics Command (Ignite Version)
 * 显示传送优化的详细性能指标 / Shows detailed performance metrics for teleport optimization
 */
public final class AkiTeleportStatsCommand extends BukkitCommand {
    
    public AkiTeleportStatsCommand() {
        super("aki-teleport-stats");
        this.setDescription("Shows teleport optimization statistics");
        this.setUsage("/aki-teleport-stats [reset]");
        this.setPermission("akiasync.admin");
    }

    @Override
    public boolean execute(@NonNull CommandSender sender, @NonNull String label, @NonNull String[] args) {
        if (!testPermission(sender)) {
            return true;
        }
        
        final AkiAsyncInitializer init = AkiAsyncInitializer.getInstance();
        if (init == null) {
            sender.sendMessage("§c[AkiAsync] 初始化器未准备好 / Initializer not ready");
            return true;
        }
        
        // Check if network optimization is enabled
        if (init.getConfigManager() == null || !init.getConfigManager().isNetworkOptimizationEnabled()) {
            sender.sendMessage("§c[AkiAsync] Network optimization is disabled");
            sender.sendMessage("§7Enable it in config.yml: network-optimization.enabled: true");
            return true;
        }
        
        // Handle reset command
        if (args.length > 0 && args[0].equalsIgnoreCase("reset")) {
            PlayerTeleportTracker.resetGlobalStatistics();
            sender.sendMessage("§a[AkiAsync] Teleport statistics reset successfully");
            return true;
        }
        
        // Display statistics
        displayTeleportStats(sender);
        return true;
    }
    
    private void displayTeleportStats(CommandSender sender) {
        sender.sendMessage("§b============ Teleport Statistics ============");
        
        // Get statistics from PlayerTeleportTracker
        long totalTeleports = PlayerTeleportTracker.getTotalTeleports();
        long optimizedTeleports = PlayerTeleportTracker.getOptimizedTeleports();
        long pendingTeleports = PlayerTeleportTracker.getPendingTeleportCount();
        double avgProcessTime = PlayerTeleportTracker.getAverageProcessTime();
        
        sender.sendMessage("§7[§bAkiAsync§7] §eTotal Teleports: §f" + totalTeleports);
        sender.sendMessage("§7[§bAkiAsync§7] §eOptimized Teleports: §f" + optimizedTeleports);
        sender.sendMessage("§7[§bAkiAsync§7] §ePending Teleports: §f" + pendingTeleports);
        sender.sendMessage("§7[§bAkiAsync§7] §eAverage Process Time: §f" + String.format("%.2f", avgProcessTime) + "ms");
        
        // Calculate optimization rate
        if (totalTeleports > 0) {
            double optimizationRate = (double) optimizedTeleports / totalTeleports * 100;
            sender.sendMessage("§7[§bAkiAsync§7] §eOptimization Rate: §f" + String.format("%.1f", optimizationRate) + "%");
        }
        
        // Network packet info
        sender.sendMessage("");
        sender.sendMessage("§e--- Network Packet Info ---");
        long packetsSent = PlayerTeleportTracker.getPacketsSent();
        long packetsOptimized = PlayerTeleportTracker.getPacketsOptimized();
        sender.sendMessage("§7[§bAkiAsync§7] §ePackets Sent: §f" + packetsSent);
        sender.sendMessage("§7[§bAkiAsync§7] §ePackets Optimized: §f" + packetsOptimized);
        
        if (packetsSent > 0) {
            double packetOptRate = (double) packetsOptimized / packetsSent * 100;
            sender.sendMessage("§7[§bAkiAsync§7] §ePacket Optimization Rate: §f" + String.format("%.1f", packetOptRate) + "%");
        }
        
        sender.sendMessage("");
        sender.sendMessage("§7Use §f/aki-teleport-stats reset §7to reset statistics");
        sender.sendMessage("§b=============================================");
    }
}
