package com.endlessepoch.core.network;

import com.endlessepoch.core.EECore;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.*;

/** Server→client: missing/wrong block positions after multiblock validation / 服务器→客户端: 多方块验证缺失/错误方块位置 */
public record SyncValidationPacket(
    ResourceLocation patternId,
    int[] missingLocal, int[] missingWorld,
    int[] wrongLocal, int[] wrongWorld,
    int controllerX, int controllerY, int controllerZ,
    int width, int height, int depth,
    boolean postFormation
) implements CustomPacketPayload {

    public static final Type<SyncValidationPacket> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(EECore.MOD_ID, "sync_validation"));

    public static final StreamCodec<FriendlyByteBuf, SyncValidationPacket> STREAM_CODEC = StreamCodec.of(
        (buf, pkt) -> {
            buf.writeResourceLocation(pkt.patternId);
            buf.writeInt(pkt.controllerX); buf.writeInt(pkt.controllerY); buf.writeInt(pkt.controllerZ);
            buf.writeInt(pkt.width); buf.writeInt(pkt.height); buf.writeInt(pkt.depth);
            buf.writeBoolean(pkt.postFormation);
            buf.writeVarIntArray(pkt.missingLocal); buf.writeVarIntArray(pkt.missingWorld);
            buf.writeVarIntArray(pkt.wrongLocal); buf.writeVarIntArray(pkt.wrongWorld);
        },
        buf -> {
            var id = buf.readResourceLocation();
            int cx = buf.readInt(), cy = buf.readInt(), cz = buf.readInt();
            int w = buf.readInt(), h = buf.readInt(), d = buf.readInt();
            boolean pf = buf.readBoolean();
            return new SyncValidationPacket(id,
                buf.readVarIntArray(), buf.readVarIntArray(),
                buf.readVarIntArray(), buf.readVarIntArray(),
                cx, cy, cz, w, h, d, pf);
        }
    );

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public List<BlockPos> missingWorldPositions() { return toPosList(missingWorld); }
    public List<BlockPos> missingLocalPositions() { return toPosList(missingLocal); }
    public List<BlockPos> wrongWorldPositions() { return toPosList(wrongWorld); }
    public List<BlockPos> wrongLocalPositions() { return toPosList(wrongLocal); }

    private static List<BlockPos> toPosList(int[] flat) {
        List<BlockPos> list = new ArrayList<>();
        for (int i = 0; i + 2 < flat.length; i += 3)
            list.add(new BlockPos(flat[i], flat[i+1], flat[i+2]));
        return list;
    }

    public static void handle(SyncValidationPacket pkt, IPayloadContext ctx) {
        ctx.enqueueWork(() -> com.endlessepoch.core.nova.client.WorldPreviewManager.get().updateValidation(pkt));
    }
}
