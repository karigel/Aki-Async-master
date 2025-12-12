package org.virgil.akiasync.ignite;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.logging.Logger;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.ResourceOrTagKeyArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.Dolphin;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.saveddata.maps.MapDecorationType;
import net.minecraft.world.level.storage.loot.LootContext;

import org.virgil.akiasync.ignite.config.IgniteConfigManager;
import org.virgil.akiasync.executor.TaskSmoothingScheduler;
import org.virgil.akiasync.mixin.bridge.Bridge;

/**
 * Ignite 模式专用的 Bridge 实现
 * Complete Bridge implementation for Ignite mode
 * 
 * 不依赖 AkiAsyncPlugin，直接使用 ConfigManager 和 Executors
 */
public class IgniteAkiAsyncBridge implements Bridge {

    private volatile IgniteConfigManager config;  // 非 final，支持热重载
    private final ExecutorService generalExecutor;
    private final ExecutorService lightingExecutor;
    private final ExecutorService tntExecutor;
    private final ExecutorService chunkTickExecutor;
    private final ExecutorService villagerBreedExecutor;
    private final ExecutorService brainExecutor;
    private final Logger logger;
    
    // 平滑调度器
    private TaskSmoothingScheduler blockTickScheduler;
    private TaskSmoothingScheduler entityTickScheduler;
    private TaskSmoothingScheduler blockEntityScheduler;
    
    // 区块加载调度器（由 IgnitePluginAdapter 设置）
    private org.virgil.akiasync.chunk.ChunkLoadPriorityScheduler chunkLoadScheduler;
    
    // Folia 检测
    private final boolean isFolia;

    public IgniteAkiAsyncBridge(
            IgniteConfigManager config,
            ExecutorService generalExecutor,
            ExecutorService lightingExecutor,
            ExecutorService tntExecutor,
            ExecutorService chunkTickExecutor,
            ExecutorService villagerBreedExecutor,
            ExecutorService brainExecutor) {
        this.config = config;
        this.generalExecutor = generalExecutor;
        this.lightingExecutor = lightingExecutor;
        this.tntExecutor = tntExecutor;
        this.chunkTickExecutor = chunkTickExecutor;
        this.villagerBreedExecutor = villagerBreedExecutor;
        this.brainExecutor = brainExecutor;
        this.logger = Logger.getLogger("AkiAsync");
        
        // 检测 Folia
        boolean folia = false;
        try {
            Class.forName("io.papermc.paper.threadedregions.RegionizedServer");
            folia = true;
        } catch (ClassNotFoundException e) {
            // Not Folia
        }
        this.isFolia = folia;
        
        // 初始化平滑调度器
        initializeSmoothingSchedulers();
    }
    
    private void initializeSmoothingSchedulers() {
        try {
            if (isFolia) {
                logger.info("[AkiAsync/Ignite] Folia environment - TaskSmoothingScheduler disabled");
                return;
            }
            
            if (config != null && config.isChunkTickAsyncEnabled() && generalExecutor != null) {
                int batchSize = config.getChunkTickAsyncBatchSize();
                blockTickScheduler = new TaskSmoothingScheduler(generalExecutor, batchSize * 10, batchSize * 2, 3);
            }
            
            if (config != null && config.isEntityTickParallel() && generalExecutor != null) {
                int batchSize = config.getEntityTickBatchSize();
                entityTickScheduler = new TaskSmoothingScheduler(generalExecutor, batchSize * 20, batchSize * 3, 2);
            }
            
            if (config != null && config.isBlockEntityParallelTickEnabled() && generalExecutor != null) {
                int batchSize = config.getBlockEntityParallelBatchSize();
                blockEntityScheduler = new TaskSmoothingScheduler(generalExecutor, batchSize * 15, batchSize * 2, 3);
            }
        } catch (Exception e) {
            logger.warning("[AkiAsync/Ignite] Failed to initialize TaskSmoothingSchedulers: " + e.getMessage());
        }
    }
    
    public void setChunkLoadScheduler(org.virgil.akiasync.chunk.ChunkLoadPriorityScheduler scheduler) {
        this.chunkLoadScheduler = scheduler;
    }

    // ==========================================
    // Nitori 优化配置
    // ==========================================
    @Override public boolean isNitoriOptimizationsEnabled() { return config != null && config.isNitoriOptimizationsEnabled(); }
    @Override public boolean isVirtualThreadEnabled() { return config != null && config.isVirtualThreadEnabled(); }
    @Override public boolean isWorkStealingEnabled() { return config != null && config.isWorkStealingEnabled(); }
    @Override public boolean isBlockPosCacheEnabled() { return config != null && config.isBlockPosCacheEnabled(); }
    @Override public boolean isOptimizedCollectionsEnabled() { return config != null && config.isOptimizedCollectionsEnabled(); }

    // ==========================================
    // 实体 Tick 并行
    // ==========================================
    @Override public boolean isEntityTickParallel() { return config != null && config.isEntityTickParallel(); }
    @Override public int getEntityTickThreads() { return config != null ? config.getEntityTickThreads() : 4; }
    @Override public int getMinEntitiesForParallel() { return config != null ? config.getMinEntitiesForParallel() : 50; }
    @Override public int getEntityTickBatchSize() { return config != null ? config.getEntityTickBatchSize() : 20; }

    // ==========================================
    // Brain 节流
    // ==========================================
    @Override public boolean isBrainThrottleEnabled() { return config != null && config.isBrainThrottleEnabled(); }
    @Override public int getBrainThrottleInterval() { return config != null ? config.getBrainThrottleInterval() : 2; }

    // ==========================================
    // 生物移动优化
    // ==========================================
    @Override public boolean isLivingEntityTravelOptimizationEnabled() { return config != null && config.isLivingEntityTravelOptimizationEnabled(); }
    @Override public int getLivingEntityTravelSkipInterval() { return config != null ? config.getLivingEntityTravelSkipInterval() : 2; }

    // ==========================================
    // 行为节流
    // ==========================================
    @Override public boolean isBehaviorThrottleEnabled() { return config != null && config.isBehaviorThrottleEnabled(); }
    @Override public int getBehaviorThrottleInterval() { return config != null ? config.getBehaviorThrottleInterval() : 2; }

    // ==========================================
    // 生物消失优化
    // ==========================================
    @Override public boolean isMobDespawnOptimizationEnabled() { return config != null && config.isMobDespawnOptimizationEnabled(); }
    @Override public int getMobDespawnCheckInterval() { return config != null ? config.getMobDespawnCheckInterval() : 20; }

    // ==========================================
    // 异步 AI 超时
    // ==========================================
    @Override public long getAsyncAITimeoutMicros() { return config != null ? config.getAsyncAITimeoutMicros() : 5000; }

    // ==========================================
    // 村民优化
    // ==========================================
    @Override public boolean isVillagerOptimizationEnabled() { return config != null && config.isVillagerOptimizationEnabled(); }
    @Override public boolean isVillagerUsePOISnapshot() { return config != null && config.isVillagerUsePOISnapshot(); }
    @Override public boolean isVillagerPoiCacheEnabled() { return config != null && config.isVillagerPoiCacheEnabled(); }
    @Override public int getVillagerPoiCacheExpireTime() { return config != null ? config.getVillagerPoiCacheExpireTime() : 200; }

    // ==========================================
    // 猪灵优化
    // ==========================================
    @Override public boolean isPiglinOptimizationEnabled() { return config != null && config.isPiglinOptimizationEnabled(); }
    @Override public boolean isPiglinUsePOISnapshot() { return config != null && config.isPiglinUsePOISnapshot(); }
    @Override public int getPiglinLookDistance() { return config != null ? config.getPiglinLookDistance() : 8; }
    @Override public int getPiglinBarterDistance() { return config != null ? config.getPiglinBarterDistance() : 8; }

    // ==========================================
    // 掠夺者优化
    // ==========================================
    @Override public boolean isPillagerFamilyOptimizationEnabled() { return config != null && config.isPillagerFamilyOptimizationEnabled(); }
    @Override public boolean isPillagerFamilyUsePOISnapshot() { return config != null && config.isPillagerFamilyUsePOISnapshot(); }

    // ==========================================
    // 其他生物优化
    // ==========================================
    @Override public boolean isEvokerOptimizationEnabled() { return config != null && config.isEvokerOptimizationEnabled(); }
    @Override public boolean isBlazeOptimizationEnabled() { return config != null && config.isBlazeOptimizationEnabled(); }
    @Override public boolean isGuardianOptimizationEnabled() { return config != null && config.isGuardianOptimizationEnabled(); }
    @Override public boolean isWitchOptimizationEnabled() { return config != null && config.isWitchOptimizationEnabled(); }
    @Override public boolean isUniversalAiOptimizationEnabled() { return config != null && config.isUniversalAiOptimizationEnabled(); }
    @Override public Set<String> getUniversalAiEntities() { return config != null ? config.getUniversalAiEntities() : Collections.emptySet(); }

    // ==========================================
    // DAB (距离激活行为)
    // ==========================================
    @Override public boolean isDabEnabled() { return config != null && config.isDabEnabled(); }
    @Override public int getDabStartDistance() { return config != null ? config.getDabStartDistance() : 12; }
    @Override public int getDabActivationDistMod() { return config != null ? config.getDabActivationDistMod() : 8; }
    @Override public int getDabMaxTickInterval() { return config != null ? config.getDabMaxTickInterval() : 20; }

    // ==========================================
    // 异步路径查找
    // ==========================================
    @Override public boolean isAsyncPathfindingEnabled() { return config != null && config.isAsyncPathfindingEnabled(); }
    @Override public int getAsyncPathfindingMaxThreads() { return config != null ? config.getAsyncPathfindingMaxThreads() : 4; }
    @Override public int getAsyncPathfindingKeepAliveSeconds() { return config != null ? (int) config.getAsyncPathfindingKeepAliveSeconds() : 60; }
    @Override public int getAsyncPathfindingMaxQueueSize() { return config != null ? config.getAsyncPathfindingMaxQueueSize() : 1000; }
    @Override public int getAsyncPathfindingTimeoutMs() { return config != null ? (int) config.getAsyncPathfindingTimeoutMs() : 50; }
    
