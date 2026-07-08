# Emissive API / 发光渲染 API

Add emissive (fullbright) glow rendering to blocks via `cutoutMipped` layer + `neoforge_data` fullbright.
通过 `cutoutMipped` 渲染层 + `neoforge_data` 全亮度为方块添加发光效果。

## Registration / 注册

```java
// In mod constructor / 在 Mod 构造方法中调用
EmissiveHelper.registerEmissiveModel(
    "your_mod:block/your_machine",       // block model path / 方块模型路径
    "your_mod:block/your_machine_e"      // emissive texture path / 发光贴图路径
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

### Multiblock machines / 多方块机器

Machine controllers use `ee_base_12_front_emissive` with voltage-tier casing body + machine overlay textures. Auto-generated — no manual JSON needed.
机器控制器使用 `ee_base_12_front_emissive`，外壳贴图由电压等级决定，面板贴图来自机器目录。自动生成，无需手写 JSON。

See [Multiblock System / 多方块系统](multiblock.md) for machine registration details.
机器注册详见 [多方块系统文档](multiblock.md)。

### Template reference / 模板参考

| Template / 模板 | Elements / 元素 | Description / 说明 |
|---|---|---|
| `ee_base_16` | 1 | Plain 16×16 cube / 纯方块体 |
| `ee_base_12` | 2 | Cube + 12×12 front panel / 方块体 + 正面凹面板 |
| `ee_base_12_front_emissive` | 3 | Cube + panel + emissive overlay (fullbright) / 方块体 + 面板 + 发光层 |

## Machine Directory Textures / 机器目录贴图

For multiblock machines, place textures in / 多方块机器贴图放置:
```
assets/<modid>/textures/block/machines/<machine_id>/
  overlay_front.png       ← front panel design / 面板图案
  overlay_front_e.png     ← emissive version (optional) / 发光版（可选，没有就不发光）
```

The model composites / 模型合成: tier casing body (side) + machine overlay (front) + emissive (fullbright).
