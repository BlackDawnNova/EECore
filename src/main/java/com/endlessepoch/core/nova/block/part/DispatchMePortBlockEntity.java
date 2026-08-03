package com.endlessepoch.core.nova.block.part;

import appeng.api.networking.GridHelper;
import appeng.api.networking.IGridNode;
import appeng.api.networking.IManagedGridNode;
import appeng.api.networking.IInWorldGridNodeHost;
import appeng.api.util.AECableType;
import com.endlessepoch.core.api.multiblock.PartType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;

public class DispatchMePortBlockEntity extends PartBlockEntity implements IInWorldGridNodeHost {

    private final IManagedGridNode mainNode;

    public DispatchMePortBlockEntity(BlockPos pos, BlockState state, PartType type, int tier) {
        super(pos, state, type, tier);
        this.mainNode = GridHelper.createManagedNode(this, (be, node) -> be.setChanged())
                .setVisualRepresentation(getBlockState().getBlock().asItem())
                .setInWorldNode(true)
                .setExposedOnSides(java.util.EnumSet.allOf(Direction.class))
                .setTagName("me_port");
    }

    // IInWorldGridNodeHost / 网格节点宿主
    @Override public IGridNode getGridNode(Direction direction) { return mainNode.getNode(); }
    @Override public AECableType getCableConnectionType(Direction direction) { return AECableType.SMART; }

    public IManagedGridNode getMainNode() { return mainNode; }

    // Lifecycle / 生命周期
    @Override public void onLoad() { super.onLoad(); if (level != null && !level.isClientSide()) mainNode.create(level, worldPosition); }
    @Override public void setRemoved() { super.setRemoved(); mainNode.destroy(); }
    @Override public void onChunkUnloaded() { super.onChunkUnloaded(); mainNode.destroy(); }
}
