package ru.method.linkenvisuals.utils.render;

import net.minecraft.util.Identifier;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class GifCache {
    private static final Map<Identifier, GifTexture> CACHE = new ConcurrentHashMap<>();

    private GifCache() {
    }

    public static GifTexture get(Identifier resourceId) {
        if (resourceId == null) {
            return null;
        }
        return CACHE.computeIfAbsent(resourceId, GifTexture::new);
    }

    public static void preload(Identifier resourceId) {
        get(resourceId);
    }

    public static void clear() {
        CACHE.clear();
    }
}
