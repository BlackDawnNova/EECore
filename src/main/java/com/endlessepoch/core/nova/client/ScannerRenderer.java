package com.endlessepoch.core.nova.client;

import com.endlessepoch.core.EECore;
import com.endlessepoch.core.nova.item.MultiblockScannerItem;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

import java.util.List;

/**
 * Renders the scanner selection box and multi-controller highlights in-world.
 */
@OnlyIn(Dist.CLIENT)
@EventBusSubscriber(modid = EECore.MOD_ID, value = Dist.CLIENT)
public final class ScannerRenderer {

    private static final RenderType NO_DEPTH_LINES = RenderType.create(
            "eecore_no_depth_lines",
            DefaultVertexFormat.POSITION_COLOR_NORMAL,
            VertexFormat.Mode.LINES,
            16384,
            false, false,
            RenderType.CompositeState.builder()
                    .setShaderState(RenderType.RENDERTYPE_LINES_SHADER)
                    .setLineState(new RenderType.LineStateShard(java.util.OptionalDouble.empty()))
                    .setDepthTestState(RenderType.NO_DEPTH_TEST)
                    .setTransparencyState(RenderType.TRANSLUCENT_TRANSPARENCY)
                    .setOutputState(RenderType.MAIN_TARGET)
                    .setCullState(RenderType.NO_CULL)
                    .createCompositeState(false)
    );

    private ScannerRenderer() {}

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) return;

        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        if (player == null || mc.level == null) return;

        ItemStack stack = findScanner(player);
        if (stack == null) return;

        BlockPos pos1 = MultiblockScannerItem.getPos1(stack);
        BlockPos pos2 = MultiblockScannerItem.getPos2(stack);

        PoseStack ps = event.getPoseStack();
        MultiBufferSource.BufferSource buf = mc.renderBuffers().bufferSource();
        Vec3 cam = mc.gameRenderer.getMainCamera().getPosition();
        ps.pushPose();
        ps.translate(-cam.x, -cam.y, -cam.z);

        if (pos1 != null) {
            VertexConsumer vc = buf.getBuffer(RenderType.lines());

            renderBlockOutline(ps, vc, pos1, 0.2f, 0.6f, 1.0f);

            if (pos2 != null) {
                renderBlockOutline(ps, vc, pos2, 1.0f, 0.65f, 0.0f);

                int minX = Math.min(pos1.getX(), pos2.getX());
                int minY = Math.min(pos1.getY(), pos2.getY());
                int minZ = Math.min(pos1.getZ(), pos2.getZ());
                int maxX = Math.max(pos1.getX(), pos2.getX());
                int maxY = Math.max(pos1.getY(), pos2.getY());
                int maxZ = Math.max(pos1.getZ(), pos2.getZ());
                AABB box = new AABB(minX + 0.002, minY + 0.002, minZ + 0.002,
                        maxX + 0.998, maxY + 0.998, maxZ + 0.998);
                LevelRenderer.renderLineBox(ps, vc, box, 0f, 1f, 0f, 0.4f);
            }
        }

        List<BlockPos> controllers = MultiblockScannerItem.getControllerPositions(stack);
        if (!controllers.isEmpty()) {
            buf.endBatch();

            VertexConsumer hlVc = buf.getBuffer(NO_DEPTH_LINES);
            long time = System.currentTimeMillis();
            float pulse = (float) (Math.sin(time * 0.004) * 0.3 + 0.7);

            for (BlockPos ctrlPos : controllers) {
                AABB ctrlBox = new AABB(ctrlPos.getX() + 0.01, ctrlPos.getY() + 0.01, ctrlPos.getZ() + 0.01,
                        ctrlPos.getX() + 0.99, ctrlPos.getY() + 0.99, ctrlPos.getZ() + 0.99);
                LevelRenderer.renderLineBox(ps, hlVc, ctrlBox, pulse, 0.05f, 0.05f, 0.9f);

                float glowPulse = pulse * 0.4f;
                AABB glowBox = new AABB(ctrlPos.getX() - 0.08, ctrlPos.getY() - 0.08, ctrlPos.getZ() - 0.08,
                        ctrlPos.getX() + 1.08, ctrlPos.getY() + 1.08, ctrlPos.getZ() + 1.08);
                LevelRenderer.renderLineBox(ps, hlVc, glowBox, glowPulse, 0.0f, 0.0f, 0.3f);

                AABB beam = new AABB(ctrlPos.getX() + 0.47, ctrlPos.getY(),
                        ctrlPos.getZ() + 0.47,
                        ctrlPos.getX() + 0.53, ctrlPos.getY() + 64,
                        ctrlPos.getZ() + 0.53);
                LevelRenderer.renderLineBox(ps, hlVc, beam, pulse * 0.8f, 0.1f, 0.1f, 0.6f);
            }

            buf.endBatch();
        } else {
            buf.endBatch();
        }
        ps.popPose();
    }

    private static void renderBlockOutline(PoseStack ps, VertexConsumer vc,
                                            BlockPos pos, float r, float g, float b) {
        AABB box = new AABB(pos.getX(), pos.getY(), pos.getZ(),
                pos.getX() + 1, pos.getY() + 1, pos.getZ() + 1);
        LevelRenderer.renderLineBox(ps, vc, box, r, g, b, 0.8f);
    }

    private static ItemStack findScanner(Player player) {
        for (ItemStack s : new ItemStack[]{player.getMainHandItem(), player.getOffhandItem()}) {
            if (s.getItem() instanceof MultiblockScannerItem) return s;
        }
        return null;
    }
}
