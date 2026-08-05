package com.endlessepoch.core;

import com.endlessepoch.core.api.EECoreCapabilities;
import com.endlessepoch.core.api.client.EmissiveHelper;
import com.endlessepoch.core.api.multiblock.MultiBlockRegistry;
import com.endlessepoch.core.api.registry.NovaNetRegistry;
import com.endlessepoch.core.api.tier.VoltageTier;
import com.endlessepoch.core.command.EECoreCommands;
import com.endlessepoch.core.nova.client.ClientPacketHandlers;
import com.endlessepoch.core.nova.network.node.NovaNodeRegistration;
import com.endlessepoch.core.registry.OreRegistry.Material;
import com.endlessepoch.core.network.OpenMbVisPacket;
import com.endlessepoch.core.network.SyncConsumerPacket;
import com.endlessepoch.core.network.SyncGeneratorPacket;
import com.endlessepoch.core.network.SyncPatternPacket;
import com.endlessepoch.core.network.SyncPatternBinaryPacket;
import com.endlessepoch.core.network.SyncValidationPacket;
import com.endlessepoch.core.registry.EECoreRecipeTypes;
import com.endlessepoch.core.registry.BlockEntities;
import com.endlessepoch.core.registry.Blocks;
import com.endlessepoch.core.registry.Items;
import com.endlessepoch.core.registry.Menus;
import com.endlessepoch.core.blockentity.creative.CreativeConsumerBlockEntity;
import com.endlessepoch.core.blockentity.creative.CreativeGeneratorBlockEntity;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.slf4j.Logger;
import com.mojang.logging.LogUtils;

import java.util.function.Supplier;

/**
 * Main mod class for Endless Epoch Core.
 * Handles mod lifecycle: registry setup, capability registration, network payload handlers, command registration.
 * <p>
 * 无尽纪元核心主模组类，管理注册表初始化、能力注册、网络载荷处理及命令注册。
 */
@Mod(EECore.MOD_ID)
public class EECore {

    public static final String MOD_ID = "eecore";
    public static final String MOD_NAME = "Endless Epoch Core";
    public static final String VERSION = "0.2.0";

    public static final Logger LOGGER = LogUtils.getLogger();

