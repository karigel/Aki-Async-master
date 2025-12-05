package org.virgil.akiasync.listener;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.server.ServerCommandEvent;
import org.bukkit.entity.Player;
import org.bukkit.ChatColor;
import org.virgil.akiasync.config.ConfigManager;
import org.virgil.akiasync.mixin.bridge.Bridge;
import org.virgil.akiasync.mixin.bridge.BridgeManager;

import java.util.logging.Logger;


public class SeedCommandListener implements Listener {
    
    private static final Logger LOGGER = Logger.getLogger("AkiAsync-SeedCommand");
    private final ConfigManager configManager;
    
    public SeedCommandListener(ConfigManager configManager) {
        this.configManager = configManager;
    }
    
    
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerCommand(PlayerCommandPreprocessEvent event) {
        Player player = event.getPlayer();
        String message = event.getMessage().toLowerCase().trim();
        

        if (!isSeedCommand(message)) {
            return;
        }
        

        if (!isEnabled()) {
            return;
        }
        

        if (!player.isOp()) {
            event.setCancelled(true);
            

            String denyMessage = getDenyMessage();
            player.sendMessage(ChatColor.RED + denyMessage);
            

            Bridge bridge = BridgeManager.getBridge();
            if (bridge != null && bridge.isSecureSeedDebugLogging()) {
                bridge.debugLog("[SecureSeed] Player %s attempted to use /seed command without OP permission",
                    player.getName());
            }
            
            LOGGER.warning(String.format(
                "Player %s attempted to use /seed command without OP permission",
                player.getName()
            ));
        }
    }
    
    
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onServerCommand(ServerCommandEvent event) {


    }
    
    
    private boolean isSeedCommand(String command) {

        if (command.startsWith("/")) {
            command = command.substring(1);
        }
        


        return command.equals("seed") || 
               command.startsWith("seed ") ||
               command.equals("minecraft:seed") ||
               command.startsWith("minecraft:seed ");
    }
    
    
    private boolean isEnabled() {
        try {
            Bridge bridge = BridgeManager.getBridge();
            return bridge != null && bridge.isSeedEncryptionEnabled();
        } catch (Exception e) {

            return true;
        }
    }
    
    
    private String getDenyMessage() {
        try {
            Bridge bridge = BridgeManager.getBridge();
            if (bridge != null) {
                String message = bridge.getSeedCommandDenyMessage();
                if (message != null && !message.isEmpty()) {
                    return ChatColor.translateAlternateColorCodes('&', message);
                }
            }
            return "You don't have permission to use this command. Only server operators can view the world seed.";
        } catch (Exception e) {
            return "You don't have permission to use this command. Only server operators can view the world seed.";
        }
    }
}
