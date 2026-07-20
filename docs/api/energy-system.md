# Energy System API / 能量系统 API

Reference documentation for EECore's Ω energy system.

---

## Voltage Tiers / 电压等级

`VoltageTier` defines 12 voltage tiers from ELV (extra-low voltage) to QV (Planck-level).

```java
VoltageTier.ELV      // 超低压/蒸汽级 ~1 Ω
VoltageTier.LV       // 低压 ~81 Ω
VoltageTier.MV       // 中压 ~6.6 kΩ
VoltageTier.HV       // 高压 ~531 kΩ
VoltageTier.EHV      // 超高压 ~43 MΩ
VoltageTier.UHV      // 特高压 ~3.5 GΩ
VoltageTier.PHV      // 行星高压 ~282 GΩ
VoltageTier.XHV      // 极限高压 ~22.9 TΩ
VoltageTier.PLV      // 等离子约束级 ~1.85 PΩ
VoltageTier.SV       // 施温格级 ~150 PΩ
VoltageTier.BV       // 真空衰变级 ~12.2 EΩ
VoltageTier.QV       // 普朗克级 ~1 ZΩ (10²¹ Ω)
```

Useful methods / 常用方法：

```java
tier.getShortName()     // "LV", "MV", etc.
tier.getChineseName()   // "低压", "中压", etc.
tier.getMinVoltage()    // BigInteger: minimum voltage of this tier
tier.getMaxVoltage()    // BigInteger: maximum voltage (exclusive)
tier.next()             // next higher tier (or same if QV)
tier.prev()             // next lower tier (or same if ELV)
tier.canHandle(other)   // check if this tier can handle the given tier/voltage
tier.getHexColor()      // color code for UI display

// Find tier from value / 根据电压值查找等级
VoltageTier.fromVoltage(OmegaValue.of(1000))  // returns MV
VoltageTier.fromShortName("HV")               // returns HV
VoltageTier.fromOrdinal(1)                    // returns LV (maps to casing tier)
```

---

## OmegaValue / 能量值

Immutable BigInteger wrapper. All operations clamp to `10¹⁰⁰⁰` (MAX_LIMIT).

```java
// Construction / 创建
OmegaValue.of(1000)                      // from long
OmegaValue.of(new BigInteger("999999"))  // from BigInteger
OmegaValue.of("1000000000000")           // from String (decimal)
OmegaValue.zero()                        // zero value
OmegaValue.max()                         // MAX_LIMIT value

// Arithmetic / 运算
val.add(other)       // addition / 加法
val.subtract(other)  // subtraction / 减法
val.multiply(5)      // multiply by long / 乘法
val.divide(other)    // division / 除法
val.pow(3)           // exponentiation / 幂运算

// Comparison / 比较
val.compareTo(other)  // -1, 0, or 1
val.isZero()          // true if zero
val.isMax()           // true if at MAX_LIMIT

// Display / 显示
val.toDisplayString()  // auto-format with suffix: "1.50 kΩ", "3.14 GΩ", "2.5×10^100 Ω"
val.toLong()           // @Deprecated: may clamp. Use toBigInteger() for large values
val.toBigInteger()     // safe for all values

// NBT / 序列化
val.saveToNBT(tag, "key");
OmegaValue.loadFromNBT(tag, "key");
```

---

## EnergyPacket / 能量包

Represents one unit of energy transfer: voltage tier + amperage + energy.

A packet contains 3 things:
- **tier**: the voltage tier (determines "voltage rank")
- **amperage** (A): how many amps (big integer)
- **energy** (Ω): how much energy

```java
// Construction / 创建
new EnergyPacket(VoltageTier.MV, 1, OmegaValue.of(128))
new EnergyPacket(VoltageTier.LV, 5, 1000)        // long overload
new EnergyPacket(VoltageTier.HV, OmegaValue.of(5000))  // 1 amp default

// Access / 访问
packet.getTier()          // VoltageTier
packet.getAmperage()      // BigInteger
packet.getEnergy()        // OmegaValue
packet.getVoltage()       // BigInteger: tier's minimum voltage
packet.getPowerPerTick()  // BigInteger: voltage × amperage
packet.isEmpty()          // true if energy is zero

// Voltage step-down / 降压
EnergyPacket stepped = packet.stepDownTo(VoltageTier.LV);
// Energy is reduced by loss factor (default: 0.8 per step)
// Amperage increases to conserve power

// Split & merge / 分割与合并
List<EnergyPacket> parts = packet.split(3);
EnergyPacket merged = EnergyPacket.merge(parts);

// FE conversion / FE 换算
packet.getFEBigInteger()   // BigInteger: energy × 2 (safe)
packet.getFE()             // @Deprecated: may overflow long
```

