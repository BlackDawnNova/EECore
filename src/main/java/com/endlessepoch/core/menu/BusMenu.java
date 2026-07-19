package com.endlessepoch.core.menu;

import com.endlessepoch.core.nova.block.part.*;
import com.endlessepoch.core.registry.Menus;
import net.minecraft.core.BlockPos; import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.FriendlyByteBuf; import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.*; import net.minecraft.world.inventory.*; import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.FluidUtil;
import net.neoforged.neoforge.items.SlotItemHandler;

public class BusMenu extends AbstractContainerMenu {
    private final InputBusBlockEntity bus; private final int slotCount; private final boolean isOutput;
    private final boolean creative; private final boolean oversized; private final boolean locked;
    private final BlockPos pos; private final int fluidSlots; private final ContainerData data;
    final ResourceLocation[] fId; final int[] fAmt, fCap;
    private net.minecraft.server.level.ServerPlayer viewer;
    private ResourceLocation[] lastFid; private int[] lastFAmt;
    /** Client-side template-count sync for creative buses: [i*2]=low 15 bits, [i*2+1]=high bits. */
    private final ContainerData templateCountData;
    private int[] clientTemplateCounts;
    /** Lock-state sync for locked-slot buses: 1=locked, 0=unlocked per slot. / 巨量锁定总线锁状态同步：1=已锁。 */
    private final ContainerData lockData;
    private boolean[] clientLockStates;
    /** Oversized-bus real-count sync — 4×14-bit per slot (56 bits). / 巨量总线真实数量同步——每槽 4×14bit。 */
    private final ContainerData storedAmountData;
    private long[] clientStoredAmounts;

    public void setViewer(net.minecraft.server.level.ServerPlayer p){this.viewer=p;}

