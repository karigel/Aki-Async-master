package org.virgil.akiasync.ignite.config;

import org.bukkit.configuration.file.YamlConfiguration;
import org.virgil.akiasync.config.ConfigManager;
import java.io.*;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Logger;

/**
 * Ignite 环境下的配置管理器
 * 继承 ConfigManager 以保持类型兼容性
 * 配置文件存储在 mods/AkiAsync/ 目录下
 */
public class IgniteConfigManager extends ConfigManager {
    
    private static final int CURRENT_CONFIG_VERSION = 16;
    
    private final File dataFolder;
    private final Logger logger;
    private YamlConfiguration config;
    
    // 所有配置字段 - 与原 ConfigManager 对齐
    private boolean entityTrackerEnabled;
    private int threadPoolSize;
    private int updateIntervalTicks;
    private int maxQueueSize;
    private boolean mobSpawningEnabled;
    private boolean spawnerOptimizationEnabled;
    private boolean densityControlEnabled;
    private int maxEntitiesPerChunk;
    private boolean brainThrottle;
    private int brainThrottleInterval;
    private boolean livingEntityTravelOptimizationEnabled;
    private int livingEntityTravelSkipInterval;
    private boolean behaviorThrottleEnabled;
    private int behaviorThrottleInterval;
    private boolean mobDespawnOptimizationEnabled;
    private int mobDespawnCheckInterval;
    private long asyncAITimeoutMicros;
    private boolean villagerOptimizationEnabled;
    private boolean villagerUsePOISnapshot;
    private boolean villagerPoiCacheEnabled;
    private int villagerPoiCacheExpireTime;
    private boolean piglinOptimizationEnabled;
    private boolean piglinUsePOISnapshot;
    private int piglinLookDistance;
    private int piglinBarterDistance;
    private boolean pillagerFamilyOptimizationEnabled;
    private boolean pillagerFamilyUsePOISnapshot;
    private boolean evokerOptimizationEnabled;
    private boolean blazeOptimizationEnabled;
    private boolean guardianOptimizationEnabled;
    private boolean witchOptimizationEnabled;
    private boolean universalAiOptimizationEnabled;
    private String universalAiEntitiesConfigFile;
    private Set<String> universalAiEntities;
    private boolean dabEnabled;
    private int dabStartDistance;
    private int dabActivationDistMod;
    private int dabMaxTickInterval;
    private boolean asyncPathfindingEnabled;
    private int asyncPathfindingMaxThreads;
    private int asyncPathfindingKeepAliveSeconds;
    private int asyncPathfindingMaxQueueSize;
    private int asyncPathfindingTimeoutMs;
    private boolean entityThrottlingEnabled;
    private String entityThrottlingConfigFile;
    private int entityThrottlingCheckInterval;
    private int entityThrottlingThrottleInterval;
    private int entityThrottlingRemovalBatchSize;
    private boolean zeroDelayFactoryOptimizationEnabled;
    private String zeroDelayFactoryEntitiesConfigFile;
    private Set<String> zeroDelayFactoryEntities;
    private boolean blockEntityParallelTickEnabled;
    private int blockEntityParallelMinBlockEntities;
    private int blockEntityParallelBatchSize;
    private boolean blockEntityParallelProtectContainers;
    private int blockEntityParallelTimeoutMs;
    private boolean hopperOptimizationEnabled;
    private int hopperCacheExpireTime;
    private boolean minecartOptimizationEnabled;
    private int minecartTickInterval;
    private boolean simpleEntitiesOptimizationEnabled;
    private boolean simpleEntitiesUsePOISnapshot;
    private boolean entityTickParallel;
    private int entityTickThreads;
    private int minEntitiesForParallel;
    private int entityTickBatchSize;
    private boolean asyncLightingEnabled;
    private int lightingThreadPoolSize;
    private int lightBatchThreshold;
    private boolean useLayeredPropagationQueue;
    private int maxLightPropagationDistance;
    private boolean skylightCacheEnabled;
    private int skylightCacheDurationMs;
    private boolean lightDeduplicationEnabled;
    private boolean dynamicBatchAdjustmentEnabled;
    private boolean advancedLightingStatsEnabled;
    private boolean playerChunkLoadingOptimizationEnabled;
    private int maxConcurrentChunkLoadsPerPlayer;
    private boolean entityTrackingRangeOptimizationEnabled;
    private double entityTrackingRangeMultiplier;
    private boolean alternateCurrentEnabled;
    private boolean redstoneWireTurboEnabled;
    private boolean redstoneUpdateBatchingEnabled;
    private int redstoneUpdateBatchThreshold;
    private boolean redstoneCacheEnabled;
    private int redstoneCacheDurationMs;
    private boolean usePandaWireAlgorithm;
    private boolean tntOptimizationEnabled;
    private Set<String> tntExplosionEntities;
    private int tntThreads;
    private int tntMaxBlocks;
    private long tntTimeoutMicros;
    private int tntBatchSize;
    private boolean tntVanillaCompatibilityEnabled;
    private boolean tntUseVanillaPower;
    private boolean tntUseVanillaFireLogic;
    private boolean tntUseVanillaDamageCalculation;
    private boolean tntUseFullRaycast;
    private boolean tntUseVanillaBlockDestruction;
    private boolean tntUseVanillaDrops;
    private boolean tntLandProtectionEnabled;
    private boolean blockLockerProtectionEnabled;
    private boolean tntMergeEnabled;
    private double tntMergeRadius;
    private int tntMaxFuseDifference;
    private float tntMergedPowerMultiplier;
    private boolean beeFixEnabled;
    private boolean asyncVillagerBreedEnabled;
    private boolean villagerAgeThrottleEnabled;
    private int villagerBreedThreads;
    private int villagerBreedCheckInterval;
    private boolean chunkTickAsyncEnabled;
    private int chunkTickThreads;
    private long chunkTickTimeoutMicros;
    private int chunkTickAsyncBatchSize;
    private boolean enableDebugLogging;
    private boolean enablePerformanceMetrics;
    private int configVersion;
    private boolean structureLocationAsyncEnabled;
    private int structureLocationThreads;
    private boolean structureAlgorithmOptimizationEnabled;
    private String structureSearchPattern;
    private boolean networkOptimizationEnabled;
    private boolean fastMovementChunkLoadEnabled;
    private double fastMovementSpeedThreshold;
    private int fastMovementPreloadDistance;
    private int fastMovementMaxConcurrentLoads;
    private boolean teleportOptimizationEnabled;
    private boolean teleportBypassQueue;
    private int teleportBoostDurationSeconds;
    private int teleportMaxChunkRate;
    private boolean teleportFilterNonEssentialPackets;
    private boolean virtualEntityCompatibilityEnabled;
    private boolean packetPriorityEnabled;
    private boolean congestionDetectionEnabled;
    private boolean chunkRateControlEnabled;
    // 网络优化详细配置
    private int highPingThreshold;
    private int criticalPingThreshold;
    private long highBandwidthThreshold;
    private int baseChunkSendRate;
    private int maxChunkSendRate;
    private int minChunkSendRate;
    private int packetSendRateBase;
    private int packetSendRateMedium;
    private int packetSendRateHeavy;
    private int packetSendRateExtreme;
    private int queueLimitMaxTotal;
    private int queueLimitMaxCritical;
    private int queueLimitMaxHigh;
    private int queueLimitMaxNormal;
    private int accelerationThresholdMedium;
    private int accelerationThresholdHeavy;
    private int accelerationThresholdExtreme;
    private boolean cleanupEnabled;
    private int cleanupStaleThreshold;
    private int cleanupCriticalCleanup;
    private int cleanupNormalCleanup;
    private boolean seedEncryptionEnabled;
    private boolean secureSeedEnabled;
    private int secureSeedBits;
    private boolean quantumSeedEnabled;
    private int quantumSeedEncryptionLevel;
    private int quantumSeedCacheSize;
    private boolean quantumSeedEnableTimeDecay;
    private boolean quantumSeedDebugLogging;
    private String seedEncryptionScheme;
    private boolean furnaceRecipeCacheEnabled;
    private boolean seedCommandRestrictionEnabled;

