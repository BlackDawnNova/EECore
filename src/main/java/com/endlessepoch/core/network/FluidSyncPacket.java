package com.endlessepoch.core.network;

import com.endlessepoch.core.EECore;
import net.minecraft.core.BlockPos; import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf; import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Sync fluid tank data from server to client after interaction. / 流体数据即时同步包。 */
public record FluidSyncPacket(BlockPos pos, int tankIdx, ResourceLocation fluidId, int amount, int capacity) implements CustomPacketPayload {
    public static final Type<FluidSyncPacket> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(EECore.MOD_ID,"fluid_sync"));
    public static final StreamCodec<RegistryFriendlyByteBuf,FluidSyncPacket> CODEC =
        new StreamCodec<>(){
            public FluidSyncPacket decode(RegistryFriendlyByteBuf b){return FluidSyncPacket.read(b);}
            public void encode(RegistryFriendlyByteBuf b,FluidSyncPacket p){p.write(b);}
        };

    public static FluidSyncPacket read(RegistryFriendlyByteBuf buf){return new FluidSyncPacket(buf.readBlockPos(),buf.readVarInt(),buf.readBoolean()?buf.readResourceLocation():null,buf.readVarInt(),buf.readVarInt());}
    public void write(RegistryFriendlyByteBuf buf){buf.writeBlockPos(pos);buf.writeVarInt(tankIdx);buf.writeBoolean(fluidId!=null);if(fluidId!=null)buf.writeResourceLocation(fluidId);buf.writeVarInt(amount);buf.writeVarInt(capacity);}

    @Override public Type<? extends CustomPacketPayload> type(){return TYPE;}

    public static void handle(FluidSyncPacket pkt, IPayloadContext ctx){
        Player player=ctx.player();
        if(player!=null){
            if(player.containerMenu instanceof com.endlessepoch.core.menu.HatchMenu m)
                m.setFluidData(pkt.tankIdx,pkt.fluidId,pkt.amount,pkt.capacity);
            else if(player.containerMenu instanceof com.endlessepoch.core.menu.BusMenu m)
                m.setFluidData(pkt.tankIdx,pkt.fluidId,pkt.amount,pkt.capacity);
        }
    }
}
