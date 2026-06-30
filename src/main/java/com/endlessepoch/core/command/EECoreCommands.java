package com.endlessepoch.core.command;

import com.endlessepoch.core.EECore;
import com.endlessepoch.core.api.multiblock.MultiBlockPattern;
import com.endlessepoch.core.api.multiblock.MultiBlockRegistry;
import com.endlessepoch.core.network.OpenMbVisPacket;
import com.endlessepoch.core.network.SyncPatternPacket;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.LinkedHashMap;
import java.util.Map;

public final class EECoreCommands {

    private static final ResourceLocation DEBUG_MBVIZ_ID =
            ResourceLocation.fromNamespaceAndPath(EECore.MOD_ID, "debug_mbvis_grass_4x4x4");

    private EECoreCommands() {}

    public static void onRegisterCommands(RegisterCommandsEvent event) {
        register(event.getDispatcher());
    }

    private static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal(EECore.MOD_ID)
                .then(Commands.literal("debug")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.literal("mbvis")
                                .executes(ctx -> openDebugMbvis(ctx.getSource())))));
    }

    private static int openDebugMbvis(CommandSourceStack source) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        MultiBlockPattern pattern = createDebugGrassPattern();

        MultiBlockRegistry.registerLocal(player.getUUID(), DEBUG_MBVIZ_ID, pattern);
        PacketDistributor.sendToPlayer(
                player,
                SyncPatternPacket.fromPattern(DEBUG_MBVIZ_ID, pattern),
                new OpenMbVisPacket(DEBUG_MBVIZ_ID)
        );

        source.sendSuccess(() -> Component.literal("Opened EECore multiblock visualizer debug pattern."), true);
        return 1;
    }

    private static MultiBlockPattern createDebugGrassPattern() {
        String[][] layers = new String[4][4];
        for (int y = 0; y < 4; y++) {
            for (int z = 0; z < 4; z++) {
                layers[y][z] = "GGGG";
            }
        }

        Map<Character, BlockState> definitions = new LinkedHashMap<>();
        definitions.put('G', Blocks.GRASS_BLOCK.defaultBlockState());
        return new MultiBlockPattern(4, 4, 4, 0, 0, 0, layers, definitions);
    }
}
