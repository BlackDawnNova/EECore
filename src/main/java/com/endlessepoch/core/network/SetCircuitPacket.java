package com.endlessepoch.core.network;

import com.endlessepoch.core.nova.block.part.InputBusBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record SetCircuitPacket(BlockPos pos, int value) implements CustomPacketPayload {
    public static final Type<SetCircuitPacket> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("eecore", "set_circuit"));
    public static final StreamCodec<FriendlyByteBuf, SetCircuitPacket> CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, SetCircuitPacket::pos,
            ByteBufCodecs.VAR_INT, SetCircuitPacket::value,
            SetCircuitPacket::new);
    @Override public Type<SetCircuitPacket> type() { return TYPE; }

    public static void handle(SetCircuitPacket p, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            var player = (ServerPlayer) ctx.player();
            if (player.distanceToSqr(p.pos.getX()+.5, p.pos.getY()+.5, p.pos.getZ()+.5) > 64) return;
            var be = player.level().getBlockEntity(p.pos);
            if (be instanceof InputBusBlockEntity bus) bus.setCircuitValue(p.value);
        });
    }
}
