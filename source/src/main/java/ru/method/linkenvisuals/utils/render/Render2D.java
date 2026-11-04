package ru.method.linkenvisuals.utils.render;

import dev.linkenvisuals.render.CirclePipeline;
import dev.linkenvisuals.render.GlowPipeline;
import dev.linkenvisuals.render.MsdfText;
import dev.linkenvisuals.render.RoundedRectPipeline;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.texture.AbstractTexture;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import ru.method.linkenvisuals.utils.render.fonts.Instance;

import java.awt.Color;

public final class Render2D {
    public static final float DEFAULT_CORNER_RADIUS = 20.0f;
    private static DrawContext activeContext;

    private Render2D() {}

    public static void prepareHudFrame(DrawContext context) {
        activeContext = context;
    }

    public static void bindContext(DrawContext context) {
        activeContext = context;
    }

    public static void setContext(DrawContext context) {
        activeContext = context;
    }

    public static DrawContext getContext() {
        return activeContext;
    }

    private static DrawContext ctx(DrawContext context) {
        return context != null ? context : activeContext;
    }

    public static Color withClickGuiFade(Color color) {
        if (color == null) return null;
        float fade = ClickGuiRenderContext.getFadeMultiplier();
        float linkFade = LinkGuiRenderContext.getFadeMultiplier();
        if (linkFade < fade) fade = linkFade;
        // Quantise to 1/64 so animation does not defeat render caches with a new key every frame.
        fade = Math.round(fade * 64f) / 64f;
        if (fade >= 0.999f) return color;
        int a = Math.max(0, Math.min(255, Math.round(color.getAlpha() * fade)));
        return new Color(color.getRed(), color.getGreen(), color.getBlue(), a);
    }

    public static int toArgb(Color color) {
        if (color == null) return 0;
        return (color.getAlpha() << 24) | (color.getRed() << 16) | (color.getGreen() << 8) | color.getBlue();
    }

    /** Donor fallback for disabled glass: preserve RGB and force opaque alpha. */
    private static Color solidColor(Color color) {
        if (color == null) return null;
        return new Color(color.getRed(), color.getGreen(), color.getBlue(), 255);
    }

    public static void drawRoundedRect(Object stackOrCtx, float x, float y, float width, float height, float radius, Color color) {
        DrawContext c = stackOrCtx instanceof DrawContext dc ? dc : activeContext;
        if (c == null || width <= 0 || height <= 0 || color == null) return;
        Color col = withClickGuiFade(color);
        if (col == null || col.getAlpha() <= 0) return;
        if (Math.abs(width - height) <= 0.5f && width <= 6.0f && radius >= Math.min(width, height) * 0.45f) {
            // Small round bullets/dots must use the SDF circle, not the old
            // 4-vertex rhombus fallback (it looked like a solid pixel plus at
            // GUI scale 2 / HiDPI).
            CirclePipeline.draw(c, x + width * 0.5f, y + height * 0.5f, Math.min(width, height) * 0.5f, toArgb(col));
            return;
        }
        RoundedRectPipeline.draw(c, x, y, width, height, radius, toArgb(col));
    }

    public static void drawPlainRect(Object stackOrCtx, float x, float y, float width, float height, Color color) {
        DrawContext c = stackOrCtx instanceof DrawContext dc ? dc : activeContext;
        if (c == null || width <= 0 || height <= 0 || color == null) return;
        Color col = withClickGuiFade(color);
        if (col == null || col.getAlpha() <= 0) return;
        int x1 = Math.round(x);
        int y1 = Math.round(y);
        int x2 = Math.round(x + width);
        int y2 = Math.round(y + height);
        if (x2 <= x1) x2 = x1 + 1;
        if (y2 <= y1) y2 = y1 + 1;
        c.fill(x1, y1, x2, y2, toArgb(col));
    }

    /** Glass panel with the donor cobweb shader baked into the fill draw. */
    public static void drawPanelBackground(Object stackOrCtx, float x, float y, float width, float height,
                                           float radius, Color color) {
        DrawContext c = stackOrCtx instanceof DrawContext dc ? dc : activeContext;
        if (c == null || width <= 0 || height <= 0 || color == null) return;
        Color col = withClickGuiFade(color);
        if (col == null || col.getAlpha() <= 0) return;
        RoundedRectPipeline.drawPanelBackground(c, x, y, width, height, radius, toArgb(col));
    }

