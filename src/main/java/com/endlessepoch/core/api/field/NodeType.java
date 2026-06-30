package com.endlessepoch.core.api.field;

/**
 * Types of nodes in the NovaNet energy field.
 * <p>
 * NovaNet 能量场中的节点类型。
 */
public enum NodeType {
    /** Collects energy from nearby generators. / 从附近的发电机收集能量。 */
    TRANSMITTER,
    /** Distributes energy to nearby consumers. / 向附近的消费者分配能量。 */
    RECEIVER,
    /** Global dimensional hub (Phase 2). / 全局维度中心（第二阶段）。 */
    HUB,
    /** Cross-dimensional relay (Phase 2). / 跨维度中继器（第二阶段）。 */
    RELAY;
}
