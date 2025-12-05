package org.kari.kariClaims.managers;

import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.kari.kariClaims.KariClaims;
import org.kari.kariClaims.database.ChunkClaimDAO;
import org.kari.kariClaims.models.ChunkClaim;
import org.kari.kariClaims.models.ClaimRegion;
import org.kari.kariClaims.utils.ChunkAnimationUtils;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 区块领地管理器
 */
public class ChunkClaimManager {
    private final KariClaims plugin;
    private final ChunkClaimDAO chunkClaimDAO;
    
    // 缓存系统
    private final Map<String, ChunkClaim> chunkCache = new ConcurrentHashMap<>();
    private final Map<UUID, List<ChunkClaim>> playerClaimsCache = new ConcurrentHashMap<>();
    private final Map<Integer, ClaimRegion> regionCache = new ConcurrentHashMap<>();
    private final Map<Integer, Set<UUID>> bannedPlayersCache = new ConcurrentHashMap<>();
    // 领地成员缓存: ClaimId -> (PlayerId -> ClaimMember)
    private final Map<Integer, Map<UUID, org.kari.kariClaims.models.ClaimMember>> claimMembersCache = new ConcurrentHashMap<>();
    
    // 临时保护时间（10分钟）
    private final Map<UUID, TemporaryProtection> temporaryProtections = new ConcurrentHashMap<>();
    
    // 能源箱位置缓存（用于快速检查）
    private final Map<String, Integer> powerCellLocationCache = new ConcurrentHashMap<>(); // "world:x:y:z" -> claimId
    
    // 数据是否已完全加载
    private volatile boolean dataLoaded = false;

    /**
     * 检查数据是否已加载完成
     */
    public boolean isDataLoaded() {
        return dataLoaded;
    }

    /**
     * 检查缓存是否为空
     */
    public boolean isCacheEmpty() {
        return chunkCache.isEmpty() && powerCellLocationCache.isEmpty();
    }

    /**
     * 检查指定领地ID是否有注册的能量电池
     */
    public boolean hasPowerCell(int claimId) {
        return powerCellLocationCache.containsValue(claimId);
    }

    public ChunkClaimManager(KariClaims plugin, ChunkClaimDAO chunkClaimDAO) {
        this.plugin = plugin;
        this.chunkClaimDAO = chunkClaimDAO;
    }

    /**
     * 加载所有领地和区域到缓存
     */
    public void loadAllClaims() {
        // 先加载区域
        chunkClaimDAO.getAllRegionsAsync().thenAccept(regions -> {
            for (ClaimRegion region : regions) {
                regionCache.put(region.getId(), region);
            }
            
            // 再加载区块领地
            chunkClaimDAO.getAllChunkClaimsAsync().thenAccept(claims -> {
                for (ChunkClaim claim : claims) {
                    String cacheKey = claim.getWorld() + ":" + claim.getChunkX() + ":" + claim.getChunkZ();
                    chunkCache.put(cacheKey, claim);
                    
                    // 更新玩家领地缓存
                    playerClaimsCache.computeIfAbsent(claim.getOwner(), k -> new ArrayList<>()).add(claim);
                    
                    // 将区块添加到对应的区域
                    if (claim.getRegionId() > 0) {
                        ClaimRegion region = regionCache.get(claim.getRegionId());
                        if (region != null) {
                            region.addChunk(claim);
                            // 如果这是默认home点所在的区块，或者其他逻辑，可以在这里处理
                        }
                    }
                }
                
                // 加载所有成员
                chunkClaimDAO.getAllClaimMembersAsync().thenAccept(members -> {
                    for (org.kari.kariClaims.models.ClaimMember member : members) {
                        claimMembersCache.computeIfAbsent(member.getClaimId(), k -> new ConcurrentHashMap<>())
                            .put(member.getPlayerId(), member);
                    }
                    
                    // 标记数据加载完成
                    dataLoaded = true;
                    plugin.getLogger().info("所有领地数据已加载完成");
                }).exceptionally(e -> null);
            }).exceptionally(e -> null);
        }).exceptionally(e -> null);
    }

    /**
     * 立即认领区块并充值10分钟
     */
    public CompletableFuture<ChunkClaim> claimChunkImmediately(Player player, Chunk chunk) {
        UUID playerId = player.getUniqueId();
        
        // 检查相邻区域以检测冲突
        List<ClaimRegion> adjRegions = getAdjacentRegions(chunk.getWorld().getName(), chunk.getX(), chunk.getZ());
        
        // 过滤出该玩家拥有的相邻区域
        List<ClaimRegion> myAdjRegions = adjRegions.stream()
            .filter(r -> r.getOwner().equals(playerId))
            .distinct()
            .collect(Collectors.toList());
            
        // 查找相邻的无区域(孤立)区块
        List<ChunkClaim> myAdjOrphans = getAdjacentOrphanChunks(chunk.getWorld().getName(), chunk.getX(), chunk.getZ()).stream()
            .filter(c -> c.getOwner().equals(playerId))
            .collect(Collectors.toList());
            
        // 如果相邻于两个或更多不同的区域，抛出冲突异常
        // 注意：目前暂不处理同时相邻于区域和孤立区块的复杂情况，优先通过区域判断
        if (myAdjRegions.size() > 1) {
            CompletableFuture<ChunkClaim> future = new CompletableFuture<>();
            String regionsStr = myAdjRegions.stream()
                .map(r -> r.getId() + ":" + r.getRegionName())
                .collect(Collectors.joining(","));
            future.completeExceptionally(new IllegalStateException("REGION_CONFLICT:" + regionsStr));
            return future;
        }

        long initialTime = plugin.getConfig().getLong("power-cell.initial-protection-time", 600);
        
        return chunkClaimDAO.createChunkClaimAsync(playerId, chunk).thenCompose(claim -> {
            claim.setInitialTime(initialTime);
            
            CompletableFuture<ClaimRegion> regionFuture;
            
            if (!myAdjRegions.isEmpty()) {
                // 情况1: 合并到唯一相邻区域
                regionFuture = CompletableFuture.completedFuture(myAdjRegions.get(0));
            } else if (!myAdjOrphans.isEmpty()) {
                // 情况2: 没有相邻区域，但有相邻孤立区块 -> 为孤立区块创建新区域
                regionFuture = chunkClaimDAO.createRegionAsync(playerId, chunk.getWorld().getName(), player.getName())
                    .thenCompose(newRegion -> {
                        regionCache.put(newRegion.getId(), newRegion);
                        
                        // 将所有相邻孤立区块加入该区域
                        List<CompletableFuture<Void>> orphanUpdates = new ArrayList<>();
                        for (ChunkClaim orphan : myAdjOrphans) {
                            orphan.setRegionId(newRegion.getId());
                            newRegion.addChunk(orphan);
                            orphanUpdates.add(chunkClaimDAO.updateChunkClaimAsync(orphan));
                        }
                        
                        return CompletableFuture.allOf(orphanUpdates.toArray(new CompletableFuture[0]))
                            .thenApply(v -> newRegion);
                    });
            } else {
                // 情况3: 全新独立区块（暂无区域）
                regionFuture = chunkClaimDAO.createRegionAsync(playerId, chunk.getWorld().getName(), player.getName())
                    .thenApply(newRegion -> {
                        regionCache.put(newRegion.getId(), newRegion);
                        return newRegion;
                    });
            }
            
            return regionFuture.thenCompose(region -> {
                claim.setRegionId(region.getId());
                region.addChunk(claim);
                
                // 如果是合并到现有区域（或刚转化的孤立区块区域），同步能量数据和保护设置
                if (!region.getChunks().isEmpty()) {
                    // 找到该区域的一个现有区块作为参考
                    for (ChunkClaim existing : region.getChunks()) {
                        if (existing.getId() != claim.getId()) {
                            // 同步能量数据
                            claim.setEnergyTime(existing.getEnergyTime());
                            claim.setEconomyBalance(existing.getEconomyBalance());
                            // 同步保护设置
                            copyClaimSettings(existing, claim);
                            break;
                        }
                    }
                }
                
                // 保存所有更新（包括regionId）到数据库
                return chunkClaimDAO.updateChunkClaimAsync(claim).thenApply(v -> {
                    // 更新缓存（不清除，直接添加）
                    String key = claim.getWorld() + ":" + claim.getChunkX() + ":" + claim.getChunkZ();
                    chunkCache.put(key, claim);
                    // 确保区域也在缓存中
                    if (!regionCache.containsKey(region.getId())) {
                        regionCache.put(region.getId(), region);
                    }
                    // 清除玩家领地列表缓存，让下次查询时重新加载
                    playerClaimsCache.remove(claim.getOwner());
                    return claim;
                });
            });
        });
    }

