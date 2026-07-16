package com.endlessepoch.core.api.energy.eb.batch;

import java.util.List;

/**
 * A merged batch of shard results delivered to one machine's main-thread queue.
 * 投递给单台机器主线程队列的合并分段。
 */
public record MergedSegment(
        long posHash,
        List<ShardResultUnit> results
) {}