    public IgniteConfigManager(File dataFolder, Logger logger) {
        super(null); // 传入 null 作为 plugin，因为 Ignite 模式不使用 plugin
        this.dataFolder = dataFolder;
        this.logger = logger;
        if (!dataFolder.exists()) {
            dataFolder.mkdirs();
        }
    }

    /**
     * 初始化配置 - 复制默认配置文件并加载
     */
    public void initialize() {
        copyDefaultConfigs();
        loadConfig();
    }
    
    private void copyDefaultConfigs() {
        copyResourceIfNotExists("config.yml");
        copyResourceIfNotExists("entities.yml");
        copyResourceIfNotExists("throttling.yml");
    }
    
    private void copyResourceIfNotExists(String resourceName) {
        File targetFile = new File(dataFolder, resourceName);
        if (!targetFile.exists()) {
            try (InputStream is = getClass().getClassLoader().getResourceAsStream(resourceName)) {
                if (is != null) {
                    java.nio.file.Files.copy(is, targetFile.toPath());
                    logger.info("[AkiAsync/Ignite] 已复制默认配置: " + resourceName);
                }
            } catch (Exception e) {
                logger.warning("[AkiAsync/Ignite] 无法复制配置文件 " + resourceName + ": " + e.getMessage());
            }
        }
    }

