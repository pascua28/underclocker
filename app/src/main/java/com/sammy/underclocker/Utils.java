package com.sammy.underclocker;

import android.content.pm.PackageManager;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.lang.reflect.Method;

import rikka.shizuku.Shizuku;
import rikka.shizuku.ShizukuRemoteProcess;

public class Utils {
    public static boolean shizukuCheckPermission() {
        if (Shizuku.isPreV11()) {
            return false;
        }
        try {
            if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
                return true;
            } else if (Shizuku.shouldShowRequestPermissionRationale()) {
                return false;
            } else {
                Shizuku.requestPermission(1);
                return false;
            }
        } catch (Throwable e) {
            e.printStackTrace();
        }

        return false;
    }
    private static String[] commandsToShCommand(String... commands) {
        String joinedCommands = String.join("\n", commands);
        return new String[]{"sh", "-c", joinedCommands};
    }

    public static ShizukuRemoteProcess exec(String[] command) throws Exception {
        Method method = Shizuku.class.getDeclaredMethod(
                "newProcess",
                String[].class,
                String[].class,
                String.class
        );

        method.setAccessible(true);

        return (ShizukuRemoteProcess) method.invoke(null, command, null, "/");
    }

    private static String runProcess(Process process) throws Exception {
        BufferedReader stdOutStream = new BufferedReader(new InputStreamReader(process.getInputStream()));
        BufferedReader stdErrStream = new BufferedReader(new InputStreamReader(process.getErrorStream()));

        String stdOut = stdOutStream.lines().reduce("", (acc, line) -> acc + line + "\n").trim();
        String stdErr = stdErrStream.lines().reduce("", (acc, line) -> acc + line + "\n").trim();

        stdOutStream.close();
        stdErrStream.close();

        process.waitFor();

        if (process.exitValue() != 0) {
            throw new Exception(stdErr);
        }

        return stdOut;
    }
    public static boolean isPrivileged() {
        if (shizukuCheckPermission()) return Shizuku.getUid() == 1000;

        return false;
    }

    public static boolean isShizuku() {
        return shizukuCheckPermission();
    }
    public static String runCmd(String commands) {
        if (!isPrivileged())
            return "";

        if (shizukuCheckPermission()) {
            try {
                return runProcess(exec(commandsToShCommand(commands)));
            } catch (Exception ignored) {
                return "";
            }
        }
        return "";
    }
}
