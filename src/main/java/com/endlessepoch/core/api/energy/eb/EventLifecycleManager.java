package com.endlessepoch.core.api.energy.eb;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages Flow lifecycle: dispose on machine removal.
 * 生命周期管理：机器移除时 dispose。
 */
public final class EventLifecycleManager {

    /** BlockPos long hash → active Flow / 坐标hash→活跃流 */
    private static final Map<Long, Flow> REGISTRY = new ConcurrentHashMap<>();

    private EventLifecycleManager() {}

    public static void register(long posHash, Flow flow) {
        REGISTRY.put(posHash, flow);
    }

    public static void unregister(long posHash) {
        var flow = REGISTRY.remove(posHash);
        if (flow != null) flow.dispose();
    }

    public static int activeCount() { return REGISTRY.size(); }
}
