package com.endlessepoch.core.screen;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;

class MainPanel extends SlotPanel {

    private static final int COLS = 9, CELL = 18, GX = 5, GY = 36;
    private EditBox search;
    private boolean btnDown() { return org.lwjgl.glfw.GLFW.glfwGetMouseButton(screen.mc().getWindow().getWindow(), 0) == 1; }

    MainPanel(DispatchScreen screen, int x, int y) {
        super(screen, x, y, "主面板"); w = 181; h = 78;
    }

    private static final int SB_X = 0, SB_Y = 15;
    private static final int DM_ICONS = 180, DM_V = 154, IW = 7, IH = 8;
    private static final int ST_U = 173, ST_V = 164;
    private static final int[] ST_SRC = {173, 180, 189};
    private static final int[] ST_W = {5, 7, 7};
    private static final int SD_U = 173, SD_V = 175;
    private static final int DN_U = 173, DN_V = 186; // density: 5×6 +2 6×8 +2 7×8 +2 7×8
    private static final int[] DN_SRC = {0, 8, 16, 25}, DN_W = {5, 6, 7, 7}, DN_H = {6, 8, 8, 8};
    private static final int[] DN_ROWS = {3, 4, 5, 6}; // split density
    private int densityIdx; // 0=3, 1=4, 2=5, 3=6
    private int sbW = 111; // left2+mid15×7+right4

    @Override public void render(GuiGraphics g, Font font, int mx, int my) {
        if (collapsed) { renderCollapsed(g, font, mx, my); return; }
        if (search == null) initSearch();
        search.setX(x + 78); search.setY(y + 6);
        int rows = DN_ROWS[densityIdx] - 2;
        g.blit(DispatchScreen.SPRITES, x, y, 236, 110, 181, 36, 512, 512);
        for (int i = 0; i < rows; i++)
            g.blit(DispatchScreen.SPRITES, x, y + 36 + i * 18, 236, 146, 181, 18, 512, 512);
        g.blit(DispatchScreen.SPRITES, x, y + 36 + rows * 18, 236, 164, 181, 24, 512, 512);
        h = 36 + rows * 18 + 24;
        renderGrid(g, font, mx, my);
        renderExtendedSidebar(g, font, x, y - SB_Y, mx, my);
        g.drawString(font, Component.literal(title), x + 8, y + 6, 0xFF_404040, false);
        search.render(g, mx, my, 0);
    }