    public BusMenu(int id, Inventory inv, InputBusBlockEntity bus) {
        super(Menus.BUS.get(), id); this.bus=bus; this.slotCount=bus.getSlotCount(); this.isOutput=bus.isOutput(); this.pos=bus.getBlockPos();
        this.creative=bus.isCreative(); this.oversized=bus.isOversized();
        this.locked=bus instanceof com.endlessepoch.core.api.part.ILockedSlotBus;
        this.data=new SimpleContainerData(2); addDataSlots(data);
        // Template data only for creative INPUT side / 模板数据仅创造输入侧
        this.templateCountData = creative && !isOutput && bus instanceof com.endlessepoch.core.nova.block.part.CreativeBusBlockEntity cb
                ? templateCountData(cb) : new SimpleContainerData(0);
        if (creative && !isOutput) addDataSlots(templateCountData);
        // Stored amount sync — works for both creative oversized and locked oversized / 巨量数据同步——兼创造和锁定
        this.storedAmountData = oversized ? storedAmountData(bus) : new SimpleContainerData(0);
        if (oversized) addDataSlots(storedAmountData);
        // Lock data must register AFTER fluidSlots is set, or getCount() reads 0 for fluid bins
        // 锁数据必须在 fluidSlots 赋值后注册，否则流体仓的 getCount() 读到 0
        var ts=((PartBlockEntity)bus).getFluidTanks();
        int fs=0; if(!ts.isEmpty()&&bus.getBlockState().getBlock() instanceof PartBlock pb)fs=pb.fluidSlots;
        this.fluidSlots=fs;
        this.lockData = locked ? lockData(bus) : new SimpleContainerData(0);
        if (locked) addDataSlots(lockData);
        this.fId=new ResourceLocation[Math.max(1,fs)];this.fAmt=new int[Math.max(1,fs)];this.fCap=new int[Math.max(1,fs)];
        syncFromBE(); addBusSlots(bus.getInventory()); addPlayerSlots(inv);
    }
    public BusMenu(int id, Inventory inv, FriendlyByteBuf buf) {
        super(Menus.BUS.get(), id); this.pos=buf.readBlockPos(); this.slotCount=buf.readVarInt();
        this.isOutput=buf.readBoolean(); this.creative=buf.readBoolean();
        this.oversized=buf.readBoolean(); this.fluidSlots=buf.readVarInt(); this.bus=null;
        this.locked=oversized && !creative && !isOutput && (slotCount>0||fluidSlots>0); // items or fluids = locked / 有物品或流体槽=锁定
        this.data=new SimpleContainerData(2); addDataSlots(data);
        this.clientTemplateCounts = new int[creative && !isOutput ? slotCount : 0];
        this.templateCountData = creative && !isOutput ? clientTemplateCountData() : new SimpleContainerData(0);
        if (creative && !isOutput) addDataSlots(templateCountData);
        this.clientStoredAmounts = new long[oversized ? slotCount : 0];
        this.storedAmountData = oversized ? clientStoredAmountData() : new SimpleContainerData(0);
        if (oversized) addDataSlots(storedAmountData);
        this.clientLockStates = new boolean[locked ? Math.max(1, slotCount + fluidSlots) : 0];
        this.lockData = locked ? clientLockData() : new SimpleContainerData(0);
        if (locked) addDataSlots(lockData);
        this.fId=new ResourceLocation[Math.max(1,fluidSlots)];this.fAmt=new int[Math.max(1,fluidSlots)];this.fCap=new int[Math.max(1,fluidSlots)];
        for(int i=0;i<fluidSlots;i++){fId[i]=buf.readBoolean()?buf.readResourceLocation():null;fAmt[i]=buf.readVarInt();fCap[i]=buf.readVarInt();}
        // Client display backing — ItemStackHandler, NOT SimpleContainer: the latter's
        // setItem clamps count to 64 and silently truncates big template counts.
        // 客户端显示底座——用 ItemStackHandler 而非 SimpleContainer：后者 setItem 会把
        // count 钳到 64，大模板数量被静默截断。
        addBusSlots(new net.neoforged.neoforge.items.ItemStackHandler(slotCount));
        addPlayerSlots(inv);
    }
    @Override public void broadcastChanges(){if(bus!=null)syncFromBE();super.broadcastChanges();}
    private void syncFromBE(){if(bus==null)return;var ts=((PartBlockEntity)bus).getFluidTanks();
        if(lastFid==null){lastFid=new ResourceLocation[Math.max(1,fluidSlots)];lastFAmt=new int[Math.max(1,fluidSlots)];}
        for(int i=0;i<fluidSlots&&i<ts.size();i++){var f=ts.get(i);fAmt[i]=f.getFluidAmount();fCap[i]=f.getCapacity();fId[i]=f.getFluid().isEmpty()?null:BuiltInRegistries.FLUID.getKey(f.getFluid().getFluid());
            // Push non-click changes too (JEI drag, pipes) / 非点击来源的变更也推送（JEI 拖拽、管道）
            if(viewer!=null&&(fAmt[i]!=lastFAmt[i]||!java.util.Objects.equals(fId[i],lastFid[i]))){
                lastFAmt[i]=fAmt[i];lastFid[i]=fId[i];
                net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(viewer,
                    new com.endlessepoch.core.network.FluidSyncPacket(pos,i,fId[i],fAmt[i],fCap[i]));
            }}}
    public int getFluidAmt(int i){return i<fluidSlots?fAmt[i]:0;} public int getFluidCap(int i){return i<fluidSlots?fCap[i]:0;}
    public void setFluidData(int i,ResourceLocation id,int amt,int cap){if(i<fluidSlots){fId[i]=id;fAmt[i]=amt;fCap[i]=cap;}}
    public ResourceLocation getFluidId(int i){return i<fluidSlots?fId[i]:null;}
    public int getFluidSlots(){return fluidSlots;} public InputBusBlockEntity getBus(){return bus;} public int getSlotCount(){return slotCount;}
    /** Phantom infinite bus? / 是否为幻影无限总线。 */
    public boolean isCreative(){return creative;}
    /** Output-side bus? / 是否为输出侧总线。 */
    public BlockPos getPos(){return pos;}
    /** Client synced template count for slot i. / 客户端同步的第 i 槽模板数量。 */
    public int templateCount(int i) {
        if (!creative || templateCountData.getCount() == 0) return 64;
        if (bus instanceof com.endlessepoch.core.nova.block.part.CreativeBusBlockEntity cb)
            return cb.getTemplateCount(i);        if (clientTemplateCounts == null || i < 0 || i >= clientTemplateCounts.length) return 64;
        return clientTemplateCounts[i];    }

