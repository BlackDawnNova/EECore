package com.endlessepoch.core.screen.creative;

import com.endlessepoch.core.api.tier.VoltageTier;
import com.endlessepoch.core.menu.creative.CreativeHatchMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/**
 * Creative energy input hatch GUI — part-style single page: ◀/▶ tier stepper,
 * LV/HV/QV presets, amperage cycler. The selected tier drives the machine's
 * effective voltage.
 * 创造能源输入仓 GUI——部件风格单页：◀/▶ 调档、LV/HV/QV 预设、安培循环。
 * 所选档位决定机器有效电压。
 */
public class CreativeHatchScreen extends CreativePartScreen<CreativeHatchMenu> {

    public CreativeHatchScreen(CreativeHatchMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
    }

    @Override
    protected void init() {
        super.init();
        int x = leftPos, y = topPos;
        // Tier stepper / 档位步进
        addRenderableWidget(Button.builder(Component.literal("◀"), b -> clickButton(50))
                .bounds(x + 44, y + 18, 20, 16).build());
        addRenderableWidget(Button.builder(Component.literal("▶"), b -> clickButton(51))
                .bounds(x + 112, y + 18, 20, 16).build());
        // Presets / 快捷预设
        addRenderableWidget(Button.builder(Component.literal("LV"), b -> clickButton(60))
                .bounds(x + 26, y + 38, 30, 16).build());
        addRenderableWidget(Button.builder(Component.literal("HV"), b -> clickButton(61))
                .bounds(x + 60, y + 38, 30, 16).build());
        addRenderableWidget(Button.builder(Component.literal("QV"), b -> clickButton(62))
                .bounds(x + 94, y + 38, 30, 16).build());
        // Amperage cycler / 安培循环
        addRenderableWidget(Button.builder(Component.literal("A"), b -> clickButton(100))
                .bounds(x + 128, y + 38, 22, 16).build());
    }

    @Override
    protected void renderContent(GuiGraphics g) {
        // Tier between the arrows / 箭头之间的当前档位
        String tier = VoltageTier.fromOrdinal(menu.tierOrdinal()).getShortName();
        g.drawCenteredString(font, tier, leftPos + 88, topPos + 22, 0x404040);
        // Supply line / 供电信息行
        Component line = Component.translatable("eecore.gui.creative_hatch.supply",
                VoltageTier.fromOrdinal(menu.tierOrdinal()).getShortName(), menu.ampValue());
        g.drawCenteredString(font, line.getString(), leftPos + imageWidth / 2, topPos + 60, 0x404040);
    }
}