    private void renderExtendedSidebar(GuiGraphics g, Font font, int sx, int sy, int mx, int my) {
        // bg: left2 + mid15×7 + right4 = 111
        g.blit(DispatchScreen.SPRITES, sx, sy, 213, 134, 2, 17, 512, 512);
        for (int i = 0; i < 7; i++)
            g.blit(DispatchScreen.SPRITES, sx + 2 + i * 15, sy, 215, 134, 15, 17, 512, 512);
        g.blit(DispatchScreen.SPRITES, sx + 107, sy, 230, 134, 4, 17, 512, 512);

        boolean hov = hit(mx, my, sx + 3, sy + 1, 13, 14);
        int u = hov ? 185 : 172;
        g.blit(DispatchScreen.SPRITES, sx + 3, sy + 3, u, 138, 13, 14, 512, 512);
        g.blit(DispatchScreen.SPRITES, sx + 6, sy + 6, 172, 154, 7, 7, 512, 512);
        if (hov) {
            java.util.List<Component> tt = new java.util.ArrayList<>();
            tt.add(Component.translatable("eecore.dispatch.sidebar.title"));
            tt.add(Component.translatable("eecore.dispatch.sidebar.collapse").withStyle(s -> s.withColor(0xFF_888888)));
            tt.add(Component.translatable("eecore.dispatch.sidebar.merge").withStyle(s -> s.withColor(0xFF_888888)));
            g.renderTooltip(font, tt, java.util.Optional.empty(), mx, my);
        }

        int dbx = sx + 18, dby = sy + 3;
        boolean h = DispatchUtil.hit(mx, my, dbx, dby, 13, 14);
        boolean down = h && btnDown();
        int yOff = down ? 1 : 0;
        int btnU = down ? 198 : (h ? 185 : 172);
        g.blit(DispatchScreen.SPRITES, dbx, dby + yOff, btnU, 138, 13, 14, 512, 512);
        int[] iconIdx = {1, 2, 0};
        g.blit(DispatchScreen.SPRITES, sx + 21, sy + 5 + yOff, DM_ICONS + iconIdx[screen.displayMode] * (IW + 1), DM_V, IW, IH, 512, 512);
        if (h) {
            String[] dmSub = {"eecore.dispatch.display.all", "eecore.dispatch.display.items", "eecore.dispatch.display.fluids"};
            java.util.List<Component> tt = new java.util.ArrayList<>();
            tt.add(Component.translatable("eecore.dispatch.tooltip.config_type"));
            int dm = screen.displayMode;
            for (int j = 0; j < 3; j++) { boolean sel = j == dm; tt.add(Component.translatable(dmSub[j]).withStyle(s -> s.withColor(sel ? DispatchScreen.C_HL : DispatchScreen.C_TD))); }
            g.renderTooltip(font, tt, java.util.Optional.empty(), mx, my);
        }

        int sbx = sx + 33, sby = sy + 3;
        boolean sh = DispatchUtil.hit(mx, my, sbx, sby, 13, 14);
        boolean sdown = sh && btnDown();
        int syOff = sdown ? 1 : 0;
        int sbu = sdown ? 198 : (sh ? 185 : 172);
        g.blit(DispatchScreen.SPRITES, sbx, sby + syOff, sbu, 138, 13, 14, 512, 512);
        int sm = screen.sortMode;
        g.blit(DispatchScreen.SPRITES, sbx + (13 - ST_W[sm]) / 2, sby + 2 + syOff, ST_SRC[sm], ST_V, ST_W[sm], 8, 512, 512);
        if (sh) {
            String[] stSub = {"eecore.dispatch.sort.name", "eecore.dispatch.sort.count", "eecore.dispatch.sort.mod"};
            java.util.List<Component> tt = new java.util.ArrayList<>();
            tt.add(Component.translatable("eecore.dispatch.tooltip.sort_by"));
            for (int j = 0; j < 3; j++) { boolean sel = j == sm; tt.add(Component.translatable(stSub[j]).withStyle(s -> s.withColor(sel ? DispatchScreen.C_HL : DispatchScreen.C_TD))); }
            g.renderTooltip(font, tt, java.util.Optional.empty(), mx, my);
        }

        int dirX = sx + 48, dirY = sy + 3;
        boolean dirH = DispatchUtil.hit(mx, my, dirX, dirY, 13, 14);
        boolean dirDown = dirH && btnDown();
        int dirYOff = dirDown ? 1 : 0;
        int dirU = dirDown ? 198 : (dirH ? 185 : 172);
        g.blit(DispatchScreen.SPRITES, dirX, dirY + dirYOff, dirU, 138, 13, 14, 512, 512);
        int ascSrc = screen.sortAsc ? SD_U : SD_U + 8; // asc 5×8, desc at +3+5=+8
        g.blit(DispatchScreen.SPRITES, dirX + 4, dirY + 2 + dirYOff, ascSrc, SD_V, 5, 8, 512, 512);
        if (dirH) {
            String[] dirSub = {"eecore.dispatch.dir.asc", "eecore.dispatch.dir.desc"};
            java.util.List<Component> dt = new java.util.ArrayList<>();
            dt.add(Component.translatable("eecore.dispatch.tooltip.sort_dir"));
            boolean sa = screen.sortAsc;
            for (int j = 0; j < 2; j++) { boolean sel = (j == 0) == sa; dt.add(Component.translatable(dirSub[j]).withStyle(s -> s.withColor(sel ? DispatchScreen.C_HL : DispatchScreen.C_TD))); }
            g.renderTooltip(font, dt, java.util.Optional.empty(), mx, my);
        }

        int dnX = sx + 63, dnY = sy + 3;
        boolean dnH = DispatchUtil.hit(mx, my, dnX, dnY, 13, 14);
        boolean dnDown = dnH && btnDown();
        int dnYOff = dnDown ? 1 : 0;
        int dnU = dnDown ? 198 : (dnH ? 185 : 172);
        g.blit(DispatchScreen.SPRITES, dnX, dnY + dnYOff, dnU, 138, 13, 14, 512, 512);
        int di = densityIdx;
        int dnxOff = (13 - DN_W[di]) / 2 + (di == 1 ? 1 : 0);
        g.blit(DispatchScreen.SPRITES, dnX + dnxOff, dnY + (14 - DN_H[di]) / 2 - 1 + dnYOff, DN_U + DN_SRC[di], DN_V, DN_W[di], DN_H[di], 512, 512);
        if (dnH) {
            String[] dnSub = {"eecore.dispatch.density.short", "eecore.dispatch.density.medium", "eecore.dispatch.density.long", "eecore.dispatch.density.longest"};
            java.util.List<Component> dt = new java.util.ArrayList<>();
            dt.add(Component.translatable("eecore.dispatch.tooltip.density"));
            for (int j = 0; j < 4; j++) { boolean sel = j == di; dt.add(Component.translatable(dnSub[j]).withStyle(s -> s.withColor(sel ? DispatchScreen.C_HL : DispatchScreen.C_TD))); }
            g.renderTooltip(font, dt, java.util.Optional.empty(), mx, my);
        }

        int setX = sx + 78, setY = sy + 3;
        boolean setH = DispatchUtil.hit(mx, my, setX, setY, 13, 14);
        boolean setDown = setH && btnDown();
        int setYOff = setDown ? 1 : 0;
        int setU = setDown ? 198 : (setH ? 185 : 172);
        g.blit(DispatchScreen.SPRITES, setX, setY + setYOff, setU, 138, 13, 14, 512, 512);
        g.blit(DispatchScreen.SPRITES, setX + 3, setY + 2 + setYOff, SD_U + 16, SD_V, 7, 8, 512, 512);
        if (setH) g.renderTooltip(font, Component.translatable("eecore.dispatch.tooltip.settings"), mx, my);

        int tbx = sx + 93, tby = sy + 3;
        boolean th = DispatchUtil.hit(mx, my, tbx, tby, 13, 14);
        boolean tdown = th && btnDown();
        int tyOff = tdown ? 1 : 0;
        int tbu = tdown ? 198 : (th ? 185 : 172);
        g.blit(DispatchScreen.SPRITES, tbx, tby + tyOff, tbu, 138, 13, 14, 512, 512);
        g.blit(DispatchScreen.SPRITES, tbx + 3, tby + 2 + tyOff, 198, ST_V, 7, 8, 512, 512);
        if (th) g.renderTooltip(font, Component.translatable("eecore.dispatch.tooltip.trash"), mx, my);
    }

