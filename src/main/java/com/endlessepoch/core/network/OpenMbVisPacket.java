package com.endlessepoch.core.network;

import com.endlessepoch.core.EECore;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.*;

public record OpenMbVisPacket(ResourceLocation patternId, boolean readOnly,
                               byte[] ecsBytes, Map<String, List<String>> alternatives) implements CustomPacketPayload {

    public OpenMbVisPacket(ResourceLocation patternId) { this(patternId, false, null); }
    public OpenMbVisPacket(ResourceLocation patternId, boolean readOnly) {
        this(patternId, readOnly, null, null);
    }

    public OpenMbVisPacket(ResourceLocation patternId, boolean readOnly, byte[] ecsBytes) {
        this(patternId, readOnly, ecsBytes, null);
    }

    public static final Type<OpenMbVisPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(EECore.MOD_ID, "open_mbvis"));

    public static final StreamCodec<FriendlyByteBuf, OpenMbVisPacket> STREAM_CODEC =
            new StreamCodec<>() {
                @Override
                public OpenMbVisPacket decode(FriendlyByteBuf buf) {
                    var id = buf.readResourceLocation();
                    boolean ro = buf.readBoolean();
                    boolean hasBytes = buf.readBoolean();
                    byte[] bytes = hasBytes ? buf.readByteArray() : null;
                    boolean hasAlts = buf.readBoolean();
                    Map<String, List<String>> alts = null;
                    if (hasAlts) {
                        int sz = buf.readVarInt();
                        alts = new LinkedHashMap<>();
                        for (int i = 0; i < sz; i++) {
                            String key = buf.readUtf();
                            int cnt = buf.readVarInt();
                            List<String> list = new ArrayList<>();
                            for (int j = 0; j < cnt; j++)
                                list.add(buf.readUtf());
                            alts.put(key, list);
                        }
                    }
                    return new OpenMbVisPacket(id, ro, bytes, alts);
                }

                @Override
                public void encode(FriendlyByteBuf buf, OpenMbVisPacket pkt) {
                    buf.writeResourceLocation(pkt.patternId);
                    buf.writeBoolean(pkt.readOnly);
                    buf.writeBoolean(pkt.ecsBytes != null);
                    if (pkt.ecsBytes != null) buf.writeByteArray(pkt.ecsBytes);
                    buf.writeBoolean(pkt.alternatives != null);
                    if (pkt.alternatives != null) {
                        buf.writeVarInt(pkt.alternatives.size());
                        for (var e : pkt.alternatives.entrySet()) {
                            buf.writeUtf(e.getKey());
                            buf.writeVarInt(e.getValue().size());
                            for (String v : e.getValue()) buf.writeUtf(v);
                        }
                    }
                }
            };

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
