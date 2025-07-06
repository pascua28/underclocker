package com.sammy.underclocker;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import rikka.shizuku.Shizuku;

public class MainActivity extends AppCompatActivity {

    private final String[] policies = {"policy0", "policy3", "policy7"};
    private final Map<String, Spinner> spinnerMap = new HashMap<>();
    private final Shizuku.OnRequestPermissionResultListener REQUEST_PERMISSION_RESULT_LISTENER = this::onRequestPermissionsResult;
    Spinner spinner0, spinner3, spinner7;
    TextView textCur0, textCur3, textCur7;
    TextView textRange0, textRange3, textRange7;
    Button saveButton;
    SharedPreferences prefs;
    private final BroadcastReceiver stateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            applyFrequencies();
        }
    };
    private Handler handler = new Handler();
    private Runnable freqUpdater;

    public static void forceStop(Context context) {
        String packageName = context.getPackageName();
        Toast.makeText(context, "Shizuku granted. Please restart.", Toast.LENGTH_SHORT).show();
        Utils.runCmd("am force-stop " + packageName);
    }

    private void onRequestPermissionsResult(int requestCode, int grantResult) {
        if (grantResult == PackageManager.PERMISSION_GRANTED)
            forceStop(this);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Shizuku.addRequestPermissionResultListener(REQUEST_PERMISSION_RESULT_LISTENER);

        spinner0 = findViewById(R.id.spinner0);
        spinner3 = findViewById(R.id.spinner3);
        spinner7 = findViewById(R.id.spinner7);
        textCur0 = findViewById(R.id.textCur0);
        textCur3 = findViewById(R.id.textCur3);
        textCur7 = findViewById(R.id.textCur7);
        textRange0 = findViewById(R.id.textRange0);
        textRange3 = findViewById(R.id.textRange3);
        textRange7 = findViewById(R.id.textRange7);
        saveButton = findViewById(R.id.saveButton);

        spinnerMap.put("policy0", spinner0);
        spinnerMap.put("policy3", spinner3);
        spinnerMap.put("policy7", spinner7);

        prefs = getSharedPreferences("UnderclockerPrefs", MODE_PRIVATE);

        for (String policy : policies) {
            setupSpinner(policy);
        }

        saveButton.setOnClickListener(v -> {
            SharedPreferences.Editor editor = prefs.edit();
            for (String policy : policies) {
                String selected = spinnerMap.get(policy).getSelectedItem().toString();
                editor.putString(policy, selected);

                if (!"unchanged".equals(selected)) {
                    String cur = Utils.runCmd("cat /sys/devices/system/cpu/cpufreq/" + policy + "/scaling_max_freq").trim();
                    try {
                        long currentFreq = Long.parseLong(cur);
                        long selectedFreq = Long.parseLong(selected);
                        if (selectedFreq < currentFreq) {
                            Utils.runCmd("echo " + selected + " > /sys/devices/system/cpu/cpufreq/" + policy + "/scaling_max_freq");
                        }
                    } catch (NumberFormatException ignored) {
                    }
                }
            }
            editor.apply();
            Toast.makeText(this, "Frequencies saved", Toast.LENGTH_SHORT).show();
        });

        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_SCREEN_ON);
        filter.addAction(Intent.ACTION_SCREEN_OFF);
        filter.addAction(Intent.ACTION_POWER_CONNECTED);
        filter.addAction(Intent.ACTION_POWER_DISCONNECTED);
        registerReceiver(stateReceiver, filter);

        startFrequencyUpdates();

        Intent svcIntent = new Intent(this, FrequencyService.class);
        startForegroundService(svcIntent);
    }

    private void setupSpinner(String policy) {
        String freqPath = "/sys/devices/system/cpu/cpufreq/" + policy + "/scaling_available_frequencies";
        String currentMaxPath = "/sys/devices/system/cpu/cpufreq/" + policy + "/scaling_max_freq";

        String freqStr = Utils.runCmd("cat " + freqPath);
        String currentMax = Utils.runCmd("cat " + currentMaxPath).trim();

        List<String> freqs = new ArrayList<>();
        freqs.add("unchanged");

        if (freqStr != null) {
            String[] tokens = freqStr.trim().split("\\s+");
            freqs.addAll(Arrays.asList(tokens));
        }

        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, freqs);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerMap.get(policy).setAdapter(adapter);

        String saved = prefs.getString(policy, "unchanged");
        if (freqs.contains(saved)) {
            spinnerMap.get(policy).setSelection(freqs.indexOf(saved));
        } else if (freqs.contains(currentMax)) {
            spinnerMap.get(policy).setSelection(freqs.indexOf(currentMax));
        }

        String min = Utils.runCmd("cat /sys/devices/system/cpu/cpufreq/" + policy + "/scaling_min_freq").trim();
        String max = Utils.runCmd("cat /sys/devices/system/cpu/cpufreq/" + policy + "/scaling_max_freq").trim();
        String rangeText = "Current range: " + toMHz(min) + " - " + toMHz(max) + " MHz";

        switch (policy) {
            case "policy0":
                textRange0.setText(rangeText);
                break;
            case "policy3":
                textRange3.setText(rangeText);
                break;
            case "policy7":
                textRange7.setText(rangeText);
                break;
        }
    }

    private String toMHz(String freqStr) {
        try {
            return String.valueOf(Integer.parseInt(freqStr.trim()) / 1000);
        } catch (NumberFormatException e) {
            return "--";
        }
    }

    private void startFrequencyUpdates() {
        freqUpdater = new Runnable() {
            @Override
            public void run() {
                textCur0.setText("Current: " + readFreq("policy0"));
                textCur3.setText("Current: " + readFreq("policy3"));
                textCur7.setText("Current: " + readFreq("policy7"));

                textRange0.setText("Current range: " + readRange("policy0"));
                textRange3.setText("Current range: " + readRange("policy3"));
                textRange7.setText("Current range: " + readRange("policy7"));

                handler.postDelayed(this, 2000); // every 2 seconds
            }
        };
        handler.post(freqUpdater);
    }

    private String readRange(String policy) {
        String min = Utils.runCmd("cat /sys/devices/system/cpu/cpufreq/" + policy + "/scaling_min_freq").trim();
        String max = Utils.runCmd("cat /sys/devices/system/cpu/cpufreq/" + policy + "/scaling_max_freq").trim();
        return toMHz(min) + " - " + toMHz(max) + " MHz";
    }

    private String readFreq(String policy) {
        String result = Utils.runCmd("cat /sys/devices/system/cpu/cpufreq/" + policy + "/scaling_cur_freq");
        if (result.isEmpty()) return "--";
        try {
            return (Integer.parseInt(result.trim()) / 1000) + " MHz";
        } catch (NumberFormatException e) {
            return "--";
        }
    }

    private void applyFrequencies() {
        for (String policy : policies) {
            String sel = prefs.getString(policy, "unchanged");
            if (!"unchanged".equals(sel)) {
                String cur = Utils.runCmd("cat /sys/devices/system/cpu/cpufreq/" + policy + "/scaling_max_freq");
                if (!cur.isEmpty()) {
                    try {
                        long currentFreq = Long.parseLong(cur.trim());
                        long selectedFreq = Long.parseLong(sel.trim());
                        if (selectedFreq < currentFreq) {
                            Utils.runCmd("echo " + sel + " > /sys/devices/system/cpu/cpufreq/" + policy + "/scaling_max_freq");
                        }
                    } catch (NumberFormatException ignored) {
                    }
                }
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unregisterReceiver(stateReceiver);
        handler.removeCallbacks(freqUpdater);
    }
}
