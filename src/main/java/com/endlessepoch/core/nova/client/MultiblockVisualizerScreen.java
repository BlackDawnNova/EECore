package com.endlessepoch.core.nova.client;

import com.endlessepoch.core.api.multiblock.MultiBlockPattern;
import com.endlessepoch.core.api.multiblock.MultiBlockRegistry;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.joml.Vector4f;

import java.util.*;

/**
 * 3D multiblock visualizer with click-to-inspect.
 * 多方块结构3D预览器，支持点击拾取方块。
 * Pick is computed inline during rendering (same PoseStack = no projection mismatch).
 */
@OnlyIn(Dist.CLIENT)
public class MultiblockVisualizerScreen extends Screen {

    private static final int GRID_COLS = 5;
    private static final int GRID_SCREEN_FRAC = 4;
    private static final int GRID_CELL_ROWS = 3;
    private static final int PADDING = 4;
    private static final int LINE_HEIGHT = 12;

    private static final int COL_PANEL_BG    = 0xCC111111;
    private static final int COL_BORDER      = 0xFF003300;
    private static final int COL_RENDER_BDR  = 0xFF00AA44;
    private static final int COL_ACCENT      = 0x00FF88;
    private static final int COL_DIM         = 0x666666;
    private static final int COL_HINT        = 0x88FFFFFF;
    private static final int COL_OVERLAY     = 0x66FFFFFF;
    private static final int COL_SCROLL_ARW  = 0x44FFAA44;
    private static final int COL_WHITE       = 0xFFFFFFFF;
    private static final int PACKED_LIGHT    = 0xF000F0;

    private static final float ROT_X_INIT    = 150f;
    private static final float ROT_Y_INIT    = 135f;
    private static final float ROT_SPEED     = 0.4f;
    private static final float ROT_X_MIN     = 90f;
    private static final float ROT_X_MAX     = 270f;
    private static final float ZOOM_INIT     = 1f;
    private static final float ZOOM_MIN      = 0.01f;
    private static final float ZOOM_MAX      = 100f;
    private static final float ZOOM_IMMERSIVE= 3f;
    private static final float ZOOM_STEP_FINE = 0.01f;
    private static final float ZOOM_STEP_NORM = 0.1f;
    private static final float ZOOM_STEP_COARSE = 1f;
    private static final float ZOOM_MED_THR  = 5f;
    private static final float ZOOM_LOW_THR  = 0.2f;

    private static final float CAM_DIST_FACTOR = 2000f;
    private static final float CAM_DIST_EPS    = 3f;
    private static final float CAM_DIST_SCALE  = 0.02f;
    private static final float BLOCK_PX_FACTOR = 0.5f;
    private static final float NEAR_PLANE      = 100f;
    private static final float FAR_PLANE       = 4000f;

    private static final float HILITE_LINE_WIDTH = 8f;
    private static final int   HILITE_BUFSZ      = 4608;
    private static final long  HILITE_FLASH_MS   = 300L;
    private static final float HILITE_ON         = 1.0f;
    private static final float HILITE_OFF        = 0.35f;
    private static final float GLOW_INFLATE      = 0.10f;
    private static final float GLOW_INTENSITY    = 0.25f;
    private static final float GLOW_ALPHA        = 0.12f;
    private static final int   HILITE_LAYERS     = 3;
    private static final float LAYER_OFFSET_STEP = 0.028f;
    private static final float LAYER_OUTER_FACTOR= 0.45f;
    private static final float LAYER_INNER_ALPHA = 0.95f;
    private static final float LAYER_OUTER_ALPHA = 0.35f;
    private static final long  K_PULSE_MS        = 500L;
    private static final RenderType CTRL_MARKER   = RenderType.create(
            "eecore_visualizer_ctrl_marker",
            DefaultVertexFormat.POSITION_COLOR,
            VertexFormat.Mode.TRIANGLES,
            6144, false, false,
            RenderType.CompositeState.builder()
                    .setShaderState(RenderStateShard.POSITION_COLOR_SHADER)
                    .setTransparencyState(RenderStateShard.TRANSLUCENT_TRANSPARENCY)
                    .setDepthTestState(RenderStateShard.NO_DEPTH_TEST)
                    .setWriteMaskState(RenderStateShard.COLOR_WRITE)
                    .setCullState(RenderStateShard.NO_CULL)
                    .createCompositeState(false)
    );

    private static final int KEY_G  = 71;
    private static final int KEY_W  = 87;
    private static final int KEY_S  = 83;
    private static final int KEY_UP = 265;
    private static final int KEY_DN = 264;
    private static final int KEY_DEL = 261;

    // Confirm dialog state / 确认弹窗
    private boolean confirmShow = false;
    private boolean confirmIsSave = false;
    private int confirmTargetIdx = -1;
    private String saveFileName = "";
    private static final int CONFIRM_W = 200, CONFIRM_H = 80;

    // Edit mode toggle / 编辑模式开关 (Alt+`)
    private boolean editModeActive = false;

    // Replace mode / 替换模式
    private boolean replaceMode = false;
    private boolean batchReplace = false;
    private BlockPos replaceSource = null;

    // Tag editing mode / 标签编辑模式
    private boolean tagModeActive = false;
    private String tagInput = "";

    private record PickRay(Vector3f start, Vector3f direction) {}

    private static final RenderType HIGHLIGHT_LINES = RenderType.create(
            "eecore_visualizer_highlight_lines",
            DefaultVertexFormat.POSITION_COLOR,
            VertexFormat.Mode.DEBUG_LINES,
            HILITE_BUFSZ,
            false, false,
            RenderType.CompositeState.builder()
                    .setShaderState(RenderStateShard.POSITION_COLOR_SHADER)
                    .setLineState(new RenderStateShard.LineStateShard(OptionalDouble.of(HILITE_LINE_WIDTH)))
                    .setTransparencyState(RenderStateShard.TRANSLUCENT_TRANSPARENCY)
                    .setDepthTestState(RenderStateShard.NO_DEPTH_TEST)
                    .setWriteMaskState(RenderStateShard.COLOR_DEPTH_WRITE)
                    .setCullState(RenderStateShard.NO_CULL)
                    .createCompositeState(false)
    );

     List<Map.Entry<ResourceLocation, MultiBlockPattern>> patterns;
    protected int selectedIndex;

    private float rotX = ROT_X_INIT, rotY = ROT_Y_INIT;
    protected float userZoom = ZOOM_INIT;
    private boolean mouseHeld = false;

    private int cellSize, gridX, gridY;
    private int listL, listR, listT, listB;
    private int renderL, renderR, renderT, renderB;
    private int futureL, futureR, futureT, futureB;
    private EECoreSceneWorld cachedScene;
    private ResourceLocation cachedPatternId;
    private int listScrollOffset;

    private BlockPos pickResult = null;
    private BlockState pickBlockState = null;
    private boolean pendingPick = false;
    private int pendingPickMx, pendingPickMy;
    private boolean clickActive = false;
    private int clickStartX, clickStartY;

    // Floating info panel / 浮动信息面板 — draggable, always on top
    private boolean panelVisible = false;
    private int panelX = 10, panelY = 10;
    private static final int PANEL_W = 200, PANEL_H = 150;
    private static final int PANEL_EDIT_H = 180;
    private static final int PANEL_TAG_H = 260;
    private int panelH() {
        if (replaceMode) return PANEL_EDIT_H;
        if (tagModeActive) return PANEL_TAG_H;
        return PANEL_H;
    }

    private String searchQuery = "";
    private java.util.List<net.minecraft.world.level.block.Block> searchResults = java.util.List.of();
    private int searchIdx = -1;
    private int searchScroll = 0;
    // Layer view (-1=all)
    protected int layerView = -1;

    // Undo stack / 撤销栈 — push reverse operation per replace
    private final java.util.ArrayDeque<Runnable> undoStack = new java.util.ArrayDeque<>();
    private boolean undoAvailable = false;
    private static final int PANEL_TITLE_H = 14;
    private boolean draggingPanel = false;
    private int dragOffX, dragOffY;

    private double bestPickRayT;
    private BlockPos bestPickPos;
    private BlockState bestPickState;

    /**
     * Immersive mode when zoom > threshold — render bounds expand to full screen.
     * 沉浸模式: 缩放超过阈值时渲染区扩展到全屏。
     */
    protected boolean immersive() { return userZoom > ZOOM_IMMERSIVE; }

