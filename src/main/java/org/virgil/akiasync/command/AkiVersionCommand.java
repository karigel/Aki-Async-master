package org.virgil.akiasync.command;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.command.defaults.BukkitCommand;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.virgil.akiasync.bootstrap.AkiAsyncInitializer;
import org.virgil.akiasync.config.ConfigManager;

public final class AkiVersionCommand extends BukkitCommand {
    private static final String VERSION = "3.2.16-SNAPSHOT";
    
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
        
        final ConfigManager config = init.getConfigManager();
        final String prefix = "§7[§bAkiAsync§7] §f";
        final String on = "§a✓ ON";
        final String off = "§c✗ OFF";
        
        sender.sendMessage("§b============ AkiAsync Status ============");
        sender.sendMessage(prefix + "§eVersion: §f" + VERSION + " §7(Ignite Fork)");
        sender.sendMessage(prefix + "§eServer: §f" + Bukkit.getName() + " " + Bukkit.getVersion());
        sender.sendMessage(prefix + "§eJava: §f" + System.getProperty("java.version"));
        sender.sendMessage("");
        
        // Core Optimizations
        sender.sendMessage("§e--- Core Optimizations ---");
        sender.sendMessage(prefix + "Entity Tick Parallel: " + (config.isEntityTickParallel() ? on : off));
        sender.sendMessage(prefix + "Block Entity Parallel: " + (config.isBlockEntityParallelTickEnabled() ? on : off));
        sender.sendMessage(prefix + "Mob Spawning Async: " + (config.isMobSpawningEnabled() ? on : off));
        sender.sendMessage(prefix + "Async Lighting: " + (config.isAsyncLightingEnabled() ? on : off));
        sender.sendMessage(prefix + "TNT Optimization: " + (config.isTNTOptimizationEnabled() ? on : off));
        
        // v3.2.16 New Features
        sender.sendMessage("");
        sender.sendMessage("§e--- v3.2.16 Sakura Features ---");
        sender.sendMessage(prefix + "TNT Merge: " + (config.isTNTMergeEnabled() ? on : off));
        sender.sendMessage(prefix + "PandaWire Redstone: " + (config.isUsePandaWireAlgorithm() ? on : off));
        sender.sendMessage(prefix + "Redstone Network Cache: " + (config.isRedstoneNetworkCacheEnabled() ? on : off));
        sender.sendMessage(prefix + "SecureSeed: " + (config.isSecureSeedEnabled() ? on : off));
        sender.sendMessage(prefix + "Seed Encryption: " + (config.isSeedEncryptionEnabled() ? on : off));
        
        // Cache Statistics
        sender.sendMessage("");
        sender.sendMessage("§e--- Cache Statistics ---");
        try {
            int pandaWireCount = org.virgil.akiasync.mixin.async.redstone.RedstoneWireHelper.getEvaluatorCount();
            int redstoneCacheCount = org.virgil.akiasync.mixin.async.redstone.RedstoneNetworkCache.getCacheCount();
            sender.sendMessage(prefix + "PandaWire Evaluators: §f" + pandaWireCount);
            sender.sendMessage(prefix + "Redstone Cache Entries: §f" + redstoneCacheCount);
        } catch (Exception e) {
            sender.sendMessage(prefix + "Cache stats unavailable");
        }
        
        sender.sendMessage("§b==========================================");
        return true;
    }
}

