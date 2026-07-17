package com.endlessepoch.core.integration;

import com.endlessepoch.core.menu.BusMenu;
import com.endlessepoch.core.network.SetGhostFluidPacket;
import com.endlessepoch.core.network.SetGhostSlotPacket;
import com.endlessepoch.core.screen.BusScreen;
import mezz.jei.api.gui.handlers.IGhostIngredientHandler;
import mezz.jei.api.ingredients.ITypedIngredient;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.FluidUtil;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;

/**
 * JEI ghost-drop targets for creative buses and assemblies — items drop onto the
 * phantom item slots, fluids (or fluid-holding items like buckets) onto the ghost
 * fluid slots. A bucket drag offers both target sets: item slot = the bucket item,
 * fluid slot = the contained fluid.
 * 创造总线/总成的 JEI 拖拽目标——物品落幻影物品槽，流体（或桶等含流体物品）落
 * 虚拟流体槽。拖桶时两种目标并存：物品槽=桶本身，流体槽=桶内流体。
 */
public class CreativeBusGhostHandler implements IGhostIngredientHandler<BusScreen> {

    @Override
    public <I> List<Target<I>> getTargetsTyped(BusScreen screen, ITypedIngredient<I> ingredient, boolean doStart) {
        BusMenu menu = screen.getMenu();
        // Only input-side creative parts take templates — voids have none
        // 仅输入侧创造部件可设模板——虚空无模板
        if (!menu.isCreative() || menu.isOutputBus()) return List.of();
        Object ing = ingredient.getIngredient();
        List<Target<I>> targets = new ArrayList<>();

        // Fluid targets on the ghost fluid slots (assemblies) / 流体目标：虚拟流体槽（总成）
        FluidStack fluid = ing instanceof FluidStack fs ? fs
                : ing instanceof ItemStack is && !is.isEmpty()
                        ? FluidUtil.getFluidContained(is).orElse(FluidStack.EMPTY)
                        : FluidStack.EMPTY;
        if (!fluid.isEmpty() && menu.getFluidSlots() > 0) {
            var fluidId = BuiltInRegistries.FLUID.getKey(fluid.getFluid());
            for (int i = 0; i < menu.getFluidSlots(); i++) {
                final int tank = i;
                var area = new Rect2i(screen.getGuiLeft() + menu.fluidSlotX(i),
                        screen.getGuiTop() + menu.fluidSlotY(i), 16, 16);
                targets.add(target(area,
                        () -> PacketDistributor.sendToServer(new SetGhostFluidPacket(menu.getPos(), tank, fluidId))));
            }
        }

        // Item targets on the phantom item slots / 物品目标：幻影物品槽
        if (ing instanceof ItemStack stack && !stack.isEmpty()) {
            int cols = menu.busCols();
            int baseX = screen.getGuiLeft() + menu.busX();
            int baseY = screen.getGuiTop() + 18 + menu.fluidRows() * 18;
            var itemId = BuiltInRegistries.ITEM.getKey(stack.getItem());
            for (int i = 0; i < menu.getSlotCount(); i++) {
                final int slot = i;
                var area = new Rect2i(baseX + (i % cols) * 18, baseY + (i / cols) * 18, 16, 16);
                targets.add(target(area,
                        () -> PacketDistributor.sendToServer(new SetGhostSlotPacket(menu.getPos(), slot, itemId))));
            }
        }
        return targets;
    }

    private static <I> Target<I> target(Rect2i area, Runnable accept) {
        return new Target<>() {
            @Override public Rect2i getArea() { return area; }
            @Override public void accept(I ing) { accept.run(); }
        };
    }

    @Override
    public void onComplete() {}
}