    private int rL() { return immersive() ? 0 : renderL; }
    private int rR() { return immersive() ? width : renderR; }
    private int rT() { return immersive() ? 0 : renderT; }
    private int rB() { return immersive() ? height : renderB; }

    public MultiblockVisualizerScreen() {
        this(null);
    }

    public MultiblockVisualizerScreen(ResourceLocation selectedPatternId) {
        super(Component.translatable("eecore.visualizer.title"));
        this.patterns = new ArrayList<>(MultiBlockRegistry.getAll().entrySet());
        this.selectedIndex = findPatternIndex(selectedPatternId);
    }

    private int findPatternIndex(ResourceLocation patternId) {
        if (patterns.isEmpty()) return -1;
        if (patternId == null) return 0;
        for (int i = 0; i < patterns.size(); i++) {
            if (patterns.get(i).getKey().equals(patternId)) return i;
        }
        return 0;
    }

    @Override
    protected void init() {
        recalcLayout();
        listScrollOffset = 0;
    }

    @Override
    public void resize(Minecraft mc, int w, int h) {
        super.resize(mc, w, h);
        recalcLayout();
    }

    private void recalcLayout() {
        int gridPx = Math.min(width, height) * GRID_SCREEN_FRAC / GRID_COLS;
        cellSize = gridPx / GRID_COLS;
        gridX = (width - GRID_COLS * cellSize) / 2;
        gridY = (height - GRID_COLS * cellSize) / 2;

        listL = gridX; listR = gridX + cellSize - 1;
        listT = gridY + cellSize; listB = gridY + GRID_CELL_ROWS * cellSize - 1;
        renderL = gridX + cellSize; renderR = gridX + (GRID_COLS - 1) * cellSize - 1;
        renderT = gridY + cellSize; renderB = gridY + GRID_CELL_ROWS * cellSize - 1;
        futureL = gridX + (GRID_COLS - 1) * cellSize; futureR = gridX + GRID_COLS * cellSize - 1;
        futureT = gridY + cellSize; futureB = gridY + GRID_CELL_ROWS * cellSize - 1;
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderBackground(g, mx, my, pt);
        if (!immersive()) drawHeader(g);
        if (!immersive()) drawList(g);
        drawRenderArea(g);
        if (!immersive()) drawFuture(g);

        if (selectedIndex >= 0 && pickResult == null
                && mx >= rL() && mx < rR() && my >= rT() && my < rB()) {
            Component hint = Component.translatable("eecore.visualizer.click_hint");
            int hw = font.width(hint);
            g.drawString(font, hint, mx - hw / 2, my - LINE_HEIGHT, COL_HINT);
        }

        if (immersive()) {
            g.drawString(font, Component.translatable("eecore.visualizer.immersive_hint"), 6, height - 16, COL_OVERLAY);
        }
        drawLayerControls(g);

        drawFloatingPanel(g, mx, my);
        drawConfirmDialog(g);
    }

    private void drawHeader(GuiGraphics g) {
        int hL = gridX + cellSize, hR = gridX + (GRID_COLS - 1) * cellSize;
        int hT = gridY, hB = gridY + cellSize;
        String title = Component.translatable("eecore.visualizer.title").getString();
        Component rainbow = com.endlessepoch.core.api.text.AnimatedText.rainbow(title);
        int tw = font.width(title);
        float scale = (hR - hL - 12f) / tw;
        g.pose().pushPose();
        g.pose().translate(hL + (hR - hL) / 2f - tw * scale / 2f, hT + cellSize / 2f - 5, 0);
        g.pose().scale(scale, scale, 1);
        g.drawString(font, rainbow, 0, 0, COL_ACCENT);
        g.pose().popPose();
    }

    private void drawList(GuiGraphics g) {
        g.fill(listL, listT, listR, listB, COL_PANEL_BG);
        g.fill(listL, listT, listR, listT + 1, COL_BORDER);
        g.fill(listL, listB - 1, listR, listB, COL_BORDER);
        g.fill(listL, listT, listL + 1, listB, COL_BORDER);
        g.fill(listR - 1, listT, listR, listB, COL_BORDER);
        g.enableScissor(listL + 1, listT + 1, listR - 1, listB - 1);
        int x = listL + PADDING, y = listT + PADDING;
        g.drawString(font, Component.translatable("eecore.visualizer.patterns"), x, y - 2, COL_ACCENT);
        if (patterns.isEmpty()) {
            g.drawString(font, Component.translatable("eecore.visualizer.empty"), x, y + 10, COL_DIM);
            g.disableScissor();
            return;
        }
        int visibleCount = (listB - listT) / LINE_HEIGHT;
        if (selectedIndex < listScrollOffset) listScrollOffset = selectedIndex;
        if (selectedIndex >= listScrollOffset + visibleCount) listScrollOffset = selectedIndex - visibleCount + 1;
        listScrollOffset = Math.max(0, Math.min(listScrollOffset, patterns.size() - visibleCount));
        int end = Math.min(listScrollOffset + visibleCount, patterns.size());
        for (int i = listScrollOffset; i < end; i++) {
            int c = i == selectedIndex ? COL_ACCENT : COL_DIM;
            String name = patterns.get(i).getKey().getPath();
            if (name.length() > LINE_HEIGHT) name = name.substring(0, LINE_HEIGHT - 1) + ".";
            g.drawString(font, name, x, y + 10 + (i - listScrollOffset) * LINE_HEIGHT, c);
        }
        g.disableScissor();
        if (listScrollOffset > 0) g.drawString(font, "▲", listR - 8, listT + 2, COL_SCROLL_ARW);
        if (end < patterns.size()) g.drawString(font, "▼", listR - 8, listB - LINE_HEIGHT, COL_SCROLL_ARW);
    }