    public static void drawCircle(Object stackOrCtx, float centerX, float centerY, float radius, Color color) {
        DrawContext c = stackOrCtx instanceof DrawContext dc ? dc : activeContext;
        if (c == null || radius <= 0 || color == null) return;
        Color col = withClickGuiFade(color);
        if (col == null || col.getAlpha() <= 0) return;
        CirclePipeline.draw(c, centerX, centerY, radius, toArgb(col));
    }

    public static void drawCircle(Object stackOrCtx, float centerX, float centerY, float radius, int segments, Color color) {
        drawCircle(stackOrCtx, centerX, centerY, radius, color);
    }

    public static void drawRing(Object stackOrCtx, float centerX, float centerY, float radius, float thickness, Color color) {
        drawOutline(stackOrCtx, centerX - radius, centerY - radius, radius * 2f, radius * 2f, radius, thickness, color);
    }

    public static void drawOutline(Object stackOrCtx, float x, float y, float width, float height, float radius, float thickness, Color color) {
        DrawContext c = stackOrCtx instanceof DrawContext dc ? dc : activeContext;
        if (c == null || width <= 0 || height <= 0 || color == null) return;
        Color col = withClickGuiFade(color);
        if (col == null || col.getAlpha() <= 0) return;
        RoundedRectPipeline.drawOutline(c, x, y, width, height, radius, thickness, toArgb(col));
    }

    public static void drawOutlineGradient(Object stackOrCtx, float x, float y, float width, float height,
                                           float radius, float thickness,
                                           Color topLeft, Color bottomLeft, Color bottomRight, Color topRight) {
        DrawContext c = stackOrCtx instanceof DrawContext dc ? dc : activeContext;
        if (c == null || width <= 0 || height <= 0) return;
        Color tl = withClickGuiFade(topLeft);
        Color bl = withClickGuiFade(bottomLeft);
        Color br = withClickGuiFade(bottomRight);
        Color tr = withClickGuiFade(topRight);
        RoundedRectPipeline.drawOutlineGradient(c, x, y, width, height, radius, thickness,
                toArgb(tl), toArgb(bl), toArgb(br), toArgb(tr));
    }

    public static void drawBorder(Object stackOrCtx, float x, float y, float width, float height,
                                  float radius, float thickness, float internalSmoothness,
                                  float externalSmoothness, Color color) {
        DrawContext c = stackOrCtx instanceof DrawContext dc ? dc : activeContext;
        if (c == null || width <= 0 || height <= 0 || color == null) return;
        Color col = withClickGuiFade(color);
        if (col == null || col.getAlpha() <= 0) return;
        RoundedRectPipeline.drawBorder(c, x, y, width, height, radius, thickness, internalSmoothness, externalSmoothness, toArgb(col));
    }

    public static void drawBorder(Object stackOrCtx, float x, float y, float width, float height,
                                  float radius, float internalSmoothness, float externalSmoothness, Color color) {
        // Donor Builder.border() defaults thickness to 0: this overload is the
        // filled soft halo used by glow/shadow, not a 1px stroke.
        drawBorder(stackOrCtx, x, y, width, height, radius, 0.0f, internalSmoothness, externalSmoothness, color);
    }

    public static void drawBorder(Object stackOrCtx, float x, float y, float width, float height,
                                  float radius, float thickness, Color color) {
        drawBorder(stackOrCtx, x, y, width, height, radius, thickness, 0.5f, 1.0f, color);
    }

    public static void drawBorder(Object stackOrCtx, float x, float y, float width, float height,
                                  float radius, float thickness, ColorRGBA color) {
        drawBorder(stackOrCtx, x, y, width, height, radius, thickness, 0.5f, 1.0f, color != null ? color.toAwtColor() : null);
    }

    public static void drawHorizontalGradient(Object stackOrCtx, float x, float y, float width, float height, Color left, Color right) {
        drawRoundedHorizontalGradient(stackOrCtx, x, y, width, height, 0f, left, right);
    }

