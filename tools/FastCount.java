import java.io.*;
import java.nio.file.*;
import java.util.zip.*;

public class FastCount {
    public static void main(String[] args) throws Exception {
        byte[] file = Files.readAllBytes(Path.of(args[0]));
        int flg = file[5] & 0xFF;
        boolean compressed = (flg & 2) != 0; // FLAG_COMPRESSED = 2
        byte[] body;
        if (compressed) {
            byte[] zipped = new byte[file.length - 10]; // -magic(4)-ver(1)-flags(1)-crc(4)
            System.arraycopy(file, 6, zipped, 0, zipped.length);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            InflaterInputStream inf = new InflaterInputStream(new ByteArrayInputStream(zipped));
            byte[] buf = new byte[4096]; int n;
            while ((n = inf.read(buf)) > 0) out.write(buf, 0, n);
            body = out.toByteArray();
        } else {
            body = new byte[file.length - 10];
            System.arraycopy(file, 6, body, 0, body.length);
        }
        DataInputStream in = new DataInputStream(new ByteArrayInputStream(body));
        int w=rv(in), h=rv(in), d=rv(in);
        // skip ctrl
        rv(in); rv(in); rv(in);
        // skip palette
        int ps = rv(in);
        for (int i = 0; i < ps; i++) {
            in.readByte(); // char
            int idl = rv(in); in.skipBytes(idl);
            int tc = rv(in);
            for (int t = 0; t < tc; t++) { int tl = rv(in); in.skipBytes(tl); }
        }
        int mode = in.readByte() & 0xFF;
        int nonAir = rv(in);
        System.out.println("Non-air blocks: " + nonAir);
    }
    static int rv(DataInputStream in) throws IOException {
        int r=0, s=0, b;
        do { b=in.readByte(); r|=(b&0x7F)<<s; s+=7; } while((b&0x80)!=0);
        return r;
    }
}
