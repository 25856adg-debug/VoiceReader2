package com.example.voicereader;

import android.content.*;
import android.os.Build;

public class BootReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context,Intent intent) {
        if(Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
            // Android requires the user to start monitoring once and grant folder access.
            // Persisted folder permission is retained, but automatic start is intentionally
            // not enabled until the user opts in from the app.
            boolean auto=context.getSharedPreferences("reader",0).getBoolean("autostart",false);
            String tree=context.getSharedPreferences("reader",0).getString("tree",null);
            if(auto && tree!=null) {
                Intent s=new Intent(context,ReaderService.class);
                s.setAction(ReaderService.START);s.putExtra("tree",tree);s.putExtra("interval",10);
                if(Build.VERSION.SDK_INT>=26)context.startForegroundService(s);else context.startService(s);
            }
        }
    }
}
