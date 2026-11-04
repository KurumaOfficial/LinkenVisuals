package ru.method.linkenvisuals.cosmetics.laby;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.util.Identifier;
import ru.method.linkenvisuals.LinkenVisuals;

/**
 * Плащи с laby.net. По ID тянет ГОТОВУЮ текстуру плаща {@code /api/v3/texture/{id}/cape.png} — это
 * полная развёртка 64×32 (или HD-кратная ей), ровно в том виде, в каком её нарисовал автор, — и
 * регистрирует под {@code linkenvisuals:laby_cape/<id>}. Если полной развёртки нет, собираем плащ из
 * одной передней грани {@code capeFront.png} на тёмной подложке.
 *
 * <p>Загрузка асинхронная и кешируется; {@link #textureFor} возвращает {@code null}, пока не готово —
 * плащ появится сам, как только текстура догрузится.</p>
 */
public final class LabyCapeManager {

    private static final String API = "https://laby.net/api/v3/texture/";
    // Повтор после сетевой ошибки: без этого одна неудача навсегда гасила плащ до перезапуска клиента.
    private static final long RETRY_AFTER_MS = 30_000L;
    // 32 hex id (в ссылке вида laby.net/capes/<id> или голым).
    private static final Pattern ID = Pattern.compile("([0-9a-fA-F]{32})");

    private static final Map<String, Identifier> CACHE = new ConcurrentHashMap<>();
    private static final Set<String> LOADING = ConcurrentHashMap.newKeySet();
    // Момент последней неудачи: не долбим фетч каждый кадр, но и не хороним плащ навсегда.
    private static final Map<String, Long> FAILED = new ConcurrentHashMap<>();

    // Известные (применённые) плащи — сохраняются в файл, показываются в сетке как «Custom Cape».
    private static final LinkedHashSet<String> KNOWN = new LinkedHashSet<>();
    private static boolean knownLoaded;

    private LabyCapeManager() {
    }

    private static Path knownFile() {
        return FabricLoader.getInstance().getConfigDir().resolve("linkenvisuals-laby-capes.txt");
    }

    private static synchronized void ensureKnownLoaded() {
        if (knownLoaded) return;
        knownLoaded = true;
        try {
            Path f = knownFile();
            if (Files.exists(f)) {
                for (String line : Files.readAllLines(f)) {
                    String s = line.trim().toLowerCase();
                    // laby cloak-ID бывают 16-hex (не только 32) — принимаем любой hex длиной ≥16.
                    if (s.matches("[0-9a-f]{16,}")) KNOWN.add(s);
                }
            }
        } catch (Throwable ignored) {
        }
    }

    /** Запомнить применённый плащ (для сетки «Custom Cape») и сохранить список. */
    public static synchronized void addKnown(String id) {
        if (id == null || !id.matches("[0-9a-f]{16,}")) return;
        ensureKnownLoaded();
        if (KNOWN.add(id)) {
            try {
                Files.write(knownFile(), KNOWN);
            } catch (Throwable ignored) {
            }
        }
    }

    /** Удалить применённый плащ из списка (кнопка удаления кастома) и сохранить. */
    public static synchronized void removeKnown(String id) {
        if (id == null) return;
        ensureKnownLoaded();
        if (KNOWN.remove(id.toLowerCase())) {
            try {
                Files.write(knownFile(), KNOWN);
            } catch (Throwable ignored) {
            }
        }
    }

    /** Список известных плащей (старые → новые) для отрисовки в сетке. */
    public static synchronized List<String> knownIds() {
        ensureKnownLoaded();
        return new ArrayList<>(KNOWN);
    }

    /** Состояние плаща для UI: готов / грузится / не смогли. */
    public enum State {
        READY,
        LOADING,
        FAILED
    }

    /** Состояние плаща БЕЗ запуска загрузки (для отрисовки сетки). */
    public static State state(String id) {
        if (id == null || id.isBlank()) {
            return State.FAILED;
        }
        if (CACHE.containsKey(id)) {
            return State.READY;
        }
        return failedRecently(id) ? State.FAILED : State.LOADING;
    }

    private static boolean failedRecently(String id) {
        Long at = FAILED.get(id);
        return at != null && System.currentTimeMillis() - at < RETRY_AFTER_MS;
    }

    /** Короткая различимая подпись плаща: «Cape · a1b2c3» вместо одинакового «Custom Cape» у всех. */
    public static String shortLabel(String id) {
        if (id == null || id.isBlank()) {
            return "Cape";
        }
        return "Cape · " + id.substring(0, Math.min(6, id.length()));
    }

    // ID прямо из пути ссылки laby.net: /capes/<id> (текущий адрес) и /cloak(s)/<id> (старый),
    // в т.ч. с языковым префиксом вида /ru/.
    private static final Pattern PATH_ID = Pattern.compile("(?:capes?|cloaks?)/([0-9a-fA-F]{16,})");

