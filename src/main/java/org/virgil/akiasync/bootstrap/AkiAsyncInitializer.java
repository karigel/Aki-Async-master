package org.virgil.akiasync.bootstrap;

import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.logging.Logger;
import org.bukkit.configuration.file.YamlConfiguration;
import org.virgil.akiasync.bridge.AkiAsyncBridge;
import org.virgil.akiasync.config.ConfigManager;

/**
 * 独立的 AkiAsync 初始化器，不依赖 JavaPlugin 生命周期
 */
public final class AkiAsyncInitializer {
    
    private static AkiAsyncInitializer instance;
    private static volatile boolean initialized = false;
    
    private ConfigManager configManager;
    // 注意：cacheManager 在独立模式下暂不使用
    // private CacheManager cacheManager;
    private AkiAsyncBridge bridge;
    private File dataFolder;
    private Logger logger;
    private YamlConfiguration config;
    
    // Executor 引用，用于关闭
    private ThreadPoolExecutor generalExecutor;
    private ThreadPoolExecutor lightingExecutor;
    private ExecutorService tntExecutor;
    private ExecutorService chunkTickExecutor;
    private ExecutorService villagerBreedExecutor;
    private ExecutorService brainExecutor;
    
    private AkiAsyncInitializer() {
    }
    
    public static AkiAsyncInitializer getInstance() {
        if (instance == null) {
            instance = new AkiAsyncInitializer();
        }
        return instance;
    }
    
