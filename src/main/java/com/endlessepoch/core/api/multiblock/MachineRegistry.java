package com.endlessepoch.core.api.multiblock;

import com.endlessepoch.core.EECore;
import com.endlessepoch.core.api.multiblock.loader.FrameMachineLoader;
import com.endlessepoch.core.api.multiblock.loader.MultiblockLoader;
import net.minecraft.resources.ResourceLocation;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/** MachineDefinition registry: lookup, auto-registration, sidecar JSON scanning. / MachineDefinition 注册表：查询、自动注册、边车JSON扫描。 */
public final class MachineRegistry {

    private static final Map<ResourceLocation, MachineDefinition> DEFINITIONS = new LinkedHashMap<>();

    private MachineRegistry() {}

    /** Register a definition (built-in machines call this). / 注册定义（内置机器调用）。 */
    public static void register(MachineDefinition def) {
        DEFINITIONS.put(def.getId(), def);
        EECore.LOGGER.info("MachineRegistry: registered {}", def.getId());
    }

    public static Optional<MachineDefinition> get(ResourceLocation id) {
        return Optional.ofNullable(DEFINITIONS.get(id));
    }

    /** Find machine by item registration id (usually matches machineId path). / 通过物品 ID 查找。 */
    public static Optional<MachineDefinition> getByItemId(String itemId) {
        return DEFINITIONS.values().stream()
                .filter(d -> d.getId().getPath().equals(itemId))
                .findFirst();
    }

    public static Collection<MachineDefinition> getAll() {
        return Collections.unmodifiableCollection(DEFINITIONS.values());
    }

    /**
     * Auto-register machines from .ecs files in config/ directory.
     * Lower priority than built-in — skips already registered IDs.
     * <p>
     * 自动扫描 config/ 目录下的 .ecs 注册机器。优先级低于内置。
     */
    public static void autoRegisterAll() {
        Path configDir = Path.of("config", "eecore", "structures");
        if (!Files.exists(configDir)) return;

        try (var walk = Files.walk(configDir, 3)) {
            walk.filter(Files::isRegularFile)
                .filter(f -> f.toString().endsWith(".ecs"))
                .forEach(MachineRegistry::registerFromFile);
        } catch (Exception e) {
            EECore.LOGGER.warn("MachineRegistry: failed to scan structures: {}", e.getMessage());
        }
    }

    private static void registerFromFile(Path file) {
        try {
            String name = file.getFileName().toString().replace(".ecs", "");
            String ns = file.getParent().getFileName().toString();
            ResourceLocation id = ResourceLocation.fromNamespaceAndPath(ns, name);
            ResourceLocation ecsFile = ResourceLocation.fromNamespaceAndPath(ns, name);

            if (DEFINITIONS.containsKey(id)) return;

            // Try sidecar JSON for metadata / 尝试边车 JSON
            String nameEn = name, nameZh = name, model = null, effect = null;
            int tier = 0;
            String casingTag = null; int innerW = -1, innerH = -1, innerD = -1;
            Path jsonFile = file.resolveSibling(name + ".json");
            if (Files.exists(jsonFile)) {
                try {
                    String json = Files.readString(jsonFile);
                    nameEn = extract(json, "name_en", name);
                    nameZh = extract(json, "name_zh", name);
                    model = extract(json, "model", null);
                    effect = extract(json, "effect", null);
                    String t = extract(json, "tier", null);
                    if (t != null) try { tier = Integer.parseInt(t); } catch(Exception ignored){}
                    casingTag = extract(json, "frame_casing", null);
                    try { String s = extract(json, "frame_inner_w", null); if (s != null) innerW = Integer.parseInt(s); } catch(Exception ignored){}
                    try { String s = extract(json, "frame_inner_h", null); if (s != null) innerH = Integer.parseInt(s); } catch(Exception ignored){}
                    try { String s = extract(json, "frame_inner_d", null); if (s != null) innerD = Integer.parseInt(s); } catch(Exception ignored){}
                } catch (Exception ignored) {}
            }

            // Load .ecs to check format / 加载.ecs判断格式
            var pat = EECoreCodec.read(file);
            if (pat.isFrameBased()) {
                if (casingTag == null) {
                    for (char c : pat.getDefinitions().keySet())
                        if (!pat.getTags(c).isEmpty()) { casingTag = pat.getTags(c).get(0); break; }
                }
                if (casingTag == null) { EECore.LOGGER.warn("FrameMachineLoader: no tags in {} — skipping", file); return; }
                if (innerW < 1) innerW = 3; if (innerH < 1) innerH = 3; if (innerD < 1) innerD = 3;
                var b = com.endlessepoch.core.api.multiblock.loader.FrameMachineLoader.load(ecsFile)
                        .name(nameEn, nameZh).tier(tier).frame(casingTag, innerW, innerH, innerD);
                if (model != null) b.model(model);
                if (effect != null) b.effect(effect);
                b.register(id);
            } else {
                var b = MultiblockLoader.load(ecsFile).name(nameEn, nameZh).tier(tier);
                if (model != null) b.model(model);
                if (effect != null) b.effect(effect);
                b.register(id);
            }
        } catch (Exception e) {
            EECore.LOGGER.warn("MachineRegistry: failed to register {}: {}", file, e.getMessage());
        }
    }

    public static String extract(String json, String key, String def) {
        String s = "\"" + key + "\":\"";
        int i = json.indexOf(s);
        if (i < 0) return def;
        i += s.length();
        int j = json.indexOf('"', i);
        return j > i ? json.substring(i, j) : def;
    }
}
