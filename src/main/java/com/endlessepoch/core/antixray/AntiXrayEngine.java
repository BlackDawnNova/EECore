package com.endlessepoch.core.antixray;

import com.endlessepoch.core.block.OreBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.PalettedContainer;

import java.util.HashMap;
import java.util.Map;

/**
 * Disguise ALL eecore ores as stone/deepslate in chunk packets.
 * ProximityRevealer later sends individual block-update packets to reveal ores near players.
 * 区块包中伪装全部 eecore 矿。靠近后由 ProximityRevealer 逐玩家发包揭示。
 */
public final class AntiXrayEngine {

    private static final ThreadLocal<Result> CACHE = new ThreadLocal<>();

    private record Result(ChunkPos pos, LevelChunkSection[] sections,
                          Map<BlockPos, BlockEntity> blockEntities) {}

    private AntiXrayEngine() {}

    public static LevelChunkSection[] getObfuscatedSections(LevelChunk chunk) {
        return resultFor(chunk).sections();
    }

    public static Map<BlockPos, BlockEntity> getFilteredBlockEntities(LevelChunk chunk) {
        return resultFor(chunk).blockEntities();
    }

    public static void clearCache(ChunkPos pos) {
        Result cur = CACHE.get();
        if (cur != null && cur.pos().equals(pos)) CACHE.remove();
    }

    private static Result resultFor(LevelChunk chunk) {
        Result cached = CACHE.get();
        if (cached != null && cached.pos().equals(chunk.getPos())) return cached;
        Result fresh = disguise(chunk);
        CACHE.set(fresh);
        return fresh;
    }

    private static Result disguise(LevelChunk chunk) {
        LevelChunkSection[] src = chunk.getSections();
        LevelChunkSection[] dst = new LevelChunkSection[src.length];
        boolean anyHidden = false;

        for (int si = 0; si < src.length; si++) {
            LevelChunkSection section = src[si];
            if (section.hasOnlyAir() || !section.maybeHas(s -> s.getBlock() instanceof OreBlock)) {
                dst[si] = section;
                continue;
            }
            BlockState disguise = chunk.getSectionYFromSectionIndex(si) < 0
                    ? Blocks.DEEPSLATE.defaultBlockState()
                    : Blocks.STONE.defaultBlockState();

            PalettedContainer<BlockState> states = section.getStates().copy();
            for (int x = 0; x < 16; x++) {
                for (int z = 0; z < 16; z++) {
                    for (int y = 0; y < 16; y++) {
                        if (states.get(x, y, z).getBlock() instanceof OreBlock) {
                            states.getAndSetUnchecked(x, y, z, disguise);
                            anyHidden = true;
                        }
                    }
                }
            }
            dst[si] = new LevelChunkSection(states, section.getBiomes());
        }

        Map<BlockPos, BlockEntity> blockEntities = chunk.getBlockEntities();
        if (anyHidden) {
            Map<BlockPos, BlockEntity> filtered = new HashMap<>(blockEntities);
            filtered.values().removeIf(be -> {
                BlockState st = be.getBlockState();
                return st != null && st.getBlock() instanceof OreBlock;
            });
            blockEntities = filtered;
        }
        return new Result(chunk.getPos(), dst, blockEntities);
    }
}