    private void renderGrid(GuiGraphics g, Font font, int mx, int my) {
        var filtered = screen.filtered;
        int total = filtered.size(), gridRows = DN_ROWS[densityIdx];
        screen.scrollOffset = Math.min(screen.scrollOffset, Math.max(0, (total + COLS - 1) / COLS - gridRows));
        for (int r = 0; r < gridRows; r++)
            for (int c = 0; c < COLS; c++) {
                int idx = screen.scrollOffset * COLS + r * COLS + c;
                if (idx >= total) break;
                int sx = x + GX + c * CELL, sy = y + 19 + r * CELL;
                g.renderItem(filtered.get(idx), sx, sy);
                DispatchUtil.slotHover(g, screen.mc(), sx, sy, 16, 16, mx, my);
            }
        if (DispatchUtil.hit(mx, my, x + GX, y + 19, COLS * CELL, gridRows * CELL)) {
            int c = (int)(mx - x - GX) / CELL, r = (int)(my - y - 19) / CELL;
            if (c >= 0 && c < COLS && r >= 0 && r < gridRows) {
                int idx = screen.scrollOffset * COLS + r * COLS + c;
                if (idx >= 0 && idx < total) g.renderTooltip(font, filtered.get(idx), mx, my);
            }
        }
        int maxOff = Math.max(0, (total + COLS - 1) / COLS - gridRows);
        if (maxOff <= 0) return;
        int sbX = x + 169, sbY = y + 18, sbH = gridRows * CELL;
        int hY = sbY + (int) ((long) (sbH - 15) * screen.scrollOffset / maxOff);
        g.blit(DispatchScreen.SPRITES, sbX - 1, hY, 170, 18, 9, 17, 512, 512);
    }