---

## IOmegaEnergyStorage / 能量存储接口

Interface your block entity must implement. Use Capability to expose it.

| Method / 方法 | Description / 说明 |
|---|---|
| `receivePacket(packet, simulate)` | Receive an energy packet. Returns accepted packet or null. |
| `extractPacket(tier, simulate)` | Extract energy at a specific tier. Returns extracted packet or null. |
| `receiveEnergy(amount, simulate)` | Simple receive by energy amount (auto-selects tier). |
| `extractEnergy(amount, simulate)` | Simple extract by energy amount. |
| `getEnergyStored()` | Total stored energy across all tiers. |
| `getEnergyStored(tier)` | Stored energy at a specific tier. |
| `getCapacity()` | Maximum storage capacity. |
| `getMaxInput()` | Maximum input per tick. |
| `getMaxOutput()` | Maximum output per tick. |
| `getTier()` | Machine's rated voltage tier. |

Default methods / 默认方法：

```java
canInput(tier)       // true if tier ≤ machine tier
canOutput(tier)      // true if machine tier ≥ target tier
hasEnough(amount)    // true if stored ≥ amount
```

### Simulation / 模拟

All receive/extract methods accept `simulate`. When `true`, no state is modified.
Always call `receivePacket(packet, true)` first to check how much would be accepted.

---

## OmegaStorage / 完整实现

The default implementation. In most cases just use this directly.

```java
// Construction / 创建
new OmegaStorage(capacity, maxIO, tier)                  // long version
new OmegaStorage(capacity, maxInput, maxOutput, tier)    // long version
new OmegaStorage(capacityBI, maxInputBI, maxOutputBI, tier)  // BigInteger version
new OmegaStorage(capacityOv, maxInputOv, maxOutputOv, tier)  // OmegaValue version
// Multi-amp — per-packet gate = tier voltage × min(hatch amps, packet amps); amps ∈ {1,2,4,8,16}
// 多安培版——每包限速 = 电压 × min(仓安培, 包安培)；安培仅允许 1/2/4/8/16
new OmegaStorage(capacity, maxInput, maxOutput, tier, amperage)       // long + amps
new OmegaStorage(capacityBI, maxInputBI, maxOutputBI, tier, amperage) // BigInteger + amps
```

Features / 功能：

- **Per-tier tracking**: energy is stored per voltage tier; extractPacket searches higher tiers if the requested tier is empty
- **Auto step-down**: if `canInput()` fails, the packet is automatically stepped down to the machine's tier
- **Events**: `EnergyTransferEvent` fired on every receive/extract
- **Input/output limiting**: respects `maxInput` and `maxOutput` settings
- **NBT persistence**: `saveToNBT()` / `loadFromNBT()`

---

## EnergyTransferEvent / 能量传输事件

Non-cancellable event fired on `NeoForge.EVENT_BUS` every time OmegaStorage receives or extracts energy.

```java
event.getPhase()    // RECEIVE or EXTRACT
event.getStorage()  // the OmegaStorage involved
event.getPacket()   // the original EnergyPacket
event.getAccepted() // how much was actually accepted (OmegaValue)
event.getTier()     // packet's voltage tier
```

---

## EECoreCapabilities.OMEGA_ENERGY / Capability

```java
BlockCapability<IOmegaEnergyStorage, Direction>
ID: eecore:omega_energy
```

Access pattern:

```java
// Server-side only
var cap = level.getCapability(EECoreCapabilities.OMEGA_ENERGY, pos, side);
if (cap instanceof IOmegaEnergyStorage storage) {
    storage.receivePacket(packet, false);
}
```

---