    @Override
    public boolean shouldThrottleEntity(Object entity) {
        // 简单实现：不节流
        return false;
    }

    // ==========================================
    // 零延迟工厂优化
    // ==========================================
    @Override public boolean isZeroDelayFactoryOptimizationEnabled() { return config != null && config.isZeroDelayFactoryOptimizationEnabled(); }
    @Override public Set<String> getZeroDelayFactoryEntities() { return config != null ? config.getZeroDelayFactoryEntities() : Collections.emptySet(); }

    // ==========================================
    // 方块实体并行
    // ==========================================
    @Override public boolean isBlockEntityParallelTickEnabled() { return config != null && config.isBlockEntityParallelTickEnabled(); }
    @Override public int getBlockEntityParallelMinBlockEntities() { return config != null ? config.getBlockEntityParallelMinBlockEntities() : 10; }
    @Override public int getBlockEntityParallelBatchSize() { return config != null ? config.getBlockEntityParallelBatchSize() : 50; }
    @Override public boolean isBlockEntityParallelProtectContainers() { return config != null && config.isBlockEntityParallelProtectContainers(); }
    @Override public int getBlockEntityParallelTimeoutMs() { return config != null ? config.getBlockEntityParallelTimeoutMs() : 100; }

    // ==========================================
    // 漏斗优化
    // ==========================================
    @Override public boolean isHopperOptimizationEnabled() { return config != null && config.isHopperOptimizationEnabled(); }
    @Override public int getHopperCacheExpireTime() { return config != null ? config.getHopperCacheExpireTime() : 40; }

    // ==========================================
    // 矿车优化
    // ==========================================
    @Override public boolean isMinecartOptimizationEnabled() { return config != null && config.isMinecartOptimizationEnabled(); }
    @Override public int getMinecartTickInterval() { return config != null ? config.getMinecartTickInterval() : 2; }

    // ==========================================
    // 简单实体优化
    // ==========================================
    @Override public boolean isSimpleEntitiesOptimizationEnabled() { return config != null && config.isSimpleEntitiesOptimizationEnabled(); }
    @Override public boolean isSimpleEntitiesUsePOISnapshot() { return config != null && config.isSimpleEntitiesUsePOISnapshot(); }

    // ==========================================
    // 生物生成
    // ==========================================
    @Override public boolean isMobSpawningEnabled() { return config != null && config.isMobSpawningEnabled(); }
    @Override public boolean isDensityControlEnabled() { return config != null && config.isDensityControlEnabled(); }
    @Override public int getMaxEntitiesPerChunk() { return config != null ? config.getMaxEntitiesPerChunk() : 50; }
    @Override public boolean isSpawnerOptimizationEnabled() { return config != null && config.isSpawnerOptimizationEnabled(); }

    // ==========================================
    // 实体追踪器
    // ==========================================
    @Override public boolean isEntityTrackerEnabled() { return config != null && config.isEntityTrackerEnabled(); }
    @Override public int getEntityTrackerQueueSize() { return 1000; }

    // ==========================================
    // 谓词缓存和优化
    // ==========================================
    @Override public boolean isPredicateCacheEnabled() { return true; }
    @Override public boolean isBlockPosPoolEnabled() { return true; }
    @Override public boolean isListPreallocEnabled() { return true; }
    @Override public int getListPreallocCapacity() { return 16; }
    @Override public boolean isEntityLookupCacheEnabled() { return true; }
    @Override public int getEntityLookupCacheDurationMs() { return 50; }

    // ==========================================
    // 线程池
    // ==========================================
    @Override public ExecutorService getGeneralExecutor() { return generalExecutor; }
    @Override public ExecutorService getTNTExecutor() { return tntExecutor; }
    @Override public ExecutorService getChunkTickExecutor() { return chunkTickExecutor; }
    @Override public ExecutorService getVillagerBreedExecutor() { return villagerBreedExecutor; }
    @Override public ExecutorService getBrainExecutor() { return brainExecutor; }
    @Override public ExecutorService getLightingExecutor() { return lightingExecutor; }

    // ==========================================
    // 光照引擎
    // ==========================================
    @Override public boolean isAsyncLightingEnabled() { return config != null && config.isAsyncLightingEnabled(); }
    @Override public int getLightBatchThreshold() { return config != null ? config.getLightBatchThreshold() : 64; }
    @Override public boolean useLayeredPropagationQueue() { return config != null && config.useLayeredPropagationQueue(); }
    @Override public int getMaxLightPropagationDistance() { return config != null ? config.getMaxLightPropagationDistance() : 15; }
    @Override public boolean isSkylightCacheEnabled() { return config != null && config.isSkylightCacheEnabled(); }
    @Override public int getSkylightCacheDurationMs() { return config != null ? config.getSkylightCacheDurationMs() : 100; }
    @Override public boolean isLightDeduplicationEnabled() { return config != null && config.isLightDeduplicationEnabled(); }
    @Override public boolean isDynamicBatchAdjustmentEnabled() { return config != null && config.isDynamicBatchAdjustmentEnabled(); }
    @Override public boolean isAdvancedLightingStatsEnabled() { return config != null && config.isAdvancedLightingStatsEnabled(); }

    // ==========================================
    // 区块加载优化
    // ==========================================
    @Override public boolean isPlayerChunkLoadingOptimizationEnabled() { return config != null && config.isPlayerChunkLoadingOptimizationEnabled(); }
    @Override public int getMaxConcurrentChunkLoadsPerPlayer() { return config != null ? config.getMaxConcurrentChunkLoadsPerPlayer() : 4; }

    // ==========================================
    // 实体追踪范围
    // ==========================================
    @Override public boolean isEntityTrackingRangeOptimizationEnabled() { return config != null && config.isEntityTrackingRangeOptimizationEnabled(); }
    @Override public double getEntityTrackingRangeMultiplier() { return config != null ? config.getEntityTrackingRangeMultiplier() : 1.0; }

    // ==========================================
    // 红石优化
    // ==========================================
    @Override public boolean isAlternateCurrentEnabled() { return config != null && config.isAlternateCurrentEnabled(); }
    @Override public boolean isRedstoneWireTurboEnabled() { return config != null && config.isRedstoneWireTurboEnabled(); }
    @Override public boolean isRedstoneUpdateBatchingEnabled() { return config != null && config.isRedstoneUpdateBatchingEnabled(); }
    @Override public int getRedstoneUpdateBatchThreshold() { return config != null ? config.getRedstoneUpdateBatchThreshold() : 64; }
    @Override public boolean isRedstoneCacheEnabled() { return config != null && config.isRedstoneCacheEnabled(); }
    @Override public int getRedstoneCacheDurationMs() { return config != null ? config.getRedstoneCacheDurationMs() : 50; }
    @Override public boolean isUsePandaWireAlgorithm() { return config != null && config.isUsePandaWireAlgorithm(); }
    @Override public boolean isRedstoneNetworkCacheEnabled() { return config != null && config.isRedstoneNetworkCacheEnabled(); }
    @Override public int getRedstoneNetworkCacheExpireTicks() { return config != null ? config.getRedstoneNetworkCacheExpireTicks() : 20; }

    // ==========================================
    // TNT 优化
    // ==========================================
    @Override public boolean isTNTOptimizationEnabled() { return config != null && config.isTNTOptimizationEnabled(); }
    @Override public Set<String> getTNTExplosionEntities() { return config != null ? config.getTNTExplosionEntities() : Collections.emptySet(); }
    @Override public int getTNTThreads() { return config != null ? config.getTNTThreads() : 4; }
    @Override public int getTNTMaxBlocks() { return config != null ? config.getTNTMaxBlocks() : 500; }
    @Override public long getTNTTimeoutMicros() { return config != null ? config.getTNTTimeoutMicros() : 10000; }
    @Override public int getTNTBatchSize() { return config != null ? config.getTNTBatchSize() : 50; }
    @Override public boolean isTNTDebugEnabled() { return config != null && config.isTNTDebugEnabled(); }
    @Override public boolean isTNTVanillaCompatibilityEnabled() { return config != null && config.isTNTVanillaCompatibilityEnabled(); }
    @Override public boolean isTNTUseVanillaPower() { return config != null && config.isTNTUseVanillaPower(); }
    @Override public boolean isTNTUseVanillaFireLogic() { return config != null && config.isTNTUseVanillaFireLogic(); }
    @Override public boolean isTNTUseVanillaDamageCalculation() { return config != null && config.isTNTUseVanillaDamageCalculation(); }
    @Override public boolean isTNTUseFullRaycast() { return config != null && config.isTNTUseFullRaycast(); }
    @Override public boolean isTNTUseVanillaBlockDestruction() { return config != null && config.isTNTUseVanillaBlockDestruction(); }
    @Override public boolean isTNTUseVanillaDrops() { return config != null && config.isTNTUseVanillaDrops(); }
    @Override public boolean isTNTUseSakuraDensityCache() { return config != null && config.isTNTUseSakuraDensityCache(); }
    @Override public boolean isTNTMergeEnabled() { return config != null && config.isTNTMergeEnabled(); }
    @Override public double getTNTMergeRadius() { return config != null ? config.getTNTMergeRadius() : 0.5; }
    @Override public int getTNTMaxFuseDifference() { return config != null ? config.getTNTMaxFuseDifference() : 5; }
    @Override public float getTNTMergedPowerMultiplier() { return config != null ? config.getTNTMergedPowerMultiplier() : 1.0f; }

