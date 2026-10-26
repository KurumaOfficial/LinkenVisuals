package dev.linkenvisuals.clickgui.link;

import com.sun.jna.Library;
import com.sun.jna.Native;
import dev.linkenvisuals.LinkenVisuals;

import java.awt.Robot;

/** Sends the OS media keys using Robot and Win32 keybd_event fallback so the read-only strip can control the active player. */
public final class LinkGuiMediaKeys {
    private static final int VK_MEDIA_NEXT_TRACK = 0xB0;
    private static final int VK_MEDIA_PREV_TRACK = 0xB1;
    private static final int VK_MEDIA_PLAY_PAUSE = 0xB3;
    private static final int KEYEVENTF_KEYUP = 0x0002;

    public interface User32Native extends Library {
        User32Native INSTANCE = Native.load("user32", User32Native.class);
        void keybd_event(byte bVk, byte bScan, int dwFlags, int dwExtraInfo);
    }

    private LinkGuiMediaKeys() {
    }

    public static void previous() {
        press(VK_MEDIA_PREV_TRACK);
    }

    public static void next() {
        press(VK_MEDIA_NEXT_TRACK);
    }

    public static void playPause() {
        press(VK_MEDIA_PLAY_PAUSE);
    }

    private static void press(int keyCode) {
        if (!isWindows()) {
            return;
        }
        try {
            Robot robot = new Robot();
            robot.keyPress(keyCode);
            robot.keyRelease(keyCode);
            return;
        } catch (Throwable failure) {
            LinkenVisuals.LOGGER.debug("[MediaKeys] Robot failed, falling back to keybd_event: {}", failure.getMessage());
        }
        try {
            User32Native.INSTANCE.keybd_event((byte) keyCode, (byte) 0, 0, 0);
            User32Native.INSTANCE.keybd_event((byte) keyCode, (byte) 0, KEYEVENTF_KEYUP, 0);
        } catch (Throwable failure) {
            LinkenVisuals.LOGGER.debug("[Visuals Modification] Media key unavailable: {}", failure.getMessage());
        }
    }

    private static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase().contains("win");
    }
}
