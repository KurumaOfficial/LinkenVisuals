package ru.method.linkenvisuals.utils.render.fonts;

import dev.linkenvisuals.render.GlyphAtlas;
import dev.linkenvisuals.render.MsdfText;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.Identifier;
import ru.method.linkenvisuals.utils.render.Render2D;

import java.awt.Color;

public class Font {
    private final String name;
    private GlyphAtlas atlas;

    public Font(String name) {
        this.name = name;
    }

    public GlyphAtlas atlas() {
        if (atlas == null) {
            try {
                atlas = GlyphAtlas.load(
                    Identifier.of("linkenvisuals", "fonts/" + name + ".json"),
                    Identifier.of("linkenvisuals", "fonts/" + name + ".png")
                );
            } catch (Throwable t) {
                // ignore
            }
        }
        return atlas;
    }

    public Instance getFont(float size) {
        return new Instance(this, size);
    }

    public float getWidth(String text, float size) {
        if (text == null || text.isEmpty()) return 0f;
        GlyphAtlas a = atlas();
        if (a == null) return text.length() * size * 0.55f;
        return a.width(text, size);
    }

    public float getHeight(float size) {
        GlyphAtlas a = atlas();
        if (a == null) return size;
        // Donor MsdfFont.getHeight: (ascender + |descender|) * 1.05.
        return (a.ascender() + Math.abs(a.descender())) * size * 1.05f;
    }

    public float getLineHeight(float size) {
        GlyphAtlas a = atlas();
        if (a == null) return size * 1.15f;
        return a.lineHeight() * size;
    }

    public float getAscender(float size) {
        GlyphAtlas a = atlas();
        if (a == null) return size * 0.95f;
        return a.ascender() * size;
    }

    public void draw(DrawContext context, String text, float x, float y, float size, int argb) {
        if (context == null || text == null || text.isEmpty()) return;
        GlyphAtlas a = atlas();
        if (a != null) {
            MsdfText.draw(context, a, text, x, y, size, argb);
        } else {
            Render2D.drawFont(context, getFont(size), text, x, y, new Color(argb, true));
        }
    }

    public void draw(Object stackOrCtx, String text, float x, float y, int argb, float size) {
        if (text == null || text.isEmpty()) return;
        Render2D.drawFont(stackOrCtx, getFont(size), text, x, y, new Color(argb, true));
    }

    public void draw(Object stackOrCtx, String text, float x, float y, float size, int argb) {
        if (text == null || text.isEmpty()) return;
        Render2D.drawFont(stackOrCtx, getFont(size), text, x, y, new Color(argb, true));
    }
}
