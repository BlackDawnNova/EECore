package com.endlessepoch.core.nova.client;

import com.endlessepoch.core.api.multiblock.IMachineEffect;
import com.endlessepoch.core.api.multiblock.MachineRegistry;
import com.endlessepoch.core.nova.block.MachineControllerBlockEntity;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import org.joml.Matrix4f;

import java.util.Random;

/**
 * Celestial effect: 45-degree halo + sun-moon orbit + black hole + galaxy spiral arms.
 * 日月星辰特效：45°光环 + 日月公转 + 黑洞 + 银河旋臂。
 */
public class CelestialEffect implements IMachineEffect {

    private static final float SX = 2f, SY = 49f, SZ = 0f;
    private static final float RING_R = 35f;
    private static final float TILT  = (float) Math.PI / 4;
    private static final float ORBIT = 0.001f;
    private static final long  CYCLE_MS = (long)(2 * Math.PI / ORBIT);
    private static final long  STAR_CYCLE = 3000L;

    private static final int STARS = 100;
    private static final float[][] SP = new float[STARS][3];
    private static final float[]   SPH = new float[STARS], SFR = new float[STARS], SSZ = new float[STARS], SCOL = new float[STARS];

    private static final int NUM_WIND = 40;
    private static final float[][] WIND_DIR = new float[NUM_WIND][3];
    private static final float[]   WIND_SPD = new float[NUM_WIND], WIND_PHASE = new float[NUM_WIND];

    static {
        var r = new Random(42);
        for (int i = 0; i < STARS; i++) {
            float a = (float)(r.nextDouble() * 2 * Math.PI);
            float rad = 6 + r.nextFloat() * 30;
            SP[i][0] = rad * (float)Math.cos(a); SP[i][1] = -25 + r.nextFloat() * 50; SP[i][2] = rad * (float)Math.sin(a);
            SPH[i] = (float)(r.nextDouble() * 2 * Math.PI); SFR[i] = 6f + r.nextFloat() * 18f;
            SSZ[i] = 0.03f + r.nextFloat() * 0.12f; SCOL[i] = r.nextFloat();
        }
        var r2 = new Random(77);
        for (int i = 0; i < NUM_WIND; i++) {
            float lat = (float)(r2.nextDouble() * Math.PI), lon = (float)(r2.nextDouble() * 2 * Math.PI);
            WIND_DIR[i][0] = (float)(Math.sin(lat) * Math.cos(lon));
            WIND_DIR[i][1] = (float)Math.cos(lat); WIND_DIR[i][2] = (float)(Math.sin(lat) * Math.sin(lon));
            WIND_SPD[i] = 0.3f + r2.nextFloat() * 0.8f; WIND_PHASE[i] = r2.nextFloat() * 2 * (float)Math.PI;
        }
    }

    @Override
    public void render(PoseStack ps, BlockEntity be, float partialTick) {
        Direction facing = be.getBlockState().hasProperty(BlockStateProperties.HORIZONTAL_FACING)
                ? be.getBlockState().getValue(BlockStateProperties.HORIZONTAL_FACING) : Direction.NORTH;

        float ox = SX, oy = SY, oz = SZ;
        if (be instanceof MachineControllerBlockEntity mcbe) {
            var def = MachineRegistry.get(mcbe.getMachineId());
            if (def.isPresent()) { ox = def.get().getOffX(); oy = def.get().getOffY(); oz = def.get().getOffZ(); }
        }

        long ms = System.currentTimeMillis();
        float orb = ((ms % CYCLE_MS) * ORBIT) % (float)(2 * Math.PI); if (orb < 0) orb += (float)(2 * Math.PI);
        float st  = (ms % STAR_CYCLE) / 1000f;

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableDepthTest();
        RenderSystem.disableCull();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);

