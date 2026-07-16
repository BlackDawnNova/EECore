package com.endlessepoch.core.api.energy.eb;

import java.lang.management.ManagementFactory;

/** CPU usage sampler (every ~20 ticks). / CPU 占用率采样器。 */
public final class CpuMonitor {

    private static final com.sun.management.OperatingSystemMXBean BEAN =
            (com.sun.management.OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean();

    private static volatile double lastUsage;
    private static int tickCounter;

    private CpuMonitor() {}

    /** Call each server tick. / 每 tick 调用。 */
    public static void tick() {
        if (++tickCounter % 20 == 0)
            lastUsage = BEAN.getCpuLoad();
    }

    /** 0.0–1.0, or -1 if not sampled yet. / 0.0-1.0，未采样返回-1。 */
    public static double usage() {
        double u = lastUsage;
        return u < 0 ? -1.0 : u;
    }
}
