package org.virgil.akiasync.ignite.command;

import org.bukkit.command.CommandSender;
import org.bukkit.command.defaults.BukkitCommand;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.virgil.akiasync.ignite.IgnitePluginAdapter;

public final class AkiTeleportStatsCommand extends BukkitCommand {
    
    public AkiTeleportStatsCommand() {
        super("aki-teleport-stats");
        this.setDescription("Shows teleport optimization statistics");
        this.setPermission("akiasync.admin");
    }

    @Override
    public boolean execute(@NonNull CommandSender sender, @NonNull String label, @NonNull String[] args) {
        if (!testPermission(sender)) {
            return true;
        }
        
        try {
            if (!IgnitePluginAdapter.isInitialized()) {
                sender.sendMessage("[AkiAsync] IgnitePluginAdapter not initialized");
                return true;
            }
            
            var tracker = IgnitePluginAdapter.getInstance().getTeleportTracker();
            if (tracker == null) {
                sender.sendMessage("[AkiAsync] TeleportTracker not available");
                return true;
            }
            
            sender.sendMessage("[AkiAsync] ====== Teleport Statistics ======");
            sender.sendMessage("[AkiAsync] " + tracker.getStatistics());
            sender.sendMessage("[AkiAsync] ================================");
        } catch (Exception e) {
            sender.sendMessage("[AkiAsync] Error: " + e.getMessage());
        }
        return true;
    }
}
