package com.endlessepoch.core.api.part;

import net.minecraft.world.item.ItemStack;

/**
 * A multiblock part whose slots can be locked to specific item types.
 * Locked slots restrict insertion to matching items and are the only slots
 * the machine batch pipeline reads from.
 * <p>
 * 槽位可锁定到特定物品类型的多方块部件。
 * 锁定槽仅接受匹配物品插入，且是机器批处理管线仅读取的槽位。
 */
public interface ILockedSlotBus {

    /**
     * The lock item for the given slot, or {@link ItemStack#EMPTY} if unlocked.
     * 指定槽位的锁定物品，未锁定时返回 EMPTY。
     */
    ItemStack getLockItem(int slot);

    /**
     * Whether this slot is locked to a specific item type.
     * 该槽是否已锁定到特定物品类型。
     */
    boolean isSlotLocked(int slot);

    /**
     * Real stored count from the {@code storedAmount[]} long array.
     * 从 storedAmount[] 长整型数组读取的真实存储数量。
     */
    long getStoredAmount(int slot);
}