    // ==========================================
    // 蜜蜂修复
    // ==========================================
    @Override public boolean isBeeFixEnabled() { return config != null && config.isBeeFixEnabled(); }

    // ==========================================
    // Folia 环境
    // ==========================================
    @Override public boolean isFoliaEnvironment() { return isFolia; }
    
    @Override
    public boolean isOwnedByCurrentRegion(ServerLevel level, BlockPos pos) {
        if (!isFolia) return true;
        // Folia 环境下检查区域所有权
        return true; // 简化实现
    }
    
    @Override
    public void scheduleRegionTask(ServerLevel level, BlockPos pos, Runnable task) {
        if (!isFolia) {
            task.run();
            return;
        }
        // Folia 环境下调度区域任务
        task.run(); // 简化实现
    }
    
    @Override
    public boolean canAccessEntityDirectly(Entity entity) {
        if (!isFolia) return true;
        return true; // 简化实现
    }
    
    @Override
    public boolean canAccessBlockPosDirectly(Level level, BlockPos pos) {
        if (!isFolia || !(level instanceof ServerLevel)) return true;
        return isOwnedByCurrentRegion((ServerLevel) level, pos);
    }
    
    @Override
    public void safeExecute(Runnable task, String context) {
        try {
            task.run();
        } catch (Exception e) {
            logger.warning("[AkiAsync] Error in " + context + ": " + e.getMessage());
        }
    }
    
    @Override
    public String checkExecutorHealth(ExecutorService executor, String name) {
        if (executor == null) return name + ": null";
        if (executor.isShutdown()) return name + ": shutdown";
        if (executor.isTerminated()) return name + ": terminated";
        return name + ": healthy";
    }
    
    @Override
    public String getBlockId(Block block) {
        return block != null ? block.getDescriptionId() : "null";
    }

    // ==========================================
    // 异步村民繁殖
    // ==========================================
    @Override public boolean isAsyncVillagerBreedEnabled() { return config != null && config.isAsyncVillagerBreedEnabled(); }
    @Override public boolean isVillagerAgeThrottleEnabled() { return config != null && config.isVillagerAgeThrottleEnabled(); }
    @Override public int getVillagerBreedThreads() { return config != null ? config.getVillagerBreedThreads() : 2; }
    @Override public int getVillagerBreedCheckInterval() { return config != null ? config.getVillagerBreedCheckInterval() : 40; }

    // ==========================================
    // 区块 Tick 异步
    // ==========================================
    @Override public boolean isChunkTickAsyncEnabled() { return config != null && config.isChunkTickAsyncEnabled(); }
    @Override public int getChunkTickThreads() { return config != null ? config.getChunkTickThreads() : 2; }
    @Override public long getChunkTickTimeoutMicros() { return config != null ? config.getChunkTickTimeoutMicros() : 10000; }
    @Override public int getChunkTickAsyncBatchSize() { return config != null ? config.getChunkTickAsyncBatchSize() : 16; }

    // ==========================================
    // 结构定位异步
    // ==========================================
    @Override public boolean isStructureLocationAsyncEnabled() { return config != null && config.isStructureLocationAsyncEnabled(); }
    @Override public int getStructureLocationThreads() { return config != null ? config.getStructureLocationThreads() : 2; }
    @Override public boolean isLocateCommandEnabled() { return config != null && config.isLocateCommandEnabled(); }
    @Override public int getLocateCommandSearchRadius() { return config != null ? config.getLocateCommandSearchRadius() : 100; }
    @Override public boolean isLocateCommandSkipKnownStructures() { return config != null && config.isLocateCommandSkipKnownStructures(); }
    @Override public boolean isVillagerTradeMapsEnabled() { return config != null && config.isVillagerTradeMapsEnabled(); }
    @Override public Set<String> getVillagerTradeMapTypes() { return config != null ? config.getVillagerTradeMapTypes() : Collections.emptySet(); }
    @Override public int getVillagerMapGenerationTimeoutSeconds() { return config != null ? config.getVillagerMapGenerationTimeoutSeconds() : 30; }
    @Override public boolean isDolphinTreasureHuntEnabled() { return config != null && config.isDolphinTreasureHuntEnabled(); }
    @Override public int getDolphinTreasureSearchRadius() { return config != null ? config.getDolphinTreasureSearchRadius() : 50; }
    @Override public boolean isChestExplorationMapsEnabled() { return config != null && config.isChestExplorationMapsEnabled(); }
    @Override public Set<String> getChestExplorationLootTables() { return config != null ? config.getChestExplorationLootTables() : Collections.emptySet(); }
    @Override public boolean isStructureLocationDebugEnabled() { return config != null && config.isStructureLocationDebugEnabled(); }
    @Override public boolean isStructureAlgorithmOptimizationEnabled() { return config != null && config.isStructureAlgorithmOptimizationEnabled(); }
    @Override public String getStructureSearchPattern() { return config != null ? config.getStructureSearchPattern() : "SPIRAL"; }
    @Override public boolean isStructureCachingEnabled() { return config != null && config.isStructureCachingEnabled(); }
    @Override public boolean isBiomeAwareSearchEnabled() { return config != null && config.isBiomeAwareSearchEnabled(); }
    @Override public int getStructureCacheMaxSize() { return config != null ? config.getStructureCacheMaxSize() : 1000; }
    @Override public long getStructureCacheExpirationMinutes() { return config != null ? config.getStructureCacheExpirationMinutes() : 30; }
    @Override public int getVillagerTradeMapsSearchRadius() { return config != null ? config.getVillagerTradeMapsSearchRadius() : 100; }
    @Override public boolean isVillagerTradeMapsSkipKnownStructures() { return config != null && config.isVillagerTradeMapsSkipKnownStructures(); }
    @Override public boolean isDolphinTreasureSkipKnownStructures() { return false; }

    // 结构定位回调（完整实现）
    @Override
    public void handleLocateCommandAsyncStart(CommandSourceStack sourceStack, ResourceOrTagKeyArgument.Result<Structure> structureResult, HolderSet<Structure> holderSet) {
        java.util.concurrent.CompletableFuture.supplyAsync(() -> {
            try {
                ServerLevel level = sourceStack.getLevel();
                BlockPos startPos = BlockPos.containing(sourceStack.getPosition());
                
                if (isStructureLocationDebugEnabled()) {
                    logger.info("[AkiAsync/Ignite] Starting async locate command from " + startPos);
                }
                
                com.mojang.datafixers.util.Pair<BlockPos, net.minecraft.core.Holder<Structure>> result;
                if (isStructureAlgorithmOptimizationEnabled()) {
                    result = org.virgil.akiasync.async.structure.OptimizedStructureLocator.findNearestStructureOptimized(
                        level, holderSet, startPos,
                        getLocateCommandSearchRadius(),
                        isLocateCommandSkipKnownStructures()
                    );
                } else {
                    result = level.getChunkSource().getGenerator().findNearestMapStructure(
                        level, holderSet, startPos,
                        getLocateCommandSearchRadius(),
                        isLocateCommandSkipKnownStructures()
                    );
                }
                return result != null ? result.getFirst() : null;
            } catch (Exception e) {
                logger.warning("[AkiAsync/Ignite] Error in async locate command: " + e.getMessage());
                return null;
            }
        }, generalExecutor).whenComplete((foundStructure, asyncThrowable) -> {
            handleLocateCommandResult(sourceStack, foundStructure, asyncThrowable);
        });
    }
    
    @Override
    public void handleLocateCommandResult(CommandSourceStack sourceStack, BlockPos structurePos, Throwable throwable) {
        org.bukkit.Bukkit.getScheduler().runTask(
            org.bukkit.Bukkit.getPluginManager().getPlugins()[0], () -> {
            try {
                if (throwable != null) {
                    logger.warning("[AkiAsync/Ignite] Locate command failed: " + throwable.getMessage());
                    sourceStack.sendFailure(net.minecraft.network.chat.Component.literal("Structure location failed: " + throwable.getMessage()));
                    return;
                }
                if (structurePos != null) {
                    sourceStack.sendSuccess(() -> net.minecraft.network.chat.Component.literal(
                        "The nearest structure is at " + structurePos.getX() + ", " + structurePos.getY() + ", " + structurePos.getZ()), false);
                    if (isStructureLocationDebugEnabled()) {
                        logger.info("[AkiAsync/Ignite] Locate command completed: structure found at " + structurePos);
                    }
                } else {
                    sourceStack.sendFailure(net.minecraft.network.chat.Component.literal("Could not find that structure nearby"));
                }
            } catch (Exception e) {
                logger.warning("[AkiAsync/Ignite] Error processing locate command result: " + e.getMessage());
            }
        });
    }
    
    @Override
    public void handleDolphinTreasureResult(Dolphin dolphin, BlockPos treasurePos, Throwable throwable) {
        if (treasurePos == null && throwable == null) {
            // Start async search
            java.util.concurrent.CompletableFuture.supplyAsync(() -> {
                try {
                    ServerLevel level = (ServerLevel) dolphin.level();
                    BlockPos startPos = dolphin.blockPosition();
                    return level.findNearestMapStructure(
                        net.minecraft.tags.StructureTags.DOLPHIN_LOCATED,
                        startPos,
                        getDolphinTreasureSearchRadius(),
                        isDolphinTreasureSkipKnownStructures()
                    );
                } catch (Exception e) {
                    logger.warning("[AkiAsync/Ignite] Error in async dolphin treasure hunt: " + e.getMessage());
                    return null;
                }
            }, generalExecutor).whenComplete((foundTreasure, asyncThrowable) -> {
                handleDolphinTreasureResult(dolphin, foundTreasure, asyncThrowable);
            });
            return;
        }
        // Process result on main thread
        org.bukkit.Bukkit.getScheduler().runTask(
            org.bukkit.Bukkit.getPluginManager().getPlugins()[0], () -> {
            try {
                if (throwable != null || treasurePos == null) return;
                try {
                    java.lang.reflect.Field treasurePosField = dolphin.getClass().getDeclaredField("treasurePos");
                    treasurePosField.setAccessible(true);
                    treasurePosField.set(dolphin, treasurePos);
                    dolphin.level().addParticle(net.minecraft.core.particles.ParticleTypes.DOLPHIN,
                        dolphin.getX(), dolphin.getY(), dolphin.getZ(), 0.0D, 0.0D, 0.0D);
                } catch (Exception e) {
                    logger.warning("[AkiAsync/Ignite] Error setting dolphin treasure position: " + e.getMessage());
                }
            } catch (Exception e) {
                // Ignore
            }
        });
    }
    
