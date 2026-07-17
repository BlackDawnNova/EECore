package com.endlessepoch.core.nova.block.part;

import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;

import java.util.function.Consumer;

/**
 * Shared creative tank factories — template (infinite source) and void (sink) —
 * used by the creative fluid hatch and the creative assemblies.
 * 创造罐共用工厂——模板罐（无限源）与虚空罐（吞噬）——创造流体仓与创造总成共用。
 */
final class CreativeFluidTanks {

    private CreativeFluidTanks() {}

    /** Stored fluid is the template; reads present a full tank, drains never deplete. / 罐内即模板，对外恒满、抽取不减。 */
    static FluidTank template(int capacity, Runnable onChanged) {
        return new FluidTank(capacity) {
            @Override public FluidStack getFluid() {
                var f = super.getFluid();
                return f.isEmpty() ? FluidStack.EMPTY : new FluidStack(f.getFluid(), getCapacity());
            }
            @Override public int getFluidAmount() { return super.getFluid().isEmpty() ? 0 : getCapacity(); }
            @Override public int fill(FluidStack resource, FluidAction action) {
                if (resource.isEmpty()) return 0;
                if (action.execute()) { setFluid(new FluidStack(resource.getFluid(), 1)); onChanged.run(); }
                return resource.getAmount(); // bucket sets the template / 灌入即设模板
            }
            @Override public FluidStack drain(FluidStack resource, FluidAction action) {
                var f = super.getFluid();
                if (f.isEmpty() || resource.isEmpty() || f.getFluid() != resource.getFluid()) return FluidStack.EMPTY;
                return new FluidStack(f.getFluid(), resource.getAmount()); // never depletes / 永不减少
            }
            @Override public FluidStack drain(int maxDrain, FluidAction action) {
                var f = super.getFluid();
                if (f.isEmpty() || maxDrain <= 0) return FluidStack.EMPTY;
                return new FluidStack(f.getFluid(), maxDrain);
            }
            @Override public boolean isFluidValid(FluidStack stack) { return true; }
        };
    }

    /** Swallows everything, always reads empty; execute-fills are tallied. / 全吞恒空，实灌记账。 */
    static FluidTank voidSink(int capacity, Consumer<FluidStack> onVoided) {
        return new FluidTank(capacity) {
            @Override public int fill(FluidStack resource, FluidAction action) {
                if (action.execute() && !resource.isEmpty()) onVoided.accept(resource);
                return resource.getAmount();
            }
            @Override public FluidStack getFluid() { return FluidStack.EMPTY; }
            @Override public int getFluidAmount() { return 0; }
            @Override public FluidStack drain(FluidStack resource, FluidAction action) { return FluidStack.EMPTY; }
            @Override public FluidStack drain(int maxDrain, FluidAction action) { return FluidStack.EMPTY; }
            @Override public boolean isFluidValid(FluidStack stack) { return true; }
        };
    }
}
