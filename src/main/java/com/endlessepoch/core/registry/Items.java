package com.endlessepoch.core.registry;

import com.endlessepoch.core.EECore;
import com.endlessepoch.core.nova.item.LaserLinkCardItem;
import com.endlessepoch.core.nova.block.ScannerBoundaryBlock;
import com.endlessepoch.core.nova.item.MultiblockScannerItem;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

/**
 * Item registry for EECore.
 * Holds DeferredRegister and supplier entries for all mod items.
 * <p>
 * EECore 物品注册表，包含所有模组物品的延迟注册与供应器。
 */
public class Items {

    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(Registries.ITEM, EECore.MOD_ID);

    public static final Supplier<BlockItem> CREATIVE_GENERATOR_ITEM =
            ITEMS.register("creative_generator",
                    () -> new BlockItem(Blocks.CREATIVE_GENERATOR.get(),
                            new Item.Properties().stacksTo(64))
            );

    public static final Supplier<BlockItem> CREATIVE_CONSUMER_ITEM =
            ITEMS.register("creative_consumer",
                    () -> new BlockItem(Blocks.CREATIVE_CONSUMER.get(),
                            new Item.Properties().stacksTo(64))
            );

    public static final Supplier<BlockItem> TEST_TRANSMITTER_ITEM =
            ITEMS.register("test_transmitter",
                    () -> new BlockItem(Blocks.TEST_TRANSMITTER.get(),
                            new Item.Properties().stacksTo(64))
            );

    public static final Supplier<LaserLinkCardItem> LASER_LINK_CARD =
            ITEMS.register("laser_link_card",
                    () -> new LaserLinkCardItem(new Item.Properties().stacksTo(1))
            );

    public static final Supplier<BlockItem> SCANNER_CONTROLLER_ITEM =
            ITEMS.register("scanner_controller",
                    () -> new BlockItem(Blocks.SCANNER_CONTROLLER.get(),
                            new Item.Properties().stacksTo(64))
            );

    public static final Supplier<BlockItem> SCANNER_BOUNDARY_ITEM =
            ITEMS.register("scanner_boundary",
                    () -> new ScannerBoundaryBlock.Item(Blocks.SCANNER_BOUNDARY.get(),
                            new Item.Properties().stacksTo(64))
            );


    public static final Supplier<MultiblockScannerItem> MULTIBLOCK_SCANNER =
            ITEMS.register("multiblock_scanner",
                    () -> new MultiblockScannerItem(new Item.Properties().stacksTo(1))
            );
}