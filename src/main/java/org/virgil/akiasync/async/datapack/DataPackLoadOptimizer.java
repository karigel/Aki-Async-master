package org.virgil.akiasync.async.datapack;

import org.virgil.akiasync.AkiAsyncPlugin;

import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;

public class DataPackLoadOptimizer {
    
    private static DataPackLoadOptimizer instance;
    private final AkiAsyncPlugin plugin;
    
    private final ExecutorService fileLoadExecutor;
    private final ExecutorService zipProcessExecutor;
    
    private final Map<String, CachedFileSystem> fileSystemCache;
    private final Map<String, CachedFileEntry> fileCache;
    
    private final AtomicLong totalFilesProcessed = new AtomicLong(0);
    private final AtomicLong totalLoadTime = new AtomicLong(0);
    private final AtomicLong cacheHits = new AtomicLong(0);
    
    private volatile boolean optimizationEnabled;
    private volatile int fileLoadThreads;
    private volatile int zipProcessThreads;
    private volatile int batchSize;
    private volatile long cacheExpirationMs;
    private volatile boolean debugLogging;
    
    private DataPackLoadOptimizer(AkiAsyncPlugin plugin) {
        this.plugin = plugin;
        this.fileSystemCache = new ConcurrentHashMap<>();
        this.fileCache = new ConcurrentHashMap<>();
        
        updateConfiguration();
        
        this.fileLoadExecutor = Executors.newFixedThreadPool(fileLoadThreads, r -> {
            Thread t = new Thread(r, "AkiAsync-DataPack-FileLoad-" + System.currentTimeMillis());
            t.setDaemon(true);
            t.setPriority(Thread.NORM_PRIORITY - 1);
            return t;
        });
        
        this.zipProcessExecutor = Executors.newFixedThreadPool(zipProcessThreads, r -> {
            Thread t = new Thread(r, "AkiAsync-DataPack-ZipProcess-" + System.currentTimeMillis());
            t.setDaemon(true);
            t.setPriority(Thread.NORM_PRIORITY);
            return t;
        });
        
        startCleanupTask();
    }
    
    public static synchronized DataPackLoadOptimizer getInstance(AkiAsyncPlugin plugin) {
        if (instance == null) {
            instance = new DataPackLoadOptimizer(plugin);
        }
        return instance;
    }
    
    // 支持独立模式：不需要 plugin
    public static synchronized DataPackLoadOptimizer getInstance() {
        if (instance == null) {
            // 尝试从 Bridge 获取配置，如果 Bridge 未初始化则使用默认值
            instance = new DataPackLoadOptimizer(null);
        }
        return instance;
    }
    
    public void updateConfiguration() {
        // 优先从 Bridge 获取配置（支持独立模式）
        org.virgil.akiasync.mixin.bridge.Bridge bridge = 
            org.virgil.akiasync.mixin.bridge.BridgeManager.getBridge();
        
        if (bridge != null) {
            this.optimizationEnabled = bridge.isDataPackOptimizationEnabled();
            this.fileLoadThreads = bridge.getDataPackFileLoadThreads();
            this.zipProcessThreads = bridge.getDataPackZipProcessThreads();
            this.batchSize = bridge.getDataPackBatchSize();
            this.cacheExpirationMs = bridge.getDataPackCacheExpirationMinutes() * 60 * 1000;
            this.debugLogging = bridge.isDataPackDebugEnabled();
        } else if (plugin != null && plugin.getBridge() != null) {
            // 回退到从 plugin 获取（兼容旧代码）
            this.optimizationEnabled = plugin.getBridge().isDataPackOptimizationEnabled();
            this.fileLoadThreads = plugin.getBridge().getDataPackFileLoadThreads();
            this.zipProcessThreads = plugin.getBridge().getDataPackZipProcessThreads();
            this.batchSize = plugin.getBridge().getDataPackBatchSize();
            this.cacheExpirationMs = plugin.getBridge().getDataPackCacheExpirationMinutes() * 60 * 1000;
            this.debugLogging = plugin.getBridge().isDataPackDebugEnabled();
        } else {
            // 使用默认值
            this.optimizationEnabled = true;
            this.fileLoadThreads = 4;
            this.zipProcessThreads = 2;
            this.batchSize = 100;
            this.cacheExpirationMs = 30 * 60 * 1000;
            this.debugLogging = false;
        }
    }
    