    boolean mouseScrolled(double mx, double my, double sy) {
        int gridRows = DN_ROWS[densityIdx];
        if (collapsed || !DispatchUtil.hit(mx, my, x + GX, y + 19, COLS * CELL, gridRows * CELL)) return false;
        int total = screen.filtered.size(), maxOff = Math.max(0, (total + COLS - 1) / COLS - gridRows);
        if (maxOff > 0) screen.scrollOffset = Math.clamp(screen.scrollOffset - (int) Math.signum(sy), 0, maxOff);
        return true;
    }

    private void initSearch() {
        search = new EditBox(screen.font(), x + 3, y + 5, 72, 12, Component.empty());
        search.setBordered(false); search.setMaxLength(15);
        search.setTextColor(0xFF_EEEEEE);
        search.setHint(Component.translatable("eecore.dispatch.search").withStyle(s -> s.withColor(0xFF_DBDCE0)));
        search.setResponder(screen::onSearch);
    }

    @Override public boolean mouseClicked(double mx, double my, int btn) {
        int bx = collapsed ? x + 40 : x + w - 13;
        if (DispatchUtil.hit(mx, my, bx, y + 3, 9, 9)) { collapsed = !collapsed; return true; }
        if (!collapsed) {
            int sy = y - SB_Y;
            if (DispatchUtil.hit(mx, my, x + 3, sy + 1, 13, 14)) { if (btn == 0) collapsed = true; else onSidebarRightClick(); return true; }
            int dbx = x + 18, dby = sy + 3;
            if (DispatchUtil.hit(mx, my, dbx, dby, 13, 14)) {
                screen.displayMode = btn == 0 ? (screen.displayMode + 1) % 3 : (screen.displayMode + 2) % 3;
                screen.onSearch(screen.searchComp.getValue()); return true;
            }
            if (DispatchUtil.hit(mx, my, x + 33, sy + 3, 13, 14)) {
                screen.sortMode = (screen.sortMode + 1) % 3; screen.onSearch(screen.searchComp.getValue()); return true;
            }
            if (DispatchUtil.hit(mx, my, x + 48, sy + 3, 13, 14)) {
                screen.sortAsc = !screen.sortAsc; screen.onSearch(screen.searchComp.getValue()); return true;
            }
            if (DispatchUtil.hit(mx, my, x + 63, sy + 3, 13, 14)) {
                densityIdx = (densityIdx + 1) % 4; return true;
            }
            if (DispatchUtil.hit(mx, my, x + 78, sy + 3, 13, 14)) {
                return true;
            }
            if (DispatchUtil.hit(mx, my, x + 93, sy + 3, 13, 14)) {
                return true;
            }
        }
        if (!collapsed && search != null) {
            boolean hit = search.isMouseOver(mx, my);
            search.setFocused(hit);
            if (hit) { search.mouseClicked(mx, my, btn); return true; }
        }
        int cw = collapsed ? 53 : w, ch = collapsed ? 16 : h;
        if (DraggablePanel.hit(mx, my, x, y, cw, ch)) { dragging = true; dragOffX = (int) mx - x; dragOffY = (int) my - y; return true; }
        return false;
    }

    boolean charTyped(char cp, int mod) { return search != null && search.charTyped(cp, mod); }
    boolean keyPressed(int key, int scan, int mod) {
        if (search == null || !search.isFocused()) return false;
        if (key == 256) { search.setFocused(false); return true; }
        return search.keyPressed(key, scan, mod);
    }

    @Override public boolean mouseClicked(double mx, double my) { return mouseClicked(mx, my, 0); }
    @Override protected void onSidebarRightClick() { screen.splitMerge = false; }
}
