package com.endlessepoch.core.api.energy.eb;

import com.endlessepoch.core.EECore;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.lang.ref.WeakReference;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * Reactive subscriber — weak references the machine BE to avoid leaks.
 * 基于弱引用的订阅者，防止内存泄漏。
 */
public interface Subscriber {
    void onNext(List<EeEvent> batch);
    void onError(Throwable error);
    void onComplete();
    default boolean isUnsubscribed() { return false; }

    /** Factory for MachineRecipeSubscriber / 工厂方法 */
    static Subscriber machine(BlockEntity machine, Consumer<List<EeEvent>> background, Runnable mainThread) {
        return new MachineRecipeSubscriber(machine, background, mainThread);
    }
}

final class MachineRecipeSubscriber implements Subscriber {
    private final WeakReference<BlockEntity> machineRef;
    private final Consumer<List<EeEvent>> backgroundLogic;
    private final Runnable mainThreadLogic;
    private final AtomicBoolean unsubscribed = new AtomicBoolean();

    MachineRecipeSubscriber(BlockEntity machine, Consumer<List<EeEvent>> background, Runnable mainThread) {
        this.machineRef = new WeakReference<>(machine);
        this.backgroundLogic = background;
        this.mainThreadLogic = mainThread;
    }

    @Override
    public void onNext(List<EeEvent> batch) {
        if (unsubscribed.get() || machineRef.get() == null || machineRef.get().isRemoved()) {
            unsubscribe();
            return;
        }
        var be = machineRef.get();
        if (be.getLevel() == null || be.getLevel().isClientSide()) return;
        // RecipeManager is not thread-safe — dispatch to main thread / RecipeManager非线程安全，转到主线程
        var server = be.getLevel().getServer();
        if (server != null) server.execute(() -> {
            backgroundLogic.accept(batch);
            mainThreadLogic.run();
        });
    }

    @Override
    public void onError(Throwable error) {
        var be = machineRef.get();
        String loc = be != null ? be.getBlockPos().toString() : "unknown";
        EECore.LOGGER.error("[EB] Subscriber error at {}: {}", loc, error.getMessage());
        unsubscribe();
    }

    @Override
    public void onComplete() { unsubscribe(); }

    @Override
    public boolean isUnsubscribed() { return unsubscribed.get(); }

    void unsubscribe() { unsubscribed.set(true); }
}
