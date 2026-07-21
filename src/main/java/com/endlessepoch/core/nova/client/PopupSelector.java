package com.endlessepoch.core.nova.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/** Generic overlay popup with selectable rows. / 通用弹窗选择器 */
class PopupSelector {
    private final String title;
    private final String[] options;
    private int selected;
    private boolean visible;

    PopupSelector(String title, String... options) { this.title = title; this.options = options; }

    int selected() { return selected; }
    void select(int i) { if (i >= 0 && i < options.length) selected = i; }
    void show() { visible = true; }
    void hide() { visible = false; }
    boolean visible() { return visible; }

    boolean keyPressed(int k) {
        if (!visible) return false;
        if (k == 256) { visible = false; return true; }
        for (int i = 0; i < options.length; i++) { if (k == 49 + i) { selected = i; return true; } }
        return false;
    }

    boolean mouseClicked(double mx, double my) {
        if (!visible) return false;
        var font = Minecraft.getInstance().font;
        int sw = Minecraft.getInstance().getWindow().getGuiScaledWidth();
        int sh = Minecraft.getInstance().getWindow().getGuiScaledHeight();
        int w = 0; for (String o : options) { int ow = font.width(Component.literal(o)) + 50; if (ow > w) w = ow; }
        int h = options.length * 16 + 40;
        int x = (sw - w) / 2, y = (sh - h) / 2;
        for (int i = 0; i < options.length; i++) {
            int rowY = y + 24 + i * 16;
            if (mx >= x + 10 && mx <= x + w - 10 && my >= rowY && my <= rowY + 14) { selected = i; visible = false; return true; }
        }
        visible = false; return true;
    }

    void draw(GuiGraphics g) {
        if (!visible) return;
        var mc = Minecraft.getInstance();
        mc.renderBuffers().bufferSource().endBatch();
        com.mojang.blaze3d.systems.RenderSystem.clearDepth(1.0);
        com.mojang.blaze3d.systems.RenderSystem.clear(256, false);
        var font = mc.font;
        int sw = mc.getWindow().getGuiScaledWidth();
        int sh = mc.getWindow().getGuiScaledHeight();
        int w = 0; for (String o : options) { int ow = font.width(Component.literal(o)) + 50; if (ow > w) w = ow; }
        int h = options.length * 16 + 40;
        int x = (sw - w) / 2, y = (sh - h) / 2;
        g.fill(x, y, x + w, y + h, 0xEE111122);
        g.renderOutline(x, y, w, h, 0xFF44CCFF);
        g.drawCenteredString(font, Component.literal(title), sw / 2, y + 8, 0xFFCC88);
        for (int i = 0; i < options.length; i++) {
            boolean sel = (i == selected);
            g.drawString(font, Component.literal((sel ? "● " : "○ ") + options[i]), x + 16, y + 24 + i * 16, sel ? 0x44CCFF : 0x888888);
        }
    }
}
