package org.virgil.akiasync.compat;

import org.virgil.akiasync.AkiAsyncPlugin;
import org.virgil.akiasync.config.ConfigManager;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * 虚拟实体兼容管理器 / Virtual Entity Compatibility Manager
 * 管理与NPC插件的兼容性
 */
public class VirtualEntityCompatManager {

    private final AkiAsyncPlugin plugin;
    private final ConfigManager configManager;
    private final Logger logger;
    private final PluginDetectorRegistry detectorRegistry;
    private final VirtualEntityPacketHandler packetHandler;
    private final Map<String, Boolean> pluginAvailability;
    private boolean enabled;

    public VirtualEntityCompatManager(AkiAsyncPlugin plugin) {
        this.plugin = plugin;
        this.configManager = plugin.getConfigManager();
        this.logger = plugin.getLogger();
        this.pluginAvailability = new ConcurrentHashMap<>();
        this.detectorRegistry = new PluginDetectorRegistry(logger);
        this.packetHandler = new VirtualEntityPacketHandler(detectorRegistry, configManager, logger);
        this.enabled = false;
    }

    public void initialize() {
        try {
            enabled = configManager.isVirtualEntityCompatibilityEnabled();
        } catch (NoSuchMethodError e) {
            enabled = true;
            logger.info("[VirtualEntity] Virtual entity compatibility config not found, defaulting to enabled");
        }

        if (!enabled) {
            logger.info("[VirtualEntity] Virtual entity compatibility is disabled in config");
            return;
        }

        try {
            boolean debugEnabled = configManager.isVirtualEntityDebugEnabled();
            org.virgil.akiasync.util.DebugLogger.updateDebugState(debugEnabled);
        } catch (NoSuchMethodError e) {
            org.virgil.akiasync.util.DebugLogger.updateDebugState(false);
        }

        logger.info("[VirtualEntity] Initializing virtual entity compatibility system...");

        // FancyNpcs
        boolean fancynpcsEnabled = true;
        boolean fancynpcsUseAPI = true;
        int fancynpcsPriority = 90;
        try {
            fancynpcsEnabled = configManager.isFancynpcsCompatEnabled();
            fancynpcsUseAPI = configManager.isFancynpcsUseAPI();
            fancynpcsPriority = configManager.getFancynpcsPriority();
        } catch (NoSuchMethodError e) {
            // Use defaults
        }

        if (fancynpcsEnabled) {
            try {
                FancyNpcsDetector fancyNpcsDetector = new FancyNpcsDetector();
                if (fancyNpcsDetector.isAvailable()) {
                    fancyNpcsDetector.setUseAPI(fancynpcsUseAPI);
                    detectorRegistry.registerDetector(fancyNpcsDetector);
                    pluginAvailability.put("FancyNpcs", true);
                    logger.info("[VirtualEntity] FancyNpcs detected and registered");
                } else {
                    pluginAvailability.put("FancyNpcs", false);
                }
            } catch (Exception e) {
                pluginAvailability.put("FancyNpcs", false);
                logger.warning("[VirtualEntity] Failed to initialize FancyNpcs detector: " + e.getMessage());
            }
        }

        // ZNPCsPlus
        boolean znpcsplusEnabled = true;
        try {
            znpcsplusEnabled = configManager.isZnpcsplusCompatEnabled();
        } catch (NoSuchMethodError e) {
            // Use defaults
        }

        if (znpcsplusEnabled) {
            try {
                ZNPCsPlusDetector znpcsPlusDetector = new ZNPCsPlusDetector();
                if (znpcsPlusDetector.isAvailable()) {
                    detectorRegistry.registerDetector(znpcsPlusDetector);
                    pluginAvailability.put("ZNPCsPlus", true);
                    logger.info("[VirtualEntity] ZNPCsPlus detected and registered");
                } else {
                    pluginAvailability.put("ZNPCsPlus", false);
                }
            } catch (Exception e) {
                pluginAvailability.put("ZNPCsPlus", false);
                logger.warning("[VirtualEntity] Failed to initialize ZNPCsPlus detector: " + e.getMessage());
            }
        }

        int registeredCount = detectorRegistry.size();
        if (registeredCount > 0) {
            logger.info("[VirtualEntity] Successfully initialized with " + registeredCount + " plugin detector(s)");
        } else {
            logger.info("[VirtualEntity] No virtual entity plugins detected");
        }
    }

    public void shutdown() {
        if (!enabled) {
            return;
        }

        logger.info("[VirtualEntity] Shutting down virtual entity compatibility system...");
        detectorRegistry.clear();
        pluginAvailability.clear();
        enabled = false;
        logger.info("[VirtualEntity] Virtual entity compatibility system shut down successfully");
    }

    public boolean isPluginAvailable(String pluginName) {
        return pluginAvailability.getOrDefault(pluginName, false);
    }

    public boolean isEnabled() {
        return enabled;
    }

    public PluginDetectorRegistry getDetectorRegistry() {
        return detectorRegistry;
    }

    public VirtualEntityPacketHandler getPacketHandler() {
        return packetHandler;
    }

    public Map<String, Boolean> getPluginAvailability() {
        return new ConcurrentHashMap<>(pluginAvailability);
    }

    public void reload() {
        logger.info("[VirtualEntity] Reloading virtual entity compatibility configuration...");

        for (PluginDetector detector : detectorRegistry.getDetectors()) {
            try {
                if (detector instanceof FancyNpcsDetector) {
                    ((FancyNpcsDetector) detector).clearCache();
                } else if (detector instanceof ZNPCsPlusDetector) {
                    ((ZNPCsPlusDetector) detector).clearCache();
                }
            } catch (Exception e) {
                logger.warning("[VirtualEntity] Failed to clear cache: " + e.getMessage());
            }
        }

        shutdown();
        initialize();

        logger.info("[VirtualEntity] Virtual entity compatibility configuration reloaded successfully");
    }
}