    public CompletableFuture<List<FileLoadResult>> optimizeZipFileLoading(
            Path zipPath, 
            List<String> filePaths) {
        
        if (!optimizationEnabled) {
            return loadFilesTraditional(zipPath, filePaths);
        }
        
        long startTime = System.nanoTime();
        
        return CompletableFuture.supplyAsync(() -> {
            try {
                if (debugLogging) {
                    java.util.logging.Logger logger = getLogger();
                    if (logger != null) {
                        logger.info(String.format(
                            "[AkiAsync-DataPack] Starting optimized loading of %d files from %s",
                            filePaths.size(), zipPath.getFileName()
                        ));
                    }
                }
                
                CachedFileSystem cachedFs = getOrCreateFileSystem(zipPath);
                
                List<FileLoadResult> results = processBatchedFiles(cachedFs.fileSystem, filePaths);
                
                long endTime = System.nanoTime();
                long loadTime = (endTime - startTime) / 1_000_000;
                
                totalFilesProcessed.addAndGet(filePaths.size());
                totalLoadTime.addAndGet(loadTime);
                
                if (debugLogging) {
                    java.util.logging.Logger logger = getLogger();
                    if (logger != null) {
                        logger.info(String.format(
                            "[AkiAsync-DataPack] Completed loading %d files in %dms (avg: %.2fms/file)",
                            filePaths.size(), loadTime, (double) loadTime / filePaths.size()
                        ));
                    }
                }
                
                return results;
                
            } catch (Exception e) {
                java.util.logging.Logger logger = getLogger();
                if (logger != null) {
                    logger.warning("[AkiAsync-DataPack] Error in optimized file loading: " + e.getMessage());
                }
                return loadFilesTraditional(zipPath, filePaths).join();
            }
        }, zipProcessExecutor);
    }
    
    private List<FileLoadResult> processBatchedFiles(FileSystem fileSystem, List<String> filePaths) {
        List<CompletableFuture<FileLoadResult>> futures = new ArrayList<>();
        
        for (int i = 0; i < filePaths.size(); i += batchSize) {
            int endIndex = Math.min(i + batchSize, filePaths.size());
            List<String> batch = filePaths.subList(i, endIndex);
            
            CompletableFuture<FileLoadResult> batchFuture = CompletableFuture.supplyAsync(() -> {
                return processBatch(fileSystem, batch);
            }, fileLoadExecutor);
            
            futures.add(batchFuture);
        }
        
        List<FileLoadResult> allResults = new ArrayList<>();
        for (CompletableFuture<FileLoadResult> future : futures) {
            try {
                FileLoadResult batchResult = future.join();
                allResults.add(batchResult);
            } catch (Exception e) {
                java.util.logging.Logger logger = getLogger();
                if (logger != null) {
                    logger.warning("[AkiAsync-DataPack] Batch processing error: " + e.getMessage());
                }
            }
        }
        
        return allResults;
    }
    
    private FileLoadResult processBatch(FileSystem fileSystem, List<String> filePaths) {
        FileLoadResult result = new FileLoadResult();
        
        for (String filePath : filePaths) {
            try {
                String cacheKey = generateCacheKey(fileSystem.toString(), filePath);
                CachedFileEntry cachedEntry = fileCache.get(cacheKey);
                
                if (cachedEntry != null && !cachedEntry.isExpired(cacheExpirationMs)) {
                    result.addCachedFile(filePath, cachedEntry.content);
                    cacheHits.incrementAndGet();
                    continue;
                }
                
                Path path = fileSystem.getPath(filePath);
                if (java.nio.file.Files.exists(path)) {
                    byte[] content = java.nio.file.Files.readAllBytes(path);
                    
                    fileCache.put(cacheKey, new CachedFileEntry(content, System.currentTimeMillis()));
                    
                    result.addLoadedFile(filePath, content);
                } else {
                    result.addMissingFile(filePath);
                }
                
            } catch (Exception e) {
                result.addErrorFile(filePath, e);
            }
        }
        
        return result;
    }
    
