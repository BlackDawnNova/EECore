package com.endlessepoch.core.screen;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

class EncodePanel extends SlotPanel {

    private static final int OX = -7, OY = -8;
    private int leftPanel;
    private int encodeMode;
    private int mulPressed = -1, encPressed = -1;
    private boolean itemReplace, fluidReplace;

    EncodePanel(DispatchScreen screen, int x, int y) {
        super(screen, x, y, "编码"); w = 170; h = 135;
    }

    @Override public void render(GuiGraphics g, Font font, int mx, int my) {
        if (collapsed) { renderCollapsed(g, font, mx, my); return; }
        g.blit(DispatchScreen.SPRITES, x, y, 0, 287, 170, 135, 512, 512);
        renderSidebar(g, font, x, y - 15, mx, my, false);
        g.drawString(font, Component.literal("工作区"), x + 8, y + 6, 0xFF_404040, false);
        int py = y + 15 + OY + 11;
        for (int i = 0; i < 2; i++) {
            int bx = x + OX + EncodeRenderer.G3_X + i * 24;
            g.blit(DispatchScreen.SPRITES, bx, py, leftPanel == i ? 199 : 181, 77, 18, 20, 512, 512);
            g.blit(DispatchScreen.SPRITES, bx + 2, py + 4, i == 0 ? 158 : 144, 90, 14, 12, 512, 512);
            if (DispatchUtil.hit(mx, my, bx, py, 18, 20))
                g.renderTooltip(font, Component.translatable(i == 0 ? "eecore.dispatch.mode.encode" : "eecore.dispatch.mode.craft"), mx, my);
        }
        if (leftPanel == 0) {
            EncodeRenderer.renderEncode(g, font, screen, x + OX, y + OY + 40, mx, my,
                    encodeMode, mulPressed, encPressed, itemReplace, fluidReplace);
            EncodeRenderer.renderTooltips(g, font, screen, x + OX, y + OY + 40, mx, my,
                    encodeMode, mulPressed, itemReplace, fluidReplace);
        } else {
            EncodeRenderer.renderCraft(g, font, screen, x + OX, y + OY + 40, mx, my);
        }
    }

    @Override public boolean mouseClicked(double mx, double my, int btn) {
        int bx = collapsed ? x + 40 : x + w - 13;
        if (DispatchUtil.hit(mx, my, bx, y + 3, 9, 9)) { collapsed = !collapsed; return true; }
        if (!collapsed && DispatchUtil.hit(mx, my, x + 3, y - 12, 13, 14)) { if (btn == 0) collapsed = true; else onSidebarRightClick(); return true; }
        if (!collapsed) {
            int py = y + 15 + OY + 11;
            for (int i = 0; i < 2; i++) {
                if (DispatchUtil.hit(mx, my, x + OX + EncodeRenderer.G3_X + i * 24, py, 18, 20)) { leftPanel = i; return true; }
            }
            if (leftPanel == 0) {
                int stX = x + OX + EncodeRenderer.G3_X + 3 * EncodeRenderer.G3_S + 28 + 22 + 22 + 14, sY = y + OY + 40 - 6;
                if (DispatchUtil.hit(mx, my, stX, sY, 20, 20)) { encodeMode = 0; return true; }
                if (DispatchUtil.hit(mx, my, stX, sY + 22, 20, 20)) { encodeMode = 1; return true; }
                int pBX = x + OX + EncodeRenderer.G3_X + 3 * EncodeRenderer.G3_S + 60, by = y + OY + 40 + 14 + EncodeRenderer.G3_S - 5;
                if (DispatchUtil.hit(mx, my, pBX, by, 18, 20)) { encPressed = btn == 1 ? 1 : 0; return true; }
                int mulY = y + OY + 40 + 14 + 3 * EncodeRenderer.G3_S + 4;
                for (int i = 0; i < 3; i++) {
                    if (DispatchUtil.hit(mx, my, x + OX + EncodeRenderer.G3_X + i * 26, mulY, 24, 14)) { mulPressed = i; return true; }
                    if (DispatchUtil.hit(mx, my, x + OX + EncodeRenderer.G3_X + i * 26, mulY + 17, 24, 14)) { mulPressed = i + 3; return true; }
                }
                if (DispatchUtil.hit(mx, my, x + OX + EncodeRenderer.G3_X + 140, mulY, 14, 14)) { itemReplace = !itemReplace; return true; }
                if (DispatchUtil.hit(mx, my, x + OX + EncodeRenderer.G3_X + 140, mulY + 17, 14, 14)) { fluidReplace = !fluidReplace; return true; }
            }
        }
        int cw = collapsed ? 53 : w, ch = collapsed ? 16 : h;
        if (DraggablePanel.hit(mx, my, x, y, cw, ch)) { dragging = true; dragOffX = (int) mx - x; dragOffY = (int) my - y; return true; }
        return false;
    }

    @Override public boolean mouseClicked(double mx, double my) { return mouseClicked(mx, my, 0); }
    @Override public void mouseReleased() { mulPressed = -1; encPressed = -1; super.mouseReleased(); }
    @Override protected void onSidebarRightClick() { screen.splitMerge = false; }

    boolean mouseScrolled(double mx, double my, double sy) {
        if (collapsed) return false;
        if (leftPanel == 0 && encodeMode == 1 && EncodeRenderer.scrollHit(x + OX, y + OY + 40, mx, my)) {
            screen.procScroll = Math.clamp(screen.procScroll - (int) Math.signum(sy), 0, 7);
            return true;
        }
        return false;
    }
}
