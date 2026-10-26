package dev.linkenvisuals.ui.linkmenu;

import dev.linkenvisuals.LinkenVisuals;
import dev.linkenvisuals.i18n.I18n;
import dev.linkenvisuals.media.MediaWatcher;
import dev.linkenvisuals.render.Fonts;
import dev.linkenvisuals.render.GlyphAtlas;
import dev.linkenvisuals.render.IconPipeline;
import dev.linkenvisuals.render.MsdfText;
import dev.linkenvisuals.render.RoundedRectPipeline;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.SharedConstants;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.Identifier;
import net.minecraft.util.Util;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * Visual chrome shared by the LINKEN main menu (and later by M4 screens):
 * palette tokens, background art, wordmark, clock, button material, socials,
 * media card and footer.
 *
 * <p>All coordinates and sizes are design units of the 1920x1080 canvas.
 */
public final class LinkenMenuChrome {
    public static final int TEXT = 0xEEF2EF;
    public static final int TEXT2 = 0xDFE5E1;
    public static final int TEXT3 = 0xC3CCC7;
    public static final int TEXT4 = 0xA8B1AC;
    public static final int MUTED = 0x8B968F;
    public static final int MUTED2 = 0x78837D;
    public static final int MUTED3 = 0x5D6862;
    public static final int MUTED4 = 0x4C5651;
    public static final int INK = 0x0B120E;
    public static final int ACCENT = 0x7EDFB4;
    public static final int DANGER = 0xE08C8C;

    public static final int ICON_PERSON = 'P';
    public static final int ICON_PEOPLE = 'N';
    public static final int ICON_GLOBE = 'E';
    public static final int ICON_GEAR = 'O';
    public static final int ICON_POWER = 'Q';
    public static final int ICON_DISCORD = 0xF144;
    public static final int ICON_TELEGRAM = 0xF1B6;

    /** Donor {@code icons2} glyphs used by the main menu. */
    public static final int MENU_ICON_PERSON = 'A';
    public static final int MENU_ICON_GEAR = 'B';
    public static final int MENU_ICON_GLOBE = 'C';
    public static final int MENU_ICON_POWER = 'D';
    public static final int MENU_ICON_TELEGRAM = 'E';
    public static final int MENU_ICON_DISCORD = 'F';
    public static final int MENU_ICON_PEOPLE = 'G';
    public static final int MENU_ICON_CODE = 'H';

    private static final Identifier BACKGROUND = LinkenVisuals.id("textures/linkmenu/bg-tower.png");
    private static final Identifier LV_LOGO = LinkenVisuals.id("textures/gui/linken_logo.png");
    private static final Identifier LV_LOGO_SMALL = LinkenVisuals.id("textures/gui/lv_logo.png");
    private static final Identifier MUSIC_PLAY = LinkenVisuals.id("textures/linkmenu/music/play.png");
    private static final Identifier MUSIC_PAUSE = LinkenVisuals.id("textures/linkmenu/music/pause.png");
    private static final float BACKGROUND_WIDTH = 1270.0f;
    private static final float BACKGROUND_HEIGHT = 999.0f;

    private static final DateTimeFormatter DATE_DAY = DateTimeFormatter.ofPattern("EEE");
    private static final DateTimeFormatter DATE_REST = DateTimeFormatter.ofPattern("d MMMM");

    private static long clockSecond = -1L;
    private static String clockHour = "";
    private static String clockMinute = "";
    private static String clockDay = "";
    private static String clockRest = "";
    private static String modVersion;

    private LinkenMenuChrome() {
    }

    public static int rgba(int rgb, int alpha, float fade) {
        return (Math.max(0, Math.min(255, Math.round(alpha * fade))) << 24) | (rgb & 0xFFFFFF);
    }

    public static int rgba(int rgb, float fade) {
        return rgba(rgb, 255, fade);
    }

    /**
     * Alpha-first overload for call sites that pass an explicit 0..255 opacity.
     * Without it an int literal binds to the {@code float fade} overload and an
     * alpha of 26 would be treated as a 26x fade, clamping the colour to opaque.
     */
    public static int rgba(int rgb, int alpha) {
        return rgba(rgb, alpha, 1.0f);
    }

    // ------------------------------------------------------------------ background

