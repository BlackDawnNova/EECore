# 多方块结构系统 / Multiblock Structure System

完整的多方块结构框架：扫描 → 缓存 → 预览 → 成形。

---

## 核心概念 / Concepts

- **Pattern**：结构定义（多层字符网格 + 方块映射表）
- **控制器**：被扫描仪检测为 'K' 的特殊方块，驱动成形
- **扫描仪**：手持物品，标记两角后扫描生成 Pattern
- **Visualizer**：3D 预览界面，支持旋转/缩放/点击选块
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
```

### MultiBlockRegistry

```java
// 注册全局结构（Mod 初始化时，所有玩家可见）
MultiBlockRegistry.registerMod(id, pattern);

// 注册玩家本地结构（扫描时的默认行为）
MultiBlockRegistry.registerLocal(playerId, id, pattern);

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

### 操作

| 操作 | 效果 |
|------|------|
| 左键拖拽 | 旋转视角 |
| 滚轮 | 缩放（>3× 进入沉浸模式）|
| 左键点击方块 | 选中并高亮 |
| G 键 | 重置视角 |
| W/S | 切换结构 |

### Pick / 拾取

点击预览区方块，使用 **Ray-AABB 求交**（Slab 法）计算命中：
1. 从屏幕点击位置发射射线，经投影矩阵逆变换到模式空间
2. 对每个方块做 AABB 求交，取 t 最近的命中
3. 拖拽旋转时超过 5px 则不触发拾取（区分点击与拖拽）
4. 选中方块显示**快速闪烁（300ms）灰白线框 + 3 层同心外发光**

---

## 多控制器高亮 / Multi-Controller Highlight

当扫描区域存在 > 1 个控制器时：

- 服务端将控制器坐标存入物品 NBT `controllers`
- 客户端渲染**穿透方块的脉冲红光柱 + 线框**
- 光柱高 64 格，任何地形都可见
- 提示：清理所有控制器后重新放置

使用自定义 `RenderType`（`NO_DEPTH_TEST`）实现真·穿透渲染。

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
