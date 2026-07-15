package com.endlessepoch.core.registry;

import com.endlessepoch.core.EECore;
import com.endlessepoch.core.block.OreBlockEntity;
import com.endlessepoch.core.blockentity.creative.CreativeConsumerBlockEntity;
import com.endlessepoch.core.blockentity.creative.CreativeGeneratorBlockEntity;
import com.endlessepoch.core.nova.block.MachineControllerBlockEntity;
import com.endlessepoch.core.nova.block.ScannerControllerBlockEntity;
import com.endlessepoch.core.nova.block.part.PartBlockEntity;
import com.endlessepoch.core.nova.network.transmitter.TransmitterBlockEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

/** Block entity type registry for EECore / 方块实体类型注册表。 */
public class BlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, EECore.MOD_ID);

    public static final Supplier<BlockEntityType<CreativeGeneratorBlockEntity>> CREATIVE_GENERATOR =
            BLOCK_ENTITIES.register("creative_generator",
                    () -> BlockEntityType.Builder.of(CreativeGeneratorBlockEntity::new,
                            Blocks.CREATIVE_GENERATOR.get()).build(null)
            );

    public static final Supplier<BlockEntityType<CreativeConsumerBlockEntity>> CREATIVE_CONSUMER =
            BLOCK_ENTITIES.register("creative_consumer",
                    () -> BlockEntityType.Builder.of(CreativeConsumerBlockEntity::new,
                            Blocks.CREATIVE_CONSUMER.get()).build(null)
            );

    public static final Supplier<BlockEntityType<ScannerControllerBlockEntity>> SCANNER_CONTROLLER =
            BLOCK_ENTITIES.register("scanner_controller",
                    () -> BlockEntityType.Builder.of(ScannerControllerBlockEntity::new,
                            Blocks.SCANNER_CONTROLLER.get()).build(null)
            );

    public static final Supplier<BlockEntityType<MachineControllerBlockEntity>> MACHINE_CONTROLLER =
            BLOCK_ENTITIES.register("machine_controller",
                    () -> BlockEntityType.Builder.of(MachineControllerBlockEntity::new,
                            Blocks.MACHINE_CONTROLLER.get()).build(null)
            );

    public static final Supplier<BlockEntityType<TransmitterBlockEntity>> TEST_TRANSMITTER =
            BLOCK_ENTITIES.register("test_transmitter",
                    () -> BlockEntityType.Builder.of(TransmitterBlockEntity::new,
                            Blocks.TEST_TRANSMITTER.get()).build(null)
            );

    public static final Supplier<BlockEntityType<PartBlockEntity>> PART =
            BLOCK_ENTITIES.register("part",
                    () -> {
                        var bb = Blocks.PART_BLOCKS.stream()
                                .map(Supplier::get).toArray(net.minecraft.world.level.block.Block[]::new);
                        return BlockEntityType.Builder.of((pos, state) -> {
                                    var block = state.getBlock();
                                    if (block instanceof EntityBlock eb)
                                        return (PartBlockEntity) eb.newBlockEntity(pos, state);
                                    return new PartBlockEntity(pos, state, com.endlessepoch.core.api.multiblock.PartType.CASING, 0);
                                }, bb).build(null);
                    }
            );

    public static final Supplier<BlockEntityType<OreBlockEntity>> ORE =
            BLOCK_ENTITIES.register("ore",
                    () -> BlockEntityType.Builder.of(OreBlockEntity::new,
                            com.endlessepoch.core.registry.OreRegistry.ORE_BLOCKS.stream()
                                    .map(Supplier::get).toArray(net.minecraft.world.level.block.Block[]::new)
                    ).build(null));
}