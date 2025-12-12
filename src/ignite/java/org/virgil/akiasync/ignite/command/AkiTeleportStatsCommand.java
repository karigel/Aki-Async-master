package org.virgil.akiasync.ignite.command;

import org.bukkit.command.CommandSender;
import org.bukkit.command.defaults.BukkitCommand;
import org.checkerframework.checker.nullness.qual.NonNull;

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
        
        // TeleportTracker was removed in upstream update
        sender.sendMessage("[AkiAsync] Teleport statistics feature has been simplified in this version.");
        sender.sendMessage("[AkiAsync] Network optimization is handled at the Mixin level.");
        return true;
    }
}
