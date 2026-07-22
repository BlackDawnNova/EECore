package com.endlessepoch.core.nova.client;

import com.endlessepoch.core.api.multiblock.MultiBlockPattern;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import org.joml.Matrix4f;

import java.util.*;

class FrameEditPanel {
    private static final int COLS = 4, ROWS = 4, CELL_W = 46, CELL_H = 22;

    private String searchQuery = "";
    private List<Block> searchResults = List.of();
    private int searchIdx = -1;
    private int searchScroll;

    boolean searchVisible;
    String searchTarget;

    private final Set<Block> componentBlocks = new LinkedHashSet<>();
    private Block casingBlock;

    void openSearch(String target) {
        searchTarget = target;
        searchVisible = true;
        searchQuery = "";
        updateSearchResults();
        searchIdx = -1;
        searchScroll = 0;
    }

    void closeSearch() {
        searchVisible = false;
        searchTarget = null;
    }

    Set<Block> getComponentBlocks() { return componentBlocks; }
    Block getCasingBlock() { return casingBlock; }

    void draw(GuiGraphics g, MultiBlockPattern pat, int mx, int my, int px, int py, int ph, Font font) {
        var mc = Minecraft.getInstance();
        var buf = mc.renderBuffers().bufferSource();
        var pose = g.pose().last().pose();

        if (searchVisible) {
            drawSearchGrid(g, font, buf, pose, px, py, ph, mx, my);
            return;
        }

        int flowY = py + 24;

        String casingLabel = casingBlock != null
                ? "§e" + Component.translatable("eecore.visualizer.frame.casing_label", casingBlock.getName().getString()).getString()
                : "§e" + Component.translatable("eecore.visualizer.frame.casing").getString() + ": §7[" + Component.translatable("eecore.visualizer.frame.casing_hint").getString() + "]";
        font.drawInBatch(Component.literal(casingLabel), px + 8, flowY, 0xFFFFFFFF, false, pose, buf, Font.DisplayMode.SEE_THROUGH, 0, 0xF000F0);
        flowY += 14;
        drawSmallBtn(g, buf, pose, "§a[" + Component.translatable("eecore.visualizer.frame.casing_select").getString() + "]", px + 8, flowY, mx, my, false);
        flowY += 18;

        g.hLine(px + 8, px + 188, flowY, 0xFF444466);
        flowY += 4;

        font.drawInBatch(Component.literal("§e" + Component.translatable("eecore.visualizer.frame.components").getString() + ":"), px + 8, flowY, 0xFFFFFFFF, false, pose, buf, Font.DisplayMode.SEE_THROUGH, 0, 0xF000F0);
        flowY += 14;

        List<Block> list = new ArrayList<>(componentBlocks);
        for (int i = 0; i < list.size(); i++) {
            Block b = list.get(i);
            String name = b.getName().getString();
            if (name.length() > 18) name = name.substring(0, 17) + ".";
            font.drawInBatch(Component.literal("§7" + name), px + 8, flowY, 0xCCCCCCCC, false, pose, buf, Font.DisplayMode.SEE_THROUGH, 0, 0xF000F0);
            font.drawInBatch(Component.literal("§c✕"), px + 170, flowY, 0xFFFF6666, false, pose, buf, Font.DisplayMode.SEE_THROUGH, 0, 0xF000F0);
            flowY += 12;
        }

        drawSmallBtn(g, buf, pose, "§a[+ " + Component.translatable("eecore.visualizer.frame.component_add").getString() + "]", px + 8, flowY, mx, my, false);
        buf.endBatch();
    }

