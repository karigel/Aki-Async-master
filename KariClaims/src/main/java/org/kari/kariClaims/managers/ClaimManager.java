package org.kari.kariClaims.managers;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.kari.kariClaims.KariClaims;
import org.kari.kariClaims.database.ClaimDAO;
import org.kari.kariClaims.models.Claim;
import org.kari.kariClaims.models.ClaimMember;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 领地管理器 - 使用缓存优化性能
 */
public class ClaimManager {
    private final KariClaims plugin;
    private final ClaimDAO claimDAO;
    
    // 缓存系统 - 提高查询性能
    private final Map<String, Claim> locationCache = new ConcurrentHashMap<>();
    private final Map<UUID, List<Claim>> playerClaimsCache = new ConcurrentHashMap<>();
    private final Map<Integer, List<ClaimMember>> membersCache = new ConcurrentHashMap<>();
    private final Map<Integer, Claim> claimsCache = new ConcurrentHashMap<>();
    
    // 缓存过期时间（毫秒）
    private static final long CACHE_EXPIRE_TIME = 300000; // 5分钟
    private final Map<String, Long> cacheTimestamps = new ConcurrentHashMap<>();

    public ClaimManager(KariClaims plugin, ClaimDAO claimDAO) {
        this.plugin = plugin;
        this.claimDAO = claimDAO;
    }

