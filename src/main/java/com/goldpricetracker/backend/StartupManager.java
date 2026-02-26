package com.goldpricetracker.backend;

import java.io.File;
import java.io.IOException;

/**
 * 开机自启管理器
 * 
 * 功能：
 * 通过修改 Windows 注册表来实现应用程序的开机自启动。
 * 
 * 原理：
 * Windows 注册表路径 HKCU\Software\Microsoft\Windows\CurrentVersion\Run
 * 存放了当前用户登录时自动运行的程序列表。
 * 我们只需要在这个路径下添加一个键值对：
 *   Key: 应用程序名称 (GoldPriceTracker)
 *   Value: 应用程序的可执行文件路径 (.exe)
 */
public class StartupManager {
    // 注册表路径
    private static final String REG_KEY = "HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Run";
    // 注册表键名
    private static final String APP_NAME = "GoldPriceTracker";

    /**
     * 检查是否已开启开机自启
     * @return true 表示已开启，false 表示未开启
     */
    public static boolean isStartupEnabled() {
        try {
            // 执行 reg query 命令查询注册表
            // 如果命令执行成功 (exit code 0)，说明键值存在
            Process process = Runtime.getRuntime().exec("reg query " + REG_KEY + " /v " + APP_NAME);
            return process.waitFor() == 0;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 设置开机自启状态
     * @param enable true 开启，false 关闭
     */
    public static void setStartup(boolean enable) {
        try {
            if (enable) {
                // 1. 获取当前运行的 EXE 文件路径
                String exePath = getCurrentExePath();
                if (exePath == null) return;

                // 2. 构造 reg add 命令
                // /v: 键名
                // /t: 类型 (REG_SZ 字符串)
                // /d: 数据 (EXE 路径)
                // /f: 强制覆盖不提示
                String cmd = "reg add " + REG_KEY + " /v " + APP_NAME + " /t REG_SZ /d \"" + exePath + "\" /f";
                Runtime.getRuntime().exec(cmd);
            } else {
                // 构造 reg delete 命令删除键值
                String cmd = "reg delete " + REG_KEY + " /v " + APP_NAME + " /f";
                Runtime.getRuntime().exec(cmd);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * 获取当前进程的可执行文件路径
     * 
     * 在 Java 9 及以上版本，可以使用 ProcessHandle API 获取。
     * 这对于 jpackage 打包后的原生应用非常有用，因为它能直接返回 .exe 的路径，
     * 而不是 .jar 的路径。
     */
    private static String getCurrentExePath() {
        try {
            // ProcessHandle.current() 获取当前 JVM 进程
            // info().command() 获取启动该进程的可执行文件路径
            return ProcessHandle.current().info().command().orElse(null);
        } catch (Exception e) {
            return null;
        }
    }
}
