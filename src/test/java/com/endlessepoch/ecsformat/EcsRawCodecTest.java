package com.endlessepoch.ecsformat;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for EcsRawCodec encode/decode round-trips.
 * EcsRawCodec 编解码往返单元测试。
 */
class EcsRawCodecTest {

    // ── 8-bit palette round trip / 8-bit 调色板往返 ──

    @Test
    void roundTrip_8bit() throws IOException {
        EcsRawData data = buildTestData(200, false);
        byte[] encoded = EcsRawCodec.encode(data);
        EcsRawData decoded = EcsRawCodec.decode(encoded);

        assertRawDataEqual(data, decoded);
    }

    // ── 16-bit palette round trip (>256 entries) / 16-bit 调色板往返 ──

    @Test
    void roundTrip_16bit() throws IOException {
        EcsRawData data = buildTestData(300, true);
        byte[] encoded = EcsRawCodec.encode(data);
        EcsRawData decoded = EcsRawCodec.decode(encoded);

        assertRawDataEqual(data, decoded);
    }

    // ── Compression / 压缩 ──

    @Test
    void roundTrip_compressed() throws IOException {
        EcsRawData data = buildTestData(50, false);
        byte[] encoded = EcsRawCodec.encode(data, true);
        EcsRawData decoded = EcsRawCodec.decode(encoded);

        assertRawDataEqual(data, decoded);
    }

    @Test
    void roundTrip_uncompressed() throws IOException {
        EcsRawData data = buildTestData(50, false);
        byte[] encoded = EcsRawCodec.encode(data, false);
        EcsRawData decoded = EcsRawCodec.decode(encoded);

        assertRawDataEqual(data, decoded);
    }

    // ── Corrupted data / 损坏数据 ──

    @Test
    void decode_corruptedData_throws() {
        byte[] garbage = new byte[]{0x45, 0x45, 0x43, 0x53, 0x03, 0x00, 0x7F, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF};
        assertThrows(Exception.class, () -> EcsRawCodec.decode(garbage));
    }

    // ── Empty voxel data (all air) / 空体素数据 ──

    @Test
    void decode_emptyVoxelData() throws IOException {
        EcsRawData data = buildEmptyData(3, 3, 3);
        byte[] encoded = EcsRawCodec.encode(data);
        EcsRawData decoded = EcsRawCodec.decode(encoded);

        assertEquals(0, decoded.controllerX);
        assertEquals(0, decoded.controllerY);
        assertEquals(0, decoded.controllerZ);
        assertEquals(27, decoded.voxelData.length);
        // All air
        for (short s : decoded.voxelData) {
            assertEquals(EcsFormat.AIR_INDEX, s & 0xFFFF);
        }
    }

    // ── VarInt round trip (implicit via all other tests) / VarInt 往返通过上述测试隐式覆盖 ──

    @Test
    void roundTrip_smallDimensions() throws IOException {
        EcsRawData data = buildTestData(3, false);
        byte[] encoded = EcsRawCodec.encode(data);
        EcsRawData decoded = EcsRawCodec.decode(encoded);

        assertEquals(3, decoded.width);
        assertEquals(4, decoded.height);
        assertEquals(5, decoded.depth);
    }

    // ── Helpers / 辅助方法 ──

    /** Build test data with a mix of air and non-air voxels. */
    private static EcsRawData buildTestData(int paletteSize, boolean force16Bit) {
        int w = 3, h = 4, d = 5;
        int cx = 1, cy = 2, cz = 3;

        List<EcsPaletteEntry> palette = new ArrayList<>();
        palette.add(new EcsPaletteEntry('A', "minecraft:air", List.of()));          // idx 0 = air
        palette.add(new EcsPaletteEntry('K', "eecore:controller", List.of("ctrl"))); // idx 1 = controller
        palette.add(new EcsPaletteEntry('#', "eecore:wildcard", List.of()));         // idx 2 = wildcard

        for (int i = 3; i < paletteSize; i++) {
            char c = (char) ('B' + (i - 3) % 20);
            List<String> tags = i % 5 == 0 ? List.of("tag" + i) : List.of();
            palette.add(new EcsPaletteEntry(c, "mod:block_" + i, tags));
        }

        // Ensure palette size triggers 16-bit path when requested
        int actualSize = force16Bit ? Math.max(paletteSize, 257) : Math.min(paletteSize, 256);
        while (palette.size() < actualSize) {
            int idx = palette.size();
            palette.add(new EcsPaletteEntry((char) (0xA0 + idx % 0x5E), "mod:extra_" + idx, List.of()));
        }

        short[] voxelData = new short[w * h * d];
        for (int i = 0; i < voxelData.length; i++)
            voxelData[i] = (short) EcsFormat.AIR_INDEX;

        // Place some non-air voxels
        voxelData[0] = (short) (3 + 0);                     // block_3
        voxelData[1] = (short) EcsFormat.CONTROLLER_INDEX;  // controller
        voxelData[2] = 3;                                    // block_3
        voxelData[3] = (short) (3 + 5);                     // block_8
        int lastIdx = voxelData.length - 1;
        voxelData[lastIdx] = (short) (3 + 10);              // block_13

        return new EcsRawData(w, h, d, cx, cy, cz, palette, voxelData);
    }

    /** Build data with all-air voxels. */
    private static EcsRawData buildEmptyData(int w, int h, int d) {
        List<EcsPaletteEntry> palette = List.of(
                new EcsPaletteEntry('A', "minecraft:air", List.of())
        );
        short[] voxelData = new short[w * h * d];
        for (int i = 0; i < voxelData.length; i++)
            voxelData[i] = (short) EcsFormat.AIR_INDEX;
        return new EcsRawData(w, h, d, 0, 0, 0, palette, voxelData);
    }

    /** Assert two EcsRawData objects are semantically equal. */
    private static void assertRawDataEqual(EcsRawData expected, EcsRawData actual) {
        assertEquals(expected.width, actual.width, "width mismatch");
        assertEquals(expected.height, actual.height, "height mismatch");
        assertEquals(expected.depth, actual.depth, "depth mismatch");
        assertEquals(expected.controllerX, actual.controllerX, "controllerX mismatch");
        assertEquals(expected.controllerY, actual.controllerY, "controllerY mismatch");
        assertEquals(expected.controllerZ, actual.controllerZ, "controllerZ mismatch");

        assertEquals(expected.palette.size(), actual.palette.size(), "palette size mismatch");
        for (int i = 0; i < expected.palette.size(); i++) {
            EcsPaletteEntry e = expected.palette.get(i);
            EcsPaletteEntry a = actual.palette.get(i);
            assertEquals(e.character(), a.character(), "palette[" + i + "] char mismatch");
            assertEquals(e.blockId(), a.blockId(), "palette[" + i + "] blockId mismatch");
            assertEquals(e.tags().size(), a.tags().size(), "palette[" + i + "] tags size mismatch");
            for (int t = 0; t < e.tags().size(); t++)
                assertEquals(e.tags().get(t), a.tags().get(t), "palette[" + i + "] tag[" + t + "] mismatch");
        }

        assertEquals(expected.voxelData.length, actual.voxelData.length, "voxelData length mismatch");
        int total = expected.width * expected.height * expected.depth;
        for (int i = 0; i < total; i++) {
            int ev = expected.voxelData[i] & 0xFFFF;
            int av = actual.voxelData[i] & 0xFFFF;
            assertEquals(ev, av, "voxelData[" + i + "] mismatch");
        }
    }
}
