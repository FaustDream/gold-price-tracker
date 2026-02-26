package com.goldpricetracker;

import javax.swing.JOptionPane;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.io.File;
import java.util.Date;

public class Launcher {
    public static void main(String[] args) {
        setupLogging();
        try {
            MainApp.main(args);
        } catch (Throwable t) {
            t.printStackTrace(); // 堆栈信息将写入日志文件
            // 弹出错误框通知用户
            JOptionPane.showMessageDialog(null, 
                "程序发生严重错误: " + t.toString() + "\n详细信息已保存至 gold_price_tracker.log", 
                "错误", 
                JOptionPane.ERROR_MESSAGE);
            System.exit(1);
        }
    }

    private static void setupLogging() {
        try {
            // 日志文件保存在当前运行目录下
            File logFile = new File("gold_price_tracker.log");
            // 追加模式，自动刷新
            FileOutputStream fos = new FileOutputStream(logFile, true);
            PrintStream ps = new PrintStream(fos, true);
            
            // 重定向标准输出和标准错误输出
            System.setOut(ps);
            System.setErr(ps);
            
            System.out.println("\n==================================================");
            System.out.println("Application Started at " + new Date());
            System.out.println("==================================================");
        } catch (Exception e) {
            // 如果日志初始化失败，尝试弹窗提醒（虽然此时可能连弹窗都弹不出来，但尽力而为）
            try {
                JOptionPane.showMessageDialog(null, "日志系统初始化失败: " + e.getMessage(), "警告", JOptionPane.WARNING_MESSAGE);
            } catch (Exception ignored) {}
        }
    }
}
