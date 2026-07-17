package com.endlessepoch.core.network;

import com.endlessepoch.core.EECore;
import com.endlessepoch.core.nova.block.part.CreativeBusBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * C2S: set/clear a phantom template slot on a creative input bus (JEI ghost drag).
 * Server validates distance and the target BE type before applying.
 * C2S：设置/清除创造输入总线的幻影模板槽（JEI 拖拽）。服务端校验距离与目标 BE 类型。
 */
public record SetGhostSlotPacket(BlockPos pos, int slot, ResourceLocation itemId) implements CustomPacketPayload {
    public static final Type<SetGhostSlotPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(EECore.MOD_ID, "set_ghost_slot"));
    public static final StreamCodec<RegistryFriendlyByteBuf, SetGhostSlotPacket> CODEC =
            new StreamCodec<>() {
                public SetGhostSlotPacket decode(RegistryFriendlyByteBuf b) {
                    return new SetGhostSlotPacket(b.readBlockPos(), b.readVarInt(),
                            b.readBoolean() ? b.readResourceLocation() : null);
                }
                public void encode(RegistryFriendlyByteBuf b, SetGhostSlotPacket p) {
                    b.writeBlockPos(p.pos); b.writeVarInt(p.slot);
                    b.writeBoolean(p.itemId != null);
                    if (p.itemId != null) b.writeResourceLocation(p.itemId);
                }
            };

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(SetGhostSlotPacket pkt, IPayloadContext ctx) {
        var player = ctx.player();
        if (player == null || player.level() == null) return;
        if (player.distanceToSqr(pkt.pos.getX() + .5, pkt.pos.getY() + .5, pkt.pos.getZ() + .5) > 64) return;
        if (!(player.level().getBlockEntity(pkt.pos) instanceof CreativeBusBlockEntity cb)) return;
        if (pkt.itemId == null) {
            cb.setTemplate(pkt.slot, ItemStack.EMPTY);
            return;
        }
        var item = BuiltInRegistries.ITEM.get(pkt.itemId);
        if (item != net.minecraft.world.item.Items.AIR) cb.setTemplate(pkt.slot, new ItemStack(item));
    }
}
