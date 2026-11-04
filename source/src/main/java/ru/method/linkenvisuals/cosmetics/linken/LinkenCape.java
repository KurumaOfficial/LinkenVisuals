package ru.method.linkenvisuals.cosmetics.linken;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.resource.Resource;
import net.minecraft.util.Identifier;
import ru.method.linkenvisuals.LinkenVisuals;

import java.io.InputStream;
import java.util.Optional;

/**
 * Фирменный анимированный плащ Linken.
 *
 * <p>Дизайн (тёмная ткань, неон-рамка, «linken.», «@LinkenVisuals», «Best Visual») запечён в
 * {@code linken_signature.png}. Поверх него каждый кадр (30 Гц) в две грани плаща дорисовывается
 * зелёный дождь и лёгкий пульс — так плащ остаётся живым и на 1.21.11, где старый FBO-пайплайн
 * 1.21.4 больше не доступен. Готовый кадр отдаётся ванильному рендереру плаща как обычная
 * динамическая текстура.</p>
 */
public final class LinkenCape {

    public static final String ID = "linken_signature";
    /** Dynamic texture that the vanilla cape renderer samples. */
    public static final Identifier CAPE_TEXTURE = Identifier.of("linkenvisuals", "linken_signature_cape");
    /** Static source design (also used for the first frame and as a fallback). */
    public static final Identifier STATIC_TEXTURE =
            Identifier.of("linkenvisuals", "textures/cosmetics/linken_signature.png");
    public static final Identifier ICON_TEXTURE =
            Identifier.of("linkenvisuals", "textures/cosmetics/linken_signature_icon.png");

    // Cape sheet: 64×32 ×SHEET. Only the two 10×16 faces are animated.
    private static final int SHEET = 16;
    private static final int TEX_W = 64 * SHEET;
    private static final int TEX_H = 32 * SHEET;
    private static final int FACE_X = 1 * SHEET;
    private static final int FACE2_X = 12 * SHEET;
    private static final int FACE_Y = 1 * SHEET;
    private static final int FACE_W = 10 * SHEET;
    private static final int FACE_H = 16 * SHEET;

    /** Same 30 Hz cap as the 1.21.4 FBO version: rain is invisible at higher rates. */
    private static final long FRAME_INTERVAL_NS = 1_000_000_000L / 30L;
    private static final int DROPS_PER_FACE = 30;

    private static final int[] BASE_FACE_A = new int[FACE_W * FACE_H];
    private static final int[] BASE_FACE_B = new int[FACE_W * FACE_H];

    private static NativeImage workImage;
    private static NativeImageBackedTexture dynamicTexture;
    private static boolean registered;

    private static volatile boolean usedThisFrame;
    private static boolean usedLastFrame;
    private static long lastFrameNs;
    private static final long START = System.nanoTime();

    private LinkenCape() {
    }

    /** Identifier for {@code CosmeticsService.resolveCapeTexture}. Marks the cape as used this frame. */
    public static Identifier texture() {
        usedThisFrame = true;
        return registered ? CAPE_TEXTURE : STATIC_TEXTURE;
    }

    /** Wake the animation for the cosmetics-screen icon even when the cape is not equipped. */
    public static void markUsed() {
        usedThisFrame = true;
    }

    public static Identifier iconTexture() {
        return ICON_TEXTURE;
    }

    public static boolean iconReady() {
        return true;
    }

    /** Called once per frame from GameRenderer.renderWorld HEAD. */
    public static void renderFrame() {
        boolean render = usedLastFrame;
        usedLastFrame = usedThisFrame;
        usedThisFrame = false;

        if (!registered) {
            try {
                ensure();
            } catch (Throwable t) {
                LinkenVisuals.LOGGER.warn("[LinkenCape] init failed: {}", t.toString());
                return;
            }
        }
        if (!render || !registered) {
            return;
        }

        long now = System.nanoTime();
        if (now - lastFrameNs < FRAME_INTERVAL_NS) {
            return;
        }
        lastFrameNs = now;
        try {
            update((now - START) / 1_000_000_000f);
        } catch (Throwable t) {
            LinkenVisuals.LOGGER.warn("[LinkenCape] frame failed: {}", t.toString());
        }
    }

    private static void ensure() throws Exception {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null) {
            return;
        }
        Optional<Resource> resource = mc.getResourceManager().getResource(STATIC_TEXTURE);
        if (resource.isEmpty()) {
            throw new IllegalStateException("missing " + STATIC_TEXTURE);
        }

        NativeImage base;
        try (InputStream in = resource.get().getInputStream()) {
            base = NativeImage.read(in);
        }

        cacheFace(base, FACE_X, BASE_FACE_A);
        cacheFace(base, FACE2_X, BASE_FACE_B);
        base.close();

        workImage = new NativeImage(TEX_W, TEX_H, true);
        restoreFace(FACE_X, BASE_FACE_A);
        restoreFace(FACE2_X, BASE_FACE_B);

