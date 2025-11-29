package org.virgil.akiasync.compat;

import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataContainer;
import org.virgil.akiasync.util.DebugLogger;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * ZNPCsPlus 插件检测器 / ZNPCsPlus Plugin Detector
 */
public class ZNPCsPlusDetector implements PluginDetector {

    private static final String PLUGIN_NAME = "ZNPCsPlus";
    private static final int PRIORITY = 90;
    private static final long CACHE_TTL_MS = 5000;
    private static final int MAX_CACHE_SIZE = 1000;
    private static final String ZNPC_NAME_PREFIX = "[ZNPC] ";

    private final ConcurrentHashMap<UUID, CacheEntry> detectionCache;
    private volatile boolean pluginAvailable;

    public ZNPCsPlusDetector() {
        this.detectionCache = new ConcurrentHashMap<>();
        this.pluginAvailable = checkPluginAvailability();
    }

    @Override
    public String getPluginName() {
        return PLUGIN_NAME;
    }

    @Override
    public int getPriority() {
        return PRIORITY;
    }

    @Override
    public boolean isAvailable() {
        return pluginAvailable;
    }

    @Override
    public boolean isVirtualEntity(Entity entity) {
        if (entity == null) {
            return false;
        }

        UUID entityId = entity.getUniqueId();
        CacheEntry cached = detectionCache.get(entityId);
        if (cached != null && !cached.isExpired()) {
            return cached.isVirtual;
        }

        boolean isVirtual = false;

        try {
            isVirtual = detectViaAPI(entity);
        } catch (Exception e) {
            // Ignore
        }

        if (!isVirtual) {
            isVirtual = detectViaFallback(entity);
        }

        cacheResult(entityId, isVirtual);
        return isVirtual;
    }

    @Override
    public boolean detectViaAPI(Entity entity) {
        if (!(entity instanceof Player)) {
            return false;
        }

        try {
            if (entity.customName() != null) {
                String customName = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText()
                    .serialize(entity.customName());
                if (customName.startsWith(ZNPC_NAME_PREFIX)) {
                    return true;
                }
            }

            String name = entity.getName();
            if (name != null && name.startsWith(ZNPC_NAME_PREFIX)) {
                return true;
            }
        } catch (Exception e) {
            DebugLogger.debug("[ZNPCsPlus Compat] Error checking entity name: %s", e.getMessage());
        }

        return false;
    }

    @Override
    public boolean detectViaFallback(Entity entity) {
        if (!(entity instanceof Player)) {
            return false;
        }

        try {
            PersistentDataContainer pdc = entity.getPersistentDataContainer();
            for (NamespacedKey key : pdc.getKeys()) {
                String keyStr = key.toString().toLowerCase();
                if (keyStr.contains("znpc") || keyStr.contains("znpcsplus")) {
                    return true;
                }
            }
        } catch (Exception e) {
            // Ignore
        }

        try {
            if (entity.hasMetadata("znpc") || 
                entity.hasMetadata("znpcsplus") || 
                entity.hasMetadata("ZNPC")) {
                return true;
            }
        } catch (Exception e) {
            // Ignore
        }

        return false;
    }

    public void clearCache() {
        detectionCache.clear();
    }

    private boolean checkPluginAvailability() {
        return Bukkit.getPluginManager().getPlugin("ZNPCsPlus") != null ||
               Bukkit.getPluginManager().getPlugin("znpcsplus") != null;
    }

    private void cacheResult(UUID entityId, boolean isVirtual) {
        if (detectionCache.size() >= MAX_CACHE_SIZE) {
            evictExpiredEntries();
        }
        detectionCache.put(entityId, new CacheEntry(isVirtual, System.currentTimeMillis() + CACHE_TTL_MS));
    }

    private void evictExpiredEntries() {
        long now = System.currentTimeMillis();
        detectionCache.entrySet().removeIf(entry -> entry.getValue().isExpired(now));
    }

    private static class CacheEntry {
        final boolean isVirtual;
        final long expiryTime;

        CacheEntry(boolean isVirtual, long expiryTime) {
            this.isVirtual = isVirtual;
            this.expiryTime = expiryTime;
        }

        boolean isExpired() {
            return isExpired(System.currentTimeMillis());
        }

        boolean isExpired(long currentTime) {
            return currentTime > expiryTime;
        }
    }
}
