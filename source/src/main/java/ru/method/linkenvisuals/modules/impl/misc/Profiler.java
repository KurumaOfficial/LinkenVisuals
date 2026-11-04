package ru.method.linkenvisuals.modules.impl.misc;

import meteordevelopment.orbit.EventHandler;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.entity.Entity;
import ru.method.linkenvisuals.LinkenVisuals;
import ru.method.linkenvisuals.api.events.impl.EventRender2D;
import ru.method.linkenvisuals.modules.api.Category;
import ru.method.linkenvisuals.modules.api.Module;

import java.io.IOException;
import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Фоновый диагност просадок FPS (перенос Profiler из Kimiko). Каждый кадр пишет время кадра в кольцевой
 * буфер, считает базлайн/1%low, семплирует GC/аллокации/потоки (JMX) и мир, ловит спайки и приписывает
 * им причину (GC / чанки / частицы / сущности / внешний стоп). При ВЫКЛЮЧЕНИИ сохраняет .txt-отчёт в
 * config/linkenvisuals/profiler. Не рисует HUD. Reflection в внутренности MC — guarded (при промахе стата
 * просто «недоступно»).
 */
public class Profiler extends Module {

    private static final int CAPACITY = 512;
    private static final double SPIKE_FLOOR_MS = 5.0;
    private static final double SPIKE_MULTIPLIER = 2.0;
    private static final DateTimeFormatter REPORT_TIME =
            DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss_SSS", Locale.ROOT).withZone(ZoneId.systemDefault());

    private final double[] frames = new double[CAPACITY];
    private final double[] scratch = new double[CAPACITY];
    private final List<GarbageCollectorMXBean> gcBeans = ManagementFactory.getGarbageCollectorMXBeans();
    private final ThreadMXBean threadBean = ManagementFactory.getThreadMXBean();
    private final Deque<Spike> spikes = new ArrayDeque<>();
    private int count;
    private int head;
    private long startedAtMs;
    private long lastFrameNanos;
    private double baselineMs = 4.0;
    private long lastGcCount = -1L;
    private long lastGcPauseMs = -1L;
    private long lastHeapUsed = -1L;
    private long allocAccum;
    private long allocRatePerSec;
    private long allocWindowStart;
    private long lastWorldSample;
    private int baseEntities;
    private int baseParticles = -1;
    private long lastSectionSample;
    private int pcRecent;
    private int puRecent;
    private long pcRecentTime;
    private String lastSectionStats;
    private Field particleManagerField;
    private Field particlesMapField;
    private boolean reflectionResolved;

    public Profiler() {
        super("Profiler", Category.Other);
    }

    @EventHandler
    public void onRender2D(EventRender2D e) {
        long now = System.nanoTime();
        if (lastFrameNanos == 0L) {
            lastFrameNanos = now;
            return;
        }
        double frameMs = (now - lastFrameNanos) / 1_000_000.0;
        lastFrameNanos = now;
        if (frameMs <= 0.0 || frameMs > 1000.0) return;
        push(frameMs);
        recomputeBaseline();
        long nowMs = System.currentTimeMillis();
        updateAllocation(nowMs);
        long[] gc = sampleGc();
        long gcDelta = lastGcCount < 0L ? 0L : gc[0] - lastGcCount;
        long gcPauseTotal = gcPauseTotalMs();
        long gcPauseDelta = lastGcPauseMs < 0L ? 0L : gcPauseTotal - lastGcPauseMs;
        lastGcCount = gc[0];
        lastGcPauseMs = gcPauseTotal;
        maybeSampleWorld(nowMs);
        detectSpike(frameMs, gcDelta, gcPauseDelta, nowMs);
    }

    @Override
    public void onEnable() {
        super.onEnable();
        resetStats();
        startedAtMs = System.currentTimeMillis();
    }

    @Override
    public void onDisable() {
        saveReport();
        super.onDisable();
    }

    private void push(double v) {
        frames[head] = v;
        head = (head + 1) % CAPACITY;
        if (count < CAPACITY) count++;
    }

    private void resetStats() {
        count = 0; head = 0; lastFrameNanos = 0L; baselineMs = 4.0;
        lastGcCount = -1L; lastGcPauseMs = -1L; lastHeapUsed = -1L;
        allocAccum = 0L; allocRatePerSec = 0L; allocWindowStart = 0L;
        lastWorldSample = 0L; baseEntities = 0; baseParticles = -1;
        lastSectionSample = 0L; pcRecent = 0; puRecent = 0; pcRecentTime = 0L;
        lastSectionStats = null; spikes.clear();
    }

