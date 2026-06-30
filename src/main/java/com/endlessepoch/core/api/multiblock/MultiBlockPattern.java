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
 */
public final class MultiBlockPattern {

    /** Horizontal size (X) */
    public final int width;
    /** Vertical size (Y, number of layers) */
    public final int height;
    /** Depth size (Z) */
    public final int depth;

    /** Controller offset relative to the pattern origin (bottom-north-west corner) */
    public final int controllerX, controllerY, controllerZ;

    /** Layer data: [layer][row][col] = character */
    private final String[][] layers;
    /** Character → BlockState mapping */
    private final Map<Character, BlockState> definitions;

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
        this.definitions = Map.copyOf(definitions);
    }

    /**
     * Get the expected block at a relative position.
     * @return mapped BlockState, or null if the position is air/any.
     */
    public BlockState getExpectedState(int relX, int relY, int relZ) {
        if (relY < 0 || relY >= height) return null;
        if (relZ < 0 || relZ >= layers[relY].length) return null;
        String row = layers[relY][relZ];
        if (row == null || relX < 0 || relX >= row.length()) return null;
        char c = row.charAt(relX);
        if (c == ' ' || c == '_') return null; // air / any block
        return definitions.get(c);
    }

    /** Raw char at position (for validation logic). */
    public char getChar(int relX, int relY, int relZ) {
        if (relY < 0 || relY >= height || relZ < 0 || relZ >= layers[relY].length) return ' ';
        String row = layers[relY][relZ];
        if (row == null || relX < 0 || relX >= row.length()) return ' ';
        return row.charAt(relX);
    }

    public Map<Character, BlockState> getDefinitions() { return definitions; }
    public String[][] getLayers() { return layers; }
}