    @Override
    public void handleChestExplorationMapAsyncStart(ItemStack stack, LootContext context, TagKey<Structure> destination, Holder<MapDecorationType> mapDecoration, byte zoom, int searchRadius, boolean skipKnownStructures, Object cir) {
        java.util.concurrent.CompletableFuture.supplyAsync(() -> {
            try {
                ServerLevel level = context.getLevel();
                net.minecraft.world.phys.Vec3 origin = context.getParamOrNull(
                    net.minecraft.world.level.storage.loot.parameters.LootContextParams.ORIGIN);
                BlockPos startPos = origin != null ? BlockPos.containing(origin) : new BlockPos(0, 64, 0);
                return level.findNearestMapStructure(destination, startPos, searchRadius, skipKnownStructures);
            } catch (Exception e) {
                logger.warning("[AkiAsync/Ignite] Error in async chest exploration map: " + e.getMessage());
                return null;
            }
        }, generalExecutor).whenComplete((foundStructure, asyncThrowable) -> {
            handleChestExplorationMapResult(stack, context, foundStructure, mapDecoration, zoom, asyncThrowable, cir);
        });
    }
    
    @Override
    public void handleChestExplorationMapResult(ItemStack stack, LootContext context, BlockPos structurePos, Holder<MapDecorationType> mapDecoration, byte zoom, Throwable throwable, Object cir) {
        org.bukkit.Bukkit.getScheduler().runTask(
            org.bukkit.Bukkit.getPluginManager().getPlugins()[0], () -> {
            try {
                if (throwable != null) {
                    setReturnValue(cir, stack);
                    return;
                }
                if (structurePos != null) {
                    ServerLevel level = context.getLevel();
                    ItemStack mapStack = net.minecraft.world.item.MapItem.create(level, structurePos.getX(), structurePos.getZ(), zoom, true, true);
                    net.minecraft.world.item.MapItem.renderBiomePreviewMap(level, mapStack);
                    net.minecraft.world.level.saveddata.maps.MapItemSavedData.addTargetDecoration(mapStack, structurePos, "+", mapDecoration);
                    setReturnValue(cir, mapStack);
                } else {
                    setReturnValue(cir, stack);
                }
            } catch (Exception e) {
                setReturnValue(cir, stack);
            }
        });
    }
    
    @Override
    public void handleVillagerTradeMapAsyncStart(MerchantOffer offer, Entity trader, TagKey<Structure> destination, Holder<MapDecorationType> destinationType, String displayName, int maxUses, int villagerXp, Object cir) {
        java.util.concurrent.CompletableFuture.supplyAsync(() -> {
            try {
                ServerLevel level = (ServerLevel) trader.level();
                BlockPos startPos = trader.blockPosition();
                return level.findNearestMapStructure(destination, startPos, getVillagerTradeMapsSearchRadius(), isVillagerTradeMapsSkipKnownStructures());
            } catch (Exception e) {
                logger.warning("[AkiAsync/Ignite] Error in async villager trade map: " + e.getMessage());
                return null;
            }
        }, generalExecutor).whenComplete((foundStructure, asyncThrowable) -> {
            handleVillagerTradeMapResult(offer, trader, foundStructure, destinationType, displayName, maxUses, villagerXp, asyncThrowable, cir);
        });
    }
    
    @Override
    public void handleVillagerTradeMapResult(MerchantOffer offer, Entity trader, BlockPos structurePos, Holder<MapDecorationType> destinationType, String displayName, int maxUses, int villagerXp, Throwable throwable, Object cir) {
        org.bukkit.Bukkit.getScheduler().runTask(
            org.bukkit.Bukkit.getPluginManager().getPlugins()[0], () -> {
            try {
                if (throwable != null || structurePos == null) {
                    setReturnValue(cir, null);
                    return;
                }
                ServerLevel level = (ServerLevel) trader.level();
                ItemStack mapStack = net.minecraft.world.item.MapItem.create(level, structurePos.getX(), structurePos.getZ(), (byte)2, true, true);
                net.minecraft.world.item.MapItem.renderBiomePreviewMap(level, mapStack);
                net.minecraft.world.level.saveddata.maps.MapItemSavedData.addTargetDecoration(mapStack, structurePos, "+", destinationType);
                mapStack.set(net.minecraft.core.component.DataComponents.ITEM_NAME, net.minecraft.network.chat.Component.translatable(displayName));
                
                MerchantOffer newOffer = new MerchantOffer(
                    new net.minecraft.world.item.trading.ItemCost(net.minecraft.world.item.Items.EMERALD, offer.getCostA().getCount()),
                    java.util.Optional.of(new net.minecraft.world.item.trading.ItemCost(net.minecraft.world.item.Items.COMPASS, 1)),
                    mapStack, 0, maxUses, villagerXp, 0.2F);
                setReturnValue(cir, newOffer);
            } catch (Exception e) {
                setReturnValue(cir, null);
            }
        });
    }
    
    private void setReturnValue(Object cir, Object value) {
        try {
            cir.getClass().getMethod("setReturnValue", Object.class).invoke(cir, value);
        } catch (Exception e) {
            logger.warning("[AkiAsync/Ignite] Failed to set return value: " + e.getMessage());
        }
    }

    // ==========================================
    // DataPack 优化
    // ==========================================
    @Override public boolean isDataPackOptimizationEnabled() { return config != null && config.isDataPackOptimizationEnabled(); }
    @Override public int getDataPackFileLoadThreads() { return config != null ? config.getDataPackFileLoadThreads() : 2; }
    @Override public int getDataPackZipProcessThreads() { return config != null ? config.getDataPackZipProcessThreads() : 2; }
    @Override public int getDataPackBatchSize() { return config != null ? config.getDataPackBatchSize() : 10; }
    @Override public long getDataPackCacheExpirationMinutes() { return config != null ? config.getDataPackCacheExpirationMinutes() : 30; }
    @Override public int getDataPackMaxFileCacheSize() { return config != null ? config.getDataPackMaxFileCacheSize() : 100; }
    @Override public int getDataPackMaxFileSystemCacheSize() { return config != null ? config.getDataPackMaxFileSystemCacheSize() : 50; }
    @Override public boolean isDataPackDebugEnabled() { return config != null && config.isDataPackDebugEnabled(); }

    // ==========================================
    // 调试日志
    // ==========================================
    @Override public boolean isDebugLoggingEnabled() { return config != null && config.isDebugLoggingEnabled(); }
    @Override public void debugLog(String message) { if (isDebugLoggingEnabled()) logger.info("[DEBUG] " + message); }
    @Override public void debugLog(String format, Object... args) { if (isDebugLoggingEnabled()) logger.info("[DEBUG] " + String.format(format, args)); }
    @Override public void errorLog(String message) { logger.severe(message); }
    @Override public void errorLog(String format, Object... args) { logger.severe(String.format(format, args)); }

    // ==========================================
    // 虚拟实体
    // ==========================================
    @Override
    public boolean isVirtualEntity(Entity entity) {
        try {
            return org.virgil.akiasync.util.VirtualEntityDetector.isVirtualEntity(entity);
        } catch (Exception e) {
            return false;
        }
    }

    // ==========================================
    // 种子保护（Ignite 模式下禁用）
    // ==========================================
    @Override public boolean isSeedProtectionEnabled() { return false; }
    @Override public boolean shouldReturnFakeSeed() { return false; }
    @Override public long getFakeSeedValue() { return 0; }
    @Override public boolean isQuantumSeedEnabled() { return false; }
    @Override public byte[] getQuantumServerKey() { return null; }
    @Override public long getEncryptedSeed(long originalSeed, int chunkX, int chunkZ, String dimension, String generationType, long gameTime) { return originalSeed; }
    @Override public boolean isSecureSeedEnabled() { return false; }
    @Override public long[] getSecureSeedWorldSeed() { return null; }
    @Override public void initializeSecureSeed(long originalSeed) { }
    @Override public int getSecureSeedBits() { return 0; }
    @Override public boolean isSeedEncryptionProtectStructures() { return false; }
    @Override public boolean isSeedEncryptionProtectOres() { return false; }
    @Override public boolean isSeedEncryptionProtectSlimes() { return false; }
    @Override public boolean isSeedEncryptionProtectBiomes() { return false; }

    // ==========================================
    // TNT 土地保护
    // ==========================================
    @Override public boolean isTNTLandProtectionEnabled() { return config != null && config.isTNTLandProtectionEnabled(); }
    @Override
    public boolean canTNTExplodeAt(ServerLevel level, BlockPos pos) {
        if (!isTNTLandProtectionEnabled()) {
            return true;
        }
        try {
            return org.virgil.akiasync.util.LandProtectionIntegration.canTNTExplode(level, pos);
        } catch (Exception e) {
            // 如果检查失败，默认允许爆炸以避免影响游戏
            return true;
        }
    }
    
