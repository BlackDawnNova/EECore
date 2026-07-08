package com.endlessepoch.core.registry;

import com.endlessepoch.core.EECore;
import com.endlessepoch.core.nova.item.LaserLinkCardItem;
import com.endlessepoch.core.nova.block.ScannerBoundaryBlock;
import com.endlessepoch.core.nova.block.MachineControllerItem;
import com.endlessepoch.core.nova.item.MultiblockScannerItem;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
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

    // Multiblock parts / 多方块部件
    public static final Supplier<BlockItem> INPUT_BUS = registerPartItem("input_bus", 1);
    public static final Supplier<BlockItem> OUTPUT_BUS = registerPartItem("output_bus", 1);
    public static final Supplier<BlockItem> INPUT_HATCH = registerPartItem("input_hatch", 1);
    public static final Supplier<BlockItem> OUTPUT_HATCH = registerPartItem("output_hatch", 1);
    public static final Supplier<BlockItem> INPUT_ASSEMBLY = registerPartItem("input_assembly", 1);
    public static final Supplier<BlockItem> OUTPUT_ASSEMBLY = registerPartItem("output_assembly", 1);

    /**
     * Register a part item with voltage-tier casing model. / 注册部件物品，使用电压外壳模型。
     */
    private static Supplier<BlockItem> registerPartItem(String id, int tier) {
        var sup = ITEMS.register(id,
                () -> new BlockItem(
                        java.util.Objects.requireNonNull(getPartBlock(id), "Part block not found: " + id),
                        new Item.Properties().stacksTo(64)));

        String casingName = com.endlessepoch.core.api.tier.VoltageTier.fromOrdinal(tier).name().toLowerCase();
        String casingTex = "eecore:block/casings/voltage/" + casingName + "/side";
        String overlayTex = "eecore:block/" + id; // overlay at textures/block/<id>.png

        // Block model / 方块模型
        String blockModel = "{\"parent\":\"eecore:block/ee_base_12_front_emissive\"," +
                "\"textures\":{" +
                "\"particle\":\"" + casingTex + "\"," +
                "\"all\":\"" + casingTex + "\"," +
                "\"front\":\"" + overlayTex + "\"," +
                "\"overlay_emissive\":\"" + overlayTex + "\"}}";
        writeJson("models/block", id, blockModel);

        // Blockstate / 方块状态
        String bs = "{\"variants\":{" +
                "\"facing=north\":{\"model\":\"eecore:block/" + id + "\",\"y\":0}," +
                "\"facing=east\":{\"model\":\"eecore:block/" + id + "\",\"y\":90}," +
                "\"facing=south\":{\"model\":\"eecore:block/" + id + "\",\"y\":180}," +
                "\"facing=west\":{\"model\":\"eecore:block/" + id + "\",\"y\":270}}}";
        writeJson("blockstates", id, bs);

        // Item model / 物品模型
        String itemModel = "{\"parent\":\"block/block\"," +
                "\"textures\":{" +
                "\"particle\":\"" + casingTex + "\"," +
                "\"all\":\"" + casingTex + "\"," +
                "\"front\":\"" + overlayTex + "\"}," +
                "\"elements\":[" +
                "{\"from\":[0,0,0.04],\"to\":[16,16,16]," +
                "\"faces\":{" +
                "\"down\":{\"uv\":[0,0,16,16],\"texture\":\"#all\"}," +
                "\"up\":{\"uv\":[0,0,16,16],\"texture\":\"#all\"}," +
                "\"north\":{\"uv\":[0,0,16,16],\"texture\":\"#all\"}," +
                "\"south\":{\"uv\":[0,0,16,16],\"texture\":\"#all\"}," +
                "\"west\":{\"uv\":[0,0,16,16],\"texture\":\"#all\"}," +
                "\"east\":{\"uv\":[0,0,16,16],\"texture\":\"#all\"}}}," +
                "{\"from\":[2,2,0],\"to\":[14,14,0.02]," +
                "\"faces\":{" +
                "\"north\":{\"uv\":[2,2,14,14],\"texture\":\"#front\"}}}]}";
        writeJson("models/item", id, itemModel);

        return sup;
    }

    private static Block getPartBlock(String id) {
        return switch (id) {
            case "input_bus" -> Blocks.INPUT_BUS.get();
            case "output_bus" -> Blocks.OUTPUT_BUS.get();
            case "input_hatch" -> Blocks.INPUT_HATCH.get();
            case "output_hatch" -> Blocks.OUTPUT_HATCH.get();
            case "input_assembly" -> Blocks.INPUT_ASSEMBLY.get();
            case "output_assembly" -> Blocks.OUTPUT_ASSEMBLY.get();
            default -> null;
        };
    }

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

    /** Machine items registered by MultiblockLoader. / 由 MultiblockLoader 注册的机器物品列表。 */
    public static final List<Supplier<Item>> MACHINE_ITEMS = new ArrayList<>();

    /**
     * Register a machine controller item + auto-generate block/item models.
     * Block model: tier casing body + machine overlay / 方块模型=电压外壳+机器面板
     * Item model:   tier casing body + 12x12 front panel / 物品模型=外壳体+面板
     */
    public static void registerMachineItem(String itemId, ResourceLocation machineId, String nameEn, String nameZh, int tier) {
        int modelIndex = com.endlessepoch.core.nova.block.MachineControllerBlock.allocateModelIndex(itemId);
        var sup = ITEMS.register(itemId,
                () -> new MachineControllerItem(Blocks.MACHINE_CONTROLLER.get(),
                        new Item.Properties().stacksTo(64), machineId, nameEn, nameZh, modelIndex));
        MACHINE_ITEMS.add(() -> sup.get());

        // Casing name from tier / 电压等级→外壳名
        String casingName = com.endlessepoch.core.api.tier.VoltageTier.fromOrdinal(tier).name().toLowerCase();
        String casingTex = "eecore:block/casings/voltage/" + casingName + "/side";
        String overlayFront = "eecore:block/machines/" + itemId + "/overlay_front";

        // Block model / 方块模型
        String blockModel = "{\"parent\":\"eecore:block/ee_base_12_front_emissive\"," +
            "\"textures\":{" +
            "\"particle\":\"" + casingTex + "\"," +
            "\"all\":\"" + casingTex + "\"," +
            "\"front\":\"" + overlayFront + "\"," +
            "\"overlay_emissive\":\"" + overlayFront + "_e\"}}";
        writeJson("models/block/machines/" + itemId, "controller", blockModel);

        // Register emissive for ALL machine_controller variants / 注册发光渲染（按方块ID匹配）
        com.endlessepoch.core.api.client.EmissiveHelper.registerEmissiveModel(
                "eecore:machine_controller",
                "eecore:block/machines/" + itemId + "/overlay_front_e");

        // Item model / 物品模型
        String itemModel = "{\"parent\":\"block/block\"," +
            "\"textures\":{" +
            "\"particle\":\"" + casingTex + "\"," +
            "\"all\":\"" + casingTex + "\"," +
            "\"front\":\"" + overlayFront + "\"}," +
            "\"elements\":[" +
            "{\"from\":[0,0,0.04],\"to\":[16,16,16]," +
            "\"faces\":{" +
            "\"down\":{\"uv\":[0,0,16,16],\"texture\":\"#all\"}," +
            "\"up\":{\"uv\":[0,0,16,16],\"texture\":\"#all\"}," +
            "\"north\":{\"uv\":[0,0,16,16],\"texture\":\"#all\"}," +
            "\"south\":{\"uv\":[0,0,16,16],\"texture\":\"#all\"}," +
            "\"west\":{\"uv\":[0,0,16,16],\"texture\":\"#all\"}," +
            "\"east\":{\"uv\":[0,0,16,16],\"texture\":\"#all\"}}}," +
            "{\"from\":[2,2,0],\"to\":[14,14,0.02]," +
            "\"faces\":{" +
            "\"north\":{\"uv\":[2,2,14,14],\"texture\":\"#front\"}}}]}";
        writeJson("models/item", itemId, itemModel);

        // Update blockstate / 更新 blockstate
        writeBlockstate();
    }

    /** Rebuild blockstate with per-machine model refs for all 0-31 indices. / 为所有 0-31 索引重建含每机器模型引用的 blockstate。 */
    private static void writeBlockstate() {
        var indices = com.endlessepoch.core.nova.block.MachineControllerBlock.getModelIndices();
        StringBuilder sb = new StringBuilder("{\n  \"variants\": {\n");
        for (int m = 0; m <= 31; m++) {
            String ref = modelRefForIndex(m, indices);
            sb.append("    \"facing=north,model=").append(m).append("\": { \"model\": \"").append(ref).append("\", \"y\": 0 },\n");
            sb.append("    \"facing=east,model=").append(m).append("\":  { \"model\": \"").append(ref).append("\", \"y\": 90 },\n");
            sb.append("    \"facing=south,model=").append(m).append("\": { \"model\": \"").append(ref).append("\", \"y\": 180 },\n");
            sb.append("    \"facing=west,model=").append(m).append("\":  { \"model\": \"").append(ref).append("\", \"y\": 270 }");
            if (m < 31) sb.append(",\n\n");
            else sb.append("\n");
        }
        sb.append("  }\n}");
        writeJson("blockstates", "machine_controller", sb.toString());
    }

    private static String modelRefForIndex(int idx, java.util.Map<String, Integer> indices) {
        if (idx == 0 || idx == 3) return "eecore:block/machine_controller";
        if (idx == 1) return "eecore:block/ee_base_12";
        if (idx == 2) return "eecore:block/ee_base_16";
        for (var e : indices.entrySet())
            if (e.getValue() == idx)
                return "eecore:block/machines/" + e.getKey() + "/controller";
        return "eecore:block/machine_controller";
    }

    /** Write a JSON file to both src/ and build/resources/. / 写入 JSON 到 src 和 build。 */
    private static void writeJson(String subPath, String fileName, String json) {
        for (String base : new String[]{"src/main/resources", "build/resources/main"}) {
            try {
                var d = java.nio.file.Path.of(base, "assets", "eecore", subPath);
                java.nio.file.Files.createDirectories(d);
                java.nio.file.Files.writeString(d.resolve(fileName + ".json"), json);
            } catch (Exception ignored) {}
        }
    }
}