package com.endlessepoch.core.mixin;

import com.endlessepoch.core.antixray.AntiXrayEngine;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ClientboundLevelChunkPacketData;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Map;

/**
 * Ore disguise: swap chunk sections + BE list with stone-disguised copies at packet build time.
 * Size calc and data extract MUST see the same sections — palette change shifts serialized size.
 * 矿石伪装：包构造时替换所有矿→石头。尺寸计算与写出必须用同一份。
 */
@Mixin(ClientboundLevelChunkPacketData.class)
public class ClientboundLevelChunkPacketDataMixin {

    @Redirect(method = "calculateChunkSize(Lnet/minecraft/world/level/chunk/LevelChunk;)I",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/level/chunk/LevelChunk;getSections()[Lnet/minecraft/world/level/chunk/LevelChunkSection;"))
    private static LevelChunkSection[] eecore$disguiseSize(LevelChunk chunk) {
        return AntiXrayEngine.getObfuscatedSections(chunk);
    }

    @Redirect(method = "extractChunkData(Lnet/minecraft/network/FriendlyByteBuf;Lnet/minecraft/world/level/chunk/LevelChunk;)V",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/level/chunk/LevelChunk;getSections()[Lnet/minecraft/world/level/chunk/LevelChunkSection;"))
    private static LevelChunkSection[] eecore$disguiseData(LevelChunk chunk) {
        return AntiXrayEngine.getObfuscatedSections(chunk);
    }

    // Disguised ore BEs would leak coordinates / 伪装矿的BE条目会泄漏坐标
    @Redirect(method = "<init>(Lnet/minecraft/world/level/chunk/LevelChunk;)V",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/level/chunk/LevelChunk;getBlockEntities()Ljava/util/Map;"))
    private Map<BlockPos, BlockEntity> eecore$filterBlockEntities(LevelChunk chunk) {
        return AntiXrayEngine.getFilteredBlockEntities(chunk);
    }

    @Inject(method = "<init>(Lnet/minecraft/world/level/chunk/LevelChunk;)V", at = @At("RETURN"))
    private void eecore$clearCache(LevelChunk chunk, CallbackInfo ci) {
        AntiXrayEngine.clearCache(chunk.getPos());
    }
}
