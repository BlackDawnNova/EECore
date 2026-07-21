package com.endlessepoch.core.nova.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

class VisualizerConfirmDialog {
    private static final int W = 200, H = 80;

    boolean show;
    boolean isSave;
    int targetIdx;
    String fileName = "";

    void open(boolean save, int idx, String defaultName) {
        show = true; isSave = save; targetIdx = idx;
        fileName = defaultName.replace("scanned_", "");
    }

    void close() { show = false; fileName = ""; }

    void charTyped(char c) {
        int cp = c;
        if (cp >= 32 && cp != 127) fileName += c;
    }

    void keyPressed(int k) {
        if (k == 259 && !fileName.isEmpty()) fileName = fileName.substring(0, fileName.length() - 1);
    }

    boolean mouseClicked(double mx, double my) {
        if (!show) return false;
        int cx = (Minecraft.getInstance().getWindow().getGuiScaledWidth() - W) / 2;
        int cy = (Minecraft.getInstance().getWindow().getGuiScaledHeight() - H) / 2;
        return mx >= cx && mx <= cx + W && my >= cy && my <= cy + H;
    }

    void draw(GuiGraphics g, String patternName) {
        if (!show) return;
        var font = Minecraft.getInstance().font;
        var mc = Minecraft.getInstance();
        mc.renderBuffers().bufferSource().endBatch();
        com.mojang.blaze3d.systems.RenderSystem.clearDepth(1.0);
        com.mojang.blaze3d.systems.RenderSystem.clear(256, false);

        int sw = mc.getWindow().getGuiScaledWidth();
        int sh = mc.getWindow().getGuiScaledHeight();
        int cx = (sw - 200) / 2, cy = (sh - 80) / 2;
        String title = isSave ? "§e确认保存修改?" : "§e确认删除此结构?";
        drawBox(g, font, cx, cy, title);

        var buf = mc.renderBuffers().bufferSource();
        var pose = g.pose().last().pose();
        String disp = isSave ? "§f" + fileName + "§7_" : "§7" + patternName;
        font.drawInBatch(Component.literal(disp), cx + 12, cy + 32, 0xCCCCCCCC, false,
                pose, buf, Font.DisplayMode.SEE_THROUGH, 0, 0xF000F0);
        font.drawInBatch(Component.literal("§a✔ 确认"), cx + 20, cy + 58, 0xFFFFFFFF, false,
                pose, buf, Font.DisplayMode.SEE_THROUGH, 0, 0xF000F0);
        font.drawInBatch(Component.literal("§c✘ 取消"), cx + 200 - 60, cy + 58, 0xFFFFFFFF, false,
                pose, buf, Font.DisplayMode.SEE_THROUGH, 0, 0xF000F0);
        buf.endBatch();
    }

    private static void drawBox(GuiGraphics g, Font font, int cx, int cy, String title) {
        g.fill(cx, cy, cx + 200, cy + 80, 0xCC000000);
        g.renderOutline(cx, cy, 200, 80, 0xFFCCCCCC);
        g.renderOutline(cx + 1, cy + 1, 198, 78, 0x33FFFFFF);
        g.drawCenteredString(font, Component.literal(title), cx + 100, cy + 8, 0xFFFFD700);
        g.hLine(cx + 10, cx + 190, cy + 22, 0xFF888888);
    }
}
