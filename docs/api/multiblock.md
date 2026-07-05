# 多方块结构系统 / Multiblock System

EECore's multiblock framework: Scan → Preview → Tag → Form.
EECore 的多方块框架：扫描 → 预览 → 标记 → 成形。

---

## 核心概念 / Core Concepts

- **Pattern**: Character-encoded 3D structure (layered grid + block mapping table) / 字符编码 3D 结构（多层网格 + 方块映射表）
- **Character pool**: A=Air, K=Controller, #=Wildcard. Auto-expands to 65,000 chars in 16-bit mode / A=空气，K=控制器，#=通配符，自动扩展到 65000 字符（16-bit 模式）
- **.ecs format**: EECore private binary format v3, deflate + CRC32. 16-bit voxel indices when palette > 256 / EECore 私有二进制格式 v3，deflate + CRC32。palSize>256 时自动切换 16-bit 体素索引
- **Tag**: Label a character (e.g. `"gregtech:input_hatch"`). Meaning defined by addon mods via `TagDefRegistry` / 给字符打标签名（如 `"gregtech:input_hatch"`），具体含义由附属 mod 通过 `TagDefRegistry` 定义
- **Controller**: Block implementing `IMultiBlockController` / 实现 `IMultiBlockController` 的方块
- **Forming**: Shift+Right-click controller → match pattern → validate blocks and tag counts / Shift+右键控制器 → 匹配 Pattern → 验证方块和 tag 数量

---

## 快速流程 / Quick Flow

```
① Place controller + blocks / 放控制器 + 方块
② Mark corners with scanner → Shift+Right-click scan / 扫描仪标记两角 → Shift+右键扫描
③ Right-click air → Visualizer preview / 右键空气 → Visualizer 预览
④ Edit mode → click block → "Tag" → enter tag name / 编辑模式 → 点方块 → "标记" → 输入标签名
⑤ Save as .ecs / 保存为 .ecs（默认格式）
⑥ Shift+Right-click controller → form validation / Shift+右键控制器 → 成形验证
```

---

## .ecs File Format / 文件格式

```
[hdr] Magic "EECS" + version(3) + flags(bit0=deflate) / 魔数+版本+标志位
[crc] CRC32 (header+payload) / CRC32（头+载荷）
[payload]
  VarInt: width, height, depth, ctrlX/Y/Z
  VarInt: palette size / 调色板大小
    for each:
      Byte/Char: character (1 byte if palSize≤256, 2 bytes if >256) / 字符
      VarInt+UTF8: block ID / 方块ID
      VarInt: tag count + for each: VarInt+UTF8 tag name / tag数量+名称
  Byte: voxelMode (2=8-bit compressed, 3=16-bit compressed)
  VarInt: non-air voxel count / 非空气体素数
  for each:
    VarInt: linear index / 线性索引
    Byte/Short: palette index (1 byte for mode2, 2 bytes for mode3) / 调色板索引
```

Addon mods can use `EcsRawCodec` from the `ecsformat` package (pure JDK, no MC dependency).
附属 mod 可直接用 `ecsformat` 包中的 `EcsRawCodec`（纯 JDK，无 MC 依赖）解析。

---

## API

### MultiBlockPattern

```java
new MultiBlockPattern(w, h, d, cx, cy, cz, layers, definitions);

pattern.addAlternatives('B', ironBlock, steelBlock);
pattern.getAlternatives('B');   // → {original, alternatives} / {原方块, 替代方块}
pattern.getTags('C');           // → ["tag_name"]
pattern.setTags('C', List.of("gregtech:input_hatch"));
byte[] data = pattern.toByteArray();
MultiBlockPattern p2 = MultiBlockPattern.fromByteArray(data);
```

### EECoreCodec

```java
EECoreCodec.encode(pattern);      // → byte[]
EECoreCodec.decode(data);         // → MultiBlockPattern
EECoreCodec.read(path);           // → MultiBlockPattern
EECoreCodec.write(path, pattern);
```

### EcsRawCodec (pure JDK, no MC dependency) / 纯 JDK，无 MC 依赖

```java
EcsRawData data = EcsRawCodec.read(path);
data.palette();   // → List<EcsPaletteEntry> (character, blockId, tags per entry)
```

### MultiBlockRegistry

```java
MultiBlockRegistry.registerMod(id, pattern);
MultiBlockRegistry.registerLocal(playerId, id, pattern);
MultiBlockRegistry.registerControllerBlock(block);
MultiBlockRegistry.get(playerId, id);
MultiBlockRegistry.getAll(playerId);
MultiBlockRegistry.removeLocal(playerId, id);
```

### TagDefRegistry