    public static void drawBackground(DrawContext context, LinkenMenuLayout.Metrics layout,
                                      float parallaxX, float parallaxY, float fade) {
        float cover = Math.max(layout.width() / BACKGROUND_WIDTH, layout.height() / BACKGROUND_HEIGHT) * 1.07f;
        float drawWidth = BACKGROUND_WIDTH * cover;
        float drawHeight = BACKGROUND_HEIGHT * cover;
        float offsetX = -parallaxX * Math.min((drawWidth - layout.width()) * 0.5f, 42.0f);
        float offsetY = -parallaxY * Math.min((drawHeight - layout.height()) * 0.5f, 26.0f);
        float x = (layout.width() - drawWidth) * 0.5f + offsetX;
        float y = (layout.height() - drawHeight) * 0.5f + offsetY;

        // Keep the donor's sub-pixel placement: the art is a single float-sized
        // quad there, while DrawContext takes integer texture rects. Translate
        // by the fractional origin and scale the integer rect back to the exact
        // float size, so cover cropping does not shift by a pixel.
        int texWidth = Math.round(BACKGROUND_WIDTH);
        int texHeight = Math.round(BACKGROUND_HEIGHT);
        int drawW = Math.max(1, Math.round(drawWidth));
        int drawH = Math.max(1, Math.round(drawHeight));
        context.getMatrices().pushMatrix();
        context.getMatrices().translate(x, y);
        context.getMatrices().scale(drawWidth / drawW, drawHeight / drawH);
        context.drawTexture(RenderPipelines.GUI_TEXTURED, BACKGROUND,
            0, 0, 0.0f, 0.0f, drawW, drawH,
            texWidth, texHeight, texWidth, texHeight, 0xFFFFFFFF);
        context.getMatrices().popMatrix();

        // Donor scrim is one horizontal gradient: rgba(6,9,8,0.5) on the left
        // fading to fully transparent on the right. One shader quad matches the
        // donor's vertex-colour gradient and avoids the 48 visible slices.
        RoundedRectPipeline.drawHorizontalGradient(context, 0.0f, 0.0f,
            layout.width(), layout.height(), 0.0f,
            rgba(0x060908, 128, fade), rgba(0x060908, 0, fade));
    }

    // ------------------------------------------------------------------ header

    public static void drawWordmark(DrawContext context, LinkenMenuLayout.Metrics layout, float fade) {
        GlyphAtlas font = LinkenFonts.bodyMedium();
        float size = 23.0f;
        String word = "linken";
        // Donor draws this header with drawSpaced(-0.035 em). The per-glyph
        // path also avoids the 0.025 em thickness advance that MsdfText adds
        // to whole strings, keeping the donor's 71 px wordmark width.
        float wordWidth = spacedWidth(font, word, size, 0.0f);
        float dotWidth = textWidth(font, ".", size);
        float total = wordWidth + dotWidth;
        float x = (layout.width() - total) * 0.5f;
        float y = 44.0f;
        drawSpaced(context, font, word, x, y, size, 0.0f, rgba(TEXT, fade));
        drawText(context, font, ".", x + wordWidth, y, size, rgba(ACCENT, fade));
    }

    /** Linken logo used by the menu, HUD and GUI. */
    public static void drawLogo(DrawContext context, float x, float y, float size, float fade) {
        Identifier texture = LV_LOGO;
        int texSize = 128;
        context.drawTexture(RenderPipelines.GUI_TEXTURED, texture,
            Math.round(x), Math.round(y), 0.0f, 0.0f,
            Math.round(size), Math.round(size), texSize, texSize, texSize, texSize,
            rgba(0xFFFFFF, fade));
    }

    /** Legacy corner wordmark used by the sub-screens. */
    public static void drawWordmark(DrawContext context, float fade) {
        GlyphAtlas font = LinkenFonts.bodyMedium();
        float size = 27.0f;
        float x = LinkenMenuLayout.MARGIN;
        float y = 36.0f;
        String word = "linken";
        drawText(context, font, word, x, y, size, rgba(TEXT, fade));
        float wordWidth = textWidth(font, word, size);
        drawText(context, font, ".", x + wordWidth, y, size, rgba(ACCENT, fade));
        drawSpaced(context, LinkenFonts.headingLight(), I18n.get("linkenvisuals.linkmenu.visuals"),
            x, y + 29.0f, 9.0f, 2.1f, rgba(MUTED3, fade));
    }

