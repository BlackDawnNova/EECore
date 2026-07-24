package com.endlessepoch.core.nova.client;

import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;
import org.joml.Vector4f;

public final class CelestialRenderer {
    private static final ResourceLocation SHADER = ResourceLocation.fromNamespaceAndPath("eecore", "celestial_test");

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent e) {
        var stage = BlackholeRenderer.showcaseMode
            ? RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS
            : RenderLevelStageEvent.Stage.AFTER_WEATHER;
        if (e.getStage() != stage) return;
        if (com.endlessepoch.core.Config.p4DisableEffects) return;
        var mc = Minecraft.getInstance(); if (mc.level==null) return;
        var cam = e.getCamera().getPosition();

        float cx=0.5f,cy=60f,cz=0.5f, tilt=(float)(Math.PI/4.0);
        float ringR = com.endlessepoch.core.nova.client.BlackholeRenderer.currentScale / 9.0f * 35f;
        double t=System.currentTimeMillis()*0.001;
        float orbit=(float)(t%(2*Math.PI)), sunA=orbit, moonA=orbit+(float)Math.PI;
        float sx=cx+ringR*(float)Math.cos(sunA), sy=cy-ringR*(float)Math.sin(sunA)*(float)Math.sin(tilt), sz=cz+ringR*(float)Math.sin(sunA)*(float)Math.cos(tilt);
        float mx=cx+ringR*(float)Math.cos(moonA), my=cy-ringR*(float)Math.sin(moonA)*(float)Math.sin(tilt), mz=cz+ringR*(float)Math.sin(moonA)*(float)Math.cos(tilt);

        var camEnt = e.getCamera().getEntity(); var look = camEnt.getViewVector(1f); var up = camEnt.getUpVector(1f);
        Matrix4f mv = new Matrix4f().lookAt(0,0,0,(float)look.x,(float)look.y,(float)look.z,(float)up.x,(float)up.y,(float)up.z);
        Matrix4f proj = new Matrix4f(e.getProjectionMatrix());
        float pcx=(float)cam.x, pcy=(float)cam.y, pcz=(float)cam.z;

        float bhDist = (float)Math.sqrt((cx-pcx)*(cx-pcx)+(cy-pcy)*(cy-pcy)+(cz-pcz)*(cz-pcz));

        var shader = foundry.veil.api.client.render.VeilRenderSystem.setShader(SHADER);
        if (shader == null) return;
        var ua = (foundry.veil.api.client.render.shader.program.UniformAccess) shader;

        float[] sU = project(mv,proj,sx,sy,sz,pcx,pcy,pcz);
        float[] mU = project(mv,proj,mx,my,mz,pcx,pcy,pcz);
        float[] bU = project(mv,proj,cx,cy,cz,pcx,pcy,pcz);
        setF(ua,"SunX",sU!=null?sU[0]:-1f); setF(ua,"SunY",sU!=null?sU[1]:-1f);
        setF(ua,"MoonX",mU!=null?mU[0]:-1f); setF(ua,"MoonY",mU!=null?mU[1]:-1f);
        setF(ua,"BhX",bU!=null?bU[0]:-1f); setF(ua,"BhY",bU!=null?bU[1]:-1f);
        setF(ua,"BhDist", bhDist);
        setF(ua,"Time",(float)(t%1000));
        setF(ua,"AspectRatio",(float)mc.getWindow().getWidth()/mc.getWindow().getHeight());

        int seg=30; setF(ua,"RingN",seg);
        for (int i=0; i<seg; i++) {
            float a=i*2f*(float)Math.PI/seg;
            float wx=cx+ringR*(float)Math.cos(a), wy=cy-ringR*(float)Math.sin(a)*(float)Math.sin(tilt), wz=cz+ringR*(float)Math.sin(a)*(float)Math.cos(tilt);
            float[] uv=project(mv,proj,wx,wy,wz,pcx,pcy,pcz);
            setF(ua,"RX["+i+"]", uv!=null?uv[0]:-99f);
            setF(ua,"RY["+i+"]", uv!=null?uv[1]:-99f);
        }

        if (BlackholeRenderer.showcaseMode) {
            com.mojang.blaze3d.systems.RenderSystem.disableBlend();
        } else {
            com.mojang.blaze3d.systems.RenderSystem.enableBlend();
            com.mojang.blaze3d.systems.RenderSystem.defaultBlendFunc();
        }
        shader.bind();
        foundry.veil.api.client.render.VeilRenderSystem.drawScreenQuad();
        foundry.veil.api.client.render.shader.program.ShaderProgram.unbind();
    }

    private static float[] project(Matrix4f mv, Matrix4f p, float wx,float wy,float wz,float cx,float cy,float cz) {
        Vector4f v=new Matrix4f(mv).transform(new Vector4f(wx-cx,wy-cy,wz-cz,1f));
        Vector4f c=new Matrix4f(p).transform(v);
        if(c.w==0||c.z<-c.w||c.z>c.w)return null;
        float ndcX=c.x/c.w, ndcY=c.y/c.w;
        if(Math.abs(ndcX)>1.5f||Math.abs(ndcY)>1.5f)return null;
        return new float[]{ndcX*0.5f+0.5f, ndcY*0.5f+0.5f};
    }

    private static void setF(foundry.veil.api.client.render.shader.program.UniformAccess s, String n, float v) { var u=s.getUniform(n); if(u!=null)u.setFloat(v); }
}
