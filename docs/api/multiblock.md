# Multiblock System / 多方块系统

EECore's multiblock framework: Define → Auto-register → Build → Form.
EECore 多方块框架：定义 → 自动注册 → 搭建 → 成形。

---

## Core Concepts / 核心概念

- **Pattern**: Character-encoded 3D structure (layered grid + block mapping table) / 字符编码 3D 结构（多层网格 + 方块映射表）
- **Character pool**: A=Air, K=Controller, #=Wildcard. Auto-expands to 65,000 chars in 16-bit mode / A=空气，K=控制器，#=通配符，自动扩展到 65000 字符（16-bit 模式）
- **.ecs format**: EECore private binary format v3, deflate + CRC32 / EECore 私有二进制格式 v3，deflate + CRC32
- **Tag**: Label a character (e.g. `"gregtech:input_hatch"`). Meaning defined by addon mods via `TagDefRegistry` / 给字符打标签名，含义由附属 mod 通过 `TagDefRegistry` 定义
- **Controller**: Block implementing `IMultiBlockController` / 实现 `IMultiBlockController` 的方块
- **Forming**: Shift+Right-click controller → match pattern → validate blocks and tag counts / Shift+右键控制器 → 匹配 Pattern → 验证方块和 tag 数量

---

## Quick Flow / 快速流程

**Built-in machines / 内置机器:**
```
① Write .ecs structure file → data/<ns>/structures/<name>.ecs
② EECoreMachines.java: .ecs("ns", "name").tier(N).name(en, zh).register()
③ clean build runClient → controller item appears in Machines tab
④ Place controller → build structure → Sneak+Right-click → form
```

**Integration pack / 整合包:**
```
① Place .ecs in config/eecore/structures/<ns>/<name>.ecs
② Put sidecar config/eecore/structures/<ns>/<name>.json: {"tier":1,"name_en":"...","name_zh":"..."}
③ Restart → auto-registered → controller item in Machines tab
```

**Addon mods / 附属 Mod:**
```java
MultiblockLoader.load(ResourceLocation.parse("mymod:my_machine"))
    .name("My Machine", "我的机器")
    .tier(1)  // LV casing
    .register(ResourceLocation.parse("mymod:my_machine"));
```

---

## Machine Registration / 机器注册

### MultiblockLoader API

```java
MultiblockLoader.load(ResourceLocation.parse("mymod:structure_name"))
    .name("My Machine", "我的机器")          // bilingual name / 双语名称
    .tier(1)                                  // voltage tier: 0=ELV, 1=LV, 2=MV... / 电压等级
    .center(0, 49, 2)                         // controller→structure center offset / 控制器到结构中心偏移（可选）
    .effect("eecore:celestial")               // visual effect / 视觉特效（可选）
    .where("TagName", Blocks.IRON_BLOCK)       // tag→block binding / 标记→方块绑定
        .or(Blocks.GOLD_BLOCK)                // alternative / 替代
    .limit("TagName", Blocks.IRON_BLOCK, 2)   // per-block max count (per-machine, independent) / 每方块上限（每机器独立）
    .limit("TagName", Blocks.GOLD_BLOCK, 1)   // -1 = unlimited / -1 = 无限
    .itemId("custom_item_id")                 // custom item registry name / 自定义物品注册名（可选）
    .register(ResourceLocation.parse("your_mod:machine_id"));
```

The `.ecs` file lookup order / `.ecs` 查找顺序:
1. `src/main/resources/data/{ns}/structures/{name}.ecs` (mod jar / 模组打包)
2. `config/eecore/structures/{ns}/{name}.ecs` (runtime / 运行时)

### MachineDefinition / 机器定义

Internal container linking .ecs + metadata + block/item/pattern suppliers.
内部容器，绑定 .ecs + 元数据 + 方块/物品/Pattern 供应。

```java
MachineDefinition def = ...
def.getTier();           // voltage tier / 电压等级
def.getEffect();         // IMachineEffect or null
def.getBlock();          // controller block / 控制器方块
def.getItem();           // controller item / 控制器物品
def.getPattern();        // Optional<MultiBlockPattern>
def.getOffX/Y/Z();      // controller→center offset per axis / 中心偏移各轴
```

