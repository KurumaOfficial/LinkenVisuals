package ru.method.linkenvisuals.hud.impl.island;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class DynamicIslandLyrics {

    private final List<LyricsLine> lyricsLines = Collections.synchronizedList(new ArrayList<>());
    private ExecutorService lyricsExecutor;
    // Флаги состояния и поля результата пишутся из фонового потока загрузки лирики,
    // а читаются из render-потока, поэтому помечены volatile — иначе изменения
    // могут быть не видны рендеру (проблема видимости памяти).
    private volatile boolean open;
    private volatile boolean loading;
    private volatile boolean loaded;
    private volatile boolean unavailable;
    // Ключ текущего запрошенного трека. Тоже volatile: сравнивается в колбэке
    // фонового потока с актуальным значением, чтобы отбросить результат старого трека.
    private volatile String lyricsKey;

    public void ensureExecutor() {
        if (lyricsExecutor == null || lyricsExecutor.isShutdown()) {
            lyricsExecutor = Executors.newSingleThreadExecutor(r -> {
                Thread t = new Thread(r, "DynamicIsland-Lyrics");
                t.setDaemon(true);
                return t;
            });
        }
    }

    public void shutdown() {
        if (lyricsExecutor != null) {
            lyricsExecutor.shutdownNow();
            lyricsExecutor = null;
        }
        reset();
    }

    public boolean isOpen() {
        return open;
    }

    public boolean isLoading() {
        return loading;
    }

    public boolean isLoaded() {
        return loaded;
    }

    public boolean isUnavailable() {
        return unavailable;
    }

    public List<LyricsLine> getLines() {
        return lyricsLines;
    }

    public void reset() {
        open = false;
        loaded = false;
        loading = false;
        unavailable = false;
        lyricsKey = null;
        lyricsLines.clear();
    }

    public void toggle(String title, String artist, long durationMs) {
        if (unavailable) return;
        open = !open;
        if (open && !loaded && !loading) {
            startFetch(title, artist, durationMs);
        }
    }

    public void startFetch(String title, String artist, long durationMs) {
        if (unavailable) return;
        ExecutorService executor = lyricsExecutor;
        if (executor == null || executor.isShutdown()) return;
        String normalizedTitle = safeText(title);
        String normalizedArtist = safeText(artist);
        String key = normalizedTitle + "|" + normalizedArtist + "|" + durationMs;
        if (lyricsKey != null && lyricsKey.equals(key) && (loaded || loading)) return;
        // Ключ сменился — начинаем загрузку нового трека. Сбрасываем unavailable,
        // чтобы новый трек не унаследовал признак «нет лирики» от старого.
        lyricsKey = key;
        lyricsLines.clear();
        loaded = false;
        loading = true;
        unavailable = false;
        // Локально фиксируем ключ запроса: колбэк применит результат только если
        // к моменту завершения загрузки ключ всё ещё соответствует этому запросу.
        final String requestKey = key;
        executor.submit(() -> {
            try {
                List<LyricsLine> parsed = fetchLyrics(normalizedTitle, normalizedArtist, durationMs);
                // Трек мог смениться за время загрузки — тогда результат уже неактуален
                // и его нельзя применять к новому треку. Просто отбрасываем.
                if (!requestKey.equals(lyricsKey)) return;
                synchronized (lyricsLines) {
                    lyricsLines.clear();
                    if (parsed != null) lyricsLines.addAll(parsed);
                }
                boolean hasLyrics = parsed != null && !parsed.isEmpty();
                loaded = true;
                loading = false;
                if (!hasLyrics) {
                    unavailable = true;
                    open = false;
                } else {
                    unavailable = false;
                }
            } catch (Throwable ignored) {
                // Ошибку применяем к состоянию только если запрос всё ещё актуален.
                if (!requestKey.equals(lyricsKey)) return;
                loaded = true;
                loading = false;
            }
        });
    }

    public int findLyricsIndex(long positionMs) {
        if (lyricsLines.isEmpty()) return -1;
        int lo = 0, hi = lyricsLines.size() - 1, ans = -1;
        while (lo <= hi) {
            int mid = (lo + hi) >>> 1;
            long t = lyricsLines.get(mid).timeMs();
            if (t <= positionMs) {
                ans = mid;
                lo = mid + 1;
            } else {
                hi = mid - 1;
            }
        }
        return ans;
    }

    private List<LyricsLine> fetchLyrics(String title, String artist, long durationMs) {
        int durationSec = toDurationSeconds(durationMs);
        String getUrl = buildLrclibGetUrl(title, artist, durationSec);
        String response = httpGet(getUrl);
        if ((response == null || response.isEmpty()) && durationSec > 0) {
            String searchUrl = buildLrclibSearchUrl(title, artist, durationSec);
            response = httpGet(searchUrl);
        }
        if (response == null || response.isEmpty()) return Collections.emptyList();
        return parseLrcJson(response, durationMs);
    }

    private List<LyricsLine> parseLrcJson(String response, long durationMs) {
        try {
            JsonElement parsed = JsonParser.parseString(response);
            if (parsed.isJsonObject()) {
                List<LyricsLine> lines = parseLyricsObject(parsed.getAsJsonObject(), durationMs);
                if (!lines.isEmpty()) return lines;
            } else if (parsed.isJsonArray()) {
                JsonArray arr = parsed.getAsJsonArray();
                for (JsonElement el : arr) {
                    if (!el.isJsonObject()) continue;
                    List<LyricsLine> lines = parseLyricsObject(el.getAsJsonObject(), durationMs);
                    if (!lines.isEmpty()) return lines;
                }
            }
        } catch (Throwable ignored) {
        }
        return Collections.emptyList();
    }

    private List<LyricsLine> parseLyricsObject(JsonObject obj, long durationMs) {
        String synced = getJsonString(obj, "syncedLyrics");
        if (synced != null && !synced.isEmpty()) {
            return parseLrc(synced);
        }
        String plain = getJsonString(obj, "plainLyrics");
        if (plain != null && !plain.isEmpty()) {
            return parsePlainLyrics(plain, durationMs);
        }
        return Collections.emptyList();
    }

    private String getJsonString(JsonObject obj, String key) {
        JsonElement el = obj.get(key);
        if (el == null || el.isJsonNull()) return null;
        if (el.isJsonPrimitive()) {
            String raw = el.getAsString();
            if (raw == null) return null;
            return raw.replace("\\r", "").replace("\\n", "\n");
        }
        return null;
    }

    private List<LyricsLine> parsePlainLyrics(String raw, long durationMs) {
        if (raw == null) return Collections.emptyList();
        String[] lines = raw.replace("\r", "\n").split("\n");
        List<String> filtered = new ArrayList<>();
        for (String line : lines) {
            if (line == null) continue;
            String trimmed = line.trim();
            if (!trimmed.isEmpty()) {
                filtered.add(trimmed);
            }
        }
        if (filtered.isEmpty()) return Collections.emptyList();
        long totalDuration = durationMs > 0 ? durationMs : 180_000L;
        long step = Math.max(1500L, totalDuration / Math.max(1, filtered.size() + 1));
        List<LyricsLine> out = new ArrayList<>();
        long cursor = 0L;
        for (String line : filtered) {
            out.add(new LyricsLine(cursor, line));
            cursor += step;
        }
        return out;
    }

    private List<LyricsLine> parseLrc(String lrc) {
        if (lrc == null) return Collections.emptyList();
        String[] lines = lrc.replace("\r", "\n").split("\n");
        List<LyricsLine> out = new ArrayList<>();
        for (String raw : lines) {
            if (raw == null || raw.isEmpty()) continue;
            int lb = raw.indexOf('[');
            int rb = raw.indexOf(']');
            if (lb != 0 || rb <= 1) continue;
            String time = raw.substring(lb + 1, rb);
            String text = raw.substring(Math.min(raw.length(), rb + 1)).trim();
            long ms = parseTimestampMs(time);
            if (ms < 0) continue;
            out.add(new LyricsLine(ms, text));
        }
        out.sort((a, b) -> Long.compare(a.timeMs(), b.timeMs()));
        return out;
    }

    private long parseTimestampMs(String stamp) {
        try {
            String[] parts = stamp.split(":");
            if (parts.length < 2) return -1;
            int min = Integer.parseInt(parts[0]);
            float sec = Float.parseFloat(parts[1]);
            return (long) (min * 60_000L + sec * 1000L);
        } catch (Exception e) {
            return -1;
        }
    }

    private String buildLrclibGetUrl(String track, String artist, int durationSec) {
        String base = "https://lrclib.net/api/get";
        int sec = Math.max(1, durationSec);
        return base + "?track_name=" + urlEnc(track) + "&artist_name=" + urlEnc(artist) + "&album_name=&duration=" + sec;
    }

    private String buildLrclibSearchUrl(String track, String artist, int durationSec) {
        String base = "https://lrclib.net/api/search";
        StringBuilder qs = new StringBuilder();
        qs.append("?track_name=").append(urlEnc(track))
                .append("&artist_name=").append(urlEnc(artist))
                .append("&album_name=");
        if (durationSec > 0) {
            qs.append("&duration=").append(durationSec);
        }
        return base + qs;
    }

    private int toDurationSeconds(long durationMs) {
        if (durationMs <= 0) return -1;
        if (durationMs < 100_000L) return (int) durationMs;
        return (int) Math.max(1, durationMs / 1000L);
    }

    private String urlEnc(String s) {
        try {
            return URLEncoder.encode(s == null ? "" : s, StandardCharsets.UTF_8.name());
        } catch (Exception e) {
            return "";
        }
    }

    private String httpGet(String urlStr) {
        HttpURLConnection conn = null;
        try {
            URL url = new URL(urlStr);
            conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);
            conn.setRequestMethod("GET");
            conn.setRequestProperty("Accept", "application/json");
            int code = conn.getResponseCode();
            InputStreamReader isr = new InputStreamReader(code >= 200 && code < 300 ? conn.getInputStream() : conn.getErrorStream(), StandardCharsets.UTF_8);
            try (BufferedReader br = new BufferedReader(isr)) {
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = br.readLine()) != null) {
                    sb.append(line);
                }
                return sb.toString();
            }
        } catch (Exception ignored) {
            return null;
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    private static String safeText(String value) {
        return value == null ? "" : value;
    }

    public record LyricsLine(long timeMs, String text) {
    }
}
