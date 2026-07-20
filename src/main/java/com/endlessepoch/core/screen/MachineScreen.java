package com.endlessepoch.core.screen;

import com.endlessepoch.core.api.machine.MachineType;
import com.endlessepoch.core.api.machine.MachineTypeRegistry;
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
    // Sidebar button sprites — PNGs under textures/gui/sprites/ / 侧边栏按钮贴图——PNG 放入 sprites/
    private static final ResourceLocation
            TEX_H    = ResourceLocation.fromNamespaceAndPath("eecore", "btn_h"),
            TEX_H_ON = ResourceLocation.fromNamespaceAndPath("eecore", "btn_h_on"),
            TEX_O    = ResourceLocation.fromNamespaceAndPath("eecore", "btn_o"),
            TEX_O_ON = ResourceLocation.fromNamespaceAndPath("eecore", "btn_o_on"),
            TEX_PAUSE = ResourceLocation.fromNamespaceAndPath("eecore", "btn_pause"),
            TEX_PLAY  = ResourceLocation.fromNamespaceAndPath("eecore", "btn_play");
    private static final int SX = 170;
    private static final int[] SY = {11, 45, 79, 113, 147, 181};
    private static final int IDX_PROFILE = 0, IDX_OC = 1, IDX_FX = 2, IDX_HEAT = 4, IDX_PAUSE = 5;

    private boolean dropdownOpen, hoverProfile, hoverPause, hoverHeat, hoverOc, hoverFx;
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

    @Override protected void renderLabels(GuiGraphics g, int mx, int my) {
        boolean cn = "zh_cn".equals(
                net.minecraft.client.Minecraft.getInstance().getLanguageManager().getSelected());
        String name = menu instanceof MachineMenu mm
                ? (cn ? mm.getNameZh() : mm.getNameEn()) : "";
        int tierIdx = menu instanceof MachineMenu mm ? mm.getEffectiveTier() : 0;
        String title = name.isEmpty() ? this.title.getString() : name;
        int maxW = SX - 22;
        if (font.width(title) > maxW)
            title = font.plainSubstrByWidth(title, maxW - font.width("…")) + "…";
        int color = parseHex(
                com.endlessepoch.core.api.tier.VoltageTier.values()[tierIdx].getHexColor());
        g.drawString(font, title, titleLabelX, titleLabelY, color);
        g.drawString(font, this.playerInventoryTitle, inventoryLabelX, inventoryLabelY, 0xFF_404040);
    }

    private static int parseHex(String hex) {
        try { return (int) Long.parseLong(hex.substring(1), 16) | 0xFF000000; }
        catch (Exception e) { return 0xFF_888888; }
    }

    @Override public void render(GuiGraphics g, int mx, int my, float p) {
        super.render(g, mx, my, p);
        this.renderTooltip(g, mx, my);
        if (hoverProfile) g.renderTooltip(font, Component.translatable("eecore.gui.switch_profile"), mx, my);
        if (hoverPause) {
            boolean p2 = menu instanceof MachineMenu mm && mm.isPaused();
            g.renderTooltip(font, Component.translatable(p2 ? "eecore.gui.resume" : "eecore.gui.pause"), mx, my);
        }
        if (hoverHeat) {
            boolean on = menu instanceof MachineMenu mm && mm.isHeatEnabled();
            g.renderTooltip(font, Component.translatable(on ? "eecore.gui.heat_on" : "eecore.gui.heat_off"), mx, my);
        }
        if (hoverOc) {
            boolean on = menu instanceof MachineMenu mm && mm.isOverclockEnabled();
            g.renderTooltip(font, Component.translatable(on ? "eecore.gui.oc_on" : "eecore.gui.oc_off"), mx, my);
        }
        if (hoverFx) {
            boolean on = menu instanceof MachineMenu mm && mm.isEffectEnabled();
            g.renderTooltip(font, Component.translatable(on ? "eecore.gui.fx_on" : "eecore.gui.fx_off"), mx, my);
        }
        if (dropdownOpen) {
            drawDropdown(g, left(), top(), mx, my);
            if (hoverDropIdx >= 0 && menu instanceof MachineMenu mm) {
                var types = mm.getSupportedProfiles();
                if (hoverDropIdx < types.size())
                    g.renderTooltip(font, types.get(hoverDropIdx).displayName(), mx, my);
            }
        }
    }

    @Override protected void renderBg(GuiGraphics g, float p, int mx, int my) {
        int x = left(), y = top();
        g.blit(BG, x, y, 0, 0, imageWidth, imageHeight, imageWidth, imageHeight);

        hoverProfile = false; hoverPause = false;
        hoverHeat = false; hoverOc = false; hoverFx = false; hoverDropIdx = -1;

        // Sidebar slots / 侧边栏槽位
        for (int i = 0; i < SY.length; i++) {
            int bx = x + SX, by = y + SY[i];
            ScreenUtil.drawSlot(g, bx, by);

            if (i == IDX_PROFILE && menu instanceof MachineMenu mm && mm.hasMultipleProfiles()) {
                drawButton(g, bx + 1, by + 1, "▾", 0xFF_6699CC, overBtn(mx, my, bx + 1, by + 1, 14, 14), pressedBtn == IDX_PROFILE);
                if (overBtn(mx, my, bx + 1, by + 1, 14, 14)) hoverProfile = true;
            } else if (i == IDX_PAUSE) {
                boolean paused = menu instanceof MachineMenu mm && mm.isPaused();
                g.blitSprite(paused ? TEX_PLAY : TEX_PAUSE, bx + 1, by + 1, 12, 12);
                if (overBtn(mx, my, bx + 1, by + 1, 14, 14)) hoverPause = true;
            } else if (i == IDX_HEAT) {
                boolean on = menu instanceof MachineMenu mm && mm.isHeatEnabled();
                g.blitSprite(on ? TEX_H_ON : TEX_H, bx + 1, by + 1, 12, 12);
                if (overBtn(mx, my, bx + 1, by + 1, 14, 14)) hoverHeat = true;
            } else if (i == IDX_OC) {
                boolean on = menu instanceof MachineMenu mm && mm.isOverclockEnabled();
                g.blitSprite(on ? TEX_O_ON : TEX_O, bx + 1, by + 1, 12, 12);
                if (overBtn(mx, my, bx + 1, by + 1, 14, 14)) hoverOc = true;
            } else if (i == IDX_FX && menu instanceof MachineMenu mm && mm.hasEffect()) {
                boolean on = mm.isEffectEnabled();
                drawButton(g, bx + 1, by + 1, "FX", on ? 0xFF_FFCC00 : 0xFF_888888, overBtn(mx, my, bx + 1, by + 1, 14, 14), pressedBtn == IDX_FX);
                if (overBtn(mx, my, bx + 1, by + 1, 14, 14)) hoverFx = true;
            }
        }

        // Status + recipe + progress / 状态+配方+进度
        if (menu instanceof MachineMenu mm) {
            int sx = x + 10, py = y + 30;
            boolean paused2 = mm.isPaused();
            boolean blocked = mm.isOutputBlocked();
            boolean running = mm.hasWork() && !paused2 && !blocked;
            int vTier = mm.getVoltageBlockedTier();
            String statusText; int statusColor;
            if (blocked) {
                statusText = Component.translatable("eecore.gui.status.output_full").getString();
                statusColor = 0xFF_FFAA00;
            } else if (running) {
                statusText = Component.translatable("eecore.gui.status.working").getString();
                statusColor = 0xFF_55FF55;
            } else if (paused2) {
                statusText = Component.translatable("eecore.gui.status.paused").getString();
                statusColor = 0xFF_FF5555;
            } else if (vTier >= 0) {
                var tiers = com.endlessepoch.core.api.tier.VoltageTier.values();
                String tierName = vTier < tiers.length ? tiers[vTier].name() : "?";
                statusText = Component.translatable("eecore.gui.status.voltage_low", tierName).getString();
                statusColor = 0xFF_FF6644;
            } else if (mm.isEnergyBlocked()) {
                statusText = Component.translatable("eecore.gui.status.energy_low").getString();
                statusColor = 0xFF_FFCC00;
            } else {
                statusText = Component.translatable("eecore.gui.status.idle").getString();
                statusColor = 0xFF_888888;
            }
            int statusEndX = sx + font.width(statusText) + 4;
            g.drawString(font, statusText, sx, py, statusColor);
            // Speed multiplier after status / 倍率接在状态文字后面
            int mul = mm.getSpeedMultiplier();
            if (mul > 100 && (mm.isOverclockEnabled() || mm.isHeatEnabled())) {
                String mulStr = String.format("⚡ %.1fx", mul / 100.0f);
                g.drawString(font, mulStr, statusEndX, py, 0xFF_44CCFF);
            }

            if (mm.hasWork()) {
                int itemId = mm.getProcessingItemId();
                if (itemId > 0) {
                    var item = net.minecraft.core.registries.BuiltInRegistries.ITEM.byId(itemId);
                    g.renderItem(item.getDefaultInstance(), sx, py + 14);
                    g.drawString(font, item.getDescription().getString(), sx + 18, py + 18, 0xFF_CCCCCC);
                }
                // Progress bar / 进度条
                float frac = (float) mm.getProgress() / mm.getMaxProgress();
                int total = 50, filled = Math.max(1, (int)(total * frac));
                String bar = "|".repeat(filled);
                g.drawString(font, bar, sx, py + 36, 0xFF_44CC44);
                String empty = "|".repeat(total - filled);
                if (!empty.isEmpty())
                    g.drawString(font, empty, sx + font.width(bar), py + 36, 0xFF_444444);

                // Batch: countdown is meaningless — show effective parallel instead
                // 批处理：倒计时无意义——改显示有效并行
                if (mm.isBatchActive()) {
                    int eff = mm.getEffectiveParallel(), hw = mm.getHardwareParallel();
                    int pc = eff < hw ? 0xFF_FFAA00 : 0xFF_44CC44;
                    g.drawString(font, Component.translatable("eecore.gui.parallel.display", eff, hw).getString(),
                            sx, py + 48, pc);
                } else {
                    int remaining = mm.getMaxProgress() - mm.getProgress();
                    float secs = remaining / 20.0f;
                    String countdown = String.format("⏱ %.1fs", secs);
                    g.drawString(font, countdown, sx, py + 48, 0xFF_FFCC44);
                }
                // Heat bar — only when heat active / 热量条——仅热量开启时显示
                if (mm.isHeatEnabled())
                    drawHeatBar(g, sx, py + 62, mm);
            }
        }

        // Player inventory / 玩家背包
        for (int r = 0; r < 3; r++)
            for (int c = 0; c < 9; c++)
                ScreenUtil.drawSlot(g, x + 8 + c * 18, y + 124 + r * 18);
        for (int c = 0; c < 9; c++)
            ScreenUtil.drawSlot(g, x + 8 + c * 18, y + 182);

    }

    private void drawDropdown(GuiGraphics g, int gx, int gy, int mx, int my) {
        if (!(menu instanceof MachineMenu mm)) return;
        var types = mm.getSupportedProfiles();
        int curIdx = Math.min(mm.getProfileIndex(), types.size() - 1);
        int dx = gx + SX - 112 + 12, dy = gy + SY[IDX_PROFILE] + 15;
        int itemH = 18, w = 112, pad = 5;
        int innerW = w - pad * 2, innerH = types.size() * itemH + pad * 2;
        int h = innerH + pad * 2;

        g.pose().pushPose(); g.pose().translate(0, 0, 300);
        g.fill(dx, dy, dx + w, dy + h, 0xFF_1A1A1A);
        g.blit(BUS_BG, dx, dy, 0, 0, w, h, w, h);
        g.blit(PANEL_BG, dx + pad, dy + pad, 0, 0, innerW, innerH, innerW, innerH);

        for (int i = 0; i < types.size(); i++) {
            var type = types.get(i);
            int iy = dy + pad + i * itemH;
            boolean hover = mx >= dx && mx < dx + w && my >= iy && my < iy + itemH;
            boolean active = (i == curIdx);

            if (active) g.fill(dx + pad, iy, dx + w - pad, iy + itemH, 0x44_FFFFFF);
            else if (hover) g.fill(dx + pad, iy, dx + w - pad, iy + itemH, 0x22_FFFFFF);

            g.renderItem(type.iconItem().getDefaultInstance(), dx + pad + 2, iy + 1);
            g.drawString(font, type.displayName().getString(), dx + pad + 20, iy + 5,
                    active ? 0xFF_FFCC44 : (hover ? 0xFFFFFFFF : 0xFF_AAAAAA));
            if (hover) hoverDropIdx = i;
        }
        g.pose().popPose();
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
            if (dropdownOpen && menu instanceof MachineMenu mm) {
                var types = mm.getSupportedProfiles();
                int dx = x + SX - 112 + 12, dy = y + SY[IDX_PROFILE] + 15;
                int itemH = 18, w = 112, pad = 5, innerH = types.size() * itemH + pad * 2;
                int h = innerH + pad * 2;
                if (mx >= dx && mx < dx + w && my >= dy && my < dy + h) {
                    int idx = (int)(my - dy - pad) / itemH;
                    if (idx >= 0 && idx < types.size()) {
                        sendClick(idx < 0 ? 0 : 3 + idx);
                        dropdownOpen = false;
                        return true;
                    }
                }
            }
            dropdownOpen = false;
            // Other buttons / 其他按钮
            if (overBtn((int)mx, (int)my, x + SX + 1, y + SY[IDX_PAUSE] + 1, 14, 14)) { pressedBtn = IDX_PAUSE; sendClick(0); return true; }
            if (overBtn((int)mx, (int)my, x + SX + 1, y + SY[IDX_OC] + 1, 14, 14)) { pressedBtn = IDX_OC; sendClick(2); return true; }
            if (overBtn((int)mx, (int)my, x + SX + 1, y + SY[IDX_FX] + 1, 14, 14)) { pressedBtn = IDX_FX; sendClick(12); return true; }
            if (overBtn((int)mx, (int)my, x + SX + 1, y + SY[IDX_HEAT] + 1, 14, 14)) { pressedBtn = IDX_HEAT; sendClick(11); return true; }
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

    /** Draw heat bar: 🔥 [████████░░░░] 65%. Color shifts blue→green→orange→red. / 热量条 */
    private void drawHeatBar(GuiGraphics g, int sx, int sy, MachineMenu mm) {
        int heat = mm.getHeatMille(); // 0-1000
        if (heat <= 0) return;
        String label = "🔥 " + (heat / 10) + "%";
        g.drawString(font, label, sx, sy, 0xFF_FFAA00);

        int barX = sx + font.width(label) + 4;
        int barW = 60, barH = 10;
        int filled = Math.max(1, barW * heat / 1000);

        // Color gradient: cold (0x4488FF blue) → warm (0x44CC44 green) → hot (0xFF8800 orange) → max (0xFF2222 red)
        int color;
        if (heat < 333) {
            float t = heat / 333f;
            color = lerpColor(0xFF_4488FF, 0xFF_44CC44, t); // blue → green
        } else if (heat < 666) {
            float t = (heat - 333) / 333f;
            color = lerpColor(0xFF_44CC44, 0xFF_FF8800, t); // green → orange
        } else {
            float t = (heat - 666) / 334f;
            color = lerpColor(0xFF_FF8800, 0xFF_FF2222, t); // orange → red
        }
        g.fill(barX, sy, barX + filled, sy + barH, color);
        g.fill(barX, sy, barX + barW, sy + barH, 0x44_FFFFFF); // outline / 外框
        g.fill(barX + filled, sy, barX + barW, sy + barH, 0xFF_333333); // empty / 空区域
    }

    /** Linear interpolate two ARGB colors / 两色线性插值 */
    private static int lerpColor(int a, int b, float t) {
        int ar = (a >> 16) & 0xFF, ag = (a >> 8) & 0xFF, ab = a & 0xFF;
        int br = (b >> 16) & 0xFF, bg = (b >> 8) & 0xFF, bb = b & 0xFF;
        int rr = (int)(ar + (br - ar) * t);
        int rg = (int)(ag + (bg - ag) * t);
        int rb = (int)(ab + (bb - ab) * t);
        return 0xFF_000000 | (rr << 16) | (rg << 8) | rb;
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
