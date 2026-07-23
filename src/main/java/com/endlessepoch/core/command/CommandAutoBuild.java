package com.endlessepoch.core.command;

import com.endlessepoch.core.api.multiblock.IMultiBlockController;
import com.endlessepoch.core.api.multiblock.MultiBlockFormHandler;
import com.endlessepoch.core.api.multiblock.MultiBlockPattern;
import com.endlessepoch.core.api.multiblock.MultiBlockRegistry;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.Vec3;

/**
 * /eecore build — command-driven multiblock construction from player inventory.
 * Supports full-build or single-layer build.
 * <p>
 * 指令建造——从背包消耗材料放置多方块结构，支持整体或单层建造。
 */
public final class CommandAutoBuild {

    private CommandAutoBuild() {}

    /** Register /eecore build subcommand. / 注册 /eecore build 子命令。 */
    public static void register(LiteralArgumentBuilder<CommandSourceStack> root) {
        root.then(Commands.literal("build")
                .requires(source -> source.hasPermission(2))
                .then(Commands.argument("id", ResourceLocationArgument.id())
                        .executes(ctx -> execute(ctx, ResourceLocationArgument.getId(ctx, "id"), -1))
                        .then(Commands.argument("layer", IntegerArgumentType.integer(0))
                                .executes(ctx -> execute(ctx,
                                        ResourceLocationArgument.getId(ctx, "id"),
                                        IntegerArgumentType.getInteger(ctx, "layer"))))));
    }

    /**
     * Core execution: locate controller, get pattern, place blocks, try formation.
     * 核心执行：定位控制器→获取模式→放置方块→尝试成型。
     *
     * @param layer Y-layer to build, or -1 for all layers / Y 层，-1 表示全建
     */
    private static int execute(CommandContext<CommandSourceStack> ctx,
                               ResourceLocation patternId, int layer) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        Level level = player.serverLevel();

        // 1. Find controller block the player is looking at / 找到玩家注视的控制器
        BlockPos controllerPos = rayTrace(player, level);
        if (controllerPos == null) {
            ctx.getSource().sendFailure(Component.literal(
                    "§cNo block targeted — look at a controller block."));
            return 0;
        }

        BlockEntity be = level.getBlockEntity(controllerPos);
        if (!(be instanceof IMultiBlockController controller)) {
            ctx.getSource().sendFailure(Component.literal(
                    "§cTarget block is not a multiblock controller."));
            return 0;
        }

        // 2. Get pattern from registry / 从注册表获取模式
        var opt = MultiBlockRegistry.get(player.getUUID(), patternId);
        if (opt.isEmpty()) {
            ctx.getSource().sendFailure(Component.literal(
                    "§cPattern not found: " + patternId));
            return 0;
        }
        MultiBlockPattern pattern = opt.get();

        // 3. Get facing direction / 获取朝向
        Direction facing = getFacing(level, controllerPos);

        // 4. Validate layer range / 验证层范围
        if (layer >= pattern.height) {
            ctx.getSource().sendFailure(Component.literal(
                    "§cLayer " + layer + " out of range (pattern height: " + pattern.height + ")"));
            return 0;
        }

        // 5. Place blocks / 放置方块
        int minY = layer >= 0 ? layer : 0;
        int maxY = layer >= 0 ? layer + 1 : pattern.height;

        int placed = 0, alreadyCorrect = 0, missingMaterials = 0;
        for (int y = minY; y < maxY; y++) {
            for (int z = 0; z < pattern.depth; z++) {
                for (int x = 0; x < pattern.width; x++) {
                    char c = pattern.getChar(x, y, z);
                    // Skip air, wildcard, and controller positions / 跳过空气/通配符/控制器
                    if (c == 'A' || c == '#' || c == 'K') continue;

                    BlockState expected = pattern.getExpectedState(x, y, z);
                    if (expected == null) continue;

                    BlockPos worldPos = patternToWorld(controllerPos, x, y, z,
                            pattern.controllerX, pattern.controllerY, pattern.controllerZ, facing);
                    BlockState current = level.getBlockState(worldPos);

                    // Already correct / 已就位
                    if (current.getBlock() == expected.getBlock()) {
                        alreadyCorrect++;
                        continue;
                    }
                    // Also check alternatives / 检查替代方块
                    var alts = pattern.getAlternatives(c);
                    if (!alts.isEmpty() && alts.contains(current)) {
                        alreadyCorrect++;
                        continue;
                    }

                    // Try to consume from inventory and place / 从背包消耗并放置
                    if (consumeItem(player, expected)) {
                        level.setBlock(worldPos, expected, 3);
                        placed++;
                    } else {
                        missingMaterials++;
                    }
                }
            }
        }

        // 6. Try formation / 尝试成型
        MultiBlockFormHandler.tryForm(be, pattern, facing, player);

        // 7. Feedback / 反馈
        String layerInfo = layer >= 0 ? " layer " + layer : " all layers";
        final int p = placed, c = alreadyCorrect, m = missingMaterials;
        ctx.getSource().sendSuccess(() -> Component.literal(
                "§aBuilt §f" + patternId + "§a" + layerInfo
                        + " — placed §e" + p + "§a, already correct §7" + c
                        + "§a, missing §c" + m), true);
        return 1;
    }

    /**
     * Raytrace from player eye to find the block they are looking at (up to 5 blocks).
     * 从玩家视线做射线检测，找到注视的方块（最多 5 格）。
     */
    private static BlockPos rayTrace(ServerPlayer player, Level level) {
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getLookAngle();
        for (float d = 0; d <= 5.0f; d += 0.5f) {
            BlockPos bp = BlockPos.containing(eye.add(look.scale(d)));
            if (!level.getBlockState(bp).isAir()) return bp;
        }
        return null;
    }

    /**
     * Extract horizontal facing from block state, default NORTH.
     * 从方块状态提取水平朝向，默认 NORTH。
     */
    private static Direction getFacing(Level level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (state.hasProperty(BlockStateProperties.HORIZONTAL_FACING)) {
            return state.getValue(BlockStateProperties.HORIZONTAL_FACING);
        }
        return Direction.NORTH;
    }

    /**
     * Map pattern-local (x,y,z) to world BlockPos relative to controller, with facing rotation.
     * 将模式局部坐标映射到世界坐标，相对控制器位置+朝向旋转。
     */
    static BlockPos patternToWorld(BlockPos controllerPos,
                                    int x, int y, int z,
                                    int cx, int cy, int cz, Direction facing) {
        int rx = x - cx;
        int ry = y - cy;
        int rz = z - cz;
        return switch (facing) {
            case NORTH -> controllerPos.offset(rx, ry, rz);
            case SOUTH -> controllerPos.offset(-rx, ry, -rz);
            case EAST  -> controllerPos.offset(-rz, ry, rx);
            case WEST  -> controllerPos.offset(rz, ry, -rx);
            default    -> controllerPos.offset(rx, ry, rz);
        };
    }

    /**
     * Consume one matching item from player inventory.
     * Creative mode always succeeds without consuming.
     * <p>
     * 从背包消耗一个匹配方块物品。创造模式不消耗直接成功。
     */
    private static boolean consumeItem(ServerPlayer player, BlockState needed) {
        if (player.isCreative()) return true;
        Item neededItem = needed.getBlock().asItem();
        Inventory inv = player.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack stack = inv.getItem(i);
            if (stack.is(neededItem)) {
                stack.shrink(1);
                inv.setChanged();
                return true;
            }
        }
        return false;
    }
}
