package com.endlessepoch.core.nova.block.part;

import com.endlessepoch.core.api.multiblock.IPart;
import com.endlessepoch.core.api.multiblock.MultiBlockFormHandler;
import com.endlessepoch.core.api.multiblock.MultiBlockRegistry;
import com.endlessepoch.core.api.multiblock.MultiBlockValidator;
import com.endlessepoch.core.api.multiblock.PartType;
import com.endlessepoch.core.nova.block.MachineControllerBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
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
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Base block for all multiblock parts (bus, hatch, assembly).
 * Stores FACING + optional slot count, links to controller via BE on formation.
 * 多方块部件基类，存朝向+可选格数，成形时通过 BE 链接控制器。
 */
public class PartBlock extends Block implements EntityBlock {

    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    private final PartType partType;
    private final int slotCount;

    /** Default slot count for bus-type parts. / 总线类部件默认格数。 */
    public static final int DEFAULT_BUS_SLOTS = 2;
    /** Max slot count for bus-type parts. / 总线类部件最大格数。 */
    public static final int MAX_BUS_SLOTS = 81;

    /**
     * Build tier-scaled block properties. Higher tier = harder to mine + needs pickaxe.
     * 按电压等级生成方块属性，等级越高越硬，需要镐子。
     */
    public static Properties tieredProperties(int tier) {
        float h = 3.0f + tier * 3.0f;   // LV=6, MV=9, HV=12, ..., QV=36
        float b = 6.0f + tier * 3.0f;
        return Properties.of().strength(h, b).noOcclusion().requiresCorrectToolForDrops();
    }

    /** Tool tier tag for a voltage tier. / 电压等级对应的工具标签。 */
    public static String toolTagForTier(int tier) {
        if (tier >= 10) return "minecraft:needs_netherite_tool";
        if (tier >= 7)  return "minecraft:needs_diamond_tool";
        if (tier >= 4)  return "minecraft:needs_iron_tool";
        return "minecraft:needs_stone_tool";
    }

    public PartBlock(Properties properties, PartType type) {
        this(properties, type, isBusType(type) ? DEFAULT_BUS_SLOTS : 0);
    }

    /**
     * Create a part block with custom slot count. / 创建自定义格数的部件方块。
     * @param slotCount inventory size for bus parts, ignored for non-bus types / 总线类部件的库存格数
     */
    public PartBlock(Properties properties, PartType type, int slotCount) {
        super(properties);
        this.partType = type;
        int min = isBusType(type) ? 1 : 0;
        this.slotCount = Math.max(min, Math.min(slotCount, MAX_BUS_SLOTS));
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    private static boolean isBusType(PartType type) {
        String path = type.getId().getPath();
        return path.equals("input_bus") || path.equals("output_bus");
    }

    public PartType getPartType() { return partType; }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
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
        return switch (partType.getId().getPath()) {
            case "input_bus", "output_bus" -> new InputBusBlockEntity(pos, state, slotCount);
            default -> new PartBlockEntity(pos, state, partType);
        };
    }

    /**
     * Right-click opens inventory GUI for bus blocks. / 右键打开总线方块界面。
     */
    @Override
    public InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                            Player player, BlockHitResult hit) {
        if (!level.isClientSide()) {
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof InputBusBlockEntity bus) {
                player.openMenu(bus, buf -> {
                    buf.writeBlockPos(pos);
                    buf.writeVarInt(bus.getSlotCount());
                });
                return InteractionResult.SUCCESS;
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide());
    }

    @Override
    public List<ItemStack> getDrops(BlockState state, LootParams.Builder builder) {
        return List.of(new ItemStack(this.asItem()));
    }

    /**
     * When the part block is broken, drop inventory + notify controller to re-validate.
     * 部件方块被破坏时掉落物品 + 通知控制器重新验证。
     */
    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moved) {
        if (!state.is(newState.getBlock())) {
            BlockEntity be = level.getBlockEntity(pos);
            if (be != null && !level.isClientSide()) {
                // Drop inventory contents / 掉落库存物品
                if (be instanceof InputBusBlockEntity bus) {
                    for (int i = 0; i < bus.getSlotCount(); i++) {
                        var stack = bus.getInventory().getStackInSlot(i);
                        if (!stack.isEmpty())
                            net.minecraft.world.Containers.dropItemStack(level,
                                    pos.getX(), pos.getY(), pos.getZ(), stack.copy());
                    }
                }
                // Notify controller to re-validate / 通知控制器重新验证
                if (be instanceof IPart part && part.isFormed()) {
                    BlockPos ctrl = part.getControllerPos();
                    BlockEntity ctrlBe = level.getBlockEntity(ctrl);
                    if (ctrlBe instanceof MachineControllerBlockEntity mc
                            && mc.getMachineId() != null
                            && mc.getMachineId().equals(part.getMachineId())) {
                        var p = MultiBlockRegistry.get(part.getMachineId());
                        if (p.isPresent() && !MultiBlockValidator.validate(
                                level, p.get(), ctrl, mc.getFacing())) {
                            mc.onMultiblockBroken();
                            MultiBlockFormHandler.notifyBreak(mc, ctrl, level);
                        }
                    }
                }
            }
        }
        super.onRemove(state, level, pos, newState, moved);
    }
}
