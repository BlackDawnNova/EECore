package com.endlessepoch.core.screen;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

public class DispatchRightPanel {
    private final DispatchScreen screen;

    public DispatchRightPanel(DispatchScreen screen) { this.screen = screen; }

    public void drawBg(GuiGraphics g, int x, int y, int mx, int my) {
        int py = y + screen.panelY() + 17, ph = screen.H - screen.panelY() - 17;
        if (screen.leftPanel == 1) {
            int vis = ph / 18;
            for (int i = 0; i < Math.min(vis, screen.MACHINES.size()); i++) {
                int mi = screen.machineScroll + i;
                if (mi >= screen.MACHINES.size()) break;
                int ry = py + i * 18;
                if (DispatchUtil.hit(mx, my, x + DispatchScreen.DIV_X + 4, ry, DispatchScreen.W - DispatchScreen.DIV_X - 8, 16))
                    g.fill(x + DispatchScreen.DIV_X + 2, ry, x + DispatchScreen.W - 4, ry + 16, 0x33_66AAFF);
            }
        } else {
            int cols = 9, sp = 18, vis = ph / sp, total = (screen.PATTERN_SLOTS + cols - 1) / cols;
            int sbX = x + DispatchScreen.W - 11, sbH = vis * sp, ty = py - 1;
            if (sbH <= 18) { g.blit(DispatchScreen.SPRITES, sbX, ty, 164, 0, 5, 9, 512, 512); g.blit(DispatchScreen.SPRITES, sbX, ty + 9, 164, 27, 5, 9, 512, 512); }
            else if (sbH <= 36) g.blit(DispatchScreen.SPRITES, sbX, ty, 164, 0, 5, 36, 512, 512);
            else {
                g.blit(DispatchScreen.SPRITES, sbX, ty, 164, 0, 5, 18, 512, 512);
                for (int sy = ty + 18; sy < ty + sbH - 18; sy += 18) g.blit(DispatchScreen.SPRITES, sbX, sy, 164, 36, 5, 18, 512, 512);
                g.blit(DispatchScreen.SPRITES, sbX, ty + sbH - 18, 164, 18, 5, 18, 512, 512);
            }
            if (total > vis) { int maxOff = Math.max(1, total - vis), hY = ty + (int) ((long) (sbH - 15) * screen.storageScroll / maxOff); g.blit(DispatchScreen.SPRITES, sbX - 1, hY, 172, 0, 7, 15, 512, 512); }
            int vv = Math.min(vis, total);
            for (int r = 0; r < vv; r++) {
                int sr = screen.storageScroll + r;
                if (sr >= total) break;
                int ry = py + r * sp;
                if (vv == 1) { g.blit(DispatchScreen.SPRITES, x + DispatchScreen.DIV_X + 3, ry - 1, 0, 0, 162, 9, 512, 512); g.blit(DispatchScreen.SPRITES, x + DispatchScreen.DIV_X + 3, ry + 8, 0, 27, 162, 9, 512, 512); }
                else if (r == 0) g.blit(DispatchScreen.SPRITES, x + DispatchScreen.DIV_X + 3, ry - 1, 0, 0, 162, 18, 512, 512);
                else if (r == vv - 1) g.blit(DispatchScreen.SPRITES, x + DispatchScreen.DIV_X + 3, ry - 1, 0, 18, 162, 18, 512, 512);
                else g.blit(DispatchScreen.SPRITES, x + DispatchScreen.DIV_X + 3, ry - 1, 0, 36, 162, 18, 512, 512);
                for (int c = 0; c < cols; c++) {
                    int idx = sr * cols + c;
                    if (idx >= screen.PATTERN_SLOTS) break;
                    int sx2 = x + DispatchScreen.DIV_X + 4 + c * sp;
                    g.renderItem(screen.DUMMY_PATTERNS[idx % screen.DUMMY_PATTERNS.length], sx2, ry);
                    DispatchUtil.slotHover(g, screen.mc(), x + DispatchScreen.DIV_X + 4 + c * sp, ry, 16, 16, mx, my);
                }
            }
        }
    }

