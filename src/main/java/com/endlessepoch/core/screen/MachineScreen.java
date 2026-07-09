package com.endlessepoch.core.screen;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;

/**
 * Base machine GUI screen. 5-row dark panel + player inventory.
 * 机器 GUI 基类，5行暗底面板+玩家背包。
 */
public class MachineScreen<T extends AbstractContainerMenu> extends AbstractContainerScreen<T> {

    protected static final ResourceLocation BG =
            ResourceLocation.parse("eecore:textures/gui/container/machine.png");

    public MachineScreen(T menu, Inventory inv, Component title) {
        super(menu, inv, title);
        this.imageWidth = 191;
        this.imageHeight = 203;
    }

    @Override
    protected void init() {
        super.init();
        this.titleLabelX = 10;
        this.titleLabelY = 12;
        this.inventoryLabelY = -99;
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mx, int my) {
        g.drawString(this.font, this.title, this.titleLabelX, this.titleLabelY, 0xFFFFFF);
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float p) {
        super.render(g, mx, my, p);
        this.renderTooltip(g, mx, my);
    }

    @Override
    protected void renderBg(GuiGraphics g, float p, int mx, int my) {
        int x = (this.width - this.imageWidth) / 2;
        int y = (this.height - this.imageHeight) / 2;
        g.blit(BG, x, y, 0, 0, imageWidth, imageHeight, imageWidth, imageHeight);

        int rx = x + 170;
        String[] labels = {"K1","K2","K3","K4","K5","K6"};
        for (int i = 0; i < 6; i++) {
            drawSlot(g, rx, y + 11 + i * 34);
            g.drawString(font, labels[i], rx + 18 + 2, y + 24 + i * 18 + 5, 0x404040);
        }

        for (int r = 0; r < 3; r++)
            for (int c = 0; c < 9; c++)
                drawSlot(g, x + 8 + c * 18, y + 124 + r * 18);
        for (int c = 0; c < 9; c++)
            drawSlot(g, x + 8 + c * 18, y + 182);
    }

    private static void drawSlot(GuiGraphics g, int sx, int sy) {
        g.fill(sx, sy, sx + 16, sy + 16, 0xFF_8B8B8B);
        g.fill(sx + 1, sy + 1, sx + 15, sy + 15, 0xFF_373737);
        g.fill(sx, sy, sx + 16, sy + 1, 0xFF_2B2B2B);
        g.fill(sx, sy, sx + 1, sy + 16, 0xFF_2B2B2B);
        g.fill(sx + 15, sy, sx + 16, sy + 16, 0xFF_FFFFFF);
        g.fill(sx, sy + 15, sx + 16, sy + 16, 0xFF_FFFFFF);
    }
}
