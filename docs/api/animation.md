# 动画工具 / Animation API

Tooltip 和文字动画工具集，供附属 Mod 直接使用。

---

## AnimatedText

### 快速预设

```java
AnimatedText.rainbow("彩色文字");          // 彩虹波浪
AnimatedText.cyanGreen("EECore 风格");     // 青绿波动
AnimatedText.golden("传说物品");            // 金色脉动
AnimatedText.blink("警告");                // 闪烁
AnimatedText.gradient("渐变", 0xFF0000, 0x0000FF);  // 静态渐变 红→蓝
AnimatedText.sweep("扫描", 0xFF0000, 0x0000FF);     // 动态扫描渐变
```

### 可调参数

```java
// 彩虹
AnimatedText.rainbow("文字", 10/*速度*/, 15/*色差*/, 1f/*饱和度*/, 0.8f/*亮度*/);
// 波浪：任意色板
AnimatedText.wave("文字", 50, 0.4f, 红, 橙, 黄, 绿, 蓝, 紫);
// 闪烁
AnimatedText.blink("文字", 200/*ms*/, 0xFF0000, 0xFFFF00);
```

### 完全自定义

```java
AnimatedText.custom("文字", (charIndex, timeMs) -> {
    return (charIndex * 0x111111 + (int)(timeMs / 10)) | 0xFF000000;
});
```

---

## AnimatedItem

继承即获得动画 tooltip。

```java
public class MyItem extends AnimatedItem {
    public MyItem() {
        super(new Properties(),
            new ItemTooltipAnimation(
                AnimatedText::rainbow,       // 标题动画
                AnimatedText::golden,        // 作者动画
                null,                        // 描述翻译键
                new String[]{"my_mod.tip1"}
            ),
            true, "MyMod", "my_mod.item.title"
        );
    }
}
```

### ItemTooltipAnimation 预设

| 预设 | 效果 |
|------|------|
| `eecoreDefault(descKey)` | 青绿标题 + 彩虹作者 |
| `legendary(descKey, extra...)` | 彩虹标题 + 金色作者 |
| `simple(descKey)` | 闪烁标题 |
| `custom(titleRenderer, descKey)` | 自定义标题渲染 |
