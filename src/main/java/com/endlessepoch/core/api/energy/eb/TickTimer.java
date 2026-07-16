package com.endlessepoch.core.api.energy.eb;

import com.endlessepoch.core.Config;

/** gameTick interval checker — drops stale events after server sleep. / tick间隔检测，服务器休眠后丢弃过期事件。 */
public final class TickTimer {
    private long lastTick;

    public boolean isExpired(long currentTick) {
        if (lastTick == 0) { lastTick = currentTick; return false; }
        long gap = currentTick - lastTick;
        lastTick = currentTick;
        return gap > Config.ebStaleTicks || gap < 0;
    }

    /** Re-sync after dormancy so the next check isn't a false stale. / 休眠后对时，防下次检查误判过期。 */
    public void reset(long currentTick) { lastTick = currentTick; }
}
