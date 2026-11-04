package dev.linkenvisuals.render;

import com.google.gson.Gson;
import com.mojang.blaze3d.systems.RenderSystem;
import dev.linkenvisuals.util.IntFloatMap;
import dev.linkenvisuals.util.IntObjectMap;
import net.minecraft.client.MinecraftClient;
import net.minecraft.resource.Resource;
import net.minecraft.util.Identifier;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

public final class GlyphAtlas {
    private static final Gson GSON = new Gson();

    private final Identifier texture;
    private final float atlasWidth;
    private final float atlasHeight;
    private final float ascender;
    private final float descender;
    private final float lineHeight;
    private final float baselineHeight;
    private final IntObjectMap<Glyph> glyphs;
    private final IntObjectMap<IntFloatMap> kerning;
    private final WidthCache widthCache = new WidthCache(1024);

    private GlyphAtlas(Identifier texture, Data data) {
        this.texture = texture;
        this.atlasWidth = data.atlas.width;
        this.atlasHeight = data.atlas.height;
        this.ascender = data.metrics.ascender;
        this.descender = data.metrics.descender;
        this.lineHeight = data.metrics.lineHeight;
        this.baselineHeight = data.metrics.lineHeight + data.metrics.descender;
        this.glyphs = new IntObjectMap<>(Math.max(16, data.glyphs.length * 2));
        for (GlyphData source : data.glyphs) {
            glyphs.put(source.unicode, new Glyph(source, atlasWidth, atlasHeight));
        }
        this.kerning = new IntObjectMap<>(16);
        if (data.kerning != null) {
            for (KerningData pair : data.kerning) {
                IntFloatMap inner = kerning.get(pair.unicode1);
                if (inner == null) {
                    inner = new IntFloatMap(8);
                    kerning.put(pair.unicode1, inner);
                }
                inner.put(pair.unicode2, pair.advance);
            }
        }
    }

    private static final Map<Identifier, GlyphAtlas> CACHE = new java.util.concurrent.ConcurrentHashMap<>();

