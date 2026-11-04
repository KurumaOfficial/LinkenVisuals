package dev.linkenvisuals.ui.linkmenu;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.linkenvisuals.LinkenVisuals;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.client.texture.TextureManager;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Растеризатор MSDF-шрифтов на CPU — единственный способ показать фирменные шрифты на стартовом
 * загрузочном экране.
 *
 * <p><b>Зачем.</b> Во время стартового ресурс-релоада не загружено вообще ничего из того, чем мод
 * обычно рисует текст: ни шейдер мода {@code linkenvisuals:core/rectangle} (через который работает
 * {@link ru.method.linkenvisuals.utils.render.Render2D}), ни ванильный {@code minecraft:core/rendertype_text}
 * (через который работает {@code DrawContext#drawText}). Оба падают с «Failed to load shader program»,
 * и текст не рисуется НИ ОДНОЙ буквой — что и превращало кастомный сплэш в пустой тёмный экран.
 *
 * <p><b>Как обходим.</b> MSDF-атласы мода ({@code assets/linkenvisuals/fonts/*.png|json}) читаем
 * напрямую из jar через classloader — мимо {@code ResourceManager}, поэтому они доступны с первого
 * кадра. Формулу MSDF (медиана RGB → знаковое расстояние → альфа) считаем на CPU, результат
 * складываем в {@link NativeImage} и заливаем динамической текстурой. Дальше это обычная GUI-текстура,
 * а {@code DrawContext#drawTexture} на сплэше работает — этим же ваниль рисует логотип Mojang.
 *
 * <p>Растр делается сразу в ФИЗИЧЕСКИХ пикселях (кегль × scaleFactor), поэтому текст остаётся чётким
 * на любом разрешении и при любом guiScale, включая полноэкранный режим на большом мониторе.
 *
 * <p>Всё выделенное освобождается в {@link #dispose()} при снятии оверлея — в игре эти текстуры
 * больше не нужны.
 */
public final class SplashFont {

    private SplashFont() {}

    private static final Logger LOGGER = LoggerFactory.getLogger("VisualsModification/Splash");

    public enum Face {
        GOLOS("golos"),
        GOLOS_SEMI("golos_semi"),
        UNBOUNDED("unbounded"),
        PLEXMONO("plexmono");

        private final String file;

        Face(String file) {
            this.file = file;
        }
    }

    /**
     * Готовая к выводу строка. Все величины — в ФИЗИЧЕСКИХ пикселях.
     *
     * @param width    ширина картинки
     * @param height   высота картинки
     * @param pad      поле вокруг текста внутри картинки (перо строки начинается на {@code pad})
     * @param baseline базовая линия, отсчёт от верхней кромки картинки
     * @param advance  ширина строки по метрикам шрифта (без полей) — по ней центрируем и стыкуем
     */
    public record Run(Identifier id, int width, int height, int pad, float baseline, float advance) {}

    private record Glyph(float advance, boolean quad,
                         float planeLeft, float planeBottom, float planeRight, float planeTop,
                         float atlasLeft, float atlasBottom, float atlasRight, float atlasTop) {}

    private record Atlas(NativeImage image, Map<Integer, Glyph> glyphs,
                         float emPx, float distanceRange, float ascender, float descender) {}

    private static final Map<Face, Atlas> ATLASES = new HashMap<>();
    private static final Map<Face, Boolean> FAILED = new HashMap<>();
    private static final Map<String, Run> RUNS = new LinkedHashMap<>();
    private static int sequence;

    // ─────────────────────────────── публичное API ───────────────────────────────

    /**
     * Растеризует строку и возвращает готовую текстуру (или {@code null}, если шрифт не поднялся —
     * вызывающий обязан пережить это без чёрного экрана).
     *
     * @param pxSize     кегль в физических пикселях
     * @param rgb        цвет без альфы
     * @param trackingEm межбуквенный интервал в долях em (letter-spacing)
     * @param alignTo    выравнивание размеров картинки вверх до кратности (= guiScale), чтобы
     *                   текстура ложилась на экран ровно 1:1 и nearest-фильтр не мылил края
     */
    public static Run text(Face face, String text, int pxSize, int rgb, float trackingEm, int alignTo) {
        if (text == null || text.isEmpty() || pxSize <= 0) return null;
        String key = face.name() + '|' + pxSize + '|' + rgb + '|' + trackingEm + '|' + alignTo + '|' + text;
        Run cached = RUNS.get(key);
        if (cached != null) return cached;

        Run run;
        try {
            run = rasterize(face, text, pxSize, rgb, trackingEm, Math.max(1, alignTo));
        } catch (Throwable t) {
            logOnce(face, t);
            run = null;
        }
        if (run != null) RUNS.put(key, run);
        return run;
    }

    /**
     * Мягкое эллиптическое свечение акцентом (в дизайне — radial-gradient за логотипом).
     * Генерится один раз в небольшом размере и растягивается: на альфе ≤ 0.06 ступенчатость
     * nearest-фильтра не видна, а лёгкий дизеринг добивает возможные полосы.
     */
    public static Run glow(int rgb, float maxAlpha) {
        String key = "glow|" + rgb + '|' + maxAlpha;
        Run cached = RUNS.get(key);
        if (cached != null) return cached;

        Run run;
        try {
            run = buildGlow(rgb, maxAlpha);
        } catch (Throwable t) {
            LOGGER.warn("[splash] не удалось создать свечение", t);
            run = null;
        }
        if (run != null) RUNS.put(key, run);
        return run;
    }

    /** Освобождает все текстуры и атласы — зовётся при снятии загрузочного оверлея. */
    public static void dispose() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc != null) {
            TextureManager tm = mc.getTextureManager();
            if (tm != null) {
                for (Run run : RUNS.values()) {
                    try {
                        tm.destroyTexture(run.id());
                    } catch (Throwable ignored) {
                    }
                }
            }
        }
        RUNS.clear();
        for (Atlas atlas : ATLASES.values()) {
            try {
                atlas.image().close();
            } catch (Throwable ignored) {
            }
        }
        ATLASES.clear();
        FAILED.clear();
    }

    // ─────────────────────────────── загрузка атласа ───────────────────────────────

    private static Atlas atlas(Face face) {
        Atlas loaded = ATLASES.get(face);
        if (loaded != null) return loaded;
        if (FAILED.containsKey(face)) return null;

        try {
            Atlas atlas = load(face);
            ATLASES.put(face, atlas);
            return atlas;
        } catch (Throwable t) {
            logOnce(face, t);
            return null;
        }
    }

    private static Atlas load(Face face) throws IOException {
        JsonObject root;
        try (InputStream in = open(face.file + ".json")) {
            root = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
        }
        NativeImage image;
        try (InputStream in = open(face.file + ".png")) {
            image = NativeImage.read(in);
        }

        JsonObject atlasJson = root.getAsJsonObject("atlas");
        JsonObject metrics = root.getAsJsonObject("metrics");

        Map<Integer, Glyph> glyphs = new HashMap<>();
        for (JsonElement element : root.getAsJsonArray("glyphs")) {
            JsonObject g = element.getAsJsonObject();
            int codePoint = g.get("unicode").getAsInt();
            float advance = g.get("advance").getAsFloat();
            if (!g.has("planeBounds") || !g.has("atlasBounds")) {
                // Пробел и прочие пустые глифы: есть только продвижение пера.
                glyphs.put(codePoint, new Glyph(advance, false, 0, 0, 0, 0, 0, 0, 0, 0));
                continue;
            }
            JsonObject plane = g.getAsJsonObject("planeBounds");
            JsonObject bounds = g.getAsJsonObject("atlasBounds");
            glyphs.put(codePoint, new Glyph(advance, true,
                    plane.get("left").getAsFloat(), plane.get("bottom").getAsFloat(),
                    plane.get("right").getAsFloat(), plane.get("top").getAsFloat(),
                    bounds.get("left").getAsFloat(), bounds.get("bottom").getAsFloat(),
                    bounds.get("right").getAsFloat(), bounds.get("top").getAsFloat()));
        }

        return new Atlas(image, glyphs,
                atlasJson.get("size").getAsFloat(),
                atlasJson.get("distanceRange").getAsFloat(),
                metrics.get("ascender").getAsFloat(),
                metrics.get("descender").getAsFloat());
    }

    private static InputStream open(String name) throws IOException {
        // Именно classloader, а НЕ ResourceManager: во время стартового релоада менеджер ресурсов
        // ещё не готов, а jar читается всегда.
        InputStream in = SplashFont.class.getResourceAsStream("/assets/linkenvisuals/fonts/link/" + name);
        if (in == null) throw new FileNotFoundException("assets/linkenvisuals/fonts/link/" + name);
        return in;
    }

    // ─────────────────────────────── растеризация ───────────────────────────────

    private static Run rasterize(Face face, String text, int pxSize, int rgb, float trackingEm, int alignTo) {
        Atlas atlas = atlas(face);
        if (atlas == null) return null;

        float px = pxSize;
        int length = text.length();

        float advanceEm = 0f;
        for (int i = 0; i < length; i++) {
            Glyph glyph = atlas.glyphs().get((int) text.charAt(i));
            if (glyph == null) continue;
            advanceEm += glyph.advance() + (i < length - 1 ? trackingEm : 0f);
        }
        if (advanceEm <= 0f) return null;

        // Поле по краям: у глифов бывают отрицательные боковые отступы и свесы за продвижение пера.
        int pad = Math.max(2, Math.round(px * 0.08f));
        float advancePx = advanceEm * px;
        int width = align(MathHelper.ceil(advancePx) + pad * 2, alignTo);
        int height = align(MathHelper.ceil((atlas.ascender() - atlas.descender()) * px) + pad * 2, alignTo);
        float baseline = pad + atlas.ascender() * px;

        // Разброс SDF в пикселях цели: сколько экранных пикселей приходится на диапазон расстояний
        // атласа при данном кегле. Ниже единицы получается алиасинг, поэтому зажимаем.
        float pxRange = Math.max(1f, atlas.distanceRange() * (px / atlas.emPx()));

        byte[] coverage = new byte[width * height];
        float pen = pad;
        for (int i = 0; i < length; i++) {
            Glyph glyph = atlas.glyphs().get((int) text.charAt(i));
            if (glyph == null) continue;
            if (glyph.quad()) blit(atlas, glyph, coverage, width, height, pen, baseline, px, pxRange);
            pen += (glyph.advance() + (i < length - 1 ? trackingEm : 0f)) * px;
        }

        NativeImage image = new NativeImage(width, height, true);
        int base = rgb & 0xFFFFFF;
        for (int y = 0; y < height; y++) {
            int row = y * width;
            for (int x = 0; x < width; x++) {
                image.setColorArgb(x, y, ((coverage[row + x] & 0xFF) << 24) | base);
            }
        }
        return register(image, pad, baseline, advancePx);
    }

    private static void blit(Atlas atlas, Glyph glyph, byte[] coverage, int width, int height,
                             float pen, float baseline, float px, float pxRange) {
        float x0 = pen + glyph.planeLeft() * px;
        float x1 = pen + glyph.planeRight() * px;
        // planeBounds заданы вверх от базовой линии, экранный Y растёт вниз — отсюда инверсия.
        float y0 = baseline - glyph.planeTop() * px;
        float y1 = baseline - glyph.planeBottom() * px;
        float quadWidth = x1 - x0;
        float quadHeight = y1 - y0;
        if (quadWidth <= 0f || quadHeight <= 0f) return;

        int fromX = Math.max(0, MathHelper.floor(x0));
        int toX = Math.min(width - 1, MathHelper.ceil(x1));
        int fromY = Math.max(0, MathHelper.floor(y0));
        int toY = Math.min(height - 1, MathHelper.ceil(y1));

        NativeImage image = atlas.image();
        int imageHeight = image.getHeight();

        for (int y = fromY; y <= toY; y++) {
            // Зажимаем 0..1 обязательно: границы цикла округлены наружу, и без зажима выборка уходит
            // за atlasBounds — то есть в СОСЕДНИЙ глиф атласа. На мелком кегле это давало призраков
            // чужих букв рядом с каждой буквой.
            float v = MathHelper.clamp((y + 0.5f - y0) / quadHeight, 0f, 1f);
            // atlasBounds записаны от НИЖНЕЙ кромки картинки (yOrigin: bottom) — переводим в строку.
            float atlasY = glyph.atlasTop() - v * (glyph.atlasTop() - glyph.atlasBottom());
            float sampleY = imageHeight - atlasY - 0.5f;
            for (int x = fromX; x <= toX; x++) {
                float u = MathHelper.clamp((x + 0.5f - x0) / quadWidth, 0f, 1f);
                float sampleX = glyph.atlasLeft() + u * (glyph.atlasRight() - glyph.atlasLeft()) - 0.5f;

                float distance = sampleMedian(image, sampleX, sampleY);
                float alpha = MathHelper.clamp((distance - 0.5f) * pxRange + 0.5f, 0f, 1f);
                if (alpha <= 0.002f) continue;

                int index = y * width + x;
                int value = Math.round(alpha * 255f);
                // Глифы могут заезжать друг на друга отрицательными отступами — берём максимум,
                // иначе на стыке появляется светлая щель.
                if ((coverage[index] & 0xFF) < value) coverage[index] = (byte) value;
            }
        }
    }

    /** Билинейная выборка по каждому каналу, затем медиана — ровно то, что делает MSDF-шейдер. */
    private static float sampleMedian(NativeImage image, float x, float y) {
        int x0 = MathHelper.floor(x);
        int y0 = MathHelper.floor(y);
        float fx = x - x0;
        float fy = y - y0;

        int c00 = texel(image, x0, y0);
        int c10 = texel(image, x0 + 1, y0);
        int c01 = texel(image, x0, y0 + 1);
        int c11 = texel(image, x0 + 1, y0 + 1);

        float r = bilerp(fx, fy, (c00 >> 16) & 0xFF, (c10 >> 16) & 0xFF, (c01 >> 16) & 0xFF, (c11 >> 16) & 0xFF);
        float g = bilerp(fx, fy, (c00 >> 8) & 0xFF, (c10 >> 8) & 0xFF, (c01 >> 8) & 0xFF, (c11 >> 8) & 0xFF);
        float b = bilerp(fx, fy, c00 & 0xFF, c10 & 0xFF, c01 & 0xFF, c11 & 0xFF);

        return median(r, g, b) / 255f;
    }

    private static int texel(NativeImage image, int x, int y) {
        int cx = MathHelper.clamp(x, 0, image.getWidth() - 1);
        int cy = MathHelper.clamp(y, 0, image.getHeight() - 1);
        return image.getColorArgb(cx, cy);
    }

    private static float bilerp(float fx, float fy, int v00, int v10, int v01, int v11) {
        float top = v00 + (v10 - v00) * fx;
        float bottom = v01 + (v11 - v01) * fx;
        return top + (bottom - top) * fy;
    }

    private static float median(float a, float b, float c) {
        return Math.max(Math.min(a, b), Math.min(Math.max(a, b), c));
    }

    // ─────────────────────────────── свечение ───────────────────────────────

    private static Run buildGlow(int rgb, float maxAlpha) {
        int size = 256;
        NativeImage image = new NativeImage(size, size, true);
        int base = rgb & 0xFFFFFF;
        float peak = maxAlpha * 255f;
        for (int y = 0; y < size; y++) {
            float ny = (y + 0.5f) / size * 2f - 1f;
            for (int x = 0; x < size; x++) {
                float nx = (x + 0.5f) / size * 2f - 1f;
                float distance = (float) Math.sqrt(nx * nx + ny * ny);
                float falloff = MathHelper.clamp(1f - distance, 0f, 1f);
                falloff = falloff * falloff * (3f - 2f * falloff); // smoothstep
                // Дизеринг: без него на 15 уровнях альфы растяжка даёт видимые кольца.
                float dithered = falloff * peak + (hash(x, y) - 0.5f);
                int alpha = MathHelper.clamp(Math.round(dithered), 0, 255);
                image.setColorArgb(x, y, (alpha << 24) | base);
            }
        }
        return register(image, 0, 0f, size);
    }

    private static float hash(int x, int y) {
        int h = x * 374761393 + y * 668265263;
        h = (h ^ (h >> 13)) * 1274126177;
        return ((h ^ (h >> 16)) & 0xFFFF) / 65535f;
    }

    // ─────────────────────────────── вспомогательное ───────────────────────────────

    private static Run register(NativeImage image, int pad, float baseline, float advance) {
        Identifier id = LinkenVisuals.id("splash/run_" + (sequence++));
        MinecraftClient.getInstance().getTextureManager()
                .registerTexture(id, new NativeImageBackedTexture(id::toString, image));
        return new Run(id, image.getWidth(), image.getHeight(), pad, baseline, advance);
    }

    private static int align(int value, int step) {
        if (step <= 1) return value;
        int remainder = value % step;
        return remainder == 0 ? value : value + (step - remainder);
    }

    private static void logOnce(Face face, Throwable t) {
        if (FAILED.putIfAbsent(face, Boolean.TRUE) == null) {
            LOGGER.warn("[splash] шрифт {} недоступен, текст на загрузке будет пропущен", face.file, t);
        }
    }
}
