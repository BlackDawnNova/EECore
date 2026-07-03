# Emissive API

为方块添加 emissive 发光渲染的 API。使用 `cutoutMipped` 渲染层正确处理透明纹理，配合 JSON 模型的 `neoforge_data` 实现全亮度发光。

## 注册

```java
// 在 Mod 构造方法中调用
EmissiveHelper.registerEmissiveModel(
    "your_mod:block/your_machine",       // 方块模型路径
    "your_mod:block/your_machine_e"      // 发光贴图路径
);
```

## 模型模板

使用 `eecore:block/ee_base_12_front_emissive` 做父模型：

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

## 模板说明

| 模板 | 元素 | 说明 |
|---|---|---|
| `ee_base_16` | 1 | 纯 16×16 方块体 |
| `ee_base_12` | 2 | 方块体 + 12×12 正面面板 |
| `ee_base_12_front_emissive` | 3 | 方块体 + 面板 + 发光覆盖层（neoforge_data 全亮度） |
