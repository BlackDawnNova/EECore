package com.endlessepoch.core.screen;

import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEFluidKey;
import com.endlessepoch.core.network.GridClickPacket;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Shared network-grid interactions for the main panel and the split-mode panel.
 * 主面板与拆分面板共用的网络网格交互（点击提取/放入/垃圾桶/流体桶 + 悬停提示）。
 */
final class GridActions {

    private GridActions() {}

    /**
     * Handle a click on a grid cell — returns true if consumed.
     * 处理网格格点击——返回是否消费。
     */
    static boolean click(DispatchScreen screen, double mx, double my,
                         int gx, int gy, int cols, int rows, int cell, int btn) {
        int c = (int) ((mx - gx) / cell), r = (int) ((my - gy) / cell);
        int idx = screen.scrollOffset * cols + r * cols + c;
        if (screen.trashMode) {
            // Trash mode blocks all other grid interactions — first click selects the
            // key, a second click on the same key deletes it, RMB cancels the selection.
            // 垃圾桶模式屏蔽其他网格操作——第一次点击框选，第二次点同物品删除，右键取消框选。
            if (btn == 1) {
                // RMB: with a selection cancel it, without one close trash mode / 右键：有框选取消框选，无框选关闭垃圾桶
                if (screen.trashPendingKey != null) screen.trashPendingKey = null;
                else screen.trashMode = false;
                return true;
            }
            if (idx >= 0 && idx < screen.filtered.size()) {
                var e = screen.filtered.get(idx);
                if (!e.key().equals(screen.trashPendingKey)) {
                    screen.trashPendingKey = e.key();
                } else {
                    net.neoforged.neoforge.network.PacketDistributor.sendToServer(
                            new GridClickPacket(e.key(), Long.MAX_VALUE, 2));
                    screen.trashPendingKey = null;
                }
            }
            return true;
        }
        var carried = screen.menu().getCarried();
        if (!carried.isEmpty()) {
            if (carried.is(Items.BUCKET) && idx >= 0 && idx < screen.filtered.size()
                    && screen.filtered.get(idx).key() instanceof AEFluidKey) {
                // Empty bucket on a fluid cell → fill it / 空桶点流体 → 装桶
                net.neoforged.neoforge.network.PacketDistributor.sendToServer(
                        new GridClickPacket(screen.filtered.get(idx).key(), 1000, 0));
            } else {
                // Fluid bucket: LMB stores the bucket, RMB pours the fluid; stackable: LMB all, RMB one
                // 流体桶：左键存桶右键倒流体；可堆叠物品：左键全存右键存一个
                var contained = net.neoforged.neoforge.fluids.FluidUtil.getFluidContained(carried);
                boolean fluidBucket = contained.isPresent() && !contained.get().isEmpty();
                int mode = fluidBucket && btn == 1 ? 5 : 4;
                long amount = !fluidBucket && btn == 1 ? 1 : carried.getCount();
                net.neoforged.neoforge.network.PacketDistributor.sendToServer(
                        new GridClickPacket(AEItemKey.of(carried), amount, mode));
            }
            return true;
        }
        if (idx >= 0 && idx < screen.filtered.size()) {
            DispatchScreen.GridEntry e = screen.filtered.get(idx);
            if (screen.hasShiftDown()) {
                net.neoforged.neoforge.network.PacketDistributor.sendToServer(
                        new GridClickPacket(e.key(), Long.MAX_VALUE, 3));
            } else if (btn == 1) {
                // RMB half, capped at 32 — non-stackable items always take one / 右键取半上限 32；不可堆叠取 1
                boolean stackable = !(e.key() instanceof AEItemKey ik) || ik.toStack(1).getMaxStackSize() > 1;
                long amount = stackable ? Math.min((e.count() + 1) / 2, 32) : 1;
                net.neoforged.neoforge.network.PacketDistributor.sendToServer(
                        new GridClickPacket(e.key(), amount, 0));
            } else {
                // LMB takes a full stack — capped by the item's max stack size / 左键取一组——上限为物品最大堆叠
                int max = e.key() instanceof AEItemKey ik2 ? ik2.toStack(1).getMaxStackSize() : 1;
                net.neoforged.neoforge.network.PacketDistributor.sendToServer(
                        new GridClickPacket(e.key(), max, 0));
            }
            return true;
        }
        return false;
    }

    /**
     * Hover tooltip for a grid cell: stored count, or the carried fluid-bucket hints.
     * 网格格悬停提示：已存储数量，或携带流体桶的操作提示。
     */
    static void tooltip(GuiGraphics g, Font font, DispatchScreen screen,
                        int gx, int gy, int cols, int rows, int cell, int mx, int my) {
        if (screen.trashMode) return; // trash mode suppresses all grid hover hints / 垃圾桶模式屏蔽网格悬浮提示
        if (!DispatchUtil.hit(mx, my, gx, gy, cols * cell, rows * cell)) return;
        int c = (int) ((mx - gx) / cell), r = (int) ((my - gy) / cell);
        if (c < 0 || c >= cols || r < 0 || r >= rows) return;
        var carried = screen.menu().getCarried();
        if (carried.isEmpty()) {
            int idx = screen.scrollOffset * cols + r * cols + c;
            if (idx >= 0 && idx < screen.filtered.size()) {
                DispatchScreen.GridEntry e = screen.filtered.get(idx);
                var tip = new ArrayList<Component>();
                tip.add(DispatchScreen.nameOf(e));
                tip.add(Component.literal("已存储: " + String.format("%,d", e.count())
                        + (e.key() instanceof AEFluidKey ? " mB" : "")).withStyle(s -> s.withColor(DispatchScreen.C_TD)));
                g.renderTooltip(font, tip, Optional.empty(), mx, my);
            }
        } else {
            var cc = net.neoforged.neoforge.fluids.FluidUtil.getFluidContained(carried);
            if (cc.isPresent() && !cc.get().isEmpty()) {
                String fn = cc.get().getFluid().getFluidType().getDescription().getString();
                var tt = new ArrayList<Component>();
                tt.add(Component.literal("左键点击: 存入 ").withStyle(s -> s.withColor(DispatchScreen.C_TD))
                        .append(Component.literal(fn + "桶").withStyle(s -> s.withColor(DispatchScreen.C_TL))));
                tt.add(Component.literal("右键点击: 存入 ").withStyle(s -> s.withColor(DispatchScreen.C_TD))
                        .append(Component.literal(fn).withStyle(s -> s.withColor(DispatchScreen.C_TL))));
                g.renderTooltip(font, tt, Optional.empty(), mx, my);
            }
        }
    }

}
