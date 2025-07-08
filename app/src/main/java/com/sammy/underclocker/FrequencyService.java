package com.sammy.underclocker;


import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.IBinder;

import androidx.core.app.NotificationCompat;

import java.util.List;

public class FrequencyService extends Service {
    private List<String> policies;
    private SharedPreferences prefs;

    private final BroadcastReceiver stateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            applyFrequencies(context);
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        prefs = getSharedPreferences("UnderclockerPrefs", MODE_PRIVATE);
        policies = Utils.detectAvailablePolicies();

        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_SCREEN_ON);
        filter.addAction(Intent.ACTION_SCREEN_OFF);
        filter.addAction(Intent.ACTION_POWER_CONNECTED);
        filter.addAction(Intent.ACTION_POWER_DISCONNECTED);
        registerReceiver(stateReceiver, filter);

        NotificationChannel channel = new NotificationChannel(
                "underclocker_channel", "Underclocker", NotificationManager.IMPORTANCE_LOW);
        ((NotificationManager) getSystemService(NOTIFICATION_SERVICE)).createNotificationChannel(channel);

        Notification notification = new NotificationCompat.Builder(this, "underclocker_channel")
                .setContentTitle("Underclocker running")
                .setContentText("Monitoring CPU frequency states")
                .setSmallIcon(android.R.drawable.stat_notify_sync)
                .build();

        startForeground(1, notification);
    }

    private void applyFrequencies(Context context) {
        for (String policy : policies) {
            String saved = prefs.getString(policy, "unchanged");
            if (!"unchanged".equals(saved)) {
                String current = Utils.runCmd("cat /sys/devices/system/cpu/cpufreq/" + policy + "/scaling_max_freq").trim();
                try {
                    long currentFreq = Long.parseLong(current);
                    long selectedFreq = Long.parseLong(saved);
                    if (selectedFreq < currentFreq) {
                        Utils.runCmd("echo " + selectedFreq + " > /sys/devices/system/cpu/cpufreq/" + policy + "/scaling_max_freq");
                    }
                } catch (NumberFormatException ignored) {
                }
            }
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        unregisterReceiver(stateReceiver);
        super.onDestroy();
    }
}
