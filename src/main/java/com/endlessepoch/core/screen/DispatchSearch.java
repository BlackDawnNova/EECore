package com.endlessepoch.core.screen;

import com.endlessepoch.core.nova.client.PinyinUtil;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class DispatchSearch {
    static final int SEARCH_X = 228, SEARCH_W = 90;
    private final DispatchScreen screen;
    private EditBox field;

    public DispatchSearch(DispatchScreen screen) { this.screen = screen; }

    public EditBox init(int leftPos, int topPos) {
        field = new EditBox(screen.font(), leftPos + SEARCH_X + 15, topPos + 5, SEARCH_W, 12, Component.empty());
        field.setBordered(false); field.setMaxLength(15);
        field.setTextColor(0xFF_EEEEEE);
        field.setHint(Component.translatable("eecore.dispatch.search").withStyle(s -> s.withColor(0xFF_DBDCE0)));
        field.setResponder(screen::onSearch);
        return field;
    }

    public void drawTooltips(GuiGraphics g, int x, int y, int mx, int my) {
        if (DispatchUtil.hit(mx, my, x + SEARCH_X, y + 3, 90, 12)) {
            List<Component> stt = new ArrayList<>();
            stt.add(Component.translatable("eecore.dispatch.search.title"));
            stt.add(Component.translatable("eecore.dispatch.search.hint_mod").withStyle(s -> s.withColor(DispatchScreen.C_TD)));
            stt.add(Component.translatable("eecore.dispatch.search.hint_tag").withStyle(s -> s.withColor(DispatchScreen.C_TD)));
            stt.add(Component.translatable("eecore.dispatch.search.hint_tooltip").withStyle(s -> s.withColor(DispatchScreen.C_TD)));
            stt.add(Component.translatable("eecore.dispatch.search.hint_fuzzy").withStyle(s -> s.withColor(DispatchScreen.C_TD)));
            stt.add(Component.translatable("eecore.dispatch.search.hint_pattern").withStyle(s -> s.withColor(DispatchScreen.C_TD)));
            g.renderTooltip(screen.font(), stt, java.util.Optional.empty(), mx, my);
        }
    }

    public void handleMouseClicked(double mx, double my, int x, int y) {
        if (!DispatchUtil.hit(mx, my, x + SEARCH_X + 2, y + 5, SEARCH_W, 12))
            field.setFocused(false);
    }

    public boolean handleCharTyped(char cp, int mod) {
        if (field.isFocused()) return field.charTyped(cp, mod);
        return false;
    }

    public boolean handleKeyPressed(int key, int scan, int mod) {
        if (!field.isFocused()) return false;
        if (key == 256) { field.setFocused(false); return true; }
        if (field.keyPressed(key, scan, mod)) return true;
        if (key == 258) { screen.sortMode = (screen.sortMode + 1) % 3; screen.onSearch(field.getValue()); }
        return true;
    }

    public String getValue() { return field != null ? field.getValue() : ""; }
    public boolean isVisible() { return field != null && field.isVisible(); }
    public void setVisible(boolean v) { if (field != null) field.setVisible(v); }
}
