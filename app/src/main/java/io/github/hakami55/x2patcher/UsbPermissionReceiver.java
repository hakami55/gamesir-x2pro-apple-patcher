package io.github.hakami55.x2patcher;

import android.content.*;
import android.hardware.usb.*;

/** Explicit, private PendingIntent target. Permission is rechecked with UsbManager. */
public class UsbPermissionReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context,Intent intent) {
        UsbDevice d=intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
        if(d!=null&&!context.getSystemService(UsbManager.class).hasPermission(d))UpdateService.deniedDevice=d.getDeviceName();
        MainActivity.permissionChanged();
    }
}
