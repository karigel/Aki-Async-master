package org.virgil.akiasync.util;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Method;
import java.util.concurrent.ConcurrentHashMap;

public class LandProtectionIntegration {

    private static volatile Boolean residenceEnabled = null;
    private static volatile Boolean dominionEnabled = null;
    private static volatile Boolean worldGuardEnabled = null;
    private static volatile Boolean landsEnabled = null;
    private static volatile Boolean kariClaimsEnabled = null;

    private static volatile Object residenceAPI = null;
    private static volatile Method residenceGetByLocMethod = null;
    private static volatile Method residenceHasFlagMethod = null;

    private static volatile Object dominionAPI = null;
    private static volatile Method dominionGetDominionMethod = null;
    private static volatile Method dominionGetFlagMethod = null;

    private static volatile Object worldGuardPlugin = null;
    private static volatile Object regionContainer = null;
    private static volatile Method createQueryMethod = null;

    private static volatile Object landsIntegration = null;
    private static volatile Method getLandByChunkMethod = null;
    private static volatile Method hasRoleFlagMethod = null;

    private static volatile Object kariClaimsPlugin = null;
    private static volatile Object kariClaimsChunkManager = null;
    private static volatile Method findChunkClaimAtMethod = null;
    private static volatile Method getClaimFromOptionalMethod = null;
    private static volatile Method isTntMethod = null;
    private static volatile Method isExplosionMethod = null;

    private static final ConcurrentHashMap<String, CacheEntry> CACHE = new ConcurrentHashMap<>();
    private static final long CACHE_DURATION_MS = 5000;
    private static final int MAX_CACHE_SIZE = 1000;

    private static class CacheEntry {
        final boolean allowed;
        final long timestamp;

        CacheEntry(boolean allowed) {
            this.allowed = allowed;
            this.timestamp = System.currentTimeMillis();
        }

        boolean isValid() {
            return System.currentTimeMillis() - timestamp < CACHE_DURATION_MS;
        }
    }

    public static boolean canTNTExplode(ServerLevel level, BlockPos pos) {
        try {
            String cacheKey = getCacheKey(level, pos);
            CacheEntry cached = CACHE.get(cacheKey);
            if (cached != null && cached.isValid()) {
                return cached.allowed;
            }

            if (CACHE.size() > MAX_CACHE_SIZE) {
                CACHE.entrySet().removeIf(entry -> !entry.getValue().isValid());
            }

            World bukkitWorld = level.getWorld();
            Location location = new Location(bukkitWorld, pos.getX(), pos.getY(), pos.getZ());

            boolean allowed = true;

            if (isResidenceEnabled()) {
                boolean residenceAllowed = checkResidence(location);
                if (!residenceAllowed) {
                    allowed = false;
                }
            }

            if (allowed && isDominionEnabled()) {
                boolean dominionAllowed = checkDominion(location);
                if (!dominionAllowed) {
                    allowed = false;
                }
            }

            if (allowed && isWorldGuardEnabled()) {
                boolean worldGuardAllowed = checkWorldGuard(location);
                if (!worldGuardAllowed) {
                    allowed = false;
                }
            }

            if (allowed && isLandsEnabled()) {
                boolean landsAllowed = checkLands(location);
                if (!landsAllowed) {
                    allowed = false;
                }
            }

            if (allowed && isKariClaimsEnabled()) {
                boolean kariClaimsAllowed = checkKariClaims(location);
                if (!kariClaimsAllowed) {
                    allowed = false;
                }
            }

            CACHE.put(cacheKey, new CacheEntry(allowed));

            return allowed;
        } catch (Exception e) {
            DebugLogger.error("[LandProtection] Error checking land protection: " + e.getMessage());
            return true;
        }
    }

    private static boolean checkResidence(Location location) {
        try {
            if (residenceAPI == null) {
                Plugin residence = Bukkit.getPluginManager().getPlugin("Residence");
                if (residence == null) {
                    residenceEnabled = false;
                    return true;
                }

                Class<?> residenceClass = Class.forName("com.bekvon.bukkit.residence.Residence");
                Method getAPIMethod = residenceClass.getMethod("getInstance");
                residenceAPI = getAPIMethod.invoke(null);

                Class<?> residenceAPIClass = Class.forName("com.bekvon.bukkit.residence.api.ResidenceApi");
                residenceGetByLocMethod = residenceAPIClass.getMethod("getByLoc", Location.class);

                Class<?> claimedResidenceClass = Class.forName("com.bekvon.bukkit.residence.protection.ClaimedResidence");
                residenceHasFlagMethod = claimedResidenceClass.getMethod("hasFlag", String.class);
            }

            Object claimedResidence = residenceGetByLocMethod.invoke(residenceAPI, location);
            if (claimedResidence == null) {
                return true;
            }

            Boolean hasTNTFlag = (Boolean) residenceHasFlagMethod.invoke(claimedResidence, "tnt");

            return hasTNTFlag != null && hasTNTFlag;

        } catch (ClassNotFoundException e) {
            residenceEnabled = false;
            return true;
        } catch (Exception e) {
            DebugLogger.error("[LandProtection] Error checking Residence: " + e.getMessage());
            return true;
        }
    }

