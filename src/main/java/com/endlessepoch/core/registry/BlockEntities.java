package com.endlessepoch.core.registry;

import com.endlessepoch.core.EECore;
import com.endlessepoch.core.blockentity.creative.CreativeConsumerBlockEntity;
import com.endlessepoch.core.blockentity.creative.CreativeGeneratorBlockEntity;
import com.endlessepoch.core.nova.block.ScannerControllerBlockEntity;
import com.endlessepoch.core.nova.network.transmitter.TransmitterBlockEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

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

    // ===== NovaNet test =====
    public static final Supplier<BlockEntityType<ScannerControllerBlockEntity>> SCANNER_CONTROLLER =
            BLOCK_ENTITIES.register("scanner_controller",
                    () -> BlockEntityType.Builder.of(ScannerControllerBlockEntity::new,
                            Blocks.SCANNER_CONTROLLER.get()).build(null)
            );

    public static final Supplier<BlockEntityType<TransmitterBlockEntity>> TEST_TRANSMITTER =
            BLOCK_ENTITIES.register("test_transmitter",
                    () -> BlockEntityType.Builder.of(TransmitterBlockEntity::new,
                            Blocks.TEST_TRANSMITTER.get()).build(null)
            );
}