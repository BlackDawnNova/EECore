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
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
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
 * Pick is computed inline during rendering (same PoseStack = no projection mismatch).
 */
@OnlyIn(Dist.CLIENT)
public class MultiblockVisualizerScreen extends Screen {

    private record PickRay(Vector3f start, Vector3f direction) {}

    private static final RenderType HIGHLIGHT_LINES = RenderType.create(
            "eecore_visualizer_highlight_lines",
            DefaultVertexFormat.POSITION_COLOR,
            VertexFormat.Mode.DEBUG_LINES,
            1536,
            false,
            false,
            RenderType.CompositeState.builder()
                    .setShaderState(RenderStateShard.POSITION_COLOR_SHADER)
                    .setLineState(new RenderStateShard.LineStateShard(OptionalDouble.of(3.5)))
                    .setTransparencyState(RenderStateShard.TRANSLUCENT_TRANSPARENCY)
                    .setDepthTestState(RenderStateShard.LEQUAL_DEPTH_TEST)
                    .setWriteMaskState(RenderStateShard.COLOR_DEPTH_WRITE)
                    .setCullState(RenderStateShard.NO_CULL)
                    .createCompositeState(false)
    );

    private final List<Map.Entry<ResourceLocation, MultiBlockPattern>> patterns;
    private int selectedIndex;

    private float rotX = 150f, rotY = 135f;
    private float userZoom = 1f;

    private int cellSize, gridX, gridY;
    private int listL, listR, listT, listB;
    private int renderL, renderR, renderT, renderB;
    private int futureL, futureR, futureT, futureB;
    private EECoreSceneWorld cachedScene;
    private ResourceLocation cachedPatternId;
    private boolean editMode;

    // ===== Pick state =====
    private BlockPos pickResult = null;
    private BlockState pickBlockState = null;
    private long pickInfoTime = 0;
    private boolean pendingPick = false;
    private int pendingPickMx, pendingPickMy;

    private static final long PICK_INFO_DURATION_MS = 15000;

    // Pick temp data (used during the render loop, not persisted between frames)
    private double bestPickRayT;
    private BlockPos bestPickPos;
    private BlockState bestPickState;

    private boolean immersive() { return userZoom > 3f; }

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

    // ================================================================
    //  Init
    // ================================================================

    @Override
    protected void init() {
        int gridPx = Math.min(width, height) * 4 / 5;
        cellSize = gridPx / 5;
        gridX = (width - 5 * cellSize) / 2;
        gridY = (height - 5 * cellSize) / 2;

        listL = gridX; listR = gridX + cellSize - 1;
        listT = gridY + cellSize; listB = gridY + 4 * cellSize - 1;
        renderL = gridX + cellSize; renderR = gridX + 4 * cellSize - 1;
        renderT = gridY + cellSize; renderB = gridY + 4 * cellSize - 1;
        futureL = gridX + 4 * cellSize; futureR = gridX + 5 * cellSize - 1;
        futureT = gridY + cellSize; futureB = gridY + 4 * cellSize - 1;
        editMode = false;
    }

    // ================================================================
    //  Render
    // ================================================================

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderBackground(g, mx, my, pt);
        if (!immersive()) drawHeader(g);
        if (!immersive()) drawList(g);
        drawRenderArea(g);
        if (!immersive()) drawFuture(g);

        // Picked block label (topmost layer — drawn after everything else)
        if (pickResult != null && pickBlockState != null) {
            Component name = pickBlockState.getBlock().getName();
            Component label = Component.literal("§e").copy()
                    .append(name)
                    .append(" §7[" + pickResult.getX() + "," + pickResult.getY() + "," + pickResult.getZ() + "]");
            int lw = font.width(label);
            g.drawString(font, label, (width - lw) / 2, 4, 0xFFFFFFFF);
        }

