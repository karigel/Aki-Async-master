package org.virgil.akiasync;

/**
 * ========================================
 * 遗留代码 - Legacy Code
 * ========================================
 * 
 * 此类是原始 Aki-Async 的 Bukkit 插件入口点。
 * 在 Ignite/Mixin 模式下，此类不会被使用。
 * 
 * This class is the original Aki-Async Bukkit plugin entry point.
 * In Ignite/Mixin mode, this class is NOT used.
 * 
 * 实际初始化由以下类完成 / Actual initialization is done by:
 * - {@link org.virgil.akiasync.bootstrap.AkiAsyncInitializer}
 * - {@link org.virgil.akiasync.mixin.mixins.bootstrap.CraftServerLoadPluginsMixin}
 * 
 * 保留此类是为了：/ This class is kept for:
 * 1. 保持与原上游代码的兼容性 / Compatibility with original upstream code
 * 2. 为依赖此类的辅助组件提供类型定义 / Type definitions for auxiliary components
 * 
 * @deprecated 使用 Ignite 模式，不要作为 Bukkit 插件加载
 *             Use Ignite mode, do not load as Bukkit plugin
 */
import org.bukkit.plugin.java.JavaPlugin;
import org.virgil.akiasync.bridge.AkiAsyncBridge;
import org.virgil.akiasync.cache.CacheManager;
import org.virgil.akiasync.command.DebugCommand;
import org.virgil.akiasync.command.ReloadCommand;
import org.virgil.akiasync.command.VersionCommand;
import org.virgil.akiasync.config.ConfigManager;
import org.virgil.akiasync.executor.AsyncExecutorManager;
import org.virgil.akiasync.listener.ConfigReloadListener;
import org.virgil.akiasync.mixin.bridge.BridgeManager;
import org.virgil.akiasync.throttling.EntityThrottlingManager;

@SuppressWarnings("unused")
public final class AkiAsyncPlugin extends JavaPlugin {
    
    private static AkiAsyncPlugin instance;
    private ConfigManager configManager;
    private AsyncExecutorManager executorManager;
    private AkiAsyncBridge bridge;
    private CacheManager cacheManager;
    private EntityThrottlingManager throttlingManager;
    private org.virgil.akiasync.network.NetworkOptimizationManager networkOptimizationManager;
    private org.virgil.akiasync.chunk.ChunkLoadPriorityScheduler chunkLoadScheduler;
    private java.util.concurrent.ScheduledExecutorService metricsScheduler;
    private org.virgil.akiasync.compat.VirtualEntityCompatManager virtualEntityCompatManager;
    
