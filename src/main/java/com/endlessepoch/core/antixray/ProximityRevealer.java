package com.endlessepoch.core.antixray;

import com.endlessepoch.core.block.OreBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Per-player proximity reveal: when a player is within range of a disguised ore,
 * sends an individual block-update to that player showing the real ore.
 * Hides it again when the player moves away.
 * 逐玩家近距离揭示：靠近时单发真实状态，远离后重新伪装。
 */
public final class ProximityRevealer {

    static final int REVEAL_RADIUS = 8;
    private static final int REVEAL_RADIUS_Y = REVEAL_RADIUS;
    private static final int SCAN_INTERVAL = 10; // 重伪装扫描间隔 / re-hide scan interval

    private static final Map<UUID, Set<BlockPos>> REVEALED = new HashMap<>();

    private static final BlockState DISGUISE_DEEPSLATE = Blocks.DEEPSLATE.defaultBlockState();
    private static final BlockState DISGUISE_STONE = Blocks.STONE.defaultBlockState();

    private static int tickCounter;

    private ProximityRevealer() {}


    public static void onServerTick(ServerTickEvent.Post event) {
        tickCounter++;
        boolean doFullScan = tickCounter % SCAN_INTERVAL == 0;

        for (ServerLevel level : event.getServer().getAllLevels()) {
            for (ServerPlayer player : level.players()) {
                processPlayer(player, level, doFullScan);
            }
        }
    }

    public static void onPlayerLeave(PlayerEvent.PlayerLoggedOutEvent event) {
        REVEALED.remove(event.getEntity().getUUID());
    }


    private static void processPlayer(ServerPlayer player, ServerLevel level, boolean doFullScan) {
        BlockPos center = player.blockPosition();
        int cx = center.getX(), cy = center.getY(), cz = center.getZ();
        Set<BlockPos> revealed = REVEALED.computeIfAbsent(player.getUUID(), k -> new HashSet<>());

        // Reveal ores newly inside range / 揭示新进入范围的矿
        for (int dx = -REVEAL_RADIUS; dx <= REVEAL_RADIUS; dx++) {
            for (int dz = -REVEAL_RADIUS; dz <= REVEAL_RADIUS; dz++) {
                for (int dy = -REVEAL_RADIUS_Y; dy <= REVEAL_RADIUS_Y; dy++) {
                    BlockPos pos = new BlockPos(cx + dx, cy + dy, cz + dz);
                    if (revealed.contains(pos)) continue;
                    BlockState state = level.getBlockState(pos);
                    if (state.getBlock() instanceof OreBlock) {
                        revealed.add(pos);
                        player.connection.send(new ClientboundBlockUpdatePacket(pos, state));
                    }
                }
            }
        }

        // Periodic re-hide: hide ores that left range / 周期性重伪装
        if (doFullScan && !revealed.isEmpty()) {
            Iterator<BlockPos> it = revealed.iterator();
            while (it.hasNext()) {
                BlockPos pos = it.next();
                if (Math.abs(pos.getX() - cx) > REVEAL_RADIUS
                        || Math.abs(pos.getY() - cy) > REVEAL_RADIUS_Y
                        || Math.abs(pos.getZ() - cz) > REVEAL_RADIUS) {
                    BlockState real = level.getBlockState(pos);
                    if (real != null && real.getBlock() instanceof OreBlock) {
                        player.connection.send(
                                new ClientboundBlockUpdatePacket(pos,
                                        pos.getY() < 0 ? DISGUISE_DEEPSLATE : DISGUISE_STONE));
                    }
                    it.remove();
                }
            }
        }
    }
}