    private void drawSmallBtn(GuiGraphics g, net.minecraft.client.renderer.MultiBufferSource.BufferSource buf,
                               Matrix4f mat, String label, int x, int y, double mx, double my, boolean disabled) {
        var font = Minecraft.getInstance().font;
        String clean = label.replaceAll("§.", "");
        int w = font.width(clean) + 12;
        boolean hovered = mx >= x && mx <= x + w && my >= y && my < y + 14;
        int bg = disabled ? 0x66000000 : hovered ? 0xAA333366 : 0x88222244;
        g.fill(x, y, x + w, y + 14, bg);
        g.renderOutline(x, y, w, 14, disabled ? 0xFF444444 : 0xFF7777AA);
        font.drawInBatch(Component.literal(label), x + 6, y + 2, disabled ? 0xFF555555 : 0xFFFFFFFF,
                false, mat, buf, Font.DisplayMode.SEE_THROUGH, 0, 0xF000F0);
    }

    private void drawSearchGrid(GuiGraphics g, Font font, net.minecraft.client.renderer.MultiBufferSource.BufferSource buf,
                                 Matrix4f pose, int px, int py, int ph, double mx, double my) {
        String showQ = searchQuery;
        if (showQ.length() > 26) showQ = showQ.substring(showQ.length() - 26);
        font.drawInBatch(Component.literal("§7> " + (searchQuery.isEmpty() ? "§8[" + Component.translatable("eecore.visualizer.frame.search").getString() + "]" : "§f" + showQ + "_")),
                px + 8, py + 24, 0xFFFFFFFF, false, pose, buf, Font.DisplayMode.SEE_THROUGH, 0, 0xF000F0);

        int gridX = px + 4, gridY = py + 38;
        int total = searchResults.size(), pageSize = COLS * ROWS;
        searchScroll = Math.min(searchScroll, Math.max(0, total - pageSize));
        for (int idx = 0; idx < pageSize; idx++) {
            int i = searchScroll + idx; if (i >= total) break;
            int ix = gridX + (idx % COLS) * CELL_W, iy = gridY + (idx / COLS) * CELL_H;
            Block block = searchResults.get(i);
            boolean hovered = mx(mx, my, ix, iy, CELL_W, CELL_H);
            g.fill(ix, iy, ix + CELL_W - 1, iy + CELL_H - 1, i == searchIdx ? 0x88444488 : hovered ? 0x44333366 : 0x22111133);
            if (i == searchIdx) g.renderOutline(ix, iy, CELL_W - 1, CELL_H - 1, 0xFFFFEE88);
            ItemStack stack = new ItemStack(block);
            if (!stack.isEmpty()) g.renderFakeItem(stack, ix + 2, iy + 2);
            if (hovered) font.drawInBatch(block.getName(), (int)mx + 8, (int)my - 12, 0xFFFFFFFF, false,
                    g.pose().last().pose(), buf, Font.DisplayMode.SEE_THROUGH, 0, 0xF000F0);
        }
        font.drawInBatch(Component.literal("§8[" + Component.translatable("eecore.visualizer.frame.esc").getString() + "]"), px + 140, py + ph - 14, 0xFFAAAAAA, false,
                pose, buf, Font.DisplayMode.SEE_THROUGH, 0, 0xF000F0);
    }

    boolean mouseClicked(double mx, double my, MultiBlockPattern pat, int px, int py, int ph) {
        if (searchVisible) {
            int gridX = px + 4, gridY = py + 38;
            int pageSize = COLS * ROWS;
            for (int idx = 0; idx < pageSize; idx++) {
                int i = searchScroll + idx; if (i >= searchResults.size()) break;
                int ix = gridX + (idx % COLS) * CELL_W, iy = gridY + (idx / COLS) * CELL_H;
                if (mx(mx, my, ix, iy, CELL_W, CELL_H)) {
                    Block block = searchResults.get(i);
                    if ("casing".equals(searchTarget)) { if (block != casingBlock) casingBlock = block; }
                    else if (block != casingBlock) { componentBlocks.add(block); }
                    closeSearch();
                    return true;
                }
            }
            closeSearch();
            return true;
        }

        if (mx(mx, my, px + 8, py + 38, 120, 14)) { openSearch("casing"); return true; }

        List<Block> list = new ArrayList<>(componentBlocks);
        int compY = py + 74;
        for (int i = 0; i < list.size(); i++) {
            if (mx(mx, my, px + 170, compY + i * 12, 16, 12)) { componentBlocks.remove(list.get(i)); return true; }
        }
        if (mx(mx, my, px + 8, compY + list.size() * 12, 120, 14)) { openSearch("component"); return true; }
        return false;
    }

