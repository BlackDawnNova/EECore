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
}
