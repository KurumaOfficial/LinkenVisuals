package ru.method.linkenvisuals.managers.misc;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import lombok.Getter;
import ru.method.linkenvisuals.LinkenVisuals;
import ru.method.linkenvisuals.utils.network.TelegramBotClient;

import java.io.IOException;
import java.io.InputStream;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;
import ru.method.linkenvisuals.utils.common.BooleanUtils;
import ru.method.linkenvisuals.utils.common.StringUtils;

public class CrashLogTelegramManager {

    private static final String ENV_ENABLED = "LV_TG_CRASH_ENABLED";
    private static final String ENV_BOT_TOKEN = "LV_TG_BOT_TOKEN";
    private static final String ENV_CHAT_ID = "LV_TG_CHAT_ID";
    private static final String PROP_ENABLED = "lv.tg.crash.enabled";
    private static final String PROP_BOT_TOKEN = "lv.tg.botToken";
    private static final String PROP_CHAT_ID = "lv.tg.chatId";
    private static final String SENT_STATE_FILE = "tg-crash-sent.prime";
    private static final int MAX_REPORT_SCAN_BYTES = 2 * 1024 * 1024;
    private static final String[] LINKEN_CRASH_MARKERS = {
            "linkenvisuals",
            "ru.method.linkenvisuals",
            "linkenvisuals.mixins",
            "[linkenvisuals]"
    };
    private static final boolean CODE_DEFAULT_ENABLED = false;
    private static final String CODE_DEFAULT_BOT_TOKEN = "";
    private static final String CODE_DEFAULT_CHAT_ID = "";

