package dev.linkenvisuals.ui.linkmenu;

import dev.linkenvisuals.render.GlowPipeline;
import dev.linkenvisuals.render.RoundedRectPipeline;
import net.minecraft.client.gui.DrawContext;

/**
 * 1:1 Window material and corner brackets ported from donor WindowMaterial.java (4b/9b).
 * Provides the dark frosted glass body with subtle glow, depth, top accent sheen,
 * and the iconic 20x20x2px mint corner brackets (top-left and bottom-right).
 */
public final class WindowMaterial {
    public static final float RADIUS = 12.0f;
    public static final float CORNER_SIZE = 20.0f;
    public static final float CORNER_THICK = 2.0f;
    public static final float CORNER_OFFSET = 14.0f;

    private WindowMaterial() {
    }

    /**
     * Renders the complete dialog window container:
     * outer neon glow, multi-layer depth shadow, dark frosted base gradient,
     * top accent highlight, and subtle glass border.
     */
    public static void window(DrawContext context, float x, float y, float w, float h,
                              float scale, float open) {
        if (open <= 0.001f) {
            return;
        }

        // 1. Neon ambient glow around perimeter
        GlowPipeline.radial(context, x + w * 0.5f, y + h * 0.5f, Math.max(w, h) * 0.55f,
            LinkenMenuChrome.rgba(LinkenMenuChrome.ACCENT, Math.round(28.0f * open)));

        // 2. Base vertical gradient (17,26,21 -> 5,8,7 with alpha ~235)
        RoundedRectPipeline.drawVerticalGradient(context, x, y, w, h, RADIUS,
            LinkenMenuChrome.rgba(0x111A15, Math.round(242.0f * open)),
            LinkenMenuChrome.rgba(0x050807, Math.round(246.0f * open)));

        // 3. Top accent sheen (soft mint glow at upper edge)
        float sheenH = Math.min(200.0f, h * 0.45f);
        RoundedRectPipeline.drawVerticalGradient(context, x, y, w, sheenH, RADIUS,
            LinkenMenuChrome.rgba(LinkenMenuChrome.ACCENT, Math.round(18.0f * open)),
            LinkenMenuChrome.rgba(LinkenMenuChrome.ACCENT, 0));

        // 4. Subtle inner highlight line along top edge
        RoundedRectPipeline.draw(context, x + RADIUS, y + 1.0f, w - RADIUS * 2.0f, 1.0f, 0.5f,
            LinkenMenuChrome.rgba(0xFFFFFF, Math.round(28.0f * open)));

        // 5. Delicate outer border
        RoundedRectPipeline.drawBorder(context, x, y, w, h, RADIUS, 0.0f, 1.0f, 1.4f,
            LinkenMenuChrome.rgba(0xFFFFFF, Math.round(24.0f * open)));
    }

    /**
     * Renders the geometric mint corner brackets:
     * top-left ┌ at offset 14px, bottom-right ┘ at offset 14px (+2px shift).
     */
    public static void corners(DrawContext context, float x, float y, float w, float h,
                               float scale, float open) {
        if (open <= 0.001f) {
            return;
        }
        int color = LinkenMenuChrome.rgba(LinkenMenuChrome.ACCENT, Math.round(240.0f * open));
        float s = CORNER_SIZE;
        float th = CORNER_THICK;
        float o = CORNER_OFFSET;
        float r = th * 0.5f;

        // Top-left bracket: ┌
        float tlx = x + o;
        float tly = y + o;
        RoundedRectPipeline.draw(context, tlx, tly, s, th, r, color);
        RoundedRectPipeline.draw(context, tlx, tly, th, s, r, color);

        // Bottom-right bracket: ┘ (shifted 2px lower-right per donor spec)
        float brx = x + w - o - s + 2.0f;
        float bry = y + h - o - s + 2.0f;
        RoundedRectPipeline.draw(context, brx, bry + s - th, s, th, r, color);
        RoundedRectPipeline.draw(context, brx + s - th, bry, th, s, r, color);
    }
}
