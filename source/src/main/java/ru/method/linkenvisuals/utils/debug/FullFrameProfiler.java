package ru.method.linkenvisuals.utils.debug;

import meteordevelopment.orbit.listeners.IListener;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import ru.method.linkenvisuals.LinkenVisuals;
import ru.method.linkenvisuals.utils.math.Counter;
import ru.method.linkenvisuals.utils.render.Render2D;
import ru.method.linkenvisuals.utils.render.fonts.Fonts;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Global CPU profiler: frame phases, manual sections, Orbit events and per-listener handlers.
 * Toggle via UI → Full Profiler.
 */
public final class FullFrameProfiler {
    private static final long LOG_INTERVAL_NANOS = 3_000_000_000L;
    private static final int MAX_LOG_SECTIONS = 12;
    private static final int MAX_LOG_EVENTS = 8;
    private static final int MAX_LOG_LISTENERS = 10;
    private static final int MAX_OVERLAY_LINES = 18;

    private static final Map<String, Stat> SECTIONS = new LinkedHashMap<>();
    private static final Map<String, Stat> EVENTS = new LinkedHashMap<>();
    private static final Map<String, Stat> LISTENERS = new LinkedHashMap<>();
    private static final Map<String, CounterStat> COUNTERS = new LinkedHashMap<>();
    private static final IdentityHashMap<IListener, String> LISTENER_OWNERS = new IdentityHashMap<>();
    private static final List<String> OVERLAY_LINES = new ArrayList<>(MAX_OVERLAY_LINES);

    private static final Comparator<Map.Entry<String, Stat>> STAT_DESCENDING = new Comparator<>() {
        @Override
        public int compare(Map.Entry<String, Stat> left, Map.Entry<String, Stat> right) {
            return Long.compare(right.getValue().totalNanos, left.getValue().totalNanos);
        }
    };

    private static volatile boolean enabled;
    private static int frameDepth;
    private static int tickDepth;
    private static long frameStartNanos;
    private static long tickStartNanos;
    private static int frameCount;
    private static long totalFrameNanos;
    private static long maxFrameNanos;
    private static long totalTickNanos;
    private static long maxTickNanos;
    private static int tickCount;
    private static long lastLogNanos;
    private static long lastOverlayUpdateNanos;
    private static double lastAvgFrameMs;
    private static double lastMaxFrameMs;
    private static double lastAvgTickMs;
    private static boolean listenersBound;

    private FullFrameProfiler() {
    }

    public static void setEnabled(boolean value) {
        enabled = value;
        if (value) {
            listenersBound = false;
            bindExistingListeners();
        } else {
            reset();
        }
    }

    public static boolean isEnabled() {
        return enabled;
    }

    public static boolean isActive() {
        return enabled && frameDepth > 0;
    }

    public static void beginFrame() {
        if (!enabled) {
            return;
        }
        long now = System.nanoTime();
        if (frameDepth++ == 0) {
            frameStartNanos = now;
            if (!listenersBound) {
                bindExistingListeners();
            }
            resetFrameCounters();
            if (lastLogNanos == 0L) {
                lastLogNanos = now;
            }
        }
    }

    public static void endFrame() {
        if (!enabled || frameDepth <= 0) {
            frameDepth = 0;
            return;
        }
        frameDepth--;
        if (frameDepth > 0) {
            return;
        }

        long now = System.nanoTime();
        long frameNanos = Math.max(0L, now - frameStartNanos);
        frameCount++;
        totalFrameNanos += frameNanos;
        maxFrameNanos = Math.max(maxFrameNanos, frameNanos);
        maybeLog(now);
        maybeRefreshOverlay(now, frameNanos);
    }

    public static void beginTick() {
        if (!enabled) {
            return;
        }
        if (tickDepth++ == 0) {
            tickStartNanos = System.nanoTime();
        }
    }

    public static void endTick() {
        if (!enabled || tickDepth <= 0) {
            tickDepth = 0;
            return;
        }
        tickDepth--;
        if (tickDepth > 0) {
            return;
        }
        long tickNanos = Math.max(0L, System.nanoTime() - tickStartNanos);
        tickCount++;
        totalTickNanos += tickNanos;
        maxTickNanos = Math.max(maxTickNanos, tickNanos);
        recordSection("tick.total", tickNanos);
    }

    public static long startSection() {
        return isActive() ? System.nanoTime() : 0L;
    }

    public static void endSection(String key, long startNanos) {
        if (startNanos == 0L) {
            return;
        }
        recordSection(key, System.nanoTime() - startNanos);
    }

    public static void recordSection(String key, long nanos) {
        if (!isActive() || key == null || key.isBlank() || nanos <= 0L) {
            return;
        }
        accumulate(SECTIONS, key, nanos);
    }