    private static boolean checkDominion(Location location) {
        try {
            if (dominionAPI == null) {
                Plugin dominion = Bukkit.getPluginManager().getPlugin("Dominion");
                if (dominion == null) {
                    dominionEnabled = false;
                    return true;
                }

                Class<?> cacheClass = Class.forName("cn.lunadeer.dominion.Cache");
                Method getInstanceMethod = cacheClass.getMethod("instance");
                dominionAPI = getInstanceMethod.invoke(null);

                dominionGetDominionMethod = cacheClass.getMethod("getDominionByLoc", Location.class);

                Class<?> dominionDTOClass = Class.forName("cn.lunadeer.dominion.dtos.DominionDTO");
                dominionGetFlagMethod = dominionDTOClass.getMethod("getFlagValue",
                    Class.forName("cn.lunadeer.dominion.dtos.Flag$FlagType"));
            }

            Object dominionDTO = dominionGetDominionMethod.invoke(dominionAPI, location);
            if (dominionDTO == null) {
                return true;
            }

            Class<?> flagTypeClass = Class.forName("cn.lunadeer.dominion.dtos.Flag$FlagType");
            Object tntFlag = null;
            for (Object enumConstant : flagTypeClass.getEnumConstants()) {
                if (enumConstant.toString().equals("TNT_EXPLODE")) {
                    tntFlag = enumConstant;
                    break;
                }
            }

            if (tntFlag == null) {
                DebugLogger.error("[LandProtection] TNT_EXPLODE flag not found in Dominion");
                return true;
            }

            Boolean allowed = (Boolean) dominionGetFlagMethod.invoke(dominionDTO, tntFlag);

            return allowed != null && allowed;

        } catch (ClassNotFoundException e) {
            dominionEnabled = false;
            return true;
        } catch (Exception e) {
            DebugLogger.error("[LandProtection] Error checking Dominion: " + e.getMessage());
            return true;
        }
    }

    private static boolean checkWorldGuard(Location location) {
        try {
            if (worldGuardPlugin == null) {
                Plugin wg = Bukkit.getPluginManager().getPlugin("WorldGuard");
                if (wg == null) {
                    worldGuardEnabled = false;
                    return true;
                }
                worldGuardPlugin = wg;

                Class<?> worldGuardClass = Class.forName("com.sk89q.worldguard.WorldGuard");
                Method getInstanceMethod = worldGuardClass.getMethod("getInstance");
                Object worldGuardInstance = getInstanceMethod.invoke(null);

                Method getPlatformMethod = worldGuardClass.getMethod("getPlatform");
                Object platform = getPlatformMethod.invoke(worldGuardInstance);

                Class<?> platformClass = Class.forName("com.sk89q.worldguard.platform.Platform");
                Method getRegionContainerMethod = platformClass.getMethod("getRegionContainer");
                regionContainer = getRegionContainerMethod.invoke(platform);

                Class<?> regionContainerClass = Class.forName("com.sk89q.worldguard.protection.regions.RegionContainer");
                createQueryMethod = regionContainerClass.getMethod("createQuery");
            }

            Object query = createQueryMethod.invoke(regionContainer);

            Class<?> wgLocationClass = Class.forName("com.sk89q.worldedit.util.Location");
            Class<?> bukkitAdapterClass = Class.forName("com.sk89q.worldedit.bukkit.BukkitAdapter");
            Method adaptLocationMethod = bukkitAdapterClass.getMethod("adapt", Location.class);
            Object wgLocation = adaptLocationMethod.invoke(null, location);

            Class<?> flagsClass = Class.forName("com.sk89q.worldguard.protection.flags.Flags");
            Object tntFlag = flagsClass.getField("TNT").get(null);

            Class<?> regionQueryClass = Class.forName("com.sk89q.worldguard.protection.regions.RegionQuery");
            Class<?> stateFlagClass = Class.forName("com.sk89q.worldguard.protection.flags.StateFlag");
            Method testStateMethod = regionQueryClass.getMethod("testState", wgLocationClass, stateFlagClass);
            Object result = testStateMethod.invoke(query, wgLocation, tntFlag);

            if (result == null) {
                return true;
            }

            String stateName = result.toString();
            return "ALLOW".equals(stateName);

        } catch (ClassNotFoundException e) {
            worldGuardEnabled = false;
            return true;
        } catch (Exception e) {
            DebugLogger.error("[LandProtection] Error checking WorldGuard: " + e.getMessage());
            return true;
        }
    }

