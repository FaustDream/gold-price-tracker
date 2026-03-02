package com.goldpricetracker.backend;

import com.sun.jna.platform.win32.User32;
import com.sun.jna.platform.win32.WinDef;
import javafx.geometry.Rectangle2D;
import javafx.stage.Screen;

/**
 * 任务栏定位工具类 (基于 JNA)
 */
public class TaskbarLocator {

    public static void setWindowTopMost(String windowTitle) {
        User32 user32 = User32.INSTANCE;
        WinDef.HWND hwnd = user32.FindWindow(null, windowTitle);
        if (hwnd != null) {
            // SWP_NOSIZE | SWP_NOMOVE | SWP_NOACTIVATE = 0x0001 | 0x0002 | 0x0010 = 0x0013
            // HWND_TOPMOST = -1
            user32.SetWindowPos(hwnd, new WinDef.HWND(new com.sun.jna.Pointer(-1)), 0, 0, 0, 0, 0x0013);
        }
    }

    /**
     * 将窗口嵌入到任务栏 (通过 SetParent) - 暂时禁用，会导致坐标系统混乱
     */
    public static void embedIntoTaskbar(String windowTitle) {
        // 物理嵌入会导致 JavaFX 坐标系与 Windows 父窗口坐标系冲突，
        // 进而导致窗口飞出屏幕或不可见，引发“报错”假象。
        // 目前回退此功能，采用强力置顶策略。
        System.out.println("Embed: Physical embedding disabled due to stability issues.");
    }

    public static Point getDockLocation(double windowWidth, double windowHeight) {
        System.out.println("TaskbarLocator: Calculating dock location for window size: " + windowWidth + "x" + windowHeight);
        try {
            Point jnaPoint = getJnaDockLocation(windowWidth, windowHeight);
            if (jnaPoint != null) {
                System.out.println("TaskbarLocator: JNA location found: " + jnaPoint.x + ", " + jnaPoint.y);
                return jnaPoint;
            }
        } catch (Throwable e) {
            e.printStackTrace();
        }
        System.out.println("TaskbarLocator: Fallback to JavaFX Screen API");
        return getJavaFXDockLocation(windowWidth, windowHeight);
    }

    private static Point getJnaDockLocation(double windowWidth, double windowHeight) {
        User32 user32 = User32.INSTANCE;
        WinDef.HWND taskbarHwnd = user32.FindWindow("Shell_TrayWnd", null);
        if (taskbarHwnd == null) {
            System.out.println("TaskbarLocator: Shell_TrayWnd not found");
            return null;
        }

        WinDef.RECT taskbarRect = new WinDef.RECT();
        if (!user32.GetWindowRect(taskbarHwnd, taskbarRect)) {
            System.out.println("TaskbarLocator: Failed to get Shell_TrayWnd rect");
            return null;
        }

        WinDef.HWND trayHwnd = user32.FindWindowEx(taskbarHwnd, null, "TrayNotifyWnd", null);
        WinDef.RECT trayRect = new WinDef.RECT();
        boolean hasTray = false;
        if (trayHwnd != null && user32.GetWindowRect(trayHwnd, trayRect)) {
            hasTray = true;
        } else {
            System.out.println("TaskbarLocator: TrayNotifyWnd not found or rect invalid");
        }

        double scaleX = getScaleFromDpi();
        double scaleY = scaleX;

        double tbLeft = taskbarRect.left / scaleX;
        double tbTop = taskbarRect.top / scaleY;
        double tbRight = taskbarRect.right / scaleX;
        double tbBottom = taskbarRect.bottom / scaleY;
        
        double trayLeft = hasTray ? trayRect.left / scaleX : tbRight;
        boolean isHorizontal = (tbRight - tbLeft) > (tbBottom - tbTop);
        System.out.printf("TaskbarLocator: Taskbar Rect (Scaled): [%f, %f, %f, %f]%n", tbLeft, tbTop, tbRight, tbBottom);
        System.out.printf("TaskbarLocator: Tray Left (Scaled): %f%n", trayLeft);
        
        double x, y;
        if (isHorizontal) {
            // 水平任务栏 (底部/顶部)
            // 目标位置：托盘区左侧
            x = trayLeft - windowWidth - 2; 
            
            if (tbTop > 0) { // 底部任务栏
                double tbHeight = tbBottom - tbTop;
                // 垂直居中
                y = tbTop + (tbHeight - windowHeight) / 2;
                
                // 再次修正：如果垂直居中导致底部超出屏幕 (tbBottom)，则向上推
                if (y + windowHeight > tbBottom) {
                    y = tbBottom - windowHeight;
                }
            } else { // 顶部任务栏
                double tbHeight = tbBottom - tbTop;
                y = tbTop + (tbHeight - windowHeight) / 2;
            }
        } else {
            // 垂直任务栏 (左/右)
            x = tbLeft + 5;
            y = tbBottom - windowHeight - 5;
        }
        System.out.println("TaskbarLocator: Computed JNA Location: " + x + ", " + y);
        return new Point(x, y);
    }

