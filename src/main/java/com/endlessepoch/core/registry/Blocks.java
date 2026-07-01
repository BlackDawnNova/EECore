package com.endlessepoch.core.registry;

import com.endlessepoch.core.EECore;
import com.endlessepoch.core.block.creative.CreativeConsumerBlock;
import com.endlessepoch.core.block.creative.CreativeGeneratorBlock;
import com.endlessepoch.core.nova.block.ScannerBoundaryBlock;
import com.endlessepoch.core.nova.block.ScannerControllerBlock;
import com.endlessepoch.core.nova.block.TransmitterTestBlock;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

public class Blocks {
    public static final DeferredRegister<Block> BLOCKS =
            DeferredRegister.create(Registries.BLOCK, EECore.MOD_ID);

    public static final Supplier<Block> CREATIVE_GENERATOR = BLOCKS.register(
            "creative_generator",
            () -> new CreativeGeneratorBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_LIGHT_BLUE)
                    .strength(-1.0f, 3600000.0f)
                    .noLootTable()
                    .noOcclusion()
            )
    );

    public static final Supplier<Block> CREATIVE_CONSUMER = BLOCKS.register(
            "creative_consumer",
            () -> new CreativeConsumerBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_RED)
                    .strength(-1.0f, 3600000.0f)
                    .noLootTable()
                    .noOcclusion()
            )
    );

    public static final Supplier<Block> SCANNER_CONTROLLER = BLOCKS.register(
            "scanner_controller",
            () -> new ScannerControllerBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_BLACK)
                    .strength(2.0f)
                    .noOcclusion()
            )
    );

    public static final Supplier<Block> TEST_TRANSMITTER = BLOCKS.register(
            "test_transmitter",
            () -> new TransmitterTestBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_LIGHT_BLUE)
                    .strength(2.0f)
                    .noOcclusion()
            )
    );

    public static final Supplier<Block> SCANNER_BOUNDARY = BLOCKS.register(
            "scanner_boundary",
            () -> new ScannerBoundaryBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.STONE)
                    .strength(1.0f)
                    .noOcclusion()
                    .noCollission()
                    .replaceable()
                    .noLootTable()
            )
    );
}