    private static boolean checkLands(Location location) {
        try {
            if (landsIntegration == null) {
                Plugin lands = Bukkit.getPluginManager().getPlugin("Lands");
                if (lands == null) {
                    landsEnabled = false;
                    return true;
                }

                Class<?> landsIntegrationClass = Class.forName("me.angeschossen.lands.api.integration.LandsIntegration");
                Method getInstanceMethod = landsIntegrationClass.getMethod("of", Plugin.class);
                landsIntegration = getInstanceMethod.invoke(null, lands);

                getLandByChunkMethod = landsIntegrationClass.getMethod("getLandByChunk", World.class, int.class, int.class);

                Class<?> landClass = Class.forName("me.angeschossen.lands.api.land.Land");
                Class<?> roleFlagClass = Class.forName("me.angeschossen.lands.api.flags.type.RoleFlag");
                hasRoleFlagMethod = landClass.getMethod("hasRoleFlag",
                    Class.forName("me.angeschossen.lands.api.player.LandPlayer"),
                    roleFlagClass,
                    boolean.class);
            }

            int chunkX = location.getBlockX() >> 4;
            int chunkZ = location.getBlockZ() >> 4;

            Object land = getLandByChunkMethod.invoke(landsIntegration, location.getWorld(), chunkX, chunkZ);
            if (land == null) {
                return true;
            }

            Class<?> roleFlagsClass = Class.forName("me.angeschossen.lands.api.flags.type.Flags");
            Object tntFlag = roleFlagsClass.getField("BLOCK_IGNITE").get(null);

            Boolean allowed = (Boolean) hasRoleFlagMethod.invoke(land, null, tntFlag, true);

            return allowed != null && allowed;

        } catch (ClassNotFoundException e) {
            landsEnabled = false;
            return true;
        } catch (Exception e) {
            DebugLogger.error("[LandProtection] Error checking Lands: " + e.getMessage());
            return true;
        }
    }

    private static boolean isResidenceEnabled() {
        if (residenceEnabled == null) {
            residenceEnabled = Bukkit.getPluginManager().isPluginEnabled("Residence");
        }
        return residenceEnabled;
    }

    private static boolean isDominionEnabled() {
        if (dominionEnabled == null) {
            dominionEnabled = Bukkit.getPluginManager().isPluginEnabled("Dominion");
        }
        return dominionEnabled;
    }

    private static boolean isWorldGuardEnabled() {
        if (worldGuardEnabled == null) {
            worldGuardEnabled = Bukkit.getPluginManager().isPluginEnabled("WorldGuard");
        }
        return worldGuardEnabled;
    }

    private static boolean isLandsEnabled() {
        if (landsEnabled == null) {
            landsEnabled = Bukkit.getPluginManager().isPluginEnabled("Lands");
        }
        return landsEnabled;
    }

