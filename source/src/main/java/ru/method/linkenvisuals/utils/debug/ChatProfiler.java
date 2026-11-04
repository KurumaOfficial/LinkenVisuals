package ru.method.linkenvisuals.utils.debug;

public final class ChatProfiler {

    private ChatProfiler() {
    }

    public static void beginFrame() {
    }

    public static void endFrame() {
    }

    public static boolean isActive() {
        return false;
    }

    public static long startSection() {
        return 0L;
    }

    public static void endSection(String key, long startNanos) {
    }

    public static void record(String key, long nanos) {
    }

    public static void incrementCounter(String key) {
    }

    public static void incrementCounter(String key, int amount) {
    }
}