    // ==========================================
    // BlockLocker 保护
    // ==========================================
    @Override public boolean isBlockLockerProtectionEnabled() { return config != null && config.isBlockLockerProtectionEnabled(); }
    @Override
    public boolean isBlockLockerProtected(ServerLevel level, BlockPos pos, BlockState state) {
        if (!isBlockLockerProtectionEnabled()) {
            return false;
        }
        try {
            return org.virgil.akiasync.util.BlockLockerIntegration.isProtected(level, pos, state);
        } catch (Exception e) {
            // 如果检查失败，默认不保护
            return false;
        }
    }

    // ==========================================
    // 配方缓存
    // ==========================================
    @Override public boolean isFurnaceRecipeCacheEnabled() { return config != null && config.isFurnaceRecipeCacheEnabled(); }
    @Override public int getFurnaceRecipeCacheSize() { return config != null ? config.getFurnaceRecipeCacheSize() : 100; }
    @Override public boolean isFurnaceCacheApplyToBlastFurnace() { return config != null && config.isFurnaceCacheApplyToBlastFurnace(); }
    @Override public boolean isFurnaceCacheApplyToSmoker() { return config != null && config.isFurnaceCacheApplyToSmoker(); }
    @Override public boolean isFurnaceFixBurnTimeBug() { return config != null && config.isFurnaceFixBurnTimeBug(); }
    @Override public boolean isCraftingRecipeCacheEnabled() { return config != null && config.isCraftingRecipeCacheEnabled(); }
    @Override public int getCraftingRecipeCacheSize() { return config != null ? config.getCraftingRecipeCacheSize() : 200; }
    @Override public boolean isCraftingOptimizeBatchCrafting() { return config != null && config.isCraftingOptimizeBatchCrafting(); }
    @Override public boolean isCraftingReduceNetworkTraffic() { return config != null && config.isCraftingReduceNetworkTraffic(); }

    // ==========================================
    // 矿车炼药锅
    // ==========================================
    @Override public boolean isMinecartCauldronDestructionEnabled() { return config != null && config.isMinecartCauldronDestructionEnabled(); }

    // ==========================================
    // 掉落方块并行
    // ==========================================
    @Override public boolean isFallingBlockParallelEnabled() { return config != null && config.isFallingBlockParallelEnabled(); }
    @Override public int getMinFallingBlocksForParallel() { return config != null ? config.getMinFallingBlocksForParallel() : 10; }
    @Override public int getFallingBlockBatchSize() { return config != null ? config.getFallingBlockBatchSize() : 50; }

    // ==========================================
    // 物品实体优化
    // ==========================================
    @Override public boolean isItemEntityMergeOptimizationEnabled() { return config != null && config.isItemEntityMergeOptimizationEnabled(); }
    @Override public int getItemEntityMergeInterval() { return config != null ? config.getItemEntityMergeInterval() : 40; }
    @Override public int getItemEntityMinNearbyItems() { return config != null ? config.getItemEntityMinNearbyItems() : 3; }
    @Override public double getItemEntityMergeRange() { return config != null ? config.getItemEntityMergeRange() : 2.0; }
    @Override public boolean isItemEntityAgeOptimizationEnabled() { return config != null && config.isItemEntityAgeOptimizationEnabled(); }
    @Override public int getItemEntityAgeInterval() { return config != null ? config.getItemEntityAgeInterval() : 100; }
    @Override public double getItemEntityPlayerDetectionRange() { return config != null ? config.getItemEntityPlayerDetectionRange() : 32.0; }

    // ==========================================
    // 快速移动区块加载
    // ==========================================
    @Override public boolean isFastMovementChunkLoadEnabled() { return config != null && config.isFastMovementChunkLoadEnabled(); }
    @Override public double getFastMovementSpeedThreshold() { return config != null ? config.getFastMovementSpeedThreshold() : 0.5; }
    @Override public int getFastMovementPreloadDistance() { return config != null ? config.getFastMovementPreloadDistance() : 8; }
    @Override public int getFastMovementMaxConcurrentLoads() { return config != null ? config.getFastMovementMaxConcurrentLoads() : 4; }
    @Override public int getFastMovementPredictionTicks() { return config != null ? config.getFastMovementPredictionTicks() : 40; }
    @Override public boolean isCenterOffsetEnabled() { return config != null && config.isCenterOffsetEnabled(); }
    @Override public double getMinOffsetSpeed() { return config != null ? config.getMinOffsetSpeed() : 3.0; }
    @Override public double getMaxOffsetSpeed() { return config != null ? config.getMaxOffsetSpeed() : 9.0; }
    @Override public double getMaxOffsetRatio() { return config != null ? config.getMaxOffsetRatio() : 0.75; }
    @Override 
    public void submitChunkLoad(ServerPlayer player, ChunkPos chunkPos, int priority, double speed) {
        if (chunkLoadScheduler == null || player == null || chunkPos == null) return;
        chunkLoadScheduler.submitChunkLoad(player, chunkPos, priority, speed);
    }

    // ==========================================
    // 其他优化
    // ==========================================
    @Override public boolean isSuffocationOptimizationEnabled() { return config != null && config.isSuffocationOptimizationEnabled(); }
    @Override public boolean isFastRayTraceEnabled() { return config != null && config.isFastRayTraceEnabled(); }
    @Override public boolean isMapRenderingOptimizationEnabled() { return config != null && config.isMapRenderingOptimizationEnabled(); }
    @Override public int getMapRenderingThreads() { return config != null ? config.getMapRenderingThreads() : 2; }

    // ==========================================
    // 主线程执行
    // ==========================================
    @Override
    public void runOnMainThread(Runnable task) {
        org.bukkit.Bukkit.getScheduler().runTask(
            org.bukkit.Bukkit.getPluginManager().getPlugins()[0], task);
    }

    // ==========================================
    // TPS/MSPT
    // ==========================================
    @Override public double getCurrentTPS() {
        try {
            // Paper API
            double[] tps = org.bukkit.Bukkit.getTPS();
            return tps.length > 0 ? Math.min(tps[0], 20.0) : 20.0;
        } catch (Exception e) {
            return 20.0;
        }
    }
    @Override public double getCurrentMSPT() {
        try {
            // Paper 1.19+ API
            return org.bukkit.Bukkit.getAverageTickTime();
        } catch (Exception e) {
            // 根据 TPS 估算
            double tps = getCurrentTPS();
            return tps > 0 ? 1000.0 / tps : 50.0;
        }
    }

    // ==========================================
    // 平滑调度器
    // ==========================================
    @Override public Object getBlockTickSmoothingScheduler() { return blockTickScheduler; }
    @Override public Object getEntityTickSmoothingScheduler() { return entityTickScheduler; }
    @Override public Object getBlockEntitySmoothingScheduler() { return blockEntityScheduler; }
    
    @Override
    public boolean submitSmoothTask(Object scheduler, Runnable task, int priority, String category) {
        if (scheduler instanceof TaskSmoothingScheduler) {
            return ((TaskSmoothingScheduler) scheduler).submit(task, category);
        }
        return false;
    }
    
    @Override
    public int submitSmoothTaskBatch(Object scheduler, java.util.List<Runnable> tasks, int priority, String category) {
        if (scheduler instanceof TaskSmoothingScheduler) {
            int count = 0;
            for (Runnable task : tasks) {
                if (((TaskSmoothingScheduler) scheduler).submit(task, category)) count++;
            }
            return count;
        }
        return 0;
    }
    
    @Override public void notifySmoothSchedulerTick(Object scheduler) {
        // TaskSmoothingScheduler 自动处理
    }
    
    @Override public void updateSmoothSchedulerMetrics(Object scheduler, double tps, double mspt) {
        // TaskSmoothingScheduler 自动处理
    }

    // ==========================================
    // Sakura 缓存
    // ==========================================
    @Override
    public void clearSakuraOptimizationCaches() {
        try {
            org.virgil.akiasync.mixin.async.explosion.density.SakuraBlockDensityCache.clearAllCaches();
            org.virgil.akiasync.mixin.async.redstone.RedstoneWireHelper.clearAllCaches();
            org.virgil.akiasync.mixin.async.explosion.density.AsyncDensityCacheManager.shutdown();
            org.virgil.akiasync.mixin.async.redstone.RedstoneNetworkCache.clearAllCaches();
            org.virgil.akiasync.mixin.async.redstone.AsyncRedstoneNetworkManager.shutdown();
            logger.info("[AkiAsync/Ignite] Sakura caches cleared");
        } catch (Exception e) {
            logger.warning("[AkiAsync/Ignite] Error clearing Sakura caches: " + e.getMessage());
        }
    }
    
