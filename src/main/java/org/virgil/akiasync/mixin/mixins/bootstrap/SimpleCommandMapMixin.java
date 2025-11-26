package org.virgil.akiasync.mixin.mixins.bootstrap;

import org.bukkit.command.SimpleCommandMap;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(value = SimpleCommandMap.class)
public abstract class SimpleCommandMapMixin {
    // 不再在这里注册命令，改为在 enablePlugins 之后注册
    // 因为此时 PluginManager 还未初始化，会导致 NPE
}

