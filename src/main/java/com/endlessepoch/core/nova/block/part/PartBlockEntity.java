package com.endlessepoch.core.nova.block.part;

import com.endlessepoch.core.api.energy.OmegaStorage;
import com.endlessepoch.core.api.multiblock.IPart;
import com.endlessepoch.core.api.multiblock.PartAbility;
import com.endlessepoch.core.api.multiblock.PartType;
import com.endlessepoch.core.api.tier.VoltageTier;
import com.endlessepoch.core.registry.BlockEntities;
import com.endlessepoch.core.menu.HatchMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;

import java.util.Set;

/**
 * Base BE for multiblock parts. Implements IPart — addon mods extend this or use directly.
 * 多方块部件基类 BE，实现 IPart——附属 mod 可继承或直接使用。
 */
public class PartBlockEntity extends BlockEntity implements IPart, MenuProvider {

    private ResourceLocation machineId;
    private BlockPos controllerPos;
    private final PartType partType;
    private final Set<PartAbility> abilities = new java.util.LinkedHashSet<>();
    private OmegaStorage energyStorage;
    private FluidTank fluidTank;

    public PartBlockEntity(BlockPos pos, BlockState state, PartType type, int tier) {
        super(BlockEntities.PART.get(), pos, state);
        this.partType = type;
        switch (type.getId().getPath()) {
            case "input_bus"       -> abilities.add(PartAbility.ITEM_INPUT);
            case "output_bus"      -> abilities.add(PartAbility.ITEM_OUTPUT);
            case "fluid_input"     -> abilities.add(PartAbility.FLUID_INPUT);
            case "fluid_output"    -> abilities.add(PartAbility.FLUID_OUTPUT);
            case "energy_input"    -> abilities.add(PartAbility.ENERGY_INPUT);
            case "energy_output"   -> abilities.add(PartAbility.ENERGY_OUTPUT);
            case "input_assembly"  -> { abilities.add(PartAbility.ITEM_INPUT); abilities.add(PartAbility.FLUID_INPUT); }
            case "output_assembly" -> { abilities.add(PartAbility.ITEM_OUTPUT); abilities.add(PartAbility.FLUID_OUTPUT); }
            case "casing" -> abilities.add(PartAbility.STRUCTURAL);
        }
        // Read config from PartBlock if available (CasingBlock doesn't extend PartBlock) / 从 PartBlock 读配置
        int fluidCap = 0;
        long energyCap = 0;
        if (state.getBlock() instanceof PartBlock pb) {
            fluidCap = pb.fluidCapacity;
            energyCap = pb.energyCapacity;
        }
        if (abilities.contains(PartAbility.ENERGY_INPUT) || abilities.contains(PartAbility.ENERGY_OUTPUT)) {
            long ec = energyCap > 0 ? energyCap : 10000;
            energyStorage = new OmegaStorage(ec, Long.MAX_VALUE, Long.MAX_VALUE, VoltageTier.fromOrdinal(tier));
        }
        if (abilities.contains(PartAbility.FLUID_INPUT) || abilities.contains(PartAbility.FLUID_OUTPUT)) {
            int fc = fluidCap > 0 ? fluidCap : 8000;
            fluidTank = new FluidTank(fc) {
                @Override protected void onContentsChanged() { setChanged(); }
            };
        }
    }

    // IPart / 部件接口

    @Override public Set<PartAbility> getAbilities() { return abilities; }

    @Override
    public void onFormed(ResourceLocation machineId, BlockPos controllerPos) {
        this.machineId = machineId;
        this.controllerPos = controllerPos;
        setChanged();
        if (level != null) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
    }

    @Override
    public void onBroken() {
        this.machineId = null;
        this.controllerPos = null;
        setChanged();
    }

    @Override public ResourceLocation getMachineId() { return machineId; }
    @Override public BlockPos getControllerPos() { return controllerPos; }
    @Override public boolean isFormed() { return machineId != null; }

    /** Omega energy storage, or null if not an energy hatch. / 能量存储，非能源仓时返回 null。 */
    public OmegaStorage getEnergyStorage() { return energyStorage; }
    /** Fluid tank, or null if not a fluid hatch. / 流体罐，非流体仓时返回 null。 */
    public FluidTank getFluidTank() { return fluidTank; }

    // MenuProvider / 菜单提供

    @Override
    public Component getDisplayName() {
        return Component.translatable(getBlockState().getBlock().getDescriptionId());
    }

    @Override
    public AbstractContainerMenu createMenu(int id, Inventory inv, Player player) {
        return new HatchMenu(id, inv, this);
    }

    // Utility / 工具方法

    public Direction getFacing() {
        var state = getBlockState();
        if (state.hasProperty(PartBlock.FACING))
            return state.getValue(PartBlock.FACING);
        return Direction.NORTH;
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider provider) {
        super.saveAdditional(tag, provider);
        if (machineId != null) tag.putString("machineId", machineId.toString());
        if (controllerPos != null) tag.putLong("ctrlPos", controllerPos.asLong());
        tag.putString("partType", partType.getId().toString());
        if (energyStorage != null) energyStorage.saveToNBT(tag);
        if (fluidTank != null) tag.put("fluidTank", fluidTank.writeToNBT(provider, new CompoundTag()));
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider provider) {
        super.loadAdditional(tag, provider);
        if (tag.contains("machineId"))
            machineId = ResourceLocation.tryParse(tag.getString("machineId"));
        if (tag.contains("ctrlPos"))
            controllerPos = BlockPos.of(tag.getLong("ctrlPos"));
        if (energyStorage != null) energyStorage.loadFromNBT(tag);
        if (fluidTank != null) fluidTank.readFromNBT(provider, tag.getCompound("fluidTank"));
    }
}
