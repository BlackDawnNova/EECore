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

	
