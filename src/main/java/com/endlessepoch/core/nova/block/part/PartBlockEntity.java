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

import java.util.List;
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
    private List<FluidTank> fluidTanks = new java.util.ArrayList<>();

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
            int count = (state.getBlock() instanceof PartBlock pb && pb.fluidSlots > 0) ? pb.fluidSlots : 1;
            boolean output = abilities.contains(PartAbility.FLUID_OUTPUT);
            for (int i = 0; i < count; i++)
                fluidTanks.add(new FluidTank(fc) {
                    @Override protected void onContentsChanged() { setChanged(); }
                    @Override public boolean isFluidValid(net.neoforged.neoforge.fluids.FluidStack s) { return !output; }
                });
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

    /** Omega energy storage (thread-safe), or null if not an energy hatch. / 能量存储（线程安全），非能源仓时返回 null。 */
    public OmegaStorage getEnergyStorage() { return energyStorage; }
    /** Fluid tanks snapshot, or null if not a fluid hatch. / 流体罐列表快照，非流体仓时返回 null。 */
    public List<FluidTank> getFluidTanks() { return java.util.Collections.unmodifiableList(fluidTanks); }
    public FluidTank getFluidTank() { return fluidTanks.isEmpty() ? null : fluidTanks.get(0); }
    /** Fluid handler — fill/drain are synchronized on the tanks list. / 流体处理器，fill/drain 锁 tanks 列表。 */
    public IFluidHandler getFluidHandler() {
        if (fluidTanks.isEmpty()) return null;
        if (fluidTanks.size() == 1) return fluidTanks.get(0);
        return new net.neoforged.neoforge.fluids.capability.IFluidHandler() {
            @Override public int getTanks() { return fluidTanks.size(); }
            @Override public net.neoforged.neoforge.fluids.FluidStack getFluidInTank(int t) { synchronized(fluidTanks) { return fluidTanks.get(t).getFluid(); } }
            @Override public int getTankCapacity(int t) { synchronized(fluidTanks) { return fluidTanks.get(t).getCapacity(); } }
            @Override public boolean isFluidValid(int t, net.neoforged.neoforge.fluids.FluidStack s) { synchronized(fluidTanks) { return fluidTanks.get(t).isFluidValid(s); } }
            @Override public int fill(net.neoforged.neoforge.fluids.FluidStack r, FluidAction a) { synchronized(fluidTanks) {
                for (var t : fluidTanks) if (t.getFluid().getFluid() == r.getFluid()) { int f=t.fill(r,a); if(f>0)return f; }
                for (var t : fluidTanks) if (t.getFluid().isEmpty()) { int f=t.fill(r,a); if(f>0)return f; }
                return 0; } }
            @Override public net.neoforged.neoforge.fluids.FluidStack drain(net.neoforged.neoforge.fluids.FluidStack r, FluidAction a) { synchronized(fluidTanks) { for (var t : fluidTanks) { var d = t.drain(r, a); if (!d.isEmpty()) return d; } return net.neoforged.neoforge.fluids.FluidStack.EMPTY; } }
            @Override public net.neoforged.neoforge.fluids.FluidStack drain(int max, FluidAction a) { synchronized(fluidTanks) { for (var t : fluidTanks) { var d = t.drain(max, a); if (!d.isEmpty()) return d; } return net.neoforged.neoforge.fluids.FluidStack.EMPTY; } }
        };
    }

    // MenuProvider / 菜单提供

    @Override
    public Component getDisplayName() {
        return Component.translatable(getBlockState().getBlock().getDescriptionId());
    }

    @Override
    public AbstractContainerMenu createMenu(int id, Inventory inv, Player player) {
        var menu = new HatchMenu(id, inv, this);
        if (player instanceof net.minecraft.server.level.ServerPlayer sp)
            menu.setViewer(sp);
        return menu;
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
        var fl = new net.minecraft.nbt.ListTag();
        for (var t : fluidTanks) fl.add(t.writeToNBT(provider, new CompoundTag()));
        if (!fl.isEmpty()) tag.put("fluidTanks", fl);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider provider) {
        super.loadAdditional(tag, provider);
        if (tag.contains("machineId"))
            machineId = ResourceLocation.tryParse(tag.getString("machineId"));
        if (tag.contains("ctrlPos"))
            controllerPos = BlockPos.of(tag.getLong("ctrlPos"));
        if (energyStorage != null) energyStorage.loadFromNBT(tag);
        if (tag.contains("fluidTanks")) {
            var fl = tag.getList("fluidTanks", net.minecraft.nbt.Tag.TAG_COMPOUND);
            for (int i = 0; i < fl.size() && i < fluidTanks.size(); i++)
                fluidTanks.get(i).readFromNBT(provider, fl.getCompound(i));
        }
    }
}
