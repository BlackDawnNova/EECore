package com.endlessepoch.core.screen;

import com.endlessepoch.core.api.machine.MachineProfile;
import com.endlessepoch.core.api.machine.MachineProfileRegistry;
import com.endlessepoch.core.menu.MachineMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;

import java.util.List;

public class MachineScreen<T extends AbstractContainerMenu> extends AbstractContainerScreen<T> {

    private static final ResourceLocation BG =
            ResourceLocation.parse("eecore:textures/gui/container/machine.png");
    private static final ResourceLocation BUS_BG =
            ResourceLocation.parse("eecore:textures/gui/container/bus.png");
    private static final ResourceLocation PANEL_BG =
            ResourceLocation.parse("eecore:textures/gui/container/button_panel.png");
    private static final int SX = 170;
    private static final int[] SY = {11, 45, 79, 113, 147, 181};
    private static final int IDX_PROFILE = 0, IDX_FORM = 3, IDX_PAUSE = 5;

    private boolean dropdownOpen, hoverProfile, hoverForm, hoverPause;
    private int hoverDropIdx = -1;
    private int pressedBtn = -1;

    public MachineScreen(T menu, Inventory inv, Component title) {
        super(menu, inv, title);
        this.imageWidth = 191; this.imageHeight = 203;
    }

    @Override protected void init() {
        super.init();
        this.titleLabelX = 10; this.titleLabelY = 14; this.inventoryLabelY = -99;
    }

    @Override public void render(GuiGraphics g, int mx, int my, float p) {
        super.render(g, mx, my, p);
        this.renderTooltip(g, mx, my);
        int x = left(), y = top();
        if (hoverProfile) g.renderTooltip(font, Component.translatable("eecore.gui.switch_profile"), mx, my);
        if (hoverForm) g.renderTooltip(font, Component.translatable("eecore.gui.retry_formation"), mx, my);
        if (hoverPause) {
            boolean p2 = menu instanceof MachineMenu mm && mm.isPaused();
            g.renderTooltip(font, Component.translatable(p2 ? "eecore.gui.resume" : "eecore.gui.pause"), mx, my);
        }
        if (dropdownOpen && hoverDropIdx >= 0) {
            var profiles = MachineProfileRegistry.getAll();
            if (hoverDropIdx < profiles.size())
                g.renderTooltip(font, profiles.get(hoverDropIdx).displayName(), mx, my);
        }
    }

    @Override protected void renderBg(GuiGraphics g, float p, int mx, int my) {
        int x = left(), y = top();
        g.blit(BG, x, y, 0, 0, imageWidth, imageHeight, imageWidth, imageHeight);

        hoverProfile = false; hoverForm = false; hoverPause = false; hoverDropIdx = -1;

        // Sidebar slots / 侧边栏槽位
        for (int i = 0; i < 6; i++) {
            int bx = x + SX, by = y + SY[i];
            ScreenUtil.drawSlot(g, bx, by);

            if (i == IDX_PROFILE) {
                drawButton(g, bx + 1, by + 1, "▾", 0xFF_6699CC, overBtn(mx, my, bx + 1, by + 1, 14, 14), pressedBtn == IDX_PROFILE);
                if (overBtn(mx, my, bx + 1, by + 1, 14, 14)) hoverProfile = true;
            } else if (i == IDX_FORM) {
                drawButton(g, bx + 1, by + 1, "⟳", 0xFF_CCAA33, overBtn(mx, my, bx + 1, by + 1, 14, 14), pressedBtn == IDX_FORM);
                if (overBtn(mx, my, bx + 1, by + 1, 14, 14)) hoverForm = true;
            } else if (i == IDX_PAUSE) {
                boolean paused = menu instanceof MachineMenu mm && mm.isPaused();
                String icon = paused ? "▶" : "⏸";
                int color = paused ? 0xFF_44CC44 : 0xFF_CC4444;
                drawButton(g, bx + 1, by + 1, icon, color, overBtn(mx, my, bx + 1, by + 1, 14, 14), pressedBtn == IDX_PAUSE);
                if (overBtn(mx, my, bx + 1, by + 1, 14, 14)) hoverPause = true;
            }
        }

        // Status + recipe + progress / 状态+配方+进度
        if (menu instanceof MachineMenu mm) {
            int sx = x + 10, py = y + 30;
            boolean paused2 = mm.isPaused();
            boolean blocked = mm.isOutputBlocked();
            boolean running = mm.hasWork() && !paused2 && !blocked;
            String key;
            int color;
            if (blocked) { key = "eecore.gui.status.output_full"; color = 0xFF_FFAA00; }
            else if (running) { key = "eecore.gui.status.working"; color = 0xFF_55FF55; }
            else if (paused2) { key = "eecore.gui.status.paused"; color = 0xFF_FF5555; }
            else { key = "eecore.gui.status.idle"; color = 0xFF_888888; }
            g.drawString(font, Component.translatable(key).getString(), sx, py, color);

            if (mm.hasWork()) {
                int itemId = mm.getProcessingItemId();
                if (itemId > 0) {
                    var item = net.minecraft.core.registries.BuiltInRegistries.ITEM.byId(itemId);
                    g.renderItem(item.getDefaultInstance(), sx, py + 14);
                    g.drawString(font, item.getDescription().getString(), sx + 18, py + 18, 0xFF_CCCCCC);
                }
                float frac = (float) mm.getProgress() / mm.getMaxProgress();
                int total = 50, filled = Math.max(1, (int)(total * frac));
                String bar = "|".repeat(filled);
                g.drawString(font, bar, sx, py + 36, 0xFF_44CC44);
                String empty = "|".repeat(total - filled);
                if (!empty.isEmpty())
                    g.drawString(font, empty, sx + font.width(bar), py + 36, 0xFF_444444);
            }
        }

        // Player inventory / 玩家背包
        for (int r = 0; r < 3; r++)
            for (int c = 0; c < 9; c++)
                ScreenUtil.drawSlot(g, x + 8 + c * 18, y + 124 + r * 18);
        for (int c = 0; c < 9; c++)
            ScreenUtil.drawSlot(g, x + 8 + c * 18, y + 182);

        // Dropdown / 下拉菜单
        if (dropdownOpen) drawDropdown(g, x, y, mx, my);
    }

