package com.endlessepoch.core.registry;

import com.endlessepoch.core.EECore;
import com.endlessepoch.core.nova.block.part.PartBlock;
import com.endlessepoch.core.nova.item.LaserLinkCardItem;
import com.endlessepoch.core.nova.block.ScannerBoundaryBlock;
import com.endlessepoch.core.nova.block.MachineControllerItem;
import com.endlessepoch.core.nova.item.MultiblockScannerItem;
import com.endlessepoch.core.nova.item.WrenchItem;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * Item registry for EECore.
 * Holds DeferredRegister and supplier entries for all mod items.
 * Model/tag generation is delegated to {@link ResourceGenerator}.
 * <p>
 * EECore 物品注册表，包含所有模组物品的延迟注册与供应器。
 * 模型/标签生成委托给 ResourceGenerator。
 */
public class Items {

    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(Registries.ITEM, EECore.MOD_ID);

    /** Accumulate block IDs per tag, flushed later by ResourceGenerator.flushTags(). / 收集方块ID。 */
    public static final java.util.Map<String, java.util.LinkedHashSet<String>> TAG_BLOCKS = new java.util.LinkedHashMap<>();
    static void addToTag(String tag, String blockId) {
        TAG_BLOCKS.computeIfAbsent(tag, k -> new java.util.LinkedHashSet<>()).add("eecore:" + blockId);
    }
    static void addToTagNs(String tag, String ns, String id) {
        TAG_BLOCKS.computeIfAbsent(tag, k -> new java.util.LinkedHashSet<>()).add(ns + ":" + id);
    }
    /** Accumulate item IDs per tag, flushed later by ResourceGenerator.flushItemTags(). / 收集物品ID。 */
    public static final java.util.Map<String, java.util.LinkedHashSet<String>> TAG_ITEMS = new java.util.LinkedHashMap<>();
    static void addToItemTag(String tag, String itemId) {
        TAG_ITEMS.computeIfAbsent(tag, k -> new java.util.LinkedHashSet<>()).add("eecore:" + itemId);
    }
    static void addToItemTagNs(String tag, String ns, String id) {
        TAG_ITEMS.computeIfAbsent(tag, k -> new java.util.LinkedHashSet<>()).add(ns + ":" + id);
    }

