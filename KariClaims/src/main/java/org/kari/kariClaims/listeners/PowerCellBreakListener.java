package org.kari.kariClaims.listeners;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.inventory.ItemStack;
import org.kari.kariClaims.KariClaims;
import org.kari.kariClaims.models.ChunkClaim;

/**
 * 能量电池破坏监听器
 */
public class PowerCellBreakListener implements Listener {
    private final KariClaims plugin;

    public PowerCellBreakListener(KariClaims plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPowerCellBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        if (block.getType() != Material.CHEST) {
            return;
        }

        Player player = event.getPlayer();
        
        // 检查是否是能量电池
        plugin.getChunkClaimManager().findChunkClaimAtAsync(block.getLocation())
            .thenAccept(claimOpt -> {
                if (claimOpt.isEmpty()) {
                    return;
                }
                
                ChunkClaim claim = claimOpt.get();
                
                // 检查能量电池位置
                plugin.getChunkClaimDAO().getPowerCellLocationAsync(claim.getId())
                    .thenAccept(powerCellLoc -> {
                        if (powerCellLoc == null || !block.getLocation().equals(powerCellLoc)) {
                            // 不是能量电池
                            return;
                        }
                        
                        // 这是能量电池！
                        event.setCancelled(true);
                        
                        // 检查权限
                        if (!claim.getOwner().equals(player.getUniqueId()) && 
                            !player.hasPermission("kariClaims.admin.bypass")) {
                            player.sendMessage("§c只有领地所有者可以破坏能量电池！");
                            return;
                        }
                        
                        // 在主线程上处理破坏
                        plugin.getServer().getScheduler().runTask(plugin, () -> {
                            handlePowerCellBreak(player, claim, block);
                        });
                    });
            });
    }

    /**
     * 处理能量电池破坏
     */
    private void handlePowerCellBreak(Player player, ChunkClaim claim, Block chestBlock) {
        if (!(chestBlock.getState() instanceof Chest)) {
            // 方块已变更，直接进入后续流程
            proceedAfterBreak(player, claim, chestBlock, new ItemStack[0]);
            return;
        }

        Chest chest = (Chest) chestBlock.getState();
        Location loc = chestBlock.getLocation();
        
        // 掉落箱子中的所有物品
        for (ItemStack item : chest.getInventory().getContents()) {
            if (item != null && item.getType() != Material.AIR) {
                loc.getWorld().dropItemNaturally(loc, item);
            }
        }
        
        // 掉落箱子本身
        loc.getWorld().dropItemNaturally(loc, new ItemStack(Material.CHEST));

        proceedAfterBreak(player, claim, chestBlock, chest.getInventory().getContents());
    }

