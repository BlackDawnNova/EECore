package com.endlessepoch.core.integration;

import com.endlessepoch.core.EECore;
import com.endlessepoch.core.api.recipe.BoilerRecipe;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.IRecipeCategory;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

public class BoilerRecipeCategory implements IRecipeCategory<BoilerRecipe> {

    public static final RecipeType<BoilerRecipe> TYPE =
            RecipeType.create(EECore.MOD_ID, "boiler", BoilerRecipe.class);
    private final IDrawable icon;

    public BoilerRecipeCategory(IGuiHelper helper) {
        this.icon = helper.createDrawableIngredient(VanillaTypes.ITEM_STACK,
                new ItemStack(net.minecraft.world.level.block.Blocks.FURNACE));
    }

    @Override
    public RecipeType<BoilerRecipe> getRecipeType() { return TYPE; }

    @Override
    public Component getTitle() {
        return Component.translatable("eecore.jei.boiler");
    }

    @Override
    public int getWidth() { return 100; }

    @Override
    public int getHeight() { return 54; }

    @Override
    public IDrawable getIcon() { return icon; }

    @Override
    public void setRecipe(IRecipeLayoutBuilder builder, BoilerRecipe recipe, IFocusGroup focuses) {
        builder.addSlot(RecipeIngredientRole.INPUT, 1, 1)
                .addFluidStack(recipe.getInputFluid().getFluid(), recipe.getInputFluid().getAmount())
                .setFluidRenderer(recipe.getInputFluid().getAmount(), false, 16, 52);

        builder.addSlot(RecipeIngredientRole.OUTPUT, 83, 1)
                .addFluidStack(recipe.getOutputFluid().getFluid(), recipe.getOutputFluid().getAmount())
                .setFluidRenderer(recipe.getOutputFluid().getAmount(), false, 16, 52);
    }

    @Override
    public void draw(BoilerRecipe recipe, IRecipeSlotsView view, GuiGraphics g, double mx, double my) {
        g.drawString(net.minecraft.client.Minecraft.getInstance().font,
                recipe.getDuration() + " ticks", 30, 38, 0xFF808080);
    }
}