    private void recomputeBaseline() {
        if (count == 0) return;
        for (int i = 0; i < count; i++) {
            scratch[i] = frames[(head - count + i + CAPACITY) % CAPACITY];
        }
        Arrays.sort(scratch, 0, count);
        baselineMs = scratch[count / 2];
    }

    private void updateAllocation(long nowMs) {
        long used = usedHeap();
        if (lastHeapUsed >= 0L && used > lastHeapUsed) allocAccum += used - lastHeapUsed;
        lastHeapUsed = used;
        if (allocWindowStart == 0L) allocWindowStart = nowMs;
        if (nowMs - allocWindowStart >= 1000L) {
            allocRatePerSec = allocAccum;
            allocAccum = 0L;
            allocWindowStart = nowMs;
        }
    }

    private long[] sampleGc() {
        long collections = 0L, time = 0L;
        for (GarbageCollectorMXBean bean : gcBeans) {
            long c = bean.getCollectionCount();
            long t = bean.getCollectionTime();
            if (c > 0L) collections += c;
            if (t > 0L) time += t;
        }
        return new long[]{collections, time};
    }

    private long gcPauseTotalMs() {
        long total = 0L;
        for (GarbageCollectorMXBean bean : gcBeans) {
            long t;
            if (bean.getName().toLowerCase(Locale.ROOT).contains("concurrent") || (t = bean.getCollectionTime()) <= 0L) continue;
            total += t;
        }
        return total;
    }

    private void maybeSampleWorld(long nowMs) {
        if (nowMs - lastSectionSample >= 200L) {
            lastSectionSample = nowMs;
            sampleSections(nowMs);
        }
        if (nowMs - lastWorldSample < 500L) return;
        lastWorldSample = nowMs;
        baseEntities = countEntities();
        baseParticles = countParticles();
    }

    private void sampleSections(long nowMs) {
        String stats = sectionStats();
        if (stats == null) return;
        lastSectionStats = stats;
        int pc = parseStat(stats, "pC:");
        int pu = parseStat(stats, "pU:");
        if (nowMs - pcRecentTime > 2000L) { pcRecent = 0; puRecent = 0; }
        if (pc >= 0 && (pc >= pcRecent || nowMs - pcRecentTime > 2000L)) { pcRecent = pc; pcRecentTime = nowMs; }
        if (pu > puRecent) { puRecent = pu; pcRecentTime = nowMs; }
    }

    private void detectSpike(double frameMs, long gcDelta, long gcPauseDelta, long nowMs) {
        if (count < 20 || frameMs < SPIKE_FLOOR_MS || frameMs < baselineMs * SPIKE_MULTIPLIER) return;
        int entities = countEntities();
        int particles = countParticles();
        int entDelta = entities - baseEntities;
        int partDelta = particles < 0 || baseParticles < 0 ? 0 : particles - baseParticles;
        boolean chunkChurn = nowMs - pcRecentTime <= 2000L && (pcRecent >= 4 || puRecent >= 2);
        gcPauseDelta = Math.max(0L, gcPauseDelta);
        String cause, detail;
        if (gcDelta > 0L && gcPauseDelta >= 5L && gcPauseDelta >= 0.35 * frameMs) {
            cause = "Сборка мусора (GC)";
            detail = String.format(Locale.ROOT, "STW-пауза ~%d мс, аллокации %s/с", gcPauseDelta, mib(allocRatePerSec));
        } else if (chunkChurn) {
            cause = "Перестройка чанков";
            detail = "очередь pC:" + pcRecent + " pU:" + puRecent + ", рендер строит геометрию";
        } else if (partDelta > 800 || particles > 5000) {
            cause = "Частицы";
            detail = "частиц: " + particles + " (+" + partDelta + ")";
        } else if (entDelta > 30) {
            cause = "Подгрузка сущностей";
            detail = "сущностей: " + entities + " (+" + entDelta + ")";
        } else if (allocRatePerSec > 1_572_864_000L) {
            cause = "Высокие аллокации";
            detail = "аллокации " + mib(allocRatePerSec) + "/с, возможное давление на GC";
        } else {
            cause = "Внешний стоп";
            detail = "GC, чанки и мир спокойны: вероятны ОС, драйвер, фоновые процессы или троттлинг";
        }
        spikes.addFirst(new Spike(nowMs, frameMs, baselineMs, cause, detail, gcPauseDelta, allocRatePerSec, pcRecent, puRecent, entities, particles));
        while (spikes.size() > CAPACITY) spikes.removeLast();
    }