    public static void drawVerticalGradient(Object stackOrCtx, float x, float y, float width, float height, Color top, Color bottom) {
        drawRoundedVerticalGradient(stackOrCtx, x, y, width, height, 0f, top, bottom);
    }

    public static void drawRoundedHorizontalGradient(Object stackOrCtx, float x, float y, float width, float height, float radius, Color left, Color right) {
        DrawContext c = stackOrCtx instanceof DrawContext dc ? dc : activeContext;
        if (c == null || width <= 0 || height <= 0 || left == null || right == null) return;
        Color l = withClickGuiFade(left);
        Color r = withClickGuiFade(right);
        RoundedRectPipeline.drawHorizontalGradient(c, x, y, width, height, radius, toArgb(l), toArgb(r));
    }

    public static void drawRoundedVerticalGradient(Object stackOrCtx, float x, float y, float width, float height, float radius, Color top, Color bottom) {
        DrawContext c = stackOrCtx instanceof DrawContext dc ? dc : activeContext;
        if (c == null || width <= 0 || height <= 0 || top == null || bottom == null) return;
        Color t = withClickGuiFade(top);
        Color b = withClickGuiFade(bottom);
        RoundedRectPipeline.drawVerticalGradient(c, x, y, width, height, radius, toArgb(t), toArgb(b));
    }

    public static void drawRect(Object stackOrCtx, float x, float y, float width, float height, float radius,
                                ColorRGBA c1, ColorRGBA c2, ColorRGBA c3, ColorRGBA c4) {
        drawRoundedVerticalGradient(stackOrCtx, x, y, width, height, radius, c1.toAwtColor(), c2.toAwtColor());
    }

    public static void drawFont(Object stackOrCtx, Instance font, String text, float x, float y, Color color) {
        DrawContext c = stackOrCtx instanceof DrawContext dc ? dc : activeContext;
        if (c == null || font == null || text == null || text.isEmpty() || color == null) return;
        Color col = withClickGuiFade(color);
        if (col == null || col.getAlpha() <= 0) return;
        font.font().draw(c, text, x, y, font.size(), toArgb(col));
    }

    public static void drawCenteredFont(Object stackOrCtx, Instance font, String text, float centerX, float y, Color color) {
        if (font == null || text == null || text.isEmpty()) return;
        float w = font.getWidth(text);
        drawFont(stackOrCtx, font, text, centerX - w * 0.5f, y, color);
    }

    private static final java.util.Map<NativeImageBackedTexture, Identifier> DYNAMIC_TEXTURE_IDS =
            java.util.Collections.synchronizedMap(new java.util.IdentityHashMap<>());
    private static int dynamicTextureSeq;

    private static Identifier dynamicTextureId(NativeImageBackedTexture texture) {
        return DYNAMIC_TEXTURE_IDS.computeIfAbsent(texture, tex -> {
            Identifier id = ru.method.linkenvisuals.LinkenVisuals.id("dynamic/tex_" + (dynamicTextureSeq++));
            net.minecraft.client.MinecraftClient client = net.minecraft.client.MinecraftClient.getInstance();
            if (client != null) {
                client.getTextureManager().registerTexture(id, tex);
            }
            return id;
        });
    }

    public static void drawTexture(Object stackOrCtx, Identifier texture, float x, float y, float width, float height) {
        drawTexture(stackOrCtx, x, y, width, height, 0f, texture, Color.WHITE);
    }

    public static void drawTexture(Object stackOrCtx, Identifier texture, int x, int y, int width, int height) {
        drawTexture(stackOrCtx, (float) x, (float) y, (float) width, (float) height, 0f, texture, Color.WHITE);
    }

    public static void drawTexture(Object stackOrCtx, float x, float y, float width, float height, float radius,
                                   NativeImageBackedTexture texture, Color color) {
        DrawContext c = stackOrCtx instanceof DrawContext dc ? dc : activeContext;
        if (c == null || texture == null || width <= 0 || height <= 0) return;
        Identifier id = dynamicTextureId(texture);
        Color col = withClickGuiFade(color != null ? color : Color.WHITE);
        if (radius > 0f) {
            RoundedTexturePipeline.draw(c, id, x, y, width, height, radius, 0f, 0f, 1f, 1f, toArgb(col));
        } else {
            c.drawTexture(net.minecraft.client.gl.RenderPipelines.GUI_TEXTURED, id,
                    Math.round(x), Math.round(y), 0f, 0f, Math.round(width), Math.round(height),
                    Math.round(width), Math.round(height), toArgb(col));
        }
    }

