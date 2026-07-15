# Ore System / 矿石系统

One-click ore registration — define a `Material` and everything is auto-generated: block textures, processing item textures, model JSONs, blockstates, translations, tool tags, ore dictionary tags.

一键矿石注册——定义 `Material` 即全自动生成：方块贴图、加工物品贴图、模型JSON、blockstate、翻译、工具标签、矿辞标签。

---

## EECore Built-in / EECore 内置矿

```java
// In EECore constructor / EECore 构造器中
OreRegistry.registerAll(
    new Material("iron",    0xAF, 0x8E, 0x77, "Iron",    "铁",   "minecraft:needs_stone_tool"),
    new Material("copper",  0xC1, 0x67, 0x46, "Copper",  "铜",   "minecraft:needs_stone_tool"),
    new Material("gold",    0xFF, 0xD7, 0x00, "Gold",    "金",   "minecraft:needs_iron_tool"),
    new Material("diamond", 0xA0, 0xFF, 0xFF, "Diamond", "钻石", "minecraft:needs_iron_tool")
);
```

Each material gets:
- Ore block + BlockItem + 9 processing items (raw, crushed, purified, refined, dusts...)
- 5 spot variant textures (`<id>_ore_0~4.png`)
- Block model + blockstate + item model (auto-generated)
- Tool tag + ore dict tags (`c:ores/<id>`, `c:raw_materials/<id>`, `c:dusts/<id>`...)
- EN/ZH translations
- Random spot variant (NBT-persisted, assigned per-material at registration)

每种材质自动获得：矿块+物品+9种加工产物、5变体矿斑、模型JSON全自动、工具标签+矿辞、中英翻译、随机矿斑NBT持久化。

---

## Addon Mod Registration / 附属 Mod 注册

```java
// In addon mod constructor / 附属Mod构造器
OreRegistry.registerAll(
    "mymod", MYMOD_BLOCKS, MYMOD_ITEMS, myCreativeTab,
    new Material("uranium", 0x55, 0xAA, 0x33, "Uranium", "铀", "minecraft:needs_iron_tool")
);
```

All ore block textures are stored in `eecore:textures/block/ores/` (shared namespace). Processing item textures and models go to the addon's namespace. Block Entity is provided by EECore (`BlockEntities.ORE`).

矿块贴图统一存在 `eecore:textures/block/ores/`，加工物品贴图和模型走 addon 命名空间。BE 由 EECore 提供。

---

## Material Record / Material 定义

```java
public record Material(
    String id,        // "uranium" / 材质ID
    int r, int g, int b, // RGB color for ore spot tinting / 矿斑染色RGB
    String nameEn,    // "Uranium" / 英文名
    String nameZh,    // "铀" / 中文名
    String toolTag    // "minecraft:needs_iron_tool" / 挖掘等级标签
)
```

---

## Rendering / 渲染

- **Solid pass / solid层**: 6-face cube with stone texture from the block below (via `OreBlockEntity.BELOW_STATE` ModelData)
- **Cutout pass / cutout层**: 6-face cube with randomly-assigned ore spot texture, slightly inflated to prevent z-fighting
- **Stone base lookup / 石底查找**: Drill-down through up to 128 blocks, skipping air/fluids/ore blocks, until finding a solid rock
- **Spot selection / 矿斑选择**: Randomly assigned per-material at registration, stored in BlockEntity NBT

---

## Template Textures / 模板贴图

Located at `assets/eecore/textures/template/` / 位于 `assets/eecore/textures/template/`:

| Directory / 目录 | Content / 内容 |
|---|---|
| `ore_spot/` | 5 grayscale ore spot patterns / 5种灰度矿斑 |
| `material_stage/` | 9 processing stage shapes / 9种加工阶段形状 |

Tinted at runtime by `OreRegistry.registerAll()` with the material's RGB color.

由 `registerAll()` 运行时用材质RGB染色。

---

## Ore Dictionary Tags / 矿辞标签

Auto-generated per material / 每种材质自动生成:

| Tag / 标签 | Example / 示例 |
|---|---|
| `c:ores/<id>` | `c:ores/iron` |
| `c:ores_in_ground/stone` | (all ores / 全部矿石) |
| `c:raw_materials/<id>` | `c:raw_materials/iron` |
| `c:dusts/<id>` | `c:dusts/iron` |
| `c:small_dusts/<id>` | `c:small_dusts/iron` |
| `c:tiny_dusts/<id>` | `c:tiny_dusts/iron` |

---

## Addon Mod Example / 附属 Mod 完整示例

```java
@Mod("mymod")
public class MyMod {
    public static final DeferredRegister<Block> BLOCKS =
            DeferredRegister.create(Registries.BLOCK, "mymod");
    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(Registries.ITEM, "mymod");
    public static final List<Supplier<Item>> CREATIVE_TAB_ITEMS = new ArrayList<>();

    public MyMod(IEventBus modBus) {
        // One-click: register all ores / 一键注册所有矿石
        OreRegistry.registerAll("mymod", BLOCKS, ITEMS, CREATIVE_TAB_ITEMS,
            new Material("uranium", 0x55, 0xAA, 0x33, "Uranium", "铀", "minecraft:needs_iron_tool"),
            new Material("tin",     0x88, 0x88, 0x88, "Tin",     "锡", "minecraft:needs_stone_tool")
        );

        BLOCKS.register(modBus);
        ITEMS.register(modBus);
    }
}
```
