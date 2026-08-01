package com.endlessepoch.core.screen;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

class InventoryPanel extends SlotPanel {
    private long lastClickTime; private int lastClickSlot = -1;
    private boolean isDoubleClick() { long n = System.currentTimeMillis(); boolean d = n - lastClickTime < 275; lastClickTime = n; return d; }

    InventoryPanel(DispatchScreen screen, int x, int y) {
        super(screen, x, y, "物品栏"); w = 170; h = 100;
    }

    @Override public void render(GuiGraphics g, Font font, int mx, int my) {
        if (collapsed) { renderCollapsed(g, font, mx, my); return; }
        int cy = y - 10;
        g.blit(DispatchScreen.SPRITES, x, y, 0, 110, 170, 86, 512, 512);
        int bgX = x + 4, bgY = cy + 14, sx = bgX + 1, sy = bgY + 1;
        g.blit(DispatchScreen.INV_BG, bgX, bgY, 0, 0, 162, 76, 162, 76);
        renderSidebar(g, font, x, y - 15, mx, my, false);
        var inv = screen.mc().player.getInventory();
        for (int row = 0; row < 3; row++)
            for (int col = 0; col < 9; col++)
                renderSlot(g, screen.font(), inv.getItem(9 + row * 9 + col), sx + col * 18, sy + row * 18, mx, my);
        for (int col = 0; col < 9; col++)
            renderSlot(g, screen.font(), inv.getItem(col), sx + col * 18, sy + 58, mx, my);
    }

    @Override public boolean mouseClicked(double mx, double my, int btn) {
        int by = collapsed ? y : y - 10;
        int bx = collapsed ? x + 40 : x + w - 13;
        if (DispatchUtil.hit(mx, my, bx, by + 3, 9, 9)) { collapsed = !collapsed; return true; }
        if (!collapsed && DispatchUtil.hit(mx, my, x + 3, y - 12, 13, 14)) { if (btn == 0) collapsed = true; else onSidebarRightClick(); return true; }
        if (!collapsed) {
            int bgX = x + 4, bgY = y - 10 + 14, sx = bgX + 1, sy = bgY + 1;
            boolean shift = net.minecraft.client.gui.screens.Screen.hasShiftDown();
            var ct = shift ? net.minecraft.world.inventory.ClickType.QUICK_MOVE : net.minecraft.world.inventory.ClickType.PICKUP;
            for (int row = 0; row < 3; row++)
                for (int col = 0; col < 9; col++)
                    if (DispatchUtil.hit(mx, my, sx + col * 18, sy + row * 18, 16, 16)) {
                        int si = row * 9 + col;
                        if (btn == 0 && !shift && si == lastClickSlot && isDoubleClick() && !screen.mc().player.containerMenu.getCarried().isEmpty()) {
                            clickSlot(screen.mc().player, si, 0, net.minecraft.world.inventory.ClickType.PICKUP_ALL);
                            lastClickSlot = -1;
                        } else { clickSlot(screen.mc().player, si, btn, ct); lastClickSlot = si; }
                        return true;
                    }
            for (int col = 0; col < 9; col++)
                if (DispatchUtil.hit(mx, my, sx + col * 18, sy + 58, 16, 16)) {
                    int si = 27 + col;
                    if (btn == 0 && !shift && si == lastClickSlot && isDoubleClick() && !screen.mc().player.containerMenu.getCarried().isEmpty()) {
                        clickSlot(screen.mc().player, si, 0, net.minecraft.world.inventory.ClickType.PICKUP_ALL);
                        lastClickSlot = -1;
                    } else { clickSlot(screen.mc().player, si, btn, ct); lastClickSlot = si; }
                    return true;
                }
        }
        int cw = collapsed ? 53 : w, ch = collapsed ? 16 : h;
        if (DraggablePanel.hit(mx, my, x, y, cw, ch)) { dragging = true; dragOffX = (int) mx - x; dragOffY = (int) my - y; return true; }
        return false;
    }

    @Override public boolean mouseClicked(double mx, double my) { return mouseClicked(mx, my, 0); }
    @Override protected void onSidebarRightClick() { screen.splitMerge = false; }
}
