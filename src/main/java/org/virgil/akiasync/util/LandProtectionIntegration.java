package org.virgil.akiasync.util;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.plugin.Plugin;

/**
 * 土地保护集成
 * Land Protection Integration
 * 
 * 支持与土地保护插件集成，防止 TNT 在保护区域内爆炸
 * Supports integration with land protection plugins to prevent TNT explosions in protected areas
 * 
 * 支持的插件 / Supported plugins:
 * - Residence (com.github.Zrips:Residence)
 * - Lands (com.github.angeschossen:LandsAPI)
 * - WorldGuard (com.sk89q.worldguard)
 * - KariClaims (org.kari.kariClaims)
 * 
 * 版本 / Version: 8.1
 */
public class LandProtectionIntegration {
    
    private static boolean residenceEnabled = false;
    private static boolean landsEnabled = false;
    private static boolean worldGuardEnabled = false;
    private static boolean kariClaimsEnabled = false;
    
    private static Object residenceAPI = null;
    private static Object landsAPI = null;
    private static Object worldGuardAPI = null;
    private static Object kariClaimsManager = null;
    
    private static volatile boolean initialized = false;
    private static volatile boolean pluginsChecked = false;
    
    /**
     * 确保已初始化（延迟初始化）
     * Ensure initialized (lazy initialization)
     * 
     * 如果首次检测时没有找到插件，会在下次调用时重新检测
     * If no plugins found on first check, will re-check on next call
     */
    private static void ensureInitialized() {
        // 如果已检测到插件，无需再次初始化
        if (initialized && pluginsChecked) {
            return;
        }
        
        synchronized (LandProtectionIntegration.class) {
            // 双重检查
            if (initialized && pluginsChecked) {
                return;
            }
            
            // 检查 Bukkit 是否已准备好
            try {
                if (Bukkit.getPluginManager() == null || Bukkit.getPluginManager().getPlugins().length == 0) {
                    // Bukkit 还没准备好，稍后再试
                    return;
                }
            } catch (Exception e) {
                // Bukkit 还没准备好
                return;
            }
            
            // 重置状态并重新初始化
            residenceEnabled = false;
            landsEnabled = false;
            worldGuardEnabled = false;
            kariClaimsEnabled = false;
            residenceAPI = null;
            landsAPI = null;
            worldGuardAPI = null;
            kariClaimsManager = null;
            
            initialize();
            initialized = true;
            
            // 如果找到了任何插件，标记为已完成检测
            if (residenceEnabled || landsEnabled || worldGuardEnabled || kariClaimsEnabled) {
                pluginsChecked = true;
            }
        }
    }
    
    private static void initialize() {
        // 检查 Residence
        Plugin residence = Bukkit.getPluginManager().getPlugin("Residence");
        if (residence != null && residence.isEnabled()) {
            try {
                Class<?> residenceClass = Class.forName("com.bekvon.bukkit.residence.Residence");
                residenceAPI = residenceClass.getMethod("getInstance").invoke(null);
                residenceEnabled = true;
            } catch (Exception e) {
                // Residence 未找到或版本不兼容
            }
        }
        
        // 检查 Lands
        Plugin lands = Bukkit.getPluginManager().getPlugin("Lands");
        if (lands != null && lands.isEnabled()) {
            try {
                Class<?> landsClass = Class.forName("me.angeschossen.lands.api.LandsIntegration");
                landsAPI = landsClass.getMethod("getInstance").invoke(null);
                landsEnabled = true;
            } catch (Exception e) {
                // Lands 未找到或版本不兼容
            }
        }
        
        // 检查 WorldGuard
        Plugin worldGuard = Bukkit.getPluginManager().getPlugin("WorldGuard");
        if (worldGuard != null && worldGuard.isEnabled()) {
            try {
                Class<?> wgClass = Class.forName("com.sk89q.worldguard.WorldGuard");
                worldGuardAPI = wgClass.getMethod("getInstance").invoke(null);
                worldGuardEnabled = true;
            } catch (Exception e) {
                // WorldGuard 未找到或版本不兼容
            }
        }
        
        // 检查 KariClaims
        Plugin kariClaims = Bukkit.getPluginManager().getPlugin("KariClaims");
        if (kariClaims != null && kariClaims.isEnabled()) {
            try {
                Class<?> kcClass = Class.forName("org.kari.kariClaims.KariClaims");
                Object instance = kcClass.getMethod("getInstance").invoke(null);
                kariClaimsManager = kcClass.getMethod("getChunkClaimManager").invoke(instance);
                kariClaimsEnabled = true;
            } catch (Exception e) {
                // KariClaims 未找到或版本不兼容
            }
        }
    }
    
