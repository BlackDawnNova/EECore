package com.endlessepoch.core.api.energy.eb;

import com.endlessepoch.core.Config;

/** gameTick interval checker — drops stale events after server sleep. / tick间隔检测，服务器休眠后丢弃过期事件。 */
public final class TickTimer {
    private long lastTick;

    /**
     * Advance the clock and report whether the gap since the last call is stale.
     * Mutates internal state — call exactly once per tick.
     * 推进时钟并报告距上次调用的间隔是否过期。有状态副作用——每 tick 恰好调用一次。
     */
    public boolean checkAndAdvance(long currentTick) {
        if (lastTick == 0) { lastTick = currentTick; return false; }
        long gap = currentTick - lastTick;
        lastTick = currentTick;
        return gap > Config.ebStaleTicks || gap < 0;
    }

    /** Re-sync after dormancy so the next check isn't a false stale. / 休眠后对时，防下次检查误判过期。 */
    public void reset(long currentTick) { lastTick = currentTick; }
}
