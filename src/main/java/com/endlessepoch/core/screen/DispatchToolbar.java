package com.endlessepoch.core.screen;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

public class DispatchToolbar {
    static final int TB_Y0 = 3, TB_STEP = 22, TB_W0 = 18, TB_H = 20, TB_CNT = 7;
    static int TB_X(int w) { return 2 - w; }

    private final DispatchScreen screen;

    public DispatchToolbar(DispatchScreen screen) { this.screen = screen; }

    private boolean btnDown() { return org.lwjgl.glfw.GLFW.glfwGetMouseButton(screen.mc().getWindow().getWindow(), 0) == 1; }

    public void draw(GuiGraphics g, int x, int y, int mx, int my) {
        int bx = x + TB_X(TB_W0), bgX = x - 19;
        g.blit(DispatchScreen.SPRITES, bgX, y, 372, 55, 21, 2, 512, 512);
        for (int i = 0; i < TB_CNT; i++) g.blit(DispatchScreen.SPRITES, bgX, y + 2 + i * TB_STEP, 372, 57, 21, 22, 512, 512);
        g.blit(DispatchScreen.SPRITES, bgX, y + 2 + TB_CNT * TB_STEP, 372, 79, 21, 4, 512, 512);
        int[] dmIcons = {281, 271, 261};
        boolean leftDown = btnDown();
        for (int i = 0; i < TB_CNT; i++) {
            int by = y + TB_Y0 + i * TB_STEP;
            boolean h = DispatchUtil.hit(mx, my, bx, by, TB_W0, TB_H);
            boolean down = h && leftDown;
            int s = down ? 353 : (h ? 335 : 317), pr = down ? 1 : 0;
            g.blit(DispatchScreen.SPRITES, bx, by, s, 55, TB_W0, TB_H, 512, 512);
            if (i == 0) g.blit(DispatchScreen.SPRITES, bx + 4, by + 5 + pr, 145, 56, 10, 10, 512, 512);
            if (i == 1) g.blit(DispatchScreen.SPRITES, bx + 4, by + 3 + pr, dmIcons[screen.displayMode], 71, 10, 12, 512, 512);
            if (i == 4) g.blit(DispatchScreen.SPRITES, bx + 5, by + 3 + pr, 294 + screen.densityIdx * 9, 77, 9, 13, 512, 512);
            if (i == TB_CNT - 1) {
                if (screen.trashMode) g.blit(DispatchScreen.SPRITES, bx, by, 353, 55, TB_W0, TB_H, 512, 512);
                g.blit(DispatchScreen.SPRITES, bx + 4, by + 4 + (screen.trashMode ? 1 : pr), 360, 78, 10, 11, 512, 512);
            }
        }
    }

    public void drawFg(GuiGraphics g, int x, int y, int mx, int my) {
        int bx = x + TB_X(TB_W0);
        boolean leftDown = btnDown();
        boolean d2 = leftDown && DispatchUtil.hit(mx, my, bx, y + TB_Y0 + 2 * TB_STEP, TB_W0, TB_H);
        boolean d3 = leftDown && DispatchUtil.hit(mx, my, bx, y + TB_Y0 + 3 * TB_STEP, TB_W0, TB_H);
        boolean d5 = leftDown && DispatchUtil.hit(mx, my, bx, y + TB_Y0 + 5 * TB_STEP, TB_W0, TB_H);
        int[] sortU = {332, 342, 350}, sortW = {10, 8, 8}, sortX = {4, 5, 5};
        g.blit(DispatchScreen.SPRITES, bx + sortX[screen.sortMode], y + TB_Y0 + 2 * TB_STEP + 3 + (d2 ? 1 : 0), sortU[screen.sortMode], 77, sortW[screen.sortMode], 13, 512, 512);
        g.blit(DispatchScreen.SPRITES, bx + 5, y + TB_Y0 + 3 * TB_STEP + 4 + (d3 ? 1 : 0), screen.sortAsc ? 261 : 269, 86, 8, 11, 512, 512);
        g.blit(DispatchScreen.SPRITES, bx + 4, y + TB_Y0 + 5 * TB_STEP + 4 + (d5 ? 1 : 0), 277, 86, 10, 11, 512, 512);
    }