    /**
     * 获取指定位置周边的孤立区块（无区域ID的区块）
     */
    private List<ChunkClaim> getAdjacentOrphanChunks(String world, int x, int z) {
        List<ChunkClaim> adjacent = new ArrayList<>();
        int[][] offsets = {{1,0}, {-1,0}, {0,1}, {0,-1}};
        
        for (int[] offset : offsets) {
            String key = world + ":" + (x + offset[0]) + ":" + (z + offset[1]);
            ChunkClaim claim = chunkCache.get(key);
            if (claim != null && claim.getRegionId() == 0) {
                adjacent.add(claim);
            }
        }
        return adjacent;
    }

    /**
     * 将区块认领并合并到指定区域
     */
    public CompletableFuture<ChunkClaim> claimChunkIntoRegion(Player player, Chunk chunk, int regionId) {
        // 如果缓存为空，先重新加载
        if (regionCache.isEmpty()) {
            try {
                reloadCache().get();
            } catch (Exception e) {
                plugin.getLogger().warning("重新加载缓存失败: " + e.getMessage());
            }
        }
        
        ClaimRegion region = regionCache.get(regionId);
        if (region == null) {
            CompletableFuture<ChunkClaim> future = new CompletableFuture<>();
            future.completeExceptionally(new IllegalArgumentException("区域不存在"));
            return future;
        }
        
        if (!region.getOwner().equals(player.getUniqueId())) {
            CompletableFuture<ChunkClaim> future = new CompletableFuture<>();
            future.completeExceptionally(new IllegalStateException("你不是该区域的所有者"));
            return future;
        }

        long initialTime = plugin.getConfig().getLong("power-cell.initial-protection-time", 600);

        return chunkClaimDAO.createChunkClaimAsync(player.getUniqueId(), chunk).thenCompose(claim -> {
            claim.setInitialTime(initialTime);
            claim.setRegionId(region.getId());
            region.addChunk(claim);
            
            // 同步能量数据和保护设置
            if (!region.getChunks().isEmpty()) {
                for (ChunkClaim existing : region.getChunks()) {
                    if (existing.getId() != claim.getId()) {
                        claim.setEnergyTime(existing.getEnergyTime());
                        claim.setEconomyBalance(existing.getEconomyBalance());
                        // 同步保护设置
                        copyClaimSettings(existing, claim);
                        break;
                    }
                }
            }
            
            // 保存到数据库
            return chunkClaimDAO.updateChunkClaimAsync(claim).thenApply(v -> {
                // 更新缓存（不清除，直接添加）
                String key = claim.getWorld() + ":" + claim.getChunkX() + ":" + claim.getChunkZ();
                chunkCache.put(key, claim);
                // 清除玩家领地列表缓存
                playerClaimsCache.remove(claim.getOwner());
                return claim;
            });
        });
    }

    /**
     * 获取指定位置周边的相邻领地区域
     * 如果缓存为空，会从数据库重新加载
     */
    public List<ClaimRegion> getAdjacentRegions(String world, int x, int z) {
        // 如果缓存为空，先尝试从数据库加载
        if (regionCache.isEmpty()) {
            try {
                reloadCache().get(); // 同步等待加载完成
            } catch (Exception e) {
                plugin.getLogger().warning("重新加载缓存失败: " + e.getMessage());
            }
        }
        
        Set<ClaimRegion> adjacent = new HashSet<>();
        for (ClaimRegion region : regionCache.values()) {
            if (!region.getWorld().equals(world)) {
                continue;
            }
            for (ChunkClaim existingChunk : region.getChunks()) {
                int dx = Math.abs(x - existingChunk.getChunkX());
                int dz = Math.abs(z - existingChunk.getChunkZ());
                if ((dx == 1 && dz == 0) || (dx == 0 && dz == 1)) {
                    adjacent.add(region);
                    break; // 此区域已判定为相邻，跳过其余区块
                }
            }
        }
        return new ArrayList<>(adjacent);
    }
    
    /**
     * 获取区域
     * 如果缓存为空，会从数据库重新加载
     */
    public ClaimRegion getRegion(int id) {
        // 如果缓存为空，先尝试从数据库加载
        if (regionCache.isEmpty()) {
            try {
                reloadCache().get(); // 同步等待加载完成
            } catch (Exception e) {
                plugin.getLogger().warning("重新加载缓存失败: " + e.getMessage());
            }
        }
        
        ClaimRegion region = regionCache.get(id);
        
        // 确保区域的区块集合已填充
        if (region != null && region.getChunks().isEmpty()) {
            // 从chunkCache中找到属于此区域的所有区块并添加
            for (ChunkClaim claim : chunkCache.values()) {
                if (claim.getRegionId() == id) {
                    region.addChunk(claim);
                }
            }
            // 也检查playerClaimsCache
            for (List<ChunkClaim> claims : playerClaimsCache.values()) {
                for (ChunkClaim claim : claims) {
                    if (claim.getRegionId() == id) {
                        region.addChunk(claim);
                    }
                }
            }
        }
        
        return region;
    }

    /**
     * 完成能量电池创建
     */
    public CompletableFuture<ChunkClaim> createPowerCell(Player player, Location powerCellLocation, long energyFromRecipe) {
        return findChunkClaimAtAsync(powerCellLocation).thenCompose(claimOpt -> {
            if (claimOpt.isEmpty()) {
                throw new IllegalStateException("此位置不在任何领地内");
            }
            
            ChunkClaim claim = claimOpt.get();
            
            if (!claim.getOwner().equals(player.getUniqueId())) {
                throw new IllegalStateException("你不是此领地的所有者");
            }
            
            // 注意：配方物品的能源现在会转换为物品放入箱子，而不是直接加到energyTime
            // energyFromRecipe参数现在不再使用，保留是为了兼容性
            
            return chunkClaimDAO.updateChunkClaimAsync(claim).thenApply(v -> claim);
        });
    }

