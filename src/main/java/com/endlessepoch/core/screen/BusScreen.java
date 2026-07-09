package com.endlessepoch.core.screen;

import com.endlessepoch.core.menu.BusMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

/**
 * Bus inventory screen. Height auto-expands with slot count.
 * 总线界面，高度随格数自动扩展。
 */
public class BusScreen extends AbstractContainerScreen<BusMenu> {

    private static final ResourceLocation BG = ResourceLocation.parse("eecore:textures/gui/container/bus.png");
    private static final int BG_W = 176;
    private static final int MAX_HEIGHT = 512;

    public BusScreen(BusMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
        this.imageWidth = BG_W;
    }

    @Override
    protected void init() {
        int totalRows = (menu.getSlotCount() + 8) / 9;
        int gap = totalRows <= 3 ? 14 : 20;
        this.imageHeight = Math.min(18 + totalRows * 18 + gap + 3 * 18 + 4 + 18 + 7, MAX_HEIGHT);
        super.init();
        this.inventoryLabelY = 18 + totalRows * 18 + gap - 11;
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
        // Bus slots / 总线格
        int cols = Math.min(menu.getSlotCount(), 9);
        int slotX = 8 + (9 - cols) * 9;
        for (int i = 0; i < menu.getSlotCount(); i++)
            drawSlot(g, x + slotX + (i % 9) * 18, y + 18 + (i / 9) * 18);
        // Player inventory / 玩家背包
        int totalRows = (menu.getSlotCount() + 8) / 9;
        int gap = totalRows <= 3 ? 14 : 20;
        int invY = 18 + totalRows * 18 + gap;
        for (int r = 0; r < 3; r++)
            for (int c = 0; c < 9; c++)
                drawSlot(g, x + 8 + c * 18, y + invY + r * 18);
        int hotY = invY + 3 * 18 + 4;
        for (int c = 0; c < 9; c++)
            drawSlot(g, x + 8 + c * 18, y + hotY);
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
