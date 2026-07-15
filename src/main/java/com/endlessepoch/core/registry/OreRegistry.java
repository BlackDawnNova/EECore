package com.endlessepoch.core.registry;

import com.endlessepoch.core.EECore;
import com.endlessepoch.core.block.OreBlock;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * One-click ore registration — define Material, everything auto-generated.
 * 一键矿石注册——定义Material，全自动生成贴图+模型+翻译+标签。
 */
public class OreRegistry {

    public static final DeferredRegister<Block> BLOCKS =
            DeferredRegister.create(Registries.BLOCK, EECore.MOD_ID);
    public static final List<java.util.function.Supplier<Block>> ORE_BLOCKS = new ArrayList<>();
    public static final List<java.util.function.Supplier<Item>> ITEMS = new ArrayList<>();

    public static final Map<String, Map<String, String>> TRANS_EN = new LinkedHashMap<>();
    public static final Map<String, Map<String, String>> TRANS_ZH = new LinkedHashMap<>();

    private static void putTrans(String ns, String key, String en, String zh) {
        TRANS_EN.computeIfAbsent(ns, k -> new LinkedHashMap<>()).put(key, en);
        TRANS_ZH.computeIfAbsent(ns, k -> new LinkedHashMap<>()).put(key, zh);
    }

    private static final String[] ITEM_STAGES = {
        "raw_%s", "crushed_%s_ore", "purified_crushed_%s_ore", "refined_%s_ore",
        "impure_%s_dust", "refined_%s_dust", "%s_dust",
        "small_%s_dust", "tiny_%s_dust"
    };
    private static final String[] STAGE_NAMES_EN = {
        "Raw %s", "Crushed %s Ore", "Purified Crushed %s Ore", "Refined %s Ore",
        "Impure %s Dust", "Refined %s Dust", "%s Dust",
        "Small %s Dust", "Tiny %s Dust"
    };
    private static final String[] STAGE_NAMES_ZH = {
        "粗%s", "粉碎%s矿石", "净化粉碎%s矿石", "精炼%s矿石",
        "不纯%s粉", "精炼%s粉", "%s粉",
        "小撮%s粉", "微量%s粉"
    };
    private static final String[] SPOT_SUFFIXES = {
        "dull_ore", "dull_ore_small", "fine_ore", "flint_ore", "diamond_ore"
    };

