package io.github.hakami55.x2patcher;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.hardware.usb.*;
import android.os.*;
import android.view.*;
import android.widget.*;
import java.io.OutputStream;
import java.lang.ref.WeakReference;
import java.nio.charset.StandardCharsets;

public class MainActivity extends Activity {
    private static WeakReference<MainActivity> current=new WeakReference<>(null);
    private final Handler handler=new Handler(Looper.getMainLooper());
    private TextView status,details;
    private ProgressBar progress;
    private Button check,upgrade,install,restore,save;
    private EditText testDevice,testOs;
    private Spinner testResult;
    private UsbManager manager;
    private String pendingAction;
    private String exportText;
    private UsbDevice pendingDevice;
    private final int navy=Color.rgb(18,43,61),teal=Color.rgb(0,107,96),muted=Color.rgb(75,93,107);
    private final Runnable refresh=new Runnable(){public void run(){render();handler.postDelayed(this,300);}};

    static void permissionChanged(){MainActivity a=current.get();if(a!=null)a.handler.post(a::afterPermission);}
    private int dp(int n){return Math.round(n*getResources().getDisplayMetrics().density);}
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);current=new WeakReference<>(this);manager=getSystemService(UsbManager.class);
        getWindow().setStatusBarColor(navy);getWindow().setNavigationBarColor(navy);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);scroll.setBackgroundColor(Color.rgb(244,247,249));
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setPadding(dp(20),dp(20),dp(20),dp(24));scroll.addView(root);
        TextView badge=text("COMMUNITY PREVIEW  /  "+BuildConfig.VERSION_NAME,12,teal,true);root.addView(badge);
        root.addView(text("X2 Pro Patcher",30,navy,true));
        root.addView(text("Apple compatibility patch for the GameSir X2 Pro",16,muted,false));space(root,18);

        LinearLayout connection=card(root);
        connection.addView(text("1  CONNECT & CHECK",12,teal,true));
        connection.addView(text("Plug the controller's movable USB-C connector directly into this Android device. Leave chargers and hubs disconnected. Force-stop the GameSir app in Android App info first.",15,navy,false));
        status=text(UpdateService.status,18,navy,true);connection.addView(status);
        progress=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);progress.setMax(100);connection.addView(progress,new LinearLayout.LayoutParams(-1,dp(12)));
        check=button("Check Controller",connection,true);check.setOnClickListener(v->request(UpdateService.CHECK));
        details=text(UpdateService.profileDetails,13,muted,false);connection.addView(details);

        LinearLayout update=card(root);update.addView(text("2  INSTALL OR RESTORE",12,teal,true));
        update.addView(text("Experimental firmware. A failed update can leave the controller unusable. The restore option writes official MAIN 129.24; it is not a full backup of your controller.",14,navy,false));
        upgrade=button("Update to Official 129.24 (Risky)",update,false);
        upgrade.setOnClickListener(v->confirm(UpdateService.UPGRADE));
        update.addView(text("Older MAIN firmware: update to official 129.24 first, then install the patch. This upgrade has not been tested on an older physical controller.",13,muted,false));
        install=button("Install Apple Patch",update,true);restore=button("Restore Official Firmware",update,false);
        install.setOnClickListener(v->confirm(UpdateService.INSTALL));restore.setOnClickListener(v->confirm(UpdateService.RESTORE));
        update.addView(text("Keep the app open and allow USB access again when update mode appears. Only the main controller firmware is updated.",13,muted,false));

        LinearLayout compatibility=card(root);compatibility.addView(text("WHAT HAS BEEN TESTED",12,teal,true));
        compatibility.addView(text("Reported working: iPhone 15 Pro Max, iPhone 17 Pro, iPad mini 6.\nNot working: iPhone 16, 16 Plus, 16 Pro.\nIndividual reports; compatibility and button mapping may vary.",14,navy,false));

        LinearLayout feedback=card(root);feedback.addView(text("3  SHARE YOUR TEST RESULT",12,teal,true));
        testDevice=new EditText(this);testDevice.setSingleLine(true);testDevice.setHint("Apple device model (optional)");testDevice.setTextSize(14);feedback.addView(testDevice);
        testOs=new EditText(this);testOs.setSingleLine(true);testOs.setHint("iOS / iPadOS version (optional)");testOs.setTextSize(14);feedback.addView(testOs);
        testResult=new Spinner(this);ArrayAdapter<String> results=new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,new String[]{"Not tested yet","Controls work","No controller detected","Some controls work"});testResult.setAdapter(results);feedback.addView(testResult);
        save=button("Save Diagnostic Report",feedback,false);save.setOnClickListener(v->saveReport());
        feedback.addView(text("The report stays on your device until you choose to share it. No internet access, root permission or account is used by this app.",13,muted,false));
        TextView link=text("Project & test reports ↗",15,teal,true);link.setPadding(0,dp(14),0,dp(14));feedback.addView(link);
        link.setOnClickListener(v->{try{startActivity(new Intent(Intent.ACTION_VIEW,android.net.Uri.parse("https://github.com/hakami55/gamesir-x2pro-apple-patcher")));}catch(ActivityNotFoundException e){Toast.makeText(this,"No browser available",Toast.LENGTH_SHORT).show();}});
        setContentView(scroll);
        if(Build.VERSION.SDK_INT>=35)root.setOnApplyWindowInsetsListener((v,insets)->{
            android.graphics.Insets bars=insets.getInsets(WindowInsets.Type.systemBars());
            root.setPadding(dp(20)+bars.left,dp(20)+bars.top,dp(20)+bars.right,dp(24)+bars.bottom);return insets;
        });
        if(state!=null){testDevice.setText(state.getString("device",""));testOs.setText(state.getString("os",""));testResult.setSelection(state.getInt("result",0));exportText=state.getString("export");}
    }
    private TextView text(String s,int size,int color,boolean bold){TextView t=new TextView(this);t.setText(s);t.setTextSize(size);t.setTextColor(color);t.setPadding(0,dp(5),0,dp(5));if(bold)t.setTypeface(null,Typeface.BOLD);return t;}
    private void space(LinearLayout root,int amount){Space s=new Space(this);root.addView(s,new LinearLayout.LayoutParams(1,dp(amount)));}
    private LinearLayout card(LinearLayout root){LinearLayout c=new LinearLayout(this);c.setOrientation(LinearLayout.VERTICAL);c.setPadding(dp(16),dp(13),dp(16),dp(14));GradientDrawable b=new GradientDrawable();b.setColor(Color.WHITE);b.setCornerRadius(dp(14));b.setStroke(dp(1),Color.rgb(219,227,232));c.setBackground(b);LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.bottomMargin=dp(14);root.addView(c,p);return c;}
    private Button button(String s,LinearLayout root,boolean filled){Button b=new Button(this);b.setText(s);b.setAllCaps(false);b.setTextSize(16);b.setMinHeight(dp(52));b.setTextColor(filled?Color.WHITE:navy);GradientDrawable d=new GradientDrawable();d.setColor(filled?teal:Color.rgb(233,241,244));d.setCornerRadius(dp(8));b.setBackground(d);LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,dp(52));p.topMargin=dp(9);p.bottomMargin=dp(5);root.addView(b,p);return b;}
    private void render(){boolean busy=UpdateService.busy.get();status.setText(UpdateService.status);details.setText(UpdateService.profileDetails);progress.setIndeterminate(busy&&UpdateService.progress<0);progress.setProgress(Math.max(0,UpdateService.progress));check.setEnabled(!busy&&pendingAction==null);boolean ready=!busy&&pendingAction==null&&UpdateService.checked;upgrade.setEnabled(ready&&UpdateService.upgradeReady);install.setEnabled(ready&&UpdateService.patchReady);restore.setEnabled(ready&&UpdateService.patchReady);save.setEnabled(!busy);getWindow().setFlags(busy?WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON:0,WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);}
    private void confirm(String action){
        if(UpdateService.busy.get())return;
        if(UpdateService.UPGRADE.equals(action)) {
            new AlertDialog.Builder(this).setTitle("Risky upgrade to official 129.24?")
                .setMessage("This writes official MAIN 129.24 over your older firmware. Older-firmware upgrades have not been tested on a physical controller. A different bootloader or configuration, or an interrupted update, could leave the controller unusable. Recovery is not guaranteed and may require a hardware programmer.\n\nThe app checks model, hardware 2.0, slave 129.3, mode 3 and the updater interface. These checks do not prove that every older firmware is compatible. There is no backup or restore to your previous version.\n\nKeep this same controller connected. Only after 129.24 is verified will the separate Apple patch option unlock.")
                .setNegativeButton("Cancel",null).setPositiveButton("Accept Risk & Update",(d,w)->request(action)).show();
            return;
        }
        new AlertDialog.Builder(this).setTitle(action.equals(UpdateService.INSTALL)?"Install experimental Apple patch?":"Restore official MAIN firmware?")
            .setMessage("This writes the controller's firmware. Keep this same controller connected until verification finishes. An interrupted or failed update may need recovery or a hardware programmer.\n\nOnly the checked 129.24 / hardware 2.0 version is supported. iPhone compatibility is not guaranteed.")
            .setNegativeButton("Cancel",null).setPositiveButton("Continue",(d,w)->request(action)).show();
    }
    private void request(String action){
        if(UpdateService.busy.get())return;
        try {
            pendingDevice=UsbLink.single(manager);pendingAction=action;
            if(!manager.hasPermission(pendingDevice)){UpdateService.status="Allow USB access to check the controller";UpdateService.permission(this,manager,pendingDevice);}
            else afterPermission();
        } catch(Exception e){pendingAction=null;UpdateService.checked=false;UpdateService.status=e.getMessage();}
        render();
    }
    private void afterPermission(){
        if(pendingAction==null||pendingDevice==null)return;
        if(!manager.hasPermission(pendingDevice)){pendingAction=null;UpdateService.status="USB permission not granted. Tap Check Controller to try again.";return;}
        String action=pendingAction;pendingAction=null;pendingDevice=null;
        if(Build.VERSION.SDK_INT>=33&&checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=android.content.pm.PackageManager.PERMISSION_GRANTED)
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},20);
        startForegroundService(new Intent(this,UpdateService.class).setAction(action));
    }
    private void saveReport(){
        exportText=UpdateService.report(this)+"\nTester-entered result\nApple model: "+testDevice.getText()+"\nOS: "+testOs.getText()+"\nResult: "+testResult.getSelectedItem()+"\n";
        Intent i=new Intent(Intent.ACTION_CREATE_DOCUMENT).setType("text/plain").addCategory(Intent.CATEGORY_OPENABLE).putExtra(Intent.EXTRA_TITLE,"x2pro-test-report.txt");
        try{startActivityForResult(i,10);}catch(ActivityNotFoundException e){Toast.makeText(this,"No document provider installed",Toast.LENGTH_LONG).show();}
    }
    @Override protected void onActivityResult(int request,int result,Intent data){super.onActivityResult(request,result,data);if(request==10&&result==RESULT_OK&&data!=null&&data.getData()!=null){try(OutputStream out=getContentResolver().openOutputStream(data.getData())){if(out==null)throw new java.io.IOException("Cannot open selected file");out.write(exportText.getBytes(StandardCharsets.UTF_8));Toast.makeText(this,"Report saved",Toast.LENGTH_SHORT).show();}catch(Exception e){Toast.makeText(this,"Could not save report: "+e.getMessage(),Toast.LENGTH_LONG).show();}}}
    @Override protected void onResume(){super.onResume();current=new WeakReference<>(this);handler.post(refresh);}
    @Override protected void onPause(){handler.removeCallbacks(refresh);super.onPause();}
    @Override protected void onDestroy(){if(current.get()==this)current.clear();super.onDestroy();}
    @Override protected void onSaveInstanceState(Bundle b){super.onSaveInstanceState(b);b.putString("device",testDevice.getText().toString());b.putString("os",testOs.getText().toString());b.putInt("result",testResult.getSelectedItemPosition());b.putString("export",exportText);}
}