    public void loadConfig() {
        File configFile = new File(dataFolder, "config.yml");
        if (!configFile.exists()) {
            logger.warning("[AkiAsync/Ignite] config.yml 不存在，使用默认值");
            setDefaults();
            return;
        }
        
        config = YamlConfiguration.loadConfiguration(configFile);
        
        // 加载所有配置项
        entityTrackerEnabled = config.getBoolean("entity-tracker.enabled", true);
        threadPoolSize = config.getInt("entity-tracker.thread-pool-size", 4);
        updateIntervalTicks = config.getInt("entity-tracker.update-interval-ticks", 1);
        maxQueueSize = config.getInt("entity-tracker.max-queue-size", 10000);
        
        mobSpawningEnabled = config.getBoolean("mob-spawning.enabled", true);
        spawnerOptimizationEnabled = config.getBoolean("mob-spawning.spawner-optimization", true);
        densityControlEnabled = config.getBoolean("density.enabled", true);
        maxEntitiesPerChunk = config.getInt("density.max-per-chunk", 80);
        brainThrottle = config.getBoolean("brain.throttle", true);
        brainThrottleInterval = config.getInt("brain.throttle-interval", 10);
        
        asyncAITimeoutMicros = config.getLong("async-ai.timeout-microseconds", 500L);
        villagerOptimizationEnabled = config.getBoolean("async-ai.villager-optimization.enabled", false);
        villagerUsePOISnapshot = config.getBoolean("async-ai.villager-optimization.use-poi-snapshot", true);
        piglinOptimizationEnabled = config.getBoolean("async-ai.piglin-optimization.enabled", false);
        pillagerFamilyOptimizationEnabled = config.getBoolean("async-ai.pillager-family-optimization.enabled", false);
        evokerOptimizationEnabled = config.getBoolean("async-ai.evoker-optimization.enabled", false);
        blazeOptimizationEnabled = config.getBoolean("async-ai.blaze-optimization.enabled", false);
        guardianOptimizationEnabled = config.getBoolean("async-ai.guardian-optimization.enabled", false);
        witchOptimizationEnabled = config.getBoolean("async-ai.witch-optimization.enabled", false);
        universalAiOptimizationEnabled = config.getBoolean("async-ai.universal-ai-optimization.enabled", false);
        
        asyncPathfindingEnabled = config.getBoolean("async-ai.async-pathfinding.enabled", true);
        asyncPathfindingMaxThreads = config.getInt("async-ai.async-pathfinding.max-threads", 8);
        
        entityThrottlingEnabled = config.getBoolean("entity-throttling.enabled", true);
        entityThrottlingConfigFile = config.getString("entity-throttling.config-file", "throttling.yml");
        entityThrottlingCheckInterval = config.getInt("entity-throttling.check-interval-seconds", 60);
        entityThrottlingThrottleInterval = config.getInt("entity-throttling.throttle-interval-seconds", 5);
        entityThrottlingRemovalBatchSize = config.getInt("entity-throttling.removal-batch-size", 10);
        entityTickParallel = config.getBoolean("entity-tick-parallel.enabled", true);
        entityTickThreads = config.getInt("entity-tick-parallel.threads", 4);
        
        asyncLightingEnabled = config.getBoolean("lighting-optimizations.async-lighting.enabled", true);
        lightingThreadPoolSize = config.getInt("lighting-optimizations.async-lighting.thread-pool-size", 2);
        
        alternateCurrentEnabled = config.getBoolean("redstone-optimizations.alternate-current.enabled", true);
        usePandaWireAlgorithm = config.getBoolean("redstone-optimizations.pandawire.enabled", false);
        
        tntOptimizationEnabled = config.getBoolean("tnt-explosion-optimization.enabled", true);
        tntThreads = config.getInt("tnt-explosion-optimization.threads", 6);
        tntLandProtectionEnabled = config.getBoolean("tnt-explosion-optimization.land-protection.enabled", true);
        blockLockerProtectionEnabled = config.getBoolean("tnt-explosion-optimization.blocklocker-protection.enabled", true);
        tntMergeEnabled = config.getBoolean("tnt-explosion-optimization.entity-merge.enabled", false);
        
        beeFixEnabled = config.getBoolean("bee-fix.enabled", true);
        
        asyncVillagerBreedEnabled = config.getBoolean("villager-breed-optimization.async-villager-breed", true);
        villagerBreedThreads = config.getInt("villager-breed-optimization.threads", 4);
        
        chunkTickAsyncEnabled = config.getBoolean("chunk-tick-async.enabled", false);
        chunkTickThreads = config.getInt("chunk-tick-async.threads", 4);
        chunkTickTimeoutMicros = config.getLong("chunk-tick-async.timeout-us", 200L);
        chunkTickAsyncBatchSize = config.getInt("chunk-tick-async.batch-size", 16);
        
        enableDebugLogging = config.getBoolean("performance.debug-logging", false);
        enablePerformanceMetrics = config.getBoolean("performance.enable-metrics", true);
        
        structureLocationAsyncEnabled = config.getBoolean("structure-location-async.enabled", true);
        structureLocationThreads = config.getInt("structure-location-async.threads", 3);
        structureAlgorithmOptimizationEnabled = config.getBoolean("structure-location-async.algorithm-optimization.enabled", true);
        structureSearchPattern = config.getString("structure-location-async.algorithm-optimization.search-pattern", "hybrid");
        
        networkOptimizationEnabled = config.getBoolean("network-optimization.enabled", true);
        fastMovementChunkLoadEnabled = config.getBoolean("fast-movement-chunk-load.enabled", true);
        teleportOptimizationEnabled = config.getBoolean("network-optimization.teleport-optimization.enabled", true);
        teleportBypassQueue = config.getBoolean("network-optimization.teleport-optimization.bypass-queue", true);
        teleportBoostDurationSeconds = config.getInt("network-optimization.teleport-optimization.boost-duration-seconds", 5);
        teleportMaxChunkRate = config.getInt("network-optimization.teleport-optimization.max-chunk-rate", 25);
        teleportFilterNonEssentialPackets = config.getBoolean("network-optimization.teleport-optimization.filter-non-essential-packets", true);
        virtualEntityCompatibilityEnabled = config.getBoolean("virtual-entity-compatibility.enabled", true);
        packetPriorityEnabled = config.getBoolean("network-optimization.packet-priority.enabled", true);
        chunkRateControlEnabled = config.getBoolean("network-optimization.chunk-rate-control.enabled", true);
        congestionDetectionEnabled = config.getBoolean("network-optimization.congestion-detection.enabled", true);
        
        // 网络优化详细配置（与原项目格式一致）
        highPingThreshold = config.getInt("network-optimization.congestion-detection.high-ping-threshold", 200);
        criticalPingThreshold = config.getInt("network-optimization.congestion-detection.critical-ping-threshold", 500);
        highBandwidthThreshold = config.getLong("network-optimization.congestion-detection.high-bandwidth-threshold", 1048576L);
        baseChunkSendRate = config.getInt("network-optimization.chunk-rate-control.base-rate", 10);
        maxChunkSendRate = config.getInt("network-optimization.chunk-rate-control.max-rate", 20);
        minChunkSendRate = config.getInt("network-optimization.chunk-rate-control.min-rate", 3);
        packetSendRateBase = config.getInt("network-optimization.packet-priority.send-rate.base", 50);
        packetSendRateMedium = config.getInt("network-optimization.packet-priority.send-rate.medium", 80);
        packetSendRateHeavy = config.getInt("network-optimization.packet-priority.send-rate.heavy", 110);
        packetSendRateExtreme = config.getInt("network-optimization.packet-priority.send-rate.extreme", 130);
        queueLimitMaxTotal = config.getInt("network-optimization.packet-priority.queue-limits.max-total", 2500);
        queueLimitMaxCritical = config.getInt("network-optimization.packet-priority.queue-limits.max-critical", 1200);
        queueLimitMaxHigh = config.getInt("network-optimization.packet-priority.queue-limits.max-high", 800);
        queueLimitMaxNormal = config.getInt("network-optimization.packet-priority.queue-limits.max-normal", 600);
        accelerationThresholdMedium = config.getInt("network-optimization.packet-priority.acceleration-thresholds.medium", 100);
        accelerationThresholdHeavy = config.getInt("network-optimization.packet-priority.acceleration-thresholds.heavy", 300);
        accelerationThresholdExtreme = config.getInt("network-optimization.packet-priority.acceleration-thresholds.extreme", 500);
        cleanupEnabled = config.getBoolean("network-optimization.packet-priority.cleanup.enabled", true);
        cleanupStaleThreshold = config.getInt("network-optimization.packet-priority.cleanup.stale-threshold", 5);
        cleanupCriticalCleanup = config.getInt("network-optimization.packet-priority.cleanup.critical-cleanup", 100);
        cleanupNormalCleanup = config.getInt("network-optimization.packet-priority.cleanup.normal-cleanup", 50);
        
        seedEncryptionEnabled = config.getBoolean("seed-encryption.enabled", false);
        secureSeedEnabled = config.getBoolean("seed-encryption.secure-seed.enabled", false);
        secureSeedBits = config.getInt("seed-encryption.secure-seed.bits", 1024);
        quantumSeedEnabled = config.getBoolean("seed-encryption.quantum-seed.enabled", false);
        quantumSeedEncryptionLevel = config.getInt("seed-encryption.quantum-seed.encryption-level", 3);
        seedEncryptionScheme = config.getString("seed-encryption.scheme", "none");
        
        furnaceRecipeCacheEnabled = config.getBoolean("recipe-cache.furnace.enabled", true);
        seedCommandRestrictionEnabled = config.getBoolean("seed-command-restriction.enabled", false);
        
        configVersion = config.getInt("version", CURRENT_CONFIG_VERSION);
        
        logger.info("[AkiAsync/Ignite] 配置文件已加载 (版本 " + configVersion + ")");
    }
    
