# Ore System / 矿石系统

One-click ore registration — define a `Material` and everything is auto-generated: block textures, processing item textures, model JSONs, blockstates, translations, tool tags, ore dictionary tags, and worldgen JSONs (configured/placed features + biome modifiers).

一键矿石注册——定义 `Material` 即全自动生成：方块贴图、加工物品贴图、模型JSON、blockstate、翻译、工具标签、矿辞标签与世界生成JSON（configured/placed feature + biome modifier）。

---

## EECore Built-in / EECore 内置矿

```java
// In EECore constructor / EECore 构造器中
OreRegistry.registerAll(
    // id        R    G    B   EN          ZH     工具标签                    替换标签                           矿团大小 数量 最低Y 最高Y 原版矿名                   群系标签
    new Material("iron",      0xAF, 0x8E, 0x77, "Iron",     "铁",   "minecraft:needs_stone_tool", "minecraft:stone_ore_replaceables",   8, 20, -64,  64, "minecraft:ore_iron",        "#c:is_overworld"),
    new Material("copper",    0xC1, 0x67, 0x46, "Copper",  "铜",   "minecraft:needs_stone_tool", "minecraft:stone_ore_replaceables",   8, 16, -16, 112, "minecraft:ore_copper",      "#c:is_overworld"),
    new Material("gold",      0xFF, 0xD7, 0x00, "Gold",    "金",   "minecraft:needs_iron_tool",  "minecraft:stone_ore_replaceables",   6,  4, -64,  32, "minecraft:ore_gold",        "#c:is_overworld"),
    new Material("diamond",   0xA0, 0xFF, 0xFF, "Diamond", "钻石", "minecraft:needs_iron_tool",  "minecraft:stone_ore_replaceables",   4,  4, -64,  16, "minecraft:ore_diamond",     "#c:is_overworld"),
    // Overworld / 主世界
    new Material("coal",      0x33, 0x33, 0x33, "Coal",     "煤",   "minecraft:needs_stone_tool", "minecraft:stone_ore_replaceables",  17, 20,   0, 192, "minecraft:ore_coal",        "#c:is_overworld"),
    new Material("redstone",  0xFF, 0x00, 0x00, "Redstone","红石", "minecraft:needs_iron_tool",  "minecraft:stone_ore_replaceables",   8,  8, -64,  15, "minecraft:ore_redstone",    "#c:is_overworld"),
    new Material("lapis",     0x22, 0x44, 0xCC, "Lapis",   "青金石","minecraft:needs_stone_tool", "minecraft:stone_ore_replaceables",   7,  2, -64,  64, "minecraft:ore_lapis",       "#c:is_overworld"),
    new Material("emerald",   0x11, 0xCC, 0x55, "Emerald", "绿宝石","minecraft:needs_iron_tool",  "minecraft:stone_ore_replaceables",   3,  3, -16, 320, "minecraft:ore_emerald",     "#c:is_overworld"),
    // Nether / 下界
    new Material("nether_gold",   0xFF, 0xD7, 0x00, "Nether Gold",    "下界金",   "minecraft:needs_stone_tool", "minecraft:nether_ore_replaceables", 10, 10, 10, 117, "minecraft:ore_nether_gold",   "#c:is_nether"),
    new Material("nether_quartz", 0xFF, 0xF5, 0xEE, "Nether Quartz", "下界石英", "minecraft:needs_stone_tool", "minecraft:nether_ore_replaceables", 14, 16, 10, 117, "minecraft:ore_nether_quartz", "#c:is_nether")
);
```

Each material gets:
- Ore block + BlockItem + 9 processing items (raw, crushed, purified, refined, dusts...)
- 5 spot variant textures (`<id>_ore_0~4.png`)
- Block model + blockstate + item model (auto-generated)
- Tool tag + ore dict tags (`c:ores/<id>`, `c:raw_materials/<id>`, `c:dusts/<id>`...)
- EN/ZH translations
- Worldgen JSONs (configured_feature + placed_feature + biome_modifier, written to both src/ and build/)
- Random spot variant (NBT-persisted, assigned per-material at registration)

