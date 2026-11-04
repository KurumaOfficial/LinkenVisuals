package ru.method.linkenvisuals.screen.clickgui.redesign;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.texture.NativeImageBackedTexture;
import org.joml.Matrix3x2fStack;
import net.minecraft.util.math.MathHelper;
import ru.method.linkenvisuals.LinkenVisuals;
import ru.method.linkenvisuals.hud.impl.DynamicIsland;
import ru.method.linkenvisuals.hud.impl.island.DynamicIslandMedia;
import ru.method.linkenvisuals.hud.impl.island.DynamicIslandMediaHolder;
import ru.method.linkenvisuals.utils.animations.Animation;
import ru.method.linkenvisuals.utils.animations.Easing;
import ru.method.linkenvisuals.utils.mediaplayer.SystemVolumeController;
import ru.method.linkenvisuals.utils.render.ColorRGBA;
import ru.method.linkenvisuals.utils.render.GuiIcons;
import ru.method.linkenvisuals.utils.render.Render2D;
import ru.method.linkenvisuals.utils.render.fonts.Fonts;
import ru.method.linkenvisuals.utils.render.fonts.Instance;

import java.awt.Color;

/**
 * Медиаплеер нового ClickGUI 3a (низ центрального окна). Привязан к реальному системному медиа
 * (DynamicIslandMedia — WinRT SMTC, общий синглтон) и системной громкости (SystemVolumeController).
 * Раскладка по споке: слева обложка+название/исполнитель, по центру prev/play/next, справа громкость;
 * ниже — таймкоды и полоса прогресса. Перемотки нет (SMTC не даёт seek) — прогресс-бар не интерактивен.
 */
public final class ClickGuiPlayer {
    private static final Color TEXT = new Color(0xee, 0xf2, 0xef);
    private static final Color TEXT3 = new Color(0xc3, 0xcc, 0xc7);
    private static final Color MUTED = new Color(0x8b, 0x96, 0x8f);
    private static final Color MUTED3 = new Color(0x5d, 0x68, 0x62);
    private static final float ART = 40f;
    private static final float MEDIA_BASELINE = 1.08984375f;

    private final DynamicIslandMedia media = DynamicIslandMediaHolder.shared();
    private final Animation[] ctrlHover = {
            new Animation(140, 1f, false, Easing.EASE_OUT_CUBIC),
            new Animation(140, 1f, false, Easing.EASE_OUT_CUBIC),
            new Animation(140, 1f, false, Easing.EASE_OUT_CUBIC)
    };

    private float x, y, width;
    private float volume = 0.72f;
    private float displayedVolume = 0.72f;
    private boolean volumeInit;
    private boolean volumeDragging;
    private long lastVolumePollMs, lastVolumeSetMs;
    private float preMuteVolume = 0.5f;
    private float displayedProgress;
    private boolean progressInit;
    private long lastProgressMs;
    private long lastVolumeSmoothMs;

    public void init() {
        media.ensureExecutor();
        SystemVolumeController.ensureInit();
        SystemVolumeController.refresh();
        Float sys = SystemVolumeController.currentVolume();
        if (sys != null) volume = sys;
    }

    public void shutdown() {
        volumeDragging = false;
    }

    public void setBounds(float x, float y, float width) {
        this.x = x;
        this.y = y;
        this.width = width;
    }

    public float height() {
        return ART + 10f + 12f;
    }

    public void tick() {
        if (!isDynamicIslandUpdating()) media.update();
        long now = System.currentTimeMillis();
        if (!volumeDragging && now - lastVolumePollMs > 250L && now - lastVolumeSetMs > 600L) {
            SystemVolumeController.refresh();
            Float sys = SystemVolumeController.currentVolume();
            if (sys != null) volume = sys;
            lastVolumePollMs = now;
        }
    }

    // ---- геометрия ----
    private float rowACenterY() { return y + ART / 2f; }
    private float[] ctrlCentersX() {
        float cx = x + width / 2f;
        return new float[]{ cx - 33f, cx, cx + 33f };
    }
    private float volBarX() { return x + width - 80f; }
    private float volIconCx() { return volBarX() - 9f - 7f; }

