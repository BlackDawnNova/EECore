package com.endlessepoch.core.nova.block.part;

import java.util.List;

/**
 * Void-sink statistics — what a creative void part has swallowed, for GUI display.
 * Implemented by the creative output bus (items) and creative fluid output hatch (mB).
 * 虚空吞噬统计——创造虚空部件吞了什么，供 GUI 显示。
 * 创造输出总线（物品）与创造流体输出仓（mB）实现。
 */
public interface VoidStats {

    /** Registry id + swallowed count; fluid entries count in mB. / 注册 ID + 吞噬数量；流体条目按 mB 计。 */
    record Entry(int id, long count, boolean fluid) {}

    /** Max distinct types tracked. / 最多追踪的种类数。 */
    int MAX_TRACKED = 16;

    /** Snapshot of tracked entries, insertion order. / 已追踪条目快照，按插入顺序。 */
    List<Entry> voidEntries();

    /** Reset all counters. / 清零全部计数。 */
    void clearVoidStats();
}
