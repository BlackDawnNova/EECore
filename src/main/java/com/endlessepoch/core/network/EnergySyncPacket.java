package com.endlessepoch.core.network;

import com.endlessepoch.core.EECore;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Sync energy hatch data from server to client after interaction. / 能源仓数据即时同步包。 */
public record EnergySyncPacket(BlockPos pos, String energyStored, String energyCapacity) implements CustomPacketPayload {
    public static final Type<EnergySyncPacket> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(EECore.MOD_ID, "energy_sync"));
    public static final StreamCodec<RegistryFriendlyByteBuf, EnergySyncPacket> CODEC =
        new StreamCodec<>() {
            public EnergySyncPacket decode(RegistryFriendlyByteBuf b) { return EnergySyncPacket.read(b); }
            public void encode(RegistryFriendlyByteBuf b, EnergySyncPacket p) { p.write(b); }
        };

    public static EnergySyncPacket read(RegistryFriendlyByteBuf buf) {
        return new EnergySyncPacket(buf.readBlockPos(), buf.readUtf(), buf.readUtf());
    }

    public void write(RegistryFriendlyByteBuf buf) {
        buf.writeBlockPos(pos); buf.writeUtf(energyStored); buf.writeUtf(energyCapacity);
    }

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(EnergySyncPacket pkt, IPayloadContext ctx) {
        Player player = ctx.player();
        if (player != null && player.containerMenu instanceof com.endlessepoch.core.menu.HatchMenu m)
            m.setEnergyData(pkt.energyStored, pkt.energyCapacity);
    }
}
