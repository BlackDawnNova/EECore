package com.endlessepoch.core.nova.gui;

import com.endlessepoch.core.api.field.INovaNode;
import com.endlessepoch.core.api.field.NodeType;
import com.endlessepoch.core.api.tier.VoltageTier;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import java.util.*;

/**
 * Abstract screen base for all NovaNet node GUIs.
 * <p>
 * Provides a standardized layout with:
 * <ul>
 *   <li>Header: node type icon + tier + title</li>
 *   <li>Stats row: range, connection count</li>
 *   <li>Energy buffer bar</li>
 *   <li>Rate bars (input/output)</li>
 *   <li>Quick-action button area</li>
 *   <li>Connected node list panel</li>
 *   <li>Footer action buttons</li>
 * </ul>
 * <p>
 * Other mods extend this for Transmitter, Receiver, Hub, Relay screens.
 * Override abstract methods to provide custom data.
 *
 * @param <T> the container menu type
 */
@OnlyIn(Dist.CLIENT)
public abstract class NovaNodeScreen<T extends AbstractContainerMenu> extends AbstractContainerScreen<T> {

    // ============================================================
    //  布局常量 / Layout constants
    // ============================================================

    /** Standard screen dimensions. */
    protected static final int PANEL_W = 220;
    protected static final int PANEL_H = 200;

    /** Content area margins (inside the panel border). */
    protected static final int MARGIN_X = 10;
    protected static final int MARGIN_Y = 18;

    /** Header area. */
    protected static final int HEADER_Y = MARGIN_Y;
    protected static final int HEADER_H = 16;

    /** Stats row (below header). */
    protected static final int STATS_Y = HEADER_Y + HEADER_H + 4;
    protected static final int STATS_H = 14;

    /** Energy bar. */
    protected static final int BAR_H = 12;
    protected static final int BAR_W = 190;
    protected static final int BUFFER_Y = STATS_Y + STATS_H + 6;

    /** Rate bars. */
    protected static final int INPUT_RATE_Y = BUFFER_Y + BAR_H + 4;
    protected static final int OUTPUT_RATE_Y = INPUT_RATE_Y + BAR_H + 2;

    /** Quick action buttons. */
    protected static final int QUICK_BTN_Y = OUTPUT_RATE_Y + BAR_H + 8;
    protected static final int QUICK_BTN_W = 62;
    protected static final int QUICK_BTN_H = 16;
    protected static final int QUICK_BTN_GAP = 4;

    /** Node list panel. */
    protected static final int NODE_LIST_Y = QUICK_BTN_Y + QUICK_BTN_H + 8;
    protected static final int NODE_LIST_H = 56;
    protected static final int NODE_LIST_ITEM_H = 12;

    /** Footer. */
    protected static final int FOOTER_Y = PANEL_H - MARGIN_Y - 16;

    // ============================================================
    //  样式 / Style
    // ============================================================

    protected NovaNodeStyle ui = NovaNodeStyle.defaultStyle();

    // ============================================================
    //  构造 / Construction
    // ============================================================

    public NovaNodeScreen(T menu, Inventory inv, Component title) {
        super(menu, inv, title);
        this.imageWidth = PANEL_W;
        this.imageHeight = PANEL_H;
        this.inventoryLabelY = -1000;
        this.titleLabelY = -1000;
    }

    // ============================================================
    //  子类必须实现的抽象方法 / Abstract methods for subclasses
    // ============================================================

    /** The NovaNet node this screen represents. */
    protected abstract INovaNode getNode();

    /** Get the current buffer energy (Ω value). */
    protected abstract long getBufferEnergy();

    /** Get the max buffer capacity (Ω value). */
    protected abstract long getBufferCapacity();

    /** Get the input rate (Ω/t). */
    protected abstract long getInputRate();

    /** Get the output rate (Ω/t). */
    protected abstract long getOutputRate();

    /** Get the connection count and max. */
    protected abstract int getConnectionCount();
    protected abstract int getMaxConnections();

    /** Get the list of connected nodes for the scrollable panel. */
    protected abstract List<NodeListEntry> getConnectedNodes();

