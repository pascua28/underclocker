package com.sammy.underclocker;


import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        Intent svcIntent = new Intent(context, FrequencyService.class);
        context.startForegroundService(svcIntent);
    }
}
