package com.endlessepoch.core.screen;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

public final class DispatchUtil {
    private DispatchUtil() {}

    public static boolean hit(double mx, double my, int bx, int by, int bw, int bh) {
        return mx >= bx && mx <= bx + bw && my >= by && my <= by + bh;
    }

    public static void slotBg(GuiGraphics g, ResourceLocation tex, int sx, int sy) { slotBg(g, tex, sx, sy, 16, 16); }
    public static void slotBg(GuiGraphics g, ResourceLocation tex, int sx, int sy, int sw, int sh) { g.blit(tex, sx, sy, 0, 0, sw, sh, 16, 16); }

    public static void slotHover(GuiGraphics g, Minecraft mc, int sx, int sy, int sw, int sh, int mx, int my) {
        if (!(mx >= sx && mx <= sx + sw && my >= sy && my <= sy + sh)) return;
        if (!mc.player.containerMenu.getCarried().isEmpty()) return;
        g.hLine(sx, sx + sw, sy - 1, 0xFFDAFFFF); g.hLine(sx - 1, sx + sw, sy + sh, 0xFFDAFFFF);
        g.vLine(sx - 1, sy - 2, sy + sh, 0xFFDAFFFF); g.vLine(sx + sw, sy - 2, sy + sh, 0xFFDAFFFF);
        g.fillGradient(net.minecraft.client.renderer.RenderType.guiOverlay(), sx, sy, sx + sw, sy + sh, 0x669CD3FF, 0x669CD3FF, 0);
    }

    /** Red hover outline for trash mode — only under the cursor. / 垃圾桶模式红色悬停描边——仅鼠标悬停格。 */
    public static void slotSelectHover(GuiGraphics g, int sx, int sy, int sw, int sh, int mx, int my) {
        if (!(mx >= sx && mx <= sx + sw && my >= sy && my <= sy + sh)) return;
        g.hLine(sx, sx + sw, sy - 1, 0xFFFF5555); g.hLine(sx - 1, sx + sw, sy + sh, 0xFFFF5555);
        g.vLine(sx - 1, sy - 2, sy + sh, 0xFFFF5555); g.vLine(sx + sw, sy - 2, sy + sh, 0xFFFF5555);
        g.fillGradient(net.minecraft.client.renderer.RenderType.guiOverlay(), sx, sy, sx + sw, sy + sh, 0x55FF5555, 0x55FF5555, 0);
    }

    /** Selected trash box — red↔black pulse fill with flowing dashed outline. / 垃圾桶框选——红黑脉冲填充+流动虚线描边。 */
    public static void slotSelect(GuiGraphics g, int sx, int sy, int sw, int sh) {
        long now = System.currentTimeMillis();
        double t = (now % 1600) / 1600.0;
        float f = (float) (0.5 - 0.5 * Math.cos(t * 2 * Math.PI));
        int red = 0x40 + (int) (f * 0xBF);
        int col = 0xFF000000 | (red << 16);
        int fill = 0x88000000 | (red << 16);
        g.fillGradient(net.minecraft.client.renderer.RenderType.guiOverlay(), sx, sy, sx + sw, sy + sh, fill, fill, 0);
        // Clockwise-flowing dashes: each edge continues the perimeter offset of the previous one
        // 顺时针流动虚线：每条边延续上一边的周长偏移
        // Clockwise flowing dashes hugging the cell edge (inside the outline), corners join
        // 顺时针流动虚线贴边（格子边缘内），拐角自然衔接
        int dash = 4, gap = 3, step = dash + gap;
        long p0 = now / 50;
        for (int x = sx + dashMod(p0, step); x < sx + sw; x += step) {
            int xe = Math.min(x + dash, sx + sw);
            g.fill(x, sy, xe, sy + 1, col);
        }
        for (int y = sy + dashMod(p0 - sw, step); y < sy + sh; y += step) {
            int ye = Math.min(y + dash, sy + sh);
            g.fill(sx + sw - 1, y, sx + sw, ye, col);
        }
        for (int x = sx + sw - dashMod(p0 - (sw + sh), step); x > sx; x -= step) {
            g.fill(Math.max(x - dash, sx), sy + sh - 1, x, sy + sh, col);
        }
        for (int y = sy + sh - dashMod(p0 - (2L * sw + sh), step); y > sy; y -= step) {
            g.fill(sx, Math.max(y - dash, sy), sx + 1, y, col);
        }
    }

    private static int dashMod(long v, int step) {
        return (int) (((v % step) + step) % step);
    }
}
