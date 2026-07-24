package com.endlessepoch.core.api.multiblock.loader;

import com.endlessepoch.core.EECore;
import com.endlessepoch.core.api.multiblock.EECoreCodec;
import com.endlessepoch.core.api.multiblock.IMachineEffect;
import com.endlessepoch.core.api.multiblock.MachineDefinition;
import com.endlessepoch.core.api.multiblock.MachineEffectRegistry;
import com.endlessepoch.core.api.multiblock.MachineRegistry;
import com.endlessepoch.core.api.multiblock.MultiBlockPattern;
import com.endlessepoch.core.api.multiblock.MultiBlockRegistry;
import com.endlessepoch.core.api.multiblock.PartCategory;
import com.endlessepoch.core.api.multiblock.TagDefRegistry;
import com.endlessepoch.core.registry.Items;
import com.endlessepoch.ecsformat.EcsFormat;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

import java.io.InputStream;
import java.nio.file.*;
import java.util.*;

/** 从.ecs定义多方块机器的构建器，创建MachineDefinition。 */
public final class MultiblockLoader {
    private final ResourceLocation ecsFile;
    private final Map<String, Set<Block>> tagBindings = new LinkedHashMap<>();
    private final Map<String, Set<PartCategory>> tagCategories = new LinkedHashMap<>();
    private String lastTag;
    private String nameEn, nameZh;
    private int tier;
    private String model;
    private float offX, offY, offZ;
    private boolean offSet;
    private IMachineEffect effect;
    private String itemId;
    private String[] supportedTypes;
    private final Map<String, Map<Block, Integer>> perBlockLimits = new LinkedHashMap<>();
    private final Map<String, Map<PartCategory, Integer>> categoryLimits = new LinkedHashMap<>();

    private MultiblockLoader(ResourceLocation ecsFile) { this.ecsFile = ecsFile; }
    public static MultiblockLoader load(ResourceLocation ecsFile) { return new MultiblockLoader(ecsFile); }

    public MultiblockLoader where(String tag, Block... blocks) {
        lastTag = tag;
        tagBindings.computeIfAbsent(tag, k -> new LinkedHashSet<>()).addAll(Arrays.asList(blocks));
        return this;
    }

    public MultiblockLoader or(Block... blocks) {
        if (lastTag != null) tagBindings.get(lastTag).addAll(Arrays.asList(blocks));
        return this;
    }

    /**
     * Bind whole part categories to a tag — every registered block in the category
     * (creative and addon variants included) becomes a valid alternative. Resolved at
     * register() time, so call register() after block registration (commonSetup+).
     * 按部件类别绑定标签——该类别下所有已注册方块（含创造与附属变体）都成为合法替选。
     * 在 register() 时解析，故 register() 须在方块注册完成后调用（commonSetup 起）。
     */
    public MultiblockLoader where(String tag, PartCategory... categories) {
        lastTag = tag;
        tagCategories.computeIfAbsent(tag, k -> new LinkedHashSet<>()).addAll(Arrays.asList(categories));
        return this;
    }

    public MultiblockLoader or(PartCategory... categories) {
        if (lastTag != null)
            tagCategories.computeIfAbsent(lastTag, k -> new LinkedHashSet<>()).addAll(Arrays.asList(categories));
        return this;
    }

    /**
     * Set per-block max count for a tag. -1 = unlimited. / 设置某方块在标签中的上限。
     */
    public MultiblockLoader limit(String tag, Block block, int maxCount) {
        perBlockLimits.computeIfAbsent(tag, k -> new LinkedHashMap<>()).put(block, maxCount);
        return this;
    }

    /**
     * Category-total limit — caps the combined count of every block in the category.
     * 类别总量上限——限制该类别所有方块的合计数量。
     */
    public MultiblockLoader limit(String tag, PartCategory category, int maxCount) {
        categoryLimits.computeIfAbsent(tag, k -> new LinkedHashMap<>()).put(category, maxCount);
        return this;
    }

    public MultiblockLoader name(String en, String zh) { this.nameEn = en; this.nameZh = zh; return this; }
    public MultiblockLoader tier(int t) { this.tier = t; return this; }
    public MultiblockLoader model(String m) { this.model = m; return this; }
    public MultiblockLoader center(float x, float y, float z) { offX = x; offY = y; offZ = z; offSet = true; return this; }
    public MultiblockLoader effect(String id) {
        this.effect = MachineEffectRegistry.create(ResourceLocation.parse(id));
        return this;
    }
    public MultiblockLoader effect(IMachineEffect e) { this.effect = e; return this; }
    public MultiblockLoader itemId(String id) { this.itemId = id; return this; }
    public MultiblockLoader supports(String... ids) { this.supportedTypes = ids; return this; }