    @Override
    public void onEnable() {
        instance = this;
        
        configManager = new ConfigManager(this);
        configManager.loadConfig();
        
        cacheManager = CacheManager.getInstance();
        executorManager = new AsyncExecutorManager(this);
        
        bridge = new AkiAsyncBridge(
            this, 
            executorManager.getExecutorService(), 
            executorManager.getLightingExecutor(), 
            executorManager.getTNTExecutor(),
            executorManager.getChunkTickExecutor(),
            executorManager.getVillagerBreedExecutor(),
            executorManager.getBrainExecutor()
        );
        BridgeManager.setBridge(bridge);
        
        org.virgil.akiasync.util.VirtualEntityDetector.setLogger(getLogger(), configManager.isDebugLoggingEnabled());
        
        // 初始化ViaVersion兼容层 / Initialize ViaVersion compatibility layer
        org.virgil.akiasync.compat.ViaVersionCompat.initialize();
        
        // 初始化虚拟实体兼容管理器 / Initialize Virtual Entity Compatibility Manager
        virtualEntityCompatManager = new org.virgil.akiasync.compat.VirtualEntityCompatManager(this);
        virtualEntityCompatManager.initialize();
        
        if (virtualEntityCompatManager.isEnabled()) {
            java.util.Map<String, Boolean> availability = virtualEntityCompatManager.getPluginAvailability();
            if (!availability.isEmpty()) {
                getLogger().info("[VirtualEntity] Plugin availability status:");
                for (java.util.Map.Entry<String, Boolean> entry : availability.entrySet()) {
                    getLogger().info("  - " + entry.getKey() + ": " +
                        (entry.getValue() ? "Available" : "Not found"));
                }
            }
        }
        
        getLogger().info("[AkiAsync] Bridge registered successfully");
        
        if (configManager.isTNTOptimizationEnabled()) {
            org.virgil.akiasync.mixin.async.TNTThreadPool.init(configManager.getTNTThreads());
            getLogger().info("[AkiAsync] TNT explosion optimization enabled with " + configManager.getTNTThreads() + " threads");
            
            // 打印保护插件兼容状态
            org.virgil.akiasync.util.LandProtectionIntegration.logCompatibilityStatus(getLogger());
        }
        
        if (configManager.isAsyncVillagerBreedEnabled()) {
            getLogger().info("[AkiAsync] Villager breed async check enabled with " + configManager.getVillagerBreedThreads() + " threads");
        }
        
        if (configManager.isStructureLocationAsyncEnabled()) {
            org.virgil.akiasync.mixin.async.StructureLocatorBridge.initialize();
            org.virgil.akiasync.async.structure.OptimizedStructureLocator.initialize(this);
            getLogger().info("[AkiAsync] Async structure location enabled with " + configManager.getStructureLocationThreads() + " threads");
            if (configManager.isStructureAlgorithmOptimizationEnabled()) {
                getLogger().info("[AkiAsync] Structure search algorithm optimization enabled (" + configManager.getStructureSearchPattern() + " pattern)");
            }
        }
        
        if (configManager.isDataPackOptimizationEnabled()) {
            org.virgil.akiasync.async.datapack.DataPackLoadOptimizer.getInstance(this);
            getLogger().info("[AkiAsync] DataPack loading optimization enabled with " + 
                configManager.getDataPackFileLoadThreads() + " file threads, " + 
                configManager.getDataPackZipProcessThreads() + " zip threads");
        }
        
        BridgeManager.validateAndDisplayConfigurations();
        
        getServer().getPluginManager().registerEvents(new ConfigReloadListener(this), this);
        
        registerCommand("aki-reload", new ReloadCommand(this));
        registerCommand("aki-debug", new DebugCommand(this));
        registerCommand("aki-version", new VersionCommand(this));
        registerCommand("aki-teleport-stats", new org.virgil.akiasync.command.TeleportStatsCommand(this));
        
        if (configManager.isNetworkOptimizationEnabled()) {
            networkOptimizationManager = new org.virgil.akiasync.network.NetworkOptimizationManager(this);
            getLogger().info("[AkiAsync] Network optimization enabled");
        }
        
        if (configManager.isFastMovementChunkLoadEnabled()) {
            chunkLoadScheduler = new org.virgil.akiasync.chunk.ChunkLoadPriorityScheduler(configManager);
            chunkLoadScheduler.start();
            getLogger().info("[AkiAsync] Chunk load priority scheduler enabled");
        }
        
        if (configManager.isPerformanceMetricsEnabled()) {
            startCombinedMetrics();
        }
        
        getLogger().info("========================================");
        getLogger().info("  AkiAsync - Async Optimization Plugin");
        getLogger().info("========================================");
        getLogger().info("Version: " + getPluginMeta().getVersion());
        getLogger().info("Commands: /aki-reload | /aki-debug | /aki-version");
        getLogger().info("");
        getLogger().info("[+] Core Features:");
        getLogger().info("  [+] Async Entity Tracker: " + (configManager.isEntityTrackerEnabled() ? "Enabled" : "Disabled"));
        getLogger().info("  [+] Async Mob Spawning: " + (configManager.isMobSpawningEnabled() ? "Enabled" : "Disabled"));
        getLogger().info("  [+] Entity Tick Parallel: " + (configManager.isEntityTickParallel() ? "Enabled" : "Disabled") + " (" + configManager.getEntityTickThreads() + " threads)");
        getLogger().info("  [+] Async Lighting: " + (configManager.isAsyncLightingEnabled() ? "Enabled" : "Disabled") + " (" + configManager.getLightingThreadPoolSize() + " threads)");
        getLogger().info("");
        getLogger().info("[*] Performance Settings:");
        getLogger().info("  [*] Thread Pool Size: " + configManager.getThreadPoolSize());
        getLogger().info("  [*] Max Entities/Chunk: " + configManager.getMaxEntitiesPerChunk());
        getLogger().info("  [*] Brain Throttle: " + (configManager.isBrainThrottleEnabled() ? "Enabled" : "Disabled") + " (" + configManager.getBrainThrottleInterval() + " ticks)");
        getLogger().info("  [*] Update Interval: " + configManager.getUpdateIntervalTicks() + " ticks");
        getLogger().info("");
        getLogger().info("[#] Optimizations:");
        getLogger().info("  [#] ServerCore optimizations: Enabled");
        getLogger().info("  [#] FerriteCore memory optimizations: Enabled");
        getLogger().info("  [#] 16-layer lighting queue: Enabled");
        getLogger().info("========================================");
        getLogger().info("Plugin enabled successfully! Use /aki-version for details");
    }
    
