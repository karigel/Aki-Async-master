package org.virgil.akiasync.mixin;
import java.util.List;
import java.util.Set;

import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

public class AkiAsyncMixinPlugin implements IMixinConfigPlugin {
    static {
        System.out.println("[AkiAsync/Ignite] ====== AkiAsyncMixinPlugin 类已加载！======");
    }
    
    @Override
    public void onLoad(String mixinPackage) {
        System.out.println("[AkiAsync/Ignite] ====== MixinPlugin.onLoad() 被调用！package: " + mixinPackage + " ======");
    }
    
    @Override
    public String getRefMapperConfig() {
        return null;
    }
    
    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        if (mixinClassName.contains("bootstrap.CraftServerLoadPluginsMixin")) {
            System.out.println("[AkiAsync/Ignite] 检查是否应用 Mixin: " + mixinClassName + " -> " + targetClassName);
        }
        return true;
    }
    
    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {
    }
    
    @Override
    public List<String> getMixins() {
        return null;
    }
    
    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
        if (mixinClassName.contains("bootstrap.CraftServerLoadPluginsMixin")) {
            System.out.println("[AkiAsync/Ignite] preApply: " + mixinClassName + " -> " + targetClassName);
        }
    }
    
    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
        if (mixinClassName.contains("bootstrap.CraftServerLoadPluginsMixin")) {
            System.out.println("[AkiAsync/Ignite] postApply: " + mixinClassName + " -> " + targetClassName);
        }
    }
}