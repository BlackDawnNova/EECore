package com.endlessepoch.core.screen;

import com.endlessepoch.core.gui.CyberButton;
import com.endlessepoch.core.gui.CyberGUIStyle;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;

public abstract class CreativeMachineScreen<T extends AbstractContainerMenu> extends AbstractContainerScreen<T> {

    protected static final int PLAYER_INVENTORY_X = 8;
    protected static final int PLAYER_INVENTORY_Y = 84;
    protected static final int HOTBAR_Y = 142;
    protected static final int SLOT_SIZE = 18;
    protected static final int BUTTON_Y = 28;
    protected static final int BUTTON_W = 40;
    protected static final int BUTTON_H = 16;
    protected static final int BUTTON_GAP = 4;
    protected static final int ARROW_SIZE = 16;

    protected static final int RAIN_COLUMNS = 8;

    protected CyberGUIStyle style = CyberGUIStyle.GREEN;

    // Matrix rain (initialized in init())
    private MatrixRain[] rainStreams;
    protected long lastRainTick = 0;

    protected long lastBlinkTime = 0;
    protected boolean blinkState = true;

    protected final List<CyberButton> cyberButtons = new ArrayList<>();
    protected boolean showSettings = false;
    protected CyberButton tierDownBtn, tierUpBtn;

    public CreativeMachineScreen(T menu, Inventory inv, Component title) {
        super(menu, inv, title);
        this.imageWidth = 176;
        this.imageHeight = 166;
        this.inventoryLabelY = -1000;
        this.titleLabelY = -1000;
    }

    public void setStyle(CyberGUIStyle style) { this.style = style; }

    // Abstract methods for subclasses
    protected abstract void createMainButtons(int startX, int cy);
    protected abstract void renderMainInfo(GuiGraphics g);
    protected abstract void renderSettingsContent(GuiGraphics g);
    protected abstract Component getTitleText(boolean showSettings);
    protected abstract boolean canShowArrows();
    protected abstract void onTierDown();
    protected abstract void onTierUp();

    @Override
    protected void init() {
        this.cyberButtons.clear();
        this.children().clear();
        this.renderables.clear();

        super.init();

        int cx = (this.width - this.imageWidth) / 2;
        int cy = (this.height - this.imageHeight) / 2;

        lastBlinkTime = System.currentTimeMillis();

        // Init Matrix rain — one stream per column, evenly spaced
        rainStreams = new MatrixRain[RAIN_COLUMNS];
        for (int i = 0; i < RAIN_COLUMNS; i++) {
            rainStreams[i] = new MatrixRain(i, RAIN_COLUMNS);
        }

        int startX = cx + 24;
        createMainButtons(startX, cy);

        // Gear button (always visible)
        cyberButtons.add(new CyberButton(
                cx + this.imageWidth + 4, cy - 2, 14, 14,
                "⚙",
                () -> {
                    showSettings = !showSettings;
                    updateVisibility();
                    playClickSound();
                }
        ));

        tierDownBtn = new CyberButton(
                cx + 55, cy + BUTTON_Y, ARROW_SIZE, BUTTON_H,
                "◀",
                () -> { if (canShowArrows()) onTierDown(); }
        );
        tierDownBtn.visible = false;
        cyberButtons.add(tierDownBtn);

        tierUpBtn = new CyberButton(
                cx + 105, cy + BUTTON_Y, ARROW_SIZE, BUTTON_H,
                "▶",
                () -> { if (canShowArrows()) onTierUp(); }
        );
        tierUpBtn.visible = false;
        cyberButtons.add(tierUpBtn);

        updateVisibility();
    }

    protected void updateVisibility() {
        for (int i = 0; i < 3 && i < cyberButtons.size(); i++) {
            cyberButtons.get(i).visible = !showSettings;
        }
        // Gear button always visible
        if (cyberButtons.size() > 3) cyberButtons.get(3).visible = true;

        tierDownBtn.visible = showSettings && canShowArrows();
        tierUpBtn.visible = showSettings && canShowArrows();
    }

    protected void playClickSound() {
        Minecraft.getInstance().getSoundManager().play(
                net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(
                        SoundEvents.UI_BUTTON_CLICK, 1.0F
                )
        );
    }

    // Render loop

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        super.renderBackground(g, mx, my, pt);
        super.render(g, mx, my, pt);
        this.renderTooltip(g, mx, my);

