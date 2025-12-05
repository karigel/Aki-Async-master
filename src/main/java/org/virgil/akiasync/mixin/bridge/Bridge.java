package org.virgil.akiasync.mixin.bridge;

import java.util.concurrent.ExecutorService;

public interface Bridge {
    
    boolean isNitoriOptimizationsEnabled();
    
    boolean isVirtualThreadEnabled();
    
    boolean isWorkStealingEnabled();
    
    boolean isBlockPosCacheEnabled();
    
    boolean isOptimizedCollectionsEnabled();
    
    boolean isEntityTickParallel();
    
    int getEntityTickThreads();
    
    int getMinEntitiesForParallel();
    
    int getEntityTickBatchSize();
    
    boolean isBrainThrottleEnabled();
    
    int getBrainThrottleInterval();
    
    long getAsyncAITimeoutMicros();
    
    boolean isVillagerOptimizationEnabled();
    
    boolean isVillagerUsePOISnapshot();
    
    boolean isPiglinOptimizationEnabled();
    
    boolean isPiglinUsePOISnapshot();
    
    int getPiglinLookDistance();
    
    int getPiglinBarterDistance();
    
    boolean isPillagerFamilyOptimizationEnabled();
    
    boolean isPillagerFamilyUsePOISnapshot();
    
    boolean isEvokerOptimizationEnabled();
    boolean isBlazeOptimizationEnabled();
    boolean isGuardianOptimizationEnabled();
    boolean isWitchOptimizationEnabled();
    boolean isUniversalAiOptimizationEnabled();
    java.util.Set<String> getUniversalAiEntities();
    boolean isZeroDelayFactoryOptimizationEnabled();
    java.util.Set<String> getZeroDelayFactoryEntities();
    boolean isBlockEntityParallelTickEnabled();
    int getBlockEntityParallelMinBlockEntities();
    int getBlockEntityParallelBatchSize();
    boolean isBlockEntityParallelProtectContainers();
    int getBlockEntityParallelTimeoutMs();
    boolean isItemEntityOptimizationEnabled();
    int getItemEntityAgeInterval();
    int getItemEntityMinNearbyItems();
    
    boolean isSimpleEntitiesOptimizationEnabled();
    
    boolean isSimpleEntitiesUsePOISnapshot();
    
    boolean isMobSpawningEnabled();
    
    int getMaxEntitiesPerChunk();
    
    boolean isSpawnerOptimizationEnabled();
    
    boolean isEntityTrackerEnabled();
    
    int getEntityTrackerQueueSize();
    
    boolean isPredicateCacheEnabled();
    
    boolean isBlockPosPoolEnabled();
    
    boolean isListPreallocEnabled();
    
    int getListPreallocCapacity();
    
    boolean isPushOptimizationEnabled();
    
    boolean isEntityLookupCacheEnabled();
    
    int getEntityLookupCacheDurationMs();
    
    boolean isCollisionOptimizationEnabled();
    
    ExecutorService getGeneralExecutor();
    
    ExecutorService getTNTExecutor();
    
    ExecutorService getChunkTickExecutor();
    
    ExecutorService getVillagerBreedExecutor();
    
    ExecutorService getBrainExecutor();
    
    boolean isAsyncLightingEnabled();
    
    ExecutorService getLightingExecutor();
    
    int getLightBatchThreshold();
    
    boolean useLayeredPropagationQueue();
    
    int getMaxLightPropagationDistance();
    
    boolean isSkylightCacheEnabled();
    
    int getSkylightCacheDurationMs();
    
    boolean isLightDeduplicationEnabled();
    
    boolean isDynamicBatchAdjustmentEnabled();
    
    boolean isAdvancedLightingStatsEnabled();
    
    boolean isPlayerChunkLoadingOptimizationEnabled();
    
    int getMaxConcurrentChunkLoadsPerPlayer();
    
    boolean isEntityTrackingRangeOptimizationEnabled();
    
    double getEntityTrackingRangeMultiplier();
    
    boolean isAlternateCurrentEnabled();
    
    boolean isRedstoneWireTurboEnabled();
    
    boolean isRedstoneUpdateBatchingEnabled();
    
    int getRedstoneUpdateBatchThreshold();
    
    boolean isRedstoneCacheEnabled();
    
    int getRedstoneCacheDurationMs();
    
    boolean isTNTOptimizationEnabled();
    
    java.util.Set<String> getTNTExplosionEntities();
    
    int getTNTThreads();
    
    int getTNTMaxBlocks();
    
    long getTNTTimeoutMicros();
    
    int getTNTBatchSize();
    
    boolean isTNTDebugEnabled();
    
    boolean isTNTVanillaCompatibilityEnabled();
    
    boolean isTNTUseVanillaPower();
    
