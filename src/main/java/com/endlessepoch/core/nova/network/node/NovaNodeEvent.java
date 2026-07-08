package com.endlessepoch.core.nova.network.node;

import com.endlessepoch.core.api.field.INovaNode;
import net.neoforged.bus.api.Event;

/**
 * Fired on NeoForge EVENT_BUS when a node is registered or unregistered.
 * <p>
 * 节点注册或注销时在 NeoForge EVENT_BUS 上触发的事件。
 */
public class NovaNodeEvent extends Event {

    public enum EventType {
        REGISTERED,
        UNREGISTERED,
        CONNECTED,
        DISCONNECTED
    }

    private final INovaNode node;
    private final EventType type;

    public NovaNodeEvent(INovaNode node, EventType type) {
        this.node = node;
        this.type = type;
    }

    public INovaNode getNode() { return node; }
    public EventType getEventType() { return type; }
}
