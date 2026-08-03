package com.endlessepoch.core.screen;

import appeng.api.stacks.AEKey;
import com.endlessepoch.core.network.GridIncrementalUpdatePacket;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Client-side grid storage view — fed by incremental packets, never read from
 * the client block entity (its node is null on the client).
 * 客户端网格存储视图——由增量包驱动，客户端 BE 无节点不读取。
 */
final class ClientStorageView {

    private final Map<AEKey, Long> view = new LinkedHashMap<>();

    void apply(GridIncrementalUpdatePacket pkt) {
        if (pkt.fullUpdate()) view.clear();
        for (var e : pkt.entries()) {
            if (e.count() > 0) view.put(e.key(), e.count());
            else view.remove(e.key());
        }
    }

    Map<AEKey, Long> snapshot() { return view; }

    int size() { return view.size(); }
}
