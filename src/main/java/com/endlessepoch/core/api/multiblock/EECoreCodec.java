package com.endlessepoch.core.api.multiblock;

import com.endlessepoch.ecsformat.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.io.IOException;
import java.nio.file.Path;
import java.util.*;

/**
 * MC-side codec. Wraps {@link EcsRawCodec}, converts between {@link MultiBlockPattern} and BlockStates.
 * <p>
 * MC 侧编解码器，包装 EcsRawCodec，在 MultiBlockPattern 与 Minecraft 方块之间转换。
 */
public final class EECoreCodec {
    private EECoreCodec() {}

    public static byte[] encode(MultiBlockPattern pattern) throws IOException {
        return EcsRawCodec.encode(toRaw(pattern));
    }

    public static byte[] encode(MultiBlockPattern pattern, boolean compress) throws IOException {
        return EcsRawCodec.encode(toRaw(pattern), compress);
    }

    public static MultiBlockPattern decode(byte[] data) throws IOException {
        return fromRaw(EcsRawCodec.decode(data));
    }

    public static MultiBlockPattern read(Path path) throws IOException {
        return fromRaw(EcsRawCodec.read(path));
    }

    public static void write(Path path, MultiBlockPattern pattern) throws IOException {
        EcsRawCodec.write(path, toRaw(pattern));
    }

    /**
     * Convert a MultiBlockPattern to the intermediate EcsRawData representation.
     * Builds a palette from definitions, flattens layers into a voxel index array.
     * <p>
     * 将 MultiBlockPattern 转换为中间表示 EcsRawData。从定义构建调色板，将层数据展平为体素索引数组。
     */
    static EcsRawData toRaw(MultiBlockPattern pattern) {
        Map<Character, BlockState> defs = pattern.getDefinitions();
        Map<Character, String> paletteMap = new LinkedHashMap<>();
        paletteMap.put(EcsFormat.CHAR_AIR, "minecraft:air");
        paletteMap.put(EcsFormat.CHAR_CONTROLLER, "minecraft:air");
        paletteMap.put(EcsFormat.CHAR_WILDCARD, "minecraft:air");

        Set<Character> used = new HashSet<>();
        for (String s : pattern.getLayerData())
            for (int i = 0; i < s.length(); i++) used.add(s.charAt(i));

        for (var e : defs.entrySet()) {
            char c = e.getKey();
            if (used.contains(c) || !pattern.getTags(c).isEmpty() || pattern.isFrameBased()) {
                String id = BuiltInRegistries.BLOCK.getKey(e.getValue().getBlock()).toString();
                if (c != EcsFormat.CHAR_AIR && c != EcsFormat.CHAR_WILDCARD)
                    paletteMap.put(c, id);
            }
        }

        List<EcsPaletteEntry> palette = new ArrayList<>();
        for (var e : paletteMap.entrySet())
            palette.add(new EcsPaletteEntry(e.getKey(), e.getValue(), pattern.getTags(e.getKey())));

        int total = pattern.width * pattern.height * pattern.depth;
        short[] voxels = new short[total];
        String[] layers = pattern.getLayerData();
        int idx = 0;
        for (int y = 0; y < pattern.height; y++) {
            String layer = layers[y];
            for (int z = 0; z < pattern.depth; z++)
                for (int x = 0; x < pattern.width; x++) {
                    char c = layer.charAt(z * pattern.width + x);
                    int pi = 0;
                    for (var pe : paletteMap.entrySet()) { if (pe.getKey() == c) break; pi++; }
                    voxels[idx++] = (short) pi;
                }
        }

        return new EcsRawData(pattern.width, pattern.height, pattern.depth,
                pattern.controllerX, pattern.controllerY, pattern.controllerZ, palette, voxels,
                pattern.isFrameBased());
    }

