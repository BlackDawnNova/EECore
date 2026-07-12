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
    private final BlockPos pos; private final int fluidSlots; private final ContainerData data;
    final ResourceLocation[] fId; final int[] fAmt, fCap;

    public BusMenu(int id, Inventory inv, InputBusBlockEntity bus) {
        super(Menus.BUS.get(), id); this.bus=bus; this.slotCount=bus.getSlotCount(); this.isOutput=bus.isOutput(); this.pos=bus.getBlockPos();
        this.data=new SimpleContainerData(2); addDataSlots(data);
        var ts=((PartBlockEntity)bus).getFluidTanks();
        int fs=0; if(!ts.isEmpty()&&bus.getBlockState().getBlock() instanceof PartBlock pb)fs=pb.fluidSlots;
        this.fluidSlots=fs;
        this.fId=new ResourceLocation[Math.max(1,fs)];this.fAmt=new int[Math.max(1,fs)];this.fCap=new int[Math.max(1,fs)];
        syncFromBE(); addBusSlots(bus.getInventory()); addPlayerSlots(inv);
    }
    public BusMenu(int id, Inventory inv, FriendlyByteBuf buf) {
        super(Menus.BUS.get(), id); this.pos=buf.readBlockPos(); this.slotCount=buf.readVarInt();
        this.isOutput=buf.readBoolean(); this.fluidSlots=buf.readVarInt(); this.bus=null;
        this.data=new SimpleContainerData(2); addDataSlots(data);
        this.fId=new ResourceLocation[Math.max(1,fluidSlots)];this.fAmt=new int[Math.max(1,fluidSlots)];this.fCap=new int[Math.max(1,fluidSlots)];
        for(int i=0;i<fluidSlots;i++){fId[i]=buf.readBoolean()?buf.readResourceLocation():null;fAmt[i]=buf.readVarInt();fCap[i]=buf.readVarInt();}
        addBusSlots(new net.neoforged.neoforge.items.wrapper.InvWrapper(new net.minecraft.world.SimpleContainer(slotCount)));
        addPlayerSlots(inv);
    }
    @Override public void broadcastChanges(){super.broadcastChanges();if(bus!=null)syncFromBE();}
    private void syncFromBE(){if(bus==null)return;var ts=((PartBlockEntity)bus).getFluidTanks();
        for(int i=0;i<fluidSlots&&i<ts.size();i++){var f=ts.get(i);fAmt[i]=f.getFluidAmount();fCap[i]=f.getCapacity();fId[i]=f.getFluid().isEmpty()?null:BuiltInRegistries.FLUID.getKey(f.getFluid().getFluid());}}
    public int getFluidAmt(int i){return i<fluidSlots?fAmt[i]:0;} public int getFluidCap(int i){return i<fluidSlots?fCap[i]:0;}
    public void setFluidData(int i,ResourceLocation id,int amt,int cap){if(i<fluidSlots){fId[i]=id;fAmt[i]=amt;fCap[i]=cap;}}
    public ResourceLocation getFluidId(int i){return i<fluidSlots?fId[i]:null;}
    public int getFluidSlots(){return fluidSlots;} public InputBusBlockEntity getBus(){return bus;} public int getSlotCount(){return slotCount;}
    private void addBusSlots(net.neoforged.neoforge.items.IItemHandler h){
        // Ghost fluid slots for click interaction / 虚拟流体槽
        for(int i=0;i<fluidSlots;i++){final int ti=i;
            this.addSlot(new Slot(new net.minecraft.world.SimpleContainer(1),0,8+(i%9)*18,18+(i/9)*18){
                @Override public boolean mayPlace(ItemStack s){return false;}
                @Override public int getMaxStackSize(){return 0;}
            });
        }
        int c=Math.min(slotCount,9);int x=8+(9-c)*9;int fR=(fluidSlots+8)/9;
        for(int i=0;i<slotCount;i++){int r=i/9,cl=i%9;this.addSlot(new SlotItemHandler(h,i,x+cl*18,18+fR*18+r*18){@Override public boolean mayPlace(ItemStack s){return !isOutput;}});}}
    private void addPlayerSlots(Inventory inv){int rs=(slotCount+8)/9,fR=(fluidSlots+8)/9,g=(rs+fR)<=3?14:20;int iy=18+fR*18+rs*18+g,hy=iy+3*18+4;
        for(int r=0;r<3;r++)for(int c=0;c<9;c++)this.addSlot(new Slot(inv,c+r*9+9,8+c*18,iy+r*18));for(int c=0;c<9;c++)this.addSlot(new Slot(inv,c,8+c*18,hy));}
    @Override public void clicked(int slotId,int button,ClickType type,Player player){
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
    @Override public ItemStack quickMoveStack(Player p,int i){return ItemStack.EMPTY;}
    @Override public boolean stillValid(Player p){return p.distanceToSqr(pos.getX()+.5,pos.getY()+.5,pos.getZ()+.5)<=64;}
}
