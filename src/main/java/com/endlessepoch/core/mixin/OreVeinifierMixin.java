package com.endlessepoch.core.mixin;

import net.minecraft.world.level.levelgen.NoiseChunk;
import net.minecraft.world.level.levelgen.OreVeinifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Disables vanilla 1.18+ large ore veins (copper iron). These use noise-density generation
 * that bypasses OreConfiguration — a separate mixin is required alongside OreConfigurationMixin.
 * 禁用原版大型矿脉(铜/铁), 其噪声密度生成绕过 OreConfiguration 需独立 Mixin。
 */
@Mixin(OreVeinifier.class)
public class OreVeinifierMixin {

    @Inject(method = "create(Lnet/minecraft/world/level/levelgen/DensityFunction;"
            + "Lnet/minecraft/world/level/levelgen/DensityFunction;"
            + "Lnet/minecraft/world/level/levelgen/DensityFunction;"
            + "Lnet/minecraft/world/level/levelgen/PositionalRandomFactory;)"
            + "Lnet/minecraft/world/level/levelgen/NoiseChunk$BlockStateFiller;",
            at = @At("HEAD"), cancellable = true)
    private static void eecore$disableLargeOreVeins(CallbackInfoReturnable<NoiseChunk.BlockStateFiller> cir) {
        cir.setReturnValue((NoiseChunk.BlockStateFiller) ctx -> null);
    }
}