    /**
     * Register: loads .ecs, creates MachineDefinition, registers pattern + controller item.
     * 注册：加载 .ecs → 创建 MachineDefinition → 注册 pattern + 控制器物品。
     */
    public MachineDefinition register(ResourceLocation machineId) {
        if (MachineRegistry.get(machineId).isPresent()) {
            EECore.LOGGER.info("MultiblockLoader: {} already registered, skipping.", machineId);
            return MachineRegistry.get(machineId).get();
        }

        MultiBlockPattern pattern = loadEcs();
        if (pattern == null) {
            EECore.LOGGER.error("MultiblockLoader: .ecs not found for {}", ecsFile);
            return null;
        }

        applyBindings(pattern, tagBindings, tagCategories, perBlockLimits, categoryLimits);

        String en = nameEn != null ? nameEn : machineId.getPath();
        String zh = nameZh != null ? nameZh : machineId.getPath();
        MachineDefinition def = new MachineDefinition(machineId, en, zh, tier, ecsFile, tagBindings);
        if (model != null) def.setModel(model);
        if (effect != null) def.setEffect(effect);

        def.setBlockSupplier(com.endlessepoch.core.registry.Blocks.MACHINE_CONTROLLER);
        def.setPatternSupplier(() -> pattern);
        if (offSet) {
            def.setCenterOffset(offX, offY, offZ);
        } else {
            def.computeCenterOffset();
        }

        MultiBlockRegistry.registerMod(machineId, pattern);

        var ctrlDef = pattern.getDefinitions().get(EcsFormat.CHAR_CONTROLLER);
        if (ctrlDef != null && ctrlDef.getBlock() != Blocks.AIR)
            MultiBlockRegistry.bindControllerToPattern(ctrlDef.getBlock(), machineId);

        String path = itemId != null ? itemId : machineId.getPath();
        Items.registerMachineItem(path, machineId, en, zh, tier, supportedTypes);

        MachineRegistry.register(def);
        EECore.LOGGER.info("MultiblockLoader: registered {} → {}", machineId, path);
        return def;
    }

    /**
     * Common tag-binding application shared with {@link FrameMachineLoader}.
     * 共享的标签绑定应用逻辑。
     */
    static void applyBindings(MultiBlockPattern pattern,
                              Map<String, Set<Block>> tagBindings,
                              Map<String, Set<PartCategory>> tagCategories,
                              Map<String, Map<Block, Integer>> perBlockLimits,
                              Map<String, Map<PartCategory, Integer>> categoryLimits) {
        for (var e : tagBindings.entrySet())
            for (Block b : e.getValue())
                for (char c : pattern.getDefinitions().keySet())
                    if (pattern.getTags(c).contains(e.getKey())) {
                        pattern.addAlternatives(c, b.defaultBlockState());
                        pattern.addExplicitBlock(b);
                    }

        for (var e : tagCategories.entrySet())
            for (PartCategory cat : e.getValue()) {
                pattern.addDeclaredCategory(cat);
                for (Block b : cat.resolve())
                    for (char c : pattern.getDefinitions().keySet())
                        if (pattern.getTags(c).contains(e.getKey()))
                            pattern.addAlternatives(c, b.defaultBlockState());
            }

        for (var e : perBlockLimits.entrySet())
            for (var be : e.getValue().entrySet())
                pattern.setBlockLimit(e.getKey(), be.getKey(), be.getValue());

        for (var e : categoryLimits.entrySet())
            for (var ce : e.getValue().entrySet())
                pattern.setCategoryLimit(e.getKey(), ce.getKey(), ce.getValue());
    }

    private MultiBlockPattern loadEcs() { return loadEcsStatic(ecsFile); }

    static MultiBlockPattern loadEcsStatic(ResourceLocation ecsFile) {
        String cp = "/data/" + ecsFile.getNamespace() + "/structures/" + ecsFile.getPath() + ".ecs";
        try (InputStream is = MultiblockLoader.class.getResourceAsStream(cp)) {
            if (is != null) return EECoreCodec.decode(is.readAllBytes());
        } catch (Exception ignored) {}
        try {
            Path p = Path.of("config", "eecore", "structures",
                    ecsFile.getNamespace(), ecsFile.getPath() + ".ecs");
            if (Files.exists(p)) return EECoreCodec.read(p);
        } catch (Exception ignored) {}
        return null;
    }
}
