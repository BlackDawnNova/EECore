package com.endlessepoch.core.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * C2S: persist sort mode / direction / display mode per player (NBT).
 * 保存玩家排序模式/升降序/显示类型到 NBT。
 */
public record SetPrefPacket(int sortMode, int sortAsc, int displayMode) implements CustomPacketPayload {

    public static final Type<SetPrefPacket> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("eecore", "set_pref"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SetPrefPacket> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, SetPrefPacket::sortMode,
            ByteBufCodecs.VAR_INT, SetPrefPacket::sortAsc,
            ByteBufCodecs.VAR_INT, SetPrefPacket::displayMode,
            SetPrefPacket::new);

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