    /** Достаёт ID плаща из ссылки laby.net (/capes/<id>), голого 32-hex или дашед-UUID; null, если не нашёл. */
    public static String parseId(String input) {
        if (input == null) return null;
        String s = input.trim();
        // 1) ID из сегмента пути — самый надёжный источник (не зависит от длины/формата id).
        Matcher p = PATH_ID.matcher(s);
        if (p.find()) return p.group(1).toLowerCase();
        // 2) 32-hex подряд (голый id).
        Matcher m = ID.matcher(s);
        if (m.find()) return m.group(1).toLowerCase();
        // 3) Дашед-UUID (8-4-4-4-12) — убираем дефисы и пробуем снова.
        m = ID.matcher(s.replace("-", ""));
        if (m.find()) return m.group(1).toLowerCase();
        return null;
    }

    /** Текстура плаща для ID (или null, пока грузится/если ошибка). Запускает фон-загрузку при первом вызове. */
    public static Identifier textureFor(String id) {
        if (id == null || id.isBlank()) return null;
        Identifier cached = CACHE.get(id);
        if (cached != null) return cached;
        if (failedRecently(id)) return null;       // недавно падало — не пробуем каждый кадр
        if (LOADING.add(id)) fetchAsync(id);
        return null;
    }

    private static void fetchAsync(String id) {
        Thread t = new Thread(() -> {
            try {
                NativeImage cape = loadCape(id);   // сеть + пиксель-циклы на ФОНОВОМ потоке (не GL)
                MinecraftClient.getInstance().execute(() -> registerCape(id, cape)); // на рендер-треде только upload
            } catch (Throwable e) {
                LinkenVisuals.LOGGER.warn("[LabyCape] не удалось загрузить плащ {}: {}", id, e.toString());
                FAILED.put(id, System.currentTimeMillis());
                LOADING.remove(id);
            }
        }, "LinkenVisuals-LabyCape");
        t.setDaemon(true);
        t.start();
    }

    /**
     * Готовая развёртка плаща, как её отдаёт laby. Основной путь — {@code cape.png}: это целая текстура
     * 64×32 (или HD, кратная ей) со всеми гранями, ничего пересобирать не нужно. Запасной — {@code
     * capeFront.png}: только видимая грань, её кладём на обе стороны поверх тёмной подложки.
     */
    private static NativeImage loadCape(String id) throws Exception {
        // Порядок важен. Плащи из laby.net/cloaks/<id> отдают текстуру по cloak*.png; аккаунт-плащи — по cape*.png.
        // РАНЬШЕ первым шёл /cape.png — для cloak-ID laby по нему возвращает ПУСТОЙ 64×32 (2:1), и код брал его
        // как есть → плащ выходил чёрным. Теперь пробуем cloak* раньше cape* и ПРОПУСКАЕМ пустые ответы.
        // cloakFront/capeFront — чистая передняя грань 10:16; cloak/cape — либо полная развёртка 2:1,
        // либо (у cloak-ID laby) ОБРЕЗОК развёртки 22×17 (≈1.29:1) — это НЕ грань, buildCape его сплющит.
        NativeImage bestFront = null;
        double bestErr = Double.MAX_VALUE;
        for (String suffix : new String[]{"cloakFront.png", "capeFront.png", "cloak.png", "cape.png"}) {
            NativeImage img;
            try {
                img = NativeImage.read(new ByteArrayInputStream(download(API + id + "/" + suffix)));
            } catch (Throwable ignored) {
                continue; // 404/не-PNG — следующий вариант
            }
            if (isBlank(img)) {
                img.close();
                continue; // пустая заглушка (напр. /capeFront.png у cloak-ID) — не годится
            }
            // Полная развёртка плаща (ширина = 2×высота, ≥64) — используем как есть.
            if (img.getWidth() >= 64 && img.getWidth() == img.getHeight() * 2) {
                if (bestFront != null) bestFront.close();
                return img;
            }
            // Иначе это передняя грань. Берём кандидата с соотношением сторон, ближайшим к 10:16 —
            // так отсекаем обрезок-развёртку 22×17 (1.29:1) в пользу настоящей грани 160×256 (0.625:1).
            double err = Math.abs((double) img.getWidth() / img.getHeight() - 10.0 / 16.0);
            if (err < bestErr) {
                if (bestFront != null) bestFront.close();
                bestFront = img;
                bestErr = err;
            } else {
                img.close();
            }
        }
        if (bestFront != null) {
            NativeImage cape = buildCape(bestFront);
            bestFront.close();
            return cape;
        }
        throw new Exception("нет непустой текстуры плаща (cloak/cape) для " + id);
    }

    /** Пустая ли текстура (полностью прозрачная/чёрная) — сэмплируем сеткой, порог 3% содержимого. */
    private static boolean isBlank(NativeImage img) {
        int w = img.getWidth(), h = img.getHeight();
        if (w <= 0 || h <= 0) return true;
        int stepX = Math.max(1, w / 48), stepY = Math.max(1, h / 48);
        int total = 0, nonBlank = 0;
        for (int y = 0; y < h; y += stepY) {
            for (int x = 0; x < w; x += stepX) {
                total++;
                int argb = img.getColorArgb(x, y);
                int a = (argb >>> 24) & 0xFF;
                int r = (argb >> 16) & 0xFF, g = (argb >> 8) & 0xFF, b = argb & 0xFF;
                if (a > 16 && (r > 16 || g > 16 || b > 16)) nonBlank++;
            }
        }
        return total == 0 || nonBlank * 100 < total * 3;
    }

