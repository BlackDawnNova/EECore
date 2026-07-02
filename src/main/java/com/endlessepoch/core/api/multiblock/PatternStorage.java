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

/** Saves/loads patterns to disk as .ecs (binary) or .json (legacy). / 结构持久化 */
public final class PatternStorage {
    private static final Path ROOT = Path.of("config", "eecore", "structures");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private PatternStorage() {}

    public static void save(ResourceLocation id, MultiBlockPattern pattern) { saveEcs(id, pattern); }

    public static void saveEcs(ResourceLocation id, MultiBlockPattern pattern) {
        pattern.compactify();
        try {
            Path dir = ROOT.resolve(id.getNamespace());
            Files.createDirectories(dir);
            EECoreCodec.write(dir.resolve(id.getPath().replace('/', '_') + EECoreStructureFormat.EXTENSION), pattern);
        } catch (IOException e) { e.printStackTrace(); }
    }

    public static void saveJson(ResourceLocation id, MultiBlockPattern pattern) {
        pattern.compactify();
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

            Set<Character> used = new HashSet<>();
            for (String s : pattern.getLayerData())
                for (int i = 0; i < s.length(); i++) used.add(s.charAt(i));

            Map<String, String> defs = new LinkedHashMap<>();
            for (var e : pattern.getDefinitions().entrySet()) {
                char c = e.getKey();
                if (c == '#' || !used.contains(c)) continue;
                defs.put(String.valueOf(c), BuiltInRegistries.BLOCK.getKey(e.getValue().getBlock()).toString());
            }
            data.put("definitions", defs);

            Map<String, List<String>> tagsOut = new LinkedHashMap<>();
            for (var e : pattern.getDefinitions().entrySet()) {
                char c = e.getKey();
                List<String> tagList = pattern.getTags(c);
                if (!tagList.isEmpty()) tagsOut.put(String.valueOf(c), tagList);
            }
            if (!tagsOut.isEmpty()) data.put("tags", tagsOut);

            Files.writeString(file, GSON.toJson(data), StandardCharsets.UTF_8);
        } catch (IOException e) { e.printStackTrace(); }
    }

    public static void onServerStarting(net.neoforged.neoforge.event.server.ServerAboutToStartEvent e) { loadAll(); }

    public static void loadAll() {
        if (!Files.exists(ROOT)) return;
        try (var walk = Files.walk(ROOT, 2)) {
            walk.filter(Files::isRegularFile)
                    .filter(f -> f.getFileName().toString().endsWith(EECoreStructureFormat.EXTENSION)
                            || f.getFileName().toString().endsWith(".json"))
                    .forEach(PatternStorage::loadFile);
        } catch (IOException e) { e.printStackTrace(); }
    }

    public static void delete(ResourceLocation id) {
        String safeName = id.getPath().replace('/', '_');
        try {
            Files.deleteIfExists(ROOT.resolve(id.getNamespace()).resolve(safeName + EECoreStructureFormat.EXTENSION));
            Files.deleteIfExists(ROOT.resolve(id.getNamespace()).resolve(safeName + ".json"));
        } catch (IOException e) { e.printStackTrace(); }
    }

    private static void loadFile(Path file) {
        String name = file.getFileName().toString();
        if (name.endsWith(EECoreStructureFormat.EXTENSION)) loadEcsFile(file);
        else if (name.endsWith(".json")) loadJsonFile(file);
    }

    private static void loadEcsFile(Path file) {
        try {
            MultiBlockPattern pattern = EECoreCodec.read(file);
            Path rel = ROOT.relativize(file);
            String ns = rel.getName(0).toString();
            String path = rel.getName(1).toString().replace(EECoreStructureFormat.EXTENSION, "").replace('_', '/');
            MultiBlockRegistry.registerMod(ResourceLocation.fromNamespaceAndPath(ns, path), pattern);
        } catch (IOException e) { e.printStackTrace(); }
    }

    private static void loadJsonFile(Path file) {
        try {
            String json = Files.readString(file, StandardCharsets.UTF_8);
            JsonObject obj = JsonParser.parseString(json).getAsJsonObject();
            int w = obj.get("width").getAsInt(), h = obj.get("height").getAsInt(), d = obj.get("depth").getAsInt();
            int cx = obj.get("controllerX").getAsInt(), cy = obj.get("controllerY").getAsInt(), cz = obj.get("controllerZ").getAsInt();

            JsonArray layersArr = obj.getAsJsonArray("layers");
            String[][] layers = new String[h][d];
            for (int y = 0; y < h; y++) {
                String layerStr = layersArr.get(y).getAsString();
                for (int z = 0; z < d; z++) layers[y][z] = layerStr.substring(z * w, (z + 1) * w);
            }

            Map<Character, BlockState> defs = new LinkedHashMap<>();
            JsonObject defsObj = obj.getAsJsonObject("definitions");
            for (var e : defsObj.entrySet()) {
                ResourceLocation blockId = ResourceLocation.parse(e.getValue().getAsString());
                Block block = BuiltInRegistries.BLOCK.get(blockId);
                if (block != null && block != net.minecraft.world.level.block.Blocks.AIR)
                    defs.put(e.getKey().charAt(0), block.defaultBlockState());
            }
            defs.putIfAbsent('A', net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
            defs.putIfAbsent('#', net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());

            MultiBlockPattern pattern = new MultiBlockPattern(w, h, d, cx, cy, cz, layers, defs);

            if (obj.has("tags")) {
                JsonObject tagsObj = obj.getAsJsonObject("tags");
                for (var e : tagsObj.entrySet()) {
                    char c = e.getKey().charAt(0);
                    List<String> tagList = new ArrayList<>();
                    for (var el : e.getValue().getAsJsonArray()) {
                        if (el.isJsonObject()) tagList.add(el.getAsJsonObject().get("name").getAsString());
                        else tagList.add(el.getAsString());
                    }
                    pattern.setTags(c, tagList);
                }
            }

            Path rel = ROOT.relativize(file);
            String ns = rel.getName(0).toString();
            String path = rel.getName(1).toString().replace(".json", "").replace('_', '/');
            MultiBlockRegistry.registerMod(ResourceLocation.fromNamespaceAndPath(ns, path), pattern);
        } catch (Exception e) { e.printStackTrace(); }
    }
}
