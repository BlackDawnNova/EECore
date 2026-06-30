package com.endlessepoch.core.nova.client;

import com.endlessepoch.core.api.multiblock.MultiBlockPattern;
import com.endlessepoch.core.api.multiblock.MultiBlockRegistry;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
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

    private static final int KEY_G  = 71;
    private static final int KEY_W  = 87;
    private static final int KEY_S  = 83;
    private static final int KEY_UP = 265;
    private static final int KEY_DN = 264;

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

    private final List<Map.Entry<ResourceLocation, MultiBlockPattern>> patterns;
    private int selectedIndex;

    private float rotX = ROT_X_INIT, rotY = ROT_Y_INIT;
    private float userZoom = ZOOM_INIT;

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

    private double bestPickRayT;
    private BlockPos bestPickPos;
    private BlockState bestPickState;

    /**
     * Immersive mode when zoom > threshold — render bounds expand to full screen.
     * 沉浸模式: 缩放超过阈值时渲染区扩展到全屏。
     */
    private boolean immersive() { return userZoom > ZOOM_IMMERSIVE; }

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
        listScrollOffset = 0;
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
        int x = listL + PADDING, y = listT + PADDING;
        g.drawString(font, Component.translatable("eecore.visualizer.patterns"), x, y - 2, COL_ACCENT);
        if (patterns.isEmpty()) {
            g.drawString(font, Component.translatable("eecore.visualizer.empty"), x, y + 10, COL_DIM);
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
        if (listScrollOffset > 0) g.drawString(font, "▲", listR - 8, listT + 2, COL_SCROLL_ARW);
        if (end < patterns.size()) g.drawString(font, "▼", listR - 8, listB - LINE_HEIGHT, COL_SCROLL_ARW);
    }

    private void drawRenderArea(GuiGraphics g) {
        int erL = rL(), erR = rR(), erT = rT(), erB = rB();
        int rwLoc = erR - erL, rhLoc = erB - erT;
        int vpSize = Math.min(rwLoc, rhLoc);
        if (selectedIndex < 0) { drawRenderBorder(g); return; }
        MultiBlockPattern pat = patterns.get(selectedIndex).getValue();
        float blockDiag = (float) Math.sqrt(pat.width*pat.width + pat.height*pat.height + pat.depth*pat.depth);
        float cameraDist = CAM_DIST_FACTOR * (float)(vpSize * Math.sqrt(2)) / (blockDiag + CAM_DIST_EPS) * userZoom * CAM_DIST_SCALE;
        float blockPx = cameraDist * BLOCK_PX_FACTOR;

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

        for (int y = 0; y < pat.height; y++)
            for (int z = 0; z < pat.depth; z++)
                for (int x = 0; x < pat.width; x++) {
                    BlockState st = cachedScene.getBlockState(new BlockPos(x, y, z));
                    if (st.isAir()) continue;
                    if (pat.getChar(x, y, z) == 'K') {
                        boolean pulse = (System.currentTimeMillis() / K_PULSE_MS) % 2 == 0;
                        st = pulse
                            ? net.minecraft.world.level.block.Blocks.RED_STAINED_GLASS.defaultBlockState()
                            : net.minecraft.world.level.block.Blocks.BLUE_STAINED_GLASS.defaultBlockState();
                    }
                    model.pushPose();
                    model.translate(x, y, z);

                    if (pendingPick) {
                        double hit = intersectRayAabb(pickRayStart, pickRayDirection,
                                x, y, z, x + 1, y + 1, z + 1);
                        if (hit >= 0 && hit < bestPickRayT) {
                            bestPickRayT = hit;
                            bestPickPos = new BlockPos(x, y, z);
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
            }
        }

        buf.endBatch();
        renderPickedBlockHighlight(model, buf);
        RenderSystem.disableDepthTest();
        if (!immersive()) drawRenderBorder(g);

        g.drawString(font, Component.translatable("eecore.visualizer.controller_label"), rL() + PADDING, rT() + PADDING, COL_WHITE);
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
        g.drawString(font, Component.literal("待定"), futureL + PADDING, futureT + PADDING, COL_ACCENT);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int btn) {
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
        if (mx >= rL() && mx <= rR() && btn == 0) {
            rotY += dx * ROT_SPEED;
            rotX -= dy * ROT_SPEED;
            rotX = Math.max(ROT_X_MIN, Math.min(ROT_X_MAX, rotX));
            if (Math.abs(mx - clickStartX) > 5 || Math.abs(my - clickStartY) > 5) {
                clickActive = false;
            }
        }
        return mx >= rL() && mx <= rR();
    }

    @Override
    public boolean mouseReleased(double mx, double my, int btn) {
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
        if (mx >= rL() && mx <= rR()) {
            if (userZoom > ZOOM_MED_THR) userZoom += sy > 0 ? ZOOM_STEP_COARSE : -ZOOM_STEP_COARSE;
            else if (userZoom == ZOOM_MED_THR) userZoom += sy > 0 ? ZOOM_STEP_COARSE : -ZOOM_STEP_NORM;
            else if (userZoom > ZOOM_LOW_THR) userZoom += sy > 0 ? ZOOM_STEP_NORM : -ZOOM_STEP_NORM;
            else userZoom += sy > 0 ? ZOOM_STEP_FINE : -ZOOM_STEP_FINE;
            userZoom = Math.max(ZOOM_MIN, Math.min(ZOOM_MAX, userZoom));
            return true;
        }
        if (mx >= listL && mx <= listR && !patterns.isEmpty()) {
            selectedIndex = (selectedIndex - (int) Math.signum(sy) + patterns.size()) % patterns.size();
            pickResult = null;
            pickBlockState = null;
            return true;
        }
        return false;
    }

    @Override
    public boolean keyPressed(int k, int s, int m) {
        if (k == KEY_G) { rotX = ROT_X_INIT; rotY = ROT_Y_INIT; userZoom = ZOOM_INIT; return true; }
        if (k == KEY_W || k == KEY_UP) { selectedIndex = Math.max(0, selectedIndex - 1); return true; }
        if (k == KEY_S || k == KEY_DN) { selectedIndex = Math.min(patterns.size() - 1, selectedIndex + 1); return true; }
        return super.keyPressed(k, s, m);
    }

    @Override public boolean isPauseScreen() { return false; }
}