    /**
     * Reconstruct a MultiBlockPattern from EcsRawData.
     * Handles empty voxel data gracefully and restores tag associations.
     * <p>
     * 从 EcsRawData 重建 MultiBlockPattern。能正确处理空体素数据并恢复标签关联。
     */
    public static MultiBlockPattern fromRaw(EcsRawData raw) {
        Map<Character, BlockState> definitions = new LinkedHashMap<>();
        Map<Character, List<String>> charTags = new LinkedHashMap<>();

        for (EcsPaletteEntry e : raw.palette) {
            char c = e.character();
            if (!e.tags().isEmpty()) charTags.put(c, e.tags());
            if (c == EcsFormat.CHAR_AIR || c == EcsFormat.CHAR_WILDCARD) {
                definitions.put(c, Blocks.AIR.defaultBlockState());
                continue;
            }
            Block block = BuiltInRegistries.BLOCK.get(ResourceLocation.parse(e.blockId()));
            definitions.put(c, block != null && block != Blocks.AIR ? block.defaultBlockState() : Blocks.AIR.defaultBlockState());
        }
        definitions.putIfAbsent(EcsFormat.CHAR_AIR, Blocks.AIR.defaultBlockState());
        definitions.putIfAbsent(EcsFormat.CHAR_WILDCARD, Blocks.AIR.defaultBlockState());

        int w = raw.width, h = raw.height, d = raw.depth;
        List<EcsPaletteEntry> pal = raw.palette;
        short[] voxels = raw.voxelData;

        if (voxels.length == 0) {
            String[] emptyLayers = new String[h];
            StringBuilder airRow = new StringBuilder(w * d);
            for (int i = 0; i < w * d; i++) airRow.append(EcsFormat.CHAR_AIR);
            String emptyLayer = airRow.toString();
            for (int y = 0; y < h; y++) emptyLayers[y] = emptyLayer;

            String[][] layers2d = new String[h][d];
            for (int y = 0; y < h; y++)
                for (int z = 0; z < d; z++)
                    layers2d[y][z] = emptyLayer.substring(z * w, (z + 1) * w);

            return new MultiBlockPattern(w, h, d,
                    raw.controllerX, raw.controllerY, raw.controllerZ,
                    layers2d, new LinkedHashMap<>());
        }

        String[] layerData = new String[h];
        int vi = 0;
        for (int y = 0; y < h; y++) {
            StringBuilder sb = new StringBuilder(w * d);
            for (int z = 0; z < d; z++)
                for (int x = 0; x < w; x++) {
                    int pi = voxels[vi++] & 0xFFFF;
                    sb.append(pi < pal.size() ? pal.get(pi).character() : EcsFormat.CHAR_AIR);
                }
            layerData[y] = sb.toString();
        }

        String[][] layers2d = new String[h][d];
        for (int y = 0; y < h; y++)
            for (int z = 0; z < d; z++)
                layers2d[y][z] = layerData[y].substring(z * w, (z + 1) * w);

        MultiBlockPattern pattern = new MultiBlockPattern(w, h, d,
                raw.controllerX, raw.controllerY, raw.controllerZ, layers2d, definitions);
        if (raw.frameBased)
            pattern.setFrameBasedFlag();
        // Auto-add all registered controller blocks as alternatives for 'K' / K位自动匹配任意控制器
        if (definitions.containsKey(EcsFormat.CHAR_CONTROLLER)) {
            for (Block cb : MultiBlockRegistry.getControllerBlocks())
                if (cb != null) pattern.addAlternatives(EcsFormat.CHAR_CONTROLLER, cb.defaultBlockState());
        }
        for (var e : charTags.entrySet()) {
            pattern.setTags(e.getKey(), e.getValue());
            for (String tag : e.getValue()) {
                var blocks = com.endlessepoch.core.api.multiblock.TagDefRegistry.getBlocks(tag);
                if (blocks != null) {
                    for (var block : blocks)
                        pattern.addAlternatives(e.getKey(), block.defaultBlockState());
                }
            }
        }
        return pattern;
    }
}
