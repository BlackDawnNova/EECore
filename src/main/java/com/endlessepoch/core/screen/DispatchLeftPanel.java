package com.endlessepoch.core.screen;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

public class DispatchLeftPanel {
    private final DispatchScreen screen;

    public DispatchLeftPanel(DispatchScreen screen) { this.screen = screen; }

    public void drawBg(GuiGraphics g, int x, int y, int mx, int my) {
        int py = y + screen.panelY() + 12;
        for (int i = 0; i < 2; i++) { int bx = x + DispatchScreen.G3_X + i * 24; g.blit(DispatchScreen.SPRITES, bx, py + 1, screen.leftPanel == i ? 199 : 181, 77, 18, 20, 512, 512); g.blit(DispatchScreen.SPRITES, bx + 2, py + 5, i == 0 ? 158 : 144, 90, 14, 12, 512, 512); }
        int subY = py + 14, cy = subY + 14;
        if (screen.leftPanel == 0) {
            int sOff = screen.encodeMode == 0 ? -8 : 0;
            int stX = x + DispatchScreen.G3_X + 3 * DispatchScreen.G3_S + 28 + 22 + 22 + 14;
            int sY = subY - 6;
            int synU = screen.encodeMode == 0 ? 221 : (DispatchUtil.hit(mx, my, stX, sY, 20, 20) ? 201 : 181);
            g.blit(DispatchScreen.SPRITES, stX, sY, synU, 56, 20, 20, 512, 512);
            g.blit(DispatchScreen.SPRITES, stX + 4, sY + (screen.encodeMode == 0 ? 4 : 3), 144, 77, 12, 12, 512, 512);
            int procU = screen.encodeMode == 1 ? 221 : (DispatchUtil.hit(mx, my, stX, sY + 22, 20, 20) ? 201 : 181);
            g.blit(DispatchScreen.SPRITES, stX, sY + 22, procU, 56, 20, 20, 512, 512);
            g.blit(DispatchScreen.SPRITES, stX + 4, sY + (screen.encodeMode == 1 ? 26 : 25), 156, 77, 12, 12, 512, 512);
            g.blit(DispatchScreen.SPRITES, x + DispatchScreen.G3_X + 8 + sOff, cy - 6, 0, 55, 54, 54, 512, 512);
            for (int r = 0; r < 3; r++) for (int c = 0; c < 3; c++) DispatchUtil.slotHover(g, screen.mc(), x + DispatchScreen.G3_X + 9 + sOff + c * 18, cy - 5 + r * 18, 16, 16, mx, my);
            int ax = x + DispatchScreen.G3_X + 3 * DispatchScreen.G3_S + 6 + sOff, rx;
            int ox = x + DispatchScreen.G3_X + 3 * DispatchScreen.G3_S + 28;
            if (screen.encodeMode == 0) {
                g.blit(DispatchScreen.SPRITES, ax + 9, cy + DispatchScreen.G3_S - 3, 97, 56, 23, 16, 512, 512);
                int resX = ax + 22;
                g.blit(DispatchScreen.SPRITES, resX + 11, cy + 9, 80, 73, 24, 24, 512, 512);
                DispatchUtil.slotHover(g, screen.mc(), resX + 11, cy + 9, 24, 24, mx, my);
            } else {
                g.blit(DispatchScreen.SPRITES, ax + 9, cy + DispatchScreen.G3_S - 3, 104, 85, 21, 16, 512, 512);
                int sbX2 = x + DispatchScreen.G3_X, procRows = 10, visRows = 3, maxPScroll = procRows - visRows;
                g.blit(DispatchScreen.SPRITES, sbX2, cy - 6, 55, 55, 5, 54, 512, 512);
                int hY = cy - 6 + (int) ((long) (54 - 15) * screen.procScroll / Math.max(1, maxPScroll));
                g.blit(DispatchScreen.SPRITES, sbX2 - 1, hY, 172, 0, 7, 15, 512, 512);
                ox = x + DispatchScreen.G3_X + 3 * DispatchScreen.G3_S + 28;
                g.blit(DispatchScreen.SPRITES, ox + 9, cy - 6, 61, 55, 18, 54, 512, 512);
                if (screen.procScroll == 0) g.blit(DispatchScreen.SPRITES, ox + 10, cy - 5, 80, 56, 16, 16, 512, 512);
                for (int i = 0; i < 3; i++) DispatchUtil.slotHover(g, screen.mc(), ox + 10, cy - 6 + i * 18, 16, 16, mx, my);
            }
            int pBX = x + DispatchScreen.G3_X + 3 * DispatchScreen.G3_S + 60, by = cy + DispatchScreen.G3_S - 5;
            g.blit(DispatchScreen.SPRITES, pBX, by - 20, 125, 56, 18, 18, 512, 512);
            DispatchUtil.slotHover(g, screen.mc(), pBX + 1, by - 19, 16, 16, mx, my);
            boolean hov = DispatchUtil.hit(mx, my, pBX, by, 18, 20);
            int ep = screen.encPressed >= 0 ? 1 : 0, es = screen.encPressed >= 0 ? 353 : (hov ? 335 : 317), arU = screen.encPressed == 1 ? 111 : 105;
            g.blit(DispatchScreen.SPRITES, pBX, by + ep, es, 55, 18, 20, 512, 512);
            g.blit(DispatchScreen.SPRITES, pBX + 6, by + 5 + ep, arU, 74, 6, 10, 512, 512);
            g.blit(DispatchScreen.SPRITES, pBX, by + 22, 125, 76, 18, 18, 512, 512);
            DispatchUtil.slotHover(g, screen.mc(), pBX + 1, by + 23, 16, 16, mx, my);
            int mulY = cy + 3 * DispatchScreen.G3_S + 4;
            String[] l1 = {"×2", "×3", "×5"}, l2 = {"÷2", "÷3", "÷5"};
            for (int i = 0; i < 3; i++) {
                int bx = x + DispatchScreen.G3_X + i * 26, st = screen.mulPressed == i ? 291 : DispatchUtil.hit(mx, my, bx, mulY, 24, 14) ? 267 : 243;
                g.blit(DispatchScreen.SPRITES, bx, mulY, st, 56, 24, 14, 512, 512);
                g.drawString(screen.font(), l1[i], bx + 12 - screen.font().width(l1[i]) / 2, mulY + 2 + (screen.mulPressed == i ? 1 : 0), 0xFFFFFF, true);
            }
            g.drawString(screen.font(), Component.translatable("eecore.dispatch.replace.item"), x + DispatchScreen.G3_X + 92, mulY + 3, 0xFF_404040, false);
            g.drawString(screen.font(), Component.translatable("eecore.dispatch.replace.fluid"), x + DispatchScreen.G3_X + 92, mulY + 20, 0xFF_404040, false);
            int[] rY = {mulY, mulY + 17};
            boolean[] rOn = {screen.itemReplace, screen.fluidReplace};
            for (int j = 0; j < 2; j++) { int u = rOn[j] ? 246 : DispatchUtil.hit(mx, my, x + DispatchScreen.G3_X + 140, rY[j], 14, 14) ? 232 : 218; g.blit(DispatchScreen.SPRITES, x + DispatchScreen.G3_X + 140, rY[j], u, 77, 14, 14, 512, 512); }
            for (int i = 0; i < 3; i++) {
                int bx = x + DispatchScreen.G3_X + i * 26, st = screen.mulPressed == i + 3 ? 291 : DispatchUtil.hit(mx, my, bx, mulY + 17, 24, 14) ? 267 : 243;
                g.blit(DispatchScreen.SPRITES, bx, mulY + 17, st, 56, 24, 14, 512, 512);
                g.drawString(screen.font(), l2[i], bx + 12 - screen.font().width(l2[i]) / 2, mulY + 19 + (screen.mulPressed == i + 3 ? 1 : 0), 0xFFFFFF, true);
            }
        } else {
            int sOff = -8;
            g.blit(DispatchScreen.SPRITES, x + DispatchScreen.G3_X + 8 + sOff, cy - 6, 0, 55, 54, 54, 512, 512);
            for (int r = 0; r < 3; r++) for (int c = 0; c < 3; c++) DispatchUtil.slotHover(g, screen.mc(), x + DispatchScreen.G3_X + 9 + sOff + c * 18, cy - 5 + r * 18, 16, 16, mx, my);
            int ax = x + DispatchScreen.G3_X + 3 * DispatchScreen.G3_S + 6 + sOff;
            g.blit(DispatchScreen.SPRITES, ax + 9, cy + DispatchScreen.G3_S - 3, 97, 56, 23, 16, 512, 512);
            g.blit(DispatchScreen.SPRITES, ax + 33, cy + 9, 80, 73, 24, 24, 512, 512);
            DispatchUtil.slotHover(g, screen.mc(), ax + 33, cy + 9, 24, 24, mx, my);
            int qy = cy + 3 * DispatchScreen.G3_S + 4;
            g.drawString(screen.font(), Component.translatable("eecore.dispatch.pending"), x + DispatchScreen.G3_X + sOff, qy, DispatchScreen.C_TD, false);
            for (int i = 0; i < screen.DUMMY_QUEUE.length; i++) { int sx = x + DispatchScreen.G3_X + i * 22; g.renderItem(screen.DUMMY_QUEUE[i], sx, qy + 10); g.renderItemDecorations(screen.font(), screen.DUMMY_QUEUE[i], sx, qy + 10); }
        }
    }

