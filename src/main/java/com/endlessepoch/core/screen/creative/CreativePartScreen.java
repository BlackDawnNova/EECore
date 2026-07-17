package com.endlessepoch.core.screen.creative;

import com.endlessepoch.core.screen.ScreenUtil;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;

/**
 * Base for creative part GUIs — same container style as the other part screens
 * (bus.png background, vanilla widgets, no settings page). Subclasses add their
 * controls in init() and content in renderContent().
 * 创造部件 GUI 基类——与其余部件界面同一容器风格（bus.png 背景、原版控件、
 * 无设置页）。子类在 init() 加控件、renderContent() 画内容。
 */
public abstract class CreativePartScreen<T extends AbstractContainerMenu> extends AbstractContainerScreen<T> {

    private static final ResourceLocation BG = ResourceLocation.parse("eecore:textures/gui/container/bus.png");

    protected CreativePartScreen(T menu, Inventory inv, Component title) {
        super(menu, inv, title);
        this.imageWidth = 176;
        this.imageHeight = 166;
    }

    @Override
    protected void init() {
        super.init();
        this.inventoryLabelY = imageHeight - 94;
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        super.render(g, mx, my, pt);
        this.renderTooltip(g, mx, my);
    }

    @Override
    protected void renderBg(GuiGraphics g, float pt, int mx, int my) {
        int x = leftPos, y = topPos;
        g.blit(BG, x, y, 0, 0, imageWidth, imageHeight, imageWidth, imageHeight);
        for (int r = 0; r < 3; r++)
            for (int c = 0; c < 9; c++)
                ScreenUtil.drawSlot(g, x + 8 + c * 18, y + 84 + r * 18);
        for (int c = 0; c < 9; c++)
            ScreenUtil.drawSlot(g, x + 8 + c * 18, y + 142);
        renderContent(g);
    }

    /** Part-specific content between title and inventory. / 标题与背包之间的部件内容区。 */
    protected abstract void renderContent(GuiGraphics g);

    /** Send a container button click to the server. / 向服务端发送容器按钮点击。 */
    protected void clickButton(int id) {
        if (this.minecraft != null && this.minecraft.gameMode != null)
            this.minecraft.gameMode.handleInventoryButtonClick(this.menu.containerId, id);
    }
}
