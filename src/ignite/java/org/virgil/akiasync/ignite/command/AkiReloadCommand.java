package org.virgil.akiasync.ignite.command;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.command.defaults.BukkitCommand;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.virgil.akiasync.ignite.AkiAsyncInitializer;
import org.virgil.akiasync.event.ConfigReloadEvent;

public final class AkiReloadCommand extends BukkitCommand {
    private static final Map<UUID, Long> confirmationMap = new ConcurrentHashMap<>();
    
    public AkiReloadCommand() {
        super("aki-reload");
        this.setDescription("Reloads the AkiAsync configuration");
        this.setPermission("akiasync.admin");
    }

    @Override
    public boolean execute(@NonNull CommandSender sender, @NonNull String label, @NonNull String[] args) {
        if (!testPermission(sender)) {
            return true;
        }
        
        final AkiAsyncInitializer init = AkiAsyncInitializer.getInstance();
        if (init == null) {
            sender.sendMessage("[AkiAsync] 初始化器未准备好");
            return true;
        }
        
        boolean requireConfirmation = true;
        
        if (!requireConfirmation) {
            performReload(sender, init);
            return true;
        }
        
        final int timeoutSeconds = 30;
        final UUID senderId = getSenderId(sender);
        final Long lastConfirmTime = confirmationMap.get(senderId);
        final long currentTime = System.currentTimeMillis();
        
        if (lastConfirmTime != null && (currentTime - lastConfirmTime) < timeoutSeconds * 1000L) {
            confirmationMap.remove(senderId);
            performReload(sender, init);
        } else {
            confirmationMap.put(senderId, currentTime);
            sendWarningMessage(sender, timeoutSeconds);
        }
        return true;
    }
    
    private void performReload(CommandSender sender, AkiAsyncInitializer init) {
        Bukkit.getPluginManager().callEvent(new ConfigReloadEvent());
        init.reloadConfig();
        sender.sendMessage(Component.text("[AkiAsync] ", NamedTextColor.GOLD)
            .append(Component.text("Configuration hot-reloaded.", NamedTextColor.GREEN)));
    }
    
    private void sendWarningMessage(CommandSender sender, int timeoutSeconds) {
        sender.sendMessage(Component.empty());
        sender.sendMessage(Component.text("⚠ ", NamedTextColor.YELLOW, TextDecoration.BOLD)
            .append(Component.text("[AkiAsync] RELOAD WARNING", NamedTextColor.RED, TextDecoration.BOLD))
            .append(Component.text(" ⚠", NamedTextColor.YELLOW, TextDecoration.BOLD)));
        sender.sendMessage(Component.empty());
        sender.sendMessage(Component.text("⚠ ", NamedTextColor.YELLOW)
            .append(Component.text("Execute again within ", NamedTextColor.GOLD))
            .append(Component.text(timeoutSeconds + "s", NamedTextColor.RED, TextDecoration.BOLD))
            .append(Component.text(" to confirm.", NamedTextColor.GOLD)));
        sender.sendMessage(Component.empty());
    }
    
    private UUID getSenderId(CommandSender sender) {
        if (sender instanceof org.bukkit.entity.Player player) {
            return player.getUniqueId();
        }
        return UUID.fromString("00000000-0000-0000-0000-000000000000");
    }
}