## MachineSpec / 机器规格

```java
// Recommended way to create machine energy storage:
MachineSpec spec = MachineSpec.builder(VoltageTier.MV)
    .capacity(OmegaValue.of(10000))
    .maxIO(OmegaValue.of(128))
    .maxAmperage(4)
    .build();

OmegaStorage storage = spec.createStorage();

// Quick shortcut / 快捷方式:
MachineSpec.simple(VoltageTier.MV, 10000, 128).createStorage();
```

---

## FE Conversion / FE 转换

```
1 Ω = 2 FE
```

```java
// BigInteger versions (safe, recommended):
EnergyUnit.FE.convertToOmega(BigInteger.valueOf(256))      // → 128
EnergyUnit.FE.convertFromOmega(BigInteger.valueOf(128))    // → 256

// Long versions (@Deprecated, may overflow):
EnergyUnit.FE.convertToOmega(256)    // → 128 (may truncate odd values)
EnergyUnit.FE.convertFromOmega(128)  // → 256 (may overflow for large values)

// EnergyBridge utility / 工具类:
EnergyBridge.feToOmega(feBigInt)           // → BigInteger of Ω
EnergyBridge.omegaToFE(omegaValue)         // → BigInteger of FE
EnergyBridge.feToPacket(feBigInt)          // → EnergyPacket
```

---

## Voltage Step-Down Loss / 降压损耗

Configurable via `Config.STEP_LOSS_FACTOR` (default: 0.8).

- 0.8 = 80% retained per step (20% loss)
- 1.0 = no loss
- 0.0 = all energy lost

```java
// Set programmatically:
EnergyPacket.setStepLoss(0.9);
```

Example: UHV (tier 5) → LV (tier 1), 4 steps:
```
After 1 step: × 0.8 = 80%
After 2 steps: × 0.8 = 64%
After 3 steps: × 0.8 = 51.2%
After 4 steps: × 0.8 = 40.96%
Total loss: 59.04%
```

---

## Machine Effective Voltage / 机器有效电压

A formed machine's tier is decided by its **energy input hatches**, not the controller.
成形机器的电压由**能源输入仓**决定，与控制器无关。

```
effective tier = highest hatch tier
               + 1 if ≥2 hatches share that tier (dual-hatch boost, capped at QV)
fallback: controller storage tier when no hatches
有效电压 = 仓最高电压；同级 ≥2 仓增压 +1（封顶 QV）；无仓回退控制器
```

The effective tier drives the overclock: each tier above a recipe's `requiredTier` halves duration and doubles total energy.
有效电压决定超频：每高出配方 `requiredTier` 一级，耗时减半、总能耗翻倍。

---

## Energy-Adaptive Parallel / 能量自适应并行

Batch write-back speed auto-scales to the sustained energy input rate — under-powered machines slow down instead of oscillating start-stop.
批处理写回速度按持续供电率自动缩放——供电不足时降速而非振荡停启。

```
sustained rate  = Σ (hatch tier voltage × amperage)     // saturating math, QV-safe / 饱和运算防 QV 溢出
effective parallel = min(hardware parallel, rate × duration ÷ energyPerOp)
```

The machine GUI shows `Parallel eff/hw` during batching — orange when energy-limited, green at full.
批处理时机器 GUI 显示 `并行 有效/硬件`——供电受限橙色，满额绿色。

---

## Auto-Batch Tiers / 智能批处理双档

Recipe processing auto-dispatches into two tiers — no manual toggle needed:

| Pending items | Computation | Write-back |
|---|---|---|
| < 32 | Main thread (inline) | Batched with parallel |
| ≥ 32 | ForkJoin worker threads | Batched with parallel |

Write-back additionally splits into a per-op light path (≤64 ops/tick budget) and a merged heavy path (>64) internally.
Non-machine recipes (vanilla furnace etc.) keep the classic event-driven path.

配方自动双档分发——无需手动开关：

| 待处理数 | 计算 | 写回 |
|---|---|---|
| < 32 | 主线程内联 | 批量并行写回 |
| ≥ 32 | ForkJoin 多线程 | 批量并行写回 |

