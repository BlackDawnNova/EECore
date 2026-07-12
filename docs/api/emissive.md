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
