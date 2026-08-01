package com.endlessepoch.core.network;

import com.endlessepoch.core.nova.block.DispatchCenterBlockEntity;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record SetGridDensityPacket(BlockPos pos, int gridRows) implements CustomPacketPayload {
    public static final Type<SetGridDensityPacket> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("eecore", "set_grid_density"));
    public static final StreamCodec<ByteBuf, SetGridDensityPacket> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, SetGridDensityPacket::pos,
            ByteBufCodecs.VAR_INT, SetGridDensityPacket::gridRows,
            SetGridDensityPacket::new);
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(SetGridDensityPacket pkt, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (ctx.player() instanceof ServerPlayer sp) {
                var be = sp.level().getBlockEntity(pkt.pos());
                if (be instanceof DispatchCenterBlockEntity dc) {
                    dc.playerDensities.put(sp.getUUID(), pkt.gridRows());
                    var def = com.endlessepoch.core.api.multiblock.MachineRegistry.get(dc.getMachineId());
                    String en = def.map(d -> d.getNameEn()).orElse("Dispatch Center");
                    String zh = def.map(d -> d.getNameZh()).orElse("调度中心");
                    sp.closeContainer();
                    sp.openMenu(dc, buf -> { buf.writeBlockPos(pkt.pos()); buf.writeVarInt(pkt.gridRows()); buf.writeUtf(en); buf.writeUtf(zh); });
                }
            }
        });
    }
}
