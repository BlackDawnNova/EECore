package com.endlessepoch.core.api.energy.eb;

import com.endlessepoch.core.Config;
import com.endlessepoch.core.api.energy.eb.batch.ShardResultUnit;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Main-thread object pool — LIFO stack of {@link ArrayDeque} up to {@code p4PoolCapacity}
 * entries. Background threads each hold a private {@link Background} instance backed by
 * {@link ConcurrentLinkedQueue} capped at capacity/4.
 * <p>
 * 主线程对象池——{@link ArrayDeque} LIFO 栈，上限 {@code p4PoolCapacity}。
 * 后台线程各自持独立 {@link Background} 实例，{@link ConcurrentLinkedQueue} 上限 capacity/4。
 */
public final class ObjectPool {

    private ObjectPool() {}

    private static void checkMainThread() { assert net.neoforged.neoforge.server.ServerLifecycleHooks.getCurrentServer() == null || net.neoforged.neoforge.server.ServerLifecycleHooks.getCurrentServer().isSameThread(); }

    private static final Queue<List<EeEvent>> EVENT_LISTS = new ArrayDeque<>();

    @SuppressWarnings("unchecked")
    public static List<EeEvent> acquireEventList() {
        checkMainThread();
        List<?> l = EVENT_LISTS.poll();
        return l != null ? (List<EeEvent>) l : new ArrayList<>();
    }

    public static void releaseEventList(List<EeEvent> list) {
        checkMainThread();
        if (list != null && EVENT_LISTS.size() < poolCap()) { list.clear(); EVENT_LISTS.offer(list); }
    }

    private static final Queue<List<ShardResultUnit>> SRU_LISTS = new ArrayDeque<>();

    @SuppressWarnings("unchecked")
    public static List<ShardResultUnit> acquireSruList() {
        checkMainThread();
        List<?> l = SRU_LISTS.poll();
        return l != null ? (List<ShardResultUnit>) l : new ArrayList<>();
    }

    public static void releaseSruList(List<ShardResultUnit> list) {
        checkMainThread();
        if (list != null && SRU_LISTS.size() < poolCap()) { list.clear(); SRU_LISTS.offer(list); }
    }

    private static final Queue<Map<ShardResultUnit.AggKey, ShardResultUnit>> AGG_MAPS = new ArrayDeque<>();

    @SuppressWarnings("unchecked")
    public static Map<ShardResultUnit.AggKey, ShardResultUnit> acquireAggMap() {
        checkMainThread();
        Map<?, ?> m = AGG_MAPS.poll();
        return m != null ? (Map<ShardResultUnit.AggKey, ShardResultUnit>) m : new LinkedHashMap<>();
    }

    public static void releaseAggMap(Map<ShardResultUnit.AggKey, ShardResultUnit> map) {
        checkMainThread();
        if (map != null && AGG_MAPS.size() < poolCap()) { map.clear(); AGG_MAPS.offer(map); }
    }

    private static int poolCap() { return Config.p4PoolCapacity > 0 ? Config.p4PoolCapacity : 4096; }

    /** One instance per ForkJoin worker, capped at poolCap/4. / 每个 ForkJoin 工作线程一个实例，容量 poolCap/4。 */
    public static final class Background {
        private final ConcurrentLinkedQueue<List<ShardResultUnit>> sruLists = new ConcurrentLinkedQueue<>();
        private final ConcurrentLinkedQueue<Map<ShardResultUnit.AggKey, ShardResultUnit>> aggMaps = new ConcurrentLinkedQueue<>();
        private final int cap;

        public Background() { this((Config.p4PoolCapacity > 0 ? Config.p4PoolCapacity : 4096) / 4); }
        Background(int cap) { this.cap = cap; }

        public List<ShardResultUnit> acquireSruList() {
            List<ShardResultUnit> l = sruLists.poll();
            return l != null ? l : new ArrayList<>();
        }

        public void releaseSruList(List<ShardResultUnit> list) {
            if (list != null && sruLists.size() < cap) { list.clear(); sruLists.offer(list); }
        }

        public Map<ShardResultUnit.AggKey, ShardResultUnit> acquireAggMap() {
            Map<ShardResultUnit.AggKey, ShardResultUnit> m = aggMaps.poll();
            return m != null ? m : new LinkedHashMap<>();
        }

        public void releaseAggMap(Map<ShardResultUnit.AggKey, ShardResultUnit> map) {
            if (map != null && aggMaps.size() < cap) { map.clear(); aggMaps.offer(map); }
        }
    }
}
