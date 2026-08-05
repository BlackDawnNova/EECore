package com.endlessepoch.core.mixin;

import com.endlessepoch.core.nova.item.CollapseCoreItem;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Crate item entities glow in the world — instantly recognizable among ordinary
 * loot, and visible through terrain so a packed core is never lost.
 * 坍缩核掉落物在世界中发光描边——混在普通掉落中一眼可辨，穿透地形可见，打包内容不会丢。
 */
@Mixin(ItemEntity.class)
public class ItemEntityGlowMixin {

    @Inject(method = "<init>(Lnet/minecraft/world/level/Level;DDDLnet/minecraft/world/item/ItemStack;)V",
            at = @At("RETURN"))
    private void eecore$glowCrate(Level level, double x, double y, double z, ItemStack stack, CallbackInfo ci) {
        if (CollapseCoreItem.isCore(stack))
            ((ItemEntity) (Object) this).setGlowingTag(true);
    }

    /**
     * Void float: cores hover in the void instead of vanishing. Break-produced cores
     * (origin recorded) hover at the break position; player-thrown cores (no origin)
     * and DROP mode hover at the void floor +1. Both sides clamp to the same target —
     * a static entity sends no position packets, so a falling client would get
     * yanked back on rare syncs (visible jump loop).
     * 虚空悬浮：核掉进虚空后悬浮而非消失。破坏核（记录 origin）悬浮在破坏位置原地；
     * 玩家扔的核（无 origin）与 DROP 模式悬浮在虚空底部+1。双端钳到同一目标——
     * 静止实体不发位置包，放任下落的客户端会被偶发同步拉回（可见跳变循环）。
     */
    @Inject(method = "tick", at = @At("HEAD"))
    private void eecore$voidFloat(CallbackInfo ci) {
        ItemEntity self = (ItemEntity) (Object) this;
        if (!CollapseCoreItem.isCore(self.getItem())) return;
        BlockPos origin = CollapseCoreItem.getOrigin(self.getItem());
        if (!self.level().isClientSide() && origin != null && self.getOwner() != null) {
            // Player-thrown core: the thrower is not synced to the client, so clear the
            // stale origin server-side and sync via a fresh reference — mutating the
            // stack's components directly (or setItem with the same reference) never
            // marks DATA_ITEM dirty, leaving the client hovering at the old spot.
            // 玩家扔的核：丢出者不同步到客户端，服务端清掉旧 origin 并用新引用同步
            // ——直接改组件或传同一引用不会标记 DATA_ITEM dirty，客户端会停在旧位置。
            CollapseCoreItem.clearOrigin(self.getItem());
            self.setItem(self.getItem().copy());
            origin = null;
        }
        boolean hoverAtOrigin = origin != null && "ORIGIN".equals(com.endlessepoch.core.Config.collapseVoidMode);
        double targetY = hoverAtOrigin ? origin.getY() + 0.5 : self.level().getMinBuildHeight() + 1;
        if (self.getY() < targetY) {
            if (hoverAtOrigin) {
                self.setPos(origin.getX() + 0.5, targetY, origin.getZ() + 0.5);
            } else {
                self.setPos(self.getX(), targetY, self.getZ());
            }
            self.setDeltaMovement(0, 0, 0);
        }
    }
}
