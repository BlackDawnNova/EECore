package com.endlessepoch.core.api.energy.eb.batch;

import com.endlessepoch.core.api.recipe.RecipeSnapshot;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.function.LongFunction;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pure-math tests for the batch shard computation — sharding, voltage filtering,
 * deterministic selection, overclock math, aggregation. Recipe lookup is stubbed.
 * 批处理分片纯计算测试——分片数、电压过滤、确定性选择、超频运算、聚合。配方查询用桩。
 */
class BatchExecutorTest {

    private static final long IRON = 10, GOLD = 20, INGOT = 11, NUGGET = 21;

    private static RecipeSnapshot recipe(long idHash, long input, long tier, long duration, long eut) {
        return new RecipeSnapshot(idHash, new long[]{input}, new long[]{INGOT}, new long[]{1},
                eut * duration, duration, 0, eut, tier, 1, 10.0, 0);
    }

    private static InputUnit unit(long itemId, long count) {
        return new InputUnit(itemId, count, 0, 0, 0);
    }

    private static BatchTask task(int machineTier, List<InputUnit> units) {
        return new BatchTask(1L, machineTier, 0.0, 1.5, 8, true, 16384, Long.MAX_VALUE, 0, 1L,
                null, units);
    }

    private static final LongFunction<List<RecipeSnapshot>> LOOKUP = itemId -> {
        // iron: ELV recipe + MV recipe (tier-layered); gold: HV only / 铁: ELV+MV 分层; 金: 仅 HV
        Map<Long, List<RecipeSnapshot>> m = Map.of(
                IRON, List.of(recipe(1, IRON, 0, 200, 32), recipe(2, IRON, 2, 200, 64)),
                GOLD, List.of(recipe(3, GOLD, 3, 300, 128)));
        return m.getOrDefault(itemId, List.of());
    };

    @Test
    void shardCount_fixed16() {
        assertEquals(16, BatchExecutor.SHARD_SIZE);
        assertEquals(7, BatchExecutor.shardCount(100)); // 6×16 + 4
        assertEquals(1, BatchExecutor.shardCount(1));
        assertEquals(0, BatchExecutor.shardCount(0));
        assertEquals(3125, BatchExecutor.shardCount(50000));
    }

    @Test
    void leaf_selectsHighestEligibleTier() {
        // LV machine: only the ELV iron recipe is eligible / LV 机器只有 ELV 配方达标
        var lv = BatchExecutor.computeLeaf(task(1, List.of(unit(IRON, 64))), 0, 1, LOOKUP);
        assertEquals(1, lv.size());
        assertEquals(1, lv.get(0).recipeIdHash());
        assertEquals(64, lv.get(0).ops());

        // MV machine: MV recipe outranks ELV / MV 机器选更高的 MV 配方
        var mv = BatchExecutor.computeLeaf(task(2, List.of(unit(IRON, 64))), 0, 1, LOOKUP);
        assertEquals(2, mv.get(0).recipeIdHash());
    }

    @Test
    void leaf_voltageGateRejects() {
        // LV machine + HV-only gold → no result / LV 机器 + 仅 HV 的金矿 → 无结果
        var out = BatchExecutor.computeLeaf(task(1, List.of(unit(GOLD, 10))), 0, 1, LOOKUP);
        assertTrue(out.isEmpty());
    }

    @Test
    void leaf_overclockMath() {
        // LV machine, ELV recipe → 1 overclock: duration 200→100, energy 6400→12800
        var out = BatchExecutor.computeLeaf(task(1, List.of(unit(IRON, 1))), 0, 1, LOOKUP);
        assertEquals(100, out.get(0).finalDuration());
        assertEquals(12800, out.get(0).energyPerOp());
    }

    @Test
    void leaf_aggregatesSameRecipeAcrossSlots() {
        var units = List.of(unit(IRON, 64), unit(IRON, 64), unit(IRON, 32));
        var out = BatchExecutor.computeLeaf(task(1, units), 0, 3, LOOKUP);
        assertEquals(1, out.size());
        assertEquals(160, out.get(0).ops());
    }

    @Test
    void mergeResults_sumsOpsByKey() {
        var a = List.of(new ShardResultUnit(1, IRON, 10, 0, 100, new long[]{INGOT}, new long[]{1}, 10.0, 100, 100));
        var b = List.of(
                new ShardResultUnit(1, IRON, 5, 0, 100, new long[]{INGOT}, new long[]{1}, 10.0, 100, 100),
                new ShardResultUnit(3, GOLD, 2, 0, 150, new long[]{NUGGET}, new long[]{3}, 10.0, 100, 100));
        var merged = BatchExecutor.mergeResults(a, b);
        assertEquals(2, merged.size());
        assertEquals(15, merged.stream().filter(u -> u.recipeIdHash() == 1).findFirst().orElseThrow().ops());
    }
}