写回内部再按 tick 预算分轻路径（≤64 逐 op）与重路径（>64 合并批量）。
非机器配方（原版熔炉等）保留经典事件驱动路径。

---

## Optimal Overclock / 最优超频

When overclock is enabled, the machine auto-selects the highest tier that actually increases throughput given current power input. If the energy supply cannot sustain even one tier of overclock, it falls back to 0 — preventing the counter-intuitive slowdown of "overclock ON but slower."

超频开启时，机器自动取当前供电下吞吐量最大的级数。若供电连一级超频都撑不起，自动退回零级——杜绝"开了超频反而慢"的反直觉行为。

`OverclockUtil.optimalOverclock(hardwareCap, totalRate, baseDuration, baseEnergy, maxOc)`

---

## Heat System / 热量系统

Per-profile heat slots with lazy cooling. Heat builds per recipe completion and decays during idle ticks. Heat provides a pure speed bonus — it does not affect energy cost.

每 profile 独立热量槽，惰性冷却。完成一次配方涨一次热量，闲置时衰减。热量只加速不增能耗。

```
heatFactor = 1.0 + (heat / maxHeat) × (speedBoostMax − 1.0)
```
Heat and overclock stack multiplicatively: `finalDuration = baseDuration ÷ 2^oc ÷ heatFactor`.
热量与超频乘法叠加。

Machine GUI shows `⚡ N.Nx` — combined overclock × heat multiplier, updated when heat actually changes.
机器 GUI 显示 `⚡ N.Nx`——超频×热量综合倍率，仅在热量变化时更新。

---

## Backpressure States / 背压状态

Five debounced states replace raw boolean flags, preventing UI flicker. Each transition requires 5 consecutive ticks (20 for recipe mismatch).

5 状态防抖替代裸 boolean，消除 UI 抖动。每转换需连续 5 tick（配方未匹配 20 tick）。

| State | Trigger |
|-------|---------|
| IDLE | Normal / 正常 |
| OUTPUT_FULL | Output bus full / 输出总线满 |
| VOLTAGE_LOW | Recipe demands higher tier or insufficient power / 配方超电压或供电不足 |
| COLD_START | Recipe matched but heat is zero (one-shot) / 配方匹配但热量为零（仅首次） |
| RECIPE_MISMATCH | Items present but no matching recipe / 有物品无配方 |

---

## Adaptive mainThreadLimit / 自适应主线程限流

Write-back budget self-scales via TCP-style AIMD:

- TPS > 19.8 for 20 consecutive ticks → +64 or +12.5% (slow increase)
- TPS ≤ 19.0 → proportional decrease (0.5× at 16 TPS, 1.0× at 19 TPS), floor 16
- Idle budget > 50% for 100 ticks → ×0.9 (slow decay), never below the configured `mainThreadLimit` base
- No ceiling; equilibrium found by TPS feedback

写回预算按 TCP AIMD 模型自寻平衡：19.8+→慢涨，19−→比例降（地板 16），闲置→慢缩（仅回落到配置基准 `mainThreadLimit`），无上限。

Config: `mainThreadAdaptive` (default true), `mainThreadLimit` as base.

---

## Oversized Output Bus / 巨量输出总线

A 16-slot output bus with unlimited per-slot count — bypasses ItemStack's 64 cap via `storedAmount[]` array. Items accumulate in one slot, tooltip shows formatted count (e.g. `×6.9K`). Extraction capped at 64 per click, read from `storedAmount[]`.

16 槽巨量输出总线，绕过 ItemStack 64 上限，单槽无限制累积。悬浮显示格式化数量。

Register via `oversized_output_bus` PartType prefix; suffix `_output_bus` auto-binds ITEM_OUTPUT.

---

## Stress Test Command / 压测命令

`/eecore stress <ticks>` — monitors a nearby formed machine for N ticks and reports throughput (ops/tick), TPS, shard count, and current dynamic mtLimit.

监控附近成型机器 N tick，报告吞吐量、TPS、分片数、动态限流值。

---

## Phase 4: Event-Driven Scheduling / 事件驱动调度

Batch writeback is triggered when ForkJoin results arrive, not by per-tick polling. `serverTick` drains the delivery queue and calls `tickBatch` only when `batchPending` is non-empty — idle machines consume zero CPU.