    // ============================================================
    //  渲染主循环 / Render loop
    // ============================================================

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        super.renderBackground(g, mx, my, pt);
        super.render(g, mx, my, pt);
        this.renderTooltip(g, mx, my);
    }

    @Override
    protected void renderBg(GuiGraphics g, float pt, int mx, int my) {
        // Panel background
        g.fill(leftPos, topPos, leftPos + PANEL_W, topPos + PANEL_H, ui.bgColor());

        // Border
        renderBorder(g);

        // Content areas
        renderHeader(g);
        renderStatsRow(g);
        renderEnergyBar(g);
        renderRateBars(g);
        renderQuickButtons(g);
        renderNodeList(g);
        renderFooter(g);
    }

    // ============================================================
    //  各区域渲染 / Render sections
    // ============================================================

    protected void renderBorder(GuiGraphics g) {
        int x = leftPos, y = topPos;
        g.fill(x, y, x + PANEL_W, y + 1, ui.borderColor());
        g.fill(x, y + PANEL_H - 1, x + PANEL_W, y + PANEL_H, ui.borderColor());
        g.fill(x, y, x + 1, y + PANEL_H, ui.borderColor());
        g.fill(x + PANEL_W - 1, y, x + PANEL_W, y + PANEL_H, ui.borderColor());
        // Glow
        g.fill(x + 1, y + 1, x + PANEL_W - 1, y + 2, ui.glowColor());
    }

    /** Header: icon + "[Transmitter] (LV)" */
    protected void renderHeader(GuiGraphics g) {
        int x = leftPos + MARGIN_X;
        int y = topPos + HEADER_Y;
        INovaNode node = getNode();
        if (node == null) return;

        Component icon = getNodeIcon(node.getNodeType());
        Component title = Component.literal(icon.getString() + " ")
                .append(Component.translatable("eecore.nova." + node.getNodeType().name().toLowerCase()))
                .append(Component.literal(" (" + node.getTier().getShortName() + ")"));

        g.drawString(font, title, x, y, ui.headerColor(), false);
        String line = "═".repeat(PANEL_W);
        g.drawString(font, line, x, y + 12, ui.borderColor(), false);
    }

    /** Stats row: "Range: 12 blocks"  "Connected: 3/16" */
    protected void renderStatsRow(GuiGraphics g) {
        int x = leftPos + MARGIN_X;
        int y = topPos + STATS_Y;
        INovaNode node = getNode();
        if (node == null) return;

        String rangeText = node.getRange() + " blocks";
        String connText = getConnectionCount() + "/" + getMaxConnections();

        Component left = Component.literal("[ ")
                .append(Component.translatable("eecore.nova.gui.range", rangeText))
                .append(" ]");
        Component right = Component.literal("[ ")
                .append(Component.translatable("eecore.nova.gui.connected", connText))
                .append(" ]");

        g.drawString(font, left, x, y, ui.statsColor(), false);
        int rw = font.width(right);
        g.drawString(font, right, leftPos + PANEL_W - MARGIN_X - rw, y, ui.statsColor(), false);
    }

    /** Energy buffer progress bar. */
    protected void renderEnergyBar(GuiGraphics g) {
        int x = leftPos + MARGIN_X;
        int y = topPos + BUFFER_Y;
        long stored = getBufferEnergy();
        long cap = getBufferCapacity();
        double fill = cap > 0 ? (double) stored / cap : 0;

        // Background
        g.fill(x, y, x + BAR_W, y + BAR_H, ui.barBgColor());
        // Fill
        int fillW = (int)(BAR_W * fill);
        if (fillW > 0) {
            int barColor = fill > 0.8 ? ui.barFullColor() : ui.barNormalColor();
            g.fill(x, y, x + fillW, y + BAR_H, barColor);
        }
        // Label
        Component label = Component.translatable("eecore.nova.gui.buffer",
                fmt(stored), fmt(cap));
        int lw = font.width(label);
        g.drawString(font, label, x + (BAR_W - lw) / 2, y + 2, ui.barTextColor(), false);
    }

    /** Input/output rate bars. */
    protected void renderRateBars(GuiGraphics g) {
        int x = leftPos + MARGIN_X;
        long maxRate = Math.max(getInputRate(), getOutputRate());
        if (maxRate == 0) maxRate = 1;

        // Input rate
        int iy = topPos + INPUT_RATE_Y;
        double inFill = (double) getInputRate() / maxRate;
        int inW = (int)(BAR_W * inFill);
        g.fill(x, iy, x + BAR_W, iy + BAR_H, ui.barBgColor());
        if (inW > 0) g.fill(x, iy, x + inW, iy + BAR_H, ui.barInputColor());
        Component inLabel = Component.translatable("eecore.nova.gui.input_rate",
                fmt(getInputRate()));
        g.drawString(font, inLabel, x + 2, iy + 2, ui.barTextColor(), false);

        // Output rate
        int oy = topPos + OUTPUT_RATE_Y;
        double outFill = (double) getOutputRate() / maxRate;
        int outW = (int)(BAR_W * outFill);
        g.fill(x, oy, x + BAR_W, oy + BAR_H, ui.barBgColor());
        if (outW > 0) g.fill(x, oy, x + outW, oy + BAR_H, ui.barOutputColor());
        Component outLabel = Component.translatable("eecore.nova.gui.output_rate",
                fmt(getOutputRate()));
        g.drawString(font, outLabel, x + 2, oy + 2, ui.barTextColor(), false);
    }

    /**
     * Quick action buttons row.
     * Subclasses override to provide custom buttons.
     */
    protected void renderQuickButtons(GuiGraphics g) {
        int x = leftPos + MARGIN_X;
        int y = topPos + QUICK_BTN_Y;
        // Subclasses can draw their own buttons here
    }

    /** Connected nodes scrollable list. */
    protected void renderNodeList(GuiGraphics g) {
        int x = leftPos + MARGIN_X;
        int y = topPos + NODE_LIST_Y;
        List<NodeListEntry> nodes = getConnectedNodes();

        // Section label
        g.drawString(font, Component.translatable("eecore.nova.gui.connected_nodes"),
                x, y - 10, ui.headerColor(), false);

        // Render each entry (max visible = NODE_LIST_H / NODE_LIST_ITEM_H)
        int maxVisible = NODE_LIST_H / NODE_LIST_ITEM_H;
        for (int i = 0; i < Math.min(nodes.size(), maxVisible); i++) {
            NodeListEntry entry = nodes.get(i);
            int ey = y + i * NODE_LIST_ITEM_H;
            renderNodeListEntry(g, x, ey, entry);
        }
    }

    /** Single entry in the connected node list. */
    protected void renderNodeListEntry(GuiGraphics g, int x, int y, NodeListEntry entry) {
        String line = "• " + entry.name + " (" + entry.tier.getShortName() + ")"
                + " - " + entry.distance + " blocks"
                + " - " + fmt(entry.flowRate) + "Ω/t";
        g.drawString(font, line, x, y, ui.statsColor(), false);
    }

    /** Footer with action buttons. */
    protected void renderFooter(GuiGraphics g) {
        int x = leftPos + MARGIN_X;
        int y = topPos + FOOTER_Y;
        // Subclasses override
    }

    // ============================================================
    //  工具方法 / Utilities
    // ============================================================

    protected Component getNodeIcon(NodeType type) {
        return switch (type) {
            case TRANSMITTER -> Component.literal("⚡");
            case RECEIVER -> Component.literal("📡");
            case HUB -> Component.literal("🌍");
            case RELAY -> Component.literal("🔷");
        };
    }

    // ============================================================
    //  数据记录 / Data records
    // ============================================================

    private static String fmt(long v) {
        if (v < 1000) return v + "Ω";
        double d = v; int idx = 0;
        String[] u = {"Ω","kΩ","MΩ","GΩ","TΩ","PΩ","EΩ"};
        while (d >= 1000 && idx < u.length - 1) { d /= 1000; idx++; }
        return String.format("%.1f%s", d, u[idx]);
    }

    /** Entry for the connected node list panel. */
    public record NodeListEntry(
            String name,
            VoltageTier tier,
            int distance,
            long flowRate
    ) {}
}