```java
// Addon mods register in commonSetup / 附属 mod 在 commonSetup 中注册
TagDefRegistry.register("gregtech:input_hatch",
    Set.of(SLV_HATCH, LV_HATCH, HV_HATCH),
    4  // global limit / 全局上限
);
// Characters with matching tag auto-expand. Limit checked during validation.
// .ecs 中对应 tag 的字符自动展开，验证时检查上限
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

---

## Visualizer Controls / 操作

| Action / 操作 | Effect / 效果 |
|------|------|
| Left-drag / 左键拖拽 | Rotate / 旋转 |
| Scroll / 滚轮 | Zoom / 缩放 |
| Left-click block / 左键点击方块 | Browse mode: show alternatives; Edit mode: replace/tag |
| G | Reset view / 重置视角 |
| W/S | Previous/next structure (browse) / layer up/down (layer mode) |
| Right All/↻ button / 右侧按钮 | Toggle layer view / 切换分层预览 |
| Alt+` | Toggle edit/browse mode / 切换编辑/浏览模式 |
| Del | Delete (with confirm) / 删除（带确认） |
| Ctrl+Z | Undo / 撤销 |
| Ctrl+C | Copy block name / 复制方块名 |
| Ctrl+V | Paste & replace / 粘贴替换 |

### Edit Mode / 编辑模式

| Button / 按钮 | Function / 功能 |
|------|------|
| Replace / 替换 | Search block & replace at position / 搜索方块替换当前位置 |
| Batch / 批量 | Replace all same-char blocks / 替换所有同字符方块 |
| Single/Batch delete / 单删/批删 | Replace with air / 方块变空气 |
| Tag / 标记 | Assign tag to character (max 1 per char) / 给字符打标签名（每字符最多一个）|

Default save format is `.ecs`. Use `/eecore export <id> json` for readable JSON export.
保存默认为 `.ecs` 格式。调试可用 `/eecore export <id> json` 导出可读 JSON。

---

## Commands / 命令

```text
/eecore reload                # Reload disk structures / 重载磁盘结构
/eecore debug mbvis           # Open debug structure / 打开调试结构
/eecore export <id> [ecs|json] # Export / 导出
/eecore import <filename>     # Import / 导入
```

---

## Addon Mod Registration / 附属 Mod 注册结构

```java
MultiBlockPattern pattern = EECoreCodec.read(ecsFile);
MultiBlockRegistry.registerMod(id, pattern);
TagDefRegistry.register("my_mod:input_hatch", blocks, 4);
MultiBlockRegistry.registerControllerBlock(controller);
```

---

## License / 许可

v0.1.1+: **GNU GPL v3.0**
Addon mods using EECore API must comply with GPL 3.0.
附属 mod 使用 EECore API 需遵守 GPL 3.0。

## MultiblockLoader API (0.1.2+)

Register multiblock machines from .ecs structure files.
从 .ecs 文件注册多方块机器。

```java
MultiblockLoader.load(ResourceLocation.parse("your_mod:structure_name"))
    .where("TagName", Blocks.IRON_BLOCK)
        .or(Blocks.GOLD_BLOCK)
    .register(ResourceLocation.parse("your_mod:machine_id"));
```

The .ecs file should be placed at / .ecs 文件放置路径:
- `src/main/resources/data/{namespace}/structures/{name}.ecs` (mod jar)
- or `config/eecore/structures/{namespace}/{name}.ecs` (runtime)

## Controller Preview / 控制器预览

Right-click air with any controller block to open a 3D preview of the bound structure.
The preview is read-only and reads from disk/network — zero memory footprint on the client.
右键空气打开绑定结构的 3D 预览。预览只读，零内存占用。

## Performance Optimizations (0.1.2+) / 性能优化

- **Back-face culling**: Structures > 30,000 blocks skip faces facing away from camera. / 大结构跳过来自相机背面的面。
- **Rotation LOD**: Blocks are randomly dropped during drag-rotation for smooth FPS. / 拖拽旋转时随机抽稀渲染。
- **Layer view**: Single layer display disables all culling and LOD. / 分层视图不启用任何优化。

## Skip Blocks (0.1.2+) / 扫描排除方块

20 blocks that cannot exist independently or are unobtainable are automatically skipped during scanning.
20 种无法独立存在或生存不可获取的方块扫描时自动跳过：

| Category 1: Needs support / 需要支撑 | Category 2: Creative-only / 生存不可获取 |
|---|---|
| FIRE, SOUL_FIRE, NETHER_PORTAL, BUBBLE_COLUMN | COMMAND_BLOCK, REPEATING_COMMAND_BLOCK, CHAIN_COMMAND_BLOCK |
| PISTON_HEAD, MOVING_PISTON, FROSTED_ICE | STRUCTURE_BLOCK, STRUCTURE_VOID, BARRIER, LIGHT, JIGSAW |
| END_PORTAL, END_GATEWAY | SPAWNER, BUDDING_AMETHYST, REINFORCED_DEEPSLATE |

Addon mods can register additional blocks / 附属 mod 可注册额外方块:
```java
MultiblockScannerItem.skipBlock(MyBlocks.CUSTOM_TRANSIENT);
```

## Fluid Rendering (0.1.2+) / 流体渲染

Water and lava source blocks are rendered as translucent colored cubes in the Visualizer.
Flowing fluids are treated as air — only source blocks count as structure.
水/岩浆源在 Visualizer 中显示为半透明色块。流动液体视为空气。

Custom fluid colors for addon mods / 附属 mod 自定义流体颜色:
```java
MultiblockVisualizerScreen.FLUID_COLORS.put(
    MyFluids.STEAM_SOURCE.get(),
    new float[]{1.0f, 1.0f, 1.0f, 0.6f}  // rgba
);
```
