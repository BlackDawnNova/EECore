package com.endlessepoch.core.api.energy.eb.batch;

import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

public final class SegmentMergeManager {

    private static final Map<Long, Queue<SpecResult>> DELIVERY = new ConcurrentHashMap<>();

    private SegmentMergeManager() {}

    public static void deliver(long posHash, long version, List<ShardResultUnit> results) {
        DELIVERY.computeIfAbsent(posHash, k -> new ConcurrentLinkedQueue<>())
                .add(new SpecResult(version, posHash, List.copyOf(results)));
    }

    public static SpecResult poll(long posHash) {
        var q = DELIVERY.get(posHash);
        return q == null ? null : q.poll();
    }

    public static void clear(long posHash) { DELIVERY.remove(posHash); }
    public static void clearAll() { DELIVERY.clear(); }
}
