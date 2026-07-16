package com.endlessepoch.core.api.energy.eb.batch;

/**
 * Aggregated shard result: N operations of one recipe for one input item.
 * Computed off-thread, applied op-by-op on the main thread.
 * 分片聚合结果：同一输入物品、同一配方的 N 次加工。后台算出，主线程逐单元写回。
 */
public record ShardResultUnit(
        long recipeIdHash,     // recipe identity / 配方哈希
        long inputItemId,      // which item to consume / 消耗的物品ID
        long ops,              // operation count / 加工次数
        long energyPerOp,      // Ω per operation after overclock / 超频后单次能耗
        long finalDuration,    // ticks per op after overclock × heat / 超频×热机后单次耗时
        long[] outputItemIds,  // outputs per op / 单次产物ID
        long[] outputCounts,   // output counts per op / 单次产物数量
        double maxHeat         // heat ceiling for bulkHeat / 热量天花板
) {

    /** Copy with a different op count. / 换 ops 的副本。 */
    public ShardResultUnit withOps(long newOps) {
        return new ShardResultUnit(recipeIdHash, inputItemId, newOps, energyPerOp,
                finalDuration, outputItemIds, outputCounts, maxHeat);
    }
}
