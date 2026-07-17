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

    /**
     * Factory for MachineRecipeSubscriber. Both closures run on the MAIN thread
     * (RecipeManager is not thread-safe) — true off-thread matching lives in the
     * Phase 3 batch pipeline (BatchExecutor + RecipeSnapshotCache).
     * 工厂方法。两个闭包都在主线程执行（RecipeManager 非线程安全）——
     * 真正的后台匹配在 Phase 3 批管线（BatchExecutor + RecipeSnapshotCache）。
     */
    static Subscriber machine(BlockEntity machine, Consumer<List<EeEvent>> matchLogic, Runnable commitLogic) {
        return new MachineRecipeSubscriber(machine, matchLogic, commitLogic);
    }
}

final class MachineRecipeSubscriber implements Subscriber {
    private final WeakReference<BlockEntity> machineRef;
    private final Consumer<List<EeEvent>> matchLogic;   // recipe matching, main thread / 配方匹配（主线程）
    private final Runnable commitLogic;                 // consume + start work, main thread / 扣料开工（主线程）
    private final AtomicBoolean unsubscribed = new AtomicBoolean();

    MachineRecipeSubscriber(BlockEntity machine, Consumer<List<EeEvent>> matchLogic, Runnable commitLogic) {
        this.machineRef = new WeakReference<>(machine);
        this.matchLogic = matchLogic;
        this.commitLogic = commitLogic;
    }

    @Override
    public void onNext(List<EeEvent> batch) {
        var be = machineRef.get();
        if (be == null || be.isRemoved() || unsubscribed.get()) {
            unsubscribe();
            return;
        }
        if (be.getLevel() == null || be.getLevel().isClientSide()) return;
        // RecipeManager is not thread-safe — dispatch to main thread / RecipeManager非线程安全，转到主线程
        var server = be.getLevel().getServer();
        if (server != null) server.execute(() -> {
            matchLogic.accept(batch);
            commitLogic.run();
        });
    }

    @Override
    public void onError(Throwable error) {
        var be = machineRef.get();
        String loc = be != null ? be.getBlockPos().toString() : "unknown";
        EECore.LOGGER.error("[EB] Subscriber error at {}", loc, error); // stack trace auto-appended / 栈自动附加
        unsubscribe();
    }

    @Override
    public void onComplete() { unsubscribe(); }

    @Override
    public boolean isUnsubscribed() { return unsubscribed.get(); }

    void unsubscribe() { unsubscribed.set(true); }
}