    public static void drawTexture(Object stackOrCtx, float x, float y, float width, float height, float radius,
                                   Identifier texture, Color color) {
        DrawContext c = stackOrCtx instanceof DrawContext dc ? dc : activeContext;
        if (c == null || texture == null || width <= 0 || height <= 0) return;
        Color col = withClickGuiFade(color != null ? color : Color.WHITE);
        if (radius > 0f) {
            RoundedTexturePipeline.draw(c, texture, x, y, width, height, radius, 0f, 0f, 1f, 1f, toArgb(col));
        } else {
            c.drawTexture(net.minecraft.client.gl.RenderPipelines.GUI_TEXTURED, texture,
                    Math.round(x), Math.round(y), 0f, 0f, Math.round(width), Math.round(height),
                    Math.round(width), Math.round(height), toArgb(col));
        }
    }

    public static void drawTexture(Object stackOrCtx, float x, float y, float width, float height, float radius,
                                   AbstractTexture texture, Color color) {
        DrawContext c = stackOrCtx instanceof DrawContext dc ? dc : activeContext;
        if (c == null || texture == null || width <= 0 || height <= 0) return;
        Color col = withClickGuiFade(color != null ? color : Color.WHITE);
        RoundedTexturePipeline.draw(c, texture, x, y, width, height, radius, 0f, 0f, 1f, 1f, toArgb(col));
    }

    public static void drawTexture(Object stackOrCtx, float x, float y, float width, float height, float radius,
                                   float u1, float v1, float u2, float v2,
                                   Identifier texture, Color color) {
        DrawContext c = stackOrCtx instanceof DrawContext dc ? dc : activeContext;
        if (c == null || texture == null || width <= 0 || height <= 0) return;
        Color col = withClickGuiFade(color != null ? color : Color.WHITE);
        if (radius > 0f) {
            RoundedTexturePipeline.draw(c, texture, x, y, width, height, radius, u1, v1, u2, v2, toArgb(col));
        } else {
            c.drawTexture(net.minecraft.client.gl.RenderPipelines.GUI_TEXTURED, texture,
                    Math.round(x), Math.round(y), u1 * 64f, v1 * 64f, Math.round(width), Math.round(height),
                    Math.round((u2 - u1) * 64f), Math.round((v2 - v1) * 64f), 64, 64, toArgb(col));
        }
    }

    /**
     * Same as {@link #drawTexture} but keeps pixel-art textures crisp
     * (nearest filtering). Used for player heads/skins: an 8x8 skin face
     * scaled to 20-40 px through bilinear filtering looked blurred.
     */
    public static void drawTextureNearest(Object stackOrCtx, float x, float y, float width, float height, float radius,
                                          float u1, float v1, float u2, float v2,
                                          Identifier texture, Color color) {
        DrawContext c = stackOrCtx instanceof DrawContext dc ? dc : activeContext;
        if (c == null || texture == null || width <= 0 || height <= 0) return;
        Color col = withClickGuiFade(color != null ? color : Color.WHITE);
        RoundedTexturePipeline.drawNearest(c, texture, x, y, width, height, Math.max(0f, radius),
                u1, v1, u2, v2, toArgb(col));
    }

    public static void drawTextureNearest(Object stackOrCtx, float x, float y, float width, float height, float radius,
                                          Identifier texture, Color color) {
        drawTextureNearest(stackOrCtx, x, y, width, height, radius, 0f, 0f, 1f, 1f, texture, color);
    }

    /** Donor-style soft rounded halo (box-shadow / neon glow), one draw call. */
    public static void drawSoftGlow(Object stackOrCtx, float x, float y, float width, float height,
                                    float radius, float spread, Color color) {
        DrawContext c = stackOrCtx instanceof DrawContext dc ? dc : activeContext;
        if (c == null || width <= 0f || height <= 0f || spread <= 0f || color == null) return;
        Color col = withClickGuiFade(color);
        if (col == null || col.getAlpha() <= 0) return;
        RoundedGlowPipeline.draw(c, x, y, width, height, radius, spread, toArgb(col));
    }

