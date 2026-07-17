package com.endlessepoch.core.menu;

import com.endlessepoch.core.api.energy.OmegaValue;
import com.endlessepoch.core.nova.block.part.PartBlockEntity;
import com.endlessepoch.core.network.EnergySyncPacket;
import com.endlessepoch.core.registry.Menus;
import net.minecraft.core.BlockPos; import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.FriendlyByteBuf; import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory; import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.*; import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.FluidUtil;
import net.neoforged.neoforge.network.PacketDistributor;

public class HatchMenu extends AbstractContainerMenu {
    private final BlockPos pos; final PartBlockEntity hatch;
    public final int tankCount; public final ResourceLocation[] fId; public final int[] fAmt, fCap;

    private OmegaValue energyStored = OmegaValue.zero();
    private OmegaValue energyCapacity = OmegaValue.zero();
    private String lastSyncedStored = "";
    private String lastSyncedCapacity = "";
    private ResourceLocation[] lastSyncedFluidId;
    private int[] lastSyncedFluidAmt;
    ServerPlayer viewer;

    public HatchMenu(int id, Inventory inv, PartBlockEntity hatch) {
        super(Menus.HATCH.get(), id); this.hatch=hatch; this.pos=hatch.getBlockPos();
        var tanks=hatch.getFluidTanks(); this.tankCount=tanks.size();
        this.fId=new ResourceLocation[tankCount]; this.fAmt=new int[tankCount]; this.fCap=new int[tankCount];
        syncFromBE(); addSlots(inv);
    }

    public HatchMenu(int id, Inventory inv, FriendlyByteBuf buf) {
        super(Menus.HATCH.get(), id); this.pos=buf.readBlockPos(); this.hatch=null;
        this.tankCount=buf.readVarInt();
        this.fId=new ResourceLocation[tankCount]; this.fAmt=new int[tankCount]; this.fCap=new int[tankCount];
        for(int i=0;i<tankCount;i++){fId[i]=buf.readBoolean()?buf.readResourceLocation():null;fAmt[i]=buf.readVarInt();fCap[i]=buf.readVarInt();}
        this.energyStored = OmegaValue.of(buf.readUtf());
        this.energyCapacity = OmegaValue.of(buf.readUtf());
        addSlots(inv);
    }

    public void setViewer(ServerPlayer p) { this.viewer = p; }

    /** Called from EnergySyncPacket handler on client. / 客户端接收 EnergySyncPacket 后调用。 */
    public void setEnergyData(String stored, String capacity) {
        this.energyStored = OmegaValue.of(stored);
        this.energyCapacity = OmegaValue.of(capacity);
    }

    public OmegaValue getEnergyStored() { return energyStored; }
    public OmegaValue getEnergyCapacity() { return energyCapacity; }

    @Override public void broadcastChanges() {
        super.broadcastChanges();
        if (hatch != null) syncFromBE();
    }

    private void syncFromBE() {
        if (hatch == null) return;
        var ts = hatch.getFluidTanks();
        if (lastSyncedFluidId == null) {
            lastSyncedFluidId = new ResourceLocation[tankCount];
            lastSyncedFluidAmt = new int[tankCount];
        }
        for (int i = 0; i < tankCount && i < ts.size(); i++) {
            var f = ts.get(i);
            fAmt[i] = f.getFluidAmount(); fCap[i] = f.getCapacity();
            fId[i] = f.getFluid().isEmpty() ? null : BuiltInRegistries.FLUID.getKey(f.getFluid().getFluid());
            // Push changes even when they didn't come from a GUI click (JEI drag, pipes)
            // 非点击来源的变更也推送（JEI 拖拽、管道）
            if (viewer != null && (fAmt[i] != lastSyncedFluidAmt[i]
                    || !java.util.Objects.equals(fId[i], lastSyncedFluidId[i]))) {
                lastSyncedFluidAmt[i] = fAmt[i];
                lastSyncedFluidId[i] = fId[i];
                PacketDistributor.sendToPlayer(viewer,
                        new com.endlessepoch.core.network.FluidSyncPacket(pos, i, fId[i], fAmt[i], fCap[i]));
            }
        }

        var es = hatch.getEnergyStorage();
        OmegaValue curStored = es != null ? es.getEnergyStored() : OmegaValue.zero();
        OmegaValue curCap = es != null ? es.getCapacity() : OmegaValue.zero();
        this.energyStored = curStored;
        this.energyCapacity = curCap;

        String curStoredStr = curStored.toBigInteger().toString();
        String curCapStr = curCap.toBigInteger().toString();
        if (!curStoredStr.equals(lastSyncedStored) || !curCapStr.equals(lastSyncedCapacity)) {
            lastSyncedStored = curStoredStr;
            lastSyncedCapacity = curCapStr;
            if (viewer != null)
                PacketDistributor.sendToPlayer(viewer, new EnergySyncPacket(pos, curStoredStr, curCapStr));
        }
    }

