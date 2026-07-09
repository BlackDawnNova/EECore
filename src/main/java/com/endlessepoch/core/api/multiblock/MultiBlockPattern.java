package com.endlessepoch.core.api.multiblock;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import java.util.*;

/** Multiblock pattern: char-coded layers + definitions + tags. / 多方块结构模式 */
public final class MultiBlockPattern {
    public final int width, height, depth;
    public final int controllerX, controllerY, controllerZ;

    private final String[] layerData; // [y] = packed String (all Z rows)
    private final Map<Character, BlockState> definitions;
    private final Map<Character, Set<BlockState>> alternatives = new LinkedHashMap<>();
    private final Map<Character, List<String>> tags = new LinkedHashMap<>();
    // Per-pattern per-block limits / 每个 pattern 独立的方块上限
    private final Map<String, Map<Block, Integer>> blockLimits = new LinkedHashMap<>();
    private final java.util.List<BlockPos> nonAirPositions;
    private final java.util.List<BlockPos> nonAirControllers;

    /** @param layers [layer][row] = char string / 层数据 [层][行] = 字符 */
    public MultiBlockPattern(int width, int height, int depth,
                             int controllerX, int controllerY, int controllerZ,
                             String[][] layers, Map<Character, BlockState> definitions,
                             java.util.List<BlockPos> nonAirPositions,
                             java.util.List<BlockPos> nonAirControllers) {
        this.width = width;
        this.height = height;
        this.depth = depth;
        this.controllerX = controllerX;
        this.controllerY = controllerY;
        this.controllerZ = controllerZ;
        this.layerData = new String[height];
        for (int y = 0; y < height; y++) {
            StringBuilder sb = new StringBuilder();
            for (int z = 0; z < depth; z++) sb.append(layers[y][z]);
            this.layerData[y] = sb.toString();
        }
        this.definitions = new LinkedHashMap<>(definitions);
        this.nonAirPositions = List.copyOf(nonAirPositions);
        this.nonAirControllers = List.copyOf(nonAirControllers);
    }

    /** Convenience constructor — auto-computes non-air positions. / 便捷构造，自动计算非空气位置 */
    public MultiBlockPattern(int width, int height, int depth,
                             int controllerX, int controllerY, int controllerZ,
                             String[][] layers, Map<Character, BlockState> definitions) {
        this(width, height, depth, controllerX, controllerY, controllerZ,
             layers, definitions,
             computePositions(layers, width, height, depth),
             computeControllers(layers, width, height, depth));
    }

    private static java.util.List<BlockPos> computePositions(String[][] layers, int w, int h, int d) {
        java.util.List<BlockPos> list = new java.util.ArrayList<>();
        for (int y = 0; y < h; y++)
            for (int z = 0; z < d; z++)
                for (int x = 0; x < w; x++)
                    if (layers[y][z].charAt(x) != 'A')
                        list.add(new BlockPos(x, y, z));
        return list;
    }

    private static java.util.List<BlockPos> computeControllers(String[][] layers, int w, int h, int d) {
        java.util.List<BlockPos> list = new java.util.ArrayList<>();
        for (int y = 0; y < h; y++)
            for (int z = 0; z < d; z++)
                for (int x = 0; x < w; x++)
                    if (layers[y][z].charAt(x) == 'K')
                        list.add(new BlockPos(x, y, z));
        return list;
    }

    public java.util.List<BlockPos> getNonAirPositions() { return nonAirPositions; }
    public java.util.List<BlockPos> getNonAirControllers() { return nonAirControllers; }

    public void setDefinition(char c, BlockState state) { definitions.put(c, state); }

    public void setBlock(int x, int y, int z, BlockState state) {
        if (y < 0 || y >= height || z < 0 || z >= depth || x < 0 || x >= width) return;
        int idx = z * width + x;
        char existing = layerData[y].charAt(idx);
        if (definitions.containsValue(state) && definitions.get(existing) != null
                && definitions.get(existing).getBlock() == state.getBlock()) return;
        char newChar = findOrCreateChar(state);
        char[] chars = layerData[y].toCharArray();
        chars[idx] = newChar;
        layerData[y] = new String(chars);
    }

    private char findOrCreateChar(BlockState state) {
        for (var e : definitions.entrySet())
            if (e.getValue().getBlock() == state.getBlock()) return e.getKey();
        char c = 'A';
        for (char c2 = 'A'; c2 <= 'Z'; c2++) {
            if (c2 == 'K') continue;
            if (!definitions.containsKey(c2)) { c = c2; break; }
        }
        definitions.put(c, state);
        return c;
    }

    public BlockState getExpectedState(int relX, int relY, int relZ) {
        if (relY < 0 || relY >= height || relZ < 0 || relZ >= depth || relX < 0 || relX >= width) return null;
        char c = layerData[relY].charAt(relZ * width + relX);
        if (c == ' ' || c == '_' || c == '#') return null;
        if (c == 'A') return definitions.getOrDefault('A', null);
        return definitions.get(c);
    }