    public static void recordEventPost(Object event, long nanos) {
        if (!isActive() || event == null || nanos <= 0L) {
            return;
        }
        accumulate(EVENTS, "event." + event.getClass().getSimpleName(), nanos);
    }

    public static void bindListener(IListener listener, String ownerName) {
        if (!enabled || listener == null || ownerName == null || ownerName.isBlank()) {
            return;
        }
        LISTENER_OWNERS.put(listener, ownerName);
    }

    public static void recordListener(IListener listener, Object event, long nanos) {
        if (!isActive() || listener == null || event == null || nanos <= 0L) {
            return;
        }
        String owner = LISTENER_OWNERS.getOrDefault(listener, listener.getClass().getSimpleName());
        String key = "handler." + owner + "." + event.getClass().getSimpleName();
        accumulate(LISTENERS, key, nanos);
    }

    public static void incrementCounter(String key) {
        incrementCounter(key, 1);
    }

    public static void incrementCounter(String key, int amount) {
        if (!isActive() || key == null || key.isBlank() || amount <= 0) {
            return;
        }
        CounterStat counter = COUNTERS.get(key);
        if (counter == null) {
            counter = new CounterStat();
            COUNTERS.put(key, counter);
        }
        counter.total += amount;
        counter.frames++;
        counter.lastFrame += amount;
    }

    public static void renderOverlay(DrawContext context) {
        if (!enabled || context == null || OVERLAY_LINES.isEmpty()) {
            return;
        }

        float x = 6f;
        float y = 6f;
        float lineHeight = 8.5f;
        float panelWidth = 0f;
        for (String line : OVERLAY_LINES) {
            panelWidth = Math.max(panelWidth, Fonts.REGULAR.getFont(6.5f).getWidth(line));
        }

        Render2D.drawRoundedRect(
                context.getMatrices(),
                x - 3f,
                y - 3f,
                panelWidth + 10f,
                OVERLAY_LINES.size() * lineHeight + 6f,
                4f,
                new Color(6, 8, 12, 185)
        );

        for (String line : OVERLAY_LINES) {
            Color color = colorForLine(line);
            Render2D.drawFont(context.getMatrices(), Fonts.REGULAR.getFont(6.5f), line, x, y, color);
            y += lineHeight;
        }
    }

    private static Color colorForLine(String line) {
        if (line.startsWith("handler.")) {
            return new Color(255, 180, 120, 235);
        }
        if (line.startsWith("event.")) {
            return new Color(180, 220, 255, 235);
        }
        if (line.startsWith("glass.") || line.startsWith("gui.") || line.startsWith("hud.") || line.startsWith("render.")) {
            return new Color(120, 210, 255, 235);
        }
        return new Color(235, 235, 235, 235);
    }

    private static void maybeRefreshOverlay(long now, long frameNanos) {
        if (now - lastOverlayUpdateNanos < 250_000_000L && frameCount > 1) {
            return;
        }
        lastOverlayUpdateNanos = now;

        OVERLAY_LINES.clear();
        MinecraftClient client = MinecraftClient.getInstance();
        String screen = client != null && client.currentScreen != null
                ? client.currentScreen.getClass().getSimpleName()
                : "in-game";

        OVERLAY_LINES.add(String.format(
                Locale.ROOT,
                "FPS %d | frame %.2fms | tick avg %.2fms | %s",
                Counter.getCurrentFPS(),
                frameNanos / 1_000_000.0,
                lastAvgTickMs,
                screen
        ));

        appendTopStats(OVERLAY_LINES, SECTIONS, 5, false);
        appendTopStats(OVERLAY_LINES, EVENTS, 3, false);
        appendTopStats(OVERLAY_LINES, LISTENERS, 4, true);

        appendCounterLine("glass.draws");
        appendCounterLine("glass.captures");
        appendCounterLine("handlers");
    }

    private static void appendTopStats(List<String> lines, Map<String, Stat> stats, int limit, boolean listeners) {
        List<Map.Entry<String, Stat>> entries = sorted(stats);
        int count = Math.min(limit, entries.size());
        for (int i = 0; i < count && lines.size() < MAX_OVERLAY_LINES; i++) {
            Map.Entry<String, Stat> entry = entries.get(i);
            Stat stat = entry.getValue();
            double avgMs = stat.totalNanos / (double) Math.max(1, stat.count) / 1_000_000.0;
            if (listeners && avgMs < 0.02) {
                continue;
            }
            lines.add(String.format(
                    Locale.ROOT,
                    "%s %.2fms x%d",
                    entry.getKey(),
                    avgMs,
                    stat.count
            ));
        }
    }

    private static void appendCounterLine(String key) {
        CounterStat counter = COUNTERS.get(key);
        if (counter == null || counter.lastFrame <= 0) {
            return;
        }
        OVERLAY_LINES.add(key + " " + counter.lastFrame + "/frame");
    }

