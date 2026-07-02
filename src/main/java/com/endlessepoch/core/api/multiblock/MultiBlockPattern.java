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

    /**
     * Compact used character range — removes gaps from editing.
     * After calling, used chars are contiguous starting from 'B'+.
     * A=air and K=controller are preserved, #=wildcard untouched.
     * <p>
     * 压缩使用的字符范围，去除编辑产生的间隙。
     * 压缩后已使用的字符从 'B' 开始连续排列。
     * A=空气 K=控制器 #=通配符 不受影响。
     */
    public void compactify() {

        java.util.Set<Character> used = new java.util.LinkedHashSet<>();
        for (String s : layerData) {
            for (int i = 0; i < s.length(); i++) {
                char c = s.charAt(i);
                if (c != 'A' && c != 'K' && c != '#') used.add(c);
            }
        }
        if (used.size() <= 1) return;

        java.util.List<Character> sorted = new java.util.ArrayList<>(used);
        java.util.Collections.sort(sorted);
        java.util.Map<Character, Character> remap = new java.util.HashMap<>();
        char next = 'B';
        for (char c : sorted) {
            if (c != next) remap.put(c, next);
            next++;
            if (next == 'K') next++; // skip controller char / 跳过 K
        }

        if (remap.isEmpty()) return;

        // Remap layer data / 重映射层数据
        for (int i = 0; i < layerData.length; i++) {
            char[] chars = layerData[i].toCharArray();
            for (int j = 0; j < chars.length; j++) {
                Character n = remap.get(chars[j]);
                if (n != null) chars[j] = n;
            }
            layerData[i] = new String(chars);
        }

        // Remap definitions / 重映射定义
        java.util.Map<Character, BlockState> newDefs = new java.util.LinkedHashMap<>();
        for (var e : definitions.entrySet()) {
            Character n = remap.get(e.getKey());
            newDefs.put(n != null ? n : e.getKey(), e.getValue());
        }
        definitions.clear();
        definitions.putAll(newDefs);

        // Remap alternatives / 重映射可替换组
        java.util.Map<Character, Set<BlockState>> newAlt = new java.util.LinkedHashMap<>();
        for (var e : alternatives.entrySet()) {
            Character n = remap.get(e.getKey());
            newAlt.put(n != null ? n : e.getKey(), e.getValue());
        }
        alternatives.clear();
        alternatives.putAll(newAlt);
    }

    // Backward compat: rebuild [layer][row] array from packed data / 兼容旧API
    public String[][] getLayers() {
        String[][] layers = new String[height][depth];
        for (int y = 0; y < height; y++)
            for (int z = 0; z < depth; z++)
                layers[y][z] = layerData[y].substring(z * width, (z + 1) * width);
        return layers;
    }
}