    private static boolean checkKariClaims(Location location) {
        try {
            if (kariClaimsChunkManager == null) {
                Plugin kariClaims = Bukkit.getPluginManager().getPlugin("KariClaims");
                if (kariClaims == null) {
                    kariClaimsEnabled = false;
                    return true;
                }
                kariClaimsPlugin = kariClaims;

                Class<?> kariClaimsClass = Class.forName("org.kari.kariClaims.KariClaims");
                Method getChunkClaimManagerMethod = kariClaimsClass.getMethod("getChunkClaimManager");
                kariClaimsChunkManager = getChunkClaimManagerMethod.invoke(kariClaims);

                Class<?> chunkClaimManagerClass = Class.forName("org.kari.kariClaims.managers.ChunkClaimManager");
                findChunkClaimAtMethod = chunkClaimManagerClass.getMethod("findChunkClaimAt", Location.class);
                
                // 获取 ChunkClaim 类的方法
                Class<?> chunkClaimClass = Class.forName("org.kari.kariClaims.models.ChunkClaim");
                isTntMethod = chunkClaimClass.getMethod("isTnt");
                isExplosionMethod = chunkClaimClass.getMethod("isExplosion");
                
                // 获取 Optional.get() 方法
                Class<?> optionalClass = Class.forName("java.util.Optional");
                getClaimFromOptionalMethod = optionalClass.getMethod("get");
            }

            Object optionalClaim = findChunkClaimAtMethod.invoke(kariClaimsChunkManager, location);
            if (optionalClaim == null) {
                return true; // 没有领地，允许TNT
            }

            // Check if Optional is present
            Class<?> optionalClass = Class.forName("java.util.Optional");
            Method isPresentMethod = optionalClass.getMethod("isPresent");
            Boolean isPresent = (Boolean) isPresentMethod.invoke(optionalClaim);

            if (isPresent == null || !isPresent) {
                return true; // 没有领地，允许TNT
            }

            // 获取 ChunkClaim 对象
            Object chunkClaim = getClaimFromOptionalMethod.invoke(optionalClaim);
            
            // 检查领地的 TNT 设置
            Boolean tntAllowed = (Boolean) isTntMethod.invoke(chunkClaim);
            if (tntAllowed != null && tntAllowed) {
                return true; // 领地明确允许TNT
            }
            
            // 检查领地的爆炸设置（作为备选）
            Boolean explosionAllowed = (Boolean) isExplosionMethod.invoke(chunkClaim);
            if (explosionAllowed != null && explosionAllowed) {
                return true; // 领地允许爆炸
            }
            
            // 默认：领地存在但未允许TNT/爆炸，阻止TNT
            return false;

        } catch (ClassNotFoundException e) {
            kariClaimsEnabled = false;
            return true;
        } catch (Exception e) {
            DebugLogger.error("[LandProtection] Error checking KariClaims: " + e.getMessage());
            return true; // 出错时默认允许，避免误伤
        }
    }

    private static boolean isKariClaimsEnabled() {
        if (kariClaimsEnabled == null) {
            kariClaimsEnabled = Bukkit.getPluginManager().isPluginEnabled("KariClaims");
        }
        return kariClaimsEnabled;
    }

    private static String getCacheKey(ServerLevel level, BlockPos pos) {
        return level.dimension().location().toString() + ":" +
               pos.getX() + "," + pos.getY() + "," + pos.getZ();
    }

    public static void clearCache() {
        CACHE.clear();
    }

    public static void reset() {
        residenceEnabled = null;
        dominionEnabled = null;
        worldGuardEnabled = null;
        landsEnabled = null;
        kariClaimsEnabled = null;
        residenceAPI = null;
        dominionAPI = null;
        worldGuardPlugin = null;
        regionContainer = null;
        landsIntegration = null;
        kariClaimsPlugin = null;
        kariClaimsChunkManager = null;
        residenceGetByLocMethod = null;
        residenceHasFlagMethod = null;
        dominionGetDominionMethod = null;
        dominionGetFlagMethod = null;
        createQueryMethod = null;
        getLandByChunkMethod = null;
        hasRoleFlagMethod = null;
        findChunkClaimAtMethod = null;
        getClaimFromOptionalMethod = null;
        isTntMethod = null;
        isExplosionMethod = null;
        clearCache();
    }
    
    /**
     * 初始化并记录兼容性状态
     * Initialize and log compatibility status
     */
    public static void logCompatibilityStatus(java.util.logging.Logger logger) {
        // 强制重新检测插件状态
        reset();
        
        StringBuilder status = new StringBuilder("[AkiAsync] Land protection plugin compatibility:");
        int detected = 0;
        
        // 检测 Residence
        if (isResidenceEnabled()) {
            status.append("\n  - Residence: ✓ Detected");
            detected++;
        } else {
            status.append("\n  - Residence: ✗ Not found");
        }
        
        // 检测 Dominion
        if (isDominionEnabled()) {
            status.append("\n  - Dominion: ✓ Detected");
            detected++;
        } else {
            status.append("\n  - Dominion: ✗ Not found");
        }
        
        // 检测 WorldGuard
        if (isWorldGuardEnabled()) {
            status.append("\n  - WorldGuard: ✓ Detected");
            detected++;
        } else {
            status.append("\n  - WorldGuard: ✗ Not found");
        }
        
        // 检测 Lands
        if (isLandsEnabled()) {
            status.append("\n  - Lands: ✓ Detected");
            detected++;
        } else {
            status.append("\n  - Lands: ✗ Not found");
        }
        
        // 检测 KariClaims
        if (isKariClaimsEnabled()) {
            status.append("\n  - KariClaims: ✓ Detected");
            detected++;
        } else {
            status.append("\n  - KariClaims: ✗ Not found");
        }
        
        if (detected > 0) {
            status.append("\n  Total: ").append(detected).append(" protection plugin(s) detected");
            logger.info(status.toString());
        } else {
            logger.info("[AkiAsync] No land protection plugins detected - TNT explosions will not be restricted by claims");
        }
    }
}
