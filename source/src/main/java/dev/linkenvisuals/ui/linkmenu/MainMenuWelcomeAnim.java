package dev.linkenvisuals.ui.linkmenu;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.linkenvisuals.render.GlowPipeline;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.util.Identifier;

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
 * 1:1 Vector handwriting animator for the "welcome" cursive script from {@code welcome.json}.
 * Traces the 32 Bezier anchor points with round joins and round caps, rendering smooth
 * antialiased frames into a dynamic GPU texture without requiring native Skija C++ runtime.
 */
public final class MainMenuWelcomeAnim {
    public static final Identifier TEXTURE_ID = Identifier.of("linkenvisuals", "textures/gui/welcome_cursive.png");

    private static final int CANVAS_W = 432;
    private static final int CANVAS_H = 128;
    private static final float STROKE_WIDTH = 9.0f;

    // 32 cubic Bezier points: { vx, vy, ox, oy, ix, iy }
    private static final float[][] NODES = {
        { 22.51f, 14.71f, 0f, 0f, 0f, 0f }, // 0
        { 36.08f, 100.98f, 14.28f, -2.72f, -20.00f, 3.81f }, // 1
        { 60.10f, 44.76f, 0f, 0f, 0f, 0f }, // 2
        { 71.55f, 101.03f, 15.08f, -2.46f, -18.90f, 3.08f }, // 3
        { 95.36f, 22.29f, -12.07f, 0.79f, 19.74f, -1.29f }, // 4
        { 108.22f, 88.41f, 15.25f, 5.08f, -24.26f, -8.08f }, // 5
        { 139.90f, 71.48f, 0.88f, -13.54f, -0.77f, 11.74f }, // 6
        { 116.75f, 72.51f, -5.20f, 15.50f, 5.66f, -16.85f }, // 7
        { 140.23f, 101.12f, 28.1f, -8.20f, -19.68f, 5.74f }, // 8
        { 185.71f, 34.46f, 1.53f, -16.08f, -1.45f, 15.25f }, // 9
        { 168.35f, 31.14f, -6.68f, 13.36f, 7.15f, -14.30f }, // 10
        { 174.73f, 102.12f, 13.26f, 0f, -24.05f, 0f }, // 11
        { 208.81f, 65.62f, 9.92f, -5.82f, -13.97f, 8.20f }, // 12
        { 225.51f, 61.42f, 0f, 0f, 0f, 0f }, // 13
        { 198.19f, 81.05f, -2.16f, 8.90f, 4.45f, -18.34f }, // 14
        { 225.99f, 99.12f, 19.36f, -10.15f, -20.83f, 10.92f }, // 15
        { 262.67f, 62.75f, 9.78f, -1.25f, -24.10f, 3.08f }, // 16
        { 278.30f, 81.82f, -0.89f, 11.62f, 0.65f, -8.40f }, // 17
        { 255.58f, 101.74f, -9.91f, -1.40f, 10.85f, 1.53f }, // 18
        { 243.20f, 77.87f, 6.10f, -13.24f, -3.88f, 8.42f }, // 19
        { 262.67f, 62.75f, 15.14f, -1.24f, -6.22f, 0.51f }, // 20
        { 290.09f, 81.07f, 7.88f, -0.96f, -8.06f, 0.98f }, // 21
        { 303.38f, 69.21f, 0f, 0f, 0f, 0f }, // 22
        { 297.63f, 100.83f, 0f, 0f, 0f, 0f }, // 23
        { 318.63f, 65.52f, 10.73f, 1f, -9.45f, -0.88f }, // 24
        { 323.32f, 92.80f, 0f, 0f, 0f, 0f }, // 25
        { 344.04f, 66.08f, 10.16f, 2.13f, -10.50f, -2.2f }, // 26
        { 349.43f, 100.70f, 12.42f, 6.21f, -11.05f, -5.52f }, // 27
        { 390.32f, 72.00f, 0.88f, -13.54f, -0.77f, 11.74f }, // 28
        { 367.17f, 73.02f, -4.55f, 13.56f, 5.66f, -16.85f }, // 29
        { 386.55f, 102.28f, 9.88f, 0.02f, -15.60f, -0.04f }, // 30
        { 405.84f, 95.19f, 0f, 0f, -2.02f, 1.58f }  // 31
    };

    private static final List<float[]> POLYLINE = new ArrayList<>();
    private static final List<Float> CUMULATIVE_LENGTHS = new ArrayList<>();
    private static float TOTAL_PATH_LENGTH;

    static {
        buildPolyline();
    }

    private static void buildPolyline() {
        POLYLINE.clear();
        CUMULATIVE_LENGTHS.clear();
        float total = 0.0f;
        float prevX = NODES[0][0];
        float prevY = NODES[0][1];
        POLYLINE.add(new float[]{ prevX, prevY });
        CUMULATIVE_LENGTHS.add(0.0f);

        int samplesPerSegment = 24;
        for (int i = 0; i < NODES.length - 1; i++) {
            float p0x = NODES[i][0];
            float p0y = NODES[i][1];
            float p1x = p0x + NODES[i][2]; // + ox
            float p1y = p0y + NODES[i][3]; // + oy
            float p3x = NODES[i + 1][0];
            float p3y = NODES[i + 1][1];
            float p2x = p3x + NODES[i + 1][4]; // + ix
            float p2y = p3y + NODES[i + 1][5]; // + iy

            for (int s = 1; s <= samplesPerSegment; s++) {
                float t = (float) s / (float) samplesPerSegment;
                float mt = 1.0f - t;
                float x = mt * mt * mt * p0x + 3.0f * mt * mt * t * p1x + 3.0f * mt * t * t * p2x + t * t * t * p3x;
                float y = mt * mt * mt * p0y + 3.0f * mt * mt * t * p1y + 3.0f * mt * t * t * p2y + t * t * t * p3y;
                float dx = x - prevX;
                float dy = y - prevY;
                total += (float) Math.hypot(dx, dy);
                POLYLINE.add(new float[]{ x, y });
                CUMULATIVE_LENGTHS.add(total);
                prevX = x;
                prevY = y;
            }
        }
        TOTAL_PATH_LENGTH = total;
    }

