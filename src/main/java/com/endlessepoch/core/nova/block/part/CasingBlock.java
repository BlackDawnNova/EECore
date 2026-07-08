package com.endlessepoch.core.nova.block.part;

import com.endlessepoch.core.api.multiblock.PartType;

/**
 * Voltage-tier casing block — a structural multiblock part with no facing.
 * 电压等级外壳方块——无朝向的结构部件。
 */
public class CasingBlock extends PartBlock {

    public CasingBlock(Properties properties, PartType type) {
        super(properties, type, false);
    }
}