    public static final DeferredRegister<CreativeModeTab> CREATIVE_TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MOD_ID);

    public static final Supplier<CreativeModeTab> MACHINES_TAB = CREATIVE_TABS.register(
            "eecore_machines",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.eecore.machines"))
                    .icon(() -> Items.LV_MACHINE_CASING.get().getDefaultInstance())
                    .withTabsBefore(CreativeModeTabs.SPAWN_EGGS)
                    .displayItems((params, output) -> {
                        // Machines populated via BuildCreativeModeTabContentsEvent / 机器通过事件填充
                    })
                    .build()
    );

    public static final Supplier<CreativeModeTab> BLOCKS_TAB = CREATIVE_TABS.register(
            "eecore_blocks",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.eecore.blocks"))
                    .icon(() -> Items.ELV_MACHINE_CASING.get().getDefaultInstance())
                    .withTabsBefore(CreativeModeTabs.SPAWN_EGGS)
                    .displayItems((params, output) -> {
                        output.accept(Items.ELV_MACHINE_CASING.get());
                        output.accept(Items.LV_MACHINE_CASING.get());
                        output.accept(Items.MV_MACHINE_CASING.get());
                        output.accept(Items.HV_MACHINE_CASING.get());
                        output.accept(Items.EHV_MACHINE_CASING.get());
                        output.accept(Items.UHV_MACHINE_CASING.get());
                        output.accept(Items.PHV_MACHINE_CASING.get());
                        output.accept(Items.XHV_MACHINE_CASING.get());
                        output.accept(Items.PLV_MACHINE_CASING.get());
                        output.accept(Items.SV_MACHINE_CASING.get());
                        output.accept(Items.BV_MACHINE_CASING.get());
                        output.accept(Items.QV_MACHINE_CASING.get());
                        output.accept(Items.CREATIVE_GENERATOR_ITEM.get());
                        output.accept(Items.CREATIVE_CONSUMER_ITEM.get());
                        output.accept(Items.TEST_TRANSMITTER_ITEM.get());
                        output.accept(Items.SCANNER_CONTROLLER_ITEM.get());
                        output.accept(Items.SCANNER_BOUNDARY_ITEM.get());
                        // Multiblock parts / 多方块部件
                        for (var sup : com.endlessepoch.core.registry.Items.PART_ITEMS)
                            output.accept(sup.get());
                        // Ore blocks / 矿石方块
                        for (var sup : com.endlessepoch.core.registry.OreRegistry.ITEMS)
                            output.accept(sup.get());
                    })
                    .build()
    );

    public static final Supplier<CreativeModeTab> ITEMS_TAB = CREATIVE_TABS.register(
            "eecore_items",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.eecore.items"))
                    .icon(() -> Items.MULTIBLOCK_SCANNER.get().getDefaultInstance())
                    .withTabsBefore(CreativeModeTabs.SPAWN_EGGS)
                    .displayItems((params, output) -> {
                        output.accept(Items.MULTIBLOCK_SCANNER.get());
                        output.accept(Items.COLLAPSE_CORE.get());
                        output.accept(Items.AE_PATTERN_CORE.get());
                        output.accept(Items.LASER_LINK_CARD.get());
                        output.accept(Items.WRENCH.get());
                        output.accept(Items.HAMMER.get());
                        output.accept(Items.FILE.get());
                        output.accept(Items.WIRE_CUTTER.get());
                        output.accept(Items.CROWBAR.get());
                        output.accept(Items.SAW.get());
                        output.accept(Items.SCREWDRIVER.get());
                    })
                    .build()
    );

    public static final Supplier<CreativeModeTab> FLUIDS_TAB = CREATIVE_TABS.register(
            "eecore_fluids",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.eecore.fluids"))
                    .icon(() -> com.endlessepoch.core.registry.Fluids.STEAM.bucket().get().getDefaultInstance())
                    .withTabsBefore(ResourceLocation.parse("eecore:eecore_items"))
                    .displayItems((params, output) -> {
                        for (var b : com.endlessepoch.core.registry.Fluids.BUCKETS)
                            output.accept(b.get());
                    })
                    .build()
    );

    public EECore(IEventBus modEventBus, ModContainer container) {
        LOGGER.info(MOD_NAME + " v" + VERSION + " 加载中...");
        // Register config so ModConfigEvent fires and values are baked / 注册配置使事件触发并烘焙值
        container.registerConfig(net.neoforged.fml.config.ModConfig.Type.COMMON, Config.SPEC);
        EmissiveHelper.registerEmissiveModel(
                "eecore:scanner_controller",
                "eecore:block/scanner_controller_front_e"
        );
        EmissiveHelper.registerEmissiveModel(
                "eecore:machine_controller",
                "eecore:block/machines/example/overlay_front_e"
        );

        Blocks.BLOCKS.register(modEventBus);
        Items.ITEMS.register(modEventBus);
        com.endlessepoch.core.registry.Blocks.flushCasingTags();
        com.endlessepoch.core.registry.Blocks.flushPartItems();
        BlockEntities.BLOCK_ENTITIES.register(modEventBus);
        com.endlessepoch.core.registry.OreRegistry.BLOCKS.register(modEventBus);
        com.endlessepoch.core.registry.OreRegistry.registerAll(
            // id/R/G/B/nameEn/nameZh/toolTag/replaceTag/size/count/minY/maxY/vanillaOre/biomeTag / id/R/G/B/英文/中文/工具标签/替换标签/矿团大小/数量/最低Y/最高Y/原版矿名/群系标签
            new Material("iron",      0xAF, 0x8E, 0x77, "Iron",     "铁",   "minecraft:needs_stone_tool", "minecraft:stone_ore_replaceables",   8, 20, -64,  64, "minecraft:ore_iron",        "#c:is_overworld"),
            new Material("copper",    0xC1, 0x67, 0x46, "Copper",  "铜",   "minecraft:needs_stone_tool", "minecraft:stone_ore_replaceables",   8, 16, -16, 112, "minecraft:ore_copper",      "#c:is_overworld"),
            new Material("gold",      0xFF, 0xD7, 0x00, "Gold",    "金",   "minecraft:needs_iron_tool",  "minecraft:stone_ore_replaceables",   6,  4, -64,  32, "minecraft:ore_gold",        "#c:is_overworld"),
            new Material("diamond",   0xA0, 0xFF, 0xFF, "Diamond", "钻石", "minecraft:needs_iron_tool",  "minecraft:stone_ore_replaceables",   4,  4, -64,  16, "minecraft:ore_diamond",     "#c:is_overworld"),
            // Overworld / 主世界
            new Material("coal",      0x33, 0x33, 0x33, "Coal",     "煤",   "minecraft:needs_stone_tool", "minecraft:stone_ore_replaceables",  17, 20,   0, 192, "minecraft:ore_coal",        "#c:is_overworld"),
            new Material("redstone",  0xFF, 0x00, 0x00, "Redstone","红石", "minecraft:needs_iron_tool",  "minecraft:stone_ore_replaceables",   8,  8, -64,  15, "minecraft:ore_redstone",    "#c:is_overworld"),
            new Material("lapis",     0x22, 0x44, 0xCC, "Lapis",   "青金石","minecraft:needs_stone_tool", "minecraft:stone_ore_replaceables",   7,  2, -64,  64, "minecraft:ore_lapis",       "#c:is_overworld"),
            new Material("emerald",   0x11, 0xCC, 0x55, "Emerald", "绿宝石","minecraft:needs_iron_tool",  "minecraft:stone_ore_replaceables",   3,  3, -16, 320, "minecraft:ore_emerald",     "#c:is_overworld"),
            // Nether / 下界
            new Material("nether_gold",   0xFF, 0xD7, 0x00, "Nether Gold",    "下界金",   "minecraft:needs_stone_tool", "minecraft:nether_ore_replaceables", 10, 10, 10, 117, "minecraft:ore_nether_gold",   "#c:is_nether"),
            new Material("nether_quartz", 0xFF, 0xF5, 0xEE, "Nether Quartz", "下界石英", "minecraft:needs_stone_tool", "minecraft:nether_ore_replaceables", 14, 16, 10, 117, "minecraft:ore_nether_quartz", "#c:is_nether")
        );
        com.endlessepoch.core.api.machine.MachineReg.BLOCKS.register(modEventBus);
        com.endlessepoch.core.api.machine.MachineReg.BLOCK_ENTITY.register(modEventBus);
        com.endlessepoch.core.api.machine.MachineReg.flushBE();
        Menus.MENUS.register(modEventBus);
        EECoreRecipeTypes.RECIPE_TYPES.register(modEventBus);
        EECoreRecipeTypes.RECIPE_SERIALIZERS.register(modEventBus);
        com.endlessepoch.core.registry.Fluids.FLUIDS.register(modEventBus);
        com.endlessepoch.core.registry.Fluids.FLUID_TYPES.register(modEventBus);
        CREATIVE_TABS.register(modEventBus);

        modEventBus.addListener(this::commonSetup);
        modEventBus.addListener(this::registerCapabilities);
        modEventBus.addListener(this::registerPayloadHandlers);
        modEventBus.addListener(EECore::onBuildCreativeTab);

        // Register machine items + tag bindings / 注册机器物品 + 标签绑定
        EECoreMachines.init(modEventBus);
        NeoForge.EVENT_BUS.register(com.endlessepoch.core.api.multiblock.MultiBlockBreakDetector.class);
        NeoForge.EVENT_BUS.addListener(com.endlessepoch.core.event.BlockPlaceHandler::onBlockPlace);
        NeoForge.EVENT_BUS.register(com.endlessepoch.core.event.AePatternRestoreHandler.class);
        NeoForge.EVENT_BUS.addListener(EECoreCommands::onRegisterCommands);
        NeoForge.EVENT_BUS.addListener(com.endlessepoch.core.api.multiblock.PatternStorage::onServerStarting);
        // Anti-xray proximity reveal & cleanup / 反矿透靠近揭示+离开清理
        NeoForge.EVENT_BUS.addListener(com.endlessepoch.core.antixray.ProximityRevealer::onServerTick);
        NeoForge.EVENT_BUS.addListener(com.endlessepoch.core.antixray.ProximityRevealer::onPlayerLeave);
        // Recipe snapshot cache preload / 配方快照缓存预热
        NeoForge.EVENT_BUS.addListener(com.endlessepoch.core.api.recipe.RecipeSnapshotCache::onServerStarted);
        // Phase 3 global driver: CPU/TPS guards + write-back budget + machine pumps / Phase3 全局驱动点
        NeoForge.EVENT_BUS.addListener(com.endlessepoch.core.api.energy.eb.batch.Phase3Driver::onServerTickPost);
        // Phase 3 clean shutdown: pools + batch queues / Phase3 干净关机
        NeoForge.EVENT_BUS.addListener(com.endlessepoch.core.api.energy.eb.batch.Phase3Shutdown::onServerStopping);
        // Celestial halo effect / 日月星辰特效 (Tesselator)
        NeoForge.EVENT_BUS.register(com.endlessepoch.core.nova.client.CelestialRenderer.class);
        // Break detection handled by MachineControllerBlockEntity.serverTick polling / 破坏检测由BE轮询处理

        // Flush block tags + lang JSON generated during registration / 写入注册期间收集的标签和翻译
        com.endlessepoch.core.registry.ResourceGenerator.flushTags(Items.TAG_BLOCKS);
        com.endlessepoch.core.registry.ResourceGenerator.flushItemTags(Items.TAG_ITEMS);
        com.endlessepoch.core.registry.ResourceGenerator.flushLang(MOD_ID,
                com.endlessepoch.core.registry.ResourceGenerator.PROJECT_ROOT);
        com.endlessepoch.core.registry.ResourceGenerator.flushTrans(MOD_ID,
                com.endlessepoch.core.registry.OreRegistry.TRANS_EN
                        .getOrDefault(MOD_ID, java.util.Collections.emptyMap()),
                com.endlessepoch.core.registry.OreRegistry.TRANS_ZH
                        .getOrDefault(MOD_ID, java.util.Collections.emptyMap()));
    }

    /**
     * Place as much of the stack as fits into inventory slots (merge + empty slots),
     * returning the remainder. Manual placement — addItem swallows overflow in NeoForge.
     * 手动放置物品到背包（合并已有堆+空槽），返回剩余——NeoForge 的 addItem 会吞掉放不下的部分。
     */
    private static net.minecraft.world.item.ItemStack placeInInventory(net.minecraft.world.entity.player.Player player,
                                                                       net.minecraft.world.item.ItemStack stack) {
        var inv = player.getInventory();
        for (int i = 0; i < inv.items.size() && !stack.isEmpty(); i++) {
            var ex = inv.items.get(i);
            if (!ex.isEmpty() && net.minecraft.world.item.ItemStack.isSameItemSameComponents(stack, ex)) {
                int space = Math.min(stack.getCount(), ex.getMaxStackSize() - ex.getCount());
                if (space > 0) { ex.grow(space); stack.shrink(space); }
            }
        }
        for (int i = 0; i < inv.items.size() && !stack.isEmpty(); i++) {
            if (inv.items.get(i).isEmpty()) {
                int max = stack.getMaxStackSize();
                if (stack.getCount() <= max) { inv.items.set(i, stack.copy()); stack.setCount(0); }
                else { inv.items.set(i, stack.split(max)); }
            }
        }
        return stack;
    }

    /**
     * Populate the MACHINES_TAB with dynamically registered machine items.
     * <p>
     * 将动态注册的机器物品添加到机器创造标签页。
     */
    public static void onBuildCreativeTab(BuildCreativeModeTabContentsEvent event) {
        if (event.getTab() == MACHINES_TAB.get()) {
            for (var sup : Items.MACHINE_ITEMS) {
                event.accept(sup.get());
            }
        }
    }

    /**
     * Common setup: initialize NovaNet node registry and multiblock controller block.
     * <p>
     * 通用初始化：注册 NovaNet 节点系统和多方块控制器方块。
     */
    private void commonSetup(FMLCommonSetupEvent event) {
        // Controller BE types must tick (break detection etc.) — addons register theirs too.
        // 控制器 BE 类型必须 tick（破坏检测等）——附属自定义控制器同样注册。
        com.endlessepoch.core.nova.block.MachineControllerBlock.registerControllerType(BlockEntities.MACHINE_CONTROLLER.get());
        com.endlessepoch.core.nova.block.MachineControllerBlock.registerControllerType(BlockEntities.DISPATCH_CONTROLLER.get());
        NovaNetRegistry reg = new NovaNetRegistry();
        NovaNodeRegistration.init(reg);

        MultiBlockRegistry.registerControllerBlock(com.endlessepoch.core.registry.Blocks.SCANNER_CONTROLLER.get());
        MultiBlockRegistry.registerControllerBlock(com.endlessepoch.core.registry.Blocks.MACHINE_CONTROLLER.get());

        com.endlessepoch.core.api.multiblock.MachineRegistry.autoRegisterAll();

        // Auto-include AE interface in all machine patterns (limit 1 per machine) / 所有机器自动添加AE接口（上限1）
        var aeBlock = com.endlessepoch.core.registry.Blocks.AE_INTERFACE.get();
        var aeState = aeBlock.defaultBlockState();
        for (var def : com.endlessepoch.core.api.multiblock.MachineRegistry.getAll()) {
            var patOpt = def.getPattern();
            if (patOpt.isEmpty()) continue;
            var pattern = patOpt.get();
            for (char c : pattern.getDefinitions().keySet()) {
                if (!pattern.getTags(c).isEmpty())
                    pattern.addAlternatives(c, aeState);
            }
            // Per-block limit = 1 through every tag on this pattern / 每个标签限1个
            for (char c : pattern.getDefinitions().keySet()) {
                for (String tag : pattern.getTags(c))
                    pattern.setBlockLimit(tag, aeBlock, 1);
            }
        }

        LOGGER.info(MOD_NAME + " initialized");
        LOGGER.info("Omega system: 12 tiers ELV~QV, 1Ω = 2FE");
        LOGGER.info("NovaNet: node registry active, test multiblock registered");

        // Register built-in machine types / 注册内置机器种类
        com.endlessepoch.core.api.machine.MachineTypeRegistry.register(
                com.endlessepoch.core.api.machine.MachineType.of(
                        EECore.MOD_ID, "furnace",
                        net.minecraft.world.item.crafting.RecipeType.SMELTING,
                        net.minecraft.world.level.block.Blocks.FURNACE,
                        "eecore.profile.furnace", (be) -> {}));
        com.endlessepoch.core.api.machine.MachineTypeRegistry.register(
                com.endlessepoch.core.api.machine.MachineType.of(
                        EECore.MOD_ID, "blast_furnace",
                        net.minecraft.world.item.crafting.RecipeType.BLASTING,
                        net.minecraft.world.level.block.Blocks.BLAST_FURNACE,
                        "eecore.profile.blast_furnace", (be) -> {}));
        com.endlessepoch.core.api.machine.MachineTypeRegistry.register(
                com.endlessepoch.core.api.machine.MachineType.of(
                        EECore.MOD_ID, "machine",
                        EECoreRecipeTypes.MACHINE.get(),
                        net.minecraft.world.level.block.Blocks.FURNACE,
                        "eecore.profile.machine", (be) -> {}));
        com.endlessepoch.core.api.machine.MachineTypeRegistry.register(
                com.endlessepoch.core.api.machine.MachineType.of(
                        EECore.MOD_ID, "boiler",
                        EECoreRecipeTypes.BOILER.get(),
                        net.minecraft.world.level.block.Blocks.BLAST_FURNACE,
                        "eecore.profile.boiler", (be) -> {}));

        // Register built-in recipe types for batch pipeline / 注册内置配方类型到批处理管线
        com.endlessepoch.core.api.recipe.RecipeSnapshotCache.register(
                EECoreRecipeTypes.MACHINE.get(),
                (id, recipe) -> recipe instanceof com.endlessepoch.core.api.recipe.AbstractMachineRecipe amr
                        ? com.endlessepoch.core.api.recipe.RecipeSnapshot.from(amr, id)
                        : null);
    }

    /**
     * Register omega energy capabilities for creative generator, consumer, and test transmitter.
     * <p>
     * 为创造模式发电机、消耗器和测试发射器注册 Omega 能量能力。
     */
    private void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(
                EECoreCapabilities.OMEGA_ENERGY,
                BlockEntities.CREATIVE_GENERATOR.get(),
                (be, side) -> be
        );
        event.registerBlockEntity(
                EECoreCapabilities.OMEGA_ENERGY,
                BlockEntities.MACHINE_CONTROLLER.get(),
                (be, side) -> ((com.endlessepoch.core.nova.block.MachineControllerBlockEntity) be).getEnergyStorage()
        );
        event.registerBlockEntity(
                EECoreCapabilities.OMEGA_ENERGY,
                BlockEntities.CREATIVE_CONSUMER.get(),
                (be, side) -> be
        );

        event.registerBlockEntity(
                EECoreCapabilities.OMEGA_ENERGY,
                BlockEntities.TEST_TRANSMITTER.get(),
                (be, side) -> be
        );

        // AE2 grid node host — cables discover devices via this capability, not instanceof
        // ME 端口网格节点宿主——AE2 线缆通过此 Capability 发现设备（非 instanceof）
        event.registerBlockEntity(
                appeng.api.AECapabilities.IN_WORLD_GRID_NODE_HOST,
                BlockEntities.PART.get(),
                (be, side) -> be instanceof com.endlessepoch.core.nova.block.part.DispatchMePortBlockEntity
                        ? (appeng.api.networking.IInWorldGridNodeHost) be : null
        );

        // Item handler for every bus-type part (creative & addon variants included) —
        // automation gets the direction-restricted view: input insert-only, output extract-only.
        // 全部总线类部件的物品能力（含创造与附属变体）——自动化侧走方向受限视图：
        // 输入只进、输出只出。
        event.registerBlock(
                net.neoforged.neoforge.capabilities.Capabilities.ItemHandler.BLOCK,
                (level, pos, state, be, side) -> {
                    if (be instanceof com.endlessepoch.core.nova.block.part.InputBusBlockEntity ib)
                        return ib.getAutomationHandler();
                    return null;
                },
                com.endlessepoch.core.registry.Blocks.PART_BLOCKS.stream()
                        .map(java.util.function.Supplier::get)
                        .filter(b -> b instanceof com.endlessepoch.core.nova.block.part.PartBlock pb
                                && com.endlessepoch.core.nova.block.part.PartBlock.isBusType(pb.getPartType()))
                        .toArray(net.minecraft.world.level.block.Block[]::new)
        );

        // Omega energy for every energy hatch (creative & addon variants included)
        // 全部能源仓的能量能力（含创造与附属变体）
        event.registerBlock(
                com.endlessepoch.core.api.EECoreCapabilities.OMEGA_ENERGY,
                (level, pos, state, be, side) -> {
                    if (be instanceof com.endlessepoch.core.nova.block.part.PartBlockEntity pe)
                        return pe.getEnergyStorage();
                    return null;
                },
                com.endlessepoch.core.registry.Blocks.PART_BLOCKS.stream()
                        .map(java.util.function.Supplier::get)
                        .filter(b -> b instanceof com.endlessepoch.core.nova.block.part.PartBlock pb
                                && energyCapablePart(pb.getPartType()))
                        .toArray(net.minecraft.world.level.block.Block[]::new)
        );

        // Fluid handler for fluid hatches + assemblies / 流体仓/总成流体能力
        event.registerBlock(
                net.neoforged.neoforge.capabilities.Capabilities.FluidHandler.BLOCK,
                (level, pos, state, be, side) -> {
                    if (be instanceof com.endlessepoch.core.nova.block.part.PartBlockEntity pe)
                        return pe.getFluidHandler();
                    return null;
                },
                com.endlessepoch.core.registry.Blocks.PART_BLOCKS.stream()
                        .map(java.util.function.Supplier::get)
                        .filter(b -> b instanceof com.endlessepoch.core.nova.block.part.PartBlock pb
                                && fluidCapablePart(pb.getPartType()))
                        .toArray(net.minecraft.world.level.block.Block[]::new)
        );
    }

    /** Fluid-capable part suffixes (creative & addon variants included). / 具备流体能力的部件后缀（含创造与附属变体）。 */
    private static boolean fluidCapablePart(com.endlessepoch.core.api.multiblock.PartType type) {
        String p = type.getId().getPath();
        return p.endsWith("fluid_input") || p.endsWith("fluid_output")
                || p.endsWith("input_assembly") || p.endsWith("output_assembly")
                || p.endsWith("input_bin");
    }

    /** Energy-capable part suffixes. / 具备能量能力的部件后缀。 */
    private static boolean energyCapablePart(com.endlessepoch.core.api.multiblock.PartType type) {
        String p = type.getId().getPath();
        return p.endsWith("energy_input") || p.endsWith("energy_output");
    }

    /**
     * Register network payload handlers for sync packets (generator, consumer, pattern, multiblock-vis).
     * <p>
     * 注册网络载荷处理器，处理发电机、消耗器、模式及多方块预览的同步包。
     */
    private void registerPayloadHandlers(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1");

        registrar.playToClient(
                SyncGeneratorPacket.TYPE,
                SyncGeneratorPacket.STREAM_CODEC,
                (payload, context) -> {
                    context.enqueueWork(() -> {
                        var level = context.player().level();
                        var be = level.getBlockEntity(payload.pos());
                        if (be instanceof CreativeGeneratorBlockEntity genBe) {
                            genBe.updateFromSync(payload);
                            level.sendBlockUpdated(payload.pos(), be.getBlockState(), be.getBlockState(), 3);
                        }
                    });
                }
        );

        registrar.playToClient(
                SyncConsumerPacket.TYPE,
                SyncConsumerPacket.STREAM_CODEC,
                (payload, context) -> {
                    context.enqueueWork(() -> {
                        var level = context.player().level();
                        var be = level.getBlockEntity(payload.pos());
                        if (be instanceof CreativeConsumerBlockEntity consumerBe) {
                            consumerBe.updateFromSync(payload);
                            level.sendBlockUpdated(payload.pos(), be.getBlockState(), be.getBlockState(), 3);
                        }
                    });
                }
        );

        registrar.playToClient(
                SyncPatternPacket.TYPE,
                SyncPatternPacket.STREAM_CODEC,
                (payload, context) -> {
                    context.enqueueWork(() -> {
                        com.endlessepoch.core.api.multiblock.MultiBlockRegistry.registerLocal(
                                context.player().getUUID(),
                                payload.patternId(),
                                payload.toPattern()
                        );
                    });
                }
        );

        registrar.playToClient(
                SyncPatternBinaryPacket.TYPE,
                SyncPatternBinaryPacket.STREAM_CODEC,
                (payload, context) -> {
                    context.enqueueWork(() -> {
                        com.endlessepoch.core.api.multiblock.MultiBlockRegistry.registerLocal(
                                context.player().getUUID(),
                                payload.patternId(),
                                payload.toPattern()
                        );
                    });
                }
        );

        registrar.playToClient(
                OpenMbVisPacket.TYPE,
                OpenMbVisPacket.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> ClientPacketHandlers.openMbVis(payload))
        );

        registrar.playToClient(
                SyncValidationPacket.TYPE,
                SyncValidationPacket.STREAM_CODEC,
                (payload, context) -> SyncValidationPacket.handle(payload, context)
        );

        registrar.playToClient(
                com.endlessepoch.core.network.FluidSyncPacket.TYPE,
                com.endlessepoch.core.network.FluidSyncPacket.CODEC,
                (payload, context) -> com.endlessepoch.core.network.FluidSyncPacket.handle(payload, context)
        );

        registrar.playToClient(
                com.endlessepoch.core.network.EnergySyncPacket.TYPE,
                com.endlessepoch.core.network.EnergySyncPacket.CODEC,
                (payload, context) -> com.endlessepoch.core.network.EnergySyncPacket.handle(payload, context)
        );

        // C2S: creative bus ghost template (JEI drag) / 创造总线幻影模板（JEI 拖拽）
        registrar.playToServer(
                com.endlessepoch.core.network.SetGhostSlotPacket.TYPE,
                com.endlessepoch.core.network.SetGhostSlotPacket.CODEC,
                (payload, context) -> context.enqueueWork(
                        () -> com.endlessepoch.core.network.SetGhostSlotPacket.handle(payload, context))
        );

        // C2S: creative parallel hatch typed value / 创造并行仓输入数值
        registrar.playToServer(
                com.endlessepoch.core.network.SetParallelPacket.TYPE,
                com.endlessepoch.core.network.SetParallelPacket.CODEC,
                (payload, context) -> context.enqueueWork(
                        () -> com.endlessepoch.core.network.SetParallelPacket.handle(payload, context))
        );

        // C2S: creative fluid hatch template (JEI drag) / 创造流体仓模板（JEI 拖拽）
        registrar.playToServer(
                com.endlessepoch.core.network.SetGhostFluidPacket.TYPE,
                com.endlessepoch.core.network.SetGhostFluidPacket.CODEC,
                (payload, context) -> context.enqueueWork(
                        () -> com.endlessepoch.core.network.SetGhostFluidPacket.handle(payload, context))
        );

        // C2S: creative bus template count / 创造总线模板数量
        registrar.playToServer(
                com.endlessepoch.core.network.SetGhostCountPacket.TYPE,
                com.endlessepoch.core.network.SetGhostCountPacket.CODEC,
                (payload, context) -> context.enqueueWork(
                        () -> com.endlessepoch.core.network.SetGhostCountPacket.handle(payload, context))
        );
        registrar.playToServer(
                com.endlessepoch.core.network.SetCircuitPacket.TYPE,
                com.endlessepoch.core.network.SetCircuitPacket.CODEC,
                (payload, context) -> context.enqueueWork(
                        () -> com.endlessepoch.core.network.SetCircuitPacket.handle(payload, context))
        );
        registrar.playToServer(
                com.endlessepoch.core.network.SetGridDensityPacket.TYPE,
                com.endlessepoch.core.network.SetGridDensityPacket.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(
                        () -> com.endlessepoch.core.network.SetGridDensityPacket.handle(payload, context))
        );
        registrar.playToClient(
                com.endlessepoch.core.network.GridIncrementalUpdatePacket.TYPE,
                com.endlessepoch.core.network.GridIncrementalUpdatePacket.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> {
                    var mc = net.minecraft.client.Minecraft.getInstance();
                    if (mc.screen instanceof com.endlessepoch.core.screen.DispatchScreen ds)
                        ds.onGridUpdate(payload);
                })
        );

        registrar.playToClient(
                com.endlessepoch.core.network.PrefPacket.TYPE,
                com.endlessepoch.core.network.PrefPacket.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> {
                    var mc = net.minecraft.client.Minecraft.getInstance();
                    if (mc.screen instanceof com.endlessepoch.core.screen.DispatchScreen ds)
                        ds.onPref(payload);
                })
        );
        registrar.playToServer(
                com.endlessepoch.core.network.SetPrefPacket.TYPE,
                com.endlessepoch.core.network.SetPrefPacket.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> {
                    var player = context.player();
                    if (player == null || !(player.containerMenu instanceof com.endlessepoch.core.menu.DispatchMenu dm)) return;
                    var be = dm.getBE();
                    if (be != null)
                        be.setPlayerPref(player.getUUID(), new int[]{payload.sortMode(), payload.sortAsc(), payload.displayMode()});
                })
        );

        // C2S: grid click — cursor extract (0 stack / 1 one), trash delete (2), all to inventory (3), insert carried (4)
        // 网格点击：提取到鼠标(0/1)、垃圾桶删除(2)、全部到背包(3)、携带放入网络(4)
        registrar.playToServer(
                com.endlessepoch.core.network.GridClickPacket.TYPE,
                com.endlessepoch.core.network.GridClickPacket.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> {
                    var player = context.player();
                    if (player == null || !(player.containerMenu instanceof com.endlessepoch.core.menu.DispatchMenu dm)) return;
                    var be = dm.getBE();
                    if (!(be instanceof com.endlessepoch.core.nova.block.DispatchCenterBlockEntity dc) || !dc.isFormed()) return;
                    if (player.distanceToSqr(be.getBlockPos().getCenter()) > 64) return;
                    var grid = dc.getGrid();
                    if (grid == null) return;
                    var storage = grid.getStorageService().getInventory();
                    var src = appeng.api.networking.security.IActionSource.ofPlayer(player);
                    if (payload.mode() == 6) {
                        // Shift+double-click on an inventory slot: deposit every matching stack
                        // 背包 Shift+双击：背包里所有同物品一起存入网络
                        com.endlessepoch.core.EECore.LOGGER.info("[Dispatch] mode6 deposit: {}", payload.key());
                        var target = payload.key();
                        var inv = player.getInventory();
                        for (int i = 0; i < inv.items.size(); i++) {
                            var st = inv.items.get(i);
                            if (st.isEmpty()) continue;
                            if (com.endlessepoch.core.nova.item.CollapseCoreItem.isCore(st)) {
                                var rem = com.endlessepoch.core.nova.item.CollapseCoreItem.unpack(storage, st, src, player.level().registryAccess());
                                inv.items.set(i, rem.isEmpty() ? net.minecraft.world.item.ItemStack.EMPTY : rem);
                                continue;
                            }
                            if (appeng.api.stacks.AEItemKey.of(st).equals(target)) {
                                long inserted = storage.insert(target, st.getCount(),
                                        appeng.api.config.Actionable.MODULATE, src);
                                if (inserted > 0) st.shrink((int) inserted);
                                if (st.isEmpty()) inv.items.set(i, net.minecraft.world.item.ItemStack.EMPTY);
                            }
                        }
                        return;
                    }
                    if (payload.mode() == 5) {
                        // RMB on a fluid bucket: pour fluid into the network, keep the empty bucket
                        // 右键流体桶：倒入网络，空桶留在手上
                        var carried = dm.getCarried();
                        if (carried.isEmpty()) return;
                        var contained = net.neoforged.neoforge.fluids.FluidUtil.getFluidContained(carried);
                        if (!contained.isPresent() || contained.get().isEmpty()) return;
                        var fs = contained.get();
                        var fk = appeng.api.stacks.AEFluidKey.of(fs);
                        long inserted = storage.insert(fk, fs.getAmount(),
                                appeng.api.config.Actionable.MODULATE, src);
                        if (inserted >= fs.getAmount())
                            dm.setCarried(new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.BUCKET));
                        else if (inserted > 0)
                            player.displayClientMessage(net.minecraft.network.chat.Component.literal("网络空间不足，只存入了 " + inserted + " mB"), true);
                        return;
                    }
                    if (payload.mode() == 4) {
                        // Insert carried into the network — amount: all (LMB) or one (RMB)
                        // 携带物品放入网络——数量：全存（左键）或存一个（右键）
                        var carried = dm.getCarried();
                        if (carried.isEmpty()) return;
                        if (com.endlessepoch.core.nova.item.CollapseCoreItem.isCore(carried)) {
                            dm.setCarried(com.endlessepoch.core.nova.item.CollapseCoreItem.unpack(storage, carried, src, player.level().registryAccess()));
                            return;
                        }
                        var key = appeng.api.stacks.AEItemKey.of(carried);
                        long inserted = storage.insert(key, payload.amount(),
                                appeng.api.config.Actionable.MODULATE, src);
                        if (inserted > 0) carried.shrink((int) inserted);
                        return;
                    }
                    if (payload.key() instanceof appeng.api.stacks.AEFluidKey fk) {
                        // Fluid extraction needs an empty bucket: hand → inventory → network. Silent block.
                        // 取流体需要空桶：手持 → 背包 → 网络。无桶静默阻止。
                        var bucketItem = fk.getFluid().getBucket();
                        if (bucketItem == null || bucketItem == net.minecraft.world.item.Items.AIR) return;
                        net.minecraft.world.item.ItemStack bucket = null;
                        var carriedB = dm.getCarried();
                        if (carriedB.is(net.minecraft.world.item.Items.BUCKET)) bucket = carriedB;
                        else {
                            var hand = player.getMainHandItem();
                            if (hand.is(net.minecraft.world.item.Items.BUCKET)) bucket = hand;
                            else {
                                for (var st : player.getInventory().items)
                                    if (st.is(net.minecraft.world.item.Items.BUCKET)) { bucket = st; break; }
                            }
                        }
                        if (bucket == null) {
                            var counter = new appeng.api.stacks.KeyCounter();
                            storage.getAvailableStacks(counter);
                            for (var e : counter) {
                                if (e.getKey() instanceof appeng.api.stacks.AEItemKey ik
                                        && ik.getItem() == net.minecraft.world.item.Items.BUCKET
                                        && storage.extract(ik, 1, appeng.api.config.Actionable.MODULATE, src) == 1) {
                                    bucket = ik.toStack(1);
                                    break;
                                }
                            }
                        }
                        if (bucket == null) return;
                        // Take the fluid first. Single bucket → filled bucket to the cursor;
                        // multiple buckets → filled bucket into the inventory, the stack shrinks in place.
                        // 先提取流体。单桶 → 流体桶到鼠标；多桶 → 流体桶进背包，原桶堆原地扣 1。
                        if (storage.extract(fk, 1000, appeng.api.config.Actionable.MODULATE, src) < 1000) return;
                        var fb = new net.minecraft.world.item.ItemStack(bucketItem);
                        if (bucket.getCount() <= 1) {
                            bucket.shrink(1);
                            dm.setCarried(fb);
                        } else {
                            if (!player.addItem(fb)) {
                                storage.insert(fk, 1000, appeng.api.config.Actionable.MODULATE, src);
                                return;
                            }
                            bucket.shrink(1);
                        }
                        return;
                    }
                    if (payload.mode() == 2) {
                        // Trash: extract and void — never into the inventory / 垃圾桶：提取后虚空，绝不进背包
                        long got = storage.extract(payload.key(), Long.MAX_VALUE,
                                appeng.api.config.Actionable.MODULATE, src);
                        com.endlessepoch.core.EECore.LOGGER.info("[Dispatch] trash delete: {} removed={}", payload.key(), got);
                        return;
                    }
                    long amount = payload.amount();
                    long got = storage.extract(payload.key(), amount,
                            appeng.api.config.Actionable.MODULATE, src);
                    if (got <= 0) return;
                    if (payload.key() instanceof appeng.api.stacks.AEItemKey ik) {
                        if (payload.mode() == 0 || payload.mode() == 1) {
                            dm.setCarried(ik.toStack((int) got));
                        } else {
                            // Shift+click: extract all — place manually into inventory slots
                            // (addItem swallows overflow in NeoForge), return the rest to the network.
                            // Shift 全取：手动放置到背包槽（NeoForge addItem 会吞掉放不下的部分），剩余插回网络。
                            var stack = ik.toStack((int) Math.min(got, Integer.MAX_VALUE));
                            var left = placeInInventory(player, stack);
                            if (!left.isEmpty())
                                storage.insert(ik, left.getCount(), appeng.api.config.Actionable.MODULATE, src);
                            com.endlessepoch.core.EECore.LOGGER.info("[Dispatch] shift extract: got={} leftInInv={}", got, got - left.getCount());
                        }
                    }
                })
        );
    }
}
