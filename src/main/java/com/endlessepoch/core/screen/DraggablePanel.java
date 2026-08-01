package com.endlessepoch.core.screen;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

public class DraggablePanel {
    int x, y, w = 53, h = 16;
    boolean collapsed = true, dragging;
    int dragOffX, dragOffY;
    String title;

    public DraggablePanel(int x, int y, String title) { this.x = x; this.y = y; this.title = title; }

    public void render(GuiGraphics g, Font font, int mx, int my) {
        renderCollapsed(g, font, mx, my);
    }

    protected void renderCollapsed(GuiGraphics g, Font font, int mx, int my) {
        g.blit(DispatchScreen.SPRITES, x, y, 172, 110, 53, 16, 512, 512);
        g.drawString(font, Component.literal(title), x + 4, y + 4, 0xFF_404040, false);
        boolean h = hit(mx, my, x + 40, y + 3, 9, 9);
        g.blit(DispatchScreen.SPRITES, x + 40, y + 3, h ? 181 : 172, 127, 9, 9, 512, 512);
        g.blit(DispatchScreen.SPRITES, x + 42, y + 5, 192, 127, 5, 5, 512, 512);
    }

    protected void renderCloseBtn(GuiGraphics g, Font font, int bx, int by, int mx, int my) {
        boolean h = hit(mx, my, bx, by, 9, 9);
        g.blit(DispatchScreen.SPRITES, bx, by, h ? 181 : 172, 127, 9, 9, 512, 512);
    }

    public boolean mouseClicked(double mx, double my, int btn) { return mouseClicked(mx, my, btn == 0); }
    public boolean mouseClicked(double mx, double my) { return mouseClicked(mx, my, 0); }
    private boolean mouseClicked(double mx, double my, boolean left) {
        int bx = collapsed ? x + 40 : x + w - 13;
        int by = collapsed ? y : y;
        if (hit(mx, my, bx, by + 3, 9, 9)) { collapsed = !collapsed; return true; }
        if (!collapsed && hit(mx, my, x + 3, y - 12, 13, 14)) { if (left) collapsed = true; else onSidebarRightClick(); return true; }
        if (hit(mx, my, x, y, collapsed ? 53 : w, collapsed ? 16 : h)) { dragging = true; dragOffX = (int) mx - x; dragOffY = (int) my - y; return true; }
        return false;
    }

    public boolean mouseDragged(double mx, double my) {
        if (!dragging) return false;
        x = Math.max(0, (int) mx - dragOffX); y = Math.max(0, (int) my - dragOffY);
        return true;
    }

    public void mouseReleased() { dragging = false; }

    protected void onSidebarRightClick() {} // override for merge action

    /** Render sidebar with button: bg left2+mid15+right4, btn 13x14 three-state */
    protected void renderSidebar(GuiGraphics g, Font font, int sx, int sy, int mx, int my, boolean pressed) {
        g.blit(DispatchScreen.SPRITES, sx, sy, 213, 134, 2, 17, 512, 512);
        g.blit(DispatchScreen.SPRITES, sx + 2, sy, 215, 134, 15, 17, 512, 512);
        g.blit(DispatchScreen.SPRITES, sx + 17, sy, 230, 134, 4, 17, 512, 512);
        boolean hov = hit(mx, my, sx + 3, sy + 1, 13, 14);
        int u = pressed ? 198 : (hov ? 185 : 172), yOff = pressed ? 1 : 0;
        g.blit(DispatchScreen.SPRITES, sx + 3, sy + 3 + yOff, u, 138, 13, 14, 512, 512);
        g.blit(DispatchScreen.SPRITES, sx + 6, sy + 6 + yOff, 172, 154, 7, 7, 512, 512);
        if (hov) {
            java.util.List<Component> tt = new java.util.ArrayList<>();
            tt.add(Component.translatable("eecore.dispatch.sidebar.title"));
            tt.add(Component.translatable("eecore.dispatch.sidebar.collapse").withStyle(s -> s.withColor(0xFF_888888)));
            tt.add(Component.translatable("eecore.dispatch.sidebar.merge").withStyle(s -> s.withColor(0xFF_888888)));
            g.renderTooltip(font, tt, java.util.Optional.empty(), mx, my);
        }
    }

    protected static boolean hit(double mx, double my, int bx, int by, int bw, int bh) {
        return mx >= bx && mx <= bx + bw && my >= by && my <= by + bh;
    }
}
