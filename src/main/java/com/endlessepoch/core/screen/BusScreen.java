package com.endlessepoch.core.screen;

import com.endlessepoch.core.menu.BusMenu;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft; import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component; import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.client.extensions.common.IClientFluidTypeExtensions;

public class BusScreen extends AbstractContainerScreen<BusMenu> {
    private static final ResourceLocation BG = ResourceLocation.parse("eecore:textures/gui/container/bus.png");
    private static final int BG_W = 176, MAX_H = 512;
    // Count popup for creative template slots / 创造模板槽数量弹框
    private net.minecraft.client.gui.components.EditBox countBox;
    private net.minecraft.client.gui.components.Button countOk;
    private int countSlot = -1;
    public BusScreen(BusMenu m, Inventory inv, Component t) { super(m,inv,t); this.imageWidth=m.imageW();this.imageHeight=m.imageH(); }

    @Override
    protected void containerTick() {
        super.containerTick();
        // Popup open on a now-empty slot → auto-dismiss / 弹框开着但槽已空 → 自动关
        if (countSlot >= 0 && !menu.slots.get(menu.getFluidSlots() + countSlot).hasItem())
            closeCountPopup();
    }

    @Override protected void init() {int rs=menu.busRows(),fR=menu.fluidRows(),g=(rs+fR)<=3?14:20;
        this.imageHeight=menu.imageH();super.init();this.inventoryLabelY=18+(fR+rs)*18+g-11;
        this.titleLabelX=(imageWidth-font.width(title))/2; // center title / 标题居中
        this.inventoryLabelX=(imageWidth-font.width(playerInventoryTitle))/2; // center "物品栏" / "物品栏"居中
        if(menu.isCreative()&&!menu.isOutputBus()){
            countBox=new net.minecraft.client.gui.components.EditBox(font,leftPos+92,topPos+3,44,12,Component.empty());
            countBox.setMaxLength(7);
            countBox.setFilter(s->s.isEmpty()||s.chars().allMatch(Character::isDigit));
            countBox.visible=false;
            addRenderableWidget(countBox);
            countOk=net.minecraft.client.gui.components.Button.builder(
                    Component.translatable("eecore.gui.parallel.confirm"),b->confirmCount())
                    .bounds(leftPos+138,topPos+2,30,14).build();
            countOk.visible=false;
            addRenderableWidget(countOk);
        }
    }

    /** Left-click empty-handed on a templated creative slot opens the count popup. / 空手左键有模板槽弹数量框。 */
    @Override
    protected void slotClicked(net.minecraft.world.inventory.Slot slot, int slotId, int mouseButton,
                               net.minecraft.world.inventory.ClickType type) {
        if (menu.isCreative() && !menu.isOutputBus() && slot != null && mouseButton == 0
                && menu.getCarried().isEmpty()
                && slotId >= menu.getFluidSlots() && slotId < menu.getFluidSlots() + menu.getSlotCount()
                && slot.hasItem()) {
            countSlot = slotId - menu.getFluidSlots();
            countBox.setValue(String.valueOf(menu.templateCount(countSlot)));
            countBox.visible = true;
            countOk.visible = true;
            setFocused(countBox);
            countBox.setFocused(true);
            return; // no packet — popup handles it / 不发点击包，弹框接管
        }
        super.slotClicked(slot, slotId, mouseButton, type);
    }

    private void confirmCount() {
        if (countSlot < 0) return;
        int v;
        try { v = Integer.parseInt(countBox.getValue().trim()); }
        catch (NumberFormatException e) { v = 64; }
        v = Math.max(1, Math.min(v, com.endlessepoch.core.nova.block.part.CreativeBusBlockEntity.MAX_TEMPLATE_COUNT));
        net.neoforged.neoforge.network.PacketDistributor.sendToServer(
                new com.endlessepoch.core.network.SetGhostCountPacket(menu.getPos(), countSlot, v));
        closeCountPopup();
    }

    /** Full-count string for tooltips (no abbreviation). / tooltip 用的完整数量（不缩略）。 */
    private static String fmtCount(long n) {
        if (n >= 1_000_000_000L) return String.format("%.1fB", n / 1_000_000_000.0);
        if (n >= 1_000_000L)     return String.format("%.1fM", n / 1_000_000.0);
        if (n >= 1_000L)         return String.format("%.1fK", n / 1_000.0);
        return String.valueOf(n);
    }

