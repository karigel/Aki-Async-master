package org.virgil.akiasync.ignite.adapter;

import net.minecraft.core.BlockPos;
import org.virgil.akiasync.ignite.config.IgniteConfigManager;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * Ignite 适配的结构缓存管理器
 * 不依赖 AkiAsyncPlugin，使用 IgniteConfigManager 获取配置
 */
public class IgniteStructureCacheManager {

    private static IgniteStructureCacheManager instance;
    private final IgniteConfigManager config;
    private final Logger logger;

    private final ConcurrentHashMap<String, CacheEntry> structureCache;
    private final ConcurrentHashMap<String, Long> negativeCache;

    private volatile int maxCacheSize;
    private volatile long expirationMinutes;
    private volatile boolean cachingEnabled;

    private final ScheduledExecutorService cleanupExecutor;

    private final AtomicLong cacheHits = new AtomicLong(0);
    private final AtomicLong cacheMisses = new AtomicLong(0);
    private final AtomicLong negativeHits = new AtomicLong(0);

    private IgniteStructureCacheManager(IgniteConfigManager config, Logger logger) {
        this.config = config;
        this.logger = logger;
        this.structureCache = new ConcurrentHashMap<>();
        this.negativeCache = new ConcurrentHashMap<>();
        this.cleanupExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "AkiAsync-Ignite-StructureCache-Cleanup");
            t.setDaemon(true);
            return t;
        });

        updateConfiguration();
        startCleanupTask();
    }

    public static synchronized IgniteStructureCacheManager getInstance(IgniteConfigManager config, Logger logger) {
        if (instance == null) {
            instance = new IgniteStructureCacheManager(config, logger);
        }
        return instance;
    }

    public static synchronized IgniteStructureCacheManager getInstance() {
        return instance;
    }

    public void updateConfiguration() {
        if (config != null) {
            this.cachingEnabled = config.isStructureCachingEnabled();
            this.maxCacheSize = config.getStructureCacheMaxSize();
            this.expirationMinutes = config.getStructureCacheExpirationMinutes();
        } else {
            this.cachingEnabled = true;
            this.maxCacheSize = 1000;
            this.expirationMinutes = 30;
        }
    }

    public BlockPos getCachedStructure(String cacheKey) {
        if (!cachingEnabled) {
            return null;
        }

        CacheEntry entry = structureCache.get(cacheKey);
        if (entry != null) {
            if (isExpired(entry)) {
                structureCache.remove(cacheKey);
                return null;
            }
            cacheHits.incrementAndGet();
            return entry.position;
        }

        cacheMisses.incrementAndGet();
        return null;
    }

    public void cacheStructure(String cacheKey, BlockPos position) {
        if (!cachingEnabled) {
            return;
        }

        if (structureCache.size() >= maxCacheSize) {
            evictOldestEntries();
        }

        CacheEntry entry = new CacheEntry(position, System.currentTimeMillis());
        structureCache.put(cacheKey, entry);
    }

    public boolean isNegativeCached(String cacheKey) {
        if (!cachingEnabled) {
            return false;
        }

        Long timestamp = negativeCache.get(cacheKey);
        if (timestamp != null) {
            long age = System.currentTimeMillis() - timestamp;
            if (age < TimeUnit.MINUTES.toMillis(expirationMinutes)) {
                negativeHits.incrementAndGet();
                return true;
            } else {
                negativeCache.remove(cacheKey);
            }
        }
        return false;
    }

    public void cacheNegativeResult(String cacheKey) {
        if (!cachingEnabled) {
            return;
        }

        if (negativeCache.size() >= maxCacheSize) {
            evictOldestNegativeCaches();
        }

        negativeCache.put(cacheKey, System.currentTimeMillis());
    }

    private boolean isExpired(CacheEntry entry) {
        long age = System.currentTimeMillis() - entry.timestamp;
        return age > TimeUnit.MINUTES.toMillis(expirationMinutes);
    }

    private void evictOldestEntries() {
        if (structureCache.size() <= maxCacheSize * 0.8) {
            return;
        }

        int toRemove = (int) (maxCacheSize * 0.2);
        var oldestEntries = structureCache.entrySet().stream()
                .sorted((e1, e2) -> Long.compare(e1.getValue().timestamp, e2.getValue().timestamp))
                .limit(toRemove)
                .map(java.util.Map.Entry::getKey)
                .collect(Collectors.toList());

        oldestEntries.forEach(structureCache::remove);
    }

    private void evictOldestNegativeCaches() {
        if (negativeCache.size() <= maxCacheSize * 0.8) {
            return;
        }

        int toRemove = (int) (maxCacheSize * 0.2);
        var oldestEntries = negativeCache.entrySet().stream()
                .sorted((e1, e2) -> Long.compare(e1.getValue(), e2.getValue()))
                .limit(toRemove)
                .map(java.util.Map.Entry::getKey)
                .collect(Collectors.toList());

        oldestEntries.forEach(negativeCache::remove);
    }

    private void startCleanupTask() {
        cleanupExecutor.scheduleAtFixedRate(() -> {
            try {
                cleanupExpiredEntries();
            } catch (Exception e) {
                if (logger != null) {
                    logger.warning("[AkiAsync/Ignite] Structure cache cleanup error: " + e.getMessage());
                }
            }
        }, 5, 5, TimeUnit.MINUTES);
    }

    private void cleanupExpiredEntries() {
        long now = System.currentTimeMillis();
        long expirationMillis = TimeUnit.MINUTES.toMillis(expirationMinutes);

        int structureRemoved = 0;
        for (var entry : structureCache.entrySet()) {
            if (now - entry.getValue().timestamp > expirationMillis) {
                structureCache.remove(entry.getKey());
                structureRemoved++;
            }
        }

        int negativeRemoved = 0;
        for (var entry : negativeCache.entrySet()) {
            if (now - entry.getValue() > expirationMillis) {
                negativeCache.remove(entry.getKey());
                negativeRemoved++;
            }
        }

        if (logger != null && (structureRemoved > 0 || negativeRemoved > 0)) {
            logger.info(String.format(
                    "[AkiAsync/Ignite] Cleaned expired cache entries: %d structures, %d negative",
                    structureRemoved, negativeRemoved
            ));
        }
    }

    public void clearCache() {
        structureCache.clear();
        negativeCache.clear();
        cacheHits.set(0);
        cacheMisses.set(0);
        negativeHits.set(0);
        if (logger != null) {
            logger.info("[AkiAsync/Ignite] Structure cache cleared");
        }
    }

    public String getStatistics() {
        long hits = cacheHits.get();
        long misses = cacheMisses.get();
        long negHits = negativeHits.get();
        long total = hits + misses;
        double hitRate = total > 0 ? (double) hits / total * 100 : 0;

        return String.format(
                "Cached: %d, Negative: %d, Hits: %d, Misses: %d, Hit Rate: %.1f%%, Negative Hits: %d",
                structureCache.size(), negativeCache.size(), hits, misses, hitRate, negHits
        );
    }

    public void shutdown() {
        cleanupExecutor.shutdown();
        try {
            if (!cleanupExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                cleanupExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            cleanupExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
        
        if (logger != null) {
            logger.info("[AkiAsync/Ignite] Structure cache manager shut down");
        }
    }

    private static class CacheEntry {
        final BlockPos position;
        final long timestamp;

        CacheEntry(BlockPos position, long timestamp) {
            this.position = position;
            this.timestamp = timestamp;
        }
    }
}