    private static void maybeLog(long now) {
        if (frameCount == 0 || now - lastLogNanos < LOG_INTERVAL_NANOS) {
            return;
        }

        lastAvgFrameMs = totalFrameNanos / (double) frameCount / 1_000_000.0;
        lastMaxFrameMs = maxFrameNanos / 1_000_000.0;
        lastAvgTickMs = tickCount > 0 ? totalTickNanos / (double) tickCount / 1_000_000.0 : 0.0;

        LinkenVisuals.LOGGER.info(
                "[FullProfiler] fps={} frames={} avgFrame={}ms maxFrame={}ms avgTick={}ms sections=[{}] events=[{}] handlers=[{}] counters=[{}]",
                Counter.getCurrentFPS(),
                frameCount,
                formatMillis(lastAvgFrameMs),
                formatMillis(lastMaxFrameMs),
                formatMillis(lastAvgTickMs),
                formatStats(SECTIONS, MAX_LOG_SECTIONS),
                formatStats(EVENTS, MAX_LOG_EVENTS),
                formatStats(LISTENERS, MAX_LOG_LISTENERS),
                formatCounters()
        );

        SECTIONS.clear();
        EVENTS.clear();
        LISTENERS.clear();
        COUNTERS.clear();
        frameCount = 0;
        totalFrameNanos = 0L;
        maxFrameNanos = 0L;
        tickCount = 0;
        totalTickNanos = 0L;
        maxTickNanos = 0L;
        lastLogNanos = now;
    }

    private static String formatStats(Map<String, Stat> stats, int limit) {
        List<Map.Entry<String, Stat>> entries = sorted(stats);
        StringBuilder builder = new StringBuilder();
        int count = Math.min(limit, entries.size());
        for (int i = 0; i < count; i++) {
            Map.Entry<String, Stat> entry = entries.get(i);
            Stat stat = entry.getValue();
            if (i > 0) {
                builder.append(", ");
            }
            builder.append(entry.getKey())
                    .append('=')
                    .append(formatMillis(stat.totalNanos / (double) Math.max(1, stat.count) / 1_000_000.0))
                    .append("ms x")
                    .append(stat.count);
        }
        return builder.length() == 0 ? "none" : builder.toString();
    }

    private static String formatCounters() {
        StringBuilder builder = new StringBuilder();
        for (Map.Entry<String, CounterStat> entry : COUNTERS.entrySet()) {
            CounterStat counter = entry.getValue();
            if (counter.total <= 0) {
                continue;
            }
            if (!builder.isEmpty()) {
                builder.append(", ");
            }
            builder.append(entry.getKey())
                    .append('=')
                    .append(counter.total / Math.max(1, frameCount))
                    .append("/frame");
        }
        return builder.isEmpty() ? "none" : builder.toString();
    }

    private static List<Map.Entry<String, Stat>> sorted(Map<String, Stat> stats) {
        List<Map.Entry<String, Stat>> entries = new ArrayList<>(stats.entrySet());
        entries.sort(STAT_DESCENDING);
        return entries;
    }

    private static void accumulate(Map<String, Stat> map, String key, long nanos) {
        Stat stat = map.get(key);
        if (stat == null) {
            stat = new Stat();
            map.put(key, stat);
        }
        stat.count++;
        stat.totalNanos += nanos;
        stat.maxNanos = Math.max(stat.maxNanos, nanos);
    }

    private static void resetFrameCounters() {
        for (CounterStat counter : COUNTERS.values()) {
            counter.lastFrame = 0;
        }
    }

    private static void bindExistingListeners() {
        ProfiledEventBus.bindAllFromInstance();
        listenersBound = true;
    }

    private static void reset() {
        SECTIONS.clear();
        EVENTS.clear();
        LISTENERS.clear();
        COUNTERS.clear();
        LISTENER_OWNERS.clear();
        OVERLAY_LINES.clear();
        frameDepth = 0;
        tickDepth = 0;
        frameCount = 0;
        tickCount = 0;
        totalFrameNanos = 0L;
        maxFrameNanos = 0L;
        totalTickNanos = 0L;
        maxTickNanos = 0L;
        lastLogNanos = 0L;
        lastOverlayUpdateNanos = 0L;
        lastAvgFrameMs = 0.0;
        lastMaxFrameMs = 0.0;
        lastAvgTickMs = 0.0;
        listenersBound = false;
    }

    private static String formatMillis(double millis) {
        return String.format(Locale.ROOT, "%.2f", millis);
    }

    private static final class Stat {
        private int count;
        private long totalNanos;
        private long maxNanos;
    }

    private static final class CounterStat {
        private int total;
        private int frames;
        private int lastFrame;
    }
}
