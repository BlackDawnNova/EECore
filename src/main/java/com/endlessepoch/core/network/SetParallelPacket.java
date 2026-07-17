package com.endlessepoch.core.network;

import com.endlessepoch.core.EECore;
import com.endlessepoch.core.nova.block.part.CreativeParallelHatchBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * C2S: confirm the typed parallel value on a creative parallel hatch.
 * Server clamps to [16, 16384] and validates distance + BE type.
 * C2S：确认创造并行仓输入的并行数。服务端钳位 [16, 16384] 并校验距离与 BE 类型。
 */
public record SetParallelPacket(BlockPos pos, int value) implements CustomPacketPayload {
    public static final Type<SetParallelPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(EECore.MOD_ID, "set_parallel"));
    public static final StreamCodec<RegistryFriendlyByteBuf, SetParallelPacket> CODEC =
            new StreamCodec<>() {
                public SetParallelPacket decode(RegistryFriendlyByteBuf b) {
                    return new SetParallelPacket(b.readBlockPos(), b.readVarInt());
                }
                public void encode(RegistryFriendlyByteBuf b, SetParallelPacket p) {
                    b.writeBlockPos(p.pos); b.writeVarInt(p.value);
                }
            };

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(SetParallelPacket pkt, IPayloadContext ctx) {
        var player = ctx.player();
        if (player == null || player.level() == null) return;
        if (player.distanceToSqr(pkt.pos.getX() + .5, pkt.pos.getY() + .5, pkt.pos.getZ() + .5) > 64) return;
        if (player.level().getBlockEntity(pkt.pos) instanceof CreativeParallelHatchBlockEntity ph)
            ph.setParallelValue(pkt.value);
    }
}
