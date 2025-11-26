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
                getLogger().info("[AkiAsync/Ignite] 命令已成功注册：/aki-reload, /aki-debug, /aki-version");
            } else {
                getLogger().warning("[AkiAsync/Ignite] CommandMap 未初始化，无法注册命令");
            }
        } catch (Exception e) {
            getLogger().severe("[AkiAsync/Ignite] 注册命令时出错: " + e.getMessage());
            e.printStackTrace();
        }
    }
}

