package com.endlessepoch.core.api.multiblock;

import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.Block;

import java.util.*;

/**
 * Defines a multiblock structure pattern.
 * <p>
 * 定义多方块结构模式。字符通过定义映射对应到 BlockState。
 * 其他模组可以通过代码或 JSON 定义模式。
 */
public final class MultiBlockPattern {

    public final int width;
    public final int height;
    public final int depth;
    public final int controllerX, controllerY, controllerZ;

    // One String per layer (height strings), each row is width chars / 每层一个字符串
    private final String[] layerData;
    private final Map<Character, BlockState> definitions;
    private final Map<Character, Set<BlockState>> alternatives = new LinkedHashMap<>();

    /**
     * @param layers [layer][row] = char string
     */
    public MultiBlockPattern(int width, int height, int depth,
                             int controllerX, int controllerY, int controllerZ,
                             String[][] layers, Map<Character, BlockState> definitions) {
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
    }

    public void setDefinition(char c, BlockState state) {
        definitions.put(c, state);
    }

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
        for (var e : definitions.entrySet()) {
            if (e.getValue().getBlock() == state.getBlock()) return e.getKey();
        }
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
        if (c == ' ' || c == '_' || c == 'A' || c == '#') return null;
        return definitions.get(c);
    }

    public char getChar(int relX, int relY, int relZ) {
        if (relY < 0 || relY >= height || relZ < 0 || relZ >= depth || relX < 0 || relX >= width) return ' ';
        return layerData[relY].charAt(relZ * width + relX);
    }

    public void addAlternatives(char c, BlockState... states) {
        alternatives.computeIfAbsent(c, k -> new LinkedHashSet<>()).addAll(List.of(states));
    }

    public Set<BlockState> getAlternatives(char c) {
        Set<BlockState> result = new LinkedHashSet<>();
        BlockState own = definitions.get(c);
        if (own != null) result.add(own);
        Set<BlockState> alt = alternatives.get(c);
        if (alt != null) result.addAll(alt);
        return result;
    }

    public Map<Character, BlockState> getDefinitions() { return definitions; }
    public String[] getLayerData() { return layerData; }

    // Backward compat: rebuild [layer][row] array from packed data / 兼容旧API
    public String[][] getLayers() {
        String[][] layers = new String[height][depth];
        for (int y = 0; y < height; y++)
            for (int z = 0; z < depth; z++)
                layers[y][z] = layerData[y].substring(z * width, (z + 1) * width);
        return layers;
    }
}
