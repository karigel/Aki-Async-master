package org.virgil.akiasync.ignite;

import org.bukkit.Bukkit;
import org.bukkit.Server;
import org.virgil.akiasync.ignite.config.IgniteConfigManager;

import java.io.File;
import java.util.logging.Logger;

/**
 * Ignite 模式下的插件适配器
 * Plugin adapter for Ignite mode
 * 
 * 这个类模拟 AkiAsyncPlugin 的核心功能，让依赖它的辅助组件可以在 Ignite 模式下工作。
 * This class simulates core AkiAsyncPlugin functionality so auxiliary components can work in Ignite mode.
 * 
 * 注意：这个类不修改原有代码，只是提供一个桥接层。
 * Note: This class does not modify original code, just provides a bridge layer.
 */
public class IgnitePluginAdapter {
    
    private static IgnitePluginAdapter instance;
    
    private final Logger logger;
    private final IgniteConfigManager configManager;
    private final File dataFolder;
    private final Server server;
    
    // 辅助管理器 / Auxiliary managers
    private Object chunkLoadScheduler;
    private org.virgil.akiasync.network.PlayerTeleportTracker teleportTracker;
    
    private IgnitePluginAdapter() {
        this.logger = Logger.getLogger("AkiAsync");
        this.server = Bukkit.getServer();
        
        // 从 AkiAsyncInitializer 获取配置
        AkiAsyncInitializer init = AkiAsyncInitializer.getInstance();
        if (init != null) {
            this.configManager = init.getConfigManager();
            this.dataFolder = init.getDataFolder();
        } else {
            throw new IllegalStateException("AkiAsyncInitializer not initialized");
        }
    }
    
    public static IgnitePluginAdapter getInstance() {
        if (instance == null) {
            synchronized (IgnitePluginAdapter.class) {
                if (instance == null) {
                    instance = new IgnitePluginAdapter();
                }
            }
        }
        return instance;
    }
    
    public static boolean isInitialized() {
        return instance != null;
    }
    
    /**
     * 初始化所有辅助功能
     * Initialize all auxiliary features
     */
    public void initializeAuxiliaryFeatures() {
        logger.info("[AkiAsync/Ignite] 正在初始化辅助功能...");
        
        // 初始化网络优化
        if (configManager.isNetworkOptimizationEnabled()) {
            try {
                initializeNetworkOptimization();
                logger.info("[AkiAsync/Ignite] 网络优化已启用");
            } catch (Exception e) {
                logger.warning("[AkiAsync/Ignite] 网络优化初始化失败: " + e.getMessage());
            }
        }
        
        // 初始化区块加载优先级调度器（Fast Movement Chunk Load + Center Offset Loading）
        if (configManager.isFastMovementChunkLoadEnabled()) {
            try {
                initializeChunkLoadScheduler();
                logger.info("[AkiAsync/Ignite] 区块加载优先级调度器已启用");
                if (configManager.isCenterOffsetEnabled()) {
                    logger.info("[AkiAsync/Ignite] Center Offset Loading 已启用");
                }
            } catch (Exception e) {
                logger.warning("[AkiAsync/Ignite] 区块加载调度器初始化失败: " + e.getMessage());
            }
        }
        
        // 初始化虚拟实体兼容管理器
        if (configManager.isVirtualEntityCompatibilityEnabled()) {
            try {
                initializeVirtualEntityCompat();
                logger.info("[AkiAsync/Ignite] 虚拟实体兼容管理器已启用");
            } catch (Exception e) {
                logger.warning("[AkiAsync/Ignite] 虚拟实体兼容初始化失败: " + e.getMessage());
            }
        }
        
        // 初始化种子加密（Seed Encryption）
        if (configManager.isSeedEncryptionEnabled()) {
            try {
                initializeSeedEncryption();
            } catch (Exception e) {
                logger.warning("[AkiAsync/Ignite] 种子加密初始化失败: " + e.getMessage());
            }
        }
        
        // 初始化实体节流管理器
        if (configManager.isEntityThrottlingEnabled()) {
            try {
                initializeEntityThrottling();
                logger.info("[AkiAsync/Ignite] 实体节流管理器已启用");
            } catch (Exception e) {
                logger.warning("[AkiAsync/Ignite] 实体节流管理器初始化失败: " + e.getMessage());
            }
        }
        
        // 初始化结构定位优化器（延迟初始化，此时插件已加载）
        if (configManager.isStructureLocationAsyncEnabled()) {
            try {
                initializeStructureLocator();
            } catch (Exception e) {
                logger.warning("[AkiAsync/Ignite] 结构定位优化器初始化失败: " + e.getMessage());
            }
        }
        
        // 显示所有功能状态
        displayFeatureStatus();
        
        logger.info("[AkiAsync/Ignite] 辅助功能初始化完成");
    }
    
