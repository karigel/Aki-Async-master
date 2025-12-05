package org.virgil.akiasync.config;

import org.bukkit.configuration.file.FileConfiguration;
import org.virgil.akiasync.AkiAsyncPlugin;

public class ConfigManager {
    
    private static final int CURRENT_CONFIG_VERSION = 14;
    
    private final AkiAsyncPlugin plugin;
    private java.util.logging.Logger logger; // ç¨äşçŹçŤć¨Ąĺź
    private FileConfiguration config;
    private boolean entityTrackerEnabled;
    private int threadPoolSize;
    private int updateIntervalTicks;
    private int maxQueueSize;
    private boolean mobSpawningEnabled;
    private boolean spawnerOptimizationEnabled;
    private int maxEntitiesPerChunk;
    private int aiCooldownTicks;
    private boolean brainThrottle;
    private int brainThrottleInterval;
    private long asyncAITimeoutMicros;
    private boolean villagerOptimizationEnabled;
    private boolean villagerUsePOISnapshot;
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
    private java.util.Set<String> universalAiEntities;
    private boolean zeroDelayFactoryOptimizationEnabled;
    private java.util.Set<String> zeroDelayFactoryEntities;
    private boolean blockEntityParallelTickEnabled;
    private int blockEntityParallelMinBlockEntities;
    private int blockEntityParallelBatchSize;
    private boolean blockEntityParallelProtectContainers;
    private int blockEntityParallelTimeoutMs;
    private boolean itemEntityOptimizationEnabled;
    private int itemEntityAgeInterval;
    private int itemEntityMinNearbyItems;
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
    private boolean tntOptimizationEnabled;
    private java.util.Set<String> tntExplosionEntities;
    private int tntThreads;
    private int tntMaxBlocks;
    private long tntTimeoutMicros;
    private int tntBatchSize;
    private boolean tntVanillaCompatibilityEnabled;
    private boolean tntUseVanillaPower;
    private boolean tntUseVanillaFireLogic;
    private boolean tntUseVanillaDamageCalculation;
    private boolean beeFixEnabled;
    private boolean tntUseFullRaycast;
    private boolean tntUseVanillaBlockDestruction;
    private boolean tntUseVanillaDrops;
    private boolean asyncVillagerBreedEnabled;
    private boolean villagerAgeThrottleEnabled;
    private int villagerBreedThreads;
    private int villagerBreedCheckInterval;
    private boolean chunkTickAsyncEnabled;
    private int chunkTickThreads;
    private long chunkTickTimeoutMicros;
    private boolean enableDebugLogging;
    private boolean enablePerformanceMetrics;
    private int configVersion;
    
    private boolean structureLocationAsyncEnabled;
    private int structureLocationThreads;
    private boolean locateCommandEnabled;
    private int locateCommandSearchRadius;
    private boolean locateCommandSkipKnownStructures;
    private boolean villagerTradeMapsEnabled;
    private java.util.Set<String> villagerTradeMapTypes;
    private int villagerMapGenerationTimeoutSeconds;
    private boolean dolphinTreasureHuntEnabled;
    private int dolphinTreasureSearchRadius;
    private int dolphinTreasureHuntInterval;
    private boolean chestExplorationMapsEnabled;
    private java.util.Set<String> chestExplorationLootTables;
    private boolean chestMapPreserveProbability;
    
    private boolean structureAlgorithmOptimizationEnabled;
    private String structureSearchPattern;
    private boolean structureCachingEnabled;
    private boolean structurePrecomputationEnabled;
    private boolean biomeAwareSearchEnabled;
    private int structureCacheMaxSize;
    private long structureCacheExpirationMinutes;
    
    private boolean dataPackOptimizationEnabled;
    private int dataPackFileLoadThreads;
    private int dataPackZipProcessThreads;
    private int dataPackBatchSize;
    private long dataPackCacheExpirationMinutes;
    private boolean dataPackDebugEnabled;

    private boolean nitoriOptimizationsEnabled;
    private boolean virtualThreadEnabled;
    private boolean workStealingEnabled;
    private boolean blockPosCacheEnabled;
    private boolean optimizedCollectionsEnabled;
    
    private boolean secureSeedEnabled;
    private boolean secureSeedProtectStructures;
    private boolean secureSeedProtectOres;
    private boolean secureSeedProtectSlimes;
    private int secureSeedBits;
    
    // ==========================================
    // ???????? / Async Pathfinding Config (v8.0)
    // ==========================================
    private boolean asyncPathfindingEnabled;
    private int asyncPathfindingMaxThreads;
    private long asyncPathfindingKeepAliveSeconds;
    private int asyncPathfindingMaxQueueSize;
    private int asyncPathfindingTimeoutMs;
    
    // ==========================================
    // ?????? / Density Control Config (v8.0)
    // ==========================================
    private boolean densityControlEnabled;
    
    // ==========================================
    // TNT ?????? / TNT Land Protection Config (v8.0)
    // ==========================================
    private boolean tntLandProtectionEnabled;
    
    // ==========================================
    // Suffocation Optimization Config (v3.2.15)
    // ==========================================
    private boolean suffocationOptimizationEnabled;
    
    // ==========================================
    // BlockLocker Protection Config (v3.2.15)
    // ==========================================
    private boolean blockLockerProtectionEnabled;
    
    // ==========================================
    // TNT Sakura Optimization Config (v3.2.16)
    // ==========================================
    private boolean tntUseSakuraDensityCache;
    private boolean tntMergeEnabled;
    private double tntMergeRadius;
    private int tntMaxFuseDifference;
    private float tntMergedPowerMultiplier;
    
    // ==========================================
    // Redstone Sakura Optimization Config (v3.2.16)
    // ==========================================
    private boolean usePandaWireAlgorithm;
    private boolean redstoneNetworkCacheEnabled;
    private int redstoneNetworkCacheExpireTicks;
    
    // ==========================================
    // SecureSeed Configuration (v3.2.16)
    // ==========================================
    private boolean seedEncryptionEnabled;
    private String seedCommandDenyMessage;
    
    // ==========================================
    // Falling Block Parallel Config (v14.0)
    // ==========================================
    private boolean fallingBlockParallelEnabled;
    private int minFallingBlocksForParallel;
    private int fallingBlockBatchSize;
    
    // ==========================================
    // Item Entity Parallel Config (v14.0)
    // ==========================================
    private boolean itemEntityParallelEnabled;
    private int minItemEntitiesForParallel;
    private int itemEntityBatchSize;
    private boolean itemEntityMergeOptimizationEnabled;
    private int itemEntityMergeInterval;
    private double itemEntityMergeRange;
    private boolean itemEntityAgeOptimizationEnabled;
    private double itemEntityPlayerDetectionRange;
    
    // ==========================================
    // Minecart Cauldron Destruction Config (v14.0)
    // ==========================================
    private boolean minecartCauldronDestructionEnabled;
    
    // ==========================================
    // Network Optimization Config (v14.0)
    // ==========================================
    private boolean networkOptimizationEnabled;
    private boolean packetPriorityEnabled;
    private boolean chunkRateControlEnabled;
    private boolean congestionDetectionEnabled;
    private boolean viewFrustumFilterEnabled;
    private boolean viewFrustumFilterEntities;
    private boolean viewFrustumFilterBlocks;
    private boolean viewFrustumFilterParticles;
    private int highPingThreshold;
    private int criticalPingThreshold;
    private long highBandwidthThreshold;
    private int baseChunkSendRate;
    private int maxChunkSendRate;
    private int minChunkSendRate;
    
    // ==========================================
    // Fast Movement Chunk Load Config
    // ==========================================
    private boolean fastMovementChunkLoadEnabled;
    private double fastMovementSpeedThreshold;
    private int fastMovementPreloadDistance;
    private int fastMovementMaxConcurrentLoads;
    private int fastMovementPredictionTicks;
    // Center Offset Config
    private boolean centerOffsetEnabled;
    private double centerOffsetMinSpeed;
    private double centerOffsetMaxSpeed;
    private double centerOffsetMaxRatio;
    // Async Loading Config
    private int asyncLoadingBatchSize;
    private long asyncLoadingBatchDelayMs;
    // Teleport Optimization Config
    private boolean teleportOptimizationEnabled;
    private boolean teleportPacketBypassEnabled;
    private int teleportBoostDurationSeconds;
    private int teleportMaxChunkRate;
    private boolean teleportFilterNonEssentialPackets;
    private boolean teleportDebugEnabled;
    // Virtual Entity Compatibility Config
    private boolean virtualEntityCompatibilityEnabled;
    private boolean virtualEntityDebugEnabled;
    private boolean virtualEntityBypassPacketQueue;
    private boolean virtualEntityExcludeFromThrottling;
    private boolean fancynpcsCompatEnabled;
    private boolean fancynpcsUseAPI;
    private int fancynpcsPriority;
    private boolean znpcsplusCompatEnabled;
    private int znpcsplusPriority;
    
    public ConfigManager(AkiAsyncPlugin plugin) {
        this.plugin = plugin;
    }
    
