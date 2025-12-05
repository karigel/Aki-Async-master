package org.virgil.akiasync.command;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.command.defaults.BukkitCommand;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.virgil.akiasync.bootstrap.AkiAsyncInitializer;
import org.virgil.akiasync.config.ConfigManager;

/**
 * Version command for Ignite mode
 * Output format matches upstream project
 */
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
        
        final ConfigManager config = init.getConfigManager();
        final String prefix = "[AkiAsync] ";
        
        // Match upstream output format exactly
        sender.sendMessage(prefix + "========================================");
        sender.sendMessage(prefix + "Plugin: AkiAsync");
        sender.sendMessage(prefix + "Version: " + VERSION + " (Ignite Fork)");
        sender.sendMessage(prefix + "Config Version: " + config.getCurrentConfigVersion());
        sender.sendMessage(prefix + "");
        sender.sendMessage(prefix + "Server: " + Bukkit.getName() + " " + Bukkit.getVersion());
        sender.sendMessage(prefix + "Minecraft: " + Bukkit.getMinecraftVersion());
        sender.sendMessage(prefix + "Java: " + System.getProperty("java.version"));
        sender.sendMessage(prefix + "OS: " + System.getProperty("os.name") + " " + System.getProperty("os.version"));
        sender.sendMessage(prefix + "");
        sender.sendMessage(prefix + "Active Optimizations:");
        sender.sendMessage(prefix + "  Entity Tracker: " + (config.isEntityTrackerEnabled() ? "ON" : "OFF"));
        sender.sendMessage(prefix + "  Mob Spawning: " + (config.isMobSpawningEnabled() ? "ON" : "OFF"));
        sender.sendMessage(prefix + "  Entity Tick Parallel: " + (config.isEntityTickParallel() ? "ON" : "OFF"));
        sender.sendMessage(prefix + "  Block Entity Parallel: " + (config.isBlockEntityParallelTickEnabled() ? "ON" : "OFF"));
        sender.sendMessage(prefix + "  Async Lighting: " + (config.isAsyncLightingEnabled() ? "ON" : "OFF"));
        sender.sendMessage(prefix + "  Async Pathfinding: " + (config.isAsyncPathfindingEnabled() ? "ON" : "OFF"));
        sender.sendMessage(prefix + "  Chunk Tick Async: " + (config.isChunkTickAsyncEnabled() ? "ON" : "OFF"));
        sender.sendMessage(prefix + "  Brain Throttle: " + (config.isBrainThrottleEnabled() ? "ON" : "OFF"));
        sender.sendMessage(prefix + "  TNT Optimization: " + (config.isTNTOptimizationEnabled() ? "ON" : "OFF"));
        sender.sendMessage(prefix + "  Universal AI: " + (config.isUniversalAiOptimizationEnabled() ? "ON" : "OFF"));
        sender.sendMessage(prefix + "  BeeFix: " + (config.isBeeFixEnabled() ? "ON" : "OFF"));
        sender.sendMessage(prefix + "  Structure Location Async: " + (config.isStructureLocationAsyncEnabled() ? "ON" : "OFF"));

        // Seed Encryption display (upstream format)
        if (config.isSeedEncryptionEnabled()) {
            String scheme = config.getSeedEncryptionScheme();
            if ("quantum".equalsIgnoreCase(scheme)) {
                sender.sendMessage(prefix + "  Seed Encryption: QuantumSeed (Level " + 
                    config.getQuantumSeedEncryptionLevel() + ")");
            } else {
                sender.sendMessage(prefix + "  Seed Encryption: SecureSeed (" + 
                    config.getSecureSeedBits() + " bits)");
            }
        } else {
            sender.sendMessage(prefix + "  Seed Encryption: OFF");
        }
        
        sender.sendMessage(prefix + "  Falling Block Parallel: " + (config.isFallingBlockParallelEnabled() ? "ON" : "OFF"));
        sender.sendMessage(prefix + "  Item Entity Parallel: " + (config.isItemEntityParallelEnabled() ? "ON" : "OFF"));
        sender.sendMessage(prefix + "  Item Entity Smart Merge: " + (config.isItemEntityMergeOptimizationEnabled() ? "ON" : "OFF"));
        sender.sendMessage(prefix + "  Item Entity Age Optimization: " + (config.isItemEntityAgeOptimizationEnabled() ? "ON" : "OFF"));
        sender.sendMessage(prefix + "  Minecart Cauldron Destruction: " + (config.isMinecartCauldronDestructionEnabled() ? "ON" : "OFF"));
        sender.sendMessage(prefix + "  Network Optimization: " + (config.isNetworkOptimizationEnabled() ? "ON" : "OFF"));
        sender.sendMessage(prefix + "  Fast Movement Chunk Load: " + (config.isFastMovementChunkLoadEnabled() ? "ON" : "OFF"));
        sender.sendMessage(prefix + "  Center Offset Loading: " + (config.isCenterOffsetEnabled() ? "ON" : "OFF"));
        sender.sendMessage(prefix + "========================================");
        return true;
    }
}