    /** 一键生成贴图+注册全部矿石 / Generate textures + register all ores */
    public static void registerAll(Material... materials) {
        String ns = EECore.MOD_ID;
        Path spotDir = ResourceGenerator.PROJECT_ROOT
                .resolve("src/main/resources/assets/eecore/textures/template/ore_spot");
        Path stageDir = ResourceGenerator.PROJECT_ROOT
                .resolve("src/main/resources/assets/eecore/textures/template/material_stage");
        Path texBlock = ResourceGenerator.PROJECT_ROOT
                .resolve("src/main/resources/assets/eecore/textures/block/ores");
        Path texItem = ResourceGenerator.PROJECT_ROOT
                .resolve("src/main/resources/assets/eecore/textures/item");
        Path modelItem = ResourceGenerator.PROJECT_ROOT
                .resolve("src/main/resources/assets/eecore/models/item");

        for (Material mat : materials) {
            for (int i = 0; i < SPOT_SUFFIXES.length; i++) {
                try {
                    byte[] png = tintSpot(spotDir, SPOT_SUFFIXES[i], mat.r, mat.g, mat.b);
                    if (png != null) {
                        Files.createDirectories(texBlock);
                        Files.write(texBlock.resolve(mat.id + "_ore_" + i + ".png"), png);
                    }
                } catch (Exception ignored) {}
            }
            for (Stage st : Stage.VALUES) {
                try {
                    byte[] png = compositeStage(stageDir, st, mat.r, mat.g, mat.b);
                    if (png != null) {
                        Files.createDirectories(texItem);
                        String name = st.fileName(mat.id);
                        Files.write(texItem.resolve(name + ".png"), png);
                        Files.createDirectories(modelItem);
                        Files.writeString(modelItem.resolve(name + ".json"),
                                "{\"parent\":\"minecraft:item/generated\",\"textures\":{\"layer0\":\"eecore:item/" + name + "\"}}");
                    }
                } catch (Exception ignored) {}
            }

            String blockId = mat.id + "_ore";
            var blockSup = BLOCKS.register(blockId, () -> new OreBlock(
                    BlockBehaviour.Properties.of().mapColor(MapColor.STONE)
                            .strength(3f, 3f).requiresCorrectToolForDrops().sound(SoundType.STONE), mat.id));
            ORE_BLOCKS.add(() -> blockSup.get());
            var oreItem = Items.ITEMS.register(blockId,
                    () -> new BlockItem(blockSup.get(), new Item.Properties()));
            ITEMS.add(() -> oreItem.get());
            for (int i = 0; i < ITEM_STAGES.length; i++) {
                String itemId = String.format(ITEM_STAGES[i], mat.id);
                var sup = Items.ITEMS.register(itemId, () -> new Item(new Item.Properties()));
                ITEMS.add(() -> sup.get());
                putTrans(ns, "item." + ns + "." + itemId,
                        String.format(STAGE_NAMES_EN[i], mat.nameEn),
                        String.format(STAGE_NAMES_ZH[i], mat.nameZh));
            }
            putTrans(ns, "block." + ns + "." + blockId, mat.nameEn + " Ore", mat.nameZh + "矿石");
            Items.addToTag(mat.toolTag, blockId);
            Items.addToTag("c:ores/" + mat.id, blockId);
            Items.addToTag("c:ores_in_ground/stone", blockId);
            Items.addToItemTag("c:raw_materials/" + mat.id, "raw_" + mat.id);
            Items.addToItemTag("c:dusts/" + mat.id, mat.id + "_dust");
            Items.addToItemTag("c:small_dusts/" + mat.id, "small_" + mat.id + "_dust");
            Items.addToItemTag("c:tiny_dusts/" + mat.id, "tiny_" + mat.id + "_dust");
            ResourceGenerator.writeOreModel(ns, mat.id);
        }
    }

    /** 材质定义——一行注册 / Material definition — one-line registration */
    public record Material(String id, int r, int g, int b, String nameEn, String nameZh, String toolTag) {}

    // 贴图生成, 服务器自动跳过 / Image generation, skips on dedicated servers

    private static byte[] tintSpot(Path dir, String spotName, int tr, int tg, int tb) {
        try {
            var img = javax.imageio.ImageIO.read(dir.resolve(spotName + ".png").toFile());
            int w = img.getWidth(), h = img.getHeight();
            var out = new java.awt.image.BufferedImage(w, h, java.awt.image.BufferedImage.TYPE_INT_ARGB);
            for (int y = 0; y < h; y++)
                for (int x = 0; x < w; x++) {
                    int argb = img.getRGB(x, y);
                    int a = (argb >> 24) & 0xFF;
                    if (a < 10) continue;
                    float lum = lumFrom(argb);
                    int nr = clamp((int)(tr / 255f * lum * 255));
                    int ng = clamp((int)(tg / 255f * lum * 255));
                    int nb = clamp((int)(tb / 255f * lum * 255));
                    out.setRGB(x, y, (a << 24) | (nr << 16) | (ng << 8) | nb);
                }
            var baos = new java.io.ByteArrayOutputStream();
            javax.imageio.ImageIO.write(out, "PNG", baos);
            return baos.toByteArray();
        } catch (Throwable t) {
            if (!loggedAwt) { EECore.LOGGER.info("OreRegistry: skipping image gen (server/headless)"); loggedAwt = true; }
            return null;
        }
    }

