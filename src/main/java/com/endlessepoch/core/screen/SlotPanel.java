package com.endlessepoch.core.screen;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

abstract class SlotPanel extends DraggablePanel {
    protected final DispatchScreen screen;

    protected SlotPanel(DispatchScreen screen, int x, int y, String title) {
        super(x, y, title); this.screen = screen;
    }

    /** Draw one slot (item + count + hover) at panel-local coords */
    protected void renderSlot(GuiGraphics g, Font font, ItemStack stack, int sx, int sy, int mx, int my) {
        g.renderItem(stack, sx, sy);
        g.renderItemDecorations(font, stack, sx, sy);
        DispatchUtil.slotHover(g, screen.mc(), sx, sy, 16, 16, mx, my);
    }

    /** Handle click on a slot via real game packet */
    protected boolean clickSlot(Player p, int idx, int btn, net.minecraft.world.inventory.ClickType type) {
        screen.mc().gameMode.handleInventoryMouseClick(p.containerMenu.containerId, idx, btn, type, p);
        return true;
    }
    protected boolean clickSlot(Player p, int idx) { return clickSlot(p, idx, 0, net.minecraft.world.inventory.ClickType.PICKUP); }
}