    protected void drawRenderArea(GuiGraphics g) {
        int erL = rL(), erR = rR(), erT = rT(), erB = rB();
        int rwLoc = erR - erL, rhLoc = erB - erT;
        int vpSize = Math.min(rwLoc, rhLoc);
        if (selectedIndex < 0) { drawRenderBorder(g); return; }
        MultiBlockPattern pat = patterns.get(selectedIndex).getValue();
        float blockDiag = (float) Math.sqrt(pat.width*pat.width + pat.height*pat.height + pat.depth*pat.depth);
        float cameraDist = CAM_DIST_FACTOR * (float)(vpSize * Math.sqrt(2)) / (blockDiag + CAM_DIST_EPS) * userZoom * CAM_DIST_SCALE;
        float blockPx = cameraDist * BLOCK_PX_FACTOR;
        // Rotation LOD — active while mouse held in render area / 旋转LOD — 左键按住期间持续生效
        boolean rotating = mouseHeld;
        int rotationSkip = 1;
        if (rotating && cachedScene != null) {
            int nonAir = cachedScene.getPositions().size();
            if (nonAir > 50_000) {
                rotationSkip = Math.max(1, nonAir / 30_000);
                rotationSkip = Math.min(rotationSkip, 32);
            }
        }

        ResourceLocation id = patterns.get(selectedIndex).getKey();
        if (cachedScene == null || !id.equals(cachedPatternId)) {
            cachedScene = new EECoreSceneWorld(pat);
            cachedPatternId = id;
        }

        RenderSystem.enableDepthTest();
        PoseStack model = new PoseStack();
        model.translate(erL + rwLoc / 2f, erT + rhLoc / 2f, 0);
        float halfVp = vpSize / 2f;
        Matrix4f projection = new Matrix4f().setOrtho(-halfVp, halfVp, -halfVp, halfVp, NEAR_PLANE, FAR_PLANE);
        model.mulPose(projection);
        model.translate(0, 0, -cameraDist);
        model.mulPose(new Quaternionf().rotateX((float) Math.toRadians(-rotX)));
        model.mulPose(new Quaternionf().rotateY((float) Math.toRadians(rotY)));
        model.scale(blockPx, blockPx, blockPx);
        model.translate(-pat.width*0.5f, -pat.height*0.5f, -pat.depth*0.5f);
        Matrix4f patternToScreen = new Matrix4f(model.last().pose());

        // Back-face culling — compute camera direction in pattern space / 背面剔除 — 计算相机在结构空间的方向
        float radX = (float) Math.toRadians(-rotX);
        float radY = (float) Math.toRadians(rotY);
        float cosX = (float) Math.cos(radX), sinX = (float) Math.sin(radX);
        float dirX = -cosX * (float) Math.sin(radY);
        float dirY = -sinX;
        float dirZ = -cosX * (float) Math.cos(radY);
        int visibleMask = 0;
        if (dirX > 0.001f) visibleMask |= EECoreSceneWorld.FACE_PX;
        if (dirX < -0.001f) visibleMask |= EECoreSceneWorld.FACE_NX;
        if (dirY > 0.001f) visibleMask |= EECoreSceneWorld.FACE_UP;
        if (dirY < -0.001f) visibleMask |= EECoreSceneWorld.FACE_DN;
        if (dirZ > 0.001f) visibleMask |= EECoreSceneWorld.FACE_PZ;
        if (dirZ < -0.001f) visibleMask |= EECoreSceneWorld.FACE_NZ;

        var renderer = Minecraft.getInstance().getBlockRenderer();
        var buf = Minecraft.getInstance().renderBuffers().bufferSource();

        Vector3f pickRayStart = null;
        Vector3f pickRayDirection = null;
        if (pendingPick) {
            bestPickRayT = Double.MAX_VALUE;
            bestPickPos = null;
            bestPickState = null;
            PickRay pickRay = createPickRay(patternToScreen, pendingPickMx, pendingPickMy, pat.width, pat.height, pat.depth);
            pickRayStart = pickRay.start();
            pickRayDirection = pickRay.direction();
        }

        for (var pos : cachedScene.getSurfacePositions()) {
                    int x = pos.getX(), y = pos.getY(), z = pos.getZ();
                    if (layerView >= 0 && y != layerView) continue;

                    if ((cachedScene.getExposedFaceMask(pos) & visibleMask) == 0) continue;

                    if (rotationSkip > 1) {
                        if (((x * 31 + y * 37 + z * 41) & 0xFF) % rotationSkip != 0) continue;
                    }

                    BlockState st = cachedScene.getBlockState(pos);

                    model.pushPose();
                    model.translate(x, y, z);

                    if (pendingPick) {
                        double hit = intersectRayAabb(pickRayStart, pickRayDirection,
                                x, y, z, x + 1, y + 1, z + 1);
                        if (hit >= 0 && hit < bestPickRayT) {
                            bestPickRayT = hit;
                            bestPickPos = pos;
                            bestPickState = st;
                        }
                    }

                    renderer.renderSingleBlock(st, model, buf, PACKED_LIGHT, OverlayTexture.NO_OVERLAY);
                    model.popPose();
                }

        if (pendingPick) {
            pendingPick = false;
            if (bestPickPos != null) {
                pickResult = bestPickPos;
                pickBlockState = bestPickState;
                if (replaceMode) {
                    replaceMode = false;
                    searchQuery = "";
                    searchResults = java.util.List.of();
                    searchIdx = -1;
                    searchScroll = 0;
                }
                panelVisible = true;
            }
        }

        buf.endBatch();

        // Controller marker / 控制器标记 — pulsing translucent envelope (red/blue)
        boolean kPulse = (System.currentTimeMillis() / K_PULSE_MS) % 2 == 0;
        float kR = kPulse ? 1.0f : 0.25f;
        float kB = kPulse ? 0.25f : 1.0f;
        VertexConsumer kVc = buf.getBuffer(CTRL_MARKER);
        for (var cPos : cachedScene.getControllerPositions()) {
                    int x = cPos.getX(), y = cPos.getY(), z = cPos.getZ();
                    if (layerView >= 0 && layerView != y) continue;
                    var st2 = cachedScene.getBlockState(cPos);
                    if (!com.endlessepoch.core.api.multiblock.MultiBlockRegistry.getControllerBlocks().contains(st2.getBlock())) continue;
                    model.pushPose();
                    model.translate(x, y, z);
                    renderSolidBox(model.last(), kVc,
                            0.0, 0.0, 0.0, 1.0, 1.0, 1.0,
                            kR, 0.1f, kB, 0.35f);
                    model.popPose();
                }
        buf.endBatch(CTRL_MARKER);

        renderPickedBlockHighlight(model, buf);
        RenderSystem.disableDepthTest();
        if (!immersive()) g.drawString(font, Component.translatable("eecore.visualizer.controller_label"), rL() + PADDING, rT() + PADDING, COL_WHITE);
        if (!immersive()) drawRenderBorder(g);
    }

    private static boolean onBtn(double mx, double my, int x, int y, int w) {
        return mx >= x - 1 && mx <= x + w + 1 && my >= y - 1 && my <= y + 14;
    }

    private void drawButton(GuiGraphics g, net.minecraft.client.renderer.MultiBufferSource.BufferSource buf,
                             org.joml.Matrix4f mat, String label, int x, int y, int w, boolean disabled) {
        int h = 16;
        g.fill(x, y, x + w, y + h, disabled ? 0x66000000 : 0xCC000000);
        g.renderOutline(x, y, w, h, disabled ? 0xFF444444 : 0xFF999999);
        g.renderOutline(x + 1, y + 1, w - 2, h - 2, disabled ? 0x22FFFFFF : 0x33FFFFFF);
        String clean = label.replaceAll("§.", "");
        int tw = font.width(clean);
        int color = disabled ? 0xFF555555 : 0xFFFFFFFF;
        font.drawInBatch(Component.literal(label),
                x + (w - tw) / 2f, y + (h - 8) / 2f + 1, color,
                false, mat, buf, Font.DisplayMode.SEE_THROUGH, 0, 0xF000F0);
    }

    private void drawDialogBox(GuiGraphics g, int cx, int cy, int cw, int ch, Component title) {
        g.fill(cx, cy, cx + cw, cy + ch, 0xCC000000);
        g.renderOutline(cx, cy, cw, ch, 0xFFCCCCCC);
        g.renderOutline(cx + 1, cy + 1, cw - 2, ch - 2, 0x33FFFFFF);
        g.drawCenteredString(font, title, cx + cw / 2, cy + 8, 0xFFFFD700);
        g.hLine(cx + 10, cx + cw - 10, cy + 22, 0xFF888888);
    }

    protected void drawLayerControls(GuiGraphics g) {
        if (selectedIndex < 0) return;
        int bw = 24, bh = 28, gap = 3, bx = width - bw - 10, by = height / 2 - bh - gap / 2;
        int panelW = bw + 4, panelH = bh * 2 + gap + 4;

        var mc = Minecraft.getInstance();
        mc.renderBuffers().bufferSource().endBatch();
        RenderSystem.clearDepth(1.0);
        RenderSystem.clear(256, false);

        g.fill(bx - 2, by - 2, bx + bw + 2, by + bh * 2 + gap + 2, 0xAA111133);
        g.renderOutline(bx - 2, by - 2, panelW, panelH, 0xFF555577);

        var buf = mc.renderBuffers().bufferSource();
        var pose = g.pose().last().pose();

        g.fill(bx, by, bx + bw, by + bh, 0xFF333366);
        g.renderOutline(bx, by, bw, bh, 0xFF7777AA);
        String num = layerView < 0 ? "ALL" : String.valueOf(layerView);
        font.drawInBatch(Component.literal(num), bx + (bw - font.width(num)) / 2f, by + (bh - 8) / 2f + 1, 0xFFFFFFFF,
                false, pose, buf, Font.DisplayMode.SEE_THROUGH, 0, 0xF000F0);

        int ry = by + bh + gap;
        g.fill(bx, ry, bx + bw, ry + bh, layerView < 0 ? 0xFF4444AA : 0xFF222244);
        g.renderOutline(bx, ry, bw, bh, 0xFF7777AA);
        font.drawInBatch(Component.literal("↻"), bx + (bw - font.width("↻")) / 2f, ry + (bh - 8) / 2f + 1, 0xFFFFFFFF,
                false, pose, buf, Font.DisplayMode.SEE_THROUGH, 0, 0xF000F0);

        buf.endBatch();
    }

