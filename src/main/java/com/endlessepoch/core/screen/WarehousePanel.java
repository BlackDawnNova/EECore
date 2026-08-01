package com.endlessepoch.core.screen;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

class WarehousePanel extends SlotPanel {

    private static final int COLS = 9, CELL = 18, GX = 4, GY = 21;
    private boolean machineMode;
    private int rightMode;
    private int scroll, modeScroll;
    private net.minecraft.client.gui.components.EditBox search;
    private final java.util.List<ItemStack> filtered = new java.util.ArrayList<>();
    private final java.util.List<DispatchScreen.MachineEntry> filteredMachines = new java.util.ArrayList<>();

    WarehousePanel(DispatchScreen screen, int x, int y) {
        super(screen, x, y, "仓库"); w = 179; h = 81;
    }

    @Override public void render(GuiGraphics g, Font font, int mx, int my) {
        if (collapsed) { renderCollapsed(g, font, mx, my); return; }
        if (search == null) initSearch();
        search.setX(x + 78); search.setY(y + 6);
        g.blit(DispatchScreen.SPRITES, x, y, 0, 201, 179, 81, 512, 512);
        renderSidebar(g, font, x, y - 15, mx, my);
        if (machineMode) renderMachines(g, font, mx, my);
        else renderStorage(g, font, mx, my);
        String key = machineMode ? "eecore.dispatch.panel.machines"
                : (rightMode == 0 ? "eecore.dispatch.panel.storage" : "eecore.dispatch.panel.ae");
        Component rTitle = Component.translatable(key);
        int rw = font.width(rTitle);
        boolean clickable = !machineMode;
        boolean hov = clickable && DispatchUtil.hit(mx, my, x + 8, y + 5, rw, 10);
        g.drawString(font, rTitle, x + 8, y + 6, hov ? DispatchScreen.C_HL : 0xFF_404040, false);
        if (hov) {
            java.util.List<Component> rt = new java.util.ArrayList<>();
            rt.add(rTitle);
            rt.add(Component.translatable(rightMode == 0 ? "eecore.dispatch.panel.switch_ae" : "eecore.dispatch.panel.switch_storage").withStyle(s -> s.withColor(DispatchScreen.C_TD)));
            g.renderTooltip(font, rt, java.util.Optional.empty(), mx, my);
        }
        search.render(g, mx, my, 0);
        if (DispatchUtil.hit(mx, my, x + 78, y + 5, 72, 12)) {
            java.util.List<Component> stt = new java.util.ArrayList<>();
            if (machineMode) {
                stt.add(Component.translatable("eecore.dispatch.search_machines.title"));
                stt.add(Component.translatable("eecore.dispatch.search_machines.desc").withStyle(s -> s.withColor(DispatchScreen.C_TD)));
            } else {
                stt.add(Component.translatable("eecore.dispatch.search_patterns.title"));
                stt.add(Component.translatable("eecore.dispatch.search.hint_mod").withStyle(s -> s.withColor(DispatchScreen.C_TD)));
                stt.add(Component.translatable("eecore.dispatch.search.hint_tag").withStyle(s -> s.withColor(DispatchScreen.C_TD)));
                stt.add(Component.translatable("eecore.dispatch.search.hint_tooltip").withStyle(s -> s.withColor(DispatchScreen.C_TD)));
                stt.add(Component.translatable("eecore.dispatch.search.hint_fuzzy").withStyle(s -> s.withColor(DispatchScreen.C_TD)));
            }
            g.renderTooltip(font, stt, java.util.Optional.empty(), mx, my);
        }
    }

    /** 144 个已存样板（DUMMY 占位，尚未接真实数据） / stored patterns — DUMMY placeholders until real data */
    private static final java.util.List<ItemStack> PATTERNS = java.util.Collections.unmodifiableList(
            java.util.stream.IntStream.range(0, DispatchScreen.PATTERN_SLOTS)
                    .mapToObj(i -> DispatchScreen.DUMMY_PATTERNS[i % DispatchScreen.DUMMY_PATTERNS.length])
                    .toList());

    private void initSearch() {
        search = new net.minecraft.client.gui.components.EditBox(screen.font(), x + 78, y + 6, 72, 12, Component.empty());
        search.setBordered(false); search.setMaxLength(15);
        search.setTextColor(0xFF_EEEEEE);
        search.setResponder(q -> onSearch(q));
        updateHint();
    }