    // Basic blocks / 基础方块

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
                    () -> new com.endlessepoch.core.nova.block.ControllerItem((com.endlessepoch.core.nova.block.ScannerControllerBlock)Blocks.SCANNER_CONTROLLER.get(),
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

    public static final Supplier<WrenchItem> WRENCH =
            ITEMS.register("wrench", () -> new WrenchItem(new Item.Properties()));

    // Tools / 工具

    public static final Supplier<Item> HAMMER = ITEMS.register("hammer",
            () -> new Item(new Item.Properties()));
    public static final Supplier<Item> FILE = ITEMS.register("file",
            () -> new Item(new Item.Properties()));
    public static final Supplier<Item> WIRE_CUTTER = ITEMS.register("wire_cutter",
            () -> new Item(new Item.Properties()));
    public static final Supplier<Item> CROWBAR = ITEMS.register("crowbar",
            () -> new Item(new Item.Properties()));
    public static final Supplier<Item> SAW = ITEMS.register("saw",
            () -> new Item(new Item.Properties()));
    public static final Supplier<Item> SCREWDRIVER = ITEMS.register("screwdriver",
            () -> new Item(new Item.Properties()));

    // Multiblock parts / 多方块部件

    /** All registered part items — used for creative tab population / 所有已注册部件物品。 */
    public static final List<Supplier<BlockItem>> PART_ITEMS = new ArrayList<>();

    /** Parts are now auto-registered via Blocks.flushPartItems() — items + models + lang all in one call. / 部件现由 flushPartItems 自动注册。 */

    /**
     * Register a part item with voltage-tier casing + custom overlay (for addon mods).
     * 注册部件物品（附属 Mod 用）。
     */
    public static Supplier<BlockItem> registerPartItem(Supplier<? extends Block> blockSupplier, String id, int tier, String overlayTex) {
        var sup = ITEMS.register(id,
                () -> new BlockItem(blockSupplier.get(), new Item.Properties().stacksTo(64)));
        ResourceGenerator.writePartModel(id, tier, overlayTex, "eecore");
        return sup;
    }

    // Machine controller / 机器控制器

    public static final Supplier<BlockItem> MACHINE_CONTROLLER_ITEM =
            ITEMS.register("machine_controller",
                    () -> new BlockItem(Blocks.MACHINE_CONTROLLER.get(),
                            new Item.Properties().stacksTo(64)));

    // Voltage-tier machine casings / 电压等级机器外壳

    public static final Supplier<BlockItem> ELV_MACHINE_CASING = registerCasingItem("elv");
    public static final Supplier<BlockItem> LV_MACHINE_CASING  = registerCasingItem("lv");
    public static final Supplier<BlockItem> MV_MACHINE_CASING  = registerCasingItem("mv");
    public static final Supplier<BlockItem> HV_MACHINE_CASING  = registerCasingItem("hv");
    public static final Supplier<BlockItem> EHV_MACHINE_CASING = registerCasingItem("ehv");
    public static final Supplier<BlockItem> UHV_MACHINE_CASING = registerCasingItem("uhv");
    public static final Supplier<BlockItem> PHV_MACHINE_CASING = registerCasingItem("phv");
    public static final Supplier<BlockItem> XHV_MACHINE_CASING = registerCasingItem("xhv");
    public static final Supplier<BlockItem> PLV_MACHINE_CASING = registerCasingItem("plv");
    public static final Supplier<BlockItem> SV_MACHINE_CASING  = registerCasingItem("sv");
    public static final Supplier<BlockItem> BV_MACHINE_CASING  = registerCasingItem("bv");
    public static final Supplier<BlockItem> QV_MACHINE_CASING  = registerCasingItem("qv");

    private static Supplier<BlockItem> registerCasingItem(String tier) {
        return ITEMS.register(tier + "_machine_casing",
                () -> new BlockItem(
                        java.util.Objects.requireNonNull(
                                getCasingBlock(tier), "Block not found for tier: " + tier),
                        new Item.Properties().stacksTo(64)));
    }

    private static Block getCasingBlock(String tier) {
        return switch (tier) {
            case "elv" -> Blocks.ELV_MACHINE_CASING.get();
            case "lv"  -> Blocks.LV_MACHINE_CASING.get();
            case "mv"  -> Blocks.MV_MACHINE_CASING.get();
            case "hv"  -> Blocks.HV_MACHINE_CASING.get();
            case "ehv" -> Blocks.EHV_MACHINE_CASING.get();
            case "uhv" -> Blocks.UHV_MACHINE_CASING.get();
            case "phv" -> Blocks.PHV_MACHINE_CASING.get();
            case "xhv" -> Blocks.XHV_MACHINE_CASING.get();
            case "plv" -> Blocks.PLV_MACHINE_CASING.get();
            case "sv"  -> Blocks.SV_MACHINE_CASING.get();
            case "bv"  -> Blocks.BV_MACHINE_CASING.get();
            case "qv"  -> Blocks.QV_MACHINE_CASING.get();
            default    -> null;
        };
    }

    // Dynamic machine items / 动态机器物品

    /** Machine items registered by MultiblockLoader. / 由 MultiblockLoader 注册的机器物品列表。 */
    public static final List<Supplier<Item>> MACHINE_ITEMS = new ArrayList<>();

    /**
     * Register a machine controller item + auto-generate block/item models.
     * Block model: tier casing body + machine overlay / 方块模型=电压外壳+机器面板
     * Item model:   tier casing body + 12x12 front panel / 物品模型=外壳体+面板
     */
    public static void registerMachineItem(String itemId, ResourceLocation machineId, String nameEn, String nameZh, int tier, String[] supportedTypes) {
        int modelIndex = com.endlessepoch.core.nova.block.MachineControllerBlock.allocateModelIndex(itemId);
        java.util.List<net.minecraft.resources.ResourceLocation> types = java.util.List.of();
        if (supportedTypes != null) {
            types = java.util.Arrays.stream(supportedTypes)
                    .map(s -> ResourceLocation.fromNamespaceAndPath(EECore.MOD_ID, s))
                    .toList();
        }
        final var finalTypes = types;
        var sup = ITEMS.register(itemId,
                () -> new MachineControllerItem(Blocks.MACHINE_CONTROLLER.get(),
                        new Item.Properties().stacksTo(64), machineId, nameEn, nameZh, modelIndex,
                        finalTypes));
        MACHINE_ITEMS.add(() -> sup.get());

        ResourceGenerator.writeMachineModel(itemId, tier);
    }
}
