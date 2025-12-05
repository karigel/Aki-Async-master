package org.virgil.akiasync.mixin.mixins.bootstrap;

import java.util.logging.Logger;
import org.bukkit.command.CommandMap;
import org.bukkit.craftbukkit.CraftServer;
import org.bukkit.plugin.PluginLoadOrder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.virgil.akiasync.bootstrap.AkiAsyncInitializer;
import org.virgil.akiasync.command.AkiDebugCommand;
import org.virgil.akiasync.command.AkiReloadCommand;
import org.virgil.akiasync.command.AkiTeleportStatsCommand;
import org.virgil.akiasync.command.AkiVersionCommand;

@Mixin(value = CraftServer.class)
public abstract class CraftServerLoadPluginsMixin {
    static {
        System.out.println("[AkiAsync/Ignite] ====== CraftServerLoadPluginsMixin 类已加载！======");
    }
    
    @Shadow public abstract Logger getLogger();
    @Shadow public abstract CommandMap getCommandMap();

    @Inject(method = "loadPlugins", at = @At("HEAD"))
    private void akiasync$beforeLoadPlugins(final CallbackInfo ci) {
        // 在加载插件之前初始化，但使用延迟初始化避免影响类加载器
        try {
            if (!org.virgil.akiasync.bootstrap.AkiAsyncInitializer.isInitialized()) {
                getLogger().info("[AkiAsync/Ignite] 在插件加载前初始化 AkiAsync...");
                // 使用延迟初始化，避免在类加载器操作期间执行
                java.util.concurrent.CompletableFuture.runAsync(() -> {
                    try {
                        Thread.sleep(100); // 短暂延迟，确保类加载器操作完成
                        AkiAsyncInitializer.initialize(getLogger());
                    } catch (Exception e) {
                        getLogger().severe("[AkiAsync/Ignite] 异步初始化失败: " + e.getMessage());
                        e.printStackTrace();
                    }
                });
            }
        } catch (Exception e) {
            getLogger().warning("[AkiAsync/Ignite] 初始化检查失败: " + e.getMessage());
        }
    }
    
    @Inject(method = "enablePlugins", at = @At("TAIL"))
    private void akiasync$afterEnablePlugins(final PluginLoadOrder type, final CallbackInfo info) {
        try {
            // 在 enablePlugins 之后注册命令，此时 PluginManager 已经初始化
            final CommandMap commandMap = getCommandMap();
            if (commandMap != null) {
                commandMap.register("akiasync", new AkiReloadCommand());
                commandMap.register("akiasync", new AkiDebugCommand());
                commandMap.register("akiasync", new AkiVersionCommand());
                commandMap.register("akiasync", new AkiTeleportStatsCommand());
                getLogger().info("[AkiAsync/Ignite] 命令已成功注册：/aki-reload, /aki-debug, /aki-version, /aki-teleport-stats");
            } else {
                getLogger().warning("[AkiAsync/Ignite] CommandMap 未初始化，无法注册命令");
            }
            
            // 插件已加载完成，初始化所有兼容层
            if (type == PluginLoadOrder.POSTWORLD) {
                // 初始化 ViaVersion 兼容层
                try {
                    org.virgil.akiasync.compat.ViaVersionCompat.initialize();
                } catch (Exception e) {
                    getLogger().warning("[AkiAsync/Ignite] ViaVersion 兼容层初始化失败: " + e.getMessage());
                }
                
                // 检测保护插件兼容性
                org.virgil.akiasync.util.LandProtectionIntegration.logCompatibilityStatus(getLogger());
                
                // 初始化虚拟实体检测器（FancyNpcs、ZNPCsPlus 等）
                try {
                    initializeVirtualEntityDetectors();
                } catch (Exception e) {
                    getLogger().warning("[AkiAsync/Ignite] 虚拟实体检测器初始化失败: " + e.getMessage());
                }
                
                // 初始化辅助功能（NetworkOptimization、ChunkLoadScheduler 等）
                try {
                    org.virgil.akiasync.bootstrap.IgnitePluginAdapter.getInstance().initializeAuxiliaryFeatures();
                } catch (Exception e) {
                    getLogger().warning("[AkiAsync/Ignite] 辅助功能初始化失败: " + e.getMessage());
                }
                
                getLogger().info("[AkiAsync/Ignite] 所有兼容层已初始化完成");
            }
        } catch (Exception e) {
            getLogger().severe("[AkiAsync/Ignite] 注册命令时出错: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * 初始化虚拟实体检测器
     */
    private void initializeVirtualEntityDetectors() {
        // FancyNpcs 检测器
        try {
            if (org.bukkit.Bukkit.getPluginManager().getPlugin("FancyNpcs") != null) {
                new org.virgil.akiasync.compat.FancyNpcsDetector();
                getLogger().info("[AkiAsync/Ignite] FancyNpcs 检测器已初始化");
            }
        } catch (Exception e) {
            // 忽略
        }
        
        // ZNPCsPlus 检测器
        try {
            if (org.bukkit.Bukkit.getPluginManager().getPlugin("ZNPCsPlus") != null) {
                new org.virgil.akiasync.compat.ZNPCsPlusDetector();
                getLogger().info("[AkiAsync/Ignite] ZNPCsPlus 检测器已初始化");
            }
        } catch (Exception e) {
            // 忽略
        }
    }
}