    /**
     * 检查 TNT 是否可以在指定位置爆炸
     * Check if TNT can explode at the specified location
     * 
     * @param level 服务器世界 / Server level
     * @param pos 位置 / Position
     * @return true 如果可以爆炸，false 如果被保护 / true if can explode, false if protected
     */
    public static boolean canTNTExplode(ServerLevel level, BlockPos pos) {
        ensureInitialized();
        org.bukkit.World bukkitWorld = level.getWorld();
        Location location = new Location(
            bukkitWorld,
            pos.getX(),
            pos.getY(),
            pos.getZ()
        );
        
        // 检查 Residence
        if (residenceEnabled && residenceAPI != null) {
            try {
                Object resManager = residenceAPI.getClass().getMethod("getResidenceManager").invoke(residenceAPI);
                Object res = resManager.getClass().getMethod("getByLoc", Location.class).invoke(resManager, location);
                if (res != null) {
                    return false; // 在 Residence 保护区域内
                }
            } catch (Exception e) {
                // 忽略错误，继续检查其他插件
            }
        }
        
        // 检查 Lands
        if (landsEnabled && landsAPI != null) {
            try {
                Object land = landsAPI.getClass().getMethod("getLand", Location.class).invoke(landsAPI, location);
                if (land != null) {
                    // 检查是否允许爆炸
                    boolean canExplode = (Boolean) land.getClass().getMethod("isFlagSet", String.class).invoke(land, "explosion");
                    return canExplode;
                }
            } catch (Exception e) {
                // 忽略错误，继续检查其他插件
            }
        }
        
        // 检查 WorldGuard
        if (worldGuardEnabled && worldGuardAPI != null) {
            try {
                Object regionManager = worldGuardAPI.getClass().getMethod("getPlatform").invoke(worldGuardAPI)
                    .getClass().getMethod("getRegionContainer").invoke(null);
                Object query = regionManager.getClass().getMethod("createQuery").invoke(regionManager);
                Object flagValue = query.getClass().getMethod("queryValue", 
                    org.bukkit.Location.class, 
                    Class.forName("com.sk89q.worldguard.protection.flags.Flag")).invoke(
                    query, location, 
                    Class.forName("com.sk89q.worldguard.protection.flags.Flags").getField("TNT").get(null)
                );
                if (flagValue != null && flagValue.toString().equals("DENY")) {
                    return false; // WorldGuard 禁止爆炸
                }
            } catch (Exception e) {
                // 忽略错误
            }
        }
        
        // 检查 KariClaims
        if (kariClaimsEnabled && kariClaimsManager != null) {
            try {
                // 调用 findChunkClaimAt(Location) 方法
                java.lang.reflect.Method findMethod = kariClaimsManager.getClass()
                    .getMethod("findChunkClaimAt", Location.class);
                Object claimOptional = findMethod.invoke(kariClaimsManager, location);
                
                // 检查 Optional 是否有值
                java.lang.reflect.Method isPresentMethod = claimOptional.getClass().getMethod("isPresent");
                boolean hasClaim = (Boolean) isPresentMethod.invoke(claimOptional);
                
                if (hasClaim) {
                    // 获取 claim 对象并检查 explosion 设置
                    java.lang.reflect.Method getMethod = claimOptional.getClass().getMethod("get");
                    Object claim = getMethod.invoke(claimOptional);
                    
                    // 检查是否允许爆炸 (isExplosion) 和 TNT (isTnt)
                    java.lang.reflect.Method isExplosionMethod = claim.getClass().getMethod("isExplosion");
                    java.lang.reflect.Method isTntMethod = claim.getClass().getMethod("isTnt");
                    
                    boolean explosionAllowed = (Boolean) isExplosionMethod.invoke(claim);
                    boolean tntAllowed = (Boolean) isTntMethod.invoke(claim);
                    
                    if (!explosionAllowed || !tntAllowed) {
                        return false; // KariClaims 禁止爆炸
                    }
                }
            } catch (Exception e) {
                // 忽略错误
            }
        }
        
        return true; // 默认允许爆炸
    }
    
    public static boolean isResidenceEnabled() {
        ensureInitialized();
        return residenceEnabled;
    }
    
    public static boolean isLandsEnabled() {
        ensureInitialized();
        return landsEnabled;
    }
    
    public static boolean isWorldGuardEnabled() {
        ensureInitialized();
        return worldGuardEnabled;
    }
    
    public static boolean isKariClaimsEnabled() {
        ensureInitialized();
        return kariClaimsEnabled;
    }
    
    /**
     * 获取所有已检测到的保护插件列表
     * Get list of all detected protection plugins
     */
    public static java.util.List<String> getDetectedPlugins() {
        ensureInitialized();
        java.util.List<String> plugins = new java.util.ArrayList<>();
        if (residenceEnabled) plugins.add("Residence");
        if (landsEnabled) plugins.add("Lands");
        if (worldGuardEnabled) plugins.add("WorldGuard");
        if (kariClaimsEnabled) plugins.add("KariClaims");
        return plugins;
    }
    
    /**
     * 检查是否有任何保护插件被检测到
     * Check if any protection plugin is detected
     */
    public static boolean hasAnyProtectionPlugin() {
        ensureInitialized();
        return residenceEnabled || landsEnabled || worldGuardEnabled || kariClaimsEnabled;
    }
    
    /**
     * 打印兼容状态日志
     * Print compatibility status log
     */
    public static void logCompatibilityStatus(java.util.logging.Logger logger) {
        if (hasAnyProtectionPlugin()) {
            logger.info("[AkiAsync] Land protection plugins detected:");
            if (residenceEnabled) logger.info("  [✓] Residence - Compatible");
            if (landsEnabled) logger.info("  [✓] Lands - Compatible");
            if (worldGuardEnabled) logger.info("  [✓] WorldGuard - Compatible");
            if (kariClaimsEnabled) logger.info("  [✓] KariClaims - Compatible");
            logger.info("[AkiAsync] TNT explosions will respect protected areas.");
        } else {
            logger.info("[AkiAsync] No land protection plugins detected.");
        }
    }
    
    public static void reload() {
        synchronized (LandProtectionIntegration.class) {
            residenceEnabled = false;
            landsEnabled = false;
            worldGuardEnabled = false;
            kariClaimsEnabled = false;
            residenceAPI = null;
            landsAPI = null;
            worldGuardAPI = null;
            kariClaimsManager = null;
            initialized = false;
            pluginsChecked = false;
        }
        ensureInitialized();
    }
}

