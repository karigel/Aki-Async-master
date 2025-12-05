package org.virgil.akiasync.command;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.jspecify.annotations.NullMarked;
import org.virgil.akiasync.AkiAsyncPlugin;

@NullMarked
public class VersionCommand implements CommandExecutor {
    private final AkiAsyncPlugin plugin;
    public VersionCommand(AkiAsyncPlugin plugin) {
        this.plugin = plugin;
    }
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String prefix = "[AkiAsync] ";
        sender.sendMessage(prefix + "========================================");
        sender.sendMessage(prefix + "Plugin: " + plugin.getPluginMeta().getName());
        sender.sendMessage(prefix + "Version: " + plugin.getPluginMeta().getVersion());
        sender.sendMessage(prefix + "Authors: " + String.join(", ", plugin.getPluginMeta().getAuthors()));
        sender.sendMessage(prefix + "Config Version: " + plugin.getConfigManager().getCurrentConfigVersion());
        sender.sendMessage(prefix + "");
        sender.sendMessage(prefix + "Server: " + Bukkit.getName() + " " + Bukkit.getVersion());
        sender.sendMessage(prefix + "Minecraft: " + Bukkit.getMinecraftVersion());
        sender.sendMessage(prefix + "Java: " + System.getProperty("java.version"));
        sender.sendMessage(prefix + "OS: " + System.getProperty("os.name") + " " + System.getProperty("os.version"));
        sender.sendMessage(prefix + "");
        sender.sendMessage(prefix + "Active Optimizations:");
        sender.sendMessage(prefix + "  Entity Tracker: " + (plugin.getConfigManager().isEntityTrackerEnabled() ? "ON" : "OFF"));
        sender.sendMessage(prefix + "  Mob Spawning: " + (plugin.getConfigManager().isMobSpawningEnabled() ? "ON" : "OFF"));
        sender.sendMessage(prefix + "  Entity Tick Parallel: " + (plugin.getConfigManager().isEntityTickParallel() ? "ON" : "OFF"));
        sender.sendMessage(prefix + "  Block Entity Parallel: " + (plugin.getConfigManager().isBlockEntityParallelTickEnabled() ? "ON" : "OFF"));
        sender.sendMessage(prefix + "  Async Lighting: " + (plugin.getConfigManager().isAsyncLightingEnabled() ? "ON" : "OFF"));
        sender.sendMessage(prefix + "  Async Pathfinding: " + (plugin.getConfigManager().isAsyncPathfindingEnabled() ? "ON" : "OFF"));
        sender.sendMessage(prefix + "  Chunk Tick Async: " + (plugin.getConfigManager().isChunkTickAsyncEnabled() ? "ON" : "OFF"));
        sender.sendMessage(prefix + "  Brain Throttle: " + (plugin.getConfigManager().isBrainThrottleEnabled() ? "ON" : "OFF"));
        sender.sendMessage(prefix + "  TNT Optimization: " + (plugin.getConfigManager().isTNTOptimizationEnabled() ? "ON" : "OFF"));
        sender.sendMessage(prefix + "  Universal AI: " + (plugin.getConfigManager().isUniversalAiOptimizationEnabled() ? "ON" : "OFF"));
        sender.sendMessage(prefix + "  BeeFix: " + (plugin.getConfigManager().isBeeFixEnabled() ? "ON" : "OFF"));
        sender.sendMessage(prefix + "  Structure Location Async: " + (plugin.getConfigManager().isStructureLocationAsyncEnabled() ? "ON" : "OFF"));
        
        // Seed Encryption display
        if (plugin.getConfigManager().isSeedEncryptionEnabled()) {
            String scheme = plugin.getConfigManager().getSeedEncryptionScheme();
            if ("quantum".equalsIgnoreCase(scheme)) {
                sender.sendMessage(prefix + "  Seed Encryption: QuantumSeed (Level " + 
                    plugin.getConfigManager().getQuantumSeedEncryptionLevel() + ")");
            } else {
                sender.sendMessage(prefix + "  Seed Encryption: SecureSeed (" + 
                    plugin.getConfigManager().getSecureSeedBits() + " bits)");
            }
        } else {
            sender.sendMessage(prefix + "  Seed Encryption: OFF");
        }
        
        sender.sendMessage(prefix + "  Falling Block Parallel: " + (plugin.getConfigManager().isFallingBlockParallelEnabled() ? "ON" : "OFF"));
        sender.sendMessage(prefix + "  Item Entity Parallel: " + (plugin.getConfigManager().isItemEntityParallelEnabled() ? "ON" : "OFF"));
        sender.sendMessage(prefix + "  Item Entity Smart Merge: " + (plugin.getConfigManager().isItemEntityMergeOptimizationEnabled() ? "ON" : "OFF"));
        sender.sendMessage(prefix + "  Item Entity Age Optimization: " + (plugin.getConfigManager().isItemEntityAgeOptimizationEnabled() ? "ON" : "OFF"));
        sender.sendMessage(prefix + "  Minecart Cauldron Destruction: " + (plugin.getConfigManager().isMinecartCauldronDestructionEnabled() ? "ON" : "OFF"));
        sender.sendMessage(prefix + "  Network Optimization: " + (plugin.getConfigManager().isNetworkOptimizationEnabled() ? "ON" : "OFF"));
        sender.sendMessage(prefix + "  Fast Movement Chunk Load: " + (plugin.getConfigManager().isFastMovementChunkLoadEnabled() ? "ON" : "OFF"));
        sender.sendMessage(prefix + "  Center Offset Loading: " + (plugin.getConfigManager().isCenterOffsetEnabled() ? "ON" : "OFF"));
        
        // v3.2.16 Sakura Features
        sender.sendMessage(prefix + "");
        sender.sendMessage(prefix + "v3.2.16 Sakura Features:");
        sender.sendMessage(prefix + "  TNT Merge: " + (plugin.getConfigManager().isTNTMergeEnabled() ? "ON" : "OFF"));
        sender.sendMessage(prefix + "  PandaWire Redstone: " + (plugin.getConfigManager().isUsePandaWireAlgorithm() ? "ON" : "OFF"));
        sender.sendMessage(prefix + "  Redstone Network Cache: " + (plugin.getConfigManager().isRedstoneNetworkCacheEnabled() ? "ON" : "OFF"));
        sender.sendMessage(prefix + "========================================");
        return true;
    }
}