    boolean isTNTUseVanillaFireLogic();
    
    boolean isTNTUseVanillaDamageCalculation();
    
    boolean isBeeFixEnabled();

    boolean isTNTUseFullRaycast();
    
    boolean isTNTUseVanillaBlockDestruction();
    
    boolean isTNTUseVanillaDrops();
    
    boolean isFoliaEnvironment();
    
    boolean isOwnedByCurrentRegion(net.minecraft.server.level.ServerLevel level, net.minecraft.core.BlockPos pos);
    void scheduleRegionTask(net.minecraft.server.level.ServerLevel level, net.minecraft.core.BlockPos pos, Runnable task);
    
    boolean canAccessEntityDirectly(net.minecraft.world.entity.Entity entity);
    boolean canAccessBlockPosDirectly(net.minecraft.world.level.Level level, net.minecraft.core.BlockPos pos);

    void safeExecute(Runnable task, String context);
    
    String checkExecutorHealth(java.util.concurrent.ExecutorService executor, String name);
    
    String getBlockId(net.minecraft.world.level.block.Block block);
    
    boolean isAsyncVillagerBreedEnabled();
    
    boolean isVillagerAgeThrottleEnabled();
    
    int getVillagerBreedThreads();
    
    int getVillagerBreedCheckInterval();
    
    boolean isChunkTickAsyncEnabled();
    
    int getChunkTickThreads();
    
    long getChunkTickTimeoutMicros();
    
    boolean isStructureLocationAsyncEnabled();
    
    int getStructureLocationThreads();
    
    boolean isLocateCommandEnabled();
    
    int getLocateCommandSearchRadius();
    
    boolean isLocateCommandSkipKnownStructures();
    
    boolean isVillagerTradeMapsEnabled();
    
    java.util.Set<String> getVillagerTradeMapTypes();
    
    int getVillagerMapGenerationTimeoutSeconds();
    
    boolean isDolphinTreasureHuntEnabled();
    
    int getDolphinTreasureSearchRadius();
    
    int getDolphinTreasureHuntInterval();
    
    boolean isChestExplorationMapsEnabled();
    
    java.util.Set<String> getChestExplorationLootTables();
    
    boolean isChestMapPreserveProbability();
    
    boolean isStructureLocationDebugEnabled();
    
    boolean isStructureAlgorithmOptimizationEnabled();
    
    String getStructureSearchPattern();
    
    boolean isStructureCachingEnabled();
    
    boolean isStructurePrecomputationEnabled();
    
    boolean isBiomeAwareSearchEnabled();
    
    int getStructureCacheMaxSize();
    
    long getStructureCacheExpirationMinutes();
    
    void handleLocateCommandResult(net.minecraft.commands.CommandSourceStack sourceStack, net.minecraft.core.BlockPos structurePos, Throwable throwable);
    
    void handleLocateCommandAsyncStart(net.minecraft.commands.CommandSourceStack sourceStack, net.minecraft.commands.arguments.ResourceOrTagKeyArgument.Result<net.minecraft.world.level.levelgen.structure.Structure> structureResult, net.minecraft.core.HolderSet<net.minecraft.world.level.levelgen.structure.Structure> holderSet);
    
    void handleDolphinTreasureResult(net.minecraft.world.entity.animal.Dolphin dolphin, net.minecraft.core.BlockPos treasurePos, Throwable throwable);
    
    void handleChestExplorationMapResult(net.minecraft.world.item.ItemStack stack, net.minecraft.world.level.storage.loot.LootContext context, net.minecraft.core.BlockPos structurePos, net.minecraft.core.Holder<net.minecraft.world.level.saveddata.maps.MapDecorationType> mapDecoration, byte zoom, Throwable throwable, Object cir);
    
    void handleVillagerTradeMapResult(net.minecraft.world.item.trading.MerchantOffer offer, net.minecraft.world.entity.Entity trader, net.minecraft.core.BlockPos structurePos, net.minecraft.core.Holder<net.minecraft.world.level.saveddata.maps.MapDecorationType> destinationType, String displayName, int maxUses, int villagerXp, Throwable throwable, Object cir);
    
    int getVillagerTradeMapsSearchRadius();
    
    boolean isVillagerTradeMapsSkipKnownStructures();
    
    boolean isDolphinTreasureSkipKnownStructures();
    
    boolean isDataPackOptimizationEnabled();
    
    int getDataPackFileLoadThreads();
    
    int getDataPackZipProcessThreads();
    
    int getDataPackBatchSize();
    
    long getDataPackCacheExpirationMinutes();
    
    boolean isDataPackDebugEnabled();
    
    boolean isDebugLoggingEnabled();
    
    void debugLog(String message);
    void debugLog(String format, Object... args);
    void errorLog(String message);
    void errorLog(String format, Object... args);
    