    public static void initialize(final Logger logger) {
        if (initialized) {
            logger.info("[AkiAsync/Ignite] 已初始化，跳过重复初始化");
            return;
        }
        
        synchronized (AkiAsyncInitializer.class) {
            if (initialized) {
                return;
            }
            
            final AkiAsyncInitializer init = getInstance();
            init.logger = logger;
            
            // 检测配置文件应该输出到哪里（mods/AkiAsync）
            init.dataFolder = detectDataFolder(logger);
            
            if (!init.dataFolder.exists()) {
                init.dataFolder.mkdirs();
            }
            
            logger.info("[AkiAsync/Ignite] 配置文件目录: " + init.dataFolder.getAbsolutePath());
            
            logger.info("[AkiAsync/Ignite] ====== 开始初始化 AkiAsync ======");
            
            // 加载配置
            init.loadConfiguration();
            
            // 创建所有 Executor（与原版 Aki-Async 保持一致的行为）
            logger.info("[AkiAsync/Ignite] 正在创建 Executor 线程池...");
            init.createExecutors();
            
            // 创建 Bridge 并传入所有 Executor
            init.bridge = new AkiAsyncBridge(
                init.configManager,
                init.generalExecutor,
                init.lightingExecutor,
                init.tntExecutor,
                init.chunkTickExecutor,
                init.villagerBreedExecutor,
                init.brainExecutor
            );
            
            try {
                org.virgil.akiasync.mixin.bridge.BridgeManager.setBridge(init.bridge);
                logger.info("[AkiAsync] Bridge registered successfully with all executors (standalone mode)");
            } catch (IllegalStateException e) {
                // Bridge 可能已经被设置（例如服务器重载时），这是正常的
                logger.info("[AkiAsync] Bridge already registered, continuing...");
            } catch (Exception e) {
                logger.warning("[AkiAsync] Failed to register bridge: " + e.getMessage());
            }
            
            // 设置 AsyncBrainExecutor 的 executor
            if (init.brainExecutor != null) {
                org.virgil.akiasync.mixin.brain.core.AsyncBrainExecutor.setExecutor(init.brainExecutor);
                logger.info("[AkiAsync] AsyncBrainExecutor configured");
            }
            org.virgil.akiasync.util.VirtualEntityDetector.setLogger(logger, init.configManager.isDebugLoggingEnabled());
            
            // 初始化 ViaVersion 兼容层（延迟到插件加载后）
            // ViaVersionCompat.initialize() 会在 CraftServerLoadPluginsMixin 中调用
            
            // 初始化各种优化模块
            if (init.configManager.isTNTOptimizationEnabled()) {
                org.virgil.akiasync.mixin.async.TNTThreadPool.init(init.configManager.getTNTThreads());
                logger.info("[AkiAsync] TNT explosion optimization enabled with " + init.configManager.getTNTThreads() + " threads");
                
                // 注意：在 Ignite/Mixin 模式下，插件可能还没完全加载
                // 保护插件检测会在首次 TNT 爆炸时自动执行（延迟初始化）
                logger.info("[AkiAsync] Land protection check will be performed when plugins are ready.");
            }
            
            if (init.configManager.isAsyncVillagerBreedEnabled()) {
                logger.info("[AkiAsync] Villager breed async check enabled with " + init.configManager.getVillagerBreedThreads() + " threads");
            }
            
            if (init.configManager.isStructureLocationAsyncEnabled()) {
                try {
                    org.virgil.akiasync.mixin.async.StructureLocatorBridge.initialize();
                    // 注意：OptimizedStructureLocator 需要 plugin，暂时跳过以避免 NPE
                    // TODO: 修复 StructureCacheManager 使其支持独立模式
                    // org.virgil.akiasync.async.structure.OptimizedStructureLocator.initialize(null);
                    logger.info("[AkiAsync] Async structure location enabled with " + init.configManager.getStructureLocationThreads() + " threads");
                    if (init.configManager.isStructureAlgorithmOptimizationEnabled()) {
                        logger.info("[AkiAsync] Structure search algorithm optimization enabled (" + init.configManager.getStructureSearchPattern() + " pattern)");
                    }
                } catch (Exception e) {
                    logger.warning("[AkiAsync] 结构定位优化初始化失败（需要 plugin 支持）: " + e.getMessage());
                }
            }
            
            if (init.configManager.isDataPackOptimizationEnabled()) {
                try {
                    // DataPackLoadOptimizer 现在支持独立模式，可以从 Bridge 获取配置
                    org.virgil.akiasync.async.datapack.DataPackLoadOptimizer.getInstance();
                    logger.info("[AkiAsync] DataPack loading optimization enabled with " + 
                        init.configManager.getDataPackFileLoadThreads() + " file threads, " + 
                        init.configManager.getDataPackZipProcessThreads() + " zip threads");
                } catch (Exception e) {
                    logger.warning("[AkiAsync] DataPack 优化初始化失败: " + e.getMessage());
                    e.printStackTrace();
                }
            }
            
            // Bridge 已设置，现在可以验证配置（仅在 Bridge 已初始化时）
            // 延迟一小段时间确保 Bridge 完全设置好
            try {
                Thread.sleep(50); // 给 Bridge 设置一点时间
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            
            if (org.virgil.akiasync.mixin.bridge.BridgeManager.isBridgeInitialized()) {
                try {
                    org.virgil.akiasync.mixin.bridge.BridgeManager.validateAndDisplayConfigurations();
                } catch (Exception e) {
                    logger.warning("[AkiAsync] 配置验证时出错（非致命）: " + e.getMessage());
                }
            } else {
                logger.info("[AkiAsync] Bridge 未初始化，跳过配置验证（这是正常的，Mixin 优化仍然可以工作）");
            }
            
            logger.info("========================================");
            logger.info("  AkiAsync - Async Optimization Mod");
            logger.info("========================================");
            logger.info("Version: 3.2.8-SNAPSHOT");
            logger.info("Commands: /aki-reload | /aki-debug | /aki-version");
            logger.info("");
            logger.info("[+] Core Features:");
            logger.info("  [+] Async Entity Tracker: " + (init.configManager.isEntityTrackerEnabled() ? "Enabled" : "Disabled"));
            logger.info("  [+] Async Mob Spawning: " + (init.configManager.isMobSpawningEnabled() ? "Enabled" : "Disabled"));
            logger.info("  [+] Entity Tick Parallel: " + (init.configManager.isEntityTickParallel() ? "Enabled" : "Disabled") + 
                " (" + init.configManager.getEntityTickThreads() + " threads)");
            logger.info("  [+] Async Lighting: " + (init.configManager.isAsyncLightingEnabled() ? "Enabled" : "Disabled") + 
                " (" + init.configManager.getLightingThreadPoolSize() + " threads)");
            logger.info("  [+] TNT Explosion Optimization: " + (init.configManager.isTNTOptimizationEnabled() ? "Enabled" : "Disabled") + 
                " (" + init.configManager.getTNTThreads() + " threads)");
            logger.info("  [+] Async Pathfinding: " + (init.configManager.isAsyncPathfindingEnabled() ? "Enabled" : "Disabled"));
            logger.info("");
            logger.info("[*] Performance Settings:");
            logger.info("  [*] Thread Pool Size: " + init.configManager.getThreadPoolSize());
            logger.info("  [*] Max Entities/Chunk: " + init.configManager.getMaxEntitiesPerChunk());
            logger.info("  [*] Brain Throttle: " + (init.configManager.isBrainThrottleEnabled() ? "Enabled" : "Disabled") + 
                " (" + init.configManager.getBrainThrottleInterval() + " ticks)");
            logger.info("");
            logger.info("[#] All Executors Initialized:");
            logger.info("  [#] General: " + init.configManager.getThreadPoolSize() + " threads");
            logger.info("  [#] Lighting: " + init.configManager.getLightingThreadPoolSize() + " threads");
            logger.info("  [#] TNT: " + init.configManager.getTNTThreads() + " threads");
            logger.info("  [#] ChunkTick: 4 threads");
            logger.info("  [#] VillagerBreed: " + init.configManager.getVillagerBreedThreads() + " threads");
            logger.info("  [#] Brain: " + (init.configManager.getThreadPoolSize() / 2) + " threads");
            logger.info("========================================");
            logger.info("Mod enabled successfully! All features available.");
            
            initialized = true;
        }
    }
    
    private void loadConfiguration() {
        // 复制所有配置文件（如果不存在）
        copyResourceIfNotExists("config.yml");
        copyResourceIfNotExists("entities.yml");
        copyResourceIfNotExists("throttling.yml");
        
        // 加载主配置文件
        final File configFile = new File(dataFolder, "config.yml");
        this.config = YamlConfiguration.loadConfiguration(configFile);
        this.configManager = new ConfigManager(null);
        this.configManager.setConfig(config);
        this.configManager.setLogger(logger); // 设置 Logger 用于独立模式
        this.configManager.loadConfig();
        logger.info("[AkiAsync] 配置文件已加载");
    }
    
    /**
     * 从 JAR 复制资源文件到数据文件夹（如果不存在）
     * Copy resource file from JAR to data folder if it doesn't exist
     */
    private void copyResourceIfNotExists(String resourceName) {
        final File targetFile = new File(dataFolder, resourceName);
        
        if (targetFile.exists()) {
            return; // 文件已存在，跳过
        }
        
        logger.info("[AkiAsync] 配置文件 " + resourceName + " 不存在，正在从 JAR 复制默认配置...");
        try {
            final java.net.URL location = AkiAsyncInitializer.class.getProtectionDomain().getCodeSource().getLocation();
            final Path jarPath = Paths.get(location.toURI());
            
            try (JarFile jar = new JarFile(jarPath.toFile())) {
                final JarEntry entry = jar.getJarEntry(resourceName);
                if (entry != null) {
                    try (InputStream stream = jar.getInputStream(entry)) {
                        Files.copy(stream, targetFile.toPath());
                        logger.info("[AkiAsync] 默认配置文件已复制到: " + targetFile.getAbsolutePath());
                    }
                } else {
                    logger.warning("[AkiAsync] JAR 中未找到资源文件: " + resourceName);
                }
            }
        } catch (Exception ex) {
            logger.severe("[AkiAsync] 无法复制配置文件 " + resourceName + ": " + ex.getMessage());
            ex.printStackTrace();
        }
    }
    
    /**
     * 检测数据文件夹位置
     * 检测 JAR 是否在 mods 文件夹中，如果是则使用 mods/AkiAsync
     * Detect data folder location
     * Check if JAR is in mods folder, if so use mods/AkiAsync
     */
    private static File detectDataFolder(Logger logger) {
        try {
            // 获取当前类的 JAR 文件位置
            final java.net.URL location = AkiAsyncInitializer.class.getProtectionDomain().getCodeSource().getLocation();
            if (location != null) {
                final Path jarPath = Paths.get(location.toURI());
                final File jarFile = jarPath.toFile();
                
                if (jarFile.exists() && jarFile.isFile()) {
                    // 获取 JAR 文件的父目录
                    final File parentDir = jarFile.getParentFile();
                    if (parentDir != null) {
                        final String parentName = parentDir.getName().toLowerCase();
                        
                        // 检查是否在 mods 文件夹中
                        if (parentName.equals("mods")) {
                            logger.info("[AkiAsync/Ignite] 检测到 JAR 在 mods 文件夹中，配置文件将输出到 mods/AkiAsync");
                            return new File(parentDir, "AkiAsync");
                        }
                    }
                }
            }
        } catch (Exception e) {
            logger.warning("[AkiAsync/Ignite] 无法检测 JAR 位置: " + e.getMessage());
        }
        
        // 默认使用 mods 文件夹（因为这是 Ignite mod loader）
        logger.info("[AkiAsync/Ignite] 使用默认配置文件路径: mods/AkiAsync");
        return new File("mods/AkiAsync");
    }
    
    /**
     * 获取数据文件夹路径
     * Get data folder path
     */
    public File getDataFolder() {
        return dataFolder;
    }
    
    public ConfigManager getConfigManager() {
        return configManager;
    }
    
    public AkiAsyncBridge getBridge() {
        return bridge;
    }
    
    public Logger getLogger() {
        return logger;
    }
    
    /**
     * 检查是否已初始化
     * Check if already initialized
     */
    public static boolean isInitialized() {
        return initialized;
    }
    
    /**
     * 创建所有 Executor 线程池（与原版 Aki-Async 保持一致的行为）
     * Create all executor thread pools (consistent with original Aki-Async behavior)
     */
    private void createExecutors() {
        int threadPoolSize = configManager.getThreadPoolSize();
        int maxQueueSize = configManager.getMaxQueueSize();
        
        // 1. General Executor（通用线程池）
        ThreadFactory generalThreadFactory = new ThreadFactory() {
            private final AtomicInteger threadNumber = new AtomicInteger(1);
            @Override
            public Thread newThread(Runnable r) {
                Thread thread = new Thread(r, "AkiAsync-Worker-" + threadNumber.getAndIncrement());
                thread.setDaemon(true);
                thread.setPriority(Thread.NORM_PRIORITY);
                return thread;
            }
        };
        BlockingQueue<Runnable> generalWorkQueue = new LinkedBlockingQueue<>(maxQueueSize);
        this.generalExecutor = new ThreadPoolExecutor(
            threadPoolSize,
            threadPoolSize,
            60L, TimeUnit.SECONDS,
            generalWorkQueue,
            generalThreadFactory,
            new ThreadPoolExecutor.CallerRunsPolicy() // 不破坏原版特性：队列满时在调用线程执行
        );
        int generalPrestarted = generalExecutor.prestartAllCoreThreads();
        logger.info("[AkiAsync] General executor initialized: " + threadPoolSize + " threads (prestarted: " + generalPrestarted + ")");
        
        // 2. Lighting Executor（光照线程池）
        int lightingThreads = configManager.getLightingThreadPoolSize();
        ThreadFactory lightingThreadFactory = new ThreadFactory() {
            private final AtomicInteger threadNumber = new AtomicInteger(1);
            @Override
            public Thread newThread(Runnable r) {
                Thread thread = new Thread(r, "AkiAsync-Lighting-" + threadNumber.getAndIncrement());
                thread.setDaemon(true);
                thread.setPriority(Thread.NORM_PRIORITY - 1);
                return thread;
            }
        };
        this.lightingExecutor = new ThreadPoolExecutor(
            lightingThreads,
            lightingThreads,
            60L, TimeUnit.SECONDS,
            new LinkedBlockingQueue<>(500),
            lightingThreadFactory,
            new ThreadPoolExecutor.CallerRunsPolicy() // 不破坏原版特性
        );
        int lightingPrestarted = lightingExecutor.prestartAllCoreThreads();
        logger.info("[AkiAsync] Lighting executor initialized: " + lightingThreads + " threads (prestarted: " + lightingPrestarted + ")");
        
        // 3. TNT Executor（TNT 爆炸线程池）
        int tntThreads = configManager.getTNTThreads();
        ThreadFactory tntThreadFactory = new ThreadFactory() {
            private final AtomicInteger threadNumber = new AtomicInteger(1);
            @Override
            public Thread newThread(Runnable r) {
                Thread thread = new Thread(r, "AkiAsync-TNT-" + threadNumber.getAndIncrement());
                thread.setDaemon(true);
                thread.setPriority(Thread.NORM_PRIORITY - 1);
                return thread;
            }
        };
        this.tntExecutor = new ThreadPoolExecutor(
            tntThreads,
            tntThreads,
            60L, TimeUnit.SECONDS,
            new LinkedBlockingQueue<>(1024),
            tntThreadFactory,
            new ThreadPoolExecutor.CallerRunsPolicy() // 不破坏原版特性
        );
        logger.info("[AkiAsync] TNT executor initialized: " + tntThreads + " threads");
        
        // 4. ChunkTick Executor（区块 Tick 线程池）
        ThreadFactory chunkTickThreadFactory = new ThreadFactory() {
            private final AtomicInteger threadNumber = new AtomicInteger(1);
            @Override
            public Thread newThread(Runnable r) {
                Thread thread = new Thread(r, "AkiAsync-ChunkTick-" + threadNumber.getAndIncrement());
                thread.setDaemon(true);
                thread.setPriority(Thread.NORM_PRIORITY - 1);
                return thread;
            }
        };
        this.chunkTickExecutor = new ThreadPoolExecutor(
            4, 4,
            60L, TimeUnit.SECONDS,
            new LinkedBlockingQueue<>(1024),
            chunkTickThreadFactory,
            new ThreadPoolExecutor.CallerRunsPolicy() // 不破坏原版特性
        );
        logger.info("[AkiAsync] ChunkTick executor initialized: 4 threads");
        
        // 5. VillagerBreed Executor（村民繁殖线程池）
        int villagerBreedThreads = configManager.getVillagerBreedThreads();
        ThreadFactory villagerBreedThreadFactory = new ThreadFactory() {
            private final AtomicInteger threadNumber = new AtomicInteger(1);
            @Override
            public Thread newThread(Runnable r) {
                Thread thread = new Thread(r, "AkiAsync-VillagerBreed-" + threadNumber.getAndIncrement());
                thread.setDaemon(true);
                thread.setPriority(Thread.NORM_PRIORITY - 1);
                return thread;
            }
        };
        this.villagerBreedExecutor = new ThreadPoolExecutor(
            villagerBreedThreads,
            villagerBreedThreads,
            60L, TimeUnit.SECONDS,
            new LinkedBlockingQueue<>(1024),
            villagerBreedThreadFactory,
            new ThreadPoolExecutor.CallerRunsPolicy() // 不破坏原版特性
        );
        logger.info("[AkiAsync] VillagerBreed executor initialized: " + villagerBreedThreads + " threads");
        
        // 6. Brain Executor（AI 大脑线程池）
        int brainThreads = threadPoolSize / 2;
        if (brainThreads < 1) brainThreads = 1;
        ThreadFactory brainThreadFactory = new ThreadFactory() {
            private final AtomicInteger threadNumber = new AtomicInteger(1);
            @Override
            public Thread newThread(Runnable r) {
                Thread thread = new Thread(r, "AkiAsync-Brain-" + threadNumber.getAndIncrement());
                thread.setDaemon(true);
                thread.setPriority(Thread.NORM_PRIORITY - 1);
                return thread;
            }
        };
        this.brainExecutor = new ThreadPoolExecutor(
            brainThreads,
            brainThreads,
            60L, TimeUnit.SECONDS,
            new LinkedBlockingQueue<>(1024),
            brainThreadFactory,
            new ThreadPoolExecutor.CallerRunsPolicy() // 不破坏原版特性
        );
        logger.info("[AkiAsync] Brain executor initialized: " + brainThreads + " threads");
    }
    
    /**
     * 关闭所有 Executor（用于服务器关闭时）
     * Shutdown all executors (for server shutdown)
     */
    public void shutdownExecutors() {
        if (logger != null) {
            logger.info("[AkiAsync] Shutting down async executors...");
        }
        
        if (generalExecutor != null) {
            generalExecutor.shutdown();
        }
        if (lightingExecutor != null) {
            lightingExecutor.shutdown();
        }
        if (tntExecutor != null) {
            tntExecutor.shutdown();
        }
        if (chunkTickExecutor != null) {
            chunkTickExecutor.shutdown();
        }
        if (villagerBreedExecutor != null) {
            villagerBreedExecutor.shutdown();
        }
        if (brainExecutor != null) {
            brainExecutor.shutdown();
        }
        
        try {
            if (generalExecutor != null && !generalExecutor.awaitTermination(10, TimeUnit.SECONDS)) {
                if (logger != null) {
                    logger.warning("[AkiAsync] General executor did not terminate in time, forcing shutdown...");
                }
                generalExecutor.shutdownNow();
            }
            if (lightingExecutor != null && !lightingExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                if (logger != null) {
                    logger.warning("[AkiAsync] Lighting executor did not terminate in time, forcing shutdown...");
                }
                lightingExecutor.shutdownNow();
            }
            if (tntExecutor != null && !tntExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                if (logger != null) {
                    logger.warning("[AkiAsync] TNT executor did not terminate in time, forcing shutdown...");
                }
                tntExecutor.shutdownNow();
            }
            if (chunkTickExecutor != null && !chunkTickExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                if (logger != null) {
                    logger.warning("[AkiAsync] ChunkTick executor did not terminate in time, forcing shutdown...");
                }
                chunkTickExecutor.shutdownNow();
            }
            if (villagerBreedExecutor != null && !villagerBreedExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                if (logger != null) {
                    logger.warning("[AkiAsync] VillagerBreed executor did not terminate in time, forcing shutdown...");
                }
                villagerBreedExecutor.shutdownNow();
            }
            if (brainExecutor != null && !brainExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                if (logger != null) {
                    logger.warning("[AkiAsync] Brain executor did not terminate in time, forcing shutdown...");
                }
                brainExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            if (generalExecutor != null) generalExecutor.shutdownNow();
            if (lightingExecutor != null) lightingExecutor.shutdownNow();
            if (tntExecutor != null) tntExecutor.shutdownNow();
            if (chunkTickExecutor != null) chunkTickExecutor.shutdownNow();
            if (villagerBreedExecutor != null) villagerBreedExecutor.shutdownNow();
            if (brainExecutor != null) brainExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
        
        if (logger != null) {
            logger.info("[AkiAsync] Async executors shut down successfully");
        }
    }
    
    public void reloadConfig() {
        logger.info("[AkiAsync] 重新加载配置...");
        
        // 重新加载配置文件
        loadConfiguration();
        
        // 更新 Bridge 的配置引用
        if (bridge != null) {
            try {
                bridge.updateConfiguration(configManager);
                logger.info("[AkiAsync] Bridge 配置已更新");
            } catch (Exception e) {
                logger.warning("[AkiAsync] 无法更新 Bridge 配置: " + e.getMessage());
            }
        }
        
        // 更新其他组件的配置
        org.virgil.akiasync.util.VirtualEntityDetector.setLogger(logger, configManager.isDebugLoggingEnabled());
        
        // 重新初始化异步路径查找处理器（如果启用）
        if (configManager.isAsyncPathfindingEnabled()) {
            try {
                org.virgil.akiasync.mixin.pathfinding.AsyncPathProcessor processor = 
                    org.virgil.akiasync.mixin.pathfinding.AsyncPathProcessor.getInstance();
                if (processor != null && bridge != null) {
                    processor.updateConfiguration(bridge);
                    logger.info("[AkiAsync] 异步路径查找配置已更新");
                }
            } catch (Exception e) {
                logger.warning("[AkiAsync] 无法更新异步路径查找配置: " + e.getMessage());
            }
        }
        
        logger.info("[AkiAsync] 配置重载完成");
    }
}

