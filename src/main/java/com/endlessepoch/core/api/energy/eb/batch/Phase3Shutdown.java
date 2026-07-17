package com.endlessepoch.core.api.energy.eb.batch;

import com.endlessepoch.core.EECore;
import com.endlessepoch.core.api.energy.eb.Schedulers;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;

/**
 * Clean Phase-3 teardown on ServerStopping: drop queued/delivered batch work, reset
 * throttle state, then shut both thread pools down with a bounded wait. Pools are
 * recreated lazily on next use, so single-player world switching keeps working.
 * ServerStopping 时的 Phase 3 干净收尾：清空排队/已投递批任务、复位限流状态，
 * 再限时等待关停两个线程池。池在下次使用时懒重建，单人换存档不受影响。
 */
public final class Phase3Shutdown {

    /** Bounded pool-termination wait. / 线程池限时等待。 */
    private static final long AWAIT_MILLIS = 5000;

    private Phase3Shutdown() {}

    public static void onServerStopping(ServerStoppingEvent event) {
        // Cancel first so in-flight completions skip delivery and re-pumping
        // 先取消队列——在途完成回调不再投递、不再续泵
        MachineLoadLimiter.clearAll();
        Schedulers.shutdownAll(AWAIT_MILLIS);
        // Pools terminated — now nothing can deliver; safe to wipe the queues
        // 池已终止，不再有投递方，安全清空投递队列
        SegmentMergeManager.clearAll();
        TpsQuotaManager.GLOBAL.reset();
        BatchExecutor.setConcurrencyScale(1.0);
        Phase3Driver.reset();
        EECore.LOGGER.info("[EB-P3] thread pools shut down cleanly");
    }
}