    public static void drawClock(DrawContext context, LinkenMenuLayout.Metrics layout, float fade) {
        updateClock();
        float right = layout.clock().right();
        float size = 44.0f;
        float gap = size * 0.15f;
        float dot = size * 0.10f;
        float hhWidth = textWidth(LinkenFonts.mono(), clockHour, size);
        float mmWidth = textWidth(LinkenFonts.mono(), clockMinute, size);
        float total = hhWidth + gap + dot + gap + mmWidth;
        float x = right - total;
        float y = 38.0f;
        drawText(context, LinkenFonts.mono(), clockHour, x, y, size, rgba(TEXT, fade));
        float cx = x + hhWidth + gap;
        roundedFill(context, cx, y + size * 0.52f, dot, dot, dot * 0.5f, rgba(TEXT, fade), 1.0f);
        roundedFill(context, cx, y + size * 0.74f, dot, dot, dot * 0.5f, rgba(TEXT, fade), 1.0f);
        drawText(context, LinkenFonts.mono(), clockMinute, cx + dot + gap, y, size, rgba(TEXT, fade));

        float dateY = y + size + 6.0f;
        // Donor draws the weekday muted and the date brighter, both with
        // 0.06 em tracking. Right-align the two runs as one line.
        float tracking = 0.06f * 12.5f;
        float dayWidth = spacedWidth(LinkenFonts.body(), clockDay, 12.5f, tracking);
        float restWidth = spacedWidth(LinkenFonts.body(), clockRest, 12.5f, tracking);
        drawSpaced(context, LinkenFonts.body(), clockDay, right - dayWidth - restWidth,
            dateY, 12.5f, tracking, rgba(MUTED2, fade));
        drawSpaced(context, LinkenFonts.body(), clockRest, right - restWidth,
            dateY, 12.5f, tracking, rgba(TEXT3, fade));
    }

    private static void updateClock() {
        long second = System.currentTimeMillis() / 1000L;
        if (second == clockSecond) {
            return;
        }
        clockSecond = second;
        Locale locale = currentLocale();
        LocalTime time = LocalTime.now();
        clockHour = String.format(locale, "%02d", time.getHour());
        clockMinute = String.format(locale, "%02d", time.getMinute());
        LocalDate date = LocalDate.now();
        String day = date.format(DATE_DAY.withLocale(locale));
        clockDay = (day.isEmpty() ? day : day.substring(0, 1).toUpperCase(locale) + day.substring(1)) + ", ";
        clockRest = date.format(DATE_REST.withLocale(locale));
    }

    private static Locale currentLocale() {
        String language = I18n.getEffectiveLanguage();
        if (language.startsWith("ru")) {
            return Locale.forLanguageTag("ru");
        }
        if (language.startsWith("uk")) {
            return Locale.forLanguageTag("uk");
        }
        return Locale.ENGLISH;
    }

    // ------------------------------------------------------------------ buttons

    public static void drawButton(DrawContext context, LinkenMenuLayout.Rect rect, boolean primary,
                                  int icon, String label, float hover, boolean danger, boolean centered,
                                  float fade, float uiScale) {
        float x = rect.x();
        float y = rect.y() - hover;
        float width = rect.width();
        float height = rect.height();
        float radius = 8.0f;

        if (primary) {
            roundedFill(context, x + 4.0f, y + height - 6.0f, width - 8.0f, 12.0f, radius,
                rgba(ACCENT, Math.round(60.0f + 40.0f * hover), fade), uiScale);
            int fill = mix(ACCENT, 0xFFFFFF, hover * 0.07f);
            roundedFill(context, x, y, width, height, radius, rgba(fill, fade), uiScale);
            context.fill(Math.round(x + radius), Math.round(y + 1.0f), Math.round(x + width - radius),
                Math.round(y + 2.0f), rgba(0xFFFFFF, 90, fade));
        } else {
            drawSecondary(context, x, y, width, height, radius, hover, danger, fade, uiScale);
        }

        float iconSize = 15.0f;
        float centerY = y + height * 0.5f;
        float labelSize = 12.5f;
        GlyphAtlas labelFont = primary ? LinkenFonts.heading() : LinkenFonts.headingLight();
        float labelWidth = textWidth(labelFont, label, labelSize);
        float contentWidth = iconSize + 9.0f + labelWidth;
        float iconX;
        float labelX;
        if (centered) {
            iconX = x + (width - contentWidth) * 0.5f;
            labelX = iconX + iconSize + 9.0f;
        } else {
            iconX = x + 16.0f;
            labelX = iconX + iconSize + 12.0f;
        }

        int iconColor = primary ? INK : MUTED;
        int labelColor = primary ? INK : (danger ? DANGER : TEXT2);
        GlyphAtlas iconAtlas = LinkenFonts.icons2();
        if (iconAtlas != null) {
            IconPipeline.draw(context, iconAtlas, icon, iconX + iconSize * 0.5f, centerY,
                iconSize, rgba(iconColor, fade));
        }
        drawOn(context, labelFont, label, labelX, centerY, labelSize, rgba(labelColor, fade));
    }