    public void drawFg(GuiGraphics g, int x, int y, int mx, int my) {
        String rKey; boolean clickable = false;
        if (screen.leftPanel == 1) rKey = "eecore.dispatch.panel.machines";
        else { rKey = screen.rightMode == 0 ? "eecore.dispatch.panel.storage" : "eecore.dispatch.panel.ae"; clickable = true; }
        Component rTitle = Component.translatable(rKey);
        g.drawString(screen.font(), Component.translatable("eecore.dispatch.panel.workspace"), x + 8, y + screen.panelY() + 2, 0xFF_404040, false);
        int rw = screen.font().width(rTitle);
        boolean hov = clickable && DispatchUtil.hit(mx, my, x + DispatchScreen.DIV_X + 1, y + screen.panelY() + 2, rw, 10);
        g.drawString(screen.font(), rTitle, x + DispatchScreen.DIV_X + 1, y + screen.panelY() + 2, hov ? DispatchScreen.C_HL : 0xFF_404040, false);
        int py = y + screen.panelY() + 17, ph = screen.H - screen.panelY() - 17;
        if (screen.leftPanel == 1) {
            MachineListRenderer.render(g, screen.font(), screen, x + DispatchScreen.DIV_X + 2, py, DispatchScreen.W - DispatchScreen.DIV_X - 8, mx, my,
                    screen.MACHINES, screen.machineScroll, ph / 18, 0, 0, 0, 0);
        }
    }

    public void drawTooltips(GuiGraphics g, int x, int y, int mx, int my) {
        if (screen.leftPanel == 1) return;
        Component rTitle = Component.translatable(screen.rightMode == 0 ? "eecore.dispatch.panel.storage" : "eecore.dispatch.panel.ae");
        int rw = screen.font().width(rTitle);
        if (DispatchUtil.hit(mx, my, x + DispatchScreen.DIV_X + 1, y + screen.panelY() + 2, rw, 10)) {
            g.drawString(screen.font(), rTitle, x + DispatchScreen.DIV_X + 1, y + screen.panelY() + 2, DispatchScreen.C_HL, false);
            List<Component> rt = new ArrayList<>();
            rt.add(Component.translatable(screen.rightMode == 0 ? "eecore.dispatch.panel.storage" : "eecore.dispatch.panel.ae"));
            rt.add(Component.translatable(screen.rightMode == 0 ? "eecore.dispatch.panel.switch_ae" : "eecore.dispatch.panel.switch_storage").withStyle(s -> s.withColor(DispatchScreen.C_TD)));
            g.renderTooltip(screen.font(), rt, java.util.Optional.empty(), mx, my);
        }
    }

    public boolean mouseClicked(double mx, double my, int x, int y) {
        if (screen.leftPanel == 0 && DispatchUtil.hit(mx, my, x + DispatchScreen.DIV_X + 1, y + screen.panelY() + 2, screen.font().width(Component.translatable(screen.rightMode == 0 ? "eecore.dispatch.panel.storage" : "eecore.dispatch.panel.ae")), 10)) {
            screen.rightMode = 1 - screen.rightMode;
            return true;
        }
        return false;
    }

    public boolean mouseScrolled(double mx, double my, double sy, int x, int yy) {
        int ph = screen.H - screen.panelY() - 12;
        if (!DispatchUtil.hit(mx, my, x + DispatchScreen.DIV_X, yy + screen.panelY() + 17, DispatchScreen.W - DispatchScreen.DIV_X, ph)) return false;
        if (screen.leftPanel == 1) {
            int maxM = MachineListRenderer.maxScroll(screen.MACHINES, ph / 18);
            if (maxM > 0) screen.machineScroll = Math.clamp(screen.machineScroll - (int) Math.signum(sy), 0, maxM);
        } else {
            int cols = 9, total = (screen.PATTERN_SLOTS + cols - 1) / cols, vis = ph / 18;
            int maxS = Math.max(0, total - vis);
            if (maxS > 0) screen.storageScroll = Math.clamp(screen.storageScroll - (int) Math.signum(sy), 0, maxS);
        }
        return true;
    }
}
