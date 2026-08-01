package com.endlessepoch.core.nova.client;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import de.javagl.obj.Obj;
import de.javagl.obj.ObjReader;
import de.javagl.obj.ObjUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.core.BlockPos;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;

import java.io.IOException;
import java.io.InputStream;

@EventBusSubscriber(modid = com.endlessepoch.core.EECore.MOD_ID, value = Dist.CLIENT)
public final class KerrRenderer {
    private KerrRenderer() {}

    private static final BlockPos BH_POS = new BlockPos(0, 60, 0);
    private static final float BH_Y = 60.5f;
    private static float[] spVerts, spUVs;
    private static DynamicTexture sunDynamicTex;
    private static boolean loaded;

    private static final boolean DISABLED = true;

    @SubscribeEvent
    public static void onRender(RenderLevelStageEvent e) {
        if (DISABLED) return;
        if (e.getStage() != RenderLevelStageEvent.Stage.AFTER_WEATHER) return;
        var mc = Minecraft.getInstance(); if (mc.level == null || mc.player == null) return;
        var cam = e.getCamera().getPosition();

        float dx = BH_POS.getX() + 0.5f - (float) cam.x;
        float dy = BH_Y - (float) cam.y;
        float dz = BH_POS.getZ() + 0.5f - (float) cam.z;
        if (dx * dx + dy * dy + dz * dz > 128 * 128) return;

        if (!loaded) loadModel();

        RenderSystem.disableCull();
        var ps = e.getPoseStack();
        ps.pushPose();
        ps.translate(dx, dy, dz);

        float s = 12f;
        var tess = Tesselator.getInstance();

        RenderSystem.depthMask(true);
        RenderSystem.disableBlend();
        if (spVerts != null && spUVs != null) {
            drawTexturedObj(tess, ps.last().pose(), spVerts, spUVs, s);
        }

        ps.popPose();
        RenderSystem.depthMask(true); RenderSystem.enableCull();
    }

    private static void loadModel() {
        loaded = true;
        try {
            InputStream is = KerrRenderer.class.getResourceAsStream("/assets/eecore/models/item/sphere.obj");
            if (is == null) is = KerrRenderer.class.getClassLoader().getResourceAsStream("assets/eecore/models/item/sphere.obj");
            if (is == null) return;
            try {
                var mtlIs = KerrRenderer.class.getResourceAsStream("/assets/eecore/models/item/sphere.mtl");
                if (mtlIs == null) mtlIs = KerrRenderer.class.getClassLoader().getResourceAsStream("assets/eecore/models/item/sphere.mtl");
                if (mtlIs != null) {
                    String texName = null;
                    var reader = new java.io.BufferedReader(new java.io.InputStreamReader(mtlIs));
                    String line;
                    while ((line = reader.readLine()) != null) {
                        if (line.startsWith("map_Kd ")) { texName = line.substring(7).trim(); break; }
                    }
                    reader.close();
                    if (texName != null) {
                        var texIs = KerrRenderer.class.getResourceAsStream("/assets/eecore/textures/" + texName);
                        if (texIs == null) texIs = KerrRenderer.class.getClassLoader().getResourceAsStream("assets/eecore/textures/" + texName);
                        if (texIs != null) {
                            var img = NativeImage.read(texIs);
                            sunDynamicTex = new DynamicTexture(img);
                            sunDynamicTex.upload();
                            texIs.close();
                        }
                    }
                }
            } catch (IOException ignored) {}
            Obj obj = ObjUtils.convertToRenderable(ObjReader.read(is));
            var vertList = new java.util.ArrayList<Float>();
            var uvList = new java.util.ArrayList<Float>();
            int faceCount = obj.getNumFaces();
            for (int i = 0; i < faceCount; i++) {
                var f = obj.getFace(i);
                int nv = f.getNumVertices();
                if (nv < 3) continue;
                for (int j = 1; j < nv - 1; j++) {
                    int[] idx = {0, j, j + 1};
                    for (int k : idx) {
                        var v = obj.getVertex(f.getVertexIndex(k));
                        vertList.add(v.getX()); vertList.add(v.getY()); vertList.add(v.getZ());
                        try {
                            var vt = obj.getTexCoord(f.getTexCoordIndex(k));
                            uvList.add(vt.getX()); uvList.add(vt.getY());
                        } catch (Exception ex) {
                            uvList.add(0f); uvList.add(0f);
                        }
                    }
                }
            }
            spVerts = new float[vertList.size()]; for (int i = 0; i < spVerts.length; i++) spVerts[i] = vertList.get(i);
            spUVs = new float[uvList.size()]; for (int i = 0; i < spUVs.length; i++) spUVs[i] = uvList.get(i);
        } catch (Exception ex) {
            com.endlessepoch.core.EECore.LOGGER.error("[Kerr] Failed to load model", ex);
        }
    }

    private static void drawTexturedObj(Tesselator tess, Matrix4f m, float[] verts, float[] uvs, float scale) {
        if (sunDynamicTex == null) return;
        RenderSystem.setShader(GameRenderer::getPositionTexShader);
        RenderSystem.setShaderTexture(0, sunDynamicTex.getId());
        var buf = tess.begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_TEX);
        for (int i = 0; i < verts.length; i += 3) {
            float x = verts[i] * scale, y = verts[i + 1] * scale, z = verts[i + 2] * scale;
            float u = (i / 3 * 2 < uvs.length) ? uvs[(i / 3) * 2] : 0;
            float v = (i / 3 * 2 + 1 < uvs.length) ? uvs[(i / 3) * 2 + 1] : 0;
            buf.addVertex(m, x, y, z).setUv(u, v);
        }
        BufferUploader.drawWithShader(buf.buildOrThrow());
    }
}
