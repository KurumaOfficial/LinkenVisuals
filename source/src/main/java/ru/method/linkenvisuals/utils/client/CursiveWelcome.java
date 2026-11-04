package ru.method.linkenvisuals.utils.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.client.texture.TextureManager;
import net.minecraft.util.Identifier;
import ru.method.linkenvisuals.LinkenVisuals;
import ru.method.linkenvisuals.utils.render.Render2D;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.util.ArrayList;
import java.util.List;

/**
 * Cursive "welcome" welcome animation from {@code mainmenu/welcome.json}.
 *
 * <p>The shipped Lottie file contains a valid 32-node Bezier path and trim-path
 * timing, but every gradient stop is {@code rgba(0,0,0,0)} — the asset is fully
 * transparent. This class rasterizes the same path, stroke width and trim timing
 * with the white ink used by the original 1.21.4 menu so the intro is visible.</p>
 */
final class CursiveWelcome {
    static final float DURATION = 493.0f / 60.0f;

    private static final float TRIM_END_FROM = 12.0f / 60.0f;
    private static final float TRIM_END_TO = 196.0f / 60.0f;
    private static final float TRIM_START_FROM = 292.0f / 60.0f;
    private static final float TRIM_START_TO = 470.0f / 60.0f;

    private static final float BASE_W = 428.0f;
    private static final float BASE_H = 123.0f;
    private static final float BASE_STROKE = 9.0f;
    // welcome.json layer transform: position (210.962, 63.445) - anchor (217.377, 69.099).
    private static final float LAYER_OFFSET_X = 210.962f - 217.377f;
    private static final float LAYER_OFFSET_Y = 63.445f - 69.099f;
    private static final int TEX_SCALE = 2;
    private static final int TEX_W = (int) BASE_W * TEX_SCALE;
    private static final int TEX_H = (int) BASE_H * TEX_SCALE;
    private static final Identifier TEXTURE_ID = LinkenVisuals.id("mainmenu/welcome_cursive");

    // 32 cubic Bezier nodes from welcome.json: { vx, vy, ox, oy, ix, iy }.
    private static final float[][] NODES = {
            { 22.51f, 14.71f, 0f, 0f, 0f, 0f },
            { 36.08f, 100.98f, 14.28f, -2.72f, -20.00f, 3.81f },
            { 60.10f, 44.76f, 0f, 0f, 0f, 0f },
            { 71.55f, 101.03f, 15.08f, -2.46f, -18.90f, 3.08f },
            { 95.36f, 22.29f, -12.07f, 0.79f, 19.74f, -1.29f },
            { 108.22f, 88.41f, 15.25f, 5.08f, -24.26f, -8.08f },
            { 139.90f, 71.48f, 0.88f, -13.54f, -0.77f, 11.74f },
            { 116.75f, 72.51f, -5.20f, 15.50f, 5.66f, -16.85f },
            { 140.23f, 101.12f, 28.10f, -8.20f, -19.68f, 5.74f },
            { 185.71f, 34.46f, 1.53f, -16.08f, -1.45f, 15.25f },
            { 168.35f, 31.14f, -6.68f, 13.36f, 7.15f, -14.30f },
            { 174.73f, 102.12f, 13.26f, 0f, -24.05f, 0f },
            { 208.81f, 65.62f, 9.92f, -5.82f, -13.97f, 8.20f },
            { 225.51f, 61.42f, 0f, 0f, 0f, 0f },
            { 198.19f, 81.05f, -2.16f, 8.90f, 4.45f, -18.34f },
            { 225.99f, 99.12f, 19.36f, -10.15f, -20.83f, 10.92f },
            { 262.67f, 62.75f, 9.78f, -1.25f, -24.10f, 3.08f },
            { 278.30f, 81.82f, -0.89f, 11.62f, 0.65f, -8.40f },
            { 255.58f, 101.74f, -9.91f, -1.40f, 10.85f, 1.53f },
            { 243.20f, 77.87f, 6.10f, -13.24f, -3.88f, 8.42f },
            { 262.67f, 62.75f, 15.14f, -1.24f, -6.22f, 0.51f },
            { 290.09f, 81.07f, 7.88f, -0.96f, -8.06f, 0.98f },
            { 303.38f, 69.21f, 0f, 0f, 0f, 0f },
            { 297.63f, 100.83f, 0f, 0f, 0f, 0f },
            { 318.63f, 65.52f, 10.73f, 1.00f, -9.45f, -0.88f },
            { 323.32f, 92.80f, 0f, 0f, 0f, 0f },
            { 344.04f, 66.08f, 10.16f, 2.13f, -10.50f, -2.20f },
            { 349.43f, 100.70f, 12.42f, 6.21f, -11.05f, -5.52f },
            { 390.32f, 72.00f, 0.88f, -13.54f, -0.77f, 11.74f },
            { 367.17f, 73.02f, -4.55f, 13.56f, 5.66f, -16.85f },
            { 386.55f, 102.28f, 9.88f, 0.02f, -15.60f, -0.04f },
            { 405.84f, 95.19f, 0f, 0f, -2.02f, 1.58f }
    };