    private void proceedAfterBreak(Player player, ChunkClaim claim, Block chestBlock, ItemStack[] dropped) {
        Location loc = chestBlock.getLocation();

        // 回退金钱
        double refundAmount = claim.getEconomyBalance();
        if (refundAmount > 0) {
            org.bukkit.OfflinePlayer owner = plugin.getServer().getOfflinePlayer(claim.getOwner());
            if (plugin.getEconomyManager().isEnabled()) {
                boolean success = plugin.getEconomyManager().deposit(owner, refundAmount);
                if (success) {
                    String formattedAmount = plugin.getEconomyManager().format(refundAmount);
                    player.sendMessage("§e已回退 §a" + formattedAmount + " §e金钱到账户");
                    if (owner.isOnline() && !owner.getUniqueId().equals(player.getUniqueId())) {
                        owner.getPlayer().sendMessage("§e你的能量电池被破坏，已回退 §a" + formattedAmount + " §e金钱");
                    }
                } else {
                    player.sendMessage("§c回退金钱失败，请联系管理员");
                }
            } else {
                player.sendMessage("§e应回退 §a" + String.format("%.2f", refundAmount) + " §e金钱（经济系统未启用）");
            }
        }

        // 保留当前剩余初始保护时间（不重置为满值）
        long initialTime = Math.max(0, claim.getInitialTime());
        claim.setInitialTime(initialTime);
        claim.setEnergyTime(0);
        claim.setEconomyBalance(0);

        // 反馈音效和粒子
        loc.getWorld().spawnParticle(org.bukkit.Particle.CLOUD, loc.clone().add(0.5, 0.8, 0.5), 20, 0.3, 0.3, 0.3, 0.02);
        loc.getWorld().playSound(loc, org.bukkit.Sound.ENTITY_GENERIC_EXPLODE, 0.7f, 1.0f);

        // 删除能量电池记录
        plugin.getChunkClaimDAO().deletePowerCellAsync(claim.getId());

        // 从缓存移除
        plugin.getChunkClaimManager().unregisterPowerCellLocation(loc);

        // 更新领地
        plugin.getChunkClaimDAO().updateChunkClaimAsync(claim)
            .thenRun(() -> plugin.getServer().getScheduler().runTask(plugin, () -> {
                // 破坏箱子
                chestBlock.setType(Material.AIR);

                // 播放音效
                player.playSound(loc, org.bukkit.Sound.ENTITY_ITEM_BREAK, 1.0f, 1.0f);

                player.sendMessage("§c能量电池已破坏！");
                player.sendMessage("§7领地还剩 §e" + (initialTime / 60) + "分钟 §7初始保护时间");
                player.sendMessage("§7请在" + (initialTime / 60) + "分钟内重新创建能量电池，否则领地将被解散！");

                // 通知领地主人
                org.bukkit.entity.Player ownerOnline = plugin.getServer().getPlayer(claim.getOwner());
                if (ownerOnline != null && ownerOnline.isOnline() && !ownerOnline.getUniqueId().equals(player.getUniqueId())) {
                    ownerOnline.sendMessage("§c你的能量电池被破坏，请尽快重新放置！");
                    ownerOnline.playSound(ownerOnline.getLocation(), org.bukkit.Sound.BLOCK_BEACON_DEACTIVATE, 1.0f, 0.8f);
                }

                // 启动倒计时任务
                startDissolutionCountdown(claim, initialTime);
            }));
    }

    /**
     * 启动领地解散倒计时
     */
    private void startDissolutionCountdown(ChunkClaim claim, long initialTimeSeconds) {
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            // 倒计时结束后检查是否重建了能量电池
            plugin.getChunkClaimDAO().getPowerCellLocationAsync(claim.getId())
                .thenAccept(powerCellLoc -> {
                    if (powerCellLoc == null) {
                        // 没有重建能量电池，解散领地
                        plugin.getChunkClaimDAO().findChunkClaimByIdAsync(claim.getId())
                            .thenAccept(claimOpt -> {
                                if (claimOpt.isEmpty()) {
                                    return; // 领地已被删除
                                }
                                
                                ChunkClaim latestClaim = claimOpt.get();
                                
                                // 再次检查是否所有时间都耗尽
                                if (latestClaim.getEnergyTime() <= 0 && 
                                    latestClaim.getEconomyBalance() <= 0 && 
                                    latestClaim.getInitialTime() <= 0) {
                                    
                                    // 解散领地
                                    plugin.getChunkClaimDAO().deleteChunkClaimAsync(latestClaim.getId())
                                        .thenRun(() -> {
                                            plugin.getChunkClaimManager().invalidateCache();
                                            
                                            Player owner = Bukkit.getPlayer(latestClaim.getOwner());
                                            if (owner != null && owner.isOnline()) {
                                                owner.sendMessage("§c你的领地已因能量耗尽而解散！");
                                            }
                                        });
                                }
                            });
                    }
                });
        }, initialTimeSeconds * 20L); // 转换为ticks
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityExplode(org.bukkit.event.entity.EntityExplodeEvent event) {
        java.util.Iterator<Block> it = event.blockList().iterator();
        while (it.hasNext()) {
            Block block = it.next();
            if (block.getType() == Material.CHEST) {
                 if (plugin.getChunkClaimManager().getPowerCellClaimIdAt(block.getLocation()) != null) {
                     it.remove();
                 }
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockExplode(org.bukkit.event.block.BlockExplodeEvent event) {
        java.util.Iterator<Block> it = event.blockList().iterator();
        while (it.hasNext()) {
            Block block = it.next();
            if (block.getType() == Material.CHEST) {
                 if (plugin.getChunkClaimManager().getPowerCellClaimIdAt(block.getLocation()) != null) {
                     it.remove();
                 }
            }
        }
    }
}

