package com.endlessepoch.core.api.multiblock.loader;

import com.endlessepoch.core.EECore;
import com.endlessepoch.core.api.multiblock.EECoreCodec;
import com.endlessepoch.core.api.multiblock.IMachineEffect;
import com.endlessepoch.core.api.multiblock.MachineDefinition;
import com.endlessepoch.core.api.multiblock.MachineEffectRegistry;
import com.endlessepoch.core.api.multiblock.MachineRegistry;
import com.endlessepoch.core.api.multiblock.MultiBlockPattern;
import com.endlessepoch.core.api.multiblock.MultiBlockRegistry;
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
    private String lastTag;
    private String nameEn, nameZh;
    private int tier;
    private String model;
    private float offX, offY, offZ;
    private boolean offSet;
    private IMachineEffect effect;
    private String itemId;

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

    /**
     * Register: loads .ecs, creates MachineDefinition, registers pattern + controller item.
     * 注册：加载 .ecs → 创建 MachineDefinition → 注册 pattern + 控制器物品。
     */
    public MachineDefinition register(ResourceLocation machineId) {
        // Skip if already registered / 已注册跳过
        if (MachineRegistry.get(machineId).isPresent()) {
            EECore.LOGGER.info("MultiblockLoader: {} already registered, skipping.", machineId);
            return MachineRegistry.get(machineId).get();
        }

        // 1. Load .ecs / 加载 .ecs
        MultiBlockPattern pattern = loadEcs();
        if (pattern == null) {
            EECore.LOGGER.error("MultiblockLoader: .ecs not found for {}", ecsFile);
            return null;
        }

        // 2. Apply tag alternatives / 应用 TAG 替选块
        for (var e : tagBindings.entrySet())
            for (Block b : e.getValue())
                for (char c : pattern.getDefinitions().keySet())
                    if (pattern.getTags(c).contains(e.getKey()))
                        pattern.addAlternatives(c, b.defaultBlockState());

        // 3. Create MachineDefinition / 创建定义
        String en = nameEn != null ? nameEn : machineId.getPath();
        String zh = nameZh != null ? nameZh : machineId.getPath();
        MachineDefinition def = new MachineDefinition(machineId, en, zh, tier, ecsFile, tagBindings);
        if (model != null) def.setModel(model);
        if (effect != null) def.setEffect(effect);

        // 4. Set up suppliers / 设置供应
        def.setBlockSupplier(com.endlessepoch.core.registry.Blocks.MACHINE_CONTROLLER);
        def.setPatternSupplier(() -> pattern);
        if (offSet) {
            def.setCenterOffset(offX, offY, offZ);
        } else {
            def.computeCenterOffset();
        }

        // 5. Register pattern / 注册 pattern
        MultiBlockRegistry.registerMod(machineId, pattern);

        // 6. Bind controller / 绑定控制器
        var ctrlDef = pattern.getDefinitions().get(EcsFormat.CHAR_CONTROLLER);
        if (ctrlDef != null && ctrlDef.getBlock() != Blocks.AIR) {
            MultiBlockRegistry.bindControllerToPattern(ctrlDef.getBlock(), machineId);
        }

        // 7. Create controller item / 创建控制器物品
        String path = itemId != null ? itemId : machineId.getPath();
        Items.registerMachineItem(path, machineId, en, zh, tier);

        // 8. Register definition / 注册定义
        MachineRegistry.register(def);
        EECore.LOGGER.info("MultiblockLoader: registered {} → {}", machineId, path);
        return def;
    }

    private MultiBlockPattern loadEcs() {
        String cp = "/data/" + ecsFile.getNamespace() + "/structures/" + ecsFile.getPath() + ".ecs";
        try (InputStream is = getClass().getResourceAsStream(cp)) {
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