    private void setDefaults() {
        entityTrackerEnabled = true;
        threadPoolSize = 4;
        tntOptimizationEnabled = true;
        tntThreads = 6;
        tntLandProtectionEnabled = true;
        asyncVillagerBreedEnabled = true;
        villagerBreedThreads = 4;
        enableDebugLogging = false;
        enablePerformanceMetrics = true;
        beeFixEnabled = true;
        usePandaWireAlgorithm = false;
        asyncPathfindingEnabled = true;
        structureLocationAsyncEnabled = true;
        structureLocationThreads = 3;
    }
    
    // Getter 方法
    public boolean isEntityTrackerEnabled() { return entityTrackerEnabled; }
    public int getThreadPoolSize() { return threadPoolSize; }
    public boolean isTNTOptimizationEnabled() { return tntOptimizationEnabled; }
    public int getTNTThreads() { return tntThreads; }
    public boolean isTNTLandProtectionEnabled() { return tntLandProtectionEnabled; }
    public boolean isBlockLockerProtectionEnabled() { return blockLockerProtectionEnabled; }
    public boolean isTNTMergeEnabled() { return tntMergeEnabled; }
    public boolean isAsyncVillagerBreedEnabled() { return asyncVillagerBreedEnabled; }
    public int getVillagerBreedThreads() { return villagerBreedThreads; }
    public boolean isDebugLoggingEnabled() { return enableDebugLogging; }
    public boolean isPerformanceMetricsEnabled() { return enablePerformanceMetrics; }
    public boolean isBeeFixEnabled() { return beeFixEnabled; }
    public boolean isUsePandaWireAlgorithm() { return usePandaWireAlgorithm; }
    public boolean isAsyncPathfindingEnabled() { return asyncPathfindingEnabled; }
    public int getAsyncPathfindingMaxThreads() { return asyncPathfindingMaxThreads; }
    public boolean isStructureLocationAsyncEnabled() { return structureLocationAsyncEnabled; }
    public int getStructureLocationThreads() { return structureLocationThreads; }
    public boolean isStructureAlgorithmOptimizationEnabled() { return structureAlgorithmOptimizationEnabled; }
    public String getStructureSearchPattern() { return structureSearchPattern; }
    public boolean isVillagerOptimizationEnabled() { return villagerOptimizationEnabled; }
    public boolean isPiglinOptimizationEnabled() { return piglinOptimizationEnabled; }
    public boolean isPillagerFamilyOptimizationEnabled() { return pillagerFamilyOptimizationEnabled; }
    public boolean isEvokerOptimizationEnabled() { return evokerOptimizationEnabled; }
    public boolean isBlazeOptimizationEnabled() { return blazeOptimizationEnabled; }
    public boolean isGuardianOptimizationEnabled() { return guardianOptimizationEnabled; }
    public boolean isWitchOptimizationEnabled() { return witchOptimizationEnabled; }
    public boolean isUniversalAiOptimizationEnabled() { return universalAiOptimizationEnabled; }
    public boolean isEntityThrottlingEnabled() { return entityThrottlingEnabled; }
    public String getEntityThrottlingConfigFile() { return entityThrottlingConfigFile; }
    public int getEntityThrottlingCheckInterval() { return entityThrottlingCheckInterval; }
    public int getEntityThrottlingThrottleInterval() { return entityThrottlingThrottleInterval; }
    public int getEntityThrottlingRemovalBatchSize() { return entityThrottlingRemovalBatchSize; }
    public boolean isEntityTickParallel() { return entityTickParallel; }
    public int getEntityTickThreads() { return entityTickThreads; }
    public boolean isAsyncLightingEnabled() { return asyncLightingEnabled; }
    public int getLightingThreadPoolSize() { return lightingThreadPoolSize; }
    public boolean isAlternateCurrentEnabled() { return alternateCurrentEnabled; }
    public boolean isNetworkOptimizationEnabled() { return networkOptimizationEnabled; }
    public boolean isFastMovementChunkLoadEnabled() { return fastMovementChunkLoadEnabled; }
    public boolean isTeleportOptimizationEnabled() { return teleportOptimizationEnabled; }
    public boolean isVirtualEntityCompatibilityEnabled() { return virtualEntityCompatibilityEnabled; }
    public boolean isSeedEncryptionEnabled() { return seedEncryptionEnabled; }
    public boolean isSecureSeedEnabled() { return secureSeedEnabled; }
    public int getSecureSeedBits() { return secureSeedBits; }
    public boolean isQuantumSeedEnabled() { return quantumSeedEnabled; }
    public int getQuantumSeedEncryptionLevel() { return quantumSeedEncryptionLevel; }
    public String getSeedEncryptionScheme() { return seedEncryptionScheme; }
    public boolean isFurnaceRecipeCacheEnabled() { return furnaceRecipeCacheEnabled; }
    public boolean isSeedCommandRestrictionEnabled() { return seedCommandRestrictionEnabled; }
    public File getDataFolder() { return dataFolder; }
    public Logger getLogger() { return logger; }
    