    private void drawConfirmDialog(GuiGraphics g) {
        if (!confirmShow || confirmTargetIdx < 0) return;

        var mc = Minecraft.getInstance();
        mc.renderBuffers().bufferSource().endBatch();
        RenderSystem.clearDepth(1.0);
        RenderSystem.clear(256, false);

        int cx = (width - CONFIRM_W) / 2, cy = (height - CONFIRM_H) / 2;
        String title = confirmIsSave ? "§e确认保存修改?" : "§e确认删除此结构?";
        drawDialogBox(g, cx, cy, CONFIRM_W, CONFIRM_H, Component.literal(title));

        var buf = mc.renderBuffers().bufferSource();
        var pose = g.pose().last().pose();

        String disp = confirmIsSave ? "§f" + saveFileName + "§7_" : "§7" + patterns.get(confirmTargetIdx).getKey().getPath();
        font.drawInBatch(Component.literal(disp), cx + 12, cy + 32, 0xCCCCCCCC, false,
                pose, buf, Font.DisplayMode.SEE_THROUGH, 0, 0xF000F0);

        font.drawInBatch(Component.literal("§a✔ 确认"), cx + 20, cy + 58, 0xFFFFFFFF, false,
                pose, buf, Font.DisplayMode.SEE_THROUGH, 0, 0xF000F0);
        font.drawInBatch(Component.literal("§c✘ 取消"), cx + CONFIRM_W - 60, cy + 58, 0xFFFFFFFF, false,
                pose, buf, Font.DisplayMode.SEE_THROUGH, 0, 0xF000F0);

        buf.endBatch();
    }

    protected void drawFloatingPanel(GuiGraphics g, int mx, int my) {
        if (!panelVisible || pickResult == null || pickBlockState == null) return;

        var mc = Minecraft.getInstance();
        int px = Math.max(0, Math.min(width - PANEL_W, panelX));
        int ph = panelH();
        int py = Math.max(0, Math.min(height - ph, panelY));

        mc.renderBuffers().bufferSource().endBatch();
        RenderSystem.clearDepth(1.0);
        RenderSystem.clear(256, false);

        drawDialogBox(g, px, py, PANEL_W, ph,
                Component.literal((draggingPanel ? "↕" : "⊞") + " ").append(pickBlockState.getBlock().getName()));

        var buf = mc.renderBuffers().bufferSource();
        var pose = g.pose().last().pose();

        font.drawInBatch(Component.literal("☒"), px + PANEL_W - 14, py + 6, 0xFFFF6666, false,
                pose, buf, Font.DisplayMode.SEE_THROUGH, 0, 0xF000F0);

        if (!replaceMode) {
            if (editModeActive) {
                boolean isCtrl = pickBlockState != null && selectedIndex >= 0
                        && patterns.get(selectedIndex).getValue().getChar(pickResult.getX(), pickResult.getY(), pickResult.getZ()) == 'K';
                int colW = 84, gap = 8, margin = (PANEL_W - colW * 2 - gap) / 2;
                int x1 = px + margin, x2 = x1 + colW + gap, y1 = py + 52, y2 = py + 72;
                var mat = g.pose().last().pose();
                if (isCtrl) {
                    drawButton(g, buf, mat, "§e 替换", x1, py + (ph - 16) / 2, PANEL_W - 2 * margin, false);
                } else {
                    drawButton(g, buf, mat, "§7 替换", x1, y1, colW, false);
                    drawButton(g, buf, mat, "§7 批量", x2, y1, colW, false);
                    drawButton(g, buf, mat, "§c 单删", x1, y2, colW, false);
                    drawButton(g, buf, mat, "§c 批删", x2, y2, colW, false);
                }
                // Tag button / 标记按钮
                if (!isCtrl) {
                    int tagY = py + 94;
                    drawButton(g, buf, mat, (tagModeActive ? "§b" : "§7") + " 标记", x1, tagY, colW * 2 + gap, false);
                        // Tag editing UI when active / 标记编辑界面
                    if (tagModeActive) {
                        char pickedChar = patterns.get(selectedIndex).getValue()
                                .getChar(pickResult.getX(), pickResult.getY(), pickResult.getZ());
                        var pat = patterns.get(selectedIndex).getValue();
                        java.util.List<String> tags = pat.getTags(pickedChar);
                        int tagUiY = py + 116;
                        String labelText = "§7标记 [" + pickedChar + "]:";
                        font.drawInBatch(Component.literal(labelText),
                                px + (PANEL_W - font.width(labelText)) / 2, tagUiY, 0xFFCCCCCC, false, mat, buf,
                                Font.DisplayMode.SEE_THROUGH, 0, 0xF000F0);
                        tagUiY += 14;
                        // Show each tag pill / 显示每个标记
                        int indX = px + 8;
                        for (String tag : tags) {
                            String label = "✕ " + tag;
                            int tw = font.width(label) + 8;
                            if (indX + tw > px + PANEL_W - 8) { indX = px + 8; tagUiY += 14; }
                            g.fill(indX, tagUiY, indX + tw, tagUiY + 12, 0x44333366);
                            font.drawInBatch(Component.literal(label),
                                    indX + 2, tagUiY + 2, 0xFFFFFFFF, false, mat, buf,
                                    Font.DisplayMode.SEE_THROUGH, 0, 0xF000F0);
                            indX += tw + 4;
                        }
                        // Only allow input if no tag yet / 无标记时才显示输入框
                        if (tags.isEmpty()) {
                            tagUiY += 22;
                            String showTagInput = tagInput.isEmpty() ? "§8新标记..." : "§f" + tagInput + "▌";
                            g.fill(px + 8, tagUiY, px + PANEL_W - 8, tagUiY + 14, 0x22111133);
                            g.renderOutline(px + 8, tagUiY, PANEL_W - 16, 14, 0xFF666666);
                            font.drawInBatch(Component.literal(showTagInput),
                                    px + 10, tagUiY + 2, 0xFFFFFFFF, false, mat, buf,
                                    Font.DisplayMode.SEE_THROUGH, 0, 0xF000F0);
                            tagUiY += 20;
                            font.drawInBatch(Component.literal("§8Enter添加"),
                                    px + (PANEL_W - font.width("§8Enter添加")) / 2, tagUiY, 0xFF888888, false, mat, buf,
                                    Font.DisplayMode.SEE_THROUGH, 0, 0xF000F0);
                        }
                    }
                }
                // Save button / 保存按钮
                drawButton(g, buf, mat, "§b 💾 保存", px + 10, py + ph - 20, PANEL_W - 20, false);
            } else {
                // Browse mode — show pattern definition blocks / 浏览模式 — 显示结构中的可用方块
                buf.endBatch();
                var pat = selectedIndex >= 0 ? patterns.get(selectedIndex).getValue() : null;
                var defBlocks = pickResult != null && pat != null
                        ? pat.getAlternatives(pat.getChar(pickResult.getX(), pickResult.getY(), pickResult.getZ()))
                        : new java.util.LinkedHashSet<net.minecraft.world.level.block.state.BlockState>();

                font.drawInBatch(Component.literal("§7可替换方块"), px + 8, py + 28, 0xCCCCCCCC, false,
                        pose, buf, Font.DisplayMode.SEE_THROUGH, 0, 0xF000F0);
                buf.endBatch();
                var itemRenderer = mc.getItemRenderer();
                int cols = 4;
                int cellW = (PANEL_W - 12) / cols, cellH = 22;
                int gridX = px + 6, gridY = py + 38;
                int idx = 0;
                for (var bs : defBlocks) {
                    if (idx >= cols * 4) break;
                    int col = idx % cols, row = idx / cols;
                    int ix = gridX + col * cellW, iy = gridY + row * cellH;
                    var itemStack = new net.minecraft.world.item.ItemStack(bs.getBlock());
                    g.fill(ix, iy, ix + cellW - 1, iy + cellH - 1, 0x22111133);
                    if (!itemStack.isEmpty()) g.renderFakeItem(itemStack, ix + 2, iy + 2);
                    if (mx >= ix && mx < ix + cellW && my >= iy && my < iy + cellH) {
                        font.drawInBatch(bs.getBlock().getName(), mx + 8, my - 12, 0xFFFFFFFF, false,
                                g.pose().last().pose(), buf, Font.DisplayMode.SEE_THROUGH, 0, 0xF000F0);
                    }
                    idx++;
                }
            }
        } else {
            String showQ = searchQuery;
            if (showQ.length() > 26) showQ = showQ.substring(showQ.length() - 26);
            String searchText = searchQuery.isEmpty() ? "§8[输入搜索...]" : "§f" + showQ + "_";
            font.drawInBatch(Component.literal("§7> " + searchText), px + 8, py + 28, 0xFFFFFFFF, false,
                    pose, buf, Font.DisplayMode.SEE_THROUGH, 0, 0xF000F0);
            buf.endBatch();

            var itemRenderer = mc.getItemRenderer();
            int cols = 4, rows = 4;
            int cellW = (PANEL_W - 12) / cols, cellH = 22;
            int gridX = px + 6, gridY = py + 38;
            int total = searchResults.size();
            int pageSize = cols * rows;
            searchScroll = Math.min(searchScroll, Math.max(0, total - pageSize));

            for (int idx = 0; idx < pageSize; idx++) {
                int i = searchScroll + idx;
                if (i >= total) break;
                int col = idx % cols, row = idx / cols;
                int ix = gridX + col * cellW, iy = gridY + row * cellH;
                var block = searchResults.get(i);
                var itemStack = new net.minecraft.world.item.ItemStack(block);
                boolean hovered = mx >= ix && mx < ix + cellW && my >= iy && my < iy + cellH;
                boolean selected = i == searchIdx;

                g.fill(ix, iy, ix + cellW - 1, iy + cellH - 1, selected ? 0x88444488 : hovered ? 0x44333366 : 0x22111133);
                if (selected) g.renderOutline(ix, iy, cellW - 1, cellH - 1, 0xFFFFEE88);

                if (!itemStack.isEmpty()) {
                    g.renderFakeItem(itemStack, ix + 2, iy + 2);
                }

                if (hovered) {
                    font.drawInBatch(block.getName(), mx + 8, my - 12, 0xFFFFFFFF, false,
                            g.pose().last().pose(), buf, Font.DisplayMode.SEE_THROUGH, 0, 0xF000F0);
                }
            }

            font.drawInBatch(Component.literal("§8[取消]"), px + PANEL_W - 46, py + ph - 14, 0xFFAAAAAA, false,
                    pose, buf, Font.DisplayMode.SEE_THROUGH, 0, 0xF000F0);
            font.drawInBatch(Component.literal("§8[取消]"), px + PANEL_W - 46, py + ph - 14, 0xFFAAAAAA, false,
                    pose, buf, Font.DisplayMode.SEE_THROUGH, 0, 0xF000F0);
        }

        buf.endBatch();
    }

