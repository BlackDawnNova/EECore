package com.endlessepoch.core.api.machine;

import com.endlessepoch.core.EECore;
import com.endlessepoch.core.registry.ResourceGenerator;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * One-click single-block machine registration.
 * Registers Block + BlockEntity + BlockItem + model JSON + translation.
 * <p>
 * 单方块机器自动注册：方块+BE+物品+模型+翻译。
 */
public class MachineReg {

    public static final DeferredRegister<Block> BLOCKS =
            DeferredRegister.create(Registries.BLOCK, EECore.MOD_ID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITY =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, EECore.MOD_ID);

    /** All registered machine items for creative tab. / 所有已注册机器物品。 */
    public static final List<Supplier<BlockItem>> ITEMS = new ArrayList<>();

    /**
     * Register a machine. Block + BE + item + model all auto-generated.
     * @param id           registry name (e.g. "steam_boiler")
     * @param machineTypeId machine type ID (e.g. "eecore:boiler")
     * @param nameEn       English display name
     * @param nameZh       Chinese display name
     * @param tier         voltage tier (0-11)
     * @param color        map color for the block
     */
    public static Supplier<? extends Block> register(String id, String machineTypeId,
                                                      String nameEn, String nameZh,
                                                      int tier, MapColor color) {
        var block = BLOCKS.register(id, () -> new MachineBlock(
                BlockBehaviour.Properties.of().mapColor(color)
                        .strength(3f + tier * 3f, 6f + tier * 3f)
                        .requiresCorrectToolForDrops()
                        .noOcclusion()));
        final String finalTypeId = machineTypeId;
        var itemSupplier = com.endlessepoch.core.registry.Items.ITEMS.register(id, () ->
                new net.minecraft.world.item.BlockItem(block.get(), new Item.Properties().stacksTo(64)) {
                    @Override
                    public InteractionResult place(BlockPlaceContext ctx) {
                        InteractionResult result = super.place(ctx);
                        if (result.consumesAction() && !ctx.getLevel().isClientSide()) {
                            BlockEntity be = ctx.getLevel().getBlockEntity(ctx.getClickedPos());
                            if (be instanceof MachineBlockEntity mbe)
                                mbe.init(finalTypeId, tier);
                        }
                        return result;
                    }
                });
        ITEMS.add(() -> (BlockItem) itemSupplier.get());

        // Model + blockstate + lang / 模型+方块状态+翻译
        ResourceGenerator.writeJsonNs("eecore", "models/block", id,
                "{\"parent\":\"eecore:block/ee_base_12\"," +
                "\"textures\":{" +
                "\"particle\":\"eecore:block/casings/voltage/" +
                        com.endlessepoch.core.api.tier.VoltageTier.fromOrdinal(tier).name().toLowerCase() +
                        "/side\"," +
                "\"all\":\"eecore:block/casings/voltage/" +
                        com.endlessepoch.core.api.tier.VoltageTier.fromOrdinal(tier).name().toLowerCase() +
                        "/side\"," +
                "\"front\":\"eecore:block/parts/" + id + "/overlay_front\"}}");
        ResourceGenerator.writeJsonNs("eecore", "blockstates", id,
                "{\"variants\":{" +
                "\"facing=north\":{\"model\":\"eecore:block/" + id + "\",\"y\":0}," +
                "\"facing=east\":{\"model\":\"eecore:block/" + id + "\",\"y\":90}," +
                "\"facing=south\":{\"model\":\"eecore:block/" + id + "\",\"y\":180}," +
                "\"facing=west\":{\"model\":\"eecore:block/" + id + "\",\"y\":270}}}");

        com.endlessepoch.core.registry.PartReg.TRANS_EN
                .computeIfAbsent("eecore", k -> new java.util.LinkedHashMap<>())
                .put("block.eecore." + id, nameEn);
        com.endlessepoch.core.registry.PartReg.TRANS_ZH
                .computeIfAbsent("eecore", k -> new java.util.LinkedHashMap<>())
                .put("block.eecore." + id, nameZh);

        return block;
    }

    /** Register the shared BE type. Call after all blocks registered. / 所有方块注册后调用。 */
    @SuppressWarnings("unchecked")
    public static void flushBE() {
        var sup = BLOCK_ENTITY.register("machine", () ->
                BlockEntityType.Builder.of(MachineBlockEntity::new,
                        BLOCKS.getEntries().stream()
                                .map(Supplier::get).toArray(Block[]::new))
                        .build(null));
        MachineBlockEntity.typeSupplier = () -> (BlockEntityType<MachineBlockEntity>) sup.get();
    }
}
