package com.endlessepoch.core.screen;

import com.endlessepoch.core.menu.DispatchMenu;
import com.endlessepoch.core.nova.client.PinyinUtil;
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

    private static final ResourceLocation BG_TEX = ResourceLocation.parse("eecore:textures/gui/dispatch/dispatch_ui.png");
    private static final ResourceLocation PANEL_LEFT = ResourceLocation.parse("eecore:textures/gui/dispatch/panel_left.png");
    private static final ResourceLocation PANEL_RIGHT = ResourceLocation.parse("eecore:textures/gui/dispatch/panel_right.png");
    static final ResourceLocation SLOT_TEX = ResourceLocation.parse("eecore:textures/gui/dispatch/slot.png");
    static final ResourceLocation SPRITES = ResourceLocation.parse("eecore:textures/gui/dispatch/dispatch_sprites.png");
    static final ResourceLocation INV_BG = ResourceLocation.parse("eecore:textures/gui/dispatch/inv_bg.png");

    private static final List<ItemStack> ALL = createDummies();
    final List<ItemStack> filtered = new ArrayList<>();
    int scrollOffset, leftPanel;
    int sortMode;
    boolean sortAsc = true;
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
    DraggablePanel barA, barD;
    InventoryPanel barB;
    MainPanel barC;

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
                barA = new DraggablePanel(cx - 80, cy - 20, "编码");
                barB = new InventoryPanel(this, cx - 80, cy + 6);
                barC = new MainPanel(this, cx + 10, cy - 20);
                barD = new DraggablePanel(cx + 10, cy + 6, "仓库"); }
            DraggablePanel[] all = {barA, barB, barC, barD}, collapsed = new DraggablePanel[4], expanded = new DraggablePanel[4];
            int ci = 0, ei = 0;
            for (DraggablePanel b : all) if (b.collapsed) collapsed[ci++] = b; else expanded[ei++] = b;
            for (int i = 0; i < ci; i++) collapsed[i].render(g, font(), mx, my);
            int topHover = -1;
            for (int i = ei - 1; i >= 0; i--)
                if (DraggablePanel.hit(mx, my, expanded[i].x, expanded[i].y, expanded[i].w, expanded[i].h)) { topHover = i; break; }
            for (int i = 0; i < ei; i++) {
                boolean covered = topHover >= 0 && topHover != i && DraggablePanel.hit(mx, my, expanded[i].x, expanded[i].y, expanded[i].w, expanded[i].h);
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
        toolbar.draw(g, x, y, mx, my);
        toolbar.drawFg(g, x, y, mx, my);
        toolbar.drawTooltips(g, x, y, mx, my);
        searchComp.drawTooltips(g, x, y, mx, my);
        leftPanelComp.drawTooltips(g, x, y, mx, my);
        rightPanelComp.drawTooltips(g, x, y, mx, my);
        rightPanelComp.drawFg(g, x, y, mx, my);
        renderGrid(g, x, y, mx, my);
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
            ItemStack s = filtered.get(idx); g.renderItem(s, sx, sy);
            DispatchUtil.slotHover(g, minecraft, sx, sy, 16, 16, mx, my);
            g.renderItemDecorations(font, s, sx, sy);
        }
        if (DispatchUtil.hit(mx, my, x + GRID_X, y + GRID_Y, COLS * ROW_H, rows * ROW_H)) {
            int c = (mx - x - GRID_X) / ROW_H, r = (my - y - GRID_Y) / ROW_H;
            if (c >= 0 && c < COLS && r >= 0 && r < rows) {
                int idx = scrollOffset * COLS + r * COLS + c;
                if (idx >= 0 && idx < total) g.renderTooltip(font, filtered.get(idx), mx, my);
            }
        }
    }

    @Override public boolean mouseScrolled(double mx, double my, double sx, double sy) {
        if (splitMerge && barC != null && barC.mouseScrolled(mx, my, sy)) return true;
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
        if (splitMerge && barA != null) {
            // expanded first (topmost wins), then collapsed / 展开优先→折叠
            DraggablePanel[] all = {barA, barB, barC, barD};
            DraggablePanel[] expanded = new DraggablePanel[4], folded = new DraggablePanel[4];
            int ei = 0, fi = 0;
            for (DraggablePanel b : all) if (b.collapsed) folded[fi++] = b; else expanded[ei++] = b;
            for (int i = ei - 1; i >= 0; i--) if (expanded[i].mouseClicked(mx, my, btn)) return true;
            for (int i = fi - 1; i >= 0; i--) if (folded[i].mouseClicked(mx, my)) return true;
        }
        if (toolbar.mouseClicked(mx, my, x, y, btn)) return true;
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
        if (splitMerge) return barC != null && barC.charTyped(cp, mod);
        if (!splitMerge && searchComp.handleCharTyped(cp, mod)) return true;
        return super.charTyped(cp, mod);
    }
    @Override public boolean keyPressed(int key, int scan, int mod) {
        if (splitMerge && barC != null && barC.keyPressed(key, scan, mod)) return true;
        if (splitMerge) {
            if (key == 256) { onClose(); return true; }
            return true;
        }
        if (!splitMerge && searchComp.handleKeyPressed(key, scan, mod)) return true;
        return super.keyPressed(key, scan, mod);
    }

    void onSearch(String q) {
        filtered.clear(); String lq = q.trim(); if (lq.isEmpty()) { filtered.addAll(ALL); scrollOffset = 0; storageScroll = 0; return; }
        boolean modF = lq.startsWith("@"), tagF = lq.startsWith("#"), tooltipF = lq.startsWith("$"), starF = lq.startsWith("*"), patF = lq.startsWith("!");
        String term = (modF || tagF || tooltipF || starF || patF) ? lq.substring(1).toLowerCase(Locale.ROOT) : lq.toLowerCase(Locale.ROOT);
        for (ItemStack s : ALL) {
            String name = s.getDisplayName().getString().toLowerCase(Locale.ROOT); boolean match;
            if (patF) match = false;
            else if (modF) match = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(s.getItem()).getNamespace().toLowerCase(Locale.ROOT).contains(term);
            else if (tagF) match = s.getItem().builtInRegistryHolder().tags().anyMatch(t -> t.location().toString().toLowerCase(Locale.ROOT).contains(term));
            else if (tooltipF) match = s.getDescriptionId().toLowerCase(Locale.ROOT).contains(term);
            else match = name.contains(term) || s.getDescriptionId().toLowerCase(Locale.ROOT).contains(term) || PinyinUtil.matches(term, name);
            if (match) filtered.add(s);
        }
        filtered.sort(sortMode == 1 ? (a, b) -> Integer.compare(b.getCount(), a.getCount()) : (a, b) -> a.getDisplayName().getString().compareToIgnoreCase(b.getDisplayName().getString()));
        scrollOffset = 0; if (patF) storageScroll = 0;
    }

    record MachineEntry(String name, int status) {}
    static final List<MachineEntry> MACHINES = List.of(new MachineEntry("创造测试机", 1), new MachineEntry("调度中心", 0), new MachineEntry("高压熔炉阵列", 1), new MachineEntry("精密组装线", 0), new MachineEntry("量子压缩机", 2), new MachineEntry("大型锅炉", 1), new MachineEntry("粒子加速器", 2), new MachineEntry("晶体生长仓", 0));
    static final int PATTERN_SLOTS = 144;
    static final ItemStack[] DUMMY_QUEUE = {new ItemStack(Items.PISTON, 16), new ItemStack(Items.IRON_PICKAXE, 3), new ItemStack(Items.LADDER, 64), new ItemStack(Items.CHEST, 8), new ItemStack(Items.IRON_DOOR, 4)};
    static final ItemStack[] DUMMY_PATTERNS = {new ItemStack(Items.CRAFTING_TABLE), new ItemStack(Items.FURNACE), new ItemStack(Items.IRON_PICKAXE), new ItemStack(Items.DIAMOND_SWORD), new ItemStack(Items.BREAD), new ItemStack(Items.OAK_PLANKS, 4), new ItemStack(Items.STICK, 4), new ItemStack(Items.TORCH, 4), new ItemStack(Items.STONE_PICKAXE), new ItemStack(Items.GOLDEN_APPLE), new ItemStack(Items.IRON_CHESTPLATE), new ItemStack(Items.BOW), new ItemStack(Items.IRON_SWORD), new ItemStack(Items.STONE_SWORD), new ItemStack(Items.WOODEN_PICKAXE), new ItemStack(Items.GOLDEN_PICKAXE), new ItemStack(Items.DIAMOND_PICKAXE), new ItemStack(Items.NETHERITE_PICKAXE), new ItemStack(Items.STONE_AXE), new ItemStack(Items.IRON_AXE), new ItemStack(Items.FLINT_AND_STEEL), new ItemStack(Items.COOKED_BEEF, 64), new ItemStack(Items.ENDER_EYE, 16), new ItemStack(Items.NETHER_STAR, 1), new ItemStack(Items.DRAGON_EGG, 1), new ItemStack(Items.ELYTRA, 1), new ItemStack(Items.TOTEM_OF_UNDYING, 1), new ItemStack(Items.NETHERITE_SCRAP, 4), new ItemStack(Items.DIAMOND_HORSE_ARMOR, 1), new ItemStack(Items.ENCHANTED_GOLDEN_APPLE, 1), new ItemStack(Items.MUSIC_DISC_CAT, 1), new ItemStack(Items.SCULK_SENSOR, 16), new ItemStack(Items.AMETHYST_CLUSTER, 8), new ItemStack(Items.SPONGE, 4), new ItemStack(Items.SEA_LANTERN, 16), new ItemStack(Items.PRISMARINE_BRICKS, 32)};

    private static List<ItemStack> createDummies() {
        List<ItemStack> l = new ArrayList<>(); add(l, Items.DIAMOND, 64); add(l, Items.IRON_INGOT, 128); add(l, Items.GOLD_INGOT, 64); add(l, Items.NETHERITE_INGOT, 1); add(l, Items.COPPER_INGOT, 256); add(l, Items.REDSTONE, 512); add(l, Items.LAPIS_LAZULI, 128); add(l, Items.EMERALD, 32); add(l, Items.COAL, 1024); add(l, Items.QUARTZ, 256); add(l, Items.AMETHYST_SHARD, 64); add(l, Items.ENDER_PEARL, 16); add(l, Items.BLAZE_ROD, 32); add(l, Items.SLIME_BALL, 64); add(l, Items.GLOWSTONE_DUST, 128); add(l, Items.GUNPOWDER, 256); add(l, Items.OBSIDIAN, 64); add(l, Items.GLASS, 512); add(l, Items.STONE, 4096); add(l, Items.OAK_LOG, 256); add(l, Items.IRON_BLOCK, 16); add(l, Items.GOLD_BLOCK, 8); add(l, Items.DIAMOND_BLOCK, 2); add(l, Items.CRAFTING_TABLE, 64); add(l, Items.FURNACE, 32); add(l, Items.CHEST, 48); add(l, Items.HOPPER, 16); add(l, Items.PISTON, 32); add(l, Items.STICKY_PISTON, 16); add(l, Items.REPEATER, 64); add(l, Items.COMPARATOR, 32); add(l, Items.DISPENSER, 16); add(l, Items.DROPPER, 32); add(l, Items.OBSERVER, 16); add(l, Items.NOTE_BLOCK, 64); add(l, Items.TNT, 32); add(l, Items.BOOKSHELF, 8); add(l, Items.ENCHANTING_TABLE, 1); add(l, Items.ANVIL, 4); add(l, Items.BREWING_STAND, 8); add(l, Items.CAULDRON, 16); add(l, Items.BEACON, 1); add(l, Items.CONDUIT, 1); add(l, Items.LODESTONE, 1); add(l, Items.LANTERN, 32); add(l, Items.SOUL_LANTERN, 16); add(l, Items.CHAIN, 64); add(l, Items.IRON_BARS, 32); add(l, Items.IRON_DOOR, 4); add(l, Items.IRON_TRAPDOOR, 8); add(l, Items.ARROW, 256); add(l, Items.SPECTRAL_ARROW, 64); add(l, Items.BOW, 1); add(l, Items.CROSSBOW, 2); add(l, Items.SHIELD, 4); add(l, Items.TRIDENT, 1); add(l, Items.FISHING_ROD, 2); add(l, Items.SHEARS, 4); add(l, Items.FLINT_AND_STEEL, 8); add(l, Items.CLOCK, 1); add(l, Items.COMPASS, 1); add(l, Items.MAP, 4); add(l, Items.NAME_TAG, 8); add(l, Items.SADDLE, 2); add(l, Items.LEAD, 16);
        return l;
    }

    private static void add(List<ItemStack> l, net.minecraft.world.item.Item i, int c) { l.add(new ItemStack(i, c)); }
}