    private void renderPickedBlockHighlight(PoseStack model, net.minecraft.client.renderer.MultiBufferSource.BufferSource buf) {
        if (pickResult == null || pickBlockState == null || cachedScene == null) return;

        boolean flashOn = (System.currentTimeMillis() / HILITE_FLASH_MS) % 2 == 0;

        model.pushPose();
        model.translate(pickResult.getX(), pickResult.getY(), pickResult.getZ());

        VertexConsumer vc = buf.getBuffer(HIGHLIGHT_LINES);
        var shape = pickBlockState.getShape(cachedScene, pickResult, CollisionContext.empty());

        for (AABB box : shape.toAabbs()) {
            float intensity = flashOn ? HILITE_ON : HILITE_OFF;

            renderBoxEdges(model.last(), vc, box.inflate(GLOW_INFLATE),
                    intensity * GLOW_INTENSITY, intensity * GLOW_INTENSITY, intensity * GLOW_INTENSITY, GLOW_ALPHA);

            for (int layer = 0; layer < HILITE_LAYERS; layer++) {
                float offset = layer * LAYER_OFFSET_STEP;
                float li = (layer == 0) ? intensity : intensity * LAYER_OUTER_FACTOR;
                float a = (layer == 0) ? LAYER_INNER_ALPHA : LAYER_OUTER_ALPHA;
                renderBoxEdges(model.last(), vc, box.inflate(offset), li, li, li, a);
            }
        }

        model.popPose();
        buf.endBatch(HIGHLIGHT_LINES);
    }

    private static void renderBoxEdges(PoseStack.Pose pose, VertexConsumer consumer, AABB box,
                                        float r, float g, float b, float a) {
        double x1 = box.minX, y1 = box.minY, z1 = box.minZ;
        double x2 = box.maxX, y2 = box.maxY, z2 = box.maxZ;
        addEdge(pose, consumer, x1, y1, z1, x2, y1, z1, r, g, b, a);
        addEdge(pose, consumer, x2, y1, z1, x2, y1, z2, r, g, b, a);
        addEdge(pose, consumer, x2, y1, z2, x1, y1, z2, r, g, b, a);
        addEdge(pose, consumer, x1, y1, z2, x1, y1, z1, r, g, b, a);
        addEdge(pose, consumer, x1, y2, z1, x2, y2, z1, r, g, b, a);
        addEdge(pose, consumer, x2, y2, z1, x2, y2, z2, r, g, b, a);
        addEdge(pose, consumer, x2, y2, z2, x1, y2, z2, r, g, b, a);
        addEdge(pose, consumer, x1, y2, z2, x1, y2, z1, r, g, b, a);
        addEdge(pose, consumer, x1, y1, z1, x1, y2, z1, r, g, b, a);
        addEdge(pose, consumer, x2, y1, z1, x2, y2, z1, r, g, b, a);
        addEdge(pose, consumer, x2, y1, z2, x2, y2, z2, r, g, b, a);
        addEdge(pose, consumer, x1, y1, z2, x1, y2, z2, r, g, b, a);
    }

    private static void addEdge(PoseStack.Pose pose, VertexConsumer consumer,
                                 double x1, double y1, double z1,
                                 double x2, double y2, double z2,
                                 float r, float g, float b, float a) {
        consumer.addVertex(pose, (float) x1, (float) y1, (float) z1).setColor(r, g, b, a);
        consumer.addVertex(pose, (float) x2, (float) y2, (float) z2).setColor(r, g, b, a);
    }

    private static void renderSolidBox(PoseStack.Pose pose, VertexConsumer consumer,
                                        double x1, double y1, double z1,
                                        double x2, double y2, double z2,
                                        float r, float g, float b, float a) {
        addTri(pose, consumer, x1, y1, z1, x2, y1, z1, x1, y1, z2, r, g, b, a);
        addTri(pose, consumer, x2, y1, z1, x2, y1, z2, x1, y1, z2, r, g, b, a);
        addTri(pose, consumer, x1, y2, z1, x1, y2, z2, x2, y2, z1, r, g, b, a);
        addTri(pose, consumer, x2, y2, z1, x1, y2, z2, x2, y2, z2, r, g, b, a);
        addTri(pose, consumer, x1, y1, z1, x1, y2, z1, x2, y1, z1, r, g, b, a);
        addTri(pose, consumer, x2, y1, z1, x1, y2, z1, x2, y2, z1, r, g, b, a);
        addTri(pose, consumer, x1, y1, z2, x2, y1, z2, x1, y2, z2, r, g, b, a);
        addTri(pose, consumer, x2, y1, z2, x2, y2, z2, x1, y2, z2, r, g, b, a);
        addTri(pose, consumer, x1, y1, z1, x1, y1, z2, x1, y2, z1, r, g, b, a);
        addTri(pose, consumer, x1, y1, z2, x1, y2, z2, x1, y2, z1, r, g, b, a);
        addTri(pose, consumer, x2, y1, z1, x2, y2, z1, x2, y1, z2, r, g, b, a);
        addTri(pose, consumer, x2, y1, z2, x2, y2, z1, x2, y2, z2, r, g, b, a);
    }

    private static void addTri(PoseStack.Pose pose, VertexConsumer consumer,
                                double x1, double y1, double z1,
                                double x2, double y2, double z2,
                                double x3, double y3, double z3,
                                float r, float g, float b, float a) {
        consumer.addVertex(pose, (float) x1, (float) y1, (float) z1).setColor(r, g, b, a);
        consumer.addVertex(pose, (float) x2, (float) y2, (float) z2).setColor(r, g, b, a);
        consumer.addVertex(pose, (float) x3, (float) y3, (float) z3).setColor(r, g, b, a);
    }

