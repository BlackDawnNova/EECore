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

## Textures / 贴图

### Machine Textures / 机器贴图

Directory-based, auto-detected. / 目录制，放贴图即用:

```
assets/<modid>/textures/block/machines/<machine_id>/
  overlay_front.png       ← front panel design / 面板图案
  overlay_front_e.png     ← emissive version (optional, can be animated) / 发光版（可选，可动画）
```

No model JSON needed per machine — generated automatically on first `runClient`.
无需为每台机器手写模型 JSON——首次 `runClient` 自动生成。

### Part Textures / 部件贴图

EECore internal parts follow fixed path convention. Addon mods specify custom overlay paths via `PartReg.register()`.
EECore 内部部件用固定路径。附属 Mod 通过 `PartReg.register()` 传自定义贴图路径。

**EECore internal** / EECore 内部:
```
assets/eecore/textures/block/parts/<part_id>/
  overlay_front.png       ← front panel (16×16 texture, 12×12 UV) / 面板
  overlay_front_e.png     ← emissive glow overlay (optional, 12×12 UV area per frame) / 发光叠加层（可选，每帧12×12 UV区域）
```

**Addon mod** / 附属 Mod (custom path):
```java
// Pass any texture path / 传任意贴图路径
"my_mod:block/parts/ev_fluid/overlay_front"
// → textures at assets/my_mod/textures/block/parts/ev_fluid/
```

Emissive auto-detection: `hasEmissiveTexture(overlayTex)` checks for `_e.png` variant. If found → model switches to `ee_base_12_front_emissive` parent + registers `EmissiveHelper`.
发光自动检测：检查 `_e.png` 变体，存在则切发光模型 + 注册 EmissiveHelper。

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

Tags define valid blocks for pattern characters. Limits use `MultiblockLoader.limit()` instead.
标签定义 Pattern 字符的有效方块。上限用 `MultiblockLoader.limit()`。

```java
TagDefRegistry.register("gregtech:input_hatch",
    Set.of(SLV_HATCH, LV_HATCH, HV_HATCH)
);
```

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
所有多方块部件实现 `IPart`，EECore 以 `PartBlock` 为基类。

```java
// IPart interface / 部件接口
Set<PartAbility> getAbilities();       // ITEM_INPUT, ITEM_OUTPUT, FLUID_INPUT, ENERGY_INPUT, etc.
void onFormed(machineId, controllerPos);
void onBroken();
BlockPos getControllerPos();
ResourceLocation getMachineId();
boolean isFormed();
```

#### PartType & PartAbility / 部件类型与能力

9 built-in PartTypes. Each has exactly ONE functional role. / 9 种内置部件类型，每种独立单一功能：

| PartType | Abilities / 能力 | BE Storage / 存储 |
|----------|-----------------|-------------------|
| `input_bus` | ITEM_INPUT | ItemStackHandler (1-81 slots) / 1-81格 |
| `output_bus` | ITEM_OUTPUT | ItemStackHandler (1-81 slots) / 1-81格 |
| `fluid_input` | FLUID_INPUT | FluidTank (configurable mB) / 可配mB |
| `fluid_output` | FLUID_OUTPUT | FluidTank (configurable mB) / 可配mB |
| `energy_input` | ENERGY_INPUT | OmegaStorage (configurable Ω) / 可配Ω |
| `energy_output` | ENERGY_OUTPUT | OmegaStorage (configurable Ω) / 可配Ω |
| `input_assembly` | ITEM_INPUT + FLUID_INPUT | ItemStackHandler + FluidTank |
| `output_assembly` | ITEM_OUTPUT + FLUID_OUTPUT | ItemStackHandler + FluidTank |
| `casing` | STRUCTURAL | — |

Addon mods register custom types / 附属 mod 注册自定义类型:
```java
PartType.register(ResourceLocation.fromNamespaceAndPath("mymod", "custom"), "mymod.part.custom");
PartAbility.register("mymod:my_ability");
```

#### PartBlock / 部件方块

Base block class for all parts. Tier controls appearance only (texture + hardness); functionality params are explicit.
统一方块基类。Tier 仅控制外观（贴图+硬度），功能参数显式可配。

