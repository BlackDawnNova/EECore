package com.endlessepoch.core.nova.network.laser;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.joml.Matrix4f;

/**
 * Renders laser beams between connected transmitter/receiver pairs.
 * <p>
 * Beam color = transmitter voltage tier color.
 * Beam thickness = current power flow.
 * Beam pulses when energy is actively flowing.
 */
@OnlyIn(Dist.CLIENT)
public final class LaserRenderer {

    private LaserRenderer() {}

    /**
     * Render all active lasers. Hook into {@code RenderLevelLastEvent}.
     */
    public static void renderAll(PoseStack poseStack, Vec3 cameraPos) {
        if (!LaserRenderConfig.isEnabled()) return;

        var player = Minecraft.getInstance().player;
        if (player == null) return;

        double maxDist2 = (double) LaserRenderConfig.getMaxRenderDistance()
                * LaserRenderConfig.getMaxRenderDistance();

        for (LaserConnection conn : LaserManager.getAll()) {
            Vec3 tx = Vec3.atCenterOf(conn.getTransmitterPos());
            Vec3 rx = Vec3.atCenterOf(conn.getReceiverPos());

            // Skip if too far
            if (tx.distanceToSqr(player.position()) > maxDist2
                    && rx.distanceToSqr(player.position()) > maxDist2) {
                continue;
            }

            renderBeam(poseStack, cameraPos, tx, rx, conn);
        }
    }

    private static void renderBeam(PoseStack poseStack, Vec3 cameraPos,
                                    Vec3 from, Vec3 to, LaserConnection conn) {
        double x1 = from.x - cameraPos.x;
        double y1 = from.y - cameraPos.y;
        double z1 = from.z - cameraPos.z;
        double x2 = to.x - cameraPos.x;
        double y2 = to.y - cameraPos.y;
        double z2 = to.z - cameraPos.z;

        // Color from voltage tier
        String hex = conn.getTier().getHexColor();
        int color = Integer.parseInt(hex.substring(1), 16);
        float r = ((color >> 16) & 0xFF) / 255f;
        float g = ((color >> 8) & 0xFF) / 255f;
        float b = (color & 0xFF) / 255f;
        float alpha = conn.isActive() ? 0.8f : 0.2f;

        // Thickness = 1.0 + power scaling
        double thickness = 0.02 + Math.min(0.08, conn.getCurrentPower() / 1000.0);

        VertexConsumer vc = Minecraft.getInstance().renderBuffers().bufferSource()
                .getBuffer(RenderType.lines());

        Matrix4f mat = poseStack.last().pose();

        // Perpendicular offset for thick beam illusion (draw 4 horizontal shifted lines)
        Vec3 dir = new Vec3(x2 - x1, y2 - y1, z2 - z1).normalize();
        Vec3 perpX = new Vec3(0, -dir.z, dir.y).normalize().scale(thickness);
        Vec3 perpZ = new Vec3(dir.z, 0, -dir.x).normalize().scale(thickness);

        vc.addVertex(mat, (float)(x1 + perpX.x), (float)(y1 + perpX.y), (float)(z1 + perpX.z)).setColor(r, g, b, alpha);
        vc.addVertex(mat, (float)(x2 + perpX.x), (float)(y2 + perpX.y), (float)(z2 + perpX.z)).setColor(r, g, b, alpha);

        vc.addVertex(mat, (float)(x1 - perpX.x), (float)(y1 - perpX.y), (float)(z1 - perpX.z)).setColor(r, g, b, alpha);
        vc.addVertex(mat, (float)(x2 - perpX.x), (float)(y2 - perpX.y), (float)(z2 - perpX.z)).setColor(r, g, b, alpha);

        vc.addVertex(mat, (float)(x1 + perpZ.x), (float)(y1 + perpZ.y), (float)(z1 + perpZ.z)).setColor(r, g, b, alpha);
        vc.addVertex(mat, (float)(x2 + perpZ.x), (float)(y2 + perpZ.y), (float)(z2 + perpZ.z)).setColor(r, g, b, alpha);

        vc.addVertex(mat, (float)(x1 - perpZ.x), (float)(y1 - perpZ.y), (float)(z1 - perpZ.z)).setColor(r, g, b, alpha);
        vc.addVertex(mat, (float)(x2 - perpZ.x), (float)(y2 - perpZ.y), (float)(z2 - perpZ.z)).setColor(r, g, b, alpha);
    }
}