    public static void drawSecondary(DrawContext context, float x, float y, float width, float height,
                                     float radius, float hover, boolean danger, float fade, float uiScale) {
        RoundedRectPipeline.drawVerticalGradient(context, x, y, width, height, radius * uiScale,
            rgba(0x181E1B, 217, fade), rgba(0x0F1311, 217, fade));
        context.fill(Math.round(x + radius), Math.round(y + 1.0f), Math.round(x + width - radius),
            Math.round(y + 2.0f), rgba(0xFFFFFF, 13, fade));
        int border = danger ? mix(0xFFFFFF, DANGER, hover * 0.8f) : 0xFFFFFF;
        int borderAlpha = danger ? Math.round(23.0f + 117.0f * hover) : Math.round(23.0f + 38.0f * hover);
        roundedOutline(context, x, y, width, height, radius, 1.0f,
            rgba(border, borderAlpha, fade), uiScale);
    }

    // ------------------------------------------------------------------ socials

    public static void drawSocialTile(DrawContext context, LinkenMenuLayout.Rect rect, int icon,
                                      String label, float hover, float fade, float uiScale) {
        float y = rect.y() - hover * 2.0f;
        drawSecondary(context, rect.x(), y, rect.width(), rect.height(), 8.0f, hover, false, fade, uiScale);
        int color = rgba(mix(MUTED, ACCENT, hover), fade);
        if (icon > 0) {
            GlyphAtlas menuIcons = LinkenFonts.icons2();
            if (menuIcons != null) {
                IconPipeline.draw(context, menuIcons, icon,
                    rect.x() + rect.width() * 0.5f, y + rect.height() * 0.5f, 17.0f, color);
            }
            return;
        }
        if ("LV".equals(label)) {
            GlyphAtlas font = LinkenFonts.heading();
            float size = 12.0f;
            float width = textWidth(font, label, size);
            float x = rect.x() + (rect.width() - width) * 0.5f;
            float centerY = y + rect.height() * 0.5f;
            drawOn(context, font, "L", x, centerY, size, rgba(TEXT2, fade));
            drawOn(context, font, "V", x + textWidth(font, "L", size), centerY, size, rgba(ACCENT, fade));
            return;
        }
        GlyphAtlas font = LinkenFonts.mono();
        float size = 13.0f;
        drawCentered(context, font, label, rect.x() + rect.width() * 0.5f,
            y + rect.height() * 0.5f, size, color);
    }

    // ------------------------------------------------------------------ media

    public static void drawMusic(DrawContext context, LinkenMenuLayout.Rect card, MediaWatcher.Track track,
                                 float fade, float uiScale) {
        drawMusic(context, card, track, fade, uiScale, 0.0f);
    }

