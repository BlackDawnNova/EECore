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
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * Generic machine controller BE. Machine ID loaded from item NBT on placement.
 * <p>
 * 通用机器控制器 BE，放置时从物品 NBT 读取机器 ID。
 */
public class MachineControllerBlockEntity extends BlockEntity implements IMultiBlockController {

    private UUID nodeId = UUID.randomUUID();
    private boolean formed;
    private UUID ownerUUID;
    private String ownerName;
    private ResourceLocation machineId;

    public MachineControllerBlockEntity(BlockPos pos, BlockState state) {
        super(BlockEntities.MACHINE_CONTROLLER.get(), pos, state);
    }

    @Override public UUID getNodeId() { return nodeId; }
    @Override public boolean isFormed() { return formed; }
    @Override public UUID getOwnerUUID() { return ownerUUID; }
    @Override public String getOwnerName() { return ownerName; }

    public ResourceLocation getMachineId() { return machineId; }
    public void setMachineId(ResourceLocation id) { this.machineId = id; setChanged(); }

    @Override
    public void onMultiblockFormed() {
        formed = true; setChanged();
        if (level != null) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
    }

    @Override
    public void onMultiblockBroken() {
        formed = false; setChanged();
        if (level != null) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
    }

    @Override
    public void stampOwner(UUID owner, String name) {
        this.ownerUUID = owner; this.ownerName = name; setChanged();
    }

    public Direction getFacing() {
        if (getBlockState().hasProperty(MachineControllerBlock.FACING))
            return getBlockState().getValue(MachineControllerBlock.FACING);
        return Direction.NORTH;
    }

    private int breakCheckTick;

    /** Client-side tick — drives BlockEntityRenderer animation timing. / 客户端刻。 */
    public void clientTick() {}

    /**
     * Lightweight periodic break validation — checks formed integrity every 100 ticks (5s).
     * 轻量定期破坏检测——每 100 tick (5秒) 验证一次成形完整性。
     */
    public void serverTick() {
        if (!formed || level == null || machineId == null || level.isClientSide()) return;
        if (++breakCheckTick < 100) return;
        breakCheckTick = 0;

        var pattern = com.endlessepoch.core.api.multiblock.MultiBlockRegistry.get(machineId);
        if (pattern.isEmpty()) return;
        if (!com.endlessepoch.core.api.multiblock.MultiBlockValidator.validate(
                level, pattern.get(), worldPosition, getFacing())) {
            onMultiblockBroken();
            com.endlessepoch.core.api.multiblock.MultiBlockFormHandler.notifyBreak(this, worldPosition, level);
        }
    }

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
        if (machineId != null) tag.putString("machineId", machineId.toString());
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider provider) {
        super.loadAdditional(tag, provider);
        if (tag.hasUUID("nodeId")) nodeId = tag.getUUID("nodeId");
        formed = tag.getBoolean("formed");
        if (tag.hasUUID("ownerUUID")) ownerUUID = tag.getUUID("ownerUUID");
        ownerName = tag.getString("ownerName");
        if (tag.contains("machineId"))
            machineId = ResourceLocation.tryParse(tag.getString("machineId"));
    }
}
