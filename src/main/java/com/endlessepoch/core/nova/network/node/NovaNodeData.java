package com.endlessepoch.core.nova.network.node;

import com.endlessepoch.core.api.field.NodeType;
import com.endlessepoch.core.api.tier.VoltageTier;
import net.minecraft.core.BlockPos;

import java.util.UUID;

/**
 * Immutable data record for a NovaNet node.
 * <p>
 * NovaNet 节点的不可变数据记录。
 */
public record NovaNodeData(
        UUID nodeId,
        BlockPos pos,
        NodeType type,
        VoltageTier tier,
        int range,
        long bufferEnergy,
        long bufferCapacity,
        UUID teamId
) {}
