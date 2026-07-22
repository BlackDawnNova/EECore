package com.endlessepoch.core.nova.block.part;

import com.endlessepoch.core.api.multiblock.IPart;
import com.endlessepoch.core.api.multiblock.MultiBlockBreakDetector;
import com.endlessepoch.core.api.multiblock.MultiBlockFormHandler;
import com.endlessepoch.core.api.multiblock.MultiBlockRegistry;
import com.endlessepoch.core.api.multiblock.MultiBlockValidator;
import com.endlessepoch.core.api.multiblock.PartType;
import com.endlessepoch.core.nova.block.MachineControllerBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import org.jetbrains.annotations.Nullable;
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
    public final int fluidSlots; // GUI fluid display count, 1-27
    private int amperage = 1;  // energy hatch amperage / 能源仓安培数，默认 1A

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

    /** Structural part (casing). */
    public PartBlock(Properties p, PartType type, int tier) { this(p, type, tier, 0, 0, 0, 0); }
    /** Item bus with slot count. */
    public PartBlock(Properties p, PartType type, int tier, int sc) { this(p, type, tier, sc, 0, 0, 0); }
    /** Assembly: slots + fluid capacity. */
    public PartBlock(Properties p, PartType type, int tier, int sc, int fc) { this(p, type, tier, sc, fc, 0, 0); }
    /** Energy hatch or full config. */
    public PartBlock(Properties p, PartType type, int tier, int sc, int fc, long ec) { this(p, type, tier, sc, fc, ec, 0); }
    /** Full config with fluid slots. */
    public PartBlock(Properties p, PartType type, int tier, int sc, int fc, long ec, int fs) {
        super(p); this.partType = type; this.tier = tier;
        this.slotCount = clamp(sc, isBusType(type) ? 1 : 0, MAX_SLOTS);
        this.fluidCapacity = Math.max(0, fc); this.energyCapacity = Math.max(0, ec);
        this.fluidSlots = fc > 0 ? Math.max(0, Math.min(MAX_SLOTS, fs)) : 0;
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    private static int clamp(int v, int min, int max) { return Math.max(min, Math.min(v, max)); }

    /** Valid amperage steps, aligned with EnergyPacket amperage model. / 合法安培档位，与能量包模型一致。 */
    public static final int[] VALID_AMPERAGES = {1, 2, 4, 8, 16};

    /**
     * Fluent: set hatch amperage (addon API). Only 1/2/4/8/16 allowed — matches the
     * EnergyPacket amperage steps; anything else fails fast at registration.
     * 链式设置安培数（附属 API）。仅允许 1/2/4/8/16，与能量包安培档位一致，非法值注册期直接报错。
     */
    public PartBlock amperage(int a) {
        if (a != 1 && a != 2 && a != 4 && a != 8 && a != 16)
            throw new IllegalArgumentException(
                    "Invalid amperage " + a + " — must be one of 1/2/4/8/16 / 非法安培数，仅允许 1/2/4/8/16");
        this.amperage = a;
        return this;
    }
    public int getAmperage() { return amperage; }

    /**
     * Suffix match so addon-registered tiered variants (e.g. "hv_input_bus") behave like built-ins.
     * 后缀匹配，附属注册的分级变体（如 hv_input_bus）与内置部件行为一致。
     */
    public static boolean isBusType(PartType type) {
        String p = type.getId().getPath();
        return p.endsWith("input_bus") || p.endsWith("output_bus")
                || p.endsWith("input_assembly") || p.endsWith("output_assembly");
    }

    /** "creative_" prefixed bus = phantom infinite bus. / creative_ 前缀总线=幻影无限总线。 */
    public static boolean isCreativeBus(PartType type) {
        return isBusType(type) && type.getId().getPath().startsWith("creative_");
    }

    /** "oversized_" prefixed bus = max-stack output bus. / oversized_ 前缀总线=巨量输出总线。 */
    public static boolean isOversizedBus(PartType type) {
        return isBusType(type) && type.getId().getPath().startsWith("oversized_");
    }

    /** Bus with "_locked_" in path = slot-lockable oversized input. / 路径含 _locked_ 的总线=锁槽巨量输入。 */
    public static boolean isLockedBus(PartType type) {
        return isBusType(type) && type.getId().getPath().contains("_locked_");
    }

    /** Fluid bin with "_locked_" in path = auto-locking oversized fluid input. / 路径含 _locked_ 的仓=自动锁巨量流体输入。 */
    public static boolean isLockedFluidBin(PartType type) {
        String p = type.getId().getPath();
        return p.contains("_locked_") && p.endsWith("input_bin");
    }

    /** "creative_" prefixed hatch (energy/fluid) = infinite/void hatch. / creative_ 前缀仓（能源/流体）=无限/虚空仓。 */
    public static boolean isCreativeHatch(PartType type) {
        String p = type.getId().getPath();
        return p.startsWith("creative_") && (p.endsWith("energy_input") || p.endsWith("energy_output")
                || p.endsWith("fluid_input") || p.endsWith("fluid_output"));
    }

    /** "creative_" prefixed parallel hatch = typed-value parallel. / creative_ 前缀并行仓=自由数值并行。 */
    public static boolean isCreativeParallel(PartType type) {
        String p = type.getId().getPath();
        return p.startsWith("creative_") && p.endsWith("parallel_hatch");
    }

    public PartType getPartType() { return partType; }
    public int getTier() { return tier; }

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
        if (isBusType(partType)) {
            if (isLockedBus(partType))
                return new LockedOversizedBusBlockEntity(pos, state, partType, tier, slotCount);
            if (isOversizedBus(partType))
                return new CreativeOversizedBusBlockEntity(pos, state, partType, tier, slotCount);
            if (isCreativeBus(partType))
                return new CreativeBusBlockEntity(pos, state, partType, tier, slotCount);
            return new InputBusBlockEntity(pos, state, partType, tier, slotCount);
        }
        if (isLockedFluidBin(partType))
            return new LockedOversizedFluidBinBlockEntity(pos, state, partType, tier);
        if (isCreativeHatch(partType))
            return new CreativeHatchBlockEntity(pos, state, partType, tier);
        if (isCreativeParallel(partType))
            return new CreativeParallelHatchBlockEntity(pos, state, partType, tier);
        if (partType.getId().getPath().endsWith("ae_interface"))
            return new AeInterfaceBlockEntity(pos, state, partType, tier);
        return new PartBlockEntity(pos, state, partType, tier);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
                                                                    BlockEntityType<T> bet) {
        if (level.isClientSide()) return null;
        // Only ae_interface parts need server tick / 仅 ae_interface 需要服务端 tick
        if (!partType.getId().getPath().endsWith("ae_interface")) return null;
        return (lvl, pos, st, be) -> {
            if (be instanceof AeInterfaceBlockEntity ae) ae.serverTick();
        };
    }

    @Override
    public InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                            Player player, BlockHitResult hit) {
        if (!level.isClientSide()) {
            BlockEntity be = level.getBlockEntity(pos);
            // Creative energy source opens its tier-adjust GUI / 创造能源源打开调档 GUI
            if (be instanceof CreativeHatchBlockEntity ch && ch.isEnergyInput()) {
                player.openMenu(ch, buf -> buf.writeBlockPos(pos));
                return InteractionResult.SUCCESS;
            }
            // Creative parallel hatch opens its number-input GUI / 创造并行仓打开数字输入 GUI
            if (be instanceof CreativeParallelHatchBlockEntity ph) {
                player.openMenu(ph, buf -> buf.writeBlockPos(pos));
                return InteractionResult.SUCCESS;
            }
            // Void variants open the swallow-stats GUI / 虚空型打开吞噬统计 GUI
            if (be instanceof CreativeBusBlockEntity cvb && cvb.isOutput()) {
                player.openMenu(cvb, buf -> buf.writeBlockPos(pos));
                return InteractionResult.SUCCESS;
            }
            if (be instanceof CreativeHatchBlockEntity cvh && cvh.isFluidVoid()) {
                player.openMenu(cvh, buf -> buf.writeBlockPos(pos));
                return InteractionResult.SUCCESS;
            }
            if (be instanceof InputBusBlockEntity bus) {
                var tanks = ((PartBlockEntity) bus).getFluidTanks();
                int fs = tanks.isEmpty() ? 0 : this.fluidSlots;
                player.openMenu(bus, buf -> {
                    buf.writeBlockPos(pos); buf.writeVarInt(bus.getSlotCount()); buf.writeBoolean(bus.isOutput());
                    buf.writeBoolean(bus.isCreative());
                    buf.writeBoolean(bus.isOversized());
                    buf.writeVarInt(fs);
                    for (int i = 0; i < fs && i < tanks.size(); i++) {
                        var s = tanks.get(i).getFluid();
                        buf.writeBoolean(!s.isEmpty());
                        if (!s.isEmpty()) buf.writeResourceLocation(net.minecraft.core.registries.BuiltInRegistries.FLUID.getKey(s.getFluid()));
                        buf.writeVarInt(tanks.get(i).getFluidAmount()); buf.writeVarInt(tanks.get(i).getCapacity());
                    }
                }); return InteractionResult.SUCCESS;
            }
            if (be instanceof PartBlockEntity pe && (pe.getEnergyStorage() != null || !pe.getFluidTanks().isEmpty())) {
                var tanks = pe.getFluidTanks();
                var es = pe.getEnergyStorage();
                player.openMenu(pe, buf -> {
                    buf.writeBlockPos(pos); buf.writeVarInt(tanks.size());
                    for (var t : tanks) {
                        var s = t.getFluid();
                        buf.writeBoolean(!s.isEmpty());
                        if (!s.isEmpty()) buf.writeResourceLocation(net.minecraft.core.registries.BuiltInRegistries.FLUID.getKey(s.getFluid()));
                        buf.writeVarInt(t.getFluidAmount()); buf.writeVarInt(t.getCapacity());
                    }                    buf.writeUtf(es != null ? es.getEnergyStored().toBigInteger().toString() : "0");
                    buf.writeUtf(es != null ? es.getCapacity().toBigInteger().toString() : "0");
                }); return InteractionResult.SUCCESS;
            }
        }
        if (partType.getId().getPath().startsWith("dispatch") || partType.getId().getPath().startsWith("supercomputing")
                || partType.getId().getPath().startsWith("pattern_unit") || partType.getId().getPath().startsWith("quantity")
                || partType.getId().getPath().startsWith("parallel_unit"))
            return InteractionResult.PASS;
        return InteractionResult.sidedSuccess(level.isClientSide());
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                              Player player, InteractionHand hand, BlockHitResult hit) {
        return net.neoforged.neoforge.fluids.FluidUtil.interactWithFluidHandler(player, hand, level, pos, hit.getDirection())
                ? ItemInteractionResult.sidedSuccess(level.isClientSide())
                : super.useItemOn(stack, state, level, pos, player, hand, hit);
    }

    @Override
    public List<ItemStack> getDrops(BlockState state, LootParams.Builder builder) {
        return List.of(new ItemStack(this.asItem()));
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moved) {
        if (!state.is(newState.getBlock()) && !level.isClientSide()) {
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof InputBusBlockEntity bus && !bus.isCreative()) {
                for (int i = 0; i < bus.getSlotCount(); i++) {
                    var stack = bus.getInventory().getStackInSlot(i);
                    if (!stack.isEmpty())
                        net.minecraft.world.Containers.dropItemStack(level,
                                pos.getX(), pos.getY(), pos.getZ(), stack.copy());
                }
            }
        }
        super.onRemove(state, level, pos, newState, moved);
    }
}
