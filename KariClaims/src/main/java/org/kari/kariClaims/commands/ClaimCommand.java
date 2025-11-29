package org.kari.kariClaims.commands;

import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.kari.kariClaims.KariClaims;
import org.kari.kariClaims.models.Claim;
import org.kari.kariClaims.models.ClaimMember;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 领地命令处理器
 */
public class ClaimCommand implements CommandExecutor, TabCompleter {
    private final KariClaims plugin;
    private final Map<UUID, Location> pos1Cache = new HashMap<>();
    private final Map<UUID, Location> pos2Cache = new HashMap<>();

    public ClaimCommand(KariClaims plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("§c此命令只能由玩家执行！");
            return true;
        }

        Player player = (Player) sender;

        if (args.length == 0) {
            sendHelp(player);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "pos1":
                handlePos1(player);
                break;
            case "pos2":
                handlePos2(player);
                break;
            case "create":
                handleCreate(player);
                break;
            case "delete":
                handleDelete(player, args);
                break;
            case "list":
                handleList(player);
                break;
            case "info":
                handleInfo(player);
                break;
            case "gui":
                handleGUI(player);
                break;
            case "addmember":
            case "add":
                handleAddMember(player, args);
                break;
            case "removemember":
            case "remove":
                handleRemoveMember(player, args);
                break;
            case "setname":
                handleSetName(player, args);
                break;
            case "setdesc":
            case "setdescription":
                handleSetDescription(player, args);
                break;
            default:
                sendHelp(player);
        }

