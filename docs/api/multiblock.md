# 多方块结构系统 / Multiblock Structure System

完整的多方块结构框架：扫描 → 缓存 → 预览 → 成形。

---

## 核心概念 / Concepts

- **Pattern**：结构定义（多层字符网格 + 方块映射表）
- **字符池**：A=空气，K=控制器，#=通配符，支持 306 种不同方块类型
- **控制器**：仅通过 `registerControllerBlock()` 注册的方块被识别为 'K'
- **扫描仪**：OP 专用手持物品，标记两角后扫描生成 Pattern
- **Visualizer**：3D 预览界面，支持旋转/缩放/分层预览/编辑替换
- **成形**：Shift+右键控制器，验证结构是否匹配 Pattern

---

## 快速流程 / Quick Flow

```
① 放置控制器方块 + 结构方块
② 手持扫描仪右键标记两个对角
③ Shift+右键扫描 → Pattern 注册到缓存
④ 右键空气 → 打开 3D Visualizer 预览
⑤ Shift+右键控制器 → 成形验证
```

---

## API 参考 / API Reference

### MultiBlockPattern

```java
// width, height, depth = 结构尺寸
// controllerX/Y/Z = 控制器在结构内的相对坐标
// layers = [layer][row] 字符层
// definitions = 字符 → BlockState

new MultiBlockPattern(width, height, depth,
    controllerX, controllerY, controllerZ,
    layers, definitions);

// 注册可替换的同类方块组
pattern.addAlternatives('A', inputBus, inputHatch, steamHatch);
pattern.getAlternatives('A'); // → {铁块, 输入总线, 输入仓, 蒸汽仓}
```

### MultiBlockRegistry

```java
// 注册全局结构（Mod 初始化时，所有玩家可见）
MultiBlockRegistry.registerMod(id, pattern);

// 注册玩家本地结构（扫描时的默认行为）
MultiBlockRegistry.registerLocal(playerId, id, pattern);

// 注册控制器方块（编辑模式下 'K' 的可替换列表）
MultiBlockRegistry.registerControllerBlock(block);

// 查询控制器方块
MultiBlockRegistry.getControllerBlocks();

// 删除单个玩家本地结构
MultiBlockRegistry.removeLocal(playerId, id);

// 查询
MultiBlockRegistry.get(playerId, id);   // 玩家 + 全局
MultiBlockRegistry.getAll(playerId);     // 玩家可见的全部

// 清理（玩家断开时）
MultiBlockRegistry.clearLocal(playerId);
```

### IMultiBlockController

控制器方块的接口：

```java
UUID getNodeId();            // 节点 ID
boolean isFormed();          // 是否已成形
void onMultiblockFormed();   // 成形回调 → 注册节点
void onMultiblockBroken();   // 破坏回调 → 注销节点
UUID getOwnerUUID();         // 拥有者
String getOwnerName();
void stampOwner(UUID owner, String name);  // 烙印拥有者
```

---

## 3D Visualizer / 3D 预览

### 操作 / Controls

| 操作 | 效果 |
|------|------|
| 左键拖拽 | 旋转视角 |
| 滚轮 | 缩放（>3× 进入沉浸模式）|
| 左键点击方块 | 浏览模式：显示可替换方块；编辑模式：替换/撤销 |
| G 键 | 重置视角（仅浏览模式） |
| W/S | 切换结构（仅浏览模式） |
| Alt+` | 切换编辑/浏览模式 |
| Del | 删除缓存结构（带确认弹窗） |
| Ctrl+Z | 撤销替换（仅编辑模式） |
| Ctrl+C | 复制方块名（仅编辑模式） |
| Ctrl+V | 粘贴替换（仅编辑模式） |

### 分层预览 / Layer View

屏幕右侧按钮组：**左键**=上一层，**右键**=下一层，**↻**=显示全部层。控制器光晕仅在当前层闪烁。

### 方块搜索 / Block Search

编辑模式下输入文字搜索方块，支持：
- 英文名：`stone` → 所有石类方块
- 拼音首字母：`st` → 石头，`ys` → 圆石
- 全拼：`shitou` → 石头，`yushi` → 玉石
- 模糊音：`zh=z, sh=s, ch=c, ang=an, ing=in, eng=en`
- 空格分词：`red stone` → Redstone 相关
- 滚轮翻页、点击选择

基于 PinIn 拼音引擎（MIT 协议，嵌入式集成）。

### Pick / 拾取

点击预览区方块，使用 **Ray-AABB 求交**（Slab 法）计算命中：
1. 从屏幕点击位置发射射线，经投影矩阵逆变换到模式空间
2. 对每个方块做 AABB 求交，取 t 最近的命中
3. 拖拽旋转时超过 5px 则不触发拾取（区分点击与拖拽）
4. 选中方块显示**快速闪烁（300ms）灰白线框 + 3 层同心外发光**

### 方块替换 / Block Replacement

编辑模式下（Alt+`）点击方块后可选 **[单个]** 或 **[批量]**：
- 单个替换：仅替换该位置，创建新字符
- 批量替换：替换该字符的所有方块
- 撤销栈支持连续多次撤销至原始状态
- 'K' 控制器仅允许替换为已注册的控制器方块

---

## 多控制器高亮 / Multi-Controller Highlight

当扫描区域存在 > 1 个控制器时：

- 服务端将控制器坐标存入物品 NBT `controllers`
- 客户端渲染**穿透方块的脉冲红光柱 + 线框**
- 光柱高 64 格，任何地形都可见
- 提示：清理所有控制器后重新放置

使用自定义 `RenderType`（`NO_DEPTH_TEST`）实现真·穿透渲染。

---

## 扫描器权限 / Scanner Permission

扫描器仅限 OP（权限等级 ≥ 2）使用。非 OP 玩家即使通过 `/give` 获取物品也无法标记或扫描。

---

## 字符池 / Character Pool

扫描器使用预定义字符池（306 种），保留字符：
- `A` = 空气
- `K` = 控制器
- `#` = 通配符（任意方块）

超过 306 种不同类型时拒绝扫描，提示精简结构。

---

## 提供全局结构 / For Addon Mods

附属 Mod 在 `FMLCommonSetupEvent` 中注册：

```java
MultiBlockPattern pattern = new MultiBlockPattern(3, 3, 3,
    1, 0, 1, layers, definitions);
MultiBlockRegistry.registerMod(
    ResourceLocation.fromNamespaceAndPath("my_mod", "my_struct"),
    pattern
);
```
