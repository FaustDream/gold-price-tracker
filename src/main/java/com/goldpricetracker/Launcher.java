package com.goldpricetracker;

import javax.swing.JOptionPane;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.io.File;
import java.net.URI;
import java.util.Date;

/**
 * 程序启动器 (Launcher)
 * 
 * 作用：
 * 这是一个“外壳”类，用于引导整个应用程序的启动。
 * 它的主要职责不是显示界面，而是做启动前的准备工作。
 * 
 * 为什么不直接用 MainApp 启动？
 * 1. 错误捕获：这里包裹了 try-catch，如果主程序崩了，这里能弹窗告诉用户发生了什么，而不是直接闪退。
 * 2. 日志重定向：setupLogging() 方法将控制台输出 (System.out) 重定向到了文件，
 *    这对排查打包后的 EXE 运行问题至关重要（因为 EXE 运行时通常看不到控制台）。
 * 3. 依赖加载：对于某些 JavaFX 版本，直接运行继承自 Application 的类可能会报错，
 *    用一个普通的 main 方法类来调用它可以规避这个问题。
 */
public class Launcher {
    public static void main(String[] args) {
        System.out.println("Launcher main starting...");
        // 1. 初始化日志系统 (重定向输出到文件)
        setupLogging();
        
        // 2. 设置全局未捕获异常处理器 (捕获非 UI 线程的崩溃)
        Thread.setDefaultUncaughtExceptionHandler((thread, throwable) -> {
            System.err.println("CRITICAL: Uncaught Exception in thread " + thread.getName());
            throwable.printStackTrace();
            // 可以选择在这里记录更详细的错误到日志
        });
        
        try {
            // 3. 启动 JavaFX 主程序
            MainApp.main(args);
        } catch (Throwable t) {
            // 4. 启动时异常捕获
            t.printStackTrace(); // 堆栈信息将写入日志文件
            
            // 弹出原生 Swing 错误框通知用户 (Swing 依赖较少，适合做崩溃弹窗)
            JOptionPane.showMessageDialog(null, 
                "程序发生严重错误: " + t.toString() + "\n详细信息已保存至 gold_price_tracker.log", 
                "错误", 
                JOptionPane.ERROR_MESSAGE);
            System.exit(1);
        }
    }

    /**
     * 配置日志输出
     * 将 System.out 和 System.err 重定向到 gold_price_tracker.log 文件
     */
    private static void setupLogging() {
        try {
            // 获取程序运行所在的目录，确保日志文件生成在软件同目录下
            String jarPath = Launcher.class.getProtectionDomain().getCodeSource().getLocation().toURI().getPath();
            File jarFile = new File(jarPath);
            File logFile = new File(jarFile.getParent(), "gold_price_tracker.log");
            
            // 如果是在开发环境下（target/classes），则直接用当前目录
            if (!jarFile.getName().endsWith(".jar")) {
                logFile = new File("gold_price_tracker.log");
            }

            // 追加模式 (true)，每次启动不覆盖旧日志
            FileOutputStream fos = new FileOutputStream(logFile, true);
            PrintStream ps = new PrintStream(fos, true);
            
            // 重定向标准输出和标准错误输出
            System.setOut(ps);
            System.setErr(ps);
            
            // 打印启动分割线和环境信息
            System.out.println("\n==================================================");
            System.out.println("Application Started at " + new Date());
            System.out.println("Log file location: " + logFile.getAbsolutePath());
            System.out.println("Java version: " + System.getProperty("java.version"));
            System.out.println("OS: " + System.getProperty("os.name") + " " + System.getProperty("os.version"));
            System.out.println("Working Directory: " + System.getProperty("user.dir"));
            System.out.println("==================================================");
        } catch (Exception e) {
            e.printStackTrace(); // 至少打印到控制台
            try {
                JOptionPane.showMessageDialog(null, "日志系统初始化失败: " + e.getMessage(), "警告", JOptionPane.WARNING_MESSAGE);
            } catch (Exception ignored) {}
        }
    }
}