    /** Собрать текстуру плаща из одной передней грани (без GL) — на фоновом потоке. */
    private static NativeImage buildCape(NativeImage front) {
        // Масштаб берём от исходника: у ванильной грани 10×16 это 1, у HD 160×256 — 16. Раньше здесь
        // всегда лепилось 1024×512, из-за чего простой плащ раздувался в 16 раз без выигрыша в детализации.
        int scale = Math.max(1, front.getWidth() / 10);
        int faceW = 10 * scale;
        int faceH = 16 * scale;
        // Автокроп: laby часто отдаёт cloakFront с прозрачными/чёрными полями (фото не 10:16 → дополнено
        // пустым). Без кропа это «чёрное пустое место» на плаще. Вырезаем bbox содержимого и растягиваем
        // его на всю грань.
        int[] bb = contentBox(front);
        NativeImage cape = new NativeImage(NativeImage.Format.RGBA, 64 * scale, 32 * scale, false);
        // Подложка на всю область развёртки (22×17) — растянутая картинка: кромки/швы плаща (u0..1,
        // u11..12, v0..1) берутся из самой картинки, без тёмной рамки по краям.
        blit(front, bb, cape, 0, 0, 22 * scale, 17 * scale);
        // Видимые грани: x=1..10 и x=12..21, y=1..16.
        blit(front, bb, cape, scale, scale, faceW, faceH);
        blit(front, bb, cape, 12 * scale, scale, faceW, faceH);
        return cape;
    }

    /** Bounding box непустых пикселей {x0,y0,w,h}; вся картинка, если полей нет (или почти нет). */
    private static int[] contentBox(NativeImage img) {
        int w = img.getWidth(), h = img.getHeight();
        int x0 = w, y0 = h, x1 = -1, y1 = -1;
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int argb = img.getColorArgb(x, y);
                int a = (argb >>> 24) & 0xFF;
                int r = (argb >> 16) & 0xFF, g = (argb >> 8) & 0xFF, b = argb & 0xFF;
                if (a > 16 && (r > 16 || g > 16 || b > 16)) {
                    if (x < x0) x0 = x;
                    if (x > x1) x1 = x;
                    if (y < y0) y0 = y;
                    if (y > y1) y1 = y;
                }
            }
        }
        if (x1 < 0) return new int[]{0, 0, w, h};                       // всё пусто — не кропаем
        int bw = x1 - x0 + 1, bh = y1 - y0 + 1;
        if (bw * 100 >= w * 97 && bh * 100 >= h * 97) return new int[]{0, 0, w, h}; // полей практически нет
        return new int[]{x0, y0, bw, bh};
    }

    /** GL-регистрация готовой текстуры — ТОЛЬКО на рендер-треде (обёртка + upload, дёшево). */
    private static void registerCape(String id, NativeImage cape) {
        try {
            Identifier tex = Identifier.of("linkenvisuals", "laby_cape/" + id);
            NativeImageBackedTexture texture = new NativeImageBackedTexture(() -> "laby_cape", cape);
            texture.upload();
            MinecraftClient.getInstance().getTextureManager().registerTexture(tex, texture);
            CACHE.put(id, tex);
            FAILED.remove(id);
            LinkenVisuals.LOGGER.info("[LabyCape] плащ {} загружен ({}x{})", id, cape.getWidth(), cape.getHeight());
        } catch (Throwable e) {
            LinkenVisuals.LOGGER.warn("[LabyCape] регистрация плаща {} упала: {}", id, e.toString());
            FAILED.put(id, System.currentTimeMillis()); // не ретраить каждый кадр при ошибке сборки
            try { cape.close(); } catch (Throwable ignored) {}
        } finally {
            LOADING.remove(id);
        }
    }

    /** Копирует регион src (bbox {x0,y0,w,h}) в dst с (dx,dy), растягивая до faceW×faceH. */
    private static void blit(NativeImage src, int[] bb, NativeImage dst, int dx, int dy, int faceW, int faceH) {
        int sw = bb[2], sh = bb[3];
        for (int y = 0; y < faceH; y++) {
            for (int x = 0; x < faceW; x++) {
                int sx = bb[0] + (sw == faceW ? x : x * sw / faceW);
                int sy = bb[1] + (sh == faceH ? y : y * sh / faceH);
                dst.setColorArgb(dx + x, dy + y, src.getColorArgb(sx, sy));
            }
        }
    }

    private static byte[] download(String url) throws Exception {
        HttpURLConnection c = (HttpURLConnection) URI.create(url).toURL().openConnection();
        c.setRequestProperty("User-Agent", "LinkenVisuals");
        c.setConnectTimeout(8000);
        c.setReadTimeout(8000);
        c.setInstanceFollowRedirects(true);
        try (InputStream in = c.getInputStream()) {
            return in.readAllBytes();
        } finally {
            c.disconnect();
        }
    }
}
