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
 */
public final class NovaNodeRegistration {

    private static INovaNetRegistry registry;

    private NovaNodeRegistration() {}

    /** Must be called during mod init to set the active registry. */
    public static void init(INovaNetRegistry registry) {
        NovaNodeRegistration.registry = registry;
    }

    /** Register a node. Thread-safe. */
    public static void register(INovaNode node) {
        if (registry != null) {
            registry.registerNode(node);
            NeoForge.EVENT_BUS.post(new NovaNodeEvent(node, NovaNodeEvent.EventType.REGISTERED));
        }
    }

    /** Unregister a node. Thread-safe. Called from setRemoved(). */
    public static void unregister(INovaNode node) {
        if (registry != null) {
            registry.unregisterNode(node);
            NeoForge.EVENT_BUS.post(new NovaNodeEvent(node, NovaNodeEvent.EventType.UNREGISTERED));
        }
    }
}
