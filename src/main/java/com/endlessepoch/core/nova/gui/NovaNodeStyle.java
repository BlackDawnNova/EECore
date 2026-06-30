package com.endlessepoch.core.nova.gui;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * Color theme for NovaNet GUI screens.
 * <p>
 * Other mods can instantiate custom themes or use the presets.
 * Default: dark cyber-green theme matching EECore's aesthetic.
 * <p>
 * NovaNet 图形界面的颜色主题。
 * <p>
 * 其他模组可以实例化自定义主题或使用预设主题。
 * 默认：暗色赛博绿主题，与 EECore 的美学风格一致。
 */
@OnlyIn(Dist.CLIENT)
public record NovaNodeStyle(
        int bgColor,
        int borderColor,
        int glowColor,
        int headerColor,
        int statsColor,
        int barBgColor,
        int barNormalColor,
        int barFullColor,
        int barInputColor,
        int barOutputColor,
        int barTextColor
) {
    /** Default green-cyber theme. / 默认赛博绿主题。 */
    public static NovaNodeStyle defaultStyle() {
        return new NovaNodeStyle(
                0xFF0A0A0A,
                0xFF003300,
                0xFF005500,
                0xFF00FF88,
                0xFF008844,
                0xFF111111,
                0xFF005500,
                0xFF00AA00,
                0xFF0066FF,
                0xFFFF6600,
                0xFFFFFFFF
        );
    }

    /** Blue theme for receivers. / 接收器蓝色主题。 */
    public static NovaNodeStyle receiverStyle() {
        return new NovaNodeStyle(
                0xFF0A0A0C, 0xFF001133, 0xFF002255,
                0xFF4488FF, 0xFF224488,
                0xFF111111, 0xFF003388, 0xFF0066FF,
                0xFF0066FF, 0xFFFF6600, 0xFFFFFFFF
        );
    }

    /** Gold theme for hubs. / 集线器金色主题。 */
    public static NovaNodeStyle hubStyle() {
        return new NovaNodeStyle(
                0xFF0C0A06, 0xFF332200, 0xFF553300,
                0xFFFFCC44, 0xFF886622,
                0xFF111111, 0xFF553300, 0xFFAA6600,
                0xFF0066FF, 0xFFFF6600, 0xFFFFFFFF
        );
    }
}
