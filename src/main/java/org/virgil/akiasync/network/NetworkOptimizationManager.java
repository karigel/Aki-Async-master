package org.virgil.akiasync.network;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.virgil.akiasync.AkiAsyncPlugin;
import org.virgil.akiasync.config.ConfigManager;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * 网络优化管理器 / Network Optimization Manager
 * 管理数据包优先级、区块发送速率控制、拥塞检测等网络优化功能
 * Manages packet priority, chunk send rate control, congestion detection and other network optimizations
 */
public class NetworkOptimizationManager implements Listener {

    private final AkiAsyncPlugin plugin;
    private final Logger logger;
    private final boolean debugEnabled;

    private final NetworkCongestionDetector congestionDetector;
    private final ChunkSendRateController chunkRateController;
    private final PriorityPacketScheduler packetScheduler;
    private final PacketSendWorker packetSendWorker;
    private final ViewFrustumPacketFilter viewFrustumFilter;
    private final ScenarioDetector scenarioDetector;
    private final PlayerTeleportTracker teleportTracker;
    private final Map<UUID, PriorityPacketQueue> playerQueues = new ConcurrentHashMap<>();

    private boolean packetPriorityEnabled;
    private boolean chunkRateControlEnabled;
    private boolean congestionDetectionEnabled;
    private boolean viewFrustumFilterEnabled;
    private boolean teleportOptimizationEnabled;

    private boolean isFolia = false;

    public NetworkOptimizationManager(AkiAsyncPlugin plugin) {
        // 检测Folia环境 / Detect Folia environment
        try {
            Class.forName("io.papermc.paper.threadedregions.RegionizedServer");
            isFolia = true;
        } catch (ClassNotFoundException e) {
            isFolia = false;
        }

        this.plugin = plugin;
        this.logger = plugin.getLogger();
        this.debugEnabled = plugin.getConfigManager().isDebugLoggingEnabled();

        this.congestionDetector = new NetworkCongestionDetector(plugin);
        this.chunkRateController = new ChunkSendRateController(plugin, congestionDetector);
        this.packetScheduler = new PriorityPacketScheduler(plugin.getConfigManager());
        this.packetScheduler.setLogger(logger);
        this.packetSendWorker = new PacketSendWorker(packetScheduler, plugin.getConfigManager());
        this.packetSendWorker.setLogger(logger);
        this.viewFrustumFilter = new ViewFrustumPacketFilter();
        this.scenarioDetector = new ScenarioDetector();
        
        // 初始化传送追踪器 / Initialize teleport tracker
        ConfigManager config = plugin.getConfigManager();
        this.teleportOptimizationEnabled = config.isTeleportOptimizationEnabled();
        if (teleportOptimizationEnabled) {
            this.teleportTracker = new PlayerTeleportTracker(
                plugin, logger, config.isTeleportDebugEnabled(),
                config.getTeleportBoostDurationSeconds(),
                config.isTeleportFilterNonEssentialPackets()
            );
        } else {
            this.teleportTracker = null;
        }

        loadConfiguration();

        // 注册事件监听器 / Register event listeners
        Bukkit.getPluginManager().registerEvents(this, plugin);

        // 启动周期性任务 / Start periodic tasks
        startPeriodicTasks();

        logger.info("[NetworkOptimization] Network optimization manager initialized");
        logger.info("  - Packet Priority: " + (packetPriorityEnabled ? "Enabled" : "Disabled"));
        logger.info("  - Chunk Rate Control: " + (chunkRateControlEnabled ? "Enabled" : "Disabled"));
        logger.info("  - Congestion Detection: " + (congestionDetectionEnabled ? "Enabled" : "Disabled"));
        logger.info("  - View Frustum Filter: " + (viewFrustumFilterEnabled ? "Enabled" : "Disabled"));
    }

    private void loadConfiguration() {
        packetPriorityEnabled = plugin.getConfigManager().isPacketPriorityEnabled();
        chunkRateControlEnabled = plugin.getConfigManager().isChunkRateControlEnabled();
        congestionDetectionEnabled = plugin.getConfigManager().isCongestionDetectionEnabled();
        viewFrustumFilterEnabled = plugin.getConfigManager().isViewFrustumFilterEnabled();

        // 配置拥塞检测器 / Configure congestion detector
        congestionDetector.setHighPingThreshold(
            plugin.getConfigManager().getHighPingThreshold()
        );
        congestionDetector.setCriticalPingThreshold(
            plugin.getConfigManager().getCriticalPingThreshold()
        );
        congestionDetector.setHighBandwidthThreshold(
            plugin.getConfigManager().getHighBandwidthThreshold()
        );

        // 配置区块发送速率控制器 / Configure chunk send rate controller
        chunkRateController.setBaseChunkSendRate(
            plugin.getConfigManager().getBaseChunkSendRate()
        );
        chunkRateController.setMaxChunkSendRate(
            plugin.getConfigManager().getMaxChunkSendRate()
        );
        chunkRateController.setMinChunkSendRate(
            plugin.getConfigManager().getMinChunkSendRate()
        );
    }

