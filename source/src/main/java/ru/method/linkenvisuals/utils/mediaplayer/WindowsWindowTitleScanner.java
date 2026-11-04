package ru.method.linkenvisuals.utils.mediaplayer;

import com.sun.jna.Native;
import com.sun.jna.platform.win32.Kernel32Util;
import com.sun.jna.platform.win32.User32;
import com.sun.jna.platform.win32.WinDef;
import com.sun.jna.platform.win32.WinUser;
import com.sun.jna.ptr.IntByReference;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Last-resort media detection from visible window titles when SMTC sessions are unavailable.
 */
public final class WindowsWindowTitleScanner {

    private static final int MAX_TITLE_LENGTH = 512;

    private WindowsWindowTitleScanner() {
    }

    public static WindowMediaCandidate scanBestCandidate() {
        if (!isWindows()) {
            return null;
        }

        List<WindowMediaCandidate> candidates = new ArrayList<>();
        User32 user32 = User32.INSTANCE;
        user32.EnumWindows((WinDef.HWND hwnd, com.sun.jna.Pointer data) -> {
            if (!user32.IsWindowVisible(hwnd)) {
                return true;
            }
            if (user32.GetWindow(hwnd, new WinDef.DWORD(WinUser.GW_OWNER)) != null) {
                return true;
            }

            char[] titleBuffer = new char[MAX_TITLE_LENGTH];
            int titleLength = user32.GetWindowText(hwnd, titleBuffer, titleBuffer.length);
            if (titleLength <= 0) {
                return true;
            }

            String title = Native.toString(titleBuffer).trim();
            if (title.isEmpty()) {
                return true;
            }

            String processName = resolveProcessName(user32, hwnd);
            WindowMediaCandidate candidate = parseCandidate(processName, title);
            if (candidate != null) {
                candidates.add(candidate);
            }
            return true;
        }, null);

        return candidates.stream()
                .max(Comparator.comparingInt(WindowMediaCandidate::score))
                .orElse(null);
    }

    private static String resolveProcessName(User32 user32, WinDef.HWND hwnd) {
        IntByReference processId = new IntByReference();
        user32.GetWindowThreadProcessId(hwnd, processId);
        int pid = processId.getValue();
        if (pid <= 0) {
            return "unknown";
        }

        try {
            String path = Kernel32Util.QueryFullProcessImageName(pid, 0);
            if (path == null || path.isBlank()) {
                return "pid-" + pid;
            }
            int slash = Math.max(path.lastIndexOf('\\'), path.lastIndexOf('/'));
            return slash >= 0 ? path.substring(slash + 1) : path;
        } catch (Throwable ignored) {
            return "pid-" + pid;
        }
    }

    static WindowMediaCandidate parseCandidate(String processName, String title) {
        if (processName == null || title == null || title.isBlank()) {
            return null;
        }

        String lowerProcess = processName.toLowerCase(Locale.ROOT);
        String lowerTitle = title.toLowerCase(Locale.ROOT);
        if (isIgnoredWindow(lowerProcess, lowerTitle)) {
            return null;
        }

        int score = 0;
        boolean mediaTitle = containsAny(lowerTitle, "youtube", "spotify", "яндекс музыка", "yandex music", "soundcloud", "музыка");
        if (containsAny(lowerProcess, "spotify")) score += 100;
        if (containsAny(lowerProcess, "yandex", "music", "aimp", "foobar", "vlc", "winamp")) score += 85;
        if (containsAny(lowerProcess, "chrome", "msedge", "firefox", "opera", "zen", "browser")) {
            // Браузер по одному имени процесса — не признак музыки: без медиа-маркеров в заголовке
            // это случайная вкладка (новости, почта, «Today …») — такие окна не берём вообще.
            if (!mediaTitle) {
                return null;
            }
            score += 55;
        }
        if (mediaTitle) score += 25;
        if (title.contains(" - ") || title.contains(" — ")) score += 10;
        if (score <= 0) {
            return null;
        }

        String cleanedTitle = cleanWindowMediaTitle(title);
        if (cleanedTitle.isBlank()) {
            return null;
        }
        return new WindowMediaCandidate(processName, cleanedTitle, processName, score);
    }

    private static boolean isIgnoredWindow(String lowerProcess, String lowerTitle) {
        if (containsAny(lowerProcess, "java", "minecraft", "tlauncher", "javaw")) return true;
        if (containsAny(lowerTitle, "minecraft", "linkenvisuals", "primevisuals")) return true;
        return lowerTitle.equals("settings") || lowerTitle.equals("параметры");
    }

    static String cleanWindowMediaTitle(String title) {
        String cleaned = title.trim();
        String yandexPrefix = "Яндекс Музыка —";
        if (cleaned.startsWith(yandexPrefix)) {
            cleaned = cleaned.substring(yandexPrefix.length()).trim();
        }

        String[] suffixes = {
                " - Google Chrome",
                " - Яндекс Браузер",
                " - Yandex Browser",
                " - Microsoft Edge",
                " - Mozilla Firefox",
                " - Opera",
                " - YouTube"
        };
        for (String suffix : suffixes) {
            if (cleaned.endsWith(suffix)) {
                cleaned = cleaned.substring(0, cleaned.length() - suffix.length()).trim();
            }
        }

        return cleaned.length() > 96 ? cleaned.substring(0, 96).trim() : cleaned;
    }

    private static boolean containsAny(String value, String... needles) {
        for (String needle : needles) {
            if (needle != null && !needle.isEmpty() && value.contains(needle)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
    }

    public record WindowMediaCandidate(String owner, String title, String artist, int score) {
    }
}
