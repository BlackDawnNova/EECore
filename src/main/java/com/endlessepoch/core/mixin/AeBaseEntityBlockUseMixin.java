package com.endlessepoch.core.mixin;

import com.endlessepoch.core.nova.item.AePatternCoreItem;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Right-click on any AE-family block (AE2, ExtendedAE, ECO — all extend
 * AEBaseEntityBlock) with an AE pattern core in hand: restore the packed patterns
 * straight into the provider. This runs inside the block's useItemOn, so it also
 * covers hosts whose GUI opens outside the NeoForge event flow (LDLib-based ECO
 * blocks never fire RightClickBlock). The event handler stays as the fast path;
 * this mixin is the fallback.
 * <p>
 * 手持 AE 样板坍缩核右键任意 AE 系方块（AE2/ExtendedAE/ECO——都继承
 * AEBaseEntityBlock）：直接把样板放回供应器。注入在方块 useItemOn 内，覆盖
 * 不走 NeoForge 事件流的宿主（LDLib 系的 ECO 方块不触发 RightClickBlock）。
 * 事件监听保留为快路径；此 Mixin 是兜底。
 */
@Pseudo
@Mixin(targets = "appeng.block.AEBaseEntityBlock")
public abstract class AeBaseEntityBlockUseMixin {

    @Inject(method = "useItemOn(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/InteractionHand;Lnet/minecraft/world/phys/BlockHitResult;)Lnet/minecraft/world/ItemInteractionResult;",
            at = @At("HEAD"), cancellable = true)
    private void eecore$restorePatterns(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                        Player player, InteractionHand hand, BlockHitResult hit,
                                        CallbackInfoReturnable<ItemInteractionResult> cir) {
        if (!(stack.getItem() instanceof AePatternCoreItem)) return;
        // ECO's FD bus decodes all patterns inside its inventory proxy on any write —
        // that decode wedges inside the click context (grid/sync locks) but is fine on
        // the server tick. Defer the restore one tick and short-circuit the GUI here.
        // ECO 的 FD 总线写入会触发全量样板解码——点击上下文（网格/同步锁）卡死，
        // 服务端 tick 正常。放回延迟一 tick，这里先短路开 GUI。
        var server = level.getServer();
        if (server != null) {
            server.execute(() -> AePatternCoreItem.restorePatterns(level, pos, player, stack));
            cir.setReturnValue(ItemInteractionResult.SUCCESS);
        }
    }
}
