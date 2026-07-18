package com.endlessepoch.core.command;

import com.endlessepoch.core.Config;
import com.endlessepoch.core.api.energy.eb.batch.BatchExecutor;
import com.endlessepoch.core.api.energy.eb.batch.TpsQuotaManager;
import com.endlessepoch.core.nova.block.MachineControllerBlockEntity;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.function.Consumer;

/**
 * /eecore stress — monitors a formed machine for N ticks and reports throughput.
 * 压测命令——监控已成型机器 N tick，输出吞吐统计。
 */
public final class CommandStress {

    private CommandStress() {}

    public static void register(LiteralArgumentBuilder<CommandSourceStack> root) {
        root.then(Commands.literal("stress")
                .requires(src -> src.hasPermission(2))
                .then(Commands.argument("ticks", IntegerArgumentType.integer(20, 6000))
                        .executes(ctx -> run(ctx, IntegerArgumentType.getInteger(ctx, "ticks")))));
    }

    private static int run(CommandContext<CommandSourceStack> ctx, int totalTicks) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        var source = ctx.getSource();
        var player = source.getPlayerOrException();
        var level = (ServerLevel) player.level();
        var pos = player.blockPosition();

        MachineControllerBlockEntity mc = null;
        int scanned = 0;
        for (int r = 1; r <= 16 && mc == null; r++)
            for (int dx = -r; dx <= r && mc == null; dx++)
                for (int dz = -r; dz <= r && mc == null; dz++)
                    for (int dy = -2; dy <= 2; dy++) {
                        var be = level.getBlockEntity(pos.offset(dx, dy, dz));
                        scanned++;
                        if (be instanceof MachineControllerBlockEntity c) mc = c;
                    }
        if (mc == null) {
            source.sendFailure(Component.literal("No controller within 16 blocks (scanned " + scanned + ")"));
            return 0;
        }
        if (!mc.isFormed()) {
            source.sendFailure(Component.literal("Machine is not formed"));
            return 0;
        }

        source.sendSuccess(() -> Component.literal(
                "Stress: " + totalTicks + " ticks..."), false);

        final MachineControllerBlockEntity target = mc;
        final long startOps = target.getBatchOpsProcessed();
        com.endlessepoch.core.EECore.LOGGER.info("[EB-STRESS] start {} ticks, startOps={}", totalTicks, startOps);

        // One-shot listener — self-unregisters after reporting so repeated runs don't pile up on the bus
        // 一次性监听器——报告后自注销，多次压测不在事件总线上累积
        var listener = new Consumer<ServerTickEvent.Post>() {
            private int ticks;

            @Override
            public void accept(ServerTickEvent.Post e) {
                if (++ticks < totalTicks) return;
                long done = target.getBatchOpsProcessed() - startOps;
                double rate = (double) done / totalTicks;
                String msg = String.format("Stress %dt: %d ops, %.1f ops/tick, TPS %.1f, shards %d, mtLimit %d",
                        totalTicks, done, rate,
                        TpsQuotaManager.GLOBAL.tps(e.getServer().tickRateManager().tickrate()),
                        BatchExecutor.activeShards(), com.endlessepoch.core.api.energy.eb.batch.MainThreadRateLimiter.currentLimit());
                source.sendSuccess(() -> Component.literal(msg), false);
                com.endlessepoch.core.EECore.LOGGER.info("[EB-STRESS] {}", msg);
                net.neoforged.neoforge.common.NeoForge.EVENT_BUS.unregister(this);
            }
        };
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(listener);

        return totalTicks;
    }
}