    public void drawTooltips(GuiGraphics g, int x, int y, int mx, int my) {
        for (int i = 0; i < 2; i++) {
            if (DispatchUtil.hit(mx, my, x + DispatchScreen.G3_X + i * 24, y + screen.panelY() + 12, 18, 20))
                g.renderTooltip(screen.font(), Component.translatable(i == 0 ? "eecore.dispatch.mode.encode" : "eecore.dispatch.mode.craft"), mx, my);
        }
        int stX = x + DispatchScreen.G3_X + 3 * DispatchScreen.G3_S + 28 + 22 + 22 + 14, sY = y + screen.panelY() + 20;
        if (DispatchUtil.hit(mx, my, stX, sY, 20, 20)) g.renderTooltip(screen.font(), Component.translatable("eecore.dispatch.template.craft"), mx, my);
        if (DispatchUtil.hit(mx, my, stX, sY + 22, 20, 20)) g.renderTooltip(screen.font(), Component.translatable("eecore.dispatch.template.process"), mx, my);
        int encX = x + DispatchScreen.G3_X + 3 * DispatchScreen.G3_S + 60, encY = y + screen.panelY() + 51;
        if (DispatchUtil.hit(mx, my, encX, encY, 18, 20)) {
            List<Component> et = new ArrayList<>();
            et.add(Component.translatable("eecore.dispatch.template.mode"));
            et.add(Component.translatable("eecore.dispatch.template.encode").withStyle(s -> s.withColor(DispatchScreen.C_TD)));
            et.add(Component.translatable("eecore.dispatch.template.upload").withStyle(s -> s.withColor(DispatchScreen.C_TD)));
            g.renderTooltip(screen.font(), et, java.util.Optional.empty(), mx, my);
        }
        int mulY = y + screen.panelY() + 92;
        String[] mlTips = {"×2", "×3", "×5", "÷2", "÷3", "÷5"};
        for (int i = 0; i < 3; i++) {
            int mX = x + DispatchScreen.G3_X + i * 26;
            if (DispatchUtil.hit(mx, my, mX, mulY, 24, 14)) g.renderTooltip(screen.font(), Component.literal(mlTips[i]), mx, my);
            if (DispatchUtil.hit(mx, my, mX, mulY + 17, 24, 14)) g.renderTooltip(screen.font(), Component.literal(mlTips[i + 3]), mx, my);
        }
        if (DispatchUtil.hit(mx, my, x + DispatchScreen.G3_X + 140, mulY, 14, 14))
            g.renderTooltip(screen.font(), Component.translatable(screen.itemReplace ? "eecore.dispatch.replace.item_enabled" : "eecore.dispatch.replace.item_disabled"), mx, my);
        if (DispatchUtil.hit(mx, my, x + DispatchScreen.G3_X + 140, mulY + 17, 14, 14))
            g.renderTooltip(screen.font(), Component.translatable(screen.fluidReplace ? "eecore.dispatch.replace.fluid_enabled" : "eecore.dispatch.replace.fluid_disabled"), mx, my);
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
        if (screen.leftPanel == 0 && screen.encodeMode == 1 && DispatchUtil.hit(mx, my, x + DispatchScreen.G3_X, yy + screen.panelY() + 26, 60, 54)) {
            screen.procScroll = Math.clamp(screen.procScroll - (int) Math.signum(sy), 0, 7); return true;
        }
        return false;
    }

    public void mouseReleased() { screen.mulPressed = -1; screen.encPressed = -1; }
}
