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
 */
public record ItemTooltipAnimation(
        Function<String, Component> titleRenderer,
        Function<String, Component> authorRenderer,
        String descriptionKey,
        String[] extraLines
) {
    /** Cyan-green wave title (EECore default). */
    public static ItemTooltipAnimation eecoreDefault(String descKey) {
        return new ItemTooltipAnimation(
                com.endlessepoch.core.api.text.AnimatedText::cyanGreen,
                com.endlessepoch.core.api.text.AnimatedText::rainbowKey,
                descKey,
                new String[0]
        );
    }

    /** Rainbow title, golden author. */
    public static ItemTooltipAnimation legendary(String descKey, String... extra) {
        return new ItemTooltipAnimation(
                com.endlessepoch.core.api.text.AnimatedText::rainbowKey,
                com.endlessepoch.core.api.text.AnimatedText::golden,
                descKey,
                extra
        );
    }

    /** Simple blink title, no author. */
    public static ItemTooltipAnimation simple(String descKey) {
        return new ItemTooltipAnimation(
                com.endlessepoch.core.api.text.AnimatedText::blink,
                null,
                descKey,
                new String[0]
        );
    }

    /** Custom: other mods define their own renderers. */
    public static ItemTooltipAnimation custom(
            Function<String, Component> titleRenderer,
            String descKey) {
        return new ItemTooltipAnimation(titleRenderer, null, descKey, new String[0]);
    }
}
