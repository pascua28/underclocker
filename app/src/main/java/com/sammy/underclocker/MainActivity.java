package com.sammy.underclocker;

import android.content.*;
import android.content.pm.PackageManager;
import android.os.*;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;
import java.util.*;

import rikka.shizuku.Shizuku;

public class MainActivity extends AppCompatActivity {

    Spinner spinner0, spinner3, spinner7;
    TextView textCur0, textCur3, textCur7;
    TextView textRange0, textRange3, textRange7;
    Button saveButton;

    private final String[] policies = {"policy0", "policy3", "policy7"};
    private final Map<String, Spinner> spinnerMap = new HashMap<>();

    SharedPreferences prefs;
    private Handler handler = new Handler();
    private Runnable freqUpdater;

    private final Shizuku.OnRequestPermissionResultListener REQUEST_PERMISSION_RESULT_LISTENER = this::onRequestPermissionsResult;

    private void onRequestPermissionsResult(int requestCode, int grantResult) {
        if (grantResult == PackageManager.PERMISSION_GRANTED)
            forceStop(this);
    }

    private final BroadcastReceiver stateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            applyFrequencies();
        }
    };

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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(svcIntent);
        } else {
            startService(svcIntent);
        }
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
        String rangeText = "Current range: " + min + " - " + max + " kHz";

        switch (policy) {
            case "policy0": textRange0.setText(rangeText); break;
            case "policy3": textRange3.setText(rangeText); break;
            case "policy7": textRange7.setText(rangeText); break;
        }
    }

    private void startFrequencyUpdates() {
        freqUpdater = new Runnable() {
            @Override
            public void run() {
                textCur0.setText("Current: " + readFreq("policy0") + " kHz");
                textCur3.setText("Current: " + readFreq("policy3") + " kHz");
                textCur7.setText("Current: " + readFreq("policy7") + " kHz");
                handler.postDelayed(this, 2000);
            }
        };
        handler.post(freqUpdater);
    }

    private String readFreq(String policy) {
        String result = Utils.runCmd("cat /sys/devices/system/cpu/cpufreq/" + policy + "/scaling_cur_freq");
        return result.isEmpty() ? "--" : result.trim();
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
                    } catch (NumberFormatException ignored) {}
                }
            }
        }
    }

    public static void forceStop(Context context) {
        String packageName = context.getPackageName();
        Toast.makeText(context, "Shizuku granted. Please restart.", Toast.LENGTH_SHORT).show();
        Utils.runCmd("am force-stop " + packageName);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unregisterReceiver(stateReceiver);
        handler.removeCallbacks(freqUpdater);
    }
}
