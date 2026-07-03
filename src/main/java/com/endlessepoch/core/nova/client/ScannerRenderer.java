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
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

import java.util.List;

/**
 * Client-side renderer for multiblock scanner overlays.
 * Draws selection boxes, controller highlights (with pulse animation), boundary placement preview.
 * <p>
 * 多方块扫描器客户端渲染器，绘制选区框、控制器高亮（脉冲动画）及边界放置预览。
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

        // Debug: panel outline on targeted controller / 面板线框调试
        if (player.isShiftKeyDown() && mc.hitResult instanceof BlockHitResult bhr
                && mc.level.getBlockEntity(bhr.getBlockPos()) instanceof com.endlessepoch.core.nova.block.ScannerControllerBlockEntity) {
            BlockPos cpos = bhr.getBlockPos();
            var state = mc.level.getBlockState(cpos);
            if (state.hasProperty(com.endlessepoch.core.nova.block.ScannerControllerBlock.FACING)) {
                var dir = state.getValue(com.endlessepoch.core.nova.block.ScannerControllerBlock.FACING);
                VertexConsumer lvc = buf.getBuffer(RenderType.lines());
                float d = 0.005f, i2 = 2f/16f, i14 = 14f/16f;
                // Full block outline (green) / 完整方块边框
                LevelRenderer.renderLineBox(ps, lvc, new AABB(cpos), 0, 1, 0, 0.4f);
                // Panel outline on front face (red) / 正面面板边框
                var box = switch (dir) {
                    case NORTH -> new AABB(cpos.getX()+i2, cpos.getY()+i2, cpos.getZ()-d,
                            cpos.getX()+i14, cpos.getY()+i14, cpos.getZ()+d);
                    case SOUTH -> new AABB(cpos.getX()+i2, cpos.getY()+i2, cpos.getZ()+1-d,
                            cpos.getX()+i14, cpos.getY()+i14, cpos.getZ()+1+d);
                    case EAST -> new AABB(cpos.getX()+1-d, cpos.getY()+i2, cpos.getZ()+i2,
                            cpos.getX()+1+d, cpos.getY()+i14, cpos.getZ()+i14);
                    case WEST -> new AABB(cpos.getX()-d, cpos.getY()+i2, cpos.getZ()+i2,
                            cpos.getX()+d, cpos.getY()+i14, cpos.getZ()+i14);
                    default -> null;
                };
                if (box != null) LevelRenderer.renderLineBox(ps, lvc, box, 1, 0, 0, 0.8f);
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

    // Boundary block placement preview / 辅助块放置预缓存
    private static long boundaryCacheTick = -1;
    private static BlockPos boundaryCachePos = null;

    @SubscribeEvent
    public static void onRenderBoundary(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) return;
        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        if (player == null) return;
        ItemStack held = player.getMainHandItem();
        if (!(held.getItem() instanceof BlockItem bi)
                || !(bi.getBlock() instanceof com.endlessepoch.core.nova.block.ScannerBoundaryBlock)) return;

        long gametime = mc.level.getGameTime();
        BlockPos target;
        if (gametime == boundaryCacheTick && boundaryCachePos != null) {
            target = boundaryCachePos;
        } else {
            var hit = player.pick(5.0, 0.0f, false);
            if (hit.getType() == HitResult.Type.BLOCK) {
                target = ((BlockHitResult) hit).getBlockPos().relative(((BlockHitResult) hit).getDirection());
            } else {
                target = BlockPos.containing(hit.getLocation());
            }
            boundaryCacheTick = gametime;
            boundaryCachePos = target;
        }
        if (!mc.level.isEmptyBlock(target)) return;

        PoseStack ps = event.getPoseStack();
        Vec3 cam = mc.gameRenderer.getMainCamera().getPosition();
        ps.pushPose();
        ps.translate(-cam.x, -cam.y, -cam.z);
        ps.translate(target.getX(), target.getY(), target.getZ());

        float alpha = 0.5f + (float) Math.sin(System.currentTimeMillis() * 0.004) * 0.2f;
        VertexConsumer vc = mc.renderBuffers().bufferSource().getBuffer(RenderType.lines());
        LevelRenderer.renderLineBox(ps, vc,
                new AABB(0.02, 0.02, 0.02, 0.98, 0.98, 0.98),
                0.4f, 0.8f, 1.0f, alpha);
        mc.renderBuffers().bufferSource().endBatch();
        ps.popPose();
    }
}
