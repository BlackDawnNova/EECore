package com.endlessepoch.core.nova.block;

import com.endlessepoch.core.api.multiblock.IMultiBlockController;
import com.endlessepoch.core.api.multiblock.IPart;
import com.endlessepoch.core.menu.MachineMenu;
import com.endlessepoch.core.registry.BlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * Generic machine controller BE. No internal inventory — items stay in part inventories.
 * Controller coordinates recipe processing via scanParts() + getInputBuses()/getOutputBuses().
 * 通用机器控制器 BE，无内部库存——物品留在部件中。控制器通过 scanParts 协调配方。
 */
public class MachineControllerBlockEntity extends BlockEntity implements IMultiBlockController, MenuProvider {

    private UUID nodeId = UUID.randomUUID();
    private boolean formed;
    private UUID ownerUUID;
    private String ownerName;
    private ResourceLocation machineId;

    private final List<BlockPos> inputBusPos = new ArrayList<>();
    private final List<BlockPos> outputBusPos = new ArrayList<>();
    private boolean partsScanned;

    public MachineControllerBlockEntity(BlockPos pos, BlockState state) {
        super(BlockEntities.MACHINE_CONTROLLER.get(), pos, state);
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (level != null && !level.isClientSide() && machineId != null) {
            com.endlessepoch.core.event.BlockPlaceHandler.registerController(worldPosition, level.dimension());
        }
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        if (level != null && !level.isClientSide()) {
            com.endlessepoch.core.event.BlockPlaceHandler.unregisterController(worldPosition);
        }
    }

    // ===== IMultiBlockController =====

    @Override public UUID getNodeId() { return nodeId; }
    @Override public boolean isFormed() { return formed; }
    @Override public UUID getOwnerUUID() { return ownerUUID; }
    @Override public String getOwnerName() { return ownerName; }

    public ResourceLocation getMachineId() { return machineId; }
    public void setMachineId(ResourceLocation id) { this.machineId = id; setChanged(); }

    /** All input bus positions in the formed structure. / 结构中所有输入总线位置。 */
    public List<BlockPos> getInputBuses() { if (formed && !partsScanned) scanParts(); return Collections.unmodifiableList(inputBusPos); }

    /** All output bus positions in the formed structure. / 结构中所有输出总线位置。 */
    public List<BlockPos> getOutputBuses() { if (formed && !partsScanned) scanParts(); return Collections.unmodifiableList(outputBusPos); }

    @Override
    public void onMultiblockFormed() {
        formed = true; partsScanned = false;
        setChanged();
        if (level != null) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
    }

    @Override
    public void onMultiblockBroken() {
        formed = false;
        autoFormCheckTick = 0;
        inputBusPos.clear(); outputBusPos.clear(); partsScanned = false;
        setChanged();
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

    // ===== Tick / 刻 =====

    private int scheduledCheckTick;
    private int autoFormCheckTick;

    /** Schedule a pattern re-check after delayTicks. / 延迟调度成形重检。 */
    public void schedulePatternCheck(int delayTicks) {
        if (level == null || level.isClientSide() || machineId == null) return;
        if (scheduledCheckTick <= 0 || delayTicks < scheduledCheckTick) {
            scheduledCheckTick = delayTicks;
        }
    }

    public void clientTick() {}

    public void serverTick() {
        if (level == null || level.isClientSide()) return;

        if (scheduledCheckTick > 0 && --scheduledCheckTick == 0) {
            tryFormation();
        }

        if (!formed && machineId != null && ++autoFormCheckTick >= 100) {
            autoFormCheckTick = 0;
            tryFormation();
        }

        if (!formed || machineId == null) return;

        if (!partsScanned) scanParts();

        if (++breakCheckTick >= 100) {
            breakCheckTick = 0;
            var pattern = com.endlessepoch.core.api.multiblock.MultiBlockRegistry.get(machineId);
            if (pattern.isPresent() && !com.endlessepoch.core.api.multiblock.MultiBlockValidator.validate(
                    level, pattern.get(), worldPosition, getFacing())) {
                onMultiblockBroken();
                com.endlessepoch.core.api.multiblock.MultiBlockFormHandler.notifyBreak(this, worldPosition, level);
            }
        }
    }

    private void tryFormation() {
        if (machineId == null) return;
        var pattern = com.endlessepoch.core.api.multiblock.MultiBlockRegistry.get(machineId);
        if (pattern.isPresent() && com.endlessepoch.core.api.multiblock.MultiBlockValidator.validate(
                level, pattern.get(), worldPosition, getFacing())) {
            com.endlessepoch.core.api.multiblock.MultiBlockFormHandler.tryForm(
                    this, pattern.get(), getFacing(), null);
            if (formed) scanParts();
        }
    }

    private int breakCheckTick;

    private void scanParts() {
        partsScanned = true;
        inputBusPos.clear();
        outputBusPos.clear();
        if (level == null || machineId == null) return;
        var pattern = com.endlessepoch.core.api.multiblock.MultiBlockRegistry.get(machineId);
        if (pattern.isEmpty()) return;
        var pat = pattern.get();
        Direction facing = getFacing();
        for (int y = 0; y < pat.height; y++)
            for (int z = 0; z < pat.depth; z++)
                for (int x = 0; x < pat.width; x++) {
                    if (pat.getChar(x, y, z) == 'A' || pat.getChar(x, y, z) == ' ') continue;
                    int rx = x - pat.controllerX, ry = y - pat.controllerY, rz = z - pat.controllerZ;
                    BlockPos wp = switch (facing) {
                        case NORTH -> worldPosition.offset(rx, ry, rz);
                        case SOUTH -> worldPosition.offset(-rx, ry, -rz);
                        case EAST  -> worldPosition.offset(-rz, ry, rx);
                        case WEST  -> worldPosition.offset(rz, ry, -rx);
                        default    -> worldPosition.offset(rx, ry, rz);
                    };
                    BlockEntity be = level.getBlockEntity(wp);
                    if (be instanceof IPart part && part.isFormed()
                            && machineId.equals(part.getMachineId())) {
                        var abilities = part.getAbilities();
                        if (abilities.contains(com.endlessepoch.core.api.multiblock.PartAbility.ITEM_INPUT))
                            inputBusPos.add(wp.immutable());
                        if (abilities.contains(com.endlessepoch.core.api.multiblock.PartAbility.ITEM_OUTPUT))
                            outputBusPos.add(wp.immutable());
                    }
                }
        setChanged();
    }

    // ===== MenuProvider =====

    @Override
    public Component getDisplayName() { return Component.literal(""); }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int id, Inventory inv, Player player) {
        return new MachineMenu(id, inv, this);
    }

    // ===== NBT =====

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

    // ===== Network =====

    @Nullable
    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider provider) {
        return saveWithoutMetadata(provider);
    }
}
