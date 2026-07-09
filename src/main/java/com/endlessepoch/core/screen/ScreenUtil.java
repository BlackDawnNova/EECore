package com.endlessepoch.core.screen;

import net.minecraft.client.gui.GuiGraphics;

public final class ScreenUtil {
    private ScreenUtil() {}

    public static void drawSlot(GuiGraphics g, int sx, int sy) {
        g.fill(sx, sy, sx + 16, sy + 16, 0xFF_8B8B8B);
        g.fill(sx + 1, sy + 1, sx + 15, sy + 15, 0xFF_373737);
        g.fill(sx, sy, sx + 16, sy + 1, 0xFF_2B2B2B);
        g.fill(sx, sy, sx + 1, sy + 16, 0xFF_2B2B2B);
        g.fill(sx + 15, sy, sx + 16, sy + 16, 0xFF_FFFFFF);
        g.fill(sx, sy + 15, sx + 16, sy + 16, 0xFF_FFFFFF);
    }
}
