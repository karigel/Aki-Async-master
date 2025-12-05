package org.virgil.akiasync.ignite.adapter;

import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.virgil.akiasync.ignite.config.IgniteConfigManager;

import java.io.File;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * Ignite 适配的实体节流管理器
 * 不依赖 AkiAsyncPlugin，使用 IgniteConfigManager 获取配置
 */
public class IgniteEntityThrottlingManager {

    private final IgniteConfigManager config;
    private final Logger logger;
    private final File dataFolder;
    private FileConfiguration throttlingConfig;
    private File throttlingFile;

    private final Map<EntityType, EntityLimit> entityLimits = new ConcurrentHashMap<>();
    private final Map<EntityType, EntityCounter> entityCounters = new ConcurrentHashMap<>();

    private int taskId = -1;
    private Object foliaTask = null;

    private boolean enabled;
    private int checkInterval;
    private int throttleInterval;
    private int removalBatchSize;
    private boolean isFolia = false;

    public IgniteEntityThrottlingManager(IgniteConfigManager config, Logger logger, File dataFolder) {
        this.config = config;
        this.logger = logger;
        this.dataFolder = dataFolder;

        try {
            Class.forName("io.papermc.paper.threadedregions.RegionizedServer");
            isFolia = true;
        } catch (ClassNotFoundException e) {
            isFolia = false;
        }
    }

    public void initialize() {
        enabled = config.isEntityThrottlingEnabled();
        if (!enabled) {
            logger.info("[AkiAsync/Ignite] [EntityThrottling] Entity throttling is disabled");
            return;
        }

        checkInterval = config.getEntityThrottlingCheckInterval();
        throttleInterval = config.getEntityThrottlingThrottleInterval();
        removalBatchSize = config.getEntityThrottlingRemovalBatchSize();

        loadThrottlingConfig();
        startCheckTask();

        logger.info("[AkiAsync/Ignite] [EntityThrottling] Entity throttling enabled");
        logger.info(String.format(
                "[AkiAsync/Ignite] [EntityThrottling] Check Interval: %ds, Throttle Interval: %ds, Removal Batch: %d",
                checkInterval, throttleInterval, removalBatchSize
        ));
    }

    private void loadThrottlingConfig() {
        throttlingFile = new File(dataFolder, "throttling.yml");
        if (!throttlingFile.exists()) {
            logger.info("[AkiAsync/Ignite] [EntityThrottling] throttling.yml not found, using defaults");
            return;
        }

        try {
            throttlingConfig = YamlConfiguration.loadConfiguration(throttlingFile);
            ConfigurationSection limitsSection = throttlingConfig.getConfigurationSection("entity-limits");
            if (limitsSection != null) {
                for (String key : limitsSection.getKeys(false)) {
                    try {
                        EntityType type = EntityType.valueOf(key.toUpperCase());
                        ConfigurationSection entitySection = limitsSection.getConfigurationSection(key);
                        if (entitySection != null) {
                            int perWorld = entitySection.getInt("per-world", -1);
                            int perChunk = entitySection.getInt("per-chunk", -1);
                            boolean removeWhenThrottled = entitySection.getBoolean("remove-when-throttled", false);

                            entityLimits.put(type, new EntityLimit(perWorld, perChunk, removeWhenThrottled));
                        }
                    } catch (IllegalArgumentException e) {
                        logger.warning("[AkiAsync/Ignite] [EntityThrottling] Invalid entity type: " + key);
                    }
                }
            }

            logger.info(String.format(
                    "[AkiAsync/Ignite] [EntityThrottling] Loaded limits for %d entity types",
                    entityLimits.size()
            ));
        } catch (Exception e) {
            logger.warning("[AkiAsync/Ignite] [EntityThrottling] Failed to load throttling.yml: " + e.getMessage());
        }
    }

    private void startCheckTask() {
        if (entityLimits.isEmpty()) {
            logger.info("[AkiAsync/Ignite] [EntityThrottling] No entity limits configured, skipping task");
            return;
        }
        
        if (isFolia) {
            startFoliaTask();
        } else {
            startBukkitTask();
        }
    }

    private void startBukkitTask() {
        try {
            org.bukkit.plugin.Plugin plugin = Bukkit.getPluginManager().getPlugins()[0];
            taskId = Bukkit.getScheduler().scheduleSyncRepeatingTask(
                    plugin,
                    this::checkAndThrottleEntities,
                    20L * checkInterval,
                    20L * checkInterval
            );

            if (taskId != -1) {
                logger.info("[AkiAsync/Ignite] [EntityThrottling] Check task started (Bukkit mode)");
            }
        } catch (Exception e) {
            logger.warning("[AkiAsync/Ignite] [EntityThrottling] Failed to start Bukkit task: " + e.getMessage());
        }
    }

