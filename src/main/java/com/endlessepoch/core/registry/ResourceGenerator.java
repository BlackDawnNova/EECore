package com.endlessepoch.core.registry;

import com.endlessepoch.core.api.client.EmissiveHelper;
import com.endlessepoch.core.api.tier.VoltageTier;
import com.endlessepoch.core.nova.block.MachineControllerBlock;
import com.endlessepoch.core.nova.block.part.PartBlock;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;

/**
 * Generates block models, blockstates, item models, and block-tag JSON at registration time.
 * Writes to both {@code src/main/resources} and {@code build/resources/main} so IDE and runtime
 * both see generated assets.
 * <p>
 * 在注册时自动生成方块模型、方块状态、物品模型和方块标签 JSON。
 * 同时写入 src/main/resources 和 build/resources/main，确保 IDE 和运行时都能看到生成的资源。
 */
public class ResourceGenerator {

    /**
     * Project root resolved from working directory. gameDirectory=run → root is parent.
     * 项目根目录，gameDirectory 在 run/ 下时回退到父目录。
     */
    static final java.nio.file.Path PROJECT_ROOT;
    static {
        java.nio.file.Path cwd = java.nio.file.Path.of("").toAbsolutePath();
        if (cwd.endsWith("run") && java.nio.file.Files.exists(cwd.resolve("../build.gradle"))) {
            PROJECT_ROOT = cwd.resolve("..").normalize().toAbsolutePath();
        } else {
            PROJECT_ROOT = cwd;
        }
    }

    /**
     * Check if an emissive texture variant exists on disk. / 检查发光贴图是否存在。
     * overlayTex "eecore:block/parts/input_bus/overlay_front"
     * → checks "assets/eecore/textures/block/parts/input_bus/overlay_front_e.png"
     */
    static boolean hasEmissiveTexture(String overlayTex) {
        int colon = overlayTex.indexOf(':');
        if (colon < 0) return false;
        String ns = overlayTex.substring(0, colon);
        String tex = overlayTex.substring(colon + 1);
        String emissivePath = "assets/" + ns + "/textures/" + tex + "_e.png";
        for (String base : new String[]{"src/main/resources", "build/resources/main"}) {
            if (java.nio.file.Files.exists(PROJECT_ROOT.resolve(base).resolve(emissivePath)))
                return true;
        }
        return false;
    }

    /**
     * Write a JSON file to both src/ and build/resources/. / 写入 JSON 到 src 和 build。
     */
    static void writeJson(String subPath, String fileName, String json) {
        for (String base : new String[]{"src/main/resources", "build/resources/main"}) {
            try {
                var d = PROJECT_ROOT.resolve(base).resolve("assets").resolve("eecore")
                        .resolve(java.nio.file.Path.of("", subPath));
                java.nio.file.Files.createDirectories(d);
                java.nio.file.Files.writeString(d.resolve(fileName + ".json"), json);
            } catch (Exception ignored) {}
        }
    }

    /**
     * Rebuild blockstate with per-machine model refs for all 0-31 indices.
     * 为所有 0-31 索引重建含每机器模型引用的 blockstate。
     */
    static void writeBlockstate() {
        var indices = MachineControllerBlock.getModelIndices();
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

    private static String modelRefForIndex(int idx, Map<String, Integer> indices) {
        if (idx == 0 || idx == 3) return "eecore:block/machine_controller";
        if (idx == 1) return "eecore:block/ee_base_12";
        if (idx == 2) return "eecore:block/ee_base_16";
        for (var e : indices.entrySet())
            if (e.getValue() == idx)
                return "eecore:block/machines/" + e.getKey() + "/controller";
        return "eecore:block/machine_controller";
    }

    /**
     * Generate block model + blockstate + item model for a multiblock part.
     * 为多方块部件生成方块模型+方块状态+物品模型。
     */
    static void writePartModel(String id, int tier, String overlayTex) {
        String casingName = VoltageTier.fromOrdinal(tier).name().toLowerCase();
        String casingTex = "eecore:block/casings/voltage/" + casingName + "/side";

        // Block model — auto-select emissive parent if _e texture exists / 自动检测发光贴图选父模型
        boolean hasE = hasEmissiveTexture(overlayTex);
        String parent = hasE ? "eecore:block/ee_base_12_front_emissive" : "eecore:block/ee_base_12";
        String blockModel = "{\"parent\":\"" + parent + "\"," +
                "\"textures\":{" +
                "\"particle\":\"" + casingTex + "\"," +
                "\"all\":\"" + casingTex + "\"," +
                "\"front\":\"" + overlayTex + "\"";
        if (hasE) blockModel += ",\"overlay_emissive\":\"" + overlayTex + "_e\"";
        blockModel += "}}";
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

        // Tool tier tag / 工具等级标签
        Items.addToTag(PartBlock.toolTagForTier(tier), id);
    }

    /**
     * Generate block model + item model + update blockstate for a machine controller.
     * 为机器控制器生成方块模型+物品模型+更新 blockstate。
     */
    static void writeMachineModel(String itemId, int tier) {
        String casingName = VoltageTier.fromOrdinal(tier).name().toLowerCase();
        String casingTex = "eecore:block/casings/voltage/" + casingName + "/side";
        String overlayFront = "eecore:block/machines/" + itemId + "/overlay_front";

        // Block model — auto-select emissive parent if _e texture exists / 自动检测发光贴图选父模型
        boolean hasE = hasEmissiveTexture(overlayFront);
        String parent = hasE ? "eecore:block/ee_base_12_front_emissive" : "eecore:block/ee_base_12";
        String blockModel = "{\"parent\":\"" + parent + "\"," +
            "\"textures\":{" +
            "\"particle\":\"" + casingTex + "\"," +
            "\"all\":\"" + casingTex + "\"," +
            "\"front\":\"" + overlayFront + "\"";
        if (hasE) blockModel += ",\"overlay_emissive\":\"" + overlayFront + "_e\"";
        blockModel += "}}";
        writeJson("models/block/machines/" + itemId, "controller", blockModel);

        // Register emissive if _e texture exists / 有发光贴图才注册
        if (hasE) {
            EmissiveHelper.registerEmissiveModel(
                    "eecore:machine_controller",
                    "eecore:block/machines/" + itemId + "/overlay_front_e");
        }

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

    /**
     * Flush accumulated block tags to data/{ns}/tags/block/ JSON.
     * 将累积的方块标签写入 data/{ns}/tags/block/ JSON。
     */
    public static void flushTags(Map<String, LinkedHashSet<String>> tagBlocks) {
        for (var e : tagBlocks.entrySet()) {
            String[] parts = e.getKey().split(":", 2);
            String ns = parts[0], path = parts.length > 1 ? parts[1] : parts[0];
            var sb = new StringBuilder("{\"replace\":false,\"values\":[");
            boolean first = true;
            for (String id : e.getValue()) {
                if (!first) sb.append(",");
                sb.append("\"").append(id).append("\"");
                first = false;
            }
            sb.append("]}");
            for (String base : new String[]{"src/main/resources", "build/resources/main"}) {
                try {
                    var d = PROJECT_ROOT.resolve(base).resolve("data").resolve(ns)
                            .resolve("tags").resolve("block");
                    java.nio.file.Files.createDirectories(d);
                    java.nio.file.Files.writeString(d.resolve(path + ".json"), sb.toString());
                } catch (Exception ignored) {}
            }
        }
    }
}