    void applyToPattern(MultiBlockPattern pat, String casingTag, String componentTag) {
        if (casingBlock != null)
            applyBlockToTag(pat, casingTag, casingBlock);
        for (Block b : componentBlocks)
            applyBlockToTag(pat, componentTag, b);
    }

    private static void applyBlockToTag(MultiBlockPattern pat, String tag, Block block) {
        char newChar = findFreeChar(pat);
        pat.setDefinition(newChar, block.defaultBlockState());
        pat.setTags(newChar, java.util.List.of(tag));
    }

    private static char findFreeChar(MultiBlockPattern pat) {
        for (char c = 'B'; c <= '~'; c++)
            if (c != '#' && !pat.getDefinitions().containsKey(c)) return c;
        for (char c = 0xA0; c <= 0xFE; c++)
            if (!pat.getDefinitions().containsKey(c)) return c;
        return 'Z';
    }

    boolean keyPressed(int k, MultiBlockPattern pat) {
        if (!searchVisible) return false;
        if (k == 256) { closeSearch(); return true; }
        if (k == 259) { if (!searchQuery.isEmpty()) { searchQuery = searchQuery.substring(0, searchQuery.length() - 1); updateSearchResults(); } return true; }
        if (k == 257 || k == 335) {
            if (searchIdx >= 0 && searchIdx < searchResults.size()) {
                Block block = searchResults.get(searchIdx);
                if ("casing".equals(searchTarget)) { if (block != casingBlock) casingBlock = block; }
                else if (block != casingBlock) { componentBlocks.add(block); }
            }
            closeSearch(); return true;
        }
        return false;
    }

    boolean charTyped(char cp, MultiBlockPattern pat) {
        if (!searchVisible) return false;
        if (cp == 8) { if (!searchQuery.isEmpty()) { searchQuery = searchQuery.substring(0, searchQuery.length() - 1); updateSearchResults(); } return true; }
        searchQuery += cp; updateSearchResults(); return true;
    }

    boolean mouseScrolled(double mx, double my, double scroll) {
        if (!searchVisible) return false;
        searchScroll = Math.max(0, searchScroll - (scroll > 0 ? 4 : -4));
        return true;
    }

    private static boolean mx(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx <= x + w && my >= y && my <= y + h;
    }

    private void updateSearchResults() {
        String q = searchQuery.toLowerCase().trim();
        if (q.isEmpty()) {
            searchResults = BuiltInRegistries.BLOCK.stream().filter(b -> b.asItem() != net.minecraft.world.item.Items.AIR).toList();
            return;
        }
        String[] tokens = q.split("[\\s]+");
        searchResults = BuiltInRegistries.BLOCK.stream()
                .filter(b -> b.asItem() != net.minecraft.world.item.Items.AIR)
                .filter(b -> {
                    String name = b.getName().getString().toLowerCase();
                    String path = BuiltInRegistries.BLOCK.getKey(b).getPath();
                    String zh = I18n.get(b.getDescriptionId(), false);
                    if (PinyinUtil.matches(q, name) || PinyinUtil.matches(q, zh)) return true;
                    for (String t : tokens) {
                        if (!name.contains(t) && !path.contains(t) && !zh.contains(t)) return false;
                    }
                    return true;
                })
                .sorted((a, b) -> a.getName().getString().compareToIgnoreCase(b.getName().getString()))
                .toList();
        searchIdx = -1; searchScroll = 0;
    }
}
