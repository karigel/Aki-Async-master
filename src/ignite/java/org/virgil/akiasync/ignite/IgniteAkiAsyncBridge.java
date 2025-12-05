package org.virgil.akiasync.ignite;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.logging.Logger;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.ResourceOrTagKeyArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.network.protocol.Packet;
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
import org.virgil.akiasync.network.PlayerTeleportTracker;
import org.virgil.akiasync.network.TeleportPacketDetector;

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
    
    // 传送追踪器（由 IgnitePluginAdapter 设置）
    private PlayerTeleportTracker teleportTracker;
    
    // 网络优化组件
    private org.virgil.akiasync.network.NetworkCongestionDetector congestionDetector;
    private org.virgil.akiasync.network.ChunkSendRateController chunkRateController;
    private org.virgil.akiasync.network.PriorityPacketScheduler packetScheduler;
    private org.virgil.akiasync.network.PacketSendWorker packetSendWorker;
    
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
        
        // 初始化网络优化组件
        initializeNetworkComponents();
    }
    
    /**
     * 初始化网络优化组件
     */
    private void initializeNetworkComponents() {
        try {
            if (config == null || !config.isNetworkOptimizationEnabled()) {
                logger.info("[AkiAsync/Ignite] Network optimization disabled");
                return;
            }
            
            // 创建拥塞检测器
            congestionDetector = new org.virgil.akiasync.network.NetworkCongestionDetector();
            
            // 创建区块发送速率控制器
            chunkRateController = new org.virgil.akiasync.network.ChunkSendRateController(congestionDetector);
            
            // 创建数据包优先级调度器
            packetScheduler = new org.virgil.akiasync.network.PriorityPacketScheduler(config);
            
            // 创建数据包发送工作线程
            if (config.isPacketPriorityEnabled()) {
                packetSendWorker = new org.virgil.akiasync.network.PacketSendWorker(packetScheduler, config);
                packetSendWorker.start();
                logger.info("[AkiAsync/Ignite] PacketSendWorker started");
            }
            
            logger.info("[AkiAsync/Ignite] Network optimization components initialized:");
            logger.info("  - NetworkCongestionDetector: Enabled");
            logger.info("  - ChunkSendRateController: Enabled");
            logger.info("  - PriorityPacketScheduler: Enabled");
            logger.info("  - PacketSendWorker: " + (packetSendWorker != null ? "Enabled" : "Disabled"));
            
        } catch (Exception e) {
            logger.warning("[AkiAsync/Ignite] Failed to initialize network components: " + e.getMessage());
        }
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
    
    public void setTeleportTracker(PlayerTeleportTracker tracker) {
        this.teleportTracker = tracker;
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
    @Override public boolean isPushOptimizationEnabled() { return true; }
    @Override public boolean isEntityLookupCacheEnabled() { return true; }
    @Override public int getEntityLookupCacheDurationMs() { return 50; }
    @Override public boolean isCollisionOptimizationEnabled() { return true; }

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

    // 结构定位回调（简单实现）
    @Override public void handleLocateCommandResult(CommandSourceStack sourceStack, BlockPos structurePos, Throwable throwable) { }
    @Override public void handleLocateCommandAsyncStart(CommandSourceStack sourceStack, ResourceOrTagKeyArgument.Result<Structure> structureResult, HolderSet<Structure> holderSet) { }
    @Override public void handleDolphinTreasureResult(Dolphin dolphin, BlockPos treasurePos, Throwable throwable) { }
    @Override public void handleChestExplorationMapAsyncStart(ItemStack stack, LootContext context, TagKey<Structure> destination, Holder<MapDecorationType> mapDecoration, byte zoom, int searchRadius, boolean skipKnownStructures, Object cir) { }
    @Override public void handleChestExplorationMapResult(ItemStack stack, LootContext context, BlockPos structurePos, Holder<MapDecorationType> mapDecoration, byte zoom, Throwable throwable, Object cir) { }
    @Override public void handleVillagerTradeMapAsyncStart(MerchantOffer offer, Entity trader, TagKey<Structure> destination, Holder<MapDecorationType> destinationType, String displayName, int maxUses, int villagerXp, Object cir) { }
    @Override public void handleVillagerTradeMapResult(MerchantOffer offer, Entity trader, BlockPos structurePos, Holder<MapDecorationType> destinationType, String displayName, int maxUses, int villagerXp, Throwable throwable, Object cir) { }

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
    @Override public boolean isItemEntityParallelEnabled() { return config != null && config.isItemEntityParallelEnabled(); }
    @Override public int getMinItemEntitiesForParallel() { return config != null ? config.getMinItemEntitiesForParallel() : 20; }
    @Override public int getItemEntityBatchSize() { return config != null ? config.getItemEntityBatchSize() : 100; }
    @Override public boolean isItemEntityMergeOptimizationEnabled() { return config != null && config.isItemEntityMergeOptimizationEnabled(); }
    @Override public int getItemEntityMergeInterval() { return config != null ? config.getItemEntityMergeInterval() : 40; }
    @Override public int getItemEntityMinNearbyItems() { return config != null ? config.getItemEntityMinNearbyItems() : 3; }
    @Override public double getItemEntityMergeRange() { return config != null ? config.getItemEntityMergeRange() : 2.0; }
    @Override public boolean isItemEntityAgeOptimizationEnabled() { return config != null && config.isItemEntityAgeOptimizationEnabled(); }
    @Override public int getItemEntityAgeInterval() { return config != null ? config.getItemEntityAgeInterval() : 100; }
    @Override public double getItemEntityPlayerDetectionRange() { return config != null ? config.getItemEntityPlayerDetectionRange() : 32.0; }

    // ==========================================
    // 网络优化
    // ==========================================
    @Override public boolean isNetworkOptimizationEnabled() { return config != null && config.isNetworkOptimizationEnabled(); }
    @Override public boolean isPacketPriorityEnabled() { return config != null && config.isPacketPriorityEnabled(); }
    @Override public boolean isChunkRateControlEnabled() { return config != null && config.isChunkRateControlEnabled(); }
    @Override public boolean isChunkSendOptimizationEnabled() { return config != null && config.isChunkRateControlEnabled(); }
    @Override public boolean isCongestionDetectionEnabled() { return config != null && config.isCongestionDetectionEnabled(); }
    @Override public int getHighPingThreshold() { return config != null ? config.getHighPingThreshold() : 150; }
    @Override public int getCriticalPingThreshold() { return config != null ? config.getCriticalPingThreshold() : 300; }
    @Override public long getHighBandwidthThreshold() { return config != null ? config.getHighBandwidthThreshold() : 1000000; }
    @Override public int getBaseChunkSendRate() { return config != null ? config.getBaseChunkSendRate() : 10; }
    @Override public int getMaxChunkSendRate() { return config != null ? config.getMaxChunkSendRate() : 20; }
    @Override public int getMinChunkSendRate() { return config != null ? config.getMinChunkSendRate() : 5; }
    @Override public int getPacketSendRateBase() { return config != null ? config.getPacketSendRateBase() : 100; }
    @Override public int getPacketSendRateMedium() { return config != null ? config.getPacketSendRateMedium() : 75; }
    @Override public int getPacketSendRateHeavy() { return config != null ? config.getPacketSendRateHeavy() : 50; }
    @Override public int getPacketSendRateExtreme() { return config != null ? config.getPacketSendRateExtreme() : 25; }
    @Override public int getQueueLimitMaxTotal() { return config != null ? config.getQueueLimitMaxTotal() : 1000; }
    @Override public int getQueueLimitMaxCritical() { return config != null ? config.getQueueLimitMaxCritical() : 100; }
    @Override public int getQueueLimitMaxHigh() { return config != null ? config.getQueueLimitMaxHigh() : 300; }
    @Override public int getQueueLimitMaxNormal() { return config != null ? config.getQueueLimitMaxNormal() : 600; }
    @Override public int getAccelerationThresholdMedium() { return config != null ? config.getAccelerationThresholdMedium() : 50; }
    @Override public int getAccelerationThresholdHeavy() { return config != null ? config.getAccelerationThresholdHeavy() : 75; }
    @Override public int getAccelerationThresholdExtreme() { return config != null ? config.getAccelerationThresholdExtreme() : 90; }
    @Override public boolean isCleanupEnabled() { return config != null && config.isCleanupEnabled(); }
    @Override public int getCleanupStaleThreshold() { return config != null ? config.getCleanupStaleThreshold() : 30; }
    @Override public int getCleanupCriticalCleanup() { return config != null ? config.getCleanupCriticalCleanup() : 50; }
    @Override public int getCleanupNormalCleanup() { return config != null ? config.getCleanupNormalCleanup() : 10; }

    // 网络功能（使用网络优化组件）
    @Override 
    public int getPlayerCongestionLevel(UUID playerId) {
        if (congestionDetector == null || playerId == null) return 0;
        org.virgil.akiasync.network.NetworkCongestionDetector.CongestionLevel level = 
            congestionDetector.getCongestionLevel(playerId);
        return level != null ? level.getLevel() : 0;
    }
    
    @Override 
    public boolean shouldPacketUseQueue(Packet<?> packet) {
        if (packetScheduler == null || packet == null) return false;
        return packetScheduler.shouldUseQueue(packet);
    }
    
    @Override 
    public int classifyPacketPriority(Packet<?> packet) {
        if (packet == null) return 1;
        org.virgil.akiasync.network.PacketPriority priority = 
            org.virgil.akiasync.network.PacketClassifier.classify(packet);
        return priority != null ? priority.ordinal() : 1;
    }
    
    @Override 
    public boolean enqueuePacket(ServerPlayer player, Packet<?> packet, int priority) {
        if (packetScheduler == null || player == null || packet == null) return false;
        org.virgil.akiasync.network.PacketPriority packetPriority = switch (priority) {
            case 0 -> org.virgil.akiasync.network.PacketPriority.HIGH;
            case 2 -> org.virgil.akiasync.network.PacketPriority.LOW;
            default -> org.virgil.akiasync.network.PacketPriority.NORMAL;
        };
        return packetScheduler.enqueuePacket(player, packet, packetPriority);
    }
    
    @Override 
    public int getPlayerPacketQueueSize(UUID playerId) {
        if (packetScheduler == null || playerId == null) return 0;
        return packetScheduler.getQueueSize(playerId);
    }
    
    @Override 
    public void recordPacketSent(UUID playerId, int bytes) {
        if (congestionDetector == null || playerId == null) return;
        congestionDetector.recordPacketSent(playerId, bytes);
    }
    
    @Override 
    public void updatePlayerChunkLocation(ServerPlayer player) {
        if (chunkRateController == null || player == null) return;
        org.bukkit.entity.Player bukkitPlayer = player.getBukkitEntity();
        if (bukkitPlayer != null) {
            chunkRateController.updatePlayerLocation(bukkitPlayer);
        }
    }
    
    @Override 
    public int calculatePlayerChunkSendRate(UUID playerId) {
        if (chunkRateController == null || playerId == null) return 10;
        org.bukkit.entity.Player player = org.bukkit.Bukkit.getPlayer(playerId);
        if (player == null) return 10;
        return chunkRateController.calculateChunkSendRate(player);
    }
    
    @Override 
    public double calculateChunkPriority(UUID playerId, int chunkX, int chunkZ) {
        if (chunkRateController == null || playerId == null) return 0.0;
        org.bukkit.entity.Player player = org.bukkit.Bukkit.getPlayer(playerId);
        if (player == null) return 0.0;
        org.bukkit.Chunk chunk = player.getWorld().getChunkAt(chunkX, chunkZ);
        return chunkRateController.calculateChunkPriority(player, chunk);
    }
    
    @Override 
    public boolean isChunkInPlayerViewDirection(UUID playerId, int chunkX, int chunkZ) {
        if (chunkRateController == null || playerId == null) return true;
        org.bukkit.entity.Player player = org.bukkit.Bukkit.getPlayer(playerId);
        if (player == null) return true;
        org.bukkit.Chunk chunk = player.getWorld().getChunkAt(chunkX, chunkZ);
        return chunkRateController.isChunkInViewDirection(player, chunk);
    }
    
    @Override 
    public void recordPlayerChunkSent(UUID playerId, boolean inViewDirection) {
        if (chunkRateController == null || playerId == null) return;
        chunkRateController.recordChunkSent(playerId, inViewDirection);
    }
    
    @Override 
    public int detectPlayerCongestion(UUID playerId) {
        return getPlayerCongestionLevel(playerId);
    }

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
    @Override public int getAsyncLoadingBatchSize() { return config != null ? config.getAsyncLoadingBatchSize() : 2; }
    @Override public long getAsyncLoadingBatchDelayMs() { return config != null ? config.getAsyncLoadingBatchDelayMs() : 20; }
    @Override 
    public void submitChunkLoad(ServerPlayer player, ChunkPos chunkPos, int priority, double speed) {
        if (chunkLoadScheduler == null || player == null || chunkPos == null) return;
        chunkLoadScheduler.submitChunkLoad(player, chunkPos, priority, speed);
    }

    // ==========================================
    // ViaVersion 兼容
    // ==========================================
    @Override public boolean isPlayerUsingViaVersion(UUID playerId) {
        return org.virgil.akiasync.compat.ViaVersionCompat.isPlayerUsingVia(playerId);
    }
    @Override public boolean isViaConnectionInPlayState(UUID playerId) {
        return org.virgil.akiasync.compat.ViaVersionCompat.isConnectionInPlayState(playerId);
    }
    @Override public int getPlayerProtocolVersion(UUID playerId) {
        org.bukkit.entity.Player player = org.bukkit.Bukkit.getPlayer(playerId);
        return player != null ? org.virgil.akiasync.compat.ViaVersionCompat.getPlayerProtocolVersion(player) : -1;
    }

    // ==========================================
    // 传送优化
    // ==========================================
    @Override public boolean isTeleportOptimizationEnabled() { return config != null && config.isTeleportOptimizationEnabled(); }
    @Override public boolean isTeleportPacketBypassEnabled() { return config != null && config.isTeleportPacketBypassEnabled(); }
    @Override public int getTeleportBoostDurationSeconds() { return config != null ? config.getTeleportBoostDurationSeconds() : 5; }
    @Override public int getTeleportMaxChunkRate() { return config != null ? config.getTeleportMaxChunkRate() : 25; }
    @Override public boolean isTeleportFilterNonEssentialPackets() { return config != null && config.isTeleportFilterNonEssentialPackets(); }
    @Override public boolean isTeleportDebugEnabled() { return config != null && config.isDebugLoggingEnabled(); }
    
    @Override
    public boolean isTeleportPacket(Packet<?> packet) {
        return TeleportPacketDetector.isTeleportPacket(packet);
    }
    
    @Override
    public void markPlayerTeleportStart(UUID playerId) {
        if (teleportTracker != null) {
            teleportTracker.markTeleportStart(playerId);
        }
    }
    
    @Override
    public boolean isPlayerTeleporting(UUID playerId) {
        return teleportTracker != null && teleportTracker.isTeleporting(playerId);
    }
    
    @Override
    public boolean shouldSendPacketDuringTeleport(Packet<?> packet, UUID playerId) {
        // 检查是否为关键包（传送期间必须发送的）
        return TeleportPacketDetector.isEssentialDuringTeleport(packet);
    }
    
    @Override
    public void recordTeleportBypassedPacket() {
        // 统计在 teleportTracker 内部处理
    }
    
    @Override
    public String getTeleportStatistics() {
        return teleportTracker != null ? teleportTracker.getDetailedStatistics() : "Teleport tracker not initialized";
    }
    
    @Override
    public boolean shouldVirtualEntityPacketBypassQueue(Packet<?> packet, ServerPlayer player) {
        return true; // 虚拟实体包直接发送
    }
    
    @Override public boolean isViewFrustumFilterEnabled() { return config != null && config.isViewFrustumFilterEnabled(); }
    @Override public boolean shouldFilterPacketByViewFrustum(Packet<?> packet, ServerPlayer player) { return false; }

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
    @Override public void clearSakuraOptimizationCaches() { }
    @Override public Map<String, Object> getSakuraCacheStatistics() { return Collections.emptyMap(); }
    @Override public void performSakuraCacheCleanup() { }

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
        if (packetSendWorker != null) {
            try {
                packetSendWorker.stop();
                logger.info("[AkiAsync/Ignite] PacketSendWorker stopped");
            } catch (Exception e) {
                // 忽略
            }
            packetSendWorker = null;
        }
        
        packetScheduler = null;
        chunkRateController = null;
        congestionDetector = null;
        
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
