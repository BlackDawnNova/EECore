package com.endlessepoch.core.network;

import appeng.api.stacks.AEKey;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

/**
 * Event-driven grid storage sync (S2C): only changed keys, never full snapshots
 * after the first packet. count=0 marks removal. Drives DispatchScreen's main grid.
 * 事件驱动网格存储增量同步（S2C）：只发变化的 key，首包后不再全量。count=0 表示移除。
 */
public record GridIncrementalUpdatePacket(List<Entry> entries, boolean fullUpdate) implements CustomPacketPayload {

    public record Entry(AEKey key, long count) {}

    public static final Type<GridIncrementalUpdatePacket> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("eecore", "grid_incremental_update"));

    private static final StreamCodec<RegistryFriendlyByteBuf, Boolean> BOOL =
            StreamCodec.of((buf, v) -> buf.writeBoolean(v), buf -> buf.readBoolean());
    private static final StreamCodec<RegistryFriendlyByteBuf, Long> VAR_LONG =
            StreamCodec.of((buf, v) -> buf.writeVarLong(v), buf -> buf.readVarLong());

    private static final StreamCodec<RegistryFriendlyByteBuf, Entry> ENTRY_CODEC = StreamCodec.composite(
            AEKey.STREAM_CODEC, Entry::key,
            VAR_LONG, Entry::count,
            Entry::new);

    private static final StreamCodec<RegistryFriendlyByteBuf, List<Entry>> LIST_CODEC = new StreamCodec<>() {
        @Override public List<Entry> decode(RegistryFriendlyByteBuf buf) {
            int n = buf.readVarInt();
            var list = new ArrayList<Entry>(n);
            for (int i = 0; i < n; i++) list.add(ENTRY_CODEC.decode(buf));
            return list;
        }
        @Override public void encode(RegistryFriendlyByteBuf buf, List<Entry> v) {
            buf.writeVarInt(v.size());
            for (var e : v) ENTRY_CODEC.encode(buf, e);
        }
    };

    public static final StreamCodec<RegistryFriendlyByteBuf, GridIncrementalUpdatePacket> STREAM_CODEC = StreamCodec.composite(
            BOOL, GridIncrementalUpdatePacket::fullUpdate,
            LIST_CODEC, GridIncrementalUpdatePacket::entries,
            (full, entries) -> new GridIncrementalUpdatePacket(entries, full));

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
