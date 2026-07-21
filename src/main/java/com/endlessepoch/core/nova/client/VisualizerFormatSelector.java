package com.endlessepoch.core.nova.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

class VisualizerFormatSelector {
    static final String[] NAMES = {
        Component.translatable("eecore.ecs.format.fixed").getString(),
        Component.translatable("eecore.ecs.format.frame").getString()
    };
    private final PopupSelector popup = new PopupSelector(
        Component.translatable("eecore.ecs.format.title").getString(), NAMES);

    boolean frameMode;
    private boolean inEditMode;
    int labelX, labelY, labelW;

    boolean isFrameMode() { return frameMode; }
    void setEditMode(boolean edit) { inEditMode = edit; if (!edit) popup.hide(); }

    boolean keyPressed(int k) {
        if (popup.visible()) return popup.keyPressed(k);
        if (!inEditMode) return false;
        if (k == 49 || k == 50) { frameMode = (k == 50); return true; }
        return false;
    }

    boolean mouseClicked(double mx, double my) {
        if (popup.visible()) {
            boolean r = popup.mouseClicked(mx, my);
            frameMode = (popup.selected() == 1);
            return r;
        }
        if (inEditMode && mx >= labelX && mx <= labelX + labelW && my >= labelY && my <= labelY + 12) {
            openPopup(); return true;
        }
        return false;
    }

    void openPopup() { popup.select(frameMode ? 1 : 0); popup.show(); }

    int draw(GuiGraphics g, int x, int y) {
        popup.draw(g);
        if (!inEditMode) return y;
        var font = Minecraft.getInstance().font;
        String label = (frameMode ? "§e" : "§b") + NAMES[frameMode ? 1 : 0] + " §7▼";
        g.drawString(font, Component.literal(label), x + 4, y, frameMode ? 0xFFCC44 : 0x44CCFF);
        labelX = x; labelY = y; labelW = font.width(Component.literal(label)) + 10;
        return y + 12;
    }
}
