package com.endlessepoch.core.gui;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import java.util.function.Supplier;

@OnlyIn(Dist.CLIENT)
public class CyberButton {
    public int x, y, w, h;
    public Supplier<String> textSupplier;
    public Runnable action;
    public Runnable rightAction;
    public boolean hovered;
    public boolean visible = true;

    public CyberButton(int x, int y, int w, int h, Supplier<String> textSupplier, Runnable action) {
        this(x, y, w, h, textSupplier, action, null);
    }

    public CyberButton(int x, int y, int w, int h, String text, Runnable action) {
        this(x, y, w, h, () -> text, action, null);
    }

    public CyberButton(int x, int y, int w, int h, Supplier<String> textSupplier,
                       Runnable action, Runnable rightAction) {
        this.x = x;
        this.y = y;
        this.w = w;
        this.h = h;
        this.textSupplier = textSupplier;
        this.action = action;
        this.rightAction = rightAction;
    }

    public void render(GuiGraphics g, Font font, CyberGUIStyle style) {
        if (!visible) return;

        int bg = hovered ? style.buttonHover : style.buttonBg;
        int border = hovered ? style.textSecondary : style.border;

        g.fill(x, y, x + w, y + h, bg);
        g.fill(x, y, x + w, y + 1, border);
        g.fill(x, y + h - 1, x + w, y + h, border);
        g.fill(x, y, x + 1, y + h, border);
        g.fill(x + w - 1, y, x + w, y + h, border);

        int c = style.corner;
        g.fill(x, y, x + 2, y + 1, c);
        g.fill(x, y, x + 1, y + 2, c);
        g.fill(x + w - 2, y, x + w, y + 1, c);
        g.fill(x + w - 1, y, x + w, y + 2, c);
        g.fill(x, y + h - 1, x + 2, y + h, c);
        g.fill(x, y + h - 2, x + 1, y + h, c);
        g.fill(x + w - 2, y + h - 1, x + w, y + h, c);
        g.fill(x + w - 1, y + h - 2, x + w, y + h, c);

        String text = textSupplier.get();
        int tw = font.width(text);
        int tx = x + (w - tw) / 2;
        int ty = y + (h - 8) / 2;
        g.drawString(font, text, tx + 1, ty + 1, 0x44000000, false);
        g.drawString(font, text, tx, ty, hovered ? style.textSecondary : style.buttonText, false);
    }

    public boolean isMouseOver(double mouseX, double mouseY) {
        return visible && mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
    }
}