    public void loadConfig() {
        if (plugin != null) {
            plugin.saveDefaultConfig();
            plugin.reloadConfig();
            config = plugin.getConfig();
        }
        // ĺŚć plugin ä¸?nullďźconfig ĺşčŻĽĺˇ˛çťéčż setConfig() čŽžç˝Ž
        if (config == null) {
            throw new IllegalStateException("ConfigManager: plugin is null and config is not set");
        }
        entityTrackerEnabled = config.getBoolean("entity-tracker.enabled", true);
        threadPoolSize = config.getInt("entity-tracker.thread-pool-size", 4);
        updateIntervalTicks = config.getInt("entity-tracker.update-interval-ticks", 1);
        maxQueueSize = config.getInt("entity-tracker.max-queue-size", 1000);
        mobSpawningEnabled = config.getBoolean("mob-spawning.enabled", true);
        spawnerOptimizationEnabled = config.getBoolean("mob-spawning.spawner-optimization", true);
        maxEntitiesPerChunk = config.getInt("density.max-per-chunk", 80);
        aiCooldownTicks = config.getInt("density.ai-cooldown-ticks", 10);
        brainThrottle = config.getBoolean("brain.throttle", true);
        brainThrottleInterval = config.getInt("brain.throttle-interval", 10);
        asyncAITimeoutMicros = config.getLong("async-ai.timeout-microseconds", 500L);
        villagerOptimizationEnabled = config.getBoolean("async-ai.villager-optimization.enabled", false);
        villagerUsePOISnapshot = config.getBoolean("async-ai.villager-optimization.use-poi-snapshot", true);
        piglinOptimizationEnabled = config.getBoolean("async-ai.piglin-optimization.enabled", false);
        piglinUsePOISnapshot = config.getBoolean("async-ai.piglin-optimization.use-poi-snapshot", false);
        piglinLookDistance = config.getInt("async-ai.piglin-optimization.look-distance", 16);
        piglinBarterDistance = config.getInt("async-ai.piglin-optimization.barter-distance", 16);
        pillagerFamilyOptimizationEnabled = config.getBoolean("async-ai.pillager-family-optimization.enabled", false);
        pillagerFamilyUsePOISnapshot = config.getBoolean("async-ai.pillager-family-optimization.use-poi-snapshot", false);
        evokerOptimizationEnabled = config.getBoolean("async-ai.evoker-optimization.enabled", false);
        blazeOptimizationEnabled = config.getBoolean("async-ai.blaze-optimization.enabled", false);
        guardianOptimizationEnabled = config.getBoolean("async-ai.guardian-optimization.enabled", false);
        witchOptimizationEnabled = config.getBoolean("async-ai.witch-optimization.enabled", false);
        universalAiOptimizationEnabled = config.getBoolean("async-ai.universal-ai-optimization.enabled", false);
        universalAiEntities = new java.util.HashSet<>(config.getStringList("async-ai.universal-ai-optimization.entities"));
        zeroDelayFactoryOptimizationEnabled = config.getBoolean("block-entity-optimizations.zero-delay-factory-optimization.enabled", false);
        zeroDelayFactoryEntities = new java.util.HashSet<>(config.getStringList("block-entity-optimizations.zero-delay-factory-optimization.entities"));
        blockEntityParallelTickEnabled = config.getBoolean("block-entity-optimizations.parallel-tick.enabled", true);
        blockEntityParallelMinBlockEntities = config.getInt("block-entity-optimizations.parallel-tick.min-block-entities", 50);
        blockEntityParallelBatchSize = config.getInt("block-entity-optimizations.parallel-tick.batch-size", 16);
        blockEntityParallelProtectContainers = config.getBoolean("block-entity-optimizations.parallel-tick.protect-containers", true);
        blockEntityParallelTimeoutMs = config.getInt("block-entity-optimizations.parallel-tick.timeout-ms", 50);
        itemEntityOptimizationEnabled = config.getBoolean("item-entity-optimizations.enabled", true);
        itemEntityAgeInterval = config.getInt("item-entity-optimizations.age-increment-interval", 10);
        itemEntityMinNearbyItems = config.getInt("item-entity-optimizations.min-nearby-items", 3);
        simpleEntitiesOptimizationEnabled = config.getBoolean("async-ai.simple-entities.enabled", false);
        simpleEntitiesUsePOISnapshot = config.getBoolean("async-ai.simple-entities.use-poi-snapshot", false);
        entityTickParallel = config.getBoolean("entity-tick-parallel.enabled", true);
        entityTickThreads = config.getInt("entity-tick-parallel.threads", 4);
        minEntitiesForParallel = config.getInt("entity-tick-parallel.min-entities", 100);
        entityTickBatchSize = config.getInt("entity-tick-parallel.batch-size", 8);
        asyncLightingEnabled = config.getBoolean("lighting-optimizations.async-lighting.enabled", true);
        lightingThreadPoolSize = config.getInt("lighting-optimizations.async-lighting.thread-pool-size", 2);
        lightBatchThreshold = config.getInt("lighting-optimizations.async-lighting.batch-threshold", 16);
        useLayeredPropagationQueue = config.getBoolean("lighting-optimizations.propagation-queue.use-layered-queue", true);
        maxLightPropagationDistance = config.getInt("lighting-optimizations.propagation-queue.max-propagation-distance", 15);
        skylightCacheEnabled = config.getBoolean("lighting-optimizations.skylight-cache.enabled", true);
        skylightCacheDurationMs = config.getInt("lighting-optimizations.skylight-cache.cache-duration-ms", 100);
        lightDeduplicationEnabled = config.getBoolean("lighting-optimizations.advanced.enable-deduplication", true);
        dynamicBatchAdjustmentEnabled = config.getBoolean("lighting-optimizations.advanced.dynamic-batch-adjustment", true);
        advancedLightingStatsEnabled = config.getBoolean("lighting-optimizations.advanced.log-advanced-stats", false);
        playerChunkLoadingOptimizationEnabled = config.getBoolean("vmp-optimizations.chunk-loading.enabled", true);
        maxConcurrentChunkLoadsPerPlayer = config.getInt("vmp-optimizations.chunk-loading.max-concurrent-per-player", 5);
        entityTrackingRangeOptimizationEnabled = config.getBoolean("vmp-optimizations.entity-tracking.enabled", true);
        entityTrackingRangeMultiplier = config.getDouble("vmp-optimizations.entity-tracking.range-multiplier", 0.8);
        alternateCurrentEnabled = config.getBoolean("redstone-optimizations.alternate-current.enabled", true);
        redstoneWireTurboEnabled = config.getBoolean("redstone-optimizations.wire-turbo.enabled", true);
        redstoneUpdateBatchingEnabled = config.getBoolean("redstone-optimizations.update-batching.enabled", true);
        redstoneUpdateBatchThreshold = config.getInt("redstone-optimizations.update-batching.batch-threshold", 8);
        redstoneCacheEnabled = config.getBoolean("redstone-optimizations.cache.enabled", true);
        redstoneCacheDurationMs = config.getInt("redstone-optimizations.cache.duration-ms", 50);
        asyncVillagerBreedEnabled = config.getBoolean("villager-breed-optimization.async-villager-breed", true);
        villagerAgeThrottleEnabled = config.getBoolean("villager-breed-optimization.age-throttle", true);
        villagerBreedThreads = config.getInt("villager-breed-optimization.threads", 4);
        villagerBreedCheckInterval = config.getInt("villager-breed-optimization.check-interval", 5);
        chunkTickAsyncEnabled = config.getBoolean("chunk-tick-async.enabled", false);
        chunkTickThreads = config.getInt("chunk-tick-async.threads", 4);
        chunkTickTimeoutMicros = config.getLong("chunk-tick-async.timeout-us", 200L);
        tntOptimizationEnabled = config.getBoolean("tnt-explosion-optimization.enabled", true);
        tntExplosionEntities = new java.util.HashSet<>(config.getStringList("tnt-explosion-optimization.entities"));
        if (tntExplosionEntities.isEmpty()) {
            tntExplosionEntities.add("minecraft:tnt");
            tntExplosionEntities.add("minecraft:tnt_minecart");
            tntExplosionEntities.add("minecraft:wither_skull");
        }
        tntThreads = config.getInt("tnt-explosion-optimization.threads", 6);
        tntMaxBlocks = config.getInt("tnt-explosion-optimization.max-blocks", 4096);
        tntTimeoutMicros = config.getLong("tnt-explosion-optimization.timeout-us", 100L);
        tntBatchSize = config.getInt("tnt-explosion-optimization.batch-size", 64);
        tntVanillaCompatibilityEnabled = config.getBoolean("tnt-explosion-optimization.vanilla-compatibility.enabled", true);
        tntUseVanillaPower = config.getBoolean("tnt-explosion-optimization.vanilla-compatibility.use-vanilla-power", true);
        tntUseVanillaFireLogic = config.getBoolean("tnt-explosion-optimization.vanilla-compatibility.use-vanilla-fire-logic", true);
        tntUseVanillaDamageCalculation = config.getBoolean("tnt-explosion-optimization.vanilla-compatibility.use-vanilla-damage-calculation", true);
        tntUseFullRaycast = config.getBoolean("tnt-explosion-optimization.vanilla-compatibility.use-full-raycast", false);
        tntUseVanillaBlockDestruction = config.getBoolean("tnt-explosion-optimization.vanilla-compatibility.use-vanilla-block-destruction", true);
        tntUseVanillaDrops = config.getBoolean("tnt-explosion-optimization.vanilla-compatibility.use-vanilla-drops", true);
        beeFixEnabled = config.getBoolean("bee-fix.enabled", true);
        enableDebugLogging = config.getBoolean("performance.debug-logging", false);
        enablePerformanceMetrics = config.getBoolean("performance.enable-metrics", true);
        configVersion = config.getInt("version", 6);
        
        structureLocationAsyncEnabled = config.getBoolean("structure-location-async.enabled", true);
        structureLocationThreads = config.getInt("structure-location-async.threads", 3);
        locateCommandEnabled = config.getBoolean("structure-location-async.locate-command.enabled", true);
        locateCommandSearchRadius = config.getInt("structure-location-async.locate-command.search-radius", 100);
        locateCommandSkipKnownStructures = config.getBoolean("structure-location-async.locate-command.skip-known-structures", false);
        villagerTradeMapsEnabled = config.getBoolean("structure-location-async.villager-trade-maps.enabled", true);
        villagerTradeMapTypes = new java.util.HashSet<>(config.getStringList("structure-location-async.villager-trade-maps.trade-types"));
        if (villagerTradeMapTypes.isEmpty()) {
            villagerTradeMapTypes.add("minecraft:ocean_monument_map");
            villagerTradeMapTypes.add("minecraft:woodland_mansion_map");
            villagerTradeMapTypes.add("minecraft:buried_treasure_map");
        }
        villagerMapGenerationTimeoutSeconds = config.getInt("structure-location-async.villager-trade-maps.generation-timeout-seconds", 30);
        dolphinTreasureHuntEnabled = config.getBoolean("structure-location-async.dolphin-treasure-hunt.enabled", true);
        dolphinTreasureSearchRadius = config.getInt("structure-location-async.dolphin-treasure-hunt.search-radius", 50);
        dolphinTreasureHuntInterval = config.getInt("structure-location-async.dolphin-treasure-hunt.hunt-interval", 100);
        chestExplorationMapsEnabled = config.getBoolean("structure-location-async.chest-exploration-maps.enabled", true);
        chestExplorationLootTables = new java.util.HashSet<>(config.getStringList("structure-location-async.chest-exploration-maps.loot-tables"));
        if (chestExplorationLootTables.isEmpty()) {
            chestExplorationLootTables.add("minecraft:chests/shipwreck_map");
            chestExplorationLootTables.add("minecraft:chests/underwater_ruin_big");
            chestExplorationLootTables.add("minecraft:chests/underwater_ruin_small");
        }
        chestMapPreserveProbability = config.getBoolean("structure-location-async.chest-exploration-maps.preserve-probability", true);
        
        structureAlgorithmOptimizationEnabled = config.getBoolean("structure-location-async.algorithm-optimization.enabled", true);
        structureSearchPattern = config.getString("structure-location-async.algorithm-optimization.search-pattern", "hybrid");
        structureCachingEnabled = config.getBoolean("structure-location-async.algorithm-optimization.caching.enabled", true);
        structureCacheMaxSize = config.getInt("structure-location-async.algorithm-optimization.caching.max-size", 1000);
        structureCacheExpirationMinutes = config.getLong("structure-location-async.algorithm-optimization.caching.expiration-minutes", 30L);
        structurePrecomputationEnabled = config.getBoolean("structure-location-async.algorithm-optimization.precomputation.enabled", true);
        biomeAwareSearchEnabled = config.getBoolean("structure-location-async.algorithm-optimization.biome-aware-search.enabled", true);
        
        dataPackOptimizationEnabled = config.getBoolean("datapack-optimization.enabled", true);
        dataPackFileLoadThreads = config.getInt("datapack-optimization.file-load-threads", 4);
        dataPackZipProcessThreads = config.getInt("datapack-optimization.zip-process-threads", 2);
        dataPackBatchSize = config.getInt("datapack-optimization.batch-size", 100);
        dataPackCacheExpirationMinutes = config.getLong("datapack-optimization.cache-expiration-minutes", 30L);
        dataPackDebugEnabled = config.getBoolean("datapack-optimization.debug-enabled", false);
        
        nitoriOptimizationsEnabled = config.getBoolean("nitori.enabled", true);
        virtualThreadEnabled = config.getBoolean("nitori.virtual-threads", true);
        workStealingEnabled = config.getBoolean("nitori.work-stealing", true);
        blockPosCacheEnabled = config.getBoolean("nitori.blockpos-cache", true);
        optimizedCollectionsEnabled = config.getBoolean("nitori.optimized-collections", true);
        
        secureSeedEnabled = config.getBoolean("secure-seed.enabled", true);
        secureSeedProtectStructures = config.getBoolean("secure-seed.protect-structures", true);
        secureSeedProtectOres = config.getBoolean("secure-seed.protect-ores", true);
        secureSeedProtectSlimes = config.getBoolean("secure-seed.protect-slimes", true);
        secureSeedBits = config.getInt("secure-seed.seed-bits", 1024);
        
        // ==========================================
        // ???????? / Async Pathfinding Config (v8.0)
        // ==========================================
        asyncPathfindingEnabled = config.getBoolean("async-pathfinding.enabled", true);
        asyncPathfindingMaxThreads = config.getInt("async-pathfinding.max-threads", 4);
        asyncPathfindingKeepAliveSeconds = config.getLong("async-pathfinding.keep-alive-seconds", 60);
        asyncPathfindingMaxQueueSize = config.getInt("async-pathfinding.max-queue-size", 1000);
        asyncPathfindingTimeoutMs = config.getInt("async-pathfinding.timeout-ms", 50);
        
        // ==========================================
        // ?????? / Density Control Config (v8.0)
        // ==========================================
        densityControlEnabled = config.getBoolean("density.enabled", true);
        
        // ==========================================
        // TNT ?????? / TNT Land Protection Config (v8.0)
        // ==========================================
        tntLandProtectionEnabled = config.getBoolean("tnt-explosion-optimization.land-protection.enabled", false);
        
        // ==========================================
        // Suffocation Optimization Config (v3.2.15)
        // ==========================================
        suffocationOptimizationEnabled = config.getBoolean("pufferfish-optimizations.suffocation-optimization.enabled", true);
        
        // ==========================================
        // BlockLocker Protection Config (v3.2.15)
        // ==========================================
        blockLockerProtectionEnabled = config.getBoolean("block-entity-optimizations.parallel-tick.blocklocker-protection", false);
        
        // ==========================================
        // TNT Sakura Optimization Config (v3.2.16)
        // ==========================================
        tntUseSakuraDensityCache = config.getBoolean("tnt-explosion-optimization.sakura.density-cache", true);
        tntMergeEnabled = config.getBoolean("tnt-explosion-optimization.sakura.merge-enabled", true);
        tntMergeRadius = config.getDouble("tnt-explosion-optimization.sakura.merge-radius", 0.5);
        tntMaxFuseDifference = config.getInt("tnt-explosion-optimization.sakura.max-fuse-difference", 5);
        tntMergedPowerMultiplier = (float) config.getDouble("tnt-explosion-optimization.sakura.merged-power-multiplier", 1.0);
        
        // ==========================================
        // Redstone Sakura Optimization Config (v3.2.16)
        // ==========================================
        usePandaWireAlgorithm = config.getBoolean("redstone-optimization.panda-wire", true);
        redstoneNetworkCacheEnabled = config.getBoolean("redstone-optimization.network-cache.enabled", true);
        redstoneNetworkCacheExpireTicks = config.getInt("redstone-optimization.network-cache.expire-ticks", 100);
        
        // ==========================================
        // SecureSeed Config (v3.2.16)
        // ==========================================
        seedEncryptionEnabled = config.getBoolean("seed-encryption.enabled", false);
        seedCommandDenyMessage = config.getString("seed-encryption.deny-message", 
            "You don't have permission to use this command. Only server operators can view the world seed.");
        
        validateConfigVersion();
        validateConfig();
    }
    