        var tess = Tesselator.getInstance();
        var buf = tess.begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_COLOR);
        ps.pushPose();
        ps.translate(toWorld(facing, ox, oy, oz)[0], toWorld(facing, ox, oy, oz)[1], toWorld(facing, ox, oy, oz)[2]);
        ps.mulPose(new org.joml.Quaternionf().rotateY(switch (facing) {
            case SOUTH -> (float) Math.PI; case EAST -> (float)(-Math.PI / 2); case WEST -> (float)(Math.PI / 2); default -> 0f;
        }));
        var m = ps.last().pose();

        ring(buf, m, RING_R, 0.8f, 0.8f, 0.25f, 0.04f, 0.05f);
        ring(buf, m, RING_R, 0.3f, 1, 0.4f, 0.1f, 0.15f);
        ring(buf, m, RING_R, 0.07f, 1, 0.5f, 0.15f, 0.5f);

        sun(buf, m, orb, st);
        moon(buf, m, orb, st);
        stars(buf, m, st);
        bh(buf, m, orb, st, ms);

        ps.popPose();
        BufferUploader.drawWithShader(buf.buildOrThrow());
        RenderSystem.enableCull();
        RenderSystem.enableDepthTest();
        RenderSystem.disableBlend();
    }

    private static void ring(BufferBuilder b, Matrix4f m, float R, float hw, float cr, float cg, float cb, float ca) {
        int s = 120; float in = R - hw, out = R + hw;
        for (int i = 0; i < s; i++) {
            float t0 = (float)i / s * 2 * (float)Math.PI, t1 = (float)(i + 1) / s * 2 * (float)Math.PI;
            float[] p0 = tp(in*(float)Math.cos(t0), in*(float)Math.sin(t0)), p1 = tp(in*(float)Math.cos(t1), in*(float)Math.sin(t1));
            float[] p2 = tp(out*(float)Math.cos(t0), out*(float)Math.sin(t0)), p3 = tp(out*(float)Math.cos(t1), out*(float)Math.sin(t1));
            b.addVertex(m, p0[0], p0[1], p0[2]).setColor(cr, cg, cb, ca); b.addVertex(m, p1[0], p1[1], p1[2]).setColor(cr, cg, cb, ca);
            b.addVertex(m, p2[0], p2[1], p2[2]).setColor(cr, cg, cb, ca); b.addVertex(m, p1[0], p1[1], p1[2]).setColor(cr, cg, cb, ca);
            b.addVertex(m, p3[0], p3[1], p3[2]).setColor(cr, cg, cb, ca); b.addVertex(m, p2[0], p2[1], p2[2]).setColor(cr, cg, cb, ca);
        }
    }

    private static void sun(BufferBuilder b, Matrix4f m, float orb, float st) {
        float[] p = rp(orb);
        hs(b,m,p[0],p[1],p[2],5.5f,0.9f,0.15f,0,0.04f); hs(b,m,p[0],p[1],p[2],4.5f,1,0.3f,0,0.08f);
        hs(b,m,p[0],p[1],p[2],3.5f,1,0.55f,0.1f,0.2f); hs(b,m,p[0],p[1],p[2],2.2f,1,0.8f,0.3f,0.5f);
        hs(b,m,p[0],p[1],p[2],1.2f,1,1,0.85f,0.9f);
        starFlare(b,m,p[0],p[1],p[2],5.5f,1,0.8f,0.3f,0.18f);
        float pt = st * 0.4f;
        for (int pi = 0; pi < 3; pi++) {
            float pw = (float)((Math.sin(pt * 2.2f + pi * 2.5f) + 1) * 0.5);
            if (pw < 0.78f) continue;
            float base = pi * 2.1f, span = 0.4f, h = 1.5f + pw * 4f;
            int steps = 16;
            for (int s = 0; s < steps; s++) {
                float t = s/(float)steps, t1 = (s+1)/(float)steps, aa = base + (t-0.5f)*span*2, aa1 = base + (t1-0.5f)*span*2;
                float y = (float)Math.sin(t*Math.PI)*h, y1 = (float)Math.sin(t1*Math.PI)*h;
                float x0=p[0]+1.2f*(float)Math.cos(aa), z0=p[2]+1.2f*(float)Math.sin(aa);
                float x1=p[0]+1.2f*(float)Math.cos(aa1), z1=p[2]+1.2f*(float)Math.sin(aa1);
                float a = pw*0.8f*(float)Math.sin(t*Math.PI), g = 0.08f*pw;
                b.addVertex(m,x0-g,p[1]+y,z0).setColor(1,0.6f,0.15f,a); b.addVertex(m,x1-g,p[1]+y1,z1).setColor(1,0.6f,0.15f,a);
                b.addVertex(m,x0+g,p[1]+y,z0).setColor(1,0.6f,0.15f,a); b.addVertex(m,x1-g,p[1]+y1,z1).setColor(1,0.6f,0.15f,a);
                b.addVertex(m,x1+g,p[1]+y1,z1).setColor(1,0.6f,0.15f,a); b.addVertex(m,x0+g,p[1]+y,z0).setColor(1,0.6f,0.15f,a);
            }
        }
        for (int wi=0; wi<NUM_WIND; wi++) {
            float ph = (st*WIND_SPD[wi]+WIND_PHASE[wi])%3f;
            float d = 5.5f+ph*3f, wx=p[0]+WIND_DIR[wi][0]*d, wy=p[1]+WIND_DIR[wi][1]*d, wz=p[2]+WIND_DIR[wi][2]*d;
            float wa=(1-ph/3f)*0.5f, ws=0.08f;
            q(b,m,wx-ws,wy,wz,wx+ws,wy,wz,wx,wy-ws,wz,wx,wy+ws,wz,1,0.9f,0.6f,wa);
        }
    }

    private static void moon(BufferBuilder b, Matrix4f m, float orb, float st) {
        float[] p = rp(orb + (float)Math.PI);
        hs(b,m,p[0],p[1],p[2],1.8f,0.7f,0.75f,0.9f,0.75f); hs(b,m,p[0],p[1],p[2],1.3f,0.85f,0.88f,0.95f,0.85f);
        for (int ci=0; ci<12; ci++) {
            double ca=ci*Math.PI*2/12+orb*0.3, cr=0.3f+Math.sin(ci*2.7)*0.6f;
            float cx=p[0]+(float)(cr*Math.cos(ca)), cz=p[2]+(float)(cr*Math.sin(ca));
            hs(b,m,cx,p[1]+(float)((ci%3-1)*0.5),cz,0.25f,1,1,1,0.5f);
        }
        hs(b,m,p[0],p[1],p[2],2.8f,0.5f,0.6f,1,0.1f);
        float mqT = st*0.5f;
        for (int qi=0; qi<4; qi++) {
            float qp=(float)((Math.sin(mqT*(3f+qi*1.5f)+qi*2f)+1)*0.5); if (qp<0.82f) continue;
            float qa=qi*1.1f, qr=0.5f+qi*0.3f, cw=0.04f*qp, ca=qp*0.8f;
            float[][] pts = new float[4][3];
            for (int j=0; j<4; j++) {
                float t=j/3f, rc=qr*t, ang=qa+(t-0.5f)*0.8f;
                pts[j][0]=p[0]+rc*(float)Math.cos(ang); pts[j][1]=p[1]+(float)(j%2==0?0.3f:-0.3f); pts[j][2]=p[2]+rc*(float)Math.sin(ang);
            }
            for (int j=0;j<3;j++) q(b,m,pts[j][0]-cw,pts[j][1],pts[j][2],pts[j+1][0]-cw,pts[j+1][1],pts[j+1][2],pts[j][0]+cw,pts[j][1],pts[j][2],pts[j+1][0]+cw,pts[j+1][1],pts[j+1][2],0.6f,0.65f,0.75f,ca);
        }
    }

    private static void stars(BufferBuilder b, Matrix4f m, float st) {
        for (int i = 0; i < STARS; i++) {
            float tw = (float)((Math.sin(st * SFR[i] + SPH[i]) + 1) * 0.5);
            float al = 0.02f + tw * 0.98f, sr = SSZ[i] * (0.4f + tw * 0.6f);
            float sx = SP[i][0], sy = SP[i][1], sz = SP[i][2];
            float cr = 0.8f + SCOL[i] * 0.2f, cg = 0.85f + SCOL[i] * 0.1f, cb = 0.85f + (1 - SCOL[i]) * 0.15f;
            msph(b, m, sx, sy, sz, sr, cr, cg, cb, al);
            msph(b, m, sx, sy, sz, sr * 2f, cr, cg, cb, al * 0.15f);
        }
    }

    private static void bh(BufferBuilder b, Matrix4f m, float orb, float st, long ms) {
        float BEAM_MID = 0f, BEAM_TOP = 45f, BEAM_BOT = -44f;
        float uFlow = (st * 1.2f) % 1f, p = (float)((Math.sin(st*2.5f)+1)*0.5);
        beamSeg(b, m, BEAM_MID, BEAM_TOP, uFlow, p, 1, 1, 0.95f);
        beamParts(b, m, BEAM_MID, BEAM_TOP, st, uFlow, 1, 1, 0.9f);
        float lFlow = (st * 1.2f + 0.5f) % 1f;
        beamSeg(b, m, BEAM_BOT, BEAM_MID, lFlow, p, 0.4f, 0.1f, 0.6f);
        beamParts(b, m, BEAM_BOT, BEAM_MID, st, lFlow, 0.5f, 0.15f, 0.7f);
        hs(b,m,0,BEAM_MID,0,0.12f,0,0,0,1); hs(b,m,0,BEAM_MID,0,0.35f,0.25f,0.02f,0.05f,0.35f);
        hs(b,m,0,BEAM_MID,0,0.55f,0.15f,0.01f,0.03f,0.18f);

        float aOut = 7.5f, aIn = 0.55f, galRot = -(ms % 60000L) / 1000f * 0.105f;
        for (int arm = 0; arm < 4; arm++) {
            float armBase = arm * (float)(Math.PI / 2) + galRot;
            int starCnt = 25 + arm * 4;
            for (int s = 0; s < 100; s++) {
                float t = s/100f, t1 = (s+1f)/100f;
                float r0=aIn+(float)Math.pow(t,0.7f)*(aOut-aIn), r1=aIn+(float)Math.pow(t1,0.7f)*(aOut-aIn);
                float a0=armBase+t*(float)(Math.PI*1.5f), a1=armBase+t1*(float)(Math.PI*1.5f);
                float x0=r0*(float)Math.cos(a0), z0=r0*(float)Math.sin(a0), x1=r1*(float)Math.cos(a1), z1=r1*(float)Math.sin(a1);
                float h=1f-(float)Math.pow(t,1.2f), w=0.02f, al=h*0.4f;
                q(b,m,x0,BEAM_MID-w,z0,x1,BEAM_MID-w,z1,x0,BEAM_MID+w,z0,x1,BEAM_MID+w,z1,0.3f,0.4f,0.6f+h*0.4f,al);
            }
            for (int si=0; si<starCnt; si++) {
                float t0=si/(float)starCnt, phase=arm*0.25f, flow=(ms%30000L)/30000f;
                float tEst=(t0+phase-flow)%1f; if(tEst<0)tEst+=1f; float speed=2.0f-tEst;
                float t=(t0+phase-flow*speed)%1f; if(t<0)t+=1f;
                float r=aIn+(float)Math.pow(t,0.7f)*(aOut-aIn), ang=armBase+t*(float)(Math.PI*1.5f);
                float nx=-(float)Math.sin(ang), nz=(float)Math.cos(ang);
                float sx=r*(float)Math.cos(ang)+nx*(si%3-1)*0.04f, sz=r*(float)Math.sin(ang)+nz*(si%3-1)*0.04f;
                float heat=1f-(float)Math.pow(t,1.0f), fade=(float)(Math.sin(t*Math.PI)*0.85+0.15);
                float sr=0.02f+heat*0.03f+(si%4)*0.005f, sa=(0.3f+heat*0.5f)*fade;
                int ct=(si+arm*11)%5; float cr,cg,cb;
                if(ct==0){cr=0.35f;cg=0.55f;cb=1;} else if(ct==1){cr=0.5f;cg=0.7f;cb=1;}
                else if(ct==2){cr=0.7f;cg=0.8f;cb=0.95f;} else if(ct==3){cr=0.85f;cg=0.85f;cb=0.75f;} else{cr=1;cg=0.8f;cb=0.5f;}
                if(t<0.03f&&sr>0.01f){
                    float tear=1f-t/0.03f; int frags=1+(int)(tear*7); float px=-sx*tear*0.6f, pz=-sz*tear*0.6f;
                    for(int f=0;f<frags;f++){
                        float fa=(si*2.7f+f*1.3f)%(float)(Math.PI*2), fd=tear*0.8f*(0.3f+f*0.1f);
                        float fx=sx+px+fd*(float)Math.cos(fa), fz=sz+pz+fd*(float)Math.sin(fa), fy=BEAM_MID+(f%3-1)*tear*0.15f;
                        msph(b,m,fx,fy,fz,sr*(1f-tear*0.8f),cr,cg,cb,Math.max(sa*(1f-tear),0.02f));
                    }
                }else{msph(b,m,sx,BEAM_MID,sz,sr,cr,cg,cb,sa); msph(b,m,sx,BEAM_MID,sz,sr*1.8f,cr,cg,cb,sa*0.2f); if(si%6==0)msph(b,m,sx,BEAM_MID,sz,sr*1.4f,cr,cg,cb,sa*0.4f);}
            }
            for(int d=0;d<20+arm*5;d++){float t0d=0.5f+arm*0.06f,td=t0d+(float)Math.pow(d/(float)(20+arm*5),0.6f)*(1f-t0d);
                float rda=aIn+(float)Math.pow(td,0.7f)*(aOut-aIn), ada=armBase+td*(float)(Math.PI*1.5f)+(d%3-1)*0.08f;
                float dx=rda*(float)Math.cos(ada), dz=rda*(float)Math.sin(ada), da=0.12f+(d%3)*0.04f, dw=0.03f+(d%4)*0.01f;
                q(b,m,dx-dw,BEAM_MID-dw,dz,dx+dw,BEAM_MID-dw,dz,dx-dw,BEAM_MID+dw,dz,dx+dw,BEAM_MID+dw,dz,0.02f,0.02f,0.04f,da);}
        }
        float flowS=(ms%30000L)/30000f;
        for(int di=0;di<180;di++){float r0=0.3f+(di*0.37f%1f)*(aOut-0.3f), a0d=di*1.7f+galRot*(0.1f+(di%3)*0.05f);
            float spd=(2.0f-r0/aOut)*0.5f, ph=(r0/aOut-flowS*spd)%1f; if(ph<0)ph+=1f;
            float rd=aIn+(float)Math.pow(ph,0.7f)*(aOut-aIn), ad=a0d+ph*(float)(Math.PI*0.5f);
            float dx=rd*(float)Math.cos(ad), dz=rd*(float)Math.sin(ad), dyS=BEAM_MID+(float)(Math.sin(di*2.7f)*0.4f);
            float fadeD=ph<0.03f?ph/0.03f:1f, tw=(float)((Math.sin(st*8f+di)+1)*0.5);
            float ds=0.025f+tw*0.01f, daD=(0.4f+tw*0.5f)*fadeD, crD=1,cgD=0.9f,cbD=0.8f+(di%2)*0.2f;
            b.addVertex(m,dx-ds,dyS,dz-ds).setColor(crD,cgD,cbD,daD); b.addVertex(m,dx+ds,dyS,dz-ds).setColor(crD,cgD,cbD,daD); b.addVertex(m,dx-ds,dyS,dz+ds).setColor(crD,cgD,cbD,daD);
            b.addVertex(m,dx+ds,dyS,dz-ds).setColor(crD,cgD,cbD,daD); b.addVertex(m,dx+ds,dyS,dz+ds).setColor(crD,cgD,cbD,daD); b.addVertex(m,dx-ds,dyS,dz+ds).setColor(crD,cgD,cbD,daD);
        }
        float gwC=(ms%8000L)/8000f; if(gwC<0.7f){float t=gwC/0.7f,r0g=0.3f+t*5f,r1g=r0g+0.08f; float ag=(1f-Math.abs(t-0.5f)*2f)*0.3f; for(int i=0;i<80;i++){float tt0=i/80f*2*(float)Math.PI,tt1=(i+1f)/80f*2*(float)Math.PI; float x0g=r0g*(float)Math.cos(tt0),z0g=r0g*(float)Math.sin(tt0),x1g=r0g*(float)Math.cos(tt1),z1g=r0g*(float)Math.sin(tt1),x2g=r1g*(float)Math.cos(tt0),z2g=r1g*(float)Math.sin(tt0),x3g=r1g*(float)Math.cos(tt1),z3g=r1g*(float)Math.sin(tt1); q(b,m,x0g,BEAM_MID-0.01f,z0g,x1g,BEAM_MID-0.01f,z1g,x2g,BEAM_MID+0.01f,z2g,x3g,BEAM_MID+0.01f,z3g,0.2f,0.15f,0.3f,ag);}}
    }

    private static float[] rp(float th) { float x=RING_R*(float)Math.cos(th), z=RING_R*(float)Math.sin(th); return tp(x,z); }
    private static float[] tp(float x,float z) { return new float[]{x,z*(float)Math.sin(TILT),z*(float)Math.cos(TILT)}; }

    private static float[] toWorld(Direction f, float ox, float oy, float oz) {
        return switch (f) {
            case NORTH -> new float[]{ox, oy, oz}; case SOUTH -> new float[]{-ox, oy, -oz};
            case EAST  -> new float[]{-oz, oy, ox}; case WEST  -> new float[]{oz, oy, -ox};
            default    -> new float[]{ox, oy, oz};
        };
    }

    private static void hs(BufferBuilder b,Matrix4f m,float cx,float cy,float cz,float r,float cr,float cg,float cb,float ca){int la=16,lo=24;for(int lat=0;lat<la;lat++){float t1=(float)lat/la*(float)Math.PI,t2=(float)(lat+1)/la*(float)Math.PI,pf=(float)Math.sin((t1+t2)*0.5f),a=ca*(0.4f+0.6f*pf);for(int lon=0;lon<lo;lon++){float p1=(float)lon/lo*2*(float)Math.PI,p2=(float)(lon+1)/lo*2*(float)Math.PI;float x0=cx+r*(float)(Math.sin(t1)*Math.cos(p1)),y0=cy+r*(float)Math.cos(t1),z0=cz+r*(float)(Math.sin(t1)*Math.sin(p1)),x1=cx+r*(float)(Math.sin(t1)*Math.cos(p2)),y1=cy+r*(float)Math.cos(t1),z1=cz+r*(float)(Math.sin(t1)*Math.sin(p2)),x2=cx+r*(float)(Math.sin(t2)*Math.cos(p1)),y2=cy+r*(float)Math.cos(t2),z2=cz+r*(float)(Math.sin(t2)*Math.sin(p1)),x3=cx+r*(float)(Math.sin(t2)*Math.cos(p2)),y3=cy+r*(float)Math.cos(t2),z3=cz+r*(float)(Math.sin(t2)*Math.sin(p2));b.addVertex(m,x0,y0,z0).setColor(cr,cg,cb,a);b.addVertex(m,x1,y1,z1).setColor(cr,cg,cb,a);b.addVertex(m,x2,y2,z2).setColor(cr,cg,cb,a);b.addVertex(m,x1,y1,z1).setColor(cr,cg,cb,a);b.addVertex(m,x3,y3,z3).setColor(cr,cg,cb,a);b.addVertex(m,x2,y2,z2).setColor(cr,cg,cb,a);}}}
    private static void msph(BufferBuilder b,Matrix4f m,float cx,float cy,float cz,float r,float cr,float cg,float cb,float ca){int la=8,lo=10;for(int lat=0;lat<la;lat++){float t1=(float)lat/la*(float)Math.PI,t2=(float)(lat+1)/la*(float)Math.PI;for(int lon=0;lon<lo;lon++){float p1=(float)lon/lo*2*(float)Math.PI,p2=(float)(lon+1)/lo*2*(float)Math.PI;float x0=cx+r*(float)(Math.sin(t1)*Math.cos(p1)),y0=cy+r*(float)Math.cos(t1),z0=cz+r*(float)(Math.sin(t1)*Math.sin(p1)),x1=cx+r*(float)(Math.sin(t1)*Math.cos(p2)),y1=cy+r*(float)Math.cos(t1),z1=cz+r*(float)(Math.sin(t1)*Math.sin(p2)),x2=cx+r*(float)(Math.sin(t2)*Math.cos(p1)),y2=cy+r*(float)Math.cos(t2),z2=cz+r*(float)(Math.sin(t2)*Math.sin(p1)),x3=cx+r*(float)(Math.sin(t2)*Math.cos(p2)),y3=cy+r*(float)Math.cos(t2),z3=cz+r*(float)(Math.sin(t2)*Math.sin(p2));b.addVertex(m,x0,y0,z0).setColor(cr,cg,cb,ca);b.addVertex(m,x1,y1,z1).setColor(cr,cg,cb,ca);b.addVertex(m,x2,y2,z2).setColor(cr,cg,cb,ca);b.addVertex(m,x1,y1,z1).setColor(cr,cg,cb,ca);b.addVertex(m,x3,y3,z3).setColor(cr,cg,cb,ca);b.addVertex(m,x2,y2,z2).setColor(cr,cg,cb,ca);}}}
    private static void q(BufferBuilder b,Matrix4f m,float x0,float y0,float z0,float x1,float y1,float z1,float x2,float y2,float z2,float x3,float y3,float z3,float r,float g,float bl,float a){b.addVertex(m,x0,y0,z0).setColor(r,g,bl,a);b.addVertex(m,x1,y1,z1).setColor(r,g,bl,a);b.addVertex(m,x2,y2,z2).setColor(r,g,bl,a);b.addVertex(m,x2,y2,z2).setColor(r,g,bl,a);b.addVertex(m,x1,y1,z1).setColor(r,g,bl,a);b.addVertex(m,x3,y3,z3).setColor(r,g,bl,a);}
    private static void beamSeg(BufferBuilder b,Matrix4f m,float yBot,float yTop,float flow,float pulse,float cr,float cg,float cb){float totalH=yTop-yBot;int segs=8,stacks=15;for(int s=0;s<stacks;s++){float y0=yBot+totalH*s/stacks,y1=yBot+totalH*(s+1)/stacks,segMid=(s+0.5f)/stacks,bright=1-Math.abs(segMid-(1-flow))*3f;if(bright<0)bright=0;float a=(0.15f+pulse*0.25f)*(0.3f+bright*0.7f);for(int i=0;i<segs;i++){float a0=(float)i/segs*2*(float)Math.PI,a1=(float)(i+1)/segs*2*(float)Math.PI;float x0=0.6f*(float)Math.cos(a0),z0=0.6f*(float)Math.sin(a0),x1=0.6f*(float)Math.cos(a1),z1=0.6f*(float)Math.sin(a1);b.addVertex(m,x0,y0,z0).setColor(cr,cg,cb,a);b.addVertex(m,x1,y0,z1).setColor(cr,cg,cb,a);b.addVertex(m,x0,y1,z0).setColor(cr,cg,cb,a);b.addVertex(m,x1,y0,z1).setColor(cr,cg,cb,a);b.addVertex(m,x1,y1,z1).setColor(cr,cg,cb,a);b.addVertex(m,x0,y1,z0).setColor(cr,cg,cb,a);float ox0=x0*1.5f,oz0=z0*1.5f,ox1=x1*1.5f,oz1=z1*1.5f;b.addVertex(m,ox0,y0,oz0).setColor(cr,cg,cb,a*0.3f);b.addVertex(m,ox1,y0,oz1).setColor(cr,cg,cb,a*0.3f);b.addVertex(m,ox0,y1,oz0).setColor(cr,cg,cb,a*0.3f);b.addVertex(m,ox1,y0,oz1).setColor(cr,cg,cb,a*0.3f);b.addVertex(m,ox1,y1,oz1).setColor(cr,cg,cb,a*0.3f);b.addVertex(m,ox0,y1,oz0).setColor(cr,cg,cb,a*0.3f);}}}
    private static void beamParts(BufferBuilder b,Matrix4f m,float yBot,float yTop,float st,float flow,float cr,float cg,float cb){float totalH=yTop-yBot;for(int p=0;p<16;p++){float phase=(flow+p*0.06f)%1f,py=yBot+phase*totalH,pa=(float)Math.sin(phase*Math.PI)*0.6f,pAngle=st*3f+p*0.7f,pR=0.6f*(0.2f+0.8f*phase);float px=pR*(float)Math.cos(pAngle),pz=pR*(float)Math.sin(pAngle),ps=0.08f;q(b,m,px-ps,py,pz,px+ps,py,pz,px,py-ps,pz,px,py+ps,pz,cr,cg,cb,pa);}}
    private static void starFlare(BufferBuilder b,Matrix4f m,float cx,float cy,float cz,float len,float cr,float cg,float cb,float ca){float w=0.15f;for(int i=0;i<8;i++){double a=i*Math.PI/4;float dx=(float)Math.cos(a),dz=(float)Math.sin(a),nx=dz,nz=-dx,wx=nx*w,wz=nz*w;b.addVertex(m,cx+dx*1.5f+wx,cy,cz+dz*1.5f+wz).setColor(cr,cg,cb,ca);b.addVertex(m,cx+dx*len+wx,cy,cz+dz*len+wz).setColor(cr,cg,cb,0);b.addVertex(m,cx+dx*len-wx,cy,cz+dz*len-wz).setColor(cr,cg,cb,0);b.addVertex(m,cx+dx*1.5f+wx,cy,cz+dz*1.5f+wz).setColor(cr,cg,cb,ca);b.addVertex(m,cx+dx*len-wx,cy,cz+dz*len-wz).setColor(cr,cg,cb,0);b.addVertex(m,cx+dx*1.5f-wx,cy,cz+dz*1.5f-wz).setColor(cr,cg,cb,ca);}}
}
