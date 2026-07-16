package com.endlessepoch.core;

import com.endlessepoch.core.api.energy.EnergyPacket;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * EECore configuration — all tunable parameters across energy, heatMap, scheduling,
 * thread pools, Phase 3 stubs, and debug logging.
 * <p>
 * EECore 全局配置 — 涵盖能量、热图、调度、线程池、Phase 3 预留及调试日志等全部可调参数。
 */
@EventBusSubscriber(modid = EECore.MOD_ID)
public class Config {

    public static final ModConfigSpec SPEC;

    // ── Cached values — set during onLoad, safe to read from any thread / 缓存值 — onLoad 时赋值，任意线程安全读取 ──
    public static volatile boolean heatEnabled = true;
    public static volatile double heatUpRate = 0.1;
    public static volatile double coolDownRate = 0.005;
    public static volatile double heatSwitchDecay = 0.3;
    public static volatile double heatSpeedBoostMax = 1.5;
    public static volatile boolean ebDebugLog;
    public static volatile int ebDebugInterval = 100;

    // ── General / 通用 ──
    public static final ModConfigSpec.DoubleValue STEP_LOSS_FACTOR;

    // ── Heat Map / 热图系统 ──
    public static final ModConfigSpec.BooleanValue HEAT_ENABLED;
    public static final ModConfigSpec.DoubleValue HEAT_UP_RATE;
    public static final ModConfigSpec.DoubleValue HEAT_COOL_DOWN_RATE;
    public static final ModConfigSpec.DoubleValue HEAT_SWITCH_DECAY;
    public static final ModConfigSpec.DoubleValue HEAT_SPEED_BOOST_MAX;

    // ── Scheduling / 调度 ──
    public static final ModConfigSpec.IntValue EB_WINDOW_NANOS;
    public static final ModConfigSpec.IntValue EB_MAX_BATCH;
    public static final ModConfigSpec.IntValue EB_STALE_TICKS;
    public static final ModConfigSpec.IntValue EB_BUFFER_CAPACITY;

    // ── Thread Pool / 线程池 ──
    public static final ModConfigSpec.IntValue EB_BG_THREADS;
    public static final ModConfigSpec.BooleanValue EB_FORK_JOIN;
    public static final ModConfigSpec.IntValue EB_FJ_PARALLELISM;
    public static final ModConfigSpec.IntValue EB_SEGMENT_COUNT;

    // ── Phase 3 stubs / Phase 3 预留 ──
    public static final ModConfigSpec.BooleanValue P3_PARALLEL_BATCHING;
    public static final ModConfigSpec.IntValue P3_BATCH_SIZE;
    public static final ModConfigSpec.BooleanValue P3_PREDICTIVE_HEAT;
    public static final ModConfigSpec.IntValue P3_PREDICT_WINDOW;
    public static final ModConfigSpec.BooleanValue P3_ADAPTIVE_SCHED;
    public static final ModConfigSpec.DoubleValue P3_CPU_TARGET;
    public static final ModConfigSpec.IntValue P3_MAIN_THREAD_LIMIT;
    public static final ModConfigSpec.IntValue P3_FORK_JOIN_RECIPES;

    // ── Debug / 调试 ──
    public static final ModConfigSpec.BooleanValue EB_DEBUG_LOG;
    public static final ModConfigSpec.IntValue EB_DEBUG_INTERVAL;

    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();

        // ── General / 通用 ──
        b.comment("EECore global configuration / EECore 全局配置").push("general");
        STEP_LOSS_FACTOR = b
                .comment(
                        "Energy loss factor per voltage step-down (0.0-1.0). Default 0.8 means 20% loss per step.",
                        "电压降级能量损耗因子 (0.0-1.0)，默认 0.8 表示每级损耗 20%。")
                .defineInRange("stepLossFactor", 0.8, 0.0, 1.0);
        b.pop();

