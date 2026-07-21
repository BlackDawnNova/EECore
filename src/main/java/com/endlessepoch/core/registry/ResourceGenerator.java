package com.endlessepoch.core.registry;

import com.endlessepoch.core.api.client.EmissiveHelper;
import com.endlessepoch.core.api.multiblock.PartType;
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
    public static final java.nio.file.Path PROJECT_ROOT;
    static {
        String prop = System.getProperty("eecore.project.dir");
        if (prop != null && !prop.isBlank()) {
            PROJECT_ROOT = java.nio.file.Path.of(prop).toAbsolutePath();
        } else {
            java.nio.file.Path cwd = java.nio.file.Path.of("").toAbsolutePath();
            if (cwd.endsWith("run") && java.nio.file.Files.exists(cwd.resolve("../build.gradle"))) {
                PROJECT_ROOT = cwd.resolve("..").normalize().toAbsolutePath();
            } else {
                PROJECT_ROOT = cwd;
            }
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
        if (ResourceGenerator.class.getClassLoader().getResource(emissivePath) != null)
            return true;
        for (String base : new String[]{"src/main/resources", "build/resources/main"}) {
            if (java.nio.file.Files.exists(PROJECT_ROOT.resolve(base).resolve(emissivePath)))
                return true;
        }
        return false;
    }

    /**
     * Write a JSON file to both src/ and build/resources/ under given namespace. / 写入 JSON 到指定 namespace。
     */
    public static void writeJsonNs(String namespace, String subPath, String fileName, String json) {
        for (String base : new String[]{"src/main/resources", "build/resources/main"}) {
            try {
                var d = PROJECT_ROOT.resolve(base).resolve("assets").resolve(namespace)
                        .resolve(java.nio.file.Path.of("", subPath));
                java.nio.file.Files.createDirectories(d);
                java.nio.file.Files.writeString(d.resolve(fileName + ".json"), json);
            } catch (Exception e) {
                com.endlessepoch.core.EECore.LOGGER.warn(
                    "writeJsonNs failed: {}/{}/{}.json (PROJECT_ROOT={}) — {}",
                    namespace, subPath, fileName, PROJECT_ROOT, e.getMessage());
            }
        }
    }

    /** Write JSON to eecore namespace (legacy). / 写入 eecore 命名空间。 */
    static void writeJson(String subPath, String fileName, String json) {
        writeJsonNs("eecore", subPath, fileName, json);
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
    static void writePartModel(String id, int tier, String overlayTex, String namespace) {
        String casingName = VoltageTier.fromOrdinal(tier).name().toLowerCase();
        String casingTex = "eecore:block/casings/voltage/" + casingName + "/side";
        String modelId = namespace + ":" + id;

        boolean hasE = hasEmissiveTexture(overlayTex);
        String parent = hasE ? "eecore:block/ee_base_12_front_emissive" : "eecore:block/ee_base_12";
        String blockModel = "{\"parent\":\"" + parent + "\"," +
                "\"textures\":{" +
                "\"particle\":\"" + casingTex + "\"," +
                "\"all\":\"" + casingTex + "\"," +
                "\"front\":\"" + overlayTex + "\"";
        if (hasE) {
            blockModel += ",\"overlay_emissive\":\"" + overlayTex + "_e\"";
            EmissiveHelper.registerEmissiveModel(modelId, overlayTex + "_e");
        }
        blockModel += "}}";
        writeJsonNs(namespace, "models/block", id, blockModel);

        // Blockstate / 方块状态
        String bs = "{\"variants\":{" +
                "\"facing=north\":{\"model\":\"" + namespace + ":block/" + id + "\",\"y\":0}," +
                "\"facing=east\":{\"model\":\"" + namespace + ":block/" + id + "\",\"y\":90}," +
                "\"facing=south\":{\"model\":\"" + namespace + ":block/" + id + "\",\"y\":180}," +
                "\"facing=west\":{\"model\":\"" + namespace + ":block/" + id + "\",\"y\":270}}}";
        writeJsonNs(namespace, "blockstates", id, bs);

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
        writeJsonNs(namespace, "models/item", id, itemModel);

        // Tool tier tag / 工具等级标签
        Items.addToTag(PartBlock.toolTagForTier(tier), id);
    }

    /** Generate block + item models + update blockstate for a machine controller. / 生成方块+物品模型+更新 blockstate。 */
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

        String itemModel = "{\"parent\":\"eecore:item/ee_machine_item\"," +
            "\"textures\":{" +
            "\"all\":\"" + casingTex + "\"," +
            "\"front\":\"" + overlayFront + "\"}}";
        writeJson("models/item", itemId, itemModel);

        // Update blockstate / 更新 blockstate
        writeBlockstate();
    }

    /**
     * Write machine item model for addon mods (different namespace).
     * 为附属 mod 写入物品模型（不同命名空间）。
     */
    public static void writeMachineItemModel(String itemId, int tier, String overlayNs) {
        String casingName = com.endlessepoch.core.api.tier.VoltageTier.fromOrdinal(tier).name().toLowerCase();
        String casingTex = "eecore:block/casings/voltage/" + casingName + "/side";
        String overlayTex = overlayNs + ":block/machines/" + itemId + "/overlay_front";
        String json = "{\"parent\":\"eecore:item/ee_machine_item\"," +
            "\"textures\":{" +
            "\"all\":\"" + casingTex + "\"," +
            "\"front\":\"" + overlayTex + "\"}}";
        writeJson("models/item", itemId, json); // model file in eecore namespace, texture in overlayNs
    }

    /**
     * One-click: block model + blockstate + item model for an ore block.
     * Model uses 5 cube faces referencing 5 spot variants to stitch them all into atlas.
     * 矿块模型——5面引5种矿斑变体，全部缝入 atlas。
     */
    public static void writeOreModel(String namespace, String materialId) {
        String[] suffixes = {"dull_ore", "dull_ore_small", "fine_ore", "flint_ore", "diamond_ore"};
        String[] faces = {"down", "up", "north", "south", "west"};
        StringBuilder texJson = new StringBuilder("\"particle\":\"eecore:block/ores/ore_spots\"");
        StringBuilder faceJson = new StringBuilder();
        for (int i = 0; i < suffixes.length; i++) {
            String texKey = "spot" + i;
            String texPath = "eecore:block/ores/" + materialId + "_ore_" + i;
            texJson.append(",\"").append(texKey).append("\":\"").append(texPath).append("\"");
            if (faceJson.length() > 0) faceJson.append(",");
            faceJson.append("\"").append(faces[i]).append("\":{\"uv\":[0,0,16,16],\"texture\":\"#spot").append(i).append("\"}");
        }
        // 6th face reuses spot0 / 第六面复用 spot0
        faceJson.append(",\"east\":{\"uv\":[0,0,16,16],\"texture\":\"#spot0\"}");
        String blockModel = "{\"parent\":\"block/block\",\"textures\":{" + texJson + "},"
                + "\"elements\":[{\"from\":[0,0,0],\"to\":[16,16,16],\"faces\":{" + faceJson + "}}]}";
        writeJsonNs(namespace, "models/block", materialId + "_ore", blockModel);

        String bs = "{\"variants\":{\"\":{\"model\":\"" + namespace + ":block/" + materialId + "_ore\"}}}";
        writeJsonNs(namespace, "blockstates", materialId + "_ore", bs);

        String itemModel = "{\"parent\":\"" + namespace + ":block/" + materialId + "_ore\"}";
        writeJsonNs(namespace, "models/item", materialId + "_ore", itemModel);
    }

    /** 矿石世界生成JSON(双写src+build, 随jar发布) / Ore worldgen JSONs — src + build, shipped in jar */
    public static void writeOreWorldgen(String namespace, String materialId, String replaceTag,
                                         int veinSize, int count, int minY, int maxY,
                                         String vanillaFeature, String biomeTag) {
        String blockRef = namespace + ":" + materialId + "_ore";
        String featName = "ore_" + materialId;
        String cfg = "{\"type\":\"minecraft:ore\",\"config\":{\"size\":" + veinSize
                + ",\"discard_chance_on_air_exposure\":0.0,\"targets\":[{\"target\":"
                + "{\"predicate_type\":\"minecraft:tag_match\",\"tag\":\"" + replaceTag + "\"},"
                + "\"state\":{\"Name\":\"" + blockRef + "\"}}]}}";
        writeDataNs(namespace, "worldgen/configured_feature", featName, cfg);
        String plc = "{\"feature\":\"" + namespace + ":" + featName
                + "\",\"placement\":["
                + "{\"type\":\"minecraft:count\",\"count\":" + count + "},"
                + "{\"type\":\"minecraft:in_square\"},"
                + "{\"type\":\"minecraft:height_range\",\"height\":{\"type\":\"minecraft:uniform\","
                + "\"min_inclusive\":{\"absolute\":" + minY + "},\"max_inclusive\":{\"absolute\":" + maxY + "}}},"
                + "{\"type\":\"minecraft:biome\"}]}";
        writeDataNs(namespace, "worldgen/placed_feature", featName, plc);
        // Add our ore / 添加矿石
        String add = "{\"type\":\"neoforge:add_features\",\"biomes\":\"" + biomeTag + "\","
                + "\"features\":\"" + namespace + ":" + featName + "\",\"step\":\"underground_ores\"}";
        writeDataNs(namespace, "neoforge/biome_modifier", "add_" + featName, add);
        // TODO: remove vanilla counterpart / 原版移除待修复
        // remove_features causes "Unbound values" crash in 1.21.1 — needs research
    }

    /** Write a data JSON to both src/ and build/resources/. / 写入 data JSON 到 src 与 build。 */
    private static void writeDataNs(String namespace, String subPath, String fileName, String json) {
        for (String base : new String[]{"src/main/resources", "build/resources/main"}) {
            try {
                var d = PROJECT_ROOT.resolve(base).resolve("data").resolve(namespace)
                        .resolve(java.nio.file.Path.of("", subPath));
                java.nio.file.Files.createDirectories(d);
                java.nio.file.Files.writeString(d.resolve(fileName + ".json"), json);
            } catch (Exception e) {
                com.endlessepoch.core.EECore.LOGGER.warn(
                    "writeDataNs failed: {}/{}/{}.json (PROJECT_ROOT={}) — {}",
                    namespace, subPath, fileName, PROJECT_ROOT, e.getMessage());
            }
        }
    }

    /**
     * Simple lang writer — appends new key-value pairs to namespace's lang JSON.
     * Unlike flushLang, this is NOT PartType-aware; pure key→value append.
     * 简单翻译写入——追加键值到语言JSON，非PartType感知。
     */
    public static void flushTrans(String namespace, Map<String, String> enMap, Map<String, String> zhMap) {
        if (enMap.isEmpty() && zhMap.isEmpty()) return;
        for (String lang : new String[]{"en_us", "zh_cn"}) {
            Map<String, String> trans = lang.equals("en_us") ? enMap : zhMap;
            for (String base : new String[]{"src/main/resources", "build/resources/main"}) {
                try {
                    var f = PROJECT_ROOT.resolve(base).resolve("assets").resolve(namespace)
                            .resolve("lang").resolve(lang + ".json");
                    java.nio.file.Files.createDirectories(f.getParent());
                    var map = new LinkedHashMap<String, String>();
                    if (java.nio.file.Files.exists(f)) {
                        for (String line : java.nio.file.Files.readAllLines(f)) {
                            String trimmed = line.trim();
                            if (trimmed.startsWith("\"") && trimmed.contains("\": \"")) {
                                int kEnd = trimmed.indexOf('"', 1); if (kEnd < 0) continue;
                                String key = trimmed.substring(1, kEnd);
                                int vStart = trimmed.indexOf("\": \"") + 4;
                                int vEnd = trimmed.lastIndexOf('"');
                                if (vStart >= 4 && vEnd > vStart)
                                    map.put(key, trimmed.substring(vStart, vEnd));
                            }
                        }
                    }
                    for (var e : trans.entrySet())
                        map.putIfAbsent(e.getKey(), e.getValue());
                    var sb = new StringBuilder("{\n");
                    int count = 0;
                    for (var e : map.entrySet()) {
                        if (count > 0) sb.append(",\n");
                        sb.append("  \"").append(e.getKey()).append("\": \"")
                                .append(e.getValue().replace("\\", "\\\\").replace("\"", "\\\"")).append("\"");
                        count++;
                    }
                    sb.append("\n}\n");
                    java.nio.file.Files.writeString(f, sb.toString());
                } catch (Exception e) {
                    com.endlessepoch.core.EECore.LOGGER.warn("flushTrans {}/{}: {}", namespace, lang, e.getMessage());
                }
            }
        }
    }

    /**
     * Sync lang JSON for a given mod namespace.
     * Only touches keys listed in PartReg.TRANS_EN/ZH — removes keys for deregistered
     * parts, adds keys for newly registered ones. All other entries are left untouched.
     * <p>
     * 同步语言 JSON —— 只处理 PartReg 字典里有的 key：删已注销部件的、补新注册的。
     * 非 PartReg 管理的 entry 原封不动。
     */
    public static void flushLang(String namespace, java.nio.file.Path root) {
        Map<String, String> enMap = PartReg.TRANS_EN.getOrDefault(namespace, java.util.Collections.emptyMap());
        Map<String, String> zhMap = PartReg.TRANS_ZH.getOrDefault(namespace, java.util.Collections.emptyMap());
        if (enMap.isEmpty() && zhMap.isEmpty()) return;

        for (String lang : new String[]{"en_us", "zh_cn"}) {
            Map<String, String> trans = lang.equals("en_us") ? enMap : zhMap;
            for (String base : new String[]{"src/main/resources", "build/resources/main"}) {
                try {
                    var f = root.resolve(base).resolve("assets").resolve(namespace)
                            .resolve("lang").resolve(lang + ".json");
                    java.nio.file.Files.createDirectories(f.getParent());

                    var map = new LinkedHashMap<String, String>();
                    if (java.nio.file.Files.exists(f)) {
                        for (String line : java.nio.file.Files.readAllLines(f)) {
                            String trimmed = line.trim();
                            if (trimmed.startsWith("\"") && trimmed.contains("\": \"")) {
                                int kEnd = trimmed.indexOf('"', 1); if (kEnd < 0) continue;
                                String key = trimmed.substring(1, kEnd);
                                int vStart = trimmed.indexOf("\": \"") + 4;
                                int vEnd = trimmed.lastIndexOf('"');
                                if (vStart >= 4 && vEnd > vStart)
                                    map.put(key, trimmed.substring(vStart, vEnd));
                            }
                        }
                    }

                    // Remove keys that were PartReg-managed but PartType no longer exists / 删已注销部件
                    String prefix = "block." + namespace + ".";
                    map.keySet().removeIf(key ->
                            key.startsWith(prefix) && trans.containsKey(key) &&
                            !PartType.values().stream().anyMatch(pt ->
                                    pt.getId().getNamespace().equals(namespace) &&
                                    key.equals(prefix + pt.getId().getPath())));

                    // Add missing part translations / 补新部件翻译
                    boolean changed = false;
                    for (var e : trans.entrySet()) {
                        if (!map.containsKey(e.getKey())) {
                            map.put(e.getKey(), e.getValue());
                            changed = true;
                        }
                    }

                    // Only write if changed / 有变化才写
                    if (!changed && map.size() > 0) continue;

                    var sb = new StringBuilder("{\n");
                    int count = 0;
                    for (var e : map.entrySet()) {
                        if (count > 0) sb.append(",\n");
                        sb.append("  \"").append(e.getKey()).append("\": \"")
                                .append(e.getValue().replace("\\", "\\\\").replace("\"", "\\\"")).append("\"");
                        count++;
                    }
                    sb.append("\n}\n");
                    java.nio.file.Files.writeString(f, sb.toString());
                } catch (Exception e) {
                    com.endlessepoch.core.EECore.LOGGER.warn("flushLang {}/{}: {}", namespace, lang, e.getMessage());
                }
            }
        }
    }

    /**
     * Flush accumulated block tags to data/{ns}/tags/block/ JSON.
     * 将累积的方块标签写入 data/{ns}/tags/block/ JSON。
     */
    public static void flushTags(Map<String, LinkedHashSet<String>> tagBlocks) {
        flushTagSet(tagBlocks, "block");
    }

    /** Flush accumulated item tags to data/{ns}/tags/item/ JSON. */
    public static void flushItemTags(Map<String, LinkedHashSet<String>> tagItems) {
        flushTagSet(tagItems, "item");
    }

    private static void flushTagSet(Map<String, LinkedHashSet<String>> tags, String tagType) {
        for (var e : tags.entrySet()) {
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
                            .resolve("tags").resolve(tagType);
                    java.nio.file.Files.createDirectories(d);
                    java.nio.file.Files.writeString(d.resolve(path + ".json"), sb.toString());
                } catch (Exception ignored) {}
            }
        }
    }

}
