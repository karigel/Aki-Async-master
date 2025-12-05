package org.kari.kariClaims.commands;

import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.kari.kariClaims.KariClaims;
import org.kari.kariClaims.models.ChunkClaim;
import org.kari.kariClaims.models.ClaimRegion;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;

import org.kari.kariClaims.utils.ChunkAnimationUtils;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 区块领地命令处理器
 */
public class ChunkClaimCommand implements CommandExecutor, TabCompleter {
    private final KariClaims plugin;
    private final Map<UUID, Long> dissolveConfirmations = new HashMap<>(); // 玩家UUID -> 确认时间戳
    private final Map<UUID, PendingInvite> pendingInvites = new HashMap<>(); // 被邀请玩家UUID -> 邀请信息

    public ChunkClaimCommand(KariClaims plugin) {
        this.plugin = plugin;
    }
    
    /**
     * 待处理的邀请信息
     */
    private static class PendingInvite {
        final int claimId;
        final UUID inviterId;
        final long inviteTime;
        
        PendingInvite(int claimId, UUID inviterId) {
            this.claimId = claimId;
            this.inviterId = inviterId;
            this.inviteTime = System.currentTimeMillis();
        }
        
        boolean isExpired() {
            return System.currentTimeMillis() - inviteTime > 300000; // 5分钟过期
        }
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
            case "claim":
                handleClaim(player);
                break;
            case "unclaim":
                handleUnclaim(player);
                break;
            case "accept":
                handleAccept(player);
                break;
            case "ban":
                handleBan(player, args);
                break;
            case "unban":
                handleUnban(player, args);
                break;
            case "dissolve":
                handleDissolve(player);
                break;
            case "sethome":
                handleSetHome(player, args);
                break;
            case "home":
                handleHome(player, args);
                break;
            case "invite":
                handleInvite(player, args);
                break;
            case "kick":
                handleKick(player, args);
                break;
            case "leave":
                handleLeave(player);
                break;
            case "lock":
                handleLock(player);
                break;
            case "show":
                handleShow(player);
                break;
            case "recipe":
                handleRecipe(player);
                break;
            case "name":
            case "setname":
                handleName(player, args);
                break;
            case "transfer":
                handleTransfer(player, args);
                break;
            case "merge":
                handleMerge(player, args);
                break;
            case "list":
                handleList(player);
                break;
            case "admin":
                // 转发到管理员命令处理器
                new AdminCommand(plugin).onCommand(player, null, label, 
                    Arrays.copyOfRange(args, 1, args.length));
                break;
            default:
                sendHelp(player);
        }

