package com.endlessepoch.core.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;
import java.util.List;

public record GridStorageUpdatePacket(List<ItemStack> items) implements CustomPacketPayload {
    public static final Type<GridStorageUpdatePacket> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("eecore", "grid_storage_update"));
    public static final StreamCodec<RegistryFriendlyByteBuf, GridStorageUpdatePacket> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.collection(ArrayList::new, ItemStack.OPTIONAL_STREAM_CODEC),
            GridStorageUpdatePacket::items,
            GridStorageUpdatePacket::new);
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(GridStorageUpdatePacket pkt, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            var mc = net.minecraft.client.Minecraft.getInstance();
            if (mc.screen instanceof com.endlessepoch.core.screen.DispatchScreen ds) {
                ds.onGridUpdate(pkt.items());
            }
        });
    }
}