    private final TelegramBotClient telegramBotClient = new TelegramBotClient(
            HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(5))
                    .version(HttpClient.Version.HTTP_1_1)
                    .build()
    );
    private final AtomicBoolean running = new AtomicBoolean(false);

    @Getter private boolean enabled = CODE_DEFAULT_ENABLED;
    @Getter private String botToken = CODE_DEFAULT_BOT_TOKEN;
    @Getter private String chatId = CODE_DEFAULT_CHAT_ID;

    public void applySettings(boolean enabled, String botToken, String chatId) {
        this.enabled = enabled;
        this.botToken = StringUtils.safeTrim(botToken);
        this.chatId = StringUtils.safeTrim(chatId);
    }

    /** Гасит HttpClient Telegram-бота при выходе (его selector-поток — не-daemon, держит JVM живой). */
    public void shutdown() {
        running.set(false);
        telegramBotClient.close();
    }

    public void sendPendingCrashReportsAsync() {
        if (!running.compareAndSet(false, true)) return;

        Thread worker = new Thread(() -> {
            try {
                sendPendingCrashReportsNow();
            } catch (Exception e) {
                LinkenVisuals.LOGGER.warn("[CrashLogTelegram] Failed to send crash reports", e);
            } finally {
                running.set(false);
            }
        }, "LinkenVisuals-CrashLogTelegram");
        worker.setDaemon(true);
        worker.start();
    }

    void sendPendingCrashReportsNow() {
        RuntimeSettings settings = resolveRuntimeSettings();
        if (!settings.enabled) {
            LinkenVisuals.LOGGER.info("[CrashLogTelegram] Sender is disabled");
            return;
        }
        if (isCodePlaceholder(settings.botToken) || isCodePlaceholder(settings.chatId)) {
            LinkenVisuals.LOGGER.warn("[CrashLogTelegram] Configure LV_TG_BOT_TOKEN/LV_TG_CHAT_ID or JVM properties lv.tg.botToken/lv.tg.chatId");
            return;
        }
        if (settings.botToken.isBlank() || settings.chatId.isBlank()) {
            LinkenVisuals.LOGGER.warn("[CrashLogTelegram] Missing bot token or chat id, skipping");
            return;
        }

        Path crashDir = resolveCrashReportsDir();
        if (!Files.isDirectory(crashDir)) {
            return;
        }

        Path sentStatePath = resolveSentStateFilePath();
        Set<String> sentFingerprints = loadSentFingerprints(sentStatePath);
        List<Path> reports = listCrashReports(crashDir);
        if (reports.isEmpty()) return;

        if (!hasCrashBaseline(sentStatePath)) {
            for (Path report : reports) {
                String fingerprint = buildFingerprint(report);
                if (!fingerprint.isBlank()) {
                    sentFingerprints.add(fingerprint);
                }
            }
            saveSentFingerprints(sentStatePath, sentFingerprints);
            LinkenVisuals.LOGGER.info("[CrashLogTelegram] Baseline created for {} existing crash reports", sentFingerprints.size());
            return;
        }

        boolean updated = false;
        for (Path report : reports) {
            String fingerprint = buildFingerprint(report);
            if (fingerprint.isBlank() || sentFingerprints.contains(fingerprint)) {
                continue;
            }

            if (!isLinkenVisualsCrashReport(report)) {
                sentFingerprints.add(fingerprint);
                updated = true;
                continue;
            }

            String caption = "LinkenVisuals crash report: " + report.getFileName();
            TelegramBotClient.SendResult result = telegramBotClient.sendDocument(
                    settings.botToken,
                    settings.chatId,
                    report,
                    caption,
                    Duration.ofSeconds(15)
            );
            if (!result.success()) {
                LinkenVisuals.LOGGER.warn("[CrashLogTelegram] Failed to send {}: {}", report.getFileName(), result.error());
                continue;
            }

            sentFingerprints.add(fingerprint);
            updated = true;
            LinkenVisuals.LOGGER.info("[CrashLogTelegram] Sent crash report {}", report.getFileName());
            break;
        }

        if (updated) {
            saveSentFingerprints(sentStatePath, sentFingerprints);
        }
    }

    private RuntimeSettings resolveRuntimeSettings() {
        boolean resolvedEnabled = enabled;
        String resolvedToken = botToken;
        String resolvedChatId = chatId;

        String envEnabled = StringUtils.safeTrim(System.getenv(ENV_ENABLED));
        if (!envEnabled.isBlank()) {
            resolvedEnabled = BooleanUtils.parseBoolean(envEnabled, resolvedEnabled);
        }
        String propEnabled = StringUtils.safeTrim(System.getProperty(PROP_ENABLED));
        if (!propEnabled.isBlank()) {
            resolvedEnabled = BooleanUtils.parseBoolean(propEnabled, resolvedEnabled);
        }

        String envToken = StringUtils.safeTrim(System.getenv(ENV_BOT_TOKEN));
        if (!envToken.isBlank()) {
            resolvedToken = envToken;
        }
        String propToken = StringUtils.safeTrim(System.getProperty(PROP_BOT_TOKEN));
        if (!propToken.isBlank()) {
            resolvedToken = propToken;
        }

        String envChat = StringUtils.safeTrim(System.getenv(ENV_CHAT_ID));
        if (!envChat.isBlank()) {
            resolvedChatId = envChat;
        }
        String propChat = StringUtils.safeTrim(System.getProperty(PROP_CHAT_ID));
        if (!propChat.isBlank()) {
            resolvedChatId = propChat;
        }

        return new RuntimeSettings(resolvedEnabled, resolvedToken, resolvedChatId);
    }

    private boolean isCodePlaceholder(String value) {
        String normalized = StringUtils.safeTrim(value);
        return "PASTE_BOT_TOKEN_HERE".equals(normalized) || "PASTE_CHAT_ID_HERE".equals(normalized);
    }

    private Path resolveCrashReportsDir() {
        Path globals = LinkenVisuals.getInstance().getGlobalsDir().toPath();
        Path runDirectory = globals.getParent();
        if (runDirectory == null) {
            return globals.resolve("crash-reports");
        }
        return runDirectory.resolve("crash-reports");
    }

    private Path resolveSentStateFilePath() {
        return LinkenVisuals.getInstance().getGlobalsDir().toPath().resolve(SENT_STATE_FILE);
    }

    private List<Path> listCrashReports(Path crashDir) {
        List<Path> reports = new ArrayList<>();
        try (Stream<Path> stream = Files.list(crashDir)) {
            stream.filter(path -> Files.isRegularFile(path) && path.getFileName().toString().endsWith(".txt"))
                    .sorted(Comparator.comparing(Path::getFileName))
                    .forEach(reports::add);
        } catch (IOException exception) {
            LinkenVisuals.LOGGER.warn("[CrashLogTelegram] Failed to list crash reports directory {}", crashDir, exception);
        }
        return reports;
    }

    private Set<String> loadSentFingerprints(Path sentStatePath) {
        Set<String> fingerprints = new HashSet<>();
        if (!Files.isRegularFile(sentStatePath)) {
            return fingerprints;
        }

        try {
            String raw = Files.readString(sentStatePath, StandardCharsets.UTF_8);
            JsonObject object = JsonParser.parseString(raw).getAsJsonObject();
            if (!object.has("sent") || !object.get("sent").isJsonArray()) {
                return fingerprints;
            }
            JsonArray sentArray = object.getAsJsonArray("sent");
            for (JsonElement element : sentArray) {
                if (!element.isJsonPrimitive()) continue;
                String value = StringUtils.safeTrim(element.getAsString());
                if (!value.isBlank()) {
                    fingerprints.add(value);
                }
            }
        } catch (Exception exception) {
            LinkenVisuals.LOGGER.warn("[CrashLogTelegram] Failed to read sent state file {}", sentStatePath, exception);
        }

        return fingerprints;
    }

    private boolean hasCrashBaseline(Path sentStatePath) {
        if (!Files.isRegularFile(sentStatePath)) {
            return false;
        }

        try {
            String raw = Files.readString(sentStatePath, StandardCharsets.UTF_8);
            JsonObject object = JsonParser.parseString(raw).getAsJsonObject();
            return object.has("baselineCreated") && object.get("baselineCreated").getAsBoolean();
        } catch (Exception exception) {
            return false;
        }
    }

    private void saveSentFingerprints(Path sentStatePath, Set<String> fingerprints) {
        JsonObject root = new JsonObject();
        JsonArray sent = new JsonArray();
        fingerprints.stream().sorted().forEach(sent::add);
        root.add("sent", sent);
        root.addProperty("baselineCreated", true);

        try {
            Files.createDirectories(sentStatePath.getParent());
            Files.writeString(sentStatePath, root.toString(), StandardCharsets.UTF_8);
        } catch (Exception exception) {
            LinkenVisuals.LOGGER.warn("[CrashLogTelegram] Failed to write sent state file {}", sentStatePath, exception);
        }
    }

    private String buildFingerprint(Path path) {
        try {
            return StringUtils.safeTrim(path.getFileName().toString()) + "|" + Files.size(path) + "|" + Files.getLastModifiedTime(path).toMillis();
        } catch (Exception exception) {
            return "";
        }
    }

    private boolean isLinkenVisualsCrashReport(Path report) {
        try {
            long size = Files.size(report);
            int scanBytes = (int) Math.min(size, MAX_REPORT_SCAN_BYTES);
            byte[] bytes = new byte[scanBytes];
            try (InputStream inputStream = Files.newInputStream(report)) {
                int offset = 0;
                while (offset < scanBytes) {
                    int read = inputStream.read(bytes, offset, scanBytes - offset);
                    if (read < 0) break;
                    offset += read;
                }
                if (offset < scanBytes) {
                    byte[] trimmed = new byte[offset];
                    System.arraycopy(bytes, 0, trimmed, 0, offset);
                    bytes = trimmed;
                }
            }

            String content = new String(bytes, StandardCharsets.UTF_8).toLowerCase(Locale.ROOT);
            for (String marker : LINKEN_CRASH_MARKERS) {
                if (content.contains(marker)) {
                    return true;
                }
            }
        } catch (Exception exception) {
            LinkenVisuals.LOGGER.warn("[CrashLogTelegram] Failed to inspect crash report {}", report.getFileName(), exception);
        }
        return false;
    }

    private record RuntimeSettings(boolean enabled, String botToken, String chatId) {}
}
