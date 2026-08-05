package com.endlessepoch.core.event;

import com.endlessepoch.core.nova.item.AePatternCoreItem;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/**
 * Right-click restore for the AE pattern core: fires BEFORE any block-side
 * useItemOn (which would swallow the click to open the provider GUI), so the
 * pattern container receives the packed patterns first. Cancelling the event
 * skips the GUI entirely. Applies to every AE-family provider regardless of
 * its block base class (AE2 / ExtendedAE / ECO).
 * <p>
 * AE 样板坍缩核右键放回：事件先于方块侧 useItemOn（方块会吞右键开 GUI），
 * 让样板容器先收到打包的样板；取消事件跳过 GUI。适用于所有 AE 系供应器
 * （AE2 / ExtendedAE / ECO），与方块基类无关。
 */
public class AePatternRestoreHandler {

    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        var stack = event.getItemStack();
        if (!(stack.getItem() instanceof AePatternCoreItem)) return;
        if (AePatternCoreItem.restorePatterns(event.getLevel(), event.getPos(), event.getEntity(), stack)) {
            event.setCanceled(true);
        }
    }
}
