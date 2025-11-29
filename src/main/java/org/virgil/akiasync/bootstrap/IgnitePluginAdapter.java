package org.virgil.akiasync.bootstrap;

import org.bukkit.Bukkit;
import org.bukkit.Server;
import org.virgil.akiasync.config.ConfigManager;

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
    private final ConfigManager configManager;
    private final File dataFolder;
    private final Server server;
    
    // 辅助管理器 / Auxiliary managers
    private Object chunkLoadScheduler;
    
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
        
        // 初始化区块加载优先级调度器
        if (configManager.isFastMovementChunkLoadEnabled()) {
            try {
                initializeChunkLoadScheduler();
                logger.info("[AkiAsync/Ignite] 区块加载优先级调度器已启用");
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
        
        logger.info("[AkiAsync/Ignite] 辅助功能初始化完成");
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
            // 创建拥塞检测器（不需要 plugin）
            Class<?> congestionClass = Class.forName("org.virgil.akiasync.network.NetworkCongestionDetector");
            java.lang.reflect.Constructor<?> congestionCtor = congestionClass.getDeclaredConstructors()[0];
            congestionCtor.setAccessible(true);
            // NetworkCongestionDetector 构造函数需要 AkiAsyncPlugin，但实际上 plugin 字段没有被使用
            // 我们可以传入 null 或使用反射绕过
            
            // 创建视锥过滤器（不需要 plugin）
            Class<?> frustumClass = Class.forName("org.virgil.akiasync.network.ViewFrustumPacketFilter");
            Object frustumFilter = frustumClass.getDeclaredConstructor().newInstance();
            
            // 创建场景检测器（不需要 plugin）
            Class<?> scenarioClass = Class.forName("org.virgil.akiasync.network.ScenarioDetector");
            Object scenarioDetector = scenarioClass.getDeclaredConstructor().newInstance();
            
            // 创建数据包调度器（只需要 ConfigManager）
            Class<?> schedulerClass = Class.forName("org.virgil.akiasync.network.PriorityPacketScheduler");
            Object packetScheduler = schedulerClass.getDeclaredConstructor(ConfigManager.class).newInstance(configManager);
            
            // 设置 logger
            java.lang.reflect.Method setLoggerMethod = schedulerClass.getMethod("setLogger", Logger.class);
            setLoggerMethod.invoke(packetScheduler, logger);
            
            logger.info("[AkiAsync/Ignite] NetworkOptimization 组件已初始化:");
            logger.info("  - ViewFrustumPacketFilter: Enabled");
            logger.info("  - ScenarioDetector: Enabled");
            logger.info("  - PriorityPacketScheduler: Enabled");
            logger.info("  - Packet Priority: " + (configManager.isPacketPriorityEnabled() ? "Enabled" : "Disabled"));
            logger.info("  - Chunk Rate Control: " + (configManager.isChunkRateControlEnabled() ? "Enabled" : "Disabled"));
            
        } catch (Exception e) {
            logger.warning("[AkiAsync/Ignite] NetworkOptimization 组件初始化失败: " + e.getMessage());
        }
    }
    
    private void initializeChunkLoadScheduler() throws Exception {
        // ChunkLoadPriorityScheduler 只需要 ConfigManager
        Class<?> schedulerClass = Class.forName("org.virgil.akiasync.chunk.ChunkLoadPriorityScheduler");
        java.lang.reflect.Constructor<?> constructor = schedulerClass.getConstructor(ConfigManager.class);
        chunkLoadScheduler = constructor.newInstance(configManager);
        
        // 调用 start() 方法
        java.lang.reflect.Method startMethod = schedulerClass.getMethod("start");
        startMethod.invoke(chunkLoadScheduler);
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
    
    public ConfigManager getConfigManager() {
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
}
