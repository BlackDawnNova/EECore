package com.endlessepoch.core.nova.client;

import com.endlessepoch.core.EECore;
import com.endlessepoch.core.api.multiblock.MultiBlockRegistry;
import com.endlessepoch.core.nova.item.MultiblockScannerItem;
import com.endlessepoch.core.registry.Blocks;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionResult;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks per-ScannerController VBO preview bindings: BlockPos → pattern ID.
 * Right-click a ScannerController while holding the scanner to bind/unbind.
 * 手持扫描仪右键 ScannerController 绑定/解绑 VBO 预览。
 */
public final class EcsPreviewTracker {
    private EcsPreviewTracker() {}

    private static final Map<BlockPos, ResourceLocation> bindings = new ConcurrentHashMap<>();

    /** Immutable snapshot of all current position→pattern bindings. / 当前所有位置→结构绑定的不可变快照。 */
    public static Map<BlockPos, ResourceLocation> getBindings() {
        return Collections.unmodifiableMap(bindings);
    }

    /** Remove a binding — called when controller is broken. / 控制器被破坏时清除绑定。 */
    public static void removeBinding(BlockPos pos) {
        bindings.remove(pos);
    }

    @EventBusSubscriber(modid = EECore.MOD_ID, value = Dist.CLIENT)
    static final class Events {
        @SubscribeEvent
        static void onRightClickBlock(PlayerInteractEvent.RightClickBlock e) {
            if (e.getSide().isServer()) return;
            var held = e.getEntity().getItemInHand(e.getHand());
            if (!(held.getItem() instanceof MultiblockScannerItem)) return;
            var pos = e.getPos();
            var level = e.getLevel();
            var state = level.getBlockState(pos);
            if (!state.is(Blocks.SCANNER_CONTROLLER.get())) return;

            if (bindings.containsKey(pos)) {
                bindings.remove(pos);
                e.getEntity().displayClientMessage(
                        Component.translatable("eecore.vbo.removed"), true);
            } else {
                var player = Minecraft.getInstance().player;
                if (player == null) return;
                var patterns = MultiBlockRegistry.getAll(player.getUUID());
                if (!patterns.isEmpty()) {
                    var entry = patterns.entrySet().iterator().next();
                    bindings.put(pos.immutable(), entry.getKey());
                    e.getEntity().displayClientMessage(
                            Component.translatable("eecore.vbo.shown", entry.getKey().toString()), true);
                } else {
                    e.getEntity().displayClientMessage(
                            Component.translatable("eecore.vbo.no_patterns"), true);
                }
            }
            e.setCancellationResult(InteractionResult.SUCCESS);
            e.setCanceled(true);
        }
    }
}
