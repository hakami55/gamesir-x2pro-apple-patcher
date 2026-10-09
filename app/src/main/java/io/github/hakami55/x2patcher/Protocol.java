package io.github.hakami55.x2patcher;

import java.io.IOException;
import java.security.MessageDigest;
import java.util.Arrays;

/** The verified legacy MAIN protocol. No PD/slave commands or arbitrary image support. */
public final class Protocol {
    private Protocol() {}
    public static final int SIZE = 33388;
    public static final int BLOCK = 60;
    public static final String STOCK_SHA = "5ff4c283062edb95c63ff1fa8f0cb2a90568089cc464ce08b803558c6b76567a";
    public static final String PATCH_SHA = "f48477d9abb2a1db369b66103b46533060bf01d0b0c7262758cb7b5756e7da18";
    public static final byte[] VENDOR_REPORT = unhex("0600ff0900a101150025ff750895400900810209009102c0");

    public interface Transport { byte[] exchange(byte[] command, int timeoutMillis) throws Exception; }
    public interface Progress { void update(int verifiedBytes, int totalBytes) throws Exception; }

    public static String sha(byte[] data) {
        try { return hex(MessageDigest.getInstance("SHA-256").digest(data)); }
        catch (Exception e) { throw new IllegalStateException(e); }
    }
    public static String hex(byte[] bytes) {
        StringBuilder b = new StringBuilder();
        for (byte x : bytes) b.append(String.format(java.util.Locale.ROOT, "%02x", x & 255));
        return b.toString();
    }
    public static byte[] unhex(String h) {
        if ((h.length() & 1) != 0) throw new IllegalArgumentException("Odd hex length");
        byte[] data = new byte[h.length()/2];
        for (int i=0;i<data.length;i++) data[i]=(byte)Integer.parseInt(h.substring(i*2,i*2+2),16);
        return data;
    }
    public static void validateImage(byte[] bytes, boolean patched) throws IOException {
        if (bytes.length != SIZE || !sha(bytes).equals(patched ? PATCH_SHA : STOCK_SHA))
            throw new IOException("Firmware integrity check failed. Nothing will be written.");
    }
    public static void validatePair(byte[] stock, byte[] patch) throws IOException {
        validateImage(stock,false); validateImage(patch,true);
        int count=0;
        for (int i=0;i<SIZE;i++) if(stock[i]!=patch[i]) {
            boolean allowed=i==0x6536 || (i>=0x653a && i<=0x653f)
                || i==0x64a8 || i==0x64ae || (i>=0x64b1 && i<=0x64ca);
            if(!allowed) throw new IOException("Patch changes an unexpected address.");
            count++;
        }
        if(count!=29) throw new IOException("Patch difference count is not 29.");
    }
    public static int validateVersions(byte[] main, byte[] slave, byte[] mode) throws IOException {
        if(main.length<20 || (main[0]&255)!=0xc9 || (main[12]&255)!=129 || main[11]!=24
                || main[8]!=2 || main[7]!=0)
            throw new IOException("Unsupported controller: requires MAIN 129.24 and hardware 2.0. No update started.");
        if(slave.length<4 || slave[0]!=0x20 || (slave[1]&255)!=0x9f || (slave[2]&255)!=129 || slave[3]!=3)
            throw new IOException("Unsupported slave firmware: requires 129.3. No update started.");
        if(mode.length<2 || mode[0]!=9 || mode[1]!=3)
            throw new IOException("This release requires controller mode 3. No mode changes were sent.");
        return mode[1];
    }
    public static byte[] block(byte[] image,int offset,byte opcode) {
        if(image.length!=SIZE || offset<0 || offset>=SIZE || offset%BLOCK!=0 || (opcode!=(byte)0x80 && opcode!=(byte)0x82))
            throw new IllegalArgumentException("Invalid firmware block");
        int length=Math.min(BLOCK,SIZE-offset);
        byte[] p=new byte[length+4];
        p[0]=opcode;p[1]=(byte)length;p[2]=(byte)offset;p[3]=(byte)(offset>>8);
        System.arraycopy(image,offset,p,4,length);return p;
    }
    public static void acknowledge(byte[] reply,int opcode) throws IOException {
        if(reply.length<2 || (reply[0]&255)!=opcode || reply[1]!=0)
            throw new IOException("Update stopped: expected "+String.format("%02x 00",opcode)+", received "+hex(reply));
    }
    public static void flash(Transport t,byte[] supplied,boolean patched,Progress progress) throws Exception {
        // Freeze input so a caller cannot change bytes after the hash check.
        byte[] image=Arrays.copyOf(supplied,supplied.length);
        validateImage(image,patched);
        acknowledge(t.exchange(new byte[]{(byte)0x81},5000),0x81);
        for(int offset=0;offset<SIZE;offset+=BLOCK) {
            acknowledge(t.exchange(block(image,offset,(byte)0x80),2500),0x80);
            acknowledge(t.exchange(block(image,offset,(byte)0x82),2500),0x82);
            progress.update(Math.min(SIZE,offset+BLOCK),SIZE);
        }
        acknowledge(t.exchange(new byte[]{(byte)0x83},5000),0x83);
    }
}
