package com.endlessepoch.core.api.machine;

import com.endlessepoch.core.api.energy.OmegaStorage;
import com.endlessepoch.core.api.tier.VoltageTier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Generic single-block machine BE. Stores a MachineType reference and delegates
 * processing to the type's IRecipeProcessor.
 * <p>
 * 通用单方块机器BE，存储MachineType并委托处理器。
 */
public class MachineBlockEntity extends BlockEntity {

    private ResourceLocation machineTypeId;
    private MachineType machineType;
    private IRecipeProcessor processor;
    private OmegaStorage energyStorage;
    private int tier;
    private boolean initialized;

    static java.util.function.Supplier<BlockEntityType<MachineBlockEntity>> typeSupplier;

    public MachineBlockEntity(BlockPos pos, BlockState state) {
        super(typeSupplier.get(), pos, state);
    }

    public void init(String typeId, int tier) {
        this.machineTypeId = ResourceLocation.parse(typeId);
        this.tier = tier;
        var opt = MachineTypeRegistry.get(machineTypeId);
        if (opt.isPresent()) {
            this.machineType = opt.get();
            this.processor = machineType.processor();
        }
        this.energyStorage = new OmegaStorage(10000, 128, 128, VoltageTier.fromOrdinal(tier));
        this.initialized = true;
        setChanged();
    }

    public MachineType getMachineType() { return machineType; }
    public IRecipeProcessor getProcessor() { return processor; }
    public OmegaStorage getEnergyStorage() { return energyStorage; }
    public boolean isInitialized() { return initialized; }

    public void serverTick() {
        if (level == null || level.isClientSide() || !initialized || processor == null) return;
        processor.tick(this);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider provider) {
        super.saveAdditional(tag, provider);
        if (machineTypeId != null) tag.putString("machineType", machineTypeId.toString());
        tag.putInt("tier", tier);
        tag.putBoolean("inited", initialized);
        if (energyStorage != null) energyStorage.saveToNBT(tag);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider provider) {
        super.loadAdditional(tag, provider);
        if (tag.contains("machineType"))
            init(tag.getString("machineType"), tag.getInt("tier"));
        initialized = tag.getBoolean("inited");
        if (energyStorage != null && tag.contains("energy"))
            energyStorage.loadFromNBT(tag);
    }
}
