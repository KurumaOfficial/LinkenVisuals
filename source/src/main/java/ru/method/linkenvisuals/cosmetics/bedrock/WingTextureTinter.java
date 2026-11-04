package ru.method.linkenvisuals.cosmetics.bedrock;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.resource.Resource;
import net.minecraft.util.Identifier;
import ru.method.linkenvisuals.LinkenVisuals;

import java.io.InputStream;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Тонирует серую текстуру косметики на CPU (пиксель × цвет тинта) и кэширует результат.
 * Нужно потому, что vertex-color тинт у рендер-слоя сущностей даёт только яркость, а не оттенок —
 * красный «не красится». Здесь оттенок гарантирован: текстура физически перекрашивается.
 */
public final class WingTextureTinter {
    private static final Map<String, Identifier> CACHE = new ConcurrentHashMap<>();

    private WingTextureTinter() {
    }

    /** Возвращает Identifier перекрашенной текстуры (или базовую, если тинт белый). */
    public static Identifier tinted(Identifier base, int argb) {
        if (base == null) {
            return null;
        }
        int tr = (argb >> 16) & 0xF8;
        int tg = (argb >> 8) & 0xF8;
        int tb = argb & 0xF8;
        if (tr >= 0xF8 && tg >= 0xF8 && tb >= 0xF8) {
            return base; // белый — без перекраски
        }
        String key = base + "#" + tr + "_" + tg + "_" + tb;
        Identifier cached = CACHE.get(key);
        if (cached != null) {
            return cached;
        }
        Identifier created = generate(base, tr, tg, tb, key);
        if (created != null) {
            CACHE.put(key, created);
            return created;
        }
        return base;
    }

    private static Identifier generate(Identifier base, int tr, int tg, int tb, String key) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null) {
            return null;
        }
        try {
            Optional<Resource> res = mc.getResourceManager().getResource(base);
            if (res.isEmpty()) {
                return null;
            }
            NativeImage src;
            try (InputStream in = res.get().getInputStream()) {
                src = NativeImage.read(in);
            }
            int w = src.getWidth();
            int h = src.getHeight();
            NativeImage out = new NativeImage(w, h, true);
            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) {
                    int p = src.getColorArgb(x, y);
                    int a = (p >> 24) & 0xFF;
                    int r = (p >> 16) & 0xFF;
                    int g = (p >> 8) & 0xFF;
                    int b = p & 0xFF;
                    int nr = r * tr / 255;
                    int ng = g * tg / 255;
                    int nb = b * tb / 255;
                    out.setColorArgb(x, y, (a << 24) | (nr << 16) | (ng << 8) | nb);
                }
            }
            src.close();

            Identifier id = LinkenVisuals.id("dynamic/wing_tint_" + Integer.toHexString(Math.abs(key.hashCode())));
            NativeImageBackedTexture tex = new NativeImageBackedTexture(() -> "wing_tint", out);
            mc.getTextureManager().registerTexture(id, tex);
            return id;
        } catch (Exception e) {
            LinkenVisuals.LOGGER.warn("[Wings] tint texture failed for {}", base, e);
            return null;
        }
    }
}