    private static PickRay createPickRay(Matrix4f patternToScreen, int mouseX, int mouseY,
                                         int width, int height, int depth) {
        float[] depthRange = projectedDepthRange(patternToScreen, width, height, depth);
        float backDepth = depthRange[0] - 1f;
        float frontDepth = depthRange[1] + 1f;
        Matrix4f screenToPattern = new Matrix4f(patternToScreen).invert();

        Vector3f rayStart = unproject(mouseX, mouseY, frontDepth, screenToPattern);
        Vector3f rayEnd = unproject(mouseX, mouseY, backDepth, screenToPattern);
        return new PickRay(
                rayStart,
                new Vector3f(rayEnd.x - rayStart.x, rayEnd.y - rayStart.y, rayEnd.z - rayStart.z)
        );
    }

    private static Vector3f unproject(int screenX, int screenY, float screenZ, Matrix4f screenToPattern) {
        Vector4f point = new Vector4f(screenX, screenY, screenZ, 1f).mul(screenToPattern);
        point.div(point.w);
        return new Vector3f(point.x, point.y, point.z);
    }

    private static float[] projectedDepthRange(Matrix4f matrix, int width, int height, int depth) {
        float minZ = Float.MAX_VALUE;
        float maxZ = -Float.MAX_VALUE;
        Vector4f corner = new Vector4f();
        for (int i = 0; i < 8; i++) {
            corner.set((i & 1) == 0 ? 0 : width,
                    (i & 2) == 0 ? 0 : height,
                    (i & 4) == 0 ? 0 : depth,
                    1f).mul(matrix);
            minZ = Math.min(minZ, corner.z);
            maxZ = Math.max(maxZ, corner.z);
        }
        return new float[]{minZ, maxZ};
    }

    private static double intersectRayAabb(Vector3f origin, Vector3f direction,
                                           double minX, double minY, double minZ,
                                           double maxX, double maxY, double maxZ) {
        double tMin = 0;
        double tMax = 1;
        double[] mins = {minX, minY, minZ};
        double[] maxs = {maxX, maxY, maxZ};
        double[] o = {origin.x, origin.y, origin.z};
        double[] d = {direction.x, direction.y, direction.z};

        for (int axis = 0; axis < 3; axis++) {
            if (Math.abs(d[axis]) < 1.0E-7) {
                if (o[axis] < mins[axis] || o[axis] > maxs[axis]) return -1;
                continue;
            }

            double inv = 1.0 / d[axis];
            double near = (mins[axis] - o[axis]) * inv;
            double far = (maxs[axis] - o[axis]) * inv;
            if (near > far) {
                double tmp = near;
                near = far;
                far = tmp;
            }
            tMin = Math.max(tMin, near);
            tMax = Math.min(tMax, far);
            if (tMin > tMax) return -1;
        }

        return tMin;
    }

    private void drawRenderBorder(GuiGraphics g) {
        g.fill(renderL, renderT, renderR, renderT + 1, COL_RENDER_BDR);
        g.fill(renderL, renderB - 1, renderR, renderB, COL_RENDER_BDR);
        g.fill(renderL, renderT, renderL + 1, renderB, COL_RENDER_BDR);
        g.fill(renderR - 1, renderT, renderR, renderB, COL_RENDER_BDR);
    }

    private void drawFuture(GuiGraphics g) {
        g.fill(futureL, futureT, futureR, futureB, COL_PANEL_BG);
        g.fill(futureL, futureT, futureR, futureT + 1, COL_BORDER);
        g.fill(futureL, futureB - 1, futureR, futureB, COL_BORDER);
        g.fill(futureL, futureT, futureL + 1, futureB, COL_BORDER);
        g.fill(futureR - 1, futureT, futureR, futureB, COL_BORDER);
        int fy = futureT + PADDING;
        String modeLabel = editModeActive ? "§a编辑模式" : "§7浏览模式";
        g.drawString(font, Component.literal(modeLabel), futureL + 4, fy, editModeActive ? 0x00FF88 : 0x888888);
        fy += 12;
        g.drawString(font, Component.literal("§7Alt+` 切换模式"), futureL + 4, fy, 0x888888);
        fy += 10;
        g.drawString(font, Component.literal("§7DEL 删除结构"), futureL + 4, fy, 0x888888);
        fy += 10;
        if (editModeActive) {
            g.drawString(font, Component.literal("§7Ctrl+Z 撤销"), futureL + 4, fy, 0x888888);
            fy += 10;
            g.drawString(font, Component.literal("§7Ctrl+C 复制"), futureL + 4, fy, 0x888888);
            fy += 10;
            g.drawString(font, Component.literal("§7Ctrl+V 粘贴替换"), futureL + 4, fy, 0x888888);
        } else {
            g.drawString(font, Component.literal("§7G 复位视角"), futureL + 4, fy, 0x888888);
            fy += 10;
            g.drawString(font, Component.literal("§7W/S 切换结构"), futureL + 4, fy, 0x888888);
            fy += 10;
            g.drawString(font, Component.literal("§7滚轮 缩放"), futureL + 4, fy, 0x888888);
        }
    }

