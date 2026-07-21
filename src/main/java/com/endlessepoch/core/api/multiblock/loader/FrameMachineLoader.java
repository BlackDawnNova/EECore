package com.endlessepoch.core.api.multiblock.loader;

import com.endlessepoch.core.EECore;
import com.endlessepoch.core.api.multiblock.*;
import com.endlessepoch.core.registry.Items;
import com.endlessepoch.ecsformat.EcsFormat;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

import java.util.*;

/** Frame-based multiblock machine builder — {@link #frame(String, int, int, int)} is required. / 框架式多方块机器构建器——必须调用 frame()。 */
public final class FrameMachineLoader {
    private final ResourceLocation ecsFile;
    private final Map<String, Set<Block>> tagBindings = new LinkedHashMap<>();
    private final Map<String, Set<PartCategory>> tagCategories = new LinkedHashMap<>();
    private String lastTag;
    private String nameEn, nameZh;
    private int tier;
    private String model;
    private IMachineEffect effect;
    private String itemId;
    private boolean frameSet;
    private String frameCasingTag;
    private int innerW, innerH, innerD;
    private final Map<String, Map<Block, Integer>> perBlockLimits = new LinkedHashMap<>();
    private final Map<String, Map<PartCategory, Integer>> categoryLimits = new LinkedHashMap<>();

    private FrameMachineLoader(ResourceLocation ecsFile) { this.ecsFile = ecsFile; }
    public static FrameMachineLoader load(ResourceLocation ecsFile) { return new FrameMachineLoader(ecsFile); }

    public FrameMachineLoader name(String en, String zh) { this.nameEn = en; this.nameZh = zh; return this; }
    public FrameMachineLoader tier(int t) { this.tier = t; return this; }
    public FrameMachineLoader model(String m) { this.model = m; return this; }
    public FrameMachineLoader itemId(String id) { this.itemId = id; return this; }
    public FrameMachineLoader effect(String id) { this.effect = MachineEffectRegistry.create(ResourceLocation.parse(id)); return this; }
    public FrameMachineLoader effect(IMachineEffect e) { this.effect = e; return this; }

    /** Required — casing tag + max interior dimensions (min interior=1×1×1, shell=interior+2). / 必调——外壳标签+内部最大尺寸（最小=1×1×1，外壳=内部+2）。 */
    public FrameMachineLoader frame(String casingTag, int innerW, int innerH, int innerD) {
        this.frameSet = true; this.frameCasingTag = casingTag;
        this.innerW = innerW; this.innerH = innerH; this.innerD = innerD;
        return this;
    }

    public FrameMachineLoader where(String tag, Block... blocks) {
        lastTag = tag;
        tagBindings.computeIfAbsent(tag, k -> new LinkedHashSet<>()).addAll(Arrays.asList(blocks));
        return this;
    }

    public FrameMachineLoader or(Block... blocks) {
        if (lastTag != null) tagBindings.get(lastTag).addAll(Arrays.asList(blocks));
        return this;
    }

    public FrameMachineLoader where(String tag, PartCategory... categories) {
        lastTag = tag;
        tagCategories.computeIfAbsent(tag, k -> new LinkedHashSet<>()).addAll(Arrays.asList(categories));
        return this;
    }

    public FrameMachineLoader or(PartCategory... categories) {
        if (lastTag != null)
            tagCategories.computeIfAbsent(lastTag, k -> new LinkedHashSet<>()).addAll(Arrays.asList(categories));
        return this;
    }

    public FrameMachineLoader limit(String tag, Block block, int maxCount) {
        perBlockLimits.computeIfAbsent(tag, k -> new LinkedHashMap<>()).put(block, maxCount);
        return this;
    }

    public FrameMachineLoader limit(String tag, PartCategory category, int maxCount) {
        categoryLimits.computeIfAbsent(tag, k -> new LinkedHashMap<>()).put(category, maxCount);
        return this;
    }

    public MachineDefinition register(ResourceLocation machineId) {
        if (!frameSet) {
            EECore.LOGGER.error("FrameMachineLoader: .frame() must be called before register() for {}", ecsFile);
            return null;
        }
        if (MachineRegistry.get(machineId).isPresent()) {
            EECore.LOGGER.info("FrameMachineLoader: {} already registered, skipping.", machineId);
            return MachineRegistry.get(machineId).get();
        }

        MultiBlockPattern pattern = MultiblockLoader.loadEcsStatic(ecsFile);
        if (pattern == null) {
            EECore.LOGGER.error("FrameMachineLoader: .ecs not found for {}", ecsFile);
            return null;
        }
        pattern.setFrameBased(frameCasingTag, innerW, innerH, innerD);

        MultiblockLoader.applyBindings(pattern, tagBindings, tagCategories, perBlockLimits, categoryLimits);

        String en = nameEn != null ? nameEn : machineId.getPath();
        String zh = nameZh != null ? nameZh : machineId.getPath();
        MachineDefinition def = new MachineDefinition(machineId, en, zh, tier, ecsFile, tagBindings);
        if (model != null) def.setModel(model);
        if (effect != null) def.setEffect(effect);

        def.setBlockSupplier(com.endlessepoch.core.registry.Blocks.MACHINE_CONTROLLER);
        def.setPatternSupplier(() -> pattern);

        MultiBlockRegistry.registerMod(machineId, pattern);

        var ctrlDef = pattern.getDefinitions().get(EcsFormat.CHAR_CONTROLLER);
        if (ctrlDef != null && ctrlDef.getBlock() != Blocks.AIR)
            MultiBlockRegistry.bindControllerToPattern(ctrlDef.getBlock(), machineId);

        String path = itemId != null ? itemId : machineId.getPath();
        Items.registerMachineItem(path, machineId, en, zh, tier, null);

        MachineRegistry.register(def);
        EECore.LOGGER.info("FrameMachineLoader: registered {} → {}", machineId, path);
        return def;
    }
}
