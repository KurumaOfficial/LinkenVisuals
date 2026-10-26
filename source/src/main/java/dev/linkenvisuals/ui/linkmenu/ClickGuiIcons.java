package dev.linkenvisuals.ui.linkmenu;

import dev.linkenvisuals.LinkenVisuals;
import dev.linkenvisuals.render.GlyphAtlas;
import dev.linkenvisuals.render.IconPipeline;
import dev.linkenvisuals.render.MsdfText;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.Identifier;

public final class ClickGuiIcons {
    private static final Identifier ICON_SOUND = LinkenVisuals.id("textures/gui/icons/icon_sound.png");
    private static final Identifier ICON_MUTE = LinkenVisuals.id("textures/gui/icons/icon_mute.png");

    private ClickGuiIcons() {
    }

    private static void fa(DrawContext context, char codepoint, float cx, float cy, float size, int color) {
        GlyphAtlas atlas = LinkenFonts.iconsFa();
        if (atlas != null && atlas.glyph(codepoint) != null) {
            MsdfText.drawCenteredOn(context, atlas, String.valueOf(codepoint), cx, cy, size, color);
        }
    }

    public static void user(DrawContext context, float cx, float cy, float size, int color) {
        fa(context, '\uF007', cx, cy, size, color);
    }

    public static void musicNote(DrawContext context, float cx, float cy, float size, int color) {
        fa(context, '\uF001', cx, cy, size, color);
    }

    public static void monitor(DrawContext context, float cx, float cy, float size, int color) {
        fa(context, '\uF108', cx, cy, size, color);
    }

    public static void keyboard(DrawContext context, float cx, float cy, float size, int color) {
        fa(context, '\uF11C', cx, cy, size, color);
    }

    public static void globe(DrawContext context, float cx, float cy, float size, int color) {
        fa(context, '\uF0AC', cx, cy, size, color);
    }

    public static void chat(DrawContext context, float cx, float cy, float size, int color) {
        comment(context, cx, cy, size, color);
    }

    public static void layers(DrawContext context, float cx, float cy, float size, int color) {
        fa(context, '\uF5FD', cx, cy, size, color);
    }

    public static void accessibility(DrawContext context, float cx, float cy, float size, int color) {
        fa(context, '\uF29A', cx, cy, size, color);
    }

    public static void database(DrawContext context, float cx, float cy, float size, int color) {
        fa(context, '\uF1C0', cx, cy, size, color);
    }

    public static void info(DrawContext context, float cx, float cy, float size, int color) {
        fa(context, '\uF05A', cx, cy, size, color);
    }

    public static void check(DrawContext context, float cx, float cy, float size, int color) {
        fa(context, '\uF00C', cx, cy, size * 0.92f, color);
    }

    public static void gear(DrawContext context, float cx, float cy, float size, int color) {
        fa(context, '\uF013', cx, cy, size, color);
    }

    public static void chartBar(DrawContext context, float cx, float cy, float size, int color) {
        fa(context, '\uF080', cx, cy, size, color);
    }

    public static void trophy(DrawContext context, float cx, float cy, float size, int color) {
        fa(context, '\uF091', cx, cy, size, color);
    }

    public static void play(DrawContext context, float cx, float cy, float size, int color) {
        GlyphAtlas atlas = LinkenFonts.mediaPlayer();
        if (atlas != null && atlas.glyph('C') != null) {
            MsdfText.drawCenteredOn(context, atlas, "C", cx + size * 0.12f, cy, size, color);
        } else {
            fa(context, '\uF04B', cx, cy, size * 0.9f, color);
        }
    }

    public static void pause(DrawContext context, float cx, float cy, float size, int color) {
        GlyphAtlas atlas = LinkenFonts.mediaPlayer();
        if (atlas != null && atlas.glyph('D') != null) {
            MsdfText.drawCenteredOn(context, atlas, "D", cx, cy, size, color);
        } else {
            fa(context, '\uF04C', cx, cy, size * 0.9f, color);
        }
    }

    public static void backward(DrawContext context, float cx, float cy, float size, int color) {
        GlyphAtlas atlas = LinkenFonts.mediaPlayer();
        if (atlas != null && atlas.glyph('A') != null) {
            MsdfText.drawCenteredOn(context, atlas, "A", cx, cy, size, color);
        } else {
            fa(context, '\uF048', cx, cy, size * 0.88f, color);
        }
    }

    public static void forward(DrawContext context, float cx, float cy, float size, int color) {
        GlyphAtlas atlas = LinkenFonts.mediaPlayer();
        if (atlas != null && atlas.glyph('B') != null) {
            MsdfText.drawCenteredOn(context, atlas, "B", cx, cy, size, color);
        } else {
            fa(context, '\uF051', cx, cy, size * 0.88f, color);
        }
    }

    public static void volumeMute(DrawContext context, float cx, float cy, float size, int color) {
        int isz = Math.round(size);
        context.drawTexture(RenderPipelines.GUI_TEXTURED, ICON_MUTE,
            Math.round(cx - size * 0.5f), Math.round(cy - size * 0.5f), 0.0f, 0.0f, isz, isz, isz, isz, color);
    }

    public static void volumeUp(DrawContext context, float cx, float cy, float size, int color) {
        int isz = Math.round(size);
        context.drawTexture(RenderPipelines.GUI_TEXTURED, ICON_SOUND,
            Math.round(cx - size * 0.5f), Math.round(cy - size * 0.5f), 0.0f, 0.0f, isz, isz, isz, isz, color);
    }

    public static void volumeDown(DrawContext context, float cx, float cy, float size, int color) {
        volumeUp(context, cx, cy, size, color);
    }

    public static void palette(DrawContext context, float cx, float cy, float size, int color) {
        fa(context, '\uF53F', cx, cy, size, color);
    }

    public static void sliders(DrawContext context, float cx, float cy, float size, int color) {
        fa(context, '\uF1DE', cx, cy, size, color);
    }

    public static void xmark(DrawContext context, float cx, float cy, float size, int color) {
        fa(context, '\uF00D', cx, cy, size, color);
    }

    public static void signOut(DrawContext context, float cx, float cy, float size, int color) {
        fa(context, '\uF08B', cx, cy, size * 0.95f, color);
    }

    public static void comment(DrawContext context, float cx, float cy, float size, int color) {
        fa(context, '\uF075', cx, cy, size, color);
    }
}