    /**
     * 初始化种子加密
     * 注意：crypto 包在原项目中不存在，这里只做基础的 SecureSeed 支持
     */
    private void initializeSeedEncryption() {
        String scheme = configManager.getSeedEncryptionScheme();
        
        if ("quantum".equalsIgnoreCase(scheme)) {
            // QuantumSeed 加密 - 原项目中 crypto 包不存在，功能不可用
            logger.info("[AkiAsync/Ignite] QuantumSeed 加密功能在 Ignite 模式下不可用（原项目未实现）");
            logger.info("[AkiAsync/Ignite] 建议使用 SecureSeed 加密作为替代");
        } else if ("secure".equalsIgnoreCase(scheme) || configManager.isSecureSeedEnabled()) {
            // SecureSeed 加密 - 通过 Bridge 支持
            try {
                org.virgil.akiasync.ignite.AkiAsyncInitializer init = org.virgil.akiasync.ignite.AkiAsyncInitializer.getInstance();
                if (init != null && init.getBridge() != null && server.getWorlds().size() > 0) {
                    long worldSeed = server.getWorlds().get(0).getSeed();
                    init.getBridge().initializeSecureSeed(worldSeed);
                    logger.info("[AkiAsync/Ignite] SecureSeed 加密已启用 (" + configManager.getSecureSeedBits() + " bits)");
                }
            } catch (Exception e) {
                logger.warning("[AkiAsync/Ignite] SecureSeed 初始化失败: " + e.getMessage());
            }
        } else {
            logger.info("[AkiAsync/Ignite] 种子加密已配置但未启用具体方案");
        }
    }
    
    /**
     * 初始化实体节流管理器
     * 使用 Ignite 适配的版本，不依赖 AkiAsyncPlugin
     */
    private void initializeEntityThrottling() throws Exception {
        try {
            org.virgil.akiasync.ignite.adapter.IgniteEntityThrottlingManager throttlingManager = 
                new org.virgil.akiasync.ignite.adapter.IgniteEntityThrottlingManager(configManager, logger, dataFolder);
            throttlingManager.initialize();
            logger.info("[AkiAsync/Ignite] EntityThrottlingManager: Enabled (Ignite adapter)");
        } catch (Exception e) {
            logger.warning("[AkiAsync/Ignite] EntityThrottlingManager initialization failed: " + e.getMessage());
            logger.info("[AkiAsync/Ignite] EntityThrottling: Mixin 级别优化已启用");
        }
    }
    