    public static void drawMusic(DrawContext context, LinkenMenuLayout.Rect card, MediaWatcher.Track track,
                                 float fade, float uiScale, float coverHover) {
        boolean present = track != null && track.present();
        float bottom = card.bottom();
        float coverSize = 34.0f;
        float progressY = bottom - 10.5f;
        float rowY = progressY - 12.0f - coverSize;
        float labelY = rowY - 9.0f - 10.5f;

        String now = I18n.get(present
            ? "linkenvisuals.linkmenu.media.now_playing"
            : "linkenvisuals.linkmenu.media.none");
        float nowWidth = textWidth(LinkenFonts.body(), now, 10.5f);
        drawSpaced(context, LinkenFonts.body(), now,
            card.x() + (card.width() - nowWidth) * 0.5f, labelY, 10.5f, 0.84f, rgba(MUTED2, fade));

        RoundedRectPipeline.drawVerticalGradient(context, card.x(), rowY, coverSize, coverSize,
            coverSize * 0.5f * uiScale, rgba(0x37302A, 255, fade), rgba(0x12100E, 255, fade));
        boolean playing = present && track.playing();
        Identifier icon = playing ? MUSIC_PAUSE : MUSIC_PLAY;
        int iconColor = mix(MUTED, TEXT, playing ? 1.0f : coverHover);
        context.drawTexture(RenderPipelines.GUI_TEXTURED, icon,
            Math.round(card.x() + (coverSize - 9.0f) * 0.5f), Math.round(rowY + (coverSize - 9.0f) * 0.5f),
            0.0f, 0.0f, 9, 9, 9, 9, rgba(iconColor, fade));

        float textX = card.x() + coverSize + 12.0f;
        float availableWidth = card.width() - (textX - card.x());
        float rowCenter = rowY + coverSize * 0.5f;
        if (present) {
            // Donor line: "TITLE by ARTIST", title/artist in the heading face.
            String title = ellipsize(LinkenFonts.heading(), track.title(), availableWidth, 13.0f);
            drawOn(context, LinkenFonts.heading(), title, textX, rowCenter, 13.0f,
                rgba(TEXT, fade));
            float byX = textX + textWidth(LinkenFonts.heading(), title, 13.0f) + 4.0f;
            float byWidth = textWidth(LinkenFonts.headingLight(), "by ", 11.0f);
            String artist = ellipsize(LinkenFonts.heading(), track.artist(),
                Math.max(0.0f, card.right() - byX - byWidth), 13.0f);
            drawOn(context, LinkenFonts.headingLight(), "by", byX, rowCenter + 2.0f, 11.0f,
                rgba(MUTED2, fade));
            drawOn(context, LinkenFonts.heading(), artist, byX + byWidth, rowCenter, 13.0f,
                rgba(TEXT, fade));
        } else {
            String hint = ellipsize(LinkenFonts.body(), I18n.get("linkenvisuals.linkmenu.media.hint"),
                availableWidth, 10.5f);
            drawOn(context, LinkenFonts.body(), hint, textX, rowCenter, 10.5f,
                rgba(MUTED2, fade));
        }

        String elapsed = "0:00";
        String remain = present ? "-" + fmtTime(Math.max(0L, track.length() - (long) track.position())) : "--:--";
        float progress = 0.0f;
        if (present && track.length() > 0L) {
            float position = Math.max(0.0f, Math.min(track.position(), track.length()));
            elapsed = fmtTime((long) position);
            progress = position / track.length();
        }
        float leftWidth = textWidth(LinkenFonts.mono(), elapsed, 10.5f);
        float rightWidth = textWidth(LinkenFonts.mono(), remain, 10.5f);
        drawText(context, LinkenFonts.mono(), elapsed, card.x(), progressY, 10.5f, rgba(MUTED, fade));
        drawRightText(context, LinkenFonts.mono(), remain, card.right(), progressY, 10.5f, rgba(MUTED, fade));
        float barX = card.x() + leftWidth + 10.0f;
        float barW = card.width() - leftWidth - rightWidth - 20.0f;
        if (barW > 4.0f) {
            roundedFill(context, barX, progressY + 5.0f, barW, 2.0f, 1.0f,
                rgba(0xFFFFFF, 41, fade), uiScale);
            if (progress > 0.001f) {
                roundedFill(context, barX, progressY + 5.0f, barW * progress, 2.0f, 1.0f,
                    rgba(TEXT, fade), uiScale);
            }
        }
    }

    // ------------------------------------------------------------------ footer

    public static void drawFooter(DrawContext context, LinkenMenuLayout.Metrics layout, float fade) {
        float x = layout.footer().x();
        float y = layout.footer().y();
        float width = layout.footer().width();
        String first = ellipsize(LinkenFonts.body(),
            I18n.get("linkenvisuals.linkmenu.disclaimer1"), width, 11.0f);
        String second = ellipsize(LinkenFonts.body(),
            I18n.get("linkenvisuals.linkmenu.disclaimer2", SharedConstants.getGameVersion().name(), version()),
            width, 11.0f);
        drawText(context, LinkenFonts.body(), first, x, y, 11.0f, rgba(MUTED4, fade));
        drawText(context, LinkenFonts.body(), second, x, y + LinkenMenuLayout.DISCLAIMER_LINE_HEIGHT,
            11.0f, rgba(MUTED4, fade));
    }

    // ------------------------------------------------------------------ text helpers

