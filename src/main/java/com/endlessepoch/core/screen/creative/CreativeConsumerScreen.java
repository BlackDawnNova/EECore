package com.endlessepoch.core.screen.creative;

import com.endlessepoch.core.gui.CyberButton;
import com.endlessepoch.core.api.tier.VoltageTier;
import com.endlessepoch.core.blockentity.creative.CreativeConsumerBlockEntity;
import com.endlessepoch.core.menu.creative.CreativeConsumerMenu;
import com.endlessepoch.core.screen.CreativeMachineScreen;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

public class CreativeConsumerScreen extends CreativeMachineScreen<CreativeConsumerMenu> {

    public CreativeConsumerScreen(CreativeConsumerMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
    }

    @Override
    protected void createMainButtons(int startX, int cy) {
        CreativeConsumerBlockEntity be = getMenu().getBlockEntity();

        cyberButtons.add(new CyberButton(
                startX, cy + BUTTON_Y, BUTTON_W, BUTTON_H,
                Component.translatable("eecore.gui.consumer.clear").getString(),
                () -> {
                    if (be != null) be.clearAll();
                    clickButton(0);
                }
        ));

        cyberButtons.add(new CyberButton(
                startX + BUTTON_W + BUTTON_GAP, cy + BUTTON_Y, BUTTON_W, BUTTON_H,
                () -> {
                    CreativeConsumerBlockEntity b = getMenu().getBlockEntity();
                    return b != null && b.isAutoMode()
                            ? Component.translatable("eecore.gui.consumer.auto").getString()
                            : Component.translatable("eecore.gui.consumer.manual").getString();
                },
                () -> {
                    CreativeConsumerBlockEntity b = getMenu().getBlockEntity();
                    if (b != null) b.toggleAutoMode();
                    clickButton(1);
                }
        ));

        cyberButtons.add(new CyberButton(
                startX + 2 * (BUTTON_W + BUTTON_GAP), cy + BUTTON_Y, BUTTON_W, BUTTON_H,
                () -> {
                    CreativeConsumerBlockEntity b = getMenu().getBlockEntity();
                    return b != null && b.isLogToChat() ? "显示" : "隐藏";
                },
                () -> {
                    CreativeConsumerBlockEntity b = getMenu().getBlockEntity();
                    if (b != null) b.setLogToChat(!b.isLogToChat());
                    clickButton(2);
                }
        ));
    }

    // ---- helpers ----

    private void clickButton(int id) {
        if (this.minecraft != null && this.minecraft.gameMode != null)
            this.minecraft.gameMode.handleInventoryButtonClick(this.menu.containerId, id);
    }

    // ---- renders ----

    @Override
    protected void renderMainInfo(GuiGraphics g) {
        CreativeConsumerBlockEntity be = getMenu().getBlockEntity();
        if (be == null) return;

        Component line1 = Component.translatable("eecore.gui.consumer.total_received",
                be.getTotalReceived().toDisplayString());
        int tw1 = font.width(line1);
        int tx1 = leftPos + (imageWidth - tw1) / 2;
        g.drawString(font, line1, tx1, topPos + 50, style.textSecondary, false);
    }

    @Override
    protected void renderSettingsContent(GuiGraphics g) {
        CreativeConsumerBlockEntity be = getMenu().getBlockEntity();
        if (be == null) return;

        if (be.isAutoMode()) {
            Component text = Component.translatable("eecore.gui.auto_adapt");
            int tw = font.width(text);
            int tx = leftPos + (imageWidth - tw) / 2;
            g.drawString(font, text, tx, topPos + 32, style.textSecondary, false);
        } else {
            String tierText = be.getManualTier().getShortName();
            int tw = font.width(tierText);
            int tx = leftPos + (imageWidth - tw) / 2;
            g.drawString(font, tierText, tx, topPos + 32, style.textPrimary, false);
        }
    }

    @Override
    protected Component getTitleText(boolean showSettings) {
        if (showSettings) return Component.translatable("eecore.gui.settings.title");
        CreativeConsumerBlockEntity be = getMenu().getBlockEntity();
        if (be != null && be.isAutoMode()) {
            return Component.translatable("eecore.gui.consumer.title_auto");
        }
        String tierShort = be != null ? be.getManualTier().getShortName() : "LV";
        return Component.translatable("eecore.gui.consumer.title_manual", tierShort);
    }

    @Override
    protected boolean canShowArrows() {
        CreativeConsumerBlockEntity be = getMenu().getBlockEntity();
        return be != null && !be.isAutoMode();
    }

    @Override
    protected void onTierDown() {
        CreativeConsumerBlockEntity be = getMenu().getBlockEntity();
        if (be != null && !be.isAutoMode()) {
            VoltageTier current = be.getManualTier();
            VoltageTier prev = current.prev();
            if (prev != current && prev != VoltageTier.ELV) {
                getMenu().setManualTier(prev); // local prediction
                clickButton(50);
            }
        }
    }

    @Override
    protected void onTierUp() {
        CreativeConsumerBlockEntity be = getMenu().getBlockEntity();
        if (be != null && !be.isAutoMode()) {
            VoltageTier current = be.getManualTier();
            VoltageTier next = current.next();
            if (next != current) {
                getMenu().setManualTier(next); // local prediction
                clickButton(51);
            }
        }
    }
}
