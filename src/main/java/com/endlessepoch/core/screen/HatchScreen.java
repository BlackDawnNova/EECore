package com.endlessepoch.core.screen;

import com.endlessepoch.core.api.energy.OmegaValue;
import com.endlessepoch.core.menu.HatchMenu;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft; import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component; import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.client.extensions.common.IClientFluidTypeExtensions;

public class HatchScreen extends AbstractContainerScreen<HatchMenu> {
    private static final ResourceLocation BG = ResourceLocation.parse("eecore:textures/gui/container/bus.png");
    public HatchScreen(HatchMenu m, Inventory inv, Component t) { super(m,inv,t); this.imageWidth=176; this.imageHeight=166; }
    @Override protected void init() { super.init(); this.inventoryLabelY=imageHeight-94; }

    @Override public void render(GuiGraphics g, int mx, int my, float p) {
        super.render(g,mx,my,p); this.renderTooltip(g,mx,my);
        int x=left(),y=top();
        boolean hasE=hasEnergy(); int ex=hasE?x+80:0;
        int slotY=(inventoryLabelY+20-16)/2;
        if(hasE&&over(mx,my,ex,y+slotY,16,16))
            g.renderTooltip(font,Component.literal(
                menu.getEnergyStored().toDisplayString()+" / "+menu.getEnergyCapacity().toDisplayString()),mx,my);
        int tc=menu.tankCount,sx=x+80-(tc-1)*10;
        for(int i=0;i<tc;i++)if(over(mx,my,sx+i*20,y+slotY,16,16)){
            var fid=menu.fId[i]; var fname="";
            if(fid!=null){var f=BuiltInRegistries.FLUID.get(fid);if(f!=null)fname=f.getFluidType().getDescription().getString()+": ";}
            g.renderTooltip(font,Component.literal(fname+menu.fAmt[i]+" / "+menu.fCap[i]+" mB"),mx,my);
        }
    }

    @Override protected void renderBg(GuiGraphics g, float p, int mx, int my) {
        int x=left(),y=top(); g.blit(BG,x,y,0,0,imageWidth,imageHeight,imageWidth,imageHeight);
        boolean hasE=hasEnergy(); int ex=hasE?x+80:0;
        int slotY=(inventoryLabelY+20-16)/2;
        if(hasE) drawEnergy(g,ex,y+slotY);
        int tc=menu.tankCount,sx=x+80-(tc-1)*10;
        for(int i=0;i<tc;i++) drawFluid(g,sx+i*20,y+slotY,menu.fId[i],menu.fAmt[i],menu.fCap[i]);
        for(int r=0;r<3;r++)for(int c=0;c<9;c++) ScreenUtil.drawSlot(g,x+8+c*18,y+84+r*18);
        for(int c=0;c<9;c++) ScreenUtil.drawSlot(g,x+8+c*18,y+142);
    }

    private boolean hasEnergy() {
        return !menu.getEnergyCapacity().isZero();
    }

    private void drawEnergy(GuiGraphics g, int sx, int sy) {
        g.fill(sx,sy,sx+16,sy+16,0xFF_000000);g.fill(sx+1,sy+1,sx+15,sy+15,0xFF_111111);
        var cap = menu.getEnergyCapacity().toBigInteger();
        var stored = menu.getEnergyStored().toBigInteger();
        if (stored.signum() > 0 && cap.signum() > 0) {
            double ratio = stored.doubleValue() / cap.doubleValue();
            if (ratio > 1) ratio = 1;
            int h = (int)(14 * ratio);
            if (h > 0) g.fill(sx+1, sy+1+14-h, sx+15, sy+1+14, 0x88_FFCC00);
        }
        g.drawCenteredString(font,"⚡",sx+8,sy+4,0x66_FFCC00);
    }

    private void drawFluid(GuiGraphics g, int sx, int sy, ResourceLocation fid, int amt, int cap) {
        g.fill(sx,sy,sx+16,sy+16,0xFF_000000);g.fill(sx+1,sy+1,sx+15,sy+15,0xFF_111111);
        if(amt>0&&fid!=null){var fl=BuiltInRegistries.FLUID.get(fid);if(fl!=null){var st=new net.neoforged.neoforge.fluids.FluidStack(fl,amt);
            var tx=IClientFluidTypeExtensions.of(fl).getStillTexture(st);
            if(tx!=null){float f=(float)amt/Math.max(1,cap);int h=Math.max(1,(int)(14*f));
                var sp=Minecraft.getInstance().getTextureAtlas(TextureAtlas.LOCATION_BLOCKS).apply(tx);
                int tint=IClientFluidTypeExtensions.of(fl).getTintColor();
                float cr=(tint>>16&255)/255f,cg=(tint>>8&255)/255f,cb=(tint&255)/255f;
                RenderSystem.setShaderColor(cr,cg,cb,1f);g.blit(sx+1,sy+1+14-h,0,14,h,sp);RenderSystem.setShaderColor(1,1,1,1);
            }}}
        g.drawCenteredString(font,"💧",sx+8,sy+4,0x66_4488FF);
    }
    private boolean over(int mx,int my,int bx,int by,int bw,int bh){return mx>=bx&&mx<bx+bw&&my>=by&&my<by+bh;}
    private int left(){return (width-imageWidth)/2;} private int top(){return (height-imageHeight)/2;}
}