    private static byte[] compositeStage(Path dir, Stage st, int tr, int tg, int tb) {
        try {
            var baseImg = javax.imageio.ImageIO.read(dir.resolve(st.base + ".png").toFile());
            var secImg = st.secondary != null ? javax.imageio.ImageIO.read(dir.resolve(st.secondary + ".png").toFile()) : null;
            var ovlImg = st.overlay != null ? javax.imageio.ImageIO.read(dir.resolve(st.overlay + ".png").toFile()) : null;
            int w = baseImg.getWidth(), h = baseImg.getHeight();
            var out = new java.awt.image.BufferedImage(w, h, java.awt.image.BufferedImage.TYPE_INT_ARGB);
            for (int y = 0; y < h; y++)
                for (int x = 0; x < w; x++) {
                    float lum = lumFrom(baseImg.getRGB(x, y));
                    if (secImg != null) { float sl = lumFrom(secImg.getRGB(x, y)); if (sl > lum) lum = sl; }
                    if (lum < 0.01f) continue;
                    lum = Math.min(1f, lum * st.baseMult);
                    int nr = clamp((int)(tr / 255f * lum * 255));
                    int ng = clamp((int)(tg / 255f * lum * 255));
                    int nb = clamp((int)(tb / 255f * lum * 255));
                    out.setRGB(x, y, 0xFF000000 | (nr << 16) | (ng << 8) | nb);
                }
            if (ovlImg != null) {
                for (int y = 0; y < h; y++)
                    for (int x = 0; x < w; x++) {
                        int oargb = ovlImg.getRGB(x, y);
                        int oa = (oargb >> 24) & 0xFF;
                        if (oa < 30) continue;
                        int brgb = out.getRGB(x, y);
                        float fa = oa / 255f;
                        out.setRGB(x, y, 0xFF000000
                                | (clamp((int)(((oargb >> 16) & 0xFF) * fa + ((brgb >> 16) & 0xFF) * (1 - fa))) << 16)
                                | (clamp((int)(((oargb >> 8) & 0xFF) * fa + ((brgb >> 8) & 0xFF) * (1 - fa))) << 8)
                                | clamp((int)((oargb & 0xFF) * fa + (brgb & 0xFF) * (1 - fa))));
                    }
            }
            var baos = new java.io.ByteArrayOutputStream();
            javax.imageio.ImageIO.write(out, "PNG", baos);
            return baos.toByteArray();
        } catch (Throwable t) {
            return null;
        }
    }

    private static float lumFrom(int argb) {
        if (((argb >> 24) & 0xFF) < 10) return 0f;
        return (0.299f * ((argb >> 16) & 0xFF) + 0.587f * ((argb >> 8) & 0xFF) + 0.114f * (argb & 0xFF)) / 255f;
    }

    private static int clamp(int v) { return Math.max(0, Math.min(255, v)); }
    private static boolean loggedAwt;

    // 加工阶段定义 / Processing stage definitions

    private record Stage(String base, String secondary, String overlay, float baseMult, String outSuffix) {
        static final Stage[] VALUES = {
            new Stage("raw_ore",          "raw_ore_secondary",          null,                     1.2f, "raw_%s"),
            new Stage("crushed",          "crushed_secondary",          "crushed_overlay",          1.0f, "crushed_%s_ore"),
            new Stage("crushed_purified", "crushed_purified_secondary", null,                       1.3f, "purified_crushed_%s_ore"),
            new Stage("crushed_refined",  "crushed_refined_secondary",  "crushed_refined_overlay",   1.5f, "refined_%s_ore"),
            new Stage("dust",             "dust_secondary",             null,                       1.3f, "%s_dust"),
            new Stage("dust_impure",      "dust_impure_secondary",      "dust_impure_overlay",       1.0f, "impure_%s_dust"),
            new Stage("dust_pure",        "dust_pure_secondary",        "dust_pure_overlay",         1.5f, "refined_%s_dust"),
            new Stage("dust_small",       "dust_small_secondary",       null,                       1.3f, "small_%s_dust"),
            new Stage("dust_tiny",        "dust_tiny_secondary",        null,                       1.3f, "tiny_%s_dust"),
        };
        String fileName(String materialId) { return String.format(outSuffix, materialId); }
    }

    // 附属Mod API / Addon-mod API

