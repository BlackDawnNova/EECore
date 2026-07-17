package com.endlessepoch.core.network;

import com.endlessepoch.core.EECore;
import com.endlessepoch.core.nova.block.part.CreativeBusBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * C2S: set the per-slot template count on a creative input bus/assembly.
 * Server clamps to [1, 1,000,000] and validates distance + BE type.
 * C2S：设置创造输入总线/总成的单槽模板数量。服务端钳位 [1, 100万] 并校验距离与 BE 类型。
 */
public record SetGhostCountPacket(BlockPos pos, int slot, int count) implements CustomPacketPayload {
    public static final Type<SetGhostCountPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(EECore.MOD_ID, "set_ghost_count"));
    public static final StreamCodec<RegistryFriendlyByteBuf, SetGhostCountPacket> CODEC =
            new StreamCodec<>() {
                public SetGhostCountPacket decode(RegistryFriendlyByteBuf b) {
                    return new SetGhostCountPacket(b.readBlockPos(), b.readVarInt(), b.readVarInt());
                }
                public void encode(RegistryFriendlyByteBuf b, SetGhostCountPacket p) {
                    b.writeBlockPos(p.pos); b.writeVarInt(p.slot); b.writeVarInt(p.count);
                }
            };

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(SetGhostCountPacket pkt, IPayloadContext ctx) {
        var player = ctx.player();
        if (player == null || player.level() == null) return;
        if (player.distanceToSqr(pkt.pos.getX() + .5, pkt.pos.getY() + .5, pkt.pos.getZ() + .5) > 64) return;
        if (player.level().getBlockEntity(pkt.pos) instanceof CreativeBusBlockEntity cb)
            cb.setTemplateCount(pkt.slot, pkt.count);
    }
}