    private void updateHint() {
        String key = machineMode ? "eecore.dispatch.search_machines" : "eecore.dispatch.search_patterns";
        search.setHint(Component.translatable(key).withStyle(s -> s.withColor(0xFF_DBDCE0)));
    }

    /** 清空搜索+取消光标（模式/视图切换时防残留） / clear search and blur on mode switch */
    private void clearSearch() {
        search.setValue("");
        search.setFocused(false);
        onSearch("");
        updateHint();
    }

    private void onSearch(String q) {
        String lq = q.trim().toLowerCase(java.util.Locale.ROOT);
        if (machineMode) {
            filteredMachines.clear();
            for (var m : DispatchScreen.MACHINES) {
                String name = m.name().toLowerCase(java.util.Locale.ROOT);
                String status = Component.translatable(m.status() == 1 ? "eecore.dispatch.machine.running" : (m.status() == 2 ? "eecore.dispatch.machine.offline" : "eecore.dispatch.machine.idle")).getString();
                if (lq.isEmpty() || name.contains(lq) || status.contains(lq)
                        || com.endlessepoch.core.nova.client.PinyinUtil.matches(lq, name))
                    filteredMachines.add(m);
            }
            modeScroll = 0;
            return;
        }
        filtered.clear();
        if (lq.isEmpty()) { filtered.addAll(PATTERNS); scroll = 0; return; }
        boolean modF = lq.startsWith("@"), tagF = lq.startsWith("#"), tooltipF = lq.startsWith("$"), starF = lq.startsWith("*");
        String term = (modF || tagF || tooltipF || starF) ? lq.substring(1) : lq;
        for (var s : PATTERNS) {
            String name = s.getDisplayName().getString().toLowerCase(java.util.Locale.ROOT);
            boolean match;
            if (modF) match = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(s.getItem()).getNamespace().toLowerCase(java.util.Locale.ROOT).contains(term);
            else if (tagF) match = s.getItem().builtInRegistryHolder().tags().anyMatch(t -> t.location().toString().toLowerCase(java.util.Locale.ROOT).contains(term));
            else if (tooltipF) match = s.getDescriptionId().toLowerCase(java.util.Locale.ROOT).contains(term);
            else match = name.contains(term) || s.getDescriptionId().toLowerCase(java.util.Locale.ROOT).contains(term) || com.endlessepoch.core.nova.client.PinyinUtil.matches(term, name);
            if (match) filtered.add(s);
        }
        scroll = 0;
    }

    private void renderSidebar(GuiGraphics g, Font font, int sx, int sy, int mx, int my) {
        g.blit(DispatchScreen.SPRITES, sx, sy, 213, 134, 2, 17, 512, 512);
        g.blit(DispatchScreen.SPRITES, sx + 2, sy, 215, 134, 15, 17, 512, 512);
        g.blit(DispatchScreen.SPRITES, sx + 17, sy, 215, 134, 15, 17, 512, 512);
        g.blit(DispatchScreen.SPRITES, sx + 32, sy, 230, 134, 4, 17, 512, 512);
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
        int mbx = sx + 18, mby = sy + 3;
        boolean mh = hit(mx, my, mbx, mby, 13, 14);
        boolean mdown = mh && btnDown();
        int mu = mdown ? 198 : (mh ? 185 : 172);
        g.blit(DispatchScreen.SPRITES, mbx, mby + (mdown ? 1 : 0), mu, 138, 13, 14, 512, 512);
        g.blit(DispatchScreen.SPRITES, mbx + 3, mby + 2 + (mdown ? 1 : 0), 198, 175, 7, 8, 512, 512);
        if (mh) g.renderTooltip(font, Component.translatable("eecore.dispatch.panel.machines"), mx, my);
    }

    private boolean btnDown() { return org.lwjgl.glfw.GLFW.glfwGetMouseButton(screen.mc().getWindow().getWindow(), 0) == 1; }