    private void saveReport() {
        long endMs = System.currentTimeMillis();
        List<String> lines = new ArrayList<>();
        lines.add("Отчёт LinkenVisuals Profiler");
        lines.add("Начало: " + Instant.ofEpochMilli(startedAtMs));
        lines.add("Окончание: " + Instant.ofEpochMilli(endMs));
        lines.add("Длительность: " + Math.max(0L, endMs - startedAtMs) / 1000L + " с");
        lines.add("");
        appendFrameStats(lines);
        appendRuntimeStats(lines);
        appendWorldStats(lines);
        appendThreadStats(lines);
        appendSpikes(lines, endMs);
        Path dir = FabricLoader.getInstance().getConfigDir().resolve("linkenvisuals").resolve("profiler");
        Path file = dir.resolve("profiler-" + REPORT_TIME.format(Instant.ofEpochMilli(endMs)) + ".txt");
        try {
            Files.createDirectories(dir);
            Files.writeString(file, String.join(System.lineSeparator(), lines) + System.lineSeparator(), StandardCharsets.UTF_8);
            LinkenVisuals.LOGGER.info("[Profiler] Отчёт сохранён: {}", file);
        } catch (IOException ex) {
            LinkenVisuals.LOGGER.error("[Profiler] Не удалось сохранить отчёт в {}", file, ex);
        }
    }

    private void appendFrameStats(List<String> list) {
        list.add("Кадры");
        if (count == 0) { list.add("Корректных кадров не записано."); return; }
        double sum = 0.0, min = Double.MAX_VALUE, max = 0.0;
        for (int i = 0; i < count; i++) {
            double v = frames[(head - count + i + CAPACITY) % CAPACITY];
            sum += v; min = Math.min(min, v); max = Math.max(max, v);
        }
        recomputeBaseline();
        double avg = sum / count;
        double p99 = scratch[(int) Math.floor(0.99 * (count - 1))];
        double p999 = scratch[(int) Math.floor(0.999 * (count - 1))];
        list.add(String.format(Locale.ROOT, "Выборка: %d; среднее %.1f мс (%.0f FPS); мин. %.1f мс; макс. %.1f мс", count, avg, 1000.0 / avg, min, max));
        list.add(String.format(Locale.ROOT, "1%% low: %.0f FPS (%.1f мс); 0.1%% low: %.0f FPS (%.1f мс); медиана %.1f мс", 1000.0 / p99, p99, 1000.0 / p999, p999, baselineMs));
    }

    private void appendRuntimeStats(List<String> list) {
        list.add("");
        list.add("Память и GC");
        list.add("Куча: " + mib(usedHeap()) + " / " + mib(Runtime.getRuntime().maxMemory()) + "; аллокации: " + mib(allocRatePerSec) + "/с");
        for (GarbageCollectorMXBean bean : gcBeans) {
            list.add(bean.getName() + ": " + bean.getCollectionCount() + " сборок, " + bean.getCollectionTime() + " мс всего");
        }
    }

    private void appendWorldStats(List<String> list) {
        list.add("");
        list.add("Мир");
        if (mc.world == null) { list.add("В момент сохранения мир не был загружен."); return; }
        int particles = countParticles();
        list.add("Сущности: " + countEntities() + "; частицы: " + (particles < 0 ? "недоступно" : particles));
        if (lastSectionStats != null) list.add("Статистика секций: " + lastSectionStats);
    }

    private void appendThreadStats(List<String> list) {
        list.add("");
        list.add("Топ JVM-потоков по накопленному CPU-времени");
        for (String s : topThreads(6)) list.add("- " + s);
    }

    private void appendSpikes(List<String> list, long endMs) {
        list.add("");
        list.add("Найдено подозрений: " + spikes.size());
        if (spikes.isEmpty()) { list.add("Просадок, прошедших порог детектора, не найдено."); return; }
        for (Spike s : spikes) {
            long ago = Math.max(0L, endMs - s.timeMs()) / 1000L;
            list.add(String.format(Locale.ROOT, "- %.1f мс (норма %.1f мс), %s, за %d с до выключения", s.frameMs(), s.baselineMs(), s.cause(), ago));
            list.add(String.format(Locale.ROOT, "  %s; пауза GC %d мс; аллокации %s/с; pC%d pU%d; сущности %d; частицы %s",
                    s.detail(), s.gcPauseMs(), mib(s.allocPerSec()), s.pc(), s.pu(), s.entities(), s.particles() < 0 ? "недоступно" : String.valueOf(s.particles())));
        }
    }