    public void drawTooltips(GuiGraphics g, int x, int y, int mx, int my) {
        String[] tips = {"eecore.dispatch.tooltip.split", "eecore.dispatch.tooltip.config_type", "eecore.dispatch.tooltip.sort_by", "eecore.dispatch.tooltip.sort_dir", "eecore.dispatch.tooltip.density", "eecore.dispatch.tooltip.settings", "eecore.dispatch.tooltip.trash"};
        String[] dmSub = {"eecore.dispatch.display.all", "eecore.dispatch.display.items", "eecore.dispatch.display.fluids"};
        int bx = x + TB_X(TB_W0);
        for (int i = 0; i < TB_CNT; i++) {
            int by = y + TB_Y0 + i * TB_STEP;
            if (!DispatchUtil.hit(mx, my, bx, by, TB_W0, TB_H)) continue;
            if (i == 1) {
                List<Component> tt = new ArrayList<>();
                tt.add(Component.translatable(tips[0]));
                int dm = screen.displayMode;
                for (int j = 0; j < 3; j++) { boolean sel = j == dm; tt.add(Component.translatable(dmSub[j]).withStyle(s -> s.withColor(sel ? DispatchScreen.C_HL : DispatchScreen.C_TD))); }
                g.renderTooltip(screen.font(), tt, java.util.Optional.empty(), mx, my);
            } else if (i == 2) {
                String[] sortSub = {"eecore.dispatch.sort.name", "eecore.dispatch.sort.count", "eecore.dispatch.sort.mod"};
                List<Component> st = new ArrayList<>();
                st.add(Component.translatable("eecore.dispatch.tooltip.sort_by"));
                int sm = screen.sortMode;
                for (int j = 0; j < 3; j++) { boolean sel = j == sm; st.add(Component.translatable(sortSub[j]).withStyle(s -> s.withColor(sel ? DispatchScreen.C_HL : DispatchScreen.C_TD))); }
                g.renderTooltip(screen.font(), st, java.util.Optional.empty(), mx, my);
            } else if (i == 3) {
                String[] dirSub = {"eecore.dispatch.dir.asc", "eecore.dispatch.dir.desc"};
                List<Component> ds = new ArrayList<>();
                ds.add(Component.translatable("eecore.dispatch.tooltip.sort_dir"));
                boolean sa = screen.sortAsc;
                for (int j = 0; j < 2; j++) { boolean sel = (j == 0) == sa; ds.add(Component.translatable(dirSub[j]).withStyle(s -> s.withColor(sel ? DispatchScreen.C_HL : DispatchScreen.C_TD))); }
                g.renderTooltip(screen.font(), ds, java.util.Optional.empty(), mx, my);
            } else if (i == 4) {
                String[] denSub = {"eecore.dispatch.density.short", "eecore.dispatch.density.medium", "eecore.dispatch.density.long", "eecore.dispatch.density.longest"};
                List<Component> dn = new ArrayList<>();
                dn.add(Component.translatable("eecore.dispatch.tooltip.density"));
                int di = screen.densityIdx;
                for (int j = 0; j < 4; j++) { boolean sel = j == di; dn.add(Component.translatable(denSub[j]).withStyle(s -> s.withColor(sel ? DispatchScreen.C_HL : DispatchScreen.C_TD))); }
                g.renderTooltip(screen.font(), dn, java.util.Optional.empty(), mx, my);
            } else if (i == 6) {
                List<Component> tt = new ArrayList<>();
                tt.add(Component.translatable("eecore.dispatch.tooltip.trash"));
                tt.add(Component.translatable("eecore.dispatch.tooltip.trash.desc").withStyle(s -> s.withColor(DispatchScreen.C_TD)));
                g.renderTooltip(screen.font(), tt, java.util.Optional.empty(), mx, my);
            } else {
                g.renderTooltip(screen.font(), Component.translatable(tips[i]), mx, my);
            }
            return;
        }
    }

    public boolean mouseClicked(double mx, double my, int x, int y, int btn) {
        int bx = x + TB_X(TB_W0);
        for (int i = 0; i < TB_CNT; i++) {
            int by = y + TB_Y0 + i * TB_STEP;
            if (DispatchUtil.hit(mx, my, bx, by, TB_W0, TB_H)) {
                screen.tbPressed = i;
                switch (i) {
                    case 0: screen.splitMerge = true; break;
                    case 1: screen.displayMode = btn == 0 ? (screen.displayMode + 1) % 3 : (screen.displayMode + 2) % 3; screen.onSearch(screen.searchComp.getValue()); screen.sendPref(); break;
                    case 2: screen.sortMode = (screen.sortMode + 1) % 3; screen.onSearch(screen.searchComp.getValue()); screen.sendPref(); break;
                    case 3: screen.sortAsc = !screen.sortAsc; screen.onSearch(screen.searchComp.getValue()); screen.sendPref(); break;
                    case 4: screen.densityIdx = (screen.densityIdx + 1) % 4; screen.rows = DispatchScreen.DENSITIES[screen.densityIdx];
                        double[] cx = new double[1], cy = new double[1];
                        org.lwjgl.glfw.GLFW.glfwGetCursorPos(screen.mc().getWindow().getWindow(), cx, cy);
                        DispatchScreen.savedMouseX = cx[0]; DispatchScreen.savedMouseY = cy[0];
                        net.neoforged.neoforge.network.PacketDistributor.sendToServer(new com.endlessepoch.core.network.SetGridDensityPacket(screen.menu().getPos(), screen.rows));
                        return true;
                    case 6:
                        screen.trashMode = !screen.trashMode;
                        if (!screen.trashMode) screen.trashPendingKey = null;
                        break;
                }
                return true;
            }
        }
        return false;
    }

    public void mouseReleased() { screen.tbPressed = -1; }
}