    public static void drawGlow(Object stackOrCtx, float x, float y, float width, float height, float radius, float blur, Color color) {
        DrawContext c = stackOrCtx instanceof DrawContext dc ? dc : activeContext;
        if (c == null || width <= 0 || height <= 0 || color == null) return;
        Color col = withClickGuiFade(color);
        if (col == null || col.getAlpha() <= 0) return;
        GlowPipeline.radial(c, x + width * 0.5f, y + height * 0.5f, Math.max(width, height) * 0.5f + blur, toArgb(col));
    }

    public static void drawBlur(Object stackOrCtx, float x, float y, float width, float height, float radius, float blurRadius) {
        DrawContext c = stackOrCtx instanceof DrawContext dc ? dc : activeContext;
        if (c == null || width <= 0 || height <= 0) return;
        if (ColorUtils.isBlurEnabled()) {
            GlassRenderer.draw(c, x, y, width, height, radius, toArgb(new Color(255, 255, 255, 235)));
        } else {
            drawRoundedRect(c, x, y, width, height, radius, new Color(15, 20, 18, 120));
        }
    }

    public static void drawBlurredRect(Object stackOrCtx, float x, float y, float width, float height, float radius, float blurRadius, Color color) {
        drawBlurredRect(stackOrCtx, x, y, width, height, radius, blurRadius, color, ColorUtils.isBlurEnabled());
    }

    /** {@code forceBlur=true} — размывает даже при выключенном тумблере блюра (фон мира за GUI). */
    public static void drawBlurredRect(Object stackOrCtx, float x, float y, float width, float height, float radius, float blurRadius, Color color, boolean forceBlur) {
        if (!forceBlur) {
            // Без blur-пайплайна сохраняем только тинт (полупрозрачная база), как donor-fallback.
            if (color != null && color.getAlpha() > 0) {
                drawRoundedRect(stackOrCtx, x, y, width, height, radius, color);
            }
            return;
        }
        DrawContext c = stackOrCtx instanceof DrawContext dc ? dc : activeContext;
        if (c == null || width <= 0f || height <= 0f) return;
        if (ColorUtils.isBlurEnabled()) {
            GlassRenderer.draw(c, x, y, width, height, radius,
                    color != null ? toArgb(withClickGuiFade(color)) : toArgb(new Color(255, 255, 255, 220)));
        } else {
            drawBlur(stackOrCtx, x, y, width, height, radius, blurRadius);
            if (color != null && color.getAlpha() > 0) {
                drawRoundedRect(stackOrCtx, x, y, width, height, radius, color);
            }
        }
    }

    /** Диагональная штриховка -45° по прямоугольнику, каждая линия клампится к границам. */
    public static void drawHatch(Object stackOrCtx, float x, float y, float width, float height, float step, float thickness, Color color) {
        Color drawColor = withClickGuiFade(color);
        if (drawColor == null || drawColor.getAlpha() <= 0 || step <= 0f || thickness <= 0f) return;
        if (width <= 0f || height <= 0f || step >= width + height) return;
        DrawContext c = stackOrCtx instanceof DrawContext dc ? dc : activeContext;
        if (c != null) {
            PatternPipeline.hatch(c, x, y, width, height, step, thickness, toArgb(drawColor));
            return;
        }
        for (float d = step; d < width + height; d += step) {
            float x1 = x + Math.min(d, width), y1 = y + Math.max(0f, d - width);
            float x2 = x + Math.max(0f, d - height), y2 = y + Math.min(d, height);
            drawLine(stackOrCtx, x1, y1, x2, y2, thickness, drawColor);
        }
    }

    /** Точечный паттерн области (шаг {@code step}, отступ {@code inset}). */
    public static void drawDotGrid(Object stackOrCtx, float x, float y, float width, float height, float step, float inset, Color color) {
        Color drawColor = withClickGuiFade(color);
        if (drawColor == null || drawColor.getAlpha() <= 0 || step <= 0f) return;
        if (width <= inset || height <= inset) return;
        DrawContext c = stackOrCtx instanceof DrawContext dc ? dc : activeContext;
        if (c != null) {
            PatternPipeline.dotGrid(c, x, y, width, height, step, inset, toArgb(drawColor));
            return;
        }
        for (float dy = y + inset; dy < y + height; dy += step) {
            for (float dx = x + inset; dx < x + width; dx += step) {
                drawPlainRect(stackOrCtx, dx - 1f, dy - 1f, 2f, 2f, drawColor);
            }
        }
    }

