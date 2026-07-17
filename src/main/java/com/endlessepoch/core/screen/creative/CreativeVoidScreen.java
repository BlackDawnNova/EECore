package com.endlessepoch.core.screen.creative;

import com.endlessepoch.core.menu.creative.CreativeVoidMenu;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.extensions.common.IClientFluidTypeExtensions;
import net.neoforged.neoforge.fluids.FluidStack;

/**
 * Creative void GUI — part-style, no slots: swallowed entries render as a mixed
 * icon grid (item icons / fluid textures) with counts, plus a clear button.
 * Assemblies show items and fluids side by side.
 * 创造虚空 GUI——部件风格、无格子：吞噬记录以混合图标网格显示（物品图标/流体贴图）
 * + 数量 + 清空按钮。总成的物品与流体并列展示。
 */
public class CreativeVoidScreen extends CreativePartScreen<CreativeVoidMenu> {

    public CreativeVoidScreen(CreativeVoidMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
    }

    @Override
    protected void init() {
        super.init();
        addRenderableWidget(Button.builder(
                Component.translatable("eecore.gui.void.clear"), b -> clickButton(70))
                .bounds(leftPos + imageWidth - 52, topPos + 14, 44, 16).build());
    }

    @Override
    protected void renderContent(GuiGraphics g) {
        int items = 0, fluids = 0;

        // Item grid: 4×2, icon + count at its right / 物品网格：4×2，图标 + 右侧数量
        for (int i = 0; i < CreativeVoidMenu.SHOWN; i++) {
            int id = menu.entryId(i);
            if (id < 0 || menu.entryFluid(i)) continue;
            if (items >= 8) break;
            int col = items % 4, row = items / 4;
            if (row >= 2) break;
            var item = BuiltInRegistries.ITEM.byId(id);
            if (item == net.minecraft.world.item.Items.AIR) continue;
            int x = leftPos + 10 + col * 40;
            int y = topPos + 36 + row * 18;
            g.renderItem(new ItemStack(item), x, y);
            g.drawString(font, fmt(menu.entryCount(i)), x + 18, y + 4, 0x404040, false);
            items++;
        }

        // Fluid lines: name + mB, below items / 流体行：名称 + mB，物品下方
        int fluidY = topPos + (items > 0 ? 36 + ((items - 1) / 4 + 1) * 18 + 2 : 36);
        for (int i = 0; i < CreativeVoidMenu.SHOWN && fluids < 4; i++) {
            int id = menu.entryId(i);
            if (id < 0 || !menu.entryFluid(i)) continue;
            var fluid = BuiltInRegistries.FLUID.byId(id);
            if (fluid == net.minecraft.world.level.material.Fluids.EMPTY) continue;
            drawFluidIcon(g, id, leftPos + 10, fluidY + fluids * 16);
            String name = fluid.getFluidType().getDescription().getString();
            g.drawString(font, name + " " + fmt(menu.entryCount(i)) + " mB",
                    leftPos + 28, fluidY + fluids * 16 + 4, 0x404040, false);
            fluids++;
        }

        if (items + fluids == 0) {
            g.drawCenteredString(font, Component.translatable("eecore.gui.void.empty").getString(),
                    leftPos + imageWidth / 2, topPos + 48, 0x808080);
        }
    }

    /** 16×16 fluid still-texture icon. / 16×16 流体静止贴图图标。 */
    private void drawFluidIcon(GuiGraphics g, int fluidId, int x, int y) {
        var fluid = BuiltInRegistries.FLUID.byId(fluidId);
        var st = new FluidStack(fluid, 1000);
        var tx = IClientFluidTypeExtensions.of(fluid).getStillTexture(st);
        if (tx == null) { g.fill(x, y, x + 16, y + 16, 0xFF_3355AA); return; }
        var sp = Minecraft.getInstance().getTextureAtlas(TextureAtlas.LOCATION_BLOCKS).apply(tx);
        int tint = IClientFluidTypeExtensions.of(fluid).getTintColor();
        RenderSystem.setShaderColor((tint >> 16 & 255) / 255f, (tint >> 8 & 255) / 255f, (tint & 255) / 255f, 1f);
        g.blit(x, y, 0, 16, 16, sp);
        RenderSystem.setShaderColor(1, 1, 1, 1);
    }

    /** Compact count: 1.2k / 3.4M. / 紧凑计数显示。 */
    private static String fmt(int n) {
        if (n >= 1_000_000) return String.format("%.1fM", n / 1_000_000.0);
        if (n >= 10_000) return String.format("%.1fk", n / 1_000.0);
        return String.valueOf(n);
    }
}