    private void closeCountPopup() {
        countSlot = -1;
        if (countBox != null) { countBox.visible = false; countBox.setFocused(false); }
        if (countOk != null) countOk.visible = false;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (countBox != null && countBox.visible && countBox.isFocused()) {
            if (keyCode == 256) { closeCountPopup(); return true; }               // ESC closes popup / ESC 关弹框
            if (keyCode == 257 || keyCode == 335) { confirmCount(); return true; } // Enter confirms / 回车确认
            if (countBox.keyPressed(keyCode, scanCode, modifiers) || countBox.canConsumeInput()) return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override public void render(GuiGraphics g,int mx,int my,float p){
        super.render(g,mx,my,p);
        // Creative template slot tooltips show the configured count / 创造模板槽悬浮提示显示配置数量
        if(menu.isCreative()&&!menu.isOutputBus()){
            int cols=menu.busCols(),x=leftPos+menu.busX();
            int y=topPos+18+menu.fluidRows()*18;
            for(int i=0;i<menu.getSlotCount();i++){
                int r=i/cols,c=i%cols,sx=x+c*18,sy=y+r*18;
                if(mx>=sx&&mx<sx+16&&my>=sy&&my<sy+16
                        && menu.slots.get(menu.getFluidSlots()+i).hasItem()){
                    var stack=menu.slots.get(menu.getFluidSlots()+i).getItem();
                    g.renderTooltip(font,net.minecraft.network.chat.Component.literal(
                            stack.getHoverName().getString()+" x"+fmtCount(menu.templateCount(i))),mx,my);
                }
            }
        }
        if(menu.isOversized()){
            int cols=menu.busCols(),x=leftPos+menu.busX(),y=topPos+18+menu.fluidRows()*18;
            for(int i=0;i<menu.getSlotCount();i++){int r=i/cols,c=i%cols,sx=x+c*18,sy=y+r*18;
                if(mx>=sx&&mx<sx+16&&my>=sy&&my<sy+16&&menu.slots.get(menu.getFluidSlots()+i).hasItem()){
                    var s=menu.slots.get(menu.getFluidSlots()+i).getItem();
                    // Real count via data-slot sync — the slot stack itself is capped at 64
                    // 真实数量走数据槽同步——槽内物品堆本身被钳在 64
                    long real=Math.max(menu.storedAmount(i),s.getCount());
                    g.renderTooltip(font,Component.literal(s.getHoverName().getString()+" x"+fmtCount(real)),mx,my);
                }
            }
        }
        this.renderTooltip(g,mx,my);
        int x=(width-imageWidth)/2,y=(height-imageHeight)/2,fs=menu.getFluidSlots();
        for(int i=0;i<fs;i++){
            int sx=x+menu.fluidSlotX(i),sy=y+menu.fluidSlotY(i);
            if(mx>=sx&&mx<sx+16&&my>=sy&&my<sy+16){
                var fid=menu.getFluidId(i); var fn="";
                if(fid!=null){var f=BuiltInRegistries.FLUID.get(fid);if(f!=null)fn=f.getFluidType().getDescription().getString()+": ";}
                g.renderTooltip(font,Component.literal(fn+menu.getFluidAmt(i)+" / "+menu.getFluidCap(i)+" mB"),mx,my);
            }
        }
    }

    @Override protected void renderBg(GuiGraphics g,float p,int mx,int my){int x=(width-imageWidth)/2,y=(height-imageHeight)/2;
        g.blit(BG,x,y,0,0,imageWidth,imageHeight,imageWidth,imageHeight);
        int fs=menu.getFluidSlots();
        for(int i=0;i<fs;i++){int sx=x+menu.fluidSlotX(i),sy=y+menu.fluidSlotY(i);
            g.fill(sx,sy,sx+16,sy+16,0xFF_000000);g.fill(sx+1,sy+1,sx+15,sy+15,0xFF_111111);
            int amt=menu.getFluidAmt(i),cap=Math.max(1,menu.getFluidCap(i));ResourceLocation fid=menu.getFluidId(i);
            if(amt>0&&fid!=null){var fl=BuiltInRegistries.FLUID.get(fid);if(fl!=null){var st=new net.neoforged.neoforge.fluids.FluidStack(fl,amt);
                var tx=IClientFluidTypeExtensions.of(fl).getStillTexture(st);if(tx!=null){
                    var sp=Minecraft.getInstance().getTextureAtlas(TextureAtlas.LOCATION_BLOCKS).apply(tx);
                    int tint=IClientFluidTypeExtensions.of(fl).getTintColor();float cr=(tint>>16&255)/255f,cg=(tint>>8&255)/255f,cb=(tint&255)/255f;
                    float f=(float)amt/cap;int h=Math.max(1,(int)(14*f));RenderSystem.setShaderColor(cr,cg,cb,1f);g.blit(sx+1,sy+1+14-h,0,14,h,sp);RenderSystem.setShaderColor(1,1,1,1);
        }}}}
        int fR=menu.fluidRows(),slotY=y+18+fR*18,cols=menu.busCols(),slotX=menu.busX();
        for(int i=0;i<menu.getSlotCount();i++)ScreenUtil.drawSlot(g,x+slotX+(i%cols)*18,slotY+(i/cols)*18);
        // Gold border on locked slots (all 4 sides) / 锁定槽金边（四边）
        for(int i=0;i<menu.getSlotCount();i++)if(menu.lockState(i)){int sx=x+slotX+(i%cols)*18,sy=slotY+(i/cols)*18;
            g.fill(sx,sy,sx+16,sy+1,0xFF_FFD700);g.fill(sx,sy,sx+1,sy+16,0xFF_FFD700);
            g.fill(sx,sy+15,sx+16,sy+16,0xFF_FFD700);g.fill(sx+15,sy,sx+16,sy+16,0xFF_FFD700);}
        for(int i=0;i<fs;i++)if(menu.lockState(menu.getSlotCount()+i)){int sx=x+menu.fluidSlotX(i),sy=y+menu.fluidSlotY(i);
            g.fill(sx,sy,sx+16,sy+1,0xFF_FFD700);g.fill(sx,sy,sx+1,sy+16,0xFF_FFD700);
            g.fill(sx,sy+15,sx+16,sy+16,0xFF_FFD700);g.fill(sx+15,sy,sx+16,sy+16,0xFF_FFD700);}
        int rs=menu.busRows(),gap=(rs+fR)<=3?14:20,invY=slotY+rs*18+gap;
        // Center player inventory exactly like addPlayerSlots does / 与 addPlayerSlots 完全一致的居中
        int invX=x+(imageWidth-162)/2;
        for(int r=0;r<3;r++)for(int c=0;c<9;c++)ScreenUtil.drawSlot(g,invX+c*18,invY+r*18);
        for(int c=0;c<9;c++)ScreenUtil.drawSlot(g,invX+c*18,invY+3*18+4);
    }
}
