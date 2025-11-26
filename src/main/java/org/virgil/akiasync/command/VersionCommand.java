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
        sender.sendMessage(prefix + "Plugin: " + plugin.getDescription().getName());
        sender.sendMessage(prefix + "Version: " + plugin.getDescription().getVersion());
        sender.sendMessage(prefix + "Authors: " + String.join(", ", plugin.getDescription().getAuthors()));
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
        sender.sendMessage(prefix + "  Chunk Tick Async: " + (plugin.getConfigManager().isChunkTickAsyncEnabled() ? "ON" : "OFF"));
        sender.sendMessage(prefix + "  Brain Throttle: " + (plugin.getConfigManager().isBrainThrottleEnabled() ? "ON" : "OFF"));
        sender.sendMessage(prefix + "  TNT Optimization: " + (plugin.getConfigManager().isTNTOptimizationEnabled() ? "ON" : "OFF"));
        sender.sendMessage(prefix + "  Universal AI: " + (plugin.getConfigManager().isUniversalAiOptimizationEnabled() ? "ON" : "OFF"));
        sender.sendMessage(prefix + "  BeeFix: " + (plugin.getConfigManager().isBeeFixEnabled() ? "ON" : "OFF"));
        sender.sendMessage(prefix + "  Structure Location Async: " + (plugin.getConfigManager().isStructureLocationAsyncEnabled() ? "ON" : "OFF"));
        sender.sendMessage(prefix + "  SecureSeed: " + (plugin.getConfigManager().isSecureSeedEnabled() ? "ON" : "OFF"));
        sender.sendMessage(prefix + "========================================");
        return true;
    }
}