    private void drawDropdown(GuiGraphics g, int gx, int gy, int mx, int my) {
        List<MachineProfile> profiles = MachineProfileRegistry.getAll();
        if (!(menu instanceof MachineMenu mm)) return;
        int curIdx = Math.min(mm.getProfileIndex(), profiles.size() - 1);
        int dx = gx + SX + 2, dy = gy + SY[IDX_PROFILE] + 15;
        int itemH = 18, w = 112, pad = 5;
        int innerW = w - pad * 2, innerH = profiles.size() * itemH + pad * 2;
        int h = innerH + pad * 2;

        g.fill(dx, dy, dx + w, dy + h, 0xFF_1A1A1A);
        g.blit(BUS_BG, dx, dy, 0, 0, w, h, w, h);
        g.blit(PANEL_BG, dx + pad, dy + pad, 0, 0, innerW, innerH, innerW, innerH);

        for (int i = 0; i < profiles.size(); i++) {
            var prof = profiles.get(i);
            int iy = dy + pad + i * itemH;
            boolean hover = mx >= dx && mx < dx + w && my >= iy && my < iy + itemH;
            boolean active = (i == curIdx);

            if (active) g.fill(dx + pad, iy, dx + w - pad, iy + itemH, 0x44_FFFFFF);
            else if (hover) g.fill(dx + pad, iy, dx + w - pad, iy + itemH, 0x22_FFFFFF);

            g.renderItem(prof.iconItem().getDefaultInstance(), dx + pad + 2, iy + 1);
            g.drawString(font, prof.displayName().getString(), dx + pad + 20, iy + 5,
                    active ? 0xFF_FFCC44 : (hover ? 0xFFFFFFFF : 0xFF_AAAAAA));
            if (hover) hoverDropIdx = i;
        }
    }

    @Override public boolean mouseClicked(double mx, double my, int btn) {
        if (btn == 0) {
            int x = left(), y = top();
            // Profile button / 机器切换按钮
            if (overBtn((int)mx, (int)my, x + SX + 1, y + SY[IDX_PROFILE] + 1, 14, 14)) {
                pressedBtn = IDX_PROFILE;
                dropdownOpen = !dropdownOpen;
                return true;
            }
            // Dropdown items / 下拉条目
            if (dropdownOpen) {
                var profiles = MachineProfileRegistry.getAll();
                int dx = x + SX + 2, dy = y + SY[IDX_PROFILE] + 15;
                int itemH = 18, w = 112, pad = 5, innerH = profiles.size() * itemH + pad * 2;
                int h = innerH + pad * 2;
                if (mx >= dx && mx < dx + w && my >= dy && my < dy + h) {
                    int idx = (int)(my - dy - pad) / itemH;
                    if (idx >= 0 && idx < profiles.size()) {
                        sendClick(idx < 0 ? 0 : 3 + idx);
                        dropdownOpen = false;
                        return true;
                    }
                }
            }
            dropdownOpen = false;
            // Other buttons / 其他按钮
            if (overBtn((int)mx, (int)my, x + SX + 1, y + SY[IDX_FORM] + 1, 14, 14)) { pressedBtn = IDX_FORM; sendClick(1); return true; }
            if (overBtn((int)mx, (int)my, x + SX + 1, y + SY[IDX_PAUSE] + 1, 14, 14)) { pressedBtn = IDX_PAUSE; sendClick(0); return true; }
        }
        return super.mouseClicked(mx, my, btn);
    }

    @Override public boolean mouseReleased(double mx, double my, int btn) {
        pressedBtn = -1;
        return super.mouseReleased(mx, my, btn);
    }

    private static final int BTN_BG = 0xFF_666666;
    private static final int BTN_TOP = 0xFF_888888;
    private static final int BTN_BOT = 0xFF_444444;

    private void drawButton(GuiGraphics g, int bx, int by, String icon, int color, boolean hover, boolean pressed) {
        g.fill(bx, by, bx + 14, by + 14, pressed ? BTN_BOT : BTN_BG);
        g.fill(bx, by, bx + 14, by + 1, pressed ? BTN_BG : BTN_TOP);
        g.fill(bx, by, bx + 1, by + 14, pressed ? BTN_BG : BTN_TOP);
        g.fill(bx, by + 13, bx + 14, by + 14, pressed ? BTN_TOP : BTN_BOT);
        g.fill(bx + 13, by, bx + 14, by + 14, pressed ? BTN_TOP : BTN_BOT);
        g.drawCenteredString(font, icon, bx + 7, by + (pressed ? 3 : 2), color);
        if (hover && !pressed) g.fill(bx + 1, by + 1, bx + 13, by + 13, 0x33FFFFFF);
    }

    private boolean overBtn(int mx, int my, int bx, int by, int bw, int bh) {
        return mx >= bx && mx < bx + bw && my >= by && my < by + bh;
    }

    private int left() { return (width - imageWidth) / 2; }
    private int top() { return (height - imageHeight) / 2; }

    private void sendClick(int id) {
        if (minecraft != null && minecraft.gameMode != null) {
            minecraft.gameMode.handleInventoryButtonClick(menu.containerId, id);
            minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
        }
    }
}
