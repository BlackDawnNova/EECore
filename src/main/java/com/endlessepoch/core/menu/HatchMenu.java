package com.endlessepoch.core.menu;

import com.endlessepoch.core.nova.block.part.PartBlockEntity;
import com.endlessepoch.core.registry.Menus;
import net.minecraft.core.BlockPos; import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.FriendlyByteBuf; import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory; import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.*; import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.FluidUtil;

public class HatchMenu extends AbstractContainerMenu {
    private final BlockPos pos; final PartBlockEntity hatch;
    private final ContainerData data;
    final int tankCount; final ResourceLocation[] fId; final int[] fAmt, fCap;

    public HatchMenu(int id, Inventory inv, PartBlockEntity hatch) {
        super(Menus.HATCH.get(), id); this.hatch=hatch; this.pos=hatch.getBlockPos();
        var tanks=hatch.getFluidTanks(); this.tankCount=tanks.size();
        this.data=new SimpleContainerData(2); addDataSlots(data);
        this.fId=new ResourceLocation[tankCount]; this.fAmt=new int[tankCount]; this.fCap=new int[tankCount];
        syncFromBE(); addSlots(inv);
    }
    public HatchMenu(int id, Inventory inv, FriendlyByteBuf buf) {
        super(Menus.HATCH.get(), id); this.pos=buf.readBlockPos(); this.hatch=null;
        this.data=new SimpleContainerData(2); addDataSlots(data); this.tankCount=buf.readVarInt();
        this.fId=new ResourceLocation[tankCount]; this.fAmt=new int[tankCount]; this.fCap=new int[tankCount];
        for(int i=0;i<tankCount;i++){fId[i]=buf.readBoolean()?buf.readResourceLocation():null;fAmt[i]=buf.readVarInt();fCap[i]=buf.readVarInt();}
        addSlots(inv);
    }
    @Override public void broadcastChanges(){super.broadcastChanges();if(hatch!=null)syncFromBE();}
    private void syncFromBE(){
        if(hatch==null)return; var es=hatch.getEnergyStorage();
        data.set(0,es!=null?es.getEnergyStored().toBigInteger().intValue():0);
        data.set(1,es!=null?es.getCapacity().toBigInteger().intValue():0);
        var ts=hatch.getFluidTanks();
        for(int i=0;i<tankCount&&i<ts.size();i++){var f=ts.get(i);fAmt[i]=f.getFluidAmount();fCap[i]=f.getCapacity();fId[i]=f.getFluid().isEmpty()?null:BuiltInRegistries.FLUID.getKey(f.getFluid().getFluid());}
    }
    public long getEnergyStored(){return data.get(0)&0xFFFFFFFFL;} public long getEnergyCapacity(){return data.get(1)&0xFFFFFFFFL;}
    public int getTankCount(){return tankCount;} public ResourceLocation getFluidId(int i){return i<tankCount?fId[i]:null;}
    public int getFluidAmt(int i){return i<tankCount?fAmt[i]:0;} public int getFluidCap(int i){return i<tankCount?fCap[i]:0;}
    public void setFluidData(int i,ResourceLocation id,int amt,int cap){if(i<tankCount){fId[i]=id;fAmt[i]=amt;fCap[i]=cap;}}

    /** Handle fluid container click on visual slot. / 处理流体容器点击。 */
    public ItemStack handleFluidClick(int tankIdx, ItemStack held){
        if(hatch==null||tankIdx>=hatch.getFluidTanks().size())return held;
        var ft=hatch.getFluidTanks().get(tankIdx);
        var r=net.neoforged.neoforge.fluids.FluidUtil.tryEmptyContainer(held,ft,Integer.MAX_VALUE,null,true);
        return r.isSuccess()?r.getResult():held;
    }

    private void addSlots(Inventory inv){
        boolean hasE=hatch!=null&&hatch.getEnergyStorage()!=null; int sx=(hasE?64:80);
        for(int i=0;i<Math.max(1,tankCount);i++){
            final int ti=i;
            this.addSlot(new Slot(new net.minecraft.world.SimpleContainer(1),0,sx+i*20,38){
                @Override public boolean mayPlace(ItemStack s){return false;} // ghost slot / 虚槽
                @Override public int getMaxStackSize(){return 0;}
            });
        }
        for(int r=0;r<3;r++)for(int c=0;c<9;c++)addSlot(new Slot(inv,c+r*9+9,8+c*18,84+r*18));
        for(int c=0;c<9;c++)addSlot(new Slot(inv,c,8+c*18,142));
    }
    @Override public void clicked(int slotId, int button, ClickType type, Player player){
        if(slotId>=0&&slotId<tankCount&&hatch!=null&&!player.containerMenu.getCarried().isEmpty()){
            var held=player.containerMenu.getCarried();
            if(FluidUtil.getFluidHandler(held).isPresent()){
                var ft=hatch.getFluidTanks().get(slotId);
                // Try empty container into tank / 先试倒入
                var r=FluidUtil.tryEmptyContainer(held,ft,Integer.MAX_VALUE,player,true);
                if(r.isSuccess()){
                    player.containerMenu.setCarried(r.getResult());
                    syncFromBE();sendSync(slotId,player);return;
                }
                // Try fill container from tank / 再试舀出
                r=FluidUtil.tryFillContainer(held,ft,Integer.MAX_VALUE,player,true);
                if(r.isSuccess()){
                    player.containerMenu.setCarried(r.getResult());
                    syncFromBE();sendSync(slotId,player);return;
                }
            }
        }
        super.clicked(slotId,button,type,player);
    }
    private void sendSync(int slotId,Player player){
        net.neoforged.neoforge.network.PacketDistributor.sendToPlayer((net.minecraft.server.level.ServerPlayer)player,
            new com.endlessepoch.core.network.FluidSyncPacket(pos,slotId,fId[slotId],fAmt[slotId],fCap[slotId]));
    }
    @Override public ItemStack quickMoveStack(Player p,int i){return ItemStack.EMPTY;}
    @Override public boolean stillValid(Player p){return p.distanceToSqr(pos.getX()+.5,pos.getY()+.5,pos.getZ()+.5)<=64;}
}
