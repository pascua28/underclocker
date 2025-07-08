package com.sammy.underclocker;


import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
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

    private LinearLayout containerLayout;
    private Button saveButton;

    private final Map<String, Spinner> spinnerMap = new HashMap<>();
    private final Map<String, TextView> currentFreqMap = new HashMap<>();
    private final Map<String, TextView> currentRangeMap = new HashMap<>();

    SharedPreferences prefs;
    private Handler handler = new Handler();
    private Runnable freqUpdater;

    private List<String> detectedPolicies;

    private final Shizuku.OnRequestPermissionResultListener REQUEST_PERMISSION_RESULT_LISTENER = this::onRequestPermissionsResult;

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

        Shizuku.addRequestPermissionResultListener(REQUEST_PERMISSION_RESULT_LISTENER);

        ScrollView scrollView = new ScrollView(this);
        containerLayout = new LinearLayout(this);
        containerLayout.setOrientation(LinearLayout.VERTICAL);
        containerLayout.setPadding(32, 64, 32, 32);
        scrollView.addView(containerLayout);
        setContentView(scrollView);

        prefs = getSharedPreferences("UnderclockerPrefs", MODE_PRIVATE);
        detectedPolicies = Utils.detectAvailablePolicies();

        for (String policy : detectedPolicies) {
            addPolicyUI(policy);
        }

        saveButton = new Button(this);
        saveButton.setText("Save");
        LinearLayout.LayoutParams saveParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        saveParams.topMargin = 24;
        containerLayout.addView(saveButton, saveParams);

        saveButton.setOnClickListener(v -> {
            SharedPreferences.Editor editor = prefs.edit();
            for (String policy : detectedPolicies) {
                String selected = spinnerMap.get(policy).getSelectedItem().toString();
                editor.putString(policy, selected);
                if (!"unchanged".equals(selected)) {
                    Utils.runCmd("echo " + selected + " > /sys/devices/system/cpu/cpufreq/" + policy + "/scaling_max_freq");
                }
            }
            editor.apply();
            Toast.makeText(this, "Frequencies saved and applied", Toast.LENGTH_SHORT).show();
        });

        startFrequencyUpdates();

        Intent svcIntent = new Intent(this, FrequencyService.class);
            startForegroundService(svcIntent);
    }

    private void addPolicyUI(String policy) {
        LinearLayout block = new LinearLayout(this);
        block.setOrientation(LinearLayout.VERTICAL);

        int dp = (int) getResources().getDisplayMetrics().density;
        block.setPadding(16 * dp, 16 * dp, 16 * dp, 16 * dp);

        LinearLayout.LayoutParams blockParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        blockParams.topMargin = 8 * dp;
        block.setLayoutParams(blockParams);

        TextView label = new TextView(this);
        label.setText("Policy " + policy.replace("policy", ""));
        block.addView(label);

        Spinner spinner = new Spinner(this);
        block.addView(spinner);
        spinnerMap.put(policy, spinner);

        TextView curFreq = new TextView(this);
        curFreq.setText("Current: -- MHz");
        block.addView(curFreq);
        currentFreqMap.put(policy, curFreq);

        TextView rangeText = new TextView(this);
        rangeText.setText("Current range: --");
        LinearLayout.LayoutParams rangeParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        rangeParams.topMargin = 8 * dp;
        rangeText.setLayoutParams(rangeParams);
        block.addView(rangeText);
        currentRangeMap.put(policy, rangeText);

        containerLayout.addView(block);

        setupSpinner(policy);
    }

    private void setupSpinner(String policy) {
        String freqPath = "/sys/devices/system/cpu/cpufreq/" + policy + "/scaling_available_frequencies";
        String currentMaxPath = "/sys/devices/system/cpu/cpufreq/" + policy + "/scaling_max_freq";

        String freqStr = Utils.runCmd("cat " + freqPath);
        String currentMax = Utils.runCmd("cat " + currentMaxPath).trim();

        List<String> freqs = new ArrayList<>();
        freqs.add("unchanged");

        if (freqStr != null && !freqStr.isEmpty()) {
            String[] tokens = freqStr.trim().split("\\s+");
            freqs.addAll(Arrays.asList(tokens));
        }

        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, freqs);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        Spinner spinner = spinnerMap.get(policy);
        spinner.setAdapter(adapter);

        String saved = prefs.getString(policy, null);
        if (saved != null && freqs.contains(saved)) {
            spinner.setSelection(freqs.indexOf(saved));
        } else if (freqs.contains(currentMax)) {
            spinner.setSelection(freqs.indexOf(currentMax));
        } else {
            spinner.setSelection(0);
        }
    }

    private void startFrequencyUpdates() {
        freqUpdater = new Runnable() {
            @Override
            public void run() {
                for (String policy : detectedPolicies) {
                    currentFreqMap.get(policy).setText("Current: " + readFreq(policy));
                    currentRangeMap.get(policy).setText("Current range: " + readRange(policy));
                }
                handler.postDelayed(this, 2000);
            }
        };
        handler.post(freqUpdater);
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

    private String readRange(String policy) {
        String min = Utils.runCmd("cat /sys/devices/system/cpu/cpufreq/" + policy + "/scaling_min_freq").trim();
        String max = Utils.runCmd("cat /sys/devices/system/cpu/cpufreq/" + policy + "/scaling_max_freq").trim();
        return toMHz(min) + " - " + toMHz(max) + " MHz";
    }

    private String toMHz(String freqStr) {
        try {
            return String.valueOf(Integer.parseInt(freqStr.trim()) / 1000);
        } catch (NumberFormatException e) {
            return "--";
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        startFrequencyUpdates();
    }

    @Override
    protected void onPause() {
        super.onPause();
        handler.removeCallbacks(freqUpdater);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        handler.removeCallbacks(freqUpdater);
    }
}