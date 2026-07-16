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

    /** Flush all active flows in a chunk region. / 排空某区块区域的全部活跃流。 */
    public static void flushRegion(long gameTick, int chunkX, int chunkZ) {
        // Flush all for now — per-chunk filtering when needed / 暂时全刷新
        for (Flow f : REGISTRY.values())
            f.flush(gameTick);
    }

    public static int activeCount() { return REGISTRY.size(); }
}
