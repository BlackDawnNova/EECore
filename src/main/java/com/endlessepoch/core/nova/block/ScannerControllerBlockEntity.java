package com.endlessepoch.core.nova.block;

import com.endlessepoch.core.api.multiblock.IMultiBlockController;
import com.endlessepoch.core.registry.BlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * Dedicated scanner controller block for multiblock structure scanning. / 专用的多方块扫描器控制器，用于结构扫描。
 * Implemented as a standalone block so the scanner can detect it as 'K'. / 作为独立方块实现，使扫描器可将其检测为 'K'。
 */
public class ScannerControllerBlockEntity extends BlockEntity implements IMultiBlockController {

    private UUID nodeId = UUID.randomUUID();
    private boolean formed;
    private UUID ownerUUID;
    private String ownerName;
    private UUID lastPreviewPlayer; // Last preview player UUID / 记录最后预览的玩家

    public ScannerControllerBlockEntity(BlockPos pos, BlockState state) {
        super(BlockEntities.SCANNER_CONTROLLER.get(), pos, state);
    }

    @Override
    public UUID getNodeId() { return nodeId; }

    @Override
    public boolean isFormed() { return formed; }

    @Override
    public void onMultiblockFormed() {
        formed = true;
        setChanged();
        if (level != null) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
    }

    @Override
    public void onMultiblockBroken() {
        formed = false;
        setChanged();
        if (level != null) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
    }

    @Override
    public UUID getOwnerUUID() { return ownerUUID; }

    @Override
    public String getOwnerName() { return ownerName; }

    /** Get the direction this controller is facing (read from block state). / 获取控制器朝向（从方块状态读取）。 */
    public Direction getFacing() {
        if (getBlockState().hasProperty(ScannerControllerBlock.FACING)) {
            return getBlockState().getValue(ScannerControllerBlock.FACING);
        }
        return Direction.NORTH;
    }

    public UUID getLastPreviewPlayer() { return lastPreviewPlayer; }
    public void setLastPreviewPlayer(UUID u) { this.lastPreviewPlayer = u; }

    @Override
    public void stampOwner(UUID owner, String name) {
        this.ownerUUID = owner;
        this.ownerName = name;
        setChanged();
    }

    /**
     * Sync formed state to client via update packet.
     * 通过更新包将成形状态同步到客户端。
     */
    @Nullable
    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider provider) {
        return saveWithoutMetadata(provider);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider provider) {
        super.saveAdditional(tag, provider);
        tag.putUUID("nodeId", nodeId);
        tag.putBoolean("formed", formed);
        if (ownerUUID != null) tag.putUUID("ownerUUID", ownerUUID);
        if (ownerName != null) tag.putString("ownerName", ownerName);
    }

    /**
     * Client-side tick — drives BlockEntityRenderer animation timing.
     * 客户端刻——驱动 BER 动画计时。
     */
    public void clientTick() {
        // Animation uses System.currentTimeMillis() in renderer; ticker triggers re-render.
        // 动画在 BER 中用 System.currentTimeMillis() 驱动，ticker 触发重渲染。
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