    private CachedFileSystem getOrCreateFileSystem(Path zipPath) throws IOException {
        String cacheKey = zipPath.toString();
        CachedFileSystem cached = fileSystemCache.get(cacheKey);
        
        if (cached != null && !cached.isExpired(cacheExpirationMs)) {
            return cached;
        }
        
        FileSystem fileSystem = java.nio.file.FileSystems.newFileSystem(zipPath, (ClassLoader) null);
        CachedFileSystem cachedFs = new CachedFileSystem(fileSystem, System.currentTimeMillis());
        
        fileSystemCache.put(cacheKey, cachedFs);
        
        if (debugLogging) {
            java.util.logging.Logger logger = getLogger();
            if (logger != null) {
                logger.info("[AkiAsync-DataPack] Created new FileSystem for: " + zipPath.getFileName());
            }
        }
        
        return cachedFs;
    }
    
    private CompletableFuture<List<FileLoadResult>> loadFilesTraditional(Path zipPath, List<String> filePaths) {
        return CompletableFuture.supplyAsync(() -> {
            List<FileLoadResult> results = new ArrayList<>();
            FileLoadResult result = new FileLoadResult();
            
            try (FileSystem fileSystem = java.nio.file.FileSystems.newFileSystem(zipPath, (ClassLoader) null)) {
                for (String filePath : filePaths) {
                    try {
                        Path path = fileSystem.getPath(filePath);
                        if (java.nio.file.Files.exists(path)) {
                            byte[] content = java.nio.file.Files.readAllBytes(path);
                            result.addLoadedFile(filePath, content);
                        } else {
                            result.addMissingFile(filePath);
                        }
                    } catch (Exception e) {
                        result.addErrorFile(filePath, e);
                    }
                }
            } catch (IOException e) {
                java.util.logging.Logger logger = getLogger();
                if (logger != null) {
                    logger.severe("[AkiAsync-DataPack] Failed to open zip file: " + e.getMessage());
                }
            }
            
            results.add(result);
            return results;
        }, fileLoadExecutor);
    }
    
    private String generateCacheKey(String fileSystemId, String filePath) {
        return fileSystemId + ":" + filePath;
    }
    
