package com.endlessepoch.core.registry;

import com.endlessepoch.core.EECore;
import com.endlessepoch.core.api.multiblock.PartType;
import com.endlessepoch.core.block.creative.CreativeConsumerBlock;
import com.endlessepoch.core.block.creative.CreativeGeneratorBlock;
import com.endlessepoch.core.nova.block.ScannerBoundaryBlock;
import com.endlessepoch.core.nova.block.MachineControllerBlock;
import com.endlessepoch.core.nova.block.ScannerControllerBlock;
import com.endlessepoch.core.nova.block.TransmitterTestBlock;
import com.endlessepoch.core.nova.block.part.CasingBlock;
import com.endlessepoch.core.nova.block.part.PartBlock;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

/**
 * Block registry for EECore.
 * Holds DeferredRegister and supplier entries for all mod blocks.
 * <p>
 * EECore 方块注册表，包含所有模组方块的延迟注册与供应器。
 */
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

    public static final Supplier<Block> MACHINE_CONTROLLER = BLOCKS.register(
            "machine_controller",
            () -> new MachineControllerBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_BLACK)
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

    // Voltage-tier machine casings / 电压等级机器外壳

    public static final Supplier<Block> ELV_MACHINE_CASING = registerCasing("elv", MapColor.COLOR_GRAY);
    public static final Supplier<Block> LV_MACHINE_CASING  = registerCasing("lv",  MapColor.COLOR_GREEN);
    public static final Supplier<Block> MV_MACHINE_CASING  = registerCasing("mv",  MapColor.COLOR_BLUE);
    public static final Supplier<Block> HV_MACHINE_CASING  = registerCasing("hv",  MapColor.COLOR_ORANGE);
    public static final Supplier<Block> EHV_MACHINE_CASING = registerCasing("ehv", MapColor.COLOR_PURPLE);
    public static final Supplier<Block> UHV_MACHINE_CASING = registerCasing("uhv", MapColor.COLOR_RED);
    public static final Supplier<Block> PHV_MACHINE_CASING = registerCasing("phv", MapColor.COLOR_CYAN);
    public static final Supplier<Block> XHV_MACHINE_CASING = registerCasing("xhv", MapColor.COLOR_YELLOW);
    public static final Supplier<Block> PLV_MACHINE_CASING = registerCasing("plv", MapColor.COLOR_PINK);
    public static final Supplier<Block> SV_MACHINE_CASING  = registerCasing("sv",  MapColor.SNOW);
    public static final Supplier<Block> BV_MACHINE_CASING  = registerCasing("bv",  MapColor.COLOR_BLACK);
    public static final Supplier<Block> QV_MACHINE_CASING  = registerCasing("qv",  MapColor.GOLD);

    private static Supplier<Block> registerCasing(String tier, MapColor color) {
        return BLOCKS.register(tier + "_machine_casing",
                () -> new CasingBlock(BlockBehaviour.Properties.of()
                        .mapColor(color)
                        .strength(2.0f)
                        .requiresCorrectToolForDrops(),
                        PartType.CASING));
    }

    // ===== Multiblock parts / 多方块部件 =====

    public static final Supplier<Block> INPUT_BUS = BLOCKS.register("input_bus",
            () -> new PartBlock(BlockBehaviour.Properties.of().strength(2.0f).noOcclusion(), PartType.INPUT_BUS));
    public static final Supplier<Block> OUTPUT_BUS = BLOCKS.register("output_bus",
            () -> new PartBlock(BlockBehaviour.Properties.of().strength(2.0f).noOcclusion(), PartType.OUTPUT_BUS));
    public static final Supplier<Block> INPUT_HATCH = BLOCKS.register("input_hatch",
            () -> new PartBlock(BlockBehaviour.Properties.of().strength(2.0f).noOcclusion(), PartType.INPUT_HATCH));
    public static final Supplier<Block> OUTPUT_HATCH = BLOCKS.register("output_hatch",
            () -> new PartBlock(BlockBehaviour.Properties.of().strength(2.0f).noOcclusion(), PartType.OUTPUT_HATCH));
    public static final Supplier<Block> INPUT_ASSEMBLY = BLOCKS.register("input_assembly",
            () -> new PartBlock(BlockBehaviour.Properties.of().strength(2.0f).noOcclusion(), PartType.INPUT_ASSEMBLY));
    public static final Supplier<Block> OUTPUT_ASSEMBLY = BLOCKS.register("output_assembly",
            () -> new PartBlock(BlockBehaviour.Properties.of().strength(2.0f).noOcclusion(), PartType.OUTPUT_ASSEMBLY));

}