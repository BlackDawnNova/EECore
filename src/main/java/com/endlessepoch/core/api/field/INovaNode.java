package com.endlessepoch.core.api.field;

import com.endlessepoch.core.api.energy.OmegaValue;
import com.endlessepoch.core.api.tier.VoltageTier;
import net.minecraft.core.BlockPos;

import java.util.UUID;

/**
 * Minimal interface for any block entity that participates in the NovaNet energy field.
 * <p>
 * Transmitters, receivers, and future node types all implement this.
 * The registry uses this interface to discover and route energy.
 * <p>
 * 参与 NovaNet 能量场的任意方块实体的最小接口。
 * <p>
 * 发射器、接收器以及未来的节点类型均实现此接口。
 * 注册表使用此接口来发现和路由能量。
 */
public interface INovaNode {

    /** Unique node identifier (persistent across chunk loads). / 唯一的节点标识符（跨区块加载持久化）。 */
    UUID getNodeId();

    /** World position of this node. / 此节点的世界坐标位置。 */
    BlockPos getBlockPos();

    /** Node type for routing decisions. / 用于路由决策的节点类型。 */
    NodeType getNodeType();

    /** Voltage tier — determines range. / 电压等级——决定覆盖范围。 */
    VoltageTier getTier();

    /** Range in blocks for energy field coverage (configurable per-tier). / 能量场覆盖范围的方块数（可按等级配置）。 */
    int getRange();

    /** Current energy stored in the node's buffer (Ω, BigInteger-backed — QV-safe). / 节点缓冲区中当前存储的能量（Ω，大数无溢出）。 */
    OmegaValue getBufferEnergy();

    /** Maximum buffer capacity (Ω, BigInteger-backed — QV-safe). / 最大缓冲区容量（Ω，大数无溢出）。 */
    OmegaValue getBufferCapacity();

    /** The team that owns this node. / 拥有此节点的队伍。 */
    UUID getTeamId();
}
