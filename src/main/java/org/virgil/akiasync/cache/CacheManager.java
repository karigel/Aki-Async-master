package org.virgil.akiasync.cache;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.logging.Logger;

import org.virgil.akiasync.mixin.bridge.Bridge;
import org.virgil.akiasync.mixin.bridge.BridgeManager;


public class CacheManager {
    
    private static final Logger LOGGER = Logger.getLogger("AkiAsync-Cache");
    private static CacheManager instance;
    
    private final Map<String, CacheEntry> globalCache = new ConcurrentHashMap<>();
    private static final int MAX_CACHE_SIZE = 10000;
    private static final long DEFAULT_EXPIRATION_MS = 30 * 60 * 1000;
    
    private ScheduledExecutorService cleanupExecutor;
    
    private CacheManager() {
        startPeriodicCleanup();
    }
    
    public static synchronized CacheManager getInstance() {
        if (instance == null) {
            instance = new CacheManager();
        }
        return instance;
    }


    private void startPeriodicCleanup() {
        cleanupExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "AkiAsync-CacheCleanup");
            t.setDaemon(true);
            return t;
        });
        
        cleanupExecutor.scheduleAtFixedRate(() -> {
            try {
                cleanupExpired();
                SakuraCacheStatistics.performPeriodicCleanup();
                
                Bridge bridge = BridgeManager.getBridge();
                if (bridge != null && bridge.isDebugLoggingEnabled()) {
                    bridge.debugLog("[AkiAsync-Cache] Periodic cleanup completed");
                }
            } catch (Exception e) {
                LOGGER.warning("[AkiAsync-Cache] Error during periodic cleanup: " + e.getMessage());
            }
        }, 5, 5, TimeUnit.MINUTES);
    }
    
    public void invalidateAll() {
        LOGGER.info("[AkiAsync] Invalidating all caches...");
        
        globalCache.clear();
        
        try {
            org.virgil.akiasync.mixin.async.villager.VillagerBreedExecutor.clearOldCache(Long.MAX_VALUE);
        } catch (Exception e) {
            LOGGER.warning("Failed to clear villager breed cache: " + e.getMessage());
        }
        
        try {
            org.virgil.akiasync.mixin.brain.core.AsyncBrainExecutor.resetStatistics();
        } catch (Exception e) {
            LOGGER.warning("Failed to reset brain executor statistics: " + e.getMessage());
        }
        
        try {
            clearSakuraOptimizationCaches();
        } catch (Exception e) {
            LOGGER.warning("Failed to clear Sakura optimization caches: " + e.getMessage());
        }
        
        LOGGER.info("[AkiAsync] Main caches cleared, controlled cleanup in progress");
    }


    private void clearSakuraOptimizationCaches() {
        try {
            Bridge bridge = BridgeManager.getBridge();
            if (bridge != null) {
                bridge.clearSakuraOptimizationCaches();
                LOGGER.info("[AkiAsync] Cleared Sakura optimization caches");
            }
        } catch (Exception e) {
            LOGGER.warning("Failed to clear Sakura optimization caches: " + e.getMessage());
        }
    }
    
    public void put(String key, Object value) {
        put(key, value, DEFAULT_EXPIRATION_MS);
    }
    
    public void put(String key, Object value, long expirationMs) {

        if (globalCache.size() >= MAX_CACHE_SIZE) {
            evictOldEntries();
        }
        
        globalCache.put(key, new CacheEntry(value, System.currentTimeMillis(), expirationMs));
    }
    
    public Object get(String key) {
        CacheEntry entry = globalCache.get(key);
        if (entry == null) {
            return null;
        }
        

        if (entry.isExpired()) {
            globalCache.remove(key);
            return null;
        }
        
        return entry.value;
    }
    
    public Object remove(String key) {
        CacheEntry entry = globalCache.remove(key);
        return entry != null ? entry.value : null;
    }
    
    public void clear() {
        globalCache.clear();
    }
    
    public int size() {
        return globalCache.size();
    }
    
    public boolean containsKey(String key) {
        CacheEntry entry = globalCache.get(key);
        if (entry != null && entry.isExpired()) {
            globalCache.remove(key);
            return false;
        }
        return entry != null;
    }


    private void evictOldEntries() {
        int toRemove = MAX_CACHE_SIZE / 10;
        
        globalCache.entrySet().stream()
            .sorted((e1, e2) -> Long.compare(e1.getValue().timestamp, e2.getValue().timestamp))
            .limit(toRemove)
            .map(Map.Entry::getKey)
            .collect(Collectors.toList())
            .forEach(globalCache::remove);
        
        Bridge bridge = BridgeManager.getBridge();
        if (bridge != null && bridge.isDebugLoggingEnabled()) {
            bridge.debugLog("[AkiAsync-Cache] Evicted %d old entries, current size: %d/%d",
                toRemove, globalCache.size(), MAX_CACHE_SIZE);
        }
    }


    public void cleanupExpired() {
        int removed = 0;
        var iterator = globalCache.entrySet().iterator();
        while (iterator.hasNext()) {
            var entry = iterator.next();
            if (entry.getValue().isExpired()) {
                iterator.remove();
                removed++;
            }
        }
        
        Bridge bridge = BridgeManager.getBridge();
        if (removed > 0 && bridge != null && bridge.isDebugLoggingEnabled()) {
            bridge.debugLog("[AkiAsync-Cache] Cleaned up %d expired entries", removed);
        }
    }


    public String getAllCacheStatistics() {
        StringBuilder sb = new StringBuilder();
        
        sb.append("§6=== AkiAsync Cache Statistics ===§r\n");
        sb.append(String.format("§e[Global Cache]§r\n  §7Size: §f%d/%d§r\n", 
            globalCache.size(), MAX_CACHE_SIZE));
        
        sb.append("\n");
        sb.append(SakuraCacheStatistics.formatStatistics());
        
        return sb.toString();
    }
    
    public void shutdown() {
        if (cleanupExecutor != null && !cleanupExecutor.isShutdown()) {
            cleanupExecutor.shutdown();
            try {
                if (!cleanupExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                    cleanupExecutor.shutdownNow();
                }
            } catch (InterruptedException e) {
                cleanupExecutor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
    }


    private static class CacheEntry {
        final Object value;
        final long timestamp;
        final long expirationMs;
        
        CacheEntry(Object value, long timestamp, long expirationMs) {
            this.value = value;
            this.timestamp = timestamp;
            this.expirationMs = expirationMs;
        }
        
        boolean isExpired() {
            return System.currentTimeMillis() - timestamp > expirationMs;
        }
    }
}