    @Override
    public Map<String, Object> getSakuraCacheStatistics() {
        Map<String, Object> stats = new java.util.HashMap<>();
        try {
            // Density cache stats
            java.util.Map<String, String> densityStats = new java.util.HashMap<>();
            for (org.bukkit.World world : org.bukkit.Bukkit.getWorlds()) {
                try {
                    net.minecraft.server.level.ServerLevel serverLevel = 
                        ((org.bukkit.craftbukkit.CraftWorld) world).getHandle();
                    org.virgil.akiasync.mixin.async.explosion.density.SakuraBlockDensityCache cache = 
                        org.virgil.akiasync.mixin.async.explosion.density.SakuraBlockDensityCache.getOrCreate(serverLevel);
                    densityStats.put(world.getName(), cache.getStats());
                } catch (Exception e) {
                    densityStats.put(world.getName(), "Error: " + e.getMessage());
                }
            }
            stats.put("density_cache", densityStats);
            
            // Async density cache stats
            java.util.Map<String, String> asyncStats = new java.util.HashMap<>();
            for (org.bukkit.World world : org.bukkit.Bukkit.getWorlds()) {
                try {
                    net.minecraft.server.level.ServerLevel serverLevel = 
                        ((org.bukkit.craftbukkit.CraftWorld) world).getHandle();
                    org.virgil.akiasync.mixin.async.explosion.density.AsyncDensityCacheManager manager = 
                        org.virgil.akiasync.mixin.async.explosion.density.AsyncDensityCacheManager.getInstance(serverLevel);
                    asyncStats.put(world.getName(), manager.getStats());
                } catch (Exception e) {
                    asyncStats.put(world.getName(), "Error: " + e.getMessage());
                }
            }
            stats.put("async_density_cache", asyncStats);
            
            // PandaWire evaluators
            stats.put("pandawire_evaluators", 
                org.virgil.akiasync.mixin.async.redstone.RedstoneWireHelper.getEvaluatorCount());
            
            // Network cache stats
            java.util.Map<String, String> networkStats = new java.util.HashMap<>();
            for (org.bukkit.World world : org.bukkit.Bukkit.getWorlds()) {
                try {
                    net.minecraft.server.level.ServerLevel serverLevel = 
                        ((org.bukkit.craftbukkit.CraftWorld) world).getHandle();
                    org.virgil.akiasync.mixin.async.redstone.AsyncRedstoneNetworkManager manager = 
                        org.virgil.akiasync.mixin.async.redstone.AsyncRedstoneNetworkManager.getInstance(serverLevel);
                    networkStats.put(world.getName(), manager.getStats());
                } catch (Exception e) {
                    networkStats.put(world.getName(), "Error: " + e.getMessage());
                }
            }
            stats.put("network_cache", networkStats);
        } catch (Exception e) {
            logger.warning("[AkiAsync/Ignite] Error getting Sakura cache stats: " + e.getMessage());
        }
        return stats;
    }
    
    @Override
    public void performSakuraCacheCleanup() {
        try {
            for (org.bukkit.World world : org.bukkit.Bukkit.getWorlds()) {
                try {
                    net.minecraft.server.level.ServerLevel serverLevel = 
                        ((org.bukkit.craftbukkit.CraftWorld) world).getHandle();
                    
                    org.virgil.akiasync.mixin.async.explosion.density.SakuraBlockDensityCache cache = 
                        org.virgil.akiasync.mixin.async.explosion.density.SakuraBlockDensityCache.getOrCreate(serverLevel);
                    cache.expire(serverLevel.getGameTime());
                    
                    org.virgil.akiasync.mixin.async.explosion.density.AsyncDensityCacheManager manager = 
                        org.virgil.akiasync.mixin.async.explosion.density.AsyncDensityCacheManager.getInstance(serverLevel);
                    manager.expire(serverLevel.getGameTime());
                    
                    org.virgil.akiasync.mixin.async.redstone.AsyncRedstoneNetworkManager networkManager = 
                        org.virgil.akiasync.mixin.async.redstone.AsyncRedstoneNetworkManager.getInstance(serverLevel);
                    networkManager.expire(serverLevel.getGameTime());
                } catch (Exception e) {
                    // Ignore individual world errors
                }
            }
        } catch (Exception e) {
            logger.warning("[AkiAsync/Ignite] Error performing Sakura cache cleanup: " + e.getMessage());
        }
    }
    
    @Override
    public void clearEntityThrottleCache(int entityId) {
        try {
            org.virgil.akiasync.network.EntityDataThrottler.clearEntity(entityId);
            org.virgil.akiasync.network.EntityPacketThrottler.clearEntity(entityId);
        } catch (Exception e) {
            // Ignore
        }
    }

    // ==========================================
    // AI 空间索引
    // ==========================================
    @Override public boolean isAiSpatialIndexEnabled() { return config != null && config.isAiSpatialIndexEnabled(); }
    @Override public int getAiSpatialIndexGridSize() { return config != null ? config.getAiSpatialIndexGridSize() : 16; }
    @Override public boolean isAiSpatialIndexAutoUpdate() { return config != null && config.isAiSpatialIndexAutoUpdate(); }
    @Override public boolean isAiSpatialIndexPlayerIndexEnabled() { return config != null && config.isAiSpatialIndexPlayerIndexEnabled(); }
    @Override public boolean isAiSpatialIndexPoiIndexEnabled() { return config != null && config.isAiSpatialIndexPoiIndexEnabled(); }
    @Override public boolean isAiSpatialIndexStatisticsEnabled() { return config != null && config.isAiSpatialIndexStatisticsEnabled(); }
    @Override public int getAiSpatialIndexLogIntervalSeconds() { return config != null ? config.getAiSpatialIndexLogIntervalSeconds() : 60; }

    // ==========================================
    // 额外生物优化
    // ==========================================
    @Override public boolean isWanderingTraderOptimizationEnabled() { return config != null && config.isWanderingTraderOptimizationEnabled(); }
    @Override public boolean isWardenOptimizationEnabled() { return config != null && config.isWardenOptimizationEnabled(); }
    @Override public boolean isHoglinOptimizationEnabled() { return config != null && config.isHoglinOptimizationEnabled(); }
    @Override public boolean isAllayOptimizationEnabled() { return config != null && config.isAllayOptimizationEnabled(); }

    // ==========================================
    // Brain 优化
    // ==========================================
    @Override public boolean isBrainMemoryOptimizationEnabled() { return config != null && config.isBrainMemoryOptimizationEnabled(); }
    @Override public boolean isPoiSnapshotEnabled() { return config != null && config.isPoiSnapshotEnabled(); }

    // ==========================================
    // 增强路径查找
    // ==========================================
    @Override public boolean isAsyncPathfindingSyncFallbackEnabled() { return config != null && config.isAsyncPathfindingSyncFallbackEnabled(); }
    @Override public boolean isEnhancedPathfindingEnabled() { return config != null && config.isEnhancedPathfindingEnabled(); }
    @Override public int getEnhancedPathfindingMaxConcurrentRequests() { return config != null ? config.getEnhancedPathfindingMaxConcurrentRequests() : 100; }
    @Override public int getEnhancedPathfindingMaxRequestsPerTick() { return config != null ? config.getEnhancedPathfindingMaxRequestsPerTick() : 20; }
    @Override public int getEnhancedPathfindingHighPriorityDistance() { return config != null ? config.getEnhancedPathfindingHighPriorityDistance() : 16; }
    @Override public int getEnhancedPathfindingMediumPriorityDistance() { return config != null ? config.getEnhancedPathfindingMediumPriorityDistance() : 32; }
    @Override public boolean isPathPrewarmEnabled() { return config != null && config.isPathPrewarmEnabled(); }
    @Override public int getPathPrewarmRadius() { return config != null ? config.getPathPrewarmRadius() : 48; }
    @Override public int getPathPrewarmMaxMobsPerBatch() { return config != null ? config.getPathPrewarmMaxMobsPerBatch() : 10; }
    @Override public int getPathPrewarmMaxPoisPerMob() { return config != null ? config.getPathPrewarmMaxPoisPerMob() : 3; }

    // ==========================================
    // 碰撞 Executor
    // ==========================================
    @Override public ExecutorService getCollisionExecutor() { return generalExecutor; }

    // ==========================================
    // 修复
    // ==========================================
    @Override public boolean isEndIslandDensityFixEnabled() { return config != null && config.isEndIslandDensityFixEnabled(); }
    @Override public boolean isPortalSuffocationCheckDisabled() { return config != null && config.isPortalSuffocationCheckDisabled(); }
    @Override public boolean isShulkerBulletSelfHitFixEnabled() { return config != null && config.isShulkerBulletSelfHitFixEnabled(); }

    // ==========================================
    // 流体优化
    // ==========================================
    @Override public boolean isFluidOptimizationEnabled() { return config != null && config.isFluidOptimizationEnabled(); }
    @Override public boolean isFluidDebugEnabled() { return config != null && config.isFluidDebugEnabled(); }
    @Override public boolean isFluidTickThrottleEnabled() { return config != null && config.isFluidTickThrottleEnabled(); }
    @Override public int getStaticFluidInterval() { return config != null ? config.getStaticFluidInterval() : 5; }
    @Override public int getFlowingFluidInterval() { return config != null ? config.getFlowingFluidInterval() : 2; }
    @Override public boolean isFluidTickCompensationEnabled() { return config != null && config.isFluidTickCompensationEnabled(); }
    @Override public boolean isFluidCompensationEnabledForWater() { return config != null && config.isFluidCompensationEnabledForWater(); }
    @Override public boolean isFluidCompensationEnabledForLava() { return config != null && config.isFluidCompensationEnabledForLava(); }
    @Override public double getFluidCompensationTPSThreshold() { return config != null ? config.getFluidCompensationTPSThreshold() : 18.0; }

    // ==========================================
    // 智能延迟补偿
    // ==========================================
    @Override public boolean isSmartLagCompensationEnabled() { return config != null && config.isSmartLagCompensationEnabled(); }
    @Override public double getSmartLagTPSThreshold() { return config != null ? config.getSmartLagTPSThreshold() : 18.0; }
    @Override public boolean isSmartLagFluidCompensationEnabled() { return config != null && config.isSmartLagFluidCompensationEnabled(); }
    @Override public boolean isSmartLagFluidWaterEnabled() { return config != null && config.isSmartLagFluidWaterEnabled(); }
    @Override public boolean isSmartLagFluidLavaEnabled() { return config != null && config.isSmartLagFluidLavaEnabled(); }
    @Override public boolean isSmartLagItemPickupDelayEnabled() { return config != null && config.isSmartLagItemPickupDelayEnabled(); }
    @Override public boolean isSmartLagPotionEffectsEnabled() { return config != null && config.isSmartLagPotionEffectsEnabled(); }
    @Override public boolean isSmartLagTimeAccelerationEnabled() { return config != null && config.isSmartLagTimeAccelerationEnabled(); }
    @Override public boolean isSmartLagDebugEnabled() { return config != null && config.isSmartLagDebugEnabled(); }
    @Override public boolean isSmartLagLogMissedTicks() { return config != null && config.isSmartLagLogMissedTicks(); }
    @Override public boolean isSmartLagLogCompensation() { return config != null && config.isSmartLagLogCompensation(); }

