package com.endlessepoch.core.network;

import com.endlessepoch.core.EECore;
import com.endlessepoch.core.api.multiblock.MultiBlockPattern;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.util.*;

/**
 * Syncs a scanned multiblock pattern from server to client (player-local).
 * <p>
 * 将扫描的多方块结构从服务器同步到客户端（玩家本地）。
 */
public record SyncPatternPacket(
        ResourceLocation patternId,
        int width, int height, int depth,
        int controllerX, int controllerY, int controllerZ,
        String[] layerData,
        Map<Character, ResourceLocation> definitions
) implements CustomPacketPayload {

    public static final Type<SyncPatternPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(EECore.MOD_ID, "sync_pattern"));

    public static final StreamCodec<FriendlyByteBuf, SyncPatternPacket> STREAM_CODEC =
            new StreamCodec<>() {
                @Override
                public SyncPatternPacket decode(FriendlyByteBuf buf) {
                    ResourceLocation id = buf.readResourceLocation();
                    int w = buf.readVarInt();
                    int h = buf.readVarInt();
                    int d = buf.readVarInt();
                    int cx = buf.readVarInt();
                    int cy = buf.readVarInt();
                    int cz = buf.readVarInt();

                    int totalLayers = h * d;
                    String[] layerData = new String[totalLayers];
                    for (int i = 0; i < totalLayers; i++) {
                        layerData[i] = buf.readUtf();
                    }

                    int defSize = buf.readVarInt();
                    Map<Character, ResourceLocation> defs = new LinkedHashMap<>();
                    for (int i = 0; i < defSize; i++) {
                        char c = buf.readChar();
                        ResourceLocation blockId = buf.readResourceLocation();
                        defs.put(c, blockId);
                    }

                    return new SyncPatternPacket(id, w, h, d, cx, cy, cz, layerData, defs);
                }

                @Override
                public void encode(FriendlyByteBuf buf, SyncPatternPacket pkt) {
                    buf.writeResourceLocation(pkt.patternId);
                    buf.writeVarInt(pkt.width);
                    buf.writeVarInt(pkt.height);
                    buf.writeVarInt(pkt.depth);
                    buf.writeVarInt(pkt.controllerX);
                    buf.writeVarInt(pkt.controllerY);
                    buf.writeVarInt(pkt.controllerZ);

                    for (String row : pkt.layerData) {
                        buf.writeUtf(row);
                    }

                    buf.writeVarInt(pkt.definitions.size());
                    for (Map.Entry<Character, ResourceLocation> e : pkt.definitions.entrySet()) {
                        buf.writeChar(e.getKey());
                        buf.writeResourceLocation(e.getValue());
                    }
                }
            };

    /**
     * Build a SyncPatternPacket from a MultiBlockPattern + ResourceLocation.
     * Only serializes block IDs (not full states); client uses default states.
     * <p>
     * 从 MultiBlockPattern 和 ResourceLocation 构建 SyncPatternPacket。
     * 仅序列化方块 ID（而非完整状态）；客户端使用默认状态。
     */
    public static SyncPatternPacket fromPattern(ResourceLocation id, MultiBlockPattern pattern) {
        int totalRows = pattern.height * pattern.depth;
        String[] flatLayers = new String[totalRows];
        int idx = 0;
        for (int y = 0; y < pattern.height; y++) {
            for (int z = 0; z < pattern.depth; z++) {
                flatLayers[idx++] = pattern.getLayers()[y][z];
            }
        }

        Map<Character, ResourceLocation> defs = new LinkedHashMap<>();
        for (Map.Entry<Character, BlockState> e : pattern.getDefinitions().entrySet()) {
            defs.put(e.getKey(), BuiltInRegistries.BLOCK.getKey(e.getValue().getBlock()));
        }

        return new SyncPatternPacket(
                id,
                pattern.width, pattern.height, pattern.depth,
                pattern.controllerX, pattern.controllerY, pattern.controllerZ,
                flatLayers, defs
        );
    }

    /**
     * Reconstruct a MultiBlockPattern from this packet.
     * <p>
     * 从此数据包重建 MultiBlockPattern。
     */
    public MultiBlockPattern toPattern() {
        String[][] layers = new String[height][depth];
        int idx = 0;
        for (int y = 0; y < height; y++) {
            for (int z = 0; z < depth; z++) {
                layers[y][z] = layerData[idx++];
            }
        }

        Map<Character, BlockState> stateDefs = new LinkedHashMap<>();
        for (Map.Entry<Character, ResourceLocation> e : definitions.entrySet()) {
            Block block = BuiltInRegistries.BLOCK.get(e.getValue());
            if (block != null) {
                stateDefs.put(e.getKey(), block.defaultBlockState());
            }
        }

        return new MultiBlockPattern(width, height, depth,
                controllerX, controllerY, controllerZ,
                layers, stateDefs);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
