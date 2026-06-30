package com.endlessepoch.core.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;

import java.math.BigInteger;

/**
 * EECore custom network codecs.
 * EECore 网络自定义编解码器。
 * ByteBufCodecs in NeoForge 1.21.1 lacks BIG_INTEGER, so we provide a binary codec.
 * NeoForge 1.21.1 的 ByteBufCodecs 不含 BIG_INTEGER，这里提供二进制版本的 codec。
 */
public final class NetworkCodecs {

    /**
     * BigInteger binary codec.
     * BigInteger 二进制编解码器。
     * Uses BigInteger.toByteArray() compact binary format, ~60% bandwidth saving over String.
     * 使用 BigInteger.toByteArray() 紧凑二进制格式，比 String 节省约 60% 带宽 (WHY: bandwidth saving).
     */
    public static final StreamCodec<FriendlyByteBuf, BigInteger> BIG_INTEGER =
            StreamCodec.of(
                    (buf, val) -> buf.writeByteArray(val.toByteArray()),
                    buf -> new BigInteger(buf.readByteArray())
            );

    private NetworkCodecs() {}
}
