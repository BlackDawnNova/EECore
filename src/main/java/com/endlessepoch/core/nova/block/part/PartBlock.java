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
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Base block for all multiblock parts. Configurable via fluent setters.
 * Tier controls appearance (texture, hardness). All functionality params are explicit.
 * 多方块部件基类，功能参数显式配置。
 */
public class PartBlock extends Block implements EntityBlock {

    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    private final PartType partType;
    final int tier;
    final int slotCount;
    final int fluidCapacity;   // mB, 0 = no tank
    final long energyCapacity; // Ω, 0 = no energy storage

    public static final int DEFAULT_BUS_SLOTS = 2;
    public static final int DEFAULT_ASSEMBLY_SLOTS = 4;
    public static final int MAX_SLOTS = 81;

    /** Build tier-scaled block properties. / 按电压等级生成方块属性。 */
    public static Properties tieredProperties(int tier) {
        float h = 3.0f + tier * 3.0f;
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

    // Constructors / 构造器（tier + 显式功能参数）

    /** Structural part (casing). / 纯结构。 */
    public PartBlock(Properties p, PartType type, int tier) {
        this(p, type, tier, 0, 0, 0);
    }

    /** Item bus with slot count. / 物品总线。 */
    public PartBlock(Properties p, PartType type, int tier, int slotCount) {
        this(p, type, tier, slotCount, 0, 0);
    }

    /** Assembly: slots + fluid capacity. / 总成：格子+流体。 */
    public PartBlock(Properties p, PartType type, int tier, int slotCount, int fluidCapacity) {
        this(p, type, tier, slotCount, fluidCapacity, 0);
    }

    /** Energy hatch or full config. / 能源仓或全配置。 */
    public PartBlock(Properties p, PartType type, int tier, int slotCount, int fluidCapacity, long energyCapacity) {
        super(p);
        this.partType = type;
        this.tier = tier;
        this.slotCount = clamp(slotCount, isBusType(type) ? 1 : 0, MAX_SLOTS);
        this.fluidCapacity = Math.max(0, fluidCapacity);
        this.energyCapacity = Math.max(0, energyCapacity);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    private static int clamp(int v, int min, int max) { return Math.max(min, Math.min(v, max)); }

    public static boolean isBusType(PartType type) {
        String p = type.getId().getPath();
        return p.equals("input_bus") || p.equals("output_bus")
                || p.equals("input_assembly") || p.equals("output_assembly");
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

    @Override public BlockState rotate(BlockState s, Rotation r) { return s.setValue(FACING, r.rotate(s.getValue(FACING))); }
    @Override public BlockState mirror(BlockState s, Mirror m) { return s.rotate(m.getRotation(s.getValue(FACING))); }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        if (isBusType(partType))
            return new InputBusBlockEntity(pos, state, partType, tier, slotCount);
        return new PartBlockEntity(pos, state, partType, tier);
    }

    @Override
    public InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                            Player player, BlockHitResult hit) {
        if (!level.isClientSide()) {
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof InputBusBlockEntity bus) {
                player.openMenu(bus, buf -> {
                    buf.writeBlockPos(pos);
                    buf.writeVarInt(bus.getSlotCount());
                    buf.writeBoolean(bus.isOutput());
                });
                return InteractionResult.SUCCESS;
            }
            if (be instanceof PartBlockEntity pe
                    && (pe.getEnergyStorage() != null || pe.getFluidTank() != null)) {
                player.openMenu(pe, buf -> buf.writeBlockPos(pos));
                return InteractionResult.SUCCESS;
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide());
    }

    @Override
    public List<ItemStack> getDrops(BlockState state, LootParams.Builder builder) {
        return List.of(new ItemStack(this.asItem()));
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moved) {
        if (!state.is(newState.getBlock())) {
            BlockEntity be = level.getBlockEntity(pos);
            if (be != null && !level.isClientSide()) {
                if (be instanceof InputBusBlockEntity bus) {
                    for (int i = 0; i < bus.getSlotCount(); i++) {
                        var stack = bus.getInventory().getStackInSlot(i);
                        if (!stack.isEmpty())
                            net.minecraft.world.Containers.dropItemStack(level,
                                    pos.getX(), pos.getY(), pos.getZ(), stack.copy());
                    }
                }
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