        // ── Heat Map / 热图系统 ──
        b.comment("HeatMap system — recipe heat tracking / 热图系统 — 配方热量追踪").push("heatMap");
        HEAT_ENABLED = b
                .comment("Enable heat tracking for recipes. Disable to skip all heat calculation.",
                        "启用配方热量追踪。关闭则跳过所有热量计算。")
                .define("enabled", true);
        HEAT_UP_RATE = b
                .comment("Heat gained per recipe completion (not per tick). One recipe = one tick of heat gain.",
                        "每完成一次配方获得的热量（非每tick）。")
                .defineInRange("heatUpRate", 0.1, 0.0, 100.0);
        HEAT_COOL_DOWN_RATE = b
                .comment("Heat lost per idle tick when machine has no work.",
                        "机器闲置时每 tick 散失的热量。")
                .defineInRange("coolDownRate", 0.005, 0.0, 100.0);
        HEAT_SWITCH_DECAY = b
                .comment("Fraction of heat retained when switching profiles (0.0-1.0). 0.3 = lose 70%.",
                        "切换配方时保留的热量比例，0.3=损失70%。")
                .defineInRange("switchDecay", 0.3, 0.0, 1.0);
        HEAT_SPEED_BOOST_MAX = b
                .comment("Max speed multiplier from heat (1.0 = no boost, 2.0 = 2x speed at max heat).",
                        "热量最大加速倍率 (1.0=无加速, 2.0=满热量时两倍速)。")
                .defineInRange("speedBoostMax", 1.5, 1.0, 10.0);
        b.pop();

        // ── Scheduling / 调度 ──
        b.comment("Event buffer scheduling / 事件缓冲调度").push("scheduling");
        EB_WINDOW_NANOS = b
                .comment("WindowBuffer flush window in nanoseconds (10ms = 10_000_000).",
                        "窗口缓冲刷新间隔（纳秒）。")
                .defineInRange("windowNanos", 10_000_000, 1_000_000, 100_000_000);
        EB_MAX_BATCH = b
                .comment("Max events per batch dispatched to background threads.",
                        "每批分派到后台线程的最大事件数。")
                .defineInRange("maxBatch", 16384, 64, 131072);
        EB_STALE_TICKS = b
                .comment("Max tick gap before queued events are considered stale and dropped.",
                        "事件积压判定过期的最大 tick 间隔。")
                .defineInRange("staleTicks", 20, 5, 6000);
        EB_BUFFER_CAPACITY = b
                .comment("Per-machine WindowBuffer queue capacity. Overflow drops oldest + WARN.",
                        "每台机器窗口缓冲容量，溢出丢弃最旧事件并 WARN。")
                .defineInRange("bufferCapacity", 16384, 512, 131072);
        b.pop();

        // ── Thread Pool / 线程池 ──
        b.comment("Thread pool configuration / 线程池配置").push("threadPool");
        EB_BG_THREADS = b
                .comment("Background thread count. 0 = auto (CPU-1 server, CPU/2 client). Requires restart.",
                        "后台线程数。0=自动（服务器 CPU-1，客户端 CPU/2）。需重启生效。")
                .defineInRange("bgThreads", 0, 0, 64);
        EB_FORK_JOIN = b
                .comment("Use ForkJoinPool for parallel operations (Phase 3). Requires restart.",
                        "使用 ForkJoinPool 并行操作（Phase 3）。需重启生效。")
                .define("forkJoin", false);
        EB_FJ_PARALLELISM = b
                .comment("ForkJoinPool parallelism. 0 = auto: min(CPU × 16, 16384). Requires restart.",
                        "ForkJoin 并行度。0=自动。需重启生效。")
                .defineInRange("fjParallelism", 0, 0, 16384);
        EB_SEGMENT_COUNT = b
                .comment("SegmentQueueManager shard count. 0 = auto: CPU × 2. Requires restart.",
                        "分段队列分片数。0=自动：CPU×2。需重启生效。")
                .defineInRange("segmentCount", 0, 0, 256);
        b.pop();

