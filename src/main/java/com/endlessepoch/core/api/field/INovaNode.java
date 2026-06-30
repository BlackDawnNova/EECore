package com.endlessepoch.core.api.field;

import com.endlessepoch.core.api.tier.VoltageTier;
import net.minecraft.core.BlockPos;

import java.util.UUID;

/**
 * Minimal interface for any block entity that participates in the NovaNet energy field.
 * <p>
 * Transmitters, receivers, and future node types all implement this.
 * The registry uses this interface to discover and route energy.
 */
public interface INovaNode {

    /** Unique node identifier (persistent across chunk loads). */
    UUID getNodeId();

    /** World position of this node. */
    BlockPos getBlockPos();

    /** Node type for routing decisions. */
    NodeType getNodeType();

    /** Voltage tier — determines range. */
    VoltageTier getTier();

    /** Range in blocks for energy field coverage (configurable per-tier). */
    int getRange();

    /** Current energy stored in the node's buffer (Ω). */
    long getBufferEnergy();

    /** Maximum buffer capacity (Ω). */
    long getBufferCapacity();

    /** The team that owns this node. */
    UUID getTeamId();
}
