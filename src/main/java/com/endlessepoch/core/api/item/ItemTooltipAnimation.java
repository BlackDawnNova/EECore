package com.endlessepoch.core.api.item;

import net.minecraft.network.chat.Component;

import java.util.function.Function;

/**
 * Animation configuration for item tooltips.
 * <p>
 * Other mods pass this to {@link AnimatedItem} to define
 * how the title, author, and description lines are rendered.
 * <p>
 * Pre-built presets are provided for common cases, or build your own.
 * <p>
 * 物品提示框的动画配置。
 * <p>
 * 其他模组将此对象传递给 {@link AnimatedItem}，
 * 以定义标题、作者和描述行的渲染方式。
 * <p>
 * 为常见情况提供了预构建预设，也可以自行构建。
 */
public record ItemTooltipAnimation(
        Function<String, Component> titleRenderer,
        Function<String, Component> authorRenderer,
        String descriptionKey,
        String[] extraLines
) {
    /** Cyan-green wave title (EECore default). / 青绿波浪标题（EECore 默认）。 */
    public static ItemTooltipAnimation eecoreDefault(String descKey) {
        return new ItemTooltipAnimation(
                com.endlessepoch.core.api.text.AnimatedText::cyanGreen,
                com.endlessepoch.core.api.text.AnimatedText::rainbowKey,
                descKey,
                new String[0]
        );
    }

    /** Rainbow title, golden author. / 彩虹标题，金色作者。 */
    public static ItemTooltipAnimation legendary(String descKey, String... extra) {
        return new ItemTooltipAnimation(
                com.endlessepoch.core.api.text.AnimatedText::rainbowKey,
                com.endlessepoch.core.api.text.AnimatedText::golden,
                descKey,
                extra
        );
    }

    /** Simple blink title, no author. / 简单闪烁标题，无作者。 */
    public static ItemTooltipAnimation simple(String descKey) {
        return new ItemTooltipAnimation(
                com.endlessepoch.core.api.text.AnimatedText::blink,
                null,
                descKey,
                new String[0]
        );
    }

    /** Custom: other mods define their own renderers. / 自定义：其他模组定义自己的渲染器。 */
    public static ItemTooltipAnimation custom(
            Function<String, Component> titleRenderer,
            String descKey) {
        return new ItemTooltipAnimation(titleRenderer, null, descKey, new String[0]);
    }
}
