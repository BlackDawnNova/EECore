package com.endlessepoch.core.nova.network.laser;

import com.endlessepoch.core.api.field.INovaNode;
import com.endlessepoch.core.api.field.NodeType;
import com.endlessepoch.core.api.tier.VoltageTier;
import net.minecraft.core.BlockPos;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages all active laser connections in the NovaNet.
 * Thread-safe singleton.
 * <p>
 * 管理 NovaNet 中所有活跃的激光连接。
 * 线程安全的单例。
 */
public final class LaserManager {

    private static final Map<UUID, LaserConnection> connections = new ConcurrentHashMap<>();
    private static final Map<UUID, Set<UUID>> transmitterLinks = new ConcurrentHashMap<>();
    private static final Map<UUID, Set<UUID>> receiverLinks = new ConcurrentHashMap<>();

    private LaserManager() {}

    /**
     * Create a laser connection between two nodes.
     * @throws IllegalArgumentException if either node is wrong type.
     * <p>
     * 在两个节点之间创建激光连接。
     * @throws IllegalArgumentException 如果任一节点类型错误。
     */
    public static LaserConnection connect(INovaNode transmitter, INovaNode receiver) {
        if (transmitter.getNodeType() != NodeType.TRANSMITTER ||
            receiver.getNodeType() != NodeType.RECEIVER) {
            throw new IllegalArgumentException("Laser requires TRANSMITTER → RECEIVER");
        }

        LaserConnection conn = new LaserConnection(
                transmitter.getNodeId(), receiver.getNodeId(),
                transmitter.getBlockPos(), receiver.getBlockPos(),
                transmitter.getTier());

        connections.put(conn.getId(), conn);
        transmitterLinks.computeIfAbsent(transmitter.getNodeId(), k -> ConcurrentHashMap.newKeySet())
                .add(conn.getId());
        receiverLinks.computeIfAbsent(receiver.getNodeId(), k -> ConcurrentHashMap.newKeySet())
                .add(conn.getId());

        return conn;
    }

    /** Disconnect a specific laser. / 断开指定的激光连接。 */
    public static void disconnect(UUID connectionId) {
        LaserConnection conn = connections.remove(connectionId);
        if (conn == null) return;

        var txSet = transmitterLinks.get(conn.getTransmitterId());
        if (txSet != null) txSet.remove(connectionId);

        var rxSet = receiverLinks.get(conn.getReceiverId());
        if (rxSet != null) rxSet.remove(connectionId);
    }

    /** Get all connections from a transmitter. / 获取来自某个发射器的所有连接。 */
    public static Set<LaserConnection> getFromTransmitter(UUID transmitterId) {
        Set<UUID> ids = transmitterLinks.get(transmitterId);
        if (ids == null) return Set.of();
        Set<LaserConnection> result = new HashSet<>();
        for (UUID id : ids) {
            LaserConnection conn = connections.get(id);
            if (conn != null) result.add(conn);
        }
        return Collections.unmodifiableSet(result);
    }

    /** All active connections. / 所有活跃的连接。 */
    public static Set<LaserConnection> getAll() {
        return Set.copyOf(connections.values());
    }
}
