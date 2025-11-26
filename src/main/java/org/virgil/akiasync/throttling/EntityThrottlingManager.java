package org.virgil.akiasync.throttling;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.virgil.akiasync.AkiAsyncPlugin;

import java.io.File;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 实体节流管理器
 * Entity Throttling Manager
 * 
 * 实现动态激活大脑（DAB - Dynamic Activation of Brain）功能
 * Implements Dynamic Activation of Brain (DAB) feature
 * 
 * 来源 / Source: Pufferfish
 * 版本 / Version: 8.0
 */
public class EntityThrottlingManager {
    
    private final AkiAsyncPlugin plugin;
    private YamlConfiguration throttlingConfig;
    
    private volatile boolean enabled = false;
    private final Map<String, ThrottleConfig> entityConfigs = new ConcurrentHashMap<>();
    private ThrottleConfig defaultConfig;
    
    public EntityThrottlingManager(AkiAsyncPlugin plugin) {
        this.plugin = plugin;
        loadConfiguration();
    }
    
    public void loadConfiguration() {
        File configFile;
        
        // 支持 Ignite 模式：如果没有 plugin，尝试从 AkiAsyncInitializer 获取数据文件夹
        if (plugin != null) {
            configFile = new File(plugin.getDataFolder(), "throttling.yml");
            
            if (!configFile.exists()) {
                plugin.saveResource("throttling.yml", false);
            }
        } else {
            // Ignite 模式：使用 AkiAsyncInitializer 的数据文件夹
            try {
                org.virgil.akiasync.bootstrap.AkiAsyncInitializer init = 
                    org.virgil.akiasync.bootstrap.AkiAsyncInitializer.getInstance();
                File dataFolder = init.getDataFolder();
                if (dataFolder == null) {
                    dataFolder = new File("plugins/AkiAsync");
                    if (!dataFolder.exists()) {
                        dataFolder.mkdirs();
                    }
                }
                configFile = new File(dataFolder, "throttling.yml");
            } catch (Exception e) {
                // 回退到默认路径
                configFile = new File("plugins/AkiAsync/throttling.yml");
                File parent = configFile.getParentFile();
                if (parent != null && !parent.exists()) {
                    parent.mkdirs();
                }
            }
        }
        
        throttlingConfig = YamlConfiguration.loadConfiguration(configFile);
        enabled = throttlingConfig.getBoolean("throttling.enabled", false);
        
        // 加载默认配置
        defaultConfig = new ThrottleConfig(
            throttlingConfig.getInt("throttling.default.activation-distance", 32),
            throttlingConfig.getInt("throttling.default.max-activation-distance", 64),
            throttlingConfig.getInt("throttling.default.throttle-interval", 20)
        );
        
        // 加载实体特定配置
        if (throttlingConfig.isConfigurationSection("throttling.entities")) {
            java.util.Set<String> entityKeys = throttlingConfig.getConfigurationSection("throttling.entities").getKeys(false);
            for (String entityKey : entityKeys) {
                ThrottleConfig config = new ThrottleConfig(
                    throttlingConfig.getInt("throttling.entities." + entityKey + ".activation-distance", defaultConfig.activationDistance),
                    throttlingConfig.getInt("throttling.entities." + entityKey + ".max-activation-distance", defaultConfig.maxActivationDistance),
                    throttlingConfig.getInt("throttling.entities." + entityKey + ".throttle-interval", defaultConfig.throttleInterval)
                );
                entityConfigs.put(entityKey, config);
            }
        }
    }
    
    public boolean shouldThrottle(Entity entity) {
        if (!enabled || entity == null) {
            return false;
        }
        
        // 检查是否有玩家在激活距离内
        ThrottleConfig config = getConfigForEntity(entity);
        if (config == null) {
            return false;
        }
        
        // 获取最近的玩家
        Player nearestPlayer = getNearestPlayer(entity, config.maxActivationDistance);
        if (nearestPlayer == null) {
            return true; // 没有玩家在范围内，节流
        }
        
        double distance = entity.getLocation().distance(nearestPlayer.getLocation());
        return distance > config.activationDistance;
    }
    
    private Player getNearestPlayer(Entity entity, int maxDistance) {
        Player nearest = null;
        double nearestDistance = maxDistance;
        
        for (Player player : entity.getWorld().getPlayers()) {
            if (!player.isOnline()) {
                continue;
            }
            
            double distance = entity.getLocation().distance(player.getLocation());
            if (distance < nearestDistance) {
                nearestDistance = distance;
                nearest = player;
            }
        }
        
        return nearest;
    }
    
    private ThrottleConfig getConfigForEntity(Entity entity) {
        String entityType = entity.getType().getKey().toString();
        return entityConfigs.getOrDefault(entityType, defaultConfig);
    }
    
    public boolean isEnabled() {
        return enabled;
    }
    
    public void reload() {
        loadConfiguration();
    }
    
    private static class ThrottleConfig {
        final int activationDistance;
        final int maxActivationDistance;
        final int throttleInterval;
        
        ThrottleConfig(int activationDistance, int maxActivationDistance, int throttleInterval) {
            this.activationDistance = activationDistance;
            this.maxActivationDistance = maxActivationDistance;
            this.throttleInterval = throttleInterval;
        }
    }
}