    @Override
    public void onDisable() {
        BridgeManager.clearBridge();
        
        if (metricsScheduler != null) {
            metricsScheduler.shutdownNow();
        }
        
        org.virgil.akiasync.mixin.async.TNTThreadPool.shutdown();
        
        org.virgil.akiasync.mixin.async.villager.VillagerBreedExecutor.shutdown();

        org.virgil.akiasync.mixin.async.StructureLocatorBridge.shutdown();
        
        org.virgil.akiasync.async.structure.OptimizedStructureLocator.shutdown();
        
        org.virgil.akiasync.async.datapack.DataPackLoadOptimizer optimizer = 
            org.virgil.akiasync.async.datapack.DataPackLoadOptimizer.getInstance();
        if (optimizer != null) {
            optimizer.shutdown();
        }
        
        if (networkOptimizationManager != null) {
            networkOptimizationManager.shutdown();
        }
        
        if (chunkLoadScheduler != null) {
            chunkLoadScheduler.shutdown();
        }
        
        if (virtualEntityCompatManager != null) {
            virtualEntityCompatManager.shutdown();
        }
        
        if (executorManager != null) {
            executorManager.shutdown();
        }
        getLogger().info("[AkiAsync] Plugin disabled. All async tasks have been gracefully shut down.");
        instance = null;
    }
    
    private void startCombinedMetrics() {
        metricsScheduler = java.util.concurrent.Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "AkiAsync-Combined-Metrics");
            t.setDaemon(true);
            t.setPriority(Thread.MIN_PRIORITY);
            return t;
        });
        
        final long[] lastGeneralCompleted = {0};
        final long[] lastGeneralTotal = {0};
        
        metricsScheduler.scheduleAtFixedRate(() -> {
            try {
                if (!configManager.isDebugLoggingEnabled()) {
                    return;
                }
                
                java.util.concurrent.ThreadPoolExecutor generalExecutor = 
                    (java.util.concurrent.ThreadPoolExecutor) executorManager.getExecutorService();
                
                long genCompleted = generalExecutor.getCompletedTaskCount();
                long genTotal = generalExecutor.getTaskCount();
                long genCompletedPeriod = genCompleted - lastGeneralCompleted[0];
                long genSubmittedPeriod = genTotal - lastGeneralTotal[0];
                lastGeneralCompleted[0] = genCompleted;
                lastGeneralTotal[0] = genTotal;
                
                double generalThroughput = genCompletedPeriod / 60.0;
                
                getLogger().info("============== AkiAsync Metrics (60s period) ==============");
                getLogger().info(String.format(
                    "[General Pool] Submitted: %d | Completed: %d (%.2f/s) | Active: %d/%d | Queue: %d",
                    genSubmittedPeriod, genCompletedPeriod, generalThroughput,
                    generalExecutor.getActiveCount(), generalExecutor.getPoolSize(),
                    generalExecutor.getQueue().size()
                ));
                getLogger().info(String.format(
                    "[Lifetime]     Completed: %d/%d tasks",
                    genCompleted, genTotal
                ));
                getLogger().info("===========================================================");
                
            } catch (Exception e) {
                getLogger().warning("[Metrics] Error: " + e.getMessage());
            }
        }, 60, 60, java.util.concurrent.TimeUnit.SECONDS);
    }
    
    public static AkiAsyncPlugin getInstance() {
        return instance;
    }
    
    public ConfigManager getConfigManager() {
        return configManager;
    }
    
    public AsyncExecutorManager getExecutorManager() {
        return executorManager;
    }
    
    public CacheManager getCacheManager() {
        return cacheManager;
    }
    
    public EntityThrottlingManager getThrottlingManager() {
        return throttlingManager;
    }
    
    public org.virgil.akiasync.network.NetworkOptimizationManager getNetworkOptimizationManager() {
        return networkOptimizationManager;
    }
    
    public org.virgil.akiasync.chunk.ChunkLoadPriorityScheduler getChunkLoadScheduler() {
        return chunkLoadScheduler;
    }
    
    public org.virgil.akiasync.compat.VirtualEntityCompatManager getVirtualEntityCompatManager() {
        return virtualEntityCompatManager;
    }
    
    public AkiAsyncBridge getBridge() {
        return bridge;
    }
    
    public void restartMetricsScheduler() {
        stopMetricsScheduler();
        
        startCombinedMetrics();
    }
    
    public void stopMetricsScheduler() {
        if (metricsScheduler != null) {
            metricsScheduler.shutdownNow();
            metricsScheduler = null;
        }
    }
    
    private void registerCommand(String name, org.bukkit.command.CommandExecutor executor) {
        org.bukkit.command.PluginCommand command = getCommand(name);
        if (command != null) {
            command.setExecutor(executor);
        }
    }
    
}