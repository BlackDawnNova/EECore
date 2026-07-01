package com.endlessepoch.core.api.multiblock;

import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.Block;

import java.util.*;

/**
 * Defines a multiblock structure pattern.
 * <p>
 * A pattern is composed of layers (bottom→top), each layer is a 2D char grid.
 * Characters map to {@link BlockState} via a definition map.
 * Other mods can define patterns in code or JSON.
 * <p>
 * 定义多方块结构模式。
 * <p>
 * 模式由层组成（从下到上），每层是一个二维字符网格。
 * 字符通过定义映射对应到 {@link BlockState}。
 * 其他模组可以通过代码或 JSON 定义模式。
 */
public final class MultiBlockPattern {

    /** Horizontal size (X) / 水平尺寸（X） */
    public final int width;
    /** Vertical size (Y, number of layers) / 垂直尺寸（Y，层数） */
    public final int height;
    /** Depth size (Z) / 深度尺寸（Z） */
    public final int depth;

    /** Controller offset relative to the pattern origin (bottom-north-west corner) / 控制器相对于模式原点（西北下角）的偏移量 */
    public final int controllerX, controllerY, controllerZ;

    /** Layer data: [layer][row][col] = character / 层数据：[层][行][列] = 字符 */
    private final String[][] layers;
    /** Character → BlockState mapping / 字符到 BlockState 的映射 */
    private final Map<Character, BlockState> definitions;
    /** Character → alternative valid blocks (siblings) / 字符 → 可替换的同类方块组 */
    private final Map<Character, Set<BlockState>> alternatives = new LinkedHashMap<>();

    public MultiBlockPattern(int width, int height, int depth,
                             int controllerX, int controllerY, int controllerZ,
                             String[][] layers, Map<Character, BlockState> definitions) {
        this.width = width;
        this.height = height;
        this.depth = depth;
        this.controllerX = controllerX;
        this.controllerY = controllerY;
        this.controllerZ = controllerZ;
        this.layers = layers;
        this.definitions = new LinkedHashMap<>(definitions);
    }

    /**
     * Change the block state for a character (affects all blocks using this char).
     * <p>
     * 修改某个字符对应的方块类型（影响所有使用该字符的方块）。
     */
    public void setDefinition(char c, BlockState state) {
        definitions.put(c, state);
    }

    /**
     * Replace a single block at a specific position (creates a new unique char if needed).
     * <p>
     * 替换指定位置的单个方块（必要时创建新字符）。
     */
    public void setBlock(int x, int y, int z, BlockState state) {
        if (y < 0 || y >= height || z < 0 || z >= layers[y].length) return;
        String row = layers[y][z];
        if (row == null || x < 0 || x >= row.length()) return;
        char existing = row.charAt(x);
        if (definitions.containsValue(state) && definitions.get(existing).getBlock() == state.getBlock()) return;
        char newChar = findOrCreateChar(state);
        StringBuilder sb = new StringBuilder(row);
        sb.setCharAt(x, newChar);
        layers[y][z] = sb.toString();
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

    /**
     * Get the expected block at a relative position.
     * <p>
     * 获取在相对位置上的预期方块。
     *
     * @return mapped BlockState, or null if the position is air/any.
     *         映射的 BlockState，如果该位置为空气/任意方块则返回 null。
     */
    public BlockState getExpectedState(int relX, int relY, int relZ) {
        if (relY < 0 || relY >= height) return null;
        if (relZ < 0 || relZ >= layers[relY].length) return null;
        String row = layers[relY][relZ];
        if (row == null || relX < 0 || relX >= row.length()) return null;
        char c = row.charAt(relX);
        if (c == ' ' || c == '_' || c == 'A' || c == '#') return null;
        return definitions.get(c);
    }

    /**
     * Raw char at position (for validation logic).
     * <p>
     * 获取位置上的原始字符（用于验证逻辑）。
     */
    public char getChar(int relX, int relY, int relZ) {
        if (relY < 0 || relY >= height || relZ < 0 || relZ >= layers[relY].length) return ' ';
        String row = layers[relY][relZ];
        if (row == null || relX < 0 || relX >= row.length()) return ' ';
        return row.charAt(relX);
    }

    /**
     * Register sibling blocks for a character — all are valid at positions with this char.
     * <p>
     * 为字符注册可替换的同类方块——该字符所在位置可以使用其中任意一种方块成型。
     */
    public void addAlternatives(char c, BlockState... states) {
        alternatives.computeIfAbsent(c, k -> new LinkedHashSet<>()).addAll(List.of(states));
    }

    /**
     * Get all valid alternatives for a character (including its own definition).
     * <p>
     * 获取字符的所有有效替换方块（包含自身定义）。
     */
    public Set<BlockState> getAlternatives(char c) {
        Set<BlockState> result = new LinkedHashSet<>();
        BlockState own = definitions.get(c);
        if (own != null) result.add(own);
        Set<BlockState> alt = alternatives.get(c);
        if (alt != null) result.addAll(alt);
        return result;
    }

    public Map<Character, BlockState> getDefinitions() { return definitions; }
    public String[][] getLayers() { return layers; }
}
