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
                Codec.DOUBLE.optionalFieldOf("maxHeat", 10.0).forGetter(MachineRecipe::getMaxHeat),
                Codec.LONG.optionalFieldOf("energyPerTick", 0L).forGetter(MachineRecipe::getEnergyPerTick),
                Codec.INT.optionalFieldOf("maxParallel", 1).forGetter(MachineRecipe::getMaxParallel),
                Codec.INT.optionalFieldOf("circuit", 0).forGetter(MachineRecipe::getCircuit)
        ).apply(instance, MachineRecipe::new));

        // 8 fields exceed StreamCodec.composite overloads — encode/decode manually
        // 8 个字段超出 composite 重载上限，手写编解码
        this.streamCodec = StreamCodec.of(
                (buf, recipe) -> {
                    ByteBufCodecs.STRING_UTF8.encode(buf, recipe.getGroup());
                    Ingredient.CONTENTS_STREAM_CODEC.encode(buf, recipe.getIngredient());
                    ItemStack.STREAM_CODEC.apply(ByteBufCodecs.list()).encode(buf, recipe.results());
                    buf.writeVarInt(recipe.getProcessingTime());
                    ByteBufCodecs.STRING_UTF8.encode(buf, recipe.getRequiredTier().getShortName());
                    buf.writeDouble(recipe.getMaxHeat());
                    buf.writeVarLong(recipe.getEnergyPerTick());
                    buf.writeVarInt(recipe.getMaxParallel());
                    buf.writeVarInt(recipe.getCircuit());
                },
                buf -> {
                    String group = ByteBufCodecs.STRING_UTF8.decode(buf);
                    Ingredient ingredient = Ingredient.CONTENTS_STREAM_CODEC.decode(buf);
                    List<ItemStack> results = ItemStack.STREAM_CODEC.apply(ByteBufCodecs.list()).decode(buf);
                    int time = buf.readVarInt();
                    VoltageTier tier = VoltageTier.fromShortName(ByteBufCodecs.STRING_UTF8.decode(buf));
                    double maxHeat = buf.readDouble();
                    long energyPerTick = buf.readVarLong();
                    int maxParallel = buf.readVarInt();
                    int circuit = buf.readVarInt();
                    return new MachineRecipe(group, ingredient, results, time,
                            tier != null ? tier : VoltageTier.ELV, maxHeat, energyPerTick, maxParallel, circuit);
                }
        );
    }

    @Override
    public MapCodec<MachineRecipe> codec() { return codec; }

    @Override
    public StreamCodec<RegistryFriendlyByteBuf, MachineRecipe> streamCodec() { return streamCodec; }
}
