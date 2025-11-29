package org.kari.kariClaims.commands;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.kari.kariClaims.KariClaims;
import org.kari.kariClaims.migration.UltimateClaimsMigrator;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 数据迁移命令
 * /kariclaims migrate <source> [options]
 */
public class MigrateCommand implements CommandExecutor, TabCompleter {
    private final KariClaims plugin;
    
    public MigrateCommand(KariClaims plugin) {
        this.plugin = plugin;
    }
    
    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, 
            @NotNull String label, @NotNull String[] args) {
        
        if (!sender.hasPermission("kariclaims.admin.migrate")) {
            sender.sendMessage("§c你没有权限执行此命令！");
            return true;
        }
        
        if (args.length < 1) {
            sendHelp(sender);
            return true;
        }
        
        String subCommand = args[0].toLowerCase();
        
        switch (subCommand) {
            case "ultimateclaims":
            case "uc":
                return handleUltimateClaimsMigration(sender, args);
            case "help":
            default:
                sendHelp(sender);
                return true;
        }
    }
    
    private boolean handleUltimateClaimsMigration(CommandSender sender, String[] args) {
        // 默认数据库文件路径
        String dbPath = "plugins/UltimateClaims/database.db";
        String tablePrefix = "ultimateclaims_";
        
        // 解析参数
        for (int i = 1; i < args.length; i++) {
            if (args[i].startsWith("--file=")) {
                dbPath = args[i].substring(7);
            } else if (args[i].startsWith("--prefix=")) {
                tablePrefix = args[i].substring(9);
            }
        }
        
        File dbFile = new File(dbPath);
        
        // 如果路径是相对的，从服务器根目录开始
        if (!dbFile.isAbsolute()) {
            dbFile = new File(plugin.getDataFolder().getParentFile().getParentFile(), dbPath);
        }
        
        if (!dbFile.exists()) {
            sender.sendMessage("§c找不到 UltimateClaims 数据库文件！");
            sender.sendMessage("§7尝试的路径: " + dbFile.getAbsolutePath());
            sender.sendMessage("§7使用: /kariclaims migrate uc --file=<path>");
            return true;
        }
        
        sender.sendMessage("§e正在从 UltimateClaims 迁移数据...");
        sender.sendMessage("§7数据库文件: " + dbFile.getAbsolutePath());
        sender.sendMessage("§7表前缀: " + tablePrefix);
        sender.sendMessage("§c警告: 请确保已备份数据库！");
        
        UltimateClaimsMigrator migrator = new UltimateClaimsMigrator(plugin);
        migrator.setTablePrefix(tablePrefix);
        
        migrator.migrateFromSQLite(dbFile, sender).thenAccept(result -> {
            if (!result.isSuccess()) {
                sender.sendMessage("§c迁移失败: " + result.getError());
            }
            
            // 输出警告
            if (!result.getWarnings().isEmpty()) {
                sender.sendMessage("§e警告信息:");
                for (String warning : result.getWarnings()) {
                    sender.sendMessage("§7- " + warning);
                }
            }
            
            // 刷新缓存
            if (result.isSuccess()) {
                plugin.getChunkClaimManager().reloadCache().thenRun(() -> {
                    sender.sendMessage("§a缓存已刷新！");
                });
            }
        });
        
        return true;
    }
    
    private void sendHelp(CommandSender sender) {
        sender.sendMessage("§6=== KariClaims 数据迁移 ===");
        sender.sendMessage("§e/kariclaims migrate ultimateclaims §7- 从 UltimateClaims 迁移数据");
        sender.sendMessage("§7  --file=<path> - 指定数据库文件路径");
        sender.sendMessage("§7  --prefix=<prefix> - 指定表前缀 (默认: ultimateclaims_)");
        sender.sendMessage("");
        sender.sendMessage("§7示例:");
        sender.sendMessage("§f  /kariclaims migrate uc");
        sender.sendMessage("§f  /kariclaims migrate uc --file=plugins/UltimateClaims/database.db");
    }
    
    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, 
            @NotNull String alias, @NotNull String[] args) {
        
        if (!sender.hasPermission("kariclaims.admin.migrate")) {
            return new ArrayList<>();
        }
        
        if (args.length == 1) {
            return Arrays.asList("ultimateclaims", "uc", "help").stream()
                .filter(s -> s.startsWith(args[0].toLowerCase()))
                .collect(Collectors.toList());
        }
        
        if (args.length >= 2 && (args[0].equalsIgnoreCase("ultimateclaims") || args[0].equalsIgnoreCase("uc"))) {
            List<String> options = Arrays.asList(
                "--file=plugins/UltimateClaims/database.db",
                "--prefix=ultimateclaims_"
            );
            return options.stream()
                .filter(s -> s.startsWith(args[args.length - 1]))
                .collect(Collectors.toList());
        }
        
        return new ArrayList<>();
    }
}
