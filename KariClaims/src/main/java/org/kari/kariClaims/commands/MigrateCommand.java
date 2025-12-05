package org.kari.kariClaims.commands;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.kari.kariClaims.KariClaims;
import org.kari.kariClaims.migration.UltimateClaimsMigrator;

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
        
        // 命令格式: /kariclaims migrate <source> [options]
        if (args.length < 1 || !args[0].equalsIgnoreCase("migrate")) {
            sendHelp(sender);
            return true;
        }
        
        if (args.length < 2) {
            sendHelp(sender);
            return true;
        }
        
        String source = args[1].toLowerCase();
        
        switch (source) {
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
        String tablePrefix = plugin.getConfig().getString("migration.ultimateclaims-table-prefix", "ultimateclaims_");
        
        // MariaDB 连接参数
        String host = null;
        int port = 3306;
        String database = null;
        String user = null;
        String password = "";
        
        // 解析参数 (从 args[2] 开始，因为 args[0]="migrate", args[1]="uc")
        for (int i = 2; i < args.length; i++) {
            if (args[i].startsWith("--prefix=")) {
                tablePrefix = args[i].substring(9);
            } else if (args[i].startsWith("--host=")) {
                host = args[i].substring(7);
            } else if (args[i].startsWith("--port=")) {
                try {
                    port = Integer.parseInt(args[i].substring(7));
                } catch (NumberFormatException e) {
                    sender.sendMessage("§c无效的端口号: " + args[i].substring(7));
                    return true;
                }
            } else if (args[i].startsWith("--database=")) {
                database = args[i].substring(11);
            } else if (args[i].startsWith("--user=")) {
                user = args[i].substring(7);
            } else if (args[i].startsWith("--password=")) {
                password = args[i].substring(11);
            }
        }
        
        // 检查必需参数
        if (host == null || database == null || user == null) {
            sender.sendMessage("§c缺少必需的数据库连接参数！");
            sendHelp(sender);
            return true;
        }
        
        sender.sendMessage("§e正在从 UltimateClaims (MariaDB) 迁移数据...");
        sender.sendMessage("§7数据库: " + host + ":" + port + "/" + database);
        sender.sendMessage("§7表前缀: " + tablePrefix);
        sender.sendMessage("§c警告: 请确保已备份数据库！");
        
        UltimateClaimsMigrator migrator = new UltimateClaimsMigrator(plugin);
        migrator.setTablePrefix(tablePrefix);
        
        final String finalHost = host;
        final int finalPort = port;
        final String finalDatabase = database;
        final String finalUser = user;
        final String finalPassword = password;
        
        migrator.migrateFromMariaDB(finalHost, finalPort, finalDatabase, finalUser, finalPassword, sender).thenAccept(result -> {
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
                // 在主线程生成物理能量箱
                if (!result.getGeneratedPowerCells().isEmpty()) {
                    plugin.getServer().getScheduler().runTask(plugin, () -> {
                        int generated = 0;
                        for (UltimateClaimsMigrator.PowerCellData data : result.getGeneratedPowerCells()) {
                            org.bukkit.World world = org.bukkit.Bukkit.getWorld(data.world);
                            if (world != null) {
                                org.bukkit.block.Block block = world.getBlockAt(data.x, data.y, data.z);
                                // 安全检查：只在空气或非固体方块位置生成箱子
                                if (block.getType() == org.bukkit.Material.CHEST) {
                                    // 已经是箱子，尝试恢复物品（如果箱子为空）
                                    restoreInventory((org.bukkit.block.Chest) block.getState(), data.inventoryBase64);
                                } else if (block.getType().isAir() || !block.getType().isSolid()) {
                                    block.setType(org.bukkit.Material.CHEST);
                                    restoreInventory((org.bukkit.block.Chest) block.getState(), data.inventoryBase64);
                                    generated++;
                                } else {
                                    // 位置被占用，尝试寻找新位置
                                    org.bukkit.Location safeLoc = findSafeLocation(world, data.x, data.y, data.z);
                                    if (safeLoc != null) {
                                        org.bukkit.block.Block newBlock = safeLoc.getBlock();
                                        newBlock.setType(org.bukkit.Material.CHEST);
                                        restoreInventory((org.bukkit.block.Chest) newBlock.getState(), data.inventoryBase64);
                                        generated++;
                                        
                                        sender.sendMessage("§e[通知] 原能量箱位置被占用，已迁移至: " + 
                                            safeLoc.getBlockX() + ", " + safeLoc.getBlockY() + ", " + safeLoc.getBlockZ());
                                            
                                        // 更新数据库
                                        if (data.claimId != -1) {
                                            plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
                                                try (java.sql.Connection conn = plugin.getDatabaseManager().getConnection();
                                                     java.sql.PreparedStatement ps = conn.prepareStatement(
                                                        "UPDATE power_cells SET x=?, y=?, z=? WHERE claim_id=?")) {
                                                    ps.setInt(1, safeLoc.getBlockX());
                                                    ps.setInt(2, safeLoc.getBlockY());
                                                    ps.setInt(3, safeLoc.getBlockZ());
                                                    ps.setInt(4, data.claimId);
                                                    ps.executeUpdate();
                                                } catch (Exception e) {
                                                    e.printStackTrace();
                                                }
                                            });
                                        }
                                    } else {
                                        sender.sendMessage("§c[警告] 无法在 " + data.world + " " + data.x + "," + data.y + "," + data.z + 
                                            " 附近找到生成能量箱的安全位置！");
                                    }
                                }
                            }
                        }
                        if (generated > 0) {
                            sender.sendMessage("§a已在世界中生成 " + generated + " 个物理能量箱！");
                        }
                    });
                }
                
                plugin.getChunkClaimManager().reloadCache().thenRun(() -> {
                    sender.sendMessage("§a缓存已刷新！");
                });
            }
        });
        
        return true;
    }
    
    @SuppressWarnings("deprecation")
    private void restoreInventory(org.bukkit.block.Chest chest, String inventoryBase64) {
        if (inventoryBase64 == null || inventoryBase64.isEmpty()) return;
        
        // 只有当箱子为空时才尝试填充，避免覆盖
        boolean isEmpty = true;
        for (org.bukkit.inventory.ItemStack item : chest.getInventory().getContents()) {
            if (item != null && item.getType() != org.bukkit.Material.AIR) {
                isEmpty = false;
                break;
            }
        }
        
        if (!isEmpty) return;
        
        try {
            // 尝试标准 Bukkit 反序列化
            byte[] data = java.util.Base64.getDecoder().decode(inventoryBase64);
            java.io.ByteArrayInputStream bais = new java.io.ByteArrayInputStream(data);
            org.bukkit.util.io.BukkitObjectInputStream bois = new org.bukkit.util.io.BukkitObjectInputStream(bais);
            
            try {
                Object obj = bois.readObject();
                
                if (obj instanceof Integer) {
                    // 模式 1: 先写入数量，再写入每个物品
                    int count = (Integer) obj;
                    for (int i = 0; i < count; i++) {
                        try {
                            Object itemObj = bois.readObject();
                            if (itemObj instanceof org.bukkit.inventory.ItemStack) {
                                chest.getInventory().addItem((org.bukkit.inventory.ItemStack) itemObj);
                            }
                        } catch (Exception e) {
                            // 读取单个物品失败，继续尝试下一个
                        }
                    }
                } else if (obj instanceof org.bukkit.inventory.ItemStack[]) {
                    // 模式 2: 直接写入 ItemStack 数组
                    chest.getInventory().setContents((org.bukkit.inventory.ItemStack[]) obj);
                } else if (obj instanceof List) {
                    // 模式 3: 写入 List<ItemStack>
                    List<?> list = (List<?>) obj;
                    for (Object item : list) {
                        if (item instanceof org.bukkit.inventory.ItemStack) {
                            chest.getInventory().addItem((org.bukkit.inventory.ItemStack) item);
                        }
                    }
                } else if (obj instanceof org.bukkit.inventory.ItemStack) {
                    // 模式 4: 单个物品
                    chest.getInventory().addItem((org.bukkit.inventory.ItemStack) obj);
                }
            } finally {
                bois.close();
            }
        } catch (Exception e) {
            // 如果失败，记录日志，不影响箱子生成
            plugin.getLogger().warning("无法还原能量箱物品: " + e.getMessage());
        }
        
        // 必须调用 update() 才能将更改应用到世界中
        chest.update();
    }

    private org.bukkit.Location findSafeLocation(org.bukkit.World world, int x, int y, int z) {
        // 以原点为中心，半径3的范围内搜索
        for (int r = 1; r <= 3; r++) {
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    int nx = x + dx;
                    int nz = z + dz;
                    
                    // 在垂直方向搜索，范围 +/- 5
                    for (int dy = -5; dy <= 5; dy++) {
                        int ny = y + dy;
                        if (ny < world.getMinHeight() || ny >= world.getMaxHeight()) continue;
                        
                        org.bukkit.block.Block block = world.getBlockAt(nx, ny, nz);
                        org.bukkit.block.Block below = world.getBlockAt(nx, ny - 1, nz);
                        
                        // 条件：当前方块非固体（空气/草等），下方方块是固体
                        if ((block.getType().isAir() || !block.getType().isSolid()) && below.getType().isSolid()) {
                            return new org.bukkit.Location(world, nx, ny, nz);
                        }
                    }
                }
            }
        }
        return null;
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage("§6=== KariClaims 数据迁移 ===");
        sender.sendMessage("§e/kariclaims migrate uc §7- 从 UltimateClaims (MariaDB) 迁移数据");
        sender.sendMessage("");
        sender.sendMessage("§7必需参数:");
        sender.sendMessage("§f  --host=<地址> §7- 数据库服务器地址");
        sender.sendMessage("§f  --database=<数据库名> §7- 数据库名称");
        sender.sendMessage("§f  --user=<用户名> §7- 数据库用户名");
        sender.sendMessage("");
        sender.sendMessage("§7可选参数:");
        sender.sendMessage("§f  --port=<端口> §7- 数据库端口 (默认: 3306)");
        sender.sendMessage("§f  --password=<密码> §7- 数据库密码 (默认: 空)");
        sender.sendMessage("§f  --prefix=<前缀> §7- 表前缀 (默认: ultimateclaims_)");
        sender.sendMessage("");
        sender.sendMessage("§7示例:");
        sender.sendMessage("§f  /kariclaims migrate uc --host=localhost --database=ultimateclaims --user=root");
        sender.sendMessage("§f  /kariclaims migrate uc --host=127.0.0.1 --database=uc --user=mc --password=123456");
    }
    
    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, 
            @NotNull String alias, @NotNull String[] args) {
        
        if (!sender.hasPermission("kariclaims.admin.migrate")) {
            return new ArrayList<>();
        }
        
        // args[0] = "migrate"
        if (args.length == 1) {
            return Arrays.asList("migrate").stream()
                .filter(s -> s.startsWith(args[0].toLowerCase()))
                .collect(Collectors.toList());
        }
        
        // args[1] = source (uc, ultimateclaims)
        if (args.length == 2 && args[0].equalsIgnoreCase("migrate")) {
            return Arrays.asList("ultimateclaims", "uc", "help").stream()
                .filter(s -> s.startsWith(args[1].toLowerCase()))
                .collect(Collectors.toList());
        }
        
        // args[2+] = options
        if (args.length >= 3 && args[0].equalsIgnoreCase("migrate") && 
                (args[1].equalsIgnoreCase("ultimateclaims") || args[1].equalsIgnoreCase("uc"))) {
            List<String> options = Arrays.asList(
                "--host=localhost",
                "--port=3306",
                "--database=",
                "--user=",
                "--password=",
                "--prefix=ultimateclaims_"
            );
            return options.stream()
                .filter(s -> s.startsWith(args[args.length - 1]))
                .collect(Collectors.toList());
        }
        
        return new ArrayList<>();
    }
}