    public static void drawLiquidGlass(Object stackOrCtx, float x, float y, float width, float height, float radius,
                                       Color tint) {
        drawLiquidGlass(stackOrCtx, x, y, width, height, radius, 0f, 0f, tint);
    }

    public static void drawLiquidGlass(Object stackOrCtx, float x, float y, float width, float height, float radius,
                                       float blur, float distort, Color tint) {
        DrawContext c = stackOrCtx instanceof DrawContext dc ? dc : activeContext;
        if (c == null || width <= 0 || height <= 0) return;
        Color glass = tint != null ? tint : new Color(20, 25, 22, 160);
        if (ColorUtils.isLiquidGlassShaderActive() && ColorUtils.isBlurEnabled()) {
            GlassRenderer.draw(c, x, y, width, height, radius, toArgb(withClickGuiFade(glass)));
        } else {
            drawRoundedRect(c, x, y, width, height, radius, solidColor(glass));
        }
        drawBorder(c, x, y, width, height, radius, 1.0f, 0.5f, 1.2f, new Color(255, 255, 255, 30));
    }

    public static class LiquidGlassBuilder {
        public float cornerSmoothness = 2.0f;
        public float smoothness = 2.0f;
        public float fresnelPower = 11.0f;
        public float fresnelAlpha = 0.5f;
        public float baseAlpha = 175.0f;
        public boolean fresnelInvert = true;
        public float fresnelMix = 0f;
        public float distortStrength = 0f;
        public float blurRadius = 0f;

        public LiquidGlassBuilder cornerSmoothness(float val) { this.cornerSmoothness = val; return this; }
        public LiquidGlassBuilder smoothness(float val) { this.smoothness = val; return this; }
        public LiquidGlassBuilder fresnelPower(float val) { this.fresnelPower = val; return this; }
        public LiquidGlassBuilder fresnelAlpha(float val) { this.fresnelAlpha = val; return this; }
        public LiquidGlassBuilder baseAlpha(float val) { this.baseAlpha = val; return this; }
        public LiquidGlassBuilder fresnelInvert(boolean val) { this.fresnelInvert = val; return this; }
        public LiquidGlassBuilder fresnelMix(float val) { this.fresnelMix = val; return this; }
        public LiquidGlassBuilder distortStrength(float val) { this.distortStrength = val; return this; }
        public LiquidGlassBuilder blurRadius(float val) { this.blurRadius = val; return this; }
    }

    public static void drawLiquidRectHud(
            Object stackOrCtx,
            float x,
            float y,
            float width,
            float height,
            BorderRadius borderRadius,
            ColorRGBA color,
            float cornerSmoothness,
            float fresnelPower,
            float fresnelAlpha,
            float baseAlpha,
            boolean fresnelInvert,
            float fresnelMix,
            float distortStrength
    ) {
        float r = borderRadius != null ? borderRadius.topLeftRadius() : 10f;
        Color c = color != null ? color.toAwtColor() : new Color(20, 20, 20, 180);
        DrawContext context = stackOrCtx instanceof DrawContext dc ? dc : activeContext;
        if (context == null || width <= 0f || height <= 0f) return;
        if (ColorUtils.isLiquidGlassShaderActive() && ColorUtils.isBlurEnabled()) {
            GlassRenderer.draw(context, x, y, width, height, r, toArgb(withClickGuiFade(c)));
        } else {
            drawRoundedRect(context, x, y, width, height, r, solidColor(c));
            drawBorder(context, x, y, width, height, r, 1.0f, 0.5f, 1.2f, new Color(255, 255, 255, 24));
        }
    }

    public static void drawLiquidRectPreset(
            Object stackOrCtx,
            float x, float y, float width, float height,
            float origWidth, float origHeight,
            BorderRadius borderRadius,
            ColorRGBA color,
            float cornerSmoothness,
            float fresnelPower,
            float fresnelAlpha,
            float baseAlpha,
            boolean fresnelInvert,
            float fresnelMix,
            float distortStrength
    ) {
        drawLiquidRectHud(stackOrCtx, x, y, width, height, borderRadius, color,
                cornerSmoothness, fresnelPower, fresnelAlpha, baseAlpha, fresnelInvert, fresnelMix, distortStrength);
    }

