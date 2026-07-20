package com.endlessepoch.core.command;

import com.endlessepoch.core.Config;
import com.endlessepoch.core.api.energy.eb.CpuMonitor;
import com.endlessepoch.core.api.energy.eb.batch.BatchExecutor;
import com.endlessepoch.core.api.energy.eb.batch.MainThreadRateLimiter;
import com.endlessepoch.core.api.energy.eb.batch.MachineLoadLimiter;
import com.endlessepoch.core.api.energy.eb.batch.SegmentMergeManager;
import com.endlessepoch.core.api.energy.eb.batch.TpsQuotaManager;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

/**
 * /eeadmin stats — Phase 4 debugging command. Displays live EB pipeline metrics.
 * /eeadmin stats —— Phase 4 调试命令，显示 EB 管线实时指标。
 */
public final class EeAdminCommand {

    private EeAdminCommand() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("eeadmin")
                .requires(s -> s.hasPermission(4))
                .then(Commands.literal("stats").executes(ctx -> {
                    var src = ctx.getSource();
                    double cpu = CpuMonitor.usage();
                    double tps = TpsQuotaManager.GLOBAL.lastTps();
                    int active = MachineLoadLimiter.activeCount();
                    int shards = BatchExecutor.activeShards();
                    int budget = MainThreadRateLimiter.remaining();
                    int done = com.endlessepoch.core.nova.block.MachineControllerBlockEntity.globalBatchCompletions;
                    src.sendSuccess(() -> Component.literal(String.format(
                            "[EECore] TPS=%.1f CPU=%.0f%% machines=%d shards=%d budget=%d batches=%d",
                            tps, cpu * 100, active, shards, budget, done)), false);
                    return 1;
                })));
    }
}
