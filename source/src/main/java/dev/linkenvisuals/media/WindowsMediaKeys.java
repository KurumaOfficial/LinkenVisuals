package dev.linkenvisuals.media;

import dev.linkenvisuals.LinkenVisuals;

import java.awt.AWTException;
import java.awt.Robot;

public final class WindowsMediaKeys {

    private static final int VK_MEDIA_PREVIOUS = 0xB1;
    private static final int VK_MEDIA_NEXT = 0xB0;
    private static final int VK_MEDIA_PLAY_PAUSE = 0xB3;

    private WindowsMediaKeys() {
    }

    public static boolean send(MediaAction action) {
        if (!isWindows()) {
            return false;
        }
        int keyCode = switch (action) {
            case PREVIOUS -> VK_MEDIA_PREVIOUS;
            case NEXT -> VK_MEDIA_NEXT;
            case PLAY_PAUSE -> VK_MEDIA_PLAY_PAUSE;
        };
        try {
            Robot robot = new Robot();
            robot.keyPress(keyCode);
            robot.keyRelease(keyCode);
            return true;
        } catch (AWTException exception) {
            LinkenVisuals.LOGGER.debug("[MediaKeys] Failed to send {}", action, exception);
            return false;
        }
    }

    private static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase().contains("win");
    }

    public enum MediaAction {
        PREVIOUS,
        PLAY_PAUSE,
        NEXT
    }
}