### MachineRegistry / 机器注册表

```java
MachineRegistry.get(machineId);           // Optional<MachineDefinition>
MachineRegistry.getByItemId("my_machine"); // find by item registry name / 按物品ID查找
MachineRegistry.getAll();                 // all registered definitions / 全部定义
MachineRegistry.autoRegisterAll();        // scan config/ for sidecar .ecs / 自动扫描注册
```

**Sidecar JSON format / 边车 JSON 格式** (`config/eecore/structures/<ns>/<name>.json`):
```json
{"name_en":"My Machine","name_zh":"我的机器","tier":"1","effect":"eecore:celestial"}
```
Note: sidecar JSON only supports basic metadata (name, tier, effect). For tag bindings and per-block limits, use the code API (MultiblockLoader or EECoreMachines). / 边车 JSON 仅支持基础元数据。标签绑定和上限请用代码 API。

### EECoreMachines (built-in) / EECoreMachines（内置）

`MachineDef` is an **internal helper** inside `EECoreMachines.java`. Addon mods should use `MultiblockLoader` directly—it has the same API. / `MachineDef` 是 EECore 内部的便捷封装，附属 mod 直接用 `MultiblockLoader`，API 一致。

```java
// EECore internal / EECore 内部用
public static final MachineDef MY_MACHINE = new MachineDef()
    .ecs("eecore", "my_structure")
    .name("My Machine", "我的机器")
    .tier(2)
    .center(0, 10, 0)
    .effect("eecore:celestial")
    .where("EE-0", Blocks.OBSIDIAN).or(Blocks.CRYING_OBSIDIAN)
    .limit("EE-0", Blocks.OBSIDIAN, 4)
    .limit("EE-0", Blocks.CRYING_OBSIDIAN, 2)
    .out("eecore:my_machine");
MY_MACHINE.register();

// Addon mods use MultiblockLoader directly / 附属 mod 直接调
MultiblockLoader.load(ResourceLocation.parse("mymod:my_machine"))
    .name(...).tier(2).where("EE-0", ...).limit(...)
    .register(ResourceLocation.parse("mymod:my_machine"));
```

---

## Machine Textures / 机器贴图

Directory-based, auto-detected. New machine just needs / 目录制，放贴图即用:

```
assets/<modid>/textures/block/machines/<machine_id>/
  overlay_front.png       ← front panel design / 面板图案
  overlay_front_e.png     ← emissive version (optional) / 发光版（可选）
```

The controller block model automatically composites / 控制器模型自动合成:
- **Body / 身体**: voltage-tier casing texture (`casings/voltage/<tier>/side.png`) / 电压外壳贴图
- **Front panel / 面板**: `machines/<id>/overlay_front.png` (12×12 inset) / 12×12 凹入面板
- **Emissive / 发光**: `machines/<id>/overlay_front_e.png` (fullbright layer) / 全亮发光层

No model JSON needed per machine — generated automatically on first `runClient`.
无需为每台机器手写模型 JSON——首次 `runClient` 自动生成。

---

## Voltage-Tier Casings / 电压外壳

12 tier-colored casing blocks, each with side/top/bottom textures.
12 级彩色外壳方块，每级三张贴图。

```
assets/eecore/textures/block/casings/voltage/<tier>/
  side.png, top.png, bottom.png
```

Block IDs / 方块 ID: `eecore:elv_machine_casing`, `eecore:lv_machine_casing`, ... `eecore:qv_machine_casing`

Use in .ecs files as structural blocks for the multibody.
在 .ecs 中作为多方块结构的外壳方块引用。

---

## Visual Effects / 视觉特效

### IMachineEffect / 机器特效接口

```java
public interface IMachineEffect {
    void render(BlockPos controllerPos, MachineDefinition def, PoseStack pose, float partialTick);
}
```

### MachineEffectRegistry / 特效注册表

```java
MachineEffectRegistry.create(ResourceLocation.parse("eecore:celestial"));
// Built-in: "eecore:celestial" — sun/moon/stars orbiting 45° ring halo / 日月星辰 45° 环特效
```

