import com.endlessepoch.ecsformat.*;
import java.nio.file.*;
public class ShowPalette {
    public static void main(String[] args) throws Exception {
        EcsRawData d = EcsRawCodec.read(Path.of(args[0]));
        System.out.printf("Size: %dx%dx%d (totalVol=%d)  Ctrl: (%d,%d,%d)  Block types: %d\n\n",
            d.width, d.height, d.depth, (long)d.width*d.height*d.depth,
            d.controllerX, d.controllerY, d.controllerZ, d.palette.size());
        System.out.println("=== Palette ===");
        for (EcsPaletteEntry e : d.palette)
            System.out.printf("  '%c' → %s  %s\n", e.character(), e.blockId(),
                e.tags().isEmpty() ? "" : "[" + String.join(",", e.tags()) + "]");
    }
}
