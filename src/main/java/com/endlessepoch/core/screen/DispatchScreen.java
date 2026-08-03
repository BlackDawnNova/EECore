package com.endlessepoch.core.screen;

import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEFluidKey;
import com.endlessepoch.core.menu.DispatchMenu;
import com.endlessepoch.core.network.GridIncrementalUpdatePacket;
import com.endlessepoch.core.nova.client.PinyinUtil;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class DispatchScreen extends AbstractContainerScreen<DispatchMenu> {

    static final int W = 354;
    private static final int HEADER_H = 17, ROW_H = 18, COLS = 18;
    private static final int GRID_X = 7, GRID_Y = 18;
    static final int[] DENSITIES = {3, 6, 9, 12};
    private static final int PANEL_H = 130, INV_AREA = 97;
    static double savedMouseX = -1, savedMouseY = -1;
    int densityIdx;
    int H = com.endlessepoch.core.menu.DispatchMenu.imgH(3);
    int rows = DENSITIES[0];

    int gridEnd() { return GRID_Y + rows * ROW_H; }
    int panelY() { return gridEnd(); }
    int invY() { return H - INV_AREA; }
    static final int DIV_X = 174, G3_X = 11, G3_S = 16;

    static final int C_HDR = 0xFF_1A1A1A, C_ROW = 0xFF_1E1E1E, C_ALT = 0xFF_2B2B2B;
    static final int C_BOT = 0xFF_161616, C_PNL = 0xFF_1C1C1C, C_PNL_H = 0xFF_252525;
    static final int C_SL = 0xFF_252525, C_BD = 0xFF_3A3A3A;
    static final int C_SBG = 0xFF_333333, C_SFG = 0xFF_888888;
    static final int C_TAB = 0xFF_2A2A2A, C_TH = 0xFF_444444, C_TA = 0xFF_555555;
    static final int C_T = 0xFF_CCCCCC, C_TD = 0xFF_888888, C_BTN = 0xFF_3A5A8A;
    static final int C_G = 0xFF_55CC55, C_R = 0xFF_CC5555;
    static final int C_HL = 0xFF_FFCC00;
    static final int C_TL = 0xFF_B0B0B0;

    private static final ResourceLocation BG_TEX = ResourceLocation.parse("eecore:textures/gui/dispatch/dispatch_ui.png");
    private static final ResourceLocation PANEL_LEFT = ResourceLocation.parse("eecore:textures/gui/dispatch/panel_left.png");
    private static final ResourceLocation PANEL_RIGHT = ResourceLocation.parse("eecore:textures/gui/dispatch/panel_right.png");
    static final ResourceLocation SLOT_TEX = ResourceLocation.parse("eecore:textures/gui/dispatch/slot.png");
    static final ResourceLocation SPRITES = ResourceLocation.parse("eecore:textures/gui/dispatch/dispatch_sprites.png");
    static final ResourceLocation INV_BG = ResourceLocation.parse("eecore:textures/gui/dispatch/inv_bg.png");

    record GridEntry(appeng.api.stacks.AEKey key, long count) {}
    private final ClientStorageView gridView = new ClientStorageView();
    private final List<GridEntry> allEntries = new ArrayList<>();
    final List<GridEntry> filtered = new ArrayList<>();
    GridEntry hoveredEntry;
    boolean trashMode;
    appeng.api.stacks.AEKey trashPendingKey;
    int scrollOffset, leftPanel;
    int sortMode;
    boolean sortAsc = false;
    int displayMode, encodeMode, machineScroll, storageScroll, procScroll, rightMode;
    int mulPressed = -1, tbPressed = -1, encPressed = -1;
    boolean itemReplace, fluidReplace, splitMerge;
    private boolean dragging;

    net.minecraft.client.Minecraft mc() { return minecraft; }
    net.minecraft.client.gui.Font font() { return font; }
    DispatchMenu menu() { return menu; }
    int lPos() { return leftPos; } int tPos() { return topPos; }
    void clickSlot(net.minecraft.world.inventory.Slot s, int idx, int btn, net.minecraft.world.inventory.ClickType t) { slotClicked(s, idx, btn, t); }

    DispatchToolbar toolbar = new DispatchToolbar(this);
    DispatchLeftPanel leftPanelComp = new DispatchLeftPanel(this);
    DispatchRightPanel rightPanelComp = new DispatchRightPanel(this);
    DispatchSearch searchComp = new DispatchSearch(this);
    EncodePanel barA;
    InventoryPanel barB;
    MainPanel barC;
    WarehousePanel barD;

    public DispatchScreen(DispatchMenu menu, net.minecraft.world.entity.player.Inventory inv, Component title) {
        super(menu, inv, title);
    }

    @Override protected void init() {
        rows = menu.getGridRows();
        for (int i = 0; i < DENSITIES.length; i++) if (DENSITIES[i] == rows) { densityIdx = i; break; }
        H = com.endlessepoch.core.menu.DispatchMenu.imgH(rows);
        imageWidth = W; imageHeight = H; super.init();
        if (savedMouseX >= 0) {
            double sx = savedMouseX, sy = savedMouseY;
            savedMouseX = -1;
            minecraft.execute(() -> org.lwjgl.glfw.GLFW.glfwSetCursorPos(minecraft.getWindow().getWindow(), sx, sy));
        }
        addRenderableWidget(searchComp.init(leftPos, topPos));
        onSearch("");
    }

    @Override protected void renderBg(GuiGraphics g, float pt, int mx, int my) {
        if (splitMerge) return;
        int x = leftPos, y = topPos;
        g.blit(BG_TEX, x, y, 0, 0, W, 72, 354, 298);
        int extra = rows - 3, btmY = y + gridEnd();
        for (int e = 0; e < extra; e += 3) g.blit(BG_TEX, x, y + 72 + e * 18, 0, 20, W, 54, 354, 298);
        g.blit(BG_TEX, x, btmY, 0, 72, W, H - (btmY - y), 354, 298);
        for (int r = 0; r < rows; r++) {
            int ry = y + GRID_Y + r * ROW_H;
            if (rows == 1) { g.blit(SPRITES, x + GRID_X, ry - 1, 188, 0, 324, 9, 512, 512); g.blit(SPRITES, x + GRID_X, ry + 8, 188, 27, 324, 9, 512, 512); }
            else if (r == 0) g.blit(SPRITES, x + GRID_X, ry - 1, 188, 0, 324, 18, 512, 512);
            else if (r == rows - 1) g.blit(SPRITES, x + GRID_X, ry - 1, 188, 18, 324, 18, 512, 512);
            else g.blit(SPRITES, x + GRID_X, ry - 1, 188, 36, 324, 18, 512, 512);
        }
        int iy = invY(), py = panelY();
        g.blit(PANEL_LEFT, x + 7, y + py + 13, 0, 0, DIV_X - 7, iy - py - 15, 167, 114);
        g.blit(PANEL_RIGHT, x + DIV_X, y + py + 13, 0, 0, W - DIV_X - 2, H - py - 22, 178, 204);
        g.blit(INV_BG, x + 7, y + iy + 13, 0, 0, 162, 76, 162, 76);
        drawScrollbar(g, x, y);
        leftPanelComp.drawBg(g, x, y, mx, my);
        rightPanelComp.drawBg(g, x, y, mx, my);
    }

    @Override public void render(GuiGraphics g, int mx, int my, float pt) {
        if (splitMerge) {
            if (!searchComp.isVisible()) ; else searchComp.setVisible(false);
            if (barA == null) { int cx = width / 2, cy = height / 2;
                barA = new EncodePanel(this, cx - 80, cy - 20);
                barB = new InventoryPanel(this, cx - 80, cy + 6);
                barC = new MainPanel(this, cx + 10, cy - 20);
                barD = new WarehousePanel(this, cx + 10, cy + 6); }
            DraggablePanel[] all = {barA, barB, barC, barD}, collapsed = new DraggablePanel[4], expanded = new DraggablePanel[4];
            int ci = 0, ei = 0;
            for (DraggablePanel b : all) if (b.collapsed) collapsed[ci++] = b; else expanded[ei++] = b;
            for (int i = 0; i < ci; i++) collapsed[i].render(g, font(), mx, my);
            int topHover = -1;
            for (int i = ei - 1; i >= 0; i--)
                if (panelHit(expanded[i], mx, my)) { topHover = i; break; }
            for (int i = 0; i < ei; i++) {
                boolean covered = topHover >= 0 && topHover != i && panelHit(expanded[i], mx, my);
                int hmx = covered ? -1 : mx, hmy = covered ? -1 : my;
                g.pose().pushPose();
                g.pose().translate(0, 0, i * 300);
                expanded[i].render(g, font(), hmx, hmy);
                g.pose().popPose();
            }
            var c = minecraft.player.containerMenu.getCarried();
            if (!c.isEmpty()) { g.pose().pushPose(); g.pose().translate(0, 0, 500); g.renderFakeItem(c, mx - 8, my - 8); g.renderItemDecorations(font, c, mx - 8, my - 8); g.pose().popPose(); }
            return;
        }
        super.render(g, mx, my, pt);
        int x = leftPos, y = topPos;
        if (!searchComp.isVisible()) searchComp.setVisible(true);
        g.drawString(font, menu.getNameZh(), x + 8, y + 5, 0xFF_404040, false);
        // formed indicator light / 成形指示灯
        boolean formed = menu.isFormed();
        int lx = x + 8 + font.width(menu.getNameZh()) + 8 + 290, ly = y + 3;
        g.blit(SPRITES, lx, ly, formed ? 207 : 214, 154, 7, 12, 512, 512);
        if (DispatchUtil.hit(mx, my, lx, ly, 7, 12))
            g.renderTooltip(font, Component.translatable(formed ? "eecore.dispatch.formed" : "eecore.dispatch.unformed"), mx, my);
        toolbar.draw(g, x, y, mx, my);
        toolbar.drawFg(g, x, y, mx, my);
        toolbar.drawTooltips(g, x, y, mx, my);
        searchComp.drawTooltips(g, x, y, mx, my);
        leftPanelComp.drawTooltips(g, x, y, mx, my);
        rightPanelComp.drawTooltips(g, x, y, mx, my);
        rightPanelComp.drawFg(g, x, y, mx, my);
        renderGrid(g, x, y, mx, my);
        if (trashMode) {
            g.blit(SPRITES, (int) mx + 4, (int) my + 4, 360, 78, 10, 11, 512, 512);
            if (trashPendingKey != null) {
                for (int i = 0; i < filtered.size(); i++) {
                    GridEntry e = filtered.get(i);
                    if (e.key().equals(trashPendingKey)) {
                        int pc = i % COLS, pr2 = i / COLS - scrollOffset;
                        if (pr2 >= 0 && pr2 < rows) {
                            int px2 = x + GRID_X + 1 + pc * ROW_H, py2 = y + GRID_Y + pr2 * ROW_H;
                            DispatchUtil.slotSelect(g, px2, py2, 16, 16);
                        }
                        break;
                    }
                }
            }
        }
        if (DispatchUtil.hit(mx, my, x + GRID_X, y + GRID_Y, COLS * ROW_H, rows * ROW_H)) {
            var cc = menu.getCarried();
            if (!cc.isEmpty()) {
                var cc2 = net.neoforged.neoforge.fluids.FluidUtil.getFluidContained(cc);
                if (cc2.isPresent() && !cc2.get().isEmpty()) {
                    String fn = cc2.get().getFluid().getFluidType().getDescription().getString();
                    var tt = new java.util.ArrayList<Component>();
                    tt.add(Component.literal("左键点击: 存入 ").withStyle(s -> s.withColor(C_TD))
                            .append(Component.literal(fn + "桶").withStyle(s -> s.withColor(C_TL))));
                    tt.add(Component.literal("右键点击: 存入 ").withStyle(s -> s.withColor(C_TD))
                            .append(Component.literal(fn).withStyle(s -> s.withColor(C_TL))));
                    g.renderTooltip(font, tt, java.util.Optional.empty(), mx, my);
                }
            }
        }
        g.drawString(font, playerInventoryTitle, x + 8, y + invY() + 1, 0xFF_404040, false);
        int iy = invY();
        for (int row = 0; row < 3; row++)
            for (int col = 0; col < 9; col++)
                DispatchUtil.slotHover(g, minecraft, x + 8 + col * 18, y + iy + 14 + row * 18, 16, 16, mx, my);
        for (int col = 0; col < 9; col++)
            DispatchUtil.slotHover(g, minecraft, x + 8 + col * 18, y + iy + 14 + 58, 16, 16, mx, my);
    }
    @Override protected void renderLabels(GuiGraphics g, int mx, int my) {}

    private void drawScrollbar(GuiGraphics g, int x, int y) {
        int by = y + GRID_Y - 1, bh = rows * ROW_H, bx = x + W - 16;
        if (bh <= 18) { g.blit(SPRITES, bx, by, 180, 0, 7, 9, 512, 512); g.blit(SPRITES, bx, by + 9, 180, 27, 7, 9, 512, 512); }
        else if (bh <= 36) { g.blit(SPRITES, bx, by, 180, 0, 7, 18, 512, 512); g.blit(SPRITES, bx, by + 18, 180, 18, 7, 18, 512, 512); }
        else {
            g.blit(SPRITES, bx, by, 180, 0, 7, 18, 512, 512);
            for (int sy = by + 18; sy < by + bh - 18; sy += 18) { g.blit(SPRITES, bx, sy, 180, 9, 7, 9, 512, 512); g.blit(SPRITES, bx, sy + 9, 180, 18, 7, 9, 512, 512); }
            g.blit(SPRITES, bx, by + bh - 18, 180, 18, 7, 18, 512, 512);
        }
        int total = filtered.size(), maxOff = Math.max(0, (total + COLS - 1) / COLS - rows);
        if (maxOff <= 0) return;
        int hY = by + (int) ((long) (bh - 15) * scrollOffset / maxOff);
        g.blit(SPRITES, bx - 1, hY, 170, 18, 9, 17, 512, 512);
    }

    void renderGrid(GuiGraphics g, int x, int y, int mx, int my) {
        int total = filtered.size(), maxOff = Math.max(0, (total + COLS - 1) / COLS - rows);
        scrollOffset = Math.min(scrollOffset, maxOff);
        for (int r = 0; r < rows; r++) for (int c = 0; c < COLS; c++) {
            int idx = scrollOffset * COLS + r * COLS + c; if (idx >= total) break;
            int sx = x + GRID_X + 1 + c * ROW_H, sy = y + GRID_Y + r * ROW_H;
            GridEntry e = filtered.get(idx);
            if (e.key() instanceof AEItemKey ik) {
                g.renderItem(ik.toStack(1), sx, sy);
                if (e.count() > 1) drawCount(g, font, fmt(e.count()), sx, sy);
            } else if (e.key() instanceof AEFluidKey fk) {
                drawFluidIcon(g, minecraft, fk, sx, sy);
                if (e.count() > 0) drawCount(g, font, fmtFluid(e.count()), sx, sy);
            }
            if (trashMode) DispatchUtil.slotSelectHover(g, sx, sy, 16, 16, mx, my);
            else DispatchUtil.slotHover(g, minecraft, sx, sy, 16, 16, mx, my);
        }
        if (DispatchUtil.hit(mx, my, x + GRID_X, y + GRID_Y, COLS * ROW_H, rows * ROW_H)) {
            int c = (mx - x - GRID_X) / ROW_H, r = (my - y - GRID_Y) / ROW_H;
            if (c >= 0 && c < COLS && r >= 0 && r < rows) {
                int idx = scrollOffset * COLS + r * COLS + c;
                if (idx >= 0 && idx < total) hoveredEntry = filtered.get(idx);
            }
        }
        GridActions.tooltip(g, font, this, x + GRID_X, y + GRID_Y, COLS, rows, ROW_H, mx, my);
    }

    static void drawFluidIcon(GuiGraphics g, net.minecraft.client.Minecraft mc, AEFluidKey key, int x, int y) {
        var fs = key.toStack(1);
        var fluid = fs.getFluid();
        var tx = net.neoforged.neoforge.client.extensions.common.IClientFluidTypeExtensions.of(fluid).getStillTexture(fs);
        if (tx == null) { g.fill(x, y, x + 16, y + 16, 0xFF_3355AA); return; }
        var sp = mc.getTextureAtlas(net.minecraft.client.renderer.texture.TextureAtlas.LOCATION_BLOCKS).apply(tx);
        int tint = net.neoforged.neoforge.client.extensions.common.IClientFluidTypeExtensions.of(fluid).getTintColor();
        com.mojang.blaze3d.systems.RenderSystem.setShaderColor((tint >> 16 & 255) / 255f, (tint >> 8 & 255) / 255f, (tint & 255) / 255f, 1f);
        g.blit(x, y, 0, 16, 16, sp);
        com.mojang.blaze3d.systems.RenderSystem.setShaderColor(1, 1, 1, 1);
    }

    /** Compact count: 12K / 345M, right-aligned in the slot corner. / 紧凑数量：12K/345M，槽内右下角右对齐。 */
    static String fmt(long n) {
        if (n >= 1_000_000_000L) return (n / 1_000_000_000L) + "B";
        if (n >= 1_000_000L) return (n / 1_000_000L) + "M";
        if (n >= 1_000L) return (n / 1_000L) + "K";
        return String.valueOf(n);
    }

    /** Fluid count in buckets: plain number ≥1, 0.xB below. / 流体按桶：≥1 纯数字，<1 显示 0.xB。 */
    static String fmtFluid(long mb) {
        if (mb < 1000) return String.format("%.1fB", mb / 1000.0);
        double b = mb / 1000.0;
        if (b >= 1_000_000) return String.format("%.1fM", b / 1e6);
        if (b >= 1_000) return String.format("%.1fK", b / 1e3);
        return b == (long) b ? String.valueOf((long) b) : String.format("%.1f", b);
    }

    static void drawCount(GuiGraphics g, Font font, String t, int sx, int sy) {
        float sc = 0.6f;
        int tw = (int) (font.width(t) * sc), th = (int) (8 * sc);
        int x = sx + 16 - tw, y = sy + 16 - th;
        g.pose().pushPose();
        g.pose().translate(x, y, 200);
        g.pose().scale(sc, sc, 1);
        g.drawString(font, t, 0, 0, 0xFFFFFF, true);
        g.pose().popPose();
    }

    @Override public boolean mouseScrolled(double mx, double my, double sx, double sy) {
        if (splitMerge && barA != null && barA.mouseScrolled(mx, my, sy)) return true;
        if (splitMerge && barC != null && barC.mouseScrolled(mx, my, sy)) return true;
        if (splitMerge && barD != null && barD.mouseScrolled(mx, my, sy)) return true;
        if (splitMerge) return true;
        if (DispatchUtil.hit(mx, my, leftPos + GRID_X, topPos + GRID_Y, W - 16, rows * ROW_H)) {
            int maxOff = Math.max(0, (filtered.size() + COLS - 1) / COLS - rows);
            if (maxOff > 0) scrollOffset = Math.clamp(scrollOffset - (int) Math.signum(sy), 0, maxOff);
            return true;
        }
        if (rightPanelComp.mouseScrolled(mx, my, sy, leftPos, topPos)) return true;
        if (leftPanelComp.mouseScrolled(mx, my, sy, leftPos, topPos)) return true;
        return true;
    }

    @Override public boolean mouseClicked(double mx, double my, int btn) {
        int x = leftPos, y = topPos;
        if (!splitMerge && btn == 0 && hasShiftDown()) {
            // Shift+click an inventory slot — deposit every matching stack into the network
            // Shift+点击背包物品——该物品全部存入网络（Shift 状态判定，非双击计时）
            int iy = invY();
            int hitSlot = -1;
            for (int row = 0; row < 3 && hitSlot < 0; row++)
                for (int col = 0; col < 9; col++)
                    if (DispatchUtil.hit(mx, my, x + 8 + col * 18, y + iy + 14 + row * 18, 16, 16)) { hitSlot = row * 9 + col; break; }
            for (int col = 0; col < 9 && hitSlot < 0; col++)
                if (DispatchUtil.hit(mx, my, x + 8 + col * 18, y + iy + 14 + 58, 16, 16)) { hitSlot = 27 + col; break; }
            if (hitSlot >= 0) {
                var st = menu.getSlot(hitSlot).getItem();
                if (!st.isEmpty()) {
                    net.neoforged.neoforge.network.PacketDistributor.sendToServer(
                            new com.endlessepoch.core.network.GridClickPacket(appeng.api.stacks.AEItemKey.of(st), 0, 6));
                    return true;
                }
            }
        }
        if (splitMerge && barA != null) {
            // topmost panel + sidebar only — occluded panels stay dead / 顶层扩展区域(含侧边栏)优先，被盖面板不响应
            DraggablePanel[] all = {barA, barB, barC, barD};
            DraggablePanel[] expanded = new DraggablePanel[4], folded = new DraggablePanel[4];
            int ei = 0, fi = 0;
            for (DraggablePanel b : all) if (b.collapsed) folded[fi++] = b; else expanded[ei++] = b;
            int top = -1;
            for (int i = ei - 1; i >= 0; i--) if (panelHit(expanded[i], mx, my)) { top = i; break; }
            if (top >= 0 && expanded[top].mouseClicked(mx, my, btn)) return true;
            for (int i = fi - 1; i >= 0; i--) if (folded[i].mouseClicked(mx, my)) return true;
        }
        if (toolbar.mouseClicked(mx, my, x, y, btn)) return true;
        if (!splitMerge && DispatchUtil.hit(mx, my, x + GRID_X, y + GRID_Y, COLS * ROW_H, rows * ROW_H)
                && GridActions.click(this, mx, my, x + GRID_X, y + GRID_Y, COLS, rows, ROW_H, btn))
            return true;
        if (DispatchUtil.hit(mx, my, x + W - 16, y + GRID_Y, 10, rows * ROW_H)) { dragging = true; return true; }
        if (leftPanelComp.mouseClicked(mx, my, x, y, btn)) return true;
        if (rightPanelComp.mouseClicked(mx, my, x, y)) return true;
        searchComp.handleMouseClicked(mx, my, x, y);
        return super.mouseClicked(mx, my, btn);
    }

    @Override public boolean mouseReleased(double mx, double my, int btn) {
        if (splitMerge && barA != null) { barA.mouseReleased(); barB.mouseReleased(); barC.mouseReleased(); barD.mouseReleased(); return true; } // all panels always release / 全部面板无条件释放
        dragging = false; toolbar.mouseReleased(); leftPanelComp.mouseReleased(); return super.mouseReleased(mx, my, btn);
    }

    @Override public boolean mouseDragged(double mx, double my, int btn, double dx, double dy) {
        if (splitMerge && barA != null && (barA.mouseDragged(mx,my)||barB.mouseDragged(mx,my)||barC.mouseDragged(mx,my)||barD.mouseDragged(mx,my))) return true;
        if (dragging) { int by = topPos + GRID_Y, maxOff = Math.max(0, (filtered.size() + COLS - 1) / COLS - rows); if (maxOff > 0) scrollOffset = Math.clamp((int) ((my - by) / (rows * ROW_H) * maxOff), 0, maxOff); return true; }
        return super.mouseDragged(mx, my, btn, dx, dy);
    }

    @Override public boolean charTyped(char cp, int mod) {
        if (splitMerge) {
            if (barC != null && barC.charTyped(cp, mod)) return true;
            if (barD != null && barD.charTyped(cp, mod)) return true;
            return false;
        }
        if (!splitMerge && searchComp.handleCharTyped(cp, mod)) return true;
        return super.charTyped(cp, mod);
    }
    @Override public boolean keyPressed(int key, int scan, int mod) {
        if (trashMode && key == 256) {
            // ESC closes trash mode instead of the GUI / ESC 关闭垃圾桶模式而非关闭界面
            trashMode = false;
            trashPendingKey = null;
            return true;
        }
        if (splitMerge) {
            if (barC != null && barC.keyPressed(key, scan, mod)) return true;
            if (barD != null && barD.keyPressed(key, scan, mod)) return true;
            if (key == 256) { onClose(); return true; }
            return true;
        }
        if (!splitMerge && searchComp.handleKeyPressed(key, scan, mod)) return true;
        return super.keyPressed(key, scan, mod);
    }

    /** Apply incremental grid update from server / 应用服务端网格增量更新 */
    public void onGridUpdate(GridIncrementalUpdatePacket pkt) {
        gridView.apply(pkt);
        allEntries.clear();
        for (var e : gridView.snapshot().entrySet()) allEntries.add(new GridEntry(e.getKey(), e.getValue()));
        onSearch(searchComp.getValue());
    }

    /** Restore per-player prefs when the menu opens / 菜单打开时恢复玩家偏好 */
    public void onPref(com.endlessepoch.core.network.PrefPacket pkt) {
        sortMode = pkt.sortMode();
        sortAsc = pkt.sortAsc() != 0;
        displayMode = pkt.displayMode();
        onSearch(searchComp.getValue());
    }

    void sendPref() {
        net.neoforged.neoforge.network.PacketDistributor.sendToServer(
                new com.endlessepoch.core.network.SetPrefPacket(sortMode, sortAsc ? 1 : 0, displayMode));
    }

    void onSearch(String q) {
        filtered.clear();
        String lq = q.trim();
        boolean modF = lq.startsWith("@"), tagF = lq.startsWith("#"), tooltipF = lq.startsWith("$"), starF = lq.startsWith("*"), patF = lq.startsWith("!");
        String term = (modF || tagF || tooltipF || starF || patF) ? lq.substring(1).toLowerCase(Locale.ROOT) : lq.toLowerCase(Locale.ROOT);
        for (GridEntry e : allEntries) {
            if (!modeOk(e)) continue;
            if (lq.isEmpty()) { filtered.add(e); continue; }
            String name = nameOf(e).getString().toLowerCase(Locale.ROOT);
            boolean match;
            if (patF) match = false;
            else if (modF) match = modOf(e).toLowerCase(Locale.ROOT).contains(term);
            else if (tagF) match = e.key() instanceof AEItemKey ik && ik.getItem().builtInRegistryHolder().tags()
                    .anyMatch(t -> t.location().toString().toLowerCase(Locale.ROOT).contains(term));
            else if (tooltipF) match = descOf(e).toLowerCase(Locale.ROOT).contains(term);
            else match = name.contains(term) || descOf(e).toLowerCase(Locale.ROOT).contains(term) || PinyinUtil.matches(term, name);
            if (match) filtered.add(e);
        }
        int dir = sortAsc ? 1 : -1;
        filtered.sort((a, b) -> {
            // Fluid counts compare in buckets (1000 mB = 1) — same unit as item stacks
            // 流体数量按桶比较（1000 mB = 1）——与物品堆叠同单位
            long ac = a.key() instanceof AEFluidKey ? a.count() / 1000 : a.count();
            long bc = b.key() instanceof AEFluidKey ? b.count() / 1000 : b.count();
            int c = sortMode == 1 ? Long.compare(ac, bc)
                    : sortMode == 2 ? modOf(a).compareToIgnoreCase(modOf(b))
                    : nameOf(a).getString().compareToIgnoreCase(nameOf(b).getString());
            if (c == 0) c = nameOf(a).getString().compareToIgnoreCase(nameOf(b).getString());
            return dir * c;
        });
        scrollOffset = 0; if (patF) storageScroll = 0;
    }

    private boolean modeOk(GridEntry e) {
        if (displayMode == 1) return e.key() instanceof AEItemKey;
        if (displayMode == 2) return e.key() instanceof AEFluidKey;
        return true;
    }

    static net.minecraft.world.item.ItemStack iconOf(GridEntry e) {
        return e.key() instanceof AEItemKey ik ? ik.toStack(1) : net.minecraft.world.item.ItemStack.EMPTY;
    }

    static Component nameOf(GridEntry e) {
        if (e.key() instanceof AEItemKey ik) return ik.toStack(1).getHoverName();
        if (e.key() instanceof AEFluidKey fk) return fk.toStack(1).getFluid().getFluidType().getDescription();
        return Component.literal("?");
    }

    private static String descOf(GridEntry e) {
        if (e.key() instanceof AEItemKey ik) return ik.toStack(1).getDescriptionId();
        if (e.key() instanceof AEFluidKey fk) return fk.toStack(1).getFluid().getFluidType().getDescription().getString();
        return "";
    }

    private static String modOf(GridEntry e) {
        if (e.key() instanceof AEItemKey ik)
            return net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(ik.getItem()).getNamespace();
        if (e.key() instanceof AEFluidKey fk)
            return net.minecraft.core.registries.BuiltInRegistries.FLUID.getKey(fk.getFluid()).getNamespace();
        return "";
    }

    record MachineEntry(String name, int status) {}
    static final List<MachineEntry> MACHINES = List.of(new MachineEntry("创造测试机", 1), new MachineEntry("调度中心", 0), new MachineEntry("高压熔炉阵列", 1), new MachineEntry("精密组装线", 0), new MachineEntry("量子压缩机", 2), new MachineEntry("大型锅炉", 1), new MachineEntry("粒子加速器", 2), new MachineEntry("晶体生长仓", 0));
    static final int PATTERN_SLOTS = 144;
    static final ItemStack[] DUMMY_QUEUE = {new ItemStack(Items.PISTON, 16), new ItemStack(Items.IRON_PICKAXE, 3), new ItemStack(Items.LADDER, 64), new ItemStack(Items.CHEST, 8), new ItemStack(Items.IRON_DOOR, 4)};
    static final ItemStack[] DUMMY_PATTERNS = {new ItemStack(Items.CRAFTING_TABLE), new ItemStack(Items.FURNACE), new ItemStack(Items.IRON_PICKAXE), new ItemStack(Items.DIAMOND_SWORD), new ItemStack(Items.BREAD), new ItemStack(Items.OAK_PLANKS, 4), new ItemStack(Items.STICK, 4), new ItemStack(Items.TORCH, 4), new ItemStack(Items.STONE_PICKAXE), new ItemStack(Items.GOLDEN_APPLE), new ItemStack(Items.IRON_CHESTPLATE), new ItemStack(Items.BOW), new ItemStack(Items.IRON_SWORD), new ItemStack(Items.STONE_SWORD), new ItemStack(Items.WOODEN_PICKAXE), new ItemStack(Items.GOLDEN_PICKAXE), new ItemStack(Items.DIAMOND_PICKAXE), new ItemStack(Items.NETHERITE_PICKAXE), new ItemStack(Items.STONE_AXE), new ItemStack(Items.IRON_AXE), new ItemStack(Items.FLINT_AND_STEEL), new ItemStack(Items.COOKED_BEEF, 64), new ItemStack(Items.ENDER_EYE, 16), new ItemStack(Items.NETHER_STAR, 1), new ItemStack(Items.DRAGON_EGG, 1), new ItemStack(Items.ELYTRA, 1), new ItemStack(Items.TOTEM_OF_UNDYING, 1), new ItemStack(Items.NETHERITE_SCRAP, 4), new ItemStack(Items.DIAMOND_HORSE_ARMOR, 1), new ItemStack(Items.ENCHANTED_GOLDEN_APPLE, 1), new ItemStack(Items.MUSIC_DISC_CAT, 1), new ItemStack(Items.SCULK_SENSOR, 16), new ItemStack(Items.AMETHYST_CLUSTER, 8), new ItemStack(Items.SPONGE, 4), new ItemStack(Items.SEA_LANTERN, 16), new ItemStack(Items.PRISMARINE_BRICKS, 32)};

    /** Hit test panel + sidebar strip (width by actual buttons) / 展开面板+侧边栏扩展区域命中（侧边栏条按实际按钮宽度） */
    private static boolean panelHit(DraggablePanel p, double mx, double my) {
        if (my < p.y - 15 || my > p.y + p.h) return false;
        if (my < p.y) return mx >= p.x && mx <= p.x + p.sidebarW();
        return mx >= p.x && mx <= p.x + p.w;
    }
}
