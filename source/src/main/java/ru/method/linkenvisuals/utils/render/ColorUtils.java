package ru.method.linkenvisuals.utils.render;

import lombok.experimental.UtilityClass;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.gui.screen.Screen;
import org.lwjgl.opengl.GL11;
import ru.method.linkenvisuals.LinkenVisuals;
import ru.method.linkenvisuals.screen.clickgui.ClickGui;
import ru.method.linkenvisuals.screen.cosmetics.CosmeticsScreen;
import ru.method.linkenvisuals.screen.linkgui.LinkGuiScreen;
import ru.method.linkenvisuals.utils.Wrapper;

import java.awt.Color;
import java.nio.ByteBuffer;

@UtilityClass
public class ColorUtils implements Wrapper {

    /** Color с каждым каналом, зажатым в [0,255]. Единый хелпер вместо копий createSafeColor в HUD. */
    public static Color safeColor(int red, int green, int blue, int alpha) {
        return new Color(
                Math.max(0, Math.min(255, red)),
                Math.max(0, Math.min(255, green)),
                Math.max(0, Math.min(255, blue)),
                Math.max(0, Math.min(255, alpha))
        );
    }

    private static final ByteBuffer PICKER_BUFFER = ByteBuffer.allocateDirect(4);
    private static long pickerFrameId = -1L;
    private static float pickerCacheAX = Float.NaN;
    private static float pickerCacheAY = Float.NaN;
    private static Color pickerCacheAColor;
    private static float pickerCacheBX = Float.NaN;
    private static float pickerCacheBY = Float.NaN;
    private static Color pickerCacheBColor;

    private static Color primaryColor = new Color(95, 218, 139);
    private static Color secondaryColor = new Color(170, 232, 195);
    private static Color backgroundColor = new Color(220, 220, 220);
    private static Color textColor = new Color(244, 247, 250);
    private static boolean themeFadeEnabled = true;
    private static boolean blurEnabled = true;

    /**
     * Liquid-glass shader + capture. Set {@code false} for A/B FPS test without glass GPU cost.
     */
    private static final boolean LIQUID_GLASS_SHADER_ENABLED = true;


    public Color getPrimaryColor() {
        return primaryColor;
    }

    public void setPrimaryColor(Color value) {
        if (value == null) return;
        primaryColor = new Color(value.getRed(), value.getGreen(), value.getBlue(), value.getAlpha());
    }

    public Color getSecondaryColor() {
        return secondaryColor;
    }

    public void setSecondaryColor(Color value) {
        if (value == null) return;
        secondaryColor = new Color(value.getRed(), value.getGreen(), value.getBlue(), value.getAlpha());
    }

    public Color getBackgroundColor() {
        return backgroundColor;
    }

    public void setBackgroundColor(Color value) {
        if (value == null) return;
        backgroundColor = new Color(value.getRed(), value.getGreen(), value.getBlue(), value.getAlpha());
    }

    public Color getTextColor() {
        return textColor;
    }

    public void setTextColor(Color value) {
        if (value == null) return;
        textColor = new Color(value.getRed(), value.getGreen(), value.getBlue(), value.getAlpha());
    }

    public boolean isThemeFadeEnabled() {
        return themeFadeEnabled;
    }

    public void setThemeFadeEnabled(boolean enabled) {
        themeFadeEnabled = enabled;
    }

    public boolean isBlurEnabled() {
        return blurEnabled;
    }

    public boolean isLiquidGlassActive() {
        return LIQUID_GLASS_SHADER_ENABLED;
    }

    public boolean isLiquidGlassShaderActive() {
        return LIQUID_GLASS_SHADER_ENABLED;
    }

    public boolean isClickGuiOpen() {
        Screen screen = mc == null ? null : mc.currentScreen;
        return screen instanceof ClickGui;
    }

    public boolean isHudEditorOpen() {
        Screen screen = mc == null ? null : mc.currentScreen;
        return screen instanceof ChatScreen;
    }

    public boolean isOverlayGuiOpen() {
        return isClickGuiOpen() || isHudEditorOpen() || isLinkGuiOpen();
    }

    public boolean isLinkGuiOpen() {
        Screen screen = mc == null ? null : mc.currentScreen;
        return screen instanceof LinkGuiScreen;
    }

    /** True when the main framebuffer may contain GUI overlays — avoid recapturing gameplay mid-draw. */
    public boolean blocksGameplayReflectionRecapture() {
        Screen screen = mc == null ? null : mc.currentScreen;
        return screen instanceof LinkGuiScreen || screen instanceof CosmeticsScreen;
    }

    public void setBlurEnabled(boolean enabled) {
        blurEnabled = enabled;
    }

    public Color picker(float x, float y) {
        long frameId = RenderFrameTracker.getFrameId();
        float sampleX = Math.round(x);
        float sampleY = Math.round(y);
        if (frameId == pickerFrameId) {
            if (sampleX == pickerCacheAX && sampleY == pickerCacheAY && pickerCacheAColor != null) {
                return pickerCacheAColor;
            }
            if (sampleX == pickerCacheBX && sampleY == pickerCacheBY && pickerCacheBColor != null) {
                return pickerCacheBColor;
            }
        } else {
            pickerFrameId = frameId;
            pickerCacheAX = Float.NaN;
            pickerCacheAY = Float.NaN;
            pickerCacheAColor = null;
            pickerCacheBX = Float.NaN;
            pickerCacheBY = Float.NaN;
            pickerCacheBColor = null;
        }

        Color sampled = readPixelColor(sampleX, sampleY);
        if (pickerCacheAColor == null) {
            pickerCacheAX = sampleX;
            pickerCacheAY = sampleY;
            pickerCacheAColor = sampled;
        } else {
            pickerCacheBX = sampleX;
            pickerCacheBY = sampleY;
            pickerCacheBColor = sampled;
        }
        return sampled;
    }

