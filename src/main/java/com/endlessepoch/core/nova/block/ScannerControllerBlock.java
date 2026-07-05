package com.endlessepoch.core.nova.block;

import com.endlessepoch.core.api.multiblock.MultiBlockFormHandler;
import com.endlessepoch.core.api.multiblock.MultiBlockRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

/**
 * Scanner controller block — placed as part of a structure, detected as 'K' by the scanner.
 * Records the player's facing when placed so the scanner normalizes the pattern to north-facing.
 */
public class ScannerControllerBlock extends Block implements EntityBlock {

    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;

    public ScannerControllerBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        return defaultBlockState().setValue(FACING, ctx.getHorizontalDirection().getOpposite());
    }

    @Override
    public BlockState rotate(BlockState state, Rotation rot) {
        return state.setValue(FACING, rot.rotate(state.getValue(FACING)));
    }

    @Override
    public BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ScannerControllerBlockEntity(pos, state);
    }

    /**
     * On sneak-click: attempt to form any registered multiblock pattern at this controller.
     * Server-side only; client returns SUCCESS to prevent off-hand activation.
     * <p>
     * 潜行右键点击时：尝试在此控制器位置成型任一已注册的多方块模式。仅服务端执行，客户端返回 SUCCESS 以防副手触发。
     */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                               Player player, BlockHitResult hit) {
        if (!player.isShiftKeyDown()) return InteractionResult.PASS;
        if (level.isClientSide()) return InteractionResult.SUCCESS;

        BlockEntity be = level.getBlockEntity(pos);
        if (!(be instanceof ScannerControllerBlockEntity sc)) return InteractionResult.PASS;

        var patterns = MultiBlockRegistry.getAll(player.getUUID());
        if (patterns.isEmpty()) {
            player.sendSystemMessage(Component.literal(
                    "No pattern registered! Scan a structure first."));
            return InteractionResult.SUCCESS;
        }

        // Try to form each pattern / 尝试成形每个模式
        for (var e : patterns.entrySet()) {
            if (MultiBlockFormHandler.tryForm(be, e.getValue(), sc.getFacing(), player)) {
                player.sendSystemMessage(Component.literal("Formed: " + e.getKey()));
                // Send empty validation to clear client preview / 清空客户端投影
                var emptyPkt = new com.endlessepoch.core.network.SyncValidationPacket(
                        e.getKey(), new int[0], new int[0], new int[0], new int[0], 0, 0, 0, 0, 0, 0);
                net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(
                        (net.minecraft.server.level.ServerPlayer) player, emptyPkt);
                return InteractionResult.SUCCESS;
            }
        }

        // Not formed — validate each pattern and send results to client / 未成形则逐个验证
        var first = patterns.entrySet().iterator().next();
        validateAndPreview(first.getValue(), first.getKey(), pos, state.getValue(FACING), level, (net.minecraft.server.level.ServerPlayer) player);
        return InteractionResult.SUCCESS;
    }

    /**
     * Validates every block in the pattern against the world.
     * Sends missing/wrong positions to client for visual feedback.
     * <p>
     * 逐块验证世界方块是否匹配——发送缺失/错误位置给客户端渲染。
     */
    private static void validateAndPreview(com.endlessepoch.core.api.multiblock.MultiBlockPattern pat,
                                           net.minecraft.resources.ResourceLocation patternId,
                                           BlockPos controllerPos, Direction facing, Level level,
                                           net.minecraft.server.level.ServerPlayer player) {
        int w = pat.width, h = pat.height, d = pat.depth;
        java.util.List<Integer> mLocal = new java.util.ArrayList<>(), mWorld = new java.util.ArrayList<>();
        java.util.List<Integer> wLocal = new java.util.ArrayList<>(), wWorld = new java.util.ArrayList<>();

        for (int y = 0; y < h; y++)
            for (int z = 0; z < d; z++)
                for (int x = 0; x < w; x++) {
                    char c = pat.getChar(x, y, z);
                    if (c == 'A' || c == ' ') continue;

                    // Compute world pos from pattern coords, centered on controller / 以控制器为中心算世界坐标
                    int rx = x - pat.controllerX;
                    int ry = y - pat.controllerY;
                    int rz = z - pat.controllerZ;
                    BlockPos worldPos = switch (facing) {
                        case NORTH -> controllerPos.offset(rx, ry, rz);
                        case SOUTH -> controllerPos.offset(-rx, ry, -rz);
                        case EAST  -> controllerPos.offset(-rz, ry, rx);
                        case WEST  -> controllerPos.offset(rz, ry, -rx);
                        default    -> controllerPos.offset(rx, ry, rz);
                    };

                    net.minecraft.world.level.block.state.BlockState worldState = level.getBlockState(worldPos);
                    var expected = pat.getExpectedState(x, y, z);

                    if (worldState.isAir()) {
                        mLocal.add(x); mLocal.add(y); mLocal.add(z);
                        mWorld.add(worldPos.getX()); mWorld.add(worldPos.getY()); mWorld.add(worldPos.getZ());
                    } else if (expected != null && expected.getBlock() != worldState.getBlock()) {
                        var alts = pat.getAlternatives(c);
                        if (!alts.contains(worldState)) {
                            wLocal.add(x); wLocal.add(y); wLocal.add(z);
                            wWorld.add(worldPos.getX()); wWorld.add(worldPos.getY()); wWorld.add(worldPos.getZ());
                        }
                    }
                }

        int MAX = 1_000_000;
        var pkt = new com.endlessepoch.core.network.SyncValidationPacket(
                patternId, toArr(mLocal, MAX), toArr(mWorld, MAX),
                toArr(wLocal, MAX), toArr(wWorld, MAX),
                controllerPos.getX(), controllerPos.getY(), controllerPos.getZ(), w, h, d);
        System.out.println("[EECore] Server: ctrl=(" + controllerPos.getX() + "," + controllerPos.getY() + "," + controllerPos.getZ() + ") missing[0]=(" + (mWorld.isEmpty() ? "none" : "" + mWorld.get(0) + "," + mWorld.get(1) + "," + mWorld.get(2)) + ") facing=" + facing);
        net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player, pkt);
    }

    private static int[] toArr(java.util.List<Integer> list, int max) {
        int len = Math.min(list.size(), max);
        int[] arr = new int[len];
        for (int i = 0; i < len; i++) arr[i] = list.get(i);
        return arr;
    }

    private static net.minecraft.resources.ResourceLocation patIdFromController() {
        return com.endlessepoch.core.api.multiblock.MultiBlockRegistry
                .getPatternForController(com.endlessepoch.core.registry.Blocks.SCANNER_CONTROLLER.get())
                .orElse(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("eecore", "unknown"));
    }
}
