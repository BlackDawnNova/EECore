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
        sb.append(String.format("Size: %dx%dx%d  Ctrl: (%d,%d,%d)  Block types: %d\n",
                data.width, data.height, data.depth,
                data.controllerX, data.controllerY, data.controllerZ,
                data.palette.size()));

        sb.append("\n--- Tagged Entries ---\n");
        int tagged = 0;
        for (int i = 0; i < data.palette.size(); i++) {
            EcsPaletteEntry e = data.palette.get(i);
            if (e.tags().isEmpty()) continue;
            tagged++;
            sb.append(String.format("  '%c' (0x%02X) → %s  [%s]\n",
                    e.character(), (int)e.character(), e.blockId(), String.join(", ", e.tags())));
        }
        if (tagged == 0) sb.append("  (none)\n");

        sb.append(String.format("\nPalette entries: %d total, %d tagged\n", data.palette.size(), tagged));

        if (args.length >= 2) { Files.writeString(Path.of(args[1]), sb.toString()); System.out.println("Written: " + args[1]); }
        else System.out.print(sb.toString());
    }
}
