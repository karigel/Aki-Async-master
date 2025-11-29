package org.virgil.akiasync.command;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.jspecify.annotations.NullMarked;
import org.virgil.akiasync.AkiAsyncPlugin;
import org.virgil.akiasync.network.NetworkOptimizationManager;
import org.virgil.akiasync.network.PlayerTeleportTracker;

/**
 * 传送统计命令 / Teleport Statistics Command
 * 显示传送优化的详细性能指标
 */
@NullMarked
public class TeleportStatsCommand implements CommandExecutor {
    private final AkiAsyncPlugin plugin;

    public TeleportStatsCommand(AkiAsyncPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        NetworkOptimizationManager manager = plugin.getNetworkOptimizationManager();
        if (manager == null) {
            sender.sendMessage("§c[AkiAsync] Network optimization manager not available");
            return true;
        }

        if (!manager.isTeleportOptimizationEnabled()) {
            sender.sendMessage("§c[AkiAsync] Teleport optimization is disabled");
            return true;
        }

        PlayerTeleportTracker tracker = manager.getTeleportTracker();
        if (tracker == null) {
            sender.sendMessage("§c[AkiAsync] Teleport tracker not available");
            return true;
        }

        if (args.length > 0 && args[0].equalsIgnoreCase("reset")) {
            tracker.resetStatistics();
            sender.sendMessage("§a[AkiAsync] Teleport statistics reset successfully");
            return true;
        }

        String stats = tracker.getDetailedStatistics();
        for (String line : stats.split("\n")) {
            sender.sendMessage("§7" + line);
        }
        return true;
    }
}
