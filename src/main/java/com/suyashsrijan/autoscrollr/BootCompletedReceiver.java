package com.suyashsrijan.autoscrollr;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.preference.PreferenceManager;
import android.util.Log;

public class BootCompletedReceiver extends BroadcastReceiver {

    public static String TAG = "AutoScrollr-App";

    @Override
    public void onReceive(Context context, Intent intent) {
        boolean autoStartOnBoot = PreferenceManager.getDefaultSharedPreferences(context).getBoolean("autoStartOnBoot", false);
        Log.i(TAG, "Received BOOT_COMPLETED intent, autoStartOnBoot=" + Boolean.toString(autoStartOnBoot));
        if (autoStartOnBoot) {
            Intent startServiceIntent = new Intent(context, ScrollrService.class);
            context.startService(startServiceIntent);
        }
    }
}