        dynamicTexture = new NativeImageBackedTexture(() -> "linken_signature_cape", workImage);
        mc.getTextureManager().registerTexture(CAPE_TEXTURE, dynamicTexture);
        dynamicTexture.upload();
        registered = true;
        LinkenVisuals.LOGGER.info("[LinkenCape] animated cape ready ({}x{})", TEX_W, TEX_H);
    }

    private static void cacheFace(NativeImage base, int fx, int[] out) {
        for (int y = 0; y < FACE_H; y++) {
            int row = y * FACE_W;
            for (int x = 0; x < FACE_W; x++) {
                out[row + x] = base.getColorArgb(fx + x, FACE_Y + y);
            }
        }
    }

    private static void restoreFace(int fx, int[] src) {
        for (int y = 0; y < FACE_H; y++) {
            int row = y * FACE_W;
            for (int x = 0; x < FACE_W; x++) {
                workImage.setColorArgb(fx + x, FACE_Y + y, src[row + x]);
            }
        }
    }

    private static void update(float t) {
        if (workImage == null || dynamicTexture == null) {
            return;
        }
        restoreFace(FACE_X, BASE_FACE_A);
        restoreFace(FACE2_X, BASE_FACE_B);

        drawRain(FACE_X, t, 0);
        drawRain(FACE2_X, t, 1);
        drawPulse(FACE_X, t, 0);
        drawPulse(FACE2_X, t, 1);

        dynamicTexture.upload();
    }

    /**
     * Vertical rain streaks. Each drop has a stable x/phase/speed derived from its index, so the
     * animation is deterministic across frames without allocations.
     */
    private static void drawRain(int fx, float t, int faceIndex) {
        for (int i = 0; i < DROPS_PER_FACE; i++) {
            int seed = i * 131 + faceIndex * 17;
            float x = 7f + hash(seed) * (FACE_W - 14);
            float speed = 0.28f + hash(seed + 1) * 0.55f;
            float phase = hash(seed + 2);
            float len = 10f + hash(seed + 3) * 34f;
            float drift = (hash(seed + 4) - 0.5f) * 7f;
            int alpha = 22 + (int) (hash(seed + 5) * 54f);

            float travel = FACE_H + len;
            float y = frac(t * speed + phase) * travel - len;

            for (int j = 0; j < (int) len; j++) {
                float k = j / len;
                float px = x + drift * k;
                int py = (int) (y + j);
                if (py < 0 || py >= FACE_H) {
                    continue;
                }
                int ix = (int) px;
                if (ix < 0 || ix >= FACE_W) {
                    continue;
                }
                int fade = (int) (alpha * Math.sin(Math.PI * k));
                if (fade <= 1) {
                    continue;
                }
                blendAccent(fx + ix, FACE_Y + py, fade);
            }
        }
    }

    /** Soft breathing glow rising from the fabric, like the 1.21.4 accent blobs. */
    private static void drawPulse(int fx, float t, int faceIndex) {
        float pulse = 0.5f - 0.5f * (float) Math.cos((t + faceIndex * 0.8f) * 1.35f);
        if (pulse <= 0.02f) {
            return;
        }
        int alpha = (int) (16f * pulse);
        if (alpha <= 0) {
            return;
        }
        int cx = fx + FACE_W / 2;
        int cy = FACE_Y + (int) (FACE_H * 0.42f);
        int radius = (int) (FACE_W * 0.42f);
        int radius2 = radius * radius;
        for (int y = -radius; y <= radius; y += 2) {
            int py = cy + y;
            if (py < FACE_Y || py >= FACE_Y + FACE_H) {
                continue;
            }
            for (int x = -radius; x <= radius; x += 2) {
                int px = cx + x;
                if (px < fx || px >= fx + FACE_W) {
                    continue;
                }
                int d2 = x * x + y * y;
                if (d2 > radius2) {
                    continue;
                }
                float falloff = 1f - (float) Math.sqrt(d2) / radius;
                int a = (int) (alpha * falloff);
                if (a > 1) {
                    blendAccent(px, py, a);
                }
            }
        }
    }

    private static void blendAccent(int x, int y, int alpha) {
        int p = workImage.getColorArgb(x, y);
        int r = (p >>> 16) & 0xFF;
        int g = (p >>> 8) & 0xFF;
        int b = p & 0xFF;

        // Accent #9ef0c7, added with alpha so the dark fabric keeps its texture.
        int nr = Math.min(255, r + (158 * alpha) / 255);
        int ng = Math.min(255, g + (240 * alpha) / 255);
        int nb = Math.min(255, b + (199 * alpha) / 255);
        workImage.setColorArgb(x, y, 0xFF000000 | (nr << 16) | (ng << 8) | nb);
    }

    private static float frac(float v) {
        return v - (float) Math.floor(v);
    }

    private static float hash(int n) {
        int x = n * 0x9E3779B9;
        x ^= x >>> 16;
        x *= 0x85EBCA6B;
        x ^= x >>> 13;
        x *= 0xC2B2AE35;
        x ^= x >>> 16;
        return (x >>> 8) / (float) (1 << 24);
    }
}