    private void renderStorage(GuiGraphics g, Font font, int mx, int my) {
        var items = filtered.isEmpty() ? PATTERNS : filtered;
        int total = (int) Math.ceil(items.size() / (double) COLS);
        int vis = 3;
        for (int r = 0; r < vis; r++) {
            int ry = y + GY + r * CELL;
            if (r == 0) g.blit(DispatchScreen.SPRITES, x + GX, ry, 0, 0, 162, 18, 512, 512);
            else if (r == vis - 1) g.blit(DispatchScreen.SPRITES, x + GX, ry, 0, 18, 162, 18, 512, 512);
            else g.blit(DispatchScreen.SPRITES, x + GX, ry, 0, 36, 162, 18, 512, 512);
            for (int c = 0; c < COLS; c++) {
                int idx = (scroll + r) * COLS + c;
                if (idx >= items.size()) break;
                int sx = x + GX + 1 + c * CELL, sy = ry + 1;
                g.renderItem(items.get(idx), sx, sy);
                DispatchUtil.slotHover(g, screen.mc(), sx, sy, 16, 16, mx, my);
            }
        }
        int maxOff = Math.max(0, total - vis);
        if (maxOff > 0) {
            int sbX = x + 169, sbY = y + GY, sbH = vis * CELL;
            int hY = sbY + (int) ((long) (sbH - 15) * scroll / maxOff);
            g.blit(DispatchScreen.SPRITES, sbX - 1, hY, 172, 0, 7, 15, 512, 512);
        }
    }

    private void renderMachines(GuiGraphics g, Font font, int mx, int my) {
        var machines = filteredMachines.isEmpty() ? DispatchScreen.MACHINES : filteredMachines;
        MachineListRenderer.render(g, font, screen, x + GX, y + GY, 162, mx, my,
                machines, modeScroll, 3, -5, -5, 8, 1);
    }

    boolean mouseScrolled(double mx, double my, double sy) {
        if (collapsed) return false;
        if (!DispatchUtil.hit(mx, my, x + GX, y + GY, 162, 3 * CELL)) return false;
        if (machineMode) {
            var machines = filteredMachines.isEmpty() ? DispatchScreen.MACHINES : filteredMachines;
            int maxM = MachineListRenderer.maxScroll(machines, 3);
            if (maxM > 0) modeScroll = Math.clamp(modeScroll - (int) Math.signum(sy), 0, maxM);
        } else {
            var items = filtered.isEmpty() ? PATTERNS : filtered;
            int total = (int) Math.ceil(items.size() / (double) COLS), maxS = Math.max(0, total - 3);
            if (maxS > 0) scroll = Math.clamp(scroll - (int) Math.signum(sy), 0, maxS);
        }
        return true;
    }

    @Override public boolean mouseClicked(double mx, double my, int btn) {
        int bx = collapsed ? x + 40 : x + w - 13;
        if (DispatchUtil.hit(mx, my, bx, y + 3, 9, 9)) { collapsed = !collapsed; return true; }
        if (!collapsed) {
            int sy = y - 15;
            if (DispatchUtil.hit(mx, my, x + 3, sy + 1, 13, 14)) { if (btn == 0) collapsed = true; else onSidebarRightClick(); return true; }
            if (DispatchUtil.hit(mx, my, x + 18, sy + 3, 13, 14)) { machineMode = !machineMode; clearSearch(); return true; }
            if (!machineMode) {
                int rw = screen.font().width(Component.translatable(rightMode == 0 ? "eecore.dispatch.panel.storage" : "eecore.dispatch.panel.ae"));
                if (DispatchUtil.hit(mx, my, x + 8, y + 5, rw, 10)) { rightMode = 1 - rightMode; clearSearch(); return true; }
            }
            if (search != null) {
                boolean hit = search.isMouseOver(mx, my);
                search.setFocused(hit);
                if (hit) { search.mouseClicked(mx, my, btn); return true; }
            }
        }
        int cw = collapsed ? 53 : w, ch = collapsed ? 16 : h;
        if (DraggablePanel.hit(mx, my, x, y, cw, ch)) { dragging = true; dragOffX = (int) mx - x; dragOffY = (int) my - y; return true; }
        return false;
    }

    @Override public boolean mouseClicked(double mx, double my) { return mouseClicked(mx, my, 0); }
    @Override protected void onSidebarRightClick() { screen.splitMerge = false; }
    @Override int sidebarW() { return 36; }

    boolean charTyped(char cp, int mod) { return search != null && search.charTyped(cp, mod); }
    boolean keyPressed(int key, int scan, int mod) {
        if (search == null || !search.isFocused()) return false;
        if (key == 256) { search.setFocused(false); return true; }
        return search.keyPressed(key, scan, mod);
    }
}
