package com.endlessepoch.core.api.multiblock;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.joml.Matrix4f;

/**
 * Renders ghost-block preview of a multiblock structure in-world.
 * <p>
 * Triggered when a player shift-right-clicks the controller with an empty hand.
 * Correctly-placed blocks render green; missing blocks render red.
 * Only used client-side.
 */
@OnlyIn(Dist.CLIENT)
public final class MultiBlockPreviewRenderer {

    /** Duration in milliseconds to show the preview. */
    private static final long PREVIEW_DURATION_MS = 5000;

    // Currently active preview (static for singleton-per-controller)
    private static MultiBlockPattern activePattern;
    private static BlockPos activeControllerPos;
    private static Direction activeFacing;
    private static Level activeLevel;
    private static long previewStartTime;

    private MultiBlockPreviewRenderer() {}

    /**
     * Trigger a preview. Call from client-side when player shift-right-clicks controller.
     */
    public static void showPreview(MultiBlockPattern pattern, BlockPos controllerPos,
                                   Direction facing, Level level) {
        activePattern = pattern;
        activeControllerPos = controllerPos;
        activeFacing = facing;
        activeLevel = level;
        previewStartTime = System.currentTimeMillis();
    }

    /** Returns true if a preview is currently active and not expired. */
    public static boolean isActive() {
        return activePattern != null && activeControllerPos != null
                && System.currentTimeMillis() - previewStartTime < PREVIEW_DURATION_MS;
    }

    /**
     * Render the preview overlay. Call from a world render event or level renderer hook.
     * Use {@code RenderLevelLastEvent} to hook into the rendering pipeline.
     */
    public static void render(PoseStack poseStack, Vec3 cameraPos) {
        if (!isActive()) {
            activePattern = null;
            return;
        }

        BlockPos origin = activeControllerPos.offset(
                -activePattern.controllerX,
                -activePattern.controllerY,
                -activePattern.controllerZ);
        int w = activePattern.width;
        int d = activePattern.depth;

        for (int y = 0; y < activePattern.height; y++) {
            for (int z = 0; z < d; z++) {
                for (int x = 0; x < w; x++) {
                    char c = activePattern.getChar(x, y, z);
                    if (c == ' ' || c == '_') continue;

                    BlockState required = activePattern.getExpectedState(x, y, z);
                    if (required == null) continue;

                    BlockPos worldPos = MultiBlockValidator.transform(
                            origin, x, y, z, w, d, activeFacing);
                    BlockState actual = activeLevel.getBlockState(worldPos);
                    boolean matches = actual.getBlock().equals(required.getBlock());

                    renderGhostBlock(poseStack, cameraPos, worldPos,
                            matches ? 0x4400FF00 : 0x44FF0000); // green=correct, red=missing
                }
            }
        }
    }

    private static void renderGhostBlock(PoseStack poseStack, Vec3 cameraPos,
                                          BlockPos pos, int colorArgb) {
        double x = pos.getX() - cameraPos.x;
        double y = pos.getY() - cameraPos.y;
        double z = pos.getZ() - cameraPos.z;

        float a = ((colorArgb >> 24) & 0xFF) / 255f;
        float r = ((colorArgb >> 16) & 0xFF) / 255f;
        float g = ((colorArgb >> 8) & 0xFF) / 255f;
        float b = (colorArgb & 0xFF) / 255f;

        VertexConsumer vc = Minecraft.getInstance().renderBuffers().bufferSource()
                .getBuffer(RenderType.lines());

        Matrix4f mat = poseStack.last().pose();
        float x1 = (float) x, y1 = (float) y, z1 = (float) z;
        float x2 = x1 + 1, y2 = y1 + 1, z2 = z1 + 1;

        // 12 lines for a wireframe cube
        // bottom face
        vc.addVertex(mat, x1, y1, z1).setColor(r, g, b, a);
        vc.addVertex(mat, x2, y1, z1).setColor(r, g, b, a);
        vc.addVertex(mat, x2, y1, z1).setColor(r, g, b, a);
        vc.addVertex(mat, x2, y1, z2).setColor(r, g, b, a);
        vc.addVertex(mat, x2, y1, z2).setColor(r, g, b, a);
        vc.addVertex(mat, x1, y1, z2).setColor(r, g, b, a);
        vc.addVertex(mat, x1, y1, z2).setColor(r, g, b, a);
        vc.addVertex(mat, x1, y1, z1).setColor(r, g, b, a);
        // top face
        vc.addVertex(mat, x1, y2, z1).setColor(r, g, b, a);
        vc.addVertex(mat, x2, y2, z1).setColor(r, g, b, a);
        vc.addVertex(mat, x2, y2, z1).setColor(r, g, b, a);
        vc.addVertex(mat, x2, y2, z2).setColor(r, g, b, a);
        vc.addVertex(mat, x2, y2, z2).setColor(r, g, b, a);
        vc.addVertex(mat, x1, y2, z2).setColor(r, g, b, a);
        vc.addVertex(mat, x1, y2, z2).setColor(r, g, b, a);
        vc.addVertex(mat, x1, y2, z1).setColor(r, g, b, a);
        // vertical edges
        vc.addVertex(mat, x1, y1, z1).setColor(r, g, b, a);
        vc.addVertex(mat, x1, y2, z1).setColor(r, g, b, a);
        vc.addVertex(mat, x2, y1, z1).setColor(r, g, b, a);
        vc.addVertex(mat, x2, y2, z1).setColor(r, g, b, a);
        vc.addVertex(mat, x2, y1, z2).setColor(r, g, b, a);
        vc.addVertex(mat, x2, y2, z2).setColor(r, g, b, a);
        vc.addVertex(mat, x1, y1, z2).setColor(r, g, b, a);
        vc.addVertex(mat, x1, y2, z2).setColor(r, g, b, a);
    }
}