    // ==========================================
    // 经验球优化
    // ==========================================
    @Override public boolean isExperienceOrbInactiveTickEnabled() { return config != null && config.isExperienceOrbInactiveTickEnabled(); }
    @Override public double getExperienceOrbInactiveRange() { return config != null ? config.getExperienceOrbInactiveRange() : 32.0; }
    @Override public int getExperienceOrbInactiveMergeInterval() { return config != null ? config.getExperienceOrbInactiveMergeInterval() : 100; }

    // ==========================================
    // 区块保护检查
    // ==========================================
    @Override
    public Boolean checkChunkProtection(ServerLevel level, int chunkX, int chunkZ) {
        // 简化实现：直接检查 canTNTExplodeAt
        if (!isTNTLandProtectionEnabled()) {
            return null;
        }
        try {
            BlockPos pos = new BlockPos(chunkX << 4, 64, chunkZ << 4);
            return !canTNTExplodeAt(level, pos);
        } catch (Exception e) {
            return null;
        }
    }

    // ==========================================
    // 生物生成间隔
    // ==========================================
    @Override public int getMobSpawnInterval() { return config != null ? config.getMobSpawnInterval() : 1; }

    // ==========================================
    // 物品实体额外配置
    // ==========================================
    @Override public boolean isItemEntityCancelVanillaMerge() { return config != null && config.isItemEntityCancelVanillaMerge(); }
    @Override public boolean isItemEntityInactiveTickEnabled() { return config != null && config.isItemEntityInactiveTickEnabled(); }
    @Override public double getItemEntityInactiveRange() { return config != null ? config.getItemEntityInactiveRange() : 32.0; }
    @Override public int getItemEntityInactiveMergeInterval() { return config != null ? config.getItemEntityInactiveMergeInterval() : 100; }

    // ==========================================
    // Execute 命令优化
    // ==========================================
    @Override public boolean isExecuteCommandInactiveSkipEnabled() { return config != null && config.isExecuteCommandInactiveSkipEnabled(); }
    @Override public int getExecuteCommandSkipLevel() { return config != null ? config.getExecuteCommandSkipLevel() : 2; }
    @Override public double getExecuteCommandSimulationDistanceMultiplier() { return config != null ? config.getExecuteCommandSimulationDistanceMultiplier() : 1.0; }
    @Override public long getExecuteCommandCacheDurationMs() { return config != null ? config.getExecuteCommandCacheDurationMs() : 100; }
    @Override public Set<String> getExecuteCommandWhitelistTypes() { return config != null ? config.getExecuteCommandWhitelistTypes() : Collections.emptySet(); }
    @Override public boolean isExecuteCommandDebugEnabled() { return config != null && config.isExecuteCommandDebugEnabled(); }
    @Override public boolean isCommandDeduplicationEnabled() { return config != null && config.isCommandDeduplicationEnabled(); }
    @Override public boolean isCommandDeduplicationDebugEnabled() { return config != null && config.isCommandDeduplicationDebugEnabled(); }

    // ==========================================
    // 碰撞优化
    // ==========================================
    @Override public boolean isCollisionOptimizationEnabled() { return config != null && config.isCollisionOptimizationEnabled(); }
    @Override public boolean isCollisionAggressiveMode() { return config != null && config.isCollisionAggressiveMode(); }
    @Override public Set<String> getCollisionExcludedEntities() { return config != null ? config.getCollisionExcludedEntities() : Collections.emptySet(); }
    @Override public boolean isCollisionCacheEnabled() { return config != null && config.isCollisionCacheEnabled(); }
    @Override public int getCollisionCacheLifetimeMs() { return config != null ? config.getCollisionCacheLifetimeMs() : 50; }
    @Override public double getCollisionCacheMovementThreshold() { return config != null ? config.getCollisionCacheMovementThreshold() : 0.01; }
    @Override public boolean isCollisionSpatialPartitionEnabled() { return config != null && config.isCollisionSpatialPartitionEnabled(); }
    @Override public int getCollisionSpatialGridSize() { return config != null ? config.getCollisionSpatialGridSize() : 4; }
    @Override public int getCollisionSpatialDensityThreshold() { return config != null ? config.getCollisionSpatialDensityThreshold() : 50; }
    @Override public int getCollisionSpatialUpdateIntervalMs() { return config != null ? config.getCollisionSpatialUpdateIntervalMs() : 100; }
    @Override public double getCollisionSkipMinMovement() { return config != null ? config.getCollisionSkipMinMovement() : 0.001; }
    @Override public int getCollisionSkipCheckIntervalMs() { return config != null ? config.getCollisionSkipCheckIntervalMs() : 50; }

    // ==========================================
    // 推挤优化
    // ==========================================
    @Override public boolean isPushOptimizationEnabled() { return config != null && config.isPushOptimizationEnabled(); }
    @Override public double getPushMaxPushPerTick() { return config != null ? config.getPushMaxPushPerTick() : 0.5; }
    @Override public double getPushDampingFactor() { return config != null ? config.getPushDampingFactor() : 0.8; }
    @Override public int getPushHighDensityThreshold() { return config != null ? config.getPushHighDensityThreshold() : 10; }
    @Override public double getPushHighDensityMultiplier() { return config != null ? config.getPushHighDensityMultiplier() : 0.5; }

    // ==========================================
    // 高级碰撞优化
    // ==========================================
    @Override public boolean isAdvancedCollisionOptimizationEnabled() { return config != null && config.isAdvancedCollisionOptimizationEnabled(); }
    @Override public int getCollisionThreshold() { return config != null ? config.getCollisionThreshold() : 10; }
    @Override public float getSuffocationDamage() { return config != null ? config.getSuffocationDamage() : 1.0f; }
    @Override public int getMaxPushIterations() { return config != null ? config.getMaxPushIterations() : 3; }

    // ==========================================
    // 光照优先级调度
    // ==========================================
    @Override public boolean isLightingPrioritySchedulingEnabled() { return config != null && config.isLightingPrioritySchedulingEnabled(); }
    @Override public int getLightingHighPriorityRadius() { return config != null ? config.getLightingHighPriorityRadius() : 2; }
    @Override public int getLightingMediumPriorityRadius() { return config != null ? config.getLightingMediumPriorityRadius() : 4; }
    @Override public int getLightingLowPriorityRadius() { return config != null ? config.getLightingLowPriorityRadius() : 8; }
    @Override public long getLightingMaxLowPriorityDelay() { return config != null ? config.getLightingMaxLowPriorityDelay() : 1000; }

    // ==========================================
    // 光照去抖动
    // ==========================================
    @Override public boolean isLightingDebouncingEnabled() { return config != null && config.isLightingDebouncingEnabled(); }
    @Override public long getLightingDebounceDelay() { return config != null ? config.getLightingDebounceDelay() : 50; }
    @Override public int getLightingMaxUpdatesPerSecond() { return config != null ? config.getLightingMaxUpdatesPerSecond() : 1000; }
    @Override public long getLightingResetOnStableMs() { return config != null ? config.getLightingResetOnStableMs() : 500; }

    // ==========================================
    // 光照合并
    // ==========================================
    @Override public boolean isLightingMergingEnabled() { return config != null && config.isLightingMergingEnabled(); }
    @Override public int getLightingMergeRadius() { return config != null ? config.getLightingMergeRadius() : 2; }
    @Override public long getLightingMergeDelay() { return config != null ? config.getLightingMergeDelay() : 20; }
    @Override public int getLightingMaxMergedUpdates() { return config != null ? config.getLightingMaxMergedUpdates() : 100; }

    // ==========================================
    // 光照区块边界
    // ==========================================
    @Override public boolean isLightingChunkBorderEnabled() { return config != null && config.isLightingChunkBorderEnabled(); }
    @Override public boolean isLightingBatchBorderUpdates() { return config != null && config.isLightingBatchBorderUpdates(); }
    @Override public long getLightingBorderUpdateDelay() { return config != null ? config.getLightingBorderUpdateDelay() : 100; }
    @Override public int getLightingCrossChunkBatchSize() { return config != null ? config.getLightingCrossChunkBatchSize() : 16; }

    // ==========================================
    // 自适应光照
    // ==========================================
    @Override public boolean isLightingAdaptiveEnabled() { return config != null && config.isLightingAdaptiveEnabled(); }
    @Override public int getLightingMonitorInterval() { return config != null ? config.getLightingMonitorInterval() : 100; }
    @Override public boolean isLightingAutoAdjustThreads() { return config != null && config.isLightingAutoAdjustThreads(); }
    @Override public boolean isLightingAutoAdjustBatchSize() { return config != null && config.isLightingAutoAdjustBatchSize(); }
    @Override public int getLightingTargetQueueSize() { return config != null ? config.getLightingTargetQueueSize() : 100; }
    @Override public int getLightingTargetLatency() { return config != null ? config.getLightingTargetLatency() : 50; }

    // ==========================================
    // 光照区块卸载
    // ==========================================
    @Override public boolean isLightingChunkUnloadEnabled() { return config != null && config.isLightingChunkUnloadEnabled(); }
    @Override public boolean isLightingAsyncCleanup() { return config != null && config.isLightingAsyncCleanup(); }
    @Override public int getLightingCleanupBatchSize() { return config != null ? config.getLightingCleanupBatchSize() : 16; }
    @Override public long getLightingCleanupDelay() { return config != null ? config.getLightingCleanupDelay() : 1000; }