Addon mods implement `IMachineEffect` and register via builder:
```java
MultiblockLoader.load(...).effect(myEffect).register(...);
```

---

## .ecs File Format / 文件格式

```
[hdr] Magic "EECS" + version(3) + flags(bit0=deflate)
[crc] CRC32 (header+payload)
[payload]
  VarInt: width, height, depth, ctrlX/Y/Z
  VarInt: palette size
    for each:
      Byte/Char: character (1 byte if palSize≤256, 2 bytes if >256)
      VarInt+UTF8: block ID
      VarInt: tag count + for each: VarInt+UTF8 tag name
  Byte: voxelMode (2=8-bit compressed, 3=16-bit compressed)
  VarInt: non-air voxel count
  for each:
    VarInt: linear index
    Byte/Short: palette index (1 byte for mode2, 2 bytes for mode3)
```

Addon mods can use `EcsRawCodec` from the `ecsformat` package (pure JDK, no MC dependency).
附属 mod 可直接用 `ecsformat` 包中的 `EcsRawCodec`（纯 JDK，无 MC 依赖）。

---

## API Reference / API 参考

### MultiBlockPattern

```java
new MultiBlockPattern(w, h, d, cx, cy, cz, layers, definitions);
pattern.addAlternatives('B', ironBlock, steelBlock);
pattern.getAlternatives('B');   // → {original, alternatives}
pattern.getTags('C');           // → ["tag_name"]
pattern.setTags('C', List.of("gregtech:input_hatch"));
```

### EECoreCodec

```java
EECoreCodec.encode(pattern);      // → byte[]
EECoreCodec.decode(data);         // → MultiBlockPattern
EECoreCodec.read(path);           // → MultiBlockPattern
EECoreCodec.write(path, pattern);
```

### MultiBlockRegistry

```java
MultiBlockRegistry.registerMod(id, pattern);
MultiBlockRegistry.registerLocal(playerId, id, pattern);
MultiBlockRegistry.registerControllerBlock(block);
MultiBlockRegistry.bindControllerToPattern(block, machineId);
MultiBlockRegistry.get(playerId, id);
MultiBlockRegistry.getAll(playerId);
```

### TagDefRegistry

Tag-level global limit (shared across all machines using this tag) / tag级全局上限（所有使用此 tag 的机器共享）：

```java
TagDefRegistry.register("gregtech:input_hatch",
    Set.of(SLV_HATCH, LV_HATCH, HV_HATCH),
    4  // global limit / 全局上限
);
```

For per-machine per-block limits, use `MultiblockLoader.limit()` instead — limits are stored per-pattern, independent across machines. / 每机器每方块独立上限用 `MultiblockLoader.limit()`——上限存 pattern，不同机器互不影响。

### IMultiBlockController

```java
UUID getNodeId();
boolean isFormed();
void onMultiblockFormed();
void onMultiblockBroken();
UUID getOwnerUUID();
String getOwnerName();
void stampOwner(UUID owner, String name);
```

### IPart / PartBlock System / 部件系统

Blocks that can be multiblock parts implement `IPart`. EECore provides `PartBlock` as the base.

```java
// IPart interface / 部件接口
Set<PartAbility> getAbilities();       // ITEM_INPUT, ITEM_OUTPUT, FLUID_INPUT, etc.
void onFormed(machineId, controllerPos);
void onBroken();
BlockPos getControllerPos();
ResourceLocation getMachineId();
boolean isFormed();
```

**PartBlock** — base block class for all parts:

```java
// Built-in LV part (default 4 slots) / 内置LV部件（默认4格）
new PartBlock(Properties.of(), PartType.INPUT_BUS)

// Custom tier + custom slot count / 自定义等级+格数
new PartBlock(PartBlock.tieredProperties(5), PartType.INPUT_BUS, 18)
// → UHV casing texture, hardness=18, 18 slots

// Static utilities / 静态工具
PartBlock.tieredProperties(int tier)   // h=3+tier*3, blast=6+tier*3
PartBlock.toolTagForTier(int tier)     // needs_stone/iron/diamond/netherite_tool
PartBlock.DEFAULT_BUS_SLOTS            // 4
PartBlock.MAX_BUS_SLOTS                // 81
```