    @Override
    public boolean mouseClicked(double mx, double my, int btn) {
        if (selectedIndex >= 0) {
            int bw = 24, bh = 28, gap = 3;
            int bx = width - bw - 10, by = height / 2 - bh - gap / 2;
            int h = patterns.get(selectedIndex).getValue().height;
            if (mx >= bx - 2 && mx <= bx + bw + 2) {
                if (my >= by + bh + gap && my <= by + bh * 2 + gap && btn == 0) { layerView = -1; return true; }
                if (my >= by && my <= by + bh) {
                    if (btn == 0 && layerView < h - 1) { if (layerView < 0) layerView = 0; else layerView++; return true; }
                    if (btn == 1 && layerView > 0) { layerView--; return true; }
                }
            }
        }

        if (btn == 0 && confirmShow) {
            int cx = (width - CONFIRM_W) / 2, cy = (height - CONFIRM_H) / 2;
            if (mx >= cx + 16 && mx <= cx + 76 && my >= cy + 56 && my <= cy + 72) {
                confirmShow = false;
                if (confirmTargetIdx >= 0 && confirmTargetIdx < patterns.size()) {
                    var id = patterns.get(confirmTargetIdx).getKey();
                    var pat = patterns.get(confirmTargetIdx).getValue();
                    if (confirmIsSave) {
                        String name = saveFileName.trim().isEmpty() ? id.getPath() : saveFileName.trim().replaceAll("[^a-zA-Z0-9_/-]", "_");
                        var saveId = ResourceLocation.fromNamespaceAndPath(id.getNamespace(), name);
                        com.endlessepoch.core.api.multiblock.PatternStorage.save(saveId, pat);
                        Minecraft.getInstance().player.displayClientMessage(
                                Component.literal("§a结构已保存: " + name), true);
                    } else {
                        MultiBlockRegistry.removeLocal(Minecraft.getInstance().player.getUUID(), id);
                        MultiBlockRegistry.removeMod(id);
                        com.endlessepoch.core.api.multiblock.PatternStorage.delete(id);
                        patterns.remove(confirmTargetIdx);
                        selectedIndex = Math.min(selectedIndex, patterns.size() - 1);
                    }
                    pickResult = null;
                    pickBlockState = null;
                    panelVisible = false;
                }
                return true;
            }
            if (mx >= cx + CONFIRM_W - 64 && mx <= cx + CONFIRM_W - 16 && my >= cy + 56 && my <= cy + 72) {
                confirmShow = false;
                return true;
            }
        }

        if (btn == 0 && panelVisible && !replaceMode && editModeActive) {
            int px2 = Math.max(0, Math.min(width - PANEL_W, panelX));
            int py2 = Math.max(0, Math.min(height - panelH(), panelY));
            int colW = 84, gap = 8, margin2 = (PANEL_W - colW * 2 - gap) / 2;
            int x1 = px2 + margin2, x2 = x1 + colW + gap, y1 = py2 + 52, y2 = py2 + 72;
            boolean isCtrl = pickResult != null && selectedIndex >= 0
                    && patterns.get(selectedIndex).getValue().getChar(pickResult.getX(), pickResult.getY(), pickResult.getZ()) == 'K';
            int ctrlY = py2 + (panelH() - 16) / 2;
            if (onBtn(mx, my, x1, isCtrl ? ctrlY : y1, isCtrl ? PANEL_W - 2 * margin2 : colW)) {
                replaceMode = true; batchReplace = false; replaceSource = pickResult;
                searchResults = (isCtrl
                        ? com.endlessepoch.core.api.multiblock.MultiBlockRegistry.getControllerBlocks()
                        .stream().filter(b -> b.asItem() != net.minecraft.world.item.Items.AIR)
                        : net.minecraft.core.registries.BuiltInRegistries.BLOCK.stream()
                        .filter(b -> b.asItem() != net.minecraft.world.item.Items.AIR)).toList();
                searchIdx = 0; searchScroll = 0; return true; }
            if (!isCtrl) {
                if (onBtn(mx, my, x2, y1, colW)) {
                    replaceMode = true; batchReplace = true; replaceSource = pickResult;
                    searchResults = net.minecraft.core.registries.BuiltInRegistries.BLOCK.stream()
                            .filter(b -> b.asItem() != net.minecraft.world.item.Items.AIR).toList();
                    searchIdx = 0; searchScroll = 0; return true; }
                if (onBtn(mx, my, x1, y2, colW)) { deleteBlock(false); return true; }
                if (onBtn(mx, my, x2, y2, colW)) { deleteBlock(true); return true; }
                // Tag button toggle / 标记按钮切换
                int tagBtnY = py2 + 94;
                if (onBtn(mx, my, x1, tagBtnY, colW * 2 + gap)) {
                    tagModeActive = !tagModeActive;
                    tagInput = "";
                    return true;
                }
                // Tag editing mode / 标记编辑模式
                if (tagModeActive && selectedIndex >= 0 && pickResult != null) {
                    char pickedChar = patterns.get(selectedIndex).getValue()
                            .getChar(pickResult.getX(), pickResult.getY(), pickResult.getZ());
                    var pat = patterns.get(selectedIndex).getValue();
                    java.util.List<String> tags = new java.util.ArrayList<>(pat.getTags(pickedChar));

                    // Click on tag pills to delete / 点击 ✕ 删除标记
                    int tX2 = px2 + 8;
                    int tY2 = py2 + 130;
                    for (int ti = 0; ti < tags.size(); ti++) {
                        String tag = tags.get(ti);
                        String label = "✕ " + tag;
                        int tw = font.width(label) + 8;
                        if (tX2 + tw > px2 + PANEL_W - 8) { tX2 = px2 + 8; tY2 += 14; }
                        if (mx >= tX2 && mx <= tX2 + tw && my >= tY2 && my <= tY2 + 12 && mx <= tX2 + 10) {
                            tags.remove(ti);
                            pat.setTags(pickedChar, tags);
                            cachedScene = null; return true;
                        }
                        tX2 += tw + 4;
                    }
                }
            }
            // Undo button / 撤销按钮
            // Save button / 保存按钮
            int ph = panelH();
            if (onBtn(mx, my, px2 + 10, py2 + ph - 20, PANEL_W - 20) && selectedIndex >= 0) {
                var pat = patterns.get(selectedIndex);
                saveFileName = pat.getKey().getPath().replace("scanned_", "");
                confirmShow = true; confirmIsSave = true; confirmTargetIdx = selectedIndex; return true; }
        }
        if (btn == 0 && panelVisible && replaceMode) {
            int px2 = Math.max(0, Math.min(width - PANEL_W, panelX));
            int py2 = Math.max(0, Math.min(height - panelH(), panelY));
            int cols = 4, rows = 4;
            int cellW = (PANEL_W - 12) / cols, cellH = 22;
            int gridX = px2 + 6, gridY = py2 + 38;
            int pageSize = cols * rows;
            int total = searchResults.size();

            if (mx >= px2 + PANEL_W - 50 && mx <= px2 + PANEL_W - 4 && my >= py2 + panelH() - 18 && my <= py2 + panelH() - 4) {
                replaceMode = false; return true;
            }
            for (int idx = 0; idx < pageSize; idx++) {
                int i = searchScroll + idx;
                if (i >= total) break;
                int col = idx % cols, row = idx / cols;
                int ix = gridX + col * cellW, iy = gridY + row * cellH;
                if (mx >= ix && mx < ix + cellW - 1 && my >= iy && my < iy + cellH - 1) {
                    if (i == searchIdx) { applyReplace(searchResults.get(i).defaultBlockState()); }
                    else { searchIdx = i; }
                    return true;
                }
            }
        }

        if (btn == 0 && panelVisible
                && mx >= panelX + PANEL_W - 14 && mx <= panelX + PANEL_W
                && my >= panelY && my <= panelY + PANEL_TITLE_H) {
            panelVisible = false;
            return true;
        }
        if (btn == 0 && panelVisible
                && mx >= panelX && mx <= panelX + PANEL_W
                && my >= panelY && my <= panelY + PANEL_TITLE_H) {
            draggingPanel = true;
            dragOffX = (int) mx - panelX;
            dragOffY = (int) my - panelY;
            return true;
        }

        if (btn == 0 && selectedIndex >= 0
                && mx >= rL() && mx < rR() && my >= rT() && my < rB()) {
            clickActive = true;
            clickStartX = (int) mx;
            clickStartY = (int) my;
            return true;
        }
        return super.mouseClicked(mx, my, btn);
    }

    @Override
    public boolean mouseDragged(double mx, double my, int btn, double dx, double dy) {
        if (btn == 0 && draggingPanel) {
            panelX = (int) mx - dragOffX;
            panelY = (int) my - dragOffY;
            return true;
        }

        if (mx >= rL() && mx <= rR() && btn == 0) {
            rotY += dx * ROT_SPEED;
            rotX -= dy * ROT_SPEED;
            rotX = Math.max(ROT_X_MIN, Math.min(ROT_X_MAX, rotX));
            if (Math.abs(mx - clickStartX) > 5 || Math.abs(my - clickStartY) > 5) {
                clickActive = false;
                mouseHeld = true;
            }
        }
        return mx >= rL() && mx <= rR();
    }

    @Override
    public boolean mouseReleased(double mx, double my, int btn) {
        if (btn == 0 && draggingPanel) {
            draggingPanel = false;
            return true;
        }

        if (btn == 0) mouseHeld = false;

        if (btn == 0 && clickActive) {
            clickActive = false;
            pendingPick = true;
            pendingPickMx = clickStartX;
            pendingPickMy = clickStartY;
        }
        return super.mouseReleased(mx, my, btn);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double sx, double sy) {
        if (replaceMode && panelVisible && !searchResults.isEmpty()) {
            int px2 = Math.max(0, Math.min(width - PANEL_W, panelX));
            int py2 = Math.max(0, Math.min(height - panelH(), panelY));
            if (mx >= px2 && mx <= px2 + PANEL_W && my >= py2 && my <= py2 + panelH()) {
                int step = (int) -Math.signum(sy);
                searchIdx = Math.max(0, Math.min(searchResults.size() - 1, searchIdx + step * 12));
                if (searchIdx < searchScroll) searchScroll = searchIdx;
                int ps2 = 12;
                if (searchIdx >= searchScroll + ps2) searchScroll = Math.max(0, searchIdx - ps2 + 1);
                return true;
            }
        }
        if (mx >= rL() && mx <= rR()) {
            if (userZoom > ZOOM_MED_THR) userZoom += sy > 0 ? ZOOM_STEP_COARSE : -ZOOM_STEP_COARSE;
            else if (userZoom == ZOOM_MED_THR) userZoom += sy > 0 ? ZOOM_STEP_COARSE : -ZOOM_STEP_NORM;
            else if (userZoom > ZOOM_LOW_THR) userZoom += sy > 0 ? ZOOM_STEP_NORM : -ZOOM_STEP_NORM;
            else userZoom += sy > 0 ? ZOOM_STEP_FINE : -ZOOM_STEP_FINE;
            userZoom = Math.max(ZOOM_MIN, Math.min(ZOOM_MAX, userZoom));
            return true;
        }
        if (mx >= listL && mx <= listR && !patterns.isEmpty() && !editModeActive) {
            selectedIndex = (selectedIndex - (int) Math.signum(sy) + patterns.size()) % patterns.size();
            pickResult = null;
            pickBlockState = null;
            return true;
        }
        return false;
    }