每种材质自动获得：矿块+物品+9种加工产物、5变体矿斑、模型JSON全自动、工具标签+矿辞、中英翻译、世界生成JSON（双写src/+build/）、随机矿斑NBT持久化。

---

## Addon Mod Registration / 附属 Mod 注册

```java
// In addon mod constructor / 附属Mod构造器
OreRegistry.registerAll(
    "mymod", MYMOD_BLOCKS, MYMOD_ITEMS, myCreativeTab,
    new Material("uranium", 0x55, 0xAA, 0x33, "Uranium", "铀", "minecraft:needs_iron_tool",
        "minecraft:stone_ore_replaceables", 6, 4, -64, 32, "minecraft:ore_uranium", "#c:is_overworld")
);
```

All ore block textures are stored in `eecore:textures/block/ores/` (shared namespace). Processing item textures and models go to the addon's namespace. Block Entity is provided by EECore (`BlockEntities.ORE`).

矿块贴图统一存在 `eecore:textures/block/ores/`，加工物品贴图和模型走 addon 命名空间。BE 由 EECore 提供。

---

## Material Record / Material 定义

```java
public record Material(
    String id,          // "uranium" / 材质ID
    int r, int g, int b, // RGB color for ore spot tinting / 矿斑染色RGB
    String nameEn,      // "Uranium" / 英文名
    String nameZh,      // "铀" / 中文名
    String toolTag,     // "minecraft:needs_iron_tool" / 挖掘等级标签
    String replaceTag,  // "minecraft:stone_ore_replaceables" / 世界生成替换目标标签
    int veinSize,       // Ore vein size / 矿团大小
    int count,          // Veins per chunk / 每区块矿团数
    int minY,           // Min Y level / 最低生成高度
    int maxY,           // Max Y level / 最高生成高度
    String vanillaFeature, // Vanilla ore feature name for removal reference / 被替换的原版矿feature名
    String biomeTag     // "#c:is_overworld" / "#c:is_nether" / 群系标签
)
```

---

## Vanilla Ore Removal / 原版矿石剔除

EECore uses two Mixins to fully disable vanilla ore generation:

EECore 使用两个 Mixin 完全禁用原版矿石生成：

| Mixin | Target / 目标 | Effect / 效果 |
|---|---|---|
| `OreConfigurationMixin` | `OreConfiguration.<init>` | Filters out 18 vanilla ore blocks from small ore features / 过滤18种原版矿小矿簇 |
| `OreVeinifierMixin` | `OreVeinifier.create` | Disables 1.18+ large noise-based copper/iron veins / 禁用大型铜/铁噪声矿脉 |

---

## Anti-Xray Ore Disguise / 反矿透矿石伪装

All eecore ore blocks are automatically disguised as stone (or deepslate below y=0) in chunk packets. When a player gets within 8 blocks, the real ore is revealed via individual block-update packets. Moving away re-hides the ore. This prevents xray-mods from detecting ores through solid terrain. Always-on, no configuration required.

所有 eecore 矿石在区块包中自动伪装为石头（y<0 为深板岩）。玩家靠近 8 格内时，逐玩家发包揭示真实矿石；远离后重新伪装。防止矿透 mod 透视矿块。常开无配置。

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

Tinted at runtime by `OreRegistry.registerAll()` with the material's RGB color. Textures are written to both `src/main/resources/` and `build/resources/main/` simultaneously, so the first launch after adding new materials renders correctly without requiring a game restart.

由 `registerAll()` 运行时用材质RGB染色。贴图双写 `src/main/resources/` 与 `build/resources/main/`，新增矿种首次启动无需小退即可正常渲染。

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
            new Material("uranium", 0x55, 0xAA, 0x33, "Uranium", "铀", "minecraft:needs_iron_tool",
                "minecraft:stone_ore_replaceables", 6, 4, -64, 32, "minecraft:ore_uranium", "#c:is_overworld"),
            new Material("tin",     0x88, 0x88, 0x88, "Tin",     "锡", "minecraft:needs_stone_tool",
                "minecraft:stone_ore_replaceables", 8, 20, -64, 64, "minecraft:ore_tin", "#c:is_overworld")
        );

        BLOCKS.register(modBus);
        ITEMS.register(modBus);
    }
}
```