    private static final List<float[]> POLYLINE = new ArrayList<>();
    private static final List<Float> CUMULATIVE = new ArrayList<>();
    private static final float TOTAL_LENGTH;

    static {
        float total = 0f;
        float prevX = NODES[0][0] + LAYER_OFFSET_X;
        float prevY = NODES[0][1] + LAYER_OFFSET_Y;
        POLYLINE.add(new float[]{ prevX, prevY });
        CUMULATIVE.add(0f);

        int samples = 48;
        for (int i = 0; i < NODES.length - 1; i++) {
            float p0x = NODES[i][0] + LAYER_OFFSET_X;
            float p0y = NODES[i][1] + LAYER_OFFSET_Y;
            float p1x = p0x + NODES[i][2];
            float p1y = p0y + NODES[i][3];
            float p3x = NODES[i + 1][0] + LAYER_OFFSET_X;
            float p3y = NODES[i + 1][1] + LAYER_OFFSET_Y;
            float p2x = p3x + NODES[i + 1][4];
            float p2y = p3y + NODES[i + 1][5];

            for (int s = 1; s <= samples; s++) {
                float t = (float) s / samples;
                float mt = 1f - t;
                float x = mt * mt * mt * p0x + 3f * mt * mt * t * p1x
                        + 3f * mt * t * t * p2x + t * t * t * p3x;
                float y = mt * mt * mt * p0y + 3f * mt * mt * t * p1y
                        + 3f * mt * t * t * p2y + t * t * t * p3y;
                total += (float) Math.hypot(x - prevX, y - prevY);
                POLYLINE.add(new float[]{ x, y });
                CUMULATIVE.add(total);
                prevX = x;
                prevY = y;
            }
        }
        TOTAL_LENGTH = total;
    }

    private static NativeImageBackedTexture texture;
    private static BufferedImage buffer;
    private static float lastStart = -1f;
    private static float lastEnd = -1f;

    private CursiveWelcome() {
    }

    static void render(DrawContext context, float screenWidth, float screenHeight, float progress) {
        if (context == null || screenWidth <= 0f || screenHeight <= 0f) return;
        progress = Math.max(0f, Math.min(1f, progress));

        float time = progress * DURATION;
        float start = trimProgress(time, TRIM_START_FROM, TRIM_START_TO, 0.414f, 0.0f, 0.667f, 1.0f);
        float end = trimProgress(time, TRIM_END_FROM, TRIM_END_TO, 0.409f, 0.273f, 0.588f, 1.0f);

        float visibleStart = start * TOTAL_LENGTH;
        float visibleEnd = end * TOTAL_LENGTH;
        if (visibleEnd - visibleStart < 0.05f) return;

        ensureTexture(visibleStart, visibleEnd);

        float drawW = Math.min(screenWidth * 0.62f, BASE_W * 2f);
        float drawH = drawW * (BASE_H / BASE_W);
        float x = (screenWidth - drawW) * 0.5f;
        float y = (screenHeight - drawH) * 0.5f;

        Render2D.drawTexture(context.getMatrices(), x, y, drawW, drawH, 0f, TEXTURE_ID, Color.WHITE);
    }

    static void dispose() {
        if (texture != null) {
            texture.close();
            texture = null;
        }
        buffer = null;
        lastStart = -1f;
        lastEnd = -1f;
    }

