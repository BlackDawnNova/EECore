package com.endlessepoch.core.nova.block.part;

import com.endlessepoch.core.api.multiblock.IPart;
import com.endlessepoch.core.api.multiblock.PartAbility;
import com.endlessepoch.core.api.multiblock.PartType;
import com.endlessepoch.core.registry.BlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Set;

/**
 * Base BE for multiblock parts. Implements IPart — addon mods extend this or use directly.
 * 多方块部件基类 BE，实现 IPart——附属 mod 可继承或直接使用。
 */
public class PartBlockEntity extends BlockEntity implements IPart {

    private ResourceLocation machineId;
    private BlockPos controllerPos;
    private final PartType partType;
    private final Set<PartAbility> abilities = new java.util.LinkedHashSet<>();

    public PartBlockEntity(BlockPos pos, BlockState state, PartType type) {
        super(BlockEntities.PART.get(), pos, state);
        this.partType = type;
        // Default abilities based on type / 按类型设置默认能力
        switch (type.getId().getPath()) {
            case "input_bus"     -> abilities.add(PartAbility.ITEM_INPUT);
            case "output_bus"    -> abilities.add(PartAbility.ITEM_OUTPUT);
            case "input_hatch"   -> { abilities.add(PartAbility.FLUID_INPUT); abilities.add(PartAbility.ENERGY_INPUT); }
            case "output_hatch"  -> { abilities.add(PartAbility.FLUID_OUTPUT); abilities.add(PartAbility.ENERGY_OUTPUT); }
            case "input_assembly"  -> { abilities.add(PartAbility.ITEM_INPUT); abilities.add(PartAbility.FLUID_INPUT); }
            case "output_assembly" -> { abilities.add(PartAbility.ITEM_OUTPUT); abilities.add(PartAbility.FLUID_OUTPUT); }
        }
    }

    // ===== IPart =====

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

    // ===== Utility / 工具方法 =====

    public Direction getFacing() {
        if (getBlockState().hasProperty(PartBlock.FACING))
            return getBlockState().getValue(PartBlock.FACING);
        return Direction.NORTH;
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider provider) {
        super.saveAdditional(tag, provider);
        if (machineId != null) tag.putString("machineId", machineId.toString());
        if (controllerPos != null) tag.putLong("ctrlPos", controllerPos.asLong());
        tag.putString("partType", partType.getId().toString());
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider provider) {
        super.loadAdditional(tag, provider);
        if (tag.contains("machineId"))
            machineId = ResourceLocation.tryParse(tag.getString("machineId"));
        if (tag.contains("ctrlPos"))
            controllerPos = BlockPos.of(tag.getLong("ctrlPos"));
    }
}
