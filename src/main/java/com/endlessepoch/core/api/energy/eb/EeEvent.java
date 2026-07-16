package com.endlessepoch.core.api.energy.eb;

/**
 * Immutable event record for the EB event pipeline. All fields long — no int overflow.
 * 不可变事件 Record，全 long 字段防溢出。
 */
public record EeEvent(
        EventType type,
        long nanoTime,
        long gameTick,
        long posHash,
        long itemId,
        long count,
        long nbtHash,
        long voltageValue,
        double heatValue
) {
    public enum EventType { ITEM_IN, VOLTAGE_CHANGE, HEAT_UPDATE }
}
