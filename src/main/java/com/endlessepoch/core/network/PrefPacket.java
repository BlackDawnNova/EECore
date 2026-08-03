package com.endlessepoch.core.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * S2C: restore sort mode / direction / display mode when the menu opens.
 * 菜单打开时恢复玩家排序模式/升降序/显示类型。
 */
public record PrefPacket(int sortMode, int sortAsc, int displayMode) implements CustomPacketPayload {

    public static final Type<PrefPacket> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("eecore", "pref"));

    public static final StreamCodec<RegistryFriendlyByteBuf, PrefPacket> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, PrefPacket::sortMode,
            ByteBufCodecs.VAR_INT, PrefPacket::sortAsc,
            ByteBufCodecs.VAR_INT, PrefPacket::displayMode,
            PrefPacket::new);

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
