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
        int maxOverclock,      // Config.p3MaxOverclock snapshot / 超频上限快照
        boolean energyEnabled, // Config.p3EnergyEnabled snapshot / 能耗开关快照
        List<InputUnit> units  // immutable input snapshot / 不可变输入快照
) {}
