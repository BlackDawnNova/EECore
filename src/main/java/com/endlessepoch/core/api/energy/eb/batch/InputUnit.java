package com.endlessepoch.core.api.energy.eb.batch;

/**
 * One input-bus slot snapshot for batch processing. Immutable, all-long payload.
 * 批处理输入快照的单槽位单元。不可变，全 long 载荷。
 */
public record InputUnit(
        long itemId,   // item registry ID / 物品注册ID
        long count,    // stack count in the slot / 槽内数量
        long nbtHash,  // component patch hash / 组件补丁哈希
        int busIndex,  // index into inputBusPos / 输入总线索引
        int slotIndex  // slot within the bus / 总线内槽位
) {}
