package com.sammy.underclocker;

import android.content.*;

public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        Intent svcIntent = new Intent(context, FrequencyService.class);
        context.startForegroundService(svcIntent);
    }
}
