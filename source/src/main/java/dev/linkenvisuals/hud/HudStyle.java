package dev.linkenvisuals.hud;

import dev.linkenvisuals.clickgui.ModuleRegistry;
import dev.linkenvisuals.clickgui.link.LinkGuiTheme;
import dev.linkenvisuals.render.GlassPanelPipeline;
import dev.linkenvisuals.render.NeonEdge;
import dev.linkenvisuals.render.RoundedRectPipeline;
import dev.linkenvisuals.ui.Theme;
import dev.linkenvisuals.ui.style.StyleFlavor;
import dev.linkenvisuals.util.ColorMath;
import net.minecraft.client.gui.DrawContext;

/**
 * Shared HUD material/shape resolver, translated from the donor
 * {@code LinkHudStyle}. The donor's eight variants are reproduced with the
 * same shade amounts, gradient rails, radii and border alphas; the selected
 * LinkGUI theme supplies the base colour.
 */
public final class HudStyle {
    public enum Variant {
        CARD, MINIMAL, NEON, AURORA, GLASS, SOLID, GRADIENT, OUTLINE
    }

    public enum Shape {
        ROUNDED, PILL, SHARP
    }

    private HudStyle() {
    }

    public static Variant variant() {
        String value = ModuleRegistry.hudStyle() == null ? "Card" : ModuleRegistry.hudStyle().value();
        return switch (value == null ? "" : value.toLowerCase(java.util.Locale.ROOT)) {
            case "linken", "card" -> Variant.CARD;
            case "minimal" -> Variant.MINIMAL;
            case "neon" -> Variant.NEON;
            case "aurora" -> Variant.AURORA;
            case "glass" -> Variant.GLASS;
            case "solid" -> Variant.SOLID;
            case "gradient" -> Variant.GRADIENT;
            case "outline" -> Variant.OUTLINE;
            default -> Variant.CARD;
        };
    }

    public static Shape shape() {
        String value = ModuleRegistry.hudShape() == null ? "Rounded" : ModuleRegistry.hudShape().value();
        return switch (value == null ? "" : value.toLowerCase(java.util.Locale.ROOT)) {
            case "pill" -> Shape.PILL;
            case "sharp" -> Shape.SHARP;
            default -> Shape.ROUNDED;
        };
    }

    public static float radius(float width, float height) {
        return radius(shape(), width, height);
    }

    /** Pure shape rule; kept static so the geometry can be tested without Minecraft. */
    public static float radius(Shape shape, float width, float height) {
        float cap = Math.max(0.0f, Math.min(width, height) * 0.5f);
        return switch (shape == null ? Shape.ROUNDED : shape) {
            case PILL -> cap;
            case SHARP -> Math.min(2.0f, cap);
            default -> Math.min(5.0f, cap);
        };
    }

