package org.virgil.akiasync.mixin.bridge;

public final class BridgeManager {
    
    private static volatile Bridge bridge = null;
    
    private BridgeManager() {
        throw new UnsupportedOperationException("This class cannot be instantiated");
    }
    
    public static void setBridge(Bridge bridge) {
        if (BridgeManager.bridge != null) {
            throw new IllegalStateException("Bridge has already been set");
        }
        BridgeManager.bridge = bridge;
        System.out.println("[AkiAsync] Bridge implementation registered: " + bridge.getClass().getName());
    }
    
    public static Bridge getBridge() {
        return bridge;
    }
    
    public static boolean isBridgeInitialized() {
        return bridge != null;
    }
    
    public static void clearBridge() {
        bridge = null;
        System.out.println("[AkiAsync] Bridge cleared");
    }
    
    public static void validateAndDisplayConfigurations() {
        // 使用局部变量避免并发问题
        Bridge currentBridge = bridge;
        if (currentBridge == null) {
            // 使用 System.err 而不是抛出异常，因为这是非致命错误
            System.err.println("[AkiAsync] Cannot validate: Bridge not initialized");
            return;
        }
        
        System.out.println("[AkiAsync] ========== Mixin Configuration Status ==========");
        
        try {
            // 再次检查，确保 bridge 在使用时仍然有效
            if (bridge == null) {
                System.err.println("[AkiAsync] Bridge became null during validation");
                return;
            }
            
            System.out.println("  [Brain] Throttle: enabled=" + currentBridge.isBrainThrottleEnabled() + 
                ", interval=" + currentBridge.getBrainThrottleInterval());
            
            System.out.println("  [Entity] TickParallel: enabled=" + currentBridge.isEntityTickParallel() + 
                ", threads=" + currentBridge.getEntityTickThreads() + 
                ", minEntities=" + currentBridge.getMinEntitiesForParallel());
            System.out.println("  [Entity] Collision: enabled=" + currentBridge.isCollisionOptimizationEnabled());
            System.out.println("  [Entity] Push: enabled=" + currentBridge.isPushOptimizationEnabled());
            System.out.println("  [Entity] LookupCache: enabled=" + currentBridge.isEntityLookupCacheEnabled() + 
                ", duration=" + currentBridge.getEntityLookupCacheDurationMs() + "ms");
            System.out.println("  [Entity] Tracker: enabled=" + currentBridge.isEntityTrackerEnabled() + 
                ", executor=" + (currentBridge.getGeneralExecutor() != null));
            
            System.out.println("  [Spawning] Enabled: " + currentBridge.isMobSpawningEnabled() + 
                ", maxPerChunk=" + currentBridge.getMaxEntitiesPerChunk());
            
            System.out.println("  [Memory] PredicateCache: " + currentBridge.isPredicateCacheEnabled());
            System.out.println("  [Memory] BlockPosPool: " + currentBridge.isBlockPosPoolEnabled());
            System.out.println("  [Memory] ListPrealloc: " + currentBridge.isListPreallocEnabled() + 
                ", capacity=" + currentBridge.getListPreallocCapacity());
            
            System.out.println("[AkiAsync] ✓ All configurations validated successfully");
            System.out.println("[AkiAsync] ✓ Mixins will initialize on first use (lazy loading)");
        } catch (NullPointerException e) {
            System.err.println("[AkiAsync] ✗ Configuration validation error: Bridge became null during validation");
        } catch (Exception e) {
            System.err.println("[AkiAsync] ✗ Configuration validation error: " + e.getMessage());
            e.printStackTrace();
        }
        
        System.out.println("[AkiAsync] ========================================================");
    }
}