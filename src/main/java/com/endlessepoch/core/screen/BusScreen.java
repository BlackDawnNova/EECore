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
    public BusScreen(BusMenu m, Inventory inv, Component t) { super(m,inv,t); this.imageWidth=BG_W; }

    @Override protected void init() {int rs=(menu.getSlotCount()+8)/9,fR=(menu.getFluidSlots()+8)/9,g=(rs+fR)<=3?14:20;
        this.imageHeight=Math.min(18+(fR+rs)*18+g+3*18+4+18+7,MAX_H);super.init();this.inventoryLabelY=18+(fR+rs)*18+g-11;}

    @Override public void render(GuiGraphics g,int mx,int my,float p){super.render(g,mx,my,p);this.renderTooltip(g,mx,my);
        int x=(width-imageWidth)/2,y=(height-imageHeight)/2,fs=menu.getFluidSlots();
        for(int i=0;i<fs;i++){int sx=x+8+(i%9)*18,sy=y+18+(i/9)*18;if(mx>=sx&&mx<sx+16&&my>=sy&&my<sy+16)g.renderTooltip(font,Component.literal(menu.getFluidAmt(i)+" / "+menu.getFluidCap(i)+" mB"),mx,my);}}

    @Override protected void renderBg(GuiGraphics g,float p,int mx,int my){int x=(width-imageWidth)/2,y=(height-imageHeight)/2;
        g.blit(BG,x,y,0,0,imageWidth,imageHeight,imageWidth,imageHeight);int fs=menu.getFluidSlots();
        for(int i=0;i<fs;i++){int sx=x+8+(i%9)*18,sy=y+18+(i/9)*18;
            g.fill(sx,sy,sx+16,sy+16,0xFF_000000);g.fill(sx+1,sy+1,sx+15,sy+15,0xFF_111111);
            int amt=menu.getFluidAmt(i),cap=Math.max(1,menu.getFluidCap(i));ResourceLocation fid=menu.getFluidId(i);
            if(amt>0&&fid!=null){var fl=BuiltInRegistries.FLUID.get(fid);if(fl!=null){var st=new net.neoforged.neoforge.fluids.FluidStack(fl,amt);
                var tx=IClientFluidTypeExtensions.of(fl).getStillTexture(st);if(tx!=null){
                    var sp=Minecraft.getInstance().getTextureAtlas(TextureAtlas.LOCATION_BLOCKS).apply(tx);
                    int tint=IClientFluidTypeExtensions.of(fl).getTintColor();float cr=(tint>>16&255)/255f,cg=(tint>>8&255)/255f,cb=(tint&255)/255f;
                    float f=(float)amt/cap;int h=Math.max(1,(int)(14*f));RenderSystem.setShaderColor(cr,cg,cb,1f);g.blit(sx+1,sy+1+14-h,0,14,h,sp);RenderSystem.setShaderColor(1,1,1,1);
        }}}}
        int fR=(fs+8)/9,slotY=y+18+fR*18,cols=Math.min(menu.getSlotCount(),9),slotX=8+(9-cols)*9;
        for(int i=0;i<menu.getSlotCount();i++)ScreenUtil.drawSlot(g,x+slotX+(i%9)*18,slotY+(i/9)*18);
        int rs=(menu.getSlotCount()+8)/9,gap=(rs+fR)<=3?14:20,invY=slotY+rs*18+gap;
        for(int r=0;r<3;r++)for(int c=0;c<9;c++)ScreenUtil.drawSlot(g,x+8+c*18,invY+r*18);
        for(int c=0;c<9;c++)ScreenUtil.drawSlot(g,x+8+c*18,invY+3*18+4);
    }
}