    public static void drawText(DrawContext context, GlyphAtlas font, String text, float x, float top,
                                float size, int color) {
        GlyphAtlas resolved = resolve(font, text);
        if (resolved != null) {
            MsdfText.draw(context, resolved, text, x, top, size, color);
            return;
        }
        MinecraftClient client = MinecraftClient.getInstance();
        if (client != null) {
            context.drawTextWithShadow(client.textRenderer, text, Math.round(x), Math.round(top + size * 0.2f), color);
        }
    }

    public static void drawOn(DrawContext context, GlyphAtlas font, String text, float x, float centerY,
                              float size, int color) {
        GlyphAtlas resolved = resolve(font, text);
        if (resolved != null) {
            MsdfText.drawOn(context, resolved, text, x, centerY, size, color);
            return;
        }
        MinecraftClient client = MinecraftClient.getInstance();
        if (client != null) {
            context.drawTextWithShadow(client.textRenderer, text, Math.round(x),
                Math.round(centerY - client.textRenderer.fontHeight * 0.5f), color);
        }
    }

    public static void drawCentered(DrawContext context, GlyphAtlas font, String text, float centerX,
                                    float centerY, float size, int color) {
        GlyphAtlas resolved = resolve(font, text);
        if (resolved != null) {
            MsdfText.drawCenteredOn(context, resolved, text, centerX, centerY, size, color);
            return;
        }
        MinecraftClient client = MinecraftClient.getInstance();
        if (client != null) {
            context.drawCenteredTextWithShadow(client.textRenderer, text, Math.round(centerX),
                Math.round(centerY - client.textRenderer.fontHeight * 0.5f), color);
        }
    }

    public static void drawRightText(DrawContext context, GlyphAtlas font, String text, float rightX,
                                     float top, float size, int color) {
        GlyphAtlas resolved = resolve(font, text);
        if (resolved != null) {
            MsdfText.drawRight(context, resolved, text, rightX, top, size, color);
            return;
        }
        MinecraftClient client = MinecraftClient.getInstance();
        if (client != null) {
            context.drawTextWithShadow(client.textRenderer, text,
                Math.round(rightX - client.textRenderer.getWidth(text)), Math.round(top + size * 0.2f), color);
        }
    }

    public static void drawSpaced(DrawContext context, GlyphAtlas font, String text, float x, float top,
                                  float size, float tracking, int color) {
        GlyphAtlas resolved = resolve(font, text);
        if (resolved == null) {
            drawText(context, font, text, x, top, size, color);
            return;
        }
        float cursor = x;
        for (int offset = 0; offset < text.length(); ) {
            int codepoint = text.codePointAt(offset);
            String glyph = new String(Character.toChars(codepoint));
            MsdfText.draw(context, resolved, glyph, cursor, top, size, color);
            cursor += resolved.width(glyph, size) + tracking;
            offset += Character.charCount(codepoint);
        }
    }

    public static float textWidth(GlyphAtlas font, String text, float size) {
        GlyphAtlas resolved = resolve(font, text);
        if (resolved != null) {
            return resolved.width(text, size);
        }
        MinecraftClient client = MinecraftClient.getInstance();
        return client != null ? client.textRenderer.getWidth(text) : text.length() * size * 0.5f;
    }

    /** Width of {@link #drawSpaced}: per-glyph advances plus tracking, no kerning. */
    private static float spacedWidth(GlyphAtlas font, String text, float size, float tracking) {
        if (text == null || text.isEmpty()) {
            return 0.0f;
        }
        GlyphAtlas resolved = resolve(font, text);
        if (resolved == null) {
            return textWidth(font, text, size);
        }
        float width = 0.0f;
        int count = 0;
        for (int offset = 0; offset < text.length(); ) {
            int codepoint = text.codePointAt(offset);
            width += resolved.width(new String(Character.toChars(codepoint)), size);
            count++;
            offset += Character.charCount(codepoint);
        }
        return width + tracking * Math.max(0, count - 1);
    }

    /** Shrinks a headline just enough to fit, without dropping any words. */
    public static float fitSize(GlyphAtlas font, String text, float maxWidth, float preferred, float minimum) {
        if (text == null || text.isEmpty() || maxWidth <= 0.0f) {
            return preferred;
        }
        float width = textWidth(font, text, preferred);
        if (width <= maxWidth) {
            return preferred;
        }
        return Math.max(minimum, preferred * maxWidth / width);
    }