    public static void panel(DrawContext context, String element, float x, float y,
                             float width, float height, float alpha) {
        if (width <= 0.0f || height <= 0.0f || alpha <= 0.01f) {
            return;
        }
        float a = Math.max(0.0f, Math.min(1.0f, alpha));
        float radius = radius(width, height);
        int base = base();
        int accent = accent(element);
        switch (variant()) {
            case CARD -> {
                RoundedRectPipeline.drawBorder(context, x - 6.0f, y - 6.0f + 1.5f,
                    width + 12.0f, height + 12.0f, radius + 6.0f, 0.0f, 1.5f, 6.0f,
                    ColorMath.withAlpha(0x000000, Math.round(46.0f * a)));
                RoundedRectPipeline.drawVerticalGradient(context, x, y, width, height, radius,
                    hudShade(base, accent, 0.05f, Math.round(238.0f * a)),
                    hudShade(base, accent, 0.012f, Math.round(248.0f * a)));
                int aurora = ColorMath.mix(accent, 0xFFFFFF, 0.22f);
                float auroraHeight = Math.min(height * 0.5f, 15.0f);
                RoundedRectPipeline.drawVerticalGradient(context, x, y, width, auroraHeight, radius,
                    ColorMath.withAlpha(aurora, Math.round(30.0f * a)),
                    ColorMath.withAlpha(aurora, 0));
                float shadeHeight = Math.min(height * 0.32f, 10.0f);
                RoundedRectPipeline.drawVerticalGradient(context, x, y + height - shadeHeight,
                    width, shadeHeight, radius, ColorMath.withAlpha(0x000000, 0),
                    ColorMath.withAlpha(0x000000, Math.round(58.0f * a)));
                int border = ColorMath.mix(ColorMath.mix(base,
                    LinkGuiTheme.isLight() ? 0x000000 : 0xFFFFFF, 0.14f), accent, 0.35f);
                RoundedRectPipeline.drawBorder(context, x, y, width, height, radius, 0.0f, 1.0f, 1.6f,
                    ColorMath.withAlpha(border, Math.round(120.0f * a)));
                RoundedRectPipeline.draw(context, x + radius, y + 1.0f,
                    width - radius * 2.0f, 1.0f, 0.5f,
                    ColorMath.withAlpha(0xFFFFFF, Math.round(16.0f * a)));
            }
            case MINIMAL -> {
                float r = Math.min(radius, 4.0f);
                RoundedRectPipeline.draw(context, x, y, width, height, r,
                    hudShade(base, accent, 0.02f, Math.round(120.0f * a)));
                float barWidth = 2.2f;
                float barInset = Math.min(height * 0.18f, radius + 1.0f);
                RoundedRectPipeline.drawVerticalGradient(context, x + 2.0f, y + barInset,
                    barWidth, height - barInset * 2.0f, barWidth * 0.5f,
                    ColorMath.withAlpha(accent, Math.round(235.0f * a)),
                    ColorMath.withAlpha(Theme.secondary(), Math.round(235.0f * a)));
            }
            case NEON -> {
                RoundedRectPipeline.drawBorder(context, x - 5.0f, y - 5.0f,
                    width + 10.0f, height + 10.0f, radius + 5.0f, 0.0f, 1.5f, 5.0f,
                    ColorMath.withAlpha(accent, Math.round(72.0f * a)));
                RoundedRectPipeline.drawVerticalGradient(context, x, y, width, height, radius,
                    hudShade(base, accent, 0.02f, Math.round(236.0f * a)),
                    hudShade(base, accent, 0.06f, Math.round(248.0f * a)));
                RoundedRectPipeline.draw(context, x + radius, y + 1.2f,
                    width - radius * 2.0f, 1.0f, 0.5f,
                    ColorMath.withAlpha(0xFFFFFF, Math.round(22.0f * a)));
                RoundedRectPipeline.drawBorder(context, x, y, width, height, radius, 0.0f, 1.0f, 2.0f,
                    ColorMath.withAlpha(accent, Math.round(220.0f * a)));
                RoundedRectPipeline.drawBorder(context, x + 1.2f, y + 1.2f,
                    width - 2.4f, height - 2.4f, Math.max(0.0f, radius - 1.2f), 0.0f, 1.0f, 1.2f,
                    ColorMath.withAlpha(accent, Math.round(60.0f * a)));
            }
            case AURORA -> {
                int top = ColorMath.mix(base, 0xFFFFFF, 0.07f);
                int bottom = ColorMath.mix(base, 0x000000, 0.34f);
                RoundedRectPipeline.drawVerticalGradient(context, x, y, width, height, radius,
                    ColorMath.withAlpha(top, Math.round(236.0f * a)),
                    ColorMath.withAlpha(bottom, Math.round(248.0f * a)));
                float sheenHeight = Math.min(height * 0.30f, 6.0f);
                RoundedRectPipeline.draw(context, x + radius * 0.6f, y + 0.8f,
                    width - radius * 1.2f, sheenHeight, radius * 0.5f,
                    ColorMath.withAlpha(0xFFFFFF, Math.round(26.0f * a)));
                float barWidth = 2.2f;
                RoundedRectPipeline.drawVerticalGradient(context, x + 1.6f, y + radius * 0.55f,
                    barWidth, height - radius * 1.1f, barWidth * 0.5f,
                    ColorMath.withAlpha(accent, Math.round(240.0f * a)),
                    ColorMath.withAlpha(Theme.secondary(), Math.round(240.0f * a)));
                RoundedRectPipeline.drawBorder(context, x, y, width, height, radius, 0.0f, 1.1f, 2.6f,
                    ColorMath.withAlpha(accent, Math.round(125.0f * a)));
            }
            case SOLID -> {
                float r = Math.min(radius, 2.2f);
                int fill = ColorMath.mix(base, 0x000000, 0.10f);
                RoundedRectPipeline.draw(context, x, y, width, height, r,
                    ColorMath.withAlpha(fill, Math.round(246.0f * a)));
                RoundedRectPipeline.drawBorder(context, x, y, width, height, r, 0.0f, 1.0f, 2.0f,
                    ColorMath.withAlpha(accent, Math.round(135.0f * a)));
            }
            case GRADIENT -> {
                int top = ColorMath.mix(base, 0xFFFFFF, 0.06f);
                int bottom = ColorMath.mix(base, 0x000000, 0.30f);
                RoundedRectPipeline.drawVerticalGradient(context, x, y, width, height, radius,
                    ColorMath.withAlpha(top, Math.round(236.0f * a)),
                    ColorMath.withAlpha(bottom, Math.round(248.0f * a)));
                float stripeHeight = Math.min(2.5f, height * 0.12f);
                RoundedRectPipeline.drawHorizontalGradient(context, x + radius * 0.5f, y,
                    width - radius, stripeHeight, stripeHeight * 0.5f,
                    ColorMath.withAlpha(accent, Math.round(220.0f * a)),
                    ColorMath.withAlpha(Theme.secondary(), Math.round(220.0f * a)));
                RoundedRectPipeline.drawBorder(context, x, y, width, height, radius, 0.0f, 1.0f, 2.0f,
                    ColorMath.withAlpha(accent, Math.round(95.0f * a)));
            }
            case OUTLINE -> {
                int fill = ColorMath.mix(base, 0x000000, 0.12f);
                RoundedRectPipeline.draw(context, x, y, width, height, radius,
                    ColorMath.withAlpha(fill, Math.round(140.0f * a)));
                int border = ColorMath.withAlpha(accent, Math.round(235.0f * a));
                float length = Math.min(width, height) * 0.30f;
                float thickness = 1.4f;
                RoundedRectPipeline.draw(context, x, y, length, thickness, thickness * 0.5f, border);
                RoundedRectPipeline.draw(context, x, y, thickness, length, thickness * 0.5f, border);
                RoundedRectPipeline.draw(context, x + width - length, y, length, thickness, thickness * 0.5f, border);
                RoundedRectPipeline.draw(context, x + width - thickness, y, thickness, length, thickness * 0.5f, border);
                RoundedRectPipeline.draw(context, x, y + height - thickness, length, thickness, thickness * 0.5f, border);
                RoundedRectPipeline.draw(context, x, y + height - length, thickness, length, thickness * 0.5f, border);
                RoundedRectPipeline.draw(context, x + width - length, y + height - thickness,
                    length, thickness, thickness * 0.5f, border);
                RoundedRectPipeline.draw(context, x + width - thickness, y + height - length,
                    thickness, length, thickness * 0.5f, border);
                RoundedRectPipeline.drawBorder(context, x, y, width, height, radius, 0.0f, 1.0f, 2.0f,
                    ColorMath.withAlpha(accent, Math.round(80.0f * a)));
            }
            default -> {
                int top = ColorMath.mix(base, 0xFFFFFF, 0.05f);
                int bottom = ColorMath.mix(base, 0x000000, 0.26f);
                RoundedRectPipeline.drawVerticalGradient(context, x, y, width, height, radius,
                    ColorMath.withAlpha(top, Math.round(232.0f * a)),
                    ColorMath.withAlpha(bottom, Math.round(246.0f * a)));
                RoundedRectPipeline.drawBorder(context, x, y, width, height, radius, 0.0f, 1.0f, 2.0f,
                    ColorMath.withAlpha(accent, Math.round(115.0f * a)));
            }
        }
    }

