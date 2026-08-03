package com.endlessepoch.core.network;

import appeng.api.stacks.AEKey;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * C2S grid interaction: extract to cursor (0 stack / 1 one), delete whole key
 * (2, trash), extract all to inventory (3), insert carried into network (4).
 * 网格交互包：提取到鼠标(0 一组/1 一个)、整项删除(2 垃圾桶)、全部到背包(3)、
 * 携带物品放入网络(4)。
 */
public record GridClickPacket(AEKey key, long amount, int mode) implements CustomPacketPayload {

    public static final Type<GridClickPacket> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("eecore", "grid_click"));

    private static final StreamCodec<RegistryFriendlyByteBuf, Long> VAR_LONG =
            StreamCodec.of((buf, v) -> buf.writeVarLong(v), buf -> buf.readVarLong());

    public static final StreamCodec<RegistryFriendlyByteBuf, GridClickPacket> STREAM_CODEC = StreamCodec.composite(
            AEKey.STREAM_CODEC, GridClickPacket::key,
            VAR_LONG, GridClickPacket::amount,
            ByteBufCodecs.VAR_INT, GridClickPacket::mode,
            (key, amount, mode) -> new GridClickPacket(key, amount, mode));

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