    private static void ensureTexture(float start, float end) {
        if (texture == null) {
            NativeImage image = new NativeImage(NativeImage.Format.RGBA, TEX_W, TEX_H, false);
            texture = new NativeImageBackedTexture(() -> "linken_welcome_cursive", image);
            MinecraftClient client = MinecraftClient.getInstance();
            TextureManager manager = client != null ? client.getTextureManager() : null;
            if (manager != null) {
                manager.registerTexture(TEXTURE_ID, texture);
            }
            buffer = new BufferedImage(TEX_W, TEX_H, BufferedImage.TYPE_INT_ARGB);
            lastStart = -1f;
            lastEnd = -1f;
        }
        if (Math.abs(start - lastStart) < 0.01f && Math.abs(end - lastEnd) < 0.01f) return;
        lastStart = start;
        lastEnd = end;

        Graphics2D g = buffer.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        g.setBackground(new Color(0, 0, 0, 0));
        g.clearRect(0, 0, TEX_W, TEX_H);

        Path2D.Float path = segment(start, end);
        if (path != null) {
            g.setStroke(new BasicStroke(BASE_STROKE * TEX_SCALE, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.setColor(Color.WHITE);
            g.draw(path);
        }
        g.dispose();

        NativeImage image = texture.getImage();
        if (image == null) return;
        int[] raw = ((DataBufferInt) buffer.getRaster().getDataBuffer()).getData();
        for (int y = 0; y < TEX_H; y++) {
            int row = y * TEX_W;
            for (int x = 0; x < TEX_W; x++) {
                int argb = raw[row + x];
                int a = (argb >>> 24) & 0xFF;
                if (a == 0) {
                    image.setColor(x, y, 0);
                    continue;
                }
                int r = (argb >>> 16) & 0xFF;
                int gc = (argb >>> 8) & 0xFF;
                int b = argb & 0xFF;
                // NativeImage stores colours as ABGR.
                image.setColor(x, y, (a << 24) | (b << 16) | (gc << 8) | r);
            }
        }
        texture.upload();
    }

    private static Path2D.Float segment(float start, float end) {
        if (end - start < 0.01f) return null;
        Path2D.Float path = new Path2D.Float();
        float[] first = pointAt(start);
        path.moveTo(first[0] * TEX_SCALE, first[1] * TEX_SCALE);

        int index = lowerBound(start);
        while (index < POLYLINE.size() && CUMULATIVE.get(index) <= end) {
            float[] point = POLYLINE.get(index);
            path.lineTo(point[0] * TEX_SCALE, point[1] * TEX_SCALE);
            index++;
        }
        float[] last = pointAt(end);
        path.lineTo(last[0] * TEX_SCALE, last[1] * TEX_SCALE);
        return path;
    }

    private static float[] pointAt(float length) {
        int index = lowerBound(length);
        if (index <= 0) return POLYLINE.get(0);
        if (index >= POLYLINE.size()) return POLYLINE.get(POLYLINE.size() - 1);
        float previousLength = CUMULATIVE.get(index - 1);
        float segmentLength = CUMULATIVE.get(index) - previousLength;
        float fraction = segmentLength <= 0.0001f ? 0f : (length - previousLength) / segmentLength;
        float[] previous = POLYLINE.get(index - 1);
        float[] next = POLYLINE.get(index);
        return new float[]{
                previous[0] + (next[0] - previous[0]) * fraction,
                previous[1] + (next[1] - previous[1]) * fraction
        };
    }

    private static int lowerBound(float length) {
        int low = 0;
        int high = CUMULATIVE.size();
        while (low < high) {
            int mid = (low + high) >>> 1;
            if (CUMULATIVE.get(mid) < length) low = mid + 1;
            else high = mid;
        }
        return low;
    }

    /**
     * Evaluates one Lottie keyframe pair with its cubic-bezier easing.
     */
    private static float trimProgress(float time, float from, float to,
                                      float x1, float y1, float x2, float y2) {
        if (time <= from) return 0f;
        if (time >= to) return 1f;
        float target = (time - from) / (to - from);
        float low = 0f;
        float high = 1f;
        for (int i = 0; i < 24; i++) {
            float mid = (low + high) * 0.5f;
            if (bezier(mid, x1, x2) < target) low = mid;
            else high = mid;
        }
        return bezier((low + high) * 0.5f, y1, y2);
    }

    private static float bezier(float t, float p1, float p2) {
        float mt = 1f - t;
        return 3f * mt * mt * t * p1 + 3f * mt * t * t * p2 + t * t * t;
    }
}