        // Hover hint
        if (selectedIndex >= 0 && pickResult == null
                && mx >= renderL && mx < renderR && my >= renderT && my < renderB) {
            Component hint = Component.translatable("eecore.visualizer.click_hint");
            int hw = font.width(hint);
            g.drawString(font, hint, mx - hw / 2, my - 12, 0x88FFFFFF);
        }
    }

    // ================================================================
    //  Pick Info Overlay
    // ================================================================


    // ================================================================
    //  GUI layout renders
    // ================================================================

    private void drawHeader(GuiGraphics g) {
        int hL = gridX + cellSize, hR = gridX + 4 * cellSize;
        int hT = gridY, hB = gridY + cellSize;
        String title = Component.translatable("eecore.visualizer.title").getString();
        Component rainbow = com.endlessepoch.core.api.text.AnimatedText.rainbow(title);
        int tw = font.width(title);
        float scale = (hR - hL - 12f) / tw;
        g.pose().pushPose();
        g.pose().translate(hL + (hR - hL) / 2f - tw * scale / 2f, hT + cellSize / 2f - 5, 0);
        g.pose().scale(scale, scale, 1);
        g.drawString(font, rainbow, 0, 0, 0x00FF88);
        g.pose().popPose();
    }

    private void drawList(GuiGraphics g) {
        g.fill(listL, listT, listR, listB, 0xCC111111);
        g.fill(listL, listT, listR, listT + 1, 0xFF003300);
        g.fill(listL, listB - 1, listR, listB, 0xFF003300);
        g.fill(listL, listT, listL + 1, listB, 0xFF003300);
        g.fill(listR - 1, listT, listR, listB, 0xFF003300);
        int x = listL + 4, y = listT + 4;
        g.drawString(font, Component.translatable("eecore.visualizer.patterns"), x, y - 2, 0x00FF88);
        if (patterns.isEmpty()) {
            g.drawString(font, Component.translatable("eecore.visualizer.empty"), x, y + 10, 0x666666);
            return;
        }
        int n = Math.min(patterns.size(), (listB - listT) / 12);
        for (int i = 0; i < n; i++) {
            int c = i == selectedIndex ? 0x00FF88 : 0x666666;
            String name = patterns.get(i).getKey().getPath();
            if (name.length() > 12) name = name.substring(0, 11) + ".";
            g.drawString(font, name, x, y + 10 + i * 12, c);
        }
    }

    // ================================================================
    //  3D render area + INLINE pick
    // ================================================================

    private void drawRenderArea(GuiGraphics g) {
        int rw = renderR - renderL, rh = renderB - renderT;
        if (selectedIndex < 0) { drawRenderBorder(g); return; }
        MultiBlockPattern pat = patterns.get(selectedIndex).getValue();
        float blockDiag = (float) Math.sqrt(pat.width*pat.width + pat.height*pat.height + pat.depth*pat.depth);
        float canvasDiag = (float) Math.sqrt(rw*rw + rh*rh);
        float cameraDist = 2000f * canvasDiag / (blockDiag + 3f) * userZoom * 0.02f;
        float blockPx = cameraDist * 0.5f;

        ResourceLocation id = patterns.get(selectedIndex).getKey();
        if (cachedScene == null || !id.equals(cachedPatternId)) {
            cachedScene = new EECoreSceneWorld(pat);
            cachedPatternId = id;
        }

        RenderSystem.enableDepthTest();
        PoseStack model = new PoseStack();
        model.translate(renderL + rw / 2f, renderT + rh / 2f, 0);
        Matrix4f projection = new Matrix4f().setOrtho(-rw / 2f, rw / 2f, -rh / 2f, rh / 2f, 100f, 4000f);
        model.mulPose(projection);
        model.translate(0, 0, -cameraDist);
        model.mulPose(new Quaternionf().rotateX((float) Math.toRadians(-rotX)));
        model.mulPose(new Quaternionf().rotateY((float) Math.toRadians(rotY)));
        model.scale(blockPx, blockPx, blockPx);
        model.translate(-pat.width*0.5f, -pat.height*0.5f, -pat.depth*0.5f);
        Matrix4f patternToScreen = new Matrix4f(model.last().pose());

        var renderer = Minecraft.getInstance().getBlockRenderer();
        var buf = Minecraft.getInstance().renderBuffers().bufferSource();

        // Reset pick tracking for this frame
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
                        boolean pulse = (System.currentTimeMillis() / 500) % 2 == 0;
                        st = pulse
                            ? net.minecraft.world.level.block.Blocks.RED_STAINED_GLASS.defaultBlockState()
                            : net.minecraft.world.level.block.Blocks.BLUE_STAINED_GLASS.defaultBlockState();
                    }
                    model.pushPose();
                    model.translate(x, y, z);

                    // Pick the nearest block hit by the mouse ray in pattern-local space.
                    if (pendingPick) {
                        double hit = intersectRayAabb(pickRayStart, pickRayDirection,
                                x, y, z, x + 1, y + 1, z + 1);
                        if (hit >= 0 && hit < bestPickRayT) {
                            bestPickRayT = hit;
                            bestPickPos = new BlockPos(x, y, z);
                            bestPickState = st;
                        }
                    }

                    renderer.renderSingleBlock(st, model, buf, 0xF000F0, OverlayTexture.NO_OVERLAY);
                    model.popPose();
                }

        // Resolve pending pick
        if (pendingPick) {
            pendingPick = false;
            if (bestPickPos != null) {
                pickResult = bestPickPos;
                pickBlockState = bestPickState;
                pickInfoTime = System.currentTimeMillis();
            }
            // No match: keep previous pickResult (so wireframe stays visible)
        }

        buf.endBatch();
        renderPickedBlockHighlight(model, buf);
        RenderSystem.disableDepthTest();
        if (!immersive()) drawRenderBorder(g);

        g.drawString(font, Component.translatable("eecore.visualizer.controller_label"), renderL + 4, renderT + 4, 0xFFFFFF);
    }

    private void renderPickedBlockHighlight(PoseStack model, net.minecraft.client.renderer.MultiBufferSource.BufferSource buf) {
        if (pickResult == null || pickBlockState == null || cachedScene == null) return;

        model.pushPose();
        model.translate(pickResult.getX(), pickResult.getY(), pickResult.getZ());

        VertexConsumer hlVc = buf.getBuffer(HIGHLIGHT_LINES);
        var shape = pickBlockState.getShape(cachedScene, pickResult, CollisionContext.empty());
        float pulse = (float)(Math.sin(System.currentTimeMillis() * 0.003) * 0.15 + 0.75);
        for (AABB box : shape.toAabbs()) {
            renderDebugLineBox(model.last(), hlVc, box, pulse, pulse, pulse, 0.9f);
        }

        model.popPose();
        buf.endBatch(HIGHLIGHT_LINES);
    }

    private static void renderDebugLineBox(PoseStack.Pose pose, VertexConsumer consumer, AABB box,
                                           float red, float green, float blue, float alpha) {
        addLine(pose, consumer, box.minX, box.minY, box.minZ, box.maxX, box.minY, box.minZ, red, green, blue, alpha);
        addLine(pose, consumer, box.maxX, box.minY, box.minZ, box.maxX, box.minY, box.maxZ, red, green, blue, alpha);
        addLine(pose, consumer, box.maxX, box.minY, box.maxZ, box.minX, box.minY, box.maxZ, red, green, blue, alpha);
        addLine(pose, consumer, box.minX, box.minY, box.maxZ, box.minX, box.minY, box.minZ, red, green, blue, alpha);

        addLine(pose, consumer, box.minX, box.maxY, box.minZ, box.maxX, box.maxY, box.minZ, red, green, blue, alpha);
        addLine(pose, consumer, box.maxX, box.maxY, box.minZ, box.maxX, box.maxY, box.maxZ, red, green, blue, alpha);
        addLine(pose, consumer, box.maxX, box.maxY, box.maxZ, box.minX, box.maxY, box.maxZ, red, green, blue, alpha);
        addLine(pose, consumer, box.minX, box.maxY, box.maxZ, box.minX, box.maxY, box.minZ, red, green, blue, alpha);

        addLine(pose, consumer, box.minX, box.minY, box.minZ, box.minX, box.maxY, box.minZ, red, green, blue, alpha);
        addLine(pose, consumer, box.maxX, box.minY, box.minZ, box.maxX, box.maxY, box.minZ, red, green, blue, alpha);
        addLine(pose, consumer, box.maxX, box.minY, box.maxZ, box.maxX, box.maxY, box.maxZ, red, green, blue, alpha);
        addLine(pose, consumer, box.minX, box.minY, box.maxZ, box.minX, box.maxY, box.maxZ, red, green, blue, alpha);
    }

    private static void addLine(PoseStack.Pose pose, VertexConsumer consumer,
                                double x1, double y1, double z1,
                                double x2, double y2, double z2,
                                float red, float green, float blue, float alpha) {
        consumer.addVertex(pose, (float)x1, (float)y1, (float)z1).setColor(red, green, blue, alpha);
        consumer.addVertex(pose, (float)x2, (float)y2, (float)z2).setColor(red, green, blue, alpha);
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
        g.fill(renderL, renderT, renderR, renderT + 1, 0xFF00AA44);
        g.fill(renderL, renderB - 1, renderR, renderB, 0xFF00AA44);
        g.fill(renderL, renderT, renderL + 1, renderB, 0xFF00AA44);
        g.fill(renderR - 1, renderT, renderR, renderB, 0xFF00AA44);
    }

    private void drawFuture(GuiGraphics g) {
        g.fill(futureL, futureT, futureR, futureB, 0xCC111111);
        g.fill(futureL, futureT, futureR, futureT + 1, 0xFF003300);
        g.fill(futureL, futureB - 1, futureR, futureB, 0xFF003300);
        g.fill(futureL, futureT, futureL + 1, futureB, 0xFF003300);
        g.fill(futureR - 1, futureT, futureR, futureB, 0xFF003300);

        String editLabel = editMode ? "✕ 退出" : "编辑";
        int ew = font.width(editLabel);
        int ex = futureL + (futureR - futureL - ew) / 2;
        g.drawString(font, editLabel, ex, futureT + 4, editMode ? 0xFF6666 : 0x00FF88);

        if (editMode && selectedIndex >= 0) {
            java.util.Set<String> seen = new java.util.LinkedHashSet<>();
            MultiBlockPattern pat = patterns.get(selectedIndex).getValue();
            for (int y = 0; y < pat.height; y++)
                for (int z = 0; z < pat.depth; z++)
                    for (int x = 0; x < pat.width; x++) {
                        char c = pat.getChar(x, y, z);
                        if (c == ' ' || c == '_') continue;
                        String name = pat.getExpectedState(x, y, z).getBlock().getName().getString();
                        if (name.length() > 10) name = name.substring(0, 9) + ".";
                        seen.add(c + "=" + name);
                    }
            int ly = futureT + 36;
            g.drawString(font, "方块列表:", futureL + 4, futureT + 22, 0x00FF88);
            for (String s : seen) {
                g.drawString(font, s, futureL + 4, ly, 0xAAAAAA);
                ly += 10;
                if (ly > futureB - 4) break;
            }
        }
    }

    // ================================================================
    //  Input
    // ================================================================

    @Override
    public boolean mouseClicked(double mx, double my, int btn) {
        if (btn == 0 && mx >= futureL && mx <= futureR && my >= futureT && my <= futureT + 14) {
            editMode = !editMode;
            return true;
        }

        if (btn == 0 && selectedIndex >= 0
                && mx >= renderL && mx < renderR && my >= renderT && my < renderB) {
            pendingPick = true;
            pendingPickMx = (int) mx;
            pendingPickMy = (int) my;
            return true;
        }

        return super.mouseClicked(mx, my, btn);
    }

    @Override
    public boolean mouseDragged(double mx, double my, int btn, double dx, double dy) {
        if (mx >= renderL && mx <= renderR && btn == 0) {
            rotY += dx * 0.4f;
            rotX -= dy * 0.4f;
            rotX = (float) Math.max(90, Math.min(270, rotX));
            pickResult = null;
            pickBlockState = null;
        }
        return mx >= renderL && mx <= renderR;
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double sx, double sy) {
        if (mx >= renderL && mx <= renderR) {
            if (userZoom > 5) userZoom += sy > 0 ? 1f : -1f;
            else if (userZoom == 5) userZoom += sy > 0 ? 1f : -0.1f;
            else if (userZoom > 0.2f) userZoom += sy > 0 ? 0.1f : -0.1f;
            else userZoom += sy > 0 ? 0.01f : -0.01f;
            userZoom = (float) Math.max(0.01f, Math.min(100, userZoom));
            pickResult = null;
            pickBlockState = null;
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
        if (k == 71) { rotX = 150; rotY = -45; userZoom = 1; return true; }
        if (k == 87 || k == 265) { selectedIndex = Math.max(0, selectedIndex - 1); return true; }
        if (k == 83 || k == 264) { selectedIndex = Math.min(patterns.size() - 1, selectedIndex + 1); return true; }
        return super.keyPressed(k, s, m);
    }

    @Override public boolean isPauseScreen() { return false; }
}