    boolean isVirtualEntity(net.minecraft.world.entity.Entity entity);
    
    boolean isSecureSeedEnabled();
    boolean isSecureSeedProtectStructures();
    boolean isSecureSeedProtectOres();
    boolean isSecureSeedProtectSlimes();
    int getSecureSeedBits();
    boolean isSecureSeedDebugLogging();
    
    // ==========================================
    // 异步路径查找 / Async Pathfinding (v8.0)
    // ==========================================
    boolean isAsyncPathfindingEnabled();
    int getAsyncPathfindingMaxThreads();
    long getAsyncPathfindingKeepAliveSeconds();
    int getAsyncPathfindingMaxQueueSize();
    int getAsyncPathfindingTimeoutMs();
    
    // ==========================================
    // 密度控制 / Density Control (v8.0)
    // ==========================================
    boolean isDensityControlEnabled();
    
    // ==========================================
    // TNT 土地保护 / TNT Land Protection (v8.0)
    // ==========================================
    boolean isTNTLandProtectionEnabled();
    boolean canTNTExplodeAt(net.minecraft.server.level.ServerLevel level, net.minecraft.core.BlockPos pos);
    
    // ==========================================
    // 实体节流 / Entity Throttling (v8.0)
    // ==========================================
    boolean shouldThrottleEntity(Object entity);
    
    // ==========================================
    // ViaVersion 兼容 / ViaVersion Compatibility
    // ==========================================
    boolean isPlayerUsingViaVersion(java.util.UUID playerId);
    boolean isViaConnectionInPlayState(java.util.UUID playerId);
    int getPlayerProtocolVersion(java.util.UUID playerId);
    
    // ==========================================
    // 窒息优化 / Suffocation Optimization (v3.2.15)
    // ==========================================
    boolean isSuffocationOptimizationEnabled();
    
    // ==========================================
    // BlockLocker 保护 / BlockLocker Protection (v3.2.15)
    // ==========================================
    boolean isBlockLockerProtectionEnabled();
    boolean isBlockLockerProtected(net.minecraft.server.level.ServerLevel level, net.minecraft.core.BlockPos pos, net.minecraft.world.level.block.state.BlockState state);
    
    // ==========================================
    // 性能指标 / Performance Metrics (v3.2.16)
    // ==========================================
    double getCurrentTPS();
    double getCurrentMSPT();
    
    // ==========================================
    // 任务平滑调度 / Task Smoothing Scheduler (v3.2.16)
    // ==========================================
    Object getBlockTickSmoothingScheduler();
    Object getEntityTickSmoothingScheduler();
    Object getBlockEntitySmoothingScheduler();
    boolean submitSmoothTask(Object scheduler, Runnable task, int priority, String category);
    int submitSmoothTaskBatch(Object scheduler, java.util.List<Runnable> tasks, int priority, String category);
    void notifySmoothSchedulerTick(Object scheduler);
    void updateSmoothSchedulerMetrics(Object scheduler, double tps, double mspt);
    
    // ==========================================
    // TNT Sakura 优化 / TNT Sakura Optimization (v3.2.16)
    // ==========================================
    boolean isTNTUseSakuraDensityCache();
    boolean isTNTMergeEnabled();
    double getTNTMergeRadius();
    int getTNTMaxFuseDifference();
    float getTNTMergedPowerMultiplier();
    
    // ==========================================
    // 红石 Sakura 优化 / Redstone Sakura Optimization (v3.2.16)
    // ==========================================
    boolean isUsePandaWireAlgorithm();
    boolean isRedstoneNetworkCacheEnabled();
    int getRedstoneNetworkCacheExpireTicks();
    
    // ==========================================
    // Sakura 缓存管理 / Sakura Cache Management (v3.2.16)
    // ==========================================
    void clearSakuraOptimizationCaches();
    java.util.Map<String, Object> getSakuraCacheStatistics();
    void performSakuraCacheCleanup();
    
    // ==========================================
    // 主线程执行 / Main Thread Execution (v3.2.16)
    // ==========================================
    void runOnMainThread(Runnable task);
    
    // ==========================================
    // SecureSeed 配置 / SecureSeed Configuration (v3.2.16)
    // ==========================================
    boolean isSeedEncryptionEnabled();
    String getSeedCommandDenyMessage();
    
    // SecureSeed 保护方法 / SecureSeed Protection Methods
    boolean isSeedProtectionEnabled();
    boolean isSeedEncryptionProtectOres();
    boolean isSeedEncryptionProtectSlimes();
    boolean isSeedEncryptionProtectStructures();
    boolean isQuantumSeedEnabled();
    long getEncryptedSeed(long originalSeed, int chunkX, int chunkZ, String dimension, String saltType, long gameTime);
    long[] getSecureSeedWorldSeed();
}