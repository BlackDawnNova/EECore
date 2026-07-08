package com.endlessepoch.core.api.multiblock;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;

import java.util.*;
import java.util.function.Supplier;

/** Machine definition binds .ecs structure + metadata + block/item/pattern suppliers + tag alternatives. / 机器定义：绑定.ecs结构、元数据、Block/Item供应、标签替选块。 */
public class MachineDefinition implements Supplier<Block> {

    private final ResourceLocation id;
    private final String nameEn, nameZh;
    private final int tier;
    private final ResourceLocation ecsFile;
    private final Map<String, Set<Block>> tagBindings;
    private String model; // block model path, e.g. "eecore:block/scanner_controller"

    private Supplier<Block> blockSupplier;
    private Supplier<? extends Item> itemSupplier;
    private Supplier<MultiBlockPattern> patternSupplier;
    private IMachineEffect effect;
    private float offX, offY, offZ; // controller→structure center / 控制器→结构中心偏移

    public MachineDefinition(ResourceLocation id, String nameEn, String nameZh, int tier,
                             ResourceLocation ecsFile, Map<String, Set<Block>> tagBindings) {
        this.id = id;
        this.nameEn = nameEn;
        this.nameZh = nameZh;
        this.tier = tier;
        this.ecsFile = ecsFile;
        this.tagBindings = Collections.unmodifiableMap(tagBindings);
    }

    // Getters / 获取器

    public ResourceLocation getId() { return id; }
    public String getNameEn() { return nameEn; }
    public String getNameZh() { return nameZh; }
    public int getTier() { return tier; }
    public ResourceLocation getEcsFile() { return ecsFile; }
    public Map<String, Set<Block>> getTagBindings() { return tagBindings; }
    public String getModel() { return model != null ? model : "eecore:block/scanner_controller"; }
    public void setModel(String m) { this.model = m; }

    public Block getBlock() { return blockSupplier != null ? blockSupplier.get() : null; }
    public Item getItem() { return itemSupplier != null ? itemSupplier.get() : null; }

    @Override
    public Block get() { return getBlock(); }

    public Optional<MultiBlockPattern> getPattern() {
        return patternSupplier != null ? Optional.ofNullable(patternSupplier.get()) : Optional.empty();
    }

    // Setters / 设置器

    public void setBlockSupplier(Supplier<Block> supplier) { this.blockSupplier = supplier; }
    public void setItemSupplier(Supplier<? extends Item> supplier) { this.itemSupplier = supplier; }
    public void setPatternSupplier(Supplier<MultiBlockPattern> supplier) { this.patternSupplier = supplier; }

    public IMachineEffect getEffect() { return effect; }
    public void setEffect(IMachineEffect e) { this.effect = e; }

    public float getOffX() { return offX; }
    public float getOffY() { return offY; }
    public float getOffZ() { return offZ; }
    public void setCenterOffset(float x, float y, float z) { offX = x; offY = y; offZ = z; }

    /** Compute controller→structure-center offset from pattern. / 从 pattern 计算中心偏移。 */
    public void computeCenterOffset() {
        var p = getPattern().orElse(null);
        if (p == null) return;
        offX = ((p.width - 1) / 2f) - p.controllerX;
        offY = ((p.height - 1) / 2f) - p.controllerY;
        offZ = ((p.depth - 1) / 2f) - p.controllerZ;
    }

    public boolean hasEffect() { return effect != null; }
}
