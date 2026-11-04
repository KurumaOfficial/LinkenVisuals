package ru.method.linkenvisuals.utils.debug;

import ru.method.linkenvisuals.LinkenVisuals;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class TabProfiler {
    private static final long LOG_INTERVAL_NANOS = 1_000_000_000L;
    private static final int MAX_HOTSPOTS = 6;

    private static final Map<String, Stat> STATS = new LinkedHashMap<>();

    private static final Comparator<Map.Entry<String, Stat>> STAT_DESCENDING = new Comparator<>() {
        @Override
        public int compare(Map.Entry<String, Stat> left, Map.Entry<String, Stat> right) {
            return Long.compare(right.getValue().totalNanos, left.getValue().totalNanos);
        }
    };

    private static int activeDepth;
    private static long frameStartNanos;
    private static int frameCount;
    private static long totalFrameNanos;
    private static long maxFrameNanos;
    private static long lastLogNanos;

    private TabProfiler() {}

    public static void beginFrame() {
        long now = System.nanoTime();
        if (activeDepth++ == 0) {
            frameStartNanos = now;
            if (lastLogNanos == 0L) {
                lastLogNanos = now;
            }
        }
    }

    public static void endFrame() {
        if (activeDepth <= 0) {
            activeDepth = 0;
            return;
        }

        activeDepth--;
        if (activeDepth > 0) {
            return;
        }

        long now = System.nanoTime();
        long frameNanos = now - frameStartNanos;
        frameCount++;
        totalFrameNanos += frameNanos;
        maxFrameNanos = Math.max(maxFrameNanos, frameNanos);
        maybeLog(now);
    }

    public static boolean isActive() {
        return activeDepth > 0;
    }

    public static long startSection() {
        return isActive() ? System.nanoTime() : 0L;
    }

    public static void endSection(String key, long startNanos) {
        if (startNanos == 0L) {
            return;
        }
        record(key, System.nanoTime() - startNanos);
    }

    public static void record(String key, long nanos) {
        if (!isActive() || key == null || key.isBlank() || nanos <= 0L) {
            return;
        }

        Stat stat = STATS.get(key);
        if (stat == null) {
            stat = new Stat();
            STATS.put(key, stat);
        }
        stat.count++;
        stat.totalNanos += nanos;
    }

    private static void maybeLog(long now) {
        if (frameCount == 0 || now - lastLogNanos < LOG_INTERVAL_NANOS) {
            return;
        }

        List<Map.Entry<String, Stat>> entries = new ArrayList<>(STATS.entrySet());
        entries.sort(STAT_DESCENDING);

        StringBuilder hotspots = new StringBuilder();
        int limit = Math.min(MAX_HOTSPOTS, entries.size());
        for (int i = 0; i < limit; i++) {
            Map.Entry<String, Stat> entry = entries.get(i);
            if (i > 0) {
                hotspots.append(", ");
            }
            hotspots.append(entry.getKey())
                    .append('=')
                    .append(formatMillis(entry.getValue().totalNanos / 1_000_000.0))
                    .append("ms x")
                    .append(entry.getValue().count);
        }

        LinkenVisuals.LOGGER.info(
                "[TabProfiler] frames={} avg={}ms max={}ms hotspots=[{}]",
                frameCount,
                formatMillis(totalFrameNanos / (double) frameCount / 1_000_000.0),
                formatMillis(maxFrameNanos / 1_000_000.0),
                hotspots.isEmpty() ? "none" : hotspots
        );

        STATS.clear();
        frameCount = 0;
        totalFrameNanos = 0L;
        maxFrameNanos = 0L;
        lastLogNanos = now;
    }

    private static String formatMillis(double millis) {
        return String.format(Locale.ROOT, "%.2f", millis);
    }

    private static final class Stat {
        private int count;
        private long totalNanos;
    }
}
