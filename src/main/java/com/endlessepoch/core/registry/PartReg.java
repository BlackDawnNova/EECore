package com.endlessepoch.core.registry;

import com.endlessepoch.core.api.multiblock.PartType;
import com.endlessepoch.core.nova.block.part.PartBlock;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.*;
import java.util.function.Supplier;

/**
 * One-click part registration for EECore and addon mods.
 * Translations are stored per-namespace and flushed to the correct lang JSON on startup.
 * <p>
 * 部件一键注册工具，EECore 和附属 Mod 通用。
 * 翻译按 namespace 分桶，启动时写入各 Mod 的语言 JSON。
 */
public final class PartReg {
    private PartReg() {}

    /** namespace → (langKey → translation) / 命名空间 → (翻译键 → 翻译) */
    public static final Map<String, Map<String, String>> TRANS_EN = new LinkedHashMap<>();
    public static final Map<String, Map<String, String>> TRANS_ZH = new LinkedHashMap<>();

    private static void putTrans(String ns, String key, String en, String zh) {
        TRANS_EN.computeIfAbsent(ns, k -> new LinkedHashMap<>()).put(key, en);
        TRANS_ZH.computeIfAbsent(ns, k -> new LinkedHashMap<>()).put(key, zh);
    }

    /**
     * One-click: Block + Item + model + tag + creative tab + translations.
     * 一键注册：方块 + 物品 + 模型 + 标签 + 创造栏 + 翻译。
     *
     * @param overlayTex  overlay texture path (e.g. "mymod:block/parts/hatch/overlay_front") / 覆面贴图路径
     */
    public static Supplier<BlockItem> register(DeferredRegister<Block> blockReg,
                                                DeferredRegister<Item> itemReg,
                                                List<Supplier<BlockItem>> tabList,
                                                String namespace, String path, int tier,
                                                String overlayTex, String nameEn, String nameZh) {
        ResourceLocation rl = ResourceLocation.fromNamespaceAndPath(namespace, path);
        PartType type = PartType.get(rl);
        if (type == null) throw new IllegalArgumentException("Unknown PartType: " + rl);

        var blockSup = blockReg.register(path,
                () -> new PartBlock(PartBlock.tieredProperties(tier), type, tier));
        Items.addToTag(PartBlock.toolTagForTier(tier), path);

        var itemSup = itemReg.register(path,
                () -> new BlockItem(blockSup.get(), new Item.Properties().stacksTo(64)));
        ResourceGenerator.writePartModel(path, tier, overlayTex, namespace);

        if (tabList != null) tabList.add(() -> itemSup.get());
        putTrans(namespace, "block." + namespace + "." + path, nameEn, nameZh);
        return itemSup;
    }

    /**
     * Register item-only (block already registered via {@link Blocks#registerPartBlock}).
     * 仅注册物品（方块已通过 registerPartBlock 注册）。
     */
    public static Supplier<BlockItem> registerItem(DeferredRegister<Item> itemReg,
                                                    Supplier<? extends Block> blockSup,
                                                    List<Supplier<BlockItem>> tabList,
                                                    String namespace, String path, int tier,
                                                    String overlayTex, String nameEn, String nameZh) {
        var itemSup = itemReg.register(path,
                () -> new BlockItem(blockSup.get(), new Item.Properties().stacksTo(64)));
        ResourceGenerator.writePartModel(path, tier, overlayTex, namespace);
        if (tabList != null) tabList.add(() -> itemSup.get());
        putTrans(namespace, "block." + namespace + "." + path, nameEn, nameZh);
        return itemSup;
    }
}