```java
// Structural only (casing) / 纯结构
new PartBlock(Properties, type, tier)

// Item bus (slot count) / 物品总线（格数）
new PartBlock(Properties, type, tier, slotCount)

// Assembly (slots + fluid capacity) / 总成（格子+流体）
new PartBlock(Properties, type, tier, slotCount, fluidCapacity)

// Energy hatch (energy capacity) / 能源仓（容量）
new PartBlock(Properties, type, tier, 0, 0, energyCapacity)

// Static utilities / 静态工具
PartBlock.tieredProperties(int tier)   // h=3+tier*3, blast=6+tier*3
PartBlock.toolTagForTier(int tier)     // needs_stone/iron/diamond/netherite_tool
PartBlock.DEFAULT_BUS_SLOTS            // 2
PartBlock.DEFAULT_ASSEMBLY_SLOTS       // 4
PartBlock.MAX_SLOTS                    // 81
```

#### One-Click Part Registration / 部件一键注册

```java
// EECore internal — one line in Blocks.java / EECore 内部一行
INPUT_BUS = registerPartBlock("input_bus", 1, 2, 0, 0, "Input Bus", "输入总线");
//           path, tier, slots, fluidCap, energyCap, nameEn, nameZh
// Item + model + translation auto-registered via flushPartItems() on startup.
// 物品+模型+翻译由 flushPartItems() 启动时自动注册。

// Addon mod — one line with custom overlay texture / 附属 Mod 一行带贴图
PartReg.register(MY_BLOCKS, MY_ITEMS, myTab,
    "my_mod",              // namespace / 命名空间
    "ev_fluid_input",      // path / 注册名
    4,                     // tier (EHV) / 电压等级
    "my_mod:block/parts/ev_fluid/overlay_front",  // overlay texture / 覆面贴图
    "EV Fluid Input Hatch", // nameEn
    "超高压流体输入仓");      // nameZh
```

Auto-generates: block model, blockstate, item model, tool tag, creative tab entry, lang entry.
自动生成：方块模型、方块状态、物品模型、工具标签、创造栏条目、翻译条目。

If `overlay_front_e.png` exists → auto-emissive model + EmissiveHelper registration. / 若有 `_e` 贴图 → 自动发光 + EmissiveHelper。

#### InputBusBlockEntity / 总线 BE

Bus with `IItemHandler` inventory, right-click opens GUI. Assembly parts also use this BE (inherits FluidTank from PartBlockEntity).
总线+总成共用此 BE（总成同时继承 PartBlockEntity 的 FluidTank）。

```java
bus.getInventory()     // IItemHandler (hopper/pipe compatible / 漏斗管道可交互)
bus.getSlotCount()     // configurable at construction time / 构造时可配
bus.isOutput()         // true for output bus/assembly / 输出总线/总成为 true
```

#### PartBlockEntity / 部件基类 BE

Energy hatches get OmegaStorage. Fluid hatches/assemblies get FluidTank. Capacities read from PartBlock config.
能源仓自动创建 OmegaStorage，流体仓/总成自动创建 FluidTank。容量从 PartBlock 配置读取。

```java
pe.getEnergyStorage()  // OmegaStorage or null / 能量存储或 null
pe.getFluidTank()      // FluidTank or null / 流体罐或 null
```

#### Emissive Overlay / 发光覆面

If `overlay_front_e.png` exists next to `overlay_front.png`, the model auto-switches to emissive parent and the part is auto-registered in EmissiveHelper for GlowBakedModel wrapping.
若 `overlay_front_e.png` 与 `overlay_front.png` 同目录存在，模型自动切至发光父模型，部件自动注册 EmissiveHelper。

```
assets/<modid>/textures/block/parts/<id>/
  overlay_front.png       ← front panel / 面板
  overlay_front_e.png     ← emissive version (optional, can be animated 16×N) / 发光版（可选，可动画 16×N）
```

#### CasingBlock / 外壳方块

Structural part with no facing. / 无朝向结构部件。

