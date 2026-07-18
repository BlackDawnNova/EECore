package com.endlessepoch.core.command;

import com.endlessepoch.core.EECore;
import com.endlessepoch.core.api.multiblock.MultiBlockPattern;
import com.endlessepoch.core.api.multiblock.MultiBlockRegistry;
import com.endlessepoch.core.api.multiblock.PatternStorage;
import com.endlessepoch.core.api.multiblock.TagDefRegistry;

import com.endlessepoch.core.network.OpenMbVisPacket;
import com.endlessepoch.core.network.SyncPatternBinaryPacket;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * EECore command registration and execution.
 * Handles /eecore reload, debug, export, import subcommands.
 * <p>
 * EECore 命令注册与执行，管理 reload / debug / export / import 子命令。
 */
public final class EECoreCommands {

    private static final ResourceLocation DEBUG_MBVIZ_ID =
            ResourceLocation.fromNamespaceAndPath(EECore.MOD_ID, "debug_mbvis_grass_4x4x4");

    private EECoreCommands() {}

    public static void onRegisterCommands(RegisterCommandsEvent event) {
        register(event.getDispatcher());
    }

    private static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        var root = Commands.literal(EECore.MOD_ID)
                .then(Commands.literal("reload")
                        .requires(source -> source.hasPermission(4))
                        .executes(ctx -> reloadStructures(ctx.getSource())))
                .then(Commands.literal("debug")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.literal("mbvis")
                                .executes(ctx -> openDebugMbvis(ctx.getSource()))))
                .then(Commands.literal("export")
                        .requires(source -> source.hasPermission(4))
                        .then(Commands.argument("id", ResourceLocationArgument.id())
                                .executes(ctx -> exportStructure(ctx.getSource(),
                                        ResourceLocationArgument.getId(ctx, "id"), "ecs"))
                                .then(Commands.literal("json")
                                        .executes(ctx -> exportStructure(ctx.getSource(),
                                                ResourceLocationArgument.getId(ctx, "id"), "json")))
                                .then(Commands.literal("ecs")
                                        .executes(ctx -> exportStructure(ctx.getSource(),
                                                ResourceLocationArgument.getId(ctx, "id"), "ecs")))))
                .then(Commands.literal("import")
                        .requires(source -> source.hasPermission(4))
                        .then(Commands.argument("filename", StringArgumentType.word())
                                .executes(ctx -> importStructure(ctx.getSource(),
                                        StringArgumentType.getString(ctx, "filename")))));
        CommandAutoBuild.register(root);
        CommandStress.register(root);
        dispatcher.register(root);
    }

    private static int reloadStructures(CommandSourceStack source) {
        PatternStorage.loadAll();
        // Recipes may have changed via datapack reload — rebuild the snapshot cache
        // 数据包重载后配方可能变化 — 重建配方快照缓存
        com.endlessepoch.core.api.recipe.RecipeSnapshotCache.reload(source.getServer().getRecipeManager());
        source.sendSuccess(() -> Component.literal("§aEECore structures + recipe snapshots reloaded."), true);
        return 1;
    }

    private static int openDebugMbvis(CommandSourceStack source) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        MultiBlockPattern pattern = createDebugTagPattern();
        // Register test tag / 注册测试标记
        TagDefRegistry.register("EECore:A",
                java.util.Set.of(Blocks.IRON_BLOCK, Blocks.GOLD_BLOCK, Blocks.DIAMOND_BLOCK), 8);

        MultiBlockRegistry.registerLocal(player.getUUID(), DEBUG_MBVIZ_ID, pattern);
        PacketDistributor.sendToPlayer(
                player,
                SyncPatternBinaryPacket.fromPattern(DEBUG_MBVIZ_ID, pattern),
                new OpenMbVisPacket(DEBUG_MBVIZ_ID)
        );

        source.sendSuccess(() -> Component.literal("§aDebug pattern opened (1x1 controller). Tag EECore:A registered."), true);
        return 1;
    }

    /**
     * /eecore export <id> [json|ecs]
     * Export a structure from registry to disk in the specified format.
     * <p>
     * 将注册表中的结构导出到磁盘，支持 json（可读调试）和 ecs（二进制）格式。
     */
    private static int exportStructure(CommandSourceStack source, ResourceLocation id, String format) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        var opt = MultiBlockRegistry.get(source.getPlayerOrException().getUUID(), id);
        MultiBlockPattern pattern = opt.orElseGet(() -> MultiBlockRegistry.get(id).orElse(null));
        if (pattern == null) {
            source.sendFailure(Component.literal("§cStructure not found: " + id));
            return 0;
        }

        if ("json".equals(format)) {
            PatternStorage.saveJson(id, pattern);
        } else {
            PatternStorage.saveEcs(id, pattern);
        }

        final String fmt = format;
        source.sendSuccess(() -> Component.literal("§aExported " + id + " as ." + fmt + " to config/eecore/scanned/"), true);
        return 1;
    }

    /**
     * /eecore import <filename>
     * Import a .json or .ecs file from config/eecore/structures/ into the registry.
     * Searches subdirectories for the file.
     * <p>
     * 从 config/eecore/structures/ 导入 .json 或 .ecs 文件到注册表。
     */
    private static int importStructure(CommandSourceStack source, String filename) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        java.nio.file.Path root = java.nio.file.Path.of("config", "eecore", "structures");

        if (!java.nio.file.Files.exists(root)) {
            source.sendFailure(Component.literal("§cconfig/eecore/structures/ does not exist."));
            return 0;
        }

        try (var walk = java.nio.file.Files.walk(root, 2)) {
            var matches = walk.filter(java.nio.file.Files::isRegularFile)
                    .filter(f -> f.getFileName().toString().equals(filename))
                    .toList();

            if (matches.isEmpty()) {
                source.sendFailure(Component.literal("§cFile not found: " + filename));
                return 0;
            }

            for (var file : matches) {
                String name = file.getFileName().toString();
                try {
                    if (name.endsWith(".ecs")) {
                        var pattern = com.endlessepoch.core.api.multiblock.EECoreCodec.read(file);
                        var rel = root.relativize(file);
                        var ns = rel.getName(0).toString().toLowerCase();
                        var path = rel.getName(1).toString().toLowerCase()
                                .replace(".ecs", "").replace('_', '/');
                        var id = ResourceLocation.fromNamespaceAndPath(ns, path);
                        MultiBlockRegistry.registerLocal(player.getUUID(), id, pattern);
                        PacketDistributor.sendToPlayer(player,
                                SyncPatternBinaryPacket.fromPattern(id, pattern));
                    } else if (name.endsWith(".json")) {
                        PatternStorage.loadAll();
                    }
                } catch (Exception e) {
                    source.sendFailure(Component.literal("§cFailed to import " + name + ": " + e.getMessage()));
                    return 0;
                }
            }

            final int count = matches.size();
            source.sendSuccess(() -> Component.literal("§aImported " + count + " file(s) and synced to client."), true);
        } catch (java.io.IOException e) {
            source.sendFailure(Component.literal("§cError: " + e.getMessage()));
        }
        return 1;
    }

    private static MultiBlockPattern createDebugTagPattern() {
        // 1x1: just controller / 仅控制器测试
        String[][] layers = new String[1][1];
        layers[0] = new String[]{"K"};
        Map<Character, BlockState> definitions = new LinkedHashMap<>();
        definitions.put('K', com.endlessepoch.core.registry.Blocks.SCANNER_CONTROLLER.get().defaultBlockState());
        return new MultiBlockPattern(1, 1, 1, 0, 0, 0, layers, definitions);
    }
}
