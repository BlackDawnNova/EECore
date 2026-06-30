package com.endlessepoch.core.network;

import com.endlessepoch.core.EECore;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.math.BigInteger;

public record SyncGeneratorPacket(
        BlockPos pos,
        String tierName,
        BigInteger output,
        BigInteger amperage,
        boolean enabled
) implements CustomPacketPayload {

    public static final Type<SyncGeneratorPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(EECore.MOD_ID, "sync_generator"));

    public static final StreamCodec<FriendlyByteBuf, SyncGeneratorPacket> STREAM_CODEC =
            StreamCodec.composite(
                    BlockPos.STREAM_CODEC,
                    SyncGeneratorPacket::pos,
                    ByteBufCodecs.STRING_UTF8,
                    SyncGeneratorPacket::tierName,
                    NetworkCodecs.BIG_INTEGER,
                    SyncGeneratorPacket::output,
                    NetworkCodecs.BIG_INTEGER,
                    SyncGeneratorPacket::amperage,
                    ByteBufCodecs.BOOL,
                    SyncGeneratorPacket::enabled,
                    SyncGeneratorPacket::new
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}