    /**
     * 查找位置所在的区块领地
     */
    public CompletableFuture<Optional<ChunkClaim>> findChunkClaimAtAsync(Location location) {
        Chunk chunk = location.getChunk();
        String cacheKey = location.getWorld().getName() + ":" + chunk.getX() + ":" + chunk.getZ();
        
        ChunkClaim cached = chunkCache.get(cacheKey);
        if (cached != null) {
            return CompletableFuture.completedFuture(Optional.of(cached));
        }
        
        // 如果数据已全部加载且缓存中没有，说明确实没有领地
        if (dataLoaded) {
            return CompletableFuture.completedFuture(Optional.empty());
        }
        
        return chunkClaimDAO.findChunkClaimAsync(
            location.getWorld().getName(), 
            chunk.getX(), 
            chunk.getZ()
        ).thenApply(claim -> {
            if (claim.isPresent()) {
                chunkCache.put(cacheKey, claim.get());
            }
            return claim;
        });
    }

    /**
     * 同步查找区块领地
     */
    public Optional<ChunkClaim> findChunkClaimAt(Location location) {
        try {
            return findChunkClaimAtAsync(location).get();
        } catch (Exception e) {
            plugin.getLogger().warning("查找区块领地时出错: " + e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * 获取玩家的所有区块领地
     */
    public CompletableFuture<List<ChunkClaim>> getPlayerChunkClaimsAsync(UUID playerId) {
        List<ChunkClaim> cached = playerClaimsCache.get(playerId);
        if (cached != null) {
            return CompletableFuture.completedFuture(cached);
        }
        
        return chunkClaimDAO.getPlayerChunkClaimsAsync(playerId).thenApply(claims -> {
            playerClaimsCache.put(playerId, claims);
            for (ChunkClaim claim : claims) {
                String cacheKey = claim.getWorld() + ":" + claim.getChunkX() + ":" + claim.getChunkZ();
                chunkCache.put(cacheKey, claim);
            }
            return claims;
        });
    }

    /**
     * 获取玩家的所有领地区域
     */
    public CompletableFuture<List<ClaimRegion>> getPlayerRegionsAsync(UUID playerId) {
        // 先从缓存中查找
        List<ClaimRegion> cachedRegions = new ArrayList<>();
        for (ClaimRegion region : regionCache.values()) {
            if (region.getOwner().equals(playerId)) {
                cachedRegions.add(region);
            }
        }
        
        if (!cachedRegions.isEmpty()) {
            return CompletableFuture.completedFuture(cachedRegions);
        }
        
        // 如果缓存为空，从数据库加载
        return chunkClaimDAO.getPlayerRegionsAsync(playerId).thenApply(regions -> {
            for (ClaimRegion region : regions) {
                regionCache.put(region.getId(), region);
            }
            return regions;
        });
    }

    /**
     * 取消认领区块
     */
    public CompletableFuture<Void> unclaimChunk(Player player, Chunk chunk) {
        return findChunkClaimAtAsync(chunk.getBlock(0, 64, 0).getLocation())
            .thenCompose(claimOpt -> {
                if (claimOpt.isEmpty()) {
                    throw new IllegalStateException("该区块未被认领");
                }
                
                ChunkClaim claim = claimOpt.get();
                if (!claim.getOwner().equals(player.getUniqueId())) {
                    throw new IllegalStateException("你不是该区块的所有者");
                }
                
                // 检查是否包含能量电池
                return chunkClaimDAO.getPowerCellLocationAsync(claim.getId()).thenCompose(powerCellLoc -> {
                    if (powerCellLoc != null) {
                        CompletableFuture<Void> failed = new CompletableFuture<>();
                        failed.completeExceptionally(new IllegalStateException("无法取消认领：该区块包含能量电池，请先拆除能量电池！"));
                        return failed;
                    }
                
                    // 从区域中移除
                    if (claim.getRegionId() > 0) {
                        ClaimRegion region = regionCache.get(claim.getRegionId());
                        if (region != null) {
                            region.removeChunk(claim);
                            // 如果区域为空，可以考虑删除区域（可选，防止残留空区域）
                            if (region.getChunks().isEmpty()) {
                                regionCache.remove(region.getId());
                                chunkClaimDAO.deleteRegionAsync(region.getId());
                            }
                        }
                    }
                    
                    return chunkClaimDAO.deleteChunkClaimAsync(claim.getId()).thenRun(() -> {
                        // 从缓存移除
                        String cacheKey = claim.getWorld() + ":" + claim.getChunkX() + ":" + claim.getChunkZ();
                        chunkCache.remove(cacheKey);
                        
                        List<ChunkClaim> playerClaims = playerClaimsCache.get(player.getUniqueId());
                        if (playerClaims != null) {
                            playerClaims.removeIf(c -> c.getId() == claim.getId());
                        }
                        
                    });
                });
            });
    }

    /**
     * 检查玩家是否是成员 (仅检查缓存，同步方法)
     */
    public boolean isMemberCached(int claimId, UUID playerId) {
        Map<UUID, org.kari.kariClaims.models.ClaimMember> members = claimMembersCache.get(claimId);
        return members != null && members.containsKey(playerId);
    }
    
    /**
     * 检查玩家是否被封禁
     */
    public CompletableFuture<Boolean> isBanned(int claimId, UUID playerId) {
        return chunkClaimDAO.isBannedAsync(claimId, playerId).thenApply(isBanned -> {
            // 更新缓存
            bannedPlayersCache.computeIfAbsent(claimId, k -> new HashSet<>());
            if (isBanned) {
                bannedPlayersCache.get(claimId).add(playerId);
            } else {
                bannedPlayersCache.get(claimId).remove(playerId);
            }
            return isBanned;
        });
    }

    /**
     * 封禁玩家
     */
    public CompletableFuture<Void> banPlayer(int claimId, UUID playerId) {
        return chunkClaimDAO.banPlayerAsync(claimId, playerId).thenRun(() -> {
            bannedPlayersCache.computeIfAbsent(claimId, k -> new HashSet<>()).add(playerId);
        });
    }

    /**
     * 解封玩家
     */
    public CompletableFuture<Void> unbanPlayer(int claimId, UUID playerId) {
        return chunkClaimDAO.unbanPlayerAsync(claimId, playerId).thenRun(() -> {
            Set<UUID> banned = bannedPlayersCache.get(claimId);
            if (banned != null) {
                banned.remove(playerId);
            }
        });
    }

    /**
     * 检查玩家是否有权限
     */
    public boolean hasPermissionSync(Player player, ChunkClaim claim, String permission) {
        if (claim.getOwner().equals(player.getUniqueId())) {
            return true;
        }
        
        // 检查缓存中的成员
        org.kari.kariClaims.models.ClaimMember member = null;
        Map<UUID, org.kari.kariClaims.models.ClaimMember> members = claimMembersCache.get(claim.getId());
        if (members != null) {
            member = members.get(player.getUniqueId());
        }
        
        // 确定所需的权限标志
        int permFlag = 0;
        switch (permission) {
            case "break": permFlag = ChunkClaim.PERM_BLOCK_BREAK; break;
            case "build": permFlag = ChunkClaim.PERM_BLOCK_PLACE; break;
            case "interact": permFlag = ChunkClaim.PERM_INTERACT; break;
            case "trade": permFlag = ChunkClaim.PERM_TRADE; break;
            case "door": permFlag = ChunkClaim.PERM_USE_DOORS; break;
            case "attack": permFlag = ChunkClaim.PERM_ATTACK_MOBS; break;
            case "redstone": permFlag = ChunkClaim.PERM_USE_REDSTONE; break;
            case "visit": return true; // 访问权限目前总是允许，除非被ban（ban检查在别处）
            default: return false;
        }
        
        if (member != null) {
            org.kari.kariClaims.models.ClaimMember.MemberRole role = member.getRole();
            if (role == org.kari.kariClaims.models.ClaimMember.MemberRole.OWNER || 
                role == org.kari.kariClaims.models.ClaimMember.MemberRole.TRUSTED) {
                return true; // 所有者和信任成员拥有所有权限
            }
            
            if (role == org.kari.kariClaims.models.ClaimMember.MemberRole.MEMBER) {
                return claim.hasMemberPermission(permFlag);
            }
            
            if (role == org.kari.kariClaims.models.ClaimMember.MemberRole.VISITOR) {
                return claim.hasVisitorPermission(permFlag);
            }
        }
        
        // 非成员，检查访客权限
        return claim.hasVisitorPermission(permFlag);
    }
    
    /**
     * 添加成员
     */
    public CompletableFuture<Void> addMember(int claimId, UUID playerId, org.kari.kariClaims.models.ClaimMember.MemberRole role) {
        return chunkClaimDAO.addChunkClaimMemberAsync(claimId, playerId, role.getId()).thenRun(() -> {
            org.kari.kariClaims.models.ClaimMember member = new org.kari.kariClaims.models.ClaimMember(
                claimId, playerId, role, System.currentTimeMillis()
            );
            claimMembersCache.computeIfAbsent(claimId, k -> new ConcurrentHashMap<>()).put(playerId, member);
        });
    }
    
    /**
     * 移除成员
     */
    public CompletableFuture<Void> removeMember(int claimId, UUID playerId) {
        return chunkClaimDAO.removeChunkClaimMemberAsync(claimId, playerId).thenRun(() -> {
            Map<UUID, org.kari.kariClaims.models.ClaimMember> members = claimMembersCache.get(claimId);
            if (members != null) {
                members.remove(playerId);
            }
        });
    }
    
    /**
     * 获取领地的所有成员
     */
    public CompletableFuture<List<org.kari.kariClaims.models.ClaimMember>> getMembers(int claimId) {
        // 优先从缓存获取
        Map<UUID, org.kari.kariClaims.models.ClaimMember> cachedMembers = claimMembersCache.get(claimId);
        if (cachedMembers != null && !cachedMembers.isEmpty()) {
            return CompletableFuture.completedFuture(new ArrayList<>(cachedMembers.values()));
        }
        
        // 如果缓存为空（可能是未加载或确实没成员），回退到数据库
        return chunkClaimDAO.getChunkClaimMembersAsync(claimId).thenApply(members -> {
             // 更新缓存
             if (!members.isEmpty()) {
                 Map<UUID, org.kari.kariClaims.models.ClaimMember> map = claimMembersCache.computeIfAbsent(claimId, k -> new ConcurrentHashMap<>());
                 for (org.kari.kariClaims.models.ClaimMember m : members) {
                     map.put(m.getPlayerId(), m);
                 }
             }
             return members;
        });
    }
    
    /**
     * 检查玩家是否是成员
     */
    public CompletableFuture<Boolean> isMember(int claimId, UUID playerId) {
        Map<UUID, org.kari.kariClaims.models.ClaimMember> members = claimMembersCache.get(claimId);
        if (members != null && members.containsKey(playerId)) {
            return CompletableFuture.completedFuture(true);
        }
        
        return chunkClaimDAO.getChunkClaimMemberAsync(claimId, playerId)
            .thenApply(Optional::isPresent);
    }
    
    /**
     * 获取封禁玩家列表
     */
    public CompletableFuture<Set<UUID>> getBannedPlayersAsync(int claimId) {
        return chunkClaimDAO.getBannedPlayersAsync(claimId);
    }

    /**
     * 转移领地所有权（只转移单个区块）
     */
    public CompletableFuture<Void> transferOwnership(int claimId, UUID newOwner) {
        return chunkClaimDAO.updateChunkClaimOwnerAsync(claimId, newOwner).thenCompose(v -> {
            // 由于ChunkClaim对象是不可变的，无法直接修改owner
            // 转移操作不频繁，直接重新加载缓存是安全的
            return reloadCache();
        });
    }
    
    /**
     * 转移区域所有权（转移区域内的所有区块）
     */
    public CompletableFuture<Void> transferRegionOwnership(int claimId, UUID newOwner) {
        return chunkClaimDAO.findChunkClaimByIdAsync(claimId).thenCompose(claimOpt -> {
            if (claimOpt.isEmpty()) {
                CompletableFuture<Void> failed = new CompletableFuture<>();
                failed.completeExceptionally(new IllegalStateException("领地不存在"));
                return failed;
            }
            
            ChunkClaim claim = claimOpt.get();
            int regionId = claim.getRegionId();
            
            // 如果不属于区域，只转移单个区块
            if (regionId == 0) {
                return transferOwnership(claimId, newOwner);
            }
            
            // 获取区域信息
            ClaimRegion region = getRegion(regionId);
            if (region == null) {
                return transferOwnership(claimId, newOwner);
            }
            
            // 转移区域内的所有区块
            List<CompletableFuture<Void>> futures = new ArrayList<>();
            for (ChunkClaim regionChunk : region.getChunks()) {
                futures.add(chunkClaimDAO.updateChunkClaimOwnerAsync(regionChunk.getId(), newOwner));
            }
            
            // 同时更新区域的所有者
            futures.add(chunkClaimDAO.updateRegionOwnerAsync(regionId, newOwner));
            
            return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                .thenCompose(v -> reloadCache());
        });
    }

    /**
     * 复制领地设置从源到目标
     */
    private void copyClaimSettings(ChunkClaim source, ChunkClaim target) {
        target.setPvpEnabled(source.isPvpEnabled());
        target.setMobSpawning(source.isMobSpawning());
        target.setFireSpread(source.isFireSpread());
        target.setExplosion(source.isExplosion());
        target.setLeafDecay(source.isLeafDecay());
        target.setEntityDrop(source.isEntityDrop());
        target.setWaterFlow(source.isWaterFlow());
        target.setMobGriefing(source.isMobGriefing());
        target.setTnt(source.isTnt());
        target.setLocked(source.isLocked());
        target.setExternalFluidInflow(source.isExternalFluidInflow());
        target.setPowerCellHopperInteraction(source.isPowerCellHopperInteraction());
        target.setVisitorPermissions(source.getVisitorPermissions());
        target.setMemberPermissions(source.getMemberPermissions());
    }
    
    /**
     * 使缓存失效（不自动重新加载，避免与正在进行的DB操作产生竞态条件）
     */
    public void invalidateCache() {
        chunkCache.clear();
        playerClaimsCache.clear();
        powerCellLocationCache.clear();
        regionCache.clear(); // 也清除区域缓存
        claimMembersCache.clear();
        dataLoaded = false; // 标记数据未加载
        // 不自动重新加载 - 让后续操作按需加载
    }
    
    /**
     * 更新缓存中的单个领地数据
     */
    public void updateClaimInCache(ChunkClaim claim) {
        String key = claim.getWorld() + ":" + claim.getChunkX() + ":" + claim.getChunkZ();
        chunkCache.put(key, claim);
        
        // 更新玩家领地列表缓存
        List<ChunkClaim> ownerClaims = playerClaimsCache.get(claim.getOwner());
        if (ownerClaims != null) {
            for (int i = 0; i < ownerClaims.size(); i++) {
                if (ownerClaims.get(i).getId() == claim.getId()) {
                    ownerClaims.set(i, claim);
                    break;
                }
            }
        }
        
        // 更新区域缓存
        if (claim.getRegionId() > 0) {
            ClaimRegion region = regionCache.get(claim.getRegionId());
            if (region != null) {
                // 尝试更新区域内的区块引用
                // 注意：这取决于 Set 的实现和对象的 equals/hashCode
                // 这里简单的做法是移除旧的（如果有机制识别）并添加新的
                // 但由于 ChunkClaim 没有重写 equals，我们只能通过遍历来替换
                // 不过，通常区域操作不频繁，这里暂不深入处理区域内的对象引用更新，
                // 除非涉及到区域级的操作。为保险起见，我们可以刷新区域
                // 但现在先只更新主缓存
            }
        }
    }

    /**
     * 强制重新加载所有缓存数据
     */
    public CompletableFuture<Void> reloadCache() {
        // 注意：不再设置 dataLoaded = false，因为我们使用临时缓存进行无缝更新
        // 旧缓存仍然可用，直到新数据准备好并进行原子替换
        
        // 创建临时缓存对象，避免清空主缓存导致的服务中断
        final Map<String, ChunkClaim> tempChunkCache = new ConcurrentHashMap<>();
        final Map<UUID, List<ChunkClaim>> tempPlayerClaimsCache = new ConcurrentHashMap<>();
        final Map<Integer, ClaimRegion> tempRegionCache = new ConcurrentHashMap<>();
        final Map<Integer, Map<UUID, org.kari.kariClaims.models.ClaimMember>> tempClaimMembersCache = new ConcurrentHashMap<>();
        final Map<String, Integer> tempPowerCellLocationCache = new ConcurrentHashMap<>();
        
        return chunkClaimDAO.getAllRegionsAsync().thenCompose(regions -> {
            for (ClaimRegion region : regions) {
                tempRegionCache.put(region.getId(), region);
            }
            return chunkClaimDAO.getAllChunkClaimsAsync().thenCompose(claims -> {
                for (ChunkClaim claim : claims) {
                    String key = claim.getWorld() + ":" + claim.getChunkX() + ":" + claim.getChunkZ();
                    tempChunkCache.put(key, claim);
                    
                    // 更新玩家领地缓存
                    tempPlayerClaimsCache.computeIfAbsent(claim.getOwner(), k -> new ArrayList<>()).add(claim);
                    
                    if (claim.getRegionId() > 0) {
                        ClaimRegion region = tempRegionCache.get(claim.getRegionId());
                        if (region != null) {
                            region.addChunk(claim);
                        }
                    }
                }
                
                // 加载所有成员
                return chunkClaimDAO.getAllClaimMembersAsync().thenCompose(members -> {
                    for (org.kari.kariClaims.models.ClaimMember member : members) {
                        tempClaimMembersCache.computeIfAbsent(member.getClaimId(), k -> new ConcurrentHashMap<>())
                            .put(member.getPlayerId(), member);
                    }
                    
                    // 重新加载能量箱位置缓存
                    return chunkClaimDAO.getAllPowerCellLocationsAsync().thenAccept(locations -> {
                        for (Map.Entry<Integer, Location> entry : locations.entrySet()) {
                            Location loc = entry.getValue();
                            if (loc != null && loc.getWorld() != null) {
                                String key = loc.getWorld().getName() + ":" + loc.getBlockX() + ":" + loc.getBlockY() + ":" + loc.getBlockZ();
                                tempPowerCellLocationCache.put(key, entry.getKey());
                            }
                        }
                        
                        // 在主线程原子性替换缓存内容（或者快速clear+putAll）
                        plugin.getServer().getScheduler().runTask(plugin, () -> {
                            chunkCache.clear();
                            chunkCache.putAll(tempChunkCache);
                            
                            playerClaimsCache.clear();
                            playerClaimsCache.putAll(tempPlayerClaimsCache);
                            
                            regionCache.clear();
                            regionCache.putAll(tempRegionCache);
                            
                            claimMembersCache.clear();
                            claimMembersCache.putAll(tempClaimMembersCache);
                            
                            powerCellLocationCache.clear();
                            powerCellLocationCache.putAll(tempPowerCellLocationCache);
                            
                            dataLoaded = true;
                            plugin.getLogger().info("已重新加载所有领地数据到缓存");
                        });
                    });
                });
            });
        });
    }

    /**
     * 注册能源箱位置到缓存
     */
    public void registerPowerCellLocation(int claimId, Location location) {
        String key = location.getWorld().getName() + ":" + location.getBlockX() + ":" + location.getBlockY() + ":" + location.getBlockZ();
        powerCellLocationCache.put(key, claimId);
    }

    /**
     * 从缓存移除能源箱位置
     */
    public void unregisterPowerCellLocation(Location location) {
        String key = location.getWorld().getName() + ":" + location.getBlockX() + ":" + location.getBlockY() + ":" + location.getBlockZ();
        powerCellLocationCache.remove(key);
    }

    /**
     * 同步检查位置是否是能源箱（使用缓存）
     */
    public Integer getPowerCellClaimIdAt(Location location) {
        String key = location.getWorld().getName() + ":" + location.getBlockX() + ":" + location.getBlockY() + ":" + location.getBlockZ();
        return powerCellLocationCache.get(key);
    }

    /**
     * 检查玩家是否有临时保护
     */
    public boolean hasTemporaryProtection(UUID playerId) {
        TemporaryProtection protection = temporaryProtections.get(playerId);
        return protection != null && System.currentTimeMillis() <= protection.getExpireTime();
    }

    /**
     * 获取玩家的临时保护
     */
    public TemporaryProtection getTemporaryProtection(UUID playerId) {
        TemporaryProtection protection = temporaryProtections.get(playerId);
        if (protection != null && System.currentTimeMillis() > protection.getExpireTime()) {
            temporaryProtections.remove(playerId);
            return null;
        }
        return protection;
    }

    /**
     * 在缓存中同步扣除能源与经济余额，保持显示与数据库一致
     */
    public void applyEnergyDrainToCache(long seconds, double pricePerSecond) {
        if (seconds <= 0) {
            return;
        }

        Set<Integer> processed = new HashSet<>();

        for (ChunkClaim claim : chunkCache.values()) {
            drainClaimEnergy(claim, seconds, pricePerSecond, processed);
        }

        for (List<ChunkClaim> claims : playerClaimsCache.values()) {
            for (ChunkClaim claim : new ArrayList<>(claims)) {
                drainClaimEnergy(claim, seconds, pricePerSecond, processed);
            }
        }
    }

    private void drainClaimEnergy(ChunkClaim claim, long seconds, double pricePerSecond, Set<Integer> processed) {
        if (claim == null || !processed.add(claim.getId())) {
            return;
        }

        long energy = claim.getEnergyTime(); // 物品时间
        double balance = claim.getEconomyBalance(); // 金钱余额
        long initialTime = claim.getInitialTime(); // 初始时间

        // 如果所有时间都为0，跳过
        if (energy <= 0 && balance <= 0 && initialTime <= 0) {
            return;
        }

        long remaining = seconds;
        
        // 优先消耗物品时间
        if (remaining > 0 && energy > 0) {
            long consumedFromItems = Math.min(energy, remaining);
            energy -= consumedFromItems;
            remaining -= consumedFromItems;
        }
        
        // 然后消耗金钱时间
        if (remaining > 0 && balance > 0) {
            double cost = remaining * pricePerSecond;
            double consumedFromBalance = Math.min(balance, cost);
            balance = Math.max(0, balance - consumedFromBalance);
            remaining -= (long)(consumedFromBalance / pricePerSecond);
        }
        
        // 最后消耗初始时间
        if (remaining > 0 && initialTime > 0) {
            long consumedFromInitial = Math.min(initialTime, remaining);
            initialTime -= consumedFromInitial;
            remaining -= consumedFromInitial;
        }

        claim.setEnergyTime(energy);
        claim.setEconomyBalance(balance);
        claim.setInitialTime(initialTime);
    }

    /**
     * 从所有能量电池箱子中消耗物品并扣除时间
     */
    public void consumeItemsFromPowerCells(long seconds, double pricePerSecond, KariClaims plugin) {
        if (seconds <= 0) {
            return;
        }

        // 获取所有有能量电池的领地
        chunkClaimDAO.getAllPowerCellLocationsAsync()
            .thenAccept(locations -> {
                for (Map.Entry<Integer, Location> entry : locations.entrySet()) {
                    int claimId = entry.getKey();
                    Location powerCellLoc = entry.getValue();
                    
                    // 从数据库加载最新的claim数据
                    chunkClaimDAO.findChunkClaimByIdAsync(claimId)
                        .thenAccept(claimOpt -> {
                            if (claimOpt.isEmpty()) {
                                return;
                            }
                            
                            ChunkClaim claim = claimOpt.get();
                            
                            // 在主线程上计算和消耗箱子中的物品
                            plugin.getServer().getScheduler().runTask(plugin, () -> {
                                org.bukkit.block.Block chestBlock = powerCellLoc.getBlock();
                                if (chestBlock.getType() != org.bukkit.Material.CHEST) {
                                    // 删除能量电池记录并改用初始/经济时间消耗
                                    chunkClaimDAO.deletePowerCellAsync(claimId);
                                    unregisterPowerCellLocation(powerCellLoc);
                                    return;
                                }
                                
                                // 重新获取箱子状态，确保是最新的
                                org.bukkit.block.Chest chest = (org.bukkit.block.Chest) chestBlock.getState();
                                org.bukkit.inventory.Inventory chestInv = chest.getInventory();
                                
                                // 计算当前箱子中的物品能源时间（仅用于调试日志，如果需要的话）
                                // long currentItemEnergy = calculateItemEnergy(chestInv, plugin);
                                
                                // 消耗逻辑优化：将 energyTime 作为缓冲区
                                // 1. 从缓冲区扣除本次消耗
                                long buffer = claim.getEnergyTime();
                                buffer -= seconds;
                                
                                // 2. 如果缓冲区不足，从箱子中提取能量补充
                                // 设定一个阈值（比如60秒），保持缓冲区至少有一定余量，避免频繁操作箱子
                                long minBuffer = 60;
                                if (buffer < minBuffer) {
                                    // 需要补充的量 = 缺口 + 最小缓冲
                                    long needed = minBuffer - buffer;
                                    
                                    // 从箱子消耗物品，获取实际补充的能量
                                    long added = consumeEnergyFromChest(chestInv, needed, plugin);
                                    buffer += added;
                                }
                                
                                // 3. 如果缓冲区还是负数（说明箱子空了或不够），尝试扣除金钱和初始时间
                                long remainingDeficit = buffer < 0 ? -buffer : 0;
                                
                                if (remainingDeficit > 0) {
                                    // 缓冲区归零（因为我们要用其他方式支付这部分赤字）
                                    buffer = 0;
                                    
                                    // 消耗金钱
                                    if (claim.getEconomyBalance() > 0) {
                                        double cost = remainingDeficit * pricePerSecond;
                                        double consumedFromBalance = Math.min(claim.getEconomyBalance(), cost);
                                        claim.setEconomyBalance(Math.max(0, claim.getEconomyBalance() - consumedFromBalance));
                                        remainingDeficit -= (long)(consumedFromBalance / pricePerSecond);
                                    }
                                    
                                    // 消耗初始时间
                                    if (remainingDeficit > 0 && claim.getInitialTime() > 0) {
                                        long consumedFromInitial = Math.min(claim.getInitialTime(), remainingDeficit);
                                        claim.setInitialTime(claim.getInitialTime() - consumedFromInitial);
                                        remainingDeficit -= consumedFromInitial;
                                    }
                                    
                                    // 如果还有赤字，说明真的耗尽了
                                    if (remainingDeficit > 0) {
                                        // 此时 buffer 为 0，且其他资源也耗尽
                                        buffer = 0; 
                                        // 让解散逻辑在后面处理
                                        // 注意：这里我们需要标记一下，因为 buffer=0 本身不代表耗尽（可能是刚好的）
                                        // 但结合下面的检查逻辑，如果没有剩余物品、没有钱、没有初始时间，且 buffer=0，那就是耗尽
                                    }
                                }
                                
                                // 更新 energyTime 为新的缓冲区值
                                claim.setEnergyTime(buffer);
                                
                                // 更新数据库
                                if (claim.getRegionId() > 0) {
                                    ClaimRegion region = regionCache.get(claim.getRegionId());
                                    if (region != null) {
                                        // 同步能量到区域内所有区块
                                        List<CompletableFuture<Void>> updates = new ArrayList<>();
                                        for (ChunkClaim regionChunk : region.getChunks()) {
                                            // 跳过当前已经处理的claim（虽然重新设置也没关系）
                                            if (regionChunk.getId() != claim.getId()) {
                                                regionChunk.setEnergyTime(claim.getEnergyTime());
                                                regionChunk.setEconomyBalance(claim.getEconomyBalance());
                                                regionChunk.setInitialTime(claim.getInitialTime());
                                            }
                                            updates.add(chunkClaimDAO.updateChunkClaimAsync(regionChunk));
                                        }
                                        // 等待所有更新完成
                                        CompletableFuture.allOf(updates.toArray(new CompletableFuture[0]));
                                    } else {
                                        chunkClaimDAO.updateChunkClaimAsync(claim);
                                    }
                                } else {
                                    chunkClaimDAO.updateChunkClaimAsync(claim);
                                }
                                
                                // 更新缓存
                                String cacheKey = claim.getWorld() + ":" + claim.getChunkX() + ":" + claim.getChunkZ();
                                chunkCache.put(cacheKey, claim);
                                List<ChunkClaim> ownerClaims = playerClaimsCache.get(claim.getOwner());
                                if (ownerClaims != null) {
                                    for (int i = 0; i < ownerClaims.size(); i++) {
                                        if (ownerClaims.get(i).getId() == claim.getId()) {
                                            ownerClaims.set(i, claim);
                                            break;
                                        }
                                    }
                                }
                                
                                // 检查是否时间耗尽
                                if (claim.getEnergyTime() <= 0 && 
                                    claim.getEconomyBalance() <= 0 && 
                                    claim.getInitialTime() <= 0) {
                                    dissolveClaimAndDestroyPowerCell(claim, powerCellLoc, plugin);
                                }
                            });
                        })
                        .exceptionally(throwable -> {
                            plugin.getLogger().warning("加载claim " + claimId + " 失败: " + throwable.getMessage());
                            return null;
                        });
                }
            })
            .thenRun(() -> {
                // 对没有能量电池记录的领地统一扣除经济/初始时间
                chunkClaimDAO.drainEnergyWithoutPowerCellsAsync(seconds, pricePerSecond)
                    .thenCompose(v -> chunkClaimDAO.getClaimsWithoutPowerCellAsync())
                    .thenAccept(claims -> {
                        for (ChunkClaim claim : claims) {
                            if (claim.getEnergyTime() <= 0 && claim.getEconomyBalance() <= 0 && claim.getInitialTime() <= 0) {
                                dissolveClaimWithoutPowerCell(claim, plugin);
                            }
                        }
                    });
            })
            .exceptionally(throwable -> {
                plugin.getLogger().warning("消耗能量电池物品失败: " + throwable.getMessage());
                return null;
            });
    }
    
    /**
     * 同步保存所有领地数据（用于服务器关闭时）
     */
    public void saveAllDataSync() {
        plugin.getLogger().info("正在同步保存所有领地数据...");
        int count = 0;
        
        // 遍历缓存中的所有区块领地
        // 使用 HashSet 去重，避免重复保存（虽然 update 也是幂等的，但去重更高效）
        Set<Integer> savedClaimIds = new HashSet<>();
        
        for (ChunkClaim claim : chunkCache.values()) {
            if (savedClaimIds.contains(claim.getId())) {
                continue;
            }
            
            try {
                chunkClaimDAO.updateChunkClaimSync(claim);
                savedClaimIds.add(claim.getId());
                count++;
            } catch (Exception e) {
                plugin.getLogger().severe("保存领地 " + claim.getId() + " 失败: " + e.getMessage());
            }
        }
        
        plugin.getLogger().info("已保存 " + count + " 个领地的数据");
    }
    
    /**
     * 解散领地并破坏能源箱
     */
    private void dissolveClaimAndDestroyPowerCell(ChunkClaim claim, Location powerCellLoc, KariClaims plugin) {
        // 播放能量电池自毁动画
        ChunkAnimationUtils.playPowerCellDestroyAnimation(plugin, powerCellLoc);
        
        // 掉落箱子中的所有物品
        org.bukkit.block.Block chestBlock = powerCellLoc.getBlock();
        if (chestBlock.getType() == org.bukkit.Material.CHEST) {
            org.bukkit.block.Chest chest = (org.bukkit.block.Chest) chestBlock.getState();
            for (org.bukkit.inventory.ItemStack item : chest.getInventory().getContents()) {
                if (item != null && item.getType() != org.bukkit.Material.AIR) {
                    powerCellLoc.getWorld().dropItemNaturally(powerCellLoc, item);
                }
            }
            
            // 破坏箱子
            chestBlock.setType(org.bukkit.Material.AIR);
            powerCellLoc.getWorld().dropItemNaturally(powerCellLoc, new org.bukkit.inventory.ItemStack(org.bukkit.Material.CHEST));
        }
        
        // 返还金钱
        double refundAmount = claim.getEconomyBalance();
        if (refundAmount > 0) {
            org.bukkit.OfflinePlayer owner = plugin.getServer().getOfflinePlayer(claim.getOwner());
            if (plugin.getEconomyManager().isEnabled()) {
                plugin.getEconomyManager().deposit(owner, refundAmount);
            }
        }
        
        // 删除能量电池记录
        plugin.getChunkClaimDAO().deletePowerCellAsync(claim.getId());
        
        // 从缓存移除
        plugin.getChunkClaimManager().unregisterPowerCellLocation(powerCellLoc);
        
        // 如果是区域的一部分，删除区域内所有区块和区域本身
        if (claim.getRegionId() > 0) {
            ClaimRegion region = regionCache.get(claim.getRegionId());
            if (region != null) {
                List<CompletableFuture<Void>> deletions = new ArrayList<>();
                // 删除区域内所有区块
                for (ChunkClaim c : region.getChunks()) {
                    deletions.add(plugin.getChunkClaimDAO().deleteChunkClaimAsync(c.getId()));
                }
                
                CompletableFuture.allOf(deletions.toArray(new CompletableFuture[0]))
                    .thenCompose(v -> plugin.getChunkClaimDAO().deleteRegionAsync(region.getId()))
                    .thenRun(() -> {
                        // 增量清理缓存：区域删除
                        regionCache.remove(region.getId());
                        List<ChunkClaim> ownerClaims = playerClaimsCache.get(claim.getOwner());
                        
                        for (ChunkClaim c : region.getChunks()) {
                            String key = c.getWorld() + ":" + c.getChunkX() + ":" + c.getChunkZ();
                            chunkCache.remove(key);
                            claimMembersCache.remove(c.getId());
                            if (ownerClaims != null) {
                                ownerClaims.removeIf(existing -> existing.getId() == c.getId());
                            }
                        }
                        
                        org.bukkit.entity.Player owner = plugin.getServer().getPlayer(claim.getOwner());
                        if (owner != null && owner.isOnline()) {
                            owner.sendMessage("§c你的领地因能量耗尽已全部解散！");
                            owner.playSound(owner.getLocation(), org.bukkit.Sound.ENTITY_WITHER_DEATH, 0.5f, 1.0f);
                        }
                        
                    });
                return;
            }
        }
        
        // 删除单个领地（如果不是区域的一部分或找不到区域）
        plugin.getChunkClaimDAO().deleteChunkClaimAsync(claim.getId())
            .thenRun(() -> {
                // 增量清理缓存：单个领地删除
                String cacheKey = claim.getWorld() + ":" + claim.getChunkX() + ":" + claim.getChunkZ();
                chunkCache.remove(cacheKey);
                claimMembersCache.remove(claim.getId());
                
                List<ChunkClaim> ownerClaims = playerClaimsCache.get(claim.getOwner());
                if (ownerClaims != null) {
                    ownerClaims.removeIf(existing -> existing.getId() == claim.getId());
                }
                
                org.bukkit.entity.Player owner = plugin.getServer().getPlayer(claim.getOwner());
                if (owner != null && owner.isOnline()) {
                    owner.sendMessage("§c你的领地因能量耗尽已全部解散！");
                    owner.playSound(owner.getLocation(), org.bukkit.Sound.ENTITY_WITHER_DEATH, 0.5f, 1.0f);
                }
                
            });
    }

    /**
     * 解散没有能量电池的领地
     */
    private void dissolveClaimWithoutPowerCell(ChunkClaim claim, KariClaims plugin) {
        plugin.getChunkClaimDAO().deleteChunkClaimAsync(claim.getId())
            .thenRun(() -> {
                // 增量清理缓存：单个领地删除
                String cacheKey = claim.getWorld() + ":" + claim.getChunkX() + ":" + claim.getChunkZ();
                chunkCache.remove(cacheKey);
                claimMembersCache.remove(claim.getId());
                
                List<ChunkClaim> ownerClaims = playerClaimsCache.get(claim.getOwner());
                if (ownerClaims != null) {
                    ownerClaims.removeIf(existing -> existing.getId() == claim.getId());
                }

                org.bukkit.entity.Player owner = plugin.getServer().getPlayer(claim.getOwner());
                if (owner != null && owner.isOnline()) {
                    owner.sendMessage("§c你的领地因能量耗尽已全部解散！");
                    owner.playSound(owner.getLocation(), org.bukkit.Sound.ENTITY_WITHER_DEATH, 0.5f, 1.0f);
                }
            });
    }

    /**
     * 从箱子中消耗指定数量的能源（通过消耗物品）
     * 按照 UltimateClaims 的实现方式：按物品价值从低到高排序，优先消耗低价值物品
     */
    private long consumeEnergyFromChest(org.bukkit.inventory.Inventory chestInv, long energyToConsume, KariClaims plugin) {
        if (energyToConsume <= 0) {
            return 0;
        }
        
        var itemsSection = plugin.getConfig().getConfigurationSection("power-cell.items");
        if (itemsSection == null) {
            return 0;
        }
        
        // 构建物品价值映射表
        Map<org.bukkit.Material, Long> itemEnergyMap = new HashMap<>();
        for (String key : itemsSection.getKeys(false)) {
            String itemType = itemsSection.getString(key + ".item");
            if (itemType != null) {
                try {
                    org.bukkit.Material material = org.bukkit.Material.valueOf(itemType.toUpperCase());
                    long energyValue = itemsSection.getLong(key + ".value", 0);
                    if (energyValue > 0) {
                        itemEnergyMap.put(material, energyValue);
                    }
                } catch (IllegalArgumentException e) {
                    plugin.getLogger().warning("无效的物品类型: " + itemType);
                }
            }
        }
        
        if (itemEnergyMap.isEmpty()) {
            plugin.getLogger().warning("没有配置有效的物品能源值，无法消耗物品");
            return 0;
        }
        
        // 收集所有可消耗的物品，按价值从低到高排序
        List<ItemEnergyEntry> itemsToConsume = new ArrayList<>();
        for (int i = 0; i < chestInv.getSize(); i++) {
            org.bukkit.inventory.ItemStack item = chestInv.getItem(i);
            if (item == null || item.getType() == org.bukkit.Material.AIR) {
                continue;
            }
            
            Long energyValue = itemEnergyMap.get(item.getType());
            if (energyValue != null && energyValue > 0) {
                itemsToConsume.add(new ItemEnergyEntry(i, item, energyValue));
            }
        }
        
        // 按价值从低到高排序（优先消耗低价值物品）
        itemsToConsume.sort(Comparator.comparingLong(ItemEnergyEntry::getEnergyValue));
        
        long remainingEnergy = energyToConsume;
        long totalConsumed = 0;
        
        // 按顺序消耗物品
        for (ItemEnergyEntry entry : itemsToConsume) {
            if (remainingEnergy <= 0) {
                break;
            }
            
            int slot = entry.getSlot();
            org.bukkit.inventory.ItemStack item = entry.getItem();
            long energyValue = entry.getEnergyValue();
            long energyFromStack = energyValue * item.getAmount();
            
            if (energyFromStack <= remainingEnergy) {
                // 消耗整个堆叠
                remainingEnergy -= energyFromStack;
                totalConsumed += energyFromStack;
                chestInv.setItem(slot, null);
            } else {
                // 部分消耗
                long itemsNeeded = (long) Math.ceil((double) remainingEnergy / energyValue);
                long energyConsumed = itemsNeeded * energyValue;
                
                int newAmount = item.getAmount() - (int) itemsNeeded;
                totalConsumed += energyConsumed;
                remainingEnergy = 0; // 已消耗完所需能源
                
                if (newAmount > 0) {
                    item.setAmount(newAmount);
                    chestInv.setItem(slot, item);
                } else {
                    chestInv.setItem(slot, null);
                }
            }
        }
        
        return totalConsumed;
    }
    
    /**
     * 物品能源条目（用于排序和消耗）
     */
    private static class ItemEnergyEntry {
        private final int slot;
        private final org.bukkit.inventory.ItemStack item;
        private final long energyValue;
        
        public ItemEnergyEntry(int slot, org.bukkit.inventory.ItemStack item, long energyValue) {
            this.slot = slot;
            this.item = item;
            this.energyValue = energyValue;
        }
        
        public int getSlot() {
            return slot;
        }
        
        public org.bukkit.inventory.ItemStack getItem() {
            return item;
        }
        
        public long getEnergyValue() {
            return energyValue;
        }
    }

    /**
     * 获取所有能量电池位置
     */
    public java.util.Collection<Location> getAllPowerCellLocations() {
        java.util.List<Location> locations = new ArrayList<>();
        for (String key : powerCellLocationCache.keySet()) {
            String[] parts = key.split(":");
            if (parts.length == 4) {
                org.bukkit.World world = plugin.getServer().getWorld(parts[0]);
                if (world != null) {
                    try {
                        int x = Integer.parseInt(parts[1]);
                        int y = Integer.parseInt(parts[2]);
                        int z = Integer.parseInt(parts[3]);
                        locations.add(new Location(world, x, y, z));
                    } catch (NumberFormatException ignored) {}
                }
            }
        }
        return locations;
    }

    /**
     * 临时保护数据
     */
    public static class TemporaryProtection {
        private final Chunk chunk;
        private final long expireTime;

        public TemporaryProtection(Chunk chunk, long expireTime) {
            this.chunk = chunk;
            this.expireTime = expireTime;
        }

        public Chunk getChunk() {
            return chunk;
        }

        public long getExpireTime() {
            return expireTime;
        }
    }
}

