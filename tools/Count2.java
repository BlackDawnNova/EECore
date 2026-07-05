import java.io.*;
import java.nio.file.*;
import java.util.zip.*;

public class Count2 {
    public static void main(String[] args) throws Exception {
        var body = decompress(Files.readAllBytes(Path.of(args[0])));
        var buf = java.nio.ByteBuffer.wrap(body);
        int w=readV(buf), h=readV(buf), d=readV(buf);
        readV(buf);readV(buf);readV(buf); // skip ctrl
        int palSize = readV(buf);
        for (int i=0;i<palSize;i++) {
            buf.get(); // char
            int idl = readV(buf); buf.position(buf.position()+idl);
            int tc = readV(buf);
            for (int t=0;t<tc;t++) { int tl=readV(buf); buf.position(buf.position()+tl); }
        }
        int mode = buf.get() & 0xFF;
        int nonAir = readV(buf);
        System.out.println("Non-air blocks: " + nonAir);
        System.out.println("Total volume: " + ((long)w*h*d));
        System.out.printf("Density: %.2f%%\n", 100.0*nonAir/(w*h*d));
    }
    static byte[] decompress(byte[] file) throws Exception {
        boolean comp = (file[5] & 2) != 0;
        int hdr=6, tail=4, len=file.length-hdr-tail;
        if (!comp) { byte[] b=new byte[len]; System.arraycopy(file,hdr,b,0,len); return b; }
        var out = new ByteArrayOutputStream();
        var inf = new InflaterInputStream(new ByteArrayInputStream(file, hdr, len));
        byte[] buf=new byte[8192]; int n;
        while((n=inf.read(buf))>0) out.write(buf,0,n);
        return out.toByteArray();
    }
    static int readV(java.nio.ByteBuffer b) {
        int r=0,s=0; byte v;
        do { v=b.get(); r|=(v&0x7F)<<s; s+=7; } while((v&0x80)!=0);
        return r;
    }
}
