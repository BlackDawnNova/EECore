package com.endlessepoch.core.mixin;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.feature.configurations.OreConfiguration;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import java.util.List;
import java.util.Set;

/** 从OreConfiguration剔除铁铜金钻原版矿 / Strip vanilla iron/copper/gold/diamond from OreConfiguration targets */
@Mixin(OreConfiguration.class)
public class OreConfigurationMixin {

    private static final Set<Block> EXCLUDED = Set.of(
        // Overworld / 主世界
        Blocks.COAL_ORE, Blocks.DEEPSLATE_COAL_ORE,
        Blocks.COPPER_ORE, Blocks.DEEPSLATE_COPPER_ORE,
        Blocks.IRON_ORE, Blocks.DEEPSLATE_IRON_ORE,
        Blocks.GOLD_ORE, Blocks.DEEPSLATE_GOLD_ORE,
        Blocks.REDSTONE_ORE, Blocks.DEEPSLATE_REDSTONE_ORE,
        Blocks.LAPIS_ORE, Blocks.DEEPSLATE_LAPIS_ORE,
        Blocks.DIAMOND_ORE, Blocks.DEEPSLATE_DIAMOND_ORE,
        Blocks.EMERALD_ORE, Blocks.DEEPSLATE_EMERALD_ORE,
        // Nether / 下界
        Blocks.NETHER_GOLD_ORE,
        Blocks.NETHER_QUARTZ_ORE
    );

    @ModifyVariable(method = "<init>", at = @At("HEAD"), index = 1)
    private static List<OreConfiguration.TargetBlockState> filterTargets(
            List<OreConfiguration.TargetBlockState> targetStates) {
        var filtered = targetStates.stream()
                .filter(t -> !EXCLUDED.contains(t.state.getBlock()))
                .toList();
        return filtered;
    }
}