    // ==========================================
    // 额外 Getter 方法 - 与原 ConfigManager 对齐
    // ==========================================
    
    // Nitori 优化
    public boolean isNitoriOptimizationsEnabled() { return true; }
    public boolean isVirtualThreadEnabled() { return false; }
    public boolean isWorkStealingEnabled() { return true; }
    public boolean isBlockPosCacheEnabled() { return true; }
    public boolean isOptimizedCollectionsEnabled() { return true; }
    
    // 实体 Tick 并行
    public int getMinEntitiesForParallel() { return 50; }
    public int getEntityTickBatchSize() { return 20; }
    
    // Brain 节流
    public boolean isBrainThrottleEnabled() { return true; }
    public int getBrainThrottleInterval() { return 2; }
    
    // 生物移动优化
    public boolean isLivingEntityTravelOptimizationEnabled() { return true; }
    public int getLivingEntityTravelSkipInterval() { return 2; }
    
    // 行为节流
    public boolean isBehaviorThrottleEnabled() { return false; }
    public int getBehaviorThrottleInterval() { return 2; }
    
    // 生物消失优化
    public boolean isMobDespawnOptimizationEnabled() { return true; }
    public int getMobDespawnCheckInterval() { return 20; }
    
    // 异步 AI 超时
    public long getAsyncAITimeoutMicros() { return 5000L; }
    
    // 村民优化详细
    public boolean isVillagerUsePOISnapshot() { return true; }
    public boolean isVillagerPoiCacheEnabled() { return true; }
    public int getVillagerPoiCacheExpireTime() { return 200; }
    
    // 猪灵优化详细
    public boolean isPiglinUsePOISnapshot() { return false; }
    public int getPiglinLookDistance() { return 8; }
    public int getPiglinBarterDistance() { return 8; }
    
    // 掠夺者优化详细
    public boolean isPillagerFamilyUsePOISnapshot() { return false; }
    
    // 通用 AI 实体
    public java.util.Set<String> getUniversalAiEntities() { return java.util.Collections.emptySet(); }
    
    // DAB (距离激活行为)
    public boolean isDabEnabled() { return true; }
    public int getDabStartDistance() { return 12; }
    public int getDabActivationDistMod() { return 8; }
    public int getDabMaxTickInterval() { return 20; }
    
    // 异步路径查找详细
    public int getAsyncPathfindingKeepAliveSeconds() { return 60; }
    public int getAsyncPathfindingMaxQueueSize() { return 1000; }
    public int getAsyncPathfindingTimeoutMs() { return 50; }
    
    // 零延迟工厂优化
    public boolean isZeroDelayFactoryOptimizationEnabled() { return false; }
    public java.util.Set<String> getZeroDelayFactoryEntities() { return java.util.Collections.emptySet(); }
    
    // 方块实体并行详细
    public boolean isBlockEntityParallelTickEnabled() { return true; }
    public int getBlockEntityParallelMinBlockEntities() { return 10; }
    public int getBlockEntityParallelBatchSize() { return 50; }
    public boolean isBlockEntityParallelProtectContainers() { return true; }
    public int getBlockEntityParallelTimeoutMs() { return 100; }
    
    // 漏斗优化
    public boolean isHopperOptimizationEnabled() { return true; }
    public int getHopperCacheExpireTime() { return 40; }
    
    // 矿车优化
    public boolean isMinecartOptimizationEnabled() { return true; }
    public int getMinecartTickInterval() { return 2; }
    
    // 简单实体优化
    public boolean isSimpleEntitiesOptimizationEnabled() { return false; }
    public boolean isSimpleEntitiesUsePOISnapshot() { return false; }
    
    // 生物生成
    public boolean isMobSpawningEnabled() { return true; }
    public boolean isDensityControlEnabled() { return true; }
    public int getMaxEntitiesPerChunk() { return 50; }
    public boolean isSpawnerOptimizationEnabled() { return true; }
    
    // 光照引擎详细
    public int getLightBatchThreshold() { return 64; }
    public boolean useLayeredPropagationQueue() { return true; }
    public int getMaxLightPropagationDistance() { return 15; }
    public boolean isSkylightCacheEnabled() { return true; }
    public int getSkylightCacheDurationMs() { return 100; }
    public boolean isLightDeduplicationEnabled() { return true; }
    public boolean isDynamicBatchAdjustmentEnabled() { return true; }
    public boolean isAdvancedLightingStatsEnabled() { return false; }
    
    // 区块加载优化
    public boolean isPlayerChunkLoadingOptimizationEnabled() { return true; }
    public int getMaxConcurrentChunkLoadsPerPlayer() { return 4; }
    
    // 实体追踪范围
    public boolean isEntityTrackingRangeOptimizationEnabled() { return true; }
    public double getEntityTrackingRangeMultiplier() { return 1.0; }
    
    // 红石优化详细
    public boolean isRedstoneWireTurboEnabled() { return true; }
    public boolean isRedstoneUpdateBatchingEnabled() { return true; }
    public int getRedstoneUpdateBatchThreshold() { return 64; }
    public boolean isRedstoneCacheEnabled() { return true; }
    public int getRedstoneCacheDurationMs() { return 50; }
    public boolean isRedstoneNetworkCacheEnabled() { return false; }
    public int getRedstoneNetworkCacheExpireTicks() { return 20; }
    
    // TNT 优化详细
    public java.util.Set<String> getTNTExplosionEntities() { 
        java.util.Set<String> set = new java.util.HashSet<>();
        set.add("minecraft:tnt");
        set.add("minecraft:tnt_minecart");
        return set;
    }
    public int getTNTMaxBlocks() { return 500; }
    public long getTNTTimeoutMicros() { return 10000L; }
    public int getTNTBatchSize() { return 50; }
    public boolean isTNTDebugEnabled() { return false; }
    public boolean isTNTVanillaCompatibilityEnabled() { return true; }
    public boolean isTNTUseVanillaPower() { return true; }
    public boolean isTNTUseVanillaFireLogic() { return true; }
    public boolean isTNTUseVanillaDamageCalculation() { return true; }
    public boolean isTNTUseFullRaycast() { return false; }
    public boolean isTNTUseVanillaBlockDestruction() { return true; }
    public boolean isTNTUseVanillaDrops() { return true; }
    public boolean isTNTUseSakuraDensityCache() { return true; }
    public double getTNTMergeRadius() { return 0.5; }
    public int getTNTMaxFuseDifference() { return 5; }
    public float getTNTMergedPowerMultiplier() { return 1.0f; }
    
