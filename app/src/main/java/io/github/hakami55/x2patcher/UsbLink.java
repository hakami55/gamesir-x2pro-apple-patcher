package io.github.hakami55.x2patcher;

import android.hardware.usb.*;
import android.os.SystemClock;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.*;
import java.util.concurrent.TimeoutException;

/** Uses Android's permissioned USB host API. Does not run su or shell commands. */
final class UsbLink implements AutoCloseable, Protocol.Transport {
    final UsbDevice device;
    final boolean dfu, patched;
    final String serial;
    boolean exactDescriptors;
    Protocol.Profile profile;
    private final UsbDeviceConnection connection;
    private final UsbInterface iface;
    private final UsbEndpoint input, output;
    private boolean claimed;

    static boolean candidate(UsbDevice d) {
        return (d.getVendorId()==0x3537 && d.getProductId()==0x0102)
            || (d.getVendorId()==0x04b4 && d.getProductId()==0x2412)
            || (d.getVendorId()==0x05ac && d.getProductId()==0x063c);
    }
    static UsbDevice single(UsbManager manager) throws IOException {
        List<UsbDevice> devices=new ArrayList<>();
        for(UsbDevice d:manager.getDeviceList().values()) if(candidate(d)) devices.add(d);
        if(devices.size()!=1) throw new IOException(devices.isEmpty()
            ? "Connect the GameSir's movable USB-C plug directly to this Android device. No supported controller found."
            : "More than one matching USB device is connected. Connect only one GameSir.");
        return devices.get(0);
    }
    UsbLink(UsbManager manager, UsbDevice d, byte[] stockDescriptor, byte[] patchDescriptor) throws Exception {
        this(manager,d,stockDescriptor,patchDescriptor,false);
    }
    UsbLink(UsbManager manager, UsbDevice d, byte[] stockDescriptor, byte[] patchDescriptor,boolean allowOlderOfficial) throws Exception {
        device=d; dfu=d.getVendorId()==0x05ac; patched=d.getVendorId()==0x04b4;
        if(!candidate(d)||!manager.hasPermission(d)) throw new IOException("USB permission or target identity is missing.");
        serial=d.getSerialNumber();
        if(!"GAMESIR".equals(d.getManufacturerName()) || !"001".equals(serial)
            || !(dfu?"X2 PRO USB DFU":"GameSir-X2 Pro-Xbox").equals(d.getProductName()))
            throw new IOException("USB identity does not match the tested X2 Pro. No command sent.");
        connection=manager.openDevice(d);
        if(connection==null) throw new IOException("Cannot open the controller. Close other controller/updater apps and try again.");
        UsbInterface selected=null; UsbEndpoint in=null,out=null;
        try {
            byte[] raw=connection.getRawDescriptors();
            if(raw==null||raw.length<18) throw new IOException("USB descriptors unavailable.");
            if(dfu && (raw[12]!=0 || raw[13]!=1)) throw new IOException("Unexpected MAIN DFU USB revision.");
            if(!dfu)exactDescriptors=Protocol.validateNormalUsb(raw,patched?patchDescriptor:stockDescriptor,allowOlderOfficial&&!patched);
            if(d.getInterfaceCount()!=(dfu?1:2)) throw new IOException("Unexpected USB interface count.");
            for(int i=0;i<d.getInterfaceCount();i++) {
                UsbInterface f=d.getInterface(i);
                if(f.getId()!=(dfu?0:1)) continue;
                if(f.getInterfaceClass()!=3 || f.getInterfaceSubclass()!=0 || f.getInterfaceProtocol()!=0 || f.getEndpointCount()!=2)
                    throw new IOException("Unexpected updater interface.");
                selected=f;
                for(int e=0;e<f.getEndpointCount();e++) {
                    UsbEndpoint ep=f.getEndpoint(e);
                    if(ep.getType()!=UsbConstants.USB_ENDPOINT_XFER_INT || ep.getMaxPacketSize()!=64)
                        throw new IOException("Unexpected updater endpoint type/size.");
                    if(ep.getDirection()==UsbConstants.USB_DIR_IN) { if(in!=null) throw new IOException("Duplicate input endpoint");in=ep; }
                    else { if(out!=null) throw new IOException("Duplicate output endpoint");out=ep; }
                }
            }
            if(selected==null||in==null||out==null) throw new IOException("Updater endpoints not found.");
            if(!connection.claimInterface(selected,true)) throw new IOException("USB interface busy. Close the GameSir app and reconnect.");
            claimed=true;
            byte[] report=new byte[24];
            int n=connection.controlTransfer(0x81,6,0x2200,selected.getId(),report,report.length,2000);
            if(n!=24||!Arrays.equals(report,Protocol.VENDOR_REPORT)) throw new IOException("Updater report descriptor differs. No firmware command sent.");
        } catch(Exception ex) {
            if(claimed && selected!=null) connection.releaseInterface(selected);
            connection.close();throw ex;
        }
        iface=selected; input=in;output=out;
    }
    @Override public byte[] exchange(byte[] command,int timeoutMillis) throws Exception {
        return transfer(command,timeoutMillis,true);
    }
    void sendHandoff() throws Exception { transfer(new byte[]{3,1},2000,false); }
    private byte[] transfer(byte[] command,int timeoutMillis,boolean response) throws Exception {
        if(command.length<1||command.length>64) throw new IOException("Invalid USB packet size.");
        UsbRequest read=new UsbRequest(),write=new UsbRequest();
        ByteBuffer incoming=ByteBuffer.allocateDirect(64),outgoing=ByteBuffer.allocateDirect(64);
        outgoing.put(command); while(outgoing.position()<64) outgoing.put((byte)0); outgoing.flip();
        boolean readQueued=false,writeQueued=false;
        try {
            if(response) {
                if(!read.initialize(connection,input) || !read.queue(incoming)) throw new IOException("Cannot queue USB reply.");
                readQueued=true;
            }
            if(!write.initialize(connection,output)||!write.queue(outgoing)) throw new IOException("Cannot queue USB command.");
            writeQueued=true;
            long deadline=SystemClock.elapsedRealtime()+timeoutMillis;
            while(readQueued||writeQueued) {
                long remaining=deadline-SystemClock.elapsedRealtime();
                if(remaining<=0) throw new TimeoutException("USB response timed out; update stopped.");
                UsbRequest complete=connection.requestWait(remaining);
                if(complete==null) throw new IOException("USB connection ended; update stopped.");
                if(complete==read) readQueued=false;
                else if(complete==write) {
                    writeQueued=false;
                    if(outgoing.position()!=64) throw new IOException("Short USB write; update stopped.");
                } else throw new IOException("Unexpected USB completion.");
            }
            byte[] reply=new byte[incoming.position()];incoming.flip();incoming.get(reply);return reply;
        } finally {
            if(readQueued) read.cancel();if(writeQueued) write.cancel();
            // Closing requests cancels/releases kernel requests; the owning connection is also
            // closed by the caller on every failure. No retry after an ambiguous write.
            read.close();write.close();
        }
    }
    int inspect() throws Exception {
        inspect(false);return 3;
    }
    Protocol.Profile inspect(boolean allowOlder) throws Exception {
        if(dfu) throw new IOException("Version checks require normal gamepad mode.");
        profile=Protocol.inspectVersions(exchange(new byte[]{1,1},2000),
            exchange(new byte[]{0x20,(byte)0x9f},2000),exchange(new byte[]{9,0},2000));
        if((!allowOlder || patched) && !profile.atTarget())
            throw new IOException("Requires MAIN 129.24. Upgrade eligible older firmware first.");
        if(profile.atTarget() && !exactDescriptors)
            throw new IOException("MAIN 129.24 USB descriptors do not match the supported firmware.");
        return profile;
    }
    @Override public void close() { if(claimed){connection.releaseInterface(iface);claimed=false;}connection.close(); }
}
