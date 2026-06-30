package com.endlessepoch.core.api.text;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;

/**
 * Animated text utility — creates Components with time-based color effects.
 * <p>
 * Other mods can use these methods anywhere a Component is needed,
 * not just in tooltips. The animation is driven by System.currentTimeMillis().
 */
public final class AnimatedText {

    private AnimatedText() {}

    // ============================================================
    //  Preset animations (use these for quick setup)
    // ============================================================

    /** Rainbow wave: default speed. */
    public static MutableComponent rainbow(String text) {
        return rainbow(text, 20, 10, 1f, 0.7f);
    }

    /** Rainbow from a translation key. */
    public static MutableComponent rainbowKey(String translationKey) {
        return rainbow(Component.translatable(translationKey).getString());
    }

    /** Per-character cyan-green hue cycling (like rainbow, but green↔cyan). */
    public static MutableComponent cyanGreen(String text) {
        MutableComponent out = Component.empty();
        long t = System.currentTimeMillis() / 25;
        for (int i = 0; i < text.length(); i++) {
            int hue = 120 + (int)((t + i * 12) % 60); // green(120) ↔ cyan(180)
            out.append(Component.literal(String.valueOf(text.charAt(i)))
                    .withStyle(s -> s.withColor(hslToRgb(hue, 1f, 0.65f))));
        }
        return out;
    }

    /** Golden pulse. */
    public static MutableComponent golden(String text) {
        return wave(text, 60, 0.4f, 0xFFAA00, 0xFFFF66, 0xFFAA00, 0x886600);
    }

    /** Blinking title. */
    public static MutableComponent blink(String text) {
        return blink(text, 400, 0x00FF88, 0x006633);
    }

    // ============================================================
    //  Parameterized versions (other mods control everything)
    // ============================================================

    /**
     * Full-control rainbow wave.
     * @param text        the text to animate
     * @param tickDivisor speed: lower = faster (default 20)
     * @param huePerChar  hue degrees between characters (default 10)
     * @param saturation  0.0 ~ 1.0
     * @param lightness   0.0 ~ 1.0 (0.7 = vibrant)
     */
    public static MutableComponent rainbow(String text, int tickDivisor, int huePerChar,
                                            float saturation, float lightness) {
        MutableComponent out = Component.empty();
        long t = System.currentTimeMillis() / tickDivisor;
        for (int i = 0; i < text.length(); i++) {
            int hue = (int)((t + i * huePerChar) % 360);
            out.append(Component.literal(String.valueOf(text.charAt(i)))
                    .withStyle(s -> s.withColor(hslToRgb(hue, saturation, lightness))));
        }
        return out;
    }

    /**
     * Wave between two colors with speed control.
     * @param tickDivisor lower = faster
     * @param amplitude   how much the wave oscillates (0.3 = subtle)
     * @param colors      alternating colors in ARGB
     */
    public static MutableComponent wave(String text, int tickDivisor, float amplitude, int... colors) {
        MutableComponent out = Component.empty();
        long t = System.currentTimeMillis() / tickDivisor;
        for (int i = 0; i < text.length(); i++) {
            float f = (float)((Math.sin((t * amplitude) + i * 0.5) + 1) / 2);
            int idx = (i % colors.length);
            int nextIdx = (idx + 1) % colors.length;
            int color = lerpColor(colors[idx], colors[nextIdx], f);
            out.append(Component.literal(String.valueOf(text.charAt(i)))
                    .withStyle(s -> s.withColor(color)));
        }
        return out;
    }

    /**
     * Blink between two colors.
     */
    public static MutableComponent blink(String text, int intervalMs, int colorOn, int colorOff) {
        long t = System.currentTimeMillis() / intervalMs;
        int color = (t & 1) == 0 ? colorOn : colorOff;
        return Component.literal(text).withStyle(s -> s.withColor(color));
    }

    // ============================================================
    //  Totally custom — other mods define their own color logic
    // ============================================================

    /**
     * Per-character custom color provider.
     * @param text     the text
     * @param colorFn  (charIndex, timeMillis) → ARGB color
     */
    public static MutableComponent custom(String text, java.util.function.BiFunction<Integer, Long, Integer> colorFn) {
        MutableComponent out = Component.empty();
        long t = System.currentTimeMillis();
        for (int i = 0; i < text.length(); i++) {
            int color = colorFn.apply(i, t);
            out.append(Component.literal(String.valueOf(text.charAt(i)))
                    .withStyle(s -> s.withColor(color & 0xFFFFFF)));
        }
        return out;
    }

    // ============================================================
    //  Utilities
    // ============================================================

    /** Static gradient from start color to end color across the text. */
    public static MutableComponent gradient(String text, int fromColor, int toColor) {
        MutableComponent out = Component.empty();
        int len = Math.max(1, text.length() - 1);
        for (int i = 0; i < text.length(); i++) {
            int color = lerpColor(fromColor, toColor, (float) i / len);
            out.append(Component.literal(String.valueOf(text.charAt(i)))
                    .withStyle(s -> s.withColor(color & 0xFFFFFF)));
        }
        return out;
    }

    /** Sweeping gradient: the gradient "moves" over time. */
    public static MutableComponent sweep(String text, int fromColor, int toColor) {
        MutableComponent out = Component.empty();
        long t = System.currentTimeMillis() / 40;
        int len = Math.max(1, text.length() - 1);
        for (int i = 0; i < text.length(); i++) {
            float pos = ((i + t) % (text.length() * 2)) / (float) (text.length() * 2);
            if (pos > 1) pos = 2 - pos;
            int color = lerpColor(fromColor, toColor, pos);
            out.append(Component.literal(String.valueOf(text.charAt(i)))
                    .withStyle(s -> s.withColor(color & 0xFFFFFF)));
        }
        return out;
    }

    /** Bold helper. */
    public static MutableComponent bold(String text) {
        return Component.literal(text).withStyle(ChatFormatting.BOLD);
    }

    // Color utils

    private static int lerpColor(int c1, int c2, float f) {
        f = Math.max(0, Math.min(1, f));
        return ((int)(((c1 >> 16) & 0xFF) + (((c2 >> 16) & 0xFF) - ((c1 >> 16) & 0xFF)) * f) << 16)
             | ((int)(((c1 >> 8) & 0xFF)  + (((c2 >> 8) & 0xFF)  - ((c1 >> 8) & 0xFF))  * f) << 8)
             | ((int)((c1 & 0xFF)         + ((c2 & 0xFF)         - (c1 & 0xFF))           * f));
    }

    private static int hslToRgb(float h, float s, float l) {
        float c = (1 - Math.abs(2 * l - 1)) * s;
        float x = c * (1 - Math.abs((h / 60) % 2 - 1));
        float m = l - c / 2;
        float r, g, b;
        if (h < 60)      { r = c; g = x; b = 0; }
        else if (h < 120) { r = x; g = c; b = 0; }
        else if (h < 180) { r = 0; g = c; b = x; }
        else if (h < 240) { r = 0; g = x; b = c; }
        else if (h < 300) { r = x; g = 0; b = c; }
        else              { r = c; g = 0; b = x; }
        return ((int)((r + m) * 255) << 16) | ((int)((g + m) * 255) << 8) | (int)((b + m) * 255);
    }
}
