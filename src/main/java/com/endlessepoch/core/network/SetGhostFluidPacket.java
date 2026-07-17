package com.endlessepoch.core.network;

import com.endlessepoch.core.EECore;
import com.endlessepoch.core.nova.block.part.CreativeHatchBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * C2S: set/clear a fluid template tank on a creative fluid input hatch (JEI ghost drag).
 * Server validates distance and the target BE type before applying.
 * C2S：设置/清除创造流体输入仓的流体模板（JEI 拖拽）。服务端校验距离与目标 BE 类型。
 */
public record SetGhostFluidPacket(BlockPos pos, int tankIdx, ResourceLocation fluidId) implements CustomPacketPayload {
    public static final Type<SetGhostFluidPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(EECore.MOD_ID, "set_ghost_fluid"));
    public static final StreamCodec<RegistryFriendlyByteBuf, SetGhostFluidPacket> CODEC =
            new StreamCodec<>() {
                public SetGhostFluidPacket decode(RegistryFriendlyByteBuf b) {
                    return new SetGhostFluidPacket(b.readBlockPos(), b.readVarInt(),
                            b.readBoolean() ? b.readResourceLocation() : null);
                }
                public void encode(RegistryFriendlyByteBuf b, SetGhostFluidPacket p) {
                    b.writeBlockPos(p.pos); b.writeVarInt(p.tankIdx);
                    b.writeBoolean(p.fluidId != null);
                    if (p.fluidId != null) b.writeResourceLocation(p.fluidId);
                }
            };

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(SetGhostFluidPacket pkt, IPayloadContext ctx) {
        var player = ctx.player();
        if (player == null || player.level() == null) return;
        if (player.distanceToSqr(pkt.pos.getX() + .5, pkt.pos.getY() + .5, pkt.pos.getZ() + .5) > 64) return;
        var fluid = pkt.fluidId == null ? null : BuiltInRegistries.FLUID.get(pkt.fluidId);
        if (fluid == Fluids.EMPTY) return;
        var be = player.level().getBlockEntity(pkt.pos);
        if (be instanceof CreativeHatchBlockEntity ch) ch.setFluidTemplate(pkt.tankIdx, fluid);
        else if (be instanceof com.endlessepoch.core.nova.block.part.CreativeBusBlockEntity cb)
            cb.setFluidTemplate(pkt.tankIdx, fluid); // creative assembly / 创造总成
    }
}