    private void startFoliaTask() {
        try {
            Class<?> globalRegionSchedulerClass = Class.forName("io.papermc.paper.threadedregions.scheduler.GlobalRegionScheduler");
            Object globalScheduler = Bukkit.getServer().getClass().getMethod("getGlobalRegionScheduler").invoke(Bukkit.getServer());

            java.lang.reflect.Method runAtFixedRateMethod = globalRegionSchedulerClass.getMethod(
                    "runAtFixedRate",
                    org.bukkit.plugin.Plugin.class,
                    java.util.function.Consumer.class,
                    long.class,
                    long.class
            );

            org.bukkit.plugin.Plugin plugin = Bukkit.getPluginManager().getPlugins()[0];
            foliaTask = runAtFixedRateMethod.invoke(
                    globalScheduler,
                    plugin,
                    (java.util.function.Consumer<Object>) task -> checkAndThrottleEntities(),
                    checkInterval,
                    checkInterval
            );

            logger.info("[AkiAsync/Ignite] [EntityThrottling] Check task started (Folia mode)");
        } catch (Exception e) {
            logger.warning("[AkiAsync/Ignite] [EntityThrottling] Failed to start Folia task: " + e.getMessage());
        }
    }

    private void checkAndThrottleEntities() {
        if (!enabled) {
            return;
        }

        try {
            entityCounters.clear();

            for (World world : Bukkit.getWorlds()) {
                for (Entity entity : world.getEntities()) {
                    EntityType type = entity.getType();
                    EntityLimit limit = entityLimits.get(type);

                    if (limit != null) {
                        EntityCounter counter = entityCounters.computeIfAbsent(type, k -> new EntityCounter());
                        counter.increment(world.getName(), entity.getChunk());

                        if (shouldThrottle(counter, limit, world.getName(), entity.getChunk())) {
                            if (limit.removeWhenThrottled && canRemoveEntity(entity)) {
                                entity.remove();
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            logger.warning("[AkiAsync/Ignite] [EntityThrottling] Error during entity check: " + e.getMessage());
        }
    }

    private boolean shouldThrottle(EntityCounter counter, EntityLimit limit, String worldName, Chunk chunk) {
        if (limit.perWorld > 0 && counter.getWorldCount(worldName) > limit.perWorld) {
            return true;
        }

        if (limit.perChunk > 0 && counter.getChunkCount(chunk) > limit.perChunk) {
            return true;
        }

        return false;
    }

    private boolean canRemoveEntity(Entity entity) {
        // 不移除玩家、村民等重要实体
        EntityType type = entity.getType();
        return type != EntityType.PLAYER 
                && type != EntityType.VILLAGER 
                && type != EntityType.ARMOR_STAND
                && !entity.isPersistent()
                && entity.getCustomName() == null;
    }

    public void shutdown() {
        if (taskId != -1) {
            Bukkit.getScheduler().cancelTask(taskId);
            taskId = -1;
        }

        if (foliaTask != null) {
            try {
                java.lang.reflect.Method cancelMethod = foliaTask.getClass().getMethod("cancel");
                cancelMethod.invoke(foliaTask);
            } catch (Exception e) {
                logger.warning("[AkiAsync/Ignite] [EntityThrottling] Failed to cancel Folia task: " + e.getMessage());
            }
            foliaTask = null;
        }

        logger.info("[AkiAsync/Ignite] [EntityThrottling] Entity throttling manager shut down");
    }

    private static class EntityLimit {
        final int perWorld;
        final int perChunk;
        final boolean removeWhenThrottled;

        EntityLimit(int perWorld, int perChunk, boolean removeWhenThrottled) {
            this.perWorld = perWorld;
            this.perChunk = perChunk;
            this.removeWhenThrottled = removeWhenThrottled;
        }
    }

    private static class EntityCounter {
        private final Map<String, Integer> worldCounts = new ConcurrentHashMap<>();
        private final Map<String, Integer> chunkCounts = new ConcurrentHashMap<>();

        void increment(String worldName, Chunk chunk) {
            worldCounts.merge(worldName, 1, Integer::sum);
            String chunkKey = chunk.getWorld().getName() + ":" + chunk.getX() + ":" + chunk.getZ();
            chunkCounts.merge(chunkKey, 1, Integer::sum);
        }

        int getWorldCount(String worldName) {
            return worldCounts.getOrDefault(worldName, 0);
        }

        int getChunkCount(Chunk chunk) {
            String chunkKey = chunk.getWorld().getName() + ":" + chunk.getX() + ":" + chunk.getZ();
            return chunkCounts.getOrDefault(chunkKey, 0);
        }
    }
}