    public void render(DrawContext ctx, float mouseX, float mouseY, Color accent, float fade, boolean light) {
        Matrix3x2fStack ms = ctx.getMatrices();
        float rowCy = rowACenterY();
        // Адаптация под светлую тему: тёмный текст вместо белого/светло-серого.
        Color cText  = light ? new Color(0x1a, 0x1f, 0x26) : TEXT;
        Color cText3 = light ? new Color(0x44, 0x4b, 0x56) : TEXT3;
        Color cMuted = light ? new Color(0x7a, 0x82, 0x8e) : MUTED;
        Color cTitle = light ? new Color(0x12, 0x16, 0x1c) : Color.WHITE;
        int hairV = light ? 30 : 255; // база hairline: тёмная на светлой теме, белая на тёмной

        // --- обложка ---
        float artY = y;
        NativeImageBackedTexture art = media.hasMedia() ? media.getArtworkTexture() : null;
        if (art != null) {
            Render2D.drawTexture(ms, x, artY, ART, ART, 8f, art, a(Color.WHITE, fade));
        } else {
            Render2D.drawRoundedRect(ms, x, artY, ART, ART, 8f, a(new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), 90), fade));
        }
        Render2D.drawBorder(ms, x, artY, ART, ART, 8f, 0.8f, new ColorRGBA(a(new Color(hairV, hairV, hairV, 26), fade)));

        // --- название/исполнитель ---
        float tx = x + ART + 13f;
        // Типографика по спеке: название — Unbounded 12/500, исполнитель — IBM Plex Mono 10.5.
        Instance titleF = Fonts.UNBOUNDED.getFont(12f);
        Instance subF = Fonts.PLEXMONO.getFont(10.5f);
        String title = media.hasMedia() ? safe(media.getTitle(), "Unknown track") : "Нет медиа";
        String artist = media.hasMedia() ? safe(media.getArtist(), "") : "плеер простаивает";
        float maxTextW = width * 0.22f;
        float titleH = titleF.getHeight();
        boolean hasArtist = artist != null && !artist.isBlank();
        float subH = subF.getHeight();
        float blockH = titleH + (hasArtist ? 3f + subH : 0f);
        float ty = rowCy - blockH / 2f;
        Render2D.drawFont(ms, titleF, clip(titleF, title, maxTextW), tx, ty, a(cTitle, fade));
        if (hasArtist) {
            Render2D.drawFont(ms, subF, clip(subF, artist, maxTextW), tx, ty + titleH + 3f, a(cMuted, fade));
        }

        // --- центр: prev / play / next ---
        float[] cxs = ctrlCentersX();
        boolean playing = media.isPlaying();
        String[] glyphs = { "A", playing ? "D" : "C", "B" };
        float[] sizes = { 16f, 21f, 16f };
        for (int i = 0; i < 3; i++) {
            boolean hover = dist(mouseX, mouseY, cxs[i], rowCy) < 13f;
            ctrlHover[i].update(hover);
            float t = MathHelper.clamp(ctrlHover[i].getValue(), 0f, 1f);
            Color base = i == 1 ? lerp(cText, accent, t) : lerp(cText3, cText, t);
            drawMediaIcon(ms, glyphs[i], cxs[i], rowCy, a(base, fade), sizes[i]);
        }

        // --- справа: громкость ---
        float barX = volBarX();
        float barY = rowCy - 1.5f;
        float iconCx = volIconCx();
        volumeInit = smoothVolumeStep();
        boolean muted = volume < 0.01f;
        GuiIcons.draw(ms, muted ? GuiIcons.MUTE : GuiIcons.SOUND, iconCx, rowCy, 14f, a(cText3, fade));
        Render2D.drawRoundedRect(ms, barX, barY, 80f, 3f, 1.5f, a(new Color(hairV, hairV, hairV, 41), fade));
        Render2D.drawRoundedRect(ms, barX, barY, 80f * displayedVolume, 3f, 1.5f, a(accent, fade));
        // Тянущийся кружок громкости — чистый круг без обводки (drag через mouseDragged).
        float volKnobX = barX + 80f * displayedVolume;
        float volKnobY = barY + 1.5f;
        Render2D.drawCircle(ms, volKnobX, volKnobY, 4.5f, 56, a(cText, fade));

        // --- прогресс ---
        float progRowY = y + ART + 8f;
        long dur = Math.max(1L, media.hasMedia() ? media.getDurationMs() : 1L);
        long pos = media.hasMedia() ? media.getPositionMs() : 0L;
        float progress = media.hasMedia() ? smoothProgress(Math.min(1f, pos / (float) dur), playing, dur) : 0f;
        String cur = fmt(pos), tot = fmt(dur);
        // Таймкоды — IBM Plex Mono 10.5 (спека: моно только для чистых данных).
        Instance tf = Fonts.PLEXMONO.getFont(10.5f);
        float curW = tf.getWidth(cur), totW = tf.getWidth(tot);
        float tcY = progRowY;
        // Округляем baseline (MSDF на мелком кегле мазался) + fake-bold: два прохода со сдвигом 0.5px — «пожирнее».
        Color tcCol = a(cText3, fade);
        int cx0 = Math.round(x), tx0 = Math.round(x + width - totW), ty0 = Math.round(tcY);
        Render2D.drawFont(ms, tf, cur, cx0, ty0, tcCol);
        Render2D.drawFont(ms, tf, cur, cx0 + 0.5f, ty0, tcCol);
        Render2D.drawFont(ms, tf, tot, tx0, ty0, tcCol);
        Render2D.drawFont(ms, tf, tot, tx0 + 0.5f, ty0, tcCol);
        float lineX = x + curW + 10f;
        float lineW = width - curW - totW - 20f;
        float lineY = tcY + tf.getHeight() / 2f - 1f;
        Render2D.drawRoundedRect(ms, lineX, lineY, lineW, 2f, 1f, a(new Color(hairV, hairV, hairV, 41), fade));
        if (progress > 0.001f) {
            Render2D.drawRoundedRect(ms, lineX, lineY, lineW * progress, 2f, 1f, a(cText, fade));
            float knobCx = lineX + lineW * progress;
            // Кноб прогресса — чистый круг без обводки.
            Render2D.drawCircle(ms, knobCx, lineY + 1f, 4.5f, 56, a(cText, fade));
        }
    }

    // ---- input ----
    public boolean mouseClicked(double mx, double my, int button) {
        if (button != 0) return false;
        float rowCy = rowACenterY();
        // Иконка mute
        float iconCx = volIconCx();
        if (dist((float) mx, (float) my, iconCx, rowCy) < 9f) { toggleMute(); return true; }
        // Полоса громкости
        float barX = volBarX();
        if (mx >= barX - 6 && mx <= barX + 86 && Math.abs(my - (rowCy - 1.5f) - 1.5f) < 8f) {
            volumeDragging = true;
            applyVolume(((float) mx - barX) / 80f);
            return true;
        }
        if (!media.hasMedia()) return false;
        float[] cxs = ctrlCentersX();
        for (int i = 0; i < 3; i++) {
            if (dist((float) mx, (float) my, cxs[i], rowCy) < 13f) {
                if (i == 0) media.previous();
                else if (i == 1) media.playPause();
                else media.next();
                return true;
            }
        }
        return false;
    }

    public void mouseReleased(double mx, double my, int button) {
        if (button == 0) volumeDragging = false;
    }

    public void mouseDragged(double mx, double my) {
        if (volumeDragging) applyVolume(((float) mx - volBarX()) / 80f);
    }

    // ---- helpers ----
    private void applyVolume(float t) {
        volume = MathHelper.clamp(t, 0f, 1f);
        SystemVolumeController.setVolume(volume);
        lastVolumeSetMs = System.currentTimeMillis();
    }

    private void toggleMute() {
        if (volume > 0.001f) { preMuteVolume = volume; applyVolume(0f); }
        else applyVolume(preMuteVolume > 0.01f ? preMuteVolume : 0.5f);
    }

    private boolean smoothVolumeStep() {
        long now = System.currentTimeMillis();
        if (!volumeInit) { displayedVolume = volume; lastVolumeSmoothMs = now; return true; }
        // time-based сглаживание (как smoothProgress): скорость не зависит от FPS
        float dt = Math.min(0.1f, Math.max(0f, (now - lastVolumeSmoothMs) / 1000f));
        lastVolumeSmoothMs = now;
        displayedVolume += (volume - displayedVolume) * (1f - (float) Math.exp(-15f * dt));
        if (Math.abs(volume - displayedVolume) < 0.001f) displayedVolume = volume;
        return true;
    }

    private float smoothProgress(float target, boolean playing, long durationMs) {
        long now = System.currentTimeMillis();
        if (!progressInit) { displayedProgress = target; progressInit = true; lastProgressMs = now; return target; }
        float dt = Math.min(0.25f, Math.max(0f, (now - lastProgressMs) / 1000f));
        lastProgressMs = now;
        if (Math.abs(target - displayedProgress) > 0.04f) { displayedProgress = target; return target; }
        if (playing && durationMs > 0L) displayedProgress += dt * 1000f / durationMs;
        displayedProgress += (target - displayedProgress) * (1f - (float) Math.exp(-0.8f * dt));
        displayedProgress = MathHelper.clamp(displayedProgress, 0f, 1f);
        return displayedProgress;
    }

    private void drawMediaIcon(Matrix3x2fStack ms, String glyph, float centerX, float centerY, Color color, float baseSize) {
        float size = baseSize;
        float planeLeft, planeRight, planeBottom, planeTop;
        switch (glyph) {
            case "A", "B" -> { planeLeft = -0.078125f; planeRight = 1.078125f; planeBottom = -0.078125f; planeTop = 1.078125f; }
            case "C", "D" -> { planeLeft = 0.046875f; planeRight = 0.953125f; planeBottom = -0.078125f; planeTop = 1.078125f; }
            default -> { planeLeft = -0.0771484375f; planeRight = 1.0791015625f; planeBottom = 0.02392578125f; planeTop = 0.97705078125f; }
        }
        float planeCenterX = (planeLeft + planeRight) * 0.5f;
        float planeCenterY = (planeBottom + planeTop) * 0.5f;
        float drawX = centerX - (planeCenterX - planeLeft) * size;
        float drawY = centerY - (MEDIA_BASELINE - planeCenterY) * size;
        if ("C".equals(glyph)) drawX += size * 0.12f;
        Render2D.drawFont(ms, Fonts.MEDIA_PLAYER.getFont(size), glyph, drawX, drawY, color);
    }

    private boolean isDynamicIslandUpdating() {
        LinkenVisuals inst = LinkenVisuals.getInstance();
        if (inst == null || inst.getHudManager() == null) return false;
        DynamicIsland island = inst.getHudManager().getHudElement(DynamicIsland.class);
        return island != null && island.isToggled();
    }

    private static float dist(float x1, float y1, float x2, float y2) {
        float dx = x1 - x2, dy = y1 - y2;
        return (float) Math.sqrt(dx * dx + dy * dy);
    }

    private static Color a(Color c, float fade) {
        int al = Math.max(0, Math.min(255, Math.round(c.getAlpha() * fade)));
        return new Color(c.getRed(), c.getGreen(), c.getBlue(), al);
    }

    private static Color lerp(Color a, Color b, float t) {
        t = MathHelper.clamp(t, 0f, 1f);
        return new Color(
                Math.round(a.getRed() + (b.getRed() - a.getRed()) * t),
                Math.round(a.getGreen() + (b.getGreen() - a.getGreen()) * t),
                Math.round(a.getBlue() + (b.getBlue() - a.getBlue()) * t),
                Math.round(a.getAlpha() + (b.getAlpha() - a.getAlpha()) * t));
    }

    private static String safe(String s, String fallback) {
        return (s == null || s.isBlank()) ? fallback : s;
    }

    private static String clip(Instance f, String text, float max) {
        if (text == null) return "";
        if (f.getWidth(text) <= max) return text;
        // "..." вместо U+2026: глифа «…» нет в атласах (charset U+0020–U+045F), он рисовался пустотой.
        while (text.length() > 1 && f.getWidth(text + "...") > max) text = text.substring(0, text.length() - 1);
        return text + "...";
    }

    private static String fmt(long ms) {
        long s = Math.max(0L, ms) / 1000L;
        return (s / 60L) + ":" + (s % 60L < 10 ? "0" : "") + (s % 60L);
    }
}