    /** Called from FluidSyncPacket handler on client. / 客户端接收 FluidSyncPacket 后调用。 */
    public void setFluidData(int i, ResourceLocation id, int amt, int cap) {
        if (i < tankCount) { fId[i] = id; fAmt[i] = amt; fCap[i] = cap; }
    }

    /** Handle fluid container click on visual slot. / 处理流体容器点击。 */
    public ItemStack handleFluidClick(int tankIdx, ItemStack held) {
        if (hatch == null || tankIdx >= hatch.getFluidTanks().size()) return held;
        var ft = hatch.getFluidTanks().get(tankIdx);
        var r = net.neoforged.neoforge.fluids.FluidUtil.tryEmptyContainer(held, ft, Integer.MAX_VALUE, null, true);
        return r.isSuccess() ? r.getResult() : held;
    }

    private void addSlots(Inventory inv) {
        boolean hasE = hatch != null && hatch.getEnergyStorage() != null;
        int sx = (hasE ? 64 : 80);
        for (int i = 0; i < Math.max(1, tankCount); i++) {
            final int ti = i;
            this.addSlot(new Slot(new net.minecraft.world.SimpleContainer(1), 0, sx + i * 20, 38) {
                @Override public boolean mayPlace(ItemStack s) { return false; }
                @Override public int getMaxStackSize() { return 0; }
            });
        }
        for (int r = 0; r < 3; r++)
            for (int c = 0; c < 9; c++)
                addSlot(new Slot(inv, c + r * 9 + 9, 8 + c * 18, 84 + r * 18));
        for (int c = 0; c < 9; c++)
            addSlot(new Slot(inv, c, 8 + c * 18, 142));
    }

    @Override
    public void clicked(int slotId, int button, ClickType type, Player player) {
        // Creative fluid template: click with a fluid = set template (container untouched),
        // empty hand = clear. Empty containers fall through and fill from the infinite tank.
        // 创造流体模板：手持流体点击=设模板（容器不消耗），空手=清除。空容器走通用路径从无限罐取液。
        if (slotId >= 0 && slotId < tankCount
                && hatch instanceof com.endlessepoch.core.nova.block.part.CreativeHatchBlockEntity ch
                && ch.isFluidTemplate()) {
            var held = player.containerMenu.getCarried();
            if (held.isEmpty()) {
                ch.setFluidTemplate(slotId, null);
                syncFromBE(); sendSync(slotId, player); return;
            }
            var contained = FluidUtil.getFluidContained(held);
            if (contained.isPresent()) {
                ch.setFluidTemplate(slotId, contained.get().getFluid());
                syncFromBE(); sendSync(slotId, player); return;
            }
        }
        if (slotId >= 0 && slotId < tankCount && hatch != null && !player.containerMenu.getCarried().isEmpty()) {
            var held = player.containerMenu.getCarried();
            if (FluidUtil.getFluidHandler(held).isPresent()) {
                var ft = hatch.getFluidTanks().get(slotId);
                var r = FluidUtil.tryEmptyContainer(held, ft, Integer.MAX_VALUE, player, true);
                if (r.isSuccess()) {
                    player.containerMenu.setCarried(r.getResult());
                    syncFromBE(); sendSync(slotId, player); return;
                }
                r = FluidUtil.tryFillContainer(held, ft, Integer.MAX_VALUE, player, true);
                if (r.isSuccess()) {
                    player.containerMenu.setCarried(r.getResult());
                    syncFromBE(); sendSync(slotId, player); return;
                }
            }
        }
        super.clicked(slotId, button, type, player);
    }

    private void sendSync(int slotId, Player player) {
        PacketDistributor.sendToPlayer((ServerPlayer) player,
                new com.endlessepoch.core.network.FluidSyncPacket(pos, slotId,
                        fId[slotId], fAmt[slotId], fCap[slotId]));
    }

    public BlockPos getPos() { return pos; }

    @Override public ItemStack quickMoveStack(Player p, int i) { return ItemStack.EMPTY; }
    @Override public boolean stillValid(Player p) { return p.distanceToSqr(pos.getX() + .5, pos.getY() + .5, pos.getZ() + .5) <= 64; }
}