    /**
     * 创建领地
     */
    public CompletableFuture<Claim> createClaim(Player player, Location pos1, Location pos2) {
        if (!pos1.getWorld().equals(pos2.getWorld())) {
            throw new IllegalArgumentException("两个位置必须在同一世界");
        }

        World world = pos1.getWorld();
        int minX = Math.min(pos1.getBlockX(), pos2.getBlockX());
        int maxX = Math.max(pos1.getBlockX(), pos2.getBlockX());
        int minZ = Math.min(pos1.getBlockZ(), pos2.getBlockZ());
        int maxZ = Math.max(pos1.getBlockZ(), pos2.getBlockZ());

        // 检查是否与其他领地重叠 - 检查四个角和中心点
        Claim newClaim = new Claim(0, player.getUniqueId(), world.getName(),
            minX, minZ, maxX, maxZ, System.currentTimeMillis());
        
        // 检查多个点以确保没有重叠
        CompletableFuture<Optional<Claim>> check1 = findClaimAtAsync(world.getName(), minX, minZ);
        CompletableFuture<Optional<Claim>> check2 = findClaimAtAsync(world.getName(), maxX, maxZ);
        CompletableFuture<Optional<Claim>> check3 = findClaimAtAsync(world.getName(), minX, maxZ);
        CompletableFuture<Optional<Claim>> check4 = findClaimAtAsync(world.getName(), maxX, minZ);
        CompletableFuture<Optional<Claim>> check5 = findClaimAtAsync(world.getName(), (minX + maxX) / 2, (minZ + maxZ) / 2);
        
        return CompletableFuture.allOf(check1, check2, check3, check4, check5).thenCompose(v -> {
            try {
                if (check1.get().isPresent() || check2.get().isPresent() || 
                    check3.get().isPresent() || check4.get().isPresent() || check5.get().isPresent()) {
                    throw new IllegalStateException("该位置已被其他领地占用");
                }
                
                return claimDAO.createClaimAsync(newClaim).thenApply(createdClaim -> {
                    // 更新缓存
                    invalidateCache();
                    return createdClaim;
                });
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
    }

    /**
     * 查找位置所在的领地（带缓存）
     */
    public CompletableFuture<Optional<Claim>> findClaimAtAsync(String world, int x, int z) {
        String cacheKey = world + ":" + x + ":" + z;
        
        // 检查缓存
        Claim cached = locationCache.get(cacheKey);
        if (cached != null && isValidCache(cacheKey)) {
            if (cached.contains(null, x, z)) {
                return CompletableFuture.completedFuture(Optional.of(cached));
            }
        }
        
        return claimDAO.findClaimAtAsync(world, x, z).thenApply(claim -> {
            if (claim.isPresent()) {
                locationCache.put(cacheKey, claim.get());
                cacheTimestamps.put(cacheKey, System.currentTimeMillis());
                claimsCache.put(claim.get().getId(), claim.get());
            }
            return claim;
        });
    }

    /**
     * 同步查找位置所在的领地
     */
    public Optional<Claim> findClaimAt(Location location) {
        try {
            return findClaimAtAsync(location.getWorld().getName(), 
                location.getBlockX(), location.getBlockZ()).get();
        } catch (Exception e) {
            plugin.getLogger().warning("查找领地时出错: " + e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * 获取玩家的所有领地（带缓存）
     */
    public CompletableFuture<List<Claim>> getPlayerClaimsAsync(UUID playerId) {
        List<Claim> cached = playerClaimsCache.get(playerId);
        String cacheKey = "player:" + playerId;
        if (cached != null && isValidCache(cacheKey)) {
            return CompletableFuture.completedFuture(cached);
        }
        
        return claimDAO.getPlayerClaimsAsync(playerId).thenApply(claims -> {
            playerClaimsCache.put(playerId, claims);
            cacheTimestamps.put(cacheKey, System.currentTimeMillis());
            for (Claim claim : claims) {
                claimsCache.put(claim.getId(), claim);
            }
            return claims;
        });
    }

    /**
     * 删除领地
     */
    public CompletableFuture<Void> deleteClaim(int claimId) {
        return claimDAO.deleteClaimAsync(claimId).thenRun(() -> {
            invalidateCache();
        });
    }

    /**
     * 更新领地
     */
    public CompletableFuture<Void> updateClaim(Claim claim) {
        return claimDAO.updateClaimAsync(claim).thenRun(() -> {
            claimsCache.put(claim.getId(), claim);
            invalidateLocationCache();
        });
    }

    /**
     * 添加成员
     */
    public CompletableFuture<Void> addMember(ClaimMember member) {
        return claimDAO.addMemberAsync(member).thenRun(() -> {
            membersCache.remove(member.getClaimId());
        });
    }

    /**
     * 获取领地的成员
     */
    public CompletableFuture<List<ClaimMember>> getClaimMembersAsync(int claimId) {
        List<ClaimMember> cached = membersCache.get(claimId);
        if (cached != null) {
            return CompletableFuture.completedFuture(cached);
        }
        
        return claimDAO.getClaimMembersAsync(claimId).thenApply(members -> {
            membersCache.put(claimId, members);
            return members;
        });
    }

    /**
     * 检查玩家是否有权限（同步版本，用于事件监听器）
     */
    public boolean hasPermissionSync(Player player, Claim claim, String permission) {
        // 如果是所有者，拥有所有权限
        if (claim.getOwner().equals(player.getUniqueId())) {
            return true;
        }
        
        // 从缓存获取成员
        List<ClaimMember> members = membersCache.get(claim.getId());
        if (members == null) {
            // 如果缓存中没有，尝试同步获取（可能阻塞）
            try {
                members = getClaimMembersAsync(claim.getId()).get();
            } catch (Exception e) {
                return false;
            }
        }
        
        for (ClaimMember member : members) {
            if (member.getPlayerId().equals(player.getUniqueId())) {
                ClaimMember.MemberRole role = member.getRole();
                switch (permission.toLowerCase()) {
                    case "build":
                    case "break":
                    case "interact":
                        return role == ClaimMember.MemberRole.OWNER || 
                               role == ClaimMember.MemberRole.TRUSTED || 
                               role == ClaimMember.MemberRole.MEMBER;
                    case "pvp":
                        return role == ClaimMember.MemberRole.OWNER || 
                               role == ClaimMember.MemberRole.TRUSTED;
                    default:
                        return role == ClaimMember.MemberRole.OWNER;
                }
            }
        }
        return false;
    }

    /**
     * 检查玩家是否有权限（异步版本）
     */
    public CompletableFuture<Boolean> hasPermission(Player player, Claim claim, String permission) {
        // 如果是所有者，拥有所有权限
        if (claim.getOwner().equals(player.getUniqueId())) {
            return CompletableFuture.completedFuture(true);
        }
        
        // 检查成员权限
        return getClaimMembersAsync(claim.getId()).thenApply(members -> {
            for (ClaimMember member : members) {
                if (member.getPlayerId().equals(player.getUniqueId())) {
                    ClaimMember.MemberRole role = member.getRole();
                    switch (permission.toLowerCase()) {
                        case "build":
                        case "break":
                        case "interact":
                            return role == ClaimMember.MemberRole.OWNER || 
                                   role == ClaimMember.MemberRole.TRUSTED || 
                                   role == ClaimMember.MemberRole.MEMBER;
                        case "pvp":
                            return role == ClaimMember.MemberRole.OWNER || 
                                   role == ClaimMember.MemberRole.TRUSTED;
                        default:
                            return role == ClaimMember.MemberRole.OWNER;
                    }
                }
            }
            return false;
        });
    }

    /**
     * 检查缓存是否有效
     */
    private boolean isValidCache(String cacheKey) {
        Long timestamp = cacheTimestamps.get(cacheKey);
        if (timestamp == null) {
            return false;
        }
        return System.currentTimeMillis() - timestamp < CACHE_EXPIRE_TIME;
    }

    /**
     * 使缓存失效
     */
    public void invalidateCache() {
        locationCache.clear();
        playerClaimsCache.clear();
        membersCache.clear();
        cacheTimestamps.clear();
    }

    /**
     * 使位置缓存失效
     */
    private void invalidateLocationCache() {
        locationCache.clear();
        cacheTimestamps.entrySet().removeIf(entry -> entry.getKey().contains(":"));
    }
}