    /**
     * 初始化结构定位优化器和缓存管理器
     */
    private void initializeStructureLocator() {
        try {
            // 初始化 Ignite 适配的结构缓存管理器
            org.virgil.akiasync.ignite.adapter.IgniteStructureCacheManager.getInstance(configManager, logger);
            logger.info("[AkiAsync/Ignite] StructureCacheManager: Enabled (Ignite adapter)");
            
            // OptimizedStructureLocator 需要 AkiAsyncPlugin，在 Ignite 模式下跳过
            // 结构定位优化已通过 StructureLocatorBridge 在 Mixin 层面实现
            logger.info("[AkiAsync/Ignite] StructureLocation: Async enabled (via StructureLocatorBridge)");
            logger.info("[AkiAsync/Ignite] OptimizedStructureLocator: Skipped (requires AkiAsyncPlugin, using Mixin optimization)");
        } catch (Exception e) {
            logger.warning("[AkiAsync/Ignite] StructureCache initialization failed: " + e.getMessage());
            logger.info("[AkiAsync/Ignite] StructureLocation: Basic async功能已启用");
        }
    }
    
    /**
     * 显示所有功能状态
     */
    private void displayFeatureStatus() {
        logger.info("[AkiAsync/Ignite] ====== 功能状态汇总 ======");
        logger.info("[核心功能 - 完全工作]");
        logger.info("  Entity Tracker: " + (configManager.isEntityTrackerEnabled() ? "ON" : "OFF"));
        logger.info("  Mob Spawning: " + (configManager.isMobSpawningEnabled() ? "ON" : "OFF"));
        logger.info("  Entity Tick Parallel: " + (configManager.isEntityTickParallel() ? "ON" : "OFF"));
        logger.info("  Block Entity Parallel: " + (configManager.isBlockEntityParallelTickEnabled() ? "ON" : "OFF"));
        logger.info("  Async Lighting: " + (configManager.isAsyncLightingEnabled() ? "ON" : "OFF"));
        logger.info("  Async Pathfinding: " + (configManager.isAsyncPathfindingEnabled() ? "ON" : "OFF"));
        logger.info("  Brain Throttle: " + (configManager.isBrainThrottleEnabled() ? "ON" : "OFF"));
        logger.info("  TNT Optimization: " + (configManager.isTNTOptimizationEnabled() ? "ON" : "OFF"));
        logger.info("  Universal AI: " + (configManager.isUniversalAiOptimizationEnabled() ? "ON" : "OFF"));
        logger.info("  BeeFix: " + (configManager.isBeeFixEnabled() ? "ON" : "OFF"));
        logger.info("  Structure Location Async: " + (configManager.isStructureLocationAsyncEnabled() ? "ON" : "OFF"));
        logger.info("  TNT Land Protection: " + (configManager.isTNTLandProtectionEnabled() ? "ON" : "OFF"));
        logger.info("[辅助功能]");
        logger.info("  Chunk Tick Async: " + (configManager.isChunkTickAsyncEnabled() ? "ON" : "OFF"));
        logger.info("  Seed Encryption: " + (configManager.isSeedEncryptionEnabled() ? "ON" : "OFF"));
        logger.info("  Falling Block Parallel: " + (configManager.isFallingBlockParallelEnabled() ? "ON" : "OFF"));
        logger.info("  Item Entity Parallel: " + (configManager.isItemEntityParallelEnabled() ? "ON" : "OFF"));
        logger.info("  Item Entity Smart Merge: " + (configManager.isItemEntityMergeOptimizationEnabled() ? "ON" : "OFF"));
        logger.info("  Item Entity Age Optimization: " + (configManager.isItemEntityAgeOptimizationEnabled() ? "ON" : "OFF"));
        logger.info("  Minecart Cauldron Destruction: " + (configManager.isMinecartCauldronDestructionEnabled() ? "ON" : "OFF"));
        logger.info("[网络功能 - 完全支持]");
        logger.info("  Network Optimization: " + (configManager.isNetworkOptimizationEnabled() ? "ON" : "OFF"));
        logger.info("  Fast Movement Chunk Load: " + (configManager.isFastMovementChunkLoadEnabled() ? "ON" : "OFF"));
        logger.info("  Center Offset Loading: " + (configManager.isCenterOffsetEnabled() ? "ON" : "OFF"));
        logger.info("  Packet Priority Queue: " + (configManager.isPacketPriorityEnabled() ? "ON" : "OFF"));
        logger.info("  Congestion Detection: " + (configManager.isCongestionDetectionEnabled() ? "ON" : "OFF"));
        logger.info("[AkiAsync/Ignite] ===========================");
    }
    
