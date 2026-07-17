package com.endlessepoch.core.integration;

import com.endlessepoch.core.menu.HatchMenu;
import com.endlessepoch.core.network.SetGhostFluidPacket;
import com.endlessepoch.core.nova.block.part.CreativeHatchBlockEntity;
import com.endlessepoch.core.screen.HatchScreen;
import mezz.jei.api.gui.handlers.IGhostIngredientHandler;
import mezz.jei.api.ingredients.ITypedIngredient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.FluidUtil;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;

/**
 * JEI ghost-drop targets for the creative fluid input hatch — drag a fluid (or any
 * fluid-holding item like a bucket) from JEI onto a tank to set its template. The
 * FLUID is configured, never the container item.
 * 创造流体输入仓的 JEI 拖拽目标——从 JEI 拖流体（或桶等含流体物品）到罐位即设模板。
 * 配置的是流体本身，不是容器物品。
 */
public class CreativeFluidGhostHandler implements IGhostIngredientHandler<HatchScreen> {

    @Override
    public <I> List<Target<I>> getTargetsTyped(HatchScreen screen, ITypedIngredient<I> ingredient, boolean doStart) {
        FluidStack fluid = extractFluid(ingredient.getIngredient());
        if (fluid.isEmpty()) return List.of();
        HatchMenu menu = screen.getMenu();
        var level = Minecraft.getInstance().level;
        // Client-side BE lookup — the menu itself doesn't carry the BE on the client
        // 客户端查 BE——客户端菜单不持有 BE 引用
        if (level == null
                || !(level.getBlockEntity(menu.getPos()) instanceof CreativeHatchBlockEntity ch)
                || !ch.isFluidTemplate()) return List.of();
        int sx = menu.getEnergyCapacity().isZero() ? 80 : 64; // mirror HatchMenu slot layout / 与菜单槽位布局一致
        var fluidId = BuiltInRegistries.FLUID.getKey(fluid.getFluid());
        List<Target<I>> targets = new ArrayList<>(menu.tankCount);
        for (int i = 0; i < menu.tankCount; i++) {
            final int tank = i;
            final var area = new Rect2i(screen.getGuiLeft() + sx + i * 20, screen.getGuiTop() + 38, 16, 16);
            targets.add(new Target<>() {
                @Override public Rect2i getArea() { return area; }
                @Override public void accept(I ing) {
                    PacketDistributor.sendToServer(new SetGhostFluidPacket(menu.getPos(), tank, fluidId));
                }
            });
        }
        return targets;
    }

    /** FluidStack directly, or the fluid inside an item (bucket etc.). / 直接流体，或物品内含流体（桶等）。 */
    private static FluidStack extractFluid(Object ingredient) {
        if (ingredient instanceof FluidStack fs) return fs;
        if (ingredient instanceof ItemStack is && !is.isEmpty())
            return FluidUtil.getFluidContained(is).orElse(FluidStack.EMPTY);
        return FluidStack.EMPTY;
    }

    @Override
    public void onComplete() {}
}
