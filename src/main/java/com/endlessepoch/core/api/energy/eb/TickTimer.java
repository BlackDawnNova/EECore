package com.endlessepoch.core.api.energy.eb;

/** gameTick interval checker — drops stale events after server sleep. / tick间隔检测，服务器休眠后丢弃过期事件。 */
public final class TickTimer {
    private static final int MAX_TICK_GAP = 20; // 1 second at 20tps / 1秒
    private long lastTick;

    public boolean isExpired(long currentTick) {
        if (lastTick == 0) { lastTick = currentTick; return false; }
        long gap = currentTick - lastTick;
        lastTick = currentTick;
        return gap > MAX_TICK_GAP || gap < 0;
    }
}