    /** 附属Mod一键注册：纹理写eecore目录，方块/物品注册到addon命名空间 / Textures in eecore assets, blocks/items in addon namespace */
    public static void registerAll(String namespace,
            DeferredRegister<Block> blockReg, DeferredRegister<Item> itemReg,
            List<java.util.function.Supplier<Item>> creativeTab, Material... materials) {
        Path spotDir = ResourceGenerator.PROJECT_ROOT
                .resolve("src/main/resources/assets/eecore/textures/template/ore_spot");
        Path stageDir = ResourceGenerator.PROJECT_ROOT
                .resolve("src/main/resources/assets/eecore/textures/template/material_stage");
        Path texBlock = ResourceGenerator.PROJECT_ROOT
                .resolve("src/main/resources/assets/eecore/textures/block/ores");
        Path texItem = ResourceGenerator.PROJECT_ROOT
                .resolve("src/main/resources/assets/" + namespace + "/textures/item");
        Path modelItem = ResourceGenerator.PROJECT_ROOT
                .resolve("src/main/resources/assets/" + namespace + "/models/item");

        for (Material mat : materials) {
            for (int i = 0; i < SPOT_SUFFIXES.length; i++) {
                try {
                    byte[] png = tintSpot(spotDir, SPOT_SUFFIXES[i], mat.r, mat.g, mat.b);
                    if (png != null) {
                        Files.createDirectories(texBlock);
                        Files.write(texBlock.resolve(mat.id + "_ore_" + i + ".png"), png);
                    }
                } catch (Exception ignored) {}
            }
            for (Stage st : Stage.VALUES) {
                try {
                    byte[] png = compositeStage(stageDir, st, mat.r, mat.g, mat.b);
                    if (png != null) {
                        Files.createDirectories(texItem);
                        String name = st.fileName(mat.id);
                        Files.write(texItem.resolve(name + ".png"), png);
                        Files.createDirectories(modelItem);
                        Files.writeString(modelItem.resolve(name + ".json"),
                                "{\"parent\":\"minecraft:item/generated\",\"textures\":{\"layer0\":\"" + namespace + ":item/" + name + "\"}}");
                    }
                } catch (Exception ignored) {}
            }
            registerChain(namespace, blockReg, itemReg, creativeTab,
                    mat.id, mat.nameEn, mat.nameZh, mat.toolTag);
        }
    }

    public static void registerChain(
            String namespace, DeferredRegister<Block> blockReg, DeferredRegister<Item> itemReg,
            List<java.util.function.Supplier<Item>> creativeTab,
            String id, String nameEn, String nameZh, String toolTag) {
        String blockId = id + "_ore";
        var blockSup = blockReg.register(blockId, () -> new OreBlock(
                BlockBehaviour.Properties.of().mapColor(MapColor.STONE)
                        .strength(3f, 3f).requiresCorrectToolForDrops().sound(SoundType.STONE), id));
        ORE_BLOCKS.add(() -> blockSup.get());
        var oreItem = itemReg.register(blockId, () -> new BlockItem(blockSup.get(), new Item.Properties()));
        if (creativeTab != null) creativeTab.add(() -> oreItem.get());
        for (int i = 0; i < ITEM_STAGES.length; i++) {
            String itemId = String.format(ITEM_STAGES[i], id);
            var sup = itemReg.register(itemId, () -> new Item(new Item.Properties()));
            if (creativeTab != null) creativeTab.add(() -> sup.get());
            putTrans(namespace, "item." + namespace + "." + itemId,
                    String.format(STAGE_NAMES_EN[i], nameEn), String.format(STAGE_NAMES_ZH[i], nameZh));
        }
        putTrans(namespace, "block." + namespace + "." + blockId, nameEn + " Ore", nameZh + "矿石");
        Items.addToTagNs(toolTag, namespace, blockId);
        Items.addToTagNs("c:ores/" + id, namespace, blockId);
        Items.addToTagNs("c:ores_in_ground/stone", namespace, blockId);
        Items.addToItemTagNs("c:raw_materials/" + id, namespace, "raw_" + id);
        Items.addToItemTagNs("c:dusts/" + id, namespace, id + "_dust");
        Items.addToItemTagNs("c:small_dusts/" + id, namespace, "small_" + id + "_dust");
        Items.addToItemTagNs("c:tiny_dusts/" + id, namespace, "tiny_" + id + "_dust");
        ResourceGenerator.writeOreModel(namespace, id);
    }
}