```java
new CasingBlock(Properties, PartType.CASING, tier)
```

#### Capability Registration / 能力注册

All capabilities are registered automatically at mod init / 启动时自动注册:
```java
// ItemHandler: INPUT_BUS, OUTPUT_BUS, INPUT_ASSEMBLY, OUTPUT_ASSEMBLY
// OMEGA_ENERGY: ENERGY_INPUT, ENERGY_OUTPUT
// FluidHandler: FLUID_INPUT, FLUID_OUTPUT, INPUT_ASSEMBLY, OUTPUT_ASSEMBLY
```

All blocks have `getDrops()` code override (no loot table JSONs needed). / 所有方块已覆写 `getDrops()`，无需 loot table JSON。

### MachineScreen / 机器界面

Generic base screen. Addon mods extend for custom recipe slots, progress bars, etc. / 通用机器界面基类，附属 mod 继承后自定义配方槽位、进度条等。

```java
public class MyScreen extends MachineScreen<MyMenu> {
    public MyScreen(MyMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
    }
    @Override
    protected void renderBg(GuiGraphics g, float p, int mx, int my) {
        super.renderBg(g, p, mx, my);  // keep base layout / 保留基础布局
        // draw custom slots, bars... / 画自定义槽位、进度条...
    }
}
```

- `imageWidth`/`imageHeight`: custom dimensions / 自定义尺寸
- `BG`: protected, override for custom texture / 覆写贴图
- Title auto-resolves bilingual via menu buffer / 标题通过菜单缓冲区双语解析
- Right side reserved slots (6 positions) / 右侧预留槽位(6个)

### WrenchItem / 扳手

`eecore:wrench` — speed=100 on EECore blocks, speed=9 (netherite) on vanilla. / 创意扳手，EECore 方块 speed=100，原版 speed=9。

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

Registered machine items appear in **EECore Machines** tab. / 注册的机器物品自动出现在 **EECore 机器**。
Part items populate **EECore Blocks** tab dynamically via `Items.PART_ITEMS` list. / 部件物品通过 `Items.PART_ITEMS` 列表动态填充 **EECore 方块**。
Casing blocks in **EECore Blocks**. Tools in **EECore Items**.

Addon mods can add items to any EECore tab / 附属 mod 可向任何 EECore 标签添加物品:
```java
modEventBus.addListener(MyMod::onBuildCreativeTab);
// event.getTab() == EECore.MACHINES_TAB.get() → event.accept(myItem);
```

---

## Custom Fluid Registration / 自定义流体注册

One-click: FluidType + Source + Flowing + Bucket + texture + model + translation + creative tab.
一键注册：流体类型 + 源 + 流 + 桶 + 贴图 + 模型 + 翻译 + 创造栏。

```java
// EECore internal / EECore 内部
Fluids.register("steam", 0xFFE0E0E0, 400, "Steam", "蒸汽", Fluids.UPRIGHT);
//              id     tint        temp  en     zh     style

// Addon mod / 附属 Mod
Fluids.register(MY_FLUID_TYPES, MY_FLUIDS, MY_ITEMS, "mymod",
    "oil", 0xFF1A0A00, 350, "Oil", "原油", Fluids.UPRIGHT);
```

Auto-generated / 自动生成:
- Bucket texture: vanilla water bucket with pixel replacement, 5-level gradient preserved / 桶贴图：原版水桶像素替换，保留5级渐变
- Bucket model JSON / 桶模型
- Translations for FluidType + BucketItem / 翻译
- Creative tab entry via `Fluids.BUCKETS` list / 创造栏通过 BUCKETS 自动收集

**Style / 样式:** `Fluids.UPRIGHT`(1) upright bucket, `Fluids.INVERTED`(2) 180° rotated.

**Client registration / 客户端注册:** `Fluids.registerClient(event)` via `RegisterClientExtensionsEvent`.
See [Emissive API](emissive.md) for client extension setup.

---

## License / 许可

**GNU GPL v3.0**
Addon mods using EECore API must comply with GPL 3.0.
附属 mod 使用 EECore API 需遵守 GPL 3.0。
