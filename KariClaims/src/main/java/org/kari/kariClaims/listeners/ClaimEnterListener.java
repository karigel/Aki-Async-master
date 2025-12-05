package org.kari.kariClaims.listeners;

import org.bukkit.Bukkit;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.kari.kariClaims.KariClaims;
import org.kari.kariClaims.models.ChunkClaim;
import org.kari.kariClaims.models.ClaimRegion;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * 监听玩家进入/离开领地，显示BossBar
 */
public class ClaimEnterListener implements Listener {
    private final KariClaims plugin;
    private final Map<UUID, BossBar> playerBossBars = new HashMap<>();
    private final Map<UUID, Integer> lastRegionId = new HashMap<>();

    public ClaimEnterListener(KariClaims plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        // 只检测跨区块移动
        if (event.getFrom().getChunk().equals(event.getTo().getChunk())) {
            return;
        }
        
        // 检查配置是否启用BossBar
        if (!plugin.getConfig().getBoolean("claim-bossbar.enabled", true)) {
            return;
        }
        
        Player player = event.getPlayer();
        
        // 异步检查领地
        plugin.getChunkClaimManager().findChunkClaimAtAsync(event.getTo())
            .thenAccept(claimOpt -> {
                // 在主线程处理BossBar
                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    handleClaimChange(player, claimOpt);
                });
            });
    }
    
    @EventHandler
    public void onPlayerTeleport(PlayerTeleportEvent event) {
        if (event.getTo() == null) {
            return;
        }
        
        // 检查配置是否启用BossBar
        if (!plugin.getConfig().getBoolean("claim-bossbar.enabled", true)) {
            return;
        }
        
        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();
        
        // 保存当前区域ID用于比较（不要在这里清除，让handleClaimChange处理）
        Integer previousRegionId = lastRegionId.get(playerId);
        
        // 异步检查目标位置的领地
        plugin.getChunkClaimManager().findChunkClaimAtAsync(event.getTo())
            .thenAccept(claimOpt -> {
                // 在主线程处理BossBar
                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    // 传送时强制更新，即使目标是同一区域
                    handleTeleportClaimChange(player, claimOpt, previousRegionId);
                });
            });
    }
    
    /**
     * 处理传送时的领地变化（强制更新BossBar）
     */
    private void handleTeleportClaimChange(Player player, Optional<ChunkClaim> claimOpt, Integer previousRegionId) {
        UUID playerId = player.getUniqueId();
        
        if (claimOpt.isEmpty()) {
            // 传送到非领地区域，移除BossBar
            removeBossBar(player);
            lastRegionId.remove(playerId);
            return;
        }
        
        ChunkClaim claim = claimOpt.get();
        int regionId = claim.getRegionId();
        
        // 获取区域信息
        ClaimRegion region = plugin.getChunkClaimManager().getRegion(regionId);
        
        // 检查锁定状态
        if (region != null && region.isLocked()) {
            boolean isOwner = claim.getOwner().equals(playerId);
            boolean isMember = plugin.getChunkClaimManager().isMemberCached(claim.getId(), playerId);
            boolean canBypass = player.hasPermission("kariClaims.bypass.lock");
            
            if (!isOwner && !isMember && !canBypass) {
                player.sendMessage("§c此领地已锁定，非成员无法进入！");
                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    org.bukkit.Location safeLoc = findSafeLocationOutsideClaim(player, claim);
                    if (safeLoc != null) {
                        player.teleport(safeLoc);
                    }
                });
                return;
            }
        }
        
        // 更新BossBar
        lastRegionId.put(playerId, regionId);
        String regionName = region != null ? region.getRegionName() : "未命名领地";
        updateBossBar(player, regionName, BarColor.YELLOW);
    }
    
    private void handleClaimChange(Player player, Optional<ChunkClaim> claimOpt) {
        UUID playerId = player.getUniqueId();
        Integer currentRegionId = lastRegionId.get(playerId);
        
        if (claimOpt.isEmpty()) {
            // 离开领地
            if (currentRegionId != null) {
                removeBossBar(player);
                lastRegionId.remove(playerId);
            }
            return;
        }
        
        ChunkClaim claim = claimOpt.get();
        int regionId = claim.getRegionId();
        
        // 获取区域信息
        ClaimRegion region = plugin.getChunkClaimManager().getRegion(regionId);
        
        // 检查锁定状态，阻止非成员进入
        if (region != null && region.isLocked()) {
            // 检查是否是所有者或成员
            boolean isOwner = claim.getOwner().equals(playerId);
            boolean isMember = plugin.getChunkClaimManager().isMemberCached(claim.getId(), playerId);
            boolean canBypass = player.hasPermission("kariClaims.bypass.lock");
            
            if (!isOwner && !isMember && !canBypass) {
                // 阻止进入，传送回原位置
                player.sendMessage("§c此领地已锁定，非成员无法进入！");
                
                // 获取上一个位置并传送回去
                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    // 传送到领地外的安全位置
                    org.bukkit.Location safeLoc = findSafeLocationOutsideClaim(player, claim);
                    if (safeLoc != null) {
                        player.teleport(safeLoc);
                    }
                });
                return;
            }
        }
        
        // 如果还在同一个区域内，不需要更新BossBar
        if (currentRegionId != null && currentRegionId == regionId) {
            return;
        }
        
        // 进入新领地或切换区域
        lastRegionId.put(playerId, regionId);
        
        String regionName = region != null ? region.getRegionName() : "未命名领地";
        
        // BossBar统一使用黄色
        BarColor color = BarColor.YELLOW;
        
        updateBossBar(player, regionName, color);
    }
    
    /**
     * 找到领地外的安全位置
     */
    private org.bukkit.Location findSafeLocationOutsideClaim(Player player, ChunkClaim claim) {
        org.bukkit.Location loc = player.getLocation();
        org.bukkit.World world = loc.getWorld();
        if (world == null) return null;
        
        // 尝试传送到相邻区块
        int chunkX = claim.getChunkX();
        int chunkZ = claim.getChunkZ();
        
        int[][] offsets = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        for (int[] offset : offsets) {
            int newChunkX = chunkX + offset[0];
            int newChunkZ = chunkZ + offset[1];
            
            // 检查这个区块是否也是锁定的领地
            org.bukkit.Location testLoc = new org.bukkit.Location(world, 
                newChunkX * 16 + 8, loc.getBlockY(), newChunkZ * 16 + 8);
            
            Optional<ChunkClaim> testClaim = plugin.getChunkClaimManager().findChunkClaimAt(testLoc);
            if (testClaim.isEmpty()) {
                // 找到安全位置
                testLoc.setY(world.getHighestBlockYAt(testLoc) + 1);
                return testLoc;
            }
        }
        
        // 如果找不到，返回出生点
        return world.getSpawnLocation();
    }
    
    private void updateBossBar(Player player, String title, BarColor color) {
        UUID playerId = player.getUniqueId();
        BossBar bossBar = playerBossBars.get(playerId);
        
        if (bossBar == null) {
            bossBar = Bukkit.createBossBar(title, color, BarStyle.SOLID);
            bossBar.addPlayer(player);
            playerBossBars.put(playerId, bossBar);
        } else {
            bossBar.setTitle(title);
            bossBar.setColor(color);
        }
        
        bossBar.setProgress(1.0);
        bossBar.setVisible(true);
    }
    
    private void removeBossBar(Player player) {
        UUID playerId = player.getUniqueId();
        BossBar bossBar = playerBossBars.remove(playerId);
        if (bossBar != null) {
            bossBar.removePlayer(player);
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        removeBossBar(player);
        lastRegionId.remove(player.getUniqueId());
    }
    
    /**
     * 清理所有BossBar（插件关闭时调用）
     */
    public void cleanup() {
        for (BossBar bossBar : playerBossBars.values()) {
            bossBar.removeAll();
        }
        playerBossBars.clear();
        lastRegionId.clear();
    }
    
    /**
     * 刷新指定区域的所有玩家BossBar（重命名后调用）
     */
    public void refreshBossBarsForRegion(int regionId, String newName) {
        for (Map.Entry<UUID, Integer> entry : lastRegionId.entrySet()) {
            if (entry.getValue() == regionId) {
                UUID playerId = entry.getKey();
                BossBar bossBar = playerBossBars.get(playerId);
                if (bossBar != null) {
                    bossBar.setTitle(newName);
                }
            }
        }
    }
    
    /**
     * 强制刷新玩家的BossBar（用于名称更新后）
     */
    public void forceRefreshPlayerBossBar(Player player) {
        UUID playerId = player.getUniqueId();
        // 清除当前状态，强制重新检测
        lastRegionId.remove(playerId);
        removeBossBar(player);
        
        // 重新检测当前位置
        plugin.getChunkClaimManager().findChunkClaimAtAsync(player.getLocation())
            .thenAccept(claimOpt -> {
                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    handleClaimChange(player, claimOpt);
                });
            });
    }
    
    /**
     * 清除指定区域的所有玩家BossBar（领地解散时调用）
     */
    public void clearBossBarsForRegions(Set<Integer> regionIds) {
        for (Map.Entry<UUID, Integer> entry : new HashMap<>(lastRegionId).entrySet()) {
            if (regionIds.contains(entry.getValue())) {
                UUID playerId = entry.getKey();
                Player player = plugin.getServer().getPlayer(playerId);
                if (player != null) {
                    removeBossBar(player);
                }
                lastRegionId.remove(playerId);
            }
        }
    }
    
    /**
     * 强制刷新所有在线玩家的BossBar（用于批量更新后）
     */
    public void forceRefreshAllBossBars() {
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            forceRefreshPlayerBossBar(player);
        }
    }
}