    private void startPeriodicTasks() {
        if (isFolia) {
            // Folia环境使用GlobalRegionScheduler / Use GlobalRegionScheduler in Folia
            try {
                Object server = Bukkit.getServer();
                Object globalScheduler = server.getClass().getMethod("getGlobalRegionScheduler").invoke(server);
                java.lang.reflect.Method runAtFixedRateMethod = globalScheduler.getClass().getMethod(
                    "runAtFixedRate",
                    org.bukkit.plugin.Plugin.class,
                    java.util.function.Consumer.class,
                    long.class,
                    long.class
                );

                // Ping更新任务 / Ping update task
                runAtFixedRateMethod.invoke(
                    globalScheduler, plugin,
                    (java.util.function.Consumer<Object>) task -> {
                        if (!congestionDetectionEnabled) return;
                        for (Player player : Bukkit.getOnlinePlayers()) {
                            congestionDetector.updatePlayerPing(player);
                        }
                    },
                    20L, 20L
                );

                logger.info("[NetworkOptimization] Using Folia GlobalRegionScheduler");
            } catch (Exception e) {
                logger.severe("[NetworkOptimization] Failed to start Folia tasks: " + e.getMessage());
            }
        } else {
            // 标准Bukkit调度器 / Standard Bukkit scheduler
            Bukkit.getScheduler().runTaskTimer(plugin, () -> {
                if (!congestionDetectionEnabled) return;
                for (Player player : Bukkit.getOnlinePlayers()) {
                    congestionDetector.updatePlayerPing(player);
                }
            }, 20L, 20L);

            // 统计日志任务 / Statistics logging task
            Bukkit.getScheduler().runTaskTimer(plugin, this::logStatistics, 1200L, 1200L);
        }
    }

    private void logStatistics() {
        if (!debugEnabled) return;

        logger.info("========== Network Optimization Statistics ==========");
        for (Player player : Bukkit.getOnlinePlayers()) {
            String playerName = player.getName();
            String networkStats = congestionDetector.getPlayerStatistics(player.getUniqueId());
            logger.info(String.format("[%s] Network: %s", playerName, networkStats));

            if (chunkRateControlEnabled) {
                String chunkStats = chunkRateController.getPlayerStatistics(player.getUniqueId());
                logger.info(String.format("[%s] Chunks: %s", playerName, chunkStats));
            }
        }
        logger.info("====================================================");
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();

        // 创建玩家数据包队列 / Create player packet queue
        if (packetPriorityEnabled) {
            PriorityPacketQueue queue = new PriorityPacketQueue(player.getName(), logger, debugEnabled);
            playerQueues.put(playerId, queue);
            packetScheduler.createQueue(playerId, player.getName());
        }

        if (chunkRateControlEnabled) {
            chunkRateController.updatePlayerLocation(player);
        }

        if (debugEnabled) {
            logger.info(String.format(
                "[NetworkOptimization] Player %s joined, network optimization enabled",
                player.getName()
            ));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        UUID playerId = event.getPlayer().getUniqueId();

        playerQueues.remove(playerId);
        packetScheduler.clearQueue(playerId);
        congestionDetector.removePlayer(playerId);
        chunkRateController.removePlayer(playerId);
        viewFrustumFilter.removePlayer(playerId);
        scenarioDetector.removePlayer(playerId);

        if (debugEnabled) {
            logger.info(String.format(
                "[NetworkOptimization] Player %s quit, data cleaned",
                event.getPlayer().getName()
            ));
        }
    }

    public NetworkCongestionDetector getCongestionDetector() {
        return congestionDetector;
    }

    public ChunkSendRateController getChunkRateController() {
        return chunkRateController;
    }

    public boolean isPacketPriorityEnabled() {
        return packetPriorityEnabled;
    }

    public boolean isChunkRateControlEnabled() {
        return chunkRateControlEnabled;
    }

    public boolean isCongestionDetectionEnabled() {
        return congestionDetectionEnabled;
    }

    public boolean isViewFrustumFilterEnabled() {
        return viewFrustumFilterEnabled;
    }

    public PriorityPacketScheduler getPacketScheduler() {
        return packetScheduler;
    }

    public PacketSendWorker getPacketSendWorker() {
        return packetSendWorker;
    }

    public ViewFrustumPacketFilter getViewFrustumFilter() {
        return viewFrustumFilter;
    }

    public ScenarioDetector getScenarioDetector() {
        return scenarioDetector;
    }
    
    public PlayerTeleportTracker getTeleportTracker() {
        return teleportTracker;
    }
    
    public boolean isTeleportOptimizationEnabled() {
        return teleportOptimizationEnabled;
    }

    public PriorityPacketQueue getPlayerQueue(UUID playerId) {
        return playerQueues.get(playerId);
    }

    public void reloadConfiguration() {
        loadConfiguration();
        logger.info("[NetworkOptimization] Configuration reloaded");
    }

    public void shutdown() {
        // 停止数据包发送工作线程 / Stop packet send worker
        if (packetPriorityEnabled) {
            packetSendWorker.stop();
        }
        
        // 清理所有玩家队列 / Clear all player queues
        for (UUID playerId : playerQueues.keySet()) {
            packetScheduler.clearQueue(playerId);
        }
        
        playerQueues.clear();
        congestionDetector.clear();
        chunkRateController.clear();
        viewFrustumFilter.clear();
        scenarioDetector.clear();
        
        // 关闭传送追踪器 / Shutdown teleport tracker
        if (teleportTracker != null) {
            teleportTracker.shutdown();
        }

        logger.info("[NetworkOptimization] Network optimization manager shut down");
    }
}
