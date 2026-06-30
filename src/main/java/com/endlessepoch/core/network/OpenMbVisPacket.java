package com.endlessepoch.core.network;

import com.endlessepoch.core.EECore;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record OpenMbVisPacket(ResourceLocation patternId) implements CustomPacketPayload {

    public static final Type<OpenMbVisPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(EECore.MOD_ID, "open_mbvis"));

    public static final StreamCodec<FriendlyByteBuf, OpenMbVisPacket> STREAM_CODEC =
            new StreamCodec<>() {
                @Override
                public OpenMbVisPacket decode(FriendlyByteBuf buf) {
                    return new OpenMbVisPacket(buf.readResourceLocation());
                }

                @Override
                public void encode(FriendlyByteBuf buf, OpenMbVisPacket pkt) {
                    buf.writeResourceLocation(pkt.patternId);
                }
            };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
