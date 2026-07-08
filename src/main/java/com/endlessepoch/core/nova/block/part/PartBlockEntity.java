package com.endlessepoch.core.nova.block.part;

import com.endlessepoch.core.EECore;
import com.endlessepoch.core.api.multiblock.MultiBlockRegistry;
import com.endlessepoch.core.api.multiblock.PartType;
import com.endlessepoch.core.registry.BlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import org.jetbrains.annotations.Nullable;

import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * Base BE for all multiblock parts. Stores machineId + controller position.
 * 所有多方块部件的基类 BE，存 machineId + 控制器位置。
 */
public class PartBlockEntity extends BlockEntity {

    private ResourceLocation machineId;
    private BlockPos controllerPos;
    private PartType partType;

    public PartBlockEntity(BlockPos pos, BlockState state, PartType type) {
        super(BlockEntities.PART.get(), pos, state);
        this.partType = type;
    }

    public PartType getPartType() { return partType; }
    public ResourceLocation getMachineId() { return machineId; }
    public BlockPos getControllerPos() { return controllerPos; }

    /** Called by the controller when the multiblock forms. / 控制器成形时调用。 */
    public void bindToController(ResourceLocation machineId, BlockPos ctrlPos) {
        this.machineId = machineId;
        this.controllerPos = ctrlPos;
        setChanged();
        if (level != null) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
    }

    /** Clear binding — called when multiblock breaks. / 清除绑定——多方块破碎时调用。 */
    public void unbind() {
        this.machineId = null;
        this.controllerPos = null;
        setChanged();
    }

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
        tag.putString("partType", partType.name());
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider provider) {
        super.loadAdditional(tag, provider);
        if (tag.contains("machineId"))
            machineId = ResourceLocation.tryParse(tag.getString("machineId"));
        if (tag.contains("ctrlPos"))
            controllerPos = BlockPos.of(tag.getLong("ctrlPos"));
        if (tag.contains("partType"))
            partType = PartType.valueOf(tag.getString("partType"));
    }
}
