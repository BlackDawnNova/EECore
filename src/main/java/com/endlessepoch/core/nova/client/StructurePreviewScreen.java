package com.endlessepoch.core.nova.client;

import com.endlessepoch.core.api.multiblock.EECoreCodec;
import com.endlessepoch.core.api.multiblock.MultiBlockPattern;
import com.endlessepoch.core.api.text.AnimatedText;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/** Read-only structure preview. Same visual style as MBVis, no editing. / 只读结构预览，与 MBVis 同风格，不可编辑。 */
public class StructurePreviewScreen extends MultiblockVisualizerScreen {

    private static final int GRID_COLS = 5;
    private static final int GRID_SCREEN_FRAC = 4;

    public StructurePreviewScreen(ResourceLocation id) {
        this(id, null, null);
    }

    public StructurePreviewScreen(ResourceLocation id, byte[] networkBytes) {
        this(id, networkBytes, null);
    }

    public StructurePreviewScreen(ResourceLocation id, byte[] networkBytes, Map<String, List<String>> alt) {
        super((ResourceLocation) null);
        MultiBlockPattern pat = null;
        if (networkBytes != null) try { pat = EECoreCodec.decode(networkBytes); } catch (Exception ignored) {}
        if (pat == null) pat = readDisk(id);
        if (pat != null && alt != null && !alt.isEmpty()) {
            for (var e : alt.entrySet()) {
                char c = e.getKey().charAt(0);
                for (String bid : e.getValue()) {
                    var block = net.minecraft.core.registries.BuiltInRegistries.BLOCK.get(ResourceLocation.parse(bid));
                    if (block != null) pat.addAlternatives(c, block.defaultBlockState());
                }
            }
        }
        if (pat != null) {
            patterns = new ArrayList<>();
            patterns.add(new AbstractMap.SimpleEntry<>(id, pat));
            selectedIndex = 0;
        }
    }

    private static MultiBlockPattern readDisk(ResourceLocation id) {
        try {
            Path p = Path.of("config", "eecore", "structures", id.getNamespace(), id.getPath() + ".ecs");
            if (Files.exists(p)) return EECoreCodec.read(p);
        } catch (Exception ignored) {}
        return null;
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderBackground(g, mx, my, pt);
        if (!immersive()) drawTitle(g);
        drawRenderArea(g);
        drawFloatingPanel(g, mx, my);
        drawHint(g);
    }

    private void drawTitle(GuiGraphics g) {
        int gridPx = Math.min(width, height) * GRID_SCREEN_FRAC / GRID_COLS;
        int cell = gridPx / GRID_COLS;
        int gx = (width - GRID_COLS * cell) / 2;
        int gy = (height - GRID_COLS * cell) / 2;
        int hL = gx + cell, hR = gx + (GRID_COLS - 1) * cell;
        int hT = gy, hB = gy + cell;
        String t = Component.translatable("eecore.preview.title").getString();
        Component rainbow = AnimatedText.rainbow(t);
        int tw = font.width(t);
        float s = (hR - hL - 12f) / tw;
        g.pose().pushPose();
        g.pose().translate(hL + (hR - hL) / 2f - tw * s / 2f, hT + cell / 2f - 5, 0);
        g.pose().scale(s, s, 1);
        g.drawString(font, rainbow, 0, 0, 0x00FF88);
        g.pose().popPose();
    }

    private void drawHint(GuiGraphics g) {
        String hint = Component.translatable("eecore.preview.hint").getString();
        int hw = font.width(hint);
        g.drawString(font, hint, (width - hw) / 2, height - 14, 0x66FFFFFF);
    }

    @Override
    public boolean keyPressed(int k, int s, int m) {
        if (k == 87 || k == 83) {
            if (!patterns.isEmpty()) {
                int h = patterns.get(0).getValue().height;
                if (k == 87) layerView = (layerView < h - 1) ? layerView + 1 : -1;
                else layerView = (layerView >= 0) ? layerView - 1 : h - 1;
            }
            return true;
        }
        if (k == 96 || k == 90 || k == 67 || k == 86 || k == 261) return false;
        return super.keyPressed(k, s, m);
    }
}
