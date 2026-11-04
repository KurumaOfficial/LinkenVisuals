package ru.method.linkenvisuals.network;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.concurrent.TimeUnit;

/**
 * HWID = SHA-256 от связки «серийник материнской платы + ProcessorId процессора».
 *
 * <p>Windows: тянется через WMI — {@code Win32_BaseBoard.SerialNumber} и {@code Win32_Processor.ProcessorId}
 * (сначала PowerShell CIM, фолбэк на устаревший {@code wmic}). На других ОС/при ошибке — деградирует до
 * связки os/arch/user/hostname, чтобы не падать.
 *
 * <p>Значение считается один раз (спавн процесса ~сотни мс) и кешируется. {@link #warmUp()} прогревает
 * его в фоне на старте, чтобы {@link #get()} потом не блокировал игровой поток.
 */
public final class HwidUtil {

    private static volatile String cached;

    private HwidUtil() {
    }

    /** Прогреть HWID в фоновом потоке (вызвать один раз на старте). */
    public static void warmUp() {
        Thread t = new Thread(HwidUtil::get, "LinkenVisuals-HWID");
        t.setDaemon(true);
        t.start();
    }

    /** HWID (SHA-256, hex). Первый вызов может блокировать на время WMI-запроса; далее — из кеша. */
    public static String get() {
        String value = cached;
        if (value != null) {
            return value;
        }
        synchronized (HwidUtil.class) {
            if (cached == null) {
                cached = sha256(rawHardwareString());
            }
            return cached;
        }
    }

    /** «serial|processorId» железа, либо OS-фолбэк, если не удалось прочитать. */
    private static String rawHardwareString() {
        String hw = readMotherboardAndCpu();
        if (hw != null && !hw.replace("|", "").isBlank()) {
            return hw;
        }
        // Фолбэк: не железо, но стабильно в пределах машины/пользователя.
        return String.join("|",
                System.getProperty("os.name", ""),
                System.getProperty("os.arch", ""),
                System.getProperty("user.name", ""),
                hostname());
    }

    /** Возвращает «boardSerial|cpuProcessorId» (Windows через WMI) или null. */
    private static String readMotherboardAndCpu() {
        String os = System.getProperty("os.name", "").toLowerCase();
        if (!os.contains("win")) {
            return null; // сервер/железо клиентов artygrief — Windows; для других ОС уйдём в фолбэк
        }
        // 1) PowerShell CIM (wmic удаляют из Win11) — один вызов, чистый вывод «serial|processorId».
        String ps = exec(new String[]{
                "powershell", "-NoProfile", "-NonInteractive", "-Command",
                "$b=(Get-CimInstance Win32_BaseBoard).SerialNumber; "
                        + "$c=(Get-CimInstance Win32_Processor | Select-Object -First 1).ProcessorId; "
                        + "$u=(Get-CimInstance Win32_ComputerSystemProduct).UUID; "
                        + "Write-Output \"$b|$c|$u\""
        });
        if (ps != null) {
            String line = firstNonBlank(ps);
            if (line != null && line.contains("|") && !line.replace("|", "").isBlank()) {
                return line;
            }
        }
        // 2) Фолбэк: устаревший wmic (шапку столбца пропускаем).
        String board = firstValue(exec(new String[]{"wmic", "baseboard", "get", "serialnumber"}), "SerialNumber");
        String cpu = firstValue(exec(new String[]{"wmic", "cpu", "get", "processorid"}), "ProcessorId");
        String uuid = firstValue(exec(new String[]{"wmic", "csproduct", "get", "uuid"}), "UUID");
        if (board != null || cpu != null || uuid != null) {
            return (board == null ? "" : board) + "|" + (cpu == null ? "" : cpu) + "|" + (uuid == null ? "" : uuid);
        }
        return null;
    }

    private static String exec(String[] cmd) {
        try {
            Process p = new ProcessBuilder(cmd).redirectErrorStream(true).start();
            StringBuilder out = new StringBuilder();
            try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = r.readLine()) != null) {
                    out.append(line).append('\n');
                }
            }
            p.waitFor(6, TimeUnit.SECONDS);
            return out.toString();
        } catch (Throwable t) {
            return null;
        }
    }

    private static String firstNonBlank(String out) {
        if (out == null) return null;
        for (String line : out.split("\\R")) {
            String t = line.trim();
            if (!t.isEmpty()) return t;
        }
        return null;
    }

    /** Первая непустая строка вывода wmic, кроме шапки столбца. */
    private static String firstValue(String out, String header) {
        if (out == null) return null;
        for (String line : out.split("\\R")) {
            String t = line.trim();
            if (t.isEmpty() || t.equalsIgnoreCase(header)) continue;
            return t;
        }
        return null;
    }

    private static String hostname() {
        try {
            String env = System.getenv("COMPUTERNAME");
            if (env != null && !env.isBlank()) return env;
            return java.net.InetAddress.getLocalHost().getHostName();
        } catch (Throwable t) {
            return "";
        }
    }

    private static String sha256(String raw) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(raw.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                sb.append(Character.forDigit((b >> 4) & 0xF, 16));
                sb.append(Character.forDigit(b & 0xF, 16));
            }
            return sb.toString();
        } catch (Throwable t) {
            return "unknown";
        }
    }
}