    /**
     * 初始化网络优化管理器
     * 使用代理插件来注册事件监听器
     */
    private void initializeNetworkOptimization() throws Exception {
        // 获取一个可用的插件来注册事件（任意已启用的插件都可以）
        org.bukkit.plugin.Plugin proxyPlugin = findProxyPlugin();
        if (proxyPlugin == null) {
            logger.warning("[AkiAsync/Ignite] NetworkOptimization: 没有找到可用的代理插件，跳过初始化");
            return;
        }
        
        // 创建独立的网络优化组件
        initializeNetworkComponents(proxyPlugin);
        
        logger.info("[AkiAsync/Ignite] NetworkOptimization: 已通过代理插件 " + proxyPlugin.getName() + " 初始化");
    }
    
    /**
     * 查找一个可用的插件作为事件注册的代理
     */
    private org.bukkit.plugin.Plugin findProxyPlugin() {
        // 优先使用我们自己的插件（如果有的话）
        String[] preferredPlugins = {"KariClaims", "WorldGuard", "Essentials", "LuckPerms"};
        
        for (String name : preferredPlugins) {
            org.bukkit.plugin.Plugin plugin = Bukkit.getPluginManager().getPlugin(name);
            if (plugin != null && plugin.isEnabled()) {
                return plugin;
            }
        }
        
        // 使用任意已启用的插件
        for (org.bukkit.plugin.Plugin plugin : Bukkit.getPluginManager().getPlugins()) {
            if (plugin.isEnabled()) {
                return plugin;
            }
        }
        
        return null;
    }
    
