package com.goldpricetracker.backend;

import java.io.File;
import java.io.IOException;

public class StartupManager {
    private static final String REG_KEY = "HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Run";
    private static final String APP_NAME = "GoldPriceTracker";

    public static boolean isStartupEnabled() {
        try {
            Process process = Runtime.getRuntime().exec("reg query " + REG_KEY + " /v " + APP_NAME);
            return process.waitFor() == 0;
        } catch (Exception e) {
            return false;
        }
    }

    public static void setStartup(boolean enable) {
        try {
            if (enable) {
                // 获取当前运行的 EXE 路径
                // 注意：在开发环境中这可能指向 java.exe，但在 jpackage 打包后会指向真实的 EXE
                String exePath = getCurrentExePath();
                if (exePath == null) return;

                String cmd = "reg add " + REG_KEY + " /v " + APP_NAME + " /t REG_SZ /d \"" + exePath + "\" /f";
                Runtime.getRuntime().exec(cmd);
            } else {
                String cmd = "reg delete " + REG_KEY + " /v " + APP_NAME + " /f";
                Runtime.getRuntime().exec(cmd);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static String getCurrentExePath() {
        try {
            // 获取当前 JAR 或 EXE 的路径
            String path = StartupManager.class.getProtectionDomain().getCodeSource().getLocation().toURI().getPath();
            File file = new File(path);
            
            // 如果是开发环境 (target/classes)，不处理
            if (file.isDirectory()) return null;
            
            // jpackage 打包后的结构通常是:
            // App/GoldPriceTracker.exe
            // App/app/gold-price-tracker.jar
            // 所以我们需要向上查找 EXE
            
            // 简单策略：假设 EXE 在 JAR 的上级目录或同级目录
            // 但最稳妥的方式是获取启动进程的路径 (Java 9+ ProcessHandle)
            // ProcessHandle.current().info().command().orElse(null);
            
            // 由于我们用的是 Java 17，可以直接用 ProcessHandle
            return ProcessHandle.current().info().command().orElse(null);
            
        } catch (Exception e) {
            return null;
        }
    }
}