    private List<String> topThreads(int n) {
        List<String> out = new ArrayList<>();
        try {
            if (!threadBean.isThreadCpuTimeSupported()) { out.add("JVM не поддерживает CPU-тайминг потоков."); return out; }
            if (!threadBean.isThreadCpuTimeEnabled()) threadBean.setThreadCpuTimeEnabled(true);
            List<ThreadCpu> cpus = new ArrayList<>();
            for (long id : threadBean.getAllThreadIds()) {
                long cpu = threadBean.getThreadCpuTime(id);
                if (cpu <= 0L) continue;
                ThreadInfo info = threadBean.getThreadInfo(id);
                cpus.add(new ThreadCpu(info == null ? "#" + id : info.getThreadName(), cpu));
            }
            cpus.sort((a, b) -> Long.compare(b.cpuNanos(), a.cpuNanos()));
            for (int i = 0; i < Math.min(n, cpus.size()); i++) {
                ThreadCpu c = cpus.get(i);
                out.add(truncate(c.name(), 28) + " " + c.cpuNanos() / 1_000_000L + " ms");
            }
        } catch (Throwable t) {
            out.add("Не удалось прочитать потоки: " + t.getClass().getSimpleName());
        }
        return out;
    }

    private int countEntities() {
        if (mc.world == null) return 0;
        try {
            int n = 0;
            for (Entity ignored : mc.world.getEntities()) n++;
            return n;
        } catch (Throwable t) {
            return -1;
        }
    }

    private int countParticles() {
        if (mc.world == null) return -1;
        try {
            if (!reflectionResolved) {
                particleManagerField = findField(mc.getClass(), "particleManager");
                if (particleManagerField != null) {
                    particleManagerField.setAccessible(true);
                    Object pm = particleManagerField.get(mc);
                    if (pm != null) {
                        particlesMapField = findField(pm.getClass(), "particles");
                        if (particlesMapField != null) particlesMapField.setAccessible(true);
                    }
                }
                reflectionResolved = true;
            }
            if (particleManagerField == null || particlesMapField == null) return -1;
            Object pm = particleManagerField.get(mc);
            Object map = pm == null ? null : particlesMapField.get(pm);
            if (!(map instanceof Map<?, ?> m)) return -1;
            int n = 0;
            for (Object v : m.values()) if (v instanceof Collection<?> col) n += col.size();
            return n;
        } catch (Throwable t) {
            return -1;
        }
    }

    private String sectionStats() {
        try {
            Method method = mc.worldRenderer.getClass().getMethod("getChunksDebugString");
            Object res = method.invoke(mc.worldRenderer);
            return res == null ? null : res.toString();
        } catch (Throwable t) {
            return null;
        }
    }

    private static Field findField(Class<?> clazz, String name) {
        for (Class<?> c = clazz; c != null && c != Object.class; c = c.getSuperclass()) {
            try {
                return c.getDeclaredField(name);
            } catch (NoSuchFieldException ignored) {
            }
        }
        return null;
    }

    private static String truncate(String s, int n) {
        return s.length() <= n ? s : s.substring(0, n - 1) + "...";
    }

    private static int parseStat(String s, String key) {
        int i = s.indexOf(key);
        if (i < 0) return -1;
        i += key.length();
        while (i < s.length() && s.charAt(i) == ' ') i++;
        int start = i;
        while (i < s.length() && Character.isDigit(s.charAt(i))) i++;
        try {
            return start == i ? -1 : Integer.parseInt(s.substring(start, i));
        } catch (NumberFormatException ex) {
            return -1;
        }
    }

    private static long usedHeap() {
        Runtime rt = Runtime.getRuntime();
        return rt.totalMemory() - rt.freeMemory();
    }

    private static String mib(long bytes) {
        double m = bytes / 1_048_576.0;
        return m >= 1024.0 ? String.format(Locale.ROOT, "%.2f GiB", m / 1024.0) : String.format(Locale.ROOT, "%.0f MiB", m);
    }

    public record Spike(long timeMs, double frameMs, double baselineMs, String cause, String detail,
                        long gcPauseMs, long allocPerSec, int pc, int pu, int entities, int particles) {}

    public record ThreadCpu(String name, long cpuNanos) {}
}
