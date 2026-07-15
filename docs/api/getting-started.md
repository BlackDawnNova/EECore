# Getting Started / 快速开始

This guide shows how to integrate Ω energy into your mod.

---

## 1. Declare a Machine Spec / 声明机器规格

The easiest way to give your machine Ω energy storage:

```java
import com.endlessepoch.core.api.energy.*;
import com.endlessepoch.core.api.tier.VoltageTier;

// Builder mode / 详细模式
OmegaStorage storage = MachineSpec.builder(VoltageTier.MV)
    .capacity(OmegaValue.of(10_000))    // 10 kΩ capacity / 容量
    .maxInput(OmegaValue.of(128))       // 128 Ω/t input limit / 输入限制
    .maxOutput(OmegaValue.of(128))      // 128 Ω/t output limit / 输出限制
    .build()
    .createStorage();

// Shortcut / 简写模式 (same result)
OmegaStorage storage = MachineSpec.simple(VoltageTier.MV, 10_000, 128).createStorage();
```

You only need `OmegaStorage` — it already implements `IOmegaEnergyStorage`.

---

## 2. Register Capability / 注册 Capability

In your block entity class:

```java
public class MyMachineBE extends BlockEntity implements IOmegaEnergyStorage {
    private final OmegaStorage storage = MachineSpec.simple(VoltageTier.MV, 10_000, 128)
            .createStorage();

    public MyMachineBE(BlockPos pos, BlockState state) {
        super(MY_BE.get(), pos, state);
    }

    // Delegate all interface methods to storage:
    @Override public EnergyPacket receivePacket(EnergyPacket p, boolean sim) { return storage.receivePacket(p, sim); }
    @Override public EnergyPacket extractPacket(VoltageTier t, boolean sim) { return storage.extractPacket(t, sim); }
    @Override public OmegaValue receiveEnergy(OmegaValue a, boolean sim) { return storage.receiveEnergy(a, sim); }
    @Override public OmegaValue extractEnergy(OmegaValue a, boolean sim) { return storage.extractEnergy(a, sim); }
    @Override public OmegaValue getEnergyStored() { return storage.getEnergyStored(); }
    @Override public OmegaValue getEnergyStored(VoltageTier t) { return storage.getEnergyStored(t); }
    @Override public OmegaValue getCapacity() { return storage.getCapacity(); }
    @Override public OmegaValue getMaxInput() { return storage.getMaxInput(); }
    @Override public OmegaValue getMaxOutput() { return storage.getMaxOutput(); }
    @Override public VoltageTier getTier() { return storage.getTier(); }
}
```

Register the capability in your mod constructor:

```java
// In your @Mod class constructor:
modEventBus.addListener((RegisterCapabilitiesEvent event) -> {
    event.registerBlockEntity(
            EECoreCapabilities.OMEGA_ENERGY,
            MY_BE.get(),
            (be, side) -> be  // your block entity implements IOmegaEnergyStorage
    );
});
```

---

## 3. Access Other Machines' Energy / 访问其他机器的能量

Using capability (no compile-time dependency on the other mod):

```java
var cap = level.getCapability(
    EECoreCapabilities.OMEGA_ENERGY, neighborPos, side
);
if (cap != null) {
    // Send energy / 发送能量
    EnergyPacket packet = new EnergyPacket(VoltageTier.MV, 1, OmegaValue.of(100));
    EnergyPacket accepted = cap.receivePacket(packet, false);

    // Extract energy / 提取能量
    EnergyPacket extracted = cap.extractPacket(VoltageTier.MV, false);

    // Query / 查询
    OmegaValue stored = cap.getEnergyStored();
    OmegaValue capacity = cap.getCapacity();
}
```

---

## 4. Listen to Energy Events / 监听能量事件

```java
@SubscribeEvent
static void onEnergyTransfer(EnergyTransferEvent event) {
    if (event.getPhase() == EnergyTransferEvent.Phase.RECEIVE) {
        System.out.println("Received " + event.getAccepted() + " Ω at " + event.getTier());
    }
}
```

---

## 5. Saving & Loading NBT / NBT 读写

```java
// Save / 保存
CompoundTag tag = new CompoundTag();
storage.saveToNBT(tag);

// Load / 加载
storage.loadFromNBT(tag);
```

---

## Full Example Block Entity / 完整示例

```java
@BlockEntity(MOD_ID + ":my_machine")
public class MyMachineBE extends BlockEntity implements IOmegaEnergyStorage {
    private final OmegaStorage storage = MachineSpec
            .builder(VoltageTier.HV)
            .capacity(OmegaValue.of(100_000))
            .maxIO(OmegaValue.of(512))
            .build()
            .createStorage();

    public MyMachineBE(BlockPos pos, BlockState state) {
        super(MY_MACHINE_BE.get(), pos, state);
    }

    // === IOmegaEnergyStorage ===
    @Override public EnergyPacket receivePacket(EnergyPacket p, boolean s) { return storage.receivePacket(p, s); }
    @Override public EnergyPacket extractPacket(VoltageTier t, boolean s) { return storage.extractPacket(t, s); }
    @Override public OmegaValue receiveEnergy(OmegaValue a, boolean s) { return storage.receiveEnergy(a, s); }
    @Override public OmegaValue extractEnergy(OmegaValue a, boolean s) { return storage.extractEnergy(a, s); }
    @Override public OmegaValue getEnergyStored() { return storage.getEnergyStored(); }
    @Override public OmegaValue getEnergyStored(VoltageTier t) { return storage.getEnergyStored(t); }
    @Override public OmegaValue getCapacity() { return storage.getCapacity(); }
    @Override public OmegaValue getMaxInput() { return storage.getMaxInput(); }
    @Override public OmegaValue getMaxOutput() { return storage.getMaxOutput(); }
    @Override public VoltageTier getTier() { return storage.getTier(); }

    // === NBT ===
    @Override protected void saveAdditional(CompoundTag tag, HolderLookup.Provider p) {
        super.saveAdditional(tag, p);
        storage.saveToNBT(tag);
    }
    @Override protected void loadAdditional(CompoundTag tag, HolderLookup.Provider p) {
        super.loadAdditional(tag, p);
        storage.loadFromNBT(tag);
    }
}
```

---

## More Systems / 更多系统

- [Multiblock Structure System](multiblock.md) — ECS format, tag/limit, part blocks, IItemHandler, wrench
- [NovaNet Energy Network](novanet.md) — Wireless energy transfer framework
- [Team System](team.md) — Per-team energy network isolation
- [Emissive Rendering](emissive.md) — Glow layer registration
- [Ore System](ore.md) — One-click ore block registration with dynamic rendering
- [Animation API](animation.md) — Rainbow/blink/gradient tooltips
