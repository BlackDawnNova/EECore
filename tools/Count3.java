import java.io.*;
import java.nio.file.*;
import java.util.zip.*;

public class Count3 {
    public static void main(String[] args) throws Exception {
        byte[] file = Files.readAllBytes(Path.of(args[0]));
        System.out.println("File size: " + file.length);
        System.out.println("Flags: " + (file[5] & 0xFF));
        int hdr=6, tail=4, compLen=file.length-hdr-tail;
        System.out.println("Compressed body size: " + compLen);
        var inf = new InflaterInputStream(new ByteArrayInputStream(file, hdr, compLen));
        var out = new ByteArrayOutputStream();
        byte[] buf=new byte[8192]; int n, total=0;
        while((n=inf.read(buf))>0) { out.write(buf,0,n); total+=n; }
        byte[] body = out.toByteArray();
        System.out.println("Decompressed body size: " + body.length);
        System.out.println("Total inflated: " + total);
        
        var bb = java.nio.ByteBuffer.wrap(body);
        int w=rv(bb), h=rv(bb), d=rv(bb);
        System.out.printf("w=%d h=%d d=%d%n", w, h, d);
        int cx=rv(bb), cy=rv(bb), cz=rv(bb);
        System.out.printf("ctrl=(%d,%d,%d)%n", cx, cy, cz);
        int palSize=rv(bb);
        System.out.println("palette size: " + palSize);
        // skip palette manually
        for (int i=0;i<palSize;i++) {
            int ch = bb.get() & 0xFF;
            int idl = rv(bb);
            byte[] idb = new byte[idl];
            bb.get(idb);
            int tc = rv(bb);
            for (int t=0;t<tc;t++) {
                int tl = rv(bb);
                byte[] tb = new byte[tl];
                bb.get(tb);
            }
            if (i<3) System.out.printf("  pal[%d]: char=%c idLen=%d tags=%d%n", i, (char)ch, idl, tc);
        }
        int mode = bb.get() & 0xFF;
        System.out.println("voxel mode: " + mode);
        int nonAir = rv(bb);
        System.out.println("Non-air blocks: " + nonAir);
        System.out.printf("Density: %.2f%%%n", 100.0*nonAir/((long)w*h*d));
    }
    static int rv(java.nio.ByteBuffer b) {
        int r=0,s=0; byte v;
        do { v=b.get(); r|=(v&0x7F)<<s; s+=7; } while((v&0x80)!=0);
        return r;
    }
}