    public static void drawLiquidGlassRectMainMenu(
            Object stackOrCtx,
            float x,
            float y,
            float width,
            float height,
            float radius,
            Color color,
            java.util.function.Consumer<LiquidGlassBuilder> builder
    ) {
        LiquidGlassBuilder b = new LiquidGlassBuilder();
        if (builder != null) {
            builder.accept(b);
        }
        drawLiquidGlass(stackOrCtx, x, y, width, height, radius, color);
    }

    public static void startScissor(DrawContext context, float x, float y, float width, float height) {
        if (context == null || width <= 0 || height <= 0) return;
        context.enableScissor(Math.round(x), Math.round(y), Math.round(x + width), Math.round(y + height));
    }

    public static void startMatrixScissor(DrawContext context, float x, float y, float width, float height) {
        startScissor(context, x, y, width, height);
    }

    public static void startMatrixScissor(DrawContext context, float x, float y, float width, float height, int padding) {
        startScissor(context, x, y, width, height);
    }

    public static void drawLine(Object stack, float x1, float y1, float x2, float y2, float thickness, Color color) {
        if (color == null) return;
        float dx = x2 - x1;
        float dy = y2 - y1;
        float length = (float) Math.hypot(dx, dy);
        if (length <= 0.001f) return;
        float angle = (float) Math.atan2(dy, dx);
        DrawContext ctx = stack instanceof DrawContext dc ? dc : activeContext;
        if (stack instanceof DrawContext dc) {
            prepareHudFrame(dc);
            org.joml.Matrix3x2fStack m3 = dc.getMatrices();
            m3.pushMatrix();
            m3.translate(x1, y1);
            m3.rotate(angle);
            drawPlainRect(dc, 0f, -thickness / 2f, length, thickness, color);
            m3.popMatrix();
        } else if (stack instanceof org.joml.Matrix3x2fStack m3) {
            m3.pushMatrix();
            m3.translate(x1, y1);
            m3.rotate(angle);
            drawPlainRect(ctx != null ? ctx : m3, 0f, -thickness / 2f, length, thickness, color);
            m3.popMatrix();
        } else if (stack instanceof MatrixStack ms) {
            ms.push();
            ms.translate(x1, y1, 0f);
            ms.multiply(net.minecraft.util.math.RotationAxis.POSITIVE_Z.rotation(angle));
            drawPlainRect(ctx != null ? ctx : ms, 0f, -thickness / 2f, length, thickness, color);
            ms.pop();
        } else {
            // Fallback: axis-aligned thin quad (approximately the line).
            drawPlainRect(ctx != null ? ctx : stack, Math.min(x1, x2), Math.min(y1, y2),
                    Math.max(Math.abs(dx), thickness), Math.max(Math.abs(dy), thickness), color);
        }
    }

    public static void drawLine(Object stack, float x1, float y1, float x2, float y2, float thickness, ColorRGBA color) {
        drawLine(stack, x1, y1, x2, y2, thickness, color != null ? color.toAwtColor() : Color.WHITE);
    }

    public static void drawLiquidRect(
            Object stackOrCtx,
            float x,
            float y,
            float width,
            float height,
            BorderRadius borderRadius,
            ColorRGBA color,
            float cornerSmoothness,
            float fresnelPower,
            float fresnelAlpha,
            float baseAlpha,
            boolean fresnelInvert,
            float fresnelMix,
            float distortStrength
    ) {
        drawLiquidRectHud(stackOrCtx, x, y, width, height, borderRadius, color, cornerSmoothness, fresnelPower, fresnelAlpha, baseAlpha, fresnelInvert, fresnelMix, distortStrength);
    }

    public static void stopScissor(DrawContext context) {
        if (context == null) return;
        // Guard against double-stop/clear sequences (e.g. a screen that already
        // ended its scissor then calls clearScissor) throwing "Scissor stack underflow".
        if (context.scissorStack != null && context.scissorStack.peekLast() != null) {
            context.disableScissor();
        }
    }

