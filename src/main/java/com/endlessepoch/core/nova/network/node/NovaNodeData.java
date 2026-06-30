package com.endlessepoch.core.nova.network.node;

import com.endlessepoch.core.api.field.NodeType;
import com.endlessepoch.core.api.tier.VoltageTier;
import net.minecraft.core.BlockPos;

import java.util.UUID;

/**
 * Immutable data record for a NovaNet node.
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
