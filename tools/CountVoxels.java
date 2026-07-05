import com.endlessepoch.ecsformat.*;
import java.nio.file.*;
public class CountVoxels {
    public static void main(String[] args) throws Exception {
        EcsRawData d = EcsRawCodec.read(Path.of(args[0]));
        // Decode voxels from byte array to count non-air blocks
        int count = 0;
        byte[] data = d.voxelData;
        int i = 0;
        while (i < data.length) {
            byte b = data[i++];
            if (b == 0) break; // no more voxels?
            // multi-segment position encoding
            int pos = 0;
            int shift = 0;
            while ((b & 0x80) != 0) {
                pos |= (b & 0x7F) << shift;
                shift += 7;
                if (i >= data.length) break;
                b = data[i++];
            }
            pos |= (b & 0x7F) << shift;
            // palette index
            if (i >= data.length) break;
            b = data[i++];
            int palIdx = 0;
            int shift2 = 0;
            while ((b & 0x80) != 0) {
                palIdx |= (b & 0x7F) << shift2;
                shift2 += 7;
                if (i >= data.length) break;
                b = data[i++];
            }
            palIdx |= (b & 0x7F) << shift2;
            if (palIdx > 0) count++; // skip air (index 0)
        }
        System.out.println("Non-air blocks: " + count);
    }
}
