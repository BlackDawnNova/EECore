# Emissive API / 发光渲染 API

Add emissive (fullbright) glow rendering to blocks. Renders via `cutoutMipped` + `translucent` layers.
通过 `cutoutMipped` + `translucent` 渲染层为方块添加发光效果，支持 animated `.mcmeta` 纹理动画。

## Auto-Registration / 自动注册

**EECore parts** — auto-detected at registration time via `hasEmissiveTexture()`. No code needed.
**EECore 部件** — 注册时自动检测 `_e` 贴图，无需手写代码。

**Addon mod parts** — pass custom overlay path to `PartReg.register()`. Emissive auto-detected from that path.
**附属 Mod 部件** — `PartReg.register()` 传自定义贴图路径，发光贴图从该路径自动检测。

**Machines** — register manually / **机器** — 手动:
```java
EmissiveHelper.registerEmissiveModel(
    "your_mod:block/your_machine",
    "your_mod:block/your_machine_e"
);
```

## Model Templates / 模型模板

### Simple blocks / 简单方块

Use `eecore:block/ee_base_12_front_emissive` as parent / 使用 `ee_base_12_front_emissive` 做父模型:

```json
{
  "parent": "eecore:block/ee_base_12_front_emissive",
  "textures": {
    "all": "your_mod:block/body",
    "front": "your_mod:block/panel",
    "overlay_emissive": "your_mod:block/your_machine_e"
  }
}
```

The emissive overlay renders ON TOP of the front panel via `translucent` pass with fullbright.
Alpha-transparent areas in `overlay_front_e.png` let the front panel show through — only glowing pixels are emissive.
发光层通过 `translucent` 渲染层覆盖在面板之上，alpha 透明区域透视面板——仅发光像素呈全亮度。

### Multiblock machines / 多方块机器

Machine controllers use `ee_base_12_front_emissive` with voltage-tier casing body + machine overlay textures. Auto-generated — no manual JSON needed.
机器控制器使用 `ee_base_12_front_emissive`，外壳贴图由电压等级决定，面板贴图来自机器目录。自动生成，无需手写 JSON。

See [Multiblock System / 多方块系统](multiblock.md) for registration details. / 注册详见多方块系统文档。

### Template reference / 模板参考

| Template / 模板 | Elements / 元素 | Render / 渲染 | Description / 说明 |
|---|---|---|---|
| `ee_base_16` | 1 | solid | Plain 16×16 cube / 纯方块体 |
| `ee_base_12` | 2 | solid + cutout | Cube + 12×12 front panel / 方块体 + 正面凹面板 |
| `ee_base_12_front_emissive` | 3 | solid + cutout + translucent | Cube + panel + emissive overlay (fullbright) / 方块体 + 面板 + 发光叠加层 |

## Part Textures / 部件贴图

```
assets/<modid>/textures/block/parts/<part_id>/
  overlay_front.png       ← front panel (16×16) / 面板
  overlay_front_e.png     ← emissive glow (12×12 UV area per frame, animated supported) / 发光叠加（每帧12×12 UV区域，支持动画）
```

Animated emissive: use `16×N` PNG + `.mcmeta` with `{"animation":{"frametime":8}}`.
动画发光贴图：`16×N` PNG + `.mcmeta` 指定帧率。

## Machine Directory Textures / 机器目录贴图

```
assets/<modid>/textures/block/machines/<machine_id>/
  overlay_front.png       ← front panel design / 面板图案
  overlay_front_e.png     ← emissive version (optional) / 发光版（可选）
```

## Optional Fusion Enhancement / 可选 Fusion 增强

[Fusion](https://modrinth.com/mod/fusion-connected-textures) (optional mod) provides **texture-level emissive** — the texture itself renders at full brightness, independent of the model format. When Fusion is loaded, EECore automatically skips its `GlowBakedModel` wrapper (they conflict) and relies on Fusion's fullbright instead; without Fusion the classic `EmissiveHelper` mechanism above is the fallback.
[Fusion](https://modrinth.com/mod/fusion-connected-textures)（可选 mod）提供**纹理级发光**——贴图本身按满亮度渲染，与模型格式无关。装了 Fusion 时 EECore 自动跳过 `GlowBakedModel` 包裹（两者冲突）改用 Fusion 满亮；没装则回退上面的 `EmissiveHelper` 经典机制。

### Setup / 配置

1. Add `.mcmeta` next to the `_e` texture (or any texture you want fullbright) / 在要发光的贴图旁加 `.mcmeta`:
```json
{
  "fusion": { "type": "base", "emissive": true }
}
```
2. Declare `fusion` in your mod's `pack.mcmeta` (required for mod resources — without it Fusion ignores your mcmeta) / 在 mod 的 `pack.mcmeta` 声明 fusion（mod 资源必需，否则 Fusion 忽略你的 mcmeta）:
```json
{
  "pack": { "pack_format": 34, "supported_formats": [0, 1000] },
  "fusion": { "min_version": "1.3.0" }
}
```
3. Declare the optional dependency in `neoforge.mods.toml` / 在 `neoforge.mods.toml` 声明可选依赖:
```toml
[[dependencies.your_mod_id]]
modId = "fusion"
type = "optional"
versionRange = "[1.0.0,)"
ordering = "NONE"
side = "CLIENT"
```
4. Add `"render_type": "cutout"` to your block model JSON — transparent overlays render correctly when the `GlowBakedModel` wrapper is absent / 方块模型 JSON 加 `"render_type": "cutout"`——无包裹时透明覆面正确渲染（参考 `ee_base_12_front_emissive`）。

### Notes / 注意

- Fusion is **All rights reserved** — it cannot be bundled (jarJar); players/launchers install it themselves. EECore declares it optional only / Fusion 是 All rights reserved——不能内嵌打包，玩家/整合包自装，EECore 只声明可选。
- Runtime check single point: `FusionSupport.active()` / 运行期判断单点：`FusionSupport.active()`。
- The `ctm` mcmeta block (`"layer": "BLOOM"`, used by CTM/GTCEu ecosystems) is inert without the CTM mod — Fusion does not provide bloom / `ctm` mcmeta 块（BLOOM 层，CTM/GTCEu 生态用）在无 CTM mod 时无效——Fusion 不提供泛光。
