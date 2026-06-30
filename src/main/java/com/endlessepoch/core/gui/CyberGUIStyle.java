package com.endlessepoch.core.gui;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class CyberGUIStyle {
    public final int bgDark;
    public final int border;
    public final int borderGlow;
    public final int corner;
    public final int innerBorder;
    public final int textPrimary;
    public final int textSecondary;
    public final int textDim;
    public final int buttonBg;
    public final int buttonHover;
    public final int buttonText;

    public static final CyberGUIStyle GREEN = new CyberGUIStyle(
            0xFF080808, 0xFF00FF00, 0x9900FF00, 0xFF88FF88, 0xAA00FF00,
            0xFF00FF00, 0xFF88FF88, 0xFF004400,
            0x88003300, 0x8800AA00, 0xFF00FF00
    );

    public static final CyberGUIStyle BLUE = new CyberGUIStyle(
            0xFF080808, 0xFF0088FF, 0x990088FF, 0xFF88CCFF, 0xAA0088FF,
            0xFF0088FF, 0xFF88CCFF, 0xFF004488,
            0x88003388, 0x880066CC, 0xFF0088FF
    );

    public static final CyberGUIStyle RED = new CyberGUIStyle(
            0xFF080808, 0xFFFF0044, 0x99FF0044, 0xFFFF88AA, 0xAAFF0044,
            0xFFFF0044, 0xFFFF88AA, 0xFF660022,
            0x88330022, 0x88AA0044, 0xFFFF0044
    );

    public static final CyberGUIStyle PURPLE = new CyberGUIStyle(
            0xFF080808, 0xFFAA44FF, 0x99AA44FF, 0xFFDD88FF, 0xAAAA44FF,
            0xFFAA44FF, 0xFFDD88FF, 0xFF550088,
            0x88330066, 0x88AA44DD, 0xFFAA44FF
    );

    public static final CyberGUIStyle ORANGE = new CyberGUIStyle(
            0xFF080808, 0xFFFF8800, 0x99FF8800, 0xFFFFBB66, 0xAAFF8800,
            0xFFFF8800, 0xFFFFBB66, 0xFF884400,
            0x88442200, 0x88CC6600, 0xFFFF8800
    );

    public CyberGUIStyle(int bgDark, int border, int borderGlow, int corner, int innerBorder,
                         int textPrimary, int textSecondary, int textDim,
                         int buttonBg, int buttonHover, int buttonText) {
        this.bgDark = bgDark;
        this.border = border;
        this.borderGlow = borderGlow;
        this.corner = corner;
        this.innerBorder = innerBorder;
        this.textPrimary = textPrimary;
        this.textSecondary = textSecondary;
        this.textDim = textDim;
        this.buttonBg = buttonBg;
        this.buttonHover = buttonHover;
        this.buttonText = buttonText;
    }
}