    /** Per-slot sync: split 28-bit count into two 14-bit DataSlot values to avoid short overflow. / 槽数量同步：拆成两个 14-bit 值防 short 溢出。 */
    private static ContainerData templateCountData(com.endlessepoch.core.nova.block.part.CreativeBusBlockEntity cb) {
        return new ContainerData() {
            @Override public int get(int i) {
                int c = i / 2 < cb.getSlotCount() ? cb.getTemplateCount(i / 2) : 64;
                return i % 2 == 0 ? c & 0x3FFF : (c >> 14) & 0x3FFF;
            }
            @Override public void set(int i, int v) {}
            @Override public int getCount() { return cb.getSlotCount() * 2; }
        };
    }
    private ContainerData clientTemplateCountData() {
        return new ContainerData() {
            @Override public int get(int i) { return 0; }
            @Override public void set(int i, int v) {
                if (clientTemplateCounts == null) return;
                int slot = i / 2;
                if (slot >= clientTemplateCounts.length) return;
                if (i % 2 == 0) clientTemplateCounts[slot] = (clientTemplateCounts[slot] & ~0x3FFF) | (v & 0x3FFF);
                else clientTemplateCounts[slot] = (clientTemplateCounts[slot] & 0x3FFF) | ((v & 0x3FFF) << 14);
            }
            @Override public int getCount() { return clientTemplateCounts != null ? clientTemplateCounts.length * 2 : 0; }
        };
    }
    private ContainerData clientStoredAmountData() {
        return new ContainerData() {
            @Override public int get(int i) { return 0; }
            @Override public void set(int i, int v) {
                if (clientStoredAmounts == null) return;
                int slot = i / 4;
                if (slot >= clientStoredAmounts.length) return;
                int shift = 14 * (i % 4);
                clientStoredAmounts[slot] = (clientStoredAmounts[slot] & ~(0x3FFFL << shift)) | ((long) (v & 0x3FFF) << shift);
            }
            @Override public int getCount() { return clientStoredAmounts != null ? clientStoredAmounts.length * 4 : 0; }
        };
    }
    /** Server side: split each slot's long into 4×14-bit values for locked oversized buses. / 服务端：锁定巨量每槽拆 4×14bit。 */
    private static ContainerData storedAmountData(InputBusBlockEntity bus) {
        return new ContainerData() {
            @Override public int get(int i) {
                long a = i / 4 < bus.getSlotCount() ? (
                        bus instanceof com.endlessepoch.core.nova.block.part.CreativeOversizedBusBlockEntity ob ? ob.getStoredAmount(i / 4) :
                        bus instanceof com.endlessepoch.core.api.part.ILockedSlotBus lb ? lb.getStoredAmount(i / 4) : 0
                ) : 0;
                return (int) ((a >>> (14 * (i % 4))) & 0x3FFF);
            }
            @Override public void set(int i, int v) {}
            @Override public int getCount() { return bus.getSlotCount() * 4; }
        };
    }
    /** Server side: lock state per slot. / 服务端：每槽锁状态。 */
    private ContainerData lockData(InputBusBlockEntity bus) {
        var lb = (com.endlessepoch.core.api.part.ILockedSlotBus) bus;
        return new ContainerData() {
            @Override public int get(int i) { return lb.isSlotLocked(i) ? 1 : 0; }
            @Override public void set(int i, int v) {}
            @Override public int getCount() { return Math.max(1, bus.getSlotCount() + fluidSlots); }
        };
    }
    private ContainerData clientLockData() {
        return new ContainerData() {
            @Override public int get(int i) { return 0; }
            @Override public void set(int i, int v) {
                if (clientLockStates != null && i >= 0 && i < clientLockStates.length)
                    clientLockStates[i] = v != 0;
            }
            @Override public int getCount() { return clientLockStates.length; }
        };
    }
    /** Real stored count for oversized-bus slot i (synced to client). / 巨量总线第 i 槽真实数量（已同步客户端）。 */
    public long storedAmount(int i) {
        if (bus instanceof com.endlessepoch.core.nova.block.part.CreativeOversizedBusBlockEntity ob)
            return ob.getStoredAmount(i);        if (bus instanceof com.endlessepoch.core.api.part.ILockedSlotBus lb)
            return lb.getStoredAmount(i);
        if (clientStoredAmounts == null || i < 0 || i >= clientStoredAmounts.length) return 0;
        return clientStoredAmounts[i];    }
    /** Locked-slot bus lock state for slot i. / 巨量锁定总线第 i 槽是否已锁。 */
    /** Lock state for item slot i (0..slotCount-1) or fluid slot (slotCount..+fluidSlots). / 物品或流体槽锁状态。 */
    public boolean lockState(int i) {
        if (bus instanceof com.endlessepoch.core.api.part.ILockedSlotBus lb)
            return lb.isSlotLocked(i);
        if (clientLockStates != null && i >= 0 && i < clientLockStates.length)
            return clientLockStates[i];
        return false;
    }
    public boolean isOutputBus(){return isOutput;}
    public boolean isOversized(){return oversized||(creative&&isOutput);}
    /** Side-by-side layout when both items and fluids exist. / 有物品+流体时左右分栏。 */
    public boolean sideBySide(){return slotCount>0&&fluidSlots>0;}
    /** Adaptive grid columns — ceil(sqrt(N)), capped at 9. / 自适应网格列数——ceil(sqrt(N))，封顶9。 */
    public int busCols(){int n=slotCount>0?slotCount:fluidSlots;int c=(int)Math.ceil(Math.sqrt(Math.max(1,n)));return Math.min(9,c);}
    /** Grid rows for height math (side-by-side takes the taller column). / 网格行数（分栏时取较高一侧）。 */
    public int busRows(){int r=(slotCount+busCols()-1)/busCols();return sideBySide()?Math.max(r,(fluidSlots+busCols()-1)/busCols()):r;}
    /** Fluid rows — 0 when side-by-side, else ceil(N/cols). / 流体行数——分栏为0，否则 ceil(N/cols)。 */
    public int fluidRows(){if(sideBySide())return 0;int c=busCols();return (fluidSlots+c-1)/c;}
    /** Total GUI width (adaptive, minimum 176). / GUI 总宽（自适应，最低 176）。 */
    public int imageW(){if(sideBySide()){int c=busCols();int w=Math.max(176,12+c*18+8+c*18);return c>4?w+11:w;}int c=busCols();return Math.max(176,8+(162-c*18)/2+c*18);}
    /** Total GUI height (adaptive). / GUI 总高（自适应）。 */
    public int imageH(){int cols=busCols(),rs=busRows(),fR=fluidRows();int g=(rs+fR)<=3?14:20;return Math.max(166,18+(rs+fR)*18+g+84);}
    /** Item grid left edge — centered when side-by-side. / 物品网格左缘——分栏时整体居中。 */
    public int busX(){if(sideBySide()){int c=busCols();int tw=c*18+8+c*18;return Math.max(12,(imageW()-tw)/2);}return 8+(162-busCols()*18)/2;}
    /** Fluid ghost slot position. / 虚拟流体槽坐标。 */
    /** Fluid slot X — right of items when side-by-side, centered otherwise. / 流体槽X——分栏时在物品右侧，否则居中。 */
    public int fluidSlotX(int i){if(sideBySide()){int c=busCols();return busX()+c*18+8+(i%c)*18;}int c=busCols();return 8+(162-c*18)/2+(i%c)*18;}
    /** Fluid slot Y — starts after item rows (or top if no items). / 流体槽Y——物品行之后（无物品则顶部）。 */
    public int fluidSlotY(int i){if(sideBySide()){int c=busCols();return 18+(i/c)*18;}int rs=slotCount>0?busRows():0,c=busCols();return 18+rs*18+(i/c)*18;}
    private void addBusSlots(net.neoforged.neoforge.items.IItemHandler h){
        // Ghost fluid slots for click interaction / 虚拟流体槽
        for(int i=0;i<fluidSlots;i++){
            this.addSlot(new Slot(new net.minecraft.world.SimpleContainer(1),0,fluidSlotX(i),fluidSlotY(i)){
                @Override public boolean mayPlace(ItemStack s){return false;}
                @Override public int getMaxStackSize(){return 0;}
            });
        }
        int cols=busCols();int x=busX();int fR=fluidRows();
        for(int i=0;i<slotCount;i++){int r=i/cols,cl=i%cols;this.addSlot(new SlotItemHandler(h,i,x+cl*18,18+fR*18+r*18){@Override public boolean mayPlace(ItemStack s){return !isOutput&&!creative&&!locked;}@Override public ItemStack remove(int amount){return h.extractItem(getSlotIndex(),amount,false);}});}}
    private void addPlayerSlots(Inventory inv){int rs=busRows(),fR=fluidRows(),g=(rs+fR)<=3?14:20;int iy=18+fR*18+rs*18+g,hy=iy+3*18+4;
        int sx=(imageW()-162)/2;
        for(int r=0;r<3;r++)for(int c=0;c<9;c++)this.addSlot(new Slot(inv,c+r*9+9,sx+c*18,iy+r*18));for(int c=0;c<9;c++)this.addSlot(new Slot(inv,c,sx+c*18,hy));}
    @Override public void clicked(int slotId,int button,ClickType type,Player player){
        // Creative bus: left-click with item = set template, right-click = clear;
        // left-click empty-handed is handled client-side (count popup), no server action.
        // 创造总线：手持物品左键=设模板，右键=清除；空手左键由客户端处理（数量弹框），服务端不动作
        int firstBus=fluidSlots, lastBus=firstBus+slotCount-1;
        if(creative&&slotId>=firstBus&&slotId<=lastBus){
            if(oversized){super.clicked(slotId,button,type,player);return;}
            if(bus instanceof com.endlessepoch.core.nova.block.part.CreativeBusBlockEntity cb){
                if(button==1) cb.setTemplate(slotId-firstBus,ItemStack.EMPTY);
                else if(!getCarried().isEmpty()) cb.setTemplate(slotId-firstBus,getCarried());
            }
            return;
        }
        // Creative input assembly: fluid ghost slot clicks configure the template (container untouched)
        // 创造输入总成：流体虚拟槽点击配置模板（容器不消耗），空容器走通用路径取液
        if(creative&&!isOutput&&slotId>=0&&slotId<fluidSlots
                &&bus instanceof com.endlessepoch.core.nova.block.part.CreativeBusBlockEntity cb){
            var held=player.containerMenu.getCarried();
            if(held.isEmpty()){cb.setFluidTemplate(slotId,null);syncFromBE();return;}
            var contained=FluidUtil.getFluidContained(held);
            if(contained.isPresent()){cb.setFluidTemplate(slotId,contained.get().getFluid());syncFromBE();return;}
        }
        if(slotId>=0&&slotId<fluidSlots&&bus!=null&&!player.containerMenu.getCarried().isEmpty()){
            var held=player.containerMenu.getCarried();
            if(FluidUtil.getFluidHandler(held).isPresent()){
                var ft=((PartBlockEntity)bus).getFluidTanks().get(slotId);
                var r=FluidUtil.tryEmptyContainer(held,ft,Integer.MAX_VALUE,player,true);
                if(r.isSuccess()){
                    player.containerMenu.setCarried(r.getResult());syncFromBE();
                    net.neoforged.neoforge.network.PacketDistributor.sendToPlayer((net.minecraft.server.level.ServerPlayer)player,
                        new com.endlessepoch.core.network.FluidSyncPacket(pos,slotId,fId[slotId],fAmt[slotId],fCap[slotId]));
                    return;
                }
                r=FluidUtil.tryFillContainer(held,ft,Integer.MAX_VALUE,player,true);
                if(r.isSuccess()){
                    player.containerMenu.setCarried(r.getResult());syncFromBE();
                    net.neoforged.neoforge.network.PacketDistributor.sendToPlayer((net.minecraft.server.level.ServerPlayer)player,
                        new com.endlessepoch.core.network.FluidSyncPacket(pos,slotId,fId[slotId],fAmt[slotId],fCap[slotId]));
                    return;
                }
            }
        }
        super.clicked(slotId,button,type,player);
    }
    @Override public ItemStack quickMoveStack(Player p, int idx) {
        Slot slot = this.slots.get(idx);
        if (!slot.hasItem()) return ItemStack.EMPTY;
        ItemStack src = slot.getItem();
        ItemStack copy = src.copy();

        int firstBusSlot = fluidSlots;
        int lastBusSlot = firstBusSlot + slotCount - 1;
        int firstPlayerSlot = lastBusSlot + 1;
        int lastPlayerSlot = this.slots.size() - 1;

        // Creative bus: shift-click = clear template (bus slot) or set first empty (player slot)
        // 创造总线：shift 点击 = 清除模板（总线槽）或设第一个空槽（背包槽）
        if (creative) {
            if (bus instanceof com.endlessepoch.core.nova.block.part.CreativeBusBlockEntity cb) {
                if (idx >= firstBusSlot && idx <= lastBusSlot) {
                    cb.setTemplate(idx - firstBusSlot, ItemStack.EMPTY);
                } else {
                    for (int i = 0; i < slotCount; i++) {
                        if (cb.getInventory().getStackInSlot(i).isEmpty()) { cb.setTemplate(i, src); break; }
                    }
                }
            }
            return ItemStack.EMPTY;
        }

        if (idx >= firstBusSlot && idx <= lastBusSlot) {
            if (!this.moveItemStackTo(src, firstPlayerSlot, lastPlayerSlot + 1, true))
                return ItemStack.EMPTY;
        } else {
            if (isOutput) return ItemStack.EMPTY;
            if (!this.moveItemStackTo(src, firstBusSlot, lastBusSlot + 1, false))
                return ItemStack.EMPTY;
        }
        if (src.isEmpty()) slot.set(ItemStack.EMPTY);
        else slot.setChanged();
        return copy;
    }
    @Override public boolean stillValid(Player p){return p.distanceToSqr(pos.getX()+.5,pos.getY()+.5,pos.getZ()+.5)<=64;}
}
