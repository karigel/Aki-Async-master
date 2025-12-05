package org.kari.kariClaims.commands;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.kari.kariClaims.KariClaims;
import org.kari.kariClaims.models.ChunkClaim;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 管理员命令处理器
 */
public class AdminCommand implements CommandExecutor, TabCompleter {
    private final KariClaims plugin;

    public AdminCommand(KariClaims plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }

        String subCommand = args[0].toLowerCase();

        switch (subCommand) {
            case "reload":
                handleReload(sender);
                break;
            case "rename":
                handleRename(sender, args);
                break;
            case "lock":
                handleLock(sender);
                break;
            case "removeclaim":
                handleRemoveClaim(sender);
                break;
            case "transfer":
                handleTransfer(sender, args);
                break;
            case "list":
                handleAdminList(sender, args);
                break;
            default:
                sendHelp(sender);
        }

        return true;
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage("§6=== KariClaims 管理员命令 ===");
        sender.sendMessage("§e/claim admin reload §7- 重新加载配置");
        sender.sendMessage("§e/claim admin list <玩家> §7- 查看玩家的所有领地");
        sender.sendMessage("§e/claim admin rename <名称> §7- 强制重命名领地");
        sender.sendMessage("§e/claim admin lock §7- 强制锁定/解锁领地");
        sender.sendMessage("§e/claim admin removeclaim §7- 强制删除领地");
        sender.sendMessage("§e/claim admin transfer <玩家> §7- 强制转移领地");
    }
    
    /**
     * 管理员查看玩家领地列表，或列出服务器所有领地
     */
    private void handleAdminList(CommandSender sender, String[] args) {
        if (!sender.hasPermission("kariclaims.admin.list")) {
            sender.sendMessage("§c你没有权限执行此命令！");
            return;
        }
        
        if (args.length < 2) {
            // 列出服务器中所有领地
            listAllRegions(sender);
            return;
        }
        
        String targetName = args[1];
        org.bukkit.OfflinePlayer target = org.bukkit.Bukkit.getOfflinePlayer(targetName);
        
        if (target == null || !target.hasPlayedBefore()) {
            sender.sendMessage("§c找不到玩家: " + targetName);
            return;
        }
        
        plugin.getChunkClaimManager().getPlayerRegionsAsync(target.getUniqueId())
            .thenAccept(regions -> {
                if (regions.isEmpty()) {
                    sender.sendMessage("§c玩家 " + targetName + " 没有任何领地！");
                    return;
                }
                
                sender.sendMessage("§6=== " + targetName + " 的领地列表 ===");
                for (org.kari.kariClaims.models.ClaimRegion region : regions) {
                    String status = region.isLocked() ? "§c[锁定]" : "§a[开放]";
                    int chunkCount = region.getChunks().size();
                    sender.sendMessage("§e" + region.getRegionName() + " §7- " + 
                        status + " §7| §f" + chunkCount + " §7个区块 §7| §f" + region.getWorld());
                }
                sender.sendMessage("§7共 §e" + regions.size() + " §7个领地");
            });
    }
    
    /**
     * 列出服务器中所有领地
     */
    private void listAllRegions(CommandSender sender) {
        plugin.getChunkClaimDAO().getAllRegionsAsync().thenAccept(regions -> {
            if (regions.isEmpty()) {
                sender.sendMessage("§c服务器中没有任何领地！");
                return;
            }
            
            sender.sendMessage("§6=== 服务器所有领地列表 ===");
            for (org.kari.kariClaims.models.ClaimRegion region : regions) {
                String ownerName = org.bukkit.Bukkit.getOfflinePlayer(region.getOwner()).getName();
                if (ownerName == null) ownerName = "未知";
                String status = region.isLocked() ? "§c[锁定]" : "§a[开放]";
                int chunkCount = region.getChunks() != null ? region.getChunks().size() : 0;
                sender.sendMessage("§e" + region.getRegionName() + " §7- " + 
                    "§b" + ownerName + " §7| " + status + " §7| §f" + chunkCount + " §7个区块 §7| §f" + region.getWorld());
            }
            sender.sendMessage("§7共 §e" + regions.size() + " §7个领地");
        });
    }

    private void handleReload(CommandSender sender) {
        if (!sender.hasPermission("kariClaims.admin")) {
            sender.sendMessage("§c你没有权限执行此命令！");
            return;
        }

        plugin.reloadConfig();
        plugin.getClaimManager().invalidateCache();
        plugin.getChunkClaimManager().invalidateCache();
        sender.sendMessage("§a配置已重新加载！");
    }

    private void handleRename(CommandSender sender, String[] args) {
        if (!sender.hasPermission("kariClaims.admin.rename")) {
            sender.sendMessage("§c你没有权限执行此命令！");
            return;
        }

        if (!(sender instanceof Player)) {
            sender.sendMessage("§c此命令只能由玩家执行！");
            return;
        }

        if (args.length < 2) {
            sender.sendMessage("§c用法: /claim admin rename <名称>");
            return;
        }

        Player player = (Player) sender;
        Optional<ChunkClaim> claim = plugin.getChunkClaimManager().findChunkClaimAt(player.getLocation());
        
        if (claim.isEmpty()) {
            player.sendMessage("§c当前位置不在任何领地内！");
            return;
        }

        String name = String.join(" ", Arrays.copyOfRange(args, 1, args.length));
        ChunkClaim c = claim.get();
        c.setChunkName(name);
        
        plugin.getChunkClaimDAO().updateChunkClaimAsync(c)
            .thenRun(() -> player.sendMessage("§a领地名称已强制更新: " + name));
    }

    private void handleLock(CommandSender sender) {
        if (!sender.hasPermission("kariClaims.admin.unlock")) {
            sender.sendMessage("§c你没有权限执行此命令！");
            return;
        }

        if (!(sender instanceof Player)) {
            sender.sendMessage("§c此命令只能由玩家执行！");
            return;
        }

        Player player = (Player) sender;
        Optional<ChunkClaim> claim = plugin.getChunkClaimManager().findChunkClaimAt(player.getLocation());
        
        if (claim.isEmpty()) {
            player.sendMessage("§c当前位置不在任何领地内！");
            return;
        }

        ChunkClaim c = claim.get();
        c.setLocked(!c.isLocked());
        
        plugin.getChunkClaimDAO().updateChunkClaimAsync(c)
            .thenRun(() -> {
                String status = c.isLocked() ? "已锁定" : "已解锁";
                player.sendMessage("§a领地" + status + "！");
            });
    }

    private void handleRemoveClaim(CommandSender sender) {
        if (!sender.hasPermission("kariClaims.admin.removeclaim")) {
            sender.sendMessage("§c你没有权限执行此命令！");
            return;
        }

        if (!(sender instanceof Player)) {
            sender.sendMessage("§c此命令只能由玩家执行！");
            return;
        }

        Player player = (Player) sender;
        Optional<ChunkClaim> claim = plugin.getChunkClaimManager().findChunkClaimAt(player.getLocation());
        
        if (claim.isEmpty()) {
            player.sendMessage("§c当前位置不在任何领地内！");
            return;
        }

        plugin.getChunkClaimDAO().deleteChunkClaimAsync(claim.get().getId())
            .thenRun(() -> {
                plugin.getChunkClaimManager().invalidateCache();
                player.sendMessage("§a领地已强制删除！");
            });
    }

    private void handleTransfer(CommandSender sender, String[] args) {
        if (!sender.hasPermission("kariClaims.admin.transfer")) {
            sender.sendMessage("§c你没有权限执行此命令！");
            return;
        }

        if (!(sender instanceof Player)) {
            sender.sendMessage("§c此命令只能由玩家执行！");
            return;
        }

        if (args.length < 2) {
            sender.sendMessage("§c用法: /claim admin transfer <玩家>");
            return;
        }

        Player player = (Player) sender;
        Optional<ChunkClaim> claim = plugin.getChunkClaimManager().findChunkClaimAt(player.getLocation());
        
        if (claim.isEmpty()) {
            player.sendMessage("§c当前位置不在任何领地内！");
            return;
        }

        String targetName = args[1];
        
        // 支持离线玩家转移 - 先尝试获取在线玩家，再尝试离线玩家
        org.bukkit.OfflinePlayer target = plugin.getServer().getPlayer(targetName);
        if (target == null) {
            // 尝试获取离线玩家
            target = plugin.getServer().getOfflinePlayer(targetName);
        }
        
        // 检查玩家是否有效（UUID不为null即可，不要求hasPlayedBefore）
        if (target == null || target.getUniqueId() == null) {
            player.sendMessage("§c无法解析玩家: " + targetName);
            return;
        }
        
        // 如果玩家从未加入过服务器，给出警告但仍允许转移
        boolean hasPlayed = target.hasPlayedBefore();
        if (!hasPlayed) {
            player.sendMessage("§e警告: 该玩家从未加入过服务器，但仍将尝试转移");
        }

        final org.bukkit.OfflinePlayer finalTarget = target;
        ChunkClaim c = claim.get();
        int regionId = c.getRegionId();
        
        // 获取区域信息用于显示
        String transferInfo;
        if (regionId > 0) {
            org.kari.kariClaims.models.ClaimRegion region = plugin.getChunkClaimManager().getRegion(regionId);
            if (region != null) {
                int chunkCount = region.getChunks().size();
                transferInfo = "§a已强制转移区域（包含 " + chunkCount + " 个区块）给: " + targetName;
            } else {
                transferInfo = "§a已强制转移领地给: " + targetName;
            }
        } else {
            transferInfo = "§a已强制转移领地给: " + targetName;
        }
        
        plugin.getChunkClaimManager().transferRegionOwnership(c.getId(), target.getUniqueId())
            .thenRun(() -> {
                player.sendMessage(transferInfo);
                if (finalTarget.isOnline()) {
                    if (regionId > 0) {
                        finalTarget.getPlayer().sendMessage("§a管理员已将一个区域（包含多个区块）转移给你！");
                    } else {
                        finalTarget.getPlayer().sendMessage("§a管理员已将领地转移给你！");
                    }
                }
            })
            .exceptionally(throwable -> {
                player.sendMessage("§c转移失败: " + throwable.getCause().getMessage());
                return null;
            });
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return Arrays.asList("reload", "rename", "lock", "removeclaim", "transfer")
                .stream()
                .filter(s -> s.startsWith(args[0].toLowerCase()))
                .collect(Collectors.toList());
        }
        return Collections.emptyList();
    }
}