    // 村民繁殖详细
    public boolean isVillagerAgeThrottleEnabled() { return true; }
    public int getVillagerBreedCheckInterval() { return 40; }
    
    // 区块 Tick 异步
    public boolean isChunkTickAsyncEnabled() { return chunkTickAsyncEnabled; }
    public int getChunkTickThreads() { return chunkTickThreads; }
    public long getChunkTickTimeoutMicros() { return chunkTickTimeoutMicros; }
    public int getChunkTickAsyncBatchSize() { return chunkTickAsyncBatchSize; }
    
    // 结构定位详细
    public boolean isLocateCommandEnabled() { return true; }
    public int getLocateCommandSearchRadius() { return 100; }
    public boolean isLocateCommandSkipKnownStructures() { return false; }
    public boolean isVillagerTradeMapsEnabled() { return true; }
    public java.util.Set<String> getVillagerTradeMapTypes() { return java.util.Collections.emptySet(); }
    public int getVillagerMapGenerationTimeoutSeconds() { return 30; }
    public boolean isDolphinTreasureHuntEnabled() { return true; }
    public int getDolphinTreasureSearchRadius() { return 50; }
    public boolean isChestExplorationMapsEnabled() { return true; }
    public java.util.Set<String> getChestExplorationLootTables() { return java.util.Collections.emptySet(); }
    public boolean isStructureLocationDebugEnabled() { return false; }
    public boolean isStructureCachingEnabled() { return true; }
    public boolean isBiomeAwareSearchEnabled() { return true; }
    public int getStructureCacheMaxSize() { return 1000; }
    public long getStructureCacheExpirationMinutes() { return 30L; }
    public int getVillagerTradeMapsSearchRadius() { return 100; }
    public boolean isVillagerTradeMapsSkipKnownStructures() { return false; }
    
    // DataPack 优化
    public boolean isDataPackOptimizationEnabled() { return true; }
    public int getDataPackFileLoadThreads() { return 2; }
    public int getDataPackZipProcessThreads() { return 2; }
    public int getDataPackBatchSize() { return 10; }
    public long getDataPackCacheExpirationMinutes() { return 30L; }
    public int getDataPackMaxFileCacheSize() { return 100; }
    public int getDataPackMaxFileSystemCacheSize() { return 50; }
    public boolean isDataPackDebugEnabled() { return false; }
    
    // 掉落方块并行
    public boolean isFallingBlockParallelEnabled() { return false; }
    
    // 物品实体并行
    public boolean isItemEntityParallelEnabled() { return false; }
    public boolean isItemEntityMergeOptimizationEnabled() { return false; }
    
    // 矿车熔炉
    public boolean isMinecartCauldronDestructionEnabled() { return false; }
    
    // 中心偏移加载
    public boolean isCenterOffsetEnabled() { return false; }
    
    // 最大队列大小
    public int getMaxQueueSize() { return 10000; }
    
    // ==========================================
    // 配方缓存
    // ==========================================
    public int getFurnaceRecipeCacheSize() { return 100; }
    public boolean isFurnaceCacheApplyToBlastFurnace() { return true; }
    public boolean isFurnaceCacheApplyToSmoker() { return true; }
    public boolean isFurnaceFixBurnTimeBug() { return true; }
    public boolean isCraftingRecipeCacheEnabled() { return true; }
    public int getCraftingRecipeCacheSize() { return 200; }
    public boolean isCraftingOptimizeBatchCrafting() { return true; }
    public boolean isCraftingReduceNetworkTraffic() { return true; }
    
    // ==========================================
    // 掉落方块详细
    // ==========================================
    public int getMinFallingBlocksForParallel() { return 10; }
    public int getFallingBlockBatchSize() { return 50; }
    
    // ==========================================
    // 物品实体详细
    // ==========================================
    public int getMinItemEntitiesForParallel() { return 20; }
    public int getItemEntityBatchSize() { return 100; }
    public int getItemEntityMergeInterval() { return 40; }
    public int getItemEntityMinNearbyItems() { return 3; }
    public double getItemEntityMergeRange() { return 2.0; }
    public boolean isItemEntityAgeOptimizationEnabled() { return false; }
    public int getItemEntityAgeInterval() { return 100; }
    public double getItemEntityPlayerDetectionRange() { return 32.0; }
    
    // ==========================================
    // 网络优化详细
    // ==========================================
    public boolean isPacketPriorityEnabled() { return packetPriorityEnabled; }
    public boolean isChunkRateControlEnabled() { return chunkRateControlEnabled; }
    public boolean isCongestionDetectionEnabled() { return congestionDetectionEnabled; }
    public int getHighPingThreshold() { return highPingThreshold; }
    public int getCriticalPingThreshold() { return criticalPingThreshold; }
    public long getHighBandwidthThreshold() { return highBandwidthThreshold; }
    public int getBaseChunkSendRate() { return baseChunkSendRate; }
    public int getMaxChunkSendRate() { return maxChunkSendRate; }
    public int getMinChunkSendRate() { return minChunkSendRate; }
    public int getPacketSendRateBase() { return packetSendRateBase; }
    public int getPacketSendRateMedium() { return packetSendRateMedium; }
    public int getPacketSendRateHeavy() { return packetSendRateHeavy; }
    public int getPacketSendRateExtreme() { return packetSendRateExtreme; }
    public int getQueueLimitMaxTotal() { return queueLimitMaxTotal; }
    public int getQueueLimitMaxCritical() { return queueLimitMaxCritical; }
    public int getQueueLimitMaxHigh() { return queueLimitMaxHigh; }
    public int getQueueLimitMaxNormal() { return queueLimitMaxNormal; }
    public int getAccelerationThresholdMedium() { return accelerationThresholdMedium; }
    public int getAccelerationThresholdHeavy() { return accelerationThresholdHeavy; }
    public int getAccelerationThresholdExtreme() { return accelerationThresholdExtreme; }
    public boolean isCleanupEnabled() { return cleanupEnabled; }
    public int getCleanupStaleThreshold() { return cleanupStaleThreshold; }
    public int getCleanupCriticalCleanup() { return cleanupCriticalCleanup; }
    public int getCleanupNormalCleanup() { return cleanupNormalCleanup; }
    