    public static void plate(DrawContext context, String element, float x, float y,
                             float width, float height, float alpha) {
        if (width <= 0.0f || height <= 0.0f || alpha <= 0.01f) {
            return;
        }
        float a = Math.max(0.0f, Math.min(1.0f, alpha));
        float radius = radius(width, height);
        int accent = accent(element);
        Variant variant = variant();
        RoundedRectPipeline.draw(context, x, y, width, height, radius,
            inset(Math.round(205.0f * a)));
        if (variant == Variant.OUTLINE || variant == Variant.GRADIENT) {
            RoundedRectPipeline.drawBorder(context, x, y, width, height, radius, 0.0f, 1.0f, 1.6f,
                ColorMath.withAlpha(accent, Math.round(70.0f * a)));
        } else if (variant == Variant.CARD || variant == Variant.NEON) {
            RoundedRectPipeline.drawBorder(context, x, y, width, height, radius, 0.0f, 1.0f, 1.4f,
                ColorMath.withAlpha(LinkGuiTheme.isLight() ? 0x000000 : 0xFFFFFF,
                    Math.round(34.0f * a)));
        }
    }

    private static int base() {
        return LinkGuiTheme.hudBase();
    }

    private static int accent(String element) {
        return Theme.hudTint(element);
    }

    private static int hudShade(int base, int accent, float amount, int alpha) {
        return ColorMath.withAlpha(ColorMath.mix(base, accent, amount), alpha);
    }

    /** Donor {@code themeInset}: theme/base colour mixed 16 % toward the contrast colour. */
    public static int inset(int alpha) {
        return inset(alpha, 0.16f);
    }

    /** Donor {@code themeInset(alpha, mix)} used by item cells and tinted plates. */
    public static int inset(int alpha, float mix) {
        int mixed = ColorMath.mix(base(), LinkGuiTheme.isLight() ? 0x000000 : 0xFFFFFF, mix);
        return ColorMath.withAlpha(mixed, alpha);
    }
}
