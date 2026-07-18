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
    private final boolean creative; private final boolean oversized;
    private final BlockPos pos; private final int fluidSlots; private final ContainerData data;
    final ResourceLocation[] fId; final int[] fAmt, fCap;
    private net.minecraft.server.level.ServerPlayer viewer;
    private ResourceLocation[] lastFid; private int[] lastFAmt;
    /** Client-side template-count sync for creative buses: [i*2]=low 15 bits, [i*2+1]=high bits. */
    private final ContainerData templateCountData;
    private int[] clientTemplateCounts;
    /** Oversized-bus real-count sync — 4×14-bit per slot (56 bits). / 巨量总线真实数量同步——每槽 4×14bit。 */
    private final ContainerData storedAmountData;
    private long[] clientStoredAmounts;

    public void setViewer(net.minecraft.server.level.ServerPlayer p){this.viewer=p;}

    public BusMenu(int id, Inventory inv, InputBusBlockEntity bus) {
        super(Menus.BUS.get(), id); this.bus=bus; this.slotCount=bus.getSlotCount(); this.isOutput=bus.isOutput(); this.pos=bus.getBlockPos();
        this.creative=bus.isCreative(); this.oversized=bus instanceof com.endlessepoch.core.nova.block.part.CreativeOversizedBusBlockEntity;
        this.data=new SimpleContainerData(2); addDataSlots(data);
        // Template data only for creative INPUT side — keeps data-slot indices aligned
        // with the client (oversized output is creative too but has no templates).
        // 模板数据仅创造输入侧注册——保证与客户端数据槽索引对齐（巨量输出也是 creative 但无模板）。
        this.templateCountData = creative && !isOutput && bus instanceof com.endlessepoch.core.nova.block.part.CreativeBusBlockEntity cb
                ? templateCountData(cb) : new SimpleContainerData(0);
        if (creative && !isOutput) addDataSlots(templateCountData);
        this.storedAmountData = oversized
                ? storedAmountData((com.endlessepoch.core.nova.block.part.CreativeOversizedBusBlockEntity) bus)
                : new SimpleContainerData(0);
        if (oversized) addDataSlots(storedAmountData);
        var ts=((PartBlockEntity)bus).getFluidTanks();
        int fs=0; if(!ts.isEmpty()&&bus.getBlockState().getBlock() instanceof PartBlock pb)fs=pb.fluidSlots;
        this.fluidSlots=fs;
        this.fId=new ResourceLocation[Math.max(1,fs)];this.fAmt=new int[Math.max(1,fs)];this.fCap=new int[Math.max(1,fs)];
        syncFromBE(); addBusSlots(bus.getInventory()); addPlayerSlots(inv);
    }
    public BusMenu(int id, Inventory inv, FriendlyByteBuf buf) {
        super(Menus.BUS.get(), id); this.pos=buf.readBlockPos(); this.slotCount=buf.readVarInt();
        this.isOutput=buf.readBoolean(); this.creative=buf.readBoolean(); this.oversized=creative&&isOutput; this.fluidSlots=buf.readVarInt(); this.bus=null;
        this.data=new SimpleContainerData(2); addDataSlots(data);
        this.clientTemplateCounts = new int[creative && !isOutput ? slotCount : 0];
        this.templateCountData = creative && !isOutput ? clientTemplateCountData() : new SimpleContainerData(0);
        if (creative && !isOutput) addDataSlots(templateCountData);
        this.clientStoredAmounts = new long[oversized ? slotCount : 0];
        this.storedAmountData = oversized ? clientStoredAmountData() : new SimpleContainerData(0);
        if (oversized) addDataSlots(storedAmountData);
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
            return cb.getTemplateCount(i); // server / 服务端
        if (clientTemplateCounts == null || i < 0 || i >= clientTemplateCounts.length) return 64;
        return clientTemplateCounts[i]; // client / 客户端
    }

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
    /** Server side: split each slot's long into 4×14-bit values. / 服务端：每槽 long 拆 4 段 14bit。 */
    private static ContainerData storedAmountData(com.endlessepoch.core.nova.block.part.CreativeOversizedBusBlockEntity ob) {
        return new ContainerData() {
            @Override public int get(int i) {
                long a = i / 4 < ob.getSlotCount() ? ob.getStoredAmount(i / 4) : 0;
                return (int) ((a >>> (14 * (i % 4))) & 0x3FFF);
            }
            @Override public void set(int i, int v) {}
            @Override public int getCount() { return ob.getSlotCount() * 4; }
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
    /** Real stored count for oversized-bus slot i (synced to client). / 巨量总线第 i 槽真实数量（已同步客户端）。 */
    public long storedAmount(int i) {
        if (bus instanceof com.endlessepoch.core.nova.block.part.CreativeOversizedBusBlockEntity ob)
            return ob.getStoredAmount(i); // server / 服务端
        if (clientStoredAmounts == null || i < 0 || i >= clientStoredAmounts.length) return 0;
        return clientStoredAmounts[i]; // client / 客户端
    }
    public boolean isOutputBus(){return isOutput;}
    public boolean isOversized(){return oversized||(creative&&isOutput);}
    /** Side-by-side creative assembly: items left 4×4, fluids right 4×4. / 创造输入总成左右分栏：左物品 4×4、右流体 4×4。 */
    public boolean sideBySide(){return creative&&!isOutput&&fluidSlots>0;}
    /** Bus grid columns — creative uses a 4-wide AE-style grid. / 总线网格列数——创造总线用 4 列 AE 风格网格。 */
    public int busCols(){return creative?Math.min(slotCount,4):Math.min(slotCount,9);}
    /** Grid rows for height math (side-by-side takes the taller column). / 网格行数（分栏时取较高一侧）。 */
    public int busRows(){int r=(slotCount+busCols()-1)/busCols();return sideBySide()?Math.max(r,(fluidSlots+3)/4):r;}
    /** Fluid rows stacked above items — 0 when side-by-side. / 叠在物品上方的流体行数——分栏时为 0。 */
    public int fluidRows(){return sideBySide()?0:(fluidSlots+8)/9;}
    /** Item grid left edge. / 物品网格左缘。 */
    public int busX(){return sideBySide()?12:8+(162-busCols()*18)/2;}
    /** Fluid ghost slot position. / 虚拟流体槽坐标。 */
    public int fluidSlotX(int i){return sideBySide()?92+(i%4)*18:8+(i%9)*18;}
    public int fluidSlotY(int i){return sideBySide()?18+(i/4)*18:18+(i/9)*18;}
    private void addBusSlots(net.neoforged.neoforge.items.IItemHandler h){
        // Ghost fluid slots for click interaction / 虚拟流体槽
        for(int i=0;i<fluidSlots;i++){
            this.addSlot(new Slot(new net.minecraft.world.SimpleContainer(1),0,fluidSlotX(i),fluidSlotY(i)){
                @Override public boolean mayPlace(ItemStack s){return false;}
                @Override public int getMaxStackSize(){return 0;}
            });
        }
        int cols=busCols();int x=busX();int fR=fluidRows();
        for(int i=0;i<slotCount;i++){int r=i/cols,cl=i%cols;this.addSlot(new SlotItemHandler(h,i,x+cl*18,18+fR*18+r*18){@Override public boolean mayPlace(ItemStack s){return !isOutput&&!creative;}@Override public ItemStack remove(int amount){return h.extractItem(getSlotIndex(),amount,false);}});}}
    private void addPlayerSlots(Inventory inv){int rs=busRows(),fR=fluidRows(),g=(rs+fR)<=3?14:20;int iy=18+fR*18+rs*18+g,hy=iy+3*18+4;
        for(int r=0;r<3;r++)for(int c=0;c<9;c++)this.addSlot(new Slot(inv,c+r*9+9,8+c*18,iy+r*18));for(int c=0;c<9;c++)this.addSlot(new Slot(inv,c,8+c*18,hy));}
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
            // Bus → player
            if (!this.moveItemStackTo(src, firstPlayerSlot, lastPlayerSlot + 1, true))
                return ItemStack.EMPTY;
        } else {
            // Player → bus (only if input bus)
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
