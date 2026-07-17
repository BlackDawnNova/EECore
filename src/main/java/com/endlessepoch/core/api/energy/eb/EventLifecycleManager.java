package com.endlessepoch.core.api.energy.eb;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages Flow lifecycle: dispose on machine removal, flush on chunk unload.
 * 生命周期管理：机器移除时 dispose，区块卸载时 flush。
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

    /** Flush every active flow. Called from the machine's own serverTick — chunk
     *  granularity is moot because each machine owns its Flow and ticks it locally.
     *  排空全部活跃流。由各机器的 serverTick 自发调用——区块粒度无意义：
     *  每台机器都有自己的 Flow 且本地 tick。 */
    public static void flushAll(long gameTick) {
        for (Flow f : REGISTRY.values())
            f.flush(gameTick);
    }

    public static int activeCount() { return REGISTRY.size(); }
}
