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
 * 
 * 版本 / Version: 8.0
 */
public class LandProtectionIntegration {
    
    private static boolean residenceEnabled = false;
    private static boolean landsEnabled = false;
    private static boolean worldGuardEnabled = false;
    
    private static Object residenceAPI = null;
    private static Object landsAPI = null;
    private static Object worldGuardAPI = null;
    
    static {
        initialize();
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
        
        return true; // 默认允许爆炸
    }
    
    public static boolean isResidenceEnabled() {
        return residenceEnabled;
    }
    
    public static boolean isLandsEnabled() {
        return landsEnabled;
    }
    
    public static boolean isWorldGuardEnabled() {
        return worldGuardEnabled;
    }
    
    public static void reload() {
        initialize();
    }
}

