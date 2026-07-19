package com.endlessepoch.core.api.energy.eb.batch;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pure-helper tests for MachineLoadLimiter — chunk splitting and pending-shard math.
 * MachineLoadLimiter 纯辅助函数测试——分块切分与待处理分片计算。
 */
class MachineLoadLimiterTest {

    private static BatchTask taskOf(int unitCount) {
        var units = new java.util.ArrayList<InputUnit>();
        for (int i = 0; i < unitCount; i++) units.add(new InputUnit(1, 1, 0, 0, i));
        return new BatchTask(42L, 3, 0.0, 1.5, 8, true, 16384, Long.MAX_VALUE, 0, List.copyOf(units));
    }

    @Test
    void chunk_splitsBySize() {
        var chunks = MachineLoadLimiter.chunk(taskOf(1000), 256);
        assertEquals(4, chunks.size()); // 256+256+256+232
        assertEquals(256, chunks.get(0).units().size());
        assertEquals(232, chunks.get(3).units().size());
        // Config snapshot carried into every chunk / 配置快照带入每个分块
        for (var c : chunks) {
            assertEquals(42L, c.posHash());
            assertEquals(3, c.machineTier());
            assertTrue(c.energyEnabled());
        }
        // No unit lost or duplicated / 单元无丢失无重复
        assertEquals(1000, chunks.stream().mapToInt(c -> c.units().size()).sum());
    }

    @Test
    void chunk_smallTaskStaysWhole() {
        var task = taskOf(5);
        var chunks = MachineLoadLimiter.chunk(task, 256);
        assertEquals(1, chunks.size());
        assertSame(task, chunks.get(0));
    }

    @Test
    void chunk_sizeFloorIsOne() {
        assertEquals(3, MachineLoadLimiter.chunk(taskOf(3), 0).size()); // 非法 0 钳位为 1
    }

    @Test
    void pendingShards_sumsQueuedAndInFlight() {
        var t1 = taskOf(100); // 100 → 7 shards
        var t2 = taskOf(1);   // 1 → 1 shard
        assertEquals(7 + 1 + 5, MachineLoadLimiter.pendingShards(List.of(t1, t2), 5));
    }
}