    // ==========================================
    // 光照线程池配置
    // ==========================================
    @Override public String getLightingThreadPoolMode() { return config != null ? config.getLightingThreadPoolMode() : "fixed"; }
    @Override public String getLightingThreadPoolCalculation() { return config != null ? config.getLightingThreadPoolCalculation() : "cores"; }
    @Override public int getLightingMinThreads() { return config != null ? config.getLightingMinThreads() : 1; }
    @Override public int getLightingMaxThreads() { return config != null ? config.getLightingMaxThreads() : 4; }
    @Override public int getLightingBatchThresholdMax() { return config != null ? config.getLightingBatchThresholdMax() : 256; }
    @Override public boolean isLightingAggressiveBatching() { return config != null && config.isLightingAggressiveBatching(); }

    // ==========================================
    // 噪声和拼图优化
    // ==========================================
    @Override public boolean isNoiseOptimizationEnabled() { return config != null && config.isNoiseOptimizationEnabled(); }
    @Override public boolean isJigsawOptimizationEnabled() { return config != null && config.isJigsawOptimizationEnabled(); }
    
    @Override
    public void initializeJigsawOctree(net.minecraft.world.phys.AABB bounds) {
        if (config != null && isJigsawOptimizationEnabled()) {
            try {
                org.virgil.akiasync.util.worldgen.OctreeHolder.set(
                    new org.virgil.akiasync.util.worldgen.BoxOctree(bounds)
                );
            } catch (Exception e) {
                logger.warning("[AkiAsync/Ignite] Failed to initialize Jigsaw Octree: " + e.getMessage());
            }
        }
    }
    
    @Override
    public boolean hasJigsawOctree() {
        try {
            return org.virgil.akiasync.util.worldgen.OctreeHolder.isSet();
        } catch (Exception e) {
            return false;
        }
    }
    
    @Override
    public void insertIntoJigsawOctree(net.minecraft.world.phys.AABB box) {
        try {
            org.virgil.akiasync.util.worldgen.BoxOctree octree = 
                org.virgil.akiasync.util.worldgen.OctreeHolder.get();
            if (octree != null) {
                octree.insert(box);
            }
        } catch (Exception e) {
            // Ignore
        }
    }
    
    @Override
    public boolean jigsawOctreeIntersects(net.minecraft.world.phys.AABB box) {
        try {
            org.virgil.akiasync.util.worldgen.BoxOctree octree = 
                org.virgil.akiasync.util.worldgen.OctreeHolder.get();
            return octree != null && octree.intersects(box);
        } catch (Exception e) {
            return false;
        }
    }
    
    @Override
    public void clearJigsawOctree() {
        try {
            org.virgil.akiasync.util.worldgen.OctreeHolder.clear();
        } catch (Exception e) {
            // Ignore
        }
    }
    
    @Override
    public String getJigsawOctreeStats() {
        try {
            org.virgil.akiasync.util.worldgen.BoxOctree octree = 
                org.virgil.akiasync.util.worldgen.OctreeHolder.get();
            if (octree != null) {
                return octree.getStats().toString();
            }
        } catch (Exception e) {
            // Ignore
        }
        return "Octree not initialized";
    }

    // ==========================================
    // 实体数据包节流
    // ==========================================
    @Override public boolean isEntityPacketThrottleEnabled() { return config != null && config.isEntityPacketThrottleEnabled(); }
    
    @Override
    public boolean shouldSendEntityUpdate(ServerPlayer player, Entity entity) {
        if (!isEntityPacketThrottleEnabled() || player == null || entity == null) {
            return true;
        }
        try {
            if (!org.virgil.akiasync.network.EntityPacketThrottler.isInitialized()) {
                return true;
            }
            return org.virgil.akiasync.network.EntityPacketThrottler.shouldSendUpdateSimple(player, entity);
        } catch (Exception e) {
            return true;
        }
    }
    
    @Override
    public void tickEntityPacketThrottler() {
        if (!isEntityPacketThrottleEnabled()) return;
        try {
            if (org.virgil.akiasync.network.EntityPacketThrottler.isInitialized()) {
                org.virgil.akiasync.network.EntityPacketThrottler.tick();
            }
        } catch (Exception e) {
            // Ignore
        }
    }
    
    @Override public boolean isEntityDataThrottleEnabled() { return config != null && config.isEntityDataThrottleEnabled(); }
    
    @Override
    public boolean shouldSendMetadata(ServerPlayer player, Entity entity, int metadataHash) {
        if (!isEntityDataThrottleEnabled() || player == null || entity == null) {
            return true;
        }
        try {
            return org.virgil.akiasync.network.EntityDataThrottler.shouldSendMetadata(player, entity, metadataHash);
        } catch (Exception e) {
            return true;
        }
    }
    
    @Override
    public boolean shouldSendNBT(ServerPlayer player, Entity entity, boolean forceUpdate) {
        if (!isEntityDataThrottleEnabled() || player == null || entity == null) {
            return true;
        }
        try {
            return org.virgil.akiasync.network.EntityDataThrottler.shouldSendNBT(player, entity, forceUpdate);
        } catch (Exception e) {
            return true;
        }
    }
    
    @Override
    public void tickEntityDataThrottler() {
        if (!isEntityDataThrottleEnabled()) return;
        try {
            org.virgil.akiasync.network.EntityDataThrottler.tick();
        } catch (Exception e) {
            // Ignore
        }
    }

    // ==========================================
    // 区块可见性过滤
    // ==========================================
    @Override public boolean isChunkVisibilityFilterEnabled() { return config != null && config.isChunkVisibilityFilterEnabled(); }
    
    @Override
    public boolean isChunkVisible(ServerPlayer player, ChunkPos chunkPos, ServerLevel level) {
        if (!isChunkVisibilityFilterEnabled() || player == null || chunkPos == null || level == null) {
            return true;
        }
        try {
            return org.virgil.akiasync.network.ChunkVisibilityFilter.isChunkVisible(player, chunkPos, level);
        } catch (Exception e) {
            return true;
        }
    }
    
    @Override
    public void tickChunkVisibilityFilter() {
        if (!isChunkVisibilityFilterEnabled()) return;
        try {
            org.virgil.akiasync.network.ChunkVisibilityFilter.tick();
        } catch (Exception e) {
            // Ignore
        }
    }

    // ==========================================
    // 配置更新（支持热重载）
    // Configuration update (hot reload support)
    // ==========================================
    
    /**
     * 更新配置引用
     * Update configuration reference for hot reload
     * 
     * @param newConfig 新的配置管理器
     */
    public void updateConfiguration(IgniteConfigManager newConfig) {
        if (newConfig == null) {
            logger.warning("[AkiAsync/Ignite] Attempted to update with null config, ignoring");
            return;
        }
        
        // 更新配置引用
        this.config = newConfig;
        logger.info("[AkiAsync/Ignite] Config reference updated");
        
        // 更新调试日志状态
        try {
            org.virgil.akiasync.util.DebugLogger.updateDebugState(newConfig.isDebugLoggingEnabled());
        } catch (Exception e) {
            // DebugLogger 可能不存在
        }
        
        // 更新 VirtualEntityDetector 的调试状态
        try {
            org.virgil.akiasync.util.VirtualEntityDetector.setLogger(logger, newConfig.isDebugLoggingEnabled());
        } catch (Exception e) {
            // 忽略
        }
        
        // 重新初始化平滑调度器（如果配置发生变化）
        try {
            reinitializeSmoothingSchedulers(newConfig);
        } catch (Exception e) {
            logger.warning("[AkiAsync/Ignite] Failed to reinitialize smoothing schedulers: " + e.getMessage());
        }
        
        logger.info("[AkiAsync/Ignite] Bridge configuration updated successfully");
    }
    
    /**
     * 重新初始化平滑调度器
     */
    private void reinitializeSmoothingSchedulers(IgniteConfigManager newConfig) {
        if (isFolia) {
            return; // Folia 环境下不使用
        }
        
        // 重新创建调度器（如果启用状态发生变化）
        if (newConfig.isChunkTickAsyncEnabled() && blockTickScheduler == null && generalExecutor != null) {
            int batchSize = newConfig.getChunkTickAsyncBatchSize();
            blockTickScheduler = new TaskSmoothingScheduler(generalExecutor, batchSize * 10, batchSize * 2, 3);
        }
        
        if (newConfig.isEntityTickParallel() && entityTickScheduler == null && generalExecutor != null) {
            int batchSize = newConfig.getEntityTickBatchSize();
            entityTickScheduler = new TaskSmoothingScheduler(generalExecutor, batchSize * 20, batchSize * 3, 2);
        }
        
        if (newConfig.isBlockEntityParallelTickEnabled() && blockEntityScheduler == null && generalExecutor != null) {
            int batchSize = newConfig.getBlockEntityParallelBatchSize();
            blockEntityScheduler = new TaskSmoothingScheduler(generalExecutor, batchSize * 15, batchSize * 2, 3);
        }
    }
    
    // ==========================================
    // 关闭方法（用于服务器停止时清理资源）
    // Shutdown method (for cleanup when server stops)
    // ==========================================
    
    /**
     * 关闭所有资源
     * Shutdown all resources
     */
    public void shutdown() {
        logger.info("[AkiAsync/Ignite] Shutting down IgniteAkiAsyncBridge...");
        
        // 关闭网络优化组件
        // 清理平滑调度器（TaskSmoothingScheduler 使用 daemon 线程，只需清空队列）
        if (blockTickScheduler != null) {
            try {
                blockTickScheduler.clearQueue();
            } catch (Exception e) {
                // 忽略
            }
            blockTickScheduler = null;
        }
        
        if (entityTickScheduler != null) {
            try {
                entityTickScheduler.clearQueue();
            } catch (Exception e) {
                // 忽略
            }
            entityTickScheduler = null;
        }
        
        if (blockEntityScheduler != null) {
            try {
                blockEntityScheduler.clearQueue();
            } catch (Exception e) {
                // 忽略
            }
            blockEntityScheduler = null;
        }
        
        logger.info("[AkiAsync/Ignite] IgniteAkiAsyncBridge shutdown complete");
    }
}
