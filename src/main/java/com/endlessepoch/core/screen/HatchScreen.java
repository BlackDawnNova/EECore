package com.endlessepoch.core.screen;

import com.endlessepoch.core.menu.HatchMenu;
import com.endlessepoch.core.nova.block.part.PartBlockEntity;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

/** Energy/fluid hatch GUI — shows energy bar + fluid tank level. / 能源/流体仓界面——显示能量条+流体存量。 */
public class HatchScreen extends AbstractContainerScreen<HatchMenu> {

    private static final ResourceLocation BG = ResourceLocation.parse("eecore:textures/gui/container/bus.png");
    private static final int BAR_X = 42, BAR_Y = 20, BAR_W = 12, BAR_H = 52;

    public HatchScreen(HatchMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
        this.imageWidth = 176; this.imageHeight = 166;
    }

    @Override protected void init() {
        super.init();
        this.inventoryLabelY = this.imageHeight - 94;
    }

    @Override public void render(GuiGraphics g, int mx, int my, float p) {
        super.render(g, mx, my, p);
        this.renderTooltip(g, mx, my);
        if (menu.getHatch() == null) return;
        var pe = menu.getHatch();
        int x = left(), y = top();
        // Energy bar tooltip / 能量条提示
        if (pe.getEnergyStorage() != null && mx >= x + BAR_X && mx < x + BAR_X + BAR_W
                && my >= y + BAR_Y && my < y + BAR_Y + BAR_H)
            g.renderTooltip(font, Component.literal(
                    pe.getEnergyStorage().getEnergyStored() + " / " + pe.getEnergyStorage().getCapacity()), mx, my);
        // Fluid tank tooltip / 流体提示
        if (pe.getFluidTank() != null && mx >= x + BAR_X + BAR_W + 20 && mx < x + BAR_X + BAR_W * 2 + 20
                && my >= y + BAR_Y && my < y + BAR_Y + BAR_H)
            g.renderTooltip(font, Component.literal(
                    pe.getFluidTank().getFluidAmount() + " / " + pe.getFluidTank().getCapacity() + " mB"), mx, my);
    }

    @Override protected void renderBg(GuiGraphics g, float p, int mx, int my) {
        int x = left(), y = top();
        g.blit(BG, x, y, 0, 0, imageWidth, imageHeight, imageWidth, imageHeight);
        if (menu.getHatch() == null) return;
        var pe = menu.getHatch();

        // Energy bar / 能量条
        if (pe.getEnergyStorage() != null) {
            var es = pe.getEnergyStorage();
            float frac = es.getEnergyStored().toBigInteger().floatValue()
                    / Math.max(1, es.getCapacity().toBigInteger().floatValue());
            int filled = Math.max(1, (int)(BAR_H * frac));
            g.fill(x + BAR_X, y + BAR_Y + BAR_H - filled,
                    x + BAR_X + BAR_W, y + BAR_Y + BAR_H, 0xFFFFCC00);
            g.fill(x + BAR_X, y + BAR_Y, x + BAR_X + BAR_W, y + BAR_Y + BAR_H - filled, 0xFF333333);
            g.drawString(font, "Ω", x + BAR_X - 8, y + BAR_Y - 12, 0xFFFFCC00);
        }

        // Fluid tank bar / 流体条
        if (pe.getFluidTank() != null) {
            var ft = pe.getFluidTank();
            float frac = (float) ft.getFluidAmount() / Math.max(1, ft.getCapacity());
            int filled = Math.max(1, (int)(BAR_H * frac));
            int fx = x + BAR_X + BAR_W + 20;
            int fluidColor = ft.getFluidAmount() > 0 ? 0xFF4488FF : 0xFF333333;
            if (ft.getFluidAmount() > 0)
                g.fill(fx, y + BAR_Y + BAR_H - filled, fx + BAR_W, y + BAR_Y + BAR_H, fluidColor);
            g.fill(fx, y + BAR_Y, fx + BAR_W, y + BAR_Y + BAR_H - filled, 0xFF333333);
            g.drawString(font, "💧", fx + 1, y + BAR_Y - 12, 0xFF4488FF);
        }

        // Status text / 状态信息
        if (pe.getEnergyStorage() == null && pe.getFluidTank() == null)
            g.drawString(font, Component.translatable("eecore.gui.status.idle"),
                    x + BAR_X, y + BAR_Y + 20, 0xFF888888);

        // Player slots / 玩家背包
        for (int r = 0; r < 3; r++)
            for (int c = 0; c < 9; c++)
                ScreenUtil.drawSlot(g, x + 8 + c * 18, y + 84 + r * 18);
        for (int c = 0; c < 9; c++)
            ScreenUtil.drawSlot(g, x + 8 + c * 18, y + 142);
    }

    private int left() { return (width - imageWidth) / 2; }
    private int top() { return (height - imageHeight) / 2; }
}
