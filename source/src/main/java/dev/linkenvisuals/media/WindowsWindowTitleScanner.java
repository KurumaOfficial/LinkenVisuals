package dev.linkenvisuals.media;

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
            if (!mediaTitle) {
                return null;
            }
            score += 60;
        } else if (!mediaTitle) {
            return null;
        }

        String cleaned = cleanTitle(title);
        ParsedTrack parsed = splitArtistTitle(cleaned);
        return new WindowMediaCandidate(processName, parsed.title(), parsed.artist(), score);
    }

    private static boolean isIgnoredWindow(String lowerProcess, String lowerTitle) {
        if (lowerProcess.contains("explorer") || lowerProcess.contains("taskmgr") || lowerProcess.contains("cmd") || lowerProcess.contains("powershell")) {
            return true;
        }
        return lowerTitle.equals("settings")
                || lowerTitle.equals("параметры")
                || lowerTitle.equals("диспетчер задач")
                || lowerTitle.startsWith("nvidia")
                || lowerTitle.startsWith("discord");
    }

    private static String cleanTitle(String raw) {
        String cleaned = raw.replaceAll("(?i)\\s*-\\s*(youtube|spotify|яндекс музыка|yandex music|soundcloud|google chrome|microsoft edge|opera|mozilla firefox)$", "");
        return cleaned.trim();
    }

    private static ParsedTrack splitArtistTitle(String cleaned) {
        String[] separators = {" - ", " — ", " – "};
        for (String sep : separators) {
            int idx = cleaned.indexOf(sep);
            if (idx > 0 && idx + sep.length() < cleaned.length()) {
                String artist = cleaned.substring(0, idx).trim();
                String title = cleaned.substring(idx + sep.length()).trim();
                return new ParsedTrack(title, artist);
            }
        }
        return new ParsedTrack(cleaned, "");
    }

    private static boolean containsAny(String text, String... values) {
        for (String value : values) {
            if (text.contains(value)) {
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

    private record ParsedTrack(String title, String artist) {
    }
}
