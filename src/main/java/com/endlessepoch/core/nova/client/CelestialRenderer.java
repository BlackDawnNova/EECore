package com.endlessepoch.core.nova.client;

import com.endlessepoch.core.api.multiblock.IMachineEffect;
import com.endlessepoch.core.api.multiblock.IMultiBlockController;
import com.endlessepoch.core.api.multiblock.MachineRegistry;
import com.endlessepoch.core.api.multiblock.MultiBlockRegistry;
import com.endlessepoch.core.nova.block.MachineControllerBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

/**
 * Effect dispatcher: finds formed controllers, calls their machine-bound {@link IMachineEffect}.
 * <p>
 * 特效调度器：遍历成形控制器，调用机器绑定的 IMachineEffect。
 */
public final class CelestialRenderer {

    private CelestialRenderer() {}

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) return;
        if (com.endlessepoch.core.Config.p4DisableEffects) return;
        var level = Minecraft.getInstance().level; if (level == null) return;
        var player = Minecraft.getInstance().player; if (player == null) return;

        var ps = event.getPoseStack();
        var cam = event.getCamera().getPosition();
        float pt = event.getPartialTick().getGameTimeDeltaPartialTick(false);

        int pcx = player.chunkPosition().x, pcz = player.chunkPosition().z;
        for (int dx = -5; dx <= 5; dx++)
            for (int dz = -5; dz <= 5; dz++) {
                var chunk = level.getChunk(pcx + dx, pcz + dz);
                for (BlockEntity be : chunk.getBlockEntities().values()) {
                    if (!(be instanceof IMultiBlockController ctrl) || !ctrl.isFormed()) continue;
                    if (be instanceof MachineControllerBlockEntity mcbe && !mcbe.isEffectEnabled()) continue;

                    var effect = findEffect(be);
                    if (effect == null) continue;

                    double sx = be.getBlockPos().getX() + 0.5 - cam.x;
                    double sy = be.getBlockPos().getY() + 0.5 - cam.y;
                    double sz = be.getBlockPos().getZ() + 0.5 - cam.z;
                    if (sx*sx + sy*sy + sz*sz > 128*128) continue;

                    boolean inside = isInsideStructure(be, player.blockPosition());
                    if (!inside && isBlockedBySolids(be, player)) continue;
                    if (inside) com.mojang.blaze3d.systems.RenderSystem.disableDepthTest();

                    ps.pushPose();
                    ps.translate(sx, sy, sz);
                    effect.render(ps, be, pt);
                    ps.popPose();

                    if (inside) com.mojang.blaze3d.systems.RenderSystem.enableDepthTest();
                }
            }
    }

    private static IMachineEffect findEffect(BlockEntity be) {
        if (be instanceof MachineControllerBlockEntity mcbe) {
            var def = MachineRegistry.get(mcbe.getMachineId());
            if (def.isPresent() && def.get().hasEffect()) return def.get().getEffect();
            return null;
        }
        return new CelestialEffect();
    }

    /** Check if player is within the formed structure. / 玩家是否在多方块结构内部。 */
    private static boolean isInsideStructure(BlockEntity be, BlockPos playerPos) {
        if (!(be instanceof MachineControllerBlockEntity mcbe)) return false;
        var machineId = mcbe.getMachineId();
        if (machineId == null) return false;
        var opt = MultiBlockRegistry.get(machineId);
        if (opt.isEmpty()) return false;
        var p = opt.get();
        Direction facing = mcbe.getFacing();
        BlockPos pos = be.getBlockPos();
        for (int y = 0; y < p.height; y++)
            for (int z = 0; z < p.depth; z++)
                for (int x = 0; x < p.width; x++) {
                    int rx = x - p.controllerX, ry = y - p.controllerY, rz = z - p.controllerZ;
                    BlockPos wp = switch (facing) {
                        case NORTH -> pos.offset(rx, ry, rz);
                        case SOUTH -> pos.offset(-rx, ry, -rz);
                        case EAST  -> pos.offset(-rz, ry, rx);
                        case WEST  -> pos.offset(rz, ry, -rx);
                        default    -> pos.offset(rx, ry, rz);
                    };
                    if (wp.equals(playerPos)) return true;
                }
        return false;
    }

    /** Check if solid blocks block line-of-sight from player to controller. / 玩家与控制器之间是否有不透明方块。 */
    private static boolean isBlockedBySolids(BlockEntity be, net.minecraft.world.entity.player.Player player) {
        var level = player.level();
        var eye = player.getEyePosition();
        var to = net.minecraft.world.phys.Vec3.atCenterOf(be.getBlockPos());
        var hit = level.clip(new net.minecraft.world.level.ClipContext(
                eye, to,
                net.minecraft.world.level.ClipContext.Block.COLLIDER,
                net.minecraft.world.level.ClipContext.Fluid.NONE,
                player));
        return hit.getType() == net.minecraft.world.phys.HitResult.Type.BLOCK
                && level.getBlockState(hit.getBlockPos()).isSolidRender(level, hit.getBlockPos());
    }
}
