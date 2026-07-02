package com.endlessepoch.core.api.multiblock;

import com.google.gson.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/**
 * Save/load scanned multiblock patterns as JSON files.
 * <p>
 * Files are stored in config/eecore/structures/{namespace}/{path}.json
 * Loaded into MOD_PATTERNS on startup, persisted across restarts.
 * <p>
 * 将扫描的多方块结构以 JSON 文件形式保存/加载。
 * 存储在 config/eecore/structures/{命名空间}/{路径}.json
 * 启动时加载到 MOD_PATTERNS，重启不丢失。
 */
public final class PatternStorage {

    private static final Path ROOT = Path.of("config", "eecore", "structures");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private PatternStorage() {}

    /**
     * Save a scanned pattern to disk. Auto-saves to {namespace}/{path}.json
     * <p>
     * 保存扫描的结构到磁盘。自动按命名空间分目录。
     */
    public static void save(ResourceLocation id, MultiBlockPattern pattern) {
        pattern.compactify(); // reorder chars before saving / 保存前重排字符
        try {
            Path dir = ROOT.resolve(id.getNamespace());
            Files.createDirectories(dir);
            Path file = dir.resolve(id.getPath().replace('/', '_') + ".json");

            Map<String, Object> data = new LinkedHashMap<>();
            data.put("width", pattern.width);
            data.put("height", pattern.height);
            data.put("depth", pattern.depth);
            data.put("controllerX", pattern.controllerX);
            data.put("controllerY", pattern.controllerY);
            data.put("controllerZ", pattern.controllerZ);
            data.put("layers", pattern.getLayerData());

            java.util.Set<Character> used = new java.util.HashSet<>();
            for (String s : pattern.getLayerData()) {
                for (int i = 0; i < s.length(); i++) used.add(s.charAt(i));
            }
            Map<String, String> defs = new LinkedHashMap<>();
            for (var e : pattern.getDefinitions().entrySet()) {
                char c = e.getKey();
                if (c == '#') continue; // wildcard not saved / 通配符不保存
                if (!used.contains(c)) continue;
                ResourceLocation key = BuiltInRegistries.BLOCK.getKey(e.getValue().getBlock());
                defs.put(String.valueOf(c), key.toString());
            }
            data.put("definitions", defs);

            Files.writeString(file, GSON.toJson(data), StandardCharsets.UTF_8);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Load all saved patterns from disk into the registry.
     * Call once during server startup.
     * <p>
     * 从磁盘加载所有保存的结构到注册表。
     * 在服务端启动时调用一次。
     */
    public static void onServerStarting(net.neoforged.neoforge.event.server.ServerAboutToStartEvent e) {
        loadAll();
    }

    public static void loadAll() {
        if (!Files.exists(ROOT)) return;
        try (var walk = Files.walk(ROOT, 2)) {
            walk.filter(Files::isRegularFile)
                    .filter(f -> f.toString().endsWith(".json"))
                    .forEach(PatternStorage::loadFile);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static void loadFile(Path file) {
        try {
            String json = Files.readString(file, StandardCharsets.UTF_8);
            JsonObject obj = JsonParser.parseString(json).getAsJsonObject();

            int w = obj.get("width").getAsInt();
            int h = obj.get("height").getAsInt();
            int d = obj.get("depth").getAsInt();
            int cx = obj.get("controllerX").getAsInt();
            int cy = obj.get("controllerY").getAsInt();
            int cz = obj.get("controllerZ").getAsInt();

            JsonArray layersArr = obj.getAsJsonArray("layers");
            String[][] layers = new String[h][d];
            for (int y = 0; y < h; y++) {
                String layerStr = layersArr.get(y).getAsString();
                for (int z = 0; z < d; z++)
                    layers[y][z] = layerStr.substring(z * w, (z + 1) * w);
            }

            JsonObject defsObj = obj.getAsJsonObject("definitions");
            Map<Character, BlockState> defs = new LinkedHashMap<>();
            for (var e : defsObj.entrySet()) {
                char c = e.getKey().charAt(0);
                ResourceLocation blockId = ResourceLocation.parse(e.getValue().getAsString());
                Block block = BuiltInRegistries.BLOCK.get(blockId);
                if (block != null && block != net.minecraft.world.level.block.Blocks.AIR) {
                    defs.put(c, block.defaultBlockState());
                }
            }

            // Ensure reserved chars exist / 确保保留字符存在
            defs.putIfAbsent('A', net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
            defs.putIfAbsent('#', net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());

            MultiBlockPattern pattern = new MultiBlockPattern(w, h, d, cx, cy, cz, layers, defs);

            Path rel = ROOT.relativize(file);
            String ns = rel.getName(0).toString();
            String path = rel.getName(1).toString().replace(".json", "").replace('_', '/');
            ResourceLocation id = ResourceLocation.fromNamespaceAndPath(ns, path);

            MultiBlockRegistry.registerMod(id, pattern);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Delete a saved pattern file from disk.
     * <p>
     * 从磁盘删除保存的结构文件。
     */
    public static void delete(ResourceLocation id) {
        Path file = ROOT.resolve(id.getNamespace()).resolve(id.getPath().replace('/', '_') + ".json");
        try {
            Files.deleteIfExists(file);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