**InputBusBlockEntity** — bus with `IItemHandler` inventory, right-click opens GUI:

```java
bus.getInventory()     // IItemHandler (hopper/pipe compatible / 漏斗管道可交互)
bus.getSlotCount()     // configurable at construction time / 构造时可配
```

Right-click opens `BusMenu` with auto-expanding `BusScreen` (inset slots, dynamic height, ≤3 rows compact).

**CasingBlock** — structural part with no facing:

```java
new CasingBlock(Properties.of(), PartType.CASING)
// Block hardness auto-scaled by tier in Blocks.java registration
```

**WrenchItem** — creative wrench for EECore blocks (`eecore:wrench`):

```
EECore blocks: speed=100, correctTool=true  → very fast
Vanilla blocks: speed=9, correctTool=true   → netherite-tier
```

All blocks have `getDrops()` code override (no loot table JSONs needed). / 所有方块已覆写 `getDrops()`，无需 loot table JSON。

**IItemHandler Capability** — registered automatically:

```java
// INPUT_BUS, OUTPUT_BUS → bus.getInventory()
// MACHINE_CONTROLLER → mc.getInventory() (9 input + 9 output slots)
```

---

## Visualizer Controls / Visualizer 操作

| Action / 操作 | Effect / 效果 |
|------|------|
| Left-drag / 左键拖拽 | Rotate / 旋转 |
| Scroll / 滚轮 | Zoom / 缩放 |
| Left-click block / 左键点击方块 | Inspect block / 查看方块 |
| G | Reset view / 重置视角 |
| W/S | Layer up/down / 上下切层 |

---

## Commands / 命令

```
/eecore reload                # Reload disk structures / 重载磁盘结构
/eecore debug mbvis           # Open debug structure / 打开调试结构
/eecore export <id> [ecs|json] # Export to config/eecore/scanned/ / 导出
/eecore import <filename>     # Import from config/eecore/structures/ / 导入
/eecore build <id> [layer]    # Auto-build from inventory / 从背包自动建造
```

---

## Performance / 性能优化

- **Back-face culling / 背面剔除**: Structures > 30,000 blocks skip faces facing away from camera
- **Rotation LOD / 旋转抽稀**: Blocks are randomly dropped during drag-rotation for smooth FPS
- **Layer view / 分层视图**: Single layer display disables all culling and LOD

## Skip Blocks / 扫描排除方块

20 blocks skipped during scanning (fire, portals, command blocks, etc.).
扫描时自动跳过 20 种方块（火、传送门、命令块等）。

Addon mods / 附属 mod 注册:
```java
MultiblockScannerItem.skipBlock(MyBlocks.CUSTOM_TRANSIENT);
```

## Fluid Rendering / 流体渲染

Water/lava sources render as translucent colored cubes. Flowing fluids treated as air.
水/岩浆源显示为半透明色块，流动液体视为空气。

Custom fluid colors / 自定义流体颜色:
```java
MultiblockVisualizerScreen.FLUID_COLORS.put(MyFluids.STEAM_SOURCE.get(), new float[]{1, 1, 1, 0.6f});
```

---

## Creative Tabs / 创造标签

Registered machine items appear in **EECore Machines** tab via `BuildCreativeModeTabContentsEvent`.
注册的机器物品自动出现在 **EECore 机器** 创造标签。Casing blocks in **EECore 方块**。Tools in **EECore 物品**。

Addon mods can add items to any EECore tab / 附属 mod 可向任何 EECore 标签添加物品:
```java
modEventBus.addListener(MyMod::onBuildCreativeTab);
// event.getTab() == EECore.MACHINES_TAB.get() → event.accept(myItem);
```

---

## License / 许可

**GNU GPL v3.0**
Addon mods using EECore API must comply with GPL 3.0.
附属 mod 使用 EECore API 需遵守 GPL 3.0。
