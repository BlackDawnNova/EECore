package com.endlessepoch.core.screen.creative;

import com.endlessepoch.core.menu.creative.CreativeParallelMenu;
import com.endlessepoch.core.network.SetParallelPacket;
import com.endlessepoch.core.nova.block.part.CreativeParallelHatchBlockEntity;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Creative parallel hatch GUI — part-style: number box [16, 16384] + confirm.
 * The value only saves (and wakes the controller) on confirm; ESC always closes.
 * 创造并行仓 GUI——部件风格：数字输入框 [16, 16384] + 确定。
 * 仅确认时保存并唤醒控制器；ESC 恒可关闭。
 */
public class CreativeParallelScreen extends CreativePartScreen<CreativeParallelMenu> {

    private EditBox valueBox;

    public CreativeParallelScreen(CreativeParallelMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
    }

    @Override
    protected void init() {
        super.init();
        int x = leftPos, y = topPos;
        valueBox = new EditBox(font, x + 30, y + 26, 70, 16, Component.empty());
        valueBox.setMaxLength(5);
        valueBox.setValue(String.valueOf(menu.savedValue()));
        valueBox.setFilter(s -> s.isEmpty() || s.chars().allMatch(Character::isDigit));
        addRenderableWidget(valueBox);
        addRenderableWidget(Button.builder(
                Component.translatable("eecore.gui.parallel.confirm"), b -> confirm())
                .bounds(x + 106, y + 26, 44, 16).build());
    }

    private void confirm() {
        int v;
        try {
            v = Integer.parseInt(valueBox.getValue().trim());
        } catch (NumberFormatException e) {
            v = menu.savedValue();
        }
        v = Math.max(CreativeParallelHatchBlockEntity.MIN_PARALLEL,
                Math.min(v, CreativeParallelHatchBlockEntity.MAX_PARALLEL));
        valueBox.setValue(String.valueOf(v));
        PacketDistributor.sendToServer(new SetParallelPacket(menu.getPos(), v));
    }

    @Override
    protected void renderContent(GuiGraphics g) {
        Component line = Component.translatable("eecore.gui.parallel.current", menu.savedValue());
        g.drawCenteredString(font, line.getString(), leftPos + imageWidth / 2, topPos + 56, 0x404040);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 256) return super.keyPressed(keyCode, scanCode, modifiers); // ESC always closes / ESC 恒可关闭
        if (valueBox != null && valueBox.isFocused()) {
            if (keyCode == 257 || keyCode == 335) { confirm(); return true; } // Enter confirms / 回车确认
            if (valueBox.keyPressed(keyCode, scanCode, modifiers) || valueBox.canConsumeInput()) return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }
}