    public char getChar(int relX, int relY, int relZ) {
        if (relY < 0 || relY >= height || relZ < 0 || relZ >= depth || relX < 0 || relX >= width) return ' ';
        return layerData[relY].charAt(relZ * width + relX);
    }

    public void addAlternatives(char c, BlockState... states) {
        alternatives.computeIfAbsent(c, k -> new LinkedHashSet<>()).addAll(List.of(states));
    }

    /** Returns the original block + alternatives for a char. / 返回某字符的原方块和可替换方块 */
    public Set<BlockState> getAlternatives(char c) {
        Set<BlockState> result = new LinkedHashSet<>();
        BlockState own = definitions.get(c);
        if (own != null) result.add(own);
        Set<BlockState> alt = alternatives.get(c);
        if (alt != null) result.addAll(alt);
        return result;
    }

    public List<String> getTags(char c) { return tags.getOrDefault(c, List.of()); }
    public void setTags(char c, List<String> tagList) {
        if (tagList == null || tagList.isEmpty()) {
            tags.remove(c);
        } else {
            tags.put(c, List.copyOf(tagList));
            for (String tag : tagList)
                for (Block b : TagDefRegistry.getBlocks(tag))
                    addAlternatives(c, b.defaultBlockState());
        }
    }

    /** Per-block limit for a tag. -1 = unlimited. / 某方块在标签中的上限。 */
    public void setBlockLimit(String tag, Block block, int max) {
        blockLimits.computeIfAbsent(tag, k -> new LinkedHashMap<>()).put(block, max);
    }
    public int getBlockLimit(String tag, Block block) {
        var m = blockLimits.get(tag);
        return m != null ? m.getOrDefault(block, 0) : 0;
    }

    public Map<Character, BlockState> getDefinitions() { return definitions; }
    public String[] getLayerData() { return layerData; }

    /** Compact chars to safe pool (0x21-0xFE), no unprintable chars. / 压缩字符到安全范围 */
    public void compactify() {
        java.util.Set<Character> used = new java.util.LinkedHashSet<>();
        for (String s : layerData)
            for (int i = 0; i < s.length(); i++) {
                char c = s.charAt(i);
                if (c != 'A' && c != 'K' && c != '#') used.add(c);
            }
        if (used.size() <= 1) return;

        char[] pool = EECoreStructureFormat.SAFE_CHAR_POOL;
        if (used.size() > pool.length)
            throw new IllegalStateException("Too many block types: " + used.size() + " > " + pool.length);

        java.util.List<Character> sorted = new java.util.ArrayList<>(used);
        java.util.Collections.sort(sorted);
        java.util.Map<Character, Character> remap = new java.util.HashMap<>();
        int poolIdx = 0;
        for (char c : sorted) {
            char target = pool[poolIdx++];
            if (c != target) remap.put(c, target);
        }
        if (remap.isEmpty()) return;

        for (int i = 0; i < layerData.length; i++) {
            char[] chars = layerData[i].toCharArray();
            for (int j = 0; j < chars.length; j++) {
                Character n = remap.get(chars[j]);
                if (n != null) chars[j] = n;
            }
            layerData[i] = new String(chars);
        }
        java.util.Map<Character, BlockState> newDefs = new java.util.LinkedHashMap<>();
        for (var e : definitions.entrySet())
            newDefs.put(remap.getOrDefault(e.getKey(), e.getKey()), e.getValue());
        definitions.clear(); definitions.putAll(newDefs);

        java.util.Map<Character, Set<BlockState>> newAlt = new java.util.LinkedHashMap<>();
        for (var e : alternatives.entrySet())
            newAlt.put(remap.getOrDefault(e.getKey(), e.getKey()), e.getValue());
        alternatives.clear(); alternatives.putAll(newAlt);

        java.util.Map<Character, List<String>> newTags = new java.util.LinkedHashMap<>();
        for (var e : tags.entrySet())
            newTags.put(remap.getOrDefault(e.getKey(), e.getKey()), e.getValue());
        tags.clear(); tags.putAll(newTags);
    }

    public byte[] toByteArray() {
        try { return EECoreCodec.encode(this); }
        catch (java.io.IOException e) { throw new RuntimeException("Failed to encode", e); }
    }

    public static MultiBlockPattern fromByteArray(byte[] data) {
        try { return EECoreCodec.decode(data); }
        catch (java.io.IOException e) { throw new RuntimeException("Failed to decode", e); }
    }

    public String[][] getLayers() {
        String[][] layers = new String[height][depth];
        for (int y = 0; y < height; y++)
            for (int z = 0; z < depth; z++)
                layers[y][z] = layerData[y].substring(z * width, (z + 1) * width);
        return layers;
    }
}