    private void validateConfigVersion() {
        if (configVersion != CURRENT_CONFIG_VERSION) {
            getLogger().warning("==========================================");
            getLogger().warning("  CONFIG VERSION MISMATCH DETECTED");
            getLogger().warning("==========================================");
            getLogger().warning("Current supported version: " + CURRENT_CONFIG_VERSION);
            getLogger().warning("Your config version: " + configVersion);
            getLogger().warning("");
            
            if (configVersion < CURRENT_CONFIG_VERSION) {
                getLogger().warning("Your config.yml is outdated!");
                getLogger().warning("Automatically backing up and regenerating config...");
            } else {
                getLogger().warning("Your config.yml is from a newer version!");
                getLogger().warning("Automatically backing up and regenerating config...");
            }
            
            if (backupAndRegenerateConfig()) {
                getLogger().info("Config backup and regeneration completed successfully!");
                getLogger().info("Old config saved as: config.yml.bak");
                getLogger().info("New config generated with version " + CURRENT_CONFIG_VERSION);
                getLogger().warning("Please review the new config.yml and adjust settings as needed.");
                getLogger().warning("==========================================");
                
                reloadConfigWithoutValidation();
            } else {
                getLogger().severe("Failed to backup and regenerate config!");
                getLogger().severe("Please manually update your config.yml");
                getLogger().warning("==========================================");
            }
        }
    }
    