批处理写回由 ForkJoin 结果到达触发，不再每 tick 轮询。`serverTick` 排空投递队列，仅在 `batchPending` 非空时调 `tickBatch`——闲置机器零 CPU 开销。

### Plan Version & Speculative Execution / 计划版本与投机执行

Each batch captures a `planVersion` snapshot. ForkJoin segments carry this version via `SpecResult`; the main thread validates per-segment. Mismatched segments are discarded losslessly (inputs only consumed at write-back).

每批拍 `planVersion` 快照。ForkJoin 分段通过 `SpecResult` 携带版本号，主线程逐段校验。版本不匹配的段无损丢弃（物品仅写回时消耗）。

### Flow Rate Tracker / 流速追踪

Sliding-window volatility measurement (`p4FlowWindow` ticks). When speculative execution is enabled, high input volatility automatically reduces speculation to avoid wasted computation.

滑动窗口波动率测量（`p4FlowWindow` tick）。投机开启时，输入剧烈波动自动降级投机等级以避免无效计算。

### Object Pool / 对象池

`ObjectPool` reuses `ArrayList`/`LinkedHashMap` instances to reduce GC pressure. Main-thread pool uses LIFO `ArrayDeque`; background threads hold isolated `ConcurrentLinkedQueue` pools. Capacity: `p4PoolCapacity` (default 4096).

`ObjectPool` 复用 `ArrayList`/`LinkedHashMap` 实例减少 GC。主线程池用 LIFO `ArrayDeque`，后台线程持独立 `ConcurrentLinkedQueue` 池。容量 `p4PoolCapacity`（默认 4096）。

### /eeadmin stats Command / 管理命令

```
/eeadmin stats
```

Requires permission level 4 (OP). Displays: TPS, CPU%, active machines, in-flight shards, mainThread budget remaining, and global batch completion count.

需权限 4 (OP)。显示：TPS、CPU%、活跃机器数、在途分片数、主线程预算余量、全局批完成计数。

### Parallel without Energy / 无能量时的并行

When `p3EnergyEnabled` is `false` (default), parallel hatches and overclocking are no longer capped by energy rate. Set `p3EnergyEnabled = true` to enable energy-aware parallel scaling.

`p3EnergyEnabled` 为 `false`（默认）时，并行仓和超频不再受能量速率限制。设为 `true` 开启能量自适应并行。

### New Config / 新增配置

| Key | Default | Range | Description |
|-----|---------|-------|-------------|
| `p4PoolCapacity` | 4096 | 256–65536 | Object pool size |
| `p4FlowWindow` | 5 | 1–20 | Flow rate sliding window (ticks) |
| `p4SpeculationEnabled` | true | — | Enable speculative execution |

---

## Multi-Type Batch Pipeline / 多类型批量管线

Addon mods register their own `RecipeType` via `RecipeSnapshotCache.register()` to gain full ForkJoin batch acceleration — no longer limited to the main-thread subscriber path.
附属Mod通过 `RecipeSnapshotCache.register()` 注册自己的 `RecipeType` 即可获得完整 ForkJoin 批量加速——不再局限主线程 subscriber 路径。

### AbstractMachineRecipe / 抽象配方基类

All batch-capable recipes must extend `AbstractMachineRecipe`. It carries the fields the EB pipeline needs: `ingredient`, `results`, `processingTime`, `requiredTier`, `energyPerTick`, `maxHeat`, `maxParallel`, `circuit`.
所有可批处理的配方需继承 `AbstractMachineRecipe`。它携带 EB 管线所需的全部字段。

```java
// Addon mod: register custom recipe type / 附属Mod：注册自定义配方类型
RecipeSnapshotCache.register(
    MyModRecipeTypes.MY_TYPE.get(),
    (id, recipe) -> RecipeSnapshot.from((AbstractMachineRecipe) recipe, id)
);

// Check if a machine profile is batch-capable / 检查机器档位是否可批处理
RecipeSnapshotCache.isBatchCapable(machineType.recipeType());
```

Types not registered fall back to the subscriber light path automatically — no code change needed.
未注册的类型自动回退 subscriber 轻路径，无需任何代码改动。
