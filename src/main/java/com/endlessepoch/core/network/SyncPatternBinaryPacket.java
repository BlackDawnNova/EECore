package com.endlessepoch.core.network;

import com.endlessepoch.core.EECore;
import com.endlessepoch.core.api.multiblock.EECoreCodec;
import com.endlessepoch.core.api.multiblock.MultiBlockPattern;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Binary pattern sync — single byte blob instead of per-row UTF-8. / 二进制结构同步包 */
public record SyncPatternBinaryPacket(ResourceLocation patternId, byte[] ecsData) implements CustomPacketPayload {
    public static final Type<SyncPatternBinaryPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(EECore.MOD_ID, "sync_pattern_bin"));

    public static final StreamCodec<FriendlyByteBuf, SyncPatternBinaryPacket> STREAM_CODEC = StreamCodec.of(
            (buf, pkt) -> { buf.writeResourceLocation(pkt.patternId); buf.writeByteArray(pkt.ecsData); },
            buf -> new SyncPatternBinaryPacket(buf.readResourceLocation(), buf.readByteArray()));

    public static SyncPatternBinaryPacket fromPattern(ResourceLocation id, MultiBlockPattern pattern) {
        try { return new SyncPatternBinaryPacket(id, EECoreCodec.encode(pattern)); }
        catch (java.io.IOException e) { throw new RuntimeException("Failed to encode pattern", e); }
    }

    public MultiBlockPattern toPattern() {
        try { return EECoreCodec.decode(ecsData); }
        catch (java.io.IOException e) { throw new RuntimeException("Failed to decode pattern", e); }
    }

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
