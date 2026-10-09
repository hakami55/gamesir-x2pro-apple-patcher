package io.github.hakami55.x2patcher;

import org.junit.Test;
import java.nio.file.*;
import java.util.*;
import java.io.IOException;
import static org.junit.Assert.*;

public class ProtocolTest {
    byte[] image(boolean patch) throws Exception {return Files.readAllBytes(Paths.get("src/main/assets/"+(patch?"patched":"official")+"-main.bin"));}
    final byte[] main=Protocol.unhex("c90b1acf00f4a800020a011881e6070904003164");
    final byte[] slave=Protocol.unhex("209f8103"),mode=Protocol.unhex("0903");

    @Test public void realFixturesMatchPinnedHashesAndOnly29AllowedDifferences() throws Exception {Protocol.validatePair(image(false),image(true));}
    @Test public void actualCapturedVersionPacketIsSupported() throws Exception {assertEquals(3,Protocol.validateVersions(main,slave,mode));}
    @Test public void rejectsWrongHardwareMainSlaveAndMode() throws Exception {
        for(int offset:new int[]{0,7,8,11,12}){byte[] m=main.clone();m[offset]^=1;assertThrows(IOException.class,()->Protocol.validateVersions(m,slave,mode));}
        for(int offset=0;offset<4;offset++){byte[] s=slave.clone();s[offset]^=1;assertThrows(IOException.class,()->Protocol.validateVersions(main,s,mode));}
        assertThrows(IOException.class,()->Protocol.validateVersions(main,slave,new byte[]{9,7}));
        assertThrows(IOException.class,()->Protocol.validateVersions(new byte[19],slave,mode));
    }
    @Test public void firstAndLastPacketsMatchIndependentLegacyTrace() throws Exception {
        byte[] stock=image(false);
        assertArrayEquals(Protocol.unhex("803c000002f7896f027b41186105e5513b5c4007bac3f9a012d096f62819413fca1a11c814445e2e1452fd70028f95e52b455942564e4a9461f3ba7f16c89b0c"),Protocol.block(stock,0,(byte)0x80));
        assertArrayEquals(Protocol.unhex("801c508277fae06dd45943cebc312ba61f928805452832bf097471e3911c168b"),Protocol.block(stock,33360,(byte)0x80));
    }
    static class Loader implements Protocol.Transport {
        final byte[] memory=new byte[Protocol.SIZE];
        int commands,written,verified;boolean erased,finished;int failAt=-1;boolean corrupt;
        @Override public byte[] exchange(byte[] p,int timeout) throws Exception {
            commands++;
            if(commands==failAt) return new byte[]{p[0],1};
            int op=p[0]&255;
            if(op==0x81){assertEquals(1,p.length);Arrays.fill(memory,(byte)0xff);erased=true;}
            else if(op==0x80 || op==0x82){
                assertTrue(erased);int n=p[1]&255,addr=(p[2]&255)|((p[3]&255)<<8);
                assertTrue(n>0&&n<=60);assertEquals(n+4,p.length);assertTrue(addr+n<=memory.length);
                if(op==0x80){assertEquals(written,addr);System.arraycopy(p,4,memory,addr,n);written+=n;if(corrupt)memory[addr]^=1;}
                else {assertEquals(verified,addr);if(!Arrays.equals(Arrays.copyOfRange(p,4,p.length),Arrays.copyOfRange(memory,addr,addr+n)))return new byte[]{p[0],1};verified+=n;}
            } else if(op==0x83){assertEquals(Protocol.SIZE,verified);finished=true;}
            else fail("Unexpected command "+op);
            return new byte[]{p[0],0};
        }
    }
    @Test public void installAndRestoreVerifyEveryByteBeforeFinish() throws Exception {
        for(boolean patch:new boolean[]{false,true}){
            Loader loader=new Loader();List<Integer> progress=new ArrayList<>();byte[] bytes=image(patch);
            Protocol.flash(loader,bytes,patch,(n,t)->{assertEquals(Protocol.SIZE,t);progress.add(n);});
            assertEquals(1116,loader.commands);assertEquals(557,progress.size());assertTrue(loader.finished);
            assertArrayEquals(bytes,loader.memory);assertEquals(Integer.valueOf(33388),progress.get(556));
        }
    }
    @Test public void noCommandsForCorruptTruncatedOrWrongImage() throws Exception {
        byte[] bytes=image(true);
        for(int pos:new int[]{0,59,60,0x64a8,0x653a,33387}){byte[] bad=bytes.clone();bad[pos]^=1;Loader l=new Loader();assertThrows(IOException.class,()->Protocol.flash(l,bad,true,(n,t)->{}));assertEquals(0,l.commands);}
        Loader l=new Loader();assertThrows(IOException.class,()->Protocol.flash(l,image(false),true,(n,t)->{}));assertEquals(0,l.commands);
        assertThrows(IOException.class,()->Protocol.flash(l,new byte[33387],true,(n,t)->{}));assertEquals(0,l.commands);
    }
    @Test public void stopsImmediatelyAtEachFailureStageWithoutFinishOrRetry() throws Exception {
        for(int command:new int[]{1,2,3,500,1114,1115,1116}){Loader l=new Loader();l.failAt=command;assertThrows(IOException.class,()->Protocol.flash(l,image(true),true,(n,t)->{}));assertEquals(command,l.commands);assertFalse(l.finished);}
        Loader corrupt=new Loader();corrupt.corrupt=true;assertThrows(IOException.class,()->Protocol.flash(corrupt,image(true),true,(n,t)->{}));assertEquals(3,corrupt.commands);assertFalse(corrupt.finished);
    }
    @Test public void rejectsWrongOrShortAcksAndPropagatesTimeout() throws Exception {
        for(byte[] reply:new byte[][]{ {},{(byte)0x81},{(byte)0x80,0},{(byte)0x81,1} })assertThrows(IOException.class,()->Protocol.acknowledge(reply,0x81));
        int[] commands={0};assertThrows(java.util.concurrent.TimeoutException.class,()->Protocol.flash((p,t)->{commands[0]++;throw new java.util.concurrent.TimeoutException();},image(true),true,(n,t)->{}));assertEquals(1,commands[0]);
    }
    @Test public void changingCallerBufferCannotChangeVerifiedPayload() throws Exception {
        byte[] input=image(true),expected=input.clone();Loader l=new Loader();Protocol.flash(l,input,true,(n,t)->Arrays.fill(input,(byte)0));assertArrayEquals(expected,l.memory);
    }
    @Test public void rejectsInvalidAddressesAndOpcodes() throws Exception {
        byte[] b=image(false);for(int offset:new int[]{-1,1,33388,65536})assertThrows(IllegalArgumentException.class,()->Protocol.block(b,offset,(byte)0x80));
        assertThrows(IllegalArgumentException.class,()->Protocol.block(b,0,(byte)0x83));
    }
}