    public static void clearScissor(DrawContext context) {
        stopScissor(context);
    }

    public static void drawLiquidRectClickGuiPanel(
            Object stackOrCtx, float x, float y, float width, float height,
            BorderRadius borderRadius, ColorRGBA color, float cornerSmoothness,
            float fresnelPower, float fresnelAlpha, float baseAlpha,
            boolean fresnelInvert, float fresnelMix, float distortStrength
    ) {
        drawLiquidRectHud(stackOrCtx, x, y, width, height, borderRadius, color, cornerSmoothness, fresnelPower, fresnelAlpha, baseAlpha, fresnelInvert, fresnelMix, distortStrength);
    }

    public static void drawLiquidRectClickGuiSettings(
            Object stackOrCtx, float x, float y, float width, float height,
            BorderRadius borderRadius, ColorRGBA color, float cornerSmoothness,
            float fresnelPower, float fresnelAlpha, float baseAlpha,
            boolean fresnelInvert, float fresnelMix, float distortStrength
    ) {
        drawLiquidRectHud(stackOrCtx, x, y, width, height, borderRadius, color, cornerSmoothness, fresnelPower, fresnelAlpha, baseAlpha, fresnelInvert, fresnelMix, distortStrength);
    }

    public static void drawLiquidRectClickGuiSearch(
            Object stackOrCtx, float x, float y, float width, float height,
            BorderRadius borderRadius, ColorRGBA color, float cornerSmoothness,
            float fresnelPower, float fresnelAlpha, float baseAlpha,
            boolean fresnelInvert, float fresnelMix, float distortStrength
    ) {
        drawLiquidRectHud(stackOrCtx, x, y, width, height, borderRadius, color, cornerSmoothness, fresnelPower, fresnelAlpha, baseAlpha, fresnelInvert, fresnelMix, distortStrength);
    }

    public static void drawLiquidRectClickGuiChip(
            Object stackOrCtx, float x, float y, float width, float height,
            BorderRadius borderRadius, ColorRGBA color, float cornerSmoothness,
            float fresnelPower, float fresnelAlpha, float baseAlpha,
            boolean fresnelInvert, float fresnelMix, float distortStrength
    ) {
        drawLiquidRectHud(stackOrCtx, x, y, width, height, borderRadius, color, cornerSmoothness, fresnelPower, fresnelAlpha, baseAlpha, fresnelInvert, fresnelMix, distortStrength);
    }

    public static void drawLiquidRectClickGuiColorPicker(
            Object stackOrCtx, float x, float y, float width, float height,
            BorderRadius borderRadius, ColorRGBA color, float cornerSmoothness,
            float fresnelPower, float fresnelAlpha, float baseAlpha,
            boolean fresnelInvert, float fresnelMix, float distortStrength
    ) {
        drawLiquidRectHud(stackOrCtx, x, y, width, height, borderRadius, color, cornerSmoothness, fresnelPower, fresnelAlpha, baseAlpha, fresnelInvert, fresnelMix, distortStrength);
    }

    public static String stripObfuscatedFormatting(String text) {
        if (text == null || text.isEmpty()) return "";
        if (text.indexOf('\u00a7') < 0 && text.indexOf('&') < 0) return text;
        StringBuilder result = new StringBuilder(text.length());
        boolean skipObfuscated = false;

        for (int i = 0; i < text.length(); i++) {
            char current = text.charAt(i);
            if ((current == '\u00a7' || current == '&') && i + 1 < text.length()) {
                char code = Character.toLowerCase(text.charAt(++i));
                if (code == 'k') {
                    skipObfuscated = true;
                    continue;
                }
                if (isMinecraftFormattingCode(code)) {
                    skipObfuscated = false;
                    continue;
                }
                if (!skipObfuscated) {
                    result.append(current).append(code);
                }
                continue;
            }

            if (!skipObfuscated) {
                result.append(current);
            }
        }
        return result.toString();
    }

    private static boolean isMinecraftFormattingCode(char code) {
        return (code >= '0' && code <= '9')
                || (code >= 'a' && code <= 'f')
                || code == 'l'
                || code == 'm'
                || code == 'n'
                || code == 'o'
                || code == 'r';
    }
}