    public static String ellipsize(GlyphAtlas font, String text, float maxWidth, float size) {
        if (text == null || text.isEmpty() || maxWidth <= 0.0f) {
            return text == null ? "" : text;
        }
        if (textWidth(font, text, size) <= maxWidth) {
            return text;
        }
        String suffix = textWidth(font, "...", size) <= maxWidth ? "..." : "";
        int end = text.length();
        while (end > 0) {
            end = text.offsetByCodePoints(end, -1);
            String candidate = text.substring(0, end) + suffix;
            if (textWidth(font, candidate, size) <= maxWidth) {
                return candidate;
            }
        }
        return suffix;
    }

    private static GlyphAtlas resolve(GlyphAtlas preferred, String text) {
        if (preferred != null && preferred.canRender(text)) {
            return preferred;
        }
        GlyphAtlas title = Fonts.titleFont();
        if (title != null && title.canRender(text)) {
            return title;
        }
        GlyphAtlas regular = Fonts.textFont();
        if (regular != null && regular.canRender(text)) {
            return regular;
        }
        return null;
    }

    // ------------------------------------------------------------------ material helpers

    public static void roundedFill(DrawContext context, float x, float y, float width, float height,
                                   float radius, int color, float uiScale) {
        RoundedRectPipeline.draw(context, x, y, width, height, radius(radius, uiScale), color);
    }

    public static void roundedOutline(DrawContext context, float x, float y, float width, float height,
                                      float radius, float thickness, int color, float uiScale) {
        RoundedRectPipeline.drawOutline(context, x, y, width, height, radius(radius, uiScale),
            thickness(thickness, uiScale), color);
    }

    /**
     * RoundedRectPipeline takes radius/thickness in GUI units and multiplies
     * them by the window GUI scale internally. The LINKEN canvas is scaled by
     * {@code uiScale} through the matrix, so passing design units multiplied by
     * that same factor keeps the radius constant at every GUI scale.
     */
    private static float radius(float designRadius, float uiScale) {
        return designRadius * uiScale;
    }

    private static float thickness(float designThickness, float uiScale) {
        return designThickness * uiScale;
    }

    // ------------------------------------------------------------------ misc

    public static String licenseWord() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.getSession() == null) {
            return I18n.get("linkenvisuals.linkmenu.account.offline");
        }
        // 1.21.11 no longer exposes a Session.AccountType; an offline/demo
        // session carries the placeholder token "0" (or no token at all).
        String token = client.getSession().getAccessToken();
        boolean licensed = token != null && !token.isBlank() && !"0".equals(token);
        return I18n.get(licensed
            ? "linkenvisuals.linkmenu.account.licensed"
            : "linkenvisuals.linkmenu.account.offline");
    }

    public static String username() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.getSession() == null || client.getSession().getUsername() == null) {
            return "player";
        }
        String name = client.getSession().getUsername();
        return name.isBlank() ? "player" : name;
    }

    public static String version() {
        if (modVersion == null) {
            modVersion = FabricLoader.getInstance()
                .getModContainer(LinkenVisuals.MOD_ID)
                .map(mod -> mod.getMetadata().getVersion().getFriendlyString())
                .orElse("dev");
        }
        return modVersion;
    }

    public static String fmtTime(long millis) {
        long seconds = Math.max(0L, millis) / 1000L;
        return seconds / 60L + ":" + (seconds % 60L < 10 ? "0" : "") + seconds % 60L;
    }

    public static int mix(int from, int to, float amount) {
        float t = Math.max(0.0f, Math.min(1.0f, amount));
        int r = Math.round(((from >> 16) & 0xFF) + ((((to >> 16) & 0xFF) - ((from >> 16) & 0xFF)) * t));
        int g = Math.round(((from >> 8) & 0xFF) + ((((to >> 8) & 0xFF) - ((from >> 8) & 0xFF)) * t));
        int b = Math.round((from & 0xFF) + (((to & 0xFF) - (from & 0xFF)) * t));
        return r << 16 | g << 8 | b;
    }

    public static void openUrl(String url) {
        try {
            Util.getOperatingSystem().open(url);
        } catch (RuntimeException failure) {
            LinkenVisuals.LOGGER.warn("[Visuals Modification] Failed to open menu URL '{}': {}", url, failure.getMessage());
        }
    }
}