    private boolean backupAndRegenerateConfig() {
        try {
            // ?? Ignite ????? plugin ? null?? AkiAsyncInitializer ???????
            java.io.File dataFolder;
            if (plugin != null) {
                dataFolder = plugin.getDataFolder();
            } else {
                // Ignite ???? AkiAsyncInitializer ???????
                try {
                    org.virgil.akiasync.bootstrap.AkiAsyncInitializer init = 
                        org.virgil.akiasync.bootstrap.AkiAsyncInitializer.getInstance();
                    dataFolder = init.getDataFolder();
                    if (dataFolder == null) {
                        dataFolder = new java.io.File("plugins/AkiAsync");
                        if (!dataFolder.exists()) {
                            dataFolder.mkdirs();
                        }
                    }
                } catch (Exception e) {
                    getLogger().warning("????????????????: " + e.getMessage());
                    dataFolder = new java.io.File("plugins/AkiAsync");
                    if (!dataFolder.exists()) {
                        dataFolder.mkdirs();
                    }
                }
            }
            
            java.io.File configFile = new java.io.File(dataFolder, "config.yml");
            java.io.File backupFile = new java.io.File(dataFolder, "config.yml.bak");
            
            if (!configFile.exists()) {
                getLogger().warning("Config file does not exist, creating new one...");
                if (plugin != null) {
                    plugin.saveDefaultConfig();
                } else {
                    // Ignite ???? JAR ??????
                    copyDefaultConfigFromJar(configFile);
                }
                return true;
            }
            
            if (backupFile.exists()) {
                if (!backupFile.delete()) {
                    getLogger().warning("Failed to delete existing backup file");
                }
            }
            
            if (!configFile.renameTo(backupFile)) {
                getLogger().severe("Failed to backup config file to config.yml.bak");
                return false;
            }
            
            getLogger().info("Config file backed up to: config.yml.bak");
            
            if (plugin != null) {
                plugin.saveDefaultConfig();
            } else {
                // Ignite ???? JAR ??????
                copyDefaultConfigFromJar(configFile);
            }
            getLogger().info("New config.yml generated with latest version");
            
            return true;
            
        } catch (Exception e) {
            getLogger().severe("Error during config backup and regeneration: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }
    
    /**
     * ? JAR ????????????? Ignite ???
     * Copy default config file from JAR (for Ignite mode)
     */
    private void copyDefaultConfigFromJar(java.io.File targetFile) {
        try {
            final java.net.URL location = ConfigManager.class.getProtectionDomain().getCodeSource().getLocation();
            if (location == null) {
                getLogger().severe("???? JAR ????");
                return;
            }
            
            final java.nio.file.Path jarPath = java.nio.file.Paths.get(location.toURI());
            
            try (java.util.jar.JarFile jar = new java.util.jar.JarFile(jarPath.toFile())) {
                final java.util.jar.JarEntry entry = jar.getJarEntry("config.yml");
                if (entry != null) {
                    try (java.io.InputStream stream = jar.getInputStream(entry)) {
                        java.nio.file.Files.copy(stream, targetFile.toPath());
                        getLogger().info("???????? JAR ???: " + targetFile.getAbsolutePath());
                    }
                } else {
                    getLogger().warning("JAR ???? config.yml ??");
                }
            }
        } catch (Exception ex) {
            getLogger().severe("??? JAR ????????: " + ex.getMessage());
            ex.printStackTrace();
        }
    }
    
    private void reloadConfigWithoutValidation() {
        plugin.reloadConfig();
        config = plugin.getConfig();
        
        entityTrackerEnabled = config.getBoolean("entity-tracker.enabled", true);
        threadPoolSize = config.getInt("entity-tracker.thread-pool-size", 4);
        updateIntervalTicks = config.getInt("entity-tracker.update-interval-ticks", 1);
        maxQueueSize = config.getInt("entity-tracker.max-queue-size", 1000);
        mobSpawningEnabled = config.getBoolean("mob-spawning.enabled", true);
        spawnerOptimizationEnabled = config.getBoolean("mob-spawning.spawner-optimization", true);
        maxEntitiesPerChunk = config.getInt("density.max-per-chunk", 80);
        aiCooldownTicks = config.getInt("density.ai-cooldown-ticks", 10);
        brainThrottle = config.getBoolean("brain.throttle", true);
        brainThrottleInterval = config.getInt("brain.throttle-interval", 10);
        asyncAITimeoutMicros = config.getLong("async-ai.timeout-microseconds", 500L);
        villagerOptimizationEnabled = config.getBoolean("async-ai.villager-optimization.enabled", false);
        villagerUsePOISnapshot = config.getBoolean("async-ai.villager-optimization.use-poi-snapshot", true);
        piglinOptimizationEnabled = config.getBoolean("async-ai.piglin-optimization.enabled", false);
        piglinUsePOISnapshot = config.getBoolean("async-ai.piglin-optimization.use-poi-snapshot", false);
        piglinLookDistance = config.getInt("async-ai.piglin-optimization.look-distance", 16);
        piglinBarterDistance = config.getInt("async-ai.piglin-optimization.barter-distance", 16);
        pillagerFamilyOptimizationEnabled = config.getBoolean("async-ai.pillager-family-optimization.enabled", false);
        pillagerFamilyUsePOISnapshot = config.getBoolean("async-ai.pillager-family-optimization.use-poi-snapshot", false);
        evokerOptimizationEnabled = config.getBoolean("async-ai.evoker-optimization.enabled", false);
        blazeOptimizationEnabled = config.getBoolean("async-ai.blaze-optimization.enabled", false);
        guardianOptimizationEnabled = config.getBoolean("async-ai.guardian-optimization.enabled", false);
        witchOptimizationEnabled = config.getBoolean("async-ai.witch-optimization.enabled", false);
        universalAiOptimizationEnabled = config.getBoolean("async-ai.universal-ai-optimization.enabled", false);
        universalAiEntities = new java.util.HashSet<>(config.getStringList("async-ai.universal-ai-optimization.entities"));
        zeroDelayFactoryOptimizationEnabled = config.getBoolean("block-entity-optimizations.zero-delay-factory-optimization.enabled", false);
        zeroDelayFactoryEntities = new java.util.HashSet<>(config.getStringList("block-entity-optimizations.zero-delay-factory-optimization.entities"));
        blockEntityParallelTickEnabled = config.getBoolean("block-entity-optimizations.parallel-tick.enabled", true);
        blockEntityParallelMinBlockEntities = config.getInt("block-entity-optimizations.parallel-tick.min-block-entities", 50);
        blockEntityParallelBatchSize = config.getInt("block-entity-optimizations.parallel-tick.batch-size", 16);
        blockEntityParallelProtectContainers = config.getBoolean("block-entity-optimizations.parallel-tick.protect-containers", true);
        blockEntityParallelTimeoutMs = config.getInt("block-entity-optimizations.parallel-tick.timeout-ms", 50);
        itemEntityOptimizationEnabled = config.getBoolean("item-entity-optimizations.enabled", true);
        itemEntityAgeInterval = config.getInt("item-entity-optimizations.age-increment-interval", 10);
        itemEntityMinNearbyItems = config.getInt("item-entity-optimizations.min-nearby-items", 3);
        simpleEntitiesOptimizationEnabled = config.getBoolean("async-ai.simple-entities.enabled", false);
        simpleEntitiesUsePOISnapshot = config.getBoolean("async-ai.simple-entities.use-poi-snapshot", false);
        entityTickParallel = config.getBoolean("entity-tick-parallel.enabled", true);
        entityTickThreads = config.getInt("entity-tick-parallel.threads", 4);
        minEntitiesForParallel = config.getInt("entity-tick-parallel.min-entities", 100);
        entityTickBatchSize = config.getInt("entity-tick-parallel.batch-size", 50);
        
        nitoriOptimizationsEnabled = config.getBoolean("nitori.enabled", true);
        virtualThreadEnabled = config.getBoolean("nitori.virtual-threads", true);
        workStealingEnabled = config.getBoolean("nitori.work-stealing", true);
        blockPosCacheEnabled = config.getBoolean("nitori.blockpos-cache", true);
        optimizedCollectionsEnabled = config.getBoolean("nitori.optimized-collections", true);
        
        asyncLightingEnabled = config.getBoolean("lighting-optimizations.async-lighting.enabled", true);
        lightingThreadPoolSize = config.getInt("lighting-optimizations.async-lighting.thread-pool-size", 2);
        lightBatchThreshold = config.getInt("lighting-optimizations.async-lighting.batch-threshold", 16);
        useLayeredPropagationQueue = config.getBoolean("lighting-optimizations.propagation-queue.use-layered-queue", true);
        maxLightPropagationDistance = config.getInt("lighting-optimizations.propagation-queue.max-propagation-distance", 15);
        skylightCacheEnabled = config.getBoolean("lighting-optimizations.skylight-cache.enabled", true);
        skylightCacheDurationMs = config.getInt("lighting-optimizations.skylight-cache.cache-duration-ms", 100);
        lightDeduplicationEnabled = config.getBoolean("lighting-optimizations.advanced.enable-deduplication", true);
        dynamicBatchAdjustmentEnabled = config.getBoolean("lighting-optimizations.advanced.dynamic-batch-adjustment", true);
        advancedLightingStatsEnabled = config.getBoolean("lighting-optimizations.advanced.log-advanced-stats", false);
        
        playerChunkLoadingOptimizationEnabled = config.getBoolean("vmp-optimizations.chunk-loading.enabled", true);
        maxConcurrentChunkLoadsPerPlayer = config.getInt("vmp-optimizations.chunk-loading.max-concurrent-per-player", 5);
        entityTrackingRangeOptimizationEnabled = config.getBoolean("vmp-optimizations.entity-tracking.enabled", true);
        entityTrackingRangeMultiplier = config.getDouble("vmp-optimizations.entity-tracking.range-multiplier", 0.8);
        
        alternateCurrentEnabled = config.getBoolean("redstone-optimizations.alternate-current.enabled", true);
        redstoneWireTurboEnabled = config.getBoolean("redstone-optimizations.wire-turbo.enabled", true);
        redstoneUpdateBatchingEnabled = config.getBoolean("redstone-optimizations.update-batching.enabled", true);
        redstoneUpdateBatchThreshold = config.getInt("redstone-optimizations.update-batching.batch-threshold", 8);
        redstoneCacheEnabled = config.getBoolean("redstone-optimizations.cache.enabled", true);
        redstoneCacheDurationMs = config.getInt("redstone-optimizations.cache.duration-ms", 50);
        
        tntOptimizationEnabled = config.getBoolean("tnt-explosion-optimization.enabled", true);
        tntExplosionEntities = new java.util.HashSet<>(config.getStringList("tnt-explosion-optimization.entities"));
        if (tntExplosionEntities.isEmpty()) {
            tntExplosionEntities.add("minecraft:tnt");
            tntExplosionEntities.add("minecraft:tnt_minecart");
            tntExplosionEntities.add("minecraft:wither_skull");
        }
        tntThreads = config.getInt("tnt-explosion-optimization.threads", 6);
        tntMaxBlocks = config.getInt("tnt-explosion-optimization.max-blocks", 4096);
        tntTimeoutMicros = config.getLong("tnt-explosion-optimization.timeout-us", 100L);
        tntBatchSize = config.getInt("tnt-explosion-optimization.batch-size", 64);
        tntVanillaCompatibilityEnabled = config.getBoolean("tnt-explosion-optimization.vanilla-compatibility.enabled", true);
        tntUseVanillaPower = config.getBoolean("tnt-explosion-optimization.vanilla-compatibility.use-vanilla-power", true);
        tntUseVanillaFireLogic = config.getBoolean("tnt-explosion-optimization.vanilla-compatibility.use-vanilla-fire-logic", true);
        tntUseVanillaDamageCalculation = config.getBoolean("tnt-explosion-optimization.vanilla-compatibility.use-vanilla-damage-calculation", true);
        tntUseFullRaycast = config.getBoolean("tnt-explosion-optimization.vanilla-compatibility.use-full-raycast", false);
        tntUseVanillaBlockDestruction = config.getBoolean("tnt-explosion-optimization.vanilla-compatibility.use-vanilla-block-destruction", true);
        tntUseVanillaDrops = config.getBoolean("tnt-explosion-optimization.vanilla-compatibility.use-vanilla-drops", true);
        
        beeFixEnabled = config.getBoolean("bee-fix.enabled", true);
        
        asyncVillagerBreedEnabled = config.getBoolean("villager-breed-optimization.async-villager-breed", true);
        villagerAgeThrottleEnabled = config.getBoolean("villager-breed-optimization.age-throttle", true);
        villagerBreedThreads = config.getInt("villager-breed-optimization.threads", 4);
        villagerBreedCheckInterval = config.getInt("villager-breed-optimization.check-interval", 5);
        
        chunkTickAsyncEnabled = config.getBoolean("chunk-tick-async.enabled", false);
        chunkTickThreads = config.getInt("chunk-tick-async.threads", 4);
        chunkTickTimeoutMicros = config.getLong("chunk-tick-async.timeout-us", 200L);
        
        structureLocationAsyncEnabled = config.getBoolean("structure-location-async.enabled", true);
        structureLocationThreads = config.getInt("structure-location-async.threads", 3);
        locateCommandEnabled = config.getBoolean("structure-location-async.locate-command.enabled", true);
        locateCommandSearchRadius = config.getInt("structure-location-async.locate-command.search-radius", 100);
        locateCommandSkipKnownStructures = config.getBoolean("structure-location-async.locate-command.skip-known-structures", false);
        villagerTradeMapsEnabled = config.getBoolean("structure-location-async.villager-trade-maps.enabled", true);
        villagerTradeMapTypes = new java.util.HashSet<>(config.getStringList("structure-location-async.villager-trade-maps.trade-types"));
        if (villagerTradeMapTypes.isEmpty()) {
            villagerTradeMapTypes.add("minecraft:ocean_monument_map");
            villagerTradeMapTypes.add("minecraft:woodland_mansion_map");
            villagerTradeMapTypes.add("minecraft:buried_treasure_map");
        }
        villagerMapGenerationTimeoutSeconds = config.getInt("structure-location-async.villager-trade-maps.generation-timeout-seconds", 30);
        dolphinTreasureHuntEnabled = config.getBoolean("structure-location-async.dolphin-treasure-hunt.enabled", true);
        dolphinTreasureSearchRadius = config.getInt("structure-location-async.dolphin-treasure-hunt.search-radius", 50);
        dolphinTreasureHuntInterval = config.getInt("structure-location-async.dolphin-treasure-hunt.hunt-interval", 100);
        chestExplorationMapsEnabled = config.getBoolean("structure-location-async.chest-exploration-maps.enabled", true);
        chestExplorationLootTables = new java.util.HashSet<>(config.getStringList("structure-location-async.chest-exploration-maps.loot-tables"));
        if (chestExplorationLootTables.isEmpty()) {
            chestExplorationLootTables.add("minecraft:chests/shipwreck_map");
            chestExplorationLootTables.add("minecraft:chests/underwater_ruin_big");
            chestExplorationLootTables.add("minecraft:chests/underwater_ruin_small");
        }
        chestMapPreserveProbability = config.getBoolean("structure-location-async.chest-exploration-maps.preserve-probability", true);
        
        structureAlgorithmOptimizationEnabled = config.getBoolean("structure-location-async.algorithm-optimization.enabled", true);
        structureSearchPattern = config.getString("structure-location-async.algorithm-optimization.search-pattern", "hybrid");
        structureCachingEnabled = config.getBoolean("structure-location-async.algorithm-optimization.caching.enabled", true);
        structureCacheMaxSize = config.getInt("structure-location-async.algorithm-optimization.caching.max-size", 1000);
        structureCacheExpirationMinutes = config.getLong("structure-location-async.algorithm-optimization.caching.expiration-minutes", 30L);
        structurePrecomputationEnabled = config.getBoolean("structure-location-async.algorithm-optimization.precomputation.enabled", true);
        biomeAwareSearchEnabled = config.getBoolean("structure-location-async.algorithm-optimization.biome-aware-search.enabled", true);
        
        dataPackOptimizationEnabled = config.getBoolean("datapack-optimization.enabled", true);
        dataPackFileLoadThreads = config.getInt("datapack-optimization.file-load-threads", 4);
        dataPackZipProcessThreads = config.getInt("datapack-optimization.zip-process-threads", 2);
        dataPackBatchSize = config.getInt("datapack-optimization.batch-size", 100);
        dataPackCacheExpirationMinutes = config.getLong("datapack-optimization.cache-expiration-minutes", 30L);
        dataPackDebugEnabled = config.getBoolean("datapack-optimization.debug-enabled", false);
        
        enableDebugLogging = config.getBoolean("performance.debug-logging", false);
        enablePerformanceMetrics = config.getBoolean("performance.enable-metrics", true);
        configVersion = config.getInt("version", 6);
        
        // ==========================================
        // Falling Block Parallel Config (v14.0)
        // ==========================================
        fallingBlockParallelEnabled = config.getBoolean("falling-block-parallel.enabled", true);
        minFallingBlocksForParallel = config.getInt("falling-block-parallel.min-falling-blocks", 50);
        fallingBlockBatchSize = config.getInt("falling-block-parallel.batch-size", 16);
        
        // ==========================================
        // Item Entity Parallel Config (v14.0)
        // ==========================================
        itemEntityParallelEnabled = config.getBoolean("item-entity-parallel.enabled", true);
        minItemEntitiesForParallel = config.getInt("item-entity-parallel.min-item-entities", 50);
        itemEntityBatchSize = config.getInt("item-entity-parallel.batch-size", 16);
        itemEntityMergeOptimizationEnabled = config.getBoolean("item-entity-parallel.merge-optimization.enabled", true);
        itemEntityMergeInterval = config.getInt("item-entity-parallel.merge-optimization.merge-interval", 40);
        itemEntityMergeRange = config.getDouble("item-entity-parallel.merge-optimization.merge-range", 0.5);
        itemEntityAgeOptimizationEnabled = config.getBoolean("item-entity-parallel.age-optimization.enabled", true);
        itemEntityPlayerDetectionRange = config.getDouble("item-entity-parallel.age-optimization.player-detection-range", 32.0);
        
        // ==========================================
        // Minecart Cauldron Destruction Config (v14.0)
        // ==========================================
        minecartCauldronDestructionEnabled = config.getBoolean("minecart-cauldron-destruction.enabled", true);
        
        // ==========================================
        // Network Optimization Config (v14.0)
        // ==========================================
        networkOptimizationEnabled = config.getBoolean("network-optimization.enabled", true);
        packetPriorityEnabled = config.getBoolean("network-optimization.packet-priority.enabled", true);
        chunkRateControlEnabled = config.getBoolean("network-optimization.chunk-rate-control.enabled", true);
        congestionDetectionEnabled = config.getBoolean("network-optimization.congestion-detection.enabled", true);
        viewFrustumFilterEnabled = config.getBoolean("network-optimization.view-frustum-filter.enabled", false);
        viewFrustumFilterEntities = config.getBoolean("network-optimization.view-frustum-filter.filter-entities", true);
        viewFrustumFilterBlocks = config.getBoolean("network-optimization.view-frustum-filter.filter-blocks", false);
        viewFrustumFilterParticles = config.getBoolean("network-optimization.view-frustum-filter.filter-particles", true);
        highPingThreshold = config.getInt("network-optimization.congestion-detection.high-ping-threshold", 150);
        criticalPingThreshold = config.getInt("network-optimization.congestion-detection.critical-ping-threshold", 300);
        highBandwidthThreshold = config.getLong("network-optimization.congestion-detection.high-bandwidth-threshold", 1000000L);
        baseChunkSendRate = config.getInt("network-optimization.chunk-rate-control.base-rate", 10);
        maxChunkSendRate = config.getInt("network-optimization.chunk-rate-control.max-rate", 20);
        minChunkSendRate = config.getInt("network-optimization.chunk-rate-control.min-rate", 3);
        
        // ==========================================
        // Fast Movement Chunk Load Config
        // ==========================================
        fastMovementChunkLoadEnabled = config.getBoolean("fast-movement-chunk-load.enabled", true);
        fastMovementSpeedThreshold = config.getDouble("fast-movement-chunk-load.speed-threshold", 0.5);
        fastMovementPreloadDistance = config.getInt("fast-movement-chunk-load.preload-distance", 8);
        fastMovementMaxConcurrentLoads = config.getInt("fast-movement-chunk-load.max-concurrent-loads", 4);
        fastMovementPredictionTicks = config.getInt("fast-movement-chunk-load.prediction-ticks", 40);
        // Center Offset Config
        centerOffsetEnabled = config.getBoolean("fast-movement-chunk-load.center-offset.enabled", true);
        centerOffsetMinSpeed = config.getDouble("fast-movement-chunk-load.center-offset.min-speed", 3.0);
        centerOffsetMaxSpeed = config.getDouble("fast-movement-chunk-load.center-offset.max-speed", 9.0);
        centerOffsetMaxRatio = config.getDouble("fast-movement-chunk-load.center-offset.max-offset-ratio", 0.75);
        // Async Loading Config
        asyncLoadingBatchSize = config.getInt("fast-movement-chunk-load.center-offset.async-loading.batch-size", 2);
        asyncLoadingBatchDelayMs = config.getLong("fast-movement-chunk-load.center-offset.async-loading.batch-delay-ms", 20L);
        
        // ==========================================
        // Teleport Optimization Config
        // ==========================================
        teleportOptimizationEnabled = config.getBoolean("network-optimization.teleport-optimization.enabled", true);
        teleportPacketBypassEnabled = config.getBoolean("network-optimization.teleport-optimization.packet-bypass", true);
        teleportBoostDurationSeconds = config.getInt("network-optimization.teleport-optimization.boost-duration-seconds", 5);
        teleportMaxChunkRate = config.getInt("network-optimization.teleport-optimization.max-chunk-rate", 25);
        teleportFilterNonEssentialPackets = config.getBoolean("network-optimization.teleport-optimization.filter-non-essential", true);
        teleportDebugEnabled = config.getBoolean("network-optimization.teleport-optimization.debug", false);
        
        // ==========================================
        // Virtual Entity Compatibility Config
        // ==========================================
        virtualEntityCompatibilityEnabled = config.getBoolean("virtual-entity-compatibility.enabled", true);
        virtualEntityDebugEnabled = config.getBoolean("virtual-entity-compatibility.debug", false);
        virtualEntityBypassPacketQueue = config.getBoolean("virtual-entity-compatibility.bypass-packet-queue", true);
        virtualEntityExcludeFromThrottling = config.getBoolean("virtual-entity-compatibility.exclude-from-throttling", true);
        fancynpcsCompatEnabled = config.getBoolean("virtual-entity-compatibility.fancynpcs.enabled", true);
        fancynpcsUseAPI = config.getBoolean("virtual-entity-compatibility.fancynpcs.use-api", true);
        fancynpcsPriority = config.getInt("virtual-entity-compatibility.fancynpcs.priority", 90);
        znpcsplusCompatEnabled = config.getBoolean("virtual-entity-compatibility.znpcsplus.enabled", true);
        znpcsplusPriority = config.getInt("virtual-entity-compatibility.znpcsplus.priority", 90);
        
        validateConfig();
    }
    
    private void validateConfig() {
        if (threadPoolSize < 1) {
            getLogger().warning("Thread pool size cannot be less than 1, setting to 1");
            threadPoolSize = 1;
        }
        if (threadPoolSize > 32) {
            getLogger().warning("Thread pool size cannot be more than 32, setting to 32");
            threadPoolSize = 32;
        }
        
        if (updateIntervalTicks < 1) {
            getLogger().warning("Update interval cannot be less than 1 tick, setting to 1");
            updateIntervalTicks = 1;
        }
        
        if (maxQueueSize < 100) {
            getLogger().warning("Max queue size cannot be less than 100, setting to 100");
            maxQueueSize = 100;
        }

        if (maxEntitiesPerChunk < 20) {
            maxEntitiesPerChunk = 20;
        }
        if (aiCooldownTicks < 0) {
            aiCooldownTicks = 0;
        }
        if (brainThrottleInterval < 0) {
            brainThrottleInterval = 0;
        }
        if (asyncAITimeoutMicros < 100) {
            getLogger().warning("Async AI timeout too low, setting to 100ĺ¨çź");
            asyncAITimeoutMicros = 100;
        }
        if (asyncAITimeoutMicros > 5000) {
            getLogger().warning("Async AI timeout too high, setting to 5000ĺ¨çź (5ms)");
            asyncAITimeoutMicros = 5000;
        }
        if (entityTickThreads < 1) entityTickThreads = 1;
        if (entityTickThreads > 16) entityTickThreads = 16;
        if (minEntitiesForParallel < 10) minEntitiesForParallel = 10;
        if (lightingThreadPoolSize < 1) lightingThreadPoolSize = 1;
        if (lightingThreadPoolSize > 8) lightingThreadPoolSize = 8;
        if (lightBatchThreshold < 1) lightBatchThreshold = 1;
        if (lightBatchThreshold > 100) lightBatchThreshold = 100;
        if (maxLightPropagationDistance < 1) maxLightPropagationDistance = 1;
        if (maxLightPropagationDistance > 32) maxLightPropagationDistance = 32;
        if (skylightCacheDurationMs < 0) skylightCacheDurationMs = 0;
        if (maxConcurrentChunkLoadsPerPlayer < 1) maxConcurrentChunkLoadsPerPlayer = 1;
        if (maxConcurrentChunkLoadsPerPlayer > 20) maxConcurrentChunkLoadsPerPlayer = 20;
        if (entityTrackingRangeMultiplier < 0.1) entityTrackingRangeMultiplier = 0.1;
        if (entityTrackingRangeMultiplier > 2.0) entityTrackingRangeMultiplier = 2.0;
        if (redstoneUpdateBatchThreshold < 1) redstoneUpdateBatchThreshold = 1;
        if (redstoneUpdateBatchThreshold > 50) redstoneUpdateBatchThreshold = 50;
        if (redstoneCacheDurationMs < 0) redstoneCacheDurationMs = 0;
        if (redstoneCacheDurationMs > 1000) redstoneCacheDurationMs = 1000;
        if (tntThreads < 1) {
            getLogger().warning("TNT threads cannot be less than 1, setting to 1");
            tntThreads = 1;
        }
        if (tntThreads > 32) {
            getLogger().warning("TNT threads cannot be more than 32, setting to 32");
            tntThreads = 32;
        }
        if (tntMaxBlocks < 256) tntMaxBlocks = 256;
        if (tntMaxBlocks > 16384) tntMaxBlocks = 16384;
        if (tntTimeoutMicros < 10) tntTimeoutMicros = 10;
        if (tntTimeoutMicros > 10000) tntTimeoutMicros = 10000;
        if (tntBatchSize < 8) tntBatchSize = 8;
        if (tntBatchSize > 256) tntBatchSize = 256;
        
        if (structureLocationThreads < 1) {
            getLogger().warning("Structure location threads cannot be less than 1, setting to 1");
            structureLocationThreads = 1;
        }
        if (structureLocationThreads > 8) {
            getLogger().warning("Structure location threads cannot be more than 8, setting to 8");
            structureLocationThreads = 8;
        }
        
        validateNitoriConfig();
        if (locateCommandSearchRadius < 10) locateCommandSearchRadius = 10;
        if (locateCommandSearchRadius > 1000) locateCommandSearchRadius = 1000;
        if (villagerMapGenerationTimeoutSeconds < 5) villagerMapGenerationTimeoutSeconds = 5;
        if (villagerMapGenerationTimeoutSeconds > 300) villagerMapGenerationTimeoutSeconds = 300;
        if (dolphinTreasureSearchRadius < 10) dolphinTreasureSearchRadius = 10;
        if (dolphinTreasureSearchRadius > 200) dolphinTreasureSearchRadius = 200;
        if (dolphinTreasureHuntInterval < 20) dolphinTreasureHuntInterval = 20;
        if (dolphinTreasureHuntInterval > 1200) dolphinTreasureHuntInterval = 1200;
    }
    
    private void validateNitoriConfig() {
        if (virtualThreadEnabled) {
            int javaVersion = getJavaMajorVersion();
            if (javaVersion < 19) {
                getLogger().warning("==========================================");
                getLogger().warning("  NITORI VIRTUAL THREAD WARNING");
                getLogger().warning("==========================================");
                getLogger().warning("Virtual Thread is enabled but your Java version (" + javaVersion + ") doesn't support it.");
                getLogger().warning("Virtual Thread requires Java 19+ (preview) or Java 21+ (stable).");
                getLogger().warning("The plugin will automatically fall back to regular threads.");
                getLogger().warning("Consider upgrading to Java 21+ for better performance.");
                getLogger().warning("==========================================");
            } else if (javaVersion >= 19 && javaVersion < 21) {
                getLogger().info("Virtual Thread enabled with Java " + javaVersion + " (preview feature)");
            } else {
                getLogger().info("Virtual Thread enabled with Java " + javaVersion + " (stable feature)");
            }
        }
        
        if (!nitoriOptimizationsEnabled) {
            getLogger().info("Nitori-style optimizations are disabled. You may miss some performance improvements.");
        } else {
            int enabledOptimizations = 0;
            if (virtualThreadEnabled) enabledOptimizations++;
            if (workStealingEnabled) enabledOptimizations++;
            if (blockPosCacheEnabled) enabledOptimizations++;
            if (optimizedCollectionsEnabled) enabledOptimizations++;
            
            getLogger().info("Nitori-style optimizations enabled: " + enabledOptimizations + "/4 features active");
        }
    }
    
    private int getJavaMajorVersion() {
        String version = System.getProperty("java.version");
        if (version.startsWith("1.")) {
            return version.charAt(2) - '0';
        }
        int dotIndex = version.indexOf(".");
        return Integer.parseInt(dotIndex == -1 ? version : version.substring(0, dotIndex));
    }
    
    public void reload() {
        loadConfig();
        getLogger().info("Configuration reloaded successfully!");
    }
    
    public boolean isEntityTrackerEnabled() {
        return entityTrackerEnabled;
    }
    
    public int getThreadPoolSize() {
        return threadPoolSize;
    }
    
    public int getUpdateIntervalTicks() {
        return updateIntervalTicks;
    }
    
    public int getMaxQueueSize() {
        return maxQueueSize;
    }
    
    public boolean isDebugLoggingEnabled() {
        return enableDebugLogging;
    }
    
    public void setDebugLoggingEnabled(boolean enabled) {
        this.enableDebugLogging = enabled;
        config.set("performance.debug-logging", enabled);
        saveConfig();
    }
    
    public boolean isPerformanceMetricsEnabled() {
        return enablePerformanceMetrics;
    }
    
    public boolean isMobSpawningEnabled() {
        return mobSpawningEnabled;
    }
    
    public boolean isSpawnerOptimizationEnabled() {
        return spawnerOptimizationEnabled;
    }
    
    public int getMaxEntitiesPerChunk() {
        return maxEntitiesPerChunk;
    }
    
    public int getAiCooldownTicks() {
        return aiCooldownTicks;
    }
    
    public boolean isBrainThrottleEnabled() { return brainThrottle; }
    public int getBrainThrottleInterval() { return brainThrottleInterval; }
    public long getAsyncAITimeoutMicros() { return asyncAITimeoutMicros; }
    public boolean isVillagerOptimizationEnabled() { return villagerOptimizationEnabled; }
    public boolean isVillagerUsePOISnapshot() { return villagerUsePOISnapshot; }
    public boolean isPiglinOptimizationEnabled() { return piglinOptimizationEnabled; }
    public boolean isPiglinUsePOISnapshot() { return piglinUsePOISnapshot; }
    public int getPiglinLookDistance() { return piglinLookDistance; }
    public int getPiglinBarterDistance() { return piglinBarterDistance; }
    public boolean isPillagerFamilyOptimizationEnabled() { return pillagerFamilyOptimizationEnabled; }
    public boolean isPillagerFamilyUsePOISnapshot() { return pillagerFamilyUsePOISnapshot; }
    public boolean isEvokerOptimizationEnabled() { return evokerOptimizationEnabled; }
    public boolean isBlazeOptimizationEnabled() { return blazeOptimizationEnabled; }
    public boolean isGuardianOptimizationEnabled() { return guardianOptimizationEnabled; }
    public boolean isWitchOptimizationEnabled() { return witchOptimizationEnabled; }
    public boolean isUniversalAiOptimizationEnabled() { return universalAiOptimizationEnabled; }
    public java.util.Set<String> getUniversalAiEntities() { return universalAiEntities; }
    public boolean isZeroDelayFactoryOptimizationEnabled() { return zeroDelayFactoryOptimizationEnabled; }
    public java.util.Set<String> getZeroDelayFactoryEntities() { return zeroDelayFactoryEntities; }
    public boolean isBlockEntityParallelTickEnabled() { return blockEntityParallelTickEnabled; }
    public int getBlockEntityParallelMinBlockEntities() { return blockEntityParallelMinBlockEntities; }
    public int getBlockEntityParallelBatchSize() { return blockEntityParallelBatchSize; }
    public boolean isBlockEntityParallelProtectContainers() { return blockEntityParallelProtectContainers; }
    public int getBlockEntityParallelTimeoutMs() { return blockEntityParallelTimeoutMs; }
    public boolean isItemEntityOptimizationEnabled() { return itemEntityOptimizationEnabled; }
    public int getItemEntityAgeInterval() { return itemEntityAgeInterval; }
    public int getItemEntityMinNearbyItems() { return itemEntityMinNearbyItems; }
    public boolean isSimpleEntitiesOptimizationEnabled() { return simpleEntitiesOptimizationEnabled; }
    public boolean isSimpleEntitiesUsePOISnapshot() { return simpleEntitiesUsePOISnapshot; }
    public boolean isEntityTickParallel() { return entityTickParallel; }
    public int getEntityTickThreads() { return entityTickThreads; }
    public int getMinEntitiesForParallel() { return minEntitiesForParallel; }
    public int getEntityTickBatchSize() { return entityTickBatchSize; }
    public boolean isAsyncLightingEnabled() { return asyncLightingEnabled; }
    public int getLightingThreadPoolSize() { return lightingThreadPoolSize; }
    public int getLightBatchThreshold() { return lightBatchThreshold; }
    public boolean useLayeredPropagationQueue() { return useLayeredPropagationQueue; }
    public int getMaxLightPropagationDistance() { return maxLightPropagationDistance; }
    public boolean isSkylightCacheEnabled() { return skylightCacheEnabled; }
    public int getSkylightCacheDurationMs() { return skylightCacheDurationMs; }
    public boolean isLightDeduplicationEnabled() { return lightDeduplicationEnabled; }
    public boolean isDynamicBatchAdjustmentEnabled() { return dynamicBatchAdjustmentEnabled; }
    public boolean isAdvancedLightingStatsEnabled() { return advancedLightingStatsEnabled; }
    public boolean isPlayerChunkLoadingOptimizationEnabled() { return playerChunkLoadingOptimizationEnabled; }
    public int getMaxConcurrentChunkLoadsPerPlayer() { return maxConcurrentChunkLoadsPerPlayer; }
    public boolean isEntityTrackingRangeOptimizationEnabled() { return entityTrackingRangeOptimizationEnabled; }
    public double getEntityTrackingRangeMultiplier() { return entityTrackingRangeMultiplier; }
    public boolean isAlternateCurrentEnabled() { return alternateCurrentEnabled; }
    public boolean isRedstoneWireTurboEnabled() { return redstoneWireTurboEnabled; }
    public boolean isRedstoneUpdateBatchingEnabled() { return redstoneUpdateBatchingEnabled; }
    public int getRedstoneUpdateBatchThreshold() { return redstoneUpdateBatchThreshold; }
    public boolean isRedstoneCacheEnabled() { return redstoneCacheEnabled; }
    public int getRedstoneCacheDurationMs() { return redstoneCacheDurationMs; }
    public boolean isAsyncVillagerBreedEnabled() { return asyncVillagerBreedEnabled; }
    public boolean isVillagerAgeThrottleEnabled() { return villagerAgeThrottleEnabled; }
    public int getVillagerBreedThreads() { return villagerBreedThreads; }
    public int getVillagerBreedCheckInterval() { return villagerBreedCheckInterval; }
    public boolean isTNTOptimizationEnabled() { return tntOptimizationEnabled; }
    public java.util.Set<String> getTNTExplosionEntities() { return tntExplosionEntities; }
    public int getTNTThreads() { return tntThreads; }
    public int getTNTMaxBlocks() { return tntMaxBlocks; }
    public long getTNTTimeoutMicros() { return tntTimeoutMicros; }
    public int getTNTBatchSize() { return tntBatchSize; }
    public boolean isTNTDebugEnabled() { return enableDebugLogging; }
    public boolean isTNTVanillaCompatibilityEnabled() { return tntVanillaCompatibilityEnabled; }
    public boolean isTNTUseVanillaPower() { return tntUseVanillaPower; }
    public boolean isTNTUseVanillaFireLogic() { return tntUseVanillaFireLogic; }
    public boolean isTNTUseVanillaDamageCalculation() { return tntUseVanillaDamageCalculation; }
    public boolean isTNTUseFullRaycast() { return tntUseFullRaycast; }
    public boolean isBeeFixEnabled() { return beeFixEnabled; }
    public boolean isTNTUseVanillaBlockDestruction() { return tntUseVanillaBlockDestruction; }
    public boolean isTNTUseVanillaDrops() { return tntUseVanillaDrops; }
    public boolean isChunkTickAsyncEnabled() { return chunkTickAsyncEnabled; }
    public int getChunkTickThreads() { return chunkTickThreads; }
    public long getChunkTickTimeoutMicros() { return chunkTickTimeoutMicros; }
    
    public int getConfigVersion() {
        return configVersion;
    }
    
    public boolean isStructureLocationAsyncEnabled() { return structureLocationAsyncEnabled; }
    public int getStructureLocationThreads() { return structureLocationThreads; }
    public boolean isLocateCommandEnabled() { return locateCommandEnabled; }
    public int getLocateCommandSearchRadius() { return locateCommandSearchRadius; }
    public boolean isLocateCommandSkipKnownStructures() { return locateCommandSkipKnownStructures; }
    public boolean isVillagerTradeMapsEnabled() { return villagerTradeMapsEnabled; }
    public java.util.Set<String> getVillagerTradeMapTypes() { return villagerTradeMapTypes; }
    public int getVillagerMapGenerationTimeoutSeconds() { return villagerMapGenerationTimeoutSeconds; }
    public boolean isDolphinTreasureHuntEnabled() { return dolphinTreasureHuntEnabled; }
    public int getDolphinTreasureSearchRadius() { return dolphinTreasureSearchRadius; }
    public int getDolphinTreasureHuntInterval() { return dolphinTreasureHuntInterval; }
    public boolean isChestExplorationMapsEnabled() { return chestExplorationMapsEnabled; }
    public java.util.Set<String> getChestExplorationLootTables() { return chestExplorationLootTables; }
    public boolean isChestMapPreserveProbability() { return chestMapPreserveProbability; }
    public boolean isStructureLocationDebugEnabled() { return enableDebugLogging; }
    
    public boolean isStructureAlgorithmOptimizationEnabled() { return structureAlgorithmOptimizationEnabled; }
    public String getStructureSearchPattern() { return structureSearchPattern; }
    public boolean isStructureCachingEnabled() { return structureCachingEnabled; }
    public boolean isStructurePrecomputationEnabled() { return structurePrecomputationEnabled; }
    public boolean isBiomeAwareSearchEnabled() { return biomeAwareSearchEnabled; }
    public int getStructureCacheMaxSize() { return structureCacheMaxSize; }
    public long getStructureCacheExpirationMinutes() { return structureCacheExpirationMinutes; }
    
    public boolean isDataPackOptimizationEnabled() { return dataPackOptimizationEnabled; }
    public int getDataPackFileLoadThreads() { return dataPackFileLoadThreads; }
    public int getDataPackZipProcessThreads() { return dataPackZipProcessThreads; }
    public int getDataPackBatchSize() { return dataPackBatchSize; }
    public long getDataPackCacheExpirationMinutes() { return dataPackCacheExpirationMinutes; }
    public boolean isDataPackDebugEnabled() { return dataPackDebugEnabled; }
    
    public boolean isNitoriOptimizationsEnabled() { return nitoriOptimizationsEnabled; }
    public boolean isVirtualThreadEnabled() { return virtualThreadEnabled; }
    public boolean isWorkStealingEnabled() { return workStealingEnabled; }
    public boolean isBlockPosCacheEnabled() { return blockPosCacheEnabled; }
    public boolean isOptimizedCollectionsEnabled() { return optimizedCollectionsEnabled; }
    
    public boolean isSecureSeedEnabled() { return secureSeedEnabled; }
    public boolean isSecureSeedProtectStructures() { return secureSeedProtectStructures; }
    public boolean isSecureSeedProtectOres() { return secureSeedProtectOres; }
    public boolean isSecureSeedProtectSlimes() { return secureSeedProtectSlimes; }
    public int getSecureSeedBits() { return secureSeedBits; }
    public boolean isSecureSeedDebugLogging() { return enableDebugLogging; }
    
    // ==========================================
    // ???????? Getter / Async Pathfinding Config Getters (v8.0)
    // ==========================================
    public boolean isAsyncPathfindingEnabled() { return asyncPathfindingEnabled; }
    public int getAsyncPathfindingMaxThreads() { return asyncPathfindingMaxThreads; }
    public long getAsyncPathfindingKeepAliveSeconds() { return asyncPathfindingKeepAliveSeconds; }
    public int getAsyncPathfindingMaxQueueSize() { return asyncPathfindingMaxQueueSize; }
    public int getAsyncPathfindingTimeoutMs() { return asyncPathfindingTimeoutMs; }
    
    // ==========================================
    // ?????? Getter / Density Control Config Getter (v8.0)
    // ==========================================
    public boolean isDensityControlEnabled() { return densityControlEnabled; }
    
    // ==========================================
    // TNT ?????? Getter / TNT Land Protection Config Getter (v8.0)
    // ==========================================
    public boolean isTNTLandProtectionEnabled() { return tntLandProtectionEnabled; }
    
    // ==========================================
    // Falling Block Parallel Getters (v14.0)
    // ==========================================
    public boolean isFallingBlockParallelEnabled() { return fallingBlockParallelEnabled; }
    public int getMinFallingBlocksForParallel() { return minFallingBlocksForParallel; }
    public int getFallingBlockBatchSize() { return fallingBlockBatchSize; }
    
    // ==========================================
    // Item Entity Parallel Getters (v14.0)
    // ==========================================
    public boolean isItemEntityParallelEnabled() { return itemEntityParallelEnabled; }
    public int getMinItemEntitiesForParallel() { return minItemEntitiesForParallel; }
    public int getItemEntityBatchSize() { return itemEntityBatchSize; }
    public boolean isItemEntityMergeOptimizationEnabled() { return itemEntityMergeOptimizationEnabled; }
    public int getItemEntityMergeInterval() { return itemEntityMergeInterval; }
    public double getItemEntityMergeRange() { return itemEntityMergeRange; }
    public boolean isItemEntityAgeOptimizationEnabled() { return itemEntityAgeOptimizationEnabled; }
    public double getItemEntityPlayerDetectionRange() { return itemEntityPlayerDetectionRange; }
    
    // ==========================================
    // Minecart Cauldron Destruction Getter (v14.0)
    // ==========================================
    public boolean isMinecartCauldronDestructionEnabled() { return minecartCauldronDestructionEnabled; }
    
    // ==========================================
    // Config Version Getter (v14.0)
    // ==========================================
    public int getCurrentConfigVersion() { return CURRENT_CONFIG_VERSION; }
    
    // ==========================================
    // Network Optimization Getters (v14.0)
    // ==========================================
    public boolean isNetworkOptimizationEnabled() { return networkOptimizationEnabled; }
    public boolean isPacketPriorityEnabled() { return packetPriorityEnabled; }
    public boolean isChunkRateControlEnabled() { return chunkRateControlEnabled; }
    public boolean isCongestionDetectionEnabled() { return congestionDetectionEnabled; }
    public boolean isViewFrustumFilterEnabled() { return viewFrustumFilterEnabled; }
    public boolean isViewFrustumFilterEntities() { return viewFrustumFilterEntities; }
    public boolean isViewFrustumFilterBlocks() { return viewFrustumFilterBlocks; }
    public boolean isViewFrustumFilterParticles() { return viewFrustumFilterParticles; }
    public int getHighPingThreshold() { return highPingThreshold; }
    public int getCriticalPingThreshold() { return criticalPingThreshold; }
    public long getHighBandwidthThreshold() { return highBandwidthThreshold; }
    public int getBaseChunkSendRate() { return baseChunkSendRate; }
    public int getMaxChunkSendRate() { return maxChunkSendRate; }
    public int getMinChunkSendRate() { return minChunkSendRate; }
    
    // ==========================================
    // Fast Movement Chunk Load Getters
    // ==========================================
    public boolean isFastMovementChunkLoadEnabled() { return fastMovementChunkLoadEnabled; }
    public double getFastMovementSpeedThreshold() { return fastMovementSpeedThreshold; }
    public int getFastMovementPreloadDistance() { return fastMovementPreloadDistance; }
    public int getFastMovementMaxConcurrentLoads() { return fastMovementMaxConcurrentLoads; }
    public int getFastMovementPredictionTicks() { return fastMovementPredictionTicks; }
    // Center Offset Getters
    public boolean isCenterOffsetEnabled() { return centerOffsetEnabled; }
    public double getCenterOffsetMinSpeed() { return centerOffsetMinSpeed; }
    public double getCenterOffsetMaxSpeed() { return centerOffsetMaxSpeed; }
    public double getCenterOffsetMaxRatio() { return centerOffsetMaxRatio; }
    // Async Loading Getters
    public int getAsyncLoadingBatchSize() { return asyncLoadingBatchSize; }
    public long getAsyncLoadingBatchDelayMs() { return asyncLoadingBatchDelayMs; }
    // Teleport Optimization Getters
    public boolean isTeleportOptimizationEnabled() { return teleportOptimizationEnabled; }
    public boolean isTeleportPacketBypassEnabled() { return teleportPacketBypassEnabled; }
    public int getTeleportBoostDurationSeconds() { return teleportBoostDurationSeconds; }
    public int getTeleportMaxChunkRate() { return teleportMaxChunkRate; }
    public boolean isTeleportFilterNonEssentialPackets() { return teleportFilterNonEssentialPackets; }
    public boolean isTeleportDebugEnabled() { return teleportDebugEnabled; }
    // Virtual Entity Compatibility Getters
    public boolean isVirtualEntityCompatibilityEnabled() { return virtualEntityCompatibilityEnabled; }
    public boolean isVirtualEntityDebugEnabled() { return virtualEntityDebugEnabled; }
    public boolean isVirtualEntityBypassPacketQueue() { return virtualEntityBypassPacketQueue; }
    public boolean isVirtualEntityExcludeFromThrottling() { return virtualEntityExcludeFromThrottling; }
    public boolean isFancynpcsCompatEnabled() { return fancynpcsCompatEnabled; }
    public boolean isFancynpcsUseAPI() { return fancynpcsUseAPI; }
    public int getFancynpcsPriority() { return fancynpcsPriority; }
    public boolean isZnpcsplusCompatEnabled() { return znpcsplusCompatEnabled; }
    public int getZnpcsplusPriority() { return znpcsplusPriority; }
    
    // ==========================================
    // Suffocation Optimization Getters (v3.2.15)
    // ==========================================
    public boolean isSuffocationOptimizationEnabled() { return suffocationOptimizationEnabled; }
    
    // ==========================================
    // BlockLocker Protection Getters (v3.2.15)
    // ==========================================
    public boolean isBlockLockerProtectionEnabled() { return blockLockerProtectionEnabled; }
    
    // ==========================================
    // TNT Sakura Optimization Getters (v3.2.16)
    // ==========================================
    public boolean isTNTUseSakuraDensityCache() { return tntUseSakuraDensityCache; }
    public boolean isTNTMergeEnabled() { return tntMergeEnabled; }
    public double getTNTMergeRadius() { return tntMergeRadius; }
    public int getTNTMaxFuseDifference() { return tntMaxFuseDifference; }
    public float getTNTMergedPowerMultiplier() { return tntMergedPowerMultiplier; }
    
    // ==========================================
    // Redstone Sakura Optimization Getters (v3.2.16)
    // ==========================================
    public boolean isUsePandaWireAlgorithm() { return usePandaWireAlgorithm; }
    public boolean isRedstoneNetworkCacheEnabled() { return redstoneNetworkCacheEnabled; }
    public int getRedstoneNetworkCacheExpireTicks() { return redstoneNetworkCacheExpireTicks; }
    
    // ==========================================
    // SecureSeed Getters (v3.2.16)
    // ==========================================
    public boolean isSeedEncryptionEnabled() { return seedEncryptionEnabled; }
    public String getSeedCommandDenyMessage() { return seedCommandDenyMessage; }
    public String getSeedEncryptionScheme() { 
        return config != null ? config.getString("seed-encryption.scheme", "secureseed") : "secureseed"; 
    }
    public int getQuantumSeedEncryptionLevel() { 
        return config != null ? config.getInt("seed-encryption.quantum-level", 3) : 3; 
    }
    
    public boolean getBoolean(String path, boolean defaultValue) {
        return config != null ? config.getBoolean(path, defaultValue) : defaultValue;
    }
    
    // ç¨äşçŹçŤć¨Ąĺźďźç´ćĽäťĺ¤é¨čŽžç˝Ž config
    public void setConfig(FileConfiguration config) {
        this.config = config;
    }
    
    // ç¨äşçŹçŤć¨ĄĺźďźčŽžç˝?Logger
    public void setLogger(java.util.logging.Logger logger) {
        this.logger = logger;
    }
    
    // čˇĺ Loggerďźäźĺä˝żç¨çŹçŤć¨Ąĺźç loggerďźĺŚĺä˝żç?plugin ç?logger
    private java.util.logging.Logger getLogger() {
        if (logger != null) {
            return logger;
        }
        if (plugin != null) {
            return plugin.getLogger();
        }
        // ĺŚćć˛Ąć loggerďźčżĺä¸ä¸ŞçŠş logger ćä˝żç¨çłťçťéťčŽ?logger
        return java.util.logging.Logger.getLogger("AkiAsync");
    }
    
    // äżĺ­éç˝Žďźç¨äşçŹçŤć¨Ąĺźďź
    private void saveConfig() {
        if (plugin != null) {
            plugin.saveConfig();
        } else if (config instanceof org.bukkit.configuration.file.YamlConfiguration) {
            try {
                java.io.File configFile = new java.io.File("plugins/AkiAsync/config.yml");
                ((org.bukkit.configuration.file.YamlConfiguration) config).save(configFile);
            } catch (Exception e) {
                getLogger().warning("Failed to save config: " + e.getMessage());
            }
        }
    }
}
