package com.endlessepoch.core;

import com.endlessepoch.core.api.EECoreCapabilities;
import com.endlessepoch.core.api.client.EmissiveHelper;
import com.endlessepoch.core.api.multiblock.MultiBlockRegistry;
import com.endlessepoch.core.api.registry.NovaNetRegistry;
import com.endlessepoch.core.api.tier.VoltageTier;
import com.endlessepoch.core.command.EECoreCommands;
import com.endlessepoch.core.nova.client.ClientPacketHandlers;
import com.endlessepoch.core.nova.network.node.NovaNodeRegistration;
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
    public static final String VERSION = "0.1.0";

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

    public EECore(IEventBus modEventBus, ModContainer container) {
        LOGGER.info(MOD_NAME + " v" + VERSION + " 加载中...");
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
        Menus.MENUS.register(modEventBus);
        EECoreRecipeTypes.RECIPE_TYPES.register(modEventBus);
        EECoreRecipeTypes.RECIPE_SERIALIZERS.register(modEventBus);
        CREATIVE_TABS.register(modEventBus);

        modEventBus.addListener(this::commonSetup);
        modEventBus.addListener(this::registerCapabilities);
        modEventBus.addListener(this::registerPayloadHandlers);
        modEventBus.addListener(EECore::onBuildCreativeTab);

        // Register machine items before DeferredRegister freezes / 注册机器物品
        EECoreMachines.registerAll();
        NeoForge.EVENT_BUS.addListener(com.endlessepoch.core.event.BlockPlaceHandler::onBlockPlace);
        NeoForge.EVENT_BUS.addListener(EECoreCommands::onRegisterCommands);
        NeoForge.EVENT_BUS.addListener(com.endlessepoch.core.api.multiblock.PatternStorage::onServerStarting);
        // Ghost preview for validation failures / 成形失败幽灵预览
        NeoForge.EVENT_BUS.register(com.endlessepoch.core.nova.client.WorldPreviewManager.get());
        // Celestial halo effect for formed controllers / 日月星辰特效
        NeoForge.EVENT_BUS.register(com.endlessepoch.core.nova.client.CelestialRenderer.class);
        // Break detection handled by MachineControllerBlockEntity.serverTick polling / 破坏检测由BE轮询处理

        // Flush block tags + lang JSON generated during registration / 写入注册期间收集的标签和翻译
        com.endlessepoch.core.registry.ResourceGenerator.flushTags(Items.TAG_BLOCKS);
        com.endlessepoch.core.registry.ResourceGenerator.flushLang(MOD_ID,
                com.endlessepoch.core.registry.ResourceGenerator.PROJECT_ROOT);
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
        NovaNetRegistry reg = new NovaNetRegistry();
        NovaNodeRegistration.init(reg);

        MultiBlockRegistry.registerControllerBlock(com.endlessepoch.core.registry.Blocks.SCANNER_CONTROLLER.get());
        MultiBlockRegistry.registerControllerBlock(com.endlessepoch.core.registry.Blocks.MACHINE_CONTROLLER.get());

        // Apply tag bindings (requires bound blocks) / 标签绑定（需要已注册的方块）
        EECoreMachines.applyBindings();
        com.endlessepoch.core.api.multiblock.MachineRegistry.autoRegisterAll();

        LOGGER.info(MOD_NAME + " initialized");
        LOGGER.info("Omega system: 12 tiers ELV~QV, 1Ω = 2FE");
        LOGGER.info("NovaNet: node registry active, test multiblock registered");

        // Register built-in machine profiles / 注册内置机器种类
        com.endlessepoch.core.api.machine.MachineProfileRegistry.register(
                com.endlessepoch.core.api.machine.MachineProfile.of(
                        EECore.MOD_ID, "furnace",
                        net.minecraft.world.item.crafting.RecipeType.SMELTING,
                        net.minecraft.world.level.block.Blocks.FURNACE,
                        "eecore.profile.furnace"));
        com.endlessepoch.core.api.machine.MachineProfileRegistry.register(
                com.endlessepoch.core.api.machine.MachineProfile.of(
                        EECore.MOD_ID, "blast_furnace",
                        net.minecraft.world.item.crafting.RecipeType.BLASTING,
                        net.minecraft.world.level.block.Blocks.BLAST_FURNACE,
                        "eecore.profile.blast_furnace"));
        // Custom EECore recipe type / 自定义配方类型
        com.endlessepoch.core.api.machine.MachineProfileRegistry.register(
                com.endlessepoch.core.api.machine.MachineProfile.of(
                        EECore.MOD_ID, "machine",
                        EECoreRecipeTypes.MACHINE.get(),
                        net.minecraft.world.level.block.Blocks.FURNACE,
                        "eecore.profile.machine"));
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

        // Item handler for input/output buses / 物品能力（输入/输出总线 + 总成）
        event.registerBlock(
                net.neoforged.neoforge.capabilities.Capabilities.ItemHandler.BLOCK,
                (level, pos, state, be, side) -> {
                    if (be instanceof com.endlessepoch.core.nova.block.part.InputBusBlockEntity ib)
                        return ib.getInventory();
                    return null;
                },
                com.endlessepoch.core.registry.Blocks.INPUT_BUS.get(),
                com.endlessepoch.core.registry.Blocks.OUTPUT_BUS.get(),
                com.endlessepoch.core.registry.Blocks.INPUT_ASSEMBLY.get(),
                com.endlessepoch.core.registry.Blocks.OUTPUT_ASSEMBLY.get()
        );

        // Omega energy for energy hatches / 能源仓能量能力
        event.registerBlock(
                com.endlessepoch.core.api.EECoreCapabilities.OMEGA_ENERGY,
                (level, pos, state, be, side) -> {
                    if (be instanceof com.endlessepoch.core.nova.block.part.PartBlockEntity pe)
                        return pe.getEnergyStorage();
                    return null;
                },
                com.endlessepoch.core.registry.Blocks.ENERGY_INPUT.get(),
                com.endlessepoch.core.registry.Blocks.ENERGY_OUTPUT.get()
        );

        // Fluid handler for fluid hatches + assemblies / 流体仓/总成流体能力
        event.registerBlock(
                net.neoforged.neoforge.capabilities.Capabilities.FluidHandler.BLOCK,
                (level, pos, state, be, side) -> {
                    if (be instanceof com.endlessepoch.core.nova.block.part.PartBlockEntity pe)
                        return pe.getFluidTank();
                    return null;
                },
                com.endlessepoch.core.registry.Blocks.FLUID_INPUT.get(),
                com.endlessepoch.core.registry.Blocks.FLUID_OUTPUT.get(),
                com.endlessepoch.core.registry.Blocks.INPUT_ASSEMBLY.get(),
                com.endlessepoch.core.registry.Blocks.OUTPUT_ASSEMBLY.get()
        );
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
    }
}
