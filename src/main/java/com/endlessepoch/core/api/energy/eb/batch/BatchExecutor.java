package com.endlessepoch.core.api.energy.eb.batch;

import com.endlessepoch.core.Config;
import com.endlessepoch.core.api.energy.eb.Schedulers;
import com.endlessepoch.core.api.recipe.RecipeSnapshot;
import com.endlessepoch.core.api.recipe.RecipeSnapshotCache;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.RecursiveTask;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.LongFunction;

/**
 * ForkJoin batch executor — splits a BatchTask into fixed 16-unit shards, each shard
 * matches recipes via RecipeSnapshotCache (thread-safe, read-only) and aggregates ops
 * by pure arithmetic. Global active-shard count is clamped to [1024, 16384].
 * ForkJoin 批处理执行器——BatchTask 按固定 16 单元切分片，分片内经配方快照缓存
 * （线程安全只读）匹配并纯算术聚合。全局活跃分片数钳位 [1024, 16384]。
 */
public final class BatchExecutor {

    /** Permanently fixed shard size — never configurable. / 分片大小永久固定，不可配置。 */
    public static final int SHARD_SIZE = 16;

    private static final AtomicInteger ACTIVE_SHARDS = new AtomicInteger();
    private static volatile double concurrencyScale = 1.0;

    private BatchExecutor() {}

    /**
     * Submit a batch job. Returns false when the global shard budget is saturated —
     * caller retries later. onComplete runs on a ForkJoin thread; failures deliver an
     * empty list and never leak to other machines.
     * 提交批处理任务。全局分片额度耗尽时返回 false，调用方稍后重试。
     * onComplete 在 ForkJoin 线程执行；异常仅影响当前机器，投递空列表。
     */
    public static boolean trySubmit(BatchTask task, Consumer<List<ShardResultUnit>> onComplete) {
        int shards = shardCount(task.units().size());
        if (shards == 0) return false;
        if (ACTIVE_SHARDS.get() + shards > effectiveCap()) return false;
        ACTIVE_SHARDS.addAndGet(shards);
        Schedulers.forkJoin().submit(() -> {
            // Log carries the worker thread name — direct proof compute left the main thread
            // 日志行首自带线程名——计算离开主线程的直接证据
            if (com.endlessepoch.core.Config.ebDebugLog)
                com.endlessepoch.core.EECore.LOGGER.debug(
                        "[EB-DBG] computing {} shard(s), {} units @{}",
                        shards, task.units().size(), task.posHash());
            List<ShardResultUnit> out;
            try {
                out = new ShardTask(task, 0, task.units().size(), RecipeSnapshotCache::get).invoke();
            } catch (Throwable e) {
                System.err.println("[EB-P3] batch compute failed @" + task.posHash() + ": " + e);
                out = List.of();
            } finally {
                ACTIVE_SHARDS.addAndGet(-shards);
            }
            onComplete.accept(out);
        });
        return true;
    }

    /** TPS/CPU guards scale the shard budget (Phase 3 M4). / TPS/CPU 限流缩放分片额度（M4 接入）。 */
    public static void setConcurrencyScale(double scale) {
        concurrencyScale = Math.max(0.0, Math.min(1.0, scale));
    }

    public static int activeShards() { return ACTIVE_SHARDS.get(); }

    /** ceil(units / 16) / 分片数 */
    static int shardCount(int units) {
        return (units + SHARD_SIZE - 1) / SHARD_SIZE;
    }

    /** Clamp [1024, 16384] then apply scale, min 1 shard. / 钳位后按限流缩放，至少 1 分片。 */
    static int effectiveCap() {
        int cap = Math.max(1024, Math.min(16384, Config.p3GlobalMaxShards));
        return Math.max(1, (int) (cap * concurrencyScale));
    }

    /** Aggregation key: recipe × input item. / 聚合键：配方×输入物品。 */
    private record AggKey(long recipeIdHash, long inputItemId) {}

    /**
     * Pure shard computation — deterministic selection (voltage-eligible, highest
     * requiredTier wins) + overclock/heat math, aggregated per recipe×item.
     * Lookup is injected for unit tests.
     * 纯分片计算——确定性选择（电压达标取最高 requiredTier）+ 超频/热机运算，
     * 按 配方×物品 聚合。lookup 注入便于单测。
     */
    static List<ShardResultUnit> computeLeaf(BatchTask t, int from, int to,
                                             LongFunction<List<RecipeSnapshot>> lookup) {
        Map<AggKey, ShardResultUnit> agg = new LinkedHashMap<>();
        for (int i = from; i < to; i++) {
            InputUnit u = t.units().get(i);
            List<RecipeSnapshot> candidates = lookup.apply(u.itemId());
            if (candidates == null || candidates.isEmpty()) continue;
            RecipeSnapshot best = null;
            for (RecipeSnapshot s : candidates) {
                if (!OverclockUtil.canProcess(t.machineTier(), s.requiredTierIndex())) continue;
                if (best == null || s.requiredTierIndex() > best.requiredTierIndex()) best = s;
            }
            if (best == null) continue;
            int oc = OverclockUtil.overclockCount(t.machineTier(), best.requiredTierIndex(), t.maxOverclock());
            long energyPerOp = t.energyEnabled()
                    ? OverclockUtil.computeEnergyPerUnit(best.energyPerTick(), best.durationTicks(), oc)
                    : 0L;
            long duration = OverclockUtil.finalDuration(best.durationTicks(), oc,
                    t.heatValue(), best.maxHeat(), t.speedBoostMax());
            var unit = new ShardResultUnit(best.recipeIdHash(), u.itemId(), u.count(),
                    energyPerOp, duration, best.outputItemIds(), best.outputCounts(), best.maxHeat());
            agg.merge(new AggKey(best.recipeIdHash(), u.itemId()), unit,
                    (a, b) -> a.withOps(a.ops() + b.ops()));
        }
        return new ArrayList<>(agg.values());
    }

    /** Merge two aggregated lists by recipe×item key. / 按聚合键合并两个结果列表。 */
    static List<ShardResultUnit> mergeResults(List<ShardResultUnit> a, List<ShardResultUnit> b) {
        Map<AggKey, ShardResultUnit> agg = new LinkedHashMap<>();
        for (var u : a) agg.merge(new AggKey(u.recipeIdHash(), u.inputItemId()), u,
                (x, y) -> x.withOps(x.ops() + y.ops()));
        for (var u : b) agg.merge(new AggKey(u.recipeIdHash(), u.inputItemId()), u,
                (x, y) -> x.withOps(x.ops() + y.ops()));
        return new ArrayList<>(agg.values());
    }

    private static final class ShardTask extends RecursiveTask<List<ShardResultUnit>> {
        private final BatchTask task;
        private final int from, to;
        private final transient LongFunction<List<RecipeSnapshot>> lookup;

        ShardTask(BatchTask task, int from, int to, LongFunction<List<RecipeSnapshot>> lookup) {
            this.task = task; this.from = from; this.to = to; this.lookup = lookup;
        }

        @Override
        protected List<ShardResultUnit> compute() {
            if (to - from <= SHARD_SIZE) {
                return computeLeaf(task, from, to, lookup);
            }
            int mid = (from + to) >>> 1;
            var left = new ShardTask(task, from, mid, lookup);
            var right = new ShardTask(task, mid, to, lookup);
            left.fork();
            var rightResult = right.compute();
            var leftResult = left.join();
            return mergeResults(leftResult, rightResult);
        }
    }
}