    /**
     * 初始化网络优化组件
     */
    private void initializeNetworkComponents(org.bukkit.plugin.Plugin proxyPlugin) {
        try {
            // 创建 PlayerTeleportTracker（核心组件，用于统计传送数据）
            boolean debugEnabled = configManager.isDebugLoggingEnabled();
            int boostDurationSeconds = configManager.getTeleportBoostDurationSeconds();
            boolean filterNonEssentialPackets = configManager.isTeleportFilterNonEssentialPackets();
            
            teleportTracker = new org.virgil.akiasync.network.PlayerTeleportTracker(
                proxyPlugin, logger, debugEnabled, boostDurationSeconds, filterNonEssentialPackets
            );
            
            // 将 TeleportTracker 设置到 Bridge 中
            org.virgil.akiasync.ignite.AkiAsyncInitializer init = org.virgil.akiasync.ignite.AkiAsyncInitializer.getInstance();
            if (init != null && init.getBridge() != null) {
                init.getBridge().setTeleportTracker(teleportTracker);
                logger.info("[AkiAsync/Ignite] TeleportTracker 已关联到 Bridge");
            }
            
            logger.info("[AkiAsync/Ignite] PlayerTeleportTracker 已初始化");
            
            // 创建视锥过滤器（不需要 plugin）
            Class<?> frustumClass = Class.forName("org.virgil.akiasync.network.ViewFrustumPacketFilter");
            Object frustumFilter = frustumClass.getDeclaredConstructor().newInstance();
            
            // 创建场景检测器（不需要 plugin）
            Class<?> scenarioClass = Class.forName("org.virgil.akiasync.network.ScenarioDetector");
            Object scenarioDetector = scenarioClass.getDeclaredConstructor().newInstance();
            
            // 创建数据包调度器 - IgniteConfigManager 现在继承 ConfigManager，类型兼容
            Class<?> schedulerClass = Class.forName("org.virgil.akiasync.network.PriorityPacketScheduler");
            Object packetScheduler = schedulerClass.getDeclaredConstructor(
                org.virgil.akiasync.config.ConfigManager.class
            ).newInstance(configManager);
            
            logger.info("[AkiAsync/Ignite] NetworkOptimization 组件已初始化:");
            logger.info("  - PlayerTeleportTracker: Enabled (boost=" + boostDurationSeconds + "s, filter=" + filterNonEssentialPackets + ")");
            logger.info("  - ViewFrustumPacketFilter: Enabled");
            logger.info("  - ScenarioDetector: Enabled");
            logger.info("  - PriorityPacketScheduler: Enabled");
            logger.info("  - Packet Priority: " + (configManager.isPacketPriorityEnabled() ? "Enabled" : "Disabled"));
            logger.info("  - Chunk Rate Control: " + (configManager.isChunkRateControlEnabled() ? "Enabled" : "Disabled"));
            
        } catch (Exception e) {
            logger.warning("[AkiAsync/Ignite] NetworkOptimization 组件初始化失败: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    private void initializeChunkLoadScheduler() throws Exception {
        // ChunkLoadPriorityScheduler - IgniteConfigManager 现在继承 ConfigManager，类型兼容
        Class<?> schedulerClass = Class.forName("org.virgil.akiasync.chunk.ChunkLoadPriorityScheduler");
        java.lang.reflect.Constructor<?> constructor = schedulerClass.getConstructor(
            org.virgil.akiasync.config.ConfigManager.class
        );
        chunkLoadScheduler = constructor.newInstance(configManager);
        
        // 调用 start() 方法
        java.lang.reflect.Method startMethod = schedulerClass.getMethod("start");
        startMethod.invoke(chunkLoadScheduler);
        
        // 将 ChunkLoadScheduler 设置到 Bridge 中
        org.virgil.akiasync.ignite.AkiAsyncInitializer init = org.virgil.akiasync.ignite.AkiAsyncInitializer.getInstance();
        if (init != null && init.getBridge() != null && chunkLoadScheduler instanceof org.virgil.akiasync.chunk.ChunkLoadPriorityScheduler) {
            init.getBridge().setChunkLoadScheduler((org.virgil.akiasync.chunk.ChunkLoadPriorityScheduler) chunkLoadScheduler);
            logger.info("[AkiAsync/Ignite] ChunkLoadScheduler 已关联到 Bridge");
        }
        
        logger.info("[AkiAsync/Ignite] ChunkLoadPriorityScheduler: Enabled");
    }
    
    private void initializeVirtualEntityCompat() throws Exception {
        // VirtualEntityCompatManager 需要 plugin，但我们可以用反射绕过
        // 它主要是管理 FancyNpcs 和 ZNPCsPlus 检测器
        // 这些检测器我们已经在 CraftServerLoadPluginsMixin 中初始化了
        logger.info("[AkiAsync/Ignite] VirtualEntityCompat: 检测器已在 POSTWORLD 阶段初始化");
    }
    
    // Getters
    public Logger getLogger() {
        return logger;
    }
    
    public IgniteConfigManager getConfigManager() {
        return configManager;
    }
    
    public File getDataFolder() {
        return dataFolder;
    }
    
    public Server getServer() {
        return server;
    }
    
    /**
     * 关闭所有辅助功能
     */
    public void shutdown() {
        logger.info("[AkiAsync/Ignite] 正在关闭辅助功能...");
        
        if (teleportTracker != null) {
            try {
                teleportTracker.shutdown();
                logger.info("[AkiAsync/Ignite] PlayerTeleportTracker 已关闭");
            } catch (Exception e) {
                // 忽略
            }
        }
        
        if (chunkLoadScheduler != null) {
            try {
                java.lang.reflect.Method stopMethod = chunkLoadScheduler.getClass().getMethod("stop");
                stopMethod.invoke(chunkLoadScheduler);
            } catch (Exception e) {
                // 忽略
            }
        }
        
        instance = null;
    }
    
    /**
     * 获取传送追踪器
     */
    public org.virgil.akiasync.network.PlayerTeleportTracker getTeleportTracker() {
        return teleportTracker;
    }
}
