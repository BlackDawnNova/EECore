package com.endlessepoch.core.screen;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

public class DispatchLeftPanel {
    private final DispatchScreen screen;

    public DispatchLeftPanel(DispatchScreen screen) { this.screen = screen; }

    public void drawBg(GuiGraphics g, int x, int y, int mx, int my) {
        int py = y + screen.panelY() + 12;
        for (int i = 0; i < 2; i++) {
            int bx = x + DispatchScreen.G3_X + i * 24;
            g.blit(DispatchScreen.SPRITES, bx, py + 1, screen.leftPanel == i ? 199 : 181, 77, 18, 20, 512, 512);
            g.blit(DispatchScreen.SPRITES, bx + 2, py + 5, i == 0 ? 158 : 144, 90, 14, 12, 512, 512);
            if (DispatchUtil.hit(mx, my, bx, py, 18, 20))
                g.renderTooltip(screen.font(), Component.translatable(i == 0 ? "eecore.dispatch.mode.encode" : "eecore.dispatch.mode.craft"), mx, my);
        }
        int subY = py + 14;
        if (screen.leftPanel == 0) {
            EncodeRenderer.renderEncode(g, screen.font(), screen, x, subY, mx, my,
                    screen.encodeMode, screen.mulPressed, screen.encPressed,
                    screen.itemReplace, screen.fluidReplace);
        } else {
            EncodeRenderer.renderCraft(g, screen.font(), screen, x, subY, mx, my);
        }
    }

    private int subY(int y) { return y + screen.panelY() + 12 + 14; }

    public void drawTooltips(GuiGraphics g, int x, int y, int mx, int my) {
        if (screen.leftPanel == 0) {
            EncodeRenderer.renderTooltips(g, screen.font(), screen, x, subY(y), mx, my,
                    screen.encodeMode, screen.mulPressed, screen.itemReplace, screen.fluidReplace);
        }
    }

    public boolean mouseClicked(double mx, double my, int x, int y, int btn) {
        for (int i = 0; i < 2; i++) {
            if (DispatchUtil.hit(mx, my, x + DispatchScreen.G3_X + i * 24, y + screen.panelY() + 12, 18, 20)) { screen.leftPanel = i; screen.storageScroll = 0; return true; }
        }
        if (screen.leftPanel == 0) {
            int stX = x + DispatchScreen.G3_X + 3 * DispatchScreen.G3_S + 28 + 22 + 22 + 14, sY = y + screen.panelY() + 20;
            if (DispatchUtil.hit(mx, my, stX, sY, 20, 20)) { screen.encodeMode = 0; return true; }
            if (DispatchUtil.hit(mx, my, stX, sY + 22, 20, 20)) { screen.encodeMode = 1; return true; }
        }
        int encX = x + DispatchScreen.G3_X + 3 * DispatchScreen.G3_S + 60, encY = y + screen.panelY() + 51;
        if (DispatchUtil.hit(mx, my, encX, encY, 18, 20)) { screen.encPressed = btn == 1 ? 1 : 0; return true; }
        int mulY = y + screen.panelY() + 92;
        for (int i = 0; i < 3; i++) {
            if (DispatchUtil.hit(mx, my, x + DispatchScreen.G3_X + i * 26, mulY, 24, 14)) { screen.mulPressed = i; return true; }
            if (DispatchUtil.hit(mx, my, x + DispatchScreen.G3_X + i * 26, mulY + 17, 24, 14)) { screen.mulPressed = i + 3; return true; }
        }
        if (DispatchUtil.hit(mx, my, x + DispatchScreen.G3_X + 140, mulY, 14, 14)) { screen.itemReplace = !screen.itemReplace; return true; }
        if (DispatchUtil.hit(mx, my, x + DispatchScreen.G3_X + 140, mulY + 17, 14, 14)) { screen.fluidReplace = !screen.fluidReplace; return true; }
        return false;
    }

    public boolean mouseScrolled(double mx, double my, double sy, int x, int yy) {
        if (screen.leftPanel == 0 && screen.encodeMode == 1
                && EncodeRenderer.scrollHit(x, subY(yy), mx, my)) {
            screen.procScroll = Math.clamp(screen.procScroll - (int) Math.signum(sy), 0, 7);
            return true;
        }
        return false;
    }

    public void mouseReleased() { screen.mulPressed = -1; screen.encPressed = -1; }
}
