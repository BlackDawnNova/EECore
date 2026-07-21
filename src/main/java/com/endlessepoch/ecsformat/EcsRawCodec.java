package com.endlessepoch.ecsformat;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.CRC32;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.InflaterInputStream;
import java.nio.charset.StandardCharsets;

public final class EcsRawCodec {

    public static byte[] encode(EcsRawData data) throws IOException {
        return encode(data, true);
    }

    public static byte[] encode(EcsRawData data, boolean compress) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        baos.write(EcsFormat.MAGIC);
        baos.write(EcsFormat.VERSION);
        byte[] payload = encodePayload(data);
        byte[] body;
        if (compress) {
            ByteArrayOutputStream gzBuf = new ByteArrayOutputStream();
            try (DeflaterOutputStream gz = new DeflaterOutputStream(gzBuf)) {
                gz.write(payload);
            }
            body = gzBuf.toByteArray();
        } else {
            body = payload;
        }
        byte flags = (byte) ((compress ? EcsFormat.FLAG_COMPRESSED : 0)
                | (data.frameBased ? EcsFormat.FLAG_FRAME_BASED : 0));
        baos.write(flags);
        baos.write(body);
        CRC32 crc = new CRC32();
        crc.update(body);
        baos.write(i2b((int) crc.getValue()));
        return baos.toByteArray();
    }

    public static EcsRawData decode(byte[] data) throws IOException {
        DataInputStream in = new DataInputStream(new ByteArrayInputStream(data));
        byte[] magic = new byte[4];
        in.readFully(magic);
        int version = in.readByte() & 0xFF;
        byte flags = in.readByte();
        byte[] body;
        if ((flags & EcsFormat.FLAG_COMPRESSED) != 0) {
            byte[] zippedWithCrc = in.readAllBytes();
            int crcLen = 4;
            byte[] zipped = new byte[zippedWithCrc.length - crcLen];
            System.arraycopy(zippedWithCrc, 0, zipped, 0, zipped.length);
            body = readAll(new InflaterInputStream(new ByteArrayInputStream(zipped)));
        } else {
            byte[] raw = in.readAllBytes();
            body = new byte[raw.length - 4];
            System.arraycopy(raw, 0, body, 0, body.length);
        }
        return decodePayload(body, version, flags);
    }

    public static EcsRawData read(Path path) throws IOException {
        return decode(Files.readAllBytes(path));
    }

    public static void write(Path path, EcsRawData data) throws IOException {
        Files.write(path, encode(data));
    }

    private static byte[] encodePayload(EcsRawData data) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(baos);
        writeVarInt(out, data.width);
        writeVarInt(out, data.height);
        writeVarInt(out, data.depth);
        writeVarInt(out, data.controllerX);
        writeVarInt(out, data.controllerY);
        writeVarInt(out, data.controllerZ);
        writeVarInt(out, data.palette.size());
        // 16-bit mode when palette > 256 entries / 调色板 > 256 时自动切换 16-bit
        boolean use16Bit = data.palette.size() > 256;
        for (EcsPaletteEntry e : data.palette) {
            if (use16Bit) {
                out.writeChar(e.character());
            } else {
                out.writeByte((byte) e.character());
            }
            byte[] idBytes = e.blockId().getBytes(StandardCharsets.UTF_8);
            writeVarInt(out, idBytes.length);
            out.write(idBytes);
            writeVarInt(out, e.tags().size());
            for (String tag : e.tags()) {
                byte[] t = tag.getBytes(StandardCharsets.UTF_8);
                writeVarInt(out, t.length);
                out.write(t);
            }
        }
        int total = data.width * data.height * data.depth;
        int airIdx = EcsFormat.AIR_INDEX;
        int nonAir = 0;
        for (int i = 0; i < total && i < data.voxelData.length; i++)
            if ((data.voxelData[i] & 0xFFFF) != airIdx) nonAir++;
        out.writeByte(use16Bit ? EcsFormat.VOXEL_16BIT_COMPRESSED : EcsFormat.VOXEL_COMPRESSED);
        writeVarInt(out, nonAir);
        for (int i = 0; i < total && i < data.voxelData.length; i++) {
            int pi = data.voxelData[i] & 0xFFFF;
            if (pi != airIdx) {
                writeVarInt(out, i);
                if (use16Bit) {
                    out.writeShort(pi);
                } else {
                    out.writeByte(pi);
                }
            }
        }
        out.flush();
        return baos.toByteArray();
    }

    private static EcsRawData decodePayload(byte[] payload, int version, int flags) throws IOException {
        DataInputStream in = new DataInputStream(new ByteArrayInputStream(payload));
        int w = readVarInt(in), h = readVarInt(in), d = readVarInt(in);
        int cx = readVarInt(in), cy = readVarInt(in), cz = readVarInt(in);
        boolean frameBased = (flags & EcsFormat.FLAG_FRAME_BASED) != 0;
        int palSize = readVarInt(in);
        // palSize > 256 → 16-bit character encoding / 调色板 > 256 判定字符编码宽度
        boolean use16Bit = palSize > 256;
        List<EcsPaletteEntry> palette = new ArrayList<>();
        for (int i = 0; i < palSize; i++) {
            char c = use16Bit ? in.readChar() : (char) (in.readByte() & 0xFF);
            int idLen = readVarInt(in);
            byte[] idBytes = new byte[idLen];
            in.readFully(idBytes);
            String blockId = new String(idBytes, StandardCharsets.UTF_8);
            List<String> tags = new ArrayList<>();
            if (version >= 2) {
                int tagCount = readVarInt(in);
                for (int t = 0; t < tagCount; t++) {
                    int tagLen = readVarInt(in);
                    byte[] tb = new byte[tagLen];
                    in.readFully(tb);
                    tags.add(new String(tb, StandardCharsets.UTF_8));
                }
            }
            palette.add(new EcsPaletteEntry(c, blockId, tags));
        }
        int voxelMode = in.readByte() & 0xFF;
        int total = w * h * d;
        short[] voxelData = new short[total];
        for (int i = 0; i < total; i++) voxelData[i] = (short) EcsFormat.AIR_INDEX;
        if (voxelMode == EcsFormat.VOXEL_COMPRESSED || voxelMode == EcsFormat.VOXEL_16BIT_COMPRESSED) {
            boolean voxel16Bit = (voxelMode == EcsFormat.VOXEL_16BIT_COMPRESSED);
            int nonAir = readVarInt(in);
            for (int i = 0; i < nonAir; i++) {
                int idx = readVarInt(in);
                int pi = voxel16Bit ? (in.readShort() & 0xFFFF) : (in.readByte() & 0xFF);
                if (idx >= 0 && idx < total) voxelData[idx] = (short) pi;
            }
        } else {
            // Legacy uncompressed 8-bit mode / 旧版未压缩 8-bit
            int totalVoxels = readVarInt(in);
            int readLen = Math.min(totalVoxels, total);
            byte[] raw = new byte[readLen];
            in.readFully(raw, 0, readLen);
            for (int i = 0; i < readLen; i++) voxelData[i] = (short) (raw[i] & 0xFF);
        }
        return new EcsRawData(w, h, d, cx, cy, cz, palette, voxelData, frameBased);
    }

    static void writeVarInt(DataOutputStream out, int value) throws IOException {
        while ((value & ~0x7F) != 0) {
            out.writeByte((value & 0x7F) | 0x80);
            value >>>= 7;
        }
        out.writeByte(value & 0x7F);
    }

    static int readVarInt(DataInputStream in) throws IOException {
        int result = 0, shift = 0, b;
        do {
            b = in.readByte();
            result |= (b & 0x7F) << shift;
            shift += 7;
            if (shift > 35) throw new IOException("VarInt too long");
        } while ((b & 0x80) != 0);
        return result;
    }

    private static byte[] i2b(int v) {
        return new byte[]{(byte)(v>>>24),(byte)(v>>>16),(byte)(v>>>8),(byte)v};
    }

    private static byte[] readAll(InputStream in) throws IOException {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        byte[] tmp = new byte[4096];
        int n;
        while ((n = in.read(tmp)) != -1) buf.write(tmp, 0, n);
        return buf.toByteArray();
    }
}
