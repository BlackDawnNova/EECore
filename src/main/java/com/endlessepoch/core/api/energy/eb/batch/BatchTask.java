package com.endlessepoch.core.api.energy.eb.batch;

import java.util.List;

/**
 * Batch job submitted to the ForkJoin pool. Carries a config snapshot so shard
 * computation stays pure — no Config/Level access on worker threads.
 * 提交给 ForkJoin 池的批处理任务。携带配置快照，分片计算保持纯函数——
 * 工作线程不触碰 Config/Level。
 */
public record BatchTask(
        long posHash,          // machine position hash / 机器坐标哈希
        int machineTier,       // effective voltage tier ordinal / 有效电压序数
        double heatValue,      // heat at submit time / 提交时热量
        double speedBoostMax,  // Config.heatSpeedBoostMax snapshot / 热机倍率上限快照
        int maxOverclock,      // Config.p3MaxOverclock snapshot (tier cap) / 超频上限快照（用作级数上限）
        boolean energyEnabled, // Config.p3EnergyEnabled snapshot / 能耗开关快照
        int hardwareCap,       // machine parallel-hatch cap / 硬件并行上限
        long totalRate,        // Σ voltage × amperage from energy inputs / 总供电速率
        int circuitValue,      // bus circuit value, 0 = any / 总线电路值，0=不限
        List<InputUnit> units  // immutable input snapshot / 不可变输入快照
) {}
