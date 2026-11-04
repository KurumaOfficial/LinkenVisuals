package ru.method.linkenvisuals.managers.misc;

import com.google.gson.JsonObject;
import com.mojang.authlib.GameProfile;
import ru.method.linkenvisuals.LinkenVisuals;
import ru.method.linkenvisuals.utils.Wrapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import ru.method.linkenvisuals.utils.common.BooleanUtils;
import ru.method.linkenvisuals.utils.common.StringUtils;

public class UsageStatsManager implements Wrapper {

    private static final String ENV_ENABLED = "LV_STATS_ENABLED";
    private static final String ENV_ENDPOINT = "LV_STATS_ENDPOINT";
    private static final boolean CODE_DEFAULT_ENABLED = false;
    private static final String CODE_DEFAULT_ENDPOINT = "http://127.0.0.1:8091/stats/ping";
    private static final long HEARTBEAT_INTERVAL_MS = 30000L;
    private static final long PING_WARN_INTERVAL_MS = 60000L;

    // Время последнего warn-лога о сбое пинга — чтобы не спамить в лог при устойчивых ошибках.
    private volatile long lastPingWarnMs = 0L;

    private final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(4))
            .version(HttpClient.Version.HTTP_1_1)
            .build();
    private final AtomicBoolean running = new AtomicBoolean(false);

    private boolean enabled = CODE_DEFAULT_ENABLED;
    private String endpointUrl = CODE_DEFAULT_ENDPOINT;

    public boolean isEnabled() {
        return enabled;
    }

    public String getEndpointUrl() {
        return endpointUrl;
    }

    public void applySettings(boolean enabled, String endpointUrl) {
        this.enabled = enabled;
        this.endpointUrl = StringUtils.safeTrim(endpointUrl);
    }

    /** Останавливает heartbeat и гасит HttpClient (его selector-поток — не-daemon, держит JVM). */
    public void shutdown() {
        running.set(false);
        try {
            client.shutdownNow();
        } catch (Throwable ignored) {
        }
    }

    public void start() {
        if (!running.compareAndSet(false, true)) return;
        if (!resolveRuntimeSettings().enabled) {
            running.set(false);
            return;
        }

        Thread worker = new Thread(() -> {
            try {
                sendPing("launch");
                while (!Thread.currentThread().isInterrupted()) {
                    try {
                        Thread.sleep(HEARTBEAT_INTERVAL_MS);
                    } catch (InterruptedException interrupted) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                    sendPing("heartbeat");
                }
            } catch (Exception exception) {
                LinkenVisuals.LOGGER.warn("[UsageStats] Reporter stopped due to error", exception);
            } finally {
                running.set(false);
            }
        }, "LinkenVisuals-UsageStats");
        worker.setDaemon(true);
        worker.start();
    }

    private void sendPing(String event) {
        try {
            RuntimeSettings settings = resolveRuntimeSettings();
            if (!settings.enabled || settings.endpointUrl.isBlank()) return;

            URI uri;
            try {
                uri = URI.create(settings.endpointUrl);
            } catch (Exception badUri) {
                LinkenVisuals.LOGGER.warn("[UsageStats] Bad endpoint URL: {}", settings.endpointUrl);
                return;
            }

            // Снимаем локальный снапшот игрока/профиля один раз, чтобы избежать TOCTOU:
            // между проверкой и использованием mc.player может стать null из основного потока.
            // player/profile могут быть null (меню и т.п.) — хелперы это корректно обработают.
            var player = mc != null ? mc.player : null;
            var profile = player != null ? player.getGameProfile() : null;

            JsonObject payload = new JsonObject();
            payload.addProperty("event", event);
            payload.addProperty("timestamp", System.currentTimeMillis() / 1000L);
            payload.addProperty("location", resolveLocation());
            payload.addProperty("modId", LinkenVisuals.MOD_ID);
            payload.addProperty("modVersion", "unknown");

            JsonObject user = new JsonObject();
            user.addProperty("uuid", resolveUserUuid(profile));
            user.addProperty("name", resolveUserName(profile));
            payload.add("user", user);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(uri)
                    .timeout(Duration.ofSeconds(4))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(payload.toString(), StandardCharsets.UTF_8))
                    .build();

            client.sendAsync(request, HttpResponse.BodyHandlers.discarding())
                    .exceptionally(throwable -> null);
        } catch (Exception exception) {
            // Одиночный сбой пинга не должен ронять цикл heartbeat. Логируем периодически, чтобы не спамить.
            long now = System.currentTimeMillis();
            if (now - lastPingWarnMs > PING_WARN_INTERVAL_MS) {
                lastPingWarnMs = now;
                LinkenVisuals.LOGGER.warn("[UsageStats] sendPing failed (event={}), heartbeat continues", event, exception);
            }
        }
    }

    private RuntimeSettings resolveRuntimeSettings() {
        boolean resolvedEnabled = enabled;
        String resolvedEndpoint = endpointUrl;

        String envEnabled = StringUtils.safeTrim(System.getenv(ENV_ENABLED));
        if (!envEnabled.isBlank()) {
            resolvedEnabled = BooleanUtils.parseBoolean(envEnabled, resolvedEnabled);
        }

        String envEndpoint = StringUtils.safeTrim(System.getenv(ENV_ENDPOINT));
        if (!envEndpoint.isBlank()) {
            resolvedEndpoint = envEndpoint;
        }

        return new RuntimeSettings(resolvedEnabled, resolvedEndpoint);
    }

    private String resolveLocation() {
        if (mc == null) return "menu";
        if (mc.isInSingleplayer()) return "singleplayer";
        if (mc.getCurrentServerEntry() != null && mc.getCurrentServerEntry().address != null) {
            String address = StringUtils.safeTrim(mc.getCurrentServerEntry().address);
            if (!address.isBlank()) return address.toLowerCase(Locale.ROOT);
        }
        return "menu";
    }

    // Работает только с переданным снапшотом профиля — mc.player повторно не читаем (см. sendPing).
    private String resolveUserUuid(GameProfile profile) {
        if (profile != null && profile.id() != null) {
            return profile.id().toString();
        }
        String name = resolveUserName(profile);
        return UUID.nameUUIDFromBytes(name.getBytes(StandardCharsets.UTF_8)).toString();
    }

    // Работает только с переданным снапшотом профиля; фолбэк на сессию — отдельный безопасный вызов.
    private String resolveUserName(GameProfile profile) {
        if (profile != null) {
            String name = StringUtils.safeTrim(profile.name());
            if (!name.isBlank()) return name;
        }
        if (mc != null && mc.getSession() != null) {
            String username = StringUtils.safeTrim(mc.getSession().getUsername());
            if (!username.isBlank()) return username;
        }
        return "unknown";
    }

    private record RuntimeSettings(boolean enabled, String endpointUrl) {}
}