    // ==========================================
    // 快速移动详细
    // ==========================================
    public double getFastMovementSpeedThreshold() { return 0.5; }
    public int getFastMovementPreloadDistance() { return 3; }
    public int getFastMovementMaxConcurrentLoads() { return 5; }
    public int getFastMovementPredictionTicks() { return 10; }
    public double getMinOffsetSpeed() { return 0.1; }
    public double getMaxOffsetSpeed() { return 1.0; }
    public double getMaxOffsetRatio() { return 0.5; }
    public int getAsyncLoadingBatchSize() { return 5; }
    public long getAsyncLoadingBatchDelayMs() { return 50L; }
    
    // ==========================================
    // 传送优化详细
    // ==========================================
    public boolean isTeleportPacketBypassEnabled() { return teleportBypassQueue; }
    public int getTeleportBoostDurationSeconds() { return teleportBoostDurationSeconds; }
    public int getTeleportMaxChunkRate() { return teleportMaxChunkRate; }
    public boolean isTeleportFilterNonEssentialPackets() { return teleportFilterNonEssentialPackets; }
    
    // ==========================================
    // 视锥过滤
    // ==========================================
    public boolean isViewFrustumFilterEnabled() { return false; }
    
    // ==========================================
    // 窒息优化和地图渲染
    // ==========================================
    public boolean isSuffocationOptimizationEnabled() { return true; }
    public boolean isFastRayTraceEnabled() { return true; }
    public boolean isMapRenderingOptimizationEnabled() { return false; }
    public int getMapRenderingThreads() { return 2; }
    
    // ==========================================
    // AI 空间索引
    // ==========================================
    public boolean isAiSpatialIndexEnabled() { return false; }
    public int getAiSpatialIndexGridSize() { return 16; }
    public boolean isAiSpatialIndexAutoUpdate() { return true; }
    public boolean isAiSpatialIndexPlayerIndexEnabled() { return true; }
    public boolean isAiSpatialIndexPoiIndexEnabled() { return true; }
    public boolean isAiSpatialIndexStatisticsEnabled() { return false; }
    public int getAiSpatialIndexLogIntervalSeconds() { return 60; }
    
    // ==========================================
    // 额外生物优化
    // ==========================================
    public boolean isWanderingTraderOptimizationEnabled() { return false; }
    public boolean isWardenOptimizationEnabled() { return false; }
    public boolean isHoglinOptimizationEnabled() { return false; }
    public boolean isAllayOptimizationEnabled() { return false; }
    
    // ==========================================
    // Brain 优化
    // ==========================================
    public boolean isBrainMemoryOptimizationEnabled() { return false; }
    public boolean isPoiSnapshotEnabled() { return true; }
    
    // ==========================================
    // 增强路径查找
    // ==========================================
    public boolean isAsyncPathfindingSyncFallbackEnabled() { return true; }
    public boolean isEnhancedPathfindingEnabled() { return false; }
    public int getEnhancedPathfindingMaxConcurrentRequests() { return 100; }
    public int getEnhancedPathfindingMaxRequestsPerTick() { return 20; }
    public int getEnhancedPathfindingHighPriorityDistance() { return 16; }
    public int getEnhancedPathfindingMediumPriorityDistance() { return 32; }
    public boolean isPathPrewarmEnabled() { return false; }
    public int getPathPrewarmRadius() { return 48; }
    public int getPathPrewarmMaxMobsPerBatch() { return 10; }
    public int getPathPrewarmMaxPoisPerMob() { return 3; }
    
    // ==========================================
    // 修复
    // ==========================================
    public boolean isEndIslandDensityFixEnabled() { return true; }
    public boolean isPortalSuffocationCheckDisabled() { return false; }
    public boolean isShulkerBulletSelfHitFixEnabled() { return true; }
    
    // ==========================================
    // 流体优化
    // ==========================================
    public boolean isFluidOptimizationEnabled() { return false; }
    public boolean isFluidDebugEnabled() { return false; }
    public boolean isFluidTickThrottleEnabled() { return false; }
    public int getStaticFluidInterval() { return 5; }
    public int getFlowingFluidInterval() { return 2; }
    public boolean isFluidTickCompensationEnabled() { return false; }
    public boolean isFluidCompensationEnabledForWater() { return true; }
    public boolean isFluidCompensationEnabledForLava() { return true; }
    public double getFluidCompensationTPSThreshold() { return 18.0; }
    
    // ==========================================
    // 智能延迟补偿
    // ==========================================
    public boolean isSmartLagCompensationEnabled() { return false; }
    public double getSmartLagTPSThreshold() { return 18.0; }
    public boolean isSmartLagFluidCompensationEnabled() { return false; }
    public boolean isSmartLagFluidWaterEnabled() { return true; }
    public boolean isSmartLagFluidLavaEnabled() { return true; }
    public boolean isSmartLagItemPickupDelayEnabled() { return false; }
    public boolean isSmartLagPotionEffectsEnabled() { return false; }
    public boolean isSmartLagTimeAccelerationEnabled() { return false; }
    public boolean isSmartLagDebugEnabled() { return false; }
    public boolean isSmartLagLogMissedTicks() { return false; }
    public boolean isSmartLagLogCompensation() { return false; }
    
    // ==========================================
    // 经验球优化
    // ==========================================
    public boolean isExperienceOrbInactiveTickEnabled() { return false; }
    public double getExperienceOrbInactiveRange() { return 32.0; }
    public int getExperienceOrbInactiveMergeInterval() { return 100; }
    
