package io.github.hakami55.x2patcher;

import android.app.*;
import android.content.*;
import android.content.pm.ServiceInfo;
import android.hardware.usb.*;
import android.os.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

public class UpdateService extends Service {
    static final String CHECK="check",INSTALL="install",RESTORE="restore";
    static final AtomicBoolean busy=new AtomicBoolean(false);
    static volatile String status="Ready to check your controller";
    static volatile int progress=0;
    static volatile boolean checked=false,patched=false;
    static volatile String deniedDevice="";
    private static final StringBuilder log=new StringBuilder();
    private PowerManager.WakeLock wake;
    private UsbManager manager;
    private String runId;
    private File logFile;
    private int lastPercent=-1;
    static synchronized String report(Context context) {
        String history=log.toString();
        File persisted=new File(context.getFilesDir(),"last-session.txt");
        if(persisted.exists())try {history=new String(java.nio.file.Files.readAllBytes(persisted.toPath()),StandardCharsets.UTF_8);}
        catch(IOException ignored) { /* Current-process log is still available. */ }
        return "X2 Pro Patcher 0.1.0-alpha1\nAndroid: "+Build.VERSION.RELEASE+" (API "+Build.VERSION.SDK_INT+")\nDevice: "+Build.MANUFACTURER+" "+Build.MODEL
            +"\nNo device serial, account, IP address or USB path is included.\n\n"+history;
    }
    private void event(String s) {
        String line=new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'",Locale.ROOT) {{setTimeZone(TimeZone.getTimeZone("UTC"));}}.format(new Date())+" "+s;
        synchronized(UpdateService.class){log.append(line).append('\n');}
        if(logFile!=null) try(FileOutputStream f=new FileOutputStream(logFile,true)) { f.write((line+"\n").getBytes(StandardCharsets.UTF_8)); }
        catch(IOException ex){ android.util.Log.e("X2Patcher","Cannot persist diagnostic log",ex); }
        android.util.Log.i("X2Patcher",s);
    }
    private void show(String s,int p) {
        status=s;progress=p;
        if(p!=lastPercent){lastPercent=p;getSystemService(NotificationManager.class).notify(7,notification(s,p));}
    }
    private Notification notification(String s,int p) {
        PendingIntent open=PendingIntent.getActivity(this,0,new Intent(this,MainActivity.class),PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT);
        return new Notification.Builder(this,"update").setSmallIcon(R.drawable.ic_patcher).setContentTitle("X2 Pro Patcher")
            .setContentText(s).setContentIntent(open).setOnlyAlertOnce(true).setOngoing(true)
            .setProgress(100,Math.max(0,p),p<0).build();
    }
    @Override public IBinder onBind(Intent intent){return null;}
    @Override public int onStartCommand(Intent intent,int flags,int startId) {
        if(intent==null||!busy.compareAndSet(false,true))return START_NOT_STICKY;
        String action=intent.getAction();
        if(!Arrays.asList(CHECK,INSTALL,RESTORE).contains(action)){busy.set(false);stopSelf();return START_NOT_STICKY;}
        getSystemService(NotificationManager.class).createNotificationChannel(new NotificationChannel("update","Controller update",NotificationManager.IMPORTANCE_LOW));
        if(Build.VERSION.SDK_INT>=29)startForeground(7,notification("Checking controller…",-1),ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE);
        else startForeground(7,notification("Checking controller…",-1));
        manager=getSystemService(UsbManager.class);
        wake=getSystemService(PowerManager.class).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,"x2patcher:update");wake.acquire(10*60*1000L);
        runId=Long.toString(System.currentTimeMillis());logFile=new File(getFilesDir(),"last-session.txt");
        checked=false;progress=0;
        new Thread(()->{
            try { event("Operation: "+action+" / "+runId); run(action); }
            catch(Exception e){status="Stopped: "+(e.getMessage()==null?e.getClass().getSimpleName():e.getMessage());event(status);}
            finally {
                busy.set(false); if(wake!=null&&wake.isHeld())wake.release();
                stopForeground(STOP_FOREGROUND_REMOVE);stopSelf();
            }
        },"x2-update").start();
        return START_NOT_STICKY;
    }
    private byte[] asset(String name) throws IOException {
        try(InputStream in=getAssets().open(name);ByteArrayOutputStream out=new ByteArrayOutputStream()) {
            byte[] b=new byte[4096];int n;while((n=in.read(b))!=-1)out.write(b,0,n);return out.toByteArray();
        }
    }
    static void permission(Context c,UsbManager manager,UsbDevice device) {
        deniedDevice="";
        Intent intent=new Intent(c,UsbPermissionReceiver.class);
        PendingIntent p=PendingIntent.getBroadcast(c,0,intent,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_MUTABLE);
        manager.requestPermission(device,p);
    }
    private void permit(UsbDevice d) throws Exception {
        if(manager.hasPermission(d))return;
        show(d.getVendorId()==0x05ac?"Allow USB access to the controller's update interface":"Allow USB access to verify the restarted controller",-1);
        permission(this,manager,d);
        long deadline=SystemClock.elapsedRealtime()+90000;
        while(SystemClock.elapsedRealtime()<deadline){
            if(manager.hasPermission(d))return;
            if(deniedDevice.equals(d.getDeviceName()))throw new IOException("USB permission was declined. Tap Check Controller to continue with the same device.");
            if(!manager.getDeviceList().containsKey(d.getDeviceName()))throw new IOException("Controller disconnected while waiting for permission.");
            SystemClock.sleep(150);
        }
        throw new IOException("USB permission was not granted. Reopen the app and retry with the same controller.");
    }
    private UsbDevice waitDevice(boolean dfu,boolean patch) throws Exception {
        long deadline=SystemClock.elapsedRealtime()+20000;
        while(SystemClock.elapsedRealtime()<deadline) {
            List<UsbDevice> matches=new ArrayList<>();
            for(UsbDevice d:manager.getDeviceList().values())if(UsbLink.candidate(d))matches.add(d);
            if(matches.size()>1)throw new IOException("Multiple matching controllers appeared. Update stopped.");
            if(matches.size()==1){UsbDevice d=matches.get(0);
                if(dfu ? d.getVendorId()==0x05ac : d.getVendorId()==(patch?0x04b4:0x3537))return d;
            }
            SystemClock.sleep(150);
        }
        throw new IOException(dfu?"The MAIN update interface did not appear. No erase command sent. Reconnect and check."
            :"All blocks were verified, but normal mode was not confirmed. Reconnect the same controller and tap Check Controller.");
    }
    private void run(String action) throws Exception {
        byte[] original=asset("official-main.bin"),candidate=asset("patched-main.bin");
        Protocol.validatePair(original,candidate);
        byte[] originalUsb=asset("official-usb.bin"),candidateUsb=asset("patched-usb.bin");
        UsbDevice device=UsbLink.single(manager);permit(device);
        SharedPreferences receipt=getSharedPreferences("recovery",MODE_PRIVATE);
        boolean isCheck=CHECK.equals(action),wantPatch=INSTALL.equals(action);
        boolean startedInDfu;
        try(UsbLink usb=new UsbLink(manager,device,originalUsb,candidateUsb)) {
            startedInDfu=usb.dfu;
            event("USB: "+String.format(Locale.ROOT,"%04x:%04x",device.getVendorId(),device.getProductId())+"; exact identity and updater descriptor accepted");
            if(usb.dfu) {
                if(!receipt.getBoolean("pending",false)||!"129.24/2.0/129.3".equals(receipt.getString("profile","")))
                    throw new IOException("Unverified controller in update mode. This app only recovers a controller it checked before updating. No erase command sent.");
                if(isCheck){checked=true;show("Recovery available for the previously checked controller. Keep the same controller connected.",0);event(status);return;}
            } else {
                usb.inspect();patched=usb.patched;checked=true;
                event("Verified MAIN 129.24, hardware 2.0, slave 129.3, mode 3. USB identity: "+(patched?"Apple patch":"official"));
                if(isCheck){show(patched?"Compatible controller • Apple patch identity detected":"Compatible controller • official firmware identity detected",0);return;}
                if(wantPatch==usb.patched){show(wantPatch?"Patch identity already present. No firmware written.":"Official firmware identity already present. No firmware written.",0);event(status);return;}
                if(!receipt.edit().putBoolean("pending",true).putString("profile","129.24/2.0/129.3").putString("target",wantPatch?"patch":"official").commit())
                    throw new IOException("Cannot save the recovery record. No update started.");
                checked=false;show("Entering update mode. Keep the controller connected.",-1);
                usb.sendHandoff();event("03 01 MAIN handoff sent; no erase yet");
            }
        }
        device=startedInDfu?device:waitDevice(true,wantPatch);permit(device);
        try(UsbLink dfu=new UsbLink(manager,device,originalUsb,candidateUsb)) {
            if(!dfu.dfu)throw new IOException("Not in MAIN update mode. No erase sent.");
            if(!receipt.getBoolean("pending",false))throw new IOException("Recovery record missing. No erase sent.");
            event("Starting MAIN update: "+(wantPatch?Protocol.PATCH_SHA:Protocol.STOCK_SHA));
            show("Writing firmware. Keep the controller connected.",0);
            Protocol.flash(dfu,wantPatch?candidate:original,wantPatch,(done,total)->{
                int percent=done*100/total;
                show("Verified "+done+" / "+total+" bytes",percent);
                if(done==total||(done/60)%32==0)event("Verified "+done+" / "+total);
            });
            event("557 blocks verified; finish acknowledged");
        }
        show("Checking the restarted controller…",100);
        device=waitDevice(false,wantPatch);permit(device);
        try(UsbLink normal=new UsbLink(manager,device,originalUsb,candidateUsb)) {
            normal.inspect();
            if(normal.dfu||normal.patched!=wantPatch)throw new IOException("Post-update identity mismatch.");
            patched=wantPatch;checked=true;
        }
        if(!receipt.edit().clear().commit())event("Could not clear recovery record; run Check Controller before the next operation.");
        show(wantPatch?"Patch installed and controller verified. Ready to test on your Apple device."
            :"Official MAIN 129.24 restored and controller verified.",100);event(status);
    }
}
