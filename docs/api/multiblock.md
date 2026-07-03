# 多方块结构系统 / Multiblock System

EECore 的多方块框架：扫描 → 预览 → 标记 → 成形。

---

## 核心概念

- **Pattern**：字符编码 3D 结构（多层网格 + 方块映射表）
- **字符池**：A=空气，K=控制器，#=通配符，253 种可打印字符（0x21–0xFE）
- **.ecs 格式**：EECore 私有二进制格式，gzip + CRC32，仅 EECore 可编解码
- **标记 (Tag)**：给字符打标签名（如 `"gregtech:input_hatch"`），具体含义由附属 mod 通过 `TagDefRegistry` 定义
- **控制器**：实现 `IMultiBlockController` 的方块
- **成形**：Shift+右键控制器 → 匹配 Pattern → 验证方块和 tag 数量

---

## 快速流程

```
① 放控制器 + 方块
② 扫描仪标记两角 → Shift+右键扫描
③ 右键空气 → Visualizer 预览
④ 编辑模式 → 点方块 → "标记" → 输入标签名
⑤ 保存为 .ecs（默认格式）
⑥ Shift+右键控制器 → 成形验证
```

---

## .ecs 文件格式

```
[hdr] 魔数 "EECS" + 版本号(1/2) + 标志位(bit0=gzip)
[crc] CRC32（头+载荷）
[payload]
  VarInt: width, height, depth, ctrlX/Y/Z
  VarInt: 调色板大小
    for each:
      Byte: 字符
      VarInt+UTF8: 方块ID
      VarInt: tag数量 + for each: VarInt+UTF8 tag名
  Byte: bitsPerVoxel
  VarInt: 体素数
  Byte[]: 体素数据（调色板索引）
```

附属 mod 可直接用 `ecsformat` 包中的 `EcsRawCodec`（纯 JDK，无 MC 依赖）解析。

---

## API

### MultiBlockPattern

```java
new MultiBlockPattern(w, h, d, cx, cy, cz, layers, definitions);

pattern.addAlternatives('B', ironBlock, steelBlock);
pattern.getAlternatives('B');   // → {原方块, 替代方块}
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

### EcsRawCodec（纯 JDK，无 MC 依赖）

```java
EcsRawData data = EcsRawCodec.read(path);
data.palette();   // → List<EcsPaletteEntry> (每个条目: character, blockId, tags)
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
// 附属 mod 在 commonSetup 中注册
TagDefRegistry.register("gregtech:input_hatch",
    Set.of(SLV_HATCH, LV_HATCH, HV_HATCH),
    4  // 全局上限
);
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

## Visualizer 操作

| 操作 | 效果 |
|------|------|
| 左键拖拽 | 旋转 |
| 滚轮 | 缩放 |
| 左键点击方块 | 浏览模式：显示替代方块；编辑模式：替换/标记 |
| G | 重置视角 |
| W/S | 切换结构（浏览模式）|
| Alt+` | 切换编辑/浏览模式 |
| Del | 删除（带确认） |
| Ctrl+Z | 撤销 |
| Ctrl+C | 复制方块名 |
| Ctrl+V | 粘贴替换 |

### 编辑模式

| 按钮 | 功能 |
|------|------|
| 替换 | 搜索方块替换当前位置 |
| 批量 | 替换所有同字符方块 |
| 单删/批删 | 方块变空气 |
| 标记 | 给字符打标签名（每字符最多一个）|

保存默认为 `.ecs` 格式。调试可用 `/eecore export <id> json` 导出可读 JSON。

---

## 命令

```text
/eecore reload                # 重载磁盘结构
/eecore debug mbvis           # 打开调试结构
/eecore export <id> [ecs|json] # 导出
/eecore import <filename>     # 导入
```

---

## 附属 Mod 注册结构

```java
MultiBlockPattern pattern = EECoreCodec.read(ecsFile);
MultiBlockRegistry.registerMod(id, pattern);
TagDefRegistry.register("my_mod:input_hatch", blocks, 4);
MultiBlockRegistry.registerControllerBlock(controller);
```

---

## 许可

v0.1.1 起：**GNU GPL v3.0**
附属 mod 使用 EECore API 需遵守 GPL 3.0。

## MultiblockLoader API (0.1.2+)

Register multiblock machines from .ecs structure files.

```java
MultiblockLoader.load(ResourceLocation.parse("your_mod:structure_name"))
    .where("TagName", Blocks.IRON_BLOCK)
        .or(Blocks.GOLD_BLOCK)
    .register(ResourceLocation.parse("your_mod:machine_id"));
```

The .ecs file should be placed at:
- `src/main/resources/data/{namespace}/structures/{name}.ecs` (mod jar)
- or `config/eecore/structures/{namespace}/{name}.ecs` (runtime)

## Controller Preview

Right-click air with any controller block to open a 3D preview of the bound structure.
The preview is read-only and reads from disk/network — zero memory footprint on the client.
