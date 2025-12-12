package org.virgil.akiasync.mixin.mixins.entity;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.Villager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Villager.class)
public class VillagerThreadSafetyMixin {

    @Unique
    private final Object aki$instanceLock = new Object();

    @Inject(method = "pickUpItem", at = @At("HEAD"), cancellable = true)
    private void aki$pickUpItemSafe(ServerLevel level, ItemEntity entity, CallbackInfo ci) {
        // Thread safety check - skip if entity is already removed
        if (entity.isRemoved()) {
            ci.cancel();
        }
    }

    @Inject(method = "spawnGolemIfNeeded", at = @At("HEAD"))
    private void aki$spawnGolemSafe(ServerLevel world, long time, int requiredCount, CallbackInfo ci) {
        // Thread safety marker for golem spawning
    }
}
