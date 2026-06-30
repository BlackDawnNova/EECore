package com.endlessepoch.core.nova.network.node;

import com.endlessepoch.core.api.field.INovaNode;
import com.endlessepoch.core.api.registry.INovaNetRegistry;
import net.neoforged.neoforge.common.NeoForge;

/**
 * Push-mode node registration helper.
 * <p>
 * Call {@link #register} when a node block entity loads.
 * Call {@link #unregister} when the block entity is removed (any reason including chunk unload).
 * This avoids per-tick scanning — nodes are in the registry as long as they exist.
 * <p>
 * 推送模式的节点注册辅助类。
 * <p>
 * 在节点方块实体加载时调用 {@link #register}。
 * 在方块实体被移除（包括区块卸载）时调用 {@link #unregister}。
 * 这避免了每 tick 扫描 —— 节点在存在期间一直注册在注册表中。
 */
public final class NovaNodeRegistration {

    private static INovaNetRegistry registry;

    private NovaNodeRegistration() {}

    /** Must be called during mod init to set the active registry. / 必须在模组初始化期间调用以设置活动注册表。 */
    public static void init(INovaNetRegistry registry) {
        NovaNodeRegistration.registry = registry;
    }

    /** Register a node. Thread-safe. / 注册一个节点。线程安全。 */
    public static void register(INovaNode node) {
        if (registry != null) {
            registry.registerNode(node);
            NeoForge.EVENT_BUS.post(new NovaNodeEvent(node, NovaNodeEvent.EventType.REGISTERED));
        }
    }

    /** Unregister a node. Thread-safe. Called from setRemoved(). / 注销一个节点。线程安全。从 setRemoved() 调用。 */
    public static void unregister(INovaNode node) {
        if (registry != null) {
            registry.unregisterNode(node);
            NeoForge.EVENT_BUS.post(new NovaNodeEvent(node, NovaNodeEvent.EventType.UNREGISTERED));
        }
    }
}
