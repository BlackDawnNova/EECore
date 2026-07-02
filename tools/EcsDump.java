/** CLI tool: decode .ecs to readable text. Uses ecsformat (no MC dependency). */
import com.endlessepoch.ecsformat.*;
import java.io.*;
import java.nio.file.*;

public class EcsDump {
    public static void main(String[] args) throws Exception {
        if (args.length < 1) { System.err.println("Usage: java EcsDump input.ecs [output.txt]"); System.exit(1); }

        EcsRawData data = EcsRawCodec.read(Path.of(args[0]));
        StringBuilder sb = new StringBuilder();

        sb.append("=== ECS Dump ===\n");
        sb.append(String.format("Size: %dx%dx%d  Ctrl: (%d,%d,%d)  Voxels: %d\n",
                data.width, data.height, data.depth,
                data.controllerX, data.controllerY, data.controllerZ,
                data.voxelData.length));

        sb.append("\n--- Palette ---\n");
        for (int i = 0; i < data.palette.size(); i++) {
            EcsPaletteEntry e = data.palette.get(i);
            sb.append(String.format("  [%d] '%c' (0x%02X) → %s", i, e.character(), (int)e.character(), e.blockId()));
            if (!e.tags().isEmpty()) sb.append(" [").append(String.join(", ", e.tags())).append("]");
            sb.append("\n");
        }

        sb.append("\n--- Layers (first 5) ---\n");
        int s = Math.min(5, data.height);
        for (int y = 0; y < s; y++) {
            sb.append(String.format("Y=%d: ", y));
            int zM = Math.min(4, data.depth), xM = Math.min(8, data.width);
            for (int z = 0; z < zM; z++) {
                for (int x = 0; x < xM; x++) {
                    int pi = data.voxelData[y * data.depth * data.width + z * data.width + x] & 0xFF;
                    sb.append(pi < data.palette.size() ? data.palette.get(pi).character() : '?');
                }
                if (z < zM - 1) sb.append('|');
            }
            sb.append("...\n");
        }

        String r = sb.toString();
        if (args.length >= 2) { Files.writeString(Path.of(args[1]), r); System.out.println("Written: " + args[1]); }
        else System.out.println(r);
    }
}