    public static GlyphAtlas load(Identifier dataId, Identifier textureId) {
        return CACHE.computeIfAbsent(dataId, k -> {
            try {
                Resource resource = MinecraftClient.getInstance().getResourceManager().getResourceOrThrow(dataId);
                try (InputStreamReader reader = new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8)) {
                    Data data = GSON.fromJson(reader, Data.class);
                    return new GlyphAtlas(textureId, data);
                }
            } catch (Exception exception) {
                throw new IllegalStateException("failed to load glyph atlas " + dataId, exception);
            }
        });
    }

    public Identifier texture() {
        return texture;
    }

    public int atlasWidth() {
        return Math.round(atlasWidth);
    }

    public int atlasHeight() {
        return Math.round(atlasHeight);
    }

    public float ascender() {
        return ascender;
    }

    public float descender() {
        return descender;
    }

    public float lineHeight() {
        return lineHeight;
    }

    public float baselineHeight() {
        return baselineHeight;
    }

    public Glyph glyph(int codepoint) {
        Glyph glyph = glyphs.get(codepoint);
        if (glyph != null) {
            return glyph;
        }
        int fallback = resolveFallback(codepoint);
        return fallback != 0 ? glyphs.get(fallback) : null;
    }

    public boolean canRender(String text) {
        if (text == null || text.isEmpty()) return true;
        for (int offset = 0; offset < text.length(); ) {
            int codepoint = text.codePointAt(offset);
            if (codepoint > 32 && glyph(codepoint) == null) {
                return false;
            }
            offset += Character.charCount(codepoint);
        }
        return true;
    }

    private static int resolveFallback(int codepoint) {
        return switch (codepoint) {
            case 0x0406 -> (int) 'I';
            case 0x0456 -> (int) 'i';
            case 0x0407 -> (int) 'I';
            case 0x0457 -> (int) 'i';
            case 0x0404 -> 0x042D;
            case 0x0454 -> 0x044D;
            case 0x0490 -> 0x0413;
            case 0x0491 -> 0x0433;
            default -> 0;
        };
    }

    public float kerning(int left, int right) {
        if (left < 0) {
            return 0.0f;
        }
        IntFloatMap inner = kerning.get(left);
        return inner == null ? 0.0f : inner.get(right);
    }

    public float width(String text, float size) {
        RenderSystem.assertOnRenderThread();
        Float cached = widthCache.get(text, size);
        if (cached != null) {
            return cached;
        }
        float width = 0.0f;
        int previous = -1;
        for (int offset = 0; offset < text.length(); ) {
            int codepoint = text.codePointAt(offset);
            Glyph glyph = glyph(codepoint);
            if (glyph != null) {
                width += kerning(previous, codepoint) * size;
                // Donor MsdfFont.computeWidth applies a 1.05 factor; wrapping and
                // button widths depend on this exact metric.
                width += glyph.advance * size * 1.05f;
                previous = codepoint;
            }
            offset += Character.charCount(codepoint);
        }
        widthCache.put(text, size, width);
        return width;
    }

    /**
     * Bounded LRU cache for measured string widths (donor's width-cache win).
     * The atlas is immutable after load, so entries never go stale.
     * Render thread only, like the rest of this class.
     */
    static final class WidthCache {
        private final LinkedHashMap<WidthKey, Float> map;

        WidthCache(int capacity) {
            map = new LinkedHashMap<>(capacity, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<WidthKey, Float> eldest) {
                    return size() > capacity;
                }
            };
        }

        Float get(String text, float size) {
            return map.get(new WidthKey(text, size));
        }

        void put(String text, float size, float width) {
            map.put(new WidthKey(text, size), width);
        }

        int size() {
            return map.size();
        }

        private record WidthKey(String text, int sizeBits) {
            WidthKey(String text, float size) {
                this(text, Float.floatToIntBits(size));
            }
        }
    }

    public static final class Glyph {
        public final float advance;
        public final float planeLeft;
        public final float planeRight;
        public final float planeTop;
        public final float planeBottom;
        public final boolean visible;
        public final float u;
        public final float v;
        public final int regionWidth;
        public final int regionHeight;
        public final float aspect;

        private Glyph(GlyphData source, float atlasWidth, float atlasHeight) {
            this.advance = source.advance;
            if (source.planeBounds == null || source.atlasBounds == null) {
                this.visible = false;
                this.planeLeft = this.planeRight = this.planeTop = this.planeBottom = 0.0f;
                this.u = this.v = 0.0f;
                this.regionWidth = this.regionHeight = 0;
                this.aspect = 1.0f;
                return;
            }
            this.visible = true;
            this.planeLeft = source.planeBounds.left;
            this.planeRight = source.planeBounds.right;
            this.planeTop = source.planeBounds.top;
            this.planeBottom = source.planeBounds.bottom;
            this.u = source.atlasBounds.left;
            this.v = atlasHeight - source.atlasBounds.top;
            this.regionWidth = Math.round(source.atlasBounds.right - source.atlasBounds.left);
            this.regionHeight = Math.round(source.atlasBounds.top - source.atlasBounds.bottom);
            float planeWidth = planeRight - planeLeft;
            float planeHeight = planeTop - planeBottom;
            this.aspect = planeHeight <= 0.0f ? 1.0f : planeWidth / planeHeight;
        }
    }

    private static final class Data {
        AtlasInfo atlas;
        MetricsInfo metrics;
        GlyphData[] glyphs;
        KerningData[] kerning;
    }

    private static final class AtlasInfo {
        float width;
        float height;
    }

    private static final class MetricsInfo {
        float ascender;
        float descender;
        float lineHeight;
    }

    private static final class GlyphData {
        int unicode;
        float advance;
        Bounds planeBounds;
        Bounds atlasBounds;
    }

    private static final class KerningData {
        int unicode1;
        int unicode2;
        float advance;
    }

    private static final class Bounds {
        float left;
        float bottom;
        float right;
        float top;
    }
}