        return true;
    }

    private void sendHelp(Player player) {
        player.sendMessage("§6=== KariClaims 命令帮助 ===");
        player.sendMessage("§e/claim pos1 §7- 设置第一个位置");
        player.sendMessage("§e/claim pos2 §7- 设置第二个位置");
        player.sendMessage("§e/claim create §7- 创建领地");
        player.sendMessage("§e/claim delete <id> §7- 删除领地");
        player.sendMessage("§e/claim list §7- 查看你的领地列表");
        player.sendMessage("§e/claim info §7- 查看当前位置的领地信息");
        player.sendMessage("§e/claim gui §7- 打开管理界面");
        player.sendMessage("§e/claim add <玩家> §7- 添加成员");
        player.sendMessage("§e/claim remove <玩家> §7- 移除成员");
        player.sendMessage("§e/claim setname <名称> §7- 设置领地名称");
        player.sendMessage("§e/claim setdesc <描述> §7- 设置领地描述");
    }

    private void handlePos1(Player player) {
        pos1Cache.put(player.getUniqueId(), player.getLocation());
        player.sendMessage("§a已设置第一个位置: §7" + formatLocation(player.getLocation()));
    }

    private void handlePos2(Player player) {
        pos2Cache.put(player.getUniqueId(), player.getLocation());
        player.sendMessage("§a已设置第二个位置: §7" + formatLocation(player.getLocation()));
    }

    private void handleCreate(Player player) {
        Location pos1 = pos1Cache.get(player.getUniqueId());
        Location pos2 = pos2Cache.get(player.getUniqueId());

        if (pos1 == null || pos2 == null) {
            player.sendMessage("§c请先设置两个位置！使用 /claim pos1 和 /claim pos2");
            return;
        }

        if (!pos1.getWorld().equals(pos2.getWorld())) {
            player.sendMessage("§c两个位置必须在同一世界！");
            return;
        }

        player.sendMessage("§7正在创建领地...");
        plugin.getClaimManager().createClaim(player, pos1, pos2)
            .thenAccept(claim -> {
                player.sendMessage("§a领地创建成功！ID: §e" + claim.getId());
                pos1Cache.remove(player.getUniqueId());
                pos2Cache.remove(player.getUniqueId());
            })
            .exceptionally(throwable -> {
                player.sendMessage("§c创建领地失败: " + throwable.getCause().getMessage());
                return null;
            });
    }

    private void handleDelete(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage("§c用法: /claim delete <领地ID>");
            return;
        }

        try {
            int claimId = Integer.parseInt(args[1]);
            plugin.getClaimManager().getPlayerClaimsAsync(player.getUniqueId())
                .thenAccept(claims -> {
                    Optional<Claim> claim = claims.stream()
                        .filter(c -> c.getId() == claimId)
                        .findFirst();
                    
                    if (claim.isEmpty()) {
                        player.sendMessage("§c未找到该领地或你不是所有者！");
                        return;
                    }

                    plugin.getClaimManager().deleteClaim(claimId)
                        .thenRun(() -> player.sendMessage("§a领地已删除！"));
                });
        } catch (NumberFormatException e) {
            player.sendMessage("§c无效的领地ID！");
        }
    }

    private void handleList(Player player) {
        plugin.getClaimManager().getPlayerClaimsAsync(player.getUniqueId())
            .thenAccept(claims -> {
                if (claims.isEmpty()) {
                    player.sendMessage("§c你还没有任何领地！");
                    return;
                }

                player.sendMessage("§6=== 你的领地列表 ===");
                for (Claim claim : claims) {
                    player.sendMessage("§eID: §7" + claim.getId() + 
                        " §e| §7名称: §f" + claim.getName() + 
                        " §e| §7面积: §f" + claim.getArea() + 
                        " §e| §7世界: §f" + claim.getWorld());
                }
            });
    }

    private void handleInfo(Player player) {
        Optional<Claim> claim = plugin.getClaimManager().findClaimAt(player.getLocation());
        if (claim.isEmpty()) {
            player.sendMessage("§c当前位置不在任何领地内！");
            return;
        }

        Claim c = claim.get();
        player.sendMessage("§6=== 领地信息 ===");
        player.sendMessage("§eID: §7" + c.getId());
        player.sendMessage("§e名称: §7" + c.getName());
        player.sendMessage("§e所有者: §7" + plugin.getServer().getOfflinePlayer(c.getOwner()).getName());
        player.sendMessage("§e面积: §7" + c.getArea());
        player.sendMessage("§e范围: §7" + c.getMinX() + ", " + c.getMinZ() + " ~ " + c.getMaxX() + ", " + c.getMaxZ());
    }

    private void handleGUI(Player player) {
        plugin.getClaimGUI().openMainMenu(player);
    }

    private void handleAddMember(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage("§c用法: /claim add <玩家>");
            return;
        }

        Optional<Claim> claim = plugin.getClaimManager().findClaimAt(player.getLocation());
        if (claim.isEmpty()) {
            player.sendMessage("§c当前位置不在任何领地内！");
            return;
        }

        if (!claim.get().getOwner().equals(player.getUniqueId())) {
            player.sendMessage("§c只有所有者可以添加成员！");
            return;
        }

        // 查找玩家
        Player target = plugin.getServer().getPlayer(args[1]);
        if (target == null) {
            player.sendMessage("§c未找到玩家: " + args[1]);
            return;
        }

        ClaimMember member = new ClaimMember(claim.get().getId(), target.getUniqueId(), 
            ClaimMember.MemberRole.MEMBER, System.currentTimeMillis());
        
        plugin.getClaimManager().addMember(member)
            .thenRun(() -> player.sendMessage("§a已添加成员: " + target.getName()));
    }

    private void handleRemoveMember(Player player, String[] args) {
        // 实现移除成员逻辑
        player.sendMessage("§7移除成员功能开发中...");
    }

    private void handleSetName(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage("§c用法: /claim setname <名称>");
            return;
        }

        Optional<Claim> claim = plugin.getClaimManager().findClaimAt(player.getLocation());
        if (claim.isEmpty()) {
            player.sendMessage("§c当前位置不在任何领地内！");
            return;
        }

        if (!claim.get().getOwner().equals(player.getUniqueId())) {
            player.sendMessage("§c只有所有者可以设置名称！");
            return;
        }

        String name = String.join(" ", Arrays.copyOfRange(args, 1, args.length));
        claim.get().setName(name);
        plugin.getClaimManager().updateClaim(claim.get())
            .thenRun(() -> player.sendMessage("§a领地名称已更新: " + name));
    }

    private void handleSetDescription(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage("§c用法: /claim setdesc <描述>");
            return;
        }

        Optional<Claim> claim = plugin.getClaimManager().findClaimAt(player.getLocation());
        if (claim.isEmpty()) {
            player.sendMessage("§c当前位置不在任何领地内！");
            return;
        }

        if (!claim.get().getOwner().equals(player.getUniqueId())) {
            player.sendMessage("§c只有所有者可以设置描述！");
            return;
        }

        String desc = String.join(" ", Arrays.copyOfRange(args, 1, args.length));
        claim.get().setDescription(desc);
        plugin.getClaimManager().updateClaim(claim.get())
            .thenRun(() -> player.sendMessage("§a领地描述已更新"));
    }

    private String formatLocation(Location loc) {
        return String.format("X: %d, Y: %d, Z: %d (%s)", 
            loc.getBlockX(), loc.getBlockY(), loc.getBlockZ(), loc.getWorld().getName());
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return Arrays.asList("pos1", "pos2", "create", "delete", "list", "info", "gui", 
                "add", "remove", "setname", "setdesc").stream()
                .filter(s -> s.startsWith(args[0].toLowerCase()))
                .collect(Collectors.toList());
        }
        return Collections.emptyList();
    }
}

