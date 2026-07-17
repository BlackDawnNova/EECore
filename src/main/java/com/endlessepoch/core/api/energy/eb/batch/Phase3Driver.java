package com.endlessepoch.core.api.energy.eb.batch;

import com.endlessepoch.core.Config;
import com.endlessepoch.core.EECore;
import com.endlessepoch.core.api.energy.eb.CpuMonitor;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Global Phase-3 driver, hooked on ServerTickEvent.Post. Each tick: sample CPU,
 * feed the 20-tick TPS window, combine both guards into one concurrency scale for
 * the ForkJoin shard budget, refill the global main-thread write-back budget, and
 * re-pump machines starved by the global budget. Tier changes are logged once.
 * Phase 3 全局驱动点，挂 ServerTickEvent.Post。每 tick：采样 CPU、喂 20-tick TPS 窗口、
 * 两路限流取 min 合成 ForkJoin 分片额度缩放、充值主线程全局写回预算、续泵被全局额度
 * 饿到的机器。档位变化只记一次日志。
 */
public final class Phase3Driver {

    private static int lastTpsTier = 2;
    private static double lastCpuScale = 1.0;

    private Phase3Driver() {}

    public static void onServerTickPost(ServerTickEvent.Post event) {
        CpuMonitor.tick();

        double tpsScale = TpsQuotaManager.GLOBAL.tick(System.nanoTime(),
                event.getServer().tickRateManager().tickrate(),
                Config.p3TpsFullThreshold, Config.p3TpsReducedThreshold);
        double cpuScale = CpuLoadGuard.scale(CpuMonitor.usage(),
                Config.p3CpuWarnThreshold, Config.p3CpuHighThreshold, Config.p3CpuCriticalThreshold);

        double scale = Math.min(tpsScale, cpuScale);
        BatchExecutor.setConcurrencyScale(scale);
        MainThreadRateLimiter.newTick(Config.p3MainThreadLimit);
        MachineLoadLimiter.pumpAll();

        if (Config.ebDebugLog && event.getServer().getTickCount() % Config.ebDebugInterval == 0)
            EECore.LOGGER.debug("[EB-DBG] throttle: scale={}, TPS={}, CPU={}%, shards={}",
                    String.format("%.2f", scale),
                    String.format("%.1f", TpsQuotaManager.GLOBAL.tps(event.getServer().tickRateManager().tickrate())),
                    String.format("%.0f", CpuMonitor.usage() * 100),
                    BatchExecutor.activeShards());

        logTierChanges(tpsScale, cpuScale);
    }

    /** Log throttle transitions once per change. / 限流档位变化各记一条日志。 */
    private static void logTierChanges(double tpsScale, double cpuScale) {
        int tpsTier = TpsQuotaManager.GLOBAL.tier();
        if (tpsTier != lastTpsTier) {
            double tps = TpsQuotaManager.GLOBAL.tps(20.0);
            if (tpsTier == 0) {
                EECore.LOGGER.warn("[EB-P3] TPS {} -> emergency mode, single serial shard only",
                        String.format("%.1f", tps));
            } else {
                EECore.LOGGER.info("[EB-P3] TPS {} -> concurrency scale {}",
                        String.format("%.1f", tps), tpsScale);
            }
            lastTpsTier = tpsTier;
        }
        if (cpuScale != lastCpuScale) {
            if (cpuScale < 1.0) {
                EECore.LOGGER.warn("[EB-P3] CPU {}% -> concurrency scale {}",
                        String.format("%.0f", CpuMonitor.usage() * 100), cpuScale);
            } else {
                EECore.LOGGER.info("[EB-P3] CPU recovered -> concurrency scale restored");
            }
            lastCpuScale = cpuScale;
        }
    }

    /** Reset driver state (server stopping). / 复位驱动状态（服务器关闭）。 */
    static void reset() {
        lastTpsTier = 2;
        lastCpuScale = 1.0;
    }
}