    private static NativeImageBackedTexture texture;
    private static BufferedImage cpuBuffer;
    private static float lastRenderedProgress = -1.0f;

    public static void render(DrawContext context, float screenWidth, float screenHeight,
                              float progress, float alpha) {
        if (alpha <= 0.001f) {
            return;
        }

        progress = Math.max(0.0f, Math.min(1.0f, progress));
        ensureTexture(progress);

        float drawW = Math.min(screenWidth * 0.52f, 432.0f);
        float scale = drawW / 432.0f;
        float drawH = 128.0f * scale;

        float x = (screenWidth - drawW) * 0.5f;
        float y = (screenHeight - drawH) * 0.48f;

        // Soft ambient mint glow behind the handwriting
        int glowColor = LinkenMenuChrome.rgba(LinkenMenuChrome.ACCENT, Math.round(18.0f * alpha));
        GlowPipeline.radial(context, x + drawW * 0.5f, y + drawH * 0.5f, drawW * 0.45f, glowColor);

        int tint = LinkenMenuChrome.rgba(0xFFFFFF, alpha);
        context.drawTexture(RenderPipelines.GUI_TEXTURED, TEXTURE_ID,
            Math.round(x), Math.round(y), 0.0f, 0.0f,
            Math.round(drawW), Math.round(drawH),
            CANVAS_W, CANVAS_H, CANVAS_W, CANVAS_H, tint);
    }

    private static void ensureTexture(float progress) {
        if (texture == null) {
            NativeImage image = new NativeImage(CANVAS_W, CANVAS_H, false);
            texture = new NativeImageBackedTexture(() -> "welcome_cursive", image);
            MinecraftClient.getInstance().getTextureManager().registerTexture(TEXTURE_ID, texture);
            cpuBuffer = new BufferedImage(CANVAS_W, CANVAS_H, BufferedImage.TYPE_INT_ARGB);
            lastRenderedProgress = -1.0f;
        }

        // Re-rasterize if progress advanced significantly
        if (Math.abs(progress - lastRenderedProgress) < 0.004f && lastRenderedProgress >= 0.0f) {
            return;
        }
        lastRenderedProgress = progress;

        Graphics2D g2 = cpuBuffer.createGraphics();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        g2.setBackground(new Color(0, 0, 0, 0));
        g2.clearRect(0, 0, CANVAS_W, CANVAS_H);

        float targetLength = progress * TOTAL_PATH_LENGTH;
        Path2D.Float path = new Path2D.Float();
        if (!POLYLINE.isEmpty()) {
            path.moveTo(POLYLINE.get(0)[0], POLYLINE.get(0)[1]);
            for (int i = 1; i < POLYLINE.size(); i++) {
                float len = CUMULATIVE_LENGTHS.get(i);
                if (len <= targetLength) {
                    path.lineTo(POLYLINE.get(i)[0], POLYLINE.get(i)[1]);
                } else {
                    float prevLen = CUMULATIVE_LENGTHS.get(i - 1);
                    float segLen = len - prevLen;
                    if (segLen > 0.001f) {
                        float frac = (targetLength - prevLen) / segLen;
                        float[] prev = POLYLINE.get(i - 1);
                        float[] curr = POLYLINE.get(i);
                        path.lineTo(prev[0] + (curr[0] - prev[0]) * frac, prev[1] + (curr[1] - prev[1]) * frac);
                    }
                    break;
                }
            }
        }

        g2.setStroke(new BasicStroke(STROKE_WIDTH, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g2.setColor(Color.WHITE);
        g2.draw(path);
        g2.dispose();

        // Upload RGB pixels into NativeImage
        NativeImage nativeImage = texture.getImage();
        if (nativeImage != null) {
            int[] raw = ((DataBufferInt) cpuBuffer.getRaster().getDataBuffer()).getData();
            for (int cy = 0; cy < CANVAS_H; cy++) {
                int rowOffset = cy * CANVAS_W;
                for (int cx = 0; cx < CANVAS_W; cx++) {
                    int argb = raw[rowOffset + cx];
                    int a = (argb >>> 24) & 0xFF;
                    int r = (argb >>> 16) & 0xFF;
                    int g = (argb >>> 8) & 0xFF;
                    int b = argb & 0xFF;
                    // NativeImage stores in ABGR
                    int abgr = (a << 24) | (b << 16) | (g << 8) | r;
                    nativeImage.setColor(cx, cy, abgr);
                }
            }
            texture.upload();
        }
    }

    public static void dispose() {
        if (texture != null) {
            texture.close();
            texture = null;
        }
        cpuBuffer = null;
        lastRenderedProgress = -1.0f;
    }
}
