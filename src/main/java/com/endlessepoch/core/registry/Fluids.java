package com.endlessepoch.core.registry;

import com.endlessepoch.core.EECore;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.fluids.BaseFlowingFluid;
import net.neoforged.neoforge.fluids.FluidType;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

/** Fluid registration utility. / 流体自动注册。 */
public class Fluids {
    public static final DeferredRegister<Fluid> FLUIDS = DeferredRegister.create(net.minecraft.core.registries.Registries.FLUID, EECore.MOD_ID);
    public static final DeferredRegister<FluidType> FLUID_TYPES = DeferredRegister.create(NeoForgeRegistries.FLUID_TYPES, EECore.MOD_ID);

    public static void registerClient(net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent e) {
        // Register all fluid client extensions here / 所有流体客户端扩展统一在这注册
        for (var entry : TINTS.entrySet())
            e.registerFluidType(new net.neoforged.neoforge.client.extensions.common.IClientFluidTypeExtensions() {
                @Override public int getTintColor() { return entry.getValue(); }
                @Override public ResourceLocation getStillTexture() { return ResourceLocation.withDefaultNamespace("block/water_still"); }
                @Override public ResourceLocation getFlowingTexture() { return ResourceLocation.withDefaultNamespace("block/water_flow"); }
            }, entry.getKey().get());
    }

    private static final Map<Supplier<FluidType>, Integer> TINTS = new LinkedHashMap<>();
    /** Auto-collected for creative tab. / 创造栏自动收集。 */
    public static final List<Supplier<BucketItem>> BUCKETS = new ArrayList<>();

    public static final int UPRIGHT = 1, INVERTED = 2;

    /** EECore internal. @param style 1=正桶 2=倒桶 */
    public static FluidReg register(String id, int tint, int temp, String en, String zh, int style) {
        return register(FLUID_TYPES, FLUIDS, Items.ITEMS, "eecore", id, tint, temp, en, zh, style);
    }

    /** Addon mod API. @param style 1=正桶 2=倒桶 */
    public static FluidReg register(DeferredRegister<FluidType> types, DeferredRegister<Fluid> fluids,
                                     DeferredRegister<Item> items, String ns, String id,
                                     int tint, int temp, String en, String zh, int style) {
        var type = types.register(id, () -> new FluidType(
                FluidType.Properties.create().temperature(temp).density(1).viscosity(1000)
                    .sound(net.neoforged.neoforge.common.SoundActions.BUCKET_FILL, net.minecraft.sounds.SoundEvents.BUCKET_FILL)
                    .sound(net.neoforged.neoforge.common.SoundActions.BUCKET_EMPTY, net.minecraft.sounds.SoundEvents.BUCKET_EMPTY)));
        TINTS.put(type, tint);

        var sRef = new AtomicReference<Supplier<Fluid>>();
        var fRef = new AtomicReference<Supplier<Fluid>>();
        var bRef = new AtomicReference<Supplier<BucketItem>>();

        var props = new BaseFlowingFluid.Properties(type, () -> sRef.get().get(), () -> fRef.get().get())
                .bucket(() -> bRef.get().get()).block(null).tickRate(5);

        Supplier<Fluid> src = fluids.register(id, () -> new BaseFlowingFluid.Source(props));
        Supplier<Fluid> flow = fluids.register(id + "_flowing", () -> new BaseFlowingFluid.Flowing(props));
        sRef.set(src); fRef.set(flow);

        var bucket = items.register(id + "_bucket",
                () -> new BucketItem(src.get(), new Item.Properties().stacksTo(1)));
        bRef.set(bucket); BUCKETS.add(bucket);

        PartReg.TRANS_EN.computeIfAbsent(ns, k -> new LinkedHashMap<>())
                .put("fluid_type."+ns+"."+id, en);
        PartReg.TRANS_ZH.computeIfAbsent(ns, k -> new LinkedHashMap<>())
                .put("fluid_type."+ns+"."+id, zh);
        PartReg.TRANS_EN.get(ns).put("item."+ns+"."+id+"_bucket", en+" Bucket");
        PartReg.TRANS_ZH.get(ns).put("item."+ns+"."+id+"_bucket", zh+"桶");
        genBucketTex(id, tint, style == INVERTED);
        ResourceGenerator.writeJsonNs(ns, "models/item", id+"_bucket",
            "{\"parent\":\"minecraft:item/generated\",\"textures\":{\"layer0\":\""+ns+":item/"+id+"_bucket\"}}");
        return new FluidReg(type, src, flow, (Supplier<BucketItem>)(Supplier<?>)bucket);
    }

    private static void genBucketTex(String id, int tint, boolean flipped) {
        try {
            var is = Thread.currentThread().getContextClassLoader()
                .getResourceAsStream("assets/minecraft/textures/item/water_bucket.png");
            if (is != null) {
                var base = javax.imageio.ImageIO.read(is); is.close();
                var img = new java.awt.image.BufferedImage(16, 16, java.awt.image.BufferedImage.TYPE_INT_ARGB);
                int tr=(tint>>16)&255, tg=(tint>>8)&255, tb=tint&255;
                for (int x=0;x<16;x++) for (int y=0;y<16;y++) {
                    int px=base.getRGB(x,y), pr=(px>>16)&255, pg=(px>>8)&255, pb=px&255;
                    if(pr<100&&pg<150&&pb>180){
                        float lum=(0.299f*pr+0.587f*pg+0.114f*pb)/255f;
                        lum=0.55f+lum*0.7f;
                        int nr=(int)(tr*lum), ng=(int)(tg*lum), nb=(int)(tb*lum);
                        img.setRGB(x,y,0xFF000000|(Math.min(255,nr)<<16)|(Math.min(255,ng)<<8)|Math.min(255,nb));
                    }else img.setRGB(x,y,px);
                }
                if (flipped) {
                    var rot = new java.awt.image.BufferedImage(16,16,java.awt.image.BufferedImage.TYPE_INT_ARGB);
                    var g2 = rot.createGraphics();
                    g2.rotate(Math.PI,8,8); g2.drawImage(img,0,0,null); g2.dispose();
                    for (int x=0;x<16;x++) for (int y=0;y<16;y++) img.setRGB(x,y,rot.getRGB(x,y));
                }
                for (String b : new String[]{"src/main/resources", "build/resources/main"}) {
                    var d = ResourceGenerator.PROJECT_ROOT.resolve(b).resolve("assets/eecore/textures/item");
                    java.nio.file.Files.createDirectories(d);
                    javax.imageio.ImageIO.write(img, "PNG", d.resolve(id + "_bucket.png").toFile());
                }
            }
        } catch (Exception ignored) {}
    }

    public record FluidReg(Supplier<? extends FluidType> type, Supplier<? extends Fluid> src, Supplier<? extends Fluid> flowing, Supplier<BucketItem> bucket) {}

    // ── Registered fluids ──
    public static final FluidReg STEAM = register("steam", 0xFFE0E0E0, 400, "Steam", "蒸汽", UPRIGHT);
    public static final FluidReg STEAM_SH = register("superheated_steam", 0xFFF5E8D0, 600, "Superheated Steam", "过热蒸汽", INVERTED);
}
