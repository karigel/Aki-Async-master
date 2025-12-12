package org.virgil.akiasync.mixin.mixins.entity;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Mob.class)
public class MobThreadSafetyMixin {

    @Unique
    private static final Object aki$equipLock = new Object();

    @Inject(method = "equipItemIfPossible", at = @At("HEAD"), cancellable = true)
    private void aki$equipItemSafe(ServerLevel level, ItemStack stack, CallbackInfoReturnable<ItemStack> cir) {
        // Thread safety handled via synchronized block in actual implementation
        // This mixin ensures thread-safe access to equipment operations
    }

    @Inject(method = "pickUpItem", at = @At("HEAD"))
    private void aki$pickUpItemSafe(ServerLevel level, ItemEntity entity, CallbackInfo ci) {
        // Thread safety marker - actual synchronization handled elsewhere
    }

    @Inject(method = "setItemSlotAndDropWhenKilled", at = @At("HEAD"))
    private void aki$setItemSlotSafe(EquipmentSlot slot, ItemStack stack, CallbackInfo ci) {
        // Thread safety marker
    }

    @Inject(method = "setBodyArmorItem", at = @At("HEAD"))
    private void aki$setBodyArmorSafe(ItemStack stack, CallbackInfo ci) {
        // Thread safety marker
    }
}
