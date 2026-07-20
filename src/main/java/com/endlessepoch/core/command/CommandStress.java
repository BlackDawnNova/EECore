package com.endlessepoch.core.command;

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

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

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

        List<MachineControllerBlockEntity> machines = new ArrayList<>();
        for (int dx = -128; dx <= 128; dx++)
            for (int dz = -128; dz <= 128; dz++)
                for (int dy = -2; dy <= 2; dy++) {
                    var be = level.getBlockEntity(pos.offset(dx, dy, dz));
                    if (be instanceof MachineControllerBlockEntity mc && mc.isFormed())
                        machines.add(mc);
                }
        if (machines.isEmpty()) {
            source.sendFailure(Component.literal("No formed machine within 128 blocks"));
            return 0;
        }

        final int count = machines.size();
        final long[] startOps = new long[1];
        for (var m : machines) startOps[0] += m.getBatchOpsProcessed();
        com.endlessepoch.core.EECore.LOGGER.info("[EB-STRESS] start {} ticks, {} machines, startOps={}", totalTicks, count, startOps[0]);

        var listener = new Consumer<ServerTickEvent.Post>() {
            private int ticks;
            @Override
            public void accept(ServerTickEvent.Post e) {
                if (++ticks < totalTicks) return;
                long done = 0; int formed = 0;
                for (var m : machines) { if (m.isFormed()) { done += m.getBatchOpsProcessed(); formed++; } }
                done = Math.max(0, done - startOps[0]);
                double rate = (double) done / totalTicks;
                String msg = String.format("Stress %dt: %d ops, %.1f ops/tick, TPS %.1f, %d/%d machines, shards %d, mtLimit %d",
                        totalTicks, done, rate,
                        TpsQuotaManager.GLOBAL.tps(e.getServer().tickRateManager().tickrate()),
                        formed, count, BatchExecutor.activeShards(),
                        com.endlessepoch.core.api.energy.eb.batch.MainThreadRateLimiter.currentLimit());
                source.sendSuccess(() -> Component.literal(msg), false);
                com.endlessepoch.core.EECore.LOGGER.info("[EB-STRESS] {}", msg);
                net.neoforged.neoforge.common.NeoForge.EVENT_BUS.unregister(this);
            }
        };
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(listener);
        return totalTicks;
    }
}
