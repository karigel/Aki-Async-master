package org.virgil.akiasync.compat;

import org.bukkit.entity.Entity;

/**
 * 插件检测器接口 / Plugin Detector Interface
 * 用于检测虚拟实体的插件特定实现
 */
public interface PluginDetector {
    
    String getPluginName();
    
    int getPriority();
    
    boolean isAvailable();
    
    boolean isVirtualEntity(Entity entity);
    
    boolean detectViaAPI(Entity entity);
    
    boolean detectViaFallback(Entity entity);
}
