package com.endlessepoch.core.screen.creative;

import com.endlessepoch.core.gui.CyberButton;
import com.endlessepoch.core.api.energy.OmegaValue;
import com.endlessepoch.core.api.tier.VoltageTier;
import com.endlessepoch.core.blockentity.creative.CreativeGeneratorBlockEntity;
import com.endlessepoch.core.menu.creative.CreativeGeneratorMenu;
import com.endlessepoch.core.screen.CreativeMachineScreen;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

import java.math.BigInteger;

/**
 * 创造模式发电机的 GUI 屏幕。
 * Creative generator GUI screen.
 * <p>
 * 提供输出切换、电压档位调节及安培数增减的交互控件。
 * Provides interactive controls for output toggling, tier adjustment, and amperage increment/decrement.
 */
public class CreativeGeneratorScreen extends CreativeMachineScreen<CreativeGeneratorMenu> {

    private CyberButton ampBtn;

    public CreativeGeneratorScreen(CreativeGeneratorMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
    }

    @Override
    protected void init() {
        super.init();
        int cx = (this.width - this.imageWidth) / 2;
        int cy = (this.height - this.imageHeight) / 2;
        ampBtn = new CyberButton(
                cx + 80, cy + BUTTON_Y + 22, 18, 14,
                () -> {
                    CreativeGeneratorBlockEntity b = getMenu().getBlockEntity();
                    return b != null ? b.getAmperage() + "A" : "1A";
                },
                () -> {
                    if (this.minecraft != null && this.minecraft.gameMode != null)
                        this.minecraft.gameMode.handleInventoryButtonClick(this.menu.containerId, 100);
                },
                () -> {
                    if (this.minecraft != null && this.minecraft.gameMode != null)
                        this.minecraft.gameMode.handleInventoryButtonClick(this.menu.containerId, 101);
                }
        );
        ampBtn.visible = false;
        cyberButtons.add(ampBtn);
    }

    @Override
    protected void createMainButtons(int startX, int cy) {
        CreativeGeneratorBlockEntity be = getMenu().getBlockEntity();
        if (be == null) return;

        cyberButtons.add(new CyberButton(
                startX, cy + BUTTON_Y, BUTTON_W, BUTTON_H,
                () -> {
                    CreativeGeneratorBlockEntity b = getMenu().getBlockEntity();
                    return b != null && b.isOutputEnabled()
                            ? Component.translatable("eecore.gui.generator.running").getString()
                            : Component.translatable("eecore.gui.generator.paused").getString();
                },
                () -> {
                    CreativeGeneratorBlockEntity b = getMenu().getBlockEntity();
                    if (b != null) b.toggleOutput();
                    if (this.minecraft != null && this.minecraft.gameMode != null)
                        this.minecraft.gameMode.handleInventoryButtonClick(this.menu.containerId, 0);
                }
        ));

        cyberButtons.add(new CyberButton(
                startX + BUTTON_W + BUTTON_GAP, cy + BUTTON_Y, BUTTON_W, BUTTON_H,
                Component.translatable("eecore.gui.generator.reset").getString(),
                () -> {
                    CreativeGeneratorBlockEntity b = getMenu().getBlockEntity();
                    if (b != null) b.resetToLV();
                    if (this.minecraft != null && this.minecraft.gameMode != null)
                        this.minecraft.gameMode.handleInventoryButtonClick(this.menu.containerId, 1);
                }
        ));

        cyberButtons.add(new CyberButton(
                startX + 2 * (BUTTON_W + BUTTON_GAP), cy + BUTTON_Y, BUTTON_W, BUTTON_H,
                () -> {
                    CreativeGeneratorBlockEntity b = getMenu().getBlockEntity();
                    return b != null && b.isLogToChat() ? "显示" : "隐藏";
                },
                () -> {
                    CreativeGeneratorBlockEntity b = getMenu().getBlockEntity();
                    if (b != null) b.setLogToChat(!b.isLogToChat());
                    if (this.minecraft != null && this.minecraft.gameMode != null)
                        this.minecraft.gameMode.handleInventoryButtonClick(this.menu.containerId, 2);
                }
        ));
    }

    @Override
    protected void renderMainInfo(GuiGraphics g) {
        CreativeGeneratorBlockEntity be = getMenu().getBlockEntity();
        if (be == null) return;

        VoltageTier tier = be.getSelectedTier();
        String tierShort = tier != null ? tier.getShortName() : "LV";
        BigInteger output = be.getOutputPerTick();
        String ampStr = be.getAmperage().toString();

        Component line1 = Component.translatable("eecore.gui.generator.tick_output",
                OmegaValue.of(output).toDisplayString(), tierShort, ampStr);
        int tw1 = font.width(line1);
        int tx1 = leftPos + (imageWidth - tw1) / 2;
        g.drawString(font, line1, tx1, topPos + 48, style.textSecondary, false);
    }

    @Override
    protected void renderSettingsContent(GuiGraphics g) {
        CreativeGeneratorBlockEntity be = getMenu().getBlockEntity();
        if (be == null) return;

        String tierText = be.getSelectedTier().getShortName();
        int tw = font.width(tierText);
        int tx = leftPos + (imageWidth - tw) / 2;
        int ty = topPos + BUTTON_Y + (BUTTON_H - 8) / 2;
        g.drawString(font, Component.literal(tierText), tx, ty, style.textPrimary, false);
    }

    @Override
    protected void updateVisibility() {
        super.updateVisibility();
        if (ampBtn != null) ampBtn.visible = showSettings;
    }

    @Override
    protected Component getTitleText(boolean showSettings) {
        CreativeGeneratorBlockEntity be = getMenu().getBlockEntity();
        String tierShort = be != null ? be.getSelectedTier().getShortName() : "LV";
        if (showSettings) return Component.translatable("eecore.gui.settings.title");
        return Component.translatable("eecore.gui.generator.title", tierShort);
    }

    @Override
    protected boolean canShowArrows() {
        return true;
    }

    @Override
    protected void onTierDown() {
        CreativeGeneratorBlockEntity be = getMenu().getBlockEntity();
        if (be != null) {
            VoltageTier current = be.getSelectedTier();
            VoltageTier prev = current.prev();
            if (prev != current && prev != VoltageTier.ELV) {
                getMenu().setTier(prev);
                clickButton(50);
            }
        }
    }

    @Override
    protected void onTierUp() {
        CreativeGeneratorBlockEntity be = getMenu().getBlockEntity();
        if (be != null) {
            VoltageTier current = be.getSelectedTier();
            VoltageTier next = current.next();
            if (next != current) {
                getMenu().setTier(next);
                clickButton(51);
            }
        }
    }

    private void clickButton(int id) {
        if (this.minecraft != null && this.minecraft.gameMode != null)
            this.minecraft.gameMode.handleInventoryButtonClick(this.menu.containerId, id);
    }
}
