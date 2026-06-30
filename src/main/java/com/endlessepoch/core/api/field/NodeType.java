package com.endlessepoch.core.api.field;

/**
 * Types of nodes in the NovaNet energy field.
 */
public enum NodeType {
    /** Collects energy from nearby generators. */
    TRANSMITTER,
    /** Distributes energy to nearby consumers. */
    RECEIVER,
    /** Global dimensional hub (Phase 2). */
    HUB,
    /** Cross-dimensional relay (Phase 2). */
    RELAY;
}
