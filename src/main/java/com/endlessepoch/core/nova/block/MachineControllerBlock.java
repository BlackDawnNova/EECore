package com.endlessepoch.core.nova.block;

import com.endlessepoch.core.EECore;
import com.endlessepoch.core.api.multiblock.MultiBlockFormHandler;
import com.endlessepoch.core.api.multiblock.MultiBlockRegistry;
import com.endlessepoch.core.registry.BlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootParams;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * Generic controller block for all multiblock machines registered via MultiblockLoader.
 * Machine identity is stored in the BlockEntity as a ResourceLocation.
 * <p>
 * MultiblockLoader 注册的通用多方块控制器。机器 ID 存在 BE 中。
 */
public class MachineControllerBlock extends Block implements EntityBlock {

    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    // 0-3 reserved for built-in models, 4-31 assigned to registered machines / 0-3内置,4-31机器
    public static final IntegerProperty MODEL = IntegerProperty.create("model", 0, 31);

    /** Machine itemId → model index for blockstate lookup / 机器 itemId → 模型索引 */
    private static final Map<String, Integer> MODEL_INDEX = new LinkedHashMap<>();
    private static int nextModelIndex = 4;

    public MachineControllerBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(MODEL, 0));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, MODEL);
    }

    /** Allocate a model index for a machine. / 为机器分配模型索引。 */
    public static int allocateModelIndex(String itemId) {
        return MODEL_INDEX.computeIfAbsent(itemId, k -> nextModelIndex++);
    }

    /** Get all allocated model indices. / 获取所有已分配的模型索引。 */
    public static Map<String, Integer> getModelIndices() {
        return Collections.unmodifiableMap(MODEL_INDEX);
    }

    /**
     * Mining speed scales with machine tier from MachineRegistry.
     * 挖掘速度随机器等级自动调整。
     */
    @Override
    public float getDestroyProgress(BlockState state, Player player,
                                     net.minecraft.world.level.BlockGetter level, BlockPos pos) {
        float base = super.getDestroyProgress(state, player, level, pos);
        if (base <= 0) return base;
        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof MachineControllerBlockEntity mc && mc.getMachineId() != null) {
            var def = com.endlessepoch.core.api.multiblock.MachineRegistry.get(mc.getMachineId());
            if (def.isPresent()) {
                int tier = def.get().getTier();
                float h = 3.0f + tier * 3.0f;
                return base * (3.0f / h);
            }
        }
        return base;
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        return defaultBlockState().setValue(FACING, ctx.getHorizontalDirection().getOpposite());
    }

    @Override
    public BlockState rotate(BlockState state, Rotation rot) {
        return state.setValue(FACING, rot.rotate(state.getValue(FACING)));
    }

    @Override
    public BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new MachineControllerBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide() && type == BlockEntities.MACHINE_CONTROLLER.get()) {
            return (l, p, s, e) -> ((MachineControllerBlockEntity) e).clientTick();
        }
        if (!level.isClientSide() && type == BlockEntities.MACHINE_CONTROLLER.get()) {
            return (l, p, s, e) -> ((MachineControllerBlockEntity) e).serverTick();
        }
        return null;
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moved) {
        if (!state.is(newState.getBlock())) {
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof MachineControllerBlockEntity mc && mc.getMachineId() != null) {
                com.endlessepoch.core.api.multiblock.MultiBlockBreakDetector.clear(pos);
                if (mc.getOwnerUUID() != null) {
                var emptyPkt = new com.endlessepoch.core.network.SyncValidationPacket(mc.getMachineId(),
                        new int[0], new int[0], new int[0], new int[0], 0, 0, 0, 0, 0, 0, false);
                var player = level.getPlayerByUUID(mc.getOwnerUUID());
                if (player instanceof net.minecraft.server.level.ServerPlayer sp)
                    net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(sp, emptyPkt);
                }
            }
        }
        super.onRemove(state, level, pos, newState, moved);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                                Player player, BlockHitResult hit) {
        if (!player.isShiftKeyDown()) {
            // Normal click: open machine GUI / 普通点击：打开机器界面
            if (!level.isClientSide()) {
                BlockEntity be = level.getBlockEntity(pos);
                if (be instanceof MachineControllerBlockEntity mc) {
                    var def = com.endlessepoch.core.api.multiblock.MachineRegistry.get(mc.getMachineId());
                    String en = def.map(com.endlessepoch.core.api.multiblock.MachineDefinition::getNameEn).orElse("Machine");
                    String zh = def.map(com.endlessepoch.core.api.multiblock.MachineDefinition::getNameZh).orElse("机器");
                    player.openMenu(mc, buf -> {
                        buf.writeBlockPos(pos);
                        buf.writeUtf(en);
                        buf.writeUtf(zh);
                        var types = mc.getSupportedTypes();
                        buf.writeVarInt(types.size());
                        for (var t : types) buf.writeUtf(t.toString());
                    });
                }
            }
            return InteractionResult.sidedSuccess(level.isClientSide());
        }
        if (level.isClientSide()) return InteractionResult.SUCCESS;

        BlockEntity be = level.getBlockEntity(pos);
        if (!(be instanceof MachineControllerBlockEntity mc)) return InteractionResult.PASS;

        ResourceLocation machineId = mc.getMachineId();
        if (machineId == null) {
            EECore.LOGGER.warn("MachineController at {} has null machineId", pos);
            return InteractionResult.PASS;
        }

        var pattern = MultiBlockRegistry.get(machineId);
        if (pattern.isEmpty()) {
            player.sendSystemMessage(Component.literal("Machine pattern not found: " + machineId));
            return InteractionResult.SUCCESS;
        }

        if (MultiBlockFormHandler.tryForm(be, pattern.get(), mc.getFacing(), player)) {
            player.sendSystemMessage(Component.literal("Formed: " + machineId));
            var emptyPkt = new com.endlessepoch.core.network.SyncValidationPacket(
                    machineId, new int[0], new int[0], new int[0], new int[0], 0, 0, 0, 0, 0, 0, false);
            net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(
                    (net.minecraft.server.level.ServerPlayer) player, emptyPkt);
            return InteractionResult.SUCCESS;
        }

        com.endlessepoch.core.api.multiblock.MultiBlockValidator.validateAndPreview(
                pattern.get(), machineId, pos, mc.getFacing(), level,
                (net.minecraft.server.level.ServerPlayer) player, mc.wasEverFormed());
        return InteractionResult.SUCCESS;
    }

    @Override
    public ItemStack getCloneItemStack(net.minecraft.world.level.LevelReader level, BlockPos pos, BlockState state) {
        var be = level.getBlockEntity(pos);
        if (be instanceof MachineControllerBlockEntity mc && mc.getMachineId() != null) {
            ResourceLocation mid = mc.getMachineId();
            String path = mid.getPath();
            for (var sup : com.endlessepoch.core.registry.Items.MACHINE_ITEMS) {
                Item item = sup.get();
                ResourceLocation iid = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(item);
                if (iid != null && iid.getPath().equals(path))
                    return new ItemStack(item);
            }
        }
        return super.getCloneItemStack(level, pos, state);
    }

    @Override
    public List<ItemStack> getDrops(BlockState state, LootParams.Builder builder) {
        return List.of(new ItemStack(this.asItem()));
    }
}