    private static Point getJavaFXDockLocation(double w, double h) {
        Rectangle2D screen = Screen.getPrimary().getBounds();
        Rectangle2D visual = Screen.getPrimary().getVisualBounds();
        
        // 推断任务栏位置
        double x, y;
        
        // 底部任务栏: visual.maxY < screen.maxY
        if (visual.getMaxY() < screen.getMaxY()) {
            double tbHeight = screen.getMaxY() - visual.getMaxY();
            double tbTop = visual.getMaxY();
            // 垂直居中
            x = visual.getMaxX() - w - 2; 
            y = tbTop + (tbHeight - h) / 2;
        } 
        // 顶部任务栏: visual.minY > screen.minY
        else if (visual.getMinY() > screen.getMinY()) {
            double tbHeight = visual.getMinY() - screen.getMinY();
            x = visual.getMaxX() - w - 2;
            y = (tbHeight - h) / 2;
        }
        // 右侧任务栏: visual.maxX < screen.maxX
        else if (visual.getMaxX() < screen.getMaxX()) {
            x = visual.getMaxX() + 5;
            y = visual.getMaxY() - h - 5;
        }
        // 左侧任务栏: visual.minX > screen.minX
        else if (visual.getMinX() > screen.getMinX()) {
            x = visual.getMinX() + 5;
            y = visual.getMaxY() - h - 5;
        }
        // 无法判断 (比如全屏模式)，放在右下角
        else {
            x = screen.getMaxX() - w - 10;
            y = screen.getMaxY() - h - 10;
        }
        
        return new Point(x, y);
    }

    public static Rectangle2D getTaskbarBounds() {
        try {
            // 优先使用 JNA
            User32 user32 = User32.INSTANCE;
            WinDef.HWND taskbarHwnd = user32.FindWindow("Shell_TrayWnd", null);
            if (taskbarHwnd != null) {
                WinDef.RECT taskbarRect = new WinDef.RECT();
                if (user32.GetWindowRect(taskbarHwnd, taskbarRect)) {
                    double scale = getScaleFromDpi();
                    return new Rectangle2D(
                        taskbarRect.left / scale,
                        taskbarRect.top / scale,
                        (taskbarRect.right - taskbarRect.left) / scale,
                        (taskbarRect.bottom - taskbarRect.top) / scale
                    );
                }
            }
        } catch (Throwable ignored) {}

        // 降级使用 JavaFX VisualBounds
        Rectangle2D screen = Screen.getPrimary().getBounds();
        Rectangle2D visual = Screen.getPrimary().getVisualBounds();
        
        if (visual.getMaxY() < screen.getMaxY()) { // Bottom
            return new Rectangle2D(0, visual.getMaxY(), screen.getWidth(), screen.getMaxY() - visual.getMaxY());
        } else if (visual.getMinY() > screen.getMinY()) { // Top
            return new Rectangle2D(0, 0, screen.getWidth(), visual.getMinY());
        }
        // 其他情况简化处理
        return new Rectangle2D(0, visual.getMaxY(), screen.getWidth(), 40); 
    }

    public static double getScaleFromDpi() {
        try {
            double dpi = Screen.getPrimary().getDpi();
            double outputScaleX = Screen.getPrimary().getOutputScaleX();
            System.out.println("TaskbarLocator: Screen DPI=" + dpi + ", OutputScaleX=" + outputScaleX);
            
            // 如果 outputScaleX 是 1.0，但 DPI 明显很大 (> 120, 约 125%)，说明 JavaFX 可能没正确识别缩放
            // 此时应该信任 DPI 计算出的缩放比
            if (outputScaleX == 1.0 && dpi > 120) {
                double scale = Math.round(dpi / 96.0 * 4.0) / 4.0;
                System.out.println("TaskbarLocator: Using DPI-calculated scale: " + scale);
                return (scale > 0 && scale <= 4.0) ? scale : 1.0;
            }
            
            // 否则优先使用 outputScaleX
            if (outputScaleX > 0) return outputScaleX;
            
            // Fallback
            if (dpi > 0) {
                double scale = Math.round(dpi / 96.0 * 4.0) / 4.0;
                return (scale > 0 && scale <= 4.0) ? scale : 1.0;
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
