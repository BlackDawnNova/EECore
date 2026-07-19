package com.endlessepoch.core.integration;

import com.endlessepoch.core.EECore;
import com.endlessepoch.core.api.recipe.MachineRecipe;
import com.endlessepoch.core.api.tier.VoltageTier;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.AbstractRecipeCategory;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Blocks;

/**
 * JEI recipe category — upper recipe slots + lower energy info.
 * <p>
 * JEI 配方分类——上方配方区，下方能量信息区。
 */
public class MachineRecipeCategory extends AbstractRecipeCategory<MachineRecipe> {

    public static final RecipeType<MachineRecipe> TYPE =
            RecipeType.create(EECore.MOD_ID, "machine", MachineRecipe.class);

    private static final int W = 168, H = 96;
    private static final int BAR_X = 66, BAR_Y = 19;
    private static final int BAR_W = 20, BAR_H = 20;
    private static final int TEX_W = 20, TEX_H = 40;
    private static final int WHITE = 0xFFFFFFFF;
    private static final VoltageTier DEFAULT_TIER = VoltageTier.ELV;

    private static final ResourceLocation BAR_TEX =
            ResourceLocation.fromNamespaceAndPath(EECore.MOD_ID, "textures/gui/jei/progress_bar_slice.png");

    private static final int[][] OUT_POS = {
        {108, 4}, {126, 4}, {144, 4},
        {108, 22}
    };

    private final IDrawable slotBg;

    public MachineRecipeCategory(IGuiHelper guiHelper) {
        super(TYPE, Component.translatable("eecore.jei.machine_category"),
                guiHelper.createDrawableItemLike(Blocks.FURNACE), W, H);
        this.slotBg = guiHelper.getSlotDrawable();
    }

    @Override
    public void setRecipe(IRecipeLayoutBuilder builder, MachineRecipe recipe, IFocusGroup focuses) {
        builder.addSlot(RecipeIngredientRole.INPUT, 20, 20)
                .setBackground(slotBg, -1, -1)
                .addIngredients(recipe.getIngredient());

        var results = recipe.getResults();
        for (int i = 0; i < OUT_POS.length; i++) {
            var slot = builder.addSlot(RecipeIngredientRole.OUTPUT, OUT_POS[i][0], OUT_POS[i][1])
                    .setBackground(slotBg, -1, -1);
            if (i < results.size()) slot.addItemStack(results.get(i));
        }
    }

    @Override
    public void draw(MachineRecipe recipe, IRecipeSlotsView view, GuiGraphics g,
                     double mouseX, double mouseY) {
        int ticks = Math.max(recipe.getProcessingTime(), 1);
        long tick = (System.currentTimeMillis() / 50) % ticks;
        float frac = (float) tick / ticks;
        int visibleW = Math.max(0, (int)(BAR_W * frac));

        g.blit(BAR_TEX, BAR_X, BAR_Y, 0, 0, BAR_W, BAR_H, TEX_W, TEX_H);
        if (visibleW > 0)
            g.blit(BAR_TEX, BAR_X, BAR_Y, 0, BAR_H, visibleW, BAR_H, TEX_W, TEX_H);

        var font = net.minecraft.client.Minecraft.getInstance().font;
        String duration = String.format("%.2f 秒", ticks / 20f);
        g.drawString(font, "耗时: " + duration, 6, 63, WHITE);
        // Energy: base tier values; overclock shown in-game only / 显示基础电压下能耗，超频只在游戏内生效
        if (recipe.getEnergyPerTick() > 0) {
            long total = recipe.getEnergyPerTick() * recipe.getProcessingTime();
            g.drawString(font, "总计: " + total + " Ω", 6, 75, WHITE);
            g.drawString(font, "功率: " + recipe.getEnergyPerTick() + " Ω/t", 6, 87, WHITE);
        } else {
            g.drawString(font, "总计: -- Ω", 6, 75, WHITE);
            g.drawString(font, "功率: -- Ω/t", 6, 87, WHITE);
        }

        VoltageTier tier = recipe.getRequiredTier() != null ? recipe.getRequiredTier() : DEFAULT_TIER;
        String tierText = tier.name();
        int tierColor = parseHex(tier.getHexColor());
        int tw = font.width(tierText);
        g.drawString(font, tierText, W - tw - 6, 87, tierColor);
        if (recipe.getCircuit() > 0) {
            int cx = W - 6, cy = 63, n = recipe.getCircuit();
            String ns = String.valueOf(n);
            g.fill(cx - font.width(ns) - 6, cy, cx, cy + 10, 0xFF_886644);
            g.drawString(font, ns, cx - font.width(ns) - 3, cy + 1, 0xFF_FFD700);
        }
    }

    @Override
    public ResourceLocation getRegistryName(MachineRecipe recipe) {
        // Unique ID from ingredient hash + time for bookmark / 收藏用唯一ID
        String key = recipe.getIngredient().hashCode() + "_" + recipe.getProcessingTime();
        return ResourceLocation.fromNamespaceAndPath(EECore.MOD_ID, "machine/" + key);
    }

    private static int parseHex(String hex) {
        try { return (int) Long.parseLong(hex.substring(1), 16) | 0xFF000000; }
        catch (Exception e) { return WHITE; }
    }
}
