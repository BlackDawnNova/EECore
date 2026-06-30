package com.endlessepoch.core.nova.block;

import com.endlessepoch.core.api.multiblock.IMultiBlockController;
import com.endlessepoch.core.registry.BlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.core.HolderLookup;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.UUID;

/**
 * Dedicated scanner controller block for multiblock structure scanning.
 * Implemented as a standalone block so the scanner can detect it as 'K'.
 */
public class ScannerControllerBlockEntity extends BlockEntity implements IMultiBlockController {

    private UUID nodeId = UUID.randomUUID();
    private boolean formed;
    private UUID ownerUUID;
    private String ownerName;

    public ScannerControllerBlockEntity(BlockPos pos, BlockState state) {
        super(BlockEntities.SCANNER_CONTROLLER.get(), pos, state);
    }

    @Override
    public UUID getNodeId() { return nodeId; }

    @Override
    public boolean isFormed() { return formed; }

    @Override
    public void onMultiblockFormed() { formed = true; setChanged(); }

    @Override
    public void onMultiblockBroken() { formed = false; setChanged(); }

    @Override
    public UUID getOwnerUUID() { return ownerUUID; }

    @Override
    public String getOwnerName() { return ownerName; }

    /** Get the direction this controller is facing (read from block state). */
    public Direction getFacing() {
        if (getBlockState().hasProperty(ScannerControllerBlock.FACING)) {
            return getBlockState().getValue(ScannerControllerBlock.FACING);
        }
        return Direction.NORTH;
    }

    @Override
    public void stampOwner(UUID owner, String name) {
        this.ownerUUID = owner;
        this.ownerName = name;
        setChanged();
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider provider) {
        super.saveAdditional(tag, provider);
        tag.putUUID("nodeId", nodeId);
        tag.putBoolean("formed", formed);
        if (ownerUUID != null) tag.putUUID("ownerUUID", ownerUUID);
        if (ownerName != null) tag.putString("ownerName", ownerName);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider provider) {
        super.loadAdditional(tag, provider);
        if (tag.hasUUID("nodeId")) nodeId = tag.getUUID("nodeId");
        formed = tag.getBoolean("formed");
        if (tag.hasUUID("ownerUUID")) ownerUUID = tag.getUUID("ownerUUID");
        ownerName = tag.getString("ownerName");
    }
}
