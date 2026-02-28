package com.goldpricetracker.backend;

import com.sun.jna.platform.win32.User32;
import com.sun.jna.platform.win32.WinDef;
import javafx.geometry.Rectangle2D;
import javafx.stage.Screen;

/**
 * 任务栏定位工具类 (基于 JNA)
 * 
 * 功能：
 * 1. 查找 Windows 任务栏的句柄 (Shell_TrayWnd)。
 * 2. 获取任务栏的位置和尺寸。
 * 3. 查找系统托盘区域 (TrayNotifyWnd) 的位置，以便将程序停靠在它旁边。
 */
public class TaskbarLocator {

    /**
     * 获取任务栏的有效停靠区域
     * 
     * 策略：
     * 我们试图将窗口放置在任务栏的“托盘区”左侧，或者如果没有托盘区，就放在任务栏的最右侧。
     * 
     * @param windowWidth 我们的窗口宽度
     * @param windowHeight 我们的窗口高度
     * @return 屏幕坐标 (X, Y)
     */
    public static Point getDockLocation(double windowWidth, double windowHeight) {
        try {
            User32 user32 = User32.INSTANCE;

            // 1. 查找主任务栏窗口
            WinDef.HWND taskbarHwnd = user32.FindWindow("Shell_TrayWnd", null);
            if (taskbarHwnd == null) {
                return getDefaultLocation(windowWidth, windowHeight);
            }

            // 2. 获取任务栏的矩形区域
            WinDef.RECT taskbarRect = new WinDef.RECT();
            if (!user32.GetWindowRect(taskbarHwnd, taskbarRect)) {
                return getDefaultLocation(windowWidth, windowHeight);
            }

            // 3. 尝试查找托盘通知区域 (TrayNotifyWnd)
            WinDef.HWND trayHwnd = user32.FindWindowEx(taskbarHwnd, null, "TrayNotifyWnd", null);
            
            // 5. 关键：查找托盘区域的"溢出按钮" (Button)，通常在 TrayNotifyWnd 的最左侧
            // 如果能找到 TrayNotifyWnd，就以它的左边界为基准
            
            WinDef.RECT trayRect = new WinDef.RECT();
            boolean hasTray = false;
            
            if (trayHwnd != null) {
                if (user32.GetWindowRect(trayHwnd, trayRect)) {
                    hasTray = true;
                }
            }

            // 6. 计算 DPI 缩放比例 (JavaFX 没有公开的 OutputScale API，使用 DPI 推断缩放倍数)
            double scaleX = getScaleFromDpi();
            double scaleY = scaleX;

            // 转换物理像素到逻辑像素
            double tbLeft = taskbarRect.left / scaleX;
            double tbTop = taskbarRect.top / scaleY;
            double tbRight = taskbarRect.right / scaleX;
            double tbBottom = taskbarRect.bottom / scaleY;
            
            // trayLeft 是托盘区域的最左侧 (即小三角按钮的左边)
            double trayLeft = hasTray ? trayRect.left / scaleX : tbRight;
            
            // 判断任务栏是在底部、顶部、左侧还是右侧
            boolean isHorizontal = (tbRight - tbLeft) > (tbBottom - tbTop);
            
            double x, y;
            
            if (isHorizontal) {
                // 任务栏在底部或顶部
                // 目标位置：托盘区域的左侧 - 窗口宽度
                // 减去 2px 是为了留一点缝隙，不紧贴小三角
                x = trayLeft - windowWidth - 2; 
                
                if (tbTop > 0) { 
                    // 任务栏在底部
                    double tbHeight = tbBottom - tbTop;
                    y = tbTop + (tbHeight - windowHeight) / 2;
                } else {
                    // 任务栏在顶部
                    y = tbBottom - windowHeight - (tbBottom - tbTop - windowHeight) / 2;
                }
            } else {
                // 垂直任务栏 (左侧或右侧) - 这种情况较少见，简单处理放在底部
                x = tbLeft + 5;
                y = tbBottom - windowHeight - 5;
            }

            return new Point(x, y);
        } catch (Exception e) {
            // JNA 调用可能会因为各种原因失败 (如权限、系统环境等)
            // 捕获所有异常，确保不会因为定位失败而导致程序崩溃，降级为默认位置
            System.err.println("JNA Taskbar detection failed: " + e.getMessage());
            return getDefaultLocation(windowWidth, windowHeight);
        }
    }

    private static Point getDefaultLocation(double w, double h) {
        Rectangle2D bounds = Screen.getPrimary().getVisualBounds();
        return new Point(bounds.getMaxX() - w - 10, bounds.getMaxY() - h - 10);
    }

    // 基于屏幕 DPI 估算缩放倍数（Windows 标准 DPI 为 96）
    private static double getScaleFromDpi() {
        try {
            double dpi = Screen.getPrimary().getDpi();
            if (dpi > 0) {
                double scale = dpi / 96.0;
                // 防御性处理，避免异常值
                if (scale < 0.5) return 1.0;
                if (scale > 4.0) return 1.0;
                return scale;
            }
        } catch (Exception ignored) {}
        return 1.0;
    }

    public static class Point {
        public double x;
        public double y;
        public Point(double x, double y) { this.x = x; this.y = y; }
    }
}
