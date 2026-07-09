package com.endlessepoch.core.nova.item;

import com.endlessepoch.core.nova.block.part.PartBlock;
import com.endlessepoch.core.nova.block.part.CasingBlock;
import com.endlessepoch.core.nova.block.MachineControllerBlock;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Creative wrench — fast on EECore blocks, useless on vanilla blocks.
 * 创造扳手——EECore 方块快速挖掘，原版方块挖不动。
 */
public class WrenchItem extends Item {

    private static final float WRENCH_SPEED = 100f;

    public WrenchItem(Properties properties) {
        super(properties.stacksTo(1).fireResistant());
    }

    @Override
    public boolean isCorrectToolForDrops(ItemStack stack, BlockState state) {
        return true;
    }

    @Override
    public float getDestroySpeed(ItemStack stack, BlockState state) {
        return isEECoreBlock(state.getBlock()) ? WRENCH_SPEED : 9.0f; // netherite speed on vanilla
    }

    private static boolean isEECoreBlock(Block block) {
        return block instanceof PartBlock
            || block instanceof CasingBlock
            || block instanceof MachineControllerBlock
            || block instanceof com.endlessepoch.core.block.creative.CreativeGeneratorBlock
            || block instanceof com.endlessepoch.core.block.creative.CreativeConsumerBlock
            || block instanceof com.endlessepoch.core.nova.block.TransmitterTestBlock
            || block instanceof com.endlessepoch.core.nova.block.ScannerControllerBlock
            || block instanceof com.endlessepoch.core.nova.block.ScannerBoundaryBlock;
    }
}
