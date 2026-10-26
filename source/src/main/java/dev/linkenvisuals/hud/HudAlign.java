package dev.linkenvisuals.hud;

import dev.linkenvisuals.render.GlyphAtlas;
import dev.linkenvisuals.render.MsdfText;
import dev.linkenvisuals.render.RoundedRectPipeline;
import dev.linkenvisuals.util.ColorMath;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.Identifier;

/**
 * Ergonomic, subpixel-accurate layout and alignment utility for HUD elements.
 * Eliminates manual pixel-tweaking by providing fluent rows, vertical/horizontal
 * centering, typography baseline offsets, and atomic surface primitives.
 */
public final class HudAlign {
    private static final float CAP_HEIGHT = 0.70f;
    private static final float ASCENDER = 0.952f;

    private HudAlign() {
    }

    /** Returns top Y for centering an element of height {@code elementH} inside {@code [containerY, containerY + containerH]}. */
    public static float centerY(float containerY, float containerH, float elementH) {
        return containerY + (containerH - elementH) * 0.5f;
    }

    /** Returns left X for centering an element of width {@code elementW} inside {@code [containerX, containerX + containerW]}. */
    public static float centerX(float containerX, float containerW, float elementW) {
        return containerX + (containerW - elementW) * 0.5f;
    }

    /** Returns right-aligned X inside container with given margin/padding. */
    public static float rightX(float containerX, float containerW, float elementW, float padding) {
        return containerX + containerW - padding - elementW;
    }

    /** Returns left-aligned X with given padding. */
    public static float leftX(float containerX, float padding) {
        return containerX + padding;
    }

    /**
     * Returns top Y for MSDF text centered vertically in a container.
     * Takes font cap-height and ascender into account for optical vertical balance.
     */
    public static float centerTextY(float containerY, float containerH, float fontSize) {
        float centerY = containerY + containerH * 0.5f;
        float baseline = centerY + CAP_HEIGHT * 0.5f * fontSize;
        return baseline - ASCENDER * fontSize;
    }

    /** Returns text baseline Y for optical vertical centering. */
    public static float baselineY(float containerY, float containerH, float fontSize) {
        return containerY + containerH * 0.5f + CAP_HEIGHT * 0.5f * fontSize;
    }

    /** Creates a fluent horizontal row layout with start padding and item spacing. */
    public static Row row(float x, float y, float height, float padding, float gap) {
        return new Row(x, y, height, padding, gap);
    }

    /** Creates a fluent row layout defaulting to standard HUD padding (7px) and gap (6px). */
    public static Row row(float x, float y, float height) {
        return new Row(x, y, height, 7.0f, 6.0f);
    }

    /** Draws a clean vertical divider line centered vertically. */
    public static void drawDivider(DrawContext context, float x, float centerY, float halfHeight, int color) {
        context.fill(Math.round(x), Math.round(centerY - halfHeight), Math.round(x) + 1,
            Math.round(centerY + halfHeight), color);
    }

    /** Draws a texture icon at centered coordinates. */
    public static void drawIcon(DrawContext context, Identifier texture, float x, float y, float size, int texSize, int color) {
        context.drawTexture(RenderPipelines.GUI_TEXTURED, texture,
            Math.round(x), Math.round(y), 0.0f, 0.0f,
            Math.round(size), Math.round(size), texSize, texSize, texSize, texSize, color);
    }

    /**
     * Fluent horizontal layout tracker. Keeps track of running cursor X, vertical centering,
     * and automatic item spacing so HUD elements can be laid out in just a few lines.
     */
    public static final class Row {
        private final float startX;
        private final float y;
        private final float height;
        private final float gap;
        private float cursor;

        public Row(float x, float y, float height, float padding, float gap) {
            this.startX = x + padding;
            this.cursor = this.startX;
            this.y = y;
            this.height = height;
            this.gap = gap;
        }

        /** Returns current cursor X and advances the cursor by {@code width + gap}. */
        public float next(float width) {
            float at = cursor;
            cursor += width + gap;
            return at;
        }

        /** Returns current cursor X without advancing. */
        public float current() {
            return cursor;
        }

        /** Advances cursor by custom spacing without registering an item. */
        public Row space(float amount) {
            cursor += amount;
            return this;
        }

        /** Y coordinate centered vertically in this row for an element of height {@code elementH}. */
        public float centerY(float elementH) {
            return HudAlign.centerY(y, height, elementH);
        }

        /** Y coordinate for optical vertical centering of MSDF text of size {@code fontSize}. */
        public float centerTextY(float fontSize) {
            return HudAlign.centerTextY(y, height, fontSize);
        }

        /** Row height. */
        public float height() {
            return height;
        }

        /** Row top Y. */
        public float y() {
            return y;
        }

        /** Row vertical center. */
        public float midY() {
            return y + height * 0.5f;
        }

        /** Total span width from row start to current cursor (excluding trailing gap, plus end padding). */
        public float totalWidth(float endPadding) {
            return Math.max(0.0f, (cursor > startX ? (cursor - gap) : cursor) - (startX - endPadding) + endPadding);
        }
    }
}
