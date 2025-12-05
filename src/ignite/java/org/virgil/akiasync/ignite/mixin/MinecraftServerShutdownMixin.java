package org.virgil.akiasync.ignite.mixin;

import net.minecraft.server.MinecraftServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.virgil.akiasync.ignite.AkiAsyncInitializer;
import org.virgil.akiasync.ignite.IgnitePluginAdapter;
import org.virgil.akiasync.mixin.bridge.BridgeManager;

import java.util.logging.Logger;

/**
 * Ignite 模式下的服务器关闭 Mixin
 * Server shutdown mixin for Ignite mode
 */
@Mixin(value = MinecraftServer.class)
public abstract class MinecraftServerShutdownMixin {

    @Inject(method = "stopServer", at = @At("HEAD"))
    private void akiasync$onServerStopping(CallbackInfo ci) {
        Logger logger = Logger.getLogger("AkiAsync");
        logger.info("[AkiAsync/Ignite] 服务器正在关闭，清理资源...");
        
        try {
            // 1. 关闭 IgnitePluginAdapter 的辅助功能
            if (IgnitePluginAdapter.isInitialized()) {
                try {
                    IgnitePluginAdapter.getInstance().shutdown();
                    logger.info("[AkiAsync/Ignite] IgnitePluginAdapter 已关闭");
                } catch (Exception e) {
                    logger.warning("[AkiAsync/Ignite] IgnitePluginAdapter 关闭失败: " + e.getMessage());
                }
            }
            
            // 2. 关闭 IgniteAkiAsyncBridge
            AkiAsyncInitializer init = AkiAsyncInitializer.getInstance();
            if (init != null && init.getBridge() != null) {
                try {
                    init.getBridge().shutdown();
                    logger.info("[AkiAsync/Ignite] IgniteAkiAsyncBridge 已关闭");
                } catch (Exception e) {
                    logger.warning("[AkiAsync/Ignite] IgniteAkiAsyncBridge 关闭失败: " + e.getMessage());
                }
            }
            
            // 3. 关闭异步路径查找处理器
            try {
                org.virgil.akiasync.mixin.pathfinding.AsyncPathProcessor.shutdown();
            } catch (Exception e) {
                // 可能未初始化
            }
            
            // 4. 关闭 TNT 线程池
            try {
                org.virgil.akiasync.mixin.async.TNTThreadPool.shutdown();
            } catch (Exception e) {
                // 可能未初始化
            }
            
            // 5. 关闭结构定位优化器
            try {
                org.virgil.akiasync.async.structure.OptimizedStructureLocator.shutdown();
            } catch (Exception e) {
                // 可能未初始化
            }
            
            // 6. 关闭 DataPack 优化器
            try {
                org.virgil.akiasync.async.datapack.DataPackLoadOptimizer optimizer = 
                    org.virgil.akiasync.async.datapack.DataPackLoadOptimizer.getInstance();
                if (optimizer != null) {
                    optimizer.shutdown();
                }
            } catch (Exception e) {
                // 可能未初始化
            }
            
            // 7. 关闭 AkiAsyncInitializer 的 Executor 线程池
            if (init != null) {
                try {
                    init.shutdownExecutors();
                    logger.info("[AkiAsync/Ignite] Executor 线程池已关闭");
                } catch (Exception e) {
                    logger.warning("[AkiAsync/Ignite] Executor 关闭失败: " + e.getMessage());
                }
            }
            
            // 8. 清理 Bridge
            try {
                BridgeManager.clearBridge();
            } catch (Exception e) {
                // 忽略
            }
            
            logger.info("[AkiAsync/Ignite] ====== AkiAsync 资源清理完成 ======");
            
        } catch (Exception e) {
            logger.severe("[AkiAsync/Ignite] 关闭过程中发生错误: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