        return true;
    }

    private void sendHelp(Player player) {
        player.sendMessage("§6=== KariClaims 命令帮助 ===");
        player.sendMessage("§e/claim claim §7- 认领当前区块");
        player.sendMessage("§e/claim unclaim §7- 取消认领当前区块");
        player.sendMessage("§e/claim list §7- 列出所有领地");
        player.sendMessage("§e/claim accept §7- 接受领地邀请");
        player.sendMessage("§e/claim ban <玩家> §7- 封禁玩家");
        player.sendMessage("§e/claim unban <玩家> §7- 解封玩家");
        player.sendMessage("§e/claim dissolve §7- 解散领地");
        player.sendMessage("§e/claim sethome §7- 设置home点（使用领地名）");
        player.sendMessage("§e/claim home [名称] §7- 传送到home点");
        player.sendMessage("§e/claim invite <玩家> §7- 邀请玩家");
        player.sendMessage("§e/claim kick <玩家> §7- 踢出玩家");
        player.sendMessage("§e/claim leave §7- 离开领地");
        player.sendMessage("§e/claim lock §7- 锁定/解锁领地");
        player.sendMessage("§e/claim recipe §7- 查看能量电池配方");
        player.sendMessage("§e/claim name <名称> §7- 重命名领地");
        player.sendMessage("§e/claim transfer <玩家> §7- 转移所有权");
        player.sendMessage("§e/claim merge <区域ID> §7- 合并到指定区域");
    }
    
    /**
     * 列出玩家的所有领地
     */
    private void handleList(Player player) {
        if (!player.hasPermission("kariclaims.list")) {
            player.sendMessage("§c你没有权限使用此命令！");
            return;
        }
        
        plugin.getChunkClaimManager().getPlayerRegionsAsync(player.getUniqueId())
            .thenAccept(regions -> {
                if (regions.isEmpty()) {
                    player.sendMessage("§c你没有任何领地！");
                    return;
                }
                
                player.sendMessage("§6=== 你的领地列表 ===");
                for (ClaimRegion region : regions) {
                    String status = region.isLocked() ? "§c[锁定]" : "§a[开放]";
                    int chunkCount = region.getChunks().size();
                    player.sendMessage("§e" + region.getRegionName() + " §7- " + 
                        status + " §7| §f" + chunkCount + " §7个区块 §7| §f" + region.getWorld());
                }
                player.sendMessage("§7共 §e" + regions.size() + " §7个领地");
            });
    }

    private void handleClaim(Player player) {
        if (!player.hasPermission("kariClaims.claim")) {
            player.sendMessage("§c你没有权限执行此命令！");
            return;
        }

        Chunk chunk = player.getLocation().getChunk();
        
        // 1. 检查是否已被认领
        plugin.getChunkClaimManager().findChunkClaimAtAsync(player.getLocation())
            .thenAccept(claimOpt -> {
                if (claimOpt.isPresent()) {
                    player.sendMessage("§c此区块已被认领！");
                    return;
                }
                
                // 2. 检查玩家是否有未放置能量箱的领地
                plugin.getChunkClaimManager().getPlayerChunkClaimsAsync(player.getUniqueId()).thenAccept(existingClaims -> {
                    if (!existingClaims.isEmpty()) {
                        Set<Integer> checkedRegions = new HashSet<>();
                        for (ChunkClaim c : existingClaims) {
                            // 如果是区域
                            if (c.getRegionId() > 0) {
                                if (checkedRegions.contains(c.getRegionId())) continue;
                                checkedRegions.add(c.getRegionId());
                                
                                boolean regionHasPowerCell = false;
                                ClaimRegion region = plugin.getChunkClaimManager().getRegion(c.getRegionId());
                                if (region != null) {
                                    for (ChunkClaim rc : region.getChunks()) {
                                        if (plugin.getChunkClaimManager().hasPowerCell(rc.getId())) {
                                            regionHasPowerCell = true;
                                            break;
                                        }
                                    }
                                }
                                
                                if (!regionHasPowerCell) {
                                    player.sendMessage("§c你有一个区域 (" + (region!=null?region.getRegionName():"未知") + ") 尚未放置能量电池！");
                                    player.sendMessage("§c请先为现有领地放置能量电池，才能认领新区块。");
                                    return;
                                }
                            } else {
                                // 独立区块
                                if (!plugin.getChunkClaimManager().hasPowerCell(c.getId())) {
                                    player.sendMessage("§c你有一个独立区块 (" + c.getChunkX() + "," + c.getChunkZ() + ") 尚未放置能量电池！");
                                    player.sendMessage("§c请先为现有领地放置能量电池，才能认领新区块。");
                                    return;
                                }
                            }
                        }
                    }
                    
                    // 3. 执行认领
                    performClaim(player, chunk);
                });
            });
    }

    private void performClaim(Player player, Chunk chunk) {
        // 立即创建领地并充值10分钟
        plugin.getChunkClaimManager().claimChunkImmediately(player, chunk)
            .thenAccept(claim -> {
                // 播放成功音效
                player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);
                
                // 播放区块认领动画（绿宝石块滚过地面）
                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    ChunkAnimationUtils.playClaimAnimation(plugin, chunk, player);
                });
                
                player.sendMessage("§a区块认领成功！");
                player.sendMessage("§e领地ID: §7" + claim.getId());
                
                // 检查是否合并到了已有区域
                boolean isMerged = false;
                if (claim.getRegionId() > 0) {
                    ClaimRegion region = plugin.getChunkClaimManager().getRegion(claim.getRegionId());
                    if (region != null && region.getChunks().size() > 1) {
                        isMerged = true;
                        player.sendMessage("§a已自动合并到区域: §e" + region.getRegionName());
                        player.sendMessage("§7能量由该区域的能量电池统一管理。");
                    }
                }
                
                if (!isMerged) {
                    player.sendMessage("§7已自动充值 §e10分钟 §7的保护时间");
                    player.sendMessage("§7请在10分钟内放置能量电池以保持领地！");
                    player.sendMessage("§7使用 /claim recipe 查看能量电池配方");
                }
            })
            .exceptionally(throwable -> {
                if (throwable.getCause() instanceof IllegalStateException && 
                    throwable.getCause().getMessage().startsWith("REGION_CONFLICT:")) {
                    
                    String msg = throwable.getCause().getMessage().substring("REGION_CONFLICT:".length());
                    player.sendMessage("§c该区块相邻于多个已拥有的区域，请选择要合并的区域：");
                    
                    String[] regions = msg.split(",");
                    for (String regionStr : regions) {
                        String[] parts = regionStr.split(":", 2);
                        String id = parts[0];
                        String name = parts.length > 1 ? parts[1] : "区域 " + id;
                        
                        Component component = Component.text("[- 点击合并到: " + name + " -]")
                            .color(NamedTextColor.GREEN)
                            .clickEvent(ClickEvent.runCommand("/claim merge " + id));
                        player.sendMessage(component);
                    }
                    return null;
                }
                
                player.sendMessage("§c认领失败: " + throwable.getCause().getMessage());
                return null;
            });
    }

    private void handleUnclaim(Player player) {
        if (!player.hasPermission("kariClaims.unclaim")) {
            player.sendMessage("§c你没有权限执行此命令！");
            return;
        }

        Chunk chunk = player.getLocation().getChunk();
        
        plugin.getChunkClaimManager().unclaimChunk(player, chunk)
            .thenRun(() -> {
                player.sendMessage("§a已取消认领此区块！");
                
                // 播放取消认领动画（红石块滚过地面）
                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    ChunkAnimationUtils.playUnclaimAnimation(plugin, chunk, player);
                });
            })
            .exceptionally(throwable -> {
                player.sendMessage("§c" + throwable.getCause().getMessage());
                return null;
            });
    }

    private void handleAccept(Player player) {
        PendingInvite invite = pendingInvites.get(player.getUniqueId());
        if (invite == null || invite.isExpired()) {
            pendingInvites.remove(player.getUniqueId());
            player.sendMessage("§c你没有待处理的邀请！");
            return;
        }
        
        // 检查领地是否还存在
        plugin.getChunkClaimDAO().findChunkClaimByIdAsync(invite.claimId)
            .thenAccept(claimOpt -> {
                if (claimOpt.isEmpty()) {
                    pendingInvites.remove(player.getUniqueId());
                    player.sendMessage("§c邀请的领地已不存在！");
                    return;
                }
                
                // 添加为成员
                plugin.getChunkClaimManager().addMember(
                    invite.claimId,
                    player.getUniqueId(),
                    org.kari.kariClaims.models.ClaimMember.MemberRole.MEMBER
                ).thenRun(() -> {
                    pendingInvites.remove(player.getUniqueId());
                    player.sendMessage("§a已接受邀请，加入领地！");
                    
                    // 通知邀请者
                    org.bukkit.OfflinePlayer inviter = plugin.getServer().getOfflinePlayer(invite.inviterId);
                    if (inviter.isOnline()) {
                        inviter.getPlayer().sendMessage("§a" + player.getName() + " 已接受你的邀请！");
                    }
                }).exceptionally(throwable -> {
                    player.sendMessage("§c接受邀请失败: " + throwable.getCause().getMessage());
                    return null;
                });
            });
    }

    private void handleBan(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage("§c用法: /claim ban <玩家>");
            return;
        }

        Optional<ChunkClaim> claim = plugin.getChunkClaimManager().findChunkClaimAt(player.getLocation());
        if (claim.isEmpty()) {
            player.sendMessage("§c当前位置不在任何领地内！");
            return;
        }

        if (!claim.get().getOwner().equals(player.getUniqueId()) && !player.isOp()) {
            player.sendMessage("§c只有所有者可以封禁玩家！");
            return;
        }

        String targetName = args[1];
        org.bukkit.OfflinePlayer target = plugin.getServer().getOfflinePlayer(targetName);
        if (target == null || !target.hasPlayedBefore()) {
            player.sendMessage("§c未找到玩家: " + targetName);
            return;
        }
        
        UUID targetId = target.getUniqueId();
        ChunkClaim c = claim.get();
        
        // 检查是否已经是成员
        plugin.getChunkClaimManager().isMember(c.getId(), targetId)
            .thenAccept(isMember -> {
                if (isMember) {
                    // 先移除成员
                    plugin.getChunkClaimManager().removeMember(c.getId(), targetId);
                }
                
                // 封禁玩家
                plugin.getChunkClaimManager().banPlayer(c.getId(), targetId)
                    .thenRun(() -> {
                        player.sendMessage("§a已封禁玩家: " + targetName);
                        if (target.isOnline()) {
                            target.getPlayer().sendMessage("§c你已被封禁，无法进入该领地！");
                        }
                    })
                    .exceptionally(throwable -> {
                        player.sendMessage("§c封禁失败: " + throwable.getCause().getMessage());
                        return null;
                    });
            });
    }

    private void handleUnban(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage("§c用法: /claim unban <玩家>");
            return;
        }

        String targetName = args[1];
        org.bukkit.OfflinePlayer target = plugin.getServer().getOfflinePlayer(targetName);
        if (target == null || !target.hasPlayedBefore()) {
            player.sendMessage("§c未找到玩家: " + targetName);
            return;
        }
        
        Optional<ChunkClaim> claim = plugin.getChunkClaimManager().findChunkClaimAt(player.getLocation());
        if (claim.isEmpty()) {
            player.sendMessage("§c当前位置不在任何领地内！");
            return;
        }
        
        if (!claim.get().getOwner().equals(player.getUniqueId()) && !player.isOp()) {
            player.sendMessage("§c只有所有者可以解封玩家！");
            return;
        }
        
        UUID targetId = target.getUniqueId();
        
        plugin.getChunkClaimManager().unbanPlayer(claim.get().getId(), targetId)
            .thenRun(() -> {
                player.sendMessage("§a已解封玩家: " + targetName);
                if (target.isOnline()) {
                    target.getPlayer().sendMessage("§a你已被解封，可以进入该领地了！");
                }
            })
            .exceptionally(throwable -> {
                player.sendMessage("§c解封失败: " + throwable.getCause().getMessage());
                return null;
            });
    }

    private void handleDissolve(Player player) {
        UUID playerId = player.getUniqueId();
        
        // 检查是否有待确认的解散请求
        Long confirmTime = dissolveConfirmations.get(playerId);
        if (confirmTime != null && System.currentTimeMillis() - confirmTime < 10000) {
            // 二次确认，执行解散所有领地
            dissolveConfirmations.remove(playerId);
            dissolveAllClaims(player);
        } else {
            // 第一次请求，要求二次确认
            dissolveConfirmations.put(playerId, System.currentTimeMillis());
            player.sendMessage("§c§l警告：此操作将解散你的所有领地！");
            player.sendMessage("§c请在10秒内再次输入 §e/claim dissolve §c以确认");
            player.sendMessage("§7此操作不可撤销！");
        }
    }

    /**
     * 解散玩家的所有领地
     */
    private void dissolveAllClaims(Player player) {
        UUID playerId = player.getUniqueId();
        
        plugin.getChunkClaimManager().getPlayerChunkClaimsAsync(playerId)
            .thenAccept(claims -> {
                if (claims.isEmpty()) {
                    player.sendMessage("§c你没有领地可以解散！");
                    return;
                }
                
                int totalClaims = claims.size();
                player.sendMessage("§7正在解散 §e" + totalClaims + " §7个领地...");
                
                // 收集所有涉及的区域ID
                Set<Integer> regionIds = new HashSet<>();
                for (ChunkClaim claim : claims) {
                    if (claim.getRegionId() > 0) {
                        regionIds.add(claim.getRegionId());
                    }
                }
                
                // 逐个解散领地并处理能源箱
                List<java.util.concurrent.CompletableFuture<Void>> futures = new ArrayList<>();
                
                for (ChunkClaim claim : claims) {
                    futures.add(dissolveClaimWithPowerCell(player, claim));
                }
                
                // 等待所有领地删除完成，然后删除区域
                final Set<Integer> finalRegionIds = regionIds;
                java.util.concurrent.CompletableFuture.allOf(futures.toArray(new java.util.concurrent.CompletableFuture[0]))
                    .thenCompose(v -> plugin.getChunkClaimDAO().deletePlayerRegionsAsync(playerId))
                    .thenRun(() -> {
                        plugin.getChunkClaimManager().invalidateCache();
                        // 清除该区域内所有玩家的BossBar
                        plugin.getServer().getScheduler().runTask(plugin, () -> {
                            plugin.getClaimEnterListener().clearBossBarsForRegions(finalRegionIds);
                        });
                        player.sendMessage("§a已成功解散所有领地！");
                        player.sendMessage("§7共解散 §e" + totalClaims + " §7个领地, §e" + finalRegionIds.size() + " §7个区域");
                    });
            });
    }

    /**
     * 解散单个领地并处理能源箱
     */
    private java.util.concurrent.CompletableFuture<Void> dissolveClaimWithPowerCell(Player player, ChunkClaim claim) {
        return plugin.getChunkClaimDAO().getPowerCellLocationAsync(claim.getId())
            .thenCompose(powerCellLoc -> {
                // 删除领地
                return plugin.getChunkClaimDAO().deleteChunkClaimAsync(claim.getId())
                    .thenRun(() -> {
                        // 处理能源箱
                        if (powerCellLoc != null) {
                            plugin.getServer().getScheduler().runTask(plugin, new Runnable() {
                                @Override
                                public void run() {
                                    handlePowerCellDissolution(powerCellLoc, claim);
                                }
                            });
                        }
                    });
            });
    }

    /**
     * 处理能源箱解散：变回普通箱子并吐出能源物品
     * 注意：只回退玩家投入的物品能源，初始赠送的时间不回退
     */
    private void handlePowerCellDissolution(Location powerCellLoc, ChunkClaim claim) {
        org.bukkit.block.Block block = powerCellLoc.getBlock();
        if (block.getType() != org.bukkit.Material.CHEST) {
            return;
        }
        
        // 播放能量电池自毁动画（与手动破坏相同）
        ChunkAnimationUtils.playPowerCellDestroyAnimation(plugin, powerCellLoc);
        
        // 能源物品已经在箱子里了（玩家放进去的），不需要额外生成物品
        // 箱子变回普通箱子后，玩家可以直接打开取回原有物品
        
        // 只回退金钱余额（如果经济系统启用）
        double economyBalance = claim.getEconomyBalance();
        if (economyBalance > 0 && plugin.getEconomyManager().isEnabled()) {
            org.bukkit.OfflinePlayer owner = plugin.getServer().getOfflinePlayer(claim.getOwner());
            plugin.getEconomyManager().deposit(owner, economyBalance);
            String formattedAmount = plugin.getEconomyManager().format(economyBalance);
            if (owner.isOnline()) {
                owner.getPlayer().sendMessage("§e领地解散，已回退 §a" + formattedAmount + " §e金钱");
            }
        }
        
        // 删除能量电池记录
        plugin.getChunkClaimDAO().deletePowerCellAsync(claim.getId());
        
        // 从缓存移除
        plugin.getChunkClaimManager().unregisterPowerCellLocation(powerCellLoc);
    }

    private void handleSetHome(Player player, String[] args) {
        Optional<ChunkClaim> claim = plugin.getChunkClaimManager().findChunkClaimAt(player.getLocation());
        if (claim.isEmpty()) {
            player.sendMessage("§c当前位置不在任何领地内！");
            return;
        }

        if (!claim.get().getOwner().equals(player.getUniqueId()) && !player.isOp()) {
            player.sendMessage("§c只有所有者可以设置home点！");
            return;
        }

        ChunkClaim c = claim.get();
        c.setHomeLocation(player.getLocation());
        
        // 获取领地显示名称（优先使用区域名称）
        String claimDisplayName = getClaimDisplayName(c);
        
        plugin.getChunkClaimDAO().updateChunkClaimAsync(c)
            .thenRun(() -> {
                player.sendMessage("§a已设置home点: §e" + claimDisplayName);
            });
    }
    
    /**
     * 获取领地显示名称（优先使用区域名称）
     */
    private String getClaimDisplayName(ChunkClaim claim) {
        // 如果属于区域，使用区域名称
        if (claim.getRegionId() > 0) {
            ClaimRegion region = plugin.getChunkClaimManager().getRegion(claim.getRegionId());
            if (region != null && region.getRegionName() != null && !region.getRegionName().isEmpty()) {
                return region.getRegionName();
            }
        }
        // 使用区块名称
        if (claim.getChunkName() != null && !claim.getChunkName().isEmpty()) {
            return claim.getChunkName();
        }
        // 默认使用"未命名领地"
        return "未命名领地";
    }

    private void handleHome(Player player, String[] args) {
        if (!player.hasPermission("kariClaims.home")) {
            player.sendMessage("§c你没有权限执行此命令！");
            return;
        }

        String targetName = args.length > 1 ? String.join(" ", Arrays.copyOfRange(args, 1, args.length)) : null;

        // 可访问的领地（同步获取，避免异步链导致无响应）
        List<ChunkClaim> accessible = new ArrayList<>();
        try {
            List<ChunkClaim> own = plugin.getChunkClaimManager().getPlayerChunkClaimsAsync(player.getUniqueId()).get();
            accessible.addAll(own.stream().filter(c -> c.getHomeLocation() != null).toList());
            List<ChunkClaim> member = plugin.getChunkClaimDAO().getMemberChunkClaimsAsync(player.getUniqueId()).get();
            accessible.addAll(member.stream().filter(c -> c.getHomeLocation() != null).toList());
            List<ChunkClaim> publics = plugin.getChunkClaimDAO().getPublicClaimsAsync().get();
            for (ChunkClaim pc : publics) {
                if (pc.getHomeLocation() != null && pc.isHomePublic()) {
                    accessible.add(pc);
                }
            }
        } catch (Exception e) {
            player.sendMessage("§c获取home列表失败，请稍后重试");
            plugin.getLogger().warning("获取home列表失败: " + e.getMessage());
            return;
        }

        if (accessible.isEmpty()) {
            player.sendMessage("§c没有可传送的领地home！");
            return;
        }

        // 如果未指定名称且只有一个可用，直接用它
        ChunkClaim target = null;
        if (targetName == null && accessible.size() == 1) {
            target = accessible.get(0);
        } else if (targetName == null) {
            player.sendMessage("§c你有多个可用的home，请使用 /claim home <领地名>");
            return;
        } else {
            // 优先匹配领地显示名称（区域名或区块名）
            for (ChunkClaim c : accessible) {
                String displayName = getClaimDisplayName(c);
                if (displayName.equalsIgnoreCase(targetName)) {
                    target = c;
                    break;
                }
            }
        }

        if (target == null) {
            player.sendMessage("§c未找到匹配的领地名称！");
            return;
        }

        ChunkClaim chosen = target;
        // 权限检查：公开或成员/所有者
        if (chosen.isHomePublic() || chosen.getOwner().equals(player.getUniqueId())) {
            teleportToHome(player, chosen);
        } else {
            plugin.getChunkClaimManager().isMember(chosen.getId(), player.getUniqueId())
                .thenAccept(isMember -> {
                    if (isMember) {
                        teleportToHome(player, chosen);
                    } else {
                        player.sendMessage("§c你没有权限传送到此home点！");
                    }
                });
        }
    }

    private void teleportToHome(Player player, ChunkClaim claim) {
        Location home = claim.getHomeLocation();
        if (home == null || home.getWorld() == null) {
            player.sendMessage("§cHome点位置无效！");
            return;
        }

        // 播放传送前音效
        player.playSound(player.getLocation(), org.bukkit.Sound.BLOCK_PORTAL_TRIGGER, 0.5f, 1.5f);
        
        player.teleport(home);
        
        // 播放传送后音效和粒子
        player.playSound(home, org.bukkit.Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 1.0f);
        home.getWorld().spawnParticle(org.bukkit.Particle.PORTAL, home.clone().add(0, 1, 0), 30, 0.5, 1, 0.5, 0.1);
        
        player.sendMessage("§a已传送到home点: §e" + getClaimDisplayName(claim));
    }

    private void handleInvite(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage("§c用法: /claim invite <玩家>");
            return;
        }

        Optional<ChunkClaim> claim = plugin.getChunkClaimManager().findChunkClaimAt(player.getLocation());
        if (claim.isEmpty()) {
            player.sendMessage("§c当前位置不在任何领地内！");
            return;
        }

        if (!claim.get().getOwner().equals(player.getUniqueId()) && !player.isOp()) {
            player.sendMessage("§c只有所有者可以邀请玩家！");
            return;
        }

        String targetName = args[1];
        Player target = plugin.getServer().getPlayer(targetName);
        if (target == null) {
            player.sendMessage("§c未找到玩家: " + targetName);
            return;
        }

        if (target.getUniqueId().equals(player.getUniqueId())) {
            player.sendMessage("§c不能邀请自己！");
            return;
        }

        // 检查是否已经邀请过
        if (pendingInvites.containsKey(target.getUniqueId())) {
            PendingInvite existingInvite = pendingInvites.get(target.getUniqueId());
            if (!existingInvite.isExpired() && existingInvite.claimId == claim.get().getId()) {
                player.sendMessage("§c该玩家已有待处理的邀请！");
                return;
            }
        }
        
        // 检查是否已经是成员
        plugin.getChunkClaimManager().isMember(claim.get().getId(), target.getUniqueId())
            .thenAccept(isMember -> {
                if (isMember) {
                    player.sendMessage("§c该玩家已经是成员了！");
                    return;
                }
                
                // 发送邀请
                pendingInvites.put(target.getUniqueId(), new PendingInvite(claim.get().getId(), player.getUniqueId()));
                player.sendMessage("§a已邀请玩家: " + targetName);
                target.sendMessage("§a" + player.getName() + " 邀请你加入他的领地！");
                target.sendMessage("§7使用 §e/claim accept §7接受邀请（5分钟内有效）");
            });
    }

    private void handleKick(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage("§c用法: /claim kick <玩家>");
            return;
        }

        Optional<ChunkClaim> claim = plugin.getChunkClaimManager().findChunkClaimAt(player.getLocation());
        if (claim.isEmpty()) {
            player.sendMessage("§c当前位置不在任何领地内！");
            return;
        }

        if (!claim.get().getOwner().equals(player.getUniqueId()) && !player.isOp()) {
            player.sendMessage("§c只有所有者可以踢出成员！");
            return;
        }

        String targetName = args[1];
        org.bukkit.OfflinePlayer target = plugin.getServer().getOfflinePlayer(targetName);
        if (target == null || !target.hasPlayedBefore()) {
            player.sendMessage("§c未找到玩家: " + targetName);
            return;
        }
        
        UUID targetId = target.getUniqueId();
        ChunkClaim c = claim.get();
        
        // 检查是否是成员
        plugin.getChunkClaimManager().isMember(c.getId(), targetId)
            .thenAccept(isMember -> {
                if (!isMember) {
                    player.sendMessage("§c该玩家不是成员！");
                    return;
                }
                
                // 移除成员
                plugin.getChunkClaimManager().removeMember(c.getId(), targetId)
                    .thenRun(() -> {
                        player.sendMessage("§a已踢出成员: " + targetName);
                        if (target.isOnline()) {
                            target.getPlayer().sendMessage("§c你已被移出该领地！");
                        }
                    })
                    .exceptionally(throwable -> {
                        player.sendMessage("§c踢出成员失败: " + throwable.getCause().getMessage());
                        return null;
                    });
            });
    }

    private void handleLeave(Player player) {
        Optional<ChunkClaim> claim = plugin.getChunkClaimManager().findChunkClaimAt(player.getLocation());
        if (claim.isEmpty()) {
            player.sendMessage("§c当前位置不在任何领地内！");
            return;
        }
        
        ChunkClaim c = claim.get();
        
        // 不能离开自己拥有的领地
        if (c.getOwner().equals(player.getUniqueId())) {
            player.sendMessage("§c你不能离开自己拥有的领地！使用 /claim dissolve 解散领地");
            return;
        }
        
        // 检查是否是成员
        plugin.getChunkClaimManager().isMember(c.getId(), player.getUniqueId())
            .thenAccept(isMember -> {
                if (!isMember) {
                    player.sendMessage("§c你不是该领地的成员！");
                    return;
                }
                
                // 移除成员
                plugin.getChunkClaimManager().removeMember(c.getId(), player.getUniqueId())
                    .thenRun(() -> {
                        player.sendMessage("§a已离开领地: " + c.getChunkName());
                    })
                    .exceptionally(throwable -> {
                        player.sendMessage("§c离开失败: " + throwable.getCause().getMessage());
                        return null;
                    });
            });
    }

    private void handleLock(Player player) {
        Optional<ChunkClaim> claim = plugin.getChunkClaimManager().findChunkClaimAt(player.getLocation());
        if (claim.isEmpty()) {
            player.sendMessage("§c当前位置不在任何领地内！");
            return;
        }

        if (!claim.get().getOwner().equals(player.getUniqueId()) && !player.isOp()) {
            player.sendMessage("§c只有所有者可以锁定领地！");
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

    private void handleRecipe(Player player) {
        player.sendMessage("§6=== 能量电池配方 ===");
        player.sendMessage("§7在认领的区块内放置一个箱子");
        player.sendMessage("§7在箱子的§e中间3x3区域§7按照以下布局放置物品：");
        player.sendMessage("§7");
        player.sendMessage("§7箱子布局（中间3x3）：");
        player.sendMessage("§7   §fI  §bD  §fI");
        player.sendMessage("§7   §bD  §fI  §bD");
        player.sendMessage("§7   §fI  §bD  §fI");
        player.sendMessage("§7");
        player.sendMessage("§7§bD §7= 钻石 (DIAMOND)");
        player.sendMessage("§7§fI §7= 铁锭 (IRON_INGOT)");
        player.sendMessage("§7");
        player.sendMessage("§c注意：配方必须放在箱子的中间3x3区域！");
        player.sendMessage("§7放置完成后关闭箱子即可创建能量电池");
    }

    private void handleName(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage("§c用法: /claim name <名称>");
            return;
        }

        Optional<ChunkClaim> claim = plugin.getChunkClaimManager().findChunkClaimAt(player.getLocation());
        if (claim.isEmpty()) {
            player.sendMessage("§c当前位置不在任何领地内！");
            return;
        }

        if (!claim.get().getOwner().equals(player.getUniqueId()) && !player.isOp()) {
            player.sendMessage("§c只有所有者可以重命名领地！");
            return;
        }

        String name = String.join(" ", Arrays.copyOfRange(args, 1, args.length));
        ChunkClaim c = claim.get();
        
        // 如果属于区域，更新区域名称
        if (c.getRegionId() > 0) {
            ClaimRegion region = plugin.getChunkClaimManager().getRegion(c.getRegionId());
            if (region != null) {
                int regionId = c.getRegionId();
                region.setRegionName(name);
                plugin.getChunkClaimDAO().updateRegionNameAsync(regionId, name)
                    .thenRun(() -> {
                        plugin.getChunkClaimManager().invalidateCache();
                        // 刷新该区域内所有玩家的BossBar
                        plugin.getServer().getScheduler().runTask(plugin, () -> {
                            plugin.getClaimEnterListener().refreshBossBarsForRegion(regionId, name);
                        });
                        player.sendMessage("§a领地名称已更新: " + name);
                    });
                return;
            }
        }
        
        // 孤立区块，更新区块名称
        c.setChunkName(name);
        plugin.getChunkClaimDAO().updateChunkClaimAsync(c)
            .thenRun(() -> {
                plugin.getChunkClaimManager().invalidateCache();
                // 刷新玩家的BossBar
                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    plugin.getClaimEnterListener().forceRefreshPlayerBossBar(player);
                });
                player.sendMessage("§a领地名称已更新: " + name);
            });
    }

    private void handleTransfer(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage("§c用法: /claim transfer <玩家>");
            return;
        }

        Optional<ChunkClaim> claim = plugin.getChunkClaimManager().findChunkClaimAt(player.getLocation());
        if (claim.isEmpty()) {
            player.sendMessage("§c当前位置不在任何领地内！");
            return;
        }

        if (!claim.get().getOwner().equals(player.getUniqueId()) && !player.isOp()) {
            player.sendMessage("§c只有所有者可以转移领地！");
            return;
        }

        String targetName = args[1];
        Player target = plugin.getServer().getPlayer(targetName);
        if (target == null) {
            player.sendMessage("§c未找到玩家: " + targetName);
            return;
        }

        // 转移所有权（转移整个区域）
        ChunkClaim c = claim.get();
        int regionId = c.getRegionId();
        
        // 获取区域信息用于显示
        String transferInfo;
        if (regionId > 0) {
            org.kari.kariClaims.models.ClaimRegion region = plugin.getChunkClaimManager().getRegion(regionId);
            if (region != null) {
                int chunkCount = region.getChunks().size();
                transferInfo = "§a已将区域（包含 " + chunkCount + " 个区块）转移给: " + targetName;
            } else {
                transferInfo = "§a已将领地转移给: " + targetName;
            }
        } else {
            transferInfo = "§a已将领地转移给: " + targetName;
        }
        
        plugin.getChunkClaimManager().transferRegionOwnership(c.getId(), target.getUniqueId())
            .thenRun(() -> {
                player.sendMessage(transferInfo);
                if (regionId > 0) {
                    target.sendMessage("§a" + player.getName() + " 已将一个区域（包含多个区块）转移给你！");
                } else {
                    target.sendMessage("§a" + player.getName() + " 已将领地转移给你！");
                }
            })
            .exceptionally(throwable -> {
                player.sendMessage("§c转移失败: " + throwable.getCause().getMessage());
                return null;
            });
    }

    private void handleMerge(Player player, String[] args) {
        if (!player.hasPermission("kariClaims.claim")) {
            player.sendMessage("§c你没有权限执行此命令！");
            return;
        }

        if (args.length < 2) {
            player.sendMessage("§c用法: /claim merge <区域ID>");
            return;
        }

        int regionId;
        try {
            regionId = Integer.parseInt(args[1]);
        } catch (NumberFormatException e) {
            player.sendMessage("§c无效的区域ID！");
            return;
        }

        Chunk chunk = player.getLocation().getChunk();
        
        // 检查是否已被认领
        plugin.getChunkClaimManager().findChunkClaimAtAsync(player.getLocation())
            .thenAccept(claimOpt -> {
                if (claimOpt.isPresent()) {
                    player.sendMessage("§c此区块已被认领！");
                    return;
                }

                plugin.getChunkClaimManager().claimChunkIntoRegion(player, chunk, regionId)
                    .thenAccept(claim -> {
                         player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);
                         player.sendMessage("§a区块认领并合并成功！");
                    })
                    .exceptionally(throwable -> {
                        player.sendMessage("§c认领失败: " + throwable.getCause().getMessage());
                        return null;
                    });
            });
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return Arrays.asList("claim", "unclaim", "list", "accept", "ban", "unban", "dissolve", 
                "sethome", "home", "invite", "kick", "leave", "lock", "show", "recipe", "name", "setname", "transfer", "merge", "admin")
                .stream()
                .filter(s -> s.startsWith(args[0].toLowerCase()))
                .collect(Collectors.toList());
        } else if (args.length == 2 && args[0].equalsIgnoreCase("home")) {
            List<String> names = new ArrayList<>();
            if (sender instanceof Player) {
                Player p = (Player) sender;
                try {
                    plugin.getChunkClaimManager().getPlayerChunkClaimsAsync(p.getUniqueId()).get()
                        .forEach(c -> {
                            if (c.getHomeLocation() != null) {
                                names.add(getClaimDisplayName(c));
                            }
                        });
                    plugin.getChunkClaimDAO().getMemberChunkClaimsAsync(p.getUniqueId()).get()
                        .forEach(c -> {
                            if (c.getHomeLocation() != null) {
                                names.add(getClaimDisplayName(c));
                            }
                        });
                    plugin.getChunkClaimDAO().getPublicClaimsSync()
                        .forEach(c -> {
                            if (c.getHomeLocation() != null && c.isHomePublic()) {
                                names.add(getClaimDisplayName(c));
                            }
                        });
                } catch (Exception ignored) {}
            }
            return names.stream()
                .filter(s -> s.toLowerCase().startsWith(args[1].toLowerCase()))
                .distinct()
                .collect(Collectors.toList());
        } else if (args.length == 2 && args[0].equalsIgnoreCase("admin")) {
            return Arrays.asList("reload", "list", "rename", "lock", "removeclaim", "transfer")
                .stream()
                .filter(s -> s.startsWith(args[1].toLowerCase()))
                .collect(Collectors.toList());
        } else if (args.length == 3 && args[0].equalsIgnoreCase("admin") && args[1].equalsIgnoreCase("list")) {
            // admin list 补全在线玩家名
            return org.bukkit.Bukkit.getOnlinePlayers().stream()
                .map(Player::getName)
                .filter(s -> s.toLowerCase().startsWith(args[2].toLowerCase()))
                .collect(Collectors.toList());
        } else if (args.length == 3 && args[0].equalsIgnoreCase("admin") && args[1].equalsIgnoreCase("transfer")) {
            // admin transfer 补全在线玩家名
            return org.bukkit.Bukkit.getOnlinePlayers().stream()
                .map(Player::getName)
                .filter(s -> s.toLowerCase().startsWith(args[2].toLowerCase()))
                .collect(Collectors.toList());
        } else if (args.length == 2 && (args[0].equalsIgnoreCase("ban") || 
                   args[0].equalsIgnoreCase("unban") || 
                   args[0].equalsIgnoreCase("invite") || 
                   args[0].equalsIgnoreCase("kick") ||
                   args[0].equalsIgnoreCase("transfer"))) {
            // 这些命令需要玩家名参数，补全在线玩家名
            return org.bukkit.Bukkit.getOnlinePlayers().stream()
                .map(Player::getName)
                .filter(s -> s.toLowerCase().startsWith(args[1].toLowerCase()))
                .collect(Collectors.toList());
        }
        return Collections.emptyList();
    }

    /**
     * 显示当前领地/区域范围的粒子框
     */
    private void handleShow(Player player) {
        Optional<ChunkClaim> claimOpt = plugin.getChunkClaimManager().findChunkClaimAt(player.getLocation());
        if (claimOpt.isEmpty()) {
            player.sendMessage("§c当前位置不在任何领地内！");
            return;
        }
        ChunkClaim startClaim = claimOpt.get();
        int regionId = startClaim.getRegionId();
        
        int durationSeconds = 60;
        long period = 10L; // every 0.5s
        long iterations = durationSeconds * 20 / period;

        new BukkitRunnable() {
            long count = 0;
            @Override
            public void run() {
                if (count++ > iterations || !player.isOnline()) {
                    cancel();
                    return;
                }
                
                // 动态获取当前的区块集合
                Set<ChunkClaim> chunks;
                if (regionId > 0) {
                    ClaimRegion region = plugin.getChunkClaimManager().getRegion(regionId);
                    if (region != null) {
                        chunks = region.getChunks();
                    } else {
                        chunks = Collections.emptySet();
                    }
                } else {
                    // 如果没有区域ID，尝试重新获取当前区块的最新状态（可能已被合并）
                    Optional<ChunkClaim> currentOpt = plugin.getChunkClaimManager().findChunkClaimAt(player.getLocation());
                    if (currentOpt.isPresent() && currentOpt.get().getId() == startClaim.getId()) {
                        chunks = Collections.singleton(currentOpt.get());
                    } else {
                        chunks = Collections.emptySet();
                    }
                }
                
                if (chunks.isEmpty()) return;
                
                spawnRegionOutline(player, chunks, player.getLocation().getBlockY());
            }
        }.runTaskTimer(plugin, 0L, period);

        player.sendMessage("§a已显示当前区域范围，持续 60 秒。");
    }
    
    private void spawnRegionOutline(Player player, Set<ChunkClaim> chunks, int y) {
        // 构建坐标查找表
        Set<Long> chunkKeys = new HashSet<>();
        for (ChunkClaim c : chunks) {
            chunkKeys.add((long)c.getChunkX() << 32 | (c.getChunkZ() & 0xFFFFFFFFL));
        }
        
        for (ChunkClaim c : chunks) {
            int cx = c.getChunkX();
            int cz = c.getChunkZ();
            int baseX = cx << 4;
            int baseZ = cz << 4;
            
            // 北边 (z-1)
            if (!chunkKeys.contains((long)cx << 32 | ((cz - 1) & 0xFFFFFFFFL))) {
                for (int i = 0; i <= 16; i++) spawnParticle(player, baseX + i, y, baseZ);
            }
            
            // 南边 (z+1)
            if (!chunkKeys.contains((long)cx << 32 | ((cz + 1) & 0xFFFFFFFFL))) {
                for (int i = 0; i <= 16; i++) spawnParticle(player, baseX + i, y, baseZ + 16);
            }
            
            // 西边 (x-1)
            if (!chunkKeys.contains((long)(cx - 1) << 32 | (cz & 0xFFFFFFFFL))) {
                for (int i = 0; i <= 16; i++) spawnParticle(player, baseX, y, baseZ + i);
            }
            
            // 东边 (x+1)
            if (!chunkKeys.contains((long)(cx + 1) << 32 | (cz & 0xFFFFFFFFL))) {
                for (int i = 0; i <= 16; i++) spawnParticle(player, baseX + 16, y, baseZ + i);
            }
        }
    }

    private void spawnParticle(Player player, double x, int y, double z) {
        player.spawnParticle(org.bukkit.Particle.DUST,
            new org.bukkit.Location(player.getWorld(), x, y + 0.2, z),
            1, new org.bukkit.Particle.DustOptions(org.bukkit.Color.AQUA, 1.2f));
    }
}

