package com.endlessepoch.core.nova.client;

import com.endlessepoch.core.api.multiblock.IMachineEffect;
import com.endlessepoch.core.api.multiblock.IMultiBlockController;
import com.endlessepoch.core.api.multiblock.MachineRegistry;
import com.endlessepoch.core.nova.block.MachineControllerBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

/**
 * Effect dispatcher: finds formed controllers, calls their machine-bound
 * {@link IMachineEffect} if registered.
 * <p>
 * 特效调度器：遍历成形控制器，调用机器绑定的 IMachineEffect。
 */
public final class CelestialRenderer {

    private CelestialRenderer() {}

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) return;
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

                    var effect = findEffect(be);
                    if (effect == null) continue;

                    double sx = be.getBlockPos().getX() + 0.5 - cam.x;
                    double sy = be.getBlockPos().getY() + 0.5 - cam.y;
                    double sz = be.getBlockPos().getZ() + 0.5 - cam.z;
                    if (sx*sx + sy*sy + sz*sz > 128*128) continue;

                    ps.pushPose();
                    ps.translate(sx, sy, sz);
                    effect.render(ps, be, pt);
                    ps.popPose();
                }
            }
    }

    /** Look up machine-specific effect; fall back to built-in celestial for legacy. / 查找机器专属特效，未注册则回退到内置日月星辰。 */
    private static IMachineEffect findEffect(BlockEntity be) {
        if (be instanceof MachineControllerBlockEntity mcbe) {
            var def = MachineRegistry.get(mcbe.getMachineId());
            if (def.isPresent() && def.get().hasEffect()) return def.get().getEffect();
            return null;
        }
        return new CelestialEffect();
    }
}