        for (CyberButton btn : cyberButtons) {
            btn.hovered = btn.isMouseOver(mx, my);
        }
    }

    @Override
    protected void renderBg(GuiGraphics g, float pt, int mx, int my) {
        g.fill(leftPos, topPos, leftPos + imageWidth, topPos + imageHeight, style.bgDark);

        updateRain();
        for (MatrixRain rain : rainStreams) {
            rain.render(g, font, leftPos, topPos, imageWidth);
        }

        renderMainBorder(g);
        renderTitle(g);
        drawPlayerInventory(g);
        drawSeparator(g);

        for (CyberButton btn : cyberButtons) {
            btn.render(g, font, style);
        }

        if (showSettings) {
            renderSettingsContent(g);
        } else {
            renderMainInfo(g);
        }
    }

    // Border

    protected void renderMainBorder(GuiGraphics g) {
        int b = style.border, gb = style.borderGlow, c = style.corner;
        g.fill(leftPos - 2, topPos - 2, leftPos + imageWidth + 2, topPos - 1, b);
        g.fill(leftPos - 2, topPos + imageHeight + 1, leftPos + imageWidth + 2, topPos + imageHeight + 2, b);
        g.fill(leftPos - 2, topPos - 2, leftPos - 1, topPos + imageHeight + 2, b);
        g.fill(leftPos + imageWidth + 1, topPos - 2, leftPos + imageWidth + 2, topPos + imageHeight + 2, b);

        g.fill(leftPos, topPos, leftPos + imageWidth, topPos + 1, gb);
        g.fill(leftPos, topPos + imageHeight - 1, leftPos + imageWidth, topPos + imageHeight, gb);
        g.fill(leftPos, topPos, leftPos + 1, topPos + imageHeight, gb);
        g.fill(leftPos + imageWidth - 1, topPos, leftPos + imageWidth, topPos + imageHeight, gb);

        g.fill(leftPos - 2, topPos - 2, leftPos + 3, topPos - 1, c);
        g.fill(leftPos - 2, topPos - 2, leftPos - 1, topPos + 3, c);
        g.fill(leftPos + imageWidth - 3, topPos - 2, leftPos + imageWidth + 2, topPos - 1, c);
        g.fill(leftPos + imageWidth + 1, topPos - 2, leftPos + imageWidth + 2, topPos + 3, c);
        g.fill(leftPos - 2, topPos + imageHeight + 1, leftPos + 3, topPos + imageHeight + 2, c);
        g.fill(leftPos - 2, topPos + imageHeight - 1, leftPos - 1, topPos + imageHeight + 2, c);
        g.fill(leftPos + imageWidth - 3, topPos + imageHeight + 1, leftPos + imageWidth + 2, topPos + imageHeight + 2, c);
        g.fill(leftPos + imageWidth + 1, topPos + imageHeight - 1, leftPos + imageWidth + 2, topPos + imageHeight + 2, c);
    }

    // Title

    protected void renderTitle(GuiGraphics g) {
        updateBlink();
        if (!blinkState) return;

        Component title = getTitleText(showSettings);
        int tw = font.width(title);
        int tx = leftPos + (imageWidth - tw) / 2;
        g.drawString(font, title, tx + 1, topPos + 6 + 1, 0x44000000, false);
        g.drawString(font, title, tx, topPos + 6, style.textPrimary, false);
    }

    // Inventory

    protected void drawPlayerInventory(GuiGraphics g) {
        int startX = leftPos + PLAYER_INVENTORY_X;
        int startY = topPos + PLAYER_INVENTORY_Y;
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                drawSlotBorder(g, startX + col * SLOT_SIZE - 1, startY + row * SLOT_SIZE - 1);
            }
        }
        int hotbarY = topPos + HOTBAR_Y;
        for (int col = 0; col < 9; col++) {
            drawSlotBorder(g, startX + col * SLOT_SIZE - 1, hotbarY - 1);
        }
    }

    protected void drawSlotBorder(GuiGraphics g, int x, int y) {
        int size = SLOT_SIZE;
        g.fill(x + 1, y + 1, x + size - 1, y + 2, style.innerBorder);
        g.fill(x + 1, y + size - 2, x + size - 1, y + size - 1, style.innerBorder);
        g.fill(x + 1, y + 1, x + 2, y + size - 1, style.innerBorder);
        g.fill(x + size - 2, y + 1, x + size - 1, y + size - 1, style.innerBorder);
        int c = style.corner;
        g.fill(x + 1, y + 1, x + 3, y + 2, c);
        g.fill(x + 1, y + 1, x + 2, y + 3, c);
        g.fill(x + size - 3, y + 1, x + size - 1, y + 2, c);
        g.fill(x + size - 2, y + 1, x + size - 1, y + 3, c);
        g.fill(x + 1, y + size - 2, x + 3, y + size - 1, c);
        g.fill(x + 1, y + size - 3, x + 2, y + size - 1, c);
        g.fill(x + size - 3, y + size - 2, x + size - 1, y + size - 1, c);
        g.fill(x + size - 2, y + size - 3, x + size - 1, y + size - 1, c);
    }

    // Separator

    protected void drawSeparator(GuiGraphics g) {
        updateBlink();
        if (!blinkState) return;
        int sepY = topPos + 75;
        int leftX = leftPos + 12;
        int rightX = leftPos + imageWidth - 12;

        Component title = Component.translatable("eecore.gui.inventory_label");
        int tw = font.width(title);
        int tx = leftPos + (imageWidth - tw) / 2;

        for (int x = leftX; x < tx - 4; x += 3) {
            g.fill(x, sepY, x + 1, sepY + 1, style.border);
        }
        for (int x = tx + tw + 4; x < rightX; x += 3) {
            g.fill(x, sepY, x + 1, sepY + 1, style.border);
        }
        g.drawString(font, title, tx, sepY - 4, style.textSecondary, false);
    }

    protected void updateBlink() {
        long now = System.currentTimeMillis();
        if (now - lastBlinkTime > 500) {
            blinkState = !blinkState;
            lastBlinkTime = now;
        }
    }

    // Mouse

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        for (CyberButton btn : cyberButtons) {
            if (btn.isMouseOver(mouseX, mouseY)) {
                if (button == 0 && btn.action != null) {
                    btn.action.run();
                    return true;
                }
                if (button == 1 && btn.rightAction != null) {
                    btn.rightAction.run();
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mx, int my) {}

    // Matrix rain

    protected void updateRain() {
        long now = System.currentTimeMillis();
        if (now - lastRainTick < 80) return;
        lastRainTick = now;
        for (MatrixRain r : rainStreams) r.tick();
    }

    static class MatrixRain {
        private final int col;
        private float y;
        private float speed;
        private char[] chars;
        private int headIdx;
        private long lastCharChange;

        MatrixRain(int colIndex, int totalCols) {
            this.col = colIndex;
            this.y = -(10 * (2 + (int)(Math.random() * 8)));
            this.speed = 1.2f + (float)Math.random() * 1.0f;
            this.chars = new char[16 + (int)(Math.random() * 8)];
            for (int i = 0; i < chars.length; i++) chars[i] = randomChar();
            this.headIdx = 0;
        }

        void reset(int lineH) {
            y = -(lineH * (2 + (int)(Math.random() * 8)));
            speed = 1.2f + (float)Math.random() * 1.0f;
            for (int i = 0; i < chars.length; i++) chars[i] = randomChar();
            headIdx = 0;
        }

        void tick() {
            y += speed;
            long now = System.currentTimeMillis();
            if (now - lastCharChange > 250 + (int)(Math.random() * 200)) {
                chars[headIdx] = randomChar();
                headIdx = (headIdx + 1) % chars.length;
                lastCharChange = now;
            }
            if (y > 70) reset(10);
        }

        void render(GuiGraphics g, net.minecraft.client.gui.Font font, int ox, int oy, int areaW) {
            int x = ox + 10 + col * (areaW - 20) / RAIN_COLUMNS;
            int lines = 12;
            for (int i = 0; i < lines; i++) {
                float cy = y - i * (font.lineHeight + 1);
                if (cy < 0 || cy >= 66) continue;
                int idx = (headIdx - i + chars.length) % chars.length;
                float t = (float) i / lines;
                int color = fadeColor(0xFF004400, 0xFF00CC00, t);
                if (i == 0) color = 0xFF88FF88; // head bright white-green
                g.drawString(font, String.valueOf(chars[idx]), x, (int)(oy + cy), color, false);
            }
        }

        private char randomChar() {
            return Math.random() < 0.5 ? '0' : '1';
        }

        private int fadeColor(int c1, int c2, float f) {
            f = Math.max(0, Math.min(1, f));
            int a1 = (c1 >> 24) & 0xFF, r1 = (c1 >> 16) & 0xFF, g1 = (c1 >> 8) & 0xFF, b1 = c1 & 0xFF;
            int a2 = (c2 >> 24) & 0xFF, r2 = (c2 >> 16) & 0xFF, g2 = (c2 >> 8) & 0xFF, b2 = c2 & 0xFF;
            return ((int)(a1 + (a2 - a1) * f) << 24)
                 | ((int)(r1 + (r2 - r1) * f) << 16)
                 | ((int)(g1 + (g2 - g1) * f) << 8)
                 | (int)(b1 + (b2 - b1) * f);
        }
    }
}
