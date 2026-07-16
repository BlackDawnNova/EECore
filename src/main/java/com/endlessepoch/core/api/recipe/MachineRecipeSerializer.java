package com.endlessepoch.core.api.recipe;

import com.endlessepoch.core.api.tier.VoltageTier;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeSerializer;

import java.util.List;

/**
 * Codec-based serializer for {@link MachineRecipe}.
 * Accepts both single {@code "result"} and array {@code "results"} JSON formats.
 * <p>
 * 基于 Codec 的 MachineRecipe 序列化器，兼容单 "result" 和数组 "results"。
 */
public class MachineRecipeSerializer implements RecipeSerializer<MachineRecipe> {

    private static final Codec<List<ItemStack>> RESULTS_CODEC = Codec.withAlternative(
            ItemStack.SINGLE_ITEM_CODEC.listOf(),
            ItemStack.SINGLE_ITEM_CODEC.xmap(List::of, list -> list.getFirst())
    );
    private static final MapCodec<List<ItemStack>> RESULTS_MAP_CODEC =
            RESULTS_CODEC.fieldOf("results");

    private static final Codec<VoltageTier> TIER_CODEC = Codec.STRING.xmap(
            s -> {
                VoltageTier t = VoltageTier.fromShortName(s);
                return t != null ? t : VoltageTier.ELV;
            },
            VoltageTier::getShortName
    );

    private final MapCodec<MachineRecipe> codec;
    private final StreamCodec<RegistryFriendlyByteBuf, MachineRecipe> streamCodec;

    public MachineRecipeSerializer() {
        this.codec = RecordCodecBuilder.mapCodec(instance -> instance.group(
                Codec.STRING.optionalFieldOf("group", "").forGetter(MachineRecipe::getGroup),
                Ingredient.CODEC_NONEMPTY.fieldOf("ingredient").forGetter(MachineRecipe::getIngredient),
                RESULTS_MAP_CODEC.forGetter(MachineRecipe::results),
                Codec.INT.optionalFieldOf("processingTime", 200).forGetter(MachineRecipe::getProcessingTime),
                TIER_CODEC.optionalFieldOf("requiredTier", VoltageTier.ELV).forGetter(MachineRecipe::getRequiredTier),
                Codec.DOUBLE.optionalFieldOf("maxHeat", 10.0).forGetter(MachineRecipe::getMaxHeat)
        ).apply(instance, MachineRecipe::new));

        this.streamCodec = StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8, MachineRecipe::getGroup,
                Ingredient.CONTENTS_STREAM_CODEC, MachineRecipe::getIngredient,
                ItemStack.STREAM_CODEC.apply(ByteBufCodecs.list()), MachineRecipe::results,
                ByteBufCodecs.VAR_INT, MachineRecipe::getProcessingTime,
                ByteBufCodecs.STRING_UTF8.map(
                        s -> { VoltageTier t = VoltageTier.fromShortName(s); return t != null ? t : VoltageTier.ELV; },
                        VoltageTier::getShortName
                ), MachineRecipe::getRequiredTier,
                ByteBufCodecs.DOUBLE, MachineRecipe::getMaxHeat,
                MachineRecipe::new
        );
    }

    @Override
    public MapCodec<MachineRecipe> codec() { return codec; }

    @Override
    public StreamCodec<RegistryFriendlyByteBuf, MachineRecipe> streamCodec() { return streamCodec; }
}