    @Override
    public boolean keyPressed(int k, int s, int m) {
        if (hasControlDown() && editModeActive) {
            if (k == 90) { undoReplace(); return true; }
            if (k == 67 && pickBlockState != null) {
                Minecraft.getInstance().keyboardHandler.setClipboard(pickBlockState.getBlock().getName().getString());
                return true;
            }
            if (k == 86 && pickResult != null) {
                String clip = Minecraft.getInstance().keyboardHandler.getClipboard();
                if (clip != null && !clip.isEmpty()) {
                    String q = clip.toLowerCase().trim();
                    var found = net.minecraft.core.registries.BuiltInRegistries.BLOCK.stream()
                            .filter(b -> b.asItem() != net.minecraft.world.item.Items.AIR)
                            .filter(b -> b.getName().getString().toLowerCase().contains(q)
                                    || net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(b).getPath().contains(q))
                            .findFirst();
                    if (found.isPresent()) {
                        replaceSource = pickResult;
                        batchReplace = false;
                        applyReplace(found.get().defaultBlockState());
                    }
                }
                return true;
            }
        }

        if (k == 96 && hasAltDown()) { editModeActive = !editModeActive; if (!editModeActive) replaceMode = false; return true; }
                if (k == KEY_DEL && selectedIndex >= 0 && selectedIndex < patterns.size()) {
            confirmShow = true;
            confirmTargetIdx = selectedIndex;
            return true;
        }
        if (k == 256) { replaceMode = false; }

        if (replaceMode && panelVisible && k == 259 && !searchQuery.isEmpty()) {
            searchQuery = searchQuery.substring(0, searchQuery.length() - 1);
            updateSearchResults();
            searchIdx = searchResults.isEmpty() ? -1 : 0;
            searchScroll = 0;
            return true;
        }

        // Replace mode — Enter to confirm / 替换模式 — Enter确认
        if (replaceMode && panelVisible && !searchResults.isEmpty()) {
            if (k == 257 || k == 335) {
                if (searchIdx >= 0 && searchIdx < searchResults.size()) {
                    applyReplace(searchResults.get(searchIdx).defaultBlockState());
                }
                return true;
            }
        }

        // Save dialog — backspace only (char input via charTyped) / 保存弹窗 — 退格键
        if (confirmShow && confirmIsSave && k == 259 && !saveFileName.isEmpty()) {
            saveFileName = saveFileName.substring(0, saveFileName.length() - 1);
            return true;
        }

        // Tag mode / 标记模式
        if (tagModeActive && panelVisible && selectedIndex >= 0 && pickResult != null) {
            char pickedChar = patterns.get(selectedIndex).getValue()
                    .getChar(pickResult.getX(), pickResult.getY(), pickResult.getZ());
            var pat = patterns.get(selectedIndex).getValue();

            // Enter: add new tag (only if no tag yet) / 添加标记（仅限无标记时）
            if ((k == 257 || k == 335) && !tagInput.isEmpty()) {
                if (!pat.getTags(pickedChar).isEmpty()) return true; // already tagged
                java.util.List<String> tags = new java.util.ArrayList<>(pat.getTags(pickedChar));
                tags.add(tagInput);
                pat.setTags(pickedChar, tags);
                tagInput = "";
                cachedScene = null;
                return true;
            }

            // Backspace / 退格
            if (k == 259 && !tagInput.isEmpty()) {
                tagInput = tagInput.substring(0, tagInput.length() - 1);
                return true;
            }
        }

        if (k == KEY_G && !editModeActive) { rotX = ROT_X_INIT; rotY = ROT_Y_INIT; userZoom = ZOOM_INIT; layerView = -1; return true; }
        if ((k == KEY_W || k == KEY_UP) && !editModeActive) { selectedIndex = Math.max(0, selectedIndex - 1); updateListScroll(); layerView = -1; return true; }
        if ((k == KEY_S || k == KEY_DN) && !editModeActive) { selectedIndex = Math.min(patterns.size() - 1, selectedIndex + 1); updateListScroll(); layerView = -1; return true; }
        return super.keyPressed(k, s, m);
    }

    private void updateSearchResults() {
        String q = searchQuery.toLowerCase().trim();
        if (q.isEmpty()) {
            searchResults = net.minecraft.core.registries.BuiltInRegistries.BLOCK.stream().filter(b -> b.asItem() != net.minecraft.world.item.Items.AIR).toList();
            return;
        }
        String[] tokens = q.split("[\\s]+");
        searchResults = net.minecraft.core.registries.BuiltInRegistries.BLOCK.stream()
                .filter(b -> b.asItem() != net.minecraft.world.item.Items.AIR)
                .filter(b -> {
                    String name = b.getName().getString().toLowerCase();
                    String path = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(b).getPath();
                    String zh = net.minecraft.client.resources.language.I18n.get(b.getDescriptionId(), false);
                    if (PinyinUtil.matches(q, name) || PinyinUtil.matches(q, zh)) return true;
                    for (String t : tokens) {
                        if (!name.contains(t) && !path.contains(t) && !zh.contains(t)) return false;
                    }
                    return true;
                })
                .sorted((a, b) -> a.getName().getString().compareToIgnoreCase(b.getName().getString()))
                .toList();
    }


    private void updateListScroll() {
        int visibleCount = (listB - listT) / LINE_HEIGHT;
        if (selectedIndex < listScrollOffset) listScrollOffset = selectedIndex;
        if (selectedIndex >= listScrollOffset + visibleCount) listScrollOffset = Math.max(0, selectedIndex - visibleCount + 1);
    }

    // Single/batch delete — replace block(s) with air / 单/批量删除
    private void deleteBlock(boolean batch) {
        if (selectedIndex < 0 || pickResult == null) return;
        applyReplace(net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), batch);
    }

    private void applyReplace(BlockState newState) {
        applyReplace(newState, batchReplace);
    }

    private void applyReplace(BlockState newState, boolean batch) {
        if (selectedIndex < 0 || pickResult == null) return;
        MultiBlockPattern pat = patterns.get(selectedIndex).getValue();
        int rx = pickResult.getX(), ry = pickResult.getY(), rz = pickResult.getZ();

        if (batch) {
            char c = pat.getChar(rx, ry, rz);
            BlockState origDef = pat.getDefinitions().get(c);
            pat.setDefinition(c, newState);
            undoStack.push(() -> { pat.setDefinition(c, origDef); cachedScene = null; });
        } else {
            BlockState orig = cachedScene != null ? cachedScene.getBlockState(pickResult)
                    : new EECoreSceneWorld(pat).getBlockState(pickResult);
            pat.setBlock(rx, ry, rz, newState);
            undoStack.push(() -> { pat.setBlock(rx, ry, rz, orig); cachedScene = null; });
        }

        undoAvailable = true;
        cachedScene = null;
        // Keep replace mode open with reset search / 保持替换模式，重置搜索
        searchQuery = "";
        searchResults = net.minecraft.core.registries.BuiltInRegistries.BLOCK.stream()
                .filter(b -> b.asItem() != net.minecraft.world.item.Items.AIR).toList();
        searchIdx = -1;
        searchScroll = 0;
        replaceSource = null;
    }

    private void undoReplace() {
        if (!undoAvailable || undoStack.isEmpty()) return;
        Runnable op = undoStack.pop();
        op.run();
        undoAvailable = !undoStack.isEmpty();
        // Keep panel open showing the restored block / 保持面板打开显示还原后的方块
        if (cachedScene == null && selectedIndex >= 0) {
            cachedScene = new EECoreSceneWorld(patterns.get(selectedIndex).getValue());
        }
        if (pickResult != null && cachedScene != null) {
            pickBlockState = cachedScene.getBlockState(pickResult);
        }
        panelVisible = pickResult != null;
    }

    @Override
    public boolean charTyped(char cp, int modifiers) {
        // Save dialog text input / 保存弹窗文字输入
        if (confirmShow && confirmIsSave && cp >= 32 && cp != 127) {
            saveFileName += cp;
            return true;
        }
        if (replaceMode && panelVisible && cp >= 32 && cp != 127) {
            searchQuery += cp;
            updateSearchResults();
            searchIdx = searchResults.isEmpty() ? -1 : 0;
            searchScroll = 0;
            return true;
        }
        // Tag input / 标签输入
        if (tagModeActive && panelVisible && cp >= 32 && cp != 127) {
            if (tagInput.length() < 64) tagInput += cp;
            return true;
        }
        return super.charTyped(cp, modifiers);
    }

    @Override public boolean isPauseScreen() { return false; }
}
