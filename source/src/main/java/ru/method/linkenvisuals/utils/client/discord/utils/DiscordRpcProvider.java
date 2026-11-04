package ru.method.linkenvisuals.utils.client.discord.utils;

import com.sun.jna.Native;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;

public final class DiscordRpcProvider {
    private static final String LIBRARY_FILE_NAME = "discord-rpc.dll";

    private static final Object LOCK = new Object();
    private static volatile DiscordRPC instance;
    private static volatile Throwable loadError;
    private static volatile Path extractedLibraryPath;

    private DiscordRpcProvider() {
    }

    public static DiscordRPC get() {
        DiscordRPC existing = instance;
        if (existing != null) return existing;
        if (loadError != null) return null;

        synchronized (LOCK) {
            existing = instance;
            if (existing != null) return existing;
            if (loadError != null) return null;
            try {
                Path extractedLibrary = extractBundledLibrary();
                extractedLibraryPath = extractedLibrary;
                instance = Native.load(extractedLibrary.toAbsolutePath().toString(), DiscordRPC.class);
                return instance;
            } catch (Throwable t) {
                loadError = t;
                return null;
            }
        }
    }

    public static Throwable getLoadError() {
        return loadError;
    }

    public static Path getExtractedLibraryPath() {
        return extractedLibraryPath;
    }

    public static void resetLoadError() {
        synchronized (LOCK) {
            loadError = null;
            instance = null;
        }
    }

    private static Path extractBundledLibrary() throws IOException {
        String resourcePath = resolveResourcePath();
        if (resourcePath == null) {
            throw new IOException("Unsupported platform for bundled Discord RPC library");
        }

        Path targetDir = Path.of(System.getProperty("java.io.tmpdir"), "linkenvisuals", "discord-rpc");
        Files.createDirectories(targetDir);

        byte[] bytes;
        try (InputStream stream = DiscordRpcProvider.class.getResourceAsStream(resourcePath)) {
            if (stream == null) {
                throw new IOException("Bundled Discord RPC library not found: " + resourcePath);
            }
            bytes = stream.readAllBytes();
        }

        // Content-addressed имя (discord-rpc-<crc>.dll), чтобы две копии игры не дрались за один
        // залоченный discord-rpc.dll: вторая копия просто грузит уже готовый файл первой
        // (Native.load одного DLL из нескольких процессов допустим). Старую фикс-схему
        // (Files.copy REPLACE_EXISTING поверх залоченного) она заменяет.
        Path targetFile = targetDir.resolve(cacheFileName(contentTag(bytes)));
        if (Files.isRegularFile(targetFile) && sameSize(targetFile, bytes.length)) {
            cleanupStale(targetDir, targetFile.getFileName().toString());
            return targetFile;
        }

        Path tmp = Files.createTempFile(targetDir, "discord-rpc-", ".part");
        try {
            Files.write(tmp, bytes);
            try {
                Files.move(tmp, targetFile, StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException moveErr) {
                if (!(Files.isRegularFile(targetFile) && sameSize(targetFile, bytes.length))) {
                    try {
                        Files.move(tmp, targetFile, StandardCopyOption.REPLACE_EXISTING);
                    } catch (IOException replaceErr) {
                        if (!Files.isRegularFile(targetFile)) {
                            throw replaceErr;
                        }
                        // Уже на месте (залочен другой копией) — используем его.
                    }
                }
            }
        } finally {
            try {
                Files.deleteIfExists(tmp);
            } catch (IOException ignored) {
                // подчистится при следующем запуске
            }
        }
        cleanupStale(targetDir, targetFile.getFileName().toString());
        return targetFile;
    }

    private static String cacheFileName(String tag) {
        int dot = LIBRARY_FILE_NAME.lastIndexOf('.');
        String stem = dot > 0 ? LIBRARY_FILE_NAME.substring(0, dot) : LIBRARY_FILE_NAME;
        String ext = dot > 0 ? LIBRARY_FILE_NAME.substring(dot) : "";
        return stem + "-" + tag + ext;
    }

    private static String contentTag(byte[] bytes) {
        java.util.zip.CRC32 crc = new java.util.zip.CRC32();
        crc.update(bytes, 0, bytes.length);
        return Long.toHexString(crc.getValue());
    }

    private static boolean sameSize(Path file, long expected) {
        try {
            return Files.size(file) == expected;
        } catch (IOException e) {
            return false;
        }
    }

    /** Удаляет discord-rpc библиотеки прошлых билдов и легаси фикс-имя; занятые пропускает. */
    private static void cleanupStale(Path dir, String keepName) {
        try {
            Files.deleteIfExists(dir.resolve(LIBRARY_FILE_NAME)); // легаси discord-rpc.dll
        } catch (IOException ignored) {
            // залочен работающей копией — не критично
        }
        try (java.nio.file.DirectoryStream<Path> s = Files.newDirectoryStream(dir, "discord-rpc-*")) {
            for (Path p : s) {
                String n = p.getFileName().toString();
                if (n.equals(keepName) || n.endsWith(".part")) {
                    continue;
                }
                try {
                    Files.deleteIfExists(p);
                } catch (IOException ignored) {
                    // занят другой копией — удалим позже
                }
            }
        } catch (IOException ignored) {
            // каталог недоступен — не критично
        }
    }

    private static String resolveResourcePath() {
        String osName = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        if (!osName.contains("win")) {
            return null;
        }

        String osArch = System.getProperty("os.arch", "").toLowerCase(Locale.ROOT);
        if (osArch.contains("64") || osArch.contains("amd64") || osArch.contains("x86_64")) {
            return "/win32-x86-64/discord-rpc.dll";
        }

        return "/assets/win32-x86/discord-rpc.dll";
    }
}
