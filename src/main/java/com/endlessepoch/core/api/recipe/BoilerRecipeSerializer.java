package com.endlessepoch.core.api.recipe;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.neoforged.neoforge.fluids.FluidStack;

public class BoilerRecipeSerializer implements RecipeSerializer<BoilerRecipe> {

    private static final MapCodec<BoilerRecipe> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Codec.STRING.optionalFieldOf("group", "").forGetter(BoilerRecipe::getGroup),
            FluidStack.CODEC.fieldOf("inputFluid").forGetter(BoilerRecipe::getInputFluid),
            FluidStack.CODEC.fieldOf("outputFluid").forGetter(BoilerRecipe::getOutputFluid),
            Codec.INT.optionalFieldOf("duration", 200).forGetter(BoilerRecipe::getDuration)
    ).apply(instance, BoilerRecipe::new));

    private static final StreamCodec<RegistryFriendlyByteBuf, BoilerRecipe> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.STRING_UTF8, BoilerRecipe::getGroup,
                    FluidStack.STREAM_CODEC, BoilerRecipe::getInputFluid,
                    FluidStack.STREAM_CODEC, BoilerRecipe::getOutputFluid,
                    ByteBufCodecs.VAR_INT, BoilerRecipe::getDuration,
                    BoilerRecipe::new
            );

    @Override
    public MapCodec<BoilerRecipe> codec() { return CODEC; }

    @Override
    public StreamCodec<RegistryFriendlyByteBuf, BoilerRecipe> streamCodec() { return STREAM_CODEC; }
}
