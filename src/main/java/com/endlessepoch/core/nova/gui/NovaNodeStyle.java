package com.endlessepoch.core.nova.gui;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * Color theme for NovaNet GUI screens.
 * <p>
 * Other mods can instantiate custom themes or use the presets.
 * Default: dark cyber-green theme matching EECore's aesthetic.
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
    /** Default green-cyber theme. */
    public static NovaNodeStyle defaultStyle() {
        return new NovaNodeStyle(
                0xFF0A0A0A,  // bg — near black
                0xFF003300,  // border — dark green
                0xFF005500,  // glow
                0xFF00FF88,  // header — bright green
                0xFF008844,  // stats — mid green
                0xFF111111,  // bar bg
                0xFF005500,  // bar normal
                0xFF00AA00,  // bar full
                0xFF0066FF,  // input rate — blue
                0xFFFF6600,  // output rate — orange
                0xFFFFFFFF   // bar text — white
        );
    }

    /** Blue theme for receivers. */
    public static NovaNodeStyle receiverStyle() {
        return new NovaNodeStyle(
                0xFF0A0A0C, 0xFF001133, 0xFF002255,
                0xFF4488FF, 0xFF224488,
                0xFF111111, 0xFF003388, 0xFF0066FF,
                0xFF0066FF, 0xFFFF6600, 0xFFFFFFFF
        );
    }

    /** Gold theme for hubs. */
    public static NovaNodeStyle hubStyle() {
        return new NovaNodeStyle(
                0xFF0C0A06, 0xFF332200, 0xFF553300,
                0xFFFFCC44, 0xFF886622,
                0xFF111111, 0xFF553300, 0xFFAA6600,
                0xFF0066FF, 0xFFFF6600, 0xFFFFFFFF
        );
    }
}