    private void startCleanupTask() {
        Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "AkiAsync-DataPack-Cleanup");
            t.setDaemon(true);
            return t;
        }).scheduleAtFixedRate(this::performCleanup, 10, 10, java.util.concurrent.TimeUnit.MINUTES);
    }
    
    private void performCleanup() {
        java.util.concurrent.atomic.AtomicInteger removedFiles = new java.util.concurrent.atomic.AtomicInteger(0);
        java.util.concurrent.atomic.AtomicInteger removedFileSystems = new java.util.concurrent.atomic.AtomicInteger(0);
        
        fileCache.entrySet().removeIf(entry -> {
            if (entry.getValue().isExpired(cacheExpirationMs)) {
                removedFiles.incrementAndGet();
                return true;
            }
            return false;
        });
        
        fileSystemCache.entrySet().removeIf(entry -> {
            if (entry.getValue().isExpired(cacheExpirationMs)) {
                try {
                    entry.getValue().fileSystem.close();
                } catch (IOException e) {
                }
                removedFileSystems.incrementAndGet();
                return true;
            }
            return false;
        });
        
        int filesRemoved = removedFiles.get();
        int systemsRemoved = removedFileSystems.get();
        if (debugLogging && (filesRemoved > 0 || systemsRemoved > 0)) {
            java.util.logging.Logger logger = getLogger();
            if (logger != null) {
                logger.info(String.format(
                    "[AkiAsync-DataPack] Cleanup completed: removed %d file entries, %d file systems",
                    filesRemoved, systemsRemoved
                ));
            }
        }
    }
    
    public DataPackStatistics getStatistics() {
        return new DataPackStatistics(
            totalFilesProcessed.get(),
            totalLoadTime.get(),
            cacheHits.get(),
            fileCache.size(),
            fileSystemCache.size()
        );
    }
    
    public void clearCache() {
        fileCache.clear();
        
        for (CachedFileSystem cached : fileSystemCache.values()) {
            try {
                cached.fileSystem.close();
            } catch (IOException e) {
            }
        }
        fileSystemCache.clear();
        
        if (debugLogging) {
            java.util.logging.Logger logger = getLogger();
            if (logger != null) {
                logger.info("[AkiAsync-DataPack] All caches cleared");
            }
        }
    }
    
    public void shutdown() {
        fileLoadExecutor.shutdown();
        zipProcessExecutor.shutdown();
        
        try {
            if (!fileLoadExecutor.awaitTermination(5, java.util.concurrent.TimeUnit.SECONDS)) {
                fileLoadExecutor.shutdownNow();
            }
            if (!zipProcessExecutor.awaitTermination(5, java.util.concurrent.TimeUnit.SECONDS)) {
                zipProcessExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            fileLoadExecutor.shutdownNow();
            zipProcessExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
        
        clearCache();
        instance = null;
    }
    
    private java.util.logging.Logger getLogger() {
        if (plugin != null) {
            return plugin.getLogger();
        }
        // 在独立模式下，尝试从 AkiAsyncInitializer 获取 logger
        try {
            org.virgil.akiasync.bootstrap.AkiAsyncInitializer init = 
                org.virgil.akiasync.bootstrap.AkiAsyncInitializer.getInstance();
            return init.getLogger();
        } catch (Exception e) {
            return null;
        }
    }
    
    private static class CachedFileSystem {
        final FileSystem fileSystem;
        final long timestamp;
        
        CachedFileSystem(FileSystem fileSystem, long timestamp) {
            this.fileSystem = fileSystem;
            this.timestamp = timestamp;
        }
        
        boolean isExpired(long expirationMs) {
            return System.currentTimeMillis() - timestamp > expirationMs;
        }
    }
    
    private static class CachedFileEntry {
        final byte[] content;
        final long timestamp;
        
        CachedFileEntry(byte[] content, long timestamp) {
            this.content = content;
            this.timestamp = timestamp;
        }
        
        boolean isExpired(long expirationMs) {
            return System.currentTimeMillis() - timestamp > expirationMs;
        }
    }
    
    public static class FileLoadResult {
        private final Map<String, byte[]> loadedFiles = new ConcurrentHashMap<>();
        private final Map<String, byte[]> cachedFiles = new ConcurrentHashMap<>();
        private final List<String> missingFiles = new ArrayList<>();
        private final Map<String, Exception> errorFiles = new ConcurrentHashMap<>();
        
        public void addLoadedFile(String path, byte[] content) {
            loadedFiles.put(path, content);
        }
        
        public void addCachedFile(String path, byte[] content) {
            cachedFiles.put(path, content);
        }
        
        public void addMissingFile(String path) {
            synchronized (missingFiles) {
                missingFiles.add(path);
            }
        }
        
        public void addErrorFile(String path, Exception error) {
            errorFiles.put(path, error);
        }
        
        public Map<String, byte[]> getLoadedFiles() { return loadedFiles; }
        public Map<String, byte[]> getCachedFiles() { return cachedFiles; }
        public List<String> getMissingFiles() { return missingFiles; }
        public Map<String, Exception> getErrorFiles() { return errorFiles; }
        
        public int getTotalFiles() {
            return loadedFiles.size() + cachedFiles.size() + missingFiles.size() + errorFiles.size();
        }
    }
    
    public static class DataPackStatistics {
        public final long totalFilesProcessed;
        public final long totalLoadTimeMs;
        public final long cacheHits;
        public final int fileCacheSize;
        public final int fileSystemCacheSize;
        public final double averageLoadTimeMs;
        public final double cacheHitRate;
        
        DataPackStatistics(long totalFilesProcessed, long totalLoadTimeMs, long cacheHits, 
                          int fileCacheSize, int fileSystemCacheSize) {
            this.totalFilesProcessed = totalFilesProcessed;
            this.totalLoadTimeMs = totalLoadTimeMs;
            this.cacheHits = cacheHits;
            this.fileCacheSize = fileCacheSize;
            this.fileSystemCacheSize = fileSystemCacheSize;
            this.averageLoadTimeMs = totalFilesProcessed > 0 ? (double) totalLoadTimeMs / totalFilesProcessed : 0.0;
            this.cacheHitRate = totalFilesProcessed > 0 ? (double) cacheHits / totalFilesProcessed * 100.0 : 0.0;
        }
        
        @Override
        public String toString() {
            return String.format(
                "DataPackStats[files=%d, loadTime=%dms, avg=%.2fms/file, cacheHits=%d(%.1f%%), fileCache=%d, fsCache=%d]",
                totalFilesProcessed, totalLoadTimeMs, averageLoadTimeMs, 
                cacheHits, cacheHitRate, fileCacheSize, fileSystemCacheSize
            );
        }
    }
}
