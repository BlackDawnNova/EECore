package com.endlessepoch.ecsformat;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.zip.CRC32;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * Standalone ECS codec — zero Minecraft dependencies.
 * Encodes/decodes .ecs binary format to/from {@link EcsRawData}.
 * <p>
 * 纯 JDK ECS 编解码器，无 Minecraft 依赖。
 */
public final class EcsRawCodec {

    private EcsRawCodec() {}

    public static byte[] encode(EcsRawData data) throws IOException {
        return encode(data, true);
    }

    public static byte[] encode(EcsRawData data, boolean compress) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        baos.write(EcsFormat.MAGIC);
        baos.write(EcsFormat.VERSION);

        byte flags = 0;
        byte[] payload = encodePayload(data);
        if (compress) flags |= EcsFormat.FLAG_COMPRESSED;

        CRC32 crc = new CRC32();
        crc.update(EcsFormat.MAGIC);
        crc.update(EcsFormat.VERSION);
        crc.update(payload);

        byte[] body;
        if (compress) {
            ByteArrayOutputStream compressed = new ByteArrayOutputStream();
            try (GZIPOutputStream gz = new GZIPOutputStream(compressed)) {
                gz.write(payload);
            }
            body = compressed.toByteArray();
        } else {
            body = payload;
        }

        baos.write(flags);
        baos.write(body);
        baos.write(i2b((int) crc.getValue()));
        return baos.toByteArray();
    }

    public static EcsRawData decode(byte[] data) throws IOException {
        DataInputStream in = new DataInputStream(new ByteArrayInputStream(data));

        byte[] magic = new byte[4];
        in.readFully(magic);
        for (int i = 0; i < 4; i++)
            if (magic[i] != EcsFormat.MAGIC[i])
                throw new IOException("Bad ECS magic");

        int version = in.readByte() & 0xFF;
        if (version < 1 || version > EcsFormat.VERSION)
            throw new IOException("Unsupported ECS version: " + version);

        byte flags = in.readByte();
        boolean compressed = (flags & EcsFormat.FLAG_COMPRESSED) != 0;

        byte[] remaining = in.readAllBytes();
        if (remaining.length < 4) throw new IOException("Truncated ECS");

        byte[] body = new byte[remaining.length - 4];
        System.arraycopy(remaining, 0, body, 0, body.length);
        int storedCrc = b2i(remaining, remaining.length - 4);

        byte[] payload;
        if (compressed) payload = readAll(new GZIPInputStream(new ByteArrayInputStream(body)));
        else payload = body;

        CRC32 crc = new CRC32();
        crc.update(EcsFormat.MAGIC);
        crc.update(version);
        crc.update(payload);
        if ((int) crc.getValue() != storedCrc)
            throw new IOException("ECS checksum mismatch");

        return decodePayload(payload, version);
    }

    public static EcsRawData read(Path path) throws IOException {
        return decode(Files.readAllBytes(path));
    }

    public static void write(Path path, EcsRawData data) throws IOException {
        Files.createDirectories(path.getParent());
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
        for (EcsPaletteEntry e : data.palette) {
            out.writeByte((byte) e.character());
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

        boolean wide = data.palette.size() > 256;
        out.writeByte(wide ? EcsFormat.VOXEL_16BIT : EcsFormat.VOXEL_8BIT);
        writeVarInt(out, data.voxelData.length);
        out.write(data.voxelData);
        out.flush();
        return baos.toByteArray();
    }

    private static EcsRawData decodePayload(byte[] payload, int version) throws IOException {
        DataInputStream in = new DataInputStream(new ByteArrayInputStream(payload));

        int w = readVarInt(in), h = readVarInt(in), d = readVarInt(in);
        int cx = readVarInt(in), cy = readVarInt(in), cz = readVarInt(in);

        int palSize = readVarInt(in);
        List<EcsPaletteEntry> palette = new ArrayList<>();
        for (int i = 0; i < palSize; i++) {
            char c = (char) (in.readByte() & 0xFF);
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

        int totalVoxels = readVarInt(in);
        in.readByte(); // bitsPerVoxel flag, discarded
        byte[] voxelData = new byte[totalVoxels];
        in.readFully(voxelData);

        return new EcsRawData(w, h, d, cx, cy, cz, palette, voxelData);
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
            b = in.readByte() & 0xFF;
            result |= (b & 0x7F) << shift;
            shift += 7;
            if (shift > 35) throw new IOException("VarInt too large");
        } while ((b & 0x80) != 0);
        return result;
    }

    private static byte[] i2b(int v) {
        return new byte[]{(byte)(v>>>24),(byte)(v>>>16),(byte)(v>>>8),(byte)v};
    }

    private static int b2i(byte[] b, int o) {
        return ((b[o]&0xFF)<<24)|((b[o+1]&0xFF)<<16)|((b[o+2]&0xFF)<<8)|(b[o+3]&0xFF);
    }

    static byte[] readAll(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[4096];
        int n;
        while ((n = in.read(buf)) != -1) out.write(buf, 0, n);
        return out.toByteArray();
    }
}