    // ==========================================
    // 生物生成间隔
    // ==========================================
    public int getMobSpawnInterval() { return 1; }
    
    // ==========================================
    // 物品实体额外配置
    // ==========================================
    public boolean isItemEntityCancelVanillaMerge() { return false; }
    public boolean isItemEntityInactiveTickEnabled() { return false; }
    public double getItemEntityInactiveRange() { return 32.0; }
    public int getItemEntityInactiveMergeInterval() { return 100; }
    
    // ==========================================
    // Execute 命令优化
    // ==========================================
    public boolean isExecuteCommandInactiveSkipEnabled() { return false; }
    public int getExecuteCommandSkipLevel() { return 2; }
    public double getExecuteCommandSimulationDistanceMultiplier() { return 1.0; }
    public long getExecuteCommandCacheDurationMs() { return 100; }
    public java.util.Set<String> getExecuteCommandWhitelistTypes() { return java.util.Collections.emptySet(); }
    public boolean isExecuteCommandDebugEnabled() { return false; }
    public boolean isCommandDeduplicationEnabled() { return false; }
    public boolean isCommandDeduplicationDebugEnabled() { return false; }
    
    // ==========================================
    // 碰撞优化
    // ==========================================
    public boolean isCollisionOptimizationEnabled() { return true; }
    public boolean isCollisionAggressiveMode() { return false; }
    public java.util.Set<String> getCollisionExcludedEntities() { return java.util.Collections.emptySet(); }
    public boolean isCollisionCacheEnabled() { return true; }
    public int getCollisionCacheLifetimeMs() { return 50; }
    public double getCollisionCacheMovementThreshold() { return 0.01; }
    public boolean isCollisionSpatialPartitionEnabled() { return true; }
    public int getCollisionSpatialGridSize() { return 4; }
    public int getCollisionSpatialDensityThreshold() { return 50; }
    public int getCollisionSpatialUpdateIntervalMs() { return 100; }
    public double getCollisionSkipMinMovement() { return 0.001; }
    public int getCollisionSkipCheckIntervalMs() { return 50; }
    
    // ==========================================
    // 推挤优化
    // ==========================================
    public boolean isPushOptimizationEnabled() { return true; }
    public double getPushMaxPushPerTick() { return 0.5; }
    public double getPushDampingFactor() { return 0.8; }
    public int getPushHighDensityThreshold() { return 10; }
    public double getPushHighDensityMultiplier() { return 0.5; }
    
    // ==========================================
    // 高级碰撞优化
    // ==========================================
    public boolean isAdvancedCollisionOptimizationEnabled() { return false; }
    public int getCollisionThreshold() { return 10; }
    public float getSuffocationDamage() { return 1.0f; }
    public int getMaxPushIterations() { return 3; }
    
    // ==========================================
    // 光照优先级调度
    // ==========================================
    public boolean isLightingPrioritySchedulingEnabled() { return false; }
    public int getLightingHighPriorityRadius() { return 2; }
    public int getLightingMediumPriorityRadius() { return 4; }
    public int getLightingLowPriorityRadius() { return 8; }
    public long getLightingMaxLowPriorityDelay() { return 1000; }
    
    // ==========================================
    // 光照去抖动
    // ==========================================
    public boolean isLightingDebouncingEnabled() { return false; }
    public long getLightingDebounceDelay() { return 50; }
    public int getLightingMaxUpdatesPerSecond() { return 1000; }
    public long getLightingResetOnStableMs() { return 500; }
    
    // ==========================================
    // 光照合并
    // ==========================================
    public boolean isLightingMergingEnabled() { return false; }
    public int getLightingMergeRadius() { return 2; }
    public long getLightingMergeDelay() { return 20; }
    public int getLightingMaxMergedUpdates() { return 100; }
    
    // ==========================================
    // 光照区块边界
    // ==========================================
    public boolean isLightingChunkBorderEnabled() { return false; }
    public boolean isLightingBatchBorderUpdates() { return false; }
    public long getLightingBorderUpdateDelay() { return 100; }
    public int getLightingCrossChunkBatchSize() { return 16; }
    
    // ==========================================
    // 自适应光照
    // ==========================================
    public boolean isLightingAdaptiveEnabled() { return false; }
    public int getLightingMonitorInterval() { return 100; }
    public boolean isLightingAutoAdjustThreads() { return false; }
    public boolean isLightingAutoAdjustBatchSize() { return false; }
    public int getLightingTargetQueueSize() { return 100; }
    public int getLightingTargetLatency() { return 50; }
    
    // ==========================================
    // 光照区块卸载
    // ==========================================
    public boolean isLightingChunkUnloadEnabled() { return false; }
    public boolean isLightingAsyncCleanup() { return false; }
    public int getLightingCleanupBatchSize() { return 16; }
    public long getLightingCleanupDelay() { return 1000; }
    
    // ==========================================
    // 光照线程池配置
    // ==========================================
    public String getLightingThreadPoolMode() { return "fixed"; }
    public String getLightingThreadPoolCalculation() { return "cores"; }
    public int getLightingMinThreads() { return 1; }
    public int getLightingMaxThreads() { return 4; }
    public int getLightingBatchThresholdMax() { return 256; }
    public boolean isLightingAggressiveBatching() { return false; }
    
    // ==========================================
    // 噪声和拼图优化
    // ==========================================
    public boolean isNoiseOptimizationEnabled() { return false; }
    public boolean isJigsawOptimizationEnabled() { return false; }
    
    // ==========================================
    // 实体数据包节流
    // ==========================================
    public boolean isEntityPacketThrottleEnabled() { return false; }
    public boolean isEntityDataThrottleEnabled() { return false; }
    
    // ==========================================
    // 区块可见性过滤
    // ==========================================
    public boolean isChunkVisibilityFilterEnabled() { return false; }
    
    // ==========================================
    // Setter 方法 (用于运行时修改)
    // ==========================================
    public void setDebugLoggingEnabled(boolean enabled) {
        this.enableDebugLogging = enabled;
        logger.info("[AkiAsync/Ignite] Debug logging " + (enabled ? "enabled" : "disabled"));
    }
}