    private Color readPixelColor(float x, float y) {
        RenderSystem.assertOnRenderThread();
        ByteBuffer buffer = PICKER_BUFFER;
        buffer.clear();

        double scale = mc.getWindow().getScaleFactor();
        int px = (int) (x * scale);
        int py = (int) ((mc.getWindow().getScaledHeight() - y) * scale);

        GL11.glReadPixels(
                px,
                py,
                1, 1,
                GL11.GL_RGBA,
                GL11.GL_UNSIGNED_BYTE,
                buffer
        );
        buffer.rewind();

        return new Color(
                buffer.get() & 0xFF,
                buffer.get() & 0xFF,
                buffer.get() & 0xFF,
                buffer.get() & 0xFF
        );
    }

    public Color getGlobalColor() {
        return getGlobalColor(0f, 255);
    }

    public Color getGlobalColor(int alpha) {
        return getGlobalColor(0f, alpha);
    }

    public Color getGlobalColor(float offset) {
        return getGlobalColor(offset, 255);
    }

    public Color getGlobalColor(float offset, int alpha) {
        Color base = resolveThemeColor(offset);
        return applyAlpha(base, alpha);
    }

    private Color resolveThemeColor(float offset) {
        if (!themeFadeEnabled) {
            return primaryColor;
        }
        float t = offset % 1f;
        if (t < 0f) t += 1f;
        float pingPong = t <= 0.5f ? t * 2f : (1f - t) * 2f;
        return mix(primaryColor, secondaryColor, pingPong);
    }

    public Color alpha(Color color, int alpha) {
        return new Color(color.getRed(), color.getGreen(), color.getBlue(), alpha);
    }

    public Color fade(Color color1, Color color2, float alpha) {
        int r = (int) (color1.getRed() * (1 - alpha) + color2.getRed() * alpha);
        int g = (int) (color1.getGreen() * (1 - alpha) + color2.getGreen() * alpha);
        int b = (int) (color1.getBlue() * (1 - alpha) + color2.getBlue() * alpha);
        int a = (int) (color1.getAlpha() * (1 - alpha) + color2.getAlpha() * alpha);
        return new Color(r, g, b, a);
    }

    public Color pulse(Color color, long speed) {
        speed = clampLong(speed, 0, 30);

        double sin = Math.sin(Math.TAU * (speed / 30f) * ((System.currentTimeMillis() - LinkenVisuals.getInstance().getInitTime()) / 1000f));
        double scale = (sin + 1f) / 2f;
        return new Color(color.getRed(), color.getGreen(), color.getBlue(), (int) (color.getAlpha() * scale));
    }

    public Color offset(Color color, float alpha) {
        alpha = clampFloat(alpha, 0f, 1f);
        return new Color(color.getRed(), color.getGreen(), color.getBlue(), (int) (color.getAlpha() * alpha));
    }

    public Color gradient(float amount) {
        return gradient(0f, amount);
    }

    public Color gradient(float offset, float amount) {
        Color primary = resolveThemeColor(offset);
        Color secondary = resolveThemeColor(offset + 0.5f);
        return gradient(primary, secondary, amount);
    }

    public Color gradient(Color color1, Color color2, float amount) {
        amount = clampFloat(amount, 0f, 1f);
        int r = (int) lerp(amount, color1.getRed(), color2.getRed());
        int g = (int) lerp(amount, color1.getGreen(), color2.getGreen());
        int b = (int) lerp(amount, color1.getBlue(), color2.getBlue());
        int a = (int) lerp(amount, color1.getAlpha(), color2.getAlpha());
        return new Color(r, g, b, a);
    }

    private Color applyAlpha(Color color, int alpha) {
        return new Color(color.getRed(), color.getGreen(), color.getBlue(), clampChannel(alpha));
    }

    private int clampChannel(int value) {
        return (int) clampLong(value, 0, 255);
    }

    public Color mix(Color first, Color second, float factor) {
        factor = clampFloat(factor, 0.0f, 1.0f);
        int r = (int) lerp(factor, first.getRed(), second.getRed());
        int g = (int) lerp(factor, first.getGreen(), second.getGreen());
        int b = (int) lerp(factor, first.getBlue(), second.getBlue());
        int a = (int) lerp(factor, first.getAlpha(), second.getAlpha());
        return new Color(r, g, b, a);
    }

    public Color getGlassBase() {
        Color base = backgroundColor;
        return new Color(base.getRed(), base.getGreen(), base.getBlue());
    }

    public Color getGlassColor(int alpha) {
        Color base = getGlassBase();
        int softenedAlpha = clampChannel((int) Math.round(alpha * 0.55f));
        return new Color(base.getRed(), base.getGreen(), base.getBlue(), softenedAlpha);
    }

    public Color getGlassHighlightColor() {
        Color base = mix(getGlassBase(), Color.WHITE, 0.18f);
        return new Color(base.getRed(), base.getGreen(), base.getBlue());
    }

    public Color getGlassOutlineColor() {
        Color base = mix(getGlassBase(), Color.WHITE, 0.12f);
        return new Color(base.getRed(), base.getGreen(), base.getBlue());
    }

    public Color getTextColor(int alpha) {
        return applyAlpha(textColor, alpha);
    }

    private float clampFloat(float value, float min, float max) {
        if (value < min) return min;
        if (value > max) return max;
        return value;
    }

    private long clampLong(long value, long min, long max) {
        if (value < min) return min;
        if (value > max) return max;
        return value;
    }

    private float lerp(float t, float a, float b) {
        return a + (b - a) * t;
    }
}