        // ── Phase 3 stubs / Phase 3 预留 ──
        b.comment(
                "Phase 3 — advanced execution (stubs, not yet wired).",
                "Phase 3 — 高级执行特性（预留，尚未接入管线）。").push("phase3");
        P3_PARALLEL_BATCHING = b
                .comment("Enable ForkJoin parallel batch processing.",
                        "启用 ForkJoin 并行批处理。")
                .define("parallelBatching", false);
        P3_BATCH_SIZE = b
                .comment("Min batch size to trigger parallel split.",
                        "触发并行拆分的批大小下限。")
                .defineInRange("batchSize", 256, 16, 16384);
        P3_PREDICTIVE_HEAT = b
                .comment("Pre-compute heat for queued recipes.",
                        "预计算排队配方的热量。")
                .define("predictiveHeat", false);
        P3_PREDICT_WINDOW = b
                .comment("Ticks ahead for predictive heating.",
                        "预计算提前量（tick）。")
                .defineInRange("predictWindow", 20, 5, 200);
        P3_ADAPTIVE_SCHED = b
                .comment("Dynamically adjust flush window based on CPU load.",
                        "根据 CPU 负载动态调整刷新窗口。")
                .define("adaptiveSched", false);
        P3_CPU_TARGET = b
                .comment("Target CPU usage for adaptive scheduling (0.0-1.0).",
                        "自适应调度的目标 CPU 占用率。")
                .defineInRange("cpuTarget", 0.8, 0.1, 0.95);
        P3_MAIN_THREAD_LIMIT = b
                .comment("Max recipes per tick on the main thread — hard lock, cannot disable.",
                        "主线程每 tick 最大配方提交数 — 硬锁，不可关。")
                .defineInRange("mainThreadLimit", 256, 16, 4096);
        P3_FORK_JOIN_RECIPES = b
                .comment("Max recipes per ForkJoin task.",
                        "每个 ForkJoin 任务最大配方数。")
                .defineInRange("fjMaxRecipes", 16, 4, 256);
        b.pop();

        // ── Debug / 调试 ──
        b.comment("Debug and logging / 调试与日志").push("debug");
        EB_DEBUG_LOG = b
                .comment("Enable EB framework debug logging. Verbose — disable in production.",
                        "启用 EB 框架调试日志。内容较多，生产环境请关闭。")
                .define("ebDebugLog", false);
        EB_DEBUG_INTERVAL = b
                .comment("Debug log output interval in ticks.",
                        "调试日志输出间隔（tick）。")
                .defineInRange("ebDebugInterval", 100, 20, 1200);
        b.pop();

        SPEC = b.build();
    }

    @SubscribeEvent
    static void onLoad(final ModConfigEvent event) {
        EnergyPacket.setStepLoss(STEP_LOSS_FACTOR.get());

        // Cache hot-path config values to avoid .get() overhead on server tick / 缓存热路径配置值
        heatEnabled = HEAT_ENABLED.get();
        heatUpRate = HEAT_UP_RATE.get();
        coolDownRate = HEAT_COOL_DOWN_RATE.get();
        heatSwitchDecay = HEAT_SWITCH_DECAY.get();
        heatSpeedBoostMax = HEAT_SPEED_BOOST_MAX.get();
        ebDebugLog = EB_DEBUG_LOG.get();
        ebDebugInterval = EB_DEBUG_INTERVAL.get();

        if (ebDebugLog) {
            EECore.LOGGER.info("[EB-Config] Debug logging enabled (interval: {} ticks)", ebDebugInterval);
        }
        if (heatEnabled) {
            EECore.LOGGER.info("[EB-Config] HeatMap enabled — heatUpRate: {}, coolDownRate: {}, switchDecay: {}, speedBoostMax: {}x",
                    heatUpRate, coolDownRate, heatSwitchDecay, heatSpeedBoostMax);
        } else {
            EECore.LOGGER.info("[EB-Config] HeatMap disabled");
        }
        EECore.LOGGER.info("[EB-Config] Scheduling — window: {}ns, maxBatch: {}, staleTicks: {}, bufferCapacity: {}",
                EB_WINDOW_NANOS.get(), EB_MAX_BATCH.get(), EB_STALE_TICKS.get(), EB_BUFFER_CAPACITY.get());
    }
}
