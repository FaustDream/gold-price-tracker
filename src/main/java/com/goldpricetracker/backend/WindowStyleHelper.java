package com.goldpricetracker.backend;

import com.sun.jna.platform.win32.User32;
import com.sun.jna.platform.win32.WinDef;

public class WindowStyleHelper {
    private static final int GWL_EXSTYLE = -20;
    private static final int WS_EX_TRANSPARENT = 0x20;
    private static final int WS_EX_LAYERED = 0x80000;

    public static void setTopMost(String windowTitle, boolean topMost) {
        User32 u = User32.INSTANCE;
        WinDef.HWND hwnd = u.FindWindow(null, windowTitle);
        if (hwnd == null) return;
        WinDef.HWND insertAfter = new WinDef.HWND(new com.sun.jna.Pointer(topMost ? -1 : -2));
        u.SetWindowPos(hwnd, insertAfter, 0, 0, 0, 0, 0x0013);
    }

    public static void setClickThrough(String windowTitle, boolean enable) {
        User32 u = User32.INSTANCE;
        WinDef.HWND hwnd = u.FindWindow(null, windowTitle);
        if (hwnd == null) return;
        int ex = u.GetWindowLong(hwnd, GWL_EXSTYLE);
        if (enable) {
            ex = ex | WS_EX_TRANSPARENT | WS_EX_LAYERED;
        } else {
            ex = ex & ~WS_EX_TRANSPARENT;
        }
        u.SetWindowLong(hwnd, GWL_EXSTYLE, ex);
        u.SetWindowPos(hwnd, new WinDef.HWND(new com.sun.jna.Pointer(-2)), 0, 0, 0, 0, 0x0027);
    }